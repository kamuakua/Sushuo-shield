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
            System.out.println(" input:  " + options.input());
            System.out.println(" output: " + options.output());
            System.out.println(" classes: " + result.classCount());
            System.out.println(" renamed classes: " + result.renamedClasses());
            System.out.println(" renamed members: " + result.renamedMembers());
            System.out.println(" virtualized methods: " + result.virtualizedMethods());
            System.out.println(" virtualized instructions: " + result.virtualizedInstructions());
            System.out.println(" encrypted strings: " + result.encryptedStrings());
            System.out.println(" obfuscated numbers: " + result.obfuscatedNumbers());
            System.out.println(" control-flow guards: " + result.controlFlowGuards());
            System.out.println(" size fallback classes: " + result.sizeFallbackClasses());
            System.out.println(" runtime: " + result.runtimeClassName().replace('/', '.'));
            System.out.println(" native VM required: " + options.requireNativeVm());
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
