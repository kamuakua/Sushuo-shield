package biz.sushuo.shield;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class CliParser {
    private CliParser() {
    }

    static ObfuscationOptions parse(String[] args) {
        if (args.length < 2 || hasHelp(args)) {
            throw new UsageException("Missing input/output jar.");
        }

        ObfuscationOptions.Builder builder = ObfuscationOptions.builder()
                .input(Main.path(args[0]))
                .output(Main.path(args[1]));

        List<String> excludes = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--seed" -> builder.seed(Long.parseLong(requireValue(args, ++i, arg)));
                case "--exclude" -> addCsv(excludes, requireValue(args, ++i, arg));
                case "--prefix" -> builder.namePrefix(toInternalPackage(requireValue(args, ++i, arg)));
                case "--preset" -> applyPreset(builder, requireValue(args, ++i, arg));
                case "--no-rename-classes" -> builder.renameClasses(false);
                case "--no-rename-members" -> builder.renameMembers(false);
                case "--rename-public-members" -> builder.renamePublicMembers(true);
                case "--no-strings" -> builder.encryptStrings(false);
                case "--no-numbers" -> builder.obfuscateNumbers(false);
                case "--no-virtualize" -> builder.virtualize(false);
                case "--no-control-flow" -> builder.controlFlow(false);
                case "--require-native-vm", "--native-required" -> builder.requireNativeVm(true);
                case "--keep-debug" -> builder.stripDebug(false);
                case "--no-resource-rewrite" -> builder.rewriteTextResources(false);
                default -> throw new UsageException("Unknown option: " + arg);
            }
        }

        builder.excludes(excludes);
        ObfuscationOptions options = builder.build();
        if (!Files.isRegularFile(options.input())) {
            throw new UsageException("Input jar does not exist: " + options.input());
        }
        if (options.input().equals(options.output())) {
            throw new UsageException("Input and output must be different files.");
        }
        return options;
    }

    static String usage() {
        return """
                Usage:
                  java -jar sushuo-shield.jar <input.jar> <output.jar> [options]

                Defaults are sushuo1337-style: class renaming, private/package member renaming,
                string encryption, number obfuscation, light control-flow guards, debug stripping,
                manifest/resource rewrite, and runtime decryptor injection.

                Options:
                  --preset <sushuo1337|balanced|compat|minecraft|minecraft-max|maximum|max|ultra>
                  --seed <long>
                  --exclude <glob[,glob...]>       Examples: com.example.api.**, *Mixin*, module-info
                  --prefix <internal/package/>     Default: sushuo1337/sushuoprotect/lib/
                  --no-rename-classes
                  --no-rename-members
                  --rename-public-members          Stronger but can break framework overrides/reflection
                  --no-strings
                  --no-numbers
                  --no-virtualize
                  --no-control-flow
                  --require-native-vm              Strongest: VM must run through embedded native runtime
                  --keep-debug
                  --no-resource-rewrite
                """;
    }

    private static boolean hasHelp(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new UsageException("Missing value for " + option);
        }
        return args[index];
    }

    private static void addCsv(List<String> target, String value) {
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    private static String toInternalPackage(String value) {
        String normalized = value.replace('.', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    private static void applyPreset(ObfuscationOptions.Builder builder, String preset) {
        switch (preset.toLowerCase()) {
            case "sushuo1337" -> {
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(true)
                        .namePrefix("sushuo1337/sushuoprotect/lib/");
            }
            case "balanced" -> {
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(false)
                        .stripDebug(true)
                        .rewriteTextResources(true);
            }
            case "compat" -> {
                builder.renameClasses(false)
                        .renameMembers(false)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(false)
                        .controlFlow(false)
                        .stripDebug(true)
                        .rewriteTextResources(false);
            }
            case "minecraft" -> {
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(false)
                        .minecraftMode(true);
            }
            case "minecraft-max", "minecraft-maximum", "mc-max" -> {
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(false)
                        .requireNativeVm(true)
                        .minecraftMode(true);
            }
            case "maximum", "max", "ultra" -> {
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(true)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(true)
                        .requireNativeVm(true)
                        .namePrefix("sushuo1337/sushuoprotect/lib/");
            }
            default -> throw new UsageException("Unknown preset: " + preset);
        }
    }
}
