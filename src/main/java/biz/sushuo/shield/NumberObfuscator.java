package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Random;

final class NumberObfuscator implements Opcodes {
    private NumberObfuscator() {
    }

    static int obfuscate(ClassNode classNode, long seed) {
        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0xBB67AE8584CAA73BL);
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                InsnList replacement = intReplacement(instruction, random);
                if (replacement == null) {
                    replacement = longReplacement(instruction, random);
                }
                if (replacement != null) {
                    method.instructions.insert(instruction, replacement);
                    method.instructions.remove(instruction);
                    count++;
                }
                instruction = next;
            }
        }
        return count;
    }

    private static InsnList intReplacement(AbstractInsnNode instruction, Random random) {
        Integer value = intValue(instruction);
        if (value == null) {
            return null;
        }
        int key = random.nextInt();
        InsnList list = new InsnList();
        list.add(new LdcInsnNode(value ^ key));
        list.add(new LdcInsnNode(key));
        list.add(new InsnNode(IXOR));
        return list;
    }

    private static InsnList longReplacement(AbstractInsnNode instruction, Random random) {
        Long value = longValue(instruction);
        if (value == null) {
            return null;
        }
        long key = random.nextLong();
        InsnList list = new InsnList();
        list.add(new LdcInsnNode(value ^ key));
        list.add(new LdcInsnNode(key));
        list.add(new InsnNode(LXOR));
        return list;
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
