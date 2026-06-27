package biz.sushuo.shield;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

final class DebugStripper {
    private DebugStripper() {
    }

    static void strip(ClassNode classNode) {
        classNode.sourceFile = null;
        classNode.sourceDebug = null;
        for (MethodNode method : classNode.methods) {
            method.localVariables = null;
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof LineNumberNode) {
                    method.instructions.remove(instruction);
                }
                instruction = next;
            }
        }
    }
}
