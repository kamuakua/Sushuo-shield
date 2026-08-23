package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** JVM-only Phantom-style preprocessing: field lowering, member shuffling and call wrappers. */
final class JvmPhantomObfuscator implements Opcodes {
    private static final int WRAPPER_SALT = 0x50484E54;

    private JvmPhantomObfuscator() {
    }

    static int apply(Map<String, ClassNode> classes, ObfuscationOptions options) {
        return apply(classes, options, Set.of(), true);
    }

    static int apply(Map<String, ClassNode> classes, ObfuscationOptions options,
                     Set<String> oversizedClasses, boolean wrapCalls) {
        if (!options.jvmPhantom() || options.minecraftMode()) {
            return 0;
        }
        List<ClassNode> project = classes.values().stream()
                .filter(node -> !options.isExcluded(node.name))
                .filter(node -> !oversizedClasses.contains(node.name))
                .filter(node -> (node.access & (ACC_MODULE | ACC_ANNOTATION)) == 0)
                .sorted(Comparator.comparing(node -> node.name))
                .toList();
        if (project.isEmpty()) {
            return 0;
        }

        Map<String, ClassNode> projectByName = new HashMap<>();
        for (ClassNode classNode : project) {
            projectByName.put(classNode.name, classNode);
            StaticFieldInitializer.move(classNode);
        }

        int wrappers = wrapCalls ? wrapProjectAccesses(project, projectByName, options.seed()) : 0;
        shuffleMembers(project, options.seed());
        return wrappers;
    }

