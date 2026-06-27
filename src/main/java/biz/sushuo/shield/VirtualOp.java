package biz.sushuo.shield;

final class VirtualOp {
    static final int PUSH_CONST = 1;
    static final int LOAD = 2;
    static final int STORE = 3;
    static final int POP = 4;
    static final int DUP = 5;
    static final int IADD = 10;
    static final int ISUB = 11;
    static final int IMUL = 12;
    static final int IDIV = 13;
    static final int IREM = 14;
    static final int INEG = 15;
    static final int IXOR = 16;
    static final int IAND = 17;
    static final int IOR = 18;
    static final int LADD = 20;
    static final int LSUB = 21;
    static final int LMUL = 22;
    static final int LDIV = 23;
    static final int LREM = 24;
    static final int LNEG = 25;
    static final int LXOR = 26;
    static final int FADD = 30;
    static final int FSUB = 31;
    static final int FMUL = 32;
    static final int FDIV = 33;
    static final int FREM = 34;
    static final int FNEG = 35;
    static final int DADD = 40;
    static final int DSUB = 41;
    static final int DMUL = 42;
    static final int DDIV = 43;
    static final int DREM = 44;
    static final int DNEG = 45;
    static final int I2L = 50;
    static final int I2F = 51;
    static final int I2D = 52;
    static final int L2I = 53;
    static final int L2F = 54;
    static final int L2D = 55;
    static final int F2I = 56;
    static final int F2L = 57;
    static final int F2D = 58;
    static final int D2I = 59;
    static final int D2L = 60;
    static final int D2F = 61;
    static final int INVOKE_STATIC = 70;
    static final int RETURN = 90;

    private VirtualOp() {
    }
}
