package biz.sushuo.shield;

import java.util.List;

record VirtualProgram(
        int id,
        int maxLocals,
        int parameterCount,
        int returnKind,
        int[] code,
        List<Object> constants,
        int key,
        int[] opcodeMap,
        String owner,
        String hostMethod,
        String resourceName
) {
    static final int RETURN_VOID = 0;
    static final int RETURN_INT = 1;
    static final int RETURN_LONG = 2;
    static final int RETURN_FLOAT = 3;
    static final int RETURN_DOUBLE = 4;
    static final int RETURN_OBJECT = 5;

    VirtualProgram withResourceName(String resourceName) {
        return new VirtualProgram(id, maxLocals, parameterCount, returnKind, code,
                constants, key, opcodeMap, owner, hostMethod, resourceName);
    }
}
