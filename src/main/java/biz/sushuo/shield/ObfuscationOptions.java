package biz.sushuo.shield;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record ObfuscationOptions(
        Path input,
        Path output,
        Path reportFile,
        long seed,
        ProtectionMode mode,
        List<String> excludes,
        String namePrefix,
        boolean renameClasses,
        boolean renameMembers,
        boolean renamePublicMembers,
        boolean encryptStrings,
        boolean obfuscateNumbers,
        boolean virtualize,
        boolean controlFlow,
        boolean stripDebug,
        boolean scrambleLineNumbers,
        boolean referenceObfuscation,
        boolean antiAiDeobfuscation,
        boolean sdkMarkers,
        boolean antiDebug,
        boolean antiVm,
        int licenseHash,
        boolean methodParameterObfuscation,
        boolean rewriteTextResources,
        boolean encryptResources,
        boolean requireNativeVm,
        boolean minecraftMode,
        boolean jvmPhantom
) {
    static Builder builder() {
        return new Builder();
    }

    boolean isExcluded(String internalName) {
        String dotted = internalName.replace('/', '.');
        for (String pattern : excludes) {
            String normalized = pattern.replace('\\', '/');
            String dottedPattern = normalized.replace('/', '.');
            if (matches(dotted, dottedPattern) || matches(internalName, normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String value, String pattern) {
        if (pattern.equals(value)) {
            return true;
        }
        String regex = pattern
                .replace(".", "\\.")
                .replace("$", "\\$")
                .replace("**", "\u0000")
                .replace("*", "[^./]*")
                .replace("\u0000", ".*");
        return value.matches(regex);
    }

    ObfuscationOptions withAdditionalExcludes(List<String> additionalExcludes) {
        if (additionalExcludes.isEmpty()) {
            return this;
        }
        Set<String> merged = new LinkedHashSet<>(excludes);
        merged.addAll(additionalExcludes);
        return new ObfuscationOptions(input, output, reportFile, seed, mode, List.copyOf(merged), namePrefix,
                renameClasses, renameMembers, renamePublicMembers, encryptStrings,
                obfuscateNumbers, virtualize, controlFlow, stripDebug, scrambleLineNumbers,
                referenceObfuscation, antiAiDeobfuscation, sdkMarkers, antiDebug, antiVm,
                licenseHash, methodParameterObfuscation, rewriteTextResources, encryptResources,
                requireNativeVm, minecraftMode, jvmPhantom);
    }

    ObfuscationOptions withClassTransforms(boolean encryptStrings, boolean obfuscateNumbers,
                                           boolean virtualize, boolean controlFlow) {
        return new ObfuscationOptions(input, output, reportFile, seed, mode, excludes, namePrefix,
                renameClasses, renameMembers, renamePublicMembers, encryptStrings,
                obfuscateNumbers, virtualize, controlFlow, stripDebug, scrambleLineNumbers,
                referenceObfuscation, antiAiDeobfuscation, sdkMarkers, antiDebug, antiVm,
                licenseHash, methodParameterObfuscation, rewriteTextResources, encryptResources,
                requireNativeVm, minecraftMode, jvmPhantom);
    }

    ObfuscationOptions withoutExtraProtectionPasses() {
        return new ObfuscationOptions(input, output, reportFile, seed, mode, excludes, namePrefix,
                renameClasses, renameMembers, renamePublicMembers, encryptStrings,
                obfuscateNumbers, virtualize, controlFlow, stripDebug, false,
                false, false, sdkMarkers, antiDebug, antiVm, licenseHash,
                methodParameterObfuscation, rewriteTextResources, encryptResources,
                requireNativeVm, minecraftMode, jvmPhantom);
    }

    static final class Builder {
        private Path input;
        private Path output;
        private Path reportFile;
        private long seed = new SecureRandom().nextLong();
        private ProtectionMode mode = ProtectionMode.SUSHUO1337;
        private List<String> excludes = new ArrayList<>();
        private String namePrefix = "sushuo1337/sushuoprotect/lib/";
        private boolean renameClasses = true;
        private boolean renameMembers = true;
        private boolean renamePublicMembers;
        private boolean encryptStrings = true;
        private boolean obfuscateNumbers = true;
        private boolean virtualize = true;
        private boolean controlFlow = true;
        private boolean stripDebug = true;
        private boolean scrambleLineNumbers;
        private boolean referenceObfuscation;
        private boolean antiAiDeobfuscation;
        private boolean sdkMarkers;
        private boolean antiDebug;
        private boolean antiVm;
        private int licenseHash;
        private boolean methodParameterObfuscation;
        private boolean rewriteTextResources = true;
        private boolean encryptResources;
        private boolean requireNativeVm;
        private boolean minecraftMode;
        private boolean jvmPhantom;

        Builder input(Path input) {
            this.input = input;
            return this;
        }

        Builder output(Path output) {
            this.output = output;
            return this;
        }

        Builder reportFile(Path reportFile) {
            this.reportFile = reportFile;
            return this;
        }

        Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        Builder mode(ProtectionMode mode) {
            this.mode = mode;
            return this;
        }

        Builder excludes(List<String> excludes) {
            this.excludes = List.copyOf(excludes);
            return this;
        }

        Builder namePrefix(String namePrefix) {
            this.namePrefix = namePrefix;
            return this;
        }

        Builder renameClasses(boolean renameClasses) {
            this.renameClasses = renameClasses;
            return this;
        }

        Builder renameMembers(boolean renameMembers) {
            this.renameMembers = renameMembers;
            return this;
        }

        Builder renamePublicMembers(boolean renamePublicMembers) {
            this.renamePublicMembers = renamePublicMembers;
            return this;
        }

        Builder encryptStrings(boolean encryptStrings) {
            this.encryptStrings = encryptStrings;
            return this;
        }

        Builder obfuscateNumbers(boolean obfuscateNumbers) {
            this.obfuscateNumbers = obfuscateNumbers;
            return this;
        }

        Builder virtualize(boolean virtualize) {
            this.virtualize = virtualize;
            return this;
        }

        Builder controlFlow(boolean controlFlow) {
            this.controlFlow = controlFlow;
            return this;
        }

        Builder stripDebug(boolean stripDebug) {
            this.stripDebug = stripDebug;
            return this;
        }

        Builder scrambleLineNumbers(boolean scrambleLineNumbers) {
            this.scrambleLineNumbers = scrambleLineNumbers;
            return this;
        }

        Builder referenceObfuscation(boolean referenceObfuscation) {
            this.referenceObfuscation = referenceObfuscation;
            return this;
        }

        Builder antiAiDeobfuscation(boolean antiAiDeobfuscation) {
            this.antiAiDeobfuscation = antiAiDeobfuscation;
            return this;
        }

        Builder sdkMarkers(boolean sdkMarkers) {
            this.sdkMarkers = sdkMarkers;
            return this;
        }

        Builder antiDebug(boolean antiDebug) {
            this.antiDebug = antiDebug;
            return this;
        }

        Builder antiVm(boolean antiVm) {
            this.antiVm = antiVm;
            return this;
        }

        Builder licenseKey(String licenseKey) {
            this.licenseHash = licenseKey == null || licenseKey.isEmpty() ? 0 : licenseHash(licenseKey);
            return this;
        }

        Builder licenseHash(int licenseHash) {
            this.licenseHash = licenseHash;
            return this;
        }

        Builder methodParameterObfuscation(boolean methodParameterObfuscation) {
            this.methodParameterObfuscation = methodParameterObfuscation;
            return this;
        }

        Builder rewriteTextResources(boolean rewriteTextResources) {
            this.rewriteTextResources = rewriteTextResources;
            return this;
        }

        Builder encryptResources(boolean encryptResources) {
            this.encryptResources = encryptResources;
            return this;
        }

        Builder requireNativeVm(boolean requireNativeVm) {
            this.requireNativeVm = requireNativeVm;
            return this;
        }

        Builder minecraftMode(boolean minecraftMode) {
            this.minecraftMode = minecraftMode;
            return this;
        }

        Builder jvmPhantom(boolean jvmPhantom) {
            this.jvmPhantom = jvmPhantom;
            return this;
        }

        ObfuscationOptions build() {
            if (input == null || output == null) {
                throw new UsageException("Input/output jar is required.");
            }
            return new ObfuscationOptions(input, output, reportFile, seed, mode, excludes, namePrefix,
                    renameClasses, renameMembers, renamePublicMembers, encryptStrings,
                    obfuscateNumbers, virtualize, controlFlow, stripDebug, scrambleLineNumbers,
                    referenceObfuscation, antiAiDeobfuscation, sdkMarkers, antiDebug, antiVm,
                    licenseHash, methodParameterObfuscation, rewriteTextResources, encryptResources,
                    requireNativeVm, minecraftMode, jvmPhantom);
        }

        private static int licenseHash(String value) {
            int hash = 0x53534C4B;
            for (int i = 0; i < value.length(); i++) {
                hash ^= value.charAt(i) * 0x45D9F3B;
                hash = Integer.rotateLeft(hash + 0x7F4A7C15, 9);
                hash ^= hash >>> 16;
                hash *= 0x85EBCA6B;
            }
            hash ^= hash >>> 13;
            hash *= 0xC2B2AE35;
            hash ^= hash >>> 16;
            return hash == 0 ? 0x13579BDF : hash;
        }
    }
}
