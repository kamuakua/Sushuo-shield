package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

final class JarObfuscatorTest {
    private static final String RUNTIME_VM_ENTRY_DESCRIPTOR =
            "([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";

    @Test
    void obfuscatesAndRunsVirtualizedJar() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-test");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path source = sourceRoot.resolve("SmokeApp.java");
        Files.writeString(source, """
                package demo;

                public final class SmokeApp {
                    public static void main(String[] args) {
                        System.out.println(add(7, 5));
                        System.out.println(mix(9L, 4L));
                        System.out.println(callAdd(3, 8));
                    }

                    static int add(int a, int b) {
                        int x = 17;
                        return (a + b) * x ^ 0x55AA;
                    }

                    static long mix(long a, long b) {
                        return (a * 31L) - (b ^ 13L);
                    }

                    static int callAdd(int a, int b) {
                        return add(a, b) - 1;
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.SmokeApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(12345L)
                .build());

        assertTrue(result.virtualizedMethods() >= 3);
        assertTrue(result.runtimeClassName().startsWith("sushuo1337/sushuoprotect/lib/"));
        Set<String> bootstrapOwners = bootstrapOwners(output);
        assertFalse(bootstrapOwners.contains(result.runtimeClassName()),
                "main runtime remains a direct invokedynamic bootstrap owner: " + bootstrapOwners);
        assertEquals(0, publicBootstrapMethods(output, result.runtimeClassName()),
                "canonical bootstrap methods remain public on the main runtime");
        List<String> directRuntimeBootstrapCalls = directRuntimeBootstrapCallSites(output, result.runtimeClassName());
        assertTrue(directRuntimeBootstrapCalls.isEmpty(),
                "bootstrap shards still directly forward into the main runtime: " + directRuntimeBootstrapCalls);
        ClassNode bootstrapDispatch = readClassNode(output,
                RuntimeClassGenerator.bootstrapDispatchClassName(result.runtimeClassName()) + ".class");
        assertFalse((bootstrapDispatch.access & ACC_PUBLIC) != 0,
                "bootstrap dispatch helper must remain package-private");
        assertTrue(bootstrapOwners.size() >= 2,
                "expected invokedynamic bootstrap calls to be distributed: " + bootstrapOwners);
        Map<String, Integer> constantBootstrapDescriptors = constantBootstrapDescriptors(output);
        assertTrue(constantBootstrapDescriptors.size() >= 4,
                "expected diversified constant bootstrap descriptors: " + constantBootstrapDescriptors);
        int constantBootstrapSites = constantBootstrapDescriptors.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int dominantConstantBootstrapSites = constantBootstrapDescriptors.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        assertTrue(constantBootstrapSites > 0
                        && (double) dominantConstantBootstrapSites / constantBootstrapSites <= 0.55d,
                "constant bootstrap descriptor remains dominant: " + constantBootstrapDescriptors);
        List<String> plainVmEntryCalls = directRuntimeVmEntryCallSites(output, result.runtimeClassName());
        assertTrue(plainVmEntryCalls.isEmpty(),
                "plain runtime _v VM entry call sites leaked: " + plainVmEntryCalls);
        String listing = listJar(output);
        assertFalse(listing.contains("NativeBridge.class"));
        assertTrue(listing.matches("(?s).*sushuo1337/sushuoprotect/lib/B[0-9a-z]+\\.class.*"));
        assertTrue(listing.contains("sushuo1337/sushuoprotect/lib/"));
        String nativeEntry = nativeEntryName(output);
        assertTrue(nativeEntry.startsWith("sushuo1337/sushuoprotect/lib/"));
        assertTrue(nativeEntry.matches(".*\\.(bin|dat|res|pak|idx|cfg)"));
        assertFalse(nativeEntry.contains("native/" + "windows-x64/"));
        assertFalse(listing.contains("sushuo1337_vm.dll"));
        assertFalse(listing.contains("sushuo1337_vm.dll.dat"));
        byte[] nativeBytes = readJarBytes(output, nativeEntry);
        assertFalse(nativeBytes[0] == 'M' && nativeBytes[1] == 'Z');
        assertFalse(nativeBytes.length >= 4
                && nativeBytes[0] == 'S'
                && nativeBytes[1] == 'S'
                && nativeBytes[2] == 'N'
                && nativeBytes[3] == '2');
        assertFalse(listing.contains("dev/"));

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("21862\n270\n21776\n", stdout);
    }

    @Test
    void vmResourcePathsAreDistributedForRealAndDecoys() {
        NamingPlan namingPlan = new NamingPlan(Map.of(), Map.of(), Map.of(),
                "probe/runtime/", "probe/runtime/C1", "probe/runtime/assets/native.bin");
        VmPayloadResources realResources = new VmPayloadResources(namingPlan, 0x5EED1234L);
        java.util.Set<String> realNames = new java.util.HashSet<>();
        for (int i = 0; i < 18; i++) {
            String runtimeName = realResources.add(sampleProgram(i), "demo/Owner" + i, "m" + i, "(I)I");
            realNames.add(runtimeName.substring(1));
        }

        ObfuscationOptions options = ObfuscationOptions.builder()
                .input(Path.of("in.jar"))
                .output(Path.of("out.jar"))
                .seed(0x5EED1234L)
                .mode(ProtectionMode.STACKED)
                .antiAiDeobfuscation(true)
                .build();
        Map<String, byte[]> decoys = AntiDeobfuscationNoise.resources(options, namingPlan, realNames);

        assertTrue(decoys.size() >= 6);
        assertDistributedVmResourceNames(realNames, namingPlan.runtimeClassName());
        assertDistributedVmResourceNames(decoys.keySet(), namingPlan.runtimeClassName());
    }

    @Test
    void controlFlowFlattensSimpleLinearArithmeticMethod() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-cfg");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path source = sourceRoot.resolve("FlowApp.java");
        Files.writeString(source, """
                package demo;

                public final class FlowApp {
                    public static void main(String[] args) {
                        System.out.println(linear(7, 5));
                    }

                    static int linear(int a, int b) {
                        int x = a + b;
                        int y = x * 3;
                        int z = y ^ 0x55AA;
                        return z - 17;
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.FlowApp");
        Path output = temp.resolve("output.jar");

        new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(0xC0FFEE12L)
                .renameClasses(false)
                .renameMembers(false)
                .encryptStrings(false)
                .obfuscateNumbers(false)
                .virtualize(false)
                .controlFlow(true)
                .referenceObfuscation(false)
                .antiAiDeobfuscation(false)
                .requireNativeVm(false)
                .build());

        ClassNode flowApp = readClassNode(output, "demo/FlowApp.class");
        MethodNode linear = findMethod(flowApp, "linear", "(II)I");
        assertTrue(containsLookupSwitch(linear), "linear method should be switch-flattened");

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("21885\n", stdout);
    }

    @Test
    void virtualizesAdditionalNumericOpcodes() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-opcodes");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path source = sourceRoot.resolve("OpcodeApp.java");
        Files.writeString(source, """
                package demo;

                public final class OpcodeApp {
                    public static void main(String[] args) {
                        System.out.println(shifts(0x12345678, 3));
                        System.out.println(loop(5));
                        System.out.println(bump(5));
                        System.out.println(compare(100L, 42L));
                        System.out.println(narrow(0x1234));
                    }

                    static int shifts(int value, int amount) {
                        return (value << amount) ^ (value >> 2) ^ (value >>> 3);
                    }

                    static int loop(int count) {
                        int total = 0;
                        for (int i = 0; i < count; i++) {
                            total += i;
                        }
                        return total;
                    }

                    static int bump(int value) {
                        int local = value;
                        local++;
                        return local;
                    }

                    static int compare(long left, long right) {
                        return Long.compare(left, right);
                    }

                    static int narrow(int value) {
                        byte b = (byte) value;
                        char c = (char) value;
                        short s = (short) value;
                        return b + c + s;
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.OpcodeApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(24680L)
                .renameClasses(false)
                .renameMembers(false)
                .encryptStrings(false)
                .obfuscateNumbers(false)
                .controlFlow(false)
                .build());

        assertTrue(result.virtualizedMethods() >= 3);

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("-1754714991\n10\n6\n1\n9372\n", stdout);
    }

    @Test
    void encryptsInvokedynamicStringConcatRecipes() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-string-concat");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path source = sourceRoot.resolve("ConcatApp.java");
        Files.writeString(source, """
                package demo;

                public final class ConcatApp {
                    public static void main(String[] args) {
                        System.out.println(debug("combat", 7, 'V', true));
                    }

                    static String debug(String mode, int ticks, char marker, boolean enabled) {
                        return "Grimvelo Debug: " + mode
                                + " Delay velocity: " + ticks
                                + " marker=" + marker
                                + " enabled=" + enabled;
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.ConcatApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(556677L)
                .renameClasses(false)
                .renameMembers(false)
                .obfuscateNumbers(false)
                .virtualize(false)
                .controlFlow(false)
                .build());

        assertTrue(result.encryptedStrings() >= 1);
        assertFalse(jarClassBytesContain(output, "Grimvelo Debug:"));
        assertFalse(jarClassBytesContain(output, "Delay velocity:"));

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("Grimvelo Debug: combat Delay velocity: 7 marker=V enabled=true\n", stdout);
    }

    @Test
    void movesStaticConstantValuesIntoEncryptedClassInitialization() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-static-fields");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path source = sourceRoot.resolve("StaticConstantsApp.java");
        Files.writeString(source, """
                package demo;

                public final class StaticConstantsApp {
                    public static final String SECRET = "static-secret-value";
                    public static final int MAGIC = 0x5A17;
                    public static final long LONG_MAGIC = 0x1122334455667788L;

                    public static void main(String[] args) throws Exception {
                        java.lang.reflect.Field secret = StaticConstantsApp.class.getDeclaredField("SECRET");
                        java.lang.reflect.Field magic = StaticConstantsApp.class.getDeclaredField("MAGIC");
                        java.lang.reflect.Field longMagic = StaticConstantsApp.class.getDeclaredField("LONG_MAGIC");
                        System.out.println(secret.get(null));
                        System.out.println(magic.getInt(null));
                        System.out.println(longMagic.getLong(null));
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.StaticConstantsApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(13579L)
                .renameClasses(false)
                .renameMembers(false)
                .virtualize(false)
                .controlFlow(false)
                .build());

        assertTrue(result.encryptedStrings() >= 1);
        assertTrue(result.obfuscatedNumbers() >= 2);
        assertFalse(jarClassBytesContain(output, "static-secret-value"));

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("static-secret-value\n23063\n1234605616436508552\n", stdout);
    }

    @Test
    void phantomJvmWrappersPreserveProjectCallsAndFields() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-phantom-jvm");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path helperSource = sourceRoot.resolve("Helper.java");
        Path appSource = sourceRoot.resolve("PhantomJvmApp.java");
        Files.writeString(helperSource, """
                package demo;

                public final class Helper {
                    public static int VALUE = 3;

                    public static int add(int left, int right) {
                        return left + right + VALUE;
                    }

                    public int inc(int value) {
                        return value + VALUE;
                    }
                }
                """);
        Files.writeString(appSource, """
                package demo;

                public final class PhantomJvmApp {
                    public static void main(String[] args) {
                        Helper helper = new Helper();
                        System.out.println(Helper.add(2, 4));
                        System.out.println(helper.inc(5));
                        Helper.VALUE = 9;
                        System.out.println(helper.inc(5));
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), helperSource.toString(), appSource.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.PhantomJvmApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(86420L)
                .mode(ProtectionMode.JVM_PHANTOM)
                .jvmPhantom(true)
                .renameClasses(false)
                .renameMembers(false)
                .virtualize(false)
                .controlFlow(false)
                .referenceObfuscation(false)
                .sdkMarkers(false)
                .build());

        assertTrue(result.encryptedStrings() >= 0);

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("9\n8\n14\n", stdout);
    }

    @Test
    void virtualizesMethodsThatInvokeExternalStaticOwners() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-external-invoke");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path source = sourceRoot.resolve("ExternalCallApp.java");
        Files.writeString(source, """
                package demo;

                public final class ExternalCallApp {
                    public static void main(String[] args) {
                        System.out.println(localOnly(20));
                        System.out.println(externalCompare(9L, 4L));
                    }

                    static int localOnly(int value) {
                        return value + 22;
                    }

                    static int externalCompare(long left, long right) {
                        return Long.compare(left, right);
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.ExternalCallApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(97531L)
                .renameClasses(false)
                .renameMembers(false)
                .encryptStrings(false)
                .obfuscateNumbers(false)
                .controlFlow(false)
                .build());

        assertEquals(2, result.virtualizedMethods());

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("42\n1\n", stdout);
    }

    @Test
    void virtualizesInstanceFieldsCallsAndBranches() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-instance-vm");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        Path source = sourceRoot.resolve("InstanceApp.java");
        Files.writeString(source, """
                package demo;

                public final class InstanceApp {
                    private int value;

                    public InstanceApp(int value) {
                        this.value = value;
                    }

                    public static void main(String[] args) {
                        InstanceApp app = new InstanceApp(7);
                        System.out.println(app.adjust(5));
                        System.out.println(app.check("shield"));
                    }

                    int adjust(int delta) {
                        value += delta;
                        if (value > 10) {
                            return helper(value) + 1;
                        }
                        return helper(value) - 1;
                    }

                    boolean check(Object value) {
                        return value instanceof String && ((String) value).length() == 6;
                    }

                    private int helper(int input) {
                        return input * 2;
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.InstanceApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(424242L)
                .renameClasses(false)
                .renameMembers(false)
                .encryptStrings(false)
                .obfuscateNumbers(false)
                .controlFlow(false)
                .build());

        assertTrue(result.virtualizedMethods() >= 3);

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("25\ntrue\n", stdout);
    }

    @Test
    void rewritesReflectiveMemberNamesInsteadOfExcludingClasses() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-reflection");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        writeSource(sourceRoot.resolve("Target.java"), """
                package demo;

                public final class Target {
                    private int secret = 41;
                }
                """);
        writeSource(sourceRoot.resolve("ReflectionApp.java"), """
                package demo;

                public final class ReflectionApp {
                    public static void main(String[] args) throws Exception {
                        Target target = new Target();
                        java.lang.reflect.Field field = Target.class.getDeclaredField("secret");
                        field.setAccessible(true);
                        System.out.println(field.getInt(target) + 1);
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(),
                        sourceRoot.resolve("Target.java").toString(),
                        sourceRoot.resolve("ReflectionApp.java").toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.ReflectionApp");
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(112233L)
                .encryptStrings(false)
                .obfuscateNumbers(false)
                .virtualize(false)
                .controlFlow(false)
                .build());

        assertEquals(2, result.renamedClasses());
        String listing = listJar(output);
        assertFalse(listing.contains("demo/Target.class"));
        assertFalse(listing.contains("demo/ReflectionApp.class"));

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = processOutput(process);
        assertEquals(0, process.waitFor());
        assertEquals("42\n", stdout);
    }

    @Test
    void minecraftPresetPreservesLoaderAndMixinMetadata() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-minecraft");
        Path sourceRoot = Files.createDirectories(temp.resolve("src"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        writeSource(sourceRoot.resolve("tech/naven/NavenModLoader.java"), """
                package tech.naven;
                import net.minecraftforge.fml.common.Mod;
                @Mod("kamclient")
                public class NavenModLoader {
                    static int helper() {
                        return com.example.Helper.value();
                    }
                }
                """);
        writeSource(sourceRoot.resolve("com/example/mixin/MixinMinecraft.java"), """
                package com.example.mixin;
                import org.spongepowered.asm.mixin.Mixin;
                import org.spongepowered.asm.mixin.injection.At;
                import org.spongepowered.asm.mixin.injection.Inject;
                @Mixin(Object.class)
                public class MixinMinecraft {
                    @Inject(method = "tick", at = @At("HEAD"))
                    private void injectNewVelocityGameTick() {
                    }
                }
                """);
        writeSource(sourceRoot.resolve("com/example/Helper.java"), """
                package com.example;
                public final class Helper {
                    public static int value() {
                        return 40 + 2;
                    }
                }
                """);
        writeSource(sourceRoot.resolve("net/minecraftforge/fml/common/Mod.java"), """
                package net.minecraftforge.fml.common;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Mod {
                    String value();
                }
                """);
        writeSource(sourceRoot.resolve("org/spongepowered/asm/mixin/Mixin.java"), """
                package org.spongepowered.asm.mixin;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Mixin {
                    Class<?>[] value();
                }
                """);
        writeSource(sourceRoot.resolve("org/spongepowered/asm/mixin/injection/At.java"), """
                package org.spongepowered.asm.mixin.injection;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface At {
                    String value();
                }
                """);
        writeSource(sourceRoot.resolve("org/spongepowered/asm/mixin/injection/Inject.java"), """
                package org.spongepowered.asm.mixin.injection;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Inject {
                    String method();
                    At at();
                }
                """);

        List<String> sources = Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toString)
                .toList();
        List<String> compileArgs = new ArrayList<>();
        compileArgs.add("-d");
        compileArgs.add(classRoot.toString());
        compileArgs.addAll(sources);
        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, compileArgs.toArray(String[]::new));
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "tech.naven.NavenModLoader", Map.of(
                "META-INF/mods.toml", """
                        modLoader="javafml"
                        [[mods]]
                        modId="kamclient"
                        """,
                "kamclient.mixins.json", """
                        {
                          "required": true,
                          "package": "com.example.mixin",
                          "client": ["MixinMinecraft"],
                          "refmap": "kamclient.refmap.json"
                        }
                        """,
                "kamclient.refmap.json", "{}"
        ));
        Path output = temp.resolve("output.jar");

        ObfuscationResult result = new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(13579L)
                .renameClasses(true)
                .renameMembers(true)
                .encryptStrings(true)
                .obfuscateNumbers(true)
                .virtualize(true)
                .controlFlow(true)
                .rewriteTextResources(false)
                .minecraftMode(true)
                .build());

        String listing = listJar(output);
        assertTrue(listing.contains("tech/naven/NavenModLoader.class"));
        assertTrue(listing.contains("com/example/mixin/MixinMinecraft.class"));
        assertFalse(listing.contains("com/example/Helper.class"));
        assertTrue(result.renamedClasses() > 0);
        assertFalse(jarClassBytesContain(output, "injectNewVelocityGameTick"));
        assertEquals("""
                {
                  "required": true,
                  "package": "com.example.mixin",
                  "client": ["MixinMinecraft"],
                  "refmap": "kamclient.refmap.json"
                }
                """, readJarText(output, "kamclient.mixins.json"));
    }

