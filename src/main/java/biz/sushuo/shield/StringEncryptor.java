package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
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
    private StringEncryptor() {
    }

    static int encrypt(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper, long seed) {
        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x6A09E667F3BCC909L);
        String mappedOwner = remapper.map(classNode.name);
        for (MethodNode method : classNode.methods) {
            int site = 0;
            String mappedMethod = remapper.mapMethodName(classNode.name, method.name, method.desc);
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String value && !value.isEmpty()) {
                    int key = random.nextInt();
                    int salt = random.nextInt();
                    int siteId = site++;
                    InsnList replacement = new InsnList();
                    replacement.add(new LdcInsnNode(StringCipher.encodeDynamic(value, key, siteId, salt, mappedOwner, mappedMethod)));
                    NumberObfuscator.pushDynamicInt(replacement, key, random, runtimeClassName, mappedOwner, mappedMethod);
                    NumberObfuscator.pushDynamicInt(replacement, siteId, random, runtimeClassName, mappedOwner, mappedMethod);
                    NumberObfuscator.pushDynamicInt(replacement, salt, random, runtimeClassName, mappedOwner, mappedMethod);
                    replacement.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_d",
                            "(Ljava/lang/String;III)Ljava/lang/String;", false));
                    method.instructions.insert(instruction, replacement);
                    method.instructions.remove(instruction);
                    count++;
                } else if (instruction instanceof InvokeDynamicInsnNode indy && isStringConcatWithConstants(indy)) {
                    String recipe = resolvedConcatRecipe(indy);
                    if (!recipe.isEmpty()) {
                        int key = random.nextInt();
                        int salt = random.nextInt();
                        int siteId = site++;
                        InsnList replacement = concatReplacement(method, indy, recipe, key, siteId, salt,
                                runtimeClassName, mappedOwner, mappedMethod, random);
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
            String mappedMethod,
            Random random
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

        replacement.add(new LdcInsnNode(StringCipher.encodeDynamic(recipe, key, siteId, salt, mappedOwner, mappedMethod)));
        NumberObfuscator.pushDynamicInt(replacement, key, random, runtimeClassName, mappedOwner, mappedMethod);
        NumberObfuscator.pushDynamicInt(replacement, siteId, random, runtimeClassName, mappedOwner, mappedMethod);
        NumberObfuscator.pushDynamicInt(replacement, salt, random, runtimeClassName, mappedOwner, mappedMethod);
        replacement.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_d",
                "(Ljava/lang/String;III)Ljava/lang/String;", false));

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
