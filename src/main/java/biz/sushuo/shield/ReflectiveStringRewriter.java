package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

final class ReflectiveStringRewriter {
    private static final int SCAN_LIMIT = 96;

    private ReflectiveStringRewriter() {
    }

    static void rewrite(ClassNode classNode, NamingPlan namingPlan) {
        if (namingPlan.classNames().isEmpty()
                && namingPlan.fieldNames().isEmpty()
                && namingPlan.methodNames().isEmpty()) {
            return;
        }
        for (MethodNode method : classNode.methods) {
            rewriteClassNameStrings(method, namingPlan.classNames());
            rewriteReflectiveMemberNames(method, namingPlan);
        }
    }

    private static void rewriteClassNameStrings(MethodNode method, Map<String, String> classNames) {
        if (classNames.isEmpty()) {
            return;
        }
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String value) {
                String rewritten = rewriteClassString(value, classNames);
                if (rewritten != null) {
                    ldc.cst = rewritten;
                }
            }
        }
    }

    private static void rewriteReflectiveMemberNames(MethodNode method, NamingPlan namingPlan) {
        if (namingPlan.fieldNames().isEmpty() && namingPlan.methodNames().isEmpty()) {
            return;
        }
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !call.owner.equals("java/lang/Class")) {
                continue;
            }
            boolean fieldLookup = call.name.equals("getDeclaredField") || call.name.equals("getField");
            boolean methodLookup = call.name.equals("getDeclaredMethod") || call.name.equals("getMethod");
            if (!fieldLookup && !methodLookup) {
                continue;
            }

            LdcInsnNode memberName = previousString(instruction);
            if (memberName == null) {
                continue;
            }
            String owner = previousOwner(memberName, namingPlan.classNames());
            if (owner == null) {
                continue;
            }
            if (fieldLookup) {
                rewriteFieldName(owner, memberName, namingPlan.fieldNames());
            } else {
                rewriteMethodName(owner, memberName, namingPlan.methodNames());
            }
        }
    }

    private static LdcInsnNode previousString(AbstractInsnNode from) {
        int scanned = 0;
        for (AbstractInsnNode cursor = from.getPrevious(); cursor != null && scanned++ < SCAN_LIMIT; cursor = cursor.getPrevious()) {
            if (cursor instanceof LdcInsnNode ldc && ldc.cst instanceof String) {
                return ldc;
            }
        }
        return null;
    }

    private static String previousOwner(AbstractInsnNode from, Map<String, String> classNames) {
        int scanned = 0;
        for (AbstractInsnNode cursor = from.getPrevious(); cursor != null && scanned++ < SCAN_LIMIT; cursor = cursor.getPrevious()) {
            if (cursor instanceof LdcInsnNode ldc) {
                if (ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) {
                    return type.getInternalName();
                }
                if (ldc.cst instanceof String value) {
                    String owner = ownerFromClassString(value, classNames);
                    if (owner != null) {
                        return owner;
                    }
                }
            }
        }
        return null;
    }

    private static void rewriteFieldName(String owner, LdcInsnNode stringLiteral, Map<MemberKey, String> fieldNames) {
        String value = (String) stringLiteral.cst;
        for (Map.Entry<MemberKey, String> entry : fieldNames.entrySet()) {
            MemberKey key = entry.getKey();
            if (key.owner().equals(owner) && key.name().equals(value)) {
                stringLiteral.cst = entry.getValue();
                return;
            }
        }
    }

    private static void rewriteMethodName(String owner, LdcInsnNode stringLiteral, Map<MemberKey, String> methodNames) {
        String value = (String) stringLiteral.cst;
        for (Map.Entry<MemberKey, String> entry : methodNames.entrySet()) {
            MemberKey key = entry.getKey();
            if (key.owner().equals(owner) && key.name().equals(value)) {
                stringLiteral.cst = entry.getValue();
                return;
            }
        }
    }

    private static String rewriteClassString(String value, Map<String, String> classNames) {
        String direct = classNames.get(value.replace('.', '/'));
        if (direct != null) {
            return value.indexOf('/') >= 0 ? direct : direct.replace('/', '.');
        }
        if (value.startsWith("L") && value.endsWith(";")) {
            String mapped = classNames.get(value.substring(1, value.length() - 1));
            if (mapped != null) {
                return "L" + mapped + ";";
            }
        }
        if (value.startsWith("[L") && value.endsWith(";")) {
            String mapped = classNames.get(value.substring(2, value.length() - 1));
            if (mapped != null) {
                return "[L" + mapped + ";";
            }
        }
        return null;
    }

    private static String ownerFromClassString(String value, Map<String, String> classNames) {
        String normalized = value;
        if (normalized.startsWith("L") && normalized.endsWith(";")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        } else if (normalized.startsWith("[L") && normalized.endsWith(";")) {
            normalized = normalized.substring(2, normalized.length() - 1);
        }
        normalized = normalized.replace('.', '/');
        if (classNames.containsKey(normalized)) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : classNames.entrySet()) {
            if (entry.getValue().equals(normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
