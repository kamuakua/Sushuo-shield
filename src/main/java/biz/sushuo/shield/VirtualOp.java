package biz.sushuo.shield;

final class VirtualOp {
    static final int MAX_OPCODE = 110;
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
    static final int ISHL = 19;
    static final int LADD = 20;
    static final int LSUB = 21;
    static final int LMUL = 22;
    static final int LDIV = 23;
    static final int LREM = 24;
    static final int LNEG = 25;
    static final int LXOR = 26;
    static final int ISHR = 27;
    static final int IUSHR = 28;
    static final int LSHL = 29;
    static final int FADD = 30;
    static final int FSUB = 31;
    static final int FMUL = 32;
    static final int FDIV = 33;
    static final int FREM = 34;
    static final int FNEG = 35;
    static final int LSHR = 36;
    static final int LUSHR = 37;
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
    static final int I2B = 62;
    static final int I2C = 63;
    static final int I2S = 64;
    static final int LCMP = 65;
    static final int FCMPL = 66;
    static final int FCMPG = 67;
    static final int DCMPL = 68;
    static final int DCMPG = 69;
    static final int INVOKE_STATIC = 70;
    static final int IINC = 71;
    static final int GET_STATIC = 72;
    static final int PUT_STATIC = 73;
    static final int GET_FIELD = 74;
    static final int PUT_FIELD = 75;
    static final int INVOKE = 76;
    static final int CHECKCAST = 77;
    static final int INSTANCEOF = 78;
    static final int GOTO = 79;
    static final int RETURN = 90;
    static final int IFEQ = 91;
    static final int IFNE = 92;
    static final int IFLT = 93;
    static final int IFGE = 94;
    static final int IFGT = 95;
    static final int IFLE = 96;
    static final int IF_ICMPEQ = 97;
    static final int IF_ICMPNE = 98;
    static final int IF_ICMPLT = 99;
    static final int IF_ICMPGE = 100;
    static final int IF_ICMPGT = 101;
    static final int IF_ICMPLE = 102;
    static final int IF_ACMPEQ = 103;
    static final int IF_ACMPNE = 104;
    static final int IFNULL = 105;
    static final int IFNONNULL = 106;

    private VirtualOp() {
    }

    static int[] logicalOpcodes() {
        return new int[]{
                PUSH_CONST, LOAD, STORE, POP, DUP,
                IADD, ISUB, IMUL, IDIV, IREM, INEG, IXOR, IAND, IOR, ISHL, ISHR, IUSHR,
                LADD, LSUB, LMUL, LDIV, LREM, LNEG, LXOR, LSHL, LSHR, LUSHR,
                FADD, FSUB, FMUL, FDIV, FREM, FNEG,
                DADD, DSUB, DMUL, DDIV, DREM, DNEG,
                I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S,
                LCMP, FCMPL, FCMPG, DCMPL, DCMPG,
                INVOKE_STATIC, IINC, GET_STATIC, PUT_STATIC, GET_FIELD, PUT_FIELD, INVOKE,
                CHECKCAST, INSTANCEOF, GOTO,
                IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL,
                RETURN
        };
    }

    static int operandCount(int opcode) {
        return switch (opcode) {
            case PUSH_CONST, LOAD, STORE -> 1;
            case GET_STATIC, PUT_STATIC, GET_FIELD, PUT_FIELD -> 3;
            case INVOKE_STATIC -> 4;
            case INVOKE -> 5;
            case IINC -> 2;
            case CHECKCAST, INSTANCEOF, GOTO,
                    IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                    IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                    IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL -> 1;
            default -> 0;
        };
    }
}
