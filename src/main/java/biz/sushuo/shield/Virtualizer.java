package biz.sushuo.shield;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class Virtualizer implements Opcodes {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private Virtualizer() {
    }

    static void virtualize(ClassNode classNode, String runtimeClassName, Remapper remapper, TransformStats stats) {
        List<MethodNode> extraMethods = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            VirtualProgram program = tryCompile(classNode, method, remapper);
            if (program == null) {
                continue;
            }
            String dataMethodName = "_vp$" + program.id();
            extraMethods.add(VirtualProgramEmitter.createProgramMethod(classNode.name, dataMethodName, program));
            replaceBody(method, runtimeClassName, classNode.name, dataMethodName, program);
            stats.addVirtualizedMethod(program.code().length);
        }
        classNode.methods.addAll(extraMethods);
    }

    private static VirtualProgram tryCompile(ClassNode classNode, MethodNode method, Remapper remapper) {
        if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            return null;
        }
        if ((method.access & ACC_STATIC) == 0) {
            return null;
        }
        if (method.name.equals("<clinit>") || method.name.equals("main")) {
            return null;
        }
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
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

        Compiler compiler = new Compiler(classNode.name, method, remapper);
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

        body.add(new MethodInsnNode(INVOKESTATIC, owner, dataMethodName, "()[Ljava/lang/Object;", false));
        pushInt(body, method.maxLocals);
        body.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

        int local = 0;
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
        private final List<Integer> code = new ArrayList<>();
        private final List<Object> constants = new ArrayList<>();
        private final Map<Object, Integer> constantPool = new HashMap<>();

        private Compiler(String owner, MethodNode method, Remapper remapper) {
            this.owner = owner;
            this.method = method;
            this.remapper = remapper;
        }

        private VirtualProgram compile() {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!compileInstruction(instruction)) {
                    return null;
                }
            }

            Type returnType = Type.getMethodType(method.desc).getReturnType();
            int returnKind = returnKind(returnType);
            return new VirtualProgram(NEXT_ID.getAndIncrement(), method.maxLocals,
                    Type.getArgumentTypes(method.desc).length, returnKind,
                    code.stream().mapToInt(Integer::intValue).toArray(),
                    List.copyOf(constants));
        }

        private boolean compileInstruction(AbstractInsnNode instruction) {
            if (instruction instanceof LabelNode || instruction instanceof FrameNode || instruction instanceof LineNumberNode) {
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
                case LADD -> emitOp(VirtualOp.LADD);
                case LSUB -> emitOp(VirtualOp.LSUB);
                case LMUL -> emitOp(VirtualOp.LMUL);
                case LDIV -> emitOp(VirtualOp.LDIV);
                case LREM -> emitOp(VirtualOp.LREM);
                case LNEG -> emitOp(VirtualOp.LNEG);
                case LXOR -> emitOp(VirtualOp.LXOR);
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
                case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN -> {
                    emit(VirtualOp.RETURN);
                    yield true;
                }
                case INVOKESTATIC -> emitInvokeStatic((MethodInsnNode) instruction);
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
    }

    private record ConstantKey(Object value, String type) {
        static ConstantKey of(Object value) {
            return new ConstantKey(value, value == null ? "null" : value.getClass().getName());
        }
    }
}
