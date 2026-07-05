package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

final class ReflectiveStringRewriter {
    private ReflectiveStringRewriter() {
    }

    static void rewrite(ClassNode classNode, NamingPlan namingPlan) {
        if (namingPlan.fieldNames().isEmpty() && namingPlan.methodNames().isEmpty()) {
            return;
        }
        for (MethodNode method : classNode.methods) {
            rewrite(method, namingPlan);
        }
    }

    private static void rewrite(MethodNode method, NamingPlan namingPlan) {
        Deque<TypeLiteral> classLiterals = new ArrayDeque<>();
        Deque<StringLiteral> stringLiterals = new ArrayDeque<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof LdcInsnNode ldc) {
                if (ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) {
                    classLiterals.push(new TypeLiteral(type.getInternalName()));
                    continue;
                }
                if (ldc.cst instanceof String value) {
                    stringLiterals.push(new StringLiteral(ldc, value));
                    continue;
                }
            }

            if (instruction instanceof MethodInsnNode call) {
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals("java/lang/Class")) {
                    if (call.name.equals("getDeclaredField") || call.name.equals("getField")) {
                        rewriteFieldName(classLiterals.peek(), stringLiterals.peek(), namingPlan.fieldNames());
                    } else if (call.name.equals("getDeclaredMethod") || call.name.equals("getMethod")) {
                        rewriteMethodName(classLiterals.peek(), stringLiterals.peek(), namingPlan.methodNames());
                    }
                    continue;
                }
            }

            if (instruction.getOpcode() >= 0) {
                classLiterals.clear();
                stringLiterals.clear();
            }
        }
    }

    private static void rewriteFieldName(TypeLiteral typeLiteral, StringLiteral stringLiteral, Map<MemberKey, String> fieldNames) {
        if (typeLiteral == null || stringLiteral == null) {
            return;
        }
        for (Map.Entry<MemberKey, String> entry : fieldNames.entrySet()) {
            MemberKey key = entry.getKey();
            if (key.owner().equals(typeLiteral.internalName()) && key.name().equals(stringLiteral.value())) {
                stringLiteral.node().cst = entry.getValue();
                return;
            }
        }
    }

    private static void rewriteMethodName(TypeLiteral typeLiteral, StringLiteral stringLiteral, Map<MemberKey, String> methodNames) {
        if (typeLiteral == null || stringLiteral == null) {
            return;
        }
        for (Map.Entry<MemberKey, String> entry : methodNames.entrySet()) {
            MemberKey key = entry.getKey();
            if (key.owner().equals(typeLiteral.internalName()) && key.name().equals(stringLiteral.value())) {
                stringLiteral.node().cst = entry.getValue();
                return;
            }
        }
    }

    private record TypeLiteral(String internalName) {
    }

    private record StringLiteral(LdcInsnNode node, String value) {
    }
}
