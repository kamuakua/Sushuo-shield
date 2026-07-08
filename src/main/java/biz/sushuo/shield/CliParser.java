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
                case "--report-file", "--report", "--change-log", "--changelog" ->
                        builder.reportFile(Main.path(requireValue(args, ++i, arg)));
                case "--exclude" -> addCsv(excludes, requireValue(args, ++i, arg));
                case "--prefix" -> builder.namePrefix(toInternalPackage(requireValue(args, ++i, arg)));
                case "--preset", "--mode" -> applyPreset(builder, requireValue(args, ++i, arg));
                case "--no-rename-classes" -> builder.renameClasses(false);
                case "--no-rename-members" -> builder.renameMembers(false);
                case "--rename-public-members" -> builder.renamePublicMembers(true);
                case "--no-strings" -> builder.encryptStrings(false);
                case "--no-numbers" -> builder.obfuscateNumbers(false);
                case "--no-virtualize" -> builder.virtualize(false);
                case "--no-control-flow" -> builder.controlFlow(false);
                case "--reference-obfuscation", "--references" -> builder.referenceObfuscation(true);
                case "--no-reference-obfuscation", "--no-references" -> builder.referenceObfuscation(false);
                case "--line-scramble", "--scramble-lines" -> builder.scrambleLineNumbers(true).stripDebug(false);
                case "--anti-ai", "--anti-deobfuscation" -> builder.antiAiDeobfuscation(true);
                case "--no-anti-ai", "--no-anti-deobfuscation" -> builder.antiAiDeobfuscation(false);
                case "--sdk-markers", "--vmprotect-sdk" -> builder.sdkMarkers(true);
                case "--no-sdk-markers", "--no-vmprotect-sdk" -> builder.sdkMarkers(false);
                case "--anti-debug" -> builder.antiDebug(true);
                case "--no-anti-debug" -> builder.antiDebug(false);
                case "--anti-vm" -> builder.antiVm(true);
                case "--no-anti-vm" -> builder.antiVm(false);
                case "--license", "--license-key" -> builder.licenseKey(requireValue(args, ++i, arg));
                case "--license-hash" -> builder.licenseHash((int) Long.parseLong(requireValue(args, ++i, arg)));
                case "--method-parameters", "--parameter-obfuscation" -> builder.methodParameterObfuscation(true);
                case "--no-method-parameters", "--no-parameter-obfuscation" -> builder.methodParameterObfuscation(false);
                case "--resource-encryption", "--encrypt-resources" -> builder.encryptResources(true);
                case "--no-resource-encryption", "--no-encrypt-resources" -> builder.encryptResources(false);
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
                  --preset/--mode <sushuo1337|balanced|compat|minecraft|minecraft-max|jnic|zkm26|vmprotect|vmp|stacked|max>
                  --seed <long>
                  --report-file <path>          Write ZKM-style change log / mapping / protection report
                  --exclude <glob[,glob...]>       Examples: com.example.api.**, *Mixin*, module-info
                  --prefix <internal/package/>     Default: sushuo1337/sushuoprotect/lib/
                  --no-rename-classes
                  --no-rename-members
                  --rename-public-members          Stronger but can break framework overrides/reflection
                  --no-strings
                  --no-numbers
                  --no-virtualize
                  --no-control-flow
                  --reference-obfuscation / --no-reference-obfuscation
                  --line-scramble
                  --anti-ai / --no-anti-ai
                  --sdk-markers / --no-sdk-markers
                  --anti-debug / --no-anti-debug
                  --anti-vm / --no-anti-vm
                  --license-key <value>            Runtime requires -Dsushuo.license=value or SUSHUO_LICENSE=value
                  --method-parameters / --no-method-parameters
                  --resource-encryption / --no-resource-encryption
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
                builder.mode(ProtectionMode.SUSHUO1337)
                        .referenceObfuscation(false)
                        .scrambleLineNumbers(false)
                        .antiAiDeobfuscation(false)
                        .sdkMarkers(false)
                        .antiDebug(false)
                        .antiVm(false)
                        .methodParameterObfuscation(false);
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(true)
                        .encryptResources(false)
                        .namePrefix("sushuo1337/sushuoprotect/lib/");
            }
            case "balanced" -> {
                builder.mode(ProtectionMode.BALANCED)
                        .referenceObfuscation(false)
                        .scrambleLineNumbers(false)
                        .antiAiDeobfuscation(false)
                        .sdkMarkers(false)
                        .antiDebug(false)
                        .antiVm(false)
                        .methodParameterObfuscation(false);
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(false)
                        .stripDebug(true)
                        .rewriteTextResources(true)
                        .encryptResources(false);
            }
            case "compat" -> {
                builder.mode(ProtectionMode.COMPAT)
                        .referenceObfuscation(false)
                        .scrambleLineNumbers(false)
                        .antiAiDeobfuscation(false)
                        .sdkMarkers(false)
                        .antiDebug(false)
                        .antiVm(false)
                        .methodParameterObfuscation(false);
                builder.renameClasses(false)
                        .renameMembers(false)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(false)
                        .controlFlow(false)
                        .stripDebug(true)
                        .rewriteTextResources(false)
                        .encryptResources(false);
            }
            case "minecraft" -> {
                builder.mode(ProtectionMode.MINECRAFT)
                        .referenceObfuscation(false)
                        .scrambleLineNumbers(false)
                        .antiAiDeobfuscation(false)
                        .sdkMarkers(false)
                        .antiDebug(false)
                        .antiVm(false)
                        .methodParameterObfuscation(false);
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(false)
                        .encryptResources(false)
                        .minecraftMode(true);
            }
            case "minecraft-max", "minecraft-maximum", "mc-max" -> {
                builder.mode(ProtectionMode.MINECRAFT_MAX)
                        .referenceObfuscation(false)
                        .scrambleLineNumbers(false)
                        .antiAiDeobfuscation(true)
                        .sdkMarkers(false)
                        .antiDebug(false)
                        .antiVm(false)
                        .methodParameterObfuscation(false);
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(false)
                        .encryptResources(false)
                        .requireNativeVm(true)
                        .minecraftMode(true);
            }
            case "jnic", "jnic-style" -> {
                builder.mode(ProtectionMode.JNIC)
                        .renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(false)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .scrambleLineNumbers(false)
                        .referenceObfuscation(true)
                        .antiAiDeobfuscation(true)
                        .sdkMarkers(false)
                        .antiDebug(false)
                        .antiVm(false)
                        .methodParameterObfuscation(false)
                        .rewriteTextResources(true)
                        .encryptResources(true)
                        .requireNativeVm(true)
                        .minecraftMode(false)
                        .namePrefix("sushuo1337/sushuoprotect/jnic/");
            }
            case "zkm", "zkm26", "zelix", "klassmaster", "zelix-klassmaster" -> {
                builder.mode(ProtectionMode.ZKM26)
                        .renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(true)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(false)
                        .controlFlow(true)
                        .stripDebug(false)
                        .scrambleLineNumbers(true)
                        .referenceObfuscation(true)
                        .antiAiDeobfuscation(true)
                        .sdkMarkers(true)
                        .antiDebug(false)
                        .antiVm(false)
                        .methodParameterObfuscation(true)
                        .rewriteTextResources(true)
                        .encryptResources(true)
                        .requireNativeVm(false)
                        .minecraftMode(false)
                        .namePrefix("sushuo1337/sushuoprotect/zkm/");
            }
            case "vmp", "vmprotect", "vm-protect", "vmprotect-ultra" -> {
                builder.mode(ProtectionMode.VMP)
                        .renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(true)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .scrambleLineNumbers(false)
                        .referenceObfuscation(true)
                        .antiAiDeobfuscation(true)
                        .sdkMarkers(true)
                        .antiDebug(true)
                        .antiVm(true)
                        .methodParameterObfuscation(false)
                        .rewriteTextResources(true)
                        .encryptResources(true)
                        .requireNativeVm(true)
                        .minecraftMode(false)
                        .namePrefix("sushuo1337/sushuoprotect/vmp/");
            }
            case "stacked", "all", "multi", "jnic-zkm-vmp" -> {
                builder.mode(ProtectionMode.STACKED)
                        .renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(true)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(false)
                        .scrambleLineNumbers(true)
                        .referenceObfuscation(true)
                        .antiAiDeobfuscation(true)
                        .sdkMarkers(true)
                        .antiDebug(true)
                        .antiVm(true)
                        .methodParameterObfuscation(true)
                        .rewriteTextResources(true)
                        .encryptResources(true)
                        .requireNativeVm(true)
                        .minecraftMode(false)
                        .namePrefix("sushuo1337/sushuoprotect/vmp/");
            }
            case "maximum", "max", "ultra" -> {
                builder.mode(ProtectionMode.STACKED)
                        .referenceObfuscation(true)
                        .scrambleLineNumbers(false)
                        .antiAiDeobfuscation(true)
                        .sdkMarkers(true)
                        .antiDebug(true)
                        .antiVm(true)
                        .methodParameterObfuscation(true);
                builder.renameClasses(true)
                        .renameMembers(true)
                        .renamePublicMembers(true)
                        .encryptStrings(true)
                        .obfuscateNumbers(true)
                        .virtualize(true)
                        .controlFlow(true)
                        .stripDebug(true)
                        .rewriteTextResources(true)
                        .encryptResources(true)
                        .requireNativeVm(true)
                        .namePrefix("sushuo1337/sushuoprotect/lib/");
            }
            default -> throw new UsageException("Unknown preset: " + preset);
        }
    }
}
