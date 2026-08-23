# Sushuo Shield

JAR obfuscator/protector with sushuo1337-style defaults:

- `sushuo1337/sushuoprotect/lib/...` class relocation by default
- class/member renaming
- string runtime encryption
- number expression obfuscation
- static constant field initialization moved into bytecode before literal passes
- Phantom-style JVM member shuffling and project call/field wrappers in strong presets
- debug/signature stripping
- manifest and text resource class-name rewrite
- lightweight control-flow guards
- VM virtualization for supported methods
- encrypted embedded JNI native VM payload with Java fallback by default

Build:

```powershell
mvn -DskipTests package
```

ImGui GUI:

```powershell
java --enable-native-access=ALL-UNNAMED -jar target\sushuo-shield-1.0.0.jar --gui
```

Use:

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar output.jar --preset sushuo1337
```

Multi-mode presets:

```powershell
# JNIC-style: native-required VM, encrypted per-method VM resources,
# reference indirection, string/number/control-flow hardening, anti-AI decoys.
java -jar target\sushuo-shield-1.0.0.jar input.jar output-jnic.jar --mode jnic

# Zelix KlassMaster 26-style: Java bytecode mode with aggressive rename,
# flow/string/integer/long/reference obfuscation, line-number scrambling,
# and anti-deobfuscation noise. No native payload is packaged in this mode.
java -jar target\sushuo-shield-1.0.0.jar input.jar output-zkm26.jar --mode zkm26

# JVM-only Phantom-style mode: field lowering, member shuffling, project
# call/field wrappers, plus the existing string/number/VM/flow passes.
java -jar target\sushuo-shield-1.0.0.jar input.jar output-jvm.jar --mode jvm

# VMProtect-style: mutation-like number/string/control-flow layer plus
# native-required VM virtualization and encrypted native/resource payloads.
java -jar target\sushuo-shield-1.0.0.jar input.jar output-vmp.jar --mode vmprotect

# Stacked mode combines the JNIC/ZKM/VMProtect-style layers.
java -jar target\sushuo-shield-1.0.0.jar input.jar output-stacked.jar --mode stacked

# `all` is an alias for the same combined protection pipeline.
java -jar target\sushuo-shield-1.0.0.jar input.jar output-all.jar --mode all
```

Extra switches:

```powershell
--reference-obfuscation      replace project method calls with runtime-resolved references
--line-scramble              keep but scramble line numbers instead of stripping them
--anti-ai                    inject opaque decoy fields/methods, fake VM programs, and fake analysis resources
--sdk-markers                recognize VMProtect-style SDK marker calls/annotations and strip marker calls
--anti-debug                 reject JDWP/-Xdebug/debug-agent execution
--anti-vm                    reject obvious VirtualBox/VMware/QEMU/Hyper-V-like environments
--license-key <value>        bind output to a runtime key supplied as -Dsushuo.license=value or SUSHUO_LICENSE=value
--method-parameters          add unused dummy parameters and rewrite project call sites before other layers
--report-file <path>         write a ZKM-style change log / protection report with mappings and counters
```

VMProtect-style SDK markers are available to source projects that compile
against this jar:

```java
import biz.sushuo.shield.sdk.VMProtectSDK;
import biz.sushuo.shield.sdk.Ultra;

