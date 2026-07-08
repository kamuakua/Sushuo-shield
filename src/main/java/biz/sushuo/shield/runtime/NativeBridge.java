package biz.sushuo.shield.runtime;

public final class NativeBridge {
    private static final String a = "%%SUSHUO_NATIVE_METHOD%%";
    private static final String b = "%%SUSHUO_NATIVE_KEY_I_METHOD%%";
    private static final String c = "%%SUSHUO_NATIVE_KEY_L_METHOD%%";

    private NativeBridge() {
    }

    public static native Object _n(Object[] program, Object[] args);

    public static native int _ki(int kind, Class<?> owner, String name, int key, int site, int salt);

    public static native long _kl(int kind, Class<?> owner, String name, long key, int site, int salt);
}
