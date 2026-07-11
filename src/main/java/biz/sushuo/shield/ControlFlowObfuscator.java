package biz.sushuo.shield;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ControlFlowObfuscator implements Opcodes {
    private static final int MIN_FLATTEN_REAL_INSTRUCTIONS = 4;
    private static final int MAX_FLATTEN_REAL_INSTRUCTIONS = 96;
    private static final int TARGET_BLOCK_REAL_INSTRUCTIONS = 2;
    private static final int MAX_FLATTEN_BLOCKS = 32;
    private static final int UNKNOWN_STACK_DELTA = Integer.MIN_VALUE;

    private ControlFlowObfuscator() {
    }

    static int apply(ClassNode classNode, String runtimeClassName) {
        return apply(classNode, runtimeClassName, false);
    }

    static int applyForcedMutate(ClassNode classNode, String runtimeClassName) {
        return apply(classNode, runtimeClassName, true);
    }

    private static int apply(ClassNode classNode, String runtimeClassName, boolean forcedMutateOnly) {
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null || method.instructions.size() == 0) {
                continue;
            }
            if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0 || method.name.equals("<init>")) {
                continue;
            }
            if (SDKMarkerSupport.noProtect(method)) {
                continue;
            }
            if (forcedMutateOnly && !SDKMarkerSupport.forceMutate(classNode, method)) {
                continue;
            }

            boolean flattened;
            try {
                flattened = flattenSimpleStraightLine(classNode, method, runtimeClassName);
            } catch (RuntimeException ex) {
                flattened = false;
            }
            if (!flattened) {
                addEntryGuard(method, runtimeClassName);
            }
            count++;
        }
        return count;
    }

    private static boolean flattenSimpleStraightLine(ClassNode classNode, MethodNode method, String runtimeClassName) {
        FlatteningPlan plan = buildFlatteningPlan(method);
        if (plan == null) {
            return false;
        }

        int stateLocal = method.maxLocals;
        int blockCount = plan.blocks.size();
        int[] blockKeys = stateKeys(classNode.name, method, blockCount);
        LabelNode dispatch = new LabelNode();
        LabelNode bad = new LabelNode();
        LabelNode[] caseLabels = new LabelNode[blockCount];
        for (int i = 0; i < blockCount; i++) {
            caseLabels[i] = new LabelNode();
        }

        List<Integer> switchOrder = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            switchOrder.add(i);
        }
        switchOrder.sort(Comparator.comparingInt(index -> blockKeys[index]));

        int[] switchKeys = new int[blockCount];
        LabelNode[] switchLabels = new LabelNode[blockCount];
        for (int i = 0; i < blockCount; i++) {
            int blockIndex = switchOrder.get(i);
            switchKeys[i] = blockKeys[blockIndex];
            switchLabels[i] = caseLabels[blockIndex];
        }

        InsnList original = method.instructions;
        InsnList flattened = new InsnList();
        addEntryGuardInstructions(flattened, runtimeClassName);
        for (LocalInit init : plan.localInits) {
            addLocalDefaultInitialization(flattened, init);
        }
        addPushInt(flattened, blockKeys[0]);
        flattened.add(new VarInsnNode(ISTORE, stateLocal));
        flattened.add(dispatch);
        flattened.add(new VarInsnNode(ILOAD, stateLocal));
        flattened.add(new LookupSwitchInsnNode(bad, switchKeys, switchLabels));
        flattened.add(bad);
        addBadThrow(flattened);

        for (int i = 0; i < blockCount; i++) {
            flattened.add(caseLabels[i]);
            List<AbstractInsnNode> block = plan.blocks.get(i);
            for (AbstractInsnNode instruction : block) {
                original.remove(instruction);
                flattened.add(instruction);
            }
            if (!endsWithReturn(block)) {
                addPushInt(flattened, blockKeys[i + 1]);
                flattened.add(new VarInsnNode(ISTORE, stateLocal));
                flattened.add(new JumpInsnNode(GOTO, dispatch));
            }
        }

        method.instructions = flattened;
        method.maxLocals = Math.max(method.maxLocals, stateLocal + 1);
        method.maxStack = Math.max(method.maxStack, 3);
        return true;
    }

    private static FlatteningPlan buildFlatteningPlan(MethodNode method) {
        if (method.instructions == null || method.instructions.size() == 0) {
            return null;
        }
        if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
            return null;
        }
        if ((method.access & (ACC_SYNTHETIC | ACC_BRIDGE | ACC_SYNCHRONIZED)) != 0) {
            return null;
        }
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return null;
        }

        List<List<AbstractInsnNode>> blocks = new ArrayList<>();
        List<AbstractInsnNode> current = new ArrayList<>();
        Map<Integer, LocalKind> storedLocals = new TreeMap<>();
        int argumentLocalLimit = argumentLocalLimit(method);
        int totalRealInstructions = 0;
        int blockRealInstructions = 0;
        int stack = 0;
        boolean sawReturn = false;

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof FrameNode) {
                continue;
            }

            int opcode = instruction.getOpcode();
            if (opcode < 0) {
                current.add(instruction);
                continue;
            }
            if (sawReturn || isForbiddenForStraightLine(instruction)) {
                return null;
            }

            int delta = stackDelta(instruction);
            if (delta == UNKNOWN_STACK_DELTA) {
                return null;
            }

            current.add(instruction);
            totalRealInstructions++;
            blockRealInstructions++;
            stack += delta;
            if (stack < 0) {
                return null;
            }
            if (!recordStore(storedLocals, argumentLocalLimit, instruction)) {
                return null;
            }

            if (isReturn(opcode)) {
                if (stack != 0) {
                    return null;
                }
                sawReturn = true;
            } else if (stack == 0
                    && blockRealInstructions >= TARGET_BLOCK_REAL_INSTRUCTIONS
                    && blocks.size() < MAX_FLATTEN_BLOCKS - 1) {
                blocks.add(current);
                current = new ArrayList<>();
                blockRealInstructions = 0;
            }
        }

        if (!sawReturn
                || stack != 0
                || totalRealInstructions < MIN_FLATTEN_REAL_INSTRUCTIONS
                || totalRealInstructions > MAX_FLATTEN_REAL_INSTRUCTIONS
                || current.isEmpty()) {
            return null;
        }
        blocks.add(current);
        if (blocks.size() < 2 || blocks.size() > MAX_FLATTEN_BLOCKS) {
            return null;
        }
        List<LocalInit> localInits = localInitializers(storedLocals);
        if (localInits == null) {
            return null;
        }
        return new FlatteningPlan(blocks, localInits);
    }

    private static int argumentLocalLimit(MethodNode method) {
        int slot = (method.access & ACC_STATIC) == 0 ? 1 : 0;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            slot += argument.getSize();
        }
        return slot;
    }

    private static boolean recordStore(Map<Integer, LocalKind> storedLocals, int argumentLocalLimit, AbstractInsnNode instruction) {
        if (!(instruction instanceof VarInsnNode varInsn)) {
            return true;
        }
        LocalKind kind = switch (instruction.getOpcode()) {
            case ISTORE -> LocalKind.INT;
            case FSTORE -> LocalKind.FLOAT;
            case LSTORE -> LocalKind.LONG;
            case DSTORE -> LocalKind.DOUBLE;
            case ASTORE -> LocalKind.REF;
            default -> null;
        };
        if (kind == null || varInsn.var < argumentLocalLimit) {
            return true;
        }
        LocalKind previous = storedLocals.putIfAbsent(varInsn.var, kind);
        return previous == null || previous == kind;
    }

    private static List<LocalInit> localInitializers(Map<Integer, LocalKind> storedLocals) {
        List<LocalInit> result = new ArrayList<>();
        for (Map.Entry<Integer, LocalKind> entry : storedLocals.entrySet()) {
            int slot = entry.getKey();
            LocalKind kind = entry.getValue();
            if (kind.size == 2 && storedLocals.containsKey(slot + 1)) {
                return null;
            }
            result.add(new LocalInit(slot, kind));
        }
        return result;
    }

    private static boolean isForbiddenForStraightLine(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        return instruction instanceof JumpInsnNode
                || instruction instanceof LookupSwitchInsnNode
                || instruction instanceof TableSwitchInsnNode
                || instruction instanceof MethodInsnNode
                || instruction instanceof InvokeDynamicInsnNode
                || instruction instanceof FieldInsnNode
                || instruction instanceof TypeInsnNode
                || instruction instanceof MultiANewArrayInsnNode
                || opcode == ATHROW
                || opcode == MONITORENTER
                || opcode == MONITOREXIT
                || opcode == RET
                || opcode == NEWARRAY
                || opcode == IALOAD
                || opcode == LALOAD
                || opcode == FALOAD
                || opcode == DALOAD
                || opcode == AALOAD
                || opcode == BALOAD
                || opcode == CALOAD
                || opcode == SALOAD
                || opcode == IASTORE
                || opcode == LASTORE
                || opcode == FASTORE
                || opcode == DASTORE
                || opcode == AASTORE
                || opcode == BASTORE
                || opcode == CASTORE
                || opcode == SASTORE
                || isComplexLdc(instruction);
    }

    private static boolean isComplexLdc(AbstractInsnNode instruction) {
        if (!(instruction instanceof LdcInsnNode ldc)) {
            return false;
        }
        Object constant = ldc.cst;
        return constant instanceof Type
                || constant instanceof org.objectweb.asm.Handle
                || constant instanceof ConstantDynamic;
    }

    private static int stackDelta(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        switch (opcode) {
            case NOP:
            case INEG:
            case LNEG:
            case FNEG:
            case DNEG:
            case I2F:
            case L2D:
            case F2I:
            case D2L:
            case I2B:
            case I2C:
            case I2S:
            case ARRAYLENGTH:
            case CHECKCAST:
            case INSTANCEOF:
                return 0;
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case BIPUSH:
            case SIPUSH:
            case ILOAD:
            case FLOAD:
            case ALOAD:
            case I2L:
            case I2D:
            case F2L:
            case F2D:
            case NEW:
                return 1;
            case LCONST_0:
            case LCONST_1:
            case DCONST_0:
            case DCONST_1:
            case LLOAD:
            case DLOAD:
            case DUP2:
            case DUP2_X1:
            case DUP2_X2:
                return 2;
            case LDC:
                Object constant = ((LdcInsnNode) instruction).cst;
                if (constant instanceof Long || constant instanceof Double) {
                    return 2;
                }
                if (constant instanceof ConstantDynamic dynamic) {
                    return Type.getType(dynamic.getDescriptor()).getSize();
                }
                return 1;
            case IALOAD:
            case FALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case ISTORE:
            case FSTORE:
            case ASTORE:
            case POP:
            case IADD:
            case FADD:
            case ISUB:
            case FSUB:
            case IMUL:
            case FMUL:
            case IDIV:
            case FDIV:
            case IREM:
            case FREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
            case L2I:
            case L2F:
            case D2I:
            case D2F:
            case IRETURN:
            case FRETURN:
            case ARETURN:
                return -1;
            case LALOAD:
            case DALOAD:
            case LSTORE:
            case DSTORE:
            case POP2:
            case LADD:
            case DADD:
            case LSUB:
            case DSUB:
            case LMUL:
            case DMUL:
            case LDIV:
            case DDIV:
            case LREM:
            case DREM:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
            case LRETURN:
            case DRETURN:
                return -2;
            case IASTORE:
            case FASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
            case LCMP:
            case DCMPL:
            case DCMPG:
                return -3;
            case LASTORE:
            case DASTORE:
                return -4;
            case DUP:
            case DUP_X1:
            case DUP_X2:
                return 1;
            case SWAP:
            case IINC:
            case RETURN:
                return 0;
            case FCMPL:
            case FCMPG:
                return -1;
            case GETSTATIC:
                return Type.getType(((FieldInsnNode) instruction).desc).getSize();
            case PUTSTATIC:
                return -Type.getType(((FieldInsnNode) instruction).desc).getSize();
            case GETFIELD:
                return Type.getType(((FieldInsnNode) instruction).desc).getSize() - 1;
            case PUTFIELD:
                return -Type.getType(((FieldInsnNode) instruction).desc).getSize() - 1;
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE:
                MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                int methodArguments = Type.getArgumentsAndReturnSizes(methodInsn.desc) >> 2;
                return Type.getReturnType(methodInsn.desc).getSize()
                        - (opcode == INVOKESTATIC ? methodArguments - 1 : methodArguments);
            case INVOKEDYNAMIC:
                InvokeDynamicInsnNode dynamicInsn = (InvokeDynamicInsnNode) instruction;
                int dynamicArguments = Type.getArgumentsAndReturnSizes(dynamicInsn.desc) >> 2;
                return Type.getReturnType(dynamicInsn.desc).getSize()
                        - (dynamicArguments - 1);
            case NEWARRAY:
            case ANEWARRAY:
                return 0;
            case MULTIANEWARRAY:
                return 1 - ((MultiANewArrayInsnNode) instruction).dims;
            default:
                if (instruction instanceof IntInsnNode) {
                    return UNKNOWN_STACK_DELTA;
                }
                return UNKNOWN_STACK_DELTA;
        }
    }

    private static boolean isReturn(int opcode) {
        return opcode >= IRETURN && opcode <= RETURN;
    }

    private static boolean endsWithReturn(List<AbstractInsnNode> block) {
        for (int i = block.size() - 1; i >= 0; i--) {
            int opcode = block.get(i).getOpcode();
            if (opcode >= 0) {
                return isReturn(opcode);
            }
        }
        return false;
    }

    private static int[] stateKeys(String owner, MethodNode method, int blockCount) {
        int base = 0x6D2B79F5 ^ owner.hashCode();
        base = Integer.rotateLeft(base ^ method.name.hashCode(), 7);
        base = Integer.rotateLeft(base ^ method.desc.hashCode(), 11);

        int[] keys = new int[blockCount];
        for (int i = 0; i < blockCount; i++) {
            keys[i] = base + (i * 0x1F123BB5);
        }
        return keys;
    }

    private static void addPushInt(InsnList instructions, int value) {
        if (value >= -1 && value <= 5) {
            instructions.add(new InsnNode(ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            instructions.add(new IntInsnNode(BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            instructions.add(new IntInsnNode(SIPUSH, value));
        } else {
            instructions.add(new LdcInsnNode(value));
        }
    }

    private static void addLocalDefaultInitialization(InsnList instructions, LocalInit init) {
        switch (init.kind) {
            case INT -> {
                instructions.add(new InsnNode(ICONST_0));
                instructions.add(new VarInsnNode(ISTORE, init.slot));
            }
            case FLOAT -> {
                instructions.add(new InsnNode(FCONST_0));
                instructions.add(new VarInsnNode(FSTORE, init.slot));
            }
            case LONG -> {
                instructions.add(new InsnNode(LCONST_0));
                instructions.add(new VarInsnNode(LSTORE, init.slot));
            }
            case DOUBLE -> {
                instructions.add(new InsnNode(DCONST_0));
                instructions.add(new VarInsnNode(DSTORE, init.slot));
            }
            case REF -> {
                instructions.add(new InsnNode(ACONST_NULL));
                instructions.add(new VarInsnNode(ASTORE, init.slot));
            }
        }
    }

    private static void addEntryGuard(MethodNode method, String runtimeClassName) {
        InsnList guard = new InsnList();
        addEntryGuardInstructions(guard, runtimeClassName);

        AbstractInsnNode first = firstRealInstruction(method);
        if (first == null) {
            method.instructions.add(guard);
        } else {
            method.instructions.insertBefore(first, guard);
        }
    }

    private static void addEntryGuardInstructions(InsnList instructions, String runtimeClassName) {
        LabelNode ok = new LabelNode();
        instructions.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
        instructions.add(new JumpInsnNode(IFNE, ok));
        addBadThrow(instructions);
        instructions.add(ok);
    }

    private static void addBadThrow(InsnList instructions) {
        instructions.add(new TypeInsnNode(NEW, "java/lang/IllegalStateException"));
        instructions.add(new InsnNode(DUP));
        instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false));
        instructions.add(new InsnNode(ATHROW));
    }

    private static AbstractInsnNode firstRealInstruction(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof LabelNode)
                    && !(instruction instanceof LineNumberNode)
                    && !(instruction instanceof FrameNode)) {
                return instruction;
            }
        }
        return null;
    }

    private enum LocalKind {
        INT(1), FLOAT(1), LONG(2), DOUBLE(2), REF(1);

        final int size;

        LocalKind(int size) {
            this.size = size;
        }
    }

    private record LocalInit(int slot, LocalKind kind) {
    }

    private record FlatteningPlan(List<List<AbstractInsnNode>> blocks, List<LocalInit> localInits) {
    }
}
