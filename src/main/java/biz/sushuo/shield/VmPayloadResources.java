package biz.sushuo.shield;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VmPayloadResources {
    static final int RESOURCE_MARKER = 0x53535234; // SSR4
    static final int RESOURCE_VERSION = 4;
    static final int CODE_SALT = 0x41C64E6D;
    static final int MAP_SALT = 0x27D4EB2D;
    static final int PACKED_CHUNK_BYTES = 64;
    private static final String[] RESOURCE_BUCKETS = {
            "assets", "res", "cfg", "lib", "packs", "modules"
    };
    private static final String[] RESOURCE_EXTENSIONS = {
            ".bin", ".dat", ".res", ".pak", ".idx", ".cfg"
    };
    private static final int RESOURCE_SALT = 0x6A09E667;
    private static final int SEAL_SALT = 0x4B455931;
    private static final int CONSTANT_NULL = 0;
    private static final int CONSTANT_INT = 1;
    private static final int CONSTANT_LONG = 2;
    private static final int CONSTANT_FLOAT = 3;
    private static final int CONSTANT_DOUBLE = 4;
    private static final int CONSTANT_STRING = 5;

    private final NamingPlan plan;
    private final long seed;
    private final byte[] nativeSecret;
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    VmPayloadResources(NamingPlan plan, long seed) {
        this.plan = plan;
        this.seed = seed;
        this.nativeSecret = nativeSecret(plan, seed);
    }

    String add(VirtualProgram program, String originalOwner, String originalMethod, String descriptor) {
        String resourceName = resourceName(program, originalOwner, originalMethod, descriptor);
        int salt = 0;
        while (resources.containsKey(resourceName)) {
            salt++;
            resourceName = resourceName(program, originalOwner + "#" + salt, originalMethod, descriptor);
        }
        String runtimeName = "/" + resourceName;
        resources.put(resourceName, pack(program, runtimeName));
        return runtimeName;
    }

    Map<String, byte[]> resources() {
        return resources;
    }

    static int guardToken(String dottedOwner, String hostMethod, int site) {
        int value = SEAL_SALT ^ dottedOwner.hashCode();
        value ^= Integer.rotateLeft(site * 0x45D9F3B, 7);
        value = Integer.rotateLeft(value + 0x7F4A7C15, 11);
        value ^= hostMethod.hashCode() * 0x5BD1E995;
        value ^= value >>> 16;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        value *= 0xC2B2AE35;
        value ^= value >>> 16;
        return value == 0 ? 0x2468ACE1 : value;
    }

    static int sealKey(VirtualProgram program) {
        int token = guardToken(program.owner().replace('/', '.'), program.hostMethod(), program.id());
        return program.key() ^ token ^ SEAL_SALT;
    }

    static String sealResourceName(VirtualProgram program) {
        int token = guardToken(program.owner().replace('/', '.'), program.hostMethod(), program.id());
        char[] chars = program.resourceName().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (chars[i] ^ sealMask(token, program.id(), i));
        }
        return new String(chars);
    }

    static byte[] nativeSecret(NamingPlan plan, long seed) {
        byte[] secret = new byte[16];
        int a = mix((int) seed ^ plan.runtimeClassName().hashCode() ^ 0x13579BDF);
        int b = mix((int) (seed >>> 32) ^ plan.nativeResourceName().hashCode() ^ 0x2468ACE1);
        int c = mix(a ^ Integer.rotateLeft(b, 7) ^ plan.runtimeClassName().length());
        int d = mix(b ^ Integer.rotateLeft(a, 11) ^ plan.nativeResourceName().length());
        putSecretWord(secret, 0, a);
        putSecretWord(secret, 4, b);
        putSecretWord(secret, 8, c);
        putSecretWord(secret, 12, d);
        return secret;
    }

    static int constantMask32(NamingPlan plan, long seed, int kind, String dottedOwner,
                              String indyName, int key, int site, int salt) {
        byte[] secret = nativeSecret(plan, seed);
        int state = 0x434B3332 ^ kind ^ key ^ salt;
        state ^= Integer.rotateLeft(site * 0x45D9F3B, 7);
        state ^= dottedOwner.hashCode();
        state = mix(state ^ secretWord(secret, 0));
        state ^= Integer.rotateLeft(indyName.hashCode(), 11);
        state = mix(state ^ secretWord(secret, 1));
        state ^= Integer.rotateLeft(secretWord(secret, 2), site & 31);
        state = mix(state + secretWord(secret, 3) + dottedOwner.length() * 0x27D4EB2D);
        return mix(state ^ indyName.length() * 0x9E3779B9);
    }

    static String distributedVmResourceName(String runtimeClassName, int a, int b, int c, int selector) {
        int slash = runtimeClassName.lastIndexOf('/');
        String base = slash < 0 ? "" : runtimeClassName.substring(0, slash + 1);
        return distributedResourceName(base, a, b, c, selector);
    }

    static String distributedResourceName(String prefix, int a, int b, int c, int selector) {
        String base = prefix == null || prefix.isEmpty() ? "" : prefix.endsWith("/") ? prefix : prefix + "/";
        int route = Math.floorMod(selector, RESOURCE_BUCKETS.length);
        String first = token(a);
        String second = token(b);
        String third = token(c);
        String leaf = "p"
                + token(a ^ Integer.rotateLeft(c, 7))
                + token(b ^ Integer.rotateLeft(selector, 11));
        String extension = RESOURCE_EXTENSIONS[Math.floorMod(selector >>> 8, RESOURCE_EXTENSIONS.length)];
        return switch (route) {
            case 0 -> base + RESOURCE_BUCKETS[route] + "/" + first + "/" + leaf + extension;
            case 1 -> base + RESOURCE_BUCKETS[route] + "/" + first + "/" + second + "/" + leaf + extension;
            case 2 -> base + RESOURCE_BUCKETS[route] + "/" + second + "/" + leaf + extension;
            case 3 -> base + RESOURCE_BUCKETS[route] + "/" + third + "/" + leaf + extension;
            case 4 -> base + RESOURCE_BUCKETS[route] + "/" + first + second.charAt(0) + "/" + leaf + extension;
            default -> base + RESOURCE_BUCKETS[route] + "/" + second + "/" + third + "/" + leaf + extension;
        };
    }

    private static String token(int value) {
        return Integer.toUnsignedString(mix(value), 36);
    }

    static long constantMask64(NamingPlan plan, long seed, int kind, String dottedOwner,
                               String indyName, long key, int site, int salt) {
        byte[] secret = nativeSecret(plan, seed);
        long state = 0x434B36344A4E494CL ^ key ^ ((long) kind << 48)
                ^ ((long) salt & 0xFFFFFFFFL) ^ ((long) site << 32);
        state ^= ((long) dottedOwner.hashCode()) * 0x9E3779B97F4A7C15L;
        state ^= ((long) indyName.hashCode()) * 0xBF58476D1CE4E5B9L;
        state ^= ((long) secretWord(secret, 0) << 32) ^ (secretWord(secret, 1) & 0xFFFFFFFFL);
        state = mix64(state);
        state ^= Long.rotateLeft(((long) secretWord(secret, 2) << 32)
                ^ (secretWord(secret, 3) & 0xFFFFFFFFL), site & 63);
        state ^= ((long) dottedOwner.length() << 17) ^ indyName.length() * 0x94D049BB133111EBL;
        return mix64(state);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value == 0 ? 0x13579BDF2468ACE1L : value;
    }

    private static int sealMask(int token, int site, int index) {
        int value = token ^ Integer.rotateLeft(site * 0x27D4EB2D, 9);
        value ^= index * 0x9E3779B9;
        value = Integer.rotateLeft(value + 0x165667B1, 7);
        value ^= value >>> 15;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        return value & 0xFFFF;
    }

    private String resourceName(VirtualProgram program, String originalOwner, String originalMethod, String descriptor) {
        int a = mix((int) seed ^ program.key() ^ originalOwner.hashCode());
        int b = mix((int) (seed >>> 32) ^ program.id() ^ originalMethod.hashCode());
        int c = mix(program.code().length ^ descriptor.hashCode() ^ Integer.rotateLeft(program.key(), 11));
        int selector = mix(a
                ^ Integer.rotateLeft(b, 5)
                ^ Integer.rotateLeft(c, 17)
                ^ program.returnKind() * 0x45D9F3B
                ^ descriptor.length() * 0x27D4EB2D);
        return distributedVmResourceName(plan.runtimeClassName(), a, b, c, selector);
    }

    private byte[] pack(VirtualProgram program, String runtimeName) {
        byte[] code = packCode(program.code(), program.key(), CODE_SALT);
        byte[] metadata = packMetadata(program);
        byte[] payload = new byte[code.length + metadata.length];
        System.arraycopy(code, 0, payload, 0, code.length);
        System.arraycopy(metadata, 0, payload, code.length, metadata.length);
        int resourceHash = runtimeName.hashCode();
        int nonce = mix(program.key()
                ^ resourceHash
                ^ Integer.rotateLeft(program.code().length, 5)
                ^ Integer.rotateLeft(payload.length, 13)
                ^ RESOURCE_SALT);
        byte[] encoded = payload.clone();
        int totalLength = 28 + encoded.length;
        int format = resourceFormat(resourceHash, totalLength);
        int keyTag = program.key() ^ resourceHash ^ format ^ nonce;
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = (byte) (encoded[i] ^ resourceMask(program.key(), nonce,
                    resourceHash, program.code().length, payload.length, i, nativeSecret));
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(format ^ resourceHeaderMask(resourceHash, totalLength, 0));
        buffer.putInt(RESOURCE_VERSION ^ resourceHeaderMask(resourceHash, totalLength, 1));
        buffer.putInt(nonce ^ resourceHeaderMask(resourceHash, totalLength, 2));
        buffer.putInt(keyTag ^ resourceHeaderMask(resourceHash, totalLength, 3));
        buffer.putInt(program.code().length ^ resourceHeaderMask(resourceHash, totalLength, 4));
        buffer.putInt(encoded.length ^ resourceHeaderMask(resourceHash, totalLength, 5));
        buffer.putInt(contextTag(program, resourceHash) ^ resourceHeaderMask(resourceHash, totalLength, 6));
        buffer.put(encoded);
        return buffer.array();
    }

    private int resourceFormat(int resourceHash, int totalLength) {
        int value = 0x52464D34 ^ resourceHash ^ totalLength;
        value ^= secretWord(nativeSecret, 0);
        value ^= Integer.rotateLeft(secretWord(nativeSecret, 1), 9);
        value ^= Integer.rotateLeft(secretWord(nativeSecret, 2), totalLength & 31);
        return mix(value ^ secretWord(nativeSecret, 3));
    }

    private int resourceHeaderMask(int resourceHash, int totalLength, int slot) {
        int value = 0x56485244 ^ resourceHash;
        value ^= Integer.rotateLeft(totalLength * 0x45D9F3B, (slot + 5) & 31);
        value ^= secretWord(nativeSecret, slot & 3);
        value ^= Integer.rotateLeft(secretWord(nativeSecret, (slot + 1) & 3), (slot * 7 + 3) & 31);
        value ^= slot * 0x9E3779B9;
        value = Integer.rotateLeft(value + 0x7F4A7C15, 9);
        value ^= value >>> 16;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        value *= 0xC2B2AE35;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static int contextTag(VirtualProgram program, int resourceHash) {
        int ownerHash = program.owner().replace('/', '.').hashCode();
        int methodHash = program.hostMethod().hashCode();
        int value = program.key() ^ resourceHash ^ RESOURCE_SALT;
        value ^= Integer.rotateLeft(program.id() * 0x45D9F3B, 7);
        value ^= Integer.rotateLeft(ownerHash, 11);
        value ^= Integer.rotateLeft(methodHash, 17);
        value ^= Integer.rotateLeft(program.code().length * 0x27D4EB2D, 5);
        value ^= Integer.rotateLeft(program.constants().size() * 0x9E3779B9, 13);
        value ^= program.returnKind() * 0x5BD1E995;
        return mix(value);
    }

    private static byte[] packMetadata(VirtualProgram program) {
        ByteWriter writer = new ByteWriter();
        int[] encodedMap = encode(program.opcodeMap(), program.key(), MAP_SALT);
        writer.writeInt(encodedMap.length);
        for (int value : encodedMap) {
            writer.writeInt(value);
        }

        List<Object> constants = new ArrayList<>(program.constants());
        writer.writeInt(constants.size());
        for (Object constant : constants) {
            if (constant == null) {
                writer.writeByte(CONSTANT_NULL);
            } else if (constant instanceof Integer value) {
                writer.writeByte(CONSTANT_INT);
                writer.writeInt(value);
            } else if (constant instanceof Long value) {
                writer.writeByte(CONSTANT_LONG);
                writer.writeLong(value);
            } else if (constant instanceof Float value) {
                writer.writeByte(CONSTANT_FLOAT);
                writer.writeInt(Float.floatToIntBits(value));
            } else if (constant instanceof Double value) {
                writer.writeByte(CONSTANT_DOUBLE);
                writer.writeLong(Double.doubleToLongBits(value));
            } else if (constant instanceof String value) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                writer.writeByte(CONSTANT_STRING);
                writer.writeInt(bytes.length);
                writer.writeBytes(bytes);
            } else {
                throw new IllegalArgumentException("Unsupported VM constant: " + constant);
            }
        }
        return writer.toByteArray();
    }

    private static byte[] packCode(int[] values, int key, int salt) {
        byte[] packed = new byte[values.length * Integer.BYTES];
        for (int i = 0; i < values.length; i++) {
            int encoded = encodePacked(values[i], key, salt, i, values.length);
            int offset = i * Integer.BYTES;
            packed[offset] = (byte) (encoded >>> 24);
            packed[offset + 1] = (byte) (encoded >>> 16);
            packed[offset + 2] = (byte) (encoded >>> 8);
            packed[offset + 3] = (byte) encoded;
        }
        return packed;
    }

    private static int encodePacked(int value, int key, int salt, int index, int length) {
        int state = packedStream(key, salt, index, length);
        int rotate = (state >>> 27) & 15;
        return Integer.rotateLeft(value ^ state ^ index, rotate);
    }

    private static int[] encode(int[] values, int key, int salt) {
        int[] encoded = new int[values.length];
        int state = key ^ salt ^ values.length;
        for (int i = 0; i < values.length; i++) {
            state = stream(state, i);
            int rotate = (state >>> 27) & 15;
            encoded[i] = Integer.rotateLeft(values[i] ^ state ^ i, rotate);
        }
        return encoded;
    }

    private static int packedStream(int key, int salt, int index, int length) {
        int state = key ^ salt ^ length ^ Integer.rotateLeft(index * 0x9E3779B9, 5);
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }

    private static int stream(int state, int index) {
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }

    private static int resourceMask(int key, int nonce, int resourceHash, int codeLength,
                                    int payloadLength, int index, byte[] secret) {
        int state = key ^ nonce ^ resourceHash ^ RESOURCE_SALT;
        state ^= secretWord(secret, 0);
        state ^= Integer.rotateLeft(secretWord(secret, 1), index & 31);
        state ^= Integer.rotateLeft(secretWord(secret, 2), (index >>> 3) & 31);
        state ^= secretWord(secret, 3) + payloadLength;
        state ^= Integer.rotateLeft(codeLength * 0x45D9F3B, 7);
        state ^= Integer.rotateLeft(payloadLength * 0x27D4EB2D, 11);
        state ^= Integer.rotateLeft(index * 0x9E3779B9, 3);
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 16;
        state *= 0x85EBCA6B;
        state ^= state >>> 13;
        state *= 0xC2B2AE35;
        state ^= state >>> 16;
        return state >>> 24;
    }

    private static int secretWord(byte[] secret, int lane) {
        int offset = lane * 4;
        return ((secret[offset] & 0xFF) << 24)
                | ((secret[offset + 1] & 0xFF) << 16)
                | ((secret[offset + 2] & 0xFF) << 8)
                | (secret[offset + 3] & 0xFF);
    }

    private static void putSecretWord(byte[] secret, int offset, int value) {
        secret[offset] = (byte) (value >>> 24);
        secret[offset + 1] = (byte) (value >>> 16);
        secret[offset + 2] = (byte) (value >>> 8);
        secret[offset + 3] = (byte) value;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static final class ByteWriter {
        private byte[] data = new byte[128];
        private int size;

        void writeByte(int value) {
            ensure(1);
            data[size++] = (byte) value;
        }

        void writeInt(int value) {
            ensure(4);
            data[size++] = (byte) (value >>> 24);
            data[size++] = (byte) (value >>> 16);
            data[size++] = (byte) (value >>> 8);
            data[size++] = (byte) value;
        }

        void writeLong(long value) {
            ensure(8);
            data[size++] = (byte) (value >>> 56);
            data[size++] = (byte) (value >>> 48);
            data[size++] = (byte) (value >>> 40);
            data[size++] = (byte) (value >>> 32);
            data[size++] = (byte) (value >>> 24);
            data[size++] = (byte) (value >>> 16);
            data[size++] = (byte) (value >>> 8);
            data[size++] = (byte) value;
        }

        void writeBytes(byte[] values) {
            ensure(values.length);
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        byte[] toByteArray() {
            return java.util.Arrays.copyOf(data, size);
        }

        private void ensure(int extra) {
            int required = size + extra;
            if (required <= data.length) {
                return;
            }
            int next = data.length;
            while (next < required) {
                next *= 2;
            }
            data = java.util.Arrays.copyOf(data, next);
        }
    }
}
