package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

final class AntiDeobfuscationNoise implements Opcodes {
    private static final int INLINE_MARKER = 0x53535632;
    private static final int RESOURCE_MARKER = VmPayloadResources.RESOURCE_MARKER;
    private static final int CODE_SALT = VmPayloadResources.CODE_SALT;
    private static final int OP_SALT = VmPayloadResources.MAP_SALT;

    private AntiDeobfuscationNoise() {
    }

    static int inject(ClassNode classNode, String runtimeClassName, long seed) {
        if ((classNode.access & (ACC_INTERFACE | ACC_ANNOTATION | ACC_ENUM)) != 0) {
            return 0;
        }

        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0xA1D30BF72F5A7C19L);
        Set<String> usedNames = new HashSet<>();
        classNode.fields.forEach(field -> usedNames.add(field.name));
        classNode.methods.forEach(method -> usedNames.add(method.name + method.desc));

        int artifacts = 0;
        int fieldCount = 1 + random.nextInt(2);
        for (int i = 0; i < fieldCount; i++) {
            String name = nextName(random, usedNames);
            classNode.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                    name, "I", null, null));
            artifacts++;
        }

        int methodCount = 2 + random.nextInt(2);
        for (int i = 0; i < methodCount; i++) {
            MethodNode method = (i & 1) == 0
                    ? intNoise(nextMethodName(random, usedNames), runtimeClassName, random)
                    : textNoise(nextMethodName(random, usedNames), runtimeClassName, random);
            classNode.methods.add(method);
            artifacts++;
        }
        int vmShapes = 1 + random.nextInt(2);
        for (int i = 0; i < vmShapes; i++) {
            MethodShape shape = MethodShape.next(random);
            classNode.methods.add(programNoise(nextProgramName(random, usedNames, shape.desc()),
                    classNode.name, runtimeClassName, random, shape));
            artifacts++;
        }
        return artifacts;
    }

    static Map<String, byte[]> resources(ObfuscationOptions options, NamingPlan namingPlan, Set<String> usedNames) {
        if (!options.antiAiDeobfuscation()) {
            return Map.of();
        }
        Random random = new Random(options.seed()
                ^ namingPlan.runtimeClassName().hashCode()
                ^ 0x41B5D03F9E3779B9L);
        Map<String, byte[]> output = new TreeMap<>();
        int target = switch (options.mode()) {
            case ZKM26 -> 5;
            case JNIC, VMP, STACKED, MINECRAFT_MAX -> 10;
            default -> 6;
        };
        int attempts = 0;
        while (output.size() < target && attempts++ < target * 32) {
            if (addResourceShape(options, namingPlan, usedNames, output, random)) {
                continue;
            }
            random.nextLong();
        }
        return output;
    }

    private static boolean addResourceShape(ObfuscationOptions options,
                                            NamingPlan namingPlan,
                                            Set<String> usedNames,
                                            Map<String, byte[]> output,
                                            Random random) {
        ProgramSpec spec = programSpec(random);
        String owner = resourceOwner(random, namingPlan);
        String host = randomMemberName(random);
        String descriptor = hostDescriptor(spec.parameterCount(), spec.returnKind(), random);
        VirtualProgram program = spec.toProgram(owner, host, null);
        VmPayloadResources sink = new VmPayloadResources(namingPlan, options.seed());
        sink.add(program, owner, host, descriptor);
        for (Map.Entry<String, byte[]> entry : sink.resources().entrySet()) {
            if (usedNames.contains(entry.getKey()) || output.containsKey(entry.getKey())) {
                return false;
            }
            output.put(entry.getKey(), entry.getValue());
            return true;
        }
        return false;
    }

    private static MethodNode intNoise(String name, String runtimeClassName, Random random) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, "(I)I", null, null);
        InsnList code = method.instructions;
        LabelNode real = new LabelNode();
        code.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
        code.add(new JumpInsnNode(IFNE, real));
        code.add(new LdcInsnNode(random.nextInt()));
        code.add(new InsnNode(IRETURN));
        code.add(real);
        code.add(new LdcInsnNode(opaqueLiteral(random)));
        code.add(new InsnNode(POP));
        code.add(new VarInsnNode(ILOAD, 0));
        code.add(new LdcInsnNode(random.nextInt()));
        code.add(new InsnNode(IXOR));
        code.add(new LdcInsnNode(random.nextInt()));
        code.add(new InsnNode(IADD));
        code.add(new InsnNode(IRETURN));
        method.maxLocals = 1;
        method.maxStack = 3;
        return method;
    }

    private static MethodNode textNoise(String name, String runtimeClassName, Random random) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        InsnList code = method.instructions;
        LabelNode passthrough = new LabelNode();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new JumpInsnNode(IFNONNULL, passthrough));
        code.add(new LdcInsnNode(opaqueLiteral(random)));
        code.add(new InsnNode(ARETURN));
        code.add(passthrough);
        code.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
        LabelNode input = new LabelNode();
        code.add(new JumpInsnNode(IFNE, input));
        code.add(new LdcInsnNode(opaqueLiteral(random)));
        code.add(new InsnNode(ARETURN));
        code.add(input);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(ARETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private static MethodNode programNoise(String name, String owner, String runtimeClassName,
                                           Random random, MethodShape shape) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, shape.desc(), null, null);
        InsnList code = method.instructions;
        Virtualizer.pushInt(code, owner.replace('/', '.').hashCode());
        Virtualizer.pushInt(code, name.hashCode());
        code.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_gh", "(II)V", false));
        LabelNode materialize = new LabelNode();
        code.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
        code.add(new JumpInsnNode(IFNE, materialize));
        code.add(new LdcInsnNode(random.nextInt()));
        code.add(new InsnNode(POP));
        code.add(materialize);
        int arrayLocal = shape.localSlots();
        if (random.nextBoolean()) {
            inlineProgram(code, random, arrayLocal);
        } else {
            resourceHandle(code, owner, name, runtimeClassName, random, arrayLocal);
        }
        code.add(new VarInsnNode(ALOAD, arrayLocal));
        code.add(new InsnNode(ARETURN));
        method.maxLocals = arrayLocal + 1;
        method.maxStack = 10;
        return method;
    }

    private static void inlineProgram(InsnList code, Random random, int arrayLocal) {
        ProgramSpec spec = programSpec(random);
        newNoiseArray(code, 9, random);
        code.add(new VarInsnNode(ASTORE, arrayLocal));
        for (int slot : shuffledSlots(9, random)) {
            switch (slot) {
                case 0 -> putInt(code, arrayLocal, 0, INLINE_MARKER);
                case 1 -> putInt(code, arrayLocal, 1, spec.maxLocals());
                case 2 -> putInt(code, arrayLocal, 2, spec.parameterCount());
                case 3 -> putInt(code, arrayLocal, 3, spec.returnKind());
                case 4 -> putIntArray(code, arrayLocal, 4, encode(spec.code(), spec.key(), CODE_SALT));
                case 5 -> putObjectArray(code, arrayLocal, 5, spec.constants());
                case 6 -> putInt(code, arrayLocal, 6, spec.id());
                case 7 -> putInt(code, arrayLocal, 7, spec.key());
                case 8 -> putIntArray(code, arrayLocal, 8, encode(spec.opcodeTable(), spec.key(), OP_SALT));
                default -> throw new IllegalStateException("Bad inline decoy slot " + slot);
            }
        }
    }

    private static void resourceHandle(InsnList code, String owner, String host, String runtimeClassName,
                                       Random random, int arrayLocal) {
        ProgramSpec spec = programSpec(random);
        String runtimeName = resourceNameLike(runtimeClassName, random);
        VirtualProgram program = spec.toProgram(owner, host, runtimeName);
        newNoiseArray(code, 12, random);
        code.add(new VarInsnNode(ASTORE, arrayLocal));
        for (int slot : shuffledSlots(12, random)) {
            switch (slot) {
                case 0 -> putInt(code, arrayLocal, 0, RESOURCE_MARKER);
                case 1 -> putInt(code, arrayLocal, 1, program.maxLocals());
                case 2 -> putInt(code, arrayLocal, 2, program.parameterCount());
                case 3 -> putInt(code, arrayLocal, 3, program.returnKind());
                case 4 -> putInt(code, arrayLocal, 4, VmPayloadResources.sealKey(program));
                case 5 -> putInt(code, arrayLocal, 5, program.code().length);
                case 6 -> putInt(code, arrayLocal, 6, VmPayloadResources.PACKED_CHUNK_BYTES);
                case 7 -> putString(code, arrayLocal, 7, VmPayloadResources.sealResourceName(program));
                case 8 -> putInt(code, arrayLocal, 8, program.id());
                case 9 -> putInt(code, arrayLocal, 9, program.constants().size());
                case 10 -> putInt(code, arrayLocal, 10, program.owner().replace('/', '.').hashCode());
                case 11 -> putInt(code, arrayLocal, 11, program.hostMethod().hashCode());
                default -> throw new IllegalStateException("Bad resource decoy slot " + slot);
            }
        }
    }

    private static ProgramSpec programSpec(Random random) {
        int id = positive(mix(random.nextInt() ^ Integer.rotateLeft(random.nextInt(), 11)));
        int key = nonZero(mix(random.nextInt() ^ Integer.rotateLeft(id, 7)));
        int[] opcodes = opcodeTable(key);
        Object[] constants;
        int[] logical;
        int returnKind;
        int maxLocals;
        int parameterCount;
        switch (random.nextInt(6)) {
            case 0 -> {
                constants = new Object[]{random.nextInt(), random.nextInt() | 1};
                logical = new int[]{VirtualOp.PUSH_CONST, 0, VirtualOp.PUSH_CONST, 1, VirtualOp.IXOR, VirtualOp.RETURN};
                returnKind = VirtualProgram.RETURN_INT;
                maxLocals = 1 + random.nextInt(3);
                parameterCount = 0;
            }
            case 1 -> {
                constants = new Object[]{random.nextLong(), random.nextLong() | 1L};
                logical = new int[]{VirtualOp.PUSH_CONST, 0, VirtualOp.PUSH_CONST, 1, VirtualOp.LXOR, VirtualOp.RETURN};
                returnKind = VirtualProgram.RETURN_LONG;
                maxLocals = 2 + random.nextInt(3);
                parameterCount = 0;
            }
            case 2 -> {
                constants = new Object[]{random.nextFloat(), random.nextFloat() + 1.0f};
                logical = new int[]{VirtualOp.PUSH_CONST, 0, VirtualOp.PUSH_CONST, 1, VirtualOp.FADD, VirtualOp.RETURN};
                returnKind = VirtualProgram.RETURN_FLOAT;
                maxLocals = 1 + random.nextInt(3);
                parameterCount = 0;
            }
            case 3 -> {
                constants = new Object[]{random.nextDouble(), random.nextDouble() + 1.0d};
                logical = new int[]{VirtualOp.PUSH_CONST, 0, VirtualOp.PUSH_CONST, 1, VirtualOp.DADD, VirtualOp.RETURN};
                returnKind = VirtualProgram.RETURN_DOUBLE;
                maxLocals = 2 + random.nextInt(3);
                parameterCount = 0;
            }
            case 4 -> {
                constants = new Object[]{random.nextInt()};
                logical = new int[]{VirtualOp.LOAD, 0, VirtualOp.PUSH_CONST, 0, VirtualOp.IXOR, VirtualOp.RETURN};
                returnKind = VirtualProgram.RETURN_INT;
                maxLocals = 1 + random.nextInt(3);
                parameterCount = 1;
            }
            default -> {
                constants = new Object[]{opaqueLiteral(random)};
                logical = new int[]{VirtualOp.PUSH_CONST, 0, VirtualOp.RETURN};
                returnKind = VirtualProgram.RETURN_OBJECT;
                maxLocals = 1 + random.nextInt(3);
                parameterCount = random.nextInt(2);
            }
        }
        return new ProgramSpec(id, maxLocals, parameterCount, returnKind,
                mapOpcodes(logical, opcodes), constants, key, opcodes);
    }

    private static int[] opcodeTable(int key) {
        int[] logicalOpcodes = VirtualOp.logicalOpcodes();
        List<Integer> physicalOpcodes = new ArrayList<>(logicalOpcodes.length);
        for (int opcode : logicalOpcodes) {
            physicalOpcodes.add(opcode);
        }
        Collections.shuffle(physicalOpcodes, new Random(key ^ 0x51ED270B));
        int[] table = new int[VirtualOp.MAX_OPCODE + 1];
        for (int i = 0; i < logicalOpcodes.length; i++) {
            table[logicalOpcodes[i]] = physicalOpcodes.get(i);
        }
        return table;
    }

    private static int[] mapOpcodes(int[] code, int[] opcodeTable) {
        int[] mapped = code.clone();
        for (int i = 0; i < mapped.length; ) {
            int opcode = mapped[i];
            mapped[i++] = opcodeTable[opcode];
            i += VirtualOp.operandCount(opcode);
        }
        return mapped;
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

    private static int stream(int state, int index) {
        state ^= index * 0x45D9F3B;
        state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
        state ^= state >>> 13;
        state *= 0x5BD1E995;
        state ^= state >>> 15;
        return state;
    }

    private static void newNoiseArray(InsnList body, int size, Random random) {
        switch (random.nextInt(3)) {
            case 0 -> {
                Virtualizer.pushInt(body, size);
                body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
            }
            case 1 -> {
                Virtualizer.pushInt(body, size);
                body.add(new TypeInsnNode(ANEWARRAY, "java/io/Serializable"));
            }
            default -> {
                body.add(new LdcInsnNode(Type.getType("Ljava/lang/Object;")));
                Virtualizer.pushInt(body, size);
                body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/reflect/Array",
                        "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;", false));
                body.add(new TypeInsnNode(CHECKCAST, "[Ljava/lang/Object;"));
            }
        }
    }

    private static int[] shuffledSlots(int size, Random random) {
        List<Integer> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);
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
        body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false));
        body.add(new InsnNode(AASTORE));
    }

    private static void putString(InsnList body, int arrayLocal, int index, String value) {
        body.add(new VarInsnNode(ALOAD, arrayLocal));
        Virtualizer.pushInt(body, index);
        body.add(new LdcInsnNode(value));
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

    private static void putObjectArray(InsnList body, int arrayLocal, int index, Object[] values) {
        body.add(new VarInsnNode(ALOAD, arrayLocal));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, values.length);
        body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        for (int i = 0; i < values.length; i++) {
            body.add(new InsnNode(DUP));
            Virtualizer.pushInt(body, i);
            pushBoxed(body, values[i]);
            body.add(new InsnNode(AASTORE));
        }
        body.add(new InsnNode(AASTORE));
    }

    private static void pushBoxed(InsnList body, Object value) {
        if (value instanceof Integer integer) {
            Virtualizer.pushInt(body, integer);
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf",
                    "(I)Ljava/lang/Integer;", false));
        } else if (value instanceof Long longValue) {
            body.add(new LdcInsnNode(longValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "valueOf",
                    "(J)Ljava/lang/Long;", false));
        } else if (value instanceof Float floatValue) {
            body.add(new LdcInsnNode(floatValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf",
                    "(F)Ljava/lang/Float;", false));
        } else if (value instanceof Double doubleValue) {
            body.add(new LdcInsnNode(doubleValue));
            body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf",
                    "(D)Ljava/lang/Double;", false));
        } else if (value instanceof String string) {
            body.add(new LdcInsnNode(string));
        } else {
            body.add(new InsnNode(ACONST_NULL));
        }
    }

    private static String nextMethodName(Random random, Set<String> usedNames) {
        while (true) {
            String name = randomMemberName(random);
            if (usedNames.add(name + "(I)I") && usedNames.add(name + "(Ljava/lang/String;)Ljava/lang/String;")) {
                return name;
            }
        }
    }

    private static String nextProgramName(Random random, Set<String> usedNames, String desc) {
        while (true) {
            String name = randomMemberName(random);
            if (usedNames.add(name + desc)) {
                return name;
            }
        }
    }

    private static String nextName(Random random, Set<String> usedNames) {
        while (true) {
            String name = randomMemberName(random);
            if (usedNames.add(name)) {
                return name;
            }
        }
    }

    private static String randomMemberName(Random random) {
        int a = mix(random.nextInt());
        int b = mix(random.nextInt() ^ Integer.rotateLeft(a, 11));
        return "_" + Integer.toUnsignedString(a, 36) + Integer.toUnsignedString(b, 36);
    }

    private static String resourceOwner(Random random, NamingPlan namingPlan) {
        String runtime = namingPlan.runtimeClassName();
        int slash = runtime.lastIndexOf('/');
        String base = slash < 0 ? "" : runtime.substring(0, slash + 1);
        return base + randomMemberName(random) + "/" + randomMemberName(random);
    }

    private static String resourceNameLike(String runtimeClassName, Random random) {
        int a = mix(random.nextInt());
        int b = mix(random.nextInt() ^ Integer.rotateLeft(a, 7));
        int c = mix(random.nextInt() ^ Integer.rotateLeft(b, 11));
        int selector = mix(a ^ Integer.rotateLeft(b, 5) ^ Integer.rotateLeft(c, 17));
        return "/" + VmPayloadResources.distributedVmResourceName(runtimeClassName, a, b, c, selector);
    }

    private static String hostDescriptor(int parameterCount, int returnKind, Random random) {
        StringBuilder descriptor = new StringBuilder("(");
        for (int i = 0; i < parameterCount; i++) {
            descriptor.append((i & 1) == 0 || random.nextBoolean() ? "I" : "Ljava/lang/Object;");
        }
        descriptor.append(')').append(returnDescriptor(returnKind));
        return descriptor.toString();
    }

    private static String returnDescriptor(int returnKind) {
        return switch (returnKind) {
            case VirtualProgram.RETURN_VOID -> "V";
            case VirtualProgram.RETURN_LONG -> "J";
            case VirtualProgram.RETURN_FLOAT -> "F";
            case VirtualProgram.RETURN_DOUBLE -> "D";
            case VirtualProgram.RETURN_OBJECT -> "Ljava/lang/Object;";
            default -> "I";
        };
    }

    private static int positive(int value) {
        value &= 0x7FFFFFFF;
        return value == 0 ? 1 : value;
    }

    private static int nonZero(int value) {
        return value == 0 ? 0x13579BDF : value;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value == 0 ? 0x13579BDF2468ACE1L : value;
    }

    private static String opaqueLiteral(Random random) {
        long left = mix64(random.nextLong() ^ 0x6A09E667F3BCC909L);
        long right = mix64(random.nextLong() ^ Long.rotateLeft(left, 17));
        return "_" + Long.toUnsignedString(left, 36)
                + '/' + Long.toUnsignedString(right, 36)
                + '$' + Integer.toUnsignedString(mix((int) (left ^ right)), 36);
    }

    private record MethodShape(String desc, int localSlots) {
        private static MethodShape next(Random random) {
            return switch (random.nextInt(6)) {
                case 0 -> new MethodShape("(II)Ljava/lang/Object;", 2);
                case 1 -> new MethodShape("(Ljava/lang/Object;I)Ljava/lang/Object;", 2);
                case 2 -> new MethodShape("(J)Ljava/lang/Object;", 2);
                case 3 -> new MethodShape("(ILjava/lang/Object;)Ljava/io/Serializable;", 2);
                case 4 -> new MethodShape("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Cloneable;", 2);
                default -> new MethodShape("(II)Ljava/io/Serializable;", 2);
            };
        }
    }

    private record ProgramSpec(
            int id,
            int maxLocals,
            int parameterCount,
            int returnKind,
            int[] code,
            Object[] constants,
            int key,
            int[] opcodeTable
    ) {
        private VirtualProgram toProgram(String owner, String hostMethod, String resourceName) {
            List<Object> constantList = new ArrayList<>(constants.length);
            Collections.addAll(constantList, constants);
            return new VirtualProgram(id, maxLocals, parameterCount, returnKind, code,
                    constantList, key, opcodeTable, owner, hostMethod, resourceName);
        }
    }
}
