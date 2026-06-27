package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Random;

final class StringEncryptor implements Opcodes {
    private StringEncryptor() {
    }

    static int encrypt(ClassNode classNode, String runtimeClassName, long seed) {
        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x6A09E667F3BCC909L);
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String value && !value.isEmpty()) {
                    int key = random.nextInt();
                    InsnList replacement = new InsnList();
                    replacement.add(new LdcInsnNode(StringCipher.encode(value, key)));
                    replacement.add(new LdcInsnNode(key));
                    replacement.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_d",
                            "(Ljava/lang/String;I)Ljava/lang/String;", false));
                    method.instructions.insert(instruction, replacement);
                    method.instructions.remove(instruction);
                    count++;
                }
                instruction = next;
            }
        }
        return count;
    }
}
