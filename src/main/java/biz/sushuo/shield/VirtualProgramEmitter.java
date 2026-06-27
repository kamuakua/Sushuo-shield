package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

final class VirtualProgramEmitter implements Opcodes {
    private VirtualProgramEmitter() {
    }

    static MethodNode createProgramMethod(String owner, String name, VirtualProgram program) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, "()[Ljava/lang/Object;", null, null);
        InsnList body = method.instructions;

        Virtualizer.pushInt(body, 6);
        body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

        putInt(body, 0, program.maxLocals());
        putInt(body, 1, program.parameterCount());
        putInt(body, 2, program.returnKind());
        putIntArray(body, 3, program.code());
        putObjectArray(body, 4, program.constants());
        putInt(body, 5, program.id());

        body.add(new InsnNode(ARETURN));
        method.maxLocals = 0;
        method.maxStack = 8;
        return method;
    }

    private static void putInt(InsnList body, int index, int value) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, value);
        body.add(integerValueOf());
        body.add(new InsnNode(AASTORE));
    }

    private static void putIntArray(InsnList body, int index, int[] values) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, values.length);
        body.add(new IntInsnNode(NEWARRAY, T_INT));
        for (int i = 0; i < values.length; i++) {
            body.add(new InsnNode(DUP));
            Virtualizer.pushInt(body, i);
            Virtualizer.pushInt(body, values[i]);
            body.add(new InsnNode(IASTORE));
        }
        body.add(new InsnNode(AASTORE));
    }

    private static void putObjectArray(InsnList body, int index, java.util.List<Object> values) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, values.size());
        body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        for (int i = 0; i < values.size(); i++) {
            body.add(new InsnNode(DUP));
            Virtualizer.pushInt(body, i);
            pushConstant(body, values.get(i));
            body.add(new InsnNode(AASTORE));
        }
        body.add(new InsnNode(AASTORE));
    }

    private static void pushConstant(InsnList body, Object value) {
        if (value == null) {
            body.add(new InsnNode(ACONST_NULL));
        } else if (value instanceof Integer integer) {
            Virtualizer.pushInt(body, integer);
            body.add(integerValueOf());
        } else if (value instanceof Long longValue) {
            body.add(new LdcInsnNode(longValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long",
                    "valueOf", "(J)Ljava/lang/Long;", false));
        } else if (value instanceof Float floatValue) {
            body.add(new LdcInsnNode(floatValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float",
                    "valueOf", "(F)Ljava/lang/Float;", false));
        } else if (value instanceof Double doubleValue) {
            body.add(new LdcInsnNode(doubleValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double",
                    "valueOf", "(D)Ljava/lang/Double;", false));
        } else if (value instanceof String string) {
            body.add(new LdcInsnNode(string));
        } else {
            throw new IllegalArgumentException("Unsupported VM constant: " + value);
        }
    }

    private static MethodInsnNode integerValueOf() {
        return new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false);
    }
}