public final class Example {
    @Ultra
    static int secret(int a, int b) {
        VMProtectSDK.beginUltra();
        int value = (a * 31) ^ b;
        VMProtectSDK.endUltra();
        return value;
    }
}
```

The marker calls are removed from protected output so the SDK classes do not
need to ship with the protected application. `@Virtualize` and `@Ultra` also
force VM virtualization for the annotated method even in a non-VM preset such as
`zkm26`; this mirrors the VMProtect SDK style of marking a sensitive routine
instead of enabling whole-program virtualization. `@Mutate` and `@Ultra` force
method-level mutation too: string encryption, integer/long mutation, and an
opaque control-flow guard are applied to the annotated method even when the
global `--no-strings --no-numbers --no-control-flow --no-virtualize` switches are
used. Class-level `@Virtualize`, `@Mutate`, and `@Ultra` apply the same policy to
all methods in that class. SDK begin/end calls are also recognized: a method that
contains `VMProtectSDK.beginVirtualization()/endVirtualization()` is promoted to
the same forced-VM policy, `beginMutation()/endMutation()` is promoted to forced
mutation, and `beginUltra()/endUltra()` is promoted to both; the marker calls are
then stripped from the protected bytecode.

Anti-deobfuscation / anti-AI layer:

- injects synthetic opaque helper methods and fields into protected classes
- injects fake `_vp$...` VM program methods that look like recoverable VM bytecode but are never called
- guards real `_vp$...` VM program suppliers in anti-AI/native-required modes so direct reflective extraction fails unless the call originates from the protected host method
- adds randomized `data/*.bin`, `data/*.json`, and `meta/*.map` decoy resources under the protection package
- native VM payload resources use a v3 header bound to the real Java call context; the C VM checks the current class/method, VM site id, return kind, resource name, code length, constant count, and per-output native secret before it releases any VM word
- adds an encrypted `meta/W*.bin` watermark/seal resource in strong non-Minecraft modes; the injected runtime verifies this resource on first `_o()` execution and rejects jars where the seal has been removed or modified
- binds that seal to the injected runtime class bytes; patching the runtime, disabling `_g(...)`, changing native-load behavior, or removing the runtime class makes the first security check fail
- optionally mutates method descriptors by adding unused `int`/`long`/`Object` parameters and rewrites all in-jar direct call sites before name mapping, reference obfuscation, virtualization, string encryption, and number mutation
- adds ZKM/VMP-style exception-flow traps in strong non-Minecraft modes: guarded dead branches plus synthetic `Throwable` catch/rethrow blocks that preserve normal behavior while confusing decompiler control-flow recovery
- automatically skips excluded classes, `@NoProtect` classes/methods, obvious reflection-heavy classes, bootstrap method-handle targets, constructors, `main`, native/abstract/varargs/bridge methods, and public/protected API methods

License-locked output:

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar locked.jar --mode vmp --license-key TEST-LICENSE-001
java "-Dsushuo.license=TEST-LICENSE-001" -jar locked.jar
```

Protection report / change log:

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar protected.jar --mode zkm26 --report-file protected-report.txt
```

The report is written outside the protected jar and includes mode/options,
runtime/native/watermark resource names, protection counters, class mappings,
method mappings, and field mappings for debugging, auditing, and release records.

Strongest mode forces the virtualized code through the embedded native VM and
uses randomized encrypted native/resource names. In this mode the Java runtime is
native-only: it exposes no Java VM interpreter, no Java VM decoder, and no
`reverseMap` helper. Virtualized methods keep only a guarded descriptor stub; the
real VM code, opcode map, and VM constant pool are moved into a per-method
encrypted binary resource. The JNI VM loads that resource at execution time and
reads/decrypts one VM word at the current PC instead of materializing a decoded
Java `int[]`. Resource v3 adds a native-side call-context seal, so copying a
program descriptor/resource and invoking `NativeBridge._n(...)` from the wrong
class or method fails before VM bytecode is released. The packed native library is
also lazy-loaded: protected classes no longer release/load the native payload at
runtime class initialization; `_v(...)` performs the first native release only
when virtualized code is actually entered and then caches the result.

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar output.jar --preset max
```

Minecraft/Fabric/Forge safe maximum keeps loader/mixin metadata stable while
requiring the native VM:

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar output.jar --preset minecraft-max
```

Native VM:

The build helper compiles `target\native\sushuo1337_vm.dll`, packs it into
`target\classes\sushuo1337\sushuoprotect\lib\native\windows-x64\sushuo1337_vm.dll.dat`,
and removes the raw DLL from resources. Protected output jars receive a second
per-output packed resource such as
`sushuo1337/sushuoprotect/lib/native/windows-x64/N....bin`; raw `MZ` DLL bytes
are not shipped as a jar resource.

The native VM also performs native-side debugger checks at `JNI_OnLoad` and at
each `_n(...)` entry using Windows debugger APIs. If native debugging is detected,
native-required protected jars fail before executing VM payloads. For regression
tests the same path can be forced with `SUSHUO_NATIVE_DEBUGGER_PRESENT=1`.

By default the injected runtime tries to load the embedded native VM first, then
falls back to the Java interpreter. Use `--require-native-vm` or `--preset max`
/ `--preset minecraft-max` to disable that fallback.

Windows build helper:

```powershell
.\native\build-windows.ps1
```

Then run protected apps with the native library path. The runtime auto-enables
native mode if the library is present in `java.library.path`; you can force native
success with `-Dsushuo.vm.native.required=true`.

```powershell
java -Djava.library.path=target\native -jar output.jar
```

Current VM scope is intentionally conservative: it virtualizes supported static
and instance methods made from constants, locals, primitive/string constants,
arithmetic/conversions, branches, field access, returns, and supported method
calls. Unsupported methods keep normal bytecode and still get the other
protection passes.
