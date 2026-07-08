package biz.sushuo.shield;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

final class ResourceEncryptor {
    private static final int MAGIC = 0x53535233; // SSR3
    private static final int VERSION = 1;

    private ResourceEncryptor() {
    }

    static boolean shouldEncrypt(String name, byte[] bytes, ObfuscationOptions options) {
        if (!options.encryptResources() || options.minecraftMode() || bytes.length >= 32 * 1024 * 1024) {
            return false;
        }
        if (isEncrypted(bytes)) {
            return false;
        }
        String normalized = name.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".class")) {
            return false;
        }
        if (lower.equals("meta-inf/manifest.mf") || lower.startsWith("meta-inf/")) {
            return false;
        }
        if (lower.equals("module-info.class") || lower.endsWith("/module-info.class")) {
            return false;
        }
        if (lower.equals("fabric.mod.json")
                || lower.equals("quilt.mod.json")
                || lower.equals("mods.toml")
                || lower.equals("mcmod.info")
                || lower.equals("pack.mcmeta")
                || lower.endsWith(".mixins.json")
                || lower.endsWith(".mixin.json")
                || lower.endsWith(".refmap.json")
                || lower.endsWith(".accesswidener")
                || lower.endsWith(".access-transformer.cfg")) {
            return false;
        }
        return true;
    }

    static byte[] encrypt(String name, byte[] bytes, String runtimeClassName, long seed) {
        int nonce = mix((int) seed
                ^ Integer.rotateLeft((int) (seed >>> 32), 11)
                ^ name.hashCode()
                ^ bytes.length
                ^ 0x52455331);
        int state = resourceState(nonce, bytes.length, runtimeClassName);
        byte[] out = new byte[16 + bytes.length];
        ByteBuffer header = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN);
        header.putInt(MAGIC);
        header.putInt(VERSION);
        header.putInt(nonce);
        header.putInt(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            state = resourceStream(state, i);
            out[16 + i] = (byte) (bytes[i] ^ (state >>> 24));
        }
        return out;
    }

    private static boolean isEncrypted(byte[] bytes) {
        if (bytes.length < 16) {
            return false;
        }
        return readInt(bytes, 0) == MAGIC && readInt(bytes, 4) == VERSION;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24
                | (bytes[offset + 1] & 0xFF) << 16
                | (bytes[offset + 2] & 0xFF) << 8
                | (bytes[offset + 3] & 0xFF);
    }

    private static int resourceState(int nonce, int length, String runtimeClassName) {
        int state = nonce ^ length ^ runtimeClassName.hashCode() ^ 0x53535233;
        state = mix(state ^ Integer.rotateLeft(runtimeClassName.length() * 0x45D9F3B, 7));
        return mix(state ^ 0x7F4A7C15);
    }

    private static int resourceStream(int state, int index) {
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
}
