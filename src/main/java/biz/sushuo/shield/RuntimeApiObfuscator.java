package biz.sushuo.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

final class RuntimeApiObfuscator {
    private static final String[][] API = {
            {"_v", "([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"},
            {"_d", "(Ljava/lang/String;I)Ljava/lang/String;"},
            {"_d", "(Ljava/lang/String;III)Ljava/lang/String;"},
            {"_q", "(III)I"},
            {"_i", "(IIII)I"},
            {"_l", "(JJII)J"},
            {"_m", "(Ljava/lang/String;)Ljava/lang/String;"},
            {"_ma", "([Ljava/lang/String;)[Ljava/lang/String;"},
            {"_sc", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"},
            {"_rs", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"},
            {"_ri", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;"},
            {"_o", "()Z"},
            {"_g", "(Ljava/lang/String;Ljava/lang/String;)V"},
            {"_rl", "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/io/InputStream;"},
            {"_rc", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;"},
            {"_rg", "(Ljava/lang/String;)Ljava/io/InputStream;"}
    };

    private RuntimeApiObfuscator() {
    }

    static Map<MemberSig, String> plan(String runtimeClassName, long seed, boolean enabled) {
        if (!enabled) {
            return Map.of();
        }
        Map<MemberSig, String> names = new HashMap<>();
        int ordinal = 0;
        for (String[] api : API) {
            MemberSig sig = new MemberSig(api[0], api[1]);
            names.put(sig, name(runtimeClassName, seed, sig, ordinal++));
        }
        return Map.copyOf(names);
    }

    static Map<String, byte[]> rewriteClasses(Map<String, byte[]> classes,
                                              String runtimeClassName,
                                              Map<MemberSig, String> apiNames) {
        if (apiNames.isEmpty()) {
            return classes;
        }
        Map<String, byte[]> rewritten = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            rewritten.put(entry.getKey(), rewriteClass(entry.getValue(), runtimeClassName, apiNames));
        }
        return rewritten;
    }

    private static byte[] rewriteClass(byte[] bytes, String runtimeClassName, Map<MemberSig, String> apiNames) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        boolean changed = rewriteClassNode(classNode, runtimeClassName, apiNames, false);
        if (!changed) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    static boolean rewriteClassNode(ClassNode classNode,
                                    String runtimeClassName,
                                    Map<MemberSig, String> apiNames,
                                    boolean renameDefinitions) {
        if (apiNames.isEmpty()) {
            return false;
        }
        boolean changed = false;
        if (renameDefinitions && classNode.name.equals(runtimeClassName)) {
            for (org.objectweb.asm.tree.MethodNode method : classNode.methods) {
                String mapped = apiNames.get(new MemberSig(method.name, method.desc));
                if (mapped != null) {
                    method.name = mapped;
                    changed = true;
                }
            }
        }
        for (org.objectweb.asm.tree.MethodNode method : classNode.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call
                        && runtimeClassName.equals(call.owner)) {
                    String mapped = apiNames.get(new MemberSig(call.name, call.desc));
                    if (mapped != null) {
                        call.name = mapped;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static String name(String runtimeClassName, long seed, MemberSig sig, int ordinal) {
        int a = mix((int) seed
                ^ runtimeClassName.hashCode()
                ^ Integer.rotateLeft(sig.name().hashCode(), 7)
                ^ Integer.rotateLeft(sig.desc().hashCode(), 13)
                ^ ordinal * 0x45D9F3B);
        int b = mix((int) (seed >>> 32)
                ^ Integer.rotateLeft(a, 11)
                ^ runtimeClassName.length() * 0x9E3779B9
                ^ ordinal * 0x27D4EB2D);
        return "_"
                + Integer.toUnsignedString(a, 36)
                + Integer.toUnsignedString(b, 36);
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    record MemberSig(String name, String desc) {
    }
}
