package biz.sushuo.shield.runtime;

public final class NativeOnlyRuntime {
    private static volatile int state = 0x13572468;
    private static final boolean NATIVE_READY = loadNative();
    private static final int NATIVE_MAGIC = 0x53534E32;
    private static final int NATIVE_VERSION = 2;

    private NativeOnlyRuntime() {
    }

    public static Object _v(Object[] program, Object[] args) {
        if (!NATIVE_READY) {
            throw new IllegalStateException("Native VM is required but unavailable.");
        }
        return NativeBridge._n(program, args);
    }

    public static String _d(String value, int key) {
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (chars[i] ^ mask(key, i));
        }
        return new String(chars);
    }

    public static String _d(String value, int key, int site, int salt) {
        String[] caller = callerContext();
        return _d(value, dynamicStringKey(key, site, salt, caller[0], caller[1]));
    }

    public static int _q(int encrypted, int key, int salt) {
        String[] caller = callerContext();
        return encrypted ^ dynamicImmediateKey(key, salt, caller[0], caller[1]);
    }

    public static int _i(int encrypted, int key, int site, int salt) {
        String[] caller = callerContext();
        return encrypted ^ dynamicIntKey(key, site, salt, caller[0], caller[1]);
    }

    public static long _l(long encrypted, long key, int site, int salt) {
        String[] caller = callerContext();
        return encrypted ^ dynamicLongKey(key, site, salt, caller[0], caller[1]);
    }

    public static String _m(String value) {
        if (value == null || !value.startsWith("SSM2:")) {
            return value;
        }
        int first = value.indexOf(':', 5);
        if (first < 0) {
            return value;
        }
        try {
            int key = (int) Long.parseUnsignedLong(value.substring(5, first), 36);
            String hex = value.substring(first + 1);
            byte[] data = new byte[hex.length() / 2];
            int local = key ^ 0x4D455441 ^ data.length;
            for (int i = 0; i < data.length; i++) {
                int high = Character.digit(hex.charAt(i * 2), 16);
                int low = Character.digit(hex.charAt(i * 2 + 1), 16);
                if (high < 0 || low < 0) {
                    return value;
                }
                local = metadataStream(local, i);
                data[i] = (byte) (((high << 4) | low) ^ (local >>> 24));
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    public static String[] _ma(String[] values) {
        if (values == null) {
            return null;
        }
        String[] decoded = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            decoded[i] = _m(values[i]);
        }
        return decoded;
    }

    public static String _sc(String recipe, Object[] args) {
        StringBuilder builder = new StringBuilder(recipe.length() + args.length * 8);
        int argIndex = 0;
        for (int i = 0; i < recipe.length(); i++) {
            char ch = recipe.charAt(i);
            if (ch == '\u0001' && argIndex < args.length) {
                builder.append(args[argIndex++]);
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    public static boolean _o() {
        int local = state;
        local ^= (int) System.nanoTime();
        local = Integer.rotateLeft(local + 0x45d9f3b, 7);
        state = local;
        return local != 0 || System.currentTimeMillis() >= 0L;
    }

    public static void _g(String owner, String method) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < trace.length - 1; i++) {
            StackTraceElement current = trace[i];
            if (owner.equals(current.getClassName()) && current.getMethodName().startsWith("_vp$")) {
                StackTraceElement caller = trace[i + 1];
                if (owner.equals(caller.getClassName()) && method.equals(caller.getMethodName())) {
                    return;
                }
                break;
            }
        }
        throw new IllegalStateException("VM program access denied.");
    }

    private static boolean loadNative() {
        try {
            return loadEmbeddedNative() || loadPathNative();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean loadEmbeddedNative() {
        String mapped = System.mapLibraryName("sushuo1337_vm");
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win") || !(arch.contains("64") || arch.equals("amd64") || arch.equals("x86_64"))) {
            return false;
        }
        String resource = nativeResourceName();
        try (java.io.InputStream input = NativeOnlyRuntime.class.getResourceAsStream(resource)) {
            if (input == null) {
                return false;
            }
            java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"),
                    "sushuo1337-" + Integer.toHexString(resource.hashCode()));
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            java.io.File lib = java.io.File.createTempFile("ssvm-", "-" + mapped, dir);
            lib.deleteOnExit();
            try (java.io.OutputStream output = new java.io.FileOutputStream(lib)) {
                output.write(decodeNative(input.readAllBytes(), resource));
            }
            System.load(lib.getAbsolutePath());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean loadPathNative() {
        String path = System.getProperty("java.library.path", "");
        String separator = System.getProperty("path.separator", ";");
        String library = System.mapLibraryName("sushuo1337_vm");
        for (String part : path.split(java.util.regex.Pattern.quote(separator))) {
            if (!part.isEmpty() && new java.io.File(part, library).isFile()) {
                System.loadLibrary("sushuo1337_vm");
                return true;
            }
        }
        return false;
    }

    private static String nativeResourceName() {
        return "%%SUSHUO_NATIVE_RESOURCE%%";
    }

    private static int mask(int key, int index) {
        int rotated = Integer.rotateLeft(key, index & 15);
        return rotated ^ (index * 0x45d9f3b) ^ (key >>> (index & 7));
    }

    private static int dynamicStringKey(int key, int site, int salt, String owner, String method) {
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

    private static int dynamicImmediateKey(int key, int salt, String owner, String method) {
        int mixed = key ^ salt ^ 0x9E3779B9;
        mixed ^= owner.hashCode();
        mixed ^= Integer.rotateLeft(method.hashCode(), 5);
        mixed = Integer.rotateLeft(mixed * 0x85EBCA6B, 13);
        mixed ^= mixed >>> 16;
        mixed *= 0xC2B2AE35;
        return mixed ^ (mixed >>> 13);
    }

    private static int dynamicIntKey(int key, int site, int salt, String owner, String method) {
        int mixed = key ^ Integer.rotateLeft(site * 0x27D4EB2D, 9) ^ salt;
        mixed ^= owner.hashCode();
        mixed = Integer.rotateLeft(mixed + 0x165667B1, 7);
        mixed ^= method.hashCode() * 0x85EBCA6B;
        mixed ^= mixed >>> 15;
        mixed *= 0xC2B2AE35;
        return mixed ^ (mixed >>> 16);
    }

    private static long dynamicLongKey(long key, int site, int salt, String owner, String method) {
        long mixed = key ^ (((long) site) << 32) ^ (salt & 0xFFFFFFFFL);
        mixed ^= owner.hashCode();
        mixed = Long.rotateLeft(mixed + 0x9E3779B97F4A7C15L, 17);
        mixed ^= ((long) method.hashCode()) * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static String[] callerContext() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        String runtime = NativeOnlyRuntime.class.getName();
        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            if (!className.equals(runtime) && !className.equals(Thread.class.getName())) {
                return new String[]{className, element.getMethodName()};
            }
        }
        return new String[]{runtime, ""};
    }

    private static byte[] decodeNative(byte[] data, String resource) {
        if (data.length >= 28) {
            try {
                java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.BIG_ENDIAN);
                if (header.getInt() == NATIVE_MAGIC) {
                    int version = header.getInt();
                    int nonce = header.getInt();
                    int keyTag = header.getInt();
                    int rawLength = header.getInt();
                    int payloadLength = header.getInt();
                    int expectedCrc = header.getInt();
                    if (version == NATIVE_VERSION && rawLength >= 0 && payloadLength >= 0
                            && payloadLength <= data.length - 28) {
                        byte[] payload = new byte[payloadLength];
                        header.get(payload);
                        String runtime = NativeOnlyRuntime.class.getName();
                        int key = keyTag ^ resource.hashCode() ^ runtime.hashCode() ^ NATIVE_MAGIC;
                        int local = nativeState(key, nonce, resource, runtime, rawLength, payloadLength);
                        for (int i = 0; i < payload.length; i++) {
                            local = nativeStream(local, i);
                            payload[i] = (byte) (payload[i] ^ (local >>> 24));
                        }
                        byte[] inflated = inflateNative(payload, rawLength);
                        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                        crc.update(inflated);
                        if ((int) crc.getValue() == expectedCrc) {
                            return inflated;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to legacy decoder.
            }
        }
        return decodeLegacyNative(data);
    }

    private static byte[] inflateNative(byte[] payload, int expectedLength) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(Math.max(expectedLength, 32));
        try (java.util.zip.InflaterInputStream inflater =
                     new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(payload))) {
            inflater.transferTo(output);
        }
        byte[] inflated = output.toByteArray();
        if (expectedLength >= 0 && inflated.length != expectedLength) {
            throw new java.io.IOException("Native payload length mismatch");
        }
        return inflated;
    }

    private static byte[] decodeLegacyNative(byte[] data) {
        byte[] decoded = data.clone();
        int local = 0x6D2B79F5 ^ decoded.length;
        for (int i = 0; i < decoded.length; i++) {
            local ^= i * 0x45D9F3B;
            local = Integer.rotateLeft(local + 0x7F4A7C15, 9);
            local ^= local >>> 13;
            local *= 0x5BD1E995;
            local ^= local >>> 15;
            decoded[i] = (byte) (decoded[i] ^ (local >>> 24));
        }
        return decoded;
    }

    private static int nativeState(int key, int nonce, String resource, String runtime, int rawLength, int payloadLength) {
        int local = key ^ nonce ^ resource.hashCode();
        local = mix(local ^ runtime.hashCode());
        local = mix(local ^ rawLength);
        return mix(local ^ payloadLength ^ 0x7F4A7C15);
    }

    private static int nativeStream(int local, int index) {
        local ^= index * 0x45D9F3B;
        local = Integer.rotateLeft(local + 0x7F4A7C15, 9);
        local ^= local >>> 13;
        local *= 0x5BD1E995;
        local ^= local >>> 15;
        return local;
    }

    private static int metadataStream(int local, int index) {
        local ^= index * 0x9E3779B9;
        local = Integer.rotateLeft(local + 0x7F4A7C15, 11);
        local ^= local >>> 16;
        local *= 0x85EBCA6B;
        local ^= local >>> 13;
        return local;
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
