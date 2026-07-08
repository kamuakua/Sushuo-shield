package biz.sushuo.shield;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Random;

final class LineNumberScrambler {
    private LineNumberScrambler() {
    }

    static int scramble(ClassNode classNode, long seed) {
        classNode.sourceFile = null;
        classNode.sourceDebug = null;
        if (classNode.innerClasses != null) {
            for (InnerClassNode innerClass : classNode.innerClasses) {
                innerClass.innerName = null;
            }
        }

        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x4F1BBCDCBFA53E0BL);
        for (MethodNode method : classNode.methods) {
            method.localVariables = null;
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            int base = 10_000 + random.nextInt(800_000);
            int step = 1 + random.nextInt(997);
            int index = 0;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof LineNumberNode line) {
                    int mixed = base ^ Integer.rotateLeft(++index * 0x45D9F3B, index & 15);
                    line.line = Math.floorMod(mixed, 900_000) + step;
                    count++;
                }
            }
        }
        return count;
    }
}
