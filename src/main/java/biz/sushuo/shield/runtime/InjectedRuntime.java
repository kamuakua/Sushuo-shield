package biz.sushuo.shield.runtime;

public final class InjectedRuntime {
    private static volatile int state = 0x13572468;
    private static volatile int nativeLoadState;
    private static final int ENCODED_MARKER = 0x53535632;
    private static final int CODE_SALT = 0x41C64E6D;
    private static final int MAP_SALT = 0x27D4EB2D;
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

    private static final int PUSH_CONST = 1;
    private static final int LOAD = 2;
    private static final int STORE = 3;
    private static final int POP = 4;
    private static final int DUP = 5;
    private static final int IADD = 10;
    private static final int ISUB = 11;
    private static final int IMUL = 12;
    private static final int IDIV = 13;
    private static final int IREM = 14;
    private static final int INEG = 15;
    private static final int IXOR = 16;
    private static final int IAND = 17;
    private static final int IOR = 18;
    private static final int ISHL = 19;
    private static final int LADD = 20;
    private static final int LSUB = 21;
    private static final int LMUL = 22;
    private static final int LDIV = 23;
    private static final int LREM = 24;
    private static final int LNEG = 25;
    private static final int LXOR = 26;
    private static final int ISHR = 27;
    private static final int IUSHR = 28;
    private static final int LSHL = 29;
    private static final int FADD = 30;
    private static final int FSUB = 31;
    private static final int FMUL = 32;
    private static final int FDIV = 33;
    private static final int FREM = 34;
    private static final int FNEG = 35;
    private static final int LSHR = 36;
    private static final int LUSHR = 37;
    private static final int DADD = 40;
    private static final int DSUB = 41;
    private static final int DMUL = 42;
    private static final int DDIV = 43;
    private static final int DREM = 44;
    private static final int DNEG = 45;
    private static final int I2L = 50;
    private static final int I2F = 51;
    private static final int I2D = 52;
    private static final int L2I = 53;
    private static final int L2F = 54;
    private static final int L2D = 55;
    private static final int F2I = 56;
    private static final int F2L = 57;
    private static final int F2D = 58;
    private static final int D2I = 59;
    private static final int D2L = 60;
    private static final int D2F = 61;
    private static final int I2B = 62;
    private static final int I2C = 63;
    private static final int I2S = 64;
    private static final int LCMP = 65;
    private static final int FCMPL = 66;
    private static final int FCMPG = 67;
    private static final int DCMPL = 68;
    private static final int DCMPG = 69;
    private static final int INVOKE_STATIC = 70;
    private static final int IINC = 71;
    private static final int GET_STATIC = 72;
    private static final int PUT_STATIC = 73;
    private static final int GET_FIELD = 74;
    private static final int PUT_FIELD = 75;
    private static final int INVOKE = 76;
    private static final int CHECKCAST = 77;
    private static final int INSTANCEOF = 78;
    private static final int GOTO = 79;
    private static final int RETURN = 90;
    private static final int IFEQ = 91;
    private static final int IFNE = 92;
    private static final int IFLT = 93;
    private static final int IFGE = 94;
    private static final int IFGT = 95;
    private static final int IFLE = 96;
    private static final int IF_ICMPEQ = 97;
    private static final int IF_ICMPNE = 98;
    private static final int IF_ICMPLT = 99;
    private static final int IF_ICMPGE = 100;
    private static final int IF_ICMPGT = 101;
    private static final int IF_ICMPLE = 102;
    private static final int IF_ACMPEQ = 103;
    private static final int IF_ACMPNE = 104;
    private static final int IFNULL = 105;
    private static final int IFNONNULL = 106;

    private InjectedRuntime() {
    }

