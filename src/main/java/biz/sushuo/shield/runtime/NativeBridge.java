package biz.sushuo.shield.runtime;

public final class NativeBridge {
    private static final String a = "%%SUSHUO_NATIVE_METHOD%%";

    private NativeBridge() {
    }

    public static native Object _n(Object[] program, Object[] args);
}
