package biz.sushuo.shield;

final class TransformStats {
    private int encryptedStrings;
    private int obfuscatedNumbers;
    private int controlFlowGuards;
    private int virtualizedMethods;
    private int virtualizedInstructions;
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

    void addSizeFallbackClass() {
        sizeFallbackClasses++;
    }

    void add(TransformStats other) {
        encryptedStrings += other.encryptedStrings;
        obfuscatedNumbers += other.obfuscatedNumbers;
        controlFlowGuards += other.controlFlowGuards;
        virtualizedMethods += other.virtualizedMethods;
        virtualizedInstructions += other.virtualizedInstructions;
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

    int sizeFallbackClasses() {
        return sizeFallbackClasses;
    }
}
