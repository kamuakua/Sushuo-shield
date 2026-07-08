package biz.sushuo.shield;

final class TransformStats {
    private int encryptedStrings;
    private int obfuscatedNumbers;
    private int controlFlowGuards;
    private int virtualizedMethods;
    private int virtualizedInstructions;
    private int referenceObfuscatedCalls;
    private int scrambledLineNumbers;
    private int antiDeobfuscationArtifacts;
    private int parameterObfuscatedMethods;
    private int sizeFallbackClasses;

    void addEncryptedStrings(int count) {
        encryptedStrings += count;
    }

    void addObfuscatedNumbers(int count) {
        obfuscatedNumbers += count;
    }

    void addControlFlowGuards(int count) {
        controlFlowGuards += count;
    }

    void addVirtualizedMethod(int instructionCount) {
        virtualizedMethods++;
        virtualizedInstructions += instructionCount;
    }

    void addReferenceObfuscatedCalls(int count) {
        referenceObfuscatedCalls += count;
    }

    void addScrambledLineNumbers(int count) {
        scrambledLineNumbers += count;
    }

    void addAntiDeobfuscationArtifacts(int count) {
        antiDeobfuscationArtifacts += count;
    }

    void addParameterObfuscatedMethods(int count) {
        parameterObfuscatedMethods += count;
    }

    void addSizeFallbackClass() {
        sizeFallbackClasses++;
    }

    void add(TransformStats other) {
        encryptedStrings += other.encryptedStrings;
        obfuscatedNumbers += other.obfuscatedNumbers;
        controlFlowGuards += other.controlFlowGuards;
        virtualizedMethods += other.virtualizedMethods;
        virtualizedInstructions += other.virtualizedInstructions;
        referenceObfuscatedCalls += other.referenceObfuscatedCalls;
        scrambledLineNumbers += other.scrambledLineNumbers;
        antiDeobfuscationArtifacts += other.antiDeobfuscationArtifacts;
        parameterObfuscatedMethods += other.parameterObfuscatedMethods;
        sizeFallbackClasses += other.sizeFallbackClasses;
    }

    int encryptedStrings() {
        return encryptedStrings;
    }

    int obfuscatedNumbers() {
        return obfuscatedNumbers;
    }

    int controlFlowGuards() {
        return controlFlowGuards;
    }

    int virtualizedMethods() {
        return virtualizedMethods;
    }

    int virtualizedInstructions() {
        return virtualizedInstructions;
    }

    int referenceObfuscatedCalls() {
        return referenceObfuscatedCalls;
    }

    int scrambledLineNumbers() {
        return scrambledLineNumbers;
    }

    int antiDeobfuscationArtifacts() {
        return antiDeobfuscationArtifacts;
    }

    int parameterObfuscatedMethods() {
        return parameterObfuscatedMethods;
    }

    int sizeFallbackClasses() {
        return sizeFallbackClasses;
    }
}
