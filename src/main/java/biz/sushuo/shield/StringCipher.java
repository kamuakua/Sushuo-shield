package biz.sushuo.shield;

final class StringCipher {
    private StringCipher() {
    }

    static String encode(String value, int key) {
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
}
