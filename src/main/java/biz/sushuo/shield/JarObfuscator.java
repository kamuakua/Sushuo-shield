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
import java.util.Random;
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
        TransformStats stats = new TransformStats();
        if (options.methodParameterObfuscation()) {
            Map<String, ClassNode> parameterClasses = parseClasses(input.classes());
            int changed = MethodParameterObfuscator.applyAll(parameterClasses, options.seed(),
                    parameterExcludedClasses(options, parameterClasses));
            if (changed > 0) {
                try {
                    ClassHierarchy originalHierarchy = ClassHierarchy.from(parameterClasses, Map.of());
                    input = new InputJar(writeClassNodes(parameterClasses, originalHierarchy), input.resources());
                    classes = parameterClasses;
                    stats.addParameterObfuscatedMethods(changed);
                } catch (RuntimeException ex) {
                    if (isClassSizeFailure(ex)) {
                        stats.addSizeFallbackClass();
                        classes = parseClasses(input.classes());
                    } else {
                        throw ex;
                    }
                }
            }
        }
        NamingPlan namingPlan = NamePlanner.plan(classes, options);
        ShieldRemapper remapper = new ShieldRemapper(namingPlan);
        ClassHierarchy hierarchy = ClassHierarchy.from(classes, namingPlan.classNames());
        Map<RuntimeApiObfuscator.MemberSig, String> runtimeApiNames = RuntimeApiObfuscator.plan(
                namingPlan.runtimeClassName(), options.seed(), true);

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

        Map<String, byte[]> nativeResources = shouldPackageNative(options)
                ? RuntimeClassGenerator.nativeResources(namingPlan, options.seed())
                : Map.of();
        if (options.requireNativeVm() && nativeResources.isEmpty()) {
            throw new IOException("Native VM is required, but the packed Windows x64 native resource is missing. "
                    + "Run native/build-windows.ps1 or mvn package on Windows with a C compiler first.");
        }
        Map<String, byte[]> antiDeobfuscationResources = antiDeobfuscationResources(options, namingPlan, vmResources.keySet());
        vmResources.putAll(antiDeobfuscationResources);
        stats.addAntiDeobfuscationArtifacts(antiDeobfuscationResources.size());
        WatermarkResources.Result watermark = WatermarkResources.create(options, namingPlan,
                outputClasses, nativeResources, vmResources);
        vmResources.putAll(watermark.resources());
        stats.addAntiDeobfuscationArtifacts(watermark.resources().size());

        outputClasses.putAll(RuntimeClassGenerator.generateAll(
                namingPlan.runtimeClassName(), namingPlan.nativeResourceName(), options.requireNativeVm(),
                options.antiDebug(), options.antiVm(), options.licenseHash(),
                watermark.resourceName().isEmpty() ? "" : "/" + watermark.resourceName(),
                watermark.integrityHash(), runtimeApiNames));

        outputClasses = RuntimeApiObfuscator.rewriteClasses(outputClasses, namingPlan.runtimeClassName(), runtimeApiNames);

        int encryptedResources = writeOutput(options, input.resources(), outputClasses, nativeResources, vmResources, namingPlan);

        ObfuscationResult result = new ObfuscationResult(
                classes.size(),
                namingPlan.classNames().size(),
                namingPlan.methodNames().size() + namingPlan.fieldNames().size(),
                stats.virtualizedMethods(),
                stats.virtualizedInstructions(),
                stats.encryptedStrings(),
                stats.obfuscatedNumbers(),
                stats.controlFlowGuards(),
                stats.referenceObfuscatedCalls(),
                stats.scrambledLineNumbers(),
                stats.antiDeobfuscationArtifacts(),
                stats.parameterObfuscatedMethods(),
                stats.sizeFallbackClasses(),
                encryptedResources,
                namingPlan.runtimeClassName()
        );
        ProtectionReportWriter.write(options, result, namingPlan, watermark);
        return result;
    }

    private static boolean shouldPackageNative(ObfuscationOptions options) {
        return switch (options.mode()) {
            case COMPAT, ZKM26 -> options.requireNativeVm();
            default -> true;
        };
    }

    private static Map<String, byte[]> antiDeobfuscationResources(ObfuscationOptions options, NamingPlan namingPlan,
                                                                  Set<String> usedNames) {
        if (!options.antiAiDeobfuscation()) {
            return Map.of();
        }
        Random random = new Random(options.seed() ^ 0x41B5D03F9E3779B9L);
        Map<String, byte[]> resources = new TreeMap<>();
        int count = switch (options.mode()) {
            case ZKM26 -> 3;
            case JNIC, VMP, STACKED, MINECRAFT_MAX -> 8;
            default -> 4;
        };
        for (int i = 0; i < count; i++) {
            String name;
            do {
                name = namingPlan.namePrefix() + "data/R"
                        + Long.toUnsignedString(random.nextLong(), 36)
                        + Long.toUnsignedString(random.nextLong(), 36)
                        + ".bin";
            } while (usedNames.contains(name) || resources.containsKey(name));
            resources.put(name, decoyPayload(random, i));
        }
        String mapName = uniqueAntiResourceName(options, namingPlan, usedNames, resources, random, "m/A", ".bin");
        resources.put(mapName, sealedAnalysisRecord(options, namingPlan, random, 0));
        String traceName = uniqueAntiResourceName(options, namingPlan, usedNames, resources, random, "d/T", ".bin");
        resources.put(traceName, sealedAnalysisRecord(options, namingPlan, random, 1));
        return resources;
    }

    private static String uniqueAntiResourceName(ObfuscationOptions options, NamingPlan namingPlan, Set<String> usedNames,
                                                 Map<String, byte[]> resources, Random random,
                                                 String infix, String suffix) {
        String name;
        do {
            name = namingPlan.namePrefix() + infix
                    + Long.toUnsignedString(random.nextLong(), 36)
                    + Long.toUnsignedString(random.nextLong(), 36)
                    + suffix;
        } while (usedNames.contains(name) || resources.containsKey(name));
        return name;
    }

    private static byte[] sealedAnalysisRecord(ObfuscationOptions options, NamingPlan namingPlan,
                                               Random random, int variant) {
        int length = 192 + random.nextInt(320);
        int context = mix(options.mode().ordinal()
                ^ Integer.rotateLeft(namingPlan.runtimeClassName().hashCode(), 5)
                ^ Integer.rotateLeft(namingPlan.namePrefix().hashCode(), 11)
                ^ variant * 0x45D9F3B
                ^ random.nextInt());
        return sealedNoise(random, context, length);
    }

    private static byte[] decoyPayload(Random random, int index) {
        int length = 64 + random.nextInt(192);
        return sealedNoise(random, mix(index * 0x45D9F3B ^ random.nextInt()), length);
    }

    private static byte[] sealedNoise(Random random, int context, int length) {
        byte[] data = new byte[length];
        int state = mix(context ^ length ^ 0x6D2B79F5);
        int rolling = mix(context ^ 0x165667B1);
        for (int i = 0; i < data.length; i++) {
            state = mix(state ^ random.nextInt() ^ i * 0x9E3779B9);
            rolling = Integer.rotateLeft(rolling + state + i * 0x27D4EB2D, 7);
            data[i] = (byte) ((state >>> ((i & 3) * 8)) ^ (rolling >>> 19));
        }
        return data;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
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
                options.withClassTransforms(false, false, false, false),
                options.withClassTransforms(false, false, false, false).withoutExtraProtectionPasses()
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

    private static Set<String> parameterExcludedClasses(
            ObfuscationOptions options,
            Map<String, ClassNode> originalClasses
    ) {
        Set<String> excluded = new TreeSet<>();
        for (ClassNode classNode : originalClasses.values()) {
            if (options.isExcluded(classNode.name) || options.sdkMarkers() && SDKMarkerSupport.noProtect(classNode)) {
                excluded.add(classNode.name);
            }
        }
        return excluded;
    }

    private static Map<String, byte[]> writeClassNodes(Map<String, ClassNode> classNodes, ClassHierarchy hierarchy) {
        Map<String, byte[]> output = new TreeMap<>();
        for (ClassNode classNode : classNodes.values()) {
            ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, hierarchy);
            classNode.accept(writer);
            output.put(classNode.name + ".class", writer.toByteArray());
        }
        return output;
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

    private static int writeOutput(
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
        int encryptedResources = 0;
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(options.output()))) {
            for (JarResource resource : resources) {
                String name = ResourceRewriter.rewriteName(resource.name(), namingPlan.classNames());
                byte[] bytes = ResourceRewriter.rewriteBytes(name, resource.bytes(), namingPlan.classNames(), options);
                if (!written.contains(name)
                        && ResourceEncryptor.shouldEncrypt(name, bytes, options)) {
                    bytes = ResourceEncryptor.encrypt(name, bytes,
                            namingPlan.runtimeClassName().replace('/', '.'), options.seed());
                    encryptedResources++;
                }
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
        return encryptedResources;
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
