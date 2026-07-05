package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

final class RuntimeClassGenerator {
    private static final String TEMPLATE = "biz/sushuo/shield/runtime/InjectedRuntime";
    private static final String NATIVE_ONLY_TEMPLATE = "biz/sushuo/shield/runtime/NativeOnlyRuntime";
    private static final String NATIVE_BRIDGE_TEMPLATE = "biz/sushuo/shield/runtime/NativeBridge";
    static final String NATIVE_BRIDGE = "sushuo1337/sushuoprotect/lib/NativeBridge";
    static final String NATIVE_RESOURCE_DIR = "sushuo1337/sushuoprotect/lib/native/";
    private static final String LEGACY_NATIVE_NAME = "windows-x64/sushuo1337_vm.dll";
    private static final String NATIVE_RESOURCE_TOKEN = "%%SUSHUO_NATIVE_RESOURCE%%";
    private static final String NATIVE_REQUIRED_TOKEN = "%%SUSHUO_NATIVE_REQUIRED%%";
    private static final int NATIVE_MAGIC = 0x53534E32; // SSN2
    private static final int NATIVE_VERSION = 2;
    private static final byte[] NATIVE_SECRET_MARKER = new byte[]{
            0x53, 0x53, 0x4B, 0x21,
            0x5A, 0x4E, 0x31, 0x43,
            0x39, 0x2A, 0x77, 0x10,
            0x6D, 0x55, 0x42, 0x7E
    };

    private RuntimeClassGenerator() {
    }

