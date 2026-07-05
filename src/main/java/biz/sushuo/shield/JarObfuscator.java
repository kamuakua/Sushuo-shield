package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

final class JarObfuscator {
    ObfuscationResult obfuscate(ObfuscationOptions options) throws IOException {
        InputJar input = readInput(options);
        Map<String, ClassNode> classes = parseClasses(input.classes());
        options = MinecraftProtector.applyMinecraftExcludes(options, classes, input.resources());
        NamingPlan namingPlan = NamePlanner.plan(classes, options);
        ShieldRemapper remapper = new ShieldRemapper(namingPlan);
        ClassHierarchy hierarchy = ClassHierarchy.from(classes, namingPlan.classNames());
        TransformStats stats = new TransformStats();

        Map<String, byte[]> outputClasses = new TreeMap<>();
        Map<String, byte[]> vmResources = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : input.classes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            TransformedClass transformed = transformClass(entry.getValue(), options, namingPlan,
                    remapper, classes.keySet(), hierarchy);
            stats.add(transformed.stats());
            outputClasses.put(transformed.name() + ".class", transformed.bytes());
            vmResources.putAll(transformed.vmResources());
        }

        outputClasses.putAll(RuntimeClassGenerator.generateAll(
                namingPlan.runtimeClassName(), namingPlan.nativeResourceName(), options.requireNativeVm()));
        Map<String, byte[]> nativeResources = RuntimeClassGenerator.nativeResources(namingPlan, options.seed());
        if (options.requireNativeVm() && nativeResources.isEmpty()) {
            throw new IOException("Native VM is required, but the packed Windows x64 native resource is missing. "
                    + "Run native/build-windows.ps1 or mvn package on Windows with a C compiler first.");
        }

        writeOutput(options, input.resources(), outputClasses, nativeResources, vmResources, namingPlan);

        return new ObfuscationResult(
                classes.size(),
                namingPlan.classNames().size(),
                namingPlan.methodNames().size() + namingPlan.fieldNames().size(),
                stats.virtualizedMethods(),
                stats.virtualizedInstructions(),
                stats.encryptedStrings(),
                stats.obfuscatedNumbers(),
                stats.controlFlowGuards(),
                stats.sizeFallbackClasses(),
                namingPlan.runtimeClassName()
        );
    }

    private static TransformedClass transformClass(
            byte[] originalBytes,
            ObfuscationOptions options,
            NamingPlan namingPlan,
            ShieldRemapper remapper,
            Set<String> projectClasses,
            ClassHierarchy hierarchy
    ) {
        List<ObfuscationOptions> attempts = List.of(
                options,
                options.withClassTransforms(options.encryptStrings(), options.obfuscateNumbers(), options.virtualize(), false),
                options.withClassTransforms(options.encryptStrings(), false, options.virtualize(), false),
                options.withClassTransforms(options.encryptStrings(), false, false, false),
                options.withClassTransforms(false, false, false, false)
        );
        RuntimeException lastFailure = null;
        for (ObfuscationOptions attempt : attempts) {
            try {
                TransformedClass transformed = transformClassAttempt(originalBytes, attempt, namingPlan, remapper, projectClasses, hierarchy);
                if (attempt != options) {
                    transformed.stats().addSizeFallbackClass();
                }
                return transformed;
            } catch (RuntimeException ex) {
                if (!isClassSizeFailure(ex)) {
                    throw ex;
                }
                lastFailure = ex;
            }
        }
        throw lastFailure == null ? new IllegalStateException("Class transform failed") : lastFailure;
    }

    private static TransformedClass transformClassAttempt(
            byte[] originalBytes,
            ObfuscationOptions options,
            NamingPlan namingPlan,
            ShieldRemapper remapper,
            Set<String> projectClasses,
            ClassHierarchy hierarchy
    ) {
        ClassNode classNode = readClass(originalBytes);
        TransformStats stats = new TransformStats();
        VmPayloadResources vmPayloadResources = new VmPayloadResources(namingPlan, options.seed());
        ReflectiveStringRewriter.rewrite(classNode, namingPlan);
        ClassTransformer.transform(classNode, options, namingPlan.runtimeClassName(), remapper,
                projectClasses, vmPayloadResources, stats);

        ClassNode remapped = new ClassNode();
        classNode.accept(new ClassRemapper(remapped, remapper));

        ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, hierarchy);
        remapped.accept(writer);
        return new TransformedClass(remapped.name, writer.toByteArray(), stats,
                Map.copyOf(vmPayloadResources.resources()));
    }

    private static boolean isClassSizeFailure(RuntimeException ex) {
        return ex instanceof org.objectweb.asm.ClassTooLargeException
                || ex instanceof MethodTooLargeException
                || ex instanceof IllegalArgumentException
                && ex.getMessage() != null
                && ex.getMessage().contains("UTF8 string too large");
    }

    private static InputJar readInput(ObfuscationOptions options) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        List<JarResource> resources = new ArrayList<>();
        try (JarFile jar = new JarFile(options.input().toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes = jar.getInputStream(entry).readAllBytes();
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    classes.put(name, bytes);
                } else if (!isSignatureOrIndexFile(name)) {
                    resources.add(new JarResource(name, bytes));
                }
            }
        }
        return new InputJar(classes, resources);
    }

    private static Map<String, ClassNode> parseClasses(Map<String, byte[]> classEntries) {
        Map<String, ClassNode> classes = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : classEntries.entrySet()) {
            ClassNode classNode = readClass(entry.getValue());
            classes.put(classNode.name, classNode);
        }
        return classes;
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }

    private static void writeOutput(
            ObfuscationOptions options,
            List<JarResource> resources,
            Map<String, byte[]> outputClasses,
            Map<String, byte[]> nativeResources,
            Map<String, byte[]> vmResources,
            NamingPlan namingPlan
    ) throws IOException {
        if (options.output().getParent() != null) {
            Files.createDirectories(options.output().getParent());
        }

        Set<String> written = new TreeSet<>();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(options.output()))) {
            for (JarResource resource : resources) {
                String name = ResourceRewriter.rewriteName(resource.name(), namingPlan.classNames());
                byte[] bytes = ResourceRewriter.rewriteBytes(name, resource.bytes(), namingPlan.classNames(), options);
                writeEntry(output, written, name, bytes);
            }

            for (Map.Entry<String, byte[]> classEntry : outputClasses.entrySet()) {
                writeEntry(output, written, classEntry.getKey(), classEntry.getValue());
            }
            for (Map.Entry<String, byte[]> nativeEntry : nativeResources.entrySet()) {
                writeEntry(output, written, nativeEntry.getKey(), nativeEntry.getValue());
            }
            for (Map.Entry<String, byte[]> vmEntry : vmResources.entrySet()) {
                writeEntry(output, written, vmEntry.getKey(), vmEntry.getValue());
            }
        }
    }

    private static void writeEntry(JarOutputStream output, Set<String> written, String name, byte[] bytes) throws IOException {
        if (!written.add(name)) {
            return;
        }
        JarEntry entry = new JarEntry(name);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static boolean isSignatureOrIndexFile(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) {
            return false;
        }
        return upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC")
                || upper.equals("META-INF/INDEX.LIST")
                || upper.startsWith("META-INF/SIG-");
    }

    private record InputJar(Map<String, byte[]> classes, List<JarResource> resources) {
    }

    private record TransformedClass(String name, byte[] bytes, TransformStats stats, Map<String, byte[]> vmResources) {
    }

    record JarResource(String name, byte[] bytes) {
    }
}
