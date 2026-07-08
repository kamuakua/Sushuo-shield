package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

final class ReferenceObfuscator implements Opcodes {
    private ReferenceObfuscator() {
    }

    static int obfuscate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper, Set<String> projectClasses) {
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if ((method.access & (ACC_ABSTRACT | ACC_NATIVE | ACC_SYNTHETIC)) != 0
                    || method.instructions == null
                    || method.instructions.size() == 0
                    || isVmDataMethod(method)
                    || SDKMarkerSupport.noProtect(method)) {
                continue;
            }

            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof MethodInsnNode call && canObfuscate(call, runtimeClassName, projectClasses)) {
                    InsnList replacement = replacement(method, call, remapper, runtimeClassName);
                    method.instructions.insert(instruction, replacement);
                    method.instructions.remove(instruction);
                    count++;
                }
                instruction = next;
            }
        }
        return count;
    }

    private static boolean isVmDataMethod(MethodNode method) {
        return (method.access & (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)) == (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)
                && method.desc.equals("()[Ljava/lang/Object;");
    }

    private static boolean canObfuscate(MethodInsnNode call, String runtimeClassName, Set<String> projectClasses) {
        if (call.name.equals("<init>")
                || call.owner.equals(runtimeClassName)
                || call.owner.equals(RuntimeClassGenerator.NATIVE_BRIDGE)
                || call.owner.startsWith("java/lang/invoke/")
                || call.owner.charAt(0) == '[') {
            return false;
        }
        if (call.getOpcode() == INVOKESPECIAL) {
            return false;
        }
        if (!projectClasses.contains(call.owner)) {
            return false;
        }
        Type methodType = Type.getMethodType(call.desc);
        if (!isSupported(methodType.getReturnType())) {
            return false;
        }
        for (Type argument : methodType.getArgumentTypes()) {
            if (!isSupported(argument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSupported(Type type) {
        return switch (type.getSort()) {
            case Type.VOID, Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT,
                    Type.LONG, Type.FLOAT, Type.DOUBLE, Type.OBJECT, Type.ARRAY -> true;
            default -> false;
        };
    }

    private static InsnList replacement(
            MethodNode method,
            MethodInsnNode call,
            ShieldRemapper remapper,
            String runtimeClassName
    ) {
        Type methodType = Type.getMethodType(call.desc);
        Type[] argumentTypes = methodType.getArgumentTypes();
        int[] argLocals = new int[argumentTypes.length];
        int nextLocal = method.maxLocals;
        InsnList list = new InsnList();

        for (int i = argumentTypes.length - 1; i >= 0; i--) {
            Type argument = argumentTypes[i];
            argLocals[i] = nextLocal;
            nextLocal += argument.getSize();
            list.add(new VarInsnNode(argument.getOpcode(ISTORE), argLocals[i]));
        }

        int targetLocal = -1;
        boolean isStatic = call.getOpcode() == INVOKESTATIC;
        if (!isStatic) {
            targetLocal = nextLocal++;
            list.add(new VarInsnNode(ASTORE, targetLocal));
        }
        method.maxLocals = Math.max(method.maxLocals, nextLocal);

        String mappedOwner = remapper.map(call.owner);
        String mappedName = remapper.mapMethodName(call.owner, call.name, call.desc);
        String mappedDescriptor = remapper.mapMethodDesc(call.desc);
        list.add(new LdcInsnNode(mappedOwner));
        list.add(new LdcInsnNode(mappedName));
        list.add(new LdcInsnNode(mappedDescriptor));
        if (!isStatic) {
            list.add(new VarInsnNode(ALOAD, targetLocal));
        }
        addArgsArray(list, argumentTypes, argLocals);
        if (isStatic) {
            list.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_rs",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false));
        } else {
            pushInt(list, call.getOpcode());
            list.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_ri",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;", false));
        }
        adaptReturn(list, methodType.getReturnType());
        return list;
    }

    private static void addArgsArray(InsnList list, Type[] argumentTypes, int[] argLocals) {
        pushInt(list, argumentTypes.length);
        list.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        for (int i = 0; i < argumentTypes.length; i++) {
            Type type = argumentTypes[i];
            list.add(new InsnNode(DUP));
            pushInt(list, i);
            list.add(new VarInsnNode(type.getOpcode(ILOAD), argLocals[i]));
            box(list, type);
            list.add(new InsnNode(AASTORE));
        }
    }

    private static void adaptReturn(InsnList list, Type type) {
        switch (type.getSort()) {
            case Type.VOID -> list.add(new InsnNode(POP));
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> {
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
            }
            case Type.LONG -> {
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Long"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
            }
            case Type.FLOAT -> {
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
            }
            case Type.DOUBLE -> {
                list.add(new TypeInsnNode(CHECKCAST, "java/lang/Double"));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
            }
            case Type.ARRAY -> list.add(new TypeInsnNode(CHECKCAST, type.getDescriptor()));
            case Type.OBJECT -> list.add(new TypeInsnNode(CHECKCAST, type.getInternalName()));
            default -> throw new IllegalArgumentException("Unsupported return type: " + type);
        }
    }

    private static void box(InsnList list, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean",
                    "valueOf", "(Z)Ljava/lang/Boolean;", false));
            case Type.CHAR -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Character",
                    "valueOf", "(C)Ljava/lang/Character;", false));
            case Type.BYTE -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Byte",
                    "valueOf", "(B)Ljava/lang/Byte;", false));
            case Type.SHORT -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Short",
                    "valueOf", "(S)Ljava/lang/Short;", false));
            case Type.INT -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer",
                    "valueOf", "(I)Ljava/lang/Integer;", false));
            case Type.FLOAT -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float",
                    "valueOf", "(F)Ljava/lang/Float;", false));
            case Type.LONG -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long",
                    "valueOf", "(J)Ljava/lang/Long;", false));
            case Type.DOUBLE -> list.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double",
                    "valueOf", "(D)Ljava/lang/Double;", false));
            default -> {
            }
        }
    }

    private static void pushInt(InsnList list, int value) {
        if (value >= -1 && value <= 5) {
            list.add(new InsnNode(ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            list.add(new IntInsnNode(BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            list.add(new IntInsnNode(SIPUSH, value));
        } else {
            list.add(new LdcInsnNode(value));
        }
    }
}
