package biz.sushuo.shield;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class MethodParameterObfuscator implements Opcodes {
    private MethodParameterObfuscator() {
    }

    static int apply(ClassNode classNode, long seed) {
        return applyAll(Map.of(classNode.name, classNode), seed, Set.of());
    }

    static int applyAll(Map<String, ClassNode> classes, long seed, Set<String> excludedClasses) {
        Set<String> methodHandleTargets = methodHandleTargets(classes.values().stream().toList());
        Map<String, Candidate> candidates = new HashMap<>();
        for (ClassNode classNode : classes.values()) {
            if ((classNode.access & (ACC_INTERFACE | ACC_ANNOTATION)) != 0) {
                continue;
            }
            if (excludedClasses.contains(classNode.name)) {
                continue;
            }
            if (hasReflectionHotspots(classNode)) {
                continue;
            }
            Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x6A09E667F3BCC909L);
            for (MethodNode method : classNode.methods) {
                Candidate candidate = candidate(classNode, method, random, methodHandleTargets);
                if (candidate != null) {
                    candidates.put(key(classNode.name, method.name, method.desc), candidate);
                }
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }

        Set<Candidate> called = new HashSet<>();
        for (ClassNode classNode : classes.values()) {
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode instruction = method.instructions == null ? null
                        : method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                    if (instruction instanceof MethodInsnNode call) {
                        Candidate candidate = candidates.get(key(call.owner, call.name, call.desc));
                        if (candidate != null) {
                            called.add(candidate);
                        }
                    }
                }
            }
        }
        candidates.values().removeIf(candidate -> !called.contains(candidate));
        if (candidates.isEmpty()) {
            return 0;
        }

        int rewrittenCalls = 0;
        for (ClassNode classNode : classes.values()) {
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode instruction = method.instructions == null ? null
                        : method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                    if (instruction instanceof MethodInsnNode call) {
                        Candidate candidate = candidates.get(key(call.owner, call.name, call.desc));
                        if (candidate != null) {
                            method.instructions.insertBefore(call, dummyValue(candidate.dummyDescriptor(), seed, rewrittenCalls));
                            call.desc = candidate.newDescriptor();
                            rewrittenCalls++;
                        }
                    }
                }
            }
        }

        int changed = 0;
        for (Candidate candidate : candidates.values()) {
            candidate.method().desc = candidate.newDescriptor();
            candidate.method().signature = null;
            candidate.method().parameters = null;
            candidate.method().visibleParameterAnnotations = null;
            candidate.method().invisibleParameterAnnotations = null;
            candidate.method().visibleAnnotableParameterCount = 0;
            candidate.method().invisibleAnnotableParameterCount = 0;
            candidate.method().localVariables = null;
            candidate.method().visibleLocalVariableAnnotations = null;
            candidate.method().invisibleLocalVariableAnnotations = null;
            changed++;
        }
        return changed;
    }

    private static Candidate candidate(ClassNode owner, MethodNode method, Random random, Set<String> methodHandleTargets) {
        if ((method.access & (ACC_PUBLIC | ACC_PROTECTED)) != 0) {
            return null;
        }
        if ((method.access & (ACC_ABSTRACT | ACC_NATIVE | ACC_VARARGS | ACC_BRIDGE)) != 0) {
            return null;
        }
        if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
            return null;
        }
        if (method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V")) {
            return null;
        }
        if (SDKMarkerSupport.noProtect(method)) {
            return null;
        }
        if (method.instructions == null || method.instructions.size() == 0) {
            return null;
        }
        if (methodHandleTargets.contains(key(owner.name, method.name, method.desc))) {
            return null;
        }
        boolean privateMethod = (method.access & ACC_PRIVATE) != 0;
        boolean staticMethod = (method.access & ACC_STATIC) != 0;
        boolean finalMethod = (method.access & ACC_FINAL) != 0 || (owner.access & ACC_FINAL) != 0;
        if (!privateMethod && !staticMethod && !finalMethod) {
            return null;
        }

        String dummyDescriptor = switch (Math.floorMod(random.nextInt(), 3)) {
            case 0 -> "I";
            case 1 -> "J";
            default -> "Ljava/lang/Object;";
        };
        Type methodType = Type.getMethodType(method.desc);
        int slots = (method.access & ACC_STATIC) == 0 ? 1 : 0;
        for (Type argument : methodType.getArgumentTypes()) {
            slots += argument.getSize();
        }
        slots += Type.getType(dummyDescriptor).getSize();
        if (slots > 250) {
            return null;
        }

        return new Candidate(method, method.desc, appendParameter(method.desc, dummyDescriptor), dummyDescriptor);
    }

    private static String appendParameter(String descriptor, String parameterDescriptor) {
        int end = descriptor.indexOf(')');
        if (end < 0) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }
        return descriptor.substring(0, end) + parameterDescriptor + descriptor.substring(end);
    }

    private static InsnList dummyValue(String descriptor, long seed, int index) {
        InsnList instructions = new InsnList();
        int mixed = mix((int) seed ^ index * 0x45D9F3B);
        if (descriptor.equals("I")) {
            Virtualizer.pushInt(instructions, mixed);
        } else if (descriptor.equals("J")) {
            instructions.add(new LdcInsnNode((((long) mixed) << 32) ^ mix(mixed + 0x7F4A7C15)));
        } else {
            instructions.add(new InsnNode(ACONST_NULL));
        }
        return instructions;
    }

    private static boolean hasReflectionHotspots(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions == null ? null
                    : method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call && isReflectionCall(call)) {
                    return true;
                }
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String value
                        && isReflectionString(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isReflectionCall(MethodInsnNode call) {
        if (call.owner.equals("java/lang/Class")) {
            return switch (call.name) {
                case "forName", "getMethod", "getDeclaredMethod", "getMethods", "getDeclaredMethods",
                        "getField", "getDeclaredField", "getFields", "getDeclaredFields" -> true;
                default -> false;
            };
        }
        if (call.owner.equals("java/lang/invoke/MethodHandles$Lookup")) {
            return switch (call.name) {
                case "findVirtual", "findStatic", "findSpecial", "findGetter", "findSetter",
                        "unreflect", "unreflectSpecial", "unreflectGetter", "unreflectSetter" -> true;
                default -> false;
            };
        }
        return call.owner.equals("java/lang/reflect/Method")
                || call.owner.equals("java/lang/reflect/Field")
                || call.owner.equals("java/lang/reflect/Constructor");
    }

    private static boolean isReflectionString(String value) {
        return value.equals("getDeclaredMethod")
                || value.equals("getMethod")
                || value.equals("findVirtual")
                || value.equals("findStatic")
                || value.equals("invoke")
                || value.startsWith("java.lang.reflect.")
                || value.startsWith("java/lang/reflect/");
    }

    private static Set<String> methodHandleTargets(List<ClassNode> classes) {
        Set<String> targets = new HashSet<>();
        for (ClassNode classNode : classes) {
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode instruction = method.instructions == null ? null
                        : method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                    if (instruction instanceof InvokeDynamicInsnNode indy) {
                        collectHandle(targets, indy.bsm);
                        if (indy.bsmArgs != null) {
                            for (Object argument : indy.bsmArgs) {
                                collectBootstrapValue(targets, argument);
                            }
                        }
                    } else if (instruction instanceof LdcInsnNode ldc) {
                        collectBootstrapValue(targets, ldc.cst);
                    }
                }
            }
        }
        return targets;
    }

    private static void collectBootstrapValue(Set<String> targets, Object value) {
        if (value instanceof Handle handle) {
            collectHandle(targets, handle);
        } else if (value instanceof ConstantDynamic constantDynamic) {
            collectHandle(targets, constantDynamic.getBootstrapMethod());
            for (int i = 0; i < constantDynamic.getBootstrapMethodArgumentCount(); i++) {
                collectBootstrapValue(targets, constantDynamic.getBootstrapMethodArgument(i));
            }
        }
    }

    private static void collectHandle(Set<String> targets, Handle handle) {
        if (handle != null) {
            targets.add(key(handle.getOwner(), handle.getName(), handle.getDesc()));
        }
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        value *= 0xC2B2AE35;
        value ^= value >>> 16;
        return value;
    }

    private static String key(String name, String descriptor) {
        return name + '\u0000' + descriptor;
    }

    private static String key(String owner, String name, String descriptor) {
        return owner + '\u0000' + name + '\u0000' + descriptor;
    }

    private record Candidate(MethodNode method, String oldDescriptor, String newDescriptor, String dummyDescriptor) {
    }
}
