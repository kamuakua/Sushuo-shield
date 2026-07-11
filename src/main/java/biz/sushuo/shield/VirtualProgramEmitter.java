package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class VirtualProgramEmitter implements Opcodes {
    private static final int ENCODED_MARKER = 0x53535632;
    private static final int CODE_SALT = 0x41C64E6D;
    private static final int MAP_SALT = 0x27D4EB2D;
    private static final int TOKEN_II = 0;
    private static final int TOKEN_OBJECT_INT = 1;
    private static final int TOKEN_LONG = 2;
    private static final int TOKEN_INT_OBJECT = 3;
    private static final int TOKEN_OBJECT_OBJECT = 4;
    private static final int RETURN_OBJECT = 0;
    private static final int RETURN_SERIALIZABLE = 1;
    private static final int RETURN_CLONEABLE = 2;
    private static final int ARRAY_OBJECT_LOCAL = 0;
    private static final int ARRAY_SERIALIZABLE_LOCAL = 1;
    private static final int ARRAY_REFLECT_OBJECT = 2;

    private VirtualProgramEmitter() {
    }

    static FactoryShape selectFactoryShape(long seed, String owner, String hostName, String hostDesc,
                                           VirtualProgram program, int role) {
        int base = mix((int) seed ^ (int) (seed >>> 32)
                ^ owner.hashCode()
                ^ Integer.rotateLeft(hostName.hashCode(), 5)
                ^ Integer.rotateLeft(hostDesc.hashCode(), 11)
                ^ Integer.rotateLeft(program.id(), 17)
                ^ role * 0x6D2B79F5);
        int tokenPick = Math.floorMod(base, 4);
        int tokenKind = tokenPick < TOKEN_LONG ? tokenPick : tokenPick + 1;
        int returnKind = Math.floorMod(Integer.rotateRight(base, 7), 3);
        int arrayKind = Math.floorMod(Integer.rotateRight(base, 13), 3);
        int salt = nonZero(mix(base ^ program.key() ^ 0x51ED270B));
        int fillSeed = mix(base ^ Integer.rotateLeft(program.key(), 9) ^ 0x7F4A7C15);
        return new FactoryShape(tokenKind, returnKind, arrayKind, salt, fillSeed);
    }

    static boolean useFactoryIndirection(long seed, String owner, String hostName, String hostDesc,
                                         VirtualProgram program) {
        int value = mix((int) seed ^ (int) (seed >>> 32)
                ^ Integer.rotateLeft(owner.hashCode(), 3)
                ^ Integer.rotateLeft(hostName.hashCode(), 13)
                ^ hostDesc.hashCode()
                ^ program.id() * 0x45D9F3B);
        return (value & 3) != 0;
    }

    static void emitFactoryInvocation(InsnList body, FactoryShape shape, int token) {
        int salt = shape.salt();
        switch (shape.tokenKind()) {
            case TOKEN_II -> {
                Virtualizer.pushInt(body, token ^ salt);
                Virtualizer.pushInt(body, salt);
            }
            case TOKEN_OBJECT_INT -> {
                pushBoxedInt(body, token ^ salt);
                Virtualizer.pushInt(body, salt);
            }
            case TOKEN_LONG -> {
                long composite = ((long) (token ^ salt) << 32) | (salt & 0xFFFFFFFFL);
                body.add(new LdcInsnNode(composite));
            }
            case TOKEN_INT_OBJECT -> {
                Virtualizer.pushInt(body, token ^ salt);
                pushBoxedInt(body, salt);
            }
            case TOKEN_OBJECT_OBJECT -> {
                pushBoxedInt(body, token ^ salt);
                pushBoxedInt(body, salt);
            }
            default -> throw new IllegalArgumentException("Bad token shape " + shape.tokenKind());
        }
    }

    static MethodNode createProgramMethod(String owner, String name, VirtualProgram program,
                                          boolean nativeOnly, String runtimeClassName) {
        return createProgramMethod(owner, name, program, nativeOnly, runtimeClassName, nativeOnly);
    }

    static MethodNode createProgramMethod(String owner, String name, VirtualProgram program,
                                          boolean nativeOnly, String runtimeClassName,
                                          boolean guardProgramAccess) {
        return createProgramMethod(owner, name, program, nativeOnly, runtimeClassName, guardProgramAccess,
                new FactoryShape(TOKEN_II, RETURN_OBJECT, ARRAY_OBJECT_LOCAL,
                        nonZero(mix(program.key() ^ name.hashCode())), mix(program.id() ^ program.key())));
    }

    static List<MethodNode> createProgramMethods(String owner,
                                                 String targetName,
                                                 String materializerName,
                                                 VirtualProgram program,
                                                 boolean nativeOnly,
                                                 String runtimeClassName,
                                                 boolean guardProgramAccess,
                                                 FactoryShape targetShape,
                                                 FactoryShape materializerShape,
                                                 boolean indirect) {
        if (!indirect) {
            return List.of(createProgramMethod(owner, targetName, program, nativeOnly, runtimeClassName,
                    guardProgramAccess, targetShape));
        }
        List<MethodNode> methods = new ArrayList<>(2);
        methods.add(createBridgeMethod(owner, targetName, materializerName, program, runtimeClassName,
                targetShape, materializerShape));
        methods.add(createProgramMethod(owner, materializerName, program, nativeOnly, runtimeClassName,
                guardProgramAccess, materializerShape, targetName));
        return methods;
    }

    private static MethodNode createBridgeMethod(String owner,
                                                 String bridgeName,
                                                 String materializerName,
                                                 VirtualProgram program,
                                                 String runtimeClassName,
                                                 FactoryShape bridgeShape,
                                                 FactoryShape materializerShape) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                bridgeName, bridgeShape.desc(), null, null);
        InsnList body = method.instructions;
        emitTokenCheck(body, bridgeShape, Virtualizer.programDataToken(owner, bridgeName, program),
                program, program.hostMethod(), runtimeClassName);

        emitFactoryInvocation(body, materializerShape,
                Virtualizer.programDataToken(owner, materializerName, program));
        body.add(new MethodInsnNode(INVOKESTATIC, owner, materializerName, materializerShape.desc(), false));
        castToReturn(body, bridgeShape);
        body.add(new InsnNode(ARETURN));
        method.maxLocals = bridgeShape.localSlots();
        method.maxStack = 10;
        return method;
    }

    static MethodNode createProgramMethod(String owner, String name, VirtualProgram program,
                                          boolean nativeOnly, String runtimeClassName,
                                          boolean guardProgramAccess, FactoryShape shape) {
        return createProgramMethod(owner, name, program, nativeOnly, runtimeClassName,
                guardProgramAccess, shape, program.hostMethod());
    }

    private static MethodNode createProgramMethod(String owner, String name, VirtualProgram program,
                                                  boolean nativeOnly, String runtimeClassName,
                                                  boolean guardProgramAccess, FactoryShape shape,
                                                  String expectedCallerMethod) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, shape.desc(), null, null);
        InsnList body = method.instructions;

        int expectedToken = Virtualizer.programDataToken(owner, name, program);
        emitTokenCheck(body, shape, expectedToken, program, expectedCallerMethod, runtimeClassName);

        if (guardProgramAccess) {
            Virtualizer.pushInt(body, program.owner().replace('/', '.').hashCode());
            Virtualizer.pushInt(body, expectedCallerMethod.hashCode());
            body.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_gh",
                    "(II)V", false));
        }

        int arrayLocal = shape.localSlots();
        int size = nativeOnly ? 12 : 9;
        newProgramArray(body, size, shape.arrayKind());
        body.add(new VarInsnNode(ASTORE, arrayLocal));
        int[] order = shuffledSlots(size, shape.fillSeed() ^ program.id() ^ (nativeOnly ? 0x4E415456 : 0x494E4C4E));
        if (nativeOnly) {
            for (int slot : order) {
                switch (slot) {
                    case 0 -> putInt(body, arrayLocal, 0, VmPayloadResources.RESOURCE_MARKER);
                    case 1 -> putInt(body, arrayLocal, 1, program.maxLocals());
                    case 2 -> putInt(body, arrayLocal, 2, program.parameterCount());
                    case 3 -> putInt(body, arrayLocal, 3, program.returnKind());
                    case 4 -> putInt(body, arrayLocal, 4, VmPayloadResources.sealKey(program));
                    case 5 -> putInt(body, arrayLocal, 5, program.code().length);
                    case 6 -> putInt(body, arrayLocal, 6, VmPayloadResources.PACKED_CHUNK_BYTES);
                    case 7 -> putString(body, arrayLocal, 7, VmPayloadResources.sealResourceName(program));
                    case 8 -> putInt(body, arrayLocal, 8, program.id());
                    case 9 -> putInt(body, arrayLocal, 9, program.constants().size());
                    case 10 -> putInt(body, arrayLocal, 10, program.owner().replace('/', '.').hashCode());
                    case 11 -> putInt(body, arrayLocal, 11, program.hostMethod().hashCode());
                    default -> throw new IllegalStateException("Bad native program slot " + slot);
                }
            }
        } else {
            for (int slot : order) {
                switch (slot) {
                    case 0 -> putInt(body, arrayLocal, 0, ENCODED_MARKER);
                    case 1 -> putInt(body, arrayLocal, 1, program.maxLocals());
                    case 2 -> putInt(body, arrayLocal, 2, program.parameterCount());
                    case 3 -> putInt(body, arrayLocal, 3, program.returnKind());
                    case 4 -> putIntArray(body, arrayLocal, 4, encode(program.code(), program.key(), CODE_SALT));
                    case 5 -> putObjectArray(body, arrayLocal, 5, program.constants());
                    case 6 -> putInt(body, arrayLocal, 6, program.id());
                    case 7 -> putInt(body, arrayLocal, 7, program.key());
                    case 8 -> putIntArray(body, arrayLocal, 8, encode(program.opcodeMap(), program.key(), MAP_SALT));
                    default -> throw new IllegalStateException("Bad inline program slot " + slot);
                }
            }
        }

        body.add(new VarInsnNode(ALOAD, arrayLocal));
        castToReturn(body, shape);
        body.add(new InsnNode(ARETURN));
        method.maxLocals = arrayLocal + 1;
        method.maxStack = 12;
        return method;
    }

    private static void emitTokenCheck(InsnList body, FactoryShape shape, int expectedToken,
                                       VirtualProgram program, String expectedCallerMethod,
                                       String runtimeClassName) {
        LabelNode tokenOk = new LabelNode();
        emitRecoveredToken(body, shape);
        Virtualizer.pushInt(body, expectedToken);
        body.add(new JumpInsnNode(IF_ICMPEQ, tokenOk));
        Virtualizer.pushInt(body, Integer.rotateLeft(program.owner().replace('/', '.').hashCode() ^ expectedToken, 5));
        Virtualizer.pushInt(body, Integer.rotateLeft(expectedCallerMethod.hashCode() ^ expectedToken, 11));
        body.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_gh",
                "(II)V", false));
        body.add(tokenOk);
    }

    private static void emitRecoveredToken(InsnList body, FactoryShape shape) {
        switch (shape.tokenKind()) {
            case TOKEN_II -> {
                body.add(new VarInsnNode(ILOAD, 0));
                body.add(new VarInsnNode(ILOAD, 1));
                body.add(new InsnNode(IXOR));
            }
            case TOKEN_OBJECT_INT -> {
                body.add(new VarInsnNode(ALOAD, 0));
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new VarInsnNode(ILOAD, 1));
                body.add(new InsnNode(IXOR));
            }
            case TOKEN_LONG -> {
                body.add(new VarInsnNode(LLOAD, 0));
                Virtualizer.pushInt(body, 32);
                body.add(new InsnNode(LUSHR));
                body.add(new InsnNode(L2I));
                body.add(new VarInsnNode(LLOAD, 0));
                body.add(new InsnNode(L2I));
                body.add(new InsnNode(IXOR));
            }
            case TOKEN_INT_OBJECT -> {
                body.add(new VarInsnNode(ILOAD, 0));
                body.add(new VarInsnNode(ALOAD, 1));
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new InsnNode(IXOR));
            }
            case TOKEN_OBJECT_OBJECT -> {
                body.add(new VarInsnNode(ALOAD, 0));
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new VarInsnNode(ALOAD, 1));
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new InsnNode(IXOR));
            }
            default -> throw new IllegalArgumentException("Bad token shape " + shape.tokenKind());
        }
    }

    private static void newProgramArray(InsnList body, int size, int arrayKind) {
        switch (arrayKind) {
            case ARRAY_OBJECT_LOCAL -> {
                Virtualizer.pushInt(body, size);
                body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
            }
            case ARRAY_SERIALIZABLE_LOCAL -> {
                Virtualizer.pushInt(body, size);
                body.add(new TypeInsnNode(ANEWARRAY, "java/io/Serializable"));
            }
            case ARRAY_REFLECT_OBJECT -> {
                body.add(new LdcInsnNode(Type.getType("Ljava/lang/Object;")));
                Virtualizer.pushInt(body, size);
                body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/reflect/Array",
                        "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;", false));
                body.add(new TypeInsnNode(CHECKCAST, "[Ljava/lang/Object;"));
            }
            default -> throw new IllegalArgumentException("Bad array shape " + arrayKind);
        }
    }

    private static int[] shuffledSlots(int size, int seed) {
        List<Integer> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, new Random(seed));
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            out[i] = slots.get(i);
        }
        return out;
    }

    private static void putInt(InsnList body, int arrayLocal, int index, int value) {
        body.add(new VarInsnNode(ALOAD, arrayLocal));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, value);
        body.add(integerValueOf());
        body.add(new InsnNode(AASTORE));
    }

    private static void putIntArray(InsnList body, int arrayLocal, int index, int[] values) {
        body.add(new VarInsnNode(ALOAD, arrayLocal));
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

    private static void putString(InsnList body, int arrayLocal, int index, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Native VM payload resource name is missing");
        }
        body.add(new VarInsnNode(ALOAD, arrayLocal));
        Virtualizer.pushInt(body, index);
        body.add(new LdcInsnNode(value));
        body.add(new InsnNode(AASTORE));
    }

    private static void putObjectArray(InsnList body, int arrayLocal, int index, java.util.List<Object> values) {
        body.add(new VarInsnNode(ALOAD, arrayLocal));
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

    private static void castToReturn(InsnList body, FactoryShape shape) {
        String internalName = shape.returnInternalName();
        if (internalName != null) {
            body.add(new TypeInsnNode(CHECKCAST, internalName));
        }
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

    private static void pushBoxedInt(InsnList body, int value) {
        Virtualizer.pushInt(body, value);
        body.add(integerValueOf());
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

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static int nonZero(int value) {
        return value == 0 ? 0x13579BDF : value;
    }

    record FactoryShape(int tokenKind, int returnKind, int arrayKind, int salt, int fillSeed) {
        String desc() {
            return "(" + tokenDescriptor() + ")" + returnDescriptor();
        }

        int localSlots() {
            return switch (tokenKind) {
                case TOKEN_LONG -> 2;
                case TOKEN_II, TOKEN_OBJECT_INT, TOKEN_INT_OBJECT, TOKEN_OBJECT_OBJECT -> 2;
                default -> throw new IllegalArgumentException("Bad token shape " + tokenKind);
            };
        }

        String returnInternalName() {
            return switch (returnKind) {
                case RETURN_OBJECT -> null;
                case RETURN_SERIALIZABLE -> "java/io/Serializable";
                case RETURN_CLONEABLE -> "java/lang/Cloneable";
                default -> throw new IllegalArgumentException("Bad return shape " + returnKind);
            };
        }

        private String tokenDescriptor() {
            return switch (tokenKind) {
                case TOKEN_II -> "II";
                case TOKEN_OBJECT_INT -> "Ljava/lang/Object;I";
                case TOKEN_LONG -> "J";
                case TOKEN_INT_OBJECT -> "ILjava/lang/Object;";
                case TOKEN_OBJECT_OBJECT -> "Ljava/lang/Object;Ljava/lang/Object;";
                default -> throw new IllegalArgumentException("Bad token shape " + tokenKind);
            };
        }

        private String returnDescriptor() {
            return switch (returnKind) {
                case RETURN_OBJECT -> "Ljava/lang/Object;";
                case RETURN_SERIALIZABLE -> "Ljava/io/Serializable;";
                case RETURN_CLONEABLE -> "Ljava/lang/Cloneable;";
                default -> throw new IllegalArgumentException("Bad return shape " + returnKind);
            };
        }
    }
}
