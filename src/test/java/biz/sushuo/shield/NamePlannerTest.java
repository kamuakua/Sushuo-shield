package biz.sushuo.shield;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NamePlannerTest implements Opcodes {
    @Test
    void strongRenamePreservesExternalVirtualMethodContracts() {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        classes.put("sample/Panel", type("sample/Panel", "javax/swing/JPanel",
                method(ACC_PROTECTED, "paintComponent", "(Ljava/awt/Graphics;)V"),
                method(ACC_PUBLIC, "applicationMethod", "()V")));
        classes.put("sample/Keys", type("sample/Keys", "java/awt/event/KeyAdapter",
                method(ACC_PUBLIC, "keyPressed", "(Ljava/awt/event/KeyEvent;)V")));
        classes.put("sample/Window", type("sample/Window", "java/awt/event/WindowAdapter",
                method(ACC_PUBLIC, "windowClosing", "(Ljava/awt/event/WindowEvent;)V")));

        ObfuscationOptions options = ObfuscationOptions.builder()
                .input(Path.of("input.jar"))
                .output(Path.of("output.jar"))
                .seed(123456789L)
                .mode(ProtectionMode.STACKED)
                .renameMembers(true)
                .renamePublicMembers(true)
                .build();
        NamingPlan plan = NamePlanner.plan(classes, options);

        assertFalse(plan.methodNames().containsKey(new MemberKey("sample/Panel", "paintComponent",
                "(Ljava/awt/Graphics;)V")));
        assertFalse(plan.methodNames().containsKey(new MemberKey("sample/Keys", "keyPressed",
                "(Ljava/awt/event/KeyEvent;)V")));
        assertFalse(plan.methodNames().containsKey(new MemberKey("sample/Window", "windowClosing",
                "(Ljava/awt/event/WindowEvent;)V")));
        assertTrue(plan.methodNames().containsKey(new MemberKey("sample/Panel", "applicationMethod", "()V")));
    }

    private static ClassNode type(String name, String superName, MethodNode... methods) {
        ClassNode node = new ClassNode();
        node.version = V17;
        node.access = ACC_PUBLIC;
        node.name = name;
        node.superName = superName;
        node.methods.addAll(java.util.List.of(methods));
        return node;
    }

    private static MethodNode method(int access, String name, String descriptor) {
        return new MethodNode(access, name, descriptor, null, null);
    }
}
