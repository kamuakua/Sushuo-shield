package biz.sushuo.shield;

final class StringCipher {
    private StringCipher() {
    }

    static String encode(String value, int key) {
        return encodeWithKey(value, key);
    }

    static String encodeDynamic(String value, int key, int site, int salt, String owner, String method) {
        return encodeWithKey(value, dynamicKey(key, site, salt, owner.replace('/', '.'), method));
    }

    static String encodeMetadata(String value, int key) {
        byte[] data = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int state = key ^ 0x4D455441 ^ data.length;
        StringBuilder hex = new StringBuilder(data.length * 2);
        for (int i = 0; i < data.length; i++) {
            state = metadataStream(state, i);
            int encoded = (data[i] ^ (state >>> 24)) & 0xFF;
            hex.append(Character.forDigit(encoded >>> 4, 16));
            hex.append(Character.forDigit(encoded & 15, 16));
        }
        return "SSM2:" + Integer.toUnsignedString(key, 36) + ":" + hex;
    }

    static int dynamicKey(int key, int site, int salt, String owner, String method) {
        int mixed = key ^ Integer.rotateLeft(site * 0x45D9F3B, 7) ^ salt;
        mixed ^= owner.hashCode();
        mixed = Integer.rotateLeft(mixed + 0x7F4A7C15, 11);
        mixed ^= method.hashCode() * 0x5BD1E995;
        mixed ^= mixed >>> 16;
        mixed *= 0x85EBCA6B;
        mixed ^= mixed >>> 13;
        mixed *= 0xC2B2AE35;
        return mixed ^ (mixed >>> 16);
    }

    private static String encodeWithKey(String value, int key) {
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (chars[i] ^ mask(key, i));
        }
        return new String(chars);
    }

    private static int mask(int key, int index) {
        int rotated = Integer.rotateLeft(key, index & 15);
        return rotated ^ (index * 0x45d9f3b) ^ (key >>> (index & 7));
    }

    private static int metadataStream(int state, int index) {
        state ^= index * 0x9E3779B9;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 11);
        state ^= state >>> 16;
        state *= 0x85EBCA6B;
        state ^= state >>> 13;
        return state;
    }
}
