package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Random;

final class NumberObfuscator implements Opcodes {
    private static final int FLAG_NATIVE_KEY = 1;
    private static final int CONST_KIND_INT = 2;
    private static final int CONST_KIND_LONG = 3;
    private static final int CONST_KIND_FLOAT = 4;
    private static final int CONST_KIND_DOUBLE = 5;

    private NumberObfuscator() {
    }

    static int obfuscate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper, long seed) {
        return obfuscate(classNode, runtimeClassName, remapper, seed, false);
    }

    static int obfuscateForcedMutate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper, long seed) {
        return obfuscate(classNode, runtimeClassName, remapper, seed, true);
    }

    static int obfuscate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                         long seed, NamingPlan namingPlan, boolean nativeKeys) {
        return obfuscate(classNode, runtimeClassName, remapper, seed, false, namingPlan, nativeKeys);
    }

    static int obfuscateForcedMutate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                                     long seed, NamingPlan namingPlan, boolean nativeKeys) {
        return obfuscate(classNode, runtimeClassName, remapper, seed, true, namingPlan, nativeKeys);
    }

    private static int obfuscate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                                 long seed, boolean forcedMutateOnly) {
        return obfuscate(classNode, runtimeClassName, remapper, seed, forcedMutateOnly, null, false);
    }

    private static int obfuscate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                                 long seed, boolean forcedMutateOnly,
                                 NamingPlan namingPlan, boolean nativeKeys) {
        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0xBB67AE8584CAA73BL);
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
                InsnList replacement = intReplacement(instruction, runtimeClassName, mappedOwner, mappedMethod,
                        site, random, namingPlan, nativeKeys, seed);
                if (replacement == null) {
                    replacement = longReplacement(instruction, runtimeClassName, mappedOwner, mappedMethod,
                            site, random, namingPlan, nativeKeys, seed);
                }
                if (replacement == null) {
                    replacement = floatReplacement(instruction, runtimeClassName, mappedOwner, mappedMethod,
                            site, random, namingPlan, nativeKeys, seed);
                }
                if (replacement == null) {
                    replacement = doubleReplacement(instruction, runtimeClassName, mappedOwner, mappedMethod,
                            site, random, namingPlan, nativeKeys, seed);
                }
                if (replacement != null) {
                    method.instructions.insert(instruction, replacement);
                    method.instructions.remove(instruction);
                    count++;
                    site++;
                }
                instruction = next;
            }
        }
        return count;
    }

    static void pushDynamicInt(InsnList list, int value, Random random, String runtimeClassName, String owner, String method) {
        int key = random.nextInt();
        int salt = random.nextInt();
        int encrypted = value ^ dynamicImmediateKey(key, salt, owner, method);
        list.add(new LdcInsnNode(encrypted));
        list.add(new LdcInsnNode(key));
        list.add(new LdcInsnNode(salt));
        list.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_q", "(III)I", false));
    }

    private static InsnList intReplacement(AbstractInsnNode instruction, String runtimeClassName, String owner, String method,
                                           int site, Random random, NamingPlan namingPlan, boolean nativeKeys, long seed) {
        Integer value = intValue(instruction);
        if (value == null) {
            return null;
        }
        int key = random.nextInt();
        int salt = random.nextInt();
        String indyName = indyName(random, method, site);
        int dynamicKey = dynamicIntKey(key, site, salt, owner, indyName);
        if (nativeKeys) {
            if (namingPlan == null) {
                throw new IllegalArgumentException("Native-key number obfuscation requires a naming plan");
            }
            dynamicKey ^= VmPayloadResources.constantMask32(namingPlan, seed, CONST_KIND_INT,
                    owner.replace('/', '.'), indyName, key, site, salt);
        }
        int encrypted = value ^ dynamicKey;
        InsnList list = new InsnList();
        list.add(new InvokeDynamicInsnNode(
                indyName,
                "()I",
                new Handle(H_INVOKESTATIC, runtimeClassName, "_ci",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;IIIII)Ljava/lang/invoke/CallSite;",
                        false),
                encrypted,
                key,
                site,
                salt,
                nativeKeys ? FLAG_NATIVE_KEY : 0));
        return list;
    }

    private static InsnList longReplacement(AbstractInsnNode instruction, String runtimeClassName, String owner, String method,
                                            int site, Random random, NamingPlan namingPlan, boolean nativeKeys, long seed) {
        Long value = longValue(instruction);
        if (value == null) {
            return null;
        }
        long key = random.nextLong();
        int salt = random.nextInt();
        String indyName = indyName(random, method, site);
        long dynamicKey = dynamicLongKey(key, site, salt, owner, indyName);
        if (nativeKeys) {
            if (namingPlan == null) {
                throw new IllegalArgumentException("Native-key number obfuscation requires a naming plan");
            }
            dynamicKey ^= VmPayloadResources.constantMask64(namingPlan, seed, CONST_KIND_LONG,
                    owner.replace('/', '.'), indyName, key, site, salt);
        }
        long encrypted = value ^ dynamicKey;
        InsnList list = new InsnList();
        list.add(new InvokeDynamicInsnNode(
                indyName,
                "()J",
                new Handle(H_INVOKESTATIC, runtimeClassName, "_cl",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;JJIII)Ljava/lang/invoke/CallSite;",
                        false),
                encrypted,
                key,
                site,
                salt,
                nativeKeys ? FLAG_NATIVE_KEY : 0));
        return list;
    }

    private static InsnList floatReplacement(AbstractInsnNode instruction, String runtimeClassName, String owner, String method,
                                             int site, Random random, NamingPlan namingPlan, boolean nativeKeys, long seed) {
        Float value = floatValue(instruction);
        if (value == null) {
            return null;
        }
        int key = random.nextInt();
        int salt = random.nextInt();
        String indyName = indyName(random, method, site);
        int dynamicKey = dynamicIntKey(key, site, salt, owner, indyName);
        if (nativeKeys) {
            if (namingPlan == null) {
                throw new IllegalArgumentException("Native-key number obfuscation requires a naming plan");
            }
            dynamicKey ^= VmPayloadResources.constantMask32(namingPlan, seed, CONST_KIND_FLOAT,
                    owner.replace('/', '.'), indyName, key, site, salt);
        }
        int encrypted = Float.floatToIntBits(value) ^ dynamicKey;
        InsnList list = new InsnList();
        list.add(new InvokeDynamicInsnNode(
                indyName,
                "()F",
                new Handle(H_INVOKESTATIC, runtimeClassName, "_cf",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;IIIII)Ljava/lang/invoke/CallSite;",
                        false),
                encrypted,
                key,
                site,
                salt,
                nativeKeys ? FLAG_NATIVE_KEY : 0));
        return list;
    }

    private static InsnList doubleReplacement(AbstractInsnNode instruction, String runtimeClassName, String owner, String method,
                                              int site, Random random, NamingPlan namingPlan, boolean nativeKeys, long seed) {
        Double value = doubleValue(instruction);
        if (value == null) {
            return null;
        }
        long key = random.nextLong();
        int salt = random.nextInt();
        String indyName = indyName(random, method, site);
        long dynamicKey = dynamicLongKey(key, site, salt, owner, indyName);
        if (nativeKeys) {
            if (namingPlan == null) {
                throw new IllegalArgumentException("Native-key number obfuscation requires a naming plan");
            }
            dynamicKey ^= VmPayloadResources.constantMask64(namingPlan, seed, CONST_KIND_DOUBLE,
                    owner.replace('/', '.'), indyName, key, site, salt);
        }
        long encrypted = Double.doubleToLongBits(value) ^ dynamicKey;
        InsnList list = new InsnList();
        list.add(new InvokeDynamicInsnNode(
                indyName,
                "()D",
                new Handle(H_INVOKESTATIC, runtimeClassName, "_cd",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;JJIII)Ljava/lang/invoke/CallSite;",
                        false),
                encrypted,
                key,
                site,
                salt,
                nativeKeys ? FLAG_NATIVE_KEY : 0));
        return list;
    }

    private static String indyName(Random random, String method, int site) {
        int a = mix(random.nextInt() ^ method.hashCode() ^ site * 0x45D9F3B);
        int b = mix(random.nextInt() ^ Integer.rotateLeft(a, 11) ^ site * 0x27D4EB2D);
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

    private static int dynamicImmediateKey(int key, int salt, String owner, String method) {
        int mixed = key ^ salt ^ 0x9E3779B9;
        mixed ^= owner.replace('/', '.').hashCode();
        mixed ^= Integer.rotateLeft(method.hashCode(), 5);
        mixed = Integer.rotateLeft(mixed * 0x85EBCA6B, 13);
        mixed ^= mixed >>> 16;
        mixed *= 0xC2B2AE35;
        return mixed ^ (mixed >>> 13);
    }

    private static int dynamicIntKey(int key, int site, int salt, String owner, String method) {
        int mixed = key ^ Integer.rotateLeft(site * 0x27D4EB2D, 9) ^ salt;
        mixed ^= owner.replace('/', '.').hashCode();
        mixed = Integer.rotateLeft(mixed + 0x165667B1, 7);
        mixed ^= method.hashCode() * 0x85EBCA6B;
        mixed ^= mixed >>> 15;
        mixed *= 0xC2B2AE35;
        return mixed ^ (mixed >>> 16);
    }

    private static long dynamicLongKey(long key, int site, int salt, String owner, String method) {
        long mixed = key ^ (((long) site) << 32) ^ (salt & 0xFFFFFFFFL);
        mixed ^= owner.replace('/', '.').hashCode();
        mixed = Long.rotateLeft(mixed + 0x9E3779B97F4A7C15L, 17);
        mixed ^= ((long) method.hashCode()) * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static Integer intValue(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        return switch (opcode) {
            case ICONST_M1 -> -1;
            case ICONST_0 -> 0;
            case ICONST_1 -> 1;
            case ICONST_2 -> 2;
            case ICONST_3 -> 3;
            case ICONST_4 -> 4;
            case ICONST_5 -> 5;
            case BIPUSH, SIPUSH -> ((IntInsnNode) instruction).operand;
            case LDC -> {
                Object constant = ((LdcInsnNode) instruction).cst;
                yield constant instanceof Integer integer ? integer : null;
            }
            default -> null;
        };
    }

    private static Long longValue(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        return switch (opcode) {
            case LCONST_0 -> 0L;
            case LCONST_1 -> 1L;
            case LDC -> {
                Object constant = ((LdcInsnNode) instruction).cst;
                yield constant instanceof Long longValue ? longValue : null;
            }
            default -> null;
        };
    }

    private static Float floatValue(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        return switch (opcode) {
            case FCONST_0 -> 0.0f;
            case FCONST_1 -> 1.0f;
            case FCONST_2 -> 2.0f;
            case LDC -> {
                Object constant = ((LdcInsnNode) instruction).cst;
                yield constant instanceof Float floatValue ? floatValue : null;
            }
            default -> null;
        };
    }

    private static Double doubleValue(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        return switch (opcode) {
            case DCONST_0 -> 0.0d;
            case DCONST_1 -> 1.0d;
            case LDC -> {
                Object constant = ((LdcInsnNode) instruction).cst;
                yield constant instanceof Double doubleValue ? doubleValue : null;
            }
            default -> null;
        };
    }
}
