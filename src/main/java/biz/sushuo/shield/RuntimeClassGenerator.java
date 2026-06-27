package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

final class RuntimeClassGenerator {
    private static final String TEMPLATE = "biz/sushuo/shield/runtime/InjectedRuntime";
    private static final String NATIVE_BRIDGE_TEMPLATE = "biz/sushuo/shield/runtime/NativeBridge";
    static final String NATIVE_BRIDGE = "sushuo1337/sushuoprotect/lib/NativeBridge";

    private RuntimeClassGenerator() {
    }

    static Map<String, byte[]> generateAll(String runtimeClassName) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(runtimeClassName + ".class", generate(TEMPLATE, runtimeClassName));
        classes.put(NATIVE_BRIDGE + ".class", generate(NATIVE_BRIDGE_TEMPLATE, NATIVE_BRIDGE));
        return classes;
    }

    static byte[] generate(String runtimeClassName) throws IOException {
        return generate(TEMPLATE, runtimeClassName);
    }

    private static byte[] generate(String template, String targetName) throws IOException {
        try (InputStream input = RuntimeClassGenerator.class.getClassLoader()
                .getResourceAsStream(template + ".class")) {
            if (input == null) {
                throw new IOException("Missing runtime template: " + template);
            }
            ClassReader reader = new ClassReader(input);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (TEMPLATE.equals(internalName)) {
                        return TEMPLATE.equals(template) ? targetName : internalName;
                    }
                    if (NATIVE_BRIDGE_TEMPLATE.equals(internalName)) {
                        return NATIVE_BRIDGE_TEMPLATE.equals(template) ? targetName : NATIVE_BRIDGE;
                    }
                    return internalName;
                }
            }), ClassReader.SKIP_DEBUG);
            return writer.toByteArray();
        }
    }
}
