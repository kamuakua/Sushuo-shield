package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Random;

final class StringEncryptor implements Opcodes {
    private static final int FLAG_NATIVE_KEY = 1;
    private static final int CONST_KIND_STRING = 1;

    private StringEncryptor() {
    }

    static int encrypt(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper, long seed) {
        return encrypt(classNode, runtimeClassName, remapper, seed, false);
    }

    static int encryptForcedMutate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper, long seed) {
        return encrypt(classNode, runtimeClassName, remapper, seed, true);
    }

    static int encrypt(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                       long seed, NamingPlan namingPlan, boolean nativeKeys) {
        return encrypt(classNode, runtimeClassName, remapper, seed, false, namingPlan, nativeKeys);
    }

    static int encryptForcedMutate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                                   long seed, NamingPlan namingPlan, boolean nativeKeys) {
        return encrypt(classNode, runtimeClassName, remapper, seed, true, namingPlan, nativeKeys);
    }

    private static int encrypt(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                               long seed, boolean forcedMutateOnly) {
        return encrypt(classNode, runtimeClassName, remapper, seed, forcedMutateOnly, null, false);
    }

    private static int encrypt(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                               long seed, boolean forcedMutateOnly,
                               NamingPlan namingPlan, boolean nativeKeys) {
        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x6A09E667F3BCC909L);
        String mappedOwner = remapper.map(classNode.name);
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null
                    || SDKMarkerSupport.noProtect(method)
                    || forcedMutateOnly && !SDKMarkerSupport.forceMutate(classNode, method)) {
                continue;
            }
            int site = 0;
            String mappedMethod = remapper.mapMethodName(classNode.name, method.name, method.desc);
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String value && !value.isEmpty()) {
                    int key = random.nextInt();
                    int salt = random.nextInt();
                    int siteId = site++;
                    String indyName = indyName(random, mappedMethod, siteId);
                    String encoded = encode(value, key, siteId, salt, mappedOwner, indyName,
                            namingPlan, nativeKeys, seed);
                    method.instructions.insert(instruction, stringIndy(runtimeClassName, indyName,
                            encoded, key, siteId, salt, nativeKeys));
                    method.instructions.remove(instruction);
                    count++;
                } else if (instruction instanceof InvokeDynamicInsnNode indy && isStringConcatWithConstants(indy)) {
                    String recipe = resolvedConcatRecipe(indy);
                    if (!recipe.isEmpty()) {
                        int key = random.nextInt();
                        int salt = random.nextInt();
                        int siteId = site++;
                        String indyName = indyName(random, mappedMethod, siteId);
                        InsnList replacement = concatReplacement(method, indy, recipe, key, siteId, salt,
                                runtimeClassName, mappedOwner, indyName, namingPlan, nativeKeys, seed);
                        method.instructions.insert(instruction, replacement);
                        method.instructions.remove(instruction);
                        count++;
                    }
                }
                instruction = next;
            }
        }
        return count;
    }

    private static InvokeDynamicInsnNode stringIndy(String runtimeClassName, String indyName,
                                                   String encoded, int key, int siteId, int salt,
                                                   boolean nativeKeys) {
        return new InvokeDynamicInsnNode(
                indyName,
                "()Ljava/lang/String;",
                new Handle(H_INVOKESTATIC, runtimeClassName, "_cs",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;IIII)Ljava/lang/invoke/CallSite;",
                        false),
                encoded,
                key,
                siteId,
                salt,
                nativeKeys ? FLAG_NATIVE_KEY : 0);
    }

    private static String encode(String value, int key, int siteId, int salt, String mappedOwner,
                                 String indyName, NamingPlan namingPlan, boolean nativeKeys, long seed) {
        String dottedOwner = mappedOwner.replace('/', '.');
        int dynamicKey = StringCipher.dynamicKey(key, siteId, salt, dottedOwner, indyName);
        if (nativeKeys) {
            if (namingPlan == null) {
                throw new IllegalArgumentException("Native-key string encryption requires a naming plan");
            }
            dynamicKey ^= VmPayloadResources.constantMask32(namingPlan, seed, CONST_KIND_STRING,
                    dottedOwner, indyName, key, siteId, salt);
        }
        return StringCipher.encodeWithKey(value, dynamicKey);
    }

    private static String indyName(Random random, String method, int siteId) {
        int a = mix(random.nextInt() ^ method.hashCode() ^ siteId * 0x45D9F3B);
        int b = mix(random.nextInt() ^ Integer.rotateLeft(a, 11) ^ siteId * 0x27D4EB2D);
        return "_" + Integer.toUnsignedString(a, 36) + Integer.toUnsignedString(b, 36);
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static boolean isStringConcatWithConstants(InvokeDynamicInsnNode indy) {
        return indy.bsm != null
                && indy.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")
                && indy.bsm.getName().equals("makeConcatWithConstants")
                && Type.getReturnType(indy.desc).equals(Type.getType(String.class))
                && indy.bsmArgs != null
                && indy.bsmArgs.length > 0
                && indy.bsmArgs[0] instanceof String;
    }

    private static String resolvedConcatRecipe(InvokeDynamicInsnNode indy) {
        String recipe = (String) indy.bsmArgs[0];
        int constantIndex = 1;
        StringBuilder resolved = new StringBuilder(recipe.length());
        for (int i = 0; i < recipe.length(); i++) {
            char ch = recipe.charAt(i);
            if (ch == '\u0002' && constantIndex < indy.bsmArgs.length) {
                resolved.append(String.valueOf(indy.bsmArgs[constantIndex++]));
            } else {
                resolved.append(ch);
            }
        }
        return resolved.toString();
    }

    private static InsnList concatReplacement(
            MethodNode method,
            InvokeDynamicInsnNode indy,
            String recipe,
            int key,
            int siteId,
            int salt,
            String runtimeClassName,
            String mappedOwner,
            String indyName,
            NamingPlan namingPlan,
            boolean nativeKeys,
            long seed
    ) {
        Type[] argumentTypes = Type.getArgumentTypes(indy.desc);
        int[] locals = new int[argumentTypes.length];
        int nextLocal = method.maxLocals;
        InsnList replacement = new InsnList();

        for (int i = argumentTypes.length - 1; i >= 0; i--) {
            Type type = argumentTypes[i];
            locals[i] = nextLocal;
            nextLocal += type.getSize();
            replacement.add(new VarInsnNode(type.getOpcode(ISTORE), locals[i]));
        }
        method.maxLocals = Math.max(method.maxLocals, nextLocal);

        replacement.add(stringIndy(runtimeClassName, indyName,
                encode(recipe, key, siteId, salt, mappedOwner, indyName, namingPlan, nativeKeys, seed),
                key, siteId, salt, nativeKeys));

        pushInt(replacement, argumentTypes.length);
        replacement.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        for (int i = 0; i < argumentTypes.length; i++) {
            Type type = argumentTypes[i];
            replacement.add(new InsnNode(DUP));
            pushInt(replacement, i);
            replacement.add(new VarInsnNode(type.getOpcode(ILOAD), locals[i]));
            box(replacement, type);
            replacement.add(new InsnNode(AASTORE));
        }
        replacement.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_sc",
                "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false));
        return replacement;
    }

    private static void pushInt(InsnList list, int value) {
        if (value >= -1 && value <= 5) {
            list.add(new InsnNode(ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            list.add(new IntInsnNode(BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            list.add(new IntInsnNode(SIPUSH, value));
        } else {
            list.add(new LdcInsnNode(value));
        }
    }

    private static void box(InsnList list, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean",
                    "valueOf", "(Z)Ljava/lang/Boolean;", false));
            case Type.CHAR -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Character",
                    "valueOf", "(C)Ljava/lang/Character;", false));
            case Type.BYTE -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Byte",
                    "valueOf", "(B)Ljava/lang/Byte;", false));
            case Type.SHORT -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Short",
                    "valueOf", "(S)Ljava/lang/Short;", false));
            case Type.INT -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer",
                    "valueOf", "(I)Ljava/lang/Integer;", false));
            case Type.FLOAT -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float",
                    "valueOf", "(F)Ljava/lang/Float;", false));
            case Type.LONG -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long",
                    "valueOf", "(J)Ljava/lang/Long;", false));
            case Type.DOUBLE -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double",
                    "valueOf", "(D)Ljava/lang/Double;", false));
            default -> {
            }
        }
    }
}
