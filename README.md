# Sushuo Shield

JAR obfuscator/protector with sushuo1337-style defaults:

- `sushuo1337/sushuoprotect/lib/...` class relocation by default
- class/member renaming
- string runtime encryption
- number expression obfuscation
- debug/signature stripping
- manifest and text resource class-name rewrite
- lightweight control-flow guards
- VM virtualization for supported methods
- encrypted embedded JNI native VM payload with Java fallback by default

Build:

```powershell
mvn -DskipTests package
```

Use:

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar output.jar --preset sushuo1337
```

Strongest mode forces the virtualized code through the embedded native VM and
uses randomized encrypted native/resource names. In this mode the Java runtime is
native-only: it exposes no Java VM interpreter, no Java VM decoder, and no
`reverseMap` helper. Virtualized methods keep only a guarded descriptor stub; the
real VM code, opcode map, and VM constant pool are moved into a per-method
encrypted binary resource. The JNI VM loads that resource at execution time and
reads/decrypts one VM word at the current PC instead of materializing a decoded
Java `int[]`.

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
