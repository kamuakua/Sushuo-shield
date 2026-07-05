package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

final class VirtualProgramEmitter implements Opcodes {
    private static final int ENCODED_MARKER = 0x53535632;
    private static final int CODE_SALT = 0x41C64E6D;
    private static final int MAP_SALT = 0x27D4EB2D;

    private VirtualProgramEmitter() {
    }

    static MethodNode createProgramMethod(String owner, String name, VirtualProgram program,
                                          boolean nativeOnly, String runtimeClassName) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, "()[Ljava/lang/Object;", null, null);
        InsnList body = method.instructions;

        if (nativeOnly) {
            body.add(new LdcInsnNode(program.owner().replace('/', '.')));
            body.add(new LdcInsnNode(program.hostMethod()));
            body.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_g",
                    "(Ljava/lang/String;Ljava/lang/String;)V", false));
        }

        if (nativeOnly) {
            Virtualizer.pushInt(body, 10);
            body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

            putInt(body, 0, VmPayloadResources.RESOURCE_MARKER);
            putInt(body, 1, program.maxLocals());
            putInt(body, 2, program.parameterCount());
            putInt(body, 3, program.returnKind());
            putInt(body, 4, VmPayloadResources.sealKey(program));
            putInt(body, 5, program.code().length);
            putInt(body, 6, VmPayloadResources.PACKED_CHUNK_BYTES);
            putString(body, 7, VmPayloadResources.sealResourceName(program));
            putInt(body, 8, program.id());
            putInt(body, 9, program.constants().size());
        } else {
            Virtualizer.pushInt(body, 9);
            body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

            putInt(body, 0, ENCODED_MARKER);
            putInt(body, 1, program.maxLocals());
            putInt(body, 2, program.parameterCount());
            putInt(body, 3, program.returnKind());
            putIntArray(body, 4, encode(program.code(), program.key(), CODE_SALT));
            putObjectArray(body, 5, program.constants());
            putInt(body, 6, program.id());
            putInt(body, 7, program.key());
            putIntArray(body, 8, encode(program.opcodeMap(), program.key(), MAP_SALT));
        }

        body.add(new InsnNode(ARETURN));
        method.maxLocals = 0;
        method.maxStack = 8;
        return method;
    }

    private static void putInt(InsnList body, int index, int value) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, value);
        body.add(integerValueOf());
        body.add(new InsnNode(AASTORE));
    }

    private static void putIntArray(InsnList body, int index, int[] values) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, values.length);
        body.add(new IntInsnNode(NEWARRAY, T_INT));
        for (int i = 0; i < values.length; i++) {
            body.add(new InsnNode(DUP));
            Virtualizer.pushInt(body, i);
            Virtualizer.pushInt(body, values[i]);
            body.add(new InsnNode(IASTORE));
        }
        body.add(new InsnNode(AASTORE));
    }

    private static void putString(InsnList body, int index, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Native VM payload resource name is missing");
        }
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        body.add(new LdcInsnNode(value));
        body.add(new InsnNode(AASTORE));
    }

    private static void putObjectArray(InsnList body, int index, java.util.List<Object> values) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, values.size());
        body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        for (int i = 0; i < values.size(); i++) {
            body.add(new InsnNode(DUP));
            Virtualizer.pushInt(body, i);
            pushConstant(body, values.get(i));
            body.add(new InsnNode(AASTORE));
        }
        body.add(new InsnNode(AASTORE));
    }

    private static void putByteChunks(InsnList body, int index, byte[][] chunks) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, chunks.length);
        body.add(new TypeInsnNode(ANEWARRAY, "[B"));
        for (int chunkIndex = 0; chunkIndex < chunks.length; chunkIndex++) {
            byte[] chunk = chunks[chunkIndex];
            body.add(new InsnNode(DUP));
            Virtualizer.pushInt(body, chunkIndex);
            Virtualizer.pushInt(body, chunk.length);
            body.add(new IntInsnNode(NEWARRAY, T_BYTE));
            for (int i = 0; i < chunk.length; i++) {
                body.add(new InsnNode(DUP));
                Virtualizer.pushInt(body, i);
                Virtualizer.pushInt(body, chunk[i]);
                body.add(new InsnNode(BASTORE));
            }
            body.add(new InsnNode(AASTORE));
        }
        body.add(new InsnNode(AASTORE));
    }

    private static void pushConstant(InsnList body, Object value) {
        if (value == null) {
            body.add(new InsnNode(ACONST_NULL));
        } else if (value instanceof Integer integer) {
            Virtualizer.pushInt(body, integer);
            body.add(integerValueOf());
        } else if (value instanceof Long longValue) {
            body.add(new LdcInsnNode(longValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long",
                    "valueOf", "(J)Ljava/lang/Long;", false));
        } else if (value instanceof Float floatValue) {
            body.add(new LdcInsnNode(floatValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float",
                    "valueOf", "(F)Ljava/lang/Float;", false));
        } else if (value instanceof Double doubleValue) {
            body.add(new LdcInsnNode(doubleValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double",
                    "valueOf", "(D)Ljava/lang/Double;", false));
        } else if (value instanceof String string) {
            body.add(new LdcInsnNode(string));
        } else {
            throw new IllegalArgumentException("Unsupported VM constant: " + value);
        }
    }

    private static MethodInsnNode integerValueOf() {
        return new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false);
    }

    private static int[] encode(int[] values, int key, int salt) {
        int[] encoded = new int[values.length];
        int state = key ^ salt ^ values.length;
        for (int i = 0; i < values.length; i++) {
            state = stream(state, i);
            int rotate = (state >>> 27) & 15;
            encoded[i] = Integer.rotateLeft(values[i] ^ state ^ i, rotate);
        }
        return encoded;
    }

    private static byte[][] pack(int[] values, int key, int salt, int chunkSize) {
        byte[] packed = new byte[values.length * Integer.BYTES];
        for (int i = 0; i < values.length; i++) {
            int encoded = encodePacked(values[i], key, salt, i, values.length);
            int offset = i * Integer.BYTES;
            packed[offset] = (byte) (encoded >>> 24);
            packed[offset + 1] = (byte) (encoded >>> 16);
            packed[offset + 2] = (byte) (encoded >>> 8);
            packed[offset + 3] = (byte) encoded;
        }
        int count = (packed.length + chunkSize - 1) / chunkSize;
        byte[][] chunks = new byte[count][];
        for (int i = 0; i < count; i++) {
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, packed.length - offset);
            chunks[i] = java.util.Arrays.copyOfRange(packed, offset, offset + length);
        }
        return chunks;
    }

    private static int encodePacked(int value, int key, int salt, int index, int length) {
        int state = packedStream(key, salt, index, length);
        int rotate = (state >>> 27) & 15;
        return Integer.rotateLeft(value ^ state ^ index, rotate);
    }

    private static int packedStream(int key, int salt, int index, int length) {
        int state = key ^ salt ^ length ^ Integer.rotateLeft(index * 0x9E3779B9, 5);
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }

    private static int stream(int state, int index) {
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }
}
