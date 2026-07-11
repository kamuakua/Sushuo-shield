package biz.sushuo.shield.runtime;

public final class NativeBridge {
    private static final String a = "%%SUSHUO_NATIVE_METHOD%%";
    private static final String b = "%%SUSHUO_NATIVE_KEY_I_METHOD%%";
    private static final String c = "%%SUSHUO_NATIVE_KEY_L_METHOD%%";

    private NativeBridge() {
    }

    static native Object _nx(Object program, Object args, int token);

    static native int _ki(int kind, Class<?> owner, String name, int key, int site, int salt);

    static native long _kl(int kind, Class<?> owner, String name, long key, int site, int salt);
}
