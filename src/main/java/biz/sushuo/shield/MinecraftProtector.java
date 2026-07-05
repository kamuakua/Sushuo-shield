package biz.sushuo.shield;

import org.objectweb.asm.Type;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftProtector {
    private static final Pattern JSON_STRING = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern TOML_VALUE = Pattern.compile("=\\s*\"([^\"]+)\"");

    private MinecraftProtector() {
    }

    static ObfuscationOptions applyMinecraftExcludes(
            ObfuscationOptions options,
            Map<String, ClassNode> classes,
            List<JarObfuscator.JarResource> resources
    ) {
        if (!options.minecraftMode()) {
            return options;
        }

        Set<String> excludes = new LinkedHashSet<>();
        addCommonPackageExcludes(excludes);
        addResourceDrivenExcludes(excludes, resources);
        addClassDrivenExcludes(excludes, classes);
        return options.withAdditionalExcludes(List.copyOf(excludes));
    }

    static boolean preserveResourceText(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("fabric.mod.json")
                || lower.equals("quilt.mod.json")
                || lower.equals("meta-inf/mods.toml")
                || lower.equals("meta-inf/neoforge.mods.toml")
                || lower.endsWith(".mixins.json")
                || lower.endsWith(".mixin.json")
                || lower.endsWith(".refmap.json")
                || lower.endsWith(".accesswidener")
                || lower.equals("meta-inf/accesstransformer.cfg");
    }

    private static void addCommonPackageExcludes(Set<String> excludes) {
        excludes.add("net.minecraft.**");
        excludes.add("com.mojang.**");
        excludes.add("org.spongepowered.**");
        excludes.add("net.fabricmc.**");
        excludes.add("net.minecraftforge.**");
        excludes.add("net.neoforged.**");
        excludes.add("cpw.mods.**");
        excludes.add("mezz.jei.**");
    }

    private static void addResourceDrivenExcludes(Set<String> excludes, List<JarObfuscator.JarResource> resources) {
        Set<String> mixinConfigs = new LinkedHashSet<>();
        for (JarObfuscator.JarResource resource : resources) {
            String name = resource.name();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("fabric.mod.json") || lower.equals("quilt.mod.json")) {
                String text = text(resource);
                addJsonClassStrings(excludes, text);
                addMixinConfigs(mixinConfigs, text);
            } else if (lower.equals("meta-inf/mods.toml") || lower.equals("meta-inf/neoforge.mods.toml")) {
                addTomlClassStrings(excludes, text(resource));
            } else if (lower.endsWith(".mixins.json") || lower.endsWith(".mixin.json")) {
                addMixinConfigExcludes(excludes, text(resource));
            }
        }

        for (JarObfuscator.JarResource resource : resources) {
            if (mixinConfigs.contains(resource.name())) {
                addMixinConfigExcludes(excludes, text(resource));
            }
        }
    }

    private static void addClassDrivenExcludes(Set<String> excludes, Map<String, ClassNode> classes) {
        for (ClassNode classNode : classes.values()) {
            if (isMinecraftSensitiveClass(classNode)) {
                excludes.add(classNode.name);
                excludes.add(classNode.name.replace('/', '.'));
            }
        }
    }

    private static boolean isMinecraftSensitiveClass(ClassNode classNode) {
        String lowerName = classNode.name.toLowerCase(Locale.ROOT);
        if ((classNode.access & (Opcodes.ACC_ANNOTATION | Opcodes.ACC_INTERFACE)) != 0) {
            return true;
        }
        if (lowerName.contains("/mixin/") || lowerName.contains("/mixins/")
                || lowerName.endsWith("mixin") || lowerName.endsWith("accessor") || lowerName.endsWith("invoker")) {
            return true;
        }
        return hasAnnotation(classNode.visibleAnnotations)
                || hasAnnotation(classNode.invisibleAnnotations)
                || implementsType(classNode, "net/fabricmc/api/ModInitializer")
                || implementsType(classNode, "net/fabricmc/api/ClientModInitializer")
                || implementsType(classNode, "net/fabricmc/api/DedicatedServerModInitializer")
                || implementsType(classNode, "org/quiltmc/loader/api/ModInitializer");
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            String desc = annotation.desc;
            if (desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")
                    || desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;")
                    || desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;")
                    || desc.equals("Lnet/minecraftforge/fml/common/Mod;")
                    || desc.equals("Lnet/neoforged/fml/common/Mod;")
                    || desc.equals("Lcpw/mods/fml/common/Mod;")
                    || desc.equals("Lcn/paradisemc/ZKMIndy;")) {
                return true;
            }
        }
        return false;
    }

    private static boolean implementsType(ClassNode classNode, String internalName) {
        return classNode.interfaces != null && classNode.interfaces.contains(internalName);
    }

    private static void addJsonClassStrings(Set<String> excludes, String text) {
        for (String value : jsonStrings(text)) {
            if (looksLikeClassName(value)) {
                addClassExclude(excludes, value);
            }
        }
    }

    private static void addMixinConfigs(Set<String> mixinConfigs, String text) {
        for (String value : jsonStrings(text)) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".mixins.json") || lower.endsWith(".mixin.json")) {
                mixinConfigs.add(value);
            }
        }
    }

    private static void addMixinConfigExcludes(Set<String> excludes, String text) {
        List<String> strings = jsonStrings(text);
        List<String> packages = new ArrayList<>();
        for (int i = 0; i < strings.size() - 1; i++) {
            if (strings.get(i).equals("package")) {
                packages.add(strings.get(i + 1));
            }
        }
        for (String value : strings) {
            if (looksLikeClassName(value)) {
                addClassExclude(excludes, value);
            } else if (looksLikeSimpleClassName(value)) {
                for (String packageName : packages) {
                    addClassExclude(excludes, packageName + "." + value);
                }
            }
        }
        for (String packageName : packages) {
            addClassExclude(excludes, packageName + ".**");
        }
    }

    private static void addTomlClassStrings(Set<String> excludes, String text) {
        Matcher matcher = TOML_VALUE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (looksLikeClassName(value)) {
                addClassExclude(excludes, value);
            }
        }
    }

    private static List<String> jsonStrings(String text) {
        List<String> values = new ArrayList<>();
        Matcher matcher = JSON_STRING.matcher(text);
        while (matcher.find()) {
            values.add(unescapeJsonString(matcher.group(1)));
        }
        return values;
    }

    private static String unescapeJsonString(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static boolean looksLikeClassName(String value) {
        if (!value.contains(".") && !value.contains("/")) {
            return false;
        }
        String normalized = value.replace('/', '.');
        if (normalized.endsWith(".json") || normalized.endsWith(".toml") || normalized.endsWith(".cfg")) {
            return false;
        }
        for (String part : normalized.split("\\.")) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return false;
            }
            for (int i = 1; i < part.length(); i++) {
                if (!Character.isJavaIdentifierPart(part.charAt(i)) && part.charAt(i) != '$') {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean looksLikeSimpleClassName(String value) {
        if (value.isEmpty() || value.contains(".") || value.contains("/") || value.endsWith(".json")) {
            return false;
        }
        char first = value.charAt(0);
        if (!Character.isUpperCase(first) && first != '_') {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isJavaIdentifierPart(ch) && ch != '$') {
                return false;
            }
        }
        return true;
    }

    private static void addClassExclude(Set<String> excludes, String className) {
        String normalized = className;
        if (normalized.startsWith("L") && normalized.endsWith(";")) {
            normalized = Type.getType(normalized).getClassName();
        }
        normalized = normalized.replace('/', '.');
        excludes.add(normalized);
        excludes.add(normalized.replace('.', '/'));
    }

    private static String text(JarObfuscator.JarResource resource) {
        return new String(resource.bytes(), StandardCharsets.UTF_8);
    }
}
