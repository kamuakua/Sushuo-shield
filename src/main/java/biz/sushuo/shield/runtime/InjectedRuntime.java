package biz.sushuo.shield.runtime;

public final class InjectedRuntime {
    private static volatile int state = 0x13572468;
    private static final boolean NATIVE_READY = loadNative();

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
    private static final int LADD = 20;
    private static final int LSUB = 21;
    private static final int LMUL = 22;
    private static final int LDIV = 23;
    private static final int LREM = 24;
    private static final int LNEG = 25;
    private static final int LXOR = 26;
    private static final int FADD = 30;
    private static final int FSUB = 31;
    private static final int FMUL = 32;
    private static final int FDIV = 33;
    private static final int FREM = 34;
    private static final int FNEG = 35;
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
    private static final int INVOKE_STATIC = 70;
    private static final int RETURN = 90;

    private InjectedRuntime() {
    }

    public static Object _v(Object[] program, Object[] args) {
        if (NATIVE_READY) {
            try {
                return NativeBridge._n(program, args);
            } catch (UnsatisfiedLinkError ignored) {
                // The native bridge is optional; protected jars remain runnable without it.
            }
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

    public static boolean _o() {
        int local = state;
        local ^= (int) System.nanoTime();
        local = Integer.rotateLeft(local + 0x45d9f3b, 7);
        state = local;
        return local != 0 || System.currentTimeMillis() >= 0L;
    }

    private static Object _j(Object[] program, Object[] args) {
        int maxLocals = ((Integer) program[0]).intValue();
        int[] code = (int[]) program[3];
        Object[] constants = (Object[]) program[4];
        Object[] locals = new Object[Math.max(maxLocals, args.length)];
        System.arraycopy(args, 0, locals, 0, args.length);
        Object[] stack = new Object[Math.max(32, code.length + 4)];
        int sp = 0;
        int pc = 0;
        while (pc < code.length) {
            int op = code[pc++];
            switch (op) {
                case PUSH_CONST -> stack[sp++] = constants[code[pc++]];
                case LOAD -> stack[sp++] = locals[code[pc++]];
                case STORE -> locals[code[pc++]] = stack[--sp];
                case POP -> sp--;
                case DUP -> stack[sp++] = stack[sp - 1];
                case IADD -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() + ((Number) stack[sp - 1]).intValue();
                case ISUB -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() - ((Number) stack[sp - 1]).intValue();
                case IMUL -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() * ((Number) stack[sp - 1]).intValue();
                case IDIV -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() / ((Number) stack[sp - 1]).intValue();
                case IREM -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() % ((Number) stack[sp - 1]).intValue();
                case INEG -> stack[sp - 1] = -((Number) stack[sp - 1]).intValue();
                case IXOR -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() ^ ((Number) stack[sp - 1]).intValue();
                case IAND -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() & ((Number) stack[sp - 1]).intValue();
                case IOR -> stack[sp - 2] = ((Number) stack[sp - 2]).intValue() | ((Number) stack[sp - 1]).intValue();
                case LADD -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() + ((Number) stack[sp - 1]).longValue();
                case LSUB -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() - ((Number) stack[sp - 1]).longValue();
                case LMUL -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() * ((Number) stack[sp - 1]).longValue();
                case LDIV -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() / ((Number) stack[sp - 1]).longValue();
                case LREM -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() % ((Number) stack[sp - 1]).longValue();
                case LNEG -> stack[sp - 1] = -((Number) stack[sp - 1]).longValue();
                case LXOR -> stack[sp - 2] = ((Number) stack[sp - 2]).longValue() ^ ((Number) stack[sp - 1]).longValue();
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
        return (op >= IADD && op <= IOR && op != INEG)
                || (op >= LADD && op <= LXOR && op != LNEG)
                || (op >= FADD && op <= FREM)
                || (op >= DADD && op <= DREM);
    }

    private static Object invoke(String owner, String name, String descriptor, Object[] args) {
        try {
            Class<?> type = Class.forName(owner.replace('/', '.'));
            Class<?>[] parameterTypes = parameterTypes(descriptor);
            java.lang.reflect.Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            Object value = method.invoke(null, coerceArgs(parameterTypes, args));
            return returnType(descriptor) == Void.TYPE ? Void.TYPE : value;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
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
            if (!nativeRequested()) {
                return false;
            }
            System.loadLibrary("sushuo1337_vm");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean nativeRequested() {
        if (Boolean.getBoolean("sushuo.shield.native")) {
            return true;
        }
        String path = System.getProperty("java.library.path", "");
        String separator = System.getProperty("path.separator", ";");
        String library = System.mapLibraryName("sushuo1337_vm");
        for (String part : path.split(java.util.regex.Pattern.quote(separator))) {
            if (part.isEmpty()) {
                continue;
            }
            if (new java.io.File(part, library).isFile()) {
                return true;
            }
        }
        return false;
    }

    private static int mask(int key, int index) {
        int rotated = Integer.rotateLeft(key, index & 15);
        return rotated ^ (index * 0x45d9f3b) ^ (key >>> (index & 7));
    }

}