    static Map<String, byte[]> generateAll(String runtimeClassName, String nativeResourceName,
                                           boolean nativeRequired) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        String template = nativeRequired ? NATIVE_ONLY_TEMPLATE : TEMPLATE;
        classes.put(runtimeClassName + ".class",
                generate(template, runtimeClassName, "/" + nativeResourceName, nativeRequired));
        classes.put(NATIVE_BRIDGE + ".class", generate(NATIVE_BRIDGE_TEMPLATE, NATIVE_BRIDGE, null, false));
        return classes;
    }

    static byte[] generate(String runtimeClassName) throws IOException {
        return generate(TEMPLATE, runtimeClassName,
                "/" + NATIVE_RESOURCE_DIR + LEGACY_NATIVE_NAME + ".dat", false);
    }

    static Map<String, byte[]> nativeResources(NamingPlan plan, long seed) throws IOException {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        byte[] nativeBytes = readBundledNative();
        if (nativeBytes != null) {
            resources.put(plan.nativeResourceName(), packNative(patchNativeSecret(nativeBytes, plan, seed), plan));
        }
        return resources;
    }

    private static byte[] readBundledNative() throws IOException {
        try (InputStream input = RuntimeClassGenerator.class.getClassLoader()
                .getResourceAsStream(NATIVE_RESOURCE_DIR + LEGACY_NATIVE_NAME + ".dat")) {
            if (input != null) {
                byte[] decoded = decodeLegacyNative(input.readAllBytes());
                if (looksLikeWindowsDll(decoded)) {
                    return decoded;
                }
            }
        }
        try (InputStream input = RuntimeClassGenerator.class.getClassLoader()
                .getResourceAsStream(NATIVE_RESOURCE_DIR + LEGACY_NATIVE_NAME)) {
            if (input != null) {
                byte[] raw = input.readAllBytes();
                if (looksLikeWindowsDll(raw)) {
                    return raw;
                }
            }
        }
        return null;
    }

    private static byte[] patchNativeSecret(byte[] raw, NamingPlan plan, long seed) throws IOException {
        byte[] patched = raw.clone();
        int offset = indexOf(patched, NATIVE_SECRET_MARKER);
        if (offset < 0) {
            throw new IOException("Native VM secret placeholder is missing. Rebuild native/sushuo1337_vm.c.");
        }
        byte[] secret = VmPayloadResources.nativeSecret(plan, seed);
        System.arraycopy(secret, 0, patched, offset, secret.length);
        return patched;
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] packNative(byte[] raw, NamingPlan plan) throws IOException {
        String resource = "/" + plan.nativeResourceName();
        String runtime = plan.runtimeClassName().replace('/', '.');
        byte[] payload = deflate(raw);
        int key = mix(plan.nativeResourceName().hashCode()
                ^ Integer.rotateLeft(plan.runtimeClassName().hashCode(), 7)
                ^ raw.length ^ payload.length ^ 0x4E415456);
        int nonce = mix(key ^ resource.hashCode() ^ 0x5A17C0DE);
        int keyTag = key ^ resource.hashCode() ^ runtime.hashCode() ^ NATIVE_MAGIC;
        int state = nativeState(key, nonce, resource, runtime, raw.length, payload.length);
        byte[] encoded = payload.clone();
        for (int i = 0; i < encoded.length; i++) {
            state = nativeStream(state, i);
            encoded[i] = (byte) (encoded[i] ^ (state >>> 24));
        }

        CRC32 crc = new CRC32();
        crc.update(raw);
        ByteBuffer buffer = ByteBuffer.allocate(28 + encoded.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(NATIVE_MAGIC);
        buffer.putInt(NATIVE_VERSION);
        buffer.putInt(nonce);
        buffer.putInt(keyTag);
        buffer.putInt(raw.length);
        buffer.putInt(encoded.length);
        buffer.putInt((int) crc.getValue());
        buffer.put(encoded);
        return buffer.array();
    }

    private static byte[] deflate(byte[] raw) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(raw.length);
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
            deflater.write(raw);
        }
        return output.toByteArray();
    }

    private static byte[] decodeLegacyNative(byte[] data) {
        byte[] decoded = data.clone();
        int state = 0x6D2B79F5 ^ decoded.length;
        for (int i = 0; i < decoded.length; i++) {
            state ^= i * 0x45D9F3B;
            state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
            state ^= state >>> 13;
            state *= 0x5BD1E995;
            state ^= state >>> 15;
            decoded[i] = (byte) (decoded[i] ^ (state >>> 24));
        }
        return decoded;
    }

    private static boolean looksLikeWindowsDll(byte[] data) {
        return data.length > 2 && data[0] == 'M' && data[1] == 'Z';
    }

    private static int nativeState(int key, int nonce, String resource, String runtime, int rawLength, int payloadLength) {
        int state = key ^ nonce ^ resource.hashCode();
        state = mix(state ^ runtime.hashCode());
        state = mix(state ^ rawLength);
        return mix(state ^ payloadLength ^ 0x7F4A7C15);
    }

    private static int nativeStream(int state, int index) {
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static byte[] generate(String template, String targetName,
                                   String nativeResourceName, boolean nativeRequired) throws IOException {
        try (InputStream input = RuntimeClassGenerator.class.getClassLoader()
                .getResourceAsStream(template + ".class")) {
            if (input == null) {
                throw new IOException("Missing runtime template: " + template);
            }
            ClassReader reader = new ClassReader(input);
            ClassNode remapped = new ClassNode();
            reader.accept(new ClassRemapper(remapped, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (TEMPLATE.equals(internalName) || NATIVE_ONLY_TEMPLATE.equals(internalName)) {
                        return internalName.equals(template) ? targetName : internalName;
                    }
                    if (NATIVE_BRIDGE_TEMPLATE.equals(internalName)) {
                        return NATIVE_BRIDGE_TEMPLATE.equals(template) ? targetName : NATIVE_BRIDGE;
                    }
                    return internalName;
                }
            }), ClassReader.SKIP_DEBUG);
            if (TEMPLATE.equals(template) || NATIVE_ONLY_TEMPLATE.equals(template)) {
                patchRuntimeConstants(remapped, nativeResourceName, nativeRequired);
            }
            ClassWriter writer = new ClassWriter(0);
            remapped.accept(writer);
            return writer.toByteArray();
        }
    }

    private static void patchRuntimeConstants(ClassNode classNode, String nativeResourceName, boolean nativeRequired) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode ldc) {
                    if (NATIVE_RESOURCE_TOKEN.equals(ldc.cst)) {
                        ldc.cst = nativeResourceName;
                    } else if (NATIVE_REQUIRED_TOKEN.equals(ldc.cst)) {
                        ldc.cst = Boolean.toString(nativeRequired);
                    }
                }
            }
        }
    }
}
