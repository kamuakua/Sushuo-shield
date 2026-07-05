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
        long seed,
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
        boolean rewriteTextResources,
        boolean requireNativeVm,
        boolean minecraftMode
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
        return new ObfuscationOptions(input, output, seed, List.copyOf(merged), namePrefix,
                renameClasses, renameMembers, renamePublicMembers, encryptStrings,
                obfuscateNumbers, virtualize, controlFlow, stripDebug, rewriteTextResources, requireNativeVm, minecraftMode);
    }

    ObfuscationOptions withClassTransforms(boolean encryptStrings, boolean obfuscateNumbers,
                                           boolean virtualize, boolean controlFlow) {
        return new ObfuscationOptions(input, output, seed, excludes, namePrefix,
                renameClasses, renameMembers, renamePublicMembers, encryptStrings,
                obfuscateNumbers, virtualize, controlFlow, stripDebug, rewriteTextResources, requireNativeVm, minecraftMode);
    }

    static final class Builder {
        private Path input;
        private Path output;
        private long seed = new SecureRandom().nextLong();
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
        private boolean rewriteTextResources = true;
        private boolean requireNativeVm;
        private boolean minecraftMode;

        Builder input(Path input) {
            this.input = input;
            return this;
        }

        Builder output(Path output) {
            this.output = output;
            return this;
        }

        Builder seed(long seed) {
            this.seed = seed;
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

        Builder rewriteTextResources(boolean rewriteTextResources) {
            this.rewriteTextResources = rewriteTextResources;
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

        ObfuscationOptions build() {
            if (input == null || output == null) {
                throw new UsageException("Input/output jar is required.");
            }
            return new ObfuscationOptions(input, output, seed, excludes, namePrefix,
                    renameClasses, renameMembers, renamePublicMembers, encryptStrings,
                    obfuscateNumbers, virtualize, controlFlow, stripDebug, rewriteTextResources, requireNativeVm, minecraftMode);
        }
    }
}
