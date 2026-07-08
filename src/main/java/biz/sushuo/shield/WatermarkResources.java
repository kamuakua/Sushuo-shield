package biz.sushuo.shield;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

final class WatermarkResources {
    private WatermarkResources() {
    }

    static Result create(ObfuscationOptions options, NamingPlan namingPlan,
                         Map<String, byte[]> outputClasses,
                         Map<String, byte[]> nativeResources,
                         Map<String, byte[]> vmResources) {
        if (!enabled(options)) {
            return Result.empty();
        }
        String name = resourceName(options, namingPlan);
        int entryHash = entryHash(outputClasses, nativeResources, vmResources);
        byte[] payload = payload(options, namingPlan, name, entryHash);
        int integrityHash = integrityHash(payload, namingPlan.runtimeClassName().replace('/', '.'), "/" + name);
        return new Result(name, integrityHash, Map.of(name, payload));
    }

    static int integrityHash(byte[] payload, String runtimeClassName, String resourceName) {
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

    private static boolean enabled(ObfuscationOptions options) {
        return options.antiAiDeobfuscation() && !options.minecraftMode();
    }

    private static String resourceName(ObfuscationOptions options, NamingPlan namingPlan) {
        int a = mix((int) options.seed() ^ namingPlan.runtimeClassName().hashCode() ^ 0x574D4B31);
        int b = mix((int) (options.seed() >>> 32) ^ namingPlan.nativeResourceName().hashCode() ^ 0x574D4B32);
        return namingPlan.namePrefix() + "meta/W"
                + Integer.toUnsignedString(a, 36)
                + Integer.toUnsignedString(b, 36)
                + ".bin";
    }

    private static byte[] payload(ObfuscationOptions options, NamingPlan namingPlan, String name, int entryHash) {
        String text = "SSWM2\n"
                + "mode=" + options.mode().name().toLowerCase(java.util.Locale.ROOT) + '\n'
                + "runtime=" + namingPlan.runtimeClassName().replace('/', '.') + '\n'
                + "resource=" + name + '\n'
                + "entryHash=" + Integer.toUnsignedString(entryHash, 16) + '\n'
                + "seedTag=" + Integer.toUnsignedString(mix((int) options.seed()), 16) + '\n';
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int key = mix((int) options.seed() ^ name.hashCode() ^ namingPlan.runtimeClassName().hashCode());
        byte[] encoded = bytes.clone();
        for (int i = 0; i < encoded.length; i++) {
            key = stream(key, i);
            encoded[i] = (byte) (encoded[i] ^ (key >>> 24));
        }
        byte[] out = new byte[8 + encoded.length];
        out[0] = 'S';
        out[1] = 'S';
        out[2] = 'W';
        out[3] = 'M';
        out[4] = (byte) (entryHash >>> 24);
        out[5] = (byte) (entryHash >>> 16);
        out[6] = (byte) (entryHash >>> 8);
        out[7] = (byte) entryHash;
        System.arraycopy(encoded, 0, out, 8, encoded.length);
        return out;
    }

    private static int entryHash(Map<String, byte[]>... maps) {
        Map<String, byte[]> ordered = new TreeMap<>();
        for (Map<String, byte[]> map : maps) {
            ordered.putAll(map);
        }
        int hash = 0x5741544D;
        for (Map.Entry<String, byte[]> entry : ordered.entrySet()) {
            hash = hashString(hash, entry.getKey());
            byte[] bytes = entry.getValue();
            hash ^= bytes.length * 0x45D9F3B;
            for (int i = 0; i < bytes.length; i++) {
                hash ^= (bytes[i] & 0xFF) + i * 0x27D4EB2D;
                hash = Integer.rotateLeft(hash + 0x7F4A7C15, 7);
                hash *= 0x5BD1E995;
            }
        }
        return mix(hash);
    }

    private static int hashString(int hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i) * 0x9E3779B9;
            hash = Integer.rotateLeft(hash + 0x165667B1, 11);
        }
        return hash;
    }

    private static int stream(int state, int index) {
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
        return value == 0 ? 0x2468ACE1 : value;
    }

    record Result(String resourceName, int integrityHash, Map<String, byte[]> resources) {
        static Result empty() {
            return new Result("", 0, Map.of());
        }
    }
}
