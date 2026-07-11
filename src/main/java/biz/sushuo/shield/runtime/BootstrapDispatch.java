package biz.sushuo.shield.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class BootstrapDispatch {
    private static final ConcurrentMap<Integer, Method> CACHE = new ConcurrentHashMap<>();

    private BootstrapDispatch() {
    }

    static CallSite _bd(Object[] arguments, int token, String ownerCipher, int ownerMask) throws Throwable {
        if (arguments == null || arguments.length < 3 || !(arguments[0] instanceof MethodHandles.Lookup lookup)) {
            throw new IllegalStateException();
        }
        Method method = CACHE.get(token);
        if (method == null) {
            Method resolved = resolve(lookup, arguments.length, token, ownerCipher, ownerMask);
            Method existing = CACHE.putIfAbsent(token, resolved);
            method = existing == null ? resolved : existing;
        }
        try {
            return (CallSite) method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw cause == null ? exception : cause;
        }
    }

    private static Method resolve(MethodHandles.Lookup lookup, int arity, int token,
                                  String ownerCipher, int ownerMask) throws ReflectiveOperationException {
        ClassLoader loader = lookup.lookupClass().getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        Class<?> owner = Class.forName(decode(ownerCipher, ownerMask ^ token), false, loader);
        for (Method candidate : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(candidate.getModifiers())
                    || !CallSite.class.isAssignableFrom(candidate.getReturnType())
                    || candidate.getParameterCount() != arity
                    || token(candidate) != token) {
                continue;
            }
            if (!candidate.trySetAccessible() && !candidate.canAccess(null)) {
                continue;
            }
            return candidate;
        }
        throw new NoSuchMethodException();
    }

    private static int token(Method method) {
        String descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes())
                .toMethodDescriptorString();
        return mix(method.getName().hashCode() ^ Integer.rotateLeft(descriptor.hashCode(), 13));
    }

    private static String decode(String value, int key) {
        char[] decoded = new char[value.length() >>> 1];
        int state = key;
        for (int index = 0; index < decoded.length; index++) {
            state = mix(state ^ index * 0x9E3779B9);
            int high = value.charAt(index << 1) - 'a';
            int low = value.charAt((index << 1) + 1) - 'a';
            decoded[index] = (char) (((high << 4) | low) ^ (state & 0xFF));
        }
        return new String(decoded);
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
