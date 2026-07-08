package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Random;

final class ExceptionFlowObfuscator implements Opcodes {
    private ExceptionFlowObfuscator() {
    }

    static int apply(ClassNode classNode, String runtimeClassName, long seed) {
        if ((classNode.access & (ACC_INTERFACE | ACC_ANNOTATION)) != 0) {
            return 0;
        }
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x510E527FADE682D1L);
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if (!canTransform(method)) {
                continue;
            }
            AbstractInsnNode first = firstRealInstruction(method);
            if (first == null) {
                continue;
            }
            int throwableLocal = method.maxLocals++;
            LabelNode start = new LabelNode();
            LabelNode body = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode handler = new LabelNode();
            LabelNode rethrow = new LabelNode();

            InsnList prefix = new InsnList();
            prefix.add(start);
            prefix.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
            prefix.add(new JumpInsnNode(IFNE, body));
            prefix.add(new LdcInsnNode("ss.xflow." + Long.toUnsignedString(random.nextLong(), 36)));
            prefix.add(new InsnNode(POP));
            prefix.add(new TypeInsnNode(NEW, "java/lang/IllegalStateException"));
            prefix.add(new InsnNode(DUP));
            prefix.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false));
            prefix.add(new InsnNode(ATHROW));
            prefix.add(body);
            method.instructions.insertBefore(first, prefix);

            InsnList suffix = new InsnList();
            suffix.add(end);
            suffix.add(handler);
            suffix.add(new VarInsnNode(ASTORE, throwableLocal));
            suffix.add(new LdcInsnNode(random.nextInt()));
            suffix.add(new InsnNode(POP));
            suffix.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
            suffix.add(new JumpInsnNode(IFNE, rethrow));
            suffix.add(new TypeInsnNode(NEW, "java/lang/IllegalStateException"));
            suffix.add(new InsnNode(DUP));
            suffix.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false));
            suffix.add(new InsnNode(ATHROW));
            suffix.add(rethrow);
            suffix.add(new VarInsnNode(ALOAD, throwableLocal));
            suffix.add(new InsnNode(ATHROW));
            method.instructions.add(suffix);

            if (method.tryCatchBlocks == null) {
                method.tryCatchBlocks = new java.util.ArrayList<>();
            }
            method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
            count += 2;
        }
        return count;
    }

    private static boolean canTransform(MethodNode method) {
        if ((method.access & (ACC_ABSTRACT | ACC_NATIVE | ACC_SYNTHETIC)) != 0) {
            return false;
        }
        if (method.instructions == null || method.instructions.size() == 0) {
            return false;
        }
        if (method.name.equals("<init>")) {
            return false;
        }
        if (isVmDataMethod(method) || method.name.startsWith("_ad$")) {
            return false;
        }
        return !SDKMarkerSupport.noProtect(method);
    }

    private static boolean isVmDataMethod(MethodNode method) {
        return (method.access & (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)) == (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)
                && method.desc.equals("()[Ljava/lang/Object;");
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