    private static void createJar(Path jar, Path classRoot, String mainClass) throws IOException {
        createJar(jar, classRoot, mainClass, Map.of());
    }

    private static void createJar(Path jar, Path classRoot, String mainClass, Map<String, String> resources) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (Path file : Files.walk(classRoot).filter(Files::isRegularFile).toList()) {
                String name = classRoot.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(name));
                Files.copy(file, output);
                output.closeEntry();
            }
            for (Map.Entry<String, String> resource : resources.entrySet()) {
                output.putNextEntry(new JarEntry(resource.getKey()));
                output.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static String listJar(Path jar) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            List<String> names = jarFile.stream().map(JarEntry::getName).toList();
            return String.join("\n", names);
        }
    }

    private static String readJarText(Path jar, String name) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(name);
            return new String(jarFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
    }

    private static byte[] readJarBytes(Path jar, String name) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(name);
            return jarFile.getInputStream(entry).readAllBytes();
        }
    }

    private static List<String> directRuntimeVmEntryCallSites(Path jar, String runtimeClassName) throws IOException {
        List<String> callSites = new ArrayList<>();
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            for (JarEntry entry : jarFile.stream()
                    .filter(candidate -> candidate.getName().endsWith(".class"))
                    .toList()) {
                ClassNode classNode = new ClassNode();
                new ClassReader(jarFile.getInputStream(entry).readAllBytes()).accept(classNode, 0);
                for (MethodNode method : classNode.methods) {
                    if (method.instructions == null) {
                        continue;
                    }
                    for (AbstractInsnNode instruction = method.instructions.getFirst();
                         instruction != null;
                         instruction = instruction.getNext()) {
                        if (instruction instanceof MethodInsnNode call
                                && call.getOpcode() == INVOKESTATIC
                                && runtimeClassName.equals(call.owner)
                                && "_v".equals(call.name)
                                && RUNTIME_VM_ENTRY_DESCRIPTOR.equals(call.desc)) {
                            callSites.add(classNode.name + "#" + method.name + method.desc);
                        }
                    }
                }
            }
        }
        return callSites;
    }

    private static List<String> directRuntimeBootstrapCallSites(Path jar, String runtimeClassName) throws IOException {
        List<String> callSites = new ArrayList<>();
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            for (JarEntry entry : jarFile.stream()
                    .filter(candidate -> candidate.getName().endsWith(".class"))
                    .toList()) {
                ClassNode classNode = new ClassNode();
                new ClassReader(jarFile.getInputStream(entry).readAllBytes()).accept(classNode, 0);
                for (MethodNode method : classNode.methods) {
                    if (method.instructions == null) {
                        continue;
                    }
                    for (AbstractInsnNode instruction = method.instructions.getFirst();
                         instruction != null;
                         instruction = instruction.getNext()) {
                        if (instruction instanceof MethodInsnNode call
                                && call.getOpcode() == INVOKESTATIC
                                && runtimeClassName.equals(call.owner)
                                && call.desc.endsWith(")Ljava/lang/invoke/CallSite;")) {
                            callSites.add(classNode.name + "#" + method.name + method.desc
                                    + " -> " + call.name + call.desc);
                        }
                    }
                }
            }
        }
        return callSites;
    }

    private static Set<String> bootstrapOwners(Path jar) throws IOException {
        Set<String> owners = new HashSet<>();
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            for (JarEntry entry : jarFile.stream()
                    .filter(candidate -> candidate.getName().endsWith(".class"))
                    .toList()) {
                ClassNode classNode = new ClassNode();
                new ClassReader(jarFile.getInputStream(entry).readAllBytes()).accept(classNode, 0);
                for (MethodNode method : classNode.methods) {
                    if (method.instructions == null) {
                        continue;
                    }
                    for (AbstractInsnNode instruction = method.instructions.getFirst();
                         instruction != null;
                         instruction = instruction.getNext()) {
                        if (instruction instanceof InvokeDynamicInsnNode indy && indy.bsm != null) {
                            owners.add(indy.bsm.getOwner());
                        }
                    }
                }
            }
        }
        return owners;
    }

    private static Map<String, Integer> constantBootstrapDescriptors(Path jar) throws IOException {
        Map<String, Integer> descriptors = new java.util.TreeMap<>();
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            for (JarEntry entry : jarFile.stream()
                    .filter(candidate -> candidate.getName().endsWith(".class"))
                    .toList()) {
                ClassNode classNode = new ClassNode();
                new ClassReader(jarFile.getInputStream(entry).readAllBytes()).accept(classNode, 0);
                for (MethodNode method : classNode.methods) {
                    if (method.instructions == null) {
                        continue;
                    }
                    for (AbstractInsnNode instruction = method.instructions.getFirst();
                         instruction != null;
                         instruction = instruction.getNext()) {
                        if (instruction instanceof InvokeDynamicInsnNode indy
                                && indy.bsm != null
                                && isZeroArgumentConstant(indy.desc)) {
                            descriptors.merge(indy.bsm.getDesc(), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return descriptors;
    }

    private static int publicBootstrapMethods(Path jar, String runtimeClassName) throws IOException {
        ClassNode runtime = readClassNode(jar, runtimeClassName + ".class");
        int count = 0;
        for (MethodNode method : runtime.methods) {
            if ((method.access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)
                    && method.desc.endsWith(")Ljava/lang/invoke/CallSite;")) {
                count++;
            }
        }
        return count;
    }

    private static boolean isZeroArgumentConstant(String descriptor) {
        if (!descriptor.startsWith("()")) {
            return false;
        }
        return descriptor.equals("()I")
                || descriptor.equals("()J")
                || descriptor.equals("()F")
                || descriptor.equals("()D")
                || descriptor.equals("()Ljava/lang/String;");
    }

    private static ClassNode readClassNode(Path jar, String name) throws IOException {
        ClassNode classNode = new ClassNode();
        new ClassReader(readJarBytes(jar, name)).accept(classNode, 0);
        return classNode;
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String desc) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + classNode.name + "#" + name + desc);
    }

    private static boolean containsLookupSwitch(MethodNode method) {
        if (method.instructions == null) {
            return false;
        }
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof LookupSwitchInsnNode) {
                return true;
            }
        }
        return false;
    }

    private static VirtualProgram sampleProgram(int index) {
        int[] opcodeMap = new int[VirtualOp.MAX_OPCODE + 1];
        for (int opcode : VirtualOp.logicalOpcodes()) {
            opcodeMap[opcode] = opcode;
        }
        int key = 0x13579BDF ^ index * 0x45D9F3B;
        if (key == 0) {
            key = 0x2468ACE1;
        }
        return new VirtualProgram(1000 + index, 2, 1, VirtualProgram.RETURN_INT,
                new int[]{VirtualOp.LOAD, 0, VirtualOp.PUSH_CONST, 0, VirtualOp.IXOR, VirtualOp.RETURN},
                List.of(index * 17 + 3), key, opcodeMap,
                "demo/Owner" + index, "m" + index, null);
    }

    private static void assertDistributedVmResourceNames(java.util.Set<String> names, String runtimeClassName) {
        assertFalse(names.isEmpty());
        int slash = runtimeClassName.lastIndexOf('/');
        String base = slash < 0 ? "" : runtimeClassName.substring(0, slash + 1);
        java.util.Set<String> buckets = new java.util.HashSet<>();
        java.util.Set<String> extensions = new java.util.HashSet<>();
        for (String name : names) {
            assertTrue(name.startsWith(base));
            assertFalse(name.contains("data/" + "R"));
            assertFalse(name.contains("native/" + "windows-x64"));
            String relative = name.substring(base.length());
            int bucketEnd = relative.indexOf('/');
            assertTrue(bucketEnd > 0, name);
            buckets.add(relative.substring(0, bucketEnd));
            int dot = relative.lastIndexOf('.');
            assertTrue(dot > bucketEnd, name);
            String extension = relative.substring(dot);
            assertTrue(extension.matches("\\.(bin|dat|res|pak|idx|cfg)"), name);
            extensions.add(extension);
        }
        assertTrue(buckets.size() >= 2, "expected multiple VM resource buckets: " + names);
        assertTrue(extensions.size() >= 2, "expected multiple VM resource extensions: " + names);
    }

    private static String processOutput(Process process) throws IOException {
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        return stripNativeAccessWarnings(output);
    }

    private static String stripNativeAccessWarnings(String output) {
        String[] lines = output.split("\n", -1);
        StringBuilder cleaned = new StringBuilder(output.length());
        boolean skippedWarning = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("WARNING: ")) {
                skippedWarning = true;
                continue;
            }
            if (skippedWarning && line.isEmpty()) {
                skippedWarning = false;
                continue;
            }
            skippedWarning = false;
            cleaned.append(line);
            if (i < lines.length - 1) {
                cleaned.append('\n');
            }
        }
        return cleaned.toString();
    }

    private static String nativeEntryName(Path jar) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            return jarFile.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith("sushuo1337/sushuoprotect/lib/"))
                    .filter(name -> !name.endsWith(".class"))
                    .filter(name -> !name.contains("meta/"))
                    .filter(name -> name.matches(".*\\.(bin|dat|res|pak|idx|cfg)"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static boolean jarClassBytesContain(Path jar, String needle) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            for (JarEntry entry : jarFile.stream().filter(entry -> entry.getName().endsWith(".class")).toList()) {
                String text = new String(jarFile.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1);
                if (text.contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void writeSource(Path path, String source) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
    }
}
