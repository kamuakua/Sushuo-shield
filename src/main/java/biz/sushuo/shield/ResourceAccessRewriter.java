package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class ResourceAccessRewriter implements Opcodes {
    private ResourceAccessRewriter() {
    }

    static int rewrite(ClassNode classNode, String runtimeClassName) {
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode call)) {
                    continue;
                }
                if (call.getOpcode() == INVOKEVIRTUAL
                        && call.owner.equals("java/lang/ClassLoader")
                        && call.name.equals("getResourceAsStream")
                        && call.desc.equals("(Ljava/lang/String;)Ljava/io/InputStream;")) {
                    call.setOpcode(INVOKESTATIC);
                    call.owner = runtimeClassName;
                    call.name = "_rl";
                    call.desc = "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/io/InputStream;";
                    call.itf = false;
                    count++;
                } else if (call.getOpcode() == INVOKEVIRTUAL
                        && call.owner.equals("java/lang/Class")
                        && call.name.equals("getResourceAsStream")
                        && call.desc.equals("(Ljava/lang/String;)Ljava/io/InputStream;")) {
                    call.setOpcode(INVOKESTATIC);
                    call.owner = runtimeClassName;
                    call.name = "_rc";
                    call.desc = "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;";
                    call.itf = false;
                    count++;
                } else if (call.getOpcode() == INVOKESTATIC
                        && call.owner.equals("java/lang/ClassLoader")
                        && call.name.equals("getSystemResourceAsStream")
                        && call.desc.equals("(Ljava/lang/String;)Ljava/io/InputStream;")) {
                    call.owner = runtimeClassName;
                    call.name = "_rg";
                    call.desc = "(Ljava/lang/String;)Ljava/io/InputStream;";
                    call.itf = false;
                    count++;
                }
            }
        }
        return count;
    }
}