    public static Object _v(Object[] program, Object[] args) {
        _o();
        boolean nativeRequired = embeddedNativeRequired() || Boolean.getBoolean("%%SUSHUO_NATIVE_REQUIRED_PROPERTY%%");
        if (nativeReady()) {
            try {
                return NativeBridge._n(program, args);
            } catch (UnsupportedOperationException ignored) {
                if (nativeRequired) {
                    throw ignored;
                }
                // Fall back when the native VM sees an opcode that needs Java reflection.
            } catch (UnsatisfiedLinkError ignored) {
                if (nativeRequired) {
                    throw ignored;
                }
                // The native bridge is optional; protected jars remain runnable without it.
            }
        } else if (nativeRequired) {
            throw new IllegalStateException("Native VM is required but unavailable.");
        }
        return _j(program, args);
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
            int state = key ^ 0x4D455441 ^ data.length;
            for (int i = 0; i < data.length; i++) {
                int high = Character.digit(hex.charAt(i * 2), 16);
                int low = Character.digit(hex.charAt(i * 2 + 1), 16);
                if (high < 0 || low < 0) {
                    return value;
                }
                state = metadataStream(state, i);
                data[i] = (byte) (((high << 4) | low) ^ (state >>> 24));
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
        synchronized (InjectedRuntime.class) {
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
        try (java.io.InputStream input = InjectedRuntime.class.getResourceAsStream(INTEGRITY_RESOURCE)) {
            if (input == null) {
                return false;
            }
            byte[] data = input.readAllBytes();
            return integrityHash(data, InjectedRuntime.class.getName(), INTEGRITY_RESOURCE) == INTEGRITY_HASH;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean verifySelfClass() {
        int expected = parseSelfHash();
        if (expected == 0) {
            return false;
        }
        String runtimeName = InjectedRuntime.class.getName();
        String classResource = "/" + runtimeName.replace('.', '/') + ".class";
        try (java.io.InputStream input = InjectedRuntime.class.getResourceAsStream(classResource)) {
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
            int local = resourceState(nonce, length, InjectedRuntime.class.getName());
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
        local = nativeMix(local ^ Integer.rotateLeft(runtimeClassName.length() * 0x45D9F3B, 7));
        return nativeMix(local ^ 0x7F4A7C15);
    }

    private static int resourceStream(int local, int index) {
        local ^= index * 0x45D9F3B;
        local = Integer.rotateLeft(local + 0x7F4A7C15, 9);
        local ^= local >>> 13;
        local *= 0x5BD1E995;
        local ^= local >>> 15;
        return local;
    }

    private static Object _j(Object[] program, Object[] args) {
        boolean encoded = isEncoded(program);
        int maxLocals = ((Integer) program[encoded ? 1 : 0]).intValue();
        int[] code = (int[]) program[encoded ? 4 : 3];
        Object[] constants = (Object[]) program[encoded ? 5 : 4];
        int[] opcodeDecode = null;
        if (encoded) {
            int key = ((Integer) program[7]).intValue();
            code = decode(code, key, CODE_SALT);
            opcodeDecode = reverseMap(decode((int[]) program[8], key, MAP_SALT));
        }
        Object[] locals = new Object[Math.max(maxLocals, args.length)];
        System.arraycopy(args, 0, locals, 0, args.length);
        Object[] stack = new Object[Math.max(32, code.length + 4)];
        int sp = 0;
        int pc = 0;
        while (pc < code.length) {
            int op = code[pc++];
            if (opcodeDecode != null) {
                op = opcodeDecode[op];
            }
            switch (op) {
                case PUSH_CONST -> stack[sp++] = constants[code[pc++]];
                case LOAD -> stack[sp++] = locals[code[pc++]];
                case STORE -> locals[code[pc++]] = stack[--sp];
                case POP -> sp--;
                case DUP -> {
                    stack[sp] = stack[sp - 1];
                    sp++;
                }
                case IADD -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() + ((Number) stack[sp - 1]).intValue();
                case ISUB -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() - ((Number) stack[sp - 1]).intValue();
                case IMUL -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() * ((Number) stack[sp - 1]).intValue();
                case IDIV -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() / ((Number) stack[sp - 1]).intValue();
                case IREM -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() % ((Number) stack[sp - 1]).intValue();
                case INEG -> stack[sp - 1] = -((Number) stack[sp - 1]).intValue();
                case IXOR -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() ^ ((Number) stack[sp - 1]).intValue();
                case IAND -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() & ((Number) stack[sp - 1]).intValue();
                case IOR -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() | ((Number) stack[sp - 1]).intValue();
                case ISHL -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() << ((Number) stack[sp - 1]).intValue();
                case ISHR -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() >> ((Number) stack[sp - 1]).intValue();
                case IUSHR -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() >>> ((Number) stack[sp - 1]).intValue();
                case LADD -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() + ((Number) stack[sp - 1]).longValue();
                case LSUB -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() - ((Number) stack[sp - 1]).longValue();
                case LMUL -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() * ((Number) stack[sp - 1]).longValue();
                case LDIV -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() / ((Number) stack[sp - 1]).longValue();
                case LREM -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() % ((Number) stack[sp - 1]).longValue();
                case LNEG -> stack[sp - 1] = -((Number) stack[sp - 1]).longValue();
                case LXOR -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() ^ ((Number) stack[sp - 1]).longValue();
                case LSHL -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() << ((Number) stack[sp - 1]).intValue();
                case LSHR -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() >> ((Number) stack[sp - 1]).intValue();
                case LUSHR -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() >>> ((Number) stack[sp - 1]).intValue();
                case FADD -> stack[sp - 2] = ((Number) stack[sp - 2]).floatValue() + ((Number) stack[sp - 1]).floatValue();
                case FSUB -> stack[sp - 2] = ((Number) stack[sp - 2]).floatValue() - ((Number) stack[sp - 1]).floatValue();
                case FMUL -> stack[sp - 2] = ((Number) stack[sp - 2]).floatValue() * ((Number) stack[sp - 1]).floatValue();
                case FDIV -> stack[sp - 2] = ((Number) stack[sp - 2]).floatValue() / ((Number) stack[sp - 1]).floatValue();
                case FREM -> stack[sp - 2] = ((Number) stack[sp - 2]).floatValue() % ((Number) stack[sp - 1]).floatValue();
                case FNEG -> stack[sp - 1] = -((Number) stack[sp - 1]).floatValue();
                case DADD -> stack[sp - 2] = ((Number) stack[sp - 2]).doubleValue() + ((Number) stack[sp - 1]).doubleValue();
                case DSUB -> stack[sp - 2] = ((Number) stack[sp - 2]).doubleValue() - ((Number) stack[sp - 1]).doubleValue();
                case DMUL -> stack[sp - 2] = ((Number) stack[sp - 2]).doubleValue() * ((Number) stack[sp - 1]).doubleValue();
                case DDIV -> stack[sp - 2] = ((Number) stack[sp - 2]).doubleValue() / ((Number) stack[sp - 1]).doubleValue();
                case DREM -> stack[sp - 2] = ((Number) stack[sp - 2]).doubleValue() % ((Number) stack[sp - 1]).doubleValue();
                case DNEG -> stack[sp - 1] = -((Number) stack[sp - 1]).doubleValue();
                case I2L -> stack[sp - 1] = ((Number) stack[sp - 1]).longValue();
                case I2F -> stack[sp - 1] = ((Number) stack[sp - 1]).floatValue();
                case I2D -> stack[sp - 1] = ((Number) stack[sp - 1]).doubleValue();
                case L2I -> stack[sp - 1] = ((Number) stack[sp - 1]).intValue();
                case L2F -> stack[sp - 1] = ((Number) stack[sp - 1]).floatValue();
                case L2D -> stack[sp - 1] = ((Number) stack[sp - 1]).doubleValue();
                case F2I -> stack[sp - 1] = ((Number) stack[sp - 1]).intValue();
                case F2L -> stack[sp - 1] = ((Number) stack[sp - 1]).longValue();
                case F2D -> stack[sp - 1] = ((Number) stack[sp - 1]).doubleValue();
                case D2I -> stack[sp - 1] = ((Number) stack[sp - 1]).intValue();
                case D2L -> stack[sp - 1] = ((Number) stack[sp - 1]).longValue();
                case D2F -> stack[sp - 1] = ((Number) stack[sp - 1]).floatValue();
                case I2B -> stack[sp - 1] = (int) (byte) ((Number) stack[sp - 1]).intValue();
                case I2C -> stack[sp - 1] = (int) (char) ((Number) stack[sp - 1]).intValue();
                case I2S -> stack[sp - 1] = (int) (short) ((Number) stack[sp - 1]).intValue();
                case LCMP -> stack[sp - 2] = Long.compare(((Number) stack[sp - 2]).longValue(), ((Number) stack[sp - 1]).longValue());
                case FCMPL -> stack[sp - 2] = compareFloat(((Number) stack[sp - 2]).floatValue(), ((Number) stack[sp - 1]).floatValue(), -1);
                case FCMPG -> stack[sp - 2] = compareFloat(((Number) stack[sp - 2]).floatValue(), ((Number) stack[sp - 1]).floatValue(), 1);
                case DCMPL -> stack[sp - 2] = compareDouble(((Number) stack[sp - 2]).doubleValue(), ((Number) stack[sp - 1]).doubleValue(), -1);
                case DCMPG -> stack[sp - 2] = compareDouble(((Number) stack[sp - 2]).doubleValue(), ((Number) stack[sp - 1]).doubleValue(), 1);
                case INVOKE_STATIC -> {
                    String owner = (String) constants[code[pc++]];
                    String name = (String) constants[code[pc++]];
                    String descriptor = (String) constants[code[pc++]];
                    int argc = code[pc++];
                    Object[] callArgs = new Object[argc];
                    for (int i = argc - 1; i >= 0; i--) {
                        callArgs[i] = stack[--sp];
                    }
                    Object result = invoke(owner, name, descriptor, callArgs);
                    if (result != Void.TYPE) {
                        stack[sp++] = result;
                    }
                }
                case GET_STATIC -> {
                    String owner = (String) constants[code[pc++]];
                    String name = (String) constants[code[pc++]];
                    String descriptor = (String) constants[code[pc++]];
                    stack[sp++] = getField(owner, name, descriptor, null);
                }
                case PUT_STATIC -> {
                    String owner = (String) constants[code[pc++]];
                    String name = (String) constants[code[pc++]];
                    String descriptor = (String) constants[code[pc++]];
                    putField(owner, name, descriptor, null, stack[--sp]);
                }
                case GET_FIELD -> {
                    String owner = (String) constants[code[pc++]];
                    String name = (String) constants[code[pc++]];
                    String descriptor = (String) constants[code[pc++]];
                    Object target = stack[--sp];
                    stack[sp++] = getField(owner, name, descriptor, target);
                }
                case PUT_FIELD -> {
                    String owner = (String) constants[code[pc++]];
                    String name = (String) constants[code[pc++]];
                    String descriptor = (String) constants[code[pc++]];
                    Object value = stack[--sp];
                    Object target = stack[--sp];
                    putField(owner, name, descriptor, target, value);
                }
                case INVOKE -> {
                    String owner = (String) constants[code[pc++]];
                    String name = (String) constants[code[pc++]];
                    String descriptor = (String) constants[code[pc++]];
                    int argc = code[pc++];
                    int invokeOpcode = code[pc++];
                    Object[] callArgs = new Object[argc];
                    for (int i = argc - 1; i >= 0; i--) {
                        callArgs[i] = stack[--sp];
                    }
                    Object target = stack[--sp];
                    Object result = invoke(owner, name, descriptor, callArgs, target, invokeOpcode);
                    if (result != Void.TYPE) {
                        stack[sp++] = result;
                    }
                }
                case CHECKCAST -> {
                    try {
                        Class<?> type = classForInternal((String) constants[code[pc++]]);
                        if (stack[sp - 1] != null) {
                            stack[sp - 1] = type.cast(stack[sp - 1]);
                        }
                    } catch (ClassNotFoundException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                case INSTANCEOF -> {
                    try {
                        Class<?> type = classForInternal((String) constants[code[pc++]]);
                        Object value = stack[--sp];
                        stack[sp++] = type.isInstance(value) ? 1 : 0;
                    } catch (ClassNotFoundException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                case GOTO -> pc = code[pc];
                case IFEQ -> {
                    int target = code[pc++];
                    if (asInt(stack[--sp]) == 0) pc = target;
                }
                case IFNE -> {
                    int target = code[pc++];
                    if (asInt(stack[--sp]) != 0) pc = target;
                }
                case IFLT -> {
                    int target = code[pc++];
                    if (asInt(stack[--sp]) < 0) pc = target;
                }
                case IFGE -> {
                    int target = code[pc++];
                    if (asInt(stack[--sp]) >= 0) pc = target;
                }
                case IFGT -> {
                    int target = code[pc++];
                    if (asInt(stack[--sp]) > 0) pc = target;
                }
                case IFLE -> {
                    int target = code[pc++];
                    if (asInt(stack[--sp]) <= 0) pc = target;
                }
                case IF_ICMPEQ -> {
                    int target = code[pc++];
                    int right = asInt(stack[--sp]);
                    int left = asInt(stack[--sp]);
                    if (left == right) pc = target;
                }
                case IF_ICMPNE -> {
                    int target = code[pc++];
                    int right = asInt(stack[--sp]);
                    int left = asInt(stack[--sp]);
                    if (left != right) pc = target;
                }
                case IF_ICMPLT -> {
                    int target = code[pc++];
                    int right = asInt(stack[--sp]);
                    int left = asInt(stack[--sp]);
                    if (left < right) pc = target;
                }
                case IF_ICMPGE -> {
                    int target = code[pc++];
                    int right = asInt(stack[--sp]);
                    int left = asInt(stack[--sp]);
                    if (left >= right) pc = target;
                }
                case IF_ICMPGT -> {
                    int target = code[pc++];
                    int right = asInt(stack[--sp]);
                    int left = asInt(stack[--sp]);
                    if (left > right) pc = target;
                }
                case IF_ICMPLE -> {
                    int target = code[pc++];
                    int right = asInt(stack[--sp]);
                    int left = asInt(stack[--sp]);
                    if (left <= right) pc = target;
                }
                case IF_ACMPEQ -> {
                    int target = code[pc++];
                    Object right = stack[--sp];
                    Object left = stack[--sp];
                    if (left == right) pc = target;
                }
                case IF_ACMPNE -> {
                    int target = code[pc++];
                    Object right = stack[--sp];
                    Object left = stack[--sp];
                    if (left != right) pc = target;
                }
                case IFNULL -> {
                    int target = code[pc++];
                    if (stack[--sp] == null) pc = target;
                }
                case IFNONNULL -> {
                    int target = code[pc++];
                    if (stack[--sp] != null) pc = target;
                }
                case IINC -> {
                    int local = code[pc++];
                    int increment = code[pc++];
                    locals[local] = asInt(locals[local]) + increment;
                }
                case RETURN -> {
                    return sp == 0 ? null : stack[--sp];
                }
                default -> throw new IllegalStateException("Bad VM opcode " + op);
            }
            if (isBinaryMath(op)) {
                sp--;
            }
        }
        return null;
    }

    private static boolean isBinaryMath(int op) {
        return switch (op) {
            case IADD, ISUB, IMUL, IDIV, IREM, IXOR, IAND, IOR, ISHL, ISHR, IUSHR,
                    LADD, LSUB, LMUL, LDIV, LREM, LXOR, LSHL, LSHR, LUSHR,
                    FADD, FSUB, FMUL, FDIV, FREM,
                    DADD, DSUB, DMUL, DDIV, DREM,
                    LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> true;
            default -> false;
        };
    }

    private static boolean isEncoded(Object[] program) {
        return program.length >= 9
                && program[0] instanceof Integer marker
                && marker.intValue() == ENCODED_MARKER;
    }

    private static int[] decode(int[] values, int key, int salt) {
        int[] decoded = new int[values.length];
        int state = key ^ salt ^ values.length;
        for (int i = 0; i < values.length; i++) {
            state = stream(state, i);
            int rotate = (state >>> 27) & 15;
            decoded[i] = Integer.rotateRight(values[i], rotate) ^ state ^ i;
        }
        return decoded;
    }

    private static int stream(int state, int index) {
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }

    private static int[] reverseMap(int[] opcodeMap) {
        int max = 0;
        for (int opcode : opcodeMap) {
            if (opcode > max) {
                max = opcode;
            }
        }
        int[] reverse = new int[Math.max(max + 1, 128)];
        for (int logical = 0; logical < opcodeMap.length; logical++) {
            int physical = opcodeMap[logical];
            if (physical != 0) {
                reverse[physical] = logical;
            }
        }
        return reverse;
    }

    private static int compareFloat(float left, float right, int nanValue) {
        if (Float.isNaN(left) || Float.isNaN(right)) {
            return nanValue;
        }
        return Float.compare(left, right);
    }

    private static int compareDouble(double left, double right, int nanValue) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return nanValue;
        }
        return Double.compare(left, right);
    }

    public static Object _rs(String owner, String name, String descriptor, Object[] args) {
        _o();
        return invoke(owner, name, descriptor, args);
    }

    public static Object _ri(String owner, String name, String descriptor, Object target, Object[] args, int invokeOpcode) {
        _o();
        return invoke(owner, name, descriptor, args, target, invokeOpcode);
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

    private static Object getField(String owner, String name, String descriptor, Object target) {
        try {
            Class<?> fieldType = parseClass(descriptor, 0);
            java.lang.reflect.Field field = findField(classForInternal(owner), name);
            field.setAccessible(true);
            return normalizeReturn(fieldType, field.get(target));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void putField(String owner, String name, String descriptor, Object target, Object value) {
        try {
            Class<?> fieldType = parseClass(descriptor, 0);
            java.lang.reflect.Field field = findField(classForInternal(owner), name);
            field.setAccessible(true);
            field.set(target, coerce(fieldType, value));
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

    private static java.lang.reflect.Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
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

    private static int asInt(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof Character character) {
            return character.charValue();
        }
        return ((Number) value).intValue();
    }

    private static Class<?>[] parameterTypes(String descriptor) throws ClassNotFoundException {
        int index = 1;
        java.util.ArrayList<Class<?>> types = new java.util.ArrayList<>();
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
        if (internalName.charAt(0) == '[') {
            return Class.forName(internalName.replace('/', '.'));
        }
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
        synchronized (InjectedRuntime.class) {
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
        String platform;
        if (os.contains("win") && (arch.contains("64") || arch.equals("amd64") || arch.equals("x86_64"))) {
            platform = "windows-x64";
        } else {
            return false;
        }
        String resource = nativeResourceName();
        try (java.io.InputStream input = InjectedRuntime.class.getResourceAsStream(resource)) {
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
                byte[] data = decodeNative(input.readAllBytes(), resource);
                output.write(data);
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
            if (part.isEmpty()) {
                continue;
            }
            if (new java.io.File(part, library).isFile()) {
                System.loadLibrary("%%SUSHUO_NATIVE_LIBRARY%%");
                return true;
            }
        }
        return false;
    }

    private static boolean embeddedNativeRequired() {
        return Boolean.parseBoolean("%%SUSHUO_NATIVE_REQUIRED%%");
    }

    private static String nativeResourceName() {
        String resource = "%%SUSHUO_NATIVE_RESOURCE%%";
        if (resource.startsWith("%%")) {
            return "/sushuo1337/sushuoprotect/lib/native/windows-x64/" + System.mapLibraryName("%%SUSHUO_NATIVE_LIBRARY%%") + ".dat";
        }
        return resource;
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
        String runtime = InjectedRuntime.class.getName();
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
                        String runtime = InjectedRuntime.class.getName();
                        int key = keyTag ^ resource.hashCode() ^ runtime.hashCode() ^ NATIVE_MAGIC;
                        int state = nativeState(key, nonce, resource, runtime, rawLength, payloadLength);
                        for (int i = 0; i < payload.length; i++) {
                            state = nativeStream(state, i);
                            payload[i] = (byte) (payload[i] ^ (state >>> 24));
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

    private static int nativeState(int key, int nonce, String resource, String runtime, int rawLength, int payloadLength) {
        int state = key ^ nonce ^ resource.hashCode();
        state = nativeMix(state ^ runtime.hashCode());
        state = nativeMix(state ^ rawLength);
        return nativeMix(state ^ payloadLength ^ 0x7F4A7C15);
    }

    private static int nativeStream(int state, int index) {
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }

    private static int nativeMix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static byte[] decodeLegacyNative(byte[] data) {
        byte[] decoded = data.clone();
        int state = 0x6D2B79F5 ^ data.length;
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

    private static int metadataStream(int state, int index) {
        state ^= index * 0x9E3779B9;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 11);
        state ^= state >>> 16;
        state *= 0x85EBCA6B;
        state ^= state >>> 13;
        return state;
    }

}
