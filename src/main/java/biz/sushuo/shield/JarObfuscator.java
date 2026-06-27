package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
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
        NamingPlan namingPlan = NamePlanner.plan(classes, options);
        ShieldRemapper remapper = new ShieldRemapper(namingPlan);
        ClassHierarchy hierarchy = ClassHierarchy.from(classes, namingPlan.classNames());
        TransformStats stats = new TransformStats();

        Map<String, byte[]> outputClasses = new TreeMap<>();
        for (ClassNode classNode : classes.values().stream().sorted(Comparator.comparing(node -> node.name)).toList()) {
            ClassTransformer.transform(classNode, options, namingPlan.runtimeClassName(), remapper, stats);

            ClassNode remapped = new ClassNode();
            classNode.accept(new ClassRemapper(remapped, remapper));

            ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, hierarchy);
            remapped.accept(writer);
            outputClasses.put(remapped.name + ".class", writer.toByteArray());
        }

        outputClasses.putAll(RuntimeClassGenerator.generateAll(namingPlan.runtimeClassName()));

        writeOutput(options, input.resources(), outputClasses, namingPlan);

        return new ObfuscationResult(
                classes.size(),
                namingPlan.classNames().size(),
                namingPlan.methodNames().size() + namingPlan.fieldNames().size(),
                stats.virtualizedMethods(),
                stats.virtualizedInstructions(),
                stats.encryptedStrings(),
                stats.obfuscatedNumbers(),
                stats.controlFlowGuards(),
                namingPlan.runtimeClassName()
        );
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
            ClassReader reader = new ClassReader(entry.getValue());
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);
            classes.put(classNode.name, classNode);
        }
        return classes;
    }

    private static void writeOutput(
            ObfuscationOptions options,
            List<JarResource> resources,
            Map<String, byte[]> outputClasses,
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

    private record JarResource(String name, byte[] bytes) {
    }
}
