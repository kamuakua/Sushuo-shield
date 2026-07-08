package biz.sushuo.shield;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            ObfuscationOptions options = CliParser.parse(args);
            ObfuscationResult result = new JarObfuscator().obfuscate(options);
            System.out.println("Sushuo Shield finished");
            System.out.println(" mode:   " + options.mode().name().toLowerCase(java.util.Locale.ROOT));
            System.out.println(" input:  " + options.input());
            System.out.println(" output: " + options.output());
            if (options.reportFile() != null) {
                System.out.println(" report: " + options.reportFile());
            }
            System.out.println(" classes: " + result.classCount());
            System.out.println(" renamed classes: " + result.renamedClasses());
            System.out.println(" renamed members: " + result.renamedMembers());
            System.out.println(" virtualized methods: " + result.virtualizedMethods());
            System.out.println(" virtualized instructions: " + result.virtualizedInstructions());
            System.out.println(" encrypted strings: " + result.encryptedStrings());
            System.out.println(" obfuscated numbers: " + result.obfuscatedNumbers());
            System.out.println(" control-flow guards: " + result.controlFlowGuards());
            System.out.println(" reference-obfuscated calls: " + result.referenceObfuscatedCalls());
            System.out.println(" scrambled line numbers: " + result.scrambledLineNumbers());
            System.out.println(" anti-deobfuscation artifacts: " + result.antiDeobfuscationArtifacts());
            System.out.println(" parameter-obfuscated methods: " + result.parameterObfuscatedMethods());
            System.out.println(" size fallback classes: " + result.sizeFallbackClasses());
            System.out.println(" encrypted resources: " + result.encryptedResources());
            System.out.println(" runtime: " + result.runtimeClassName().replace('/', '.'));
            System.out.println(" native VM required: " + options.requireNativeVm());
            System.out.println(" sdk markers: " + options.sdkMarkers());
            System.out.println(" anti-debug: " + options.antiDebug());
            System.out.println(" anti-vm: " + options.antiVm());
            System.out.println(" license lock: " + (options.licenseHash() != 0));
            System.out.println(" method parameters: " + options.methodParameterObfuscation());
        } catch (UsageException ex) {
            System.err.println(ex.getMessage());
            System.err.println();
            System.err.println(CliParser.usage());
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Sushuo Shield failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static Path path(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }
}
