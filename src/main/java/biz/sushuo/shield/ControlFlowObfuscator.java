package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

final class ControlFlowObfuscator implements Opcodes {
    private ControlFlowObfuscator() {
    }

    static int apply(ClassNode classNode, String runtimeClassName) {
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null || method.instructions.size() == 0) {
                continue;
            }
            if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || method.name.equals("<init>")) {
                continue;
            }

            LabelNode ok = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
            guard.add(new JumpInsnNode(IFNE, ok));
            guard.add(new TypeInsnNode(NEW, "java/lang/IllegalStateException"));
            guard.add(new InsnNode(DUP));
            guard.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false));
            guard.add(new InsnNode(ATHROW));
            guard.add(ok);

            AbstractInsnNode first = firstRealInstruction(method);
            if (first == null) {
                method.instructions.add(guard);
            } else {
                method.instructions.insertBefore(first, guard);
            }
            count++;
        }
        return count;
    }

    private static AbstractInsnNode firstRealInstruction(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof LabelNode)
                    && !(instruction instanceof LineNumberNode)
                    && !(instruction instanceof FrameNode)) {
                return instruction;
            }
        }
        return null;
    }
}
