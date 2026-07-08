package biz.sushuo.shield;

record ObfuscationResult(
        int classCount,
        int renamedClasses,
        int renamedMembers,
        int virtualizedMethods,
        int virtualizedInstructions,
        int encryptedStrings,
        int obfuscatedNumbers,
        int controlFlowGuards,
        int referenceObfuscatedCalls,
        int scrambledLineNumbers,
        int antiDeobfuscationArtifacts,
        int parameterObfuscatedMethods,
        int sizeFallbackClasses,
        int encryptedResources,
        String runtimeClassName
) {
}
