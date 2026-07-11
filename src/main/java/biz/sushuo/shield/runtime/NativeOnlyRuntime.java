package biz.sushuo.shield.runtime;

public final class NativeOnlyRuntime {
    private static volatile int state = 0x13572468;
    private static volatile int nativeLoadState;
    private static final int NATIVE_VERSION = 3;
    private static final int FLAG_NATIVE_KEY = 1;
    private static final int CONST_KIND_STRING = 1;
    private static final int CONST_KIND_INT = 2;
    private static final int CONST_KIND_LONG = 3;
    private static final int CONST_KIND_FLOAT = 4;
    private static final int CONST_KIND_DOUBLE = 5;
    private static final int CONST_KIND_METHOD_META = 7;
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


    public static Object _vx(Object programBox, Object argsBox, int token) {
        _o();
        Object[] program = vmArray(programBox, token);
        Object[] args = vmArray(argsBox, token ^ 0x56584142);
        if (token != vmCallToken(program, args.length)) {
            state ^= token;
            throw new IllegalStateException("x");
        }
        if (!nativeReady()) {
            throw new IllegalStateException("Native VM is required but unavailable.");
        }
        return NativeBridge._nx(program, args, token);
    }

    private static Object[] vmArray(Object box, int token) {
        if (box instanceof Object[] array) {
            return array;
        }
        state ^= token;
        throw new IllegalStateException("x");
    }

    private static int vmCallToken(Object[] program, int localSlots) {
        int value = 0x56584D31;
        value ^= vmProgramId(program) * 0x45D9F3B;
        value ^= Integer.rotateLeft(localSlots * 0x27D4EB2D, 7);
        return mix(value);
    }

    private static int vmProgramId(Object[] program) {
        if (program == null || program.length < 7 || !(program[0] instanceof Integer marker)) {
            return 0;
        }
        int index = marker.intValue() == 0x53535234 ? 8 : marker.intValue() == 0x53535632 ? 6 : -1;
        if (index < 0 || index >= program.length || !(program[index] instanceof Integer id)) {
            return 0;
        }
        return id.intValue();
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

    public static java.lang.invoke.CallSite _cs(java.lang.invoke.MethodHandles.Lookup lookup,
                                                String name,
                                                java.lang.invoke.MethodType type,
                                                String value,
                                                int key,
                                                int site,
                                                int salt,
                                                int flags) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        Class<?> owner = lookup.lookupClass();
        constantCallSiteGuard(owner);
        java.lang.invoke.MethodHandle target = java.lang.invoke.MethodHandles.lookup().findStatic(
                ownerRuntimeClass(), "_rcs",
                java.lang.invoke.MethodType.methodType(String.class, Class.class, String.class, String.class,
                        int.class, int.class, int.class, int.class));
        target = java.lang.invoke.MethodHandles.insertArguments(target, 0, owner, name, value, key, site, salt, flags);
        return new java.lang.invoke.MutableCallSite(target.asType(type));
    }

