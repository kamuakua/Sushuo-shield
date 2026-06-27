package biz.sushuo.shield;

import org.objectweb.asm.commons.Remapper;

final class ShieldRemapper extends Remapper {
    private final NamingPlan plan;

    ShieldRemapper(NamingPlan plan) {
        this.plan = plan;
    }

    @Override
    public String map(String internalName) {
        String mapped = plan.classNames().get(internalName);
        return mapped != null ? mapped : internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        String mapped = plan.methodNames().get(new MemberKey(owner, name, descriptor));
        return mapped != null ? mapped : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String mapped = plan.fieldNames().get(new MemberKey(owner, name, descriptor));
        return mapped != null ? mapped : name;
    }

    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        return name;
    }
}