    private static void shuffleMembers(List<ClassNode> classes, long seed) {
        for (ClassNode classNode : classes) {
            Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x4D53485546464C45L);
            Collections.shuffle(classNode.fields, random);
            Collections.shuffle(classNode.methods, random);
        }
    }

    private static int wrapProjectAccesses(List<ClassNode> classes, Map<String, ClassNode> projectByName, long seed) {
        int count = 0;
        Map<WrapperKey, String> wrappers = new HashMap<>();
        Map<String, Set<String>> usedNames = new HashMap<>();
        for (ClassNode classNode : classes) {
            Set<String> names = new HashSet<>();
            for (MethodNode method : classNode.methods) {
                names.add(method.name + method.desc);
            }
            usedNames.put(classNode.name, names);
        }

        for (ClassNode caller : classes) {
            for (MethodNode method : new ArrayList<>(caller.methods)) {
                if (method.instructions == null || method.name.equals("<clinit>")
                        || method.name.startsWith("_ps$") && (method.access & ACC_SYNTHETIC) != 0) {
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                    AbstractInsnNode next = instruction.getNext();
                    if (instruction instanceof MethodInsnNode call) {
                        WrapperSpec spec = methodWrapper(call, projectByName, usedNames, seed);
                        if (spec != null) {
                            WrapperKey key = new WrapperKey(call.getOpcode(), call.owner, call.name, call.desc);
                            String wrapperName = wrappers.get(key);
                            if (wrapperName == null) {
                                wrapperName = addMethodWrapper(spec, projectByName.get(call.owner), usedNames, seed,
                                        wrappers.size());
                                wrappers.put(key, wrapperName);
                            }
                            call.owner = spec.owner;
                            call.name = wrapperName;
                            call.desc = spec.wrapperDescriptor;
                            call.setOpcode(INVOKESTATIC);
                            call.itf = false;
                            count++;
                        }
                    } else if (instruction instanceof FieldInsnNode field) {
                        WrapperSpec spec = fieldWrapper(field, projectByName, usedNames, seed);
                        if (spec != null) {
                            WrapperKey key = new WrapperKey(field.getOpcode(), field.owner, field.name, field.desc);
                            String wrapperName = wrappers.get(key);
                            if (wrapperName == null) {
                                wrapperName = addFieldWrapper(spec, projectByName.get(field.owner), usedNames, seed,
                                        wrappers.size());
                                wrappers.put(key, wrapperName);
                            }
                            method.instructions.set(field, new MethodInsnNode(INVOKESTATIC,
                                    spec.owner, wrapperName, spec.wrapperDescriptor, false));
                            count++;
                        }
                    }
                    instruction = next;
                }
            }
        }
        return count;
    }

    private static WrapperSpec methodWrapper(MethodInsnNode call, Map<String, ClassNode> projectByName,
                                              Map<String, Set<String>> usedNames, long seed) {
        ClassNode owner = projectByName.get(call.owner);
        if (owner == null || (owner.access & ACC_INTERFACE) != 0
                || call.getOpcode() == INVOKESPECIAL || call.getOpcode() == INVOKEINTERFACE
                || call.name.equals("<init>") || call.name.equals("<clinit>")) {
            return null;
        }
        MethodNode target = findMethod(owner, call.name, call.desc);
        if (target == null || (target.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            return null;
        }
        Type returnType = Type.getReturnType(call.desc);
        Type[] arguments = Type.getArgumentTypes(call.desc);
        Type[] wrapperArguments;
        if (call.getOpcode() == INVOKEVIRTUAL) {
            wrapperArguments = new Type[arguments.length + 1];
            wrapperArguments[0] = Type.getObjectType(call.owner);
            System.arraycopy(arguments, 0, wrapperArguments, 1, arguments.length);
        } else {
            wrapperArguments = arguments;
        }
        return new WrapperSpec(call.owner, Type.getMethodDescriptor(returnType, wrapperArguments),
                call.desc, call.name, call.getOpcode(), false);
    }

    private static WrapperSpec fieldWrapper(FieldInsnNode field, Map<String, ClassNode> projectByName,
                                            Map<String, Set<String>> usedNames, long seed) {
        ClassNode owner = projectByName.get(field.owner);
        if (owner == null || (owner.access & ACC_INTERFACE) != 0) {
            return null;
        }
        FieldNode target = findField(owner, field.name, field.desc);
        if (target == null || (target.access & ACC_FINAL) != 0
                && (field.getOpcode() == PUTSTATIC || field.getOpcode() == PUTFIELD)) {
            return null;
        }
        Type fieldType = Type.getType(field.desc);
        Type[] arguments = switch (field.getOpcode()) {
            case GETSTATIC -> new Type[0];
            case PUTSTATIC -> new Type[]{fieldType};
            case GETFIELD -> new Type[]{Type.getObjectType(field.owner)};
            case PUTFIELD -> new Type[]{Type.getObjectType(field.owner), fieldType};
            default -> null;
        };
        if (arguments == null) {
            return null;
        }
        Type returnType = switch (field.getOpcode()) {
            case GETSTATIC, GETFIELD -> fieldType;
            default -> Type.VOID_TYPE;
        };
        return new WrapperSpec(field.owner, Type.getMethodDescriptor(returnType, arguments),
                field.desc, field.name, field.getOpcode(), true);
    }

    private static String addMethodWrapper(WrapperSpec spec, ClassNode owner, Map<String, Set<String>> usedNames,
                                           long seed, int index) {
        String name = nextWrapperName(owner.name, spec.memberName, spec.originalDescriptor, usedNames.get(owner.name),
                seed, index);
        MethodNode wrapper = new MethodNode(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, name,
                spec.wrapperDescriptor, null, null);
        Type[] args = Type.getArgumentTypes(spec.wrapperDescriptor);
        int local = 0;
        for (Type argument : args) {
            wrapper.instructions.add(new VarInsnNode(argument.getOpcode(ILOAD), local));
            local += argument.getSize();
        }
        wrapper.instructions.add(new MethodInsnNode(spec.originalOpcode == INVOKEVIRTUAL ? INVOKEVIRTUAL : INVOKESTATIC,
                spec.owner, spec.memberName, spec.originalDescriptor, false));
        wrapper.instructions.add(new InsnNode(Type.getReturnType(spec.originalDescriptor).getOpcode(IRETURN)));
        owner.methods.add(wrapper);
        return name;
    }

    private static String addFieldWrapper(WrapperSpec spec, ClassNode owner, Map<String, Set<String>> usedNames,
                                          long seed, int index) {
        String name = nextWrapperName(owner.name, spec.memberName, spec.originalDescriptor, usedNames.get(owner.name),
                seed, index);
        MethodNode wrapper = new MethodNode(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, name,
                spec.wrapperDescriptor, null, null);
        switch (spec.originalOpcode) {
            case GETSTATIC -> wrapper.instructions.add(new FieldInsnNode(GETSTATIC, spec.owner,
                    spec.memberName, spec.originalDescriptor));
            case PUTSTATIC -> wrapper.instructions.add(new VarInsnNode(Type.getType(spec.originalDescriptor).getOpcode(ILOAD), 0));
            case GETFIELD -> wrapper.instructions.add(new VarInsnNode(ALOAD, 0));
            case PUTFIELD -> {
                wrapper.instructions.add(new VarInsnNode(ALOAD, 0));
                wrapper.instructions.add(new VarInsnNode(Type.getType(spec.originalDescriptor).getOpcode(ILOAD), 1));
            }
            default -> throw new IllegalArgumentException("Unsupported field opcode");
        }
        if (spec.originalOpcode == PUTSTATIC || spec.originalOpcode == PUTFIELD) {
            if (spec.originalOpcode == PUTSTATIC) {
                wrapper.instructions.add(new FieldInsnNode(PUTSTATIC, spec.owner, spec.memberName,
                        spec.originalDescriptor));
            } else {
                wrapper.instructions.add(new FieldInsnNode(PUTFIELD, spec.owner, spec.memberName,
                        spec.originalDescriptor));
            }
        } else if (spec.originalOpcode == GETFIELD) {
            wrapper.instructions.add(new FieldInsnNode(GETFIELD, spec.owner, spec.memberName,
                    spec.originalDescriptor));
        }
        wrapper.instructions.add(new InsnNode(spec.originalOpcode == GETSTATIC || spec.originalOpcode == GETFIELD
                ? Type.getType(spec.originalDescriptor).getOpcode(IRETURN) : RETURN));
        owner.methods.add(wrapper);
        return name;
    }

    private static String nextWrapperName(String owner, String member, String descriptor, Set<String> used,
                                          long seed, int index) {
        Random random = new Random(seed ^ owner.hashCode() ^ member.hashCode()
                ^ descriptor.hashCode() ^ index * WRAPPER_SALT);
        while (true) {
            String candidate = "_ps$" + Integer.toUnsignedString(random.nextInt(), 36)
                    + Integer.toUnsignedString(random.nextInt(), 36);
            if (used.add(candidate + descriptor)) {
                return candidate;
            }
        }
    }

    private static MethodNode findMethod(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    private static FieldNode findField(ClassNode owner, String name, String descriptor) {
        for (FieldNode field : owner.fields) {
            if (field.name.equals(name) && field.desc.equals(descriptor)) {
                return field;
            }
        }
        return null;
    }

    private record WrapperKey(int opcode, String owner, String name, String descriptor) {
    }

    private record WrapperSpec(String owner, String wrapperDescriptor, String originalDescriptor,
                               String memberName, int originalOpcode, boolean field) {
    }
}
