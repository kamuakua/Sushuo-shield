package biz.sushuo.shield;

import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        assertTrue(listing.contains("sushuo1337/sushuoprotect/lib/NativeBridge.class"));
        assertTrue(listing.contains("sushuo1337/sushuoprotect/lib/"));
        assertFalse(listing.contains("dev/"));

        Process process = new ProcessBuilder(javaBin(), "-jar", output.toString())
                .redirectErrorStream(true)
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, process.waitFor());
        assertEquals("21862\n270\n21776\n", stdout);
    }

    private static void createJar(Path jar, Path classRoot, String mainClass) throws IOException {
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
        }
    }

    private static String listJar(Path jar) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
            List<String> names = jarFile.stream().map(JarEntry::getName).toList();
            return String.join("\n", names);
        }
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
    }
}
