package biz.sushuo.shield.runtime;

public final class NativeBridge {
    private NativeBridge() {
    }

    public static native Object _n(Object[] program, Object[] args);
}
