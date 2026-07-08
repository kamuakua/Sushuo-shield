package biz.sushuo.shield.runtime;

public final class NativeOnlyRuntime {
    private static volatile int state = 0x13572468;
    private static volatile int nativeLoadState;
    private static final int NATIVE_MAGIC = 0x53534E32;
    private static final int NATIVE_VERSION = 2;
    private static final boolean ANTI_DEBUG = Boolean.parseBoolean("%%SUSHUO_ANTI_DEBUG%%");
    private static final boolean ANTI_VM = Boolean.parseBoolean("%%SUSHUO_ANTI_VM%%");
    private static final int LICENSE_HASH = parseOptionInt("%%SUSHUO_LICENSE_HASH%%");
    private static final String INTEGRITY_RESOURCE = "%%SUSHUO_INTEGRITY_RESOURCE%%";
    private static final int INTEGRITY_HASH = parseOptionInt("%%SUSHUO_INTEGRITY_HASH%%");
    private static final String SELF_HASH = "J$7e9a2d4f00000000";
    private static final String SELF_HASH_PREFIX = "J$7e9a2d4f";
    private static volatile int integrityState;

    private NativeOnlyRuntime() {
    }

    public static Object _v(Object[] program, Object[] args) {
        _o();
        if (!nativeReady()) {
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

    public static Object _rs(String owner, String name, String descriptor, Object[] args) {
        _o();
        return invoke(owner, name, descriptor, args);
    }

    public static Object _ri(String owner, String name, String descriptor, Object target, Object[] args, int invokeOpcode) {
        _o();
        return invoke(owner, name, descriptor, args, target, invokeOpcode);
    }

    public static boolean _o() {
        securityCheck();
        int local = state;
        local ^= (int) System.nanoTime();
        local = Integer.rotateLeft(local + 0x45d9f3b, 7);
        state = local;
        return local != 0 || System.currentTimeMillis() >= 0L;
    }

    private static void securityCheck() {
        if (ANTI_DEBUG && debuggerPresent()) {
            throw new IllegalStateException("Protected application cannot run under debugger.");
        }
        if (ANTI_VM && virtualMachineEnvironment()) {
            throw new IllegalStateException("Protected application cannot run inside this VM environment.");
        }
        if (LICENSE_HASH != 0 && !licenseAccepted()) {
            throw new IllegalStateException("Protected application license check failed.");
        }
        if (INTEGRITY_HASH != 0 && !integrityAccepted()) {
            throw new IllegalStateException("Protected application integrity check failed.");
        }
    }

    private static boolean debuggerPresent() {
        try {
            java.util.List<String> args = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                String lower = arg.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("jdwp") || lower.contains("-xdebug")
                        || lower.contains("javaagent") && lower.contains("debug")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return Boolean.getBoolean("%%SUSHUO_DEBUGGER_PROPERTY%%");
    }

    private static boolean virtualMachineEnvironment() {
        String joined = (System.getProperty("java.vm.name", "") + ' '
                + System.getProperty("java.vm.vendor", "") + ' '
                + System.getProperty("os.name", "") + ' '
                + System.getenv("PROCESSOR_IDENTIFIER") + ' '
                + System.getenv("COMPUTERNAME")).toLowerCase(java.util.Locale.ROOT);
        return joined.contains("virtualbox")
                || joined.contains("vmware")
                || joined.contains("qemu")
                || joined.contains("bochs")
                || joined.contains("xen")
                || joined.contains("hyper-v")
                || joined.contains("vbox");
    }

    private static boolean licenseAccepted() {
        String license = System.getProperty("%%SUSHUO_LICENSE_PROPERTY%%");
        if (license == null || license.isEmpty()) {
            license = System.getenv("%%SUSHUO_LICENSE_ENV%%");
        }
        return license != null && licenseHash(license) == LICENSE_HASH;
    }

    private static int licenseHash(String value) {
        int hash = 0x53534C4B;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i) * 0x45D9F3B;
            hash = Integer.rotateLeft(hash + 0x7F4A7C15, 9);
            hash ^= hash >>> 16;
            hash *= 0x85EBCA6B;
        }
        hash ^= hash >>> 13;
        hash *= 0xC2B2AE35;
        hash ^= hash >>> 16;
        return hash == 0 ? 0x13579BDF : hash;
    }

    private static int parseOptionInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean integrityAccepted() {
        int local = integrityState;
        if (local == 1) {
            return true;
        }
        if (local == 2) {
            return false;
        }
        synchronized (NativeOnlyRuntime.class) {
            local = integrityState;
            if (local == 0) {
                integrityState = verifyIntegrityResource() ? 1 : 2;
            }
            return integrityState == 1;
        }
    }

    private static boolean verifyIntegrityResource() {
        if (INTEGRITY_RESOURCE == null || INTEGRITY_RESOURCE.isEmpty() || INTEGRITY_RESOURCE.startsWith("%%")) {
            return false;
        }
        if (!verifySelfClass()) {
            return false;
        }
        try (java.io.InputStream input = NativeOnlyRuntime.class.getResourceAsStream(INTEGRITY_RESOURCE)) {
            if (input == null) {
                return false;
            }
            byte[] data = input.readAllBytes();
            return integrityHash(data, NativeOnlyRuntime.class.getName(), INTEGRITY_RESOURCE) == INTEGRITY_HASH;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean verifySelfClass() {
        int expected = parseSelfHash();
        if (expected == 0) {
            return false;
        }
        String runtimeName = NativeOnlyRuntime.class.getName();
        String classResource = "/" + runtimeName.replace('.', '/') + ".class";
        try (java.io.InputStream input = NativeOnlyRuntime.class.getResourceAsStream(classResource)) {
            if (input == null) {
                return false;
            }
            byte[] data = input.readAllBytes();
            byte[] normalized = normalizeSelfHashConstant(data);
            return integrityHash(normalized, runtimeName, "self:" + runtimeName) == expected;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int parseSelfHash() {
        try {
            if (SELF_HASH == null || !SELF_HASH.startsWith(SELF_HASH_PREFIX)
                    || SELF_HASH.length() < SELF_HASH_PREFIX.length() + 8) {
                return 0;
            }
            long value = Long.parseLong(SELF_HASH.substring(SELF_HASH_PREFIX.length(), SELF_HASH_PREFIX.length() + 8), 16);
            return (int) value;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static byte[] normalizeSelfHashConstant(byte[] data) {
        byte[] out = data.clone();
        byte[] prefix = SELF_HASH_PREFIX.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
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

    private static Object invoke(String owner, String name, String descriptor, Object[] args) {
        return invoke(owner, name, descriptor, args, null, 184);
    }

    private static Object invoke(String owner, String name, String descriptor, Object[] args, Object target, int invokeOpcode) {
        try {
            Class<?> type = classForInternal(owner);
            Class<?>[] parameterTypes = parameterTypes(descriptor);
            java.lang.reflect.Method method = findMethod(type, name, parameterTypes);
            method.setAccessible(true);
            Object value = method.invoke(invokeOpcode == 184 ? null : target, coerceArgs(parameterTypes, args));
            Class<?> returnType = returnType(descriptor);
            return returnType == Void.TYPE ? Void.TYPE : normalizeReturn(returnType, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static java.lang.reflect.Method findMethod(Class<?> type, String name, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return type.getMethod(name, parameterTypes);
    }

    private static Object normalizeReturn(Class<?> type, Object value) {
        if (type == Void.TYPE) {
            return Void.TYPE;
        }
        if (type == Boolean.TYPE) {
            return Boolean.TRUE.equals(value) ? 1 : 0;
        }
        if (type == Character.TYPE) {
            return value instanceof Character character ? (int) character.charValue() : value;
        }
        if (type == Byte.TYPE || type == Short.TYPE) {
            return ((Number) value).intValue();
        }
        return value;
    }

    private static Object[] coerceArgs(Class<?>[] parameterTypes, Object[] args) {
        Object[] coerced = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            coerced[i] = coerce(parameterTypes[i], args[i]);
        }
        return coerced;
    }

    private static Object coerce(Class<?> target, Object value) {
        if (!target.isPrimitive() || value == null) {
            return value;
        }
        if (target == Boolean.TYPE) {
            return value instanceof Boolean bool ? bool : ((Number) value).intValue() != 0;
        }
        if (target == Character.TYPE) {
            return value instanceof Character character ? character : (char) ((Number) value).intValue();
        }
        if (target == Byte.TYPE) {
            return ((Number) value).byteValue();
        }
        if (target == Short.TYPE) {
            return ((Number) value).shortValue();
        }
        if (target == Integer.TYPE) {
            return ((Number) value).intValue();
        }
        if (target == Long.TYPE) {
            return ((Number) value).longValue();
        }
        if (target == Float.TYPE) {
            return ((Number) value).floatValue();
        }
        if (target == Double.TYPE) {
            return ((Number) value).doubleValue();
        }
        return value;
    }

    private static Class<?>[] parameterTypes(String descriptor) throws ClassNotFoundException {
        java.util.ArrayList<Class<?>> types = new java.util.ArrayList<>();
        int index = 1;
        while (descriptor.charAt(index) != ')') {
            types.add(parseClass(descriptor, index));
            index = nextTypeIndex(descriptor, index);
        }
        return types.toArray(Class<?>[]::new);
    }

    private static Class<?> returnType(String descriptor) throws ClassNotFoundException {
        return parseClass(descriptor, descriptor.indexOf(')') + 1);
    }

    private static Class<?> parseClass(String descriptor, int index) throws ClassNotFoundException {
        char kind = descriptor.charAt(index);
        return switch (kind) {
            case 'V' -> Void.TYPE;
            case 'Z' -> Boolean.TYPE;
            case 'C' -> Character.TYPE;
            case 'B' -> Byte.TYPE;
            case 'S' -> Short.TYPE;
            case 'I' -> Integer.TYPE;
            case 'J' -> Long.TYPE;
            case 'F' -> Float.TYPE;
            case 'D' -> Double.TYPE;
            case 'L' -> {
                int end = descriptor.indexOf(';', index);
                yield Class.forName(descriptor.substring(index + 1, end).replace('/', '.'));
            }
            case '[' -> {
                int cursor = nextTypeIndex(descriptor, index);
                yield Class.forName(descriptor.substring(index, cursor).replace('/', '.'));
            }
            default -> throw new IllegalArgumentException("Bad descriptor: " + descriptor);
        };
    }

    private static Class<?> classForInternal(String internalName) throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'));
    }

    private static int nextTypeIndex(String descriptor, int index) {
        int cursor = index;
        while (descriptor.charAt(cursor) == '[') {
            cursor++;
        }
        if (descriptor.charAt(cursor) == 'L') {
            return descriptor.indexOf(';', cursor) + 1;
        }
        return cursor + 1;
    }

    public static void _g(String owner, String method) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < trace.length - 1; i++) {
            StackTraceElement current = trace[i];
            StackTraceElement caller = trace[i + 1];
            if (owner.equals(current.getClassName())
                    && owner.equals(caller.getClassName())
                    && method.equals(caller.getMethodName())
                    && !method.equals(current.getMethodName())) {
                return;
            }
        }
        throw new IllegalStateException("VM program access denied.");
    }

    public static java.io.InputStream _rl(ClassLoader loader, String name) {
        _o();
        java.io.InputStream in = loader == null
                ? ClassLoader.getSystemResourceAsStream(name)
                : loader.getResourceAsStream(name);
        return resourceStream(in);
    }

    public static java.io.InputStream _rc(Class clazz, String name) {
        _o();
        if (clazz == null) {
            return null;
        }
        return resourceStream(clazz.getResourceAsStream(name));
    }

    public static java.io.InputStream _rg(String name) {
        _o();
        return resourceStream(ClassLoader.getSystemResourceAsStream(name));
    }

    private static java.io.InputStream resourceStream(java.io.InputStream in) {
        if (in == null) {
            return null;
        }
        try {
            byte[] data = in.readAllBytes();
            if (data.length < 16 || readInt(data, 0) != 0x53535233 || readInt(data, 4) != 1) {
                return new java.io.ByteArrayInputStream(data);
            }
            int nonce = readInt(data, 8);
            int length = readInt(data, 12);
            if (length < 0 || length > data.length - 16) {
                return new java.io.ByteArrayInputStream(data);
            }
            byte[] decoded = new byte[length];
            int local = resourceState(nonce, length, NativeOnlyRuntime.class.getName());
            for (int i = 0; i < length; i++) {
                local = resourceStream(local, i);
                decoded[i] = (byte) (data[16 + i] ^ (local >>> 24));
            }
            return new java.io.ByteArrayInputStream(decoded);
        } catch (java.io.IOException ignored) {
            return null;
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
            }
        }
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24
                | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8
                | (data[offset + 3] & 0xFF);
    }

    private static int resourceState(int nonce, int length, String runtimeClassName) {
        int local = nonce ^ length ^ runtimeClassName.hashCode() ^ 0x53535233;
        local = mix(local ^ Integer.rotateLeft(runtimeClassName.length() * 0x45D9F3B, 7));
        return mix(local ^ 0x7F4A7C15);
    }

    private static int resourceStream(int local, int index) {
        local ^= index * 0x45D9F3B;
        local = Integer.rotateLeft(local + 0x7F4A7C15, 9);
        local ^= local >>> 13;
        local *= 0x5BD1E995;
        local ^= local >>> 15;
        return local;
    }

    private static boolean loadNative() {
        try {
            return loadEmbeddedNative() || loadPathNative();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean nativeReady() {
        int local = nativeLoadState;
        if (local == 1) {
            return true;
        }
        if (local == 2) {
            return false;
        }
        synchronized (NativeOnlyRuntime.class) {
            local = nativeLoadState;
            if (local == 0) {
                nativeLoadState = loadNative() ? 1 : 2;
            }
            return nativeLoadState == 1;
        }
    }

    private static boolean loadEmbeddedNative() {
        String mapped = System.mapLibraryName("%%SUSHUO_NATIVE_LIBRARY%%");
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
                    "j" + Integer.toHexString(resource.hashCode()));
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
        String library = System.mapLibraryName("%%SUSHUO_NATIVE_LIBRARY%%");
        for (String part : path.split(java.util.regex.Pattern.quote(separator))) {
            if (!part.isEmpty() && new java.io.File(part, library).isFile()) {
                System.loadLibrary("%%SUSHUO_NATIVE_LIBRARY%%");
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
