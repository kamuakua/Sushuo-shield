package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

final class SDKMarkerSupport implements Opcodes {
    static final String OWNER = "biz/sushuo/shield/sdk/VMProtectSDK";
    static final String VIRTUALIZE = "Lbiz/sushuo/shield/sdk/Virtualize;";
    static final String MUTATE = "Lbiz/sushuo/shield/sdk/Mutate;";
    static final String ULTRA = "Lbiz/sushuo/shield/sdk/Ultra;";
    static final String NO_PROTECT = "Lbiz/sushuo/shield/sdk/NoProtect;";

    private SDKMarkerSupport() {
    }

    static int promoteMarkerBlocks(ClassNode classNode) {
        int count = 0;
        for (MethodNode method : classNode.methods) {
            boolean virtualize = false;
            boolean mutate = false;
            boolean ultra = false;
            for (AbstractInsnNode instruction = method.instructions == null ? null
                    : method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == INVOKESTATIC
                        && OWNER.equals(call.owner)
                        && call.desc.equals("()V")) {
                    switch (call.name) {
                        case "beginVirtualization", "endVirtualization" -> virtualize = true;
                        case "beginMutation", "endMutation" -> mutate = true;
                        case "beginUltra", "endUltra" -> ultra = true;
                        default -> {
                        }
                    }
                }
            }
            if (ultra) {
                if (addInvisibleAnnotation(method, ULTRA)) {
                    count++;
                }
            } else {
                if (virtualize && addInvisibleAnnotation(method, VIRTUALIZE)) {
                    count++;
                }
                if (mutate && addInvisibleAnnotation(method, MUTATE)) {
                    count++;
                }
            }
        }
        return count;
    }

    static int stripMarkerCalls(ClassNode classNode) {
        int count = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions == null ? null
                    : method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == INVOKESTATIC
                        && OWNER.equals(call.owner)
                        && call.desc.equals("()V")
                        && isMarkerMethod(call.name)) {
                    method.instructions.remove(instruction);
                    count++;
                }
                instruction = next;
            }
        }
        return count;
    }

    static boolean noProtect(ClassNode classNode) {
        return hasAnnotation(classNode.visibleAnnotations, NO_PROTECT)
                || hasAnnotation(classNode.invisibleAnnotations, NO_PROTECT);
    }

    static boolean noProtect(MethodNode method) {
        return hasAnnotation(method.visibleAnnotations, NO_PROTECT)
                || hasAnnotation(method.invisibleAnnotations, NO_PROTECT);
    }

    static boolean forceVirtualize(MethodNode method) {
        return hasAnnotation(method.visibleAnnotations, VIRTUALIZE)
                || hasAnnotation(method.invisibleAnnotations, VIRTUALIZE)
                || hasAnnotation(method.visibleAnnotations, ULTRA)
                || hasAnnotation(method.invisibleAnnotations, ULTRA);
    }

    static boolean forceVirtualize(ClassNode classNode, MethodNode method) {
        return forceVirtualize(method)
                || hasAnnotation(classNode.visibleAnnotations, VIRTUALIZE)
                || hasAnnotation(classNode.invisibleAnnotations, VIRTUALIZE)
                || hasAnnotation(classNode.visibleAnnotations, ULTRA)
                || hasAnnotation(classNode.invisibleAnnotations, ULTRA);
    }

    static boolean forceMutate(MethodNode method) {
        return hasAnnotation(method.visibleAnnotations, MUTATE)
                || hasAnnotation(method.invisibleAnnotations, MUTATE)
                || hasAnnotation(method.visibleAnnotations, ULTRA)
                || hasAnnotation(method.invisibleAnnotations, ULTRA);
    }

    static boolean forceMutate(ClassNode classNode, MethodNode method) {
        return forceMutate(method)
                || hasAnnotation(classNode.visibleAnnotations, MUTATE)
                || hasAnnotation(classNode.invisibleAnnotations, MUTATE)
                || hasAnnotation(classNode.visibleAnnotations, ULTRA)
                || hasAnnotation(classNode.invisibleAnnotations, ULTRA);
    }

    private static boolean isMarkerMethod(String name) {
        return switch (name) {
            case "beginVirtualization", "endVirtualization",
                    "beginMutation", "endMutation",
                    "beginUltra", "endUltra" -> true;
            default -> false;
        };
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (desc.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean addInvisibleAnnotation(MethodNode method, String desc) {
        if (hasAnnotation(method.visibleAnnotations, desc) || hasAnnotation(method.invisibleAnnotations, desc)) {
            return false;
        }
        if (method.invisibleAnnotations == null) {
            method.invisibleAnnotations = new ArrayList<>();
        }
        method.invisibleAnnotations.add(new AnnotationNode(desc));
        return true;
    }
}
