package biz.sushuo.shield;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

final class ReferenceObfuscator implements Opcodes {
    private static final String RM_BOOTSTRAP_DESC =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIII)Ljava/lang/invoke/CallSite;";
    private static final int CONST_KIND_METHOD_META = 7;
    private static final char METHOD_META_SEPARATOR = '\u001f';

    private ReferenceObfuscator() {
    }

    static int obfuscate(ClassNode classNode, String runtimeClassName, ShieldRemapper remapper,
                         Set<String> projectClasses, NamingPlan namingPlan, long globalSeed,
                         boolean nativeKeys) {
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
                    InsnList replacement = replacement(classNode, method, call, remapper, runtimeClassName,
                            namingPlan, globalSeed, nativeKeys);
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
                && (method.desc.equals("()[Ljava/lang/Object;")
                || method.desc.equals("()Ljava/lang/Object;")
                || method.desc.equals("(I)Ljava/lang/Object;"));
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
            ClassNode classNode,
            MethodNode method,
            MethodInsnNode call,
            ShieldRemapper remapper,
            String runtimeClassName,
            NamingPlan namingPlan,
            long globalSeed,
            boolean nativeKeys
    ) {
        InsnList indyReplacement = invokedynamicReplacement(classNode, method, call, remapper,
                runtimeClassName, namingPlan, globalSeed, nativeKeys);
        if (indyReplacement != null) {
            return indyReplacement;
        }

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

    private static InsnList invokedynamicReplacement(
            ClassNode classNode,
            MethodNode method,
            MethodInsnNode call,
            ShieldRemapper remapper,
            String runtimeClassName,
            NamingPlan namingPlan,
            long globalSeed,
            boolean nativeKeys
    ) {
        if (classNode.version < V1_7) {
            return null;
        }
        int opcode = call.getOpcode();
        if (opcode != INVOKESTATIC && opcode != INVOKEVIRTUAL && opcode != INVOKEINTERFACE) {
            return null;
        }
        try {
            String mappedOwner = remapper.map(call.owner);
            String mappedName = remapper.mapMethodName(call.owner, call.name, call.desc);
            String mappedDescriptor = remapper.mapMethodDesc(call.desc);
            String indyDescriptor = invokedynamicDescriptor(call);
            String mappedIndyDescriptor = remapper.mapMethodDesc(indyDescriptor);
            IndyPayload payload = encodeMethodPayload(classNode, method, call, remapper,
                    mappedOwner, mappedName, mappedDescriptor, mappedIndyDescriptor,
                    namingPlan, globalSeed, nativeKeys);
            InsnList list = new InsnList();
            list.add(new InvokeDynamicInsnNode(
                    payload.siteName(),
                    indyDescriptor,
                    new Handle(H_INVOKESTATIC, runtimeClassName, "_rm", RM_BOOTSTRAP_DESC, false),
                    payload.part0(),
                    payload.part1(),
                    payload.part2(),
                    payload.seed(),
                    payload.salt(),
                    payload.flags(),
                    payload.check(),
                    payload.binding()));
            return list;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static IndyPayload encodeMethodPayload(
            ClassNode classNode,
            MethodNode method,
            MethodInsnNode call,
            ShieldRemapper remapper,
            String mappedOwner,
            String mappedName,
            String mappedDescriptor,
            String mappedIndyDescriptor,
            NamingPlan namingPlan,
            long globalSeed,
            boolean nativeKeys
    ) {
        String mappedCaller = remapper.map(classNode.name);
        String mappedCallerMethod = remapper.mapMethodName(classNode.name, method.name, method.desc);
        String mappedCallerDescriptor = remapper.mapMethodDesc(method.desc);
        int fingerprint = mix(0x494E4459
                ^ mappedCaller.hashCode()
                ^ Integer.rotateLeft(mappedCallerMethod.hashCode(), 5)
                ^ Integer.rotateLeft(mappedCallerDescriptor.hashCode(), 11)
                ^ Integer.rotateLeft(mappedOwner.hashCode(), 17)
                ^ Integer.rotateLeft(mappedName.hashCode(), 23)
                ^ mappedDescriptor.hashCode()
                ^ call.getOpcode() * 0x45D9F3B);
        int seed = mix(fingerprint ^ mappedIndyDescriptor.hashCode() ^ method.instructions.size() * 0x27D4EB2D);
        int salt = mix(seed ^ Integer.rotateLeft(fingerprint, 13)
                ^ mappedOwner.length() * 0x7F4A7C15
                ^ mappedName.length() * 0x165667B1
                ^ mappedDescriptor.length() * 0x9E3779B9);
        String siteName = "_"
                + Integer.toUnsignedString(mix(seed ^ mappedCaller.hashCode() ^ 0x4D434131), 36)
                + Integer.toUnsignedString(mix(salt ^ mappedIndyDescriptor.hashCode() ^ 0x4D434132), 36);

        String clear = mappedOwner + METHOD_META_SEPARATOR + mappedName + METHOD_META_SEPARATOR + mappedDescriptor;
        byte[] plain = clear.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int key = methodMetaKey(seed, salt, mappedCaller, siteName, mappedIndyDescriptor);
        if (nativeKeys) {
            int nativeKey = dynamicIntKey(seed, salt, mappedIndyDescriptor.hashCode(), mappedCaller, siteName)
                    ^ VmPayloadResources.constantMask32(namingPlan, globalSeed, CONST_KIND_METHOD_META,
                    mappedCaller.replace('/', '.'), siteName, seed, salt, mappedIndyDescriptor.hashCode());
            key ^= nativeKey;
        }
        byte[] encrypted = plain.clone();
        int local = mix(key ^ encrypted.length ^ 0x4D43444D);
        for (int i = 0; i < encrypted.length; i++) {
            local = methodMetaStream(local, i);
            encrypted[i] = (byte) (encrypted[i] ^ (local >>> 24));
        }

        byte[][] shards = shard(encrypted, key);
        int flags = call.getOpcode() ^ mix(key ^ 0x4F50434F);
        int check = checksum(plain) ^ mix(key ^ Integer.rotateLeft(plain.length * 0x45D9F3B, 7) ^ 0x4348454B);
        return new IndyPayload(siteName, hex(shards[0]), hex(shards[1]), hex(shards[2]),
                seed, salt, flags, check, nativeKeys ? 1 : 0);
    }

    private static byte[][] shard(byte[] bytes, int key) {
        int lanes = 3;
        int shift = key & 7;
        int[] sizes = new int[lanes];
        for (int i = 0; i < bytes.length; i++) {
            sizes[(i + shift) % lanes]++;
        }
        byte[][] shards = new byte[lanes][];
        for (int i = 0; i < lanes; i++) {
            shards[i] = new byte[sizes[i]];
        }
        int[] positions = new int[lanes];
        for (int i = 0; i < bytes.length; i++) {
            int lane = (i + shift) % lanes;
            shards[lane][positions[lane]++] = bytes[i];
        }
        return shards;
    }

    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = alphabet[value >>> 4];
            chars[i * 2 + 1] = alphabet[value & 15];
        }
        return new String(chars);
    }

    private static int methodMetaKey(int seed, int salt, String owner, String name, String descriptor) {
        int value = mix(seed ^ owner.hashCode() ^ 0x4D434B31);
        value ^= Integer.rotateLeft(name.hashCode(), 7);
        value ^= Integer.rotateLeft(descriptor.hashCode(), 13);
        value = mix(value ^ salt ^ owner.length() * 0x45D9F3B);
        value ^= Integer.rotateLeft(descriptor.length() * 0x27D4EB2D, 9);
        return mix(value ^ 0x4D434B32);
    }

    private static int methodMetaStream(int local, int index) {
        local ^= index * 0x9E3779B9;
        local = Integer.rotateLeft(local + 0x7F4A7C15, 11);
        local ^= local >>> 16;
        local *= 0x85EBCA6B;
        local ^= local >>> 13;
        local *= 0xC2B2AE35;
        local ^= local >>> 16;
        return local == 0 ? 0x13579BDF : local;
    }

    private static int dynamicIntKey(int key, int site, int salt, String owner, String method) {
        int mixed = key ^ Integer.rotateLeft(site * 0x27D4EB2D, 9) ^ salt;
        mixed ^= owner.replace('/', '.').hashCode();
        mixed = Integer.rotateLeft(mixed + 0x165667B1, 7);
        mixed ^= method.hashCode() * 0x85EBCA6B;
        mixed ^= mixed >>> 15;
        mixed *= 0xC2B2AE35;
        return mixed ^ (mixed >>> 16);
    }

    private static int checksum(byte[] bytes) {
        int value = 0x811C9DC5;
        for (byte b : bytes) {
            value ^= b & 0xFF;
            value *= 0x01000193;
            value = Integer.rotateLeft(value, 5) ^ 0x7F4A7C15;
        }
        return mix(value ^ bytes.length);
    }

    private static String invokedynamicDescriptor(MethodInsnNode call) {
        if (call.getOpcode() == INVOKESTATIC) {
            return call.desc;
        }
        Type methodType = Type.getMethodType(call.desc);
        Type[] arguments = methodType.getArgumentTypes();
        Type[] indyArguments = new Type[arguments.length + 1];
        indyArguments[0] = Type.getObjectType(call.owner);
        System.arraycopy(arguments, 0, indyArguments, 1, arguments.length);
        return Type.getMethodDescriptor(methodType.getReturnType(), indyArguments);
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

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private record IndyPayload(String siteName,
                               String part0,
                               String part1,
                               String part2,
                               int seed,
                               int salt,
                               int flags,
                               int check,
                               int binding) {
    }
}
