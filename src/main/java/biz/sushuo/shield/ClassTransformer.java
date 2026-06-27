package biz.sushuo.shield;

import org.objectweb.asm.tree.ClassNode;

final class ClassTransformer {
    private ClassTransformer() {
    }

    static void transform(
            ClassNode classNode,
            ObfuscationOptions options,
            String runtimeClassName,
            ShieldRemapper remapper,
            TransformStats stats
    ) {
        if (options.stripDebug()) {
            DebugStripper.strip(classNode);
        }
        if (options.virtualize()) {
            Virtualizer.virtualize(classNode, runtimeClassName, remapper, stats);
        }
        if (options.encryptStrings()) {
            stats.addEncryptedStrings(StringEncryptor.encrypt(classNode, runtimeClassName, options.seed()));
        }
        if (options.obfuscateNumbers()) {
            stats.addObfuscatedNumbers(NumberObfuscator.obfuscate(classNode, options.seed()));
        }
        if (options.controlFlow()) {
            stats.addControlFlowGuards(ControlFlowObfuscator.apply(classNode, runtimeClassName));
        }
    }
}
