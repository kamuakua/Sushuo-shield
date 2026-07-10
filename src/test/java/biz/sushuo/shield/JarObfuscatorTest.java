package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JarObfuscatorTest {
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
        String listing = listJar(output);
        assertFalse(listing.contains("NativeBridge.class"));
        assertTrue(listing.matches("(?s).*sushuo1337/sushuoprotect/lib/B[0-9a-z]+\\.class.*"));
        assertTrue(listing.contains("sushuo1337/sushuoprotect/lib/"));
        String nativeEntry = nativeEntryName(output);
        assertTrue(nativeEntry.startsWith("sushuo1337/sushuoprotect/lib/native/windows-x64/N"));
        assertTrue(nativeEntry.endsWith(".bin"));
        assertFalse(listing.contains("sushuo1337_vm.dll"));
        assertFalse(listing.contains("sushuo1337_vm.dll.dat"));
        byte[] nativeBytes = readJarBytes(output, nativeEntry);
        assertEquals('S', nativeBytes[0]);
        assertEquals('S', nativeBytes[1]);
        assertFalse(nativeBytes[0] == 'M' && nativeBytes[1] == 'Z');
        assertFalse(listing.contains("dev/"));

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, process.waitFor());
        assertEquals("21862\n270\n21776\n", stdout);
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
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
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
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, process.waitFor());
        assertEquals("Grimvelo Debug: combat Delay velocity: 7 marker=V enabled=true\n", stdout);
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
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
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
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
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
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
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

    @Test
    void strongMemberRenamePreservesSwingCallbackNames() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-swing-callbacks");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        writeSource(sourceRoot.resolve("SwingCallbacks.java"), """
                package demo;
                import java.awt.Graphics;
                import java.awt.event.KeyAdapter;
                import java.awt.event.KeyEvent;
                import java.awt.event.WindowAdapter;
                import java.awt.event.WindowEvent;
                import javax.swing.JPanel;

                public final class SwingCallbacks {
                    public static final class Board extends JPanel {
                        @Override
                        protected void paintComponent(Graphics g) {
                            super.paintComponent(g);
                        }
                    }
                    public static final class Keys extends KeyAdapter {
                        @Override
                        public void keyPressed(KeyEvent event) {
                        }
                    }
                    public static final class WindowClose extends WindowAdapter {
                        @Override
                        public void windowClosing(WindowEvent event) {
                        }
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), sourceRoot.resolve("SwingCallbacks.java").toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.SwingCallbacks");
        Path output = temp.resolve("output.jar");

        new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(998877L)
                .renameClasses(false)
                .renameMembers(true)
                .renamePublicMembers(true)
                .encryptStrings(false)
                .obfuscateNumbers(false)
                .virtualize(false)
                .controlFlow(false)
                .build());

        assertTrue(jarClassHasMethod(output, "demo/SwingCallbacks$Board.class",
                "paintComponent", "(Ljava/awt/Graphics;)V"));
        assertTrue(jarClassHasMethod(output, "demo/SwingCallbacks$Keys.class",
                "keyPressed", "(Ljava/awt/event/KeyEvent;)V"));
        assertTrue(jarClassHasMethod(output, "demo/SwingCallbacks$WindowClose.class",
                "windowClosing", "(Ljava/awt/event/WindowEvent;)V"));
    }

    @Test
    void parameterObfuscationLeavesNonPrivateMethodDescriptorsStable() throws Exception {
        Path temp = Files.createTempDirectory("sushuo-shield-parameter-safety");
        Path sourceRoot = Files.createDirectories(temp.resolve("src/demo"));
        Path classRoot = Files.createDirectories(temp.resolve("classes"));
        writeSource(sourceRoot.resolve("ParameterApp.java"), """
                package demo;

                public final class ParameterApp {
                    public static void main(String[] args) {
                        System.out.println(new ParameterApp().visible(40));
                    }

                    final int visible(int value) {
                        return helper(value) + 1;
                    }

                    private int helper(int value) {
                        return value + 1;
                    }
                }
                """);

        int compileResult = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classRoot.toString(), sourceRoot.resolve("ParameterApp.java").toString());
        assertEquals(0, compileResult);

        Path input = temp.resolve("input.jar");
        createJar(input, classRoot, "demo.ParameterApp");
        Path output = temp.resolve("output.jar");

        new JarObfuscator().obfuscate(ObfuscationOptions.builder()
                .input(input)
                .output(output)
                .seed(123456L)
                .renameClasses(false)
                .renameMembers(false)
                .encryptStrings(false)
                .obfuscateNumbers(false)
                .virtualize(false)
                .controlFlow(false)
                .methodParameterObfuscation(true)
                .build());

        assertTrue(jarClassHasMethod(output, "demo/ParameterApp.class", "visible", "(I)I"));
        assertTrue(jarClassHasMethod(output, "demo/ParameterApp.class", "helper", "(II)I")
                || jarClassHasMethod(output, "demo/ParameterApp.class", "helper", "(IJ)I")
                || jarClassHasMethod(output, "demo/ParameterApp.class", "helper", "(ILjava/lang/Object;)I"));

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, process.waitFor());
        assertEquals("42\n", stdout);
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

    private static boolean jarClassHasMethod(Path jar, String name, String methodName, String descriptor) throws IOException {
        ClassNode classNode = readJarClass(jar, name);
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(methodName) && method.desc.equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode readJarClass(Path jar, String name) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(name);
            ClassReader reader = new ClassReader(jarFile.getInputStream(entry).readAllBytes());
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);
            return classNode;
        }
    }

    private static String nativeEntryName(Path jar) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            return jarFile.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith("sushuo1337/sushuoprotect/lib/native/windows-x64/"))
                    .filter(name -> name.endsWith(".bin"))
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
