package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
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

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

final class AntiDeobfuscationNoise implements Opcodes {
    private static final String[] DECOY_WORDS = {
            "license", "bootstrap", "decrypt", "native", "virtual", "resource",
            "session", "loader", "mapping", "constant", "dispatcher", "bridge"
    };

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
                    ? intDecoy(nextMethodName(random, usedNames), runtimeClassName, random)
                    : stringDecoy(nextMethodName(random, usedNames), runtimeClassName, random);
            classNode.methods.add(method);
            artifacts++;
        }
        int vmDecoys = 1 + random.nextInt(2);
        for (int i = 0; i < vmDecoys; i++) {
            classNode.methods.add(vmProgramDecoy(nextProgramName(random, usedNames), random));
            artifacts++;
        }
        return artifacts;
    }

    private static MethodNode intDecoy(String name, String runtimeClassName, Random random) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, "(I)I", null, null);
        InsnList code = method.instructions;
        LabelNode real = new LabelNode();
        code.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
        code.add(new JumpInsnNode(IFNE, real));
        code.add(new LdcInsnNode(random.nextInt()));
        code.add(new InsnNode(IRETURN));
        code.add(real);
        code.add(new LdcInsnNode(decoyString(random)));
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

    private static MethodNode stringDecoy(String name, String runtimeClassName, Random random) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        InsnList code = method.instructions;
        LabelNode passthrough = new LabelNode();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new JumpInsnNode(IFNONNULL, passthrough));
        code.add(new LdcInsnNode(decoyString(random)));
        code.add(new InsnNode(ARETURN));
        code.add(passthrough);
        code.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_o", "()Z", false));
        LabelNode input = new LabelNode();
        code.add(new JumpInsnNode(IFNE, input));
        code.add(new LdcInsnNode(decoyString(random)));
        code.add(new InsnNode(ARETURN));
        code.add(input);
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(ARETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private static MethodNode vmProgramDecoy(String name, Random random) {
        MethodNode method = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                name, "()[Ljava/lang/Object;", null, null);
        InsnList code = method.instructions;
        int key = random.nextInt() | 1;
        int[] fakeCode = fakeEncodedInts(random, 12 + random.nextInt(18), key ^ 0x41C64E6D);
        int[] fakeMap = fakeEncodedInts(random, 16, key ^ 0x27D4EB2D);
        Object[] fakePool = {
                decoyString(random),
                "java/lang/System",
                "out",
                "Ljava/io/PrintStream;",
                "println",
                "(Ljava/lang/String;)V",
                random.nextInt(),
                random.nextLong()
        };

        Virtualizer.pushInt(code, 9);
        code.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        putInt(code, 0, 0x53535632);
        putInt(code, 1, 1 + random.nextInt(4));
        putInt(code, 2, random.nextInt(3));
        putInt(code, 3, random.nextInt(10));
        putIntArray(code, 4, fakeCode);
        putObjectArray(code, 5, fakePool);
        putInt(code, 6, Math.abs(random.nextInt()));
        putInt(code, 7, key);
        putIntArray(code, 8, fakeMap);
        code.add(new InsnNode(ARETURN));
        method.maxLocals = 0;
        method.maxStack = 8;
        return method;
    }

    private static int[] fakeEncodedInts(Random random, int length, int key) {
        int[] values = new int[length];
        int state = key ^ length;
        for (int i = 0; i < values.length; i++) {
            state ^= i * 0x45D9F3B;
            state = Integer.rotateLeft(state + 0x7F4A7C15, 9);
            state ^= state >>> 13;
            state *= 0x5BD1E995;
            state ^= state >>> 15;
            values[i] = Integer.rotateLeft(random.nextInt() ^ state ^ i, (state >>> 27) & 15);
        }
        return values;
    }

    private static void putInt(InsnList body, int index, int value) {
        body.add(new InsnNode(DUP));
        Virtualizer.pushInt(body, index);
        Virtualizer.pushInt(body, value);
        body.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false));
        body.add(new InsnNode(AASTORE));
    }

    private static void putIntArray(InsnList body, int index, int[] values) {
        body.add(new InsnNode(DUP));
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

    private static void putObjectArray(InsnList body, int index, Object[] values) {
        body.add(new InsnNode(DUP));
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

    private static String nextProgramName(Random random, Set<String> usedNames) {
        while (true) {
            String name = randomMemberName(random);
            if (usedNames.add(name + "()[Ljava/lang/Object;")) {
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

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value == 0 ? 0x13579BDF : value;
    }

    private static String decoyString(Random random) {
        String left = DECOY_WORDS[random.nextInt(DECOY_WORDS.length)];
        String right = DECOY_WORDS[random.nextInt(DECOY_WORDS.length)];
        return "ss.ad." + left + "." + right + "#" + Long.toUnsignedString(random.nextLong(), 36);
    }
}
