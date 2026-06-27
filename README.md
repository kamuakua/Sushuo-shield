# Sushuo Shield

JAR obfuscator/protector with sushuo1337-style defaults:

- `sushuo1337/sushuoprotect/lib/...` class relocation by default
- class/member renaming
- string runtime encryption
- number expression obfuscation
- debug/signature stripping
- manifest and text resource class-name rewrite
- lightweight control-flow guards
- VM virtualization for supported static methods
- JNI native VM entry point with Java fallback

Build:

```powershell
mvn -DskipTests package
```

Use:

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar output.jar --preset sushuo1337
```

Compatibility mode for reflection-heavy Minecraft/Fabric/Forge jars:

```powershell
java -jar target\sushuo-shield-1.0.0.jar input.jar output.jar --preset compat
```

Native VM:

The injected runtime tries to load `sushuo1337_vm` first. If the DLL/so/dylib is
not present, it runs the same VM bytecode through a Java interpreter so protected
jars stay runnable.

Windows build helper:

```powershell
.\native\build-windows.ps1
```

Then run protected apps with the native library path. The runtime auto-enables
native mode if the library is present in `java.library.path`; you can force the
attempt with `-Dsushuo.shield.native=true`.

```powershell
java -Djava.library.path=target\native -jar output.jar
```

Current VM scope is intentionally conservative: it virtualizes static methods made
from constants, locals, primitive/string constants, arithmetic/conversions, returns,
and supported static calls. Unsupported methods keep normal bytecode and still get
the other protection passes.
