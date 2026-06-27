package biz.sushuo.shield;

import java.util.Map;

record NamingPlan(
        Map<String, String> classNames,
        Map<MemberKey, String> methodNames,
        Map<MemberKey, String> fieldNames,
        String runtimeClassName
) {
}