    public static java.lang.invoke.CallSite _ci(java.lang.invoke.MethodHandles.Lookup lookup,
                                                String name,
                                                java.lang.invoke.MethodType type,
                                                int encrypted,
                                                int key,
                                                int site,
                                                int salt,
                                                int flags) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        Class<?> owner = lookup.lookupClass();
        constantCallSiteGuard(owner);
        java.lang.invoke.MethodHandle target = java.lang.invoke.MethodHandles.lookup().findStatic(
                ownerRuntimeClass(), "_rci",
                java.lang.invoke.MethodType.methodType(int.class, int.class, Class.class, String.class,
                        int.class, int.class, int.class, int.class, int.class));
        target = java.lang.invoke.MethodHandles.insertArguments(target, 0, CONST_KIND_INT, owner, name, encrypted, key, site, salt, flags);
        return new java.lang.invoke.MutableCallSite(target.asType(type));
    }

    public static java.lang.invoke.CallSite _cl(java.lang.invoke.MethodHandles.Lookup lookup,
                                                String name,
                                                java.lang.invoke.MethodType type,
                                                long encrypted,
                                                long key,
                                                int site,
                                                int salt,
                                                int flags) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        Class<?> owner = lookup.lookupClass();
        constantCallSiteGuard(owner);
        java.lang.invoke.MethodHandle target = java.lang.invoke.MethodHandles.lookup().findStatic(
                ownerRuntimeClass(), "_rcl",
                java.lang.invoke.MethodType.methodType(long.class, int.class, Class.class, String.class,
                        long.class, long.class, int.class, int.class, int.class));
        target = java.lang.invoke.MethodHandles.insertArguments(target, 0, CONST_KIND_LONG, owner, name, encrypted, key, site, salt, flags);
        return new java.lang.invoke.MutableCallSite(target.asType(type));
    }

    public static java.lang.invoke.CallSite _cf(java.lang.invoke.MethodHandles.Lookup lookup,
                                                String name,
                                                java.lang.invoke.MethodType type,
                                                int encrypted,
                                                int key,
                                                int site,
                                                int salt,
                                                int flags) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        Class<?> owner = lookup.lookupClass();
        constantCallSiteGuard(owner);
        java.lang.invoke.MethodHandle target = java.lang.invoke.MethodHandles.lookup().findStatic(
                ownerRuntimeClass(), "_rcf",
                java.lang.invoke.MethodType.methodType(float.class, Class.class, String.class,
                        int.class, int.class, int.class, int.class, int.class));
        target = java.lang.invoke.MethodHandles.insertArguments(target, 0, owner, name, encrypted, key, site, salt, flags);
        return new java.lang.invoke.MutableCallSite(target.asType(type));
    }

    public static java.lang.invoke.CallSite _cd(java.lang.invoke.MethodHandles.Lookup lookup,
                                                String name,
                                                java.lang.invoke.MethodType type,
                                                long encrypted,
                                                long key,
                                                int site,
                                                int salt,
                                                int flags) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        Class<?> owner = lookup.lookupClass();
        constantCallSiteGuard(owner);
        java.lang.invoke.MethodHandle target = java.lang.invoke.MethodHandles.lookup().findStatic(
                ownerRuntimeClass(), "_rcd",
                java.lang.invoke.MethodType.methodType(double.class, Class.class, String.class,
                        long.class, long.class, int.class, int.class, int.class));
        target = java.lang.invoke.MethodHandles.insertArguments(target, 0, owner, name, encrypted, key, site, salt, flags);
        return new java.lang.invoke.MutableCallSite(target.asType(type));
    }

    public static java.lang.invoke.CallSite _rm(java.lang.invoke.MethodHandles.Lookup lookup,
                                                String name,
                                                java.lang.invoke.MethodType type,
                                                String part0,
                                                String part1,
                                                String part2,
                                                int seed,
                                                int salt,
                                                int flags,
                                                int check,
                                                int binding) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        Class<?> caller = lookup.lookupClass();
        constantCallSiteGuard(caller);
        Object[] meta = _rcmp(caller, name, type, part0, part1, part2,
                seed, salt, flags, check, binding);
        String owner = (String) meta[0];
        String targetName = (String) meta[1];
        String descriptor = (String) meta[2];
        int invokeOpcode = (Integer) meta[3];
        java.lang.invoke.MethodHandle target;
        try {
            target = _rcmr(type, owner, targetName, descriptor, invokeOpcode);
        } catch (Throwable ignored) {
            target = _rcmf(type, owner, targetName, descriptor, invokeOpcode);
        }
        return new java.lang.invoke.MutableCallSite(target.asType(type));
    }

    public static java.lang.invoke.CallSite _rv(java.lang.invoke.MethodHandles.Lookup lookup,
                                                String name,
                                                java.lang.invoke.MethodType type,
                                                int seed,
                                                int salt,
                                                int check) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        Class<?> caller = lookup.lookupClass();
        constantCallSiteGuard(caller);
        java.lang.invoke.MethodType expected = java.lang.invoke.MethodType.methodType(
                Object.class, Object.class, Object.class, int.class);
        int key = vmEntryIndyKey(seed, salt, caller, name, type);
        if (check != vmEntryIndyCheck(key, seed, salt)
                || type.parameterCount() != 3
                || type.returnType() != Object.class) {
            state ^= key;
            throw new IllegalStateException("x");
        }
        java.lang.invoke.MethodHandle target = java.lang.invoke.MethodHandles.lookup().findStatic(
                ownerRuntimeClass(), "%%SUSHUO_VM_ENTRY_TARGET%%", expected);
        return new java.lang.invoke.MutableCallSite(target.asType(type));
    }

    private static int vmEntryIndyKey(int seed, int salt, Class<?> caller,
                                      String name, java.lang.invoke.MethodType type) {
        String owner = caller.getName().replace('.', '/');
        String descriptor = type.toMethodDescriptorString();
        int value = mix(seed ^ owner.hashCode() ^ 0x56524931);
        value ^= Integer.rotateLeft(name.hashCode(), 7);
        value ^= Integer.rotateLeft(descriptor.hashCode(), 13);
        value = mix(value ^ salt ^ owner.length() * 0x45D9F3B);
        value ^= Integer.rotateLeft(descriptor.length() * 0x27D4EB2D, 9);
        return mix(value ^ 0x56524932);
    }

    private static int vmEntryIndyCheck(int key, int seed, int salt) {
        return mix(key ^ seed ^ Integer.rotateLeft(salt, 11) ^ 0x56524348);
    }

    private static Object[] _rcmp(Class<?> caller,
                                  String name,
                                  java.lang.invoke.MethodType type,
                                  String part0,
                                  String part1,
                                  String part2,
                                  int seed,
                                  int salt,
                                  int flags,
                                  int check,
                                  int binding) {
        String ownerName = caller.getName().replace('.', '/');
        String descriptor = type.toMethodDescriptorString();
        int key = methodMetaKey(seed, salt, ownerName, name, descriptor);
        if (binding != 0) {
            key ^= nativeIntKey(CONST_KIND_METHOD_META, caller, name,
                    seed, salt, descriptor.hashCode());
        }
        byte[][] shards = new byte[][]{hexToBytes(part0), hexToBytes(part1), hexToBytes(part2)};
        int length = shards[0].length + shards[1].length + shards[2].length;
        byte[] encrypted = new byte[length];
        int[] positions = new int[3];
        int shift = key & 7;
        for (int i = 0; i < encrypted.length; i++) {
            int lane = (i + shift) % 3;
            encrypted[i] = shards[lane][positions[lane]++];
        }
        int local = mix(key ^ encrypted.length ^ 0x4D43444D);
        for (int i = 0; i < encrypted.length; i++) {
            local = methodMetaStream(local, i);
            encrypted[i] = (byte) (encrypted[i] ^ (local >>> 24));
        }
        int expected = checksum(encrypted) ^ mix(key ^ Integer.rotateLeft(encrypted.length * 0x45D9F3B, 7) ^ 0x4348454B);
        int invokeOpcode = flags ^ mix(key ^ 0x4F50434F);
        if (check != expected || (invokeOpcode != 184 && invokeOpcode != 182 && invokeOpcode != 185)) {
            state ^= key;
            throw new IllegalStateException("x");
        }
        String clear = new String(encrypted, java.nio.charset.StandardCharsets.UTF_8);
        int first = clear.indexOf('\u001f');
        int second = first < 0 ? -1 : clear.indexOf('\u001f', first + 1);
        if (first <= 0 || second <= first + 1 || second >= clear.length() - 1) {
            state ^= key;
            throw new IllegalStateException("x");
        }
        return new Object[]{clear.substring(0, first), clear.substring(first + 1, second),
                clear.substring(second + 1), invokeOpcode};
    }

    private static byte[] hexToBytes(String value) {
        int length = value.length();
        if ((length & 1) != 0) {
            throw new IllegalStateException("x");
        }
        byte[] out = new byte[length / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalStateException("x");
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private static int methodMetaKey(int seed, int salt, String owner, String name, String descriptor) {
        int value = mix(seed ^ owner.hashCode() ^ 0x4D434B31);
        value ^= Integer.rotateLeft(name.hashCode(), 7);
        value ^= Integer.rotateLeft(descriptor.hashCode(), 13);
        value = mix(value ^ salt ^ owner.length() * 0x45D9F3B);
        value ^= Integer.rotateLeft(descriptor.length() * 0x27D4EB2D, 9);
        return mix(value ^ 0x4D434B32);
    }

    private static int methodMetaStream(int local, int index) {
        local ^= index * 0x9E3779B9;
        local = Integer.rotateLeft(local + 0x7F4A7C15, 11);
        local ^= local >>> 16;
        local *= 0x85EBCA6B;
        local ^= local >>> 13;
        local *= 0xC2B2AE35;
        local ^= local >>> 16;
        return local == 0 ? 0x13579BDF : local;
    }

    private static int checksum(byte[] bytes) {
        int value = 0x811C9DC5;
        for (byte b : bytes) {
            value ^= b & 0xFF;
            value *= 0x01000193;
            value = Integer.rotateLeft(value, 5) ^ 0x7F4A7C15;
        }
        return mix(value ^ bytes.length);
    }

    private static Class<?> ownerRuntimeClass() {
        return NativeOnlyRuntime.class;
    }

    private static String _rcs(Class<?> owner, String name, String value, int key, int site, int salt, int flags) {
        constantCallSiteGuard(owner);
        String ownerName = owner.getName();
        int decryptKey = (flags & FLAG_NATIVE_KEY) != 0
                ? nativeIntKey(CONST_KIND_STRING, owner, name, key, site, salt)
                : dynamicStringKey(key, site, salt, ownerName, name);
        return _d(value, decryptKey);
    }

    private static int _rci(int kind, Class<?> owner, String name, int encrypted, int key, int site, int salt, int flags) {
        constantCallSiteGuard(owner);
        String ownerName = owner.getName();
        int decryptKey = (flags & FLAG_NATIVE_KEY) != 0
                ? nativeIntKey(kind, owner, name, key, site, salt)
                : dynamicIntKey(key, site, salt, ownerName, name);
        return encrypted ^ decryptKey;
    }

    private static long _rcl(int kind, Class<?> owner, String name, long encrypted, long key, int site, int salt, int flags) {
        constantCallSiteGuard(owner);
        String ownerName = owner.getName();
        long decryptKey = (flags & FLAG_NATIVE_KEY) != 0
                ? nativeLongKey(kind, owner, name, key, site, salt)
                : dynamicLongKey(key, site, salt, ownerName, name);
        return encrypted ^ decryptKey;
    }

    private static float _rcf(Class<?> owner, String name, int encrypted, int key, int site, int salt, int flags) {
        return Float.intBitsToFloat(_rci(CONST_KIND_FLOAT, owner, name, encrypted, key, site, salt, flags));
    }

    private static double _rcd(Class<?> owner, String name, long encrypted, long key, int site, int salt, int flags) {
        return Double.longBitsToDouble(_rcl(CONST_KIND_DOUBLE, owner, name, encrypted, key, site, salt, flags));
    }

    private static java.lang.invoke.MethodHandle _rcmr(java.lang.invoke.MethodType callType,
                                                       String owner,
                                                       String name,
                                                       String descriptor,
                                                       int flags) throws ReflectiveOperationException {
        Class<?> ownerClass = classForInternal(owner);
        Class<?>[] parameterTypes = parameterTypes(descriptor);
        Class<?> returnType = returnType(descriptor);
        java.lang.invoke.MethodType methodType = java.lang.invoke.MethodType.methodType(returnType, parameterTypes);
        java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                ownerClass, java.lang.invoke.MethodHandles.lookup());
        java.lang.invoke.MethodHandle target;
        if (flags == 184) {
            target = lookup.findStatic(ownerClass, name, methodType);
        } else if (flags == 182 || flags == 185) {
            target = lookup.findVirtual(ownerClass, name, methodType);
        } else {
            throw new NoSuchMethodException(name);
        }
        return _rcmw(target.asType(callType));
    }

    private static java.lang.invoke.MethodHandle _rcmf(java.lang.invoke.MethodType callType,
                                                       String owner,
                                                       String name,
                                                       String descriptor,
                                                       int flags) throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        java.lang.invoke.MethodHandle target;
        if (flags == 184) {
            target = java.lang.invoke.MethodHandles.lookup().findStatic(
                    ownerRuntimeClass(), "_rcms",
                    java.lang.invoke.MethodType.methodType(Object.class,
                            String.class, String.class, String.class, int.class, Object[].class));
            target = java.lang.invoke.MethodHandles.insertArguments(target, 0, owner, name, descriptor, flags);
            target = target.asCollector(Object[].class, callType.parameterCount());
        } else {
            target = java.lang.invoke.MethodHandles.lookup().findStatic(
                    ownerRuntimeClass(), "_rcmi",
                    java.lang.invoke.MethodType.methodType(Object.class,
                            String.class, String.class, String.class, int.class, Object.class, Object[].class));
            target = java.lang.invoke.MethodHandles.insertArguments(target, 0, owner, name, descriptor, flags);
            target = target.asCollector(Object[].class, Math.max(0, callType.parameterCount() - 1));
        }
        return target.asType(callType);
    }

    private static java.lang.invoke.MethodHandle _rcmw(java.lang.invoke.MethodHandle target)
            throws java.lang.NoSuchMethodException, java.lang.IllegalAccessException {
        java.lang.invoke.MethodHandle guard = java.lang.invoke.MethodHandles.lookup().findStatic(
                ownerRuntimeClass(), "_rcmg", java.lang.invoke.MethodType.methodType(void.class));
        return java.lang.invoke.MethodHandles.foldArguments(target, guard);
    }

    private static void _rcmg() {
        _o();
    }

    private static Object _rcms(String owner, String name, String descriptor, int flags, Object[] args) {
        _o();
        return _rcmv(owner, name, descriptor, args, null, flags);
    }

    private static Object _rcmi(String owner, String name, String descriptor, int flags, Object target, Object[] args) {
        _o();
        return _rcmv(owner, name, descriptor, args, target, flags);
    }

    private static Object _rcmv(String owner, String name, String descriptor, Object[] args, Object target, int invokeOpcode) {
        try {
            Class<?> type = classForInternal(owner);
            Class<?>[] parameterTypes = parameterTypes(descriptor);
            java.lang.reflect.Method method = findMethod(type, name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(invokeOpcode == 184 ? null : target, coerceArgs(parameterTypes, args));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int nativeIntKey(int kind, Class<?> owner, String name, int key, int site, int salt) {
        constantCallSiteGuard(owner);
        if (!nativeReady()) {
            throw new IllegalStateException("x");
        }
        return NativeBridge._ki(kind, owner, name, key, site, salt);
    }

    private static long nativeLongKey(int kind, Class<?> owner, String name, long key, int site, int salt) {
        constantCallSiteGuard(owner);
        if (!nativeReady()) {
            throw new IllegalStateException("x");
        }
        return NativeBridge._kl(kind, owner, name, key, site, salt);
    }

    private static void constantCallSiteGuard(Class<?> owner) {
        String expected = owner.getName();
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : trace) {
            if (expected.equals(element.getClassName())) {
                return;
            }
        }
        throw new IllegalStateException("x");
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
            Class<?> returnType = returnType(descriptor);
            java.lang.invoke.MethodType methodType = java.lang.invoke.MethodType.methodType(returnType, parameterTypes);
            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                    type, java.lang.invoke.MethodHandles.lookup());
            java.lang.invoke.MethodHandle handle = invokeOpcode == 184
                    ? lookup.findStatic(type, name, methodType)
                    : lookup.findVirtual(type, name, methodType).bindTo(target);
            Object value = handle.invokeWithArguments(coerceArgs(parameterTypes, args));
            return returnType == Void.TYPE ? Void.TYPE : normalizeReturn(returnType, value);
        } catch (Throwable ex) {
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

    public static void _gh(int ownerHash, int methodHash) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        int ownerFrames = 0;
        boolean hostFrame = false;
        String firstOwnerMethod = null;
        for (StackTraceElement frame : trace) {
            if (frame.getClassName().hashCode() != ownerHash) {
                continue;
            }
            ownerFrames++;
            if (firstOwnerMethod == null) {
                firstOwnerMethod = frame.getMethodName();
            } else if (!firstOwnerMethod.equals(frame.getMethodName())) {
                ownerFrames++;
            }
            if (frame.getMethodName().hashCode() == methodHash) {
                hostFrame = true;
            }
        }
        if (ownerFrames >= 2 && hostFrame) {
            return;
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
            java.io.File lib = java.io.File.createTempFile(tempNativePrefix(resource, mapped), tempNativeSuffix(resource, mapped), dir);
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

    private static String tempNativePrefix(String resource, String mapped) {
        int a = mix(resource.hashCode() ^ Integer.rotateLeft(mapped.hashCode(), 7) ^ 0x544D5031);
        int b = mix(a ^ resource.length() * 0x45D9F3B ^ mapped.length() * 0x27D4EB2D);
        String value = "j" + Integer.toUnsignedString(a, 36) + Integer.toUnsignedString(b, 36);
        return value.length() >= 3 ? value : (value + "x7q");
    }

    private static String tempNativeSuffix(String resource, String mapped) {
        int a = mix(resource.hashCode() ^ Integer.rotateLeft(mapped.hashCode(), 11) ^ 0x544D5032);
        String ext = mapped.endsWith(".dll") ? ".dll" : "." + Integer.toUnsignedString(a, 36);
        return "-" + Integer.toUnsignedString(a, 36) + ext;
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
                String runtime = NativeOnlyRuntime.class.getName();
                int resourceHash = resource.hashCode();
                int runtimeHash = runtime.hashCode();
                int format = nativeFormat(resourceHash, runtimeHash, data.length);
                int headerFormat = header.getInt() ^ nativeHeaderMask(resourceHash, runtimeHash, data.length, 0);
                if (headerFormat == format) {
                    int version = header.getInt() ^ nativeHeaderMask(resourceHash, runtimeHash, data.length, 1);
                    int nonce = header.getInt() ^ nativeHeaderMask(resourceHash, runtimeHash, data.length, 2);
                    int keyTag = header.getInt() ^ nativeHeaderMask(resourceHash, runtimeHash, data.length, 3);
                    int rawLength = header.getInt() ^ nativeHeaderMask(resourceHash, runtimeHash, data.length, 4);
                    int payloadLength = header.getInt() ^ nativeHeaderMask(resourceHash, runtimeHash, data.length, 5);
                    int expectedCrc = header.getInt() ^ nativeHeaderMask(resourceHash, runtimeHash, data.length, 6);
                    if (version == NATIVE_VERSION && rawLength >= 0 && payloadLength >= 0
                            && payloadLength <= data.length - 28) {
                        byte[] payload = new byte[payloadLength];
                        header.get(payload);
                        int key = keyTag ^ resourceHash ^ runtimeHash ^ format;
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

    private static int nativeFormat(int resourceHash, int runtimeHash, int totalLength) {
        int value = 0x4E464D33 ^ resourceHash;
        value ^= Integer.rotateLeft(runtimeHash, 7);
        value ^= Integer.rotateLeft(totalLength * 0x27D4EB2D, 11);
        value ^= Integer.rotateLeft(resourceHash * 0x45D9F3B, 3);
        return mix(value ^ 0x7F4A7C15);
    }

    private static int nativeHeaderMask(int resourceHash, int runtimeHash, int totalLength, int slot) {
        int value = 0x4E485244 ^ resourceHash;
        value ^= Integer.rotateLeft(runtimeHash, (slot * 5 + 7) & 31);
        value ^= Integer.rotateLeft(totalLength * 0x45D9F3B, (slot + 3) & 31);
        value ^= slot * 0x9E3779B9;
        value = Integer.rotateLeft(value + 0x7F4A7C15, 9);
        value ^= value >>> 16;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        value *= 0xC2B2AE35;
        value ^= value >>> 16;
        return value == 0 ? 0x2468ACE1 : value;
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
