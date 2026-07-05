package biz.sushuo.shield;

import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

final class ClassTransformer {
    private ClassTransformer() {
    }

    static void transform(
            ClassNode classNode,
            ObfuscationOptions options,
            String runtimeClassName,
            ShieldRemapper remapper,
            Set<String> projectClasses,
            VmPayloadResources vmPayloadResources,
            TransformStats stats
    ) {
        if (options.stripDebug()) {
            DebugStripper.strip(classNode);
        }
        if (options.isExcluded(classNode.name)) {
            return;
        }
        if (options.virtualize()) {
            Virtualizer.virtualize(classNode, runtimeClassName, remapper, projectClasses,
                    options.seed(), options.requireNativeVm(), vmPayloadResources, stats);
        }
        if (options.encryptStrings()) {
            stats.addEncryptedStrings(MetadataEncryptor.encrypt(classNode, runtimeClassName, options.seed()));
        }
        if (options.encryptStrings()) {
            stats.addEncryptedStrings(StringEncryptor.encrypt(classNode, runtimeClassName, remapper, options.seed()));
        }
        if (options.obfuscateNumbers()) {
            stats.addObfuscatedNumbers(NumberObfuscator.obfuscate(classNode, runtimeClassName, remapper, options.seed()));
        }
        if (options.controlFlow()) {
            stats.addControlFlowGuards(ControlFlowObfuscator.apply(classNode, runtimeClassName));
        }
    }
}
