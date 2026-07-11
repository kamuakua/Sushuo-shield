package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class BootstrapShardObfuscator implements Opcodes {
    private static final int SHARD_COUNT = 4;
    private static final String BOOTSTRAP_PREFIX =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;";
    private static final String CALL_SITE_RETURN = ")Ljava/lang/invoke/CallSite;";
    private static final String CALL_SITE_SUFFIX = ")Ljava/lang/invoke/CallSite;";
    private static final String STRING_BOOTSTRAP_DESC =
            BOOTSTRAP_PREFIX + "Ljava/lang/String;IIII" + CALL_SITE_RETURN;
    private static final String INT_BOOTSTRAP_DESC =
            BOOTSTRAP_PREFIX + "IIIII" + CALL_SITE_RETURN;
    private static final String LONG_BOOTSTRAP_DESC =
            BOOTSTRAP_PREFIX + "JJIII" + CALL_SITE_RETURN;
    private static final String DISPATCH_DESC =
            "([Ljava/lang/Object;ILjava/lang/String;I)Ljava/lang/invoke/CallSite;";

    private BootstrapShardObfuscator() {
    }

    static Map<String, byte[]> distribute(
            Map<String, byte[]> classes,
            String runtimeClassName,
            Map<RuntimeApiObfuscator.MemberSig, String> runtimeApiNames,
            long seed
    ) {
        Set<BootstrapMethod> mappedBootstraps = mappedBootstraps(runtimeApiNames);
        if (mappedBootstraps.isEmpty()) {
            return classes;
        }

        List<String> shardNames = shardNames(runtimeClassName, seed, classes.keySet());
        String dispatchClassName = RuntimeClassGenerator.bootstrapDispatchClassName(runtimeClassName);
        String dispatchMethodName = RuntimeClassGenerator.bootstrapDispatchMethodName(dispatchClassName);
        Map<BootstrapMethod, Integer> siteCounts = countBootstrapSites(
                classes, runtimeClassName, mappedBootstraps);
        Map<BootstrapMethod, List<String>> routes = routes(siteCounts, shardNames, seed);
        Map<String, Set<AdapterMethod>> usedMethods = new LinkedHashMap<>();
        for (String shardName : shardNames) {
            usedMethods.put(shardName, new HashSet<>());
        }

        Map<String, byte[]> rewritten = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            rewritten.put(entry.getKey(), rewriteClass(entry.getValue(), runtimeClassName,
                    routes, usedMethods, seed));
        }
        for (String shardName : shardNames) {
            Set<AdapterMethod> methods = usedMethods.get(shardName);
            if (!methods.isEmpty()) {
                rewritten.put(shardName + ".class", generateShard(shardName, runtimeClassName,
                        dispatchClassName, dispatchMethodName, methods, seed));
            }
        }
        return rewritten;
    }

    private static Set<BootstrapMethod> mappedBootstraps(
            Map<RuntimeApiObfuscator.MemberSig, String> runtimeApiNames
    ) {
        Set<BootstrapMethod> mapped = new HashSet<>();
        for (Map.Entry<RuntimeApiObfuscator.MemberSig, String> entry : runtimeApiNames.entrySet()) {
            RuntimeApiObfuscator.MemberSig signature = entry.getKey();
            if (signature.desc().endsWith(CALL_SITE_SUFFIX)) {
                mapped.add(new BootstrapMethod(entry.getValue(), signature.desc()));
            }
        }
        return mapped;
    }

    private static Map<BootstrapMethod, Integer> countBootstrapSites(
            Map<String, byte[]> classes,
            String runtimeClassName,
            Set<BootstrapMethod> mappedBootstraps
    ) {
        Map<BootstrapMethod, Integer> counts = new HashMap<>();
        for (byte[] bytes : classes.values()) {
            ClassNode classNode = new ClassNode();
            new ClassReader(bytes).accept(classNode, 0);
            for (MethodNode method : classNode.methods) {
                if (method.instructions == null) {
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (instruction instanceof InvokeDynamicInsnNode indy
                            && indy.bsm != null
                            && runtimeClassName.equals(indy.bsm.getOwner())) {
                        BootstrapMethod bootstrap = new BootstrapMethod(indy.bsm.getName(), indy.bsm.getDesc());
                        if (mappedBootstraps.contains(bootstrap)) {
                            counts.merge(bootstrap, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return counts;
    }

    private static Map<BootstrapMethod, List<String>> routes(
            Map<BootstrapMethod, Integer> siteCounts,
            List<String> shardNames,
            long seed
    ) {
        List<Map.Entry<BootstrapMethod, Integer>> ordered = new ArrayList<>(siteCounts.entrySet());
        ordered.sort(Map.Entry.<BootstrapMethod, Integer>comparingByValue().reversed()
                .thenComparing(entry -> entry.getKey().name())
                .thenComparing(entry -> entry.getKey().desc()));

        long[] predictedLoad = new long[shardNames.size()];
        Map<BootstrapMethod, List<String>> routes = new HashMap<>();
        for (Map.Entry<BootstrapMethod, Integer> entry : ordered) {
            BootstrapMethod method = entry.getKey();
            int count = entry.getValue();
            int replicas = count >= 256 ? shardNames.size() : count >= 32 ? 2 : 1;
            List<Integer> candidates = new ArrayList<>(shardNames.size());
            for (int index = 0; index < shardNames.size(); index++) {
                candidates.add(index);
            }
            candidates.sort(Comparator
                    .comparingLong((Integer index) -> predictedLoad[index])
                    .thenComparingInt(index -> mix((int) seed
                            ^ method.name().hashCode()
                            ^ Integer.rotateLeft(method.desc().hashCode(), 7)
                            ^ index * 0x45D9F3B)));
            List<String> selected = new ArrayList<>(replicas);
            long addedLoad = (count + replicas - 1L) / replicas;
            for (int i = 0; i < replicas; i++) {
                int index = candidates.get(i);
                selected.add(shardNames.get(index));
                predictedLoad[index] += addedLoad;
            }
            routes.put(method, List.copyOf(selected));
        }
        return routes;
    }

    private static byte[] rewriteClass(
            byte[] bytes,
            String runtimeClassName,
            Map<BootstrapMethod, List<String>> routes,
            Map<String, Set<AdapterMethod>> usedMethods,
            long seed
    ) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        boolean changed = false;
        int ordinal = 0;
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof InvokeDynamicInsnNode indy)
                        || indy.bsm == null
                        || !runtimeClassName.equals(indy.bsm.getOwner())) {
                    continue;
                }
                BootstrapMethod bootstrap = new BootstrapMethod(indy.bsm.getName(), indy.bsm.getDesc());
                List<String> methodRoutes = routes.get(bootstrap);
                if (methodRoutes == null || methodRoutes.isEmpty()) {
                    continue;
                }
                int selector = mix((int) seed
                        ^ (int) (seed >>> 32)
                        ^ classNode.name.hashCode()
                        ^ Integer.rotateLeft(method.name.hashCode(), 5)
                        ^ Integer.rotateLeft(method.desc.hashCode(), 11)
                        ^ Integer.rotateLeft(indy.name.hashCode(), 17)
                        ^ ordinal++ * 0x45D9F3B);
                AdaptedBootstrap adapted = adaptBootstrap(bootstrap, indy.bsmArgs, selector);
                int routeIndex;
                if (adapted.routeBucket() < 0) {
                    routeIndex = Math.floorMod(selector, methodRoutes.size());
                } else {
                    int routeOffset = mix(bootstrap.name().hashCode()
                            ^ Integer.rotateLeft(bootstrap.desc().hashCode(), 9));
                    routeIndex = Math.floorMod(routeOffset + adapted.routeBucket(), methodRoutes.size());
                }
                String shardName = methodRoutes.get(routeIndex);
                indy.bsmArgs = adapted.arguments();
                indy.bsm = new Handle(indy.bsm.getTag(), shardName, indy.bsm.getName(),
                        adapted.method().adapterDesc(), false);
                usedMethods.get(shardName).add(adapted.method());
                changed = true;
            }
        }
        if (!changed) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] generateShard(
            String shardName,
            String runtimeClassName,
            String dispatchClassName,
            String dispatchMethodName,
            Set<AdapterMethod> methods,
            long seed
    ) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC,
                shardName, null, "java/lang/Object", null);

        MethodVisitor constructor = writer.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        List<AdapterMethod> ordered = new ArrayList<>(methods);
        ordered.sort(Comparator.comparing((AdapterMethod method) -> method.target().name())
                .thenComparing(AdapterMethod::adapterDesc));
        for (AdapterMethod method : ordered) {
            MethodVisitor visitor = writer.visitMethod(
                    ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC,
                    method.target().name(), method.adapterDesc(), null, null);
            visitor.visitCode();
            emitDispatcherCall(visitor, method, runtimeClassName, shardName,
                    dispatchClassName, dispatchMethodName, seed);
            visitor.visitInsn(ARETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static AdaptedBootstrap adaptBootstrap(
            BootstrapMethod target,
            Object[] arguments,
            int selector
    ) {
        int variant = Math.floorMod(mix(selector ^ target.name().hashCode()
                ^ Integer.rotateLeft(target.desc().hashCode(), 13)), 4);
        if (INT_BOOTSTRAP_DESC.equals(target.desc()) && hasTypes(arguments,
                Integer.class, Integer.class, Integer.class, Integer.class, Integer.class)) {
            int encrypted = (Integer) arguments[0];
            int key = (Integer) arguments[1];
            int site = (Integer) arguments[2];
            int salt = (Integer) arguments[3];
            int flags = (Integer) arguments[4];
            return switch (variant) {
                case 1 -> adapted(target, AdapterLayout.INT_PACK_HEAD,
                        pack(encrypted, key), site, salt, flags);
                case 2 -> adapted(target, AdapterLayout.INT_PACK_TAIL,
                        encrypted, pack(key, site), pack(salt, flags));
                case 3 -> adapted(target, AdapterLayout.INT_PACK_ALL,
                        pack(encrypted, key), pack(site, salt),
                        pack(flags, mix(encrypted ^ key ^ site ^ salt)));
                default -> adapted(target, AdapterLayout.PASSTHROUGH, arguments);
            };
        }
        if (LONG_BOOTSTRAP_DESC.equals(target.desc()) && hasTypes(arguments,
                Long.class, Long.class, Integer.class, Integer.class, Integer.class)) {
            long encrypted = (Long) arguments[0];
            long key = (Long) arguments[1];
            int site = (Integer) arguments[2];
            int salt = (Integer) arguments[3];
            int flags = (Integer) arguments[4];
            return switch (variant) {
                case 1 -> adapted(target, AdapterLayout.LONG_PACK_TAIL,
                        encrypted, key, pack(site, salt), flags);
                case 2 -> adapted(target, AdapterLayout.LONG_SPLIT,
                        (int) (encrypted >>> 32), (int) encrypted,
                        (int) (key >>> 32), (int) key, site, salt, flags);
                case 3 -> adapted(target, AdapterLayout.LONG_CROSS_PACK,
                        encrypted, (int) (key >>> 32), pack((int) key, site), pack(salt, flags));
                default -> adapted(target, AdapterLayout.PASSTHROUGH, arguments);
            };
        }
        if (STRING_BOOTSTRAP_DESC.equals(target.desc()) && hasTypes(arguments,
                String.class, Integer.class, Integer.class, Integer.class, Integer.class)) {
            String value = (String) arguments[0];
            int key = (Integer) arguments[1];
            int site = (Integer) arguments[2];
            int salt = (Integer) arguments[3];
            int flags = (Integer) arguments[4];
            return switch (variant) {
                case 1 -> adapted(target, AdapterLayout.STRING_PACK_HEAD,
                        value, pack(key, site), salt, flags);
                case 2 -> adapted(target, AdapterLayout.STRING_PACK_MIDDLE,
                        value, key, pack(site, salt), flags);
                case 3 -> adapted(target, AdapterLayout.STRING_PACK_ALL,
                        value, pack(key, site), pack(salt, flags));
                default -> adapted(target, AdapterLayout.PASSTHROUGH, arguments);
            };
        }
        return new AdaptedBootstrap(new AdapterMethod(target, AdapterLayout.PASSTHROUGH),
                arguments == null ? new Object[0] : arguments.clone(), -1);
    }

    private static AdaptedBootstrap adapted(
            BootstrapMethod target,
            AdapterLayout layout,
            Object... arguments
    ) {
        return new AdaptedBootstrap(new AdapterMethod(target, layout), arguments, layout.routeBucket());
    }

    private static boolean hasTypes(Object[] arguments, Class<?>... types) {
        if (arguments == null || arguments.length != types.length) {
            return false;
        }
        for (int index = 0; index < types.length; index++) {
            if (!types[index].isInstance(arguments[index])) {
                return false;
            }
        }
        return true;
    }

    private static long pack(int high, int low) {
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    private static void emitDispatcherCall(
            MethodVisitor visitor,
            AdapterMethod method,
            String runtimeClassName,
            String shardName,
            String dispatchClassName,
            String dispatchMethodName,
            long seed
    ) {
        Type[] targetArguments = Type.getArgumentTypes(method.target().desc());
        pushInt(visitor, targetArguments.length);
        visitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        for (int index = 0; index < targetArguments.length; index++) {
            visitor.visitInsn(DUP);
            pushInt(visitor, index);
            emitAdapterArgument(visitor, method, index);
            box(visitor, targetArguments[index]);
            visitor.visitInsn(AASTORE);
        }
        int token = dispatchToken(method.target());
        int ownerMask = mix((int) seed
                ^ (int) (seed >>> 32)
                ^ shardName.hashCode()
                ^ Integer.rotateLeft(method.target().name().hashCode(), 7)
                ^ Integer.rotateLeft(method.target().desc().hashCode(), 13));
        visitor.visitLdcInsn(token);
        visitor.visitLdcInsn(ownerCipher(runtimeClassName.replace('/', '.'), ownerMask ^ token));
        visitor.visitLdcInsn(ownerMask);
        visitor.visitMethodInsn(INVOKESTATIC, dispatchClassName, dispatchMethodName, DISPATCH_DESC, false);
    }

    private static void emitAdapterArgument(MethodVisitor visitor, AdapterMethod method, int argumentIndex) {
        if (argumentIndex == 0) {
            visitor.visitVarInsn(ALOAD, 0);
            return;
        }
        if (argumentIndex == 1) {
            visitor.visitVarInsn(ALOAD, 1);
            return;
        }
        if (argumentIndex == 2) {
            visitor.visitVarInsn(ALOAD, 2);
            return;
        }
        switch (method.layout()) {
            case PASSTHROUGH -> emitPassthroughArgument(visitor, method.adapterDesc(), argumentIndex);
            case INT_PACK_HEAD -> emitIntPackHeadArgument(visitor, argumentIndex);
            case INT_PACK_TAIL -> emitIntPackTailArgument(visitor, argumentIndex);
            case INT_PACK_ALL -> emitIntPackAllArgument(visitor, argumentIndex);
            case LONG_PACK_TAIL -> emitLongPackTailArgument(visitor, argumentIndex);
            case LONG_SPLIT -> emitLongSplitArgument(visitor, argumentIndex);
            case LONG_CROSS_PACK -> emitLongCrossPackArgument(visitor, argumentIndex);
            case STRING_PACK_HEAD -> emitStringPackHeadArgument(visitor, argumentIndex);
            case STRING_PACK_MIDDLE -> emitStringPackMiddleArgument(visitor, argumentIndex);
            case STRING_PACK_ALL -> emitStringPackAllArgument(visitor, argumentIndex);
        }
    }

    private static void emitPassthroughArgument(MethodVisitor visitor, String descriptor, int argumentIndex) {
        Type[] arguments = Type.getArgumentTypes(descriptor);
        int local = 0;
        for (int index = 0; index < argumentIndex; index++) {
            local += arguments[index].getSize();
        }
        Type argument = arguments[argumentIndex];
        visitor.visitVarInsn(argument.getOpcode(ILOAD), local);
    }

    private static void emitIntPackHeadArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> emitPackedHighInt(visitor, 3);
            case 4 -> emitPackedLowInt(visitor, 3);
            case 5 -> visitor.visitVarInsn(ILOAD, 5);
            case 6 -> visitor.visitVarInsn(ILOAD, 6);
            case 7 -> visitor.visitVarInsn(ILOAD, 7);
            default -> throw new IllegalArgumentException("Unexpected int-head argument " + argumentIndex);
        }
    }

    private static void emitIntPackTailArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> visitor.visitVarInsn(ILOAD, 3);
            case 4 -> emitPackedHighInt(visitor, 4);
            case 5 -> emitPackedLowInt(visitor, 4);
            case 6 -> emitPackedHighInt(visitor, 6);
            case 7 -> emitPackedLowInt(visitor, 6);
            default -> throw new IllegalArgumentException("Unexpected int-tail argument " + argumentIndex);
        }
    }

    private static void emitIntPackAllArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> emitPackedHighInt(visitor, 3);
            case 4 -> emitPackedLowInt(visitor, 3);
            case 5 -> emitPackedHighInt(visitor, 5);
            case 6 -> emitPackedLowInt(visitor, 5);
            case 7 -> emitPackedHighInt(visitor, 7);
            default -> throw new IllegalArgumentException("Unexpected int-all argument " + argumentIndex);
        }
    }

    private static void emitLongPackTailArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> visitor.visitVarInsn(LLOAD, 3);
            case 4 -> visitor.visitVarInsn(LLOAD, 5);
            case 5 -> emitPackedHighInt(visitor, 7);
            case 6 -> emitPackedLowInt(visitor, 7);
            case 7 -> visitor.visitVarInsn(ILOAD, 9);
            default -> throw new IllegalArgumentException("Unexpected long-tail argument " + argumentIndex);
        }
    }

    private static void emitLongSplitArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> emitLongFromInts(visitor, 3, 4);
            case 4 -> emitLongFromInts(visitor, 5, 6);
            case 5 -> visitor.visitVarInsn(ILOAD, 7);
            case 6 -> visitor.visitVarInsn(ILOAD, 8);
            case 7 -> visitor.visitVarInsn(ILOAD, 9);
            default -> throw new IllegalArgumentException("Unexpected long-split argument " + argumentIndex);
        }
    }

    private static void emitLongCrossPackArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> visitor.visitVarInsn(LLOAD, 3);
            case 4 -> emitLongFromHighIntAndPackedHigh(visitor, 5, 6);
            case 5 -> emitPackedLowInt(visitor, 6);
            case 6 -> emitPackedHighInt(visitor, 8);
            case 7 -> emitPackedLowInt(visitor, 8);
            default -> throw new IllegalArgumentException("Unexpected long-cross argument " + argumentIndex);
        }
    }

    private static void emitStringPackHeadArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> visitor.visitVarInsn(ALOAD, 3);
            case 4 -> emitPackedHighInt(visitor, 4);
            case 5 -> emitPackedLowInt(visitor, 4);
            case 6 -> visitor.visitVarInsn(ILOAD, 6);
            case 7 -> visitor.visitVarInsn(ILOAD, 7);
            default -> throw new IllegalArgumentException("Unexpected string-head argument " + argumentIndex);
        }
    }

    private static void emitStringPackMiddleArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> visitor.visitVarInsn(ALOAD, 3);
            case 4 -> visitor.visitVarInsn(ILOAD, 4);
            case 5 -> emitPackedHighInt(visitor, 5);
            case 6 -> emitPackedLowInt(visitor, 5);
            case 7 -> visitor.visitVarInsn(ILOAD, 7);
            default -> throw new IllegalArgumentException("Unexpected string-middle argument " + argumentIndex);
        }
    }

    private static void emitStringPackAllArgument(MethodVisitor visitor, int argumentIndex) {
        switch (argumentIndex) {
            case 3 -> visitor.visitVarInsn(ALOAD, 3);
            case 4 -> emitPackedHighInt(visitor, 4);
            case 5 -> emitPackedLowInt(visitor, 4);
            case 6 -> emitPackedHighInt(visitor, 6);
            case 7 -> emitPackedLowInt(visitor, 6);
            default -> throw new IllegalArgumentException("Unexpected string-all argument " + argumentIndex);
        }
    }

    private static void pushInt(MethodVisitor visitor, int value) {
        if (value >= -1 && value <= 5) {
            visitor.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            visitor.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            visitor.visitIntInsn(SIPUSH, value);
        } else {
            visitor.visitLdcInsn(value);
        }
    }

    private static void box(MethodVisitor visitor, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf",
                    "(Z)Ljava/lang/Boolean;", false);
            case Type.BYTE -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf",
                    "(B)Ljava/lang/Byte;", false);
            case Type.CHAR -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf",
                    "(C)Ljava/lang/Character;", false);
            case Type.SHORT -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf",
                    "(S)Ljava/lang/Short;", false);
            case Type.INT -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf",
                    "(I)Ljava/lang/Integer;", false);
            case Type.FLOAT -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf",
                    "(F)Ljava/lang/Float;", false);
            case Type.LONG -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf",
                    "(J)Ljava/lang/Long;", false);
            case Type.DOUBLE -> visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf",
                    "(D)Ljava/lang/Double;", false);
            case Type.ARRAY, Type.OBJECT -> {
                // Reference arguments already match Object[].
            }
            default -> throw new IllegalArgumentException("Unsupported bootstrap argument type " + type);
        }
    }

    private static int dispatchToken(BootstrapMethod target) {
        return mix(target.name().hashCode() ^ Integer.rotateLeft(target.desc().hashCode(), 13));
    }

    private static String ownerCipher(String value, int key) {
        char[] encoded = new char[value.length() << 1];
        int state = key;
        for (int index = 0; index < value.length(); index++) {
            state = mix(state ^ index * 0x9E3779B9);
            int current = value.charAt(index) ^ (state & 0xFF);
            encoded[index << 1] = (char) ('a' + ((current >>> 4) & 0xF));
            encoded[(index << 1) + 1] = (char) ('a' + (current & 0xF));
        }
        return new String(encoded);
    }

    private static void emitPackedHighInt(MethodVisitor visitor, int local) {
        visitor.visitVarInsn(LLOAD, local);
        visitor.visitIntInsn(BIPUSH, 32);
        visitor.visitInsn(LUSHR);
        visitor.visitInsn(L2I);
    }

    private static void emitPackedLowInt(MethodVisitor visitor, int local) {
        visitor.visitVarInsn(LLOAD, local);
        visitor.visitInsn(L2I);
    }

    private static void emitLongFromInts(MethodVisitor visitor, int highLocal, int lowLocal) {
        visitor.visitVarInsn(ILOAD, highLocal);
        visitor.visitInsn(I2L);
        visitor.visitIntInsn(BIPUSH, 32);
        visitor.visitInsn(LSHL);
        visitor.visitVarInsn(ILOAD, lowLocal);
        visitor.visitInsn(I2L);
        visitor.visitLdcInsn(0xFFFFFFFFL);
        visitor.visitInsn(LAND);
        visitor.visitInsn(LOR);
    }

    private static void emitLongFromHighIntAndPackedHigh(
            MethodVisitor visitor,
            int highLocal,
            int packedLocal
    ) {
        visitor.visitVarInsn(ILOAD, highLocal);
        visitor.visitInsn(I2L);
        visitor.visitIntInsn(BIPUSH, 32);
        visitor.visitInsn(LSHL);
        visitor.visitVarInsn(LLOAD, packedLocal);
        visitor.visitIntInsn(BIPUSH, 32);
        visitor.visitInsn(LUSHR);
        visitor.visitLdcInsn(0xFFFFFFFFL);
        visitor.visitInsn(LAND);
        visitor.visitInsn(LOR);
    }

    private static List<String> shardNames(String runtimeClassName, long seed, Set<String> existingEntries) {
        int slash = runtimeClassName.lastIndexOf('/');
        String prefix = slash < 0 ? "" : runtimeClassName.substring(0, slash + 1);
        Set<String> used = new HashSet<>();
        for (String entry : existingEntries) {
            if (entry.endsWith(".class")) {
                used.add(entry.substring(0, entry.length() - ".class".length()));
            }
        }

        List<String> names = new ArrayList<>(SHARD_COUNT);
        for (int index = 0; index < SHARD_COUNT; index++) {
            int attempt = 0;
            while (true) {
                int a = mix((int) seed ^ runtimeClassName.hashCode()
                        ^ index * 0x45D9F3B ^ attempt * 0x27D4EB2D ^ 0x42534D31);
                int b = mix((int) (seed >>> 32) ^ Integer.rotateLeft(a, 9)
                        ^ runtimeClassName.length() * 0x9E3779B9 ^ 0x42534D32);
                String candidate = prefix + "C"
                        + Integer.toUnsignedString(a, 36)
                        + Integer.toUnsignedString(b, 36);
                if (used.add(candidate)) {
                    names.add(candidate);
                    break;
                }
                attempt++;
            }
        }
        return names;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private record BootstrapMethod(String name, String desc) {
    }

    private record AdapterMethod(BootstrapMethod target, AdapterLayout layout) {
        String adapterDesc() {
            return layout.descriptor(target.desc());
        }
    }

    private record AdaptedBootstrap(AdapterMethod method, Object[] arguments, int routeBucket) {
    }

    private enum AdapterLayout {
        PASSTHROUGH(null, 0),
        INT_PACK_HEAD(BOOTSTRAP_PREFIX + "JIII" + CALL_SITE_RETURN, 1),
        INT_PACK_TAIL(BOOTSTRAP_PREFIX + "IJJ" + CALL_SITE_RETURN, 2),
        INT_PACK_ALL(BOOTSTRAP_PREFIX + "JJJ" + CALL_SITE_RETURN, 3),
        LONG_PACK_TAIL(BOOTSTRAP_PREFIX + "JJJI" + CALL_SITE_RETURN, 1),
        LONG_SPLIT(BOOTSTRAP_PREFIX + "IIIIIII" + CALL_SITE_RETURN, 2),
        LONG_CROSS_PACK(BOOTSTRAP_PREFIX + "JIJJ" + CALL_SITE_RETURN, 3),
        STRING_PACK_HEAD(BOOTSTRAP_PREFIX + "Ljava/lang/String;JII" + CALL_SITE_RETURN, 1),
        STRING_PACK_MIDDLE(BOOTSTRAP_PREFIX + "Ljava/lang/String;IJI" + CALL_SITE_RETURN, 2),
        STRING_PACK_ALL(BOOTSTRAP_PREFIX + "Ljava/lang/String;JJ" + CALL_SITE_RETURN, 3);

        private final String descriptor;
        private final int routeBucket;

        AdapterLayout(String descriptor, int routeBucket) {
            this.descriptor = descriptor;
            this.routeBucket = routeBucket;
        }

        String descriptor(String fallback) {
            return descriptor == null ? fallback : descriptor;
        }

        int routeBucket() {
            return routeBucket;
        }
    }
}
