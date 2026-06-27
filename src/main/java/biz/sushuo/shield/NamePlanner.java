package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

final class NamePlanner implements Opcodes {
    private NamePlanner() {
    }

    static NamingPlan plan(Map<String, ClassNode> classes, ObfuscationOptions options) {
        Random random = new Random(options.seed());
        Map<String, String> classNames = new TreeMap<>();
        Map<MemberKey, String> methodNames = new HashMap<>();
        Map<MemberKey, String> fieldNames = new HashMap<>();
        Set<String> usedClasses = new HashSet<>(classes.keySet());

        if (options.renameClasses()) {
            for (ClassNode classNode : classes.values()) {
                if (canRenameClass(classNode, options)) {
                    classNames.put(classNode.name, nextClassName(options.namePrefix(), random, usedClasses));
                }
            }
        }

        if (options.renameMembers()) {
            for (ClassNode classNode : classes.values()) {
                if (options.isExcluded(classNode.name)) {
                    continue;
                }
                int methodIndex = 0;
                int fieldIndex = 0;
                for (FieldNode field : classNode.fields) {
                    if (canRenameField(classNode, field, options)) {
                        fieldNames.put(new MemberKey(classNode.name, field.name, field.desc), memberName(fieldIndex++));
                    }
                }
                for (MethodNode method : classNode.methods) {
                    if (canRenameMethod(classNode, method, options)) {
                        methodNames.put(new MemberKey(classNode.name, method.name, method.desc), memberName(methodIndex++));
                    }
                }
            }
        }

        String runtimeClassName = nextClassName(options.namePrefix(), random, usedClasses);
        return new NamingPlan(Map.copyOf(classNames), Map.copyOf(methodNames), Map.copyOf(fieldNames), runtimeClassName);
    }

    private static boolean canRenameClass(ClassNode classNode, ObfuscationOptions options) {
        if (options.isExcluded(classNode.name)) {
            return false;
        }
        if (classNode.name.equals("module-info") || classNode.name.endsWith("/module-info")) {
            return false;
        }
        if (classNode.name.equals("package-info") || classNode.name.endsWith("/package-info")) {
            return false;
        }
        return (classNode.access & ACC_MODULE) == 0;
    }

    private static boolean canRenameField(ClassNode owner, FieldNode field, ObfuscationOptions options) {
        if (field.name.startsWith("$") || field.name.startsWith("this$") || field.name.equals("serialVersionUID")) {
            return false;
        }
        if ((owner.access & ACC_ENUM) != 0 && (field.access & ACC_ENUM) != 0) {
            return false;
        }
        if (options.renamePublicMembers()) {
            return true;
        }
        return (field.access & ACC_PRIVATE) != 0;
    }

    private static boolean canRenameMethod(ClassNode owner, MethodNode method, ObfuscationOptions options) {
        if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
            return false;
        }
        if (method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V")
                && (method.access & ACC_PUBLIC) != 0 && (method.access & ACC_STATIC) != 0) {
            return false;
        }
        if ((method.access & ACC_NATIVE) != 0) {
            return false;
        }
        if ((owner.access & ACC_ENUM) != 0 && (method.name.equals("values") || method.name.equals("valueOf"))) {
            return false;
        }
        if (options.renamePublicMembers()) {
            return true;
        }
        return (method.access & ACC_PRIVATE) != 0;
    }

    private static String nextClassName(String prefix, Random random, Set<String> usedClasses) {
        while (true) {
            String candidate = prefix + "C" + Long.toUnsignedString(random.nextLong(), 36)
                    + Long.toUnsignedString(random.nextLong(), 36);
            if (usedClasses.add(candidate)) {
                return candidate;
            }
        }
    }

    private static String memberName(int index) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder builder = new StringBuilder("_");
        int value = index;
        do {
            builder.append(alphabet.charAt(value % alphabet.length()));
            value /= alphabet.length();
        } while (value > 0);
        return builder.toString();
    }
}
