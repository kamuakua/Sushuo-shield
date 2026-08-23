package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Moves ConstantValue attributes into bytecode so the normal literal passes can process them. */
final class StaticFieldInitializer implements Opcodes {
    private StaticFieldInitializer() {
    }

    static int move(ClassNode classNode) {
        if ((classNode.access & ACC_INTERFACE) != 0 || classNode.fields == null || classNode.fields.isEmpty()) {
            return 0;
        }

        InsnList initializers = new InsnList();
        int moved = 0;
        for (FieldNode field : classNode.fields) {
            if ((field.access & ACC_STATIC) == 0
                    || field.value == null) {
                continue;
            }
            if (!(field.value instanceof String
                    || field.value instanceof Integer
                    || field.value instanceof Long
                    || field.value instanceof Float
                    || field.value instanceof Double)) {
                continue;
            }
            initializers.add(new LdcInsnNode(field.value));
            initializers.add(new FieldInsnNode(PUTSTATIC, classNode.name, field.name, field.desc));
            field.value = null;
            moved++;
        }
        if (moved == 0) {
            return 0;
        }

        MethodNode clinit = null;
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("<clinit>") && method.desc.equals("()V")) {
                clinit = method;
                break;
            }
        }
        if (clinit == null) {
            clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(new org.objectweb.asm.tree.InsnNode(RETURN));
            classNode.methods.add(clinit);
        }
        clinit.instructions.insert(initializers);
        return moved;
    }
}
