package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        String namePrefix = effectiveNamePrefix(options);

        if (options.renameClasses()) {
            for (ClassNode classNode : classes.values()) {
                if (canRenameClass(classNode, options)) {
                    classNames.put(classNode.name, nextClassName(namePrefix, random, usedClasses));
                }
            }
        }

        if (options.renameMembers()) {
            for (ClassNode classNode : classes.values()) {
                if (options.isExcluded(classNode.name)) {
                    if (options.minecraftMode() && isMixinLikeClass(classNode)) {
                        int methodIndex = 0;
                        Set<String> usedMethodKeys = new HashSet<>();
                        classNode.methods.forEach(method -> usedMethodKeys.add(method.name + method.desc));
                        for (MethodNode method : classNode.methods) {
                            if (canRenameMixinHandler(method)) {
                                String next = nextMemberName(random, usedMethodKeys, classNode.name,
                                        method.name, method.desc, methodIndex++, options);
                                methodNames.put(new MemberKey(classNode.name, method.name, method.desc), next);
                            }
                        }
                    }
                    continue;
                }
                boolean strongMemberRename = options.renamePublicMembers()
                        || options.minecraftMode() && classNames.containsKey(classNode.name);
                int methodIndex = 0;
                int fieldIndex = 0;
                Set<String> usedMethodKeys = new HashSet<>();
                classNode.methods.forEach(method -> usedMethodKeys.add(method.name + method.desc));
                Set<String> usedFieldNames = new HashSet<>();
                classNode.fields.forEach(field -> usedFieldNames.add(field.name));
                for (FieldNode field : classNode.fields) {
                    if (canRenameField(classNode, field, options, strongMemberRename)) {
                        String next = nextFieldName(random, usedFieldNames, classNode.name,
                                field.name, field.desc, fieldIndex++, options);
                        fieldNames.put(new MemberKey(classNode.name, field.name, field.desc), next);
                    }
                }
                for (MethodNode method : classNode.methods) {
                    if (canRenameMethod(classNode, method, options, strongMemberRename)) {
                        String next = nextMemberName(random, usedMethodKeys, classNode.name,
                                method.name, method.desc, methodIndex++, options);
                        methodNames.put(new MemberKey(classNode.name, method.name, method.desc), next);
                    }
                }
            }
        }

        String runtimeClassName = nextClassName(namePrefix, random, usedClasses);
        String nativeResourceName = nextNativeResourceName(namePrefix, random, options);
        return new NamingPlan(Map.copyOf(classNames), Map.copyOf(methodNames), Map.copyOf(fieldNames),
                namePrefix, runtimeClassName, nativeResourceName);
    }

    private static String effectiveNamePrefix(ObfuscationOptions options) {
        if (options.minecraftMode()) {
            return options.namePrefix();
        }
        return switch (options.mode()) {
            case JNIC, ZKM26, VMP, STACKED -> stealthPrefix(options.seed(), options.mode());
            default -> options.namePrefix();
        };
    }

    private static String stealthPrefix(long seed, ProtectionMode mode) {
        Random random = new Random(seed ^ 0x504B47535445414CL ^ mode.ordinal() * 0x9E3779B97F4A7C15L);
        return stealthPart(random, 8) + "/"
                + stealthPart(random, 10) + "/"
                + stealthPart(random, 6) + "/";
    }

    private static String stealthPart(Random random, int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder(length);
        builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        for (int i = 1; i < length; i++) {
            int value = mix(random.nextInt() ^ i * 0x45D9F3B);
            builder.append(chars.charAt(Math.floorMod(value, chars.length())));
        }
        return builder.toString();
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

    private static boolean canRenameField(ClassNode owner, FieldNode field, ObfuscationOptions options, boolean strongMemberRename) {
        if (field.name.startsWith("$") || field.name.startsWith("this$") || field.name.equals("serialVersionUID")) {
            return false;
        }
        if ((owner.access & ACC_ENUM) != 0 && (field.access & ACC_ENUM) != 0 && !strongMemberRename) {
            return false;
        }
        if (strongMemberRename) {
            return true;
        }
        return (field.access & ACC_PRIVATE) != 0;
    }

    private static boolean canRenameMethod(ClassNode owner, MethodNode method, ObfuscationOptions options, boolean strongMemberRename) {
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
        if (strongMemberRename && !isLikelyOverride(method)) {
            return true;
        }
        return (method.access & ACC_PRIVATE) != 0;
    }

    private static boolean isMixinLikeClass(ClassNode classNode) {
        String lowerName = classNode.name.toLowerCase(java.util.Locale.ROOT);
        return lowerName.contains("/mixin/")
                || lowerName.contains("/mixins/")
                || hasClassAnnotation(classNode.visibleAnnotations)
                || hasClassAnnotation(classNode.invisibleAnnotations);
    }

    private static boolean hasClassAnnotation(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                return true;
            }
        }
        return false;
    }

    private static boolean canRenameMixinHandler(MethodNode method) {
        if (method.name.equals("<init>") || method.name.equals("<clinit>") || (method.access & ACC_NATIVE) != 0) {
            return false;
        }
        return hasMixinInjectionAnnotation(method.visibleAnnotations)
                || hasMixinInjectionAnnotation(method.invisibleAnnotations);
    }

    private static boolean hasMixinInjectionAnnotation(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                    || annotation.desc.startsWith("Lcom/llamalad7/mixinextras/injector/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLikelyOverride(MethodNode method) {
        return switch (method.name) {
            case "toString", "hashCode", "equals", "compareTo", "iterator", "forEach", "spliterator",
                    "run", "call", "get", "accept", "apply", "test", "onEnable", "onDisable",
                    "toggle", "getName", "getDescription", "getCategory", "isEnabled" -> true;
            default -> false;
        };
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

    private static String nextNativeResourceName(String prefix, Random random, ObfuscationOptions options) {
        if (!options.minecraftMode() && switch (options.mode()) {
            case JNIC, VMP, STACKED -> true;
            default -> false;
        }) {
            return prefix + "r/"
                    + Long.toUnsignedString(random.nextLong(), 36)
                    + "/"
                    + Long.toUnsignedString(random.nextLong(), 36)
                    + ".bin";
        }
        return prefix + "native/windows-x64/N"
                + Long.toUnsignedString(random.nextLong(), 36)
                + Long.toUnsignedString(random.nextLong(), 36)
                + ".bin";
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static String nextMemberName(Random random, Set<String> usedMethodKeys,
                                         String owner, String name, String desc, int index,
                                         ObfuscationOptions options) {
        while (true) {
            String candidate = memberName(random, owner, name, desc, index, options);
            if (usedMethodKeys.add(candidate + desc)) {
                return candidate;
            }
            index++;
        }
    }

    private static String nextFieldName(Random random, Set<String> usedFieldNames,
                                        String owner, String name, String desc, int index,
                                        ObfuscationOptions options) {
        while (true) {
            String candidate = memberName(random, owner, name, desc, index, options);
            if (usedFieldNames.add(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private static String memberName(Random random, String owner, String name, String desc, int index,
                                     ObfuscationOptions options) {
        if (strongGeneratedMemberNames(options)) {
            int a = mix((int) random.nextLong()
                    ^ owner.hashCode()
                    ^ Integer.rotateLeft(name.hashCode(), 7)
                    ^ Integer.rotateLeft(desc.hashCode(), 13)
                    ^ index * 0x45D9F3B);
            int b = mix((int) (random.nextLong() >>> 32)
                    ^ Integer.rotateLeft(a, 11)
                    ^ owner.length() * 0x27D4EB2D
                    ^ index * 0x9E3779B9);
            return "_"
                    + Integer.toUnsignedString(a, 36)
                    + Integer.toUnsignedString(b, 36);
        }
        return shortMemberName(index);
    }

    private static boolean strongGeneratedMemberNames(ObfuscationOptions options) {
        return switch (options.mode()) {
            case JNIC, ZKM26, VMP, STACKED, MINECRAFT_MAX -> true;
            default -> false;
        };
    }

    private static String shortMemberName(int index) {
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
