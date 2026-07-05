package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Random;

final class MetadataEncryptor implements Opcodes {
    private static final String MODULE_INFO = "com/heypixel/heypixelmod/obsoverlay/modules/ModuleInfo";
    private static final String COMMAND_INFO = "com/heypixel/heypixelmod/obsoverlay/commands/CommandInfo";

    private MetadataEncryptor() {
    }

    static int encrypt(ClassNode classNode, String runtimeClassName, long seed) {
        int count = 0;
        Random random = new Random(seed ^ classNode.name.hashCode() ^ 0x4D45544144415441L);
        count += encryptAnnotations(classNode.visibleAnnotations, random);
        count += encryptAnnotations(classNode.invisibleAnnotations, random);
        if (count > 0 || isMetadataConsumer(classNode.name)) {
            patchMetadataReads(classNode, runtimeClassName);
        }
        return count;
    }

    private static int encryptAnnotations(List<AnnotationNode> annotations, Random random) {
        if (annotations == null) {
            return 0;
        }
        int count = 0;
        for (AnnotationNode annotation : annotations) {
            if (!isProtectedAnnotation(annotation.desc) || annotation.values == null) {
                continue;
            }
            for (int i = 0; i < annotation.values.size() - 1; i += 2) {
                Object key = annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if (shouldEncrypt(key) && value instanceof String string && !string.isEmpty()) {
                    annotation.values.set(i + 1, StringCipher.encodeMetadata(string, random.nextInt()));
                    count++;
                } else if ("aliases".equals(key) && value instanceof List<?> aliases) {
                    @SuppressWarnings("unchecked")
                    List<Object> mutable = (List<Object>) aliases;
                    for (int j = 0; j < mutable.size(); j++) {
                        Object alias = mutable.get(j);
                        if (alias instanceof String string && !string.isEmpty()) {
                            mutable.set(j, StringCipher.encodeMetadata(string, random.nextInt()));
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static boolean shouldEncrypt(Object key) {
        return "name".equals(key) || "cnName".equals(key) || "description".equals(key);
    }

    private static boolean isProtectedAnnotation(String descriptor) {
        return descriptor.equals('L' + MODULE_INFO + ';')
                || descriptor.equals('L' + COMMAND_INFO + ';');
    }

    private static boolean isMetadataConsumer(String className) {
        return className.equals("com/heypixel/heypixelmod/obsoverlay/modules/Module")
                || className.equals("com/heypixel/heypixelmod/obsoverlay/commands/Command");
    }

    private static void patchMetadataReads(ClassNode classNode, String runtimeClassName) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != INVOKEINTERFACE) {
                    continue;
                }
                if (call.owner.equals(MODULE_INFO)
                        && (call.name.equals("name") || call.name.equals("cnName") || call.name.equals("description"))
                        && call.desc.equals("()Ljava/lang/String;")) {
                    InsnList decode = new InsnList();
                    decode.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_m",
                            "(Ljava/lang/String;)Ljava/lang/String;", false));
                    method.instructions.insert(call, decode);
                } else if (call.owner.equals(COMMAND_INFO)
                        && (call.name.equals("name") || call.name.equals("description"))
                        && call.desc.equals("()Ljava/lang/String;")) {
                    InsnList decode = new InsnList();
                    decode.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_m",
                            "(Ljava/lang/String;)Ljava/lang/String;", false));
                    method.instructions.insert(call, decode);
                } else if (call.owner.equals(COMMAND_INFO)
                        && call.name.equals("aliases")
                        && call.desc.equals("()[Ljava/lang/String;")) {
                    InsnList decode = new InsnList();
                    decode.add(new MethodInsnNode(INVOKESTATIC, runtimeClassName, "_ma",
                            "([Ljava/lang/String;)[Ljava/lang/String;", false));
                    method.instructions.insert(call, decode);
                }
            }
        }
    }
}
