package biz.sushuo.shield;

import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

final class ClassTransformer {
    private ClassTransformer() {
    }

    static void transform(
            ClassNode classNode,
            ObfuscationOptions options,
            NamingPlan namingPlan,
            ShieldRemapper remapper,
            Set<String> projectClasses,
            VmPayloadResources vmPayloadResources,
            TransformStats stats
    ) {
        String runtimeClassName = namingPlan.runtimeClassName();
        if (options.scrambleLineNumbers()) {
            stats.addScrambledLineNumbers(LineNumberScrambler.scramble(classNode, options.seed()));
        } else if (options.stripDebug()) {
            DebugStripper.strip(classNode);
        }
        if (options.isExcluded(classNode.name)) {
            return;
        }
        if (options.sdkMarkers()) {
            stats.addAntiDeobfuscationArtifacts(SDKMarkerSupport.promoteMarkerBlocks(classNode));
            stats.addAntiDeobfuscationArtifacts(SDKMarkerSupport.stripMarkerCalls(classNode));
            if (SDKMarkerSupport.noProtect(classNode)) {
                return;
            }
        }
        if (options.encryptStrings() || options.obfuscateNumbers() || options.sdkMarkers()) {
            StaticFieldInitializer.move(classNode);
        }
        if (options.encryptResources() && !options.minecraftMode()) {
            ResourceAccessRewriter.rewrite(classNode, runtimeClassName);
        }
        if (options.antiAiDeobfuscation()) {
            stats.addAntiDeobfuscationArtifacts(AntiDeobfuscationNoise.inject(classNode, runtimeClassName, options.seed()));
        }
        if (options.referenceObfuscation()) {
            stats.addReferenceObfuscatedCalls(ReferenceObfuscator.obfuscate(classNode, runtimeClassName,
                    remapper, projectClasses, namingPlan, options.seed(), options.requireNativeVm()));
        }
        if (options.virtualize()) {
            Virtualizer.virtualize(classNode, runtimeClassName, remapper, projectClasses,
                    options.seed(), options.requireNativeVm(), options.minecraftMode(), vmPayloadResources, stats,
                    false, options.requireNativeVm() || options.antiAiDeobfuscation());
        } else if (options.sdkMarkers()) {
            Virtualizer.virtualize(classNode, runtimeClassName, remapper, projectClasses,
                    options.seed(), options.requireNativeVm(), options.minecraftMode(), vmPayloadResources, stats, true,
                    options.requireNativeVm() || options.antiAiDeobfuscation());
        }
        if (options.encryptStrings()) {
            stats.addEncryptedStrings(MetadataEncryptor.encrypt(classNode, runtimeClassName, options.seed()));
        }
        if (options.encryptStrings()) {
            stats.addEncryptedStrings(StringEncryptor.encrypt(classNode, runtimeClassName, remapper,
                    options.seed(), namingPlan, options.requireNativeVm()));
        } else if (options.sdkMarkers()) {
            stats.addEncryptedStrings(StringEncryptor.encryptForcedMutate(classNode, runtimeClassName, remapper,
                    options.seed(), namingPlan, options.requireNativeVm()));
        }
        if (options.obfuscateNumbers()) {
            stats.addObfuscatedNumbers(NumberObfuscator.obfuscate(classNode, runtimeClassName, remapper,
                    options.seed(), namingPlan, options.requireNativeVm()));
        } else if (options.sdkMarkers()) {
            stats.addObfuscatedNumbers(NumberObfuscator.obfuscateForcedMutate(classNode, runtimeClassName, remapper,
                    options.seed(), namingPlan, options.requireNativeVm()));
        }
        if (options.controlFlow()) {
            stats.addControlFlowGuards(ControlFlowObfuscator.apply(classNode, runtimeClassName));
            if (options.antiAiDeobfuscation() && !options.minecraftMode()) {
                stats.addControlFlowGuards(ExceptionFlowObfuscator.apply(classNode, runtimeClassName, options.seed()));
            }
        } else if (options.sdkMarkers()) {
            stats.addControlFlowGuards(ControlFlowObfuscator.applyForcedMutate(classNode, runtimeClassName));
        }
    }
}
