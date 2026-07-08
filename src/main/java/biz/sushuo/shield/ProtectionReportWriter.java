package biz.sushuo.shield;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

final class ProtectionReportWriter {
    private ProtectionReportWriter() {
    }

    static void write(ObfuscationOptions options, ObfuscationResult result,
                      NamingPlan namingPlan, WatermarkResources.Result watermark) throws IOException {
        if (options.reportFile() == null) {
            return;
        }
        if (options.reportFile().getParent() != null) {
            Files.createDirectories(options.reportFile().getParent());
        }
        Files.writeString(options.reportFile(), render(options, result, namingPlan, watermark),
                StandardCharsets.UTF_8);
    }

    private static String render(ObfuscationOptions options, ObfuscationResult result,
                                 NamingPlan namingPlan, WatermarkResources.Result watermark) {
        StringBuilder out = new StringBuilder(16_384);
        out.append("# Sushuo Shield protection report\n");
        out.append("generated: ").append(Instant.now()).append('\n');
        out.append("mode: ").append(options.mode().name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        out.append("input: ").append(options.input()).append('\n');
        out.append("output: ").append(options.output()).append('\n');
        out.append("seed: ").append(options.seed()).append('\n');
        out.append("prefix: ").append(namingPlan.namePrefix()).append('\n');
        out.append("runtime: ").append(result.runtimeClassName().replace('/', '.')).append('\n');
        out.append("nativeResource: ").append(namingPlan.nativeResourceName()).append('\n');
        out.append("nativeVmResourceVersion: ").append(VmPayloadResources.RESOURCE_VERSION).append('\n');
        out.append("nativeVmRequired: ").append(options.requireNativeVm()).append('\n');
        out.append("licenseLock: ").append(options.licenseHash() != 0).append('\n');
        if (options.licenseHash() != 0) {
            out.append("licenseHash: 0x").append(Integer.toUnsignedString(options.licenseHash(), 16)).append('\n');
        }
        if (watermark != null && !watermark.resourceName().isEmpty()) {
            out.append("watermarkResource: ").append(watermark.resourceName()).append('\n');
            out.append("watermarkIntegrityHash: 0x")
                    .append(Integer.toUnsignedString(watermark.integrityHash(), 16)).append('\n');
        }
        out.append('\n');

        out.append("## Options\n");
        appendFlag(out, "renameClasses", options.renameClasses());
        appendFlag(out, "renameMembers", options.renameMembers());
        appendFlag(out, "renamePublicMembers", options.renamePublicMembers());
        appendFlag(out, "encryptStrings", options.encryptStrings());
        appendFlag(out, "obfuscateNumbers", options.obfuscateNumbers());
        appendFlag(out, "virtualize", options.virtualize());
        appendFlag(out, "controlFlow", options.controlFlow());
        appendFlag(out, "stripDebug", options.stripDebug());
        appendFlag(out, "scrambleLineNumbers", options.scrambleLineNumbers());
        appendFlag(out, "referenceObfuscation", options.referenceObfuscation());
        appendFlag(out, "antiAiDeobfuscation", options.antiAiDeobfuscation());
        appendFlag(out, "sdkMarkers", options.sdkMarkers());
        appendFlag(out, "antiDebug", options.antiDebug());
        appendFlag(out, "antiVm", options.antiVm());
        appendFlag(out, "methodParameterObfuscation", options.methodParameterObfuscation());
        appendFlag(out, "rewriteTextResources", options.rewriteTextResources());
        appendFlag(out, "encryptResources", options.encryptResources());
        appendFlag(out, "minecraftMode", options.minecraftMode());
        if (!options.excludes().isEmpty()) {
            out.append("excludes:\n");
            for (String exclude : options.excludes()) {
                out.append("  - ").append(exclude).append('\n');
            }
        }
        out.append('\n');

        out.append("## Statistics\n");
        appendStat(out, "classes", result.classCount());
        appendStat(out, "renamedClasses", result.renamedClasses());
        appendStat(out, "renamedMembers", result.renamedMembers());
        appendStat(out, "virtualizedMethods", result.virtualizedMethods());
        appendStat(out, "virtualizedInstructions", result.virtualizedInstructions());
        appendStat(out, "encryptedStrings", result.encryptedStrings());
        appendStat(out, "obfuscatedNumbers", result.obfuscatedNumbers());
        appendStat(out, "controlFlowGuards", result.controlFlowGuards());
        appendStat(out, "referenceObfuscatedCalls", result.referenceObfuscatedCalls());
        appendStat(out, "scrambledLineNumbers", result.scrambledLineNumbers());
        appendStat(out, "antiDeobfuscationArtifacts", result.antiDeobfuscationArtifacts());
        appendStat(out, "parameterObfuscatedMethods", result.parameterObfuscatedMethods());
        appendStat(out, "sizeFallbackClasses", result.sizeFallbackClasses());
        appendStat(out, "encryptedResources", result.encryptedResources());
        out.append('\n');

        out.append("## Class mapping\n");
        namingPlan.classNames().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.append(entry.getKey().replace('/', '.'))
                        .append(" -> ")
                        .append(entry.getValue().replace('/', '.'))
                        .append('\n'));
        out.append('\n');

        out.append("## Method mapping\n");
        namingPlan.methodNames().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().owner() + '\u0000'
                        + entry.getKey().name() + '\u0000' + entry.getKey().descriptor()))
                .forEach(entry -> appendMember(out, entry.getKey(), entry.getValue()));
        out.append('\n');

        out.append("## Field mapping\n");
        namingPlan.fieldNames().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().owner() + '\u0000'
                        + entry.getKey().name() + '\u0000' + entry.getKey().descriptor()))
                .forEach(entry -> appendMember(out, entry.getKey(), entry.getValue()));
        return out.toString();
    }

    private static void appendFlag(StringBuilder out, String name, boolean value) {
        out.append(name).append(": ").append(value).append('\n');
    }

    private static void appendStat(StringBuilder out, String name, int value) {
        out.append(name).append(": ").append(value).append('\n');
    }

    private static void appendMember(StringBuilder out, MemberKey key, String mappedName) {
        out.append(key.owner().replace('/', '.'))
                .append('.')
                .append(key.name())
                .append(key.descriptor())
                .append(" -> ")
                .append(mappedName)
                .append('\n');
    }
}
