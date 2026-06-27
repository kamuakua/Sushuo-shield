package biz.sushuo.shield;

final class TransformStats {
    private int encryptedStrings;
    private int obfuscatedNumbers;
    private int controlFlowGuards;
    private int virtualizedMethods;
    private int virtualizedInstructions;

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
}
