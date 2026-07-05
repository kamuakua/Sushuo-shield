package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Random;

final class NumberObfuscator implements Opcodes {
    private NumberObfuscator() {
    }

    static int obfuscate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper, long seed) {
        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0xBB67AE8584CAA73BL);
        String mappedOwner = remapper.map(classNode.name);
        for (MethodNode method : classNode.methods) {
            int site = 0;
            String mappedMethod = remapper.mapMethodName(classNode.name, method.name, method.desc);
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                InsnList replacement = intReplacement(instruction, runtimeClassName, mappedOwner, mappedMethod, site, random);
                if (replacement == null) {
                    replacement = longReplacement(instruction, runtimeClassName, mappedOwner, mappedMethod, site, random);
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

    private static InsnList intReplacement(AbstractInsnNode instruction, String runtimeClassName, String owner, String method, int site, Random random) {
        Integer value = intValue(instruction);
        if (value == null) {
            return null;
        }
        int key = random.nextInt();
        int salt = random.nextInt();
        int encrypted = value ^ dynamicIntKey(key, site, salt, owner, method);
        InsnList list = new InsnList();
        pushDynamicInt(list, encrypted, random, runtimeClassName, owner, method);
        pushDynamicInt(list, key, random, runtimeClassName, owner, method);
        pushDynamicInt(list, site, random, runtimeClassName, owner, method);
        pushDynamicInt(list, salt, random, runtimeClassName, owner, method);
        list.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName,
                "_i", "(IIII)I", false));
        return list;
    }

    private static InsnList longReplacement(AbstractInsnNode instruction, String runtimeClassName, String owner, String method, int site, Random random) {
        Long value = longValue(instruction);
        if (value == null) {
            return null;
        }
        long key = random.nextLong();
        int salt = random.nextInt();
        long encrypted = value ^ dynamicLongKey(key, site, salt, owner, method);
        InsnList list = new InsnList();
        list.add(new LdcInsnNode(encrypted));
        list.add(new LdcInsnNode(key));
        pushDynamicInt(list, site, random, runtimeClassName, owner, method);
        pushDynamicInt(list, salt, random, runtimeClassName, owner, method);
        list.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName,
                "_l", "(JJII)J", false));
        return list;
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
}
