package biz.sushuo.shield;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class Virtualizer implements Opcodes {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private Virtualizer() {
    }

    static void virtualize(
            ClassNode classNode,
            String runtimeClassName,
            Remapper remapper,
            Set<String> projectClasses,
            long seed,
            boolean nativeOnly,
            boolean minecraftMode,
            VmPayloadResources vmPayloadResources,
            TransformStats stats
    ) {
        virtualize(classNode, runtimeClassName, remapper, projectClasses, seed,
                nativeOnly, minecraftMode, vmPayloadResources, stats, false, nativeOnly);
    }

    static void virtualize(
            ClassNode classNode,
            String runtimeClassName,
            Remapper remapper,
            Set<String> projectClasses,
            long seed,
            boolean nativeOnly,
            boolean minecraftMode,
            VmPayloadResources vmPayloadResources,
            TransformStats stats,
            boolean sdkForcedOnly
    ) {
        virtualize(classNode, runtimeClassName, remapper, projectClasses, seed,
                nativeOnly, minecraftMode, vmPayloadResources, stats, sdkForcedOnly, nativeOnly);
    }

    static void virtualize(
            ClassNode classNode,
            String runtimeClassName,
            Remapper remapper,
            Set<String> projectClasses,
            long seed,
            boolean nativeOnly,
            boolean minecraftMode,
            VmPayloadResources vmPayloadResources,
            TransformStats stats,
            boolean sdkForcedOnly,
            boolean guardProgramAccess
    ) {
        List<MethodNode> extraMethods = new ArrayList<>();
        Set<String> usedProgramMethodKeys = new HashSet<>();
        for (MethodNode method : classNode.methods) {
            usedProgramMethodKeys.add(method.name + method.desc);
        }
        Random programNameRandom = new Random(seed ^ classNode.name.hashCode() ^ 0x56504E4D4554484CL);
        for (MethodNode method : classNode.methods) {
            if (sdkForcedOnly && !SDKMarkerSupport.forceVirtualize(classNode, method)) {
                continue;
            }
            VirtualProgram program = tryCompile(classNode, method, remapper, projectClasses, seed, minecraftMode);
            if (program == null) {
                continue;
            }
            if (nativeOnly && vmPayloadResources != null) {
                program = program.withResourceName(vmPayloadResources.add(program, classNode.name, method.name, method.desc));
            }
            String dataMethodName = nextProgramMethodName(programNameRandom, usedProgramMethodKeys,
                    classNode.name, method.name, method.desc, program.id());
            extraMethods.add(VirtualProgramEmitter.createProgramMethod(
                    classNode.name, dataMethodName, program, nativeOnly, runtimeClassName, guardProgramAccess));
            replaceBody(method, runtimeClassName, classNode.name, dataMethodName, program);
            stats.addVirtualizedMethod(program.code().length);
        }
        classNode.methods.addAll(extraMethods);
    }

    private static String nextProgramMethodName(Random random, Set<String> usedMethodKeys,
                                                String owner, String name, String desc, int id) {
        int attempt = 0;
        while (true) {
            int a = outerMix((int) random.nextLong()
                    ^ owner.hashCode()
                    ^ Integer.rotateLeft(name.hashCode(), 7)
                    ^ Integer.rotateLeft(desc.hashCode(), 13)
                    ^ id * 0x45D9F3B
                    ^ attempt);
            int b = outerMix((int) (random.nextLong() >>> 32)
                    ^ Integer.rotateLeft(a, 11)
                    ^ owner.length() * 0x27D4EB2D
                    ^ attempt * 0x9E3779B9);
            String candidate = "_"
                    + Integer.toUnsignedString(a, 36)
                    + Integer.toUnsignedString(b, 36);
            if (usedMethodKeys.add(candidate + "()Ljava/lang/Object;")) {
                return candidate;
            }
            attempt++;
        }
    }

    private static int outerMix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static VirtualProgram tryCompile(
            ClassNode classNode,
            MethodNode method,
            Remapper remapper,
            Set<String> projectClasses,
            long seed,
            boolean minecraftMode
    ) {
        if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            return null;
        }
        if (method.name.equals("<init>") || method.name.equals("<clinit>") || method.name.equals("main")) {
            return null;
        }
        if (SDKMarkerSupport.noProtect(method)) {
            return null;
        }
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return null;
        }
        if (minecraftMode && isMinecraftRuntimeSensitive(classNode, method)) {
            return null;
        }
        Type methodType = Type.getMethodType(method.desc);
        if (!isSupportedReturn(methodType.getReturnType())) {
            return null;
        }
        for (Type argument : methodType.getArgumentTypes()) {
            if (!isSupportedArgument(argument)) {
                return null;
            }
        }

        Compiler compiler = new Compiler(classNode.name, method, remapper, projectClasses, seed);
        return compiler.compile();
    }

    private static boolean isSupportedArgument(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT,
                    Type.LONG, Type.FLOAT, Type.DOUBLE, Type.OBJECT, Type.ARRAY -> true;
            default -> false;
        };
    }

    private static boolean isSupportedReturn(Type type) {
        return type.getSort() == Type.VOID || isSupportedArgument(type);
    }

    private static boolean isMinecraftRuntimeSensitive(ClassNode classNode, MethodNode method) {
        if (mentionsMinecraftAsyncType(method.desc)) {
            return true;
        }
        if (implementsAny(classNode,
                "java/lang/Runnable",
                "java/util/concurrent/Callable",
                "java/util/function/Supplier",
                "java/util/function/Consumer",
                "java/util/function/Function",
                "java/util/function/BiFunction",
                "java/util/function/Predicate")
                && switch (method.name) {
                    case "run", "call", "get", "accept", "apply", "test" -> true;
                    default -> false;
                }) {
            return true;
        }
        for (AbstractInsnNode instruction = method.instructions == null ? null
                : method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode methodInsn) {
                if (isMinecraftAsyncOwner(methodInsn.owner)
                        || mentionsMinecraftAsyncType(methodInsn.desc)
                        || isAsyncCompletableFutureCall(methodInsn)) {
                    return true;
                }
            } else if (instruction instanceof FieldInsnNode fieldInsn) {
                if (isMinecraftAsyncOwner(fieldInsn.owner) || mentionsMinecraftAsyncType(fieldInsn.desc)) {
                    return true;
                }
            } else if (instruction instanceof TypeInsnNode typeInsn) {
                if (isMinecraftAsyncOwner(typeInsn.desc) || mentionsMinecraftAsyncType(typeInsn.desc)) {
                    return true;
                }
            } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) {
                if (mentionsMinecraftAsyncType(type.getDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean implementsAny(ClassNode classNode, String... interfaces) {
        if (classNode.interfaces == null || classNode.interfaces.isEmpty()) {
            return false;
        }
        for (String implemented : classNode.interfaces) {
            for (String candidate : interfaces) {
                if (implemented.equals(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMinecraftAsyncOwner(String owner) {
        return owner != null && (owner.startsWith("java/util/concurrent/")
                || owner.startsWith("java/util/function/")
                || owner.equals("java/lang/Thread")
                || owner.equals("java/lang/Runnable"));
    }

    private static boolean mentionsMinecraftAsyncType(String descriptor) {
        if (descriptor == null) {
            return false;
        }
        return descriptor.contains("java/util/concurrent/")
                || descriptor.contains("java/util/function/")
                || descriptor.contains("java/lang/Thread")
                || descriptor.contains("java/lang/Runnable");
    }

    private static boolean isAsyncCompletableFutureCall(MethodInsnNode instruction) {
        if (!"java/util/concurrent/CompletableFuture".equals(instruction.owner)) {
            return false;
        }
        return switch (instruction.name) {
            case "cancel", "allOf", "anyOf", "join", "get", "getNow",
                    "complete", "completeExceptionally", "whenComplete", "handle",
                    "thenApply", "thenAccept", "thenRun", "thenCompose",
                    "thenCombine", "runAsync", "supplyAsync" -> true;
            default -> instruction.name.startsWith("then")
                    || instruction.name.endsWith("Async")
                    || instruction.name.contains("Complete");
        };
    }

    private static void replaceBody(
            MethodNode method,
            String runtimeClassName,
            String owner,
            String dataMethodName,
            VirtualProgram program
    ) {
        Type methodType = Type.getMethodType(method.desc);
        Type[] arguments = methodType.getArgumentTypes();
        InsnList body = new InsnList();

        body.add(new MethodInsnNode(INVOKESTATIC, owner, dataMethodName, "()Ljava/lang/Object;", false));
        body.add(new TypeInsnNode(CHECKCAST, "[Ljava/lang/Object;"));
        pushInt(body, method.maxLocals);
        body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

        int local = 0;
        if ((method.access & ACC_STATIC) == 0) {
            body.add(new InsnNode(DUP));
            pushInt(body, local);
            body.add(new VarInsnNode(ALOAD, local));
            body.add(new InsnNode(AASTORE));
            local++;
        }
        for (int i = 0; i < arguments.length; i++) {
            Type argument = arguments[i];
            body.add(new InsnNode(DUP));
            pushInt(body, local);
            addLoad(body, argument, local);
            box(body, argument);
            body.add(new InsnNode(AASTORE));
            local += argument.getSize();
        }

        body.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_v",
                "([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));
        addReturn(body, methodType.getReturnType());

        method.instructions = body;
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.maxLocals = Math.max(method.maxLocals, local);
        method.maxStack = 6;
    }

    private static void addLoad(InsnList body, Type type, int local) {
        body.add(new VarInsnNode(type.getOpcode(ILOAD), local));
    }

    private static void box(InsnList body, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer",
                    "valueOf", "(I)Ljava/lang/Integer;", false));
            case Type.LONG -> body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long",
                    "valueOf", "(J)Ljava/lang/Long;", false));
            case Type.FLOAT -> body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float",
                    "valueOf", "(F)Ljava/lang/Float;", false));
            case Type.DOUBLE -> body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double",
                    "valueOf", "(D)Ljava/lang/Double;", false));
            default -> {
            }
        }
    }

    private static void addReturn(InsnList body, Type returnType) {
        switch (returnType.getSort()) {
            case Type.VOID -> {
                body.add(new InsnNode(POP));
                body.add(new InsnNode(RETURN));
            }
            case Type.BOOLEAN -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new InsnNode(IRETURN));
            }
            case Type.CHAR -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new InsnNode(IRETURN));
            }
            case Type.BYTE -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new InsnNode(IRETURN));
            }
            case Type.SHORT -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                body.add(new InsnNode(IRETURN));
            }
            case Type.INT -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                body.add(new InsnNode(IRETURN));
            }
            case Type.LONG -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Long"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                body.add(new InsnNode(LRETURN));
            }
            case Type.FLOAT -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                body.add(new InsnNode(FRETURN));
            }
            case Type.DOUBLE -> {
                body.add(new TypeInsnNode(CHECKCAST, "java/lang/Double"));
                body.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                body.add(new InsnNode(DRETURN));
            }
            default -> {
                body.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
                body.add(new InsnNode(ARETURN));
            }
        }
    }

    static void pushInt(InsnList body, int value) {
        if (value >= -1 && value <= 5) {
            body.add(new InsnNode(ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            body.add(new IntInsnNode(BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            body.add(new IntInsnNode(SIPUSH, value));
        } else {
            body.add(new LdcInsnNode(value));
        }
    }

    private static final class Compiler {
        private final String owner;
        private final MethodNode method;
        private final Remapper remapper;
        private final Set<String> projectClasses;
        private final long seed;
        private final List<Integer> code = new ArrayList<>();
        private final List<Object> constants = new ArrayList<>();
        private final Map<Object, Integer> constantPool = new HashMap<>();
        private final Map<LabelNode, Integer> labels = new HashMap<>();
        private final List<JumpFixup> jumpFixups = new ArrayList<>();

        private Compiler(String owner, MethodNode method, Remapper remapper, Set<String> projectClasses, long seed) {
            this.owner = owner;
            this.method = method;
            this.remapper = remapper;
            this.projectClasses = projectClasses;
            this.seed = seed;
        }

        private VirtualProgram compile() {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!compileInstruction(instruction)) {
                    return null;
                }
            }
            if (!resolveJumps()) {
                return null;
            }

            Type returnType = Type.getMethodType(method.desc).getReturnType();
            int returnKind = returnKind(returnType);
            int id = NEXT_ID.getAndIncrement();
            int key = programKey(owner, method, id, seed);
            int[] opcodeMap = opcodeMap(key);
            return new VirtualProgram(id, method.maxLocals,
                    Type.getArgumentTypes(method.desc).length, returnKind,
                    mapOpcodes(code.stream().mapToInt(Integer::intValue).toArray(), opcodeMap),
                    new ArrayList<>(constants), key, opcodeMap,
                    remapper.map(owner), remapper.mapMethodName(owner, method.name, method.desc), null);
        }

        private boolean compileInstruction(AbstractInsnNode instruction) {
            if (instruction instanceof LabelNode label) {
                labels.put(label, code.size());
                return true;
            }
            if (instruction instanceof FrameNode || instruction instanceof LineNumberNode) {
                return true;
            }
            return switch (instruction.getOpcode()) {
                case NOP -> true;
                case ACONST_NULL -> {
                    emit(VirtualOp.PUSH_CONST, constant(null));
                    yield true;
                }
                case ICONST_M1 -> emitConst(-1);
                case ICONST_0 -> emitConst(0);
                case ICONST_1 -> emitConst(1);
                case ICONST_2 -> emitConst(2);
                case ICONST_3 -> emitConst(3);
                case ICONST_4 -> emitConst(4);
                case ICONST_5 -> emitConst(5);
                case LCONST_0 -> emitConst(0L);
                case LCONST_1 -> emitConst(1L);
                case FCONST_0 -> emitConst(0.0f);
                case FCONST_1 -> emitConst(1.0f);
                case FCONST_2 -> emitConst(2.0f);
                case DCONST_0 -> emitConst(0.0d);
                case DCONST_1 -> emitConst(1.0d);
                case BIPUSH, SIPUSH -> emitConst(((IntInsnNode) instruction).operand);
                case LDC -> emitLdc((LdcInsnNode) instruction);
                case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> {
                    emit(VirtualOp.LOAD, ((VarInsnNode) instruction).var);
                    yield true;
                }
                case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> {
                    emit(VirtualOp.STORE, ((VarInsnNode) instruction).var);
                    yield true;
                }
                case POP -> {
                    emit(VirtualOp.POP);
                    yield true;
                }
                case DUP -> {
                    emit(VirtualOp.DUP);
                    yield true;
                }
                case IADD -> emitOp(VirtualOp.IADD);
                case ISUB -> emitOp(VirtualOp.ISUB);
                case IMUL -> emitOp(VirtualOp.IMUL);
                case IDIV -> emitOp(VirtualOp.IDIV);
                case IREM -> emitOp(VirtualOp.IREM);
                case INEG -> emitOp(VirtualOp.INEG);
                case IXOR -> emitOp(VirtualOp.IXOR);
                case IAND -> emitOp(VirtualOp.IAND);
                case IOR -> emitOp(VirtualOp.IOR);
                case ISHL -> emitOp(VirtualOp.ISHL);
                case ISHR -> emitOp(VirtualOp.ISHR);
                case IUSHR -> emitOp(VirtualOp.IUSHR);
                case LADD -> emitOp(VirtualOp.LADD);
                case LSUB -> emitOp(VirtualOp.LSUB);
                case LMUL -> emitOp(VirtualOp.LMUL);
                case LDIV -> emitOp(VirtualOp.LDIV);
                case LREM -> emitOp(VirtualOp.LREM);
                case LNEG -> emitOp(VirtualOp.LNEG);
                case LXOR -> emitOp(VirtualOp.LXOR);
                case LSHL -> emitOp(VirtualOp.LSHL);
                case LSHR -> emitOp(VirtualOp.LSHR);
                case LUSHR -> emitOp(VirtualOp.LUSHR);
                case FADD -> emitOp(VirtualOp.FADD);
                case FSUB -> emitOp(VirtualOp.FSUB);
                case FMUL -> emitOp(VirtualOp.FMUL);
                case FDIV -> emitOp(VirtualOp.FDIV);
                case FREM -> emitOp(VirtualOp.FREM);
                case FNEG -> emitOp(VirtualOp.FNEG);
                case DADD -> emitOp(VirtualOp.DADD);
                case DSUB -> emitOp(VirtualOp.DSUB);
                case DMUL -> emitOp(VirtualOp.DMUL);
                case DDIV -> emitOp(VirtualOp.DDIV);
                case DREM -> emitOp(VirtualOp.DREM);
                case DNEG -> emitOp(VirtualOp.DNEG);
                case I2L -> emitOp(VirtualOp.I2L);
                case I2F -> emitOp(VirtualOp.I2F);
                case I2D -> emitOp(VirtualOp.I2D);
                case L2I -> emitOp(VirtualOp.L2I);
                case L2F -> emitOp(VirtualOp.L2F);
                case L2D -> emitOp(VirtualOp.L2D);
                case F2I -> emitOp(VirtualOp.F2I);
                case F2L -> emitOp(VirtualOp.F2L);
                case F2D -> emitOp(VirtualOp.F2D);
                case D2I -> emitOp(VirtualOp.D2I);
                case D2L -> emitOp(VirtualOp.D2L);
                case D2F -> emitOp(VirtualOp.D2F);
                case I2B -> emitOp(VirtualOp.I2B);
                case I2C -> emitOp(VirtualOp.I2C);
                case I2S -> emitOp(VirtualOp.I2S);
                case LCMP -> emitOp(VirtualOp.LCMP);
                case FCMPL -> emitOp(VirtualOp.FCMPL);
                case FCMPG -> emitOp(VirtualOp.FCMPG);
                case DCMPL -> emitOp(VirtualOp.DCMPL);
                case DCMPG -> emitOp(VirtualOp.DCMPG);
                case IINC -> {
                    org.objectweb.asm.tree.IincInsnNode iinc = (org.objectweb.asm.tree.IincInsnNode) instruction;
                    emit(VirtualOp.IINC, iinc.var, iinc.incr);
                    yield true;
                }
                case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN -> {
                    emit(VirtualOp.RETURN);
                    yield true;
                }
                case INVOKESTATIC -> emitInvokeStatic((MethodInsnNode) instruction);
                case INVOKEVIRTUAL, INVOKEINTERFACE, INVOKESPECIAL -> emitInvoke((MethodInsnNode) instruction);
                case GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD -> emitField((FieldInsnNode) instruction);
                case CHECKCAST, INSTANCEOF -> emitType((TypeInsnNode) instruction);
                case GOTO, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                        IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                        IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL -> emitJump((JumpInsnNode) instruction);
                default -> false;
            };
        }

        private boolean emitLdc(LdcInsnNode instruction) {
            Object constant = instruction.cst;
            if (constant instanceof Integer || constant instanceof Long || constant instanceof Float
                    || constant instanceof Double || constant instanceof String) {
                emit(VirtualOp.PUSH_CONST, constant(constant));
                return true;
            }
            if (constant instanceof Type) {
                return false;
            }
            if (constant instanceof Handle) {
                return false;
            }
            return false;
        }

        private boolean emitInvokeStatic(MethodInsnNode instruction) {
            if (instruction.itf || instruction.owner.equals(owner) && instruction.name.startsWith("_vp$")) {
                return false;
            }
            Type methodType = Type.getMethodType(instruction.desc);
            for (Type argument : methodType.getArgumentTypes()) {
                if (!isSupportedArgument(argument)) {
                    return false;
                }
            }
            if (!isSupportedReturn(methodType.getReturnType())) {
                return false;
            }
            String mappedOwner = remapper.map(instruction.owner);
            String mappedName = remapper.mapMethodName(instruction.owner, instruction.name, instruction.desc);
            String mappedDescriptor = remapper.mapMethodDesc(instruction.desc);
            emit(VirtualOp.INVOKE_STATIC, constant(mappedOwner), constant(mappedName),
                    constant(mappedDescriptor), methodType.getArgumentTypes().length);
            return true;
        }

        private boolean emitInvoke(MethodInsnNode instruction) {
            if (instruction.name.equals("<init>")) {
                return false;
            }
            if (instruction.getOpcode() == INVOKESPECIAL && !instruction.owner.equals(owner)) {
                return false;
            }
            Type methodType = Type.getMethodType(instruction.desc);
            for (Type argument : methodType.getArgumentTypes()) {
                if (!isSupportedArgument(argument)) {
                    return false;
                }
            }
            if (!isSupportedReturn(methodType.getReturnType())) {
                return false;
            }
            String mappedOwner = remapper.map(instruction.owner);
            String mappedName = remapper.mapMethodName(instruction.owner, instruction.name, instruction.desc);
            String mappedDescriptor = remapper.mapMethodDesc(instruction.desc);
            emit(VirtualOp.INVOKE, constant(mappedOwner), constant(mappedName),
                    constant(mappedDescriptor), methodType.getArgumentTypes().length, instruction.getOpcode());
            return true;
        }

        private boolean emitField(FieldInsnNode instruction) {
            Type type = Type.getType(instruction.desc);
            if (!isSupportedArgument(type)) {
                return false;
            }
            String mappedOwner = remapper.map(instruction.owner);
            String mappedName = remapper.mapFieldName(instruction.owner, instruction.name, instruction.desc);
            String mappedDescriptor = remapper.mapDesc(instruction.desc);
            int virtualOpcode = switch (instruction.getOpcode()) {
                case GETSTATIC -> VirtualOp.GET_STATIC;
                case PUTSTATIC -> VirtualOp.PUT_STATIC;
                case GETFIELD -> VirtualOp.GET_FIELD;
                case PUTFIELD -> VirtualOp.PUT_FIELD;
                default -> throw new IllegalArgumentException("Bad field opcode " + instruction.getOpcode());
            };
            emit(virtualOpcode, constant(mappedOwner), constant(mappedName), constant(mappedDescriptor));
            return true;
        }

        private boolean emitType(TypeInsnNode instruction) {
            if (instruction.desc == null || instruction.desc.isEmpty()) {
                return false;
            }
            emit(instruction.getOpcode() == CHECKCAST ? VirtualOp.CHECKCAST : VirtualOp.INSTANCEOF,
                    constant(remapper.mapType(instruction.desc)));
            return true;
        }

        private boolean emitJump(JumpInsnNode instruction) {
            int virtualOpcode = switch (instruction.getOpcode()) {
                case GOTO -> VirtualOp.GOTO;
                case IFEQ -> VirtualOp.IFEQ;
                case IFNE -> VirtualOp.IFNE;
                case IFLT -> VirtualOp.IFLT;
                case IFGE -> VirtualOp.IFGE;
                case IFGT -> VirtualOp.IFGT;
                case IFLE -> VirtualOp.IFLE;
                case IF_ICMPEQ -> VirtualOp.IF_ICMPEQ;
                case IF_ICMPNE -> VirtualOp.IF_ICMPNE;
                case IF_ICMPLT -> VirtualOp.IF_ICMPLT;
                case IF_ICMPGE -> VirtualOp.IF_ICMPGE;
                case IF_ICMPGT -> VirtualOp.IF_ICMPGT;
                case IF_ICMPLE -> VirtualOp.IF_ICMPLE;
                case IF_ACMPEQ -> VirtualOp.IF_ACMPEQ;
                case IF_ACMPNE -> VirtualOp.IF_ACMPNE;
                case IFNULL -> VirtualOp.IFNULL;
                case IFNONNULL -> VirtualOp.IFNONNULL;
                default -> throw new IllegalArgumentException("Bad jump opcode " + instruction.getOpcode());
            };
            emit(virtualOpcode, 0);
            jumpFixups.add(new JumpFixup(code.size() - 1, instruction.label));
            return true;
        }

        private boolean resolveJumps() {
            for (JumpFixup fixup : jumpFixups) {
                Integer target = labels.get(fixup.target());
                if (target == null) {
                    return false;
                }
                code.set(fixup.operandIndex(), target);
            }
            return true;
        }

        private boolean emitConst(Object value) {
            emit(VirtualOp.PUSH_CONST, constant(value));
            return true;
        }

        private boolean emitOp(int opcode) {
            emit(opcode);
            return true;
        }

        private void emit(int... values) {
            for (int value : values) {
                code.add(value);
            }
        }

        private int constant(Object value) {
            Object key = ConstantKey.of(value);
            Integer existing = constantPool.get(key);
            if (existing != null) {
                return existing;
            }
            int index = constants.size();
            constants.add(value);
            constantPool.put(key, index);
            return index;
        }

        private static int returnKind(Type returnType) {
            return switch (returnType.getSort()) {
                case Type.VOID -> VirtualProgram.RETURN_VOID;
                case Type.LONG -> VirtualProgram.RETURN_LONG;
                case Type.FLOAT -> VirtualProgram.RETURN_FLOAT;
                case Type.DOUBLE -> VirtualProgram.RETURN_DOUBLE;
                case Type.OBJECT, Type.ARRAY -> VirtualProgram.RETURN_OBJECT;
                default -> VirtualProgram.RETURN_INT;
            };
        }

        private static int programKey(String owner, MethodNode method, int id, long seed) {
            int key = mix((int) seed) ^ mix((int) (seed >>> 32)) ^ 0x6D2B79F5;
            key = mix(key ^ owner.hashCode());
            key = mix(key ^ method.name.hashCode());
            key = mix(key ^ method.desc.hashCode());
            key = mix(key ^ id);
            return key == 0 ? 0x13579BDF : key;
        }

        private static int mix(int value) {
            value ^= value >>> 16;
            value *= 0x7FEB352D;
            value ^= value >>> 15;
            value *= 0x846CA68B;
            value ^= value >>> 16;
            return value;
        }

        private static int[] opcodeMap(int key) {
            int[] logicalOpcodes = VirtualOp.logicalOpcodes();
            List<Integer> physicalOpcodes = new ArrayList<>(logicalOpcodes.length);
            for (int opcode : logicalOpcodes) {
                physicalOpcodes.add(opcode);
            }
            Collections.shuffle(physicalOpcodes, new Random(key ^ 0x51ED270B));
            int[] map = new int[VirtualOp.MAX_OPCODE + 1];
            for (int i = 0; i < logicalOpcodes.length; i++) {
                map[logicalOpcodes[i]] = physicalOpcodes.get(i);
            }
            return map;
        }

        private static int[] mapOpcodes(int[] code, int[] opcodeMap) {
            int[] mapped = code.clone();
            for (int i = 0; i < mapped.length; ) {
                int opcode = mapped[i];
                mapped[i++] = opcodeMap[opcode];
                i += VirtualOp.operandCount(opcode);
            }
            return mapped;
        }
    }

    private record ConstantKey(Object value, String type) {
        static ConstantKey of(Object value) {
            return new ConstantKey(value, value == null ? "null" : value.getClass().getName());
        }
    }

    private record JumpFixup(int operandIndex, LabelNode target) {
    }
}
