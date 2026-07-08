package biz.sushuo.shield;

import org.objectweb.asm.Handle;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

final class RuntimeClassGenerator {
    private static final String TEMPLATE = "biz/sushuo/shield/runtime/InjectedRuntime";
    private static final String NATIVE_ONLY_TEMPLATE = "biz/sushuo/shield/runtime/NativeOnlyRuntime";
    private static final String NATIVE_BRIDGE_TEMPLATE = "biz/sushuo/shield/runtime/NativeBridge";
    static final String NATIVE_BRIDGE = "sushuo1337/sushuoprotect/lib/NativeBridge";
    private static final byte[] NATIVE_BRIDGE_MARKER = NATIVE_BRIDGE.getBytes(StandardCharsets.ISO_8859_1);
    static final String NATIVE_RESOURCE_DIR = "sushuo1337/sushuoprotect/lib/native/";
    private static final String LEGACY_NATIVE_NAME = "windows-x64/sushuo1337_vm.dll";
    private static final String NATIVE_RESOURCE_TOKEN = "%%SUSHUO_NATIVE_RESOURCE%%";
    private static final String NATIVE_LIBRARY_TOKEN = "%%SUSHUO_NATIVE_LIBRARY%%";
    private static final String NATIVE_REQUIRED_TOKEN = "%%SUSHUO_NATIVE_REQUIRED%%";
    private static final String ANTI_DEBUG_TOKEN = "%%SUSHUO_ANTI_DEBUG%%";
    private static final String ANTI_VM_TOKEN = "%%SUSHUO_ANTI_VM%%";
    private static final String LICENSE_HASH_TOKEN = "%%SUSHUO_LICENSE_HASH%%";
    private static final String INTEGRITY_RESOURCE_TOKEN = "%%SUSHUO_INTEGRITY_RESOURCE%%";
    private static final String INTEGRITY_HASH_TOKEN = "%%SUSHUO_INTEGRITY_HASH%%";
    private static final String NATIVE_METHOD_TOKEN = "%%SUSHUO_NATIVE_METHOD%%";
    private static final String LICENSE_PROPERTY_TOKEN = "%%SUSHUO_LICENSE_PROPERTY%%";
    private static final String LICENSE_ENV_TOKEN = "%%SUSHUO_LICENSE_ENV%%";
    private static final String DEBUGGER_PROPERTY_TOKEN = "%%SUSHUO_DEBUGGER_PROPERTY%%";
    private static final String NATIVE_REQUIRED_PROPERTY_TOKEN = "%%SUSHUO_NATIVE_REQUIRED_PROPERTY%%";
    private static final String SELF_HASH_TOKEN = "J$7e9a2d4f00000000";
    private static final String SELF_HASH_PREFIX = "J$7e9a2d4f";
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
        return generateAll(runtimeClassName, nativeResourceName, nativeRequired, false, false, 0);
    }

    static Map<String, byte[]> generateAll(String runtimeClassName, String nativeResourceName,
                                           boolean nativeRequired, boolean antiDebug,
                                           boolean antiVm, int licenseHash) throws IOException {
        return generateAll(runtimeClassName, nativeResourceName, nativeRequired,
                antiDebug, antiVm, licenseHash, "", 0);
    }

    static Map<String, byte[]> generateAll(String runtimeClassName, String nativeResourceName,
                                           boolean nativeRequired, boolean antiDebug,
                                           boolean antiVm, int licenseHash,
                                           String integrityResourceName, int integrityHash) throws IOException {
        return generateAll(runtimeClassName, nativeResourceName, nativeRequired, antiDebug, antiVm,
                licenseHash, integrityResourceName, integrityHash, Map.of());
    }

    static Map<String, byte[]> generateAll(String runtimeClassName, String nativeResourceName,
                                           boolean nativeRequired, boolean antiDebug,
                                           boolean antiVm, int licenseHash,
                                           String integrityResourceName, int integrityHash,
                                           Map<RuntimeApiObfuscator.MemberSig, String> runtimeApiNames) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        String template = nativeRequired ? NATIVE_ONLY_TEMPLATE : TEMPLATE;
        String bridgeClassName = bridgeClassName(runtimeClassName);
        String bridgeMethodName = bridgeMethodName(runtimeClassName);
        String nativeLibraryName = nativeLibraryName(runtimeClassName);
        byte[] runtimeClass = generate(template, runtimeClassName, "/" + nativeResourceName, nativeRequired,
                antiDebug, antiVm, licenseHash, integrityResourceName, integrityHash, SELF_HASH_TOKEN,
                runtimeApiNames, bridgeClassName, bridgeMethodName, nativeLibraryName);
        if (integrityHash != 0 && integrityResourceName != null && !integrityResourceName.isEmpty()) {
            int selfHash = selfClassHash(runtimeClass, runtimeClassName.replace('/', '.'));
            runtimeClass = patchSelfHashToken(runtimeClass, formatSelfHash(selfHash));
        }
        classes.put(runtimeClassName + ".class", runtimeClass);
        classes.put(bridgeClassName + ".class",
                generate(NATIVE_BRIDGE_TEMPLATE, bridgeClassName, null, false,
                        false, false, 0, "", 0, SELF_HASH_TOKEN, Map.of(), bridgeClassName, bridgeMethodName, nativeLibraryName));
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
            resources.put(plan.nativeResourceName(), packNative(patchNative(nativeBytes, plan, seed), plan));
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

    private static byte[] patchNative(byte[] raw, NamingPlan plan, long seed) throws IOException {
        byte[] patched = raw.clone();
        int offset = indexOf(patched, NATIVE_SECRET_MARKER);
        if (offset < 0) {
            throw new IOException("Native VM secret placeholder is missing. Rebuild native/sushuo1337_vm.c.");
        }
        byte[] secret = VmPayloadResources.nativeSecret(plan, seed);
        System.arraycopy(secret, 0, patched, offset, secret.length);
        patchNativeBridgeName(patched, bridgeClassName(plan.runtimeClassName()));
        return patched;
    }

    private static void patchNativeBridgeName(byte[] patched, String bridgeName) throws IOException {
        byte[] replacement = bridgeName.getBytes(StandardCharsets.ISO_8859_1);
        if (replacement.length != NATIVE_BRIDGE_MARKER.length) {
            throw new IOException("Native bridge marker length mismatch: " + bridgeName);
        }
        int count = 0;
        int from = 0;
        while (from < patched.length) {
            int offset = indexOf(patched, NATIVE_BRIDGE_MARKER, from);
            if (offset < 0) {
                break;
            }
            System.arraycopy(replacement, 0, patched, offset, replacement.length);
            count++;
            from = offset + replacement.length;
        }
        if (count == 0) {
            throw new IOException("Native bridge marker is missing. Rebuild native/sushuo1337_vm.c.");
        }
    }

    private static int indexOf(byte[] data, byte[] needle) {
        return indexOf(data, needle, 0);
    }

    private static int indexOf(byte[] data, byte[] needle, int start) {
        outer:
        for (int i = Math.max(0, start); i <= data.length - needle.length; i++) {
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
        return generate(template, targetName, nativeResourceName, nativeRequired, false, false, 0);
    }

    private static byte[] generate(String template, String targetName,
                                   String nativeResourceName, boolean nativeRequired,
                                   boolean antiDebug, boolean antiVm, int licenseHash) throws IOException {
        return generate(template, targetName, nativeResourceName, nativeRequired,
                antiDebug, antiVm, licenseHash, "", 0);
    }

    private static byte[] generate(String template, String targetName,
                                   String nativeResourceName, boolean nativeRequired,
                                   boolean antiDebug, boolean antiVm, int licenseHash,
                                   String integrityResourceName, int integrityHash) throws IOException {
        return generate(template, targetName, nativeResourceName, nativeRequired,
                antiDebug, antiVm, licenseHash, integrityResourceName, integrityHash, SELF_HASH_TOKEN);
    }

    private static byte[] generate(String template, String targetName,
                                   String nativeResourceName, boolean nativeRequired,
                                   boolean antiDebug, boolean antiVm, int licenseHash,
                                   String integrityResourceName, int integrityHash,
                                   String selfHashValue) throws IOException {
        return generate(template, targetName, nativeResourceName, nativeRequired, antiDebug, antiVm,
                licenseHash, integrityResourceName, integrityHash, selfHashValue, Map.of());
    }

    private static byte[] generate(String template, String targetName,
                                   String nativeResourceName, boolean nativeRequired,
                                   boolean antiDebug, boolean antiVm, int licenseHash,
                                   String integrityResourceName, int integrityHash,
                                   String selfHashValue,
                                   Map<RuntimeApiObfuscator.MemberSig, String> runtimeApiNames) throws IOException {
        return generate(template, targetName, nativeResourceName, nativeRequired, antiDebug, antiVm,
                licenseHash, integrityResourceName, integrityHash, selfHashValue,
                runtimeApiNames, NATIVE_BRIDGE, "_n", "sushuo1337_vm");
    }

    private static byte[] generate(String template, String targetName,
                                   String nativeResourceName, boolean nativeRequired,
                                   boolean antiDebug, boolean antiVm, int licenseHash,
                                   String integrityResourceName, int integrityHash,
                                   String selfHashValue,
                                   Map<RuntimeApiObfuscator.MemberSig, String> runtimeApiNames,
                                   String bridgeClassName,
                                   String bridgeMethodName,
                                   String nativeLibraryName) throws IOException {
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
                        return NATIVE_BRIDGE_TEMPLATE.equals(template) ? targetName : bridgeClassName;
                    }
                    return internalName;
                }
            }), ClassReader.SKIP_DEBUG);
            if (TEMPLATE.equals(template) || NATIVE_ONLY_TEMPLATE.equals(template)) {
                patchRuntimeConstants(remapped, nativeResourceName, nativeLibraryName, nativeRequired, antiDebug, antiVm,
                        licenseHash, integrityResourceName, integrityHash, selfHashValue);
                rewriteNativeBridgeCalls(remapped, bridgeClassName, bridgeMethodName);
                RuntimeApiObfuscator.rewriteClassNode(remapped, targetName, runtimeApiNames, true);
                obfuscateRuntimePrivateMembers(remapped, targetName);
            } else if (NATIVE_BRIDGE_TEMPLATE.equals(template)) {
                patchNativeBridgeClass(remapped, bridgeMethodName);
            }
            ClassWriter writer = new ClassWriter(0);
            remapped.accept(writer);
            return writer.toByteArray();
        }
    }

    private static void rewriteNativeBridgeCalls(ClassNode classNode, String bridgeClassName, String bridgeMethodName) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call
                        && bridgeClassName.equals(call.owner)
                        && call.name.equals("_n")
                        && call.desc.equals("([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
                    call.name = bridgeMethodName;
                }
            }
        }
    }

    private static void patchNativeBridgeClass(ClassNode classNode, String bridgeMethodName) {
        for (FieldNode field : classNode.fields) {
            if ("a".equals(field.name)
                    && "Ljava/lang/String;".equals(field.desc)
                    && NATIVE_METHOD_TOKEN.equals(field.value)) {
                field.value = bridgeMethodName;
            }
        }
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("_n")
                    && method.desc.equals("([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
                method.name = bridgeMethodName;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode ldc
                        && NATIVE_METHOD_TOKEN.equals(ldc.cst)) {
                    ldc.cst = bridgeMethodName;
                }
            }
        }
    }

    private static String bridgeClassName(String runtimeClassName) {
        int slash = runtimeClassName.lastIndexOf('/');
        String prefix = slash < 0 ? "" : runtimeClassName.substring(0, slash + 1);
        if (prefix.length() >= NATIVE_BRIDGE.length()) {
            slash = NATIVE_BRIDGE.lastIndexOf('/');
            prefix = slash < 0 ? "" : NATIVE_BRIDGE.substring(0, slash + 1);
        }
        int simpleLength = NATIVE_BRIDGE.length() - prefix.length();
        int a = mix(runtimeClassName.hashCode() ^ 0x4E425247);
        int b = mix(a ^ Integer.rotateLeft(runtimeClassName.length() * 0x9E3779B9, 7));
        StringBuilder suffix = new StringBuilder("B")
                .append(Integer.toUnsignedString(a, 36))
                .append(Integer.toUnsignedString(b, 36));
        int salt = b;
        while (suffix.length() < simpleLength) {
            salt = mix(salt ^ suffix.length() * 0x45D9F3B);
            suffix.append(Integer.toUnsignedString(salt, 36));
        }
        if (suffix.length() > simpleLength) {
            suffix.setLength(simpleLength);
        }
        return prefix + suffix;
    }

    private static String nativeLibraryName(String runtimeClassName) {
        int a = mix(runtimeClassName.hashCode() ^ 0x4E4C4942);
        int b = mix(a ^ Integer.rotateLeft(runtimeClassName.length() * 0x27D4EB2D, 9));
        return "j"
                + Integer.toUnsignedString(a, 36)
                + Integer.toUnsignedString(b, 36);
    }

    private static String runtimePropertyName(String nativeResourceName, int salt) {
        int a = mix(nativeResourceName.hashCode() ^ salt);
        int b = mix(a ^ Integer.rotateLeft(nativeResourceName.length() * 0x45D9F3B, 7));
        return "j."
                + Integer.toUnsignedString(a, 36)
                + "."
                + Integer.toUnsignedString(b, 36);
    }

    private static String runtimeEnvName(String nativeResourceName, int salt) {
        int a = mix(nativeResourceName.hashCode() ^ salt);
        int b = mix(a ^ Integer.rotateLeft(nativeResourceName.length() * 0x27D4EB2D, 11));
        return ("J_"
                + Integer.toUnsignedString(a, 36)
                + "_"
                + Integer.toUnsignedString(b, 36)).toUpperCase(Locale.ROOT);
    }

    private static String bridgeMethodName(String runtimeClassName) {
        int a = mix(runtimeClassName.hashCode() ^ 0x4E4D4554);
        int b = mix(a ^ Integer.rotateLeft(runtimeClassName.length() * 0x45D9F3B, 5));
        return "_"
                + Integer.toUnsignedString(a, 36)
                + Integer.toUnsignedString(b, 36);
    }

    private static void patchRuntimeConstants(ClassNode classNode, String nativeResourceName, String nativeLibraryName, boolean nativeRequired,
                                              boolean antiDebug, boolean antiVm, int licenseHash,
                                              String integrityResourceName, int integrityHash,
                                              String selfHashValue) {
        for (FieldNode field : classNode.fields) {
            if (field.value instanceof String value) {
                field.value = patchRuntimeConstantValue(value, nativeResourceName, nativeLibraryName, nativeRequired,
                        antiDebug, antiVm, licenseHash, integrityResourceName, integrityHash, selfHashValue);
            }
        }
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof LdcInsnNode ldc) {
                    if (ldc.cst instanceof String value) {
                        ldc.cst = patchRuntimeConstantValue(value, nativeResourceName, nativeLibraryName, nativeRequired,
                                antiDebug, antiVm, licenseHash, integrityResourceName, integrityHash, selfHashValue);
                    }
                }
            }
        }
    }

    private static String patchRuntimeConstantValue(String value, String nativeResourceName, String nativeLibraryName,
                                                    boolean nativeRequired, boolean antiDebug, boolean antiVm,
                                                    int licenseHash, String integrityResourceName, int integrityHash,
                                                    String selfHashValue) {
        if (NATIVE_RESOURCE_TOKEN.equals(value)) {
            return nativeResourceName;
        } else if (NATIVE_LIBRARY_TOKEN.equals(value)) {
            return nativeLibraryName;
        } else if (NATIVE_REQUIRED_TOKEN.equals(value)) {
            return Boolean.toString(nativeRequired);
        } else if (NATIVE_REQUIRED_PROPERTY_TOKEN.equals(value)) {
            return runtimePropertyName(nativeResourceName, 0x4E525052);
        } else if (ANTI_DEBUG_TOKEN.equals(value)) {
            return Boolean.toString(antiDebug);
        } else if (ANTI_VM_TOKEN.equals(value)) {
            return Boolean.toString(antiVm);
        } else if (LICENSE_HASH_TOKEN.equals(value)) {
            return Integer.toString(licenseHash);
        } else if (LICENSE_PROPERTY_TOKEN.equals(value)) {
            return runtimePropertyName(nativeResourceName, 0x4C494350);
        } else if (LICENSE_ENV_TOKEN.equals(value)) {
            return runtimeEnvName(nativeResourceName, 0x4C494345);
        } else if (DEBUGGER_PROPERTY_TOKEN.equals(value)) {
            return runtimePropertyName(nativeResourceName, 0x44424750);
        } else if (INTEGRITY_RESOURCE_TOKEN.equals(value)) {
            return integrityResourceName == null ? "" : integrityResourceName;
        } else if (INTEGRITY_HASH_TOKEN.equals(value)) {
            return Integer.toString(integrityHash);
        } else if (SELF_HASH_TOKEN.equals(value)) {
            return selfHashValue == null ? SELF_HASH_TOKEN : selfHashValue;
        }
        return value;
    }

    private static void obfuscateRuntimePrivateMembers(ClassNode classNode, String targetName) {
        Map<String, String> methodNames = new HashMap<>();
        Set<String> usedMethodKeys = new HashSet<>();
        for (MethodNode method : classNode.methods) {
            usedMethodKeys.add(method.name + method.desc);
        }
        int methodOrdinal = 0;
        for (MethodNode method : classNode.methods) {
            if (!isRuntimePrivateMethodRenameCandidate(method)) {
                continue;
            }
            String oldKey = method.name + method.desc;
            String next;
            do {
                next = runtimeName("m", targetName, method.name, method.desc, methodOrdinal++);
            } while (usedMethodKeys.contains(next + method.desc));
            usedMethodKeys.remove(oldKey);
            usedMethodKeys.add(next + method.desc);
            methodNames.put(oldKey, next);
            method.name = next;
        }

        Map<String, String> fieldNames = new HashMap<>();
        Set<String> usedFieldNames = new HashSet<>();
        for (FieldNode field : classNode.fields) {
            usedFieldNames.add(field.name);
        }
        int fieldOrdinal = 0;
        for (FieldNode field : classNode.fields) {
            if ((field.access & Opcodes.ACC_PRIVATE) == 0) {
                continue;
            }
            String next;
            do {
                next = runtimeName("f", targetName, field.name, field.desc, fieldOrdinal++);
            } while (usedFieldNames.contains(next));
            usedFieldNames.remove(field.name);
            usedFieldNames.add(next);
            fieldNames.put(field.name + '\u0000' + field.desc, next);
            field.name = next;
        }

        if (methodNames.isEmpty() && fieldNames.isEmpty()) {
            return;
        }
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call
                        && classNode.name.equals(call.owner)) {
                    String mapped = methodNames.get(call.name + call.desc);
                    if (mapped != null) {
                        call.name = mapped;
                    }
                } else if (instruction instanceof FieldInsnNode field
                        && classNode.name.equals(field.owner)) {
                    String mapped = fieldNames.get(field.name + '\u0000' + field.desc);
                    if (mapped != null) {
                        field.name = mapped;
                    }
                } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        indy.bsmArgs[i] = remapRuntimeHandle(indy.bsmArgs[i], classNode.name, methodNames);
                    }
                    indy.bsm = (Handle) remapRuntimeHandle(indy.bsm, classNode.name, methodNames);
                } else if (instruction instanceof LdcInsnNode ldc) {
                    ldc.cst = remapRuntimeHandle(ldc.cst, classNode.name, methodNames);
                }
            }
        }
    }

    private static Object remapRuntimeHandle(Object value, String owner, Map<String, String> methodNames) {
        if (value instanceof Handle handle) {
            if (owner.equals(handle.getOwner())) {
                String mapped = methodNames.get(handle.getName() + handle.getDesc());
                if (mapped != null) {
                    return new Handle(handle.getTag(), handle.getOwner(), mapped, handle.getDesc(), handle.isInterface());
                }
            }
            return handle;
        }
        if (value instanceof Type type && type.getSort() == Type.METHOD) {
            return type;
        }
        return value;
    }

    private static boolean isRuntimePrivateMethodRenameCandidate(MethodNode method) {
        if ((method.access & Opcodes.ACC_PRIVATE) == 0) {
            return false;
        }
        return !method.name.equals("<init>") && !method.name.equals("<clinit>");
    }

    private static String runtimeName(String prefix, String targetName, String name, String desc, int ordinal) {
        int value = mix(targetName.hashCode()
                ^ Integer.rotateLeft(name.hashCode(), 7)
                ^ Integer.rotateLeft(desc.hashCode(), 13)
                ^ ordinal * 0x45D9F3B);
        int value2 = mix(value ^ Integer.rotateLeft(targetName.length() * 0x9E3779B9, ordinal & 15));
        return "_" + prefix
                + Integer.toUnsignedString(value, 36)
                + Integer.toUnsignedString(value2, 36);
    }

    private static String formatSelfHash(int hash) {
        return SELF_HASH_PREFIX + String.format(Locale.ROOT, "%08x", hash);
    }

    private static byte[] patchSelfHashToken(byte[] classBytes, String selfHashValue) throws IOException {
        byte[] out = classBytes.clone();
        byte[] token = SELF_HASH_TOKEN.getBytes(StandardCharsets.ISO_8859_1);
        byte[] value = selfHashValue.getBytes(StandardCharsets.ISO_8859_1);
        if (token.length != value.length) {
            throw new IOException("Runtime self hash token length mismatch.");
        }
        int count = 0;
        outer:
        for (int i = 0; i <= out.length - token.length; i++) {
            for (int j = 0; j < token.length; j++) {
                if (out[i + j] != token[j]) {
                    continue outer;
                }
            }
            System.arraycopy(value, 0, out, i, value.length);
            count++;
        }
        if (count == 0) {
            throw new IOException("Runtime self hash token is missing.");
        }
        return out;
    }

    private static int selfClassHash(byte[] classBytes, String runtimeClassName) {
        return integrityHash(normalizeSelfHashConstant(classBytes), runtimeClassName, "self:" + runtimeClassName);
    }

    private static byte[] normalizeSelfHashConstant(byte[] data) {
        byte[] out = data.clone();
        byte[] prefix = SELF_HASH_PREFIX.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = 0; i <= out.length - prefix.length - 8; i++) {
            boolean matched = true;
            for (int j = 0; j < prefix.length; j++) {
                if (out[i + j] != prefix[j]) {
                    matched = false;
                    break;
                }
            }
            if (!matched) {
                continue;
            }
            boolean hex = true;
            for (int j = 0; j < 8; j++) {
                if (!isHex(out[i + prefix.length + j])) {
                    hex = false;
                    break;
                }
            }
            if (hex) {
                for (int j = 0; j < 8; j++) {
                    out[i + prefix.length + j] = '0';
                }
            }
        }
        return out;
    }

    private static boolean isHex(byte value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    private static int integrityHash(byte[] payload, String runtimeClassName, String resourceName) {
        int hash = 0x53534943 ^ runtimeClassName.hashCode() ^ resourceName.hashCode() ^ payload.length;
        for (int i = 0; i < payload.length; i++) {
            hash ^= (payload[i] & 0xFF) * 0x45D9F3B;
            hash = Integer.rotateLeft(hash + 0x7F4A7C15 + i, 9);
            hash ^= hash >>> 16;
            hash *= 0x85EBCA6B;
        }
        hash ^= hash >>> 13;
        hash *= 0xC2B2AE35;
        hash ^= hash >>> 16;
        return hash == 0 ? 0x13579BDF : hash;
    }
}
