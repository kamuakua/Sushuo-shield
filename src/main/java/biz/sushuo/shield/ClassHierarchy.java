package biz.sushuo.shield;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ClassHierarchy {
    private final Map<String, Info> infos;

    private ClassHierarchy(Map<String, Info> infos) {
        this.infos = infos;
    }

    static ClassHierarchy from(Map<String, ClassNode> classes, Map<String, String> classNames) {
        Map<String, Info> infos = new HashMap<>();
        for (ClassNode node : classes.values()) {
            String name = classNames.getOrDefault(node.name, node.name);
            String superName = node.superName == null ? null : classNames.getOrDefault(node.superName, node.superName);
            List<String> interfaces = node.interfaces.stream()
                    .map(interfaceName -> classNames.getOrDefault(interfaceName, interfaceName))
                    .toList();
            infos.put(name, new Info(name, superName, interfaces, (node.access & Opcodes.ACC_INTERFACE) != 0));
        }
        return new ClassHierarchy(infos);
    }

    String commonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        if (type1.charAt(0) == '[' || type2.charAt(0) == '[') {
            return commonArraySuper(type1, type2);
        }
        if (isAssignableFrom(type1, type2)) {
            return type1;
        }
        if (isAssignableFrom(type2, type1)) {
            return type2;
        }
        Info info1 = info(type1);
        Info info2 = info(type2);
        if (info1.isInterface || info2.isInterface) {
            return "java/lang/Object";
        }
        String cursor = type1;
        while (cursor != null) {
            cursor = info(cursor).superName;
            if (cursor == null) {
                return "java/lang/Object";
            }
            if (isAssignableFrom(cursor, type2)) {
                return cursor;
            }
        }
        return "java/lang/Object";
    }

    private String commonArraySuper(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        Type left = Type.getType(type1);
        Type right = Type.getType(type2);
        if (left.getSort() != Type.ARRAY || right.getSort() != Type.ARRAY) {
            return "java/lang/Object";
        }
        if (left.getDimensions() == right.getDimensions()
                && left.getElementType().getSort() == Type.OBJECT
                && right.getElementType().getSort() == Type.OBJECT) {
            String element = commonSuperClass(left.getElementType().getInternalName(), right.getElementType().getInternalName());
            return "[".repeat(left.getDimensions()) + "L" + element + ";";
        }
        return "java/lang/Object";
    }

    private boolean isAssignableFrom(String target, String source) {
        if (target.equals(source)) {
            return true;
        }
        if (target.equals("java/lang/Object")) {
            return true;
        }
        Info sourceInfo = info(source);
        if (sourceInfo.superName != null && isAssignableFrom(target, sourceInfo.superName)) {
            return true;
        }
        for (String interfaceName : sourceInfo.interfaces) {
            if (isAssignableFrom(target, interfaceName)) {
                return true;
            }
        }
        return false;
    }

    private Info info(String name) {
        Info known = infos.get(name);
        if (known != null) {
            return known;
        }
        Info reflected = reflect(name);
        infos.put(name, reflected);
        return reflected;
    }

    private static Info reflect(String name) {
        try {
            Class<?> type = Class.forName(name.replace('/', '.'), false, ClassLoader.getSystemClassLoader());
            Class<?> superclass = type.getSuperclass();
            List<String> interfaces = List.of(type.getInterfaces()).stream()
                    .map(interfaceType -> interfaceType.getName().replace('.', '/'))
                    .toList();
            return new Info(name,
                    superclass == null ? null : superclass.getName().replace('.', '/'),
                    interfaces,
                    Modifier.isInterface(type.getModifiers()));
        } catch (Throwable ignored) {
            return new Info(name, "java/lang/Object", List.of(), false);
        }
    }

    private record Info(String name, String superName, List<String> interfaces, boolean isInterface) {
    }
}
