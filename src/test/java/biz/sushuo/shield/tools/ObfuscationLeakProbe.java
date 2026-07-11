
package biz.sushuo.shield.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Test-scope probe for obfuscated jars. Emits one machine-readable JSON summary to stdout. */
public final class ObfuscationLeakProbe implements Opcodes {
    private static final String SCHEMA = "sushuo-obf-leak-probe/v1";
    private static final String DESC_OBJECT = "()Ljava/lang/Object;";
    private static final String DESC_OBJECT_ARRAY = "()[Ljava/lang/Object;";
    private static final String DESC_VM_ENTRY_ARRAY = "([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String DESC_VM_ENTRY_OBJECT_OBJECT_INT = "(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;";
    private static final String DESC_PROGRAM_FACTORY = "(I)Ljava/lang/Object;";
    /** Legacy alias retained for existing regression script fields. */
    private static final String DESC_VM_ENTRY = DESC_VM_ENTRY_ARRAY;
    private static final String DESC_MH_CONSTANT = "(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;";
    private static final String OWNER_CALLSITE = "java/lang/invoke/CallSite";
    private static final String OWNER_CONSTANT_CALLSITE = "java/lang/invoke/ConstantCallSite";
    private static final String OWNER_METHOD_HANDLE = "java/lang/invoke/MethodHandle";
    private static final String OWNER_METHOD_HANDLES = "java/lang/invoke/MethodHandles";
    private static final String OWNER_METHOD_TYPE = "java/lang/invoke/MethodType";
    private static final String OWNER_LOOKUP = "java/lang/invoke/MethodHandles$Lookup";
    private static final String OWNER_INPUT_STREAM = "java/io/InputStream";
    private static final int SAMPLE = 8;
    private static final int DEPTH = 3;

    public static void main(String[] args) throws Exception {
        Opt opt = Opt.parse(args);
        if (opt.help) {
            System.out.println(Opt.usage());
            return;
        }
        Summary out = new Summary(opt);
        try {
            if (opt.jar == null) throw new IllegalArgumentException("Missing --jar <path>");
            List<CInfo> classes = readJar(opt.jar);
            out.classCount = classes.size();
            out.staticScan = staticScan(classes, opt.detailLimit);
            out.indyScan = indyScan(classes, opt.detailLimit);
            out.callSiteShapes = callSiteShapeScan(classes, out.indyScan, opt.detailLimit);
            out.vmAbiShape = vmAbiShapeScan(classes, opt.detailLimit);
            out.runtimeApiRisk = runtimeApiScan(classes, out.indyScan, opt.detailLimit);
            URLClassLoader loader = null;
            if (opt.invokeMetadata || opt.invokeIndy) loader = newLoader(opt);
            out.metadataReflection = opt.invokeMetadata
                    ? probeMetadata(loader, out.staticScan.metadataCandidates, opt)
                    : InvokeSummary.disabled("metadata invocation disabled");
            out.indyOracle = opt.invokeIndy
                    ? probeIndy(loader, out.indyScan.constantCandidates, opt)
                    : InvokeSummary.disabled("invokedynamic oracle invocation disabled");
            if (loader != null) loader.close();
        } catch (Throwable t) {
            out.ok = false;
            out.errors.add(error(t));
        }
        System.out.println(json(out.map()));
        if (!out.ok && opt.failOnProbeError) System.exit(2);
    }

    private static List<CInfo> readJar(Path jar) throws Exception {
        List<CInfo> list = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (JarEntry e : jf.stream().filter(x -> !x.isDirectory() && x.getName().endsWith(".class")).toList()) {
                byte[] bytes = jf.getInputStream(e).readAllBytes();
                ClassReader cr = new ClassReader(bytes);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                list.add(new CInfo(e.getName(), cn, bytes.length));
            }
        }
        return list;
    }

    private static StaticScan staticScan(List<CInfo> classes, int detailLimit) {
        StaticScan s = new StaticScan();
        for (CInfo ci : classes) {
            for (MethodNode m : ci.node.methods) {
                Type ret = Type.getReturnType(m.desc);
                boolean noArgs = Type.getArgumentTypes(m.desc).length == 0;
                boolean privStat = (m.access & ACC_PRIVATE) != 0 && (m.access & ACC_STATIC) != 0;
                if (DESC_OBJECT_ARRAY.equals(m.desc)) {
                    s.noArgObjectArrayDescriptor++;
                    if (s.noArgObjectArrayExamples.size() < detailLimit) s.noArgObjectArrayExamples.add(methodMap(ci.node.name, m));
                }
                if (DESC_OBJECT.equals(m.desc)) s.noArgObjectDescriptor++;
                if (noArgs && privStat && isObjectOrObjectArray(ret)) {
                    MC c = new MC(ci.node.name, m.name, m.desc, m.access);
                    s.metadataCandidates.add(c);
                    if (DESC_OBJECT_ARRAY.equals(m.desc)) s.privateStaticNoArgObjectArray++;
                    else s.privateStaticNoArgObject++;
                    if (s.metadataExamples.size() < detailLimit) s.metadataExamples.add(c.map());
                }
            }
        }
        return s;
    }

    private static IndyScan indyScan(List<CInfo> classes, int detailLimit) {
        IndyScan s = new IndyScan();
        for (CInfo ci : classes) {
            for (MethodNode m : ci.node.methods) {
                if (m.instructions == null) continue;
                for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
                    if (!(p instanceof InvokeDynamicInsnNode indy)) continue;
                    s.totalSites++;
                    if (indy.bsm != null) {
                        s.sitesWithBootstrap++;
                        s.bootstrapOwners.add(indy.bsm.getOwner());
                        inc(s.bootstrapOwnerSites, indy.bsm.getOwner());
                        inc(s.bootstrapDescriptors, indy.bsm.getDesc());
                        inc(s.bootstrapHandles, handle(indy.bsm));
                        if (!indy.bsm.getOwner().startsWith("java/lang/invoke/") && indy.bsmArgs != null) {
                            for (Object argument : indy.bsmArgs) {
                                if (argument instanceof Handle) {
                                    s.customBootstrapHandleArguments++;
                                }
                            }
                        }
                    } else {
                        s.sitesMissingBootstrap++;
                    }
                    Type mt = Type.getMethodType(indy.desc);
                    String kind = kind(mt.getReturnType());
                    if (kind != null) inc(s.returnSites, kind);
                    if (kind != null && mt.getArgumentTypes().length == 0) {
                        IC c = new IC(ci.node.name, m.name, m.desc, indy);
                        s.constantCandidates.add(c);
                        inc(s.zeroArgConstantReturnSites, kind);
                        if (indy.bsm != null) {
                            s.constantCandidatesWithBootstrap++;
                            inc(s.constantCandidateBootstrapDescriptors, indy.bsm.getDesc());
                            inc(s.constantCandidateBootstrapHandles, handle(indy.bsm));
                            inc(s.constantCandidateBootstrapDescriptorsByKind, kind + " " + indy.bsm.getDesc());
                        }
                        if (s.constantExamples.size() < detailLimit) s.constantExamples.add(c.map(false));
                    }
                }
            }
        }
        return s;
    }

    private static CallSiteShapes callSiteShapeScan(List<CInfo> classes, IndyScan indyScan, int detailLimit) {
        CallSiteShapes s = new CallSiteShapes();
        Set<String> referencedBootstraps = indyScan == null ? Collections.emptySet() : indyScan.bootstrapMethodKeys();
        if (indyScan != null) {
            s.indySites = indyScan.totalSites;
            s.zeroArgConstantCandidates = indyScan.constantCandidates.size();
        }
        for (CInfo ci : classes) {
            for (MethodNode m : ci.node.methods) {
                int methodConstantCallSites = 0;
                int methodMethodHandlesConstant = 0;
                int methodTargetAccess = 0;
                boolean returnsCallSite = returnsCallSite(m.desc);
                boolean referencedByIndy = referencedBootstraps.contains(methodKey(ci.node.name, m.name, m.desc));
                if (returnsCallSite) s.callSiteReturningMethods++;
                if (referencedByIndy) s.indyBootstrapMethods++;
                if (m.instructions != null) {
                    for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
                        if (p instanceof TypeInsnNode ti
                                && ti.getOpcode() == NEW
                                && OWNER_CONSTANT_CALLSITE.equals(ti.desc)) {
                            s.constantCallSiteNew++;
                        }
                        if (!(p instanceof MethodInsnNode mi)) continue;
                        if (OWNER_CONSTANT_CALLSITE.equals(mi.owner) && "<init>".equals(mi.name)) {
                            s.constantCallSiteCtor++;
                            methodConstantCallSites++;
                            inc(s.constantCallSiteConstructorDescriptors, mi.desc);
                            addMethodExample(s.examples, detailLimit, ci.node.name, m, mi);
                        } else if (OWNER_METHOD_HANDLES.equals(mi.owner) && "constant".equals(mi.name)) {
                            s.methodHandlesConstant++;
                            methodMethodHandlesConstant++;
                            inc(s.methodHandlesConstantDescriptors, mi.desc);
                            addMethodExample(s.examples, detailLimit, ci.node.name, m, mi);
                        } else if (OWNER_CALLSITE.equals(mi.owner) && ("dynamicInvoker".equals(mi.name) || "getTarget".equals(mi.name))) {
                            s.callSiteTargetAccess++;
                            methodTargetAccess++;
                            addMethodExample(s.callSiteAccessExamples, detailLimit, ci.node.name, m, mi);
                        }
                    }
                }
                if (methodTargetAccess > 0) s.callSiteTargetAccessMethods++;
                boolean constantStyle = methodConstantCallSites > 0 || methodMethodHandlesConstant > 0;
                if (!constantStyle) continue;
                s.constantStyleMethods++;
                if (methodConstantCallSites > 0 && methodMethodHandlesConstant > 0) {
                    s.constantCallSiteAndMethodHandlesConstantMethods++;
                }
                if (returnsCallSite) s.constantStyleCallSiteReturningMethods++;
                if (referencedByIndy) {
                    s.constantStyleBootstrapMethodsReferencedByIndy++;
                    s.riskyStaticBootstrapShapes++;
                }
                if (s.methodShapeExamples.size() < detailLimit) {
                    Map<String,Object> x = methodMap(ci.node.name, m);
                    x.put("returnsCallSite", returnsCallSite);
                    x.put("referencedByInvokedynamic", referencedByIndy);
                    x.put("constantCallSiteCtor", methodConstantCallSites);
                    x.put("methodHandlesConstant", methodMethodHandlesConstant);
                    x.put("callSiteTargetAccess", methodTargetAccess);
                    s.methodShapeExamples.add(x);
                }
            }
        }
        return s;
    }

    private static VmAbiShape vmAbiShapeScan(List<CInfo> classes, int detailLimit) {
        VmAbiShape s = new VmAbiShape();
        Set<String> programFactories = new LinkedHashSet<>();
        for (CInfo ci : classes) {
            for (MethodNode m : ci.node.methods) {
                if (DESC_PROGRAM_FACTORY.equals(m.desc)) {
                    s.programFactoryDescriptorMethods++;
                    if (isProgramFactoryObjectArrayShape(m)) {
                        programFactories.add(methodKey(ci.node.name, m.name, m.desc));
                        s.programFactoryObjectArrayShapeCandidates++;
                        if ((m.access & (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)) == (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)) {
                            s.privateStaticSyntheticProgramFactories++;
                        }
                        if (s.programFactoryExamples.size() < detailLimit) {
                            Map<String,Object> x = methodMap(ci.node.name, m);
                            x.put("objectArrayNew", opcodeCount(m, ANEWARRAY, "java/lang/Object"));
                            x.put("aastore", opcodeCount(m, AASTORE, null));
                            x.put("areturn", opcodeCount(m, ARETURN, null));
                            s.programFactoryExamples.add(x);
                        }
                    }
                }
            }
        }
        for (CInfo ci : classes) {
            for (MethodNode m : ci.node.methods) {
                if (DESC_VM_ENTRY_ARRAY.equals(m.desc) && isPublicStatic(m.access)) {
                    s.publicStaticMethods++;
                    s.objectArrayPublicStaticMethods++;
                    if (s.publicStaticMethodExamples.size() < detailLimit) {
                        s.publicStaticMethodExamples.add(methodMap(ci.node.name, m));
                    }
                }
                if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(m.desc) && isPublicStatic(m.access)) {
                    s.objectObjectIntPublicStaticMethods++;
                    if (s.objectObjectIntPublicStaticMethodExamples.size() < detailLimit) {
                        s.objectObjectIntPublicStaticMethodExamples.add(methodMap(ci.node.name, m));
                    }
                }
                if (DESC_VM_ENTRY_ARRAY.equals(m.desc) && (m.access & ACC_NATIVE) != 0) {
                    s.nativeBridgeNativeMethods++;
                    s.objectArrayNativeBridgeNativeMethods++;
                    if (ci.node.name.contains("NativeBridge")) {
                        s.namedNativeBridgeNativeMethods++;
                    }
                    if (s.nativeBridgeNativeMethodExamples.size() < detailLimit) {
                        Map<String,Object> x = methodMap(ci.node.name, m);
                        x.put("namedNativeBridgeClass", ci.node.name.contains("NativeBridge"));
                        s.nativeBridgeNativeMethodExamples.add(x);
                    }
                }
                if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(m.desc) && (m.access & ACC_NATIVE) != 0) {
                    s.objectObjectIntNativeMethods++;
                    if (s.objectObjectIntNativeMethodExamples.size() < detailLimit) {
                        s.objectObjectIntNativeMethodExamples.add(methodMap(ci.node.name, m));
                    }
                }
                if (m.instructions == null) continue;
                VmCallsiteMatch callsiteShape = findVmCallsiteShape(m, programFactories);
                if (callsiteShape != null) {
                    s.vmCallsiteShapeCandidates++;
                    if ("objectArray".equals(callsiteShape.entryKind)) s.vmCallsiteObjectArrayEntryCandidates++;
                    else if ("objectObjectInt".equals(callsiteShape.entryKind)) s.vmCallsiteObjectObjectIntEntryCandidates++;
                    inc(s.vmCallsiteEntryOwners, callsiteShape.entryOwner);
                    inc(s.vmCallsiteProgramFactoryTargets, callsiteShape.factoryTarget);
                    if (s.vmCallsiteShapeExamples.size() < detailLimit) {
                        Map<String,Object> x = methodMap(ci.node.name, m);
                        x.put("programFactory", callsiteShape.factoryTarget);
                        x.put("entryKind", callsiteShape.entryKind);
                        x.put("entry", callsiteShape.entryOwner + "." + callsiteShape.entryName + callsiteShape.entryDesc);
                        x.put("orderedWindow", "INVOKESTATIC (I)Object -> ANEWARRAY Object -> INVOKESTATIC VM entry");
                        s.vmCallsiteShapeExamples.add(x);
                    }
                }
                for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
                    if (!(p instanceof MethodInsnNode mi) || mi.getOpcode() != INVOKESTATIC) continue;
                    if (DESC_VM_ENTRY_ARRAY.equals(mi.desc)) {
                        s.invokestaticCallSites++;
                        s.objectArrayInvokestaticCallSites++;
                        inc(s.invokestaticOwners, mi.owner);
                        inc(s.invokestaticTargets, methodKey(mi.owner, mi.name, mi.desc));
                        addMethodExample(s.invokestaticCallSiteExamples, detailLimit, ci.node.name, m, mi);
                    } else if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(mi.desc)) {
                        s.objectObjectIntInvokestaticCallSites++;
                        inc(s.objectObjectIntInvokestaticOwners, mi.owner);
                        inc(s.objectObjectIntInvokestaticTargets, methodKey(mi.owner, mi.name, mi.desc));
                        addMethodExample(s.objectObjectIntInvokestaticCallSiteExamples, detailLimit, ci.node.name, m, mi);
                    } else if (DESC_PROGRAM_FACTORY.equals(mi.desc)) {
                        s.programFactoryCallSites++;
                        String key = methodKey(mi.owner, mi.name, mi.desc);
                        inc(s.programFactoryCallTargets, key);
                        if (programFactories.contains(key)) {
                            s.referencedProgramFactoryCallSites++;
                            s.referencedProgramFactories.add(key);
                            addMethodExample(s.referencedProgramFactoryExamples, detailLimit, ci.node.name, m, mi);
                        }
                    }
                }
            }
        }
        s.referencedProgramFactoryUniqueTargets = s.referencedProgramFactories.size();
        return s;
    }

    private static RuntimeApiRisk runtimeApiScan(List<CInfo> classes, IndyScan indyScan, int detailLimit) {
        RuntimeApiRisk s = new RuntimeApiRisk();
        Set<String> bootstrapOwners = indyScan == null ? Collections.emptySet() : indyScan.bootstrapOwners;
        Map<String,Integer> bootstrapOwnerSites = indyScan == null ? Collections.emptyMap() : indyScan.bootstrapOwnerSites;
        Set<String> runtimeCoreOwners = coreRuntimeOwners(classes);
        if (indyScan != null) {
            s.indySites = indyScan.totalSites;
            s.bootstrapOwnerClasses = bootstrapOwners.size();
            int dominantSites = 0;
            for (Map.Entry<String,Integer> e : bootstrapOwnerSites.entrySet()) {
                if (e.getValue() > dominantSites) {
                    s.dominantBootstrapOwner = e.getKey();
                    dominantSites = e.getValue();
                }
            }
            s.dominantBootstrapOwnerSites = dominantSites;
            s.dominantBootstrapOwnerShare = s.indySites <= 0 ? 0.0d : ((double) dominantSites) / (double) s.indySites;
        }
        for (CInfo ci : classes) {
            boolean bootstrapOwner = bootstrapOwners.contains(ci.node.name);
            boolean runtimeLike = bootstrapOwner
                    || ci.node.name.contains("/sushuoprotect/")
                    || ci.node.name.contains("InjectedRuntime")
                    || ci.node.name.contains("NativeOnlyRuntime")
                    || ci.node.name.contains("NativeBridge");
            int classPublicStatic = 0;
            int classRuntimeApis = 0;
            int classBootstrapSites = bootstrapOwnerSites.getOrDefault(ci.node.name, 0);
            int classVmEntryMethods = 0;
            int classVmEntryCallSites = 0;
            int classProgramFactories = 0;
            int classNativeVmEntries = 0;
            int classCallSiteReturningMethods = 0;
            Map<String,Integer> classCategories = new TreeMap<>();
            for (MethodNode m : ci.node.methods) {
                if (isVmEntryDescriptor(m.desc)) classVmEntryMethods++;
                if (isProgramFactoryObjectArrayShape(m)) classProgramFactories++;
                if ((m.access & ACC_NATIVE) != 0 && isVmEntryDescriptor(m.desc)) classNativeVmEntries++;
                if (returnsCallSite(m.desc)) classCallSiteReturningMethods++;
                boolean publicStatic = isPublicStatic(m.access);
                if (publicStatic) {
                    s.publicStaticMethodsAll++;
                    classPublicStatic++;
                }
                boolean apiName = looksLikeRuntimeApiName(m.name);
                String category = runtimeApiCategory(m);
                boolean runtimeApi = publicStatic && (runtimeLike || apiName || category != null);
                if (runtimeApi) {
                    classRuntimeApis++;
                    s.enumerablePublicStaticRuntimeApis++;
                    if (bootstrapOwner) s.publicStaticMethodsOnBootstrapOwners++;
                    if (runtimeLike) s.publicStaticMethodsOnRuntimeLikeClasses++;
                    if (category == null) category = bootstrapOwner ? "otherPublicStaticOnBootstrapOwner" : "otherPublicStaticRuntimeLike";
                    inc(s.apiCategories, category);
                    inc(s.apiDescriptors, m.desc);
                    inc(classCategories, category);
                    if ("bootstrapCallSite".equals(category)) s.publicStaticBootstrapApis++;
                    if ("noArgMetadataObject".equals(category) || "noArgMetadataObjectArray".equals(category)) {
                        s.publicStaticNoArgMetadataApis++;
                    }
                    if (isRuntimeLeakProneDescriptor(m.desc)) s.publicStaticLeakProneDescriptors++;
                    if (s.publicStaticApiExamples.size() < detailLimit) {
                        Map<String,Object> x = methodMap(ci.node.name, m);
                        x.put("bootstrapOwner", bootstrapOwner);
                        x.put("runtimeLikeClass", runtimeLike);
                        x.put("category", category);
                        s.publicStaticApiExamples.add(x);
                    }
                }
                if (!runtimeLike && !apiName) continue;
                if (looksLikeRuntimeApiName(m.name)) {
                    s.shortRuntimeApiNames++;
                    if (s.apiNameExamples.size() < detailLimit) s.apiNameExamples.add(methodMap(ci.node.name, m));
                }
                if (m.instructions == null) continue;
                for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
                    if (p instanceof MethodInsnNode mi) {
                        if (OWNER_METHOD_HANDLES.equals(mi.owner) && "constant".equals(mi.name)) {
                            s.runtimeMethodHandlesConstant++;
                            addMethodExample(s.examples, detailLimit, ci.node.name, m, mi);
                        } else if (OWNER_CONSTANT_CALLSITE.equals(mi.owner) && "<init>".equals(mi.name)) {
                            s.runtimeConstantCallSiteCtor++;
                            addMethodExample(s.examples, detailLimit, ci.node.name, m, mi);
                        } else if ("java/lang/Class".equals(mi.owner)
                                && ("forName".equals(mi.name) || "getDeclaredMethod".equals(mi.name) || "getDeclaredField".equals(mi.name))) {
                            s.runtimeReflectionLookups++;
                            addMethodExample(s.reflectionExamples, detailLimit, ci.node.name, m, mi);
                        } else if ("java/lang/reflect/Method".equals(mi.owner) && "invoke".equals(mi.name)) {
                            s.runtimeReflectionInvokes++;
                            addMethodExample(s.reflectionExamples, detailLimit, ci.node.name, m, mi);
                        }
                    } else if (p instanceof TypeInsnNode ti && OWNER_CONSTANT_CALLSITE.equals(ti.desc)) {
                        s.constantCallSiteTypeRefs++;
                    }
                    if (p instanceof MethodInsnNode mi && mi.getOpcode() == INVOKESTATIC && isVmEntryDescriptor(mi.desc)) {
                        classVmEntryCallSites++;
                    }
                    if (p instanceof MethodInsnNode mi
                            && mi.getOpcode() == INVOKESTATIC
                            && runtimeCoreOwners.contains(mi.owner)
                            && returnsCallSite(mi.desc)) {
                        s.directRuntimeBootstrapForwardCalls++;
                        inc(s.directRuntimeBootstrapForwardOwners, mi.owner);
                        addMethodExample(s.directRuntimeBootstrapForwardExamples, detailLimit, ci.node.name, m, mi);
                    }
                }
            }
            if (bootstrapOwner) {
                if (classVmEntryMethods > 0) s.bootstrapOwnersWithVmEntryMethods++;
                if (classVmEntryCallSites > 0) s.bootstrapOwnersWithVmEntryCallSites++;
                if (classProgramFactories > 0) s.bootstrapOwnersWithProgramFactories++;
                if (classNativeVmEntries > 0) s.bootstrapOwnersWithNativeVmEntries++;
                boolean central = (classBootstrapSites > 0 || classRuntimeApis > 0 || classCallSiteReturningMethods > 0)
                        && (classVmEntryMethods > 0 || classVmEntryCallSites > 0 || classProgramFactories > 0 || classNativeVmEntries > 0);
                if (central) {
                    s.bootstrapVmCentralityRiskClasses++;
                    s.bootstrapVmCentralityRiskScore += Math.max(1, classBootstrapSites)
                            + classRuntimeApis
                            + (classVmEntryMethods * 5)
                            + (classVmEntryCallSites * 3)
                            + (classProgramFactories * 2)
                            + (classNativeVmEntries * 5);
                    if (s.bootstrapCentralityExamples.size() < detailLimit) {
                        Map<String,Object> x = new LinkedHashMap<>();
                        x.put("class", bin(ci.node.name));
                        x.put("bootstrapSites", classBootstrapSites);
                        x.put("runtimeApis", classRuntimeApis);
                        x.put("vmEntryMethods", classVmEntryMethods);
                        x.put("vmEntryCallSites", classVmEntryCallSites);
                        x.put("programFactories", classProgramFactories);
                        x.put("nativeVmEntries", classNativeVmEntries);
                        x.put("callSiteReturningMethods", classCallSiteReturningMethods);
                        s.bootstrapCentralityExamples.add(x);
                    }
                }
            }
            if (classRuntimeApis > 0) {
                s.runtimeCandidateClasses++;
                if (bootstrapOwner) s.bootstrapOwnerRuntimeClasses++;
                if (s.runtimeClassExamples.size() < detailLimit) {
                    Map<String,Object> x = new LinkedHashMap<>();
                    x.put("class", bin(ci.node.name));
                    x.put("bootstrapOwner", bootstrapOwner);
                    x.put("publicStaticMethods", classPublicStatic);
                    x.put("enumerablePublicStaticRuntimeApis", classRuntimeApis);
                    x.put("categories", classCategories);
                    s.runtimeClassExamples.add(x);
                }
            }
        }
        s.riskyStaticBootstrapShapes = s.runtimeMethodHandlesConstant + s.runtimeConstantCallSiteCtor;
        return s;
    }

    private static Set<String> coreRuntimeOwners(List<CInfo> classes) {
        Set<String> owners = new HashSet<>();
        for (CInfo ci : classes) {
            for (MethodNode method : ci.node.methods) {
                if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(method.desc)
                        && isPublicStatic(method.access)
                        && (method.access & ACC_NATIVE) == 0) {
                    owners.add(ci.node.name);
                }
            }
        }
        return owners;
    }

    private static InvokeSummary probeMetadata(URLClassLoader loader, List<MC> candidates, Opt opt) {
        InvokeSummary s = InvokeSummary.enabled(candidates.size());
        for (MC c : candidates) {
            String rk = DESC_OBJECT_ARRAY.equals(c.desc) ? "Object[]" : "Object";
            inc(s.attemptedByKind, rk);
            try {
                Object value = timeout(opt.invokeTimeoutMs, () -> {
                    Class<?> owner = Class.forName(bin(c.owner), false, loader);
                    Method m = owner.getDeclaredMethod(c.name);
                    m.setAccessible(true);
                    return m.invoke(null);
                });
                s.invoked++;
                if (value == null) s.nullResults++;
                else {
                    s.leaked++;
                    inc(s.leakedByKind, rk);
                    if (value.getClass().isArray() && Array.getLength(value) > 0) s.nonEmptyArrayLeaks++;
                    if (s.examples.size() < opt.detailLimit) {
                        Map<String,Object> x = c.map();
                        x.put("value", valueInfo(value, opt.valueStringLimit));
                        s.examples.add(x);
                    }
                }
            } catch (Throwable t) {
                s.failed++;
                inc(s.failedByKind, rk);
                if (s.failures.size() < opt.detailLimit) {
                    Map<String,Object> x = c.map();
                    x.put("error", error(unwrap(t)));
                    s.failures.add(x);
                }
            }
        }
        return s;
    }

    private static InvokeSummary probeIndy(URLClassLoader loader, List<IC> candidates, Opt opt) {
        InvokeSummary s = InvokeSummary.enabled(candidates.size());
        for (IC c : candidates) {
            String k = kind(Type.getReturnType(c.indy.desc));
            if (k == null) continue;
            inc(s.attemptedByKind, k);
            try {
                Object value = timeout(opt.invokeTimeoutMs, () -> invokeIndy(loader, c));
                s.invoked++;
                if (value == null) s.nullResults++;
                else if (matches(value, k)) {
                    s.leaked++;
                    inc(s.leakedByKind, k);
                    if (s.examples.size() < opt.detailLimit) {
                        Map<String,Object> x = c.map(true);
                        x.put("kind", k);
                        x.put("value", valueInfo(value, opt.valueStringLimit));
                        s.examples.add(x);
                    }
                } else {
                    s.mismatchedResults++;
                    if (s.failures.size() < opt.detailLimit) {
                        Map<String,Object> x = c.map(true);
                        x.put("kind", k);
                        x.put("error", Map.of("type", "ResultTypeMismatch", "message", "got " + value.getClass().getName()));
                        x.put("value", valueInfo(value, opt.valueStringLimit));
                        s.failures.add(x);
                    }
                }
            } catch (Throwable t) {
                s.failed++;
                inc(s.failedByKind, k);
                if (s.failures.size() < opt.detailLimit) {
                    Map<String,Object> x = c.map(true);
                    x.put("kind", k);
                    x.put("error", error(unwrap(t)));
                    s.failures.add(x);
                }
            }
        }
        return s;
    }

    private static Object invokeIndy(URLClassLoader loader, IC c) throws Throwable {
        InvokeDynamicInsnNode indy = c.indy;
        if (indy.bsm == null) throw new IllegalStateException("missing bootstrap handle");
        if (indy.bsm.getTag() != H_INVOKESTATIC) throw new UnsupportedOperationException("bootstrap tag " + indy.bsm.getTag());
        Class<?> caller = Class.forName(bin(c.owner), false, loader);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(caller, MethodHandles.lookup());
        MethodType indyType = MethodType.fromMethodDescriptorString(indy.desc, loader);
        if (indyType.parameterCount() != 0) throw new UnsupportedOperationException("non-zero-arg indy " + indy.desc);
        CallSite cs = callBootstrap(loader, lookup, indy);
        if (cs == null) return null;
        MethodHandle target = cs.getTarget();
        if (target.type().parameterCount() != 0) throw new UnsupportedOperationException("target type " + target.type());
        return target.invokeWithArguments();
    }

    private static CallSite callBootstrap(ClassLoader loader, MethodHandles.Lookup callerLookup, InvokeDynamicInsnNode indy) throws Throwable {
        Handle h = indy.bsm;
        Class<?> owner = Class.forName(bin(h.getOwner()), false, loader);
        Type[] ats = Type.getArgumentTypes(h.getDesc());
        Class<?>[] pts = new Class<?>[ats.length];
        for (int i = 0; i < ats.length; i++) pts[i] = classFor(ats[i], loader);
        Method m;
        try { m = owner.getDeclaredMethod(h.getName(), pts); } catch (NoSuchMethodException e) { m = owner.getMethod(h.getName(), pts); }
        if (!Modifier.isStatic(m.getModifiers())) throw new UnsupportedOperationException("bootstrap is not static");
        m.setAccessible(true);
        Object[] args = bootstrapArgs(loader, callerLookup, indy, m.getParameterTypes(), m.isVarArgs());
        Object r = m.invoke(null, args);
        if (r == null) return null;
        if (!(r instanceof CallSite cs)) throw new ClassCastException("bootstrap returned " + r.getClass().getName());
        return cs;
    }

    private static Object[] bootstrapArgs(ClassLoader loader, MethodHandles.Lookup lookup, InvokeDynamicInsnNode indy,
                                          Class<?>[] pts, boolean varArgs) throws Throwable {
        Object[] raw = indy.bsmArgs == null ? new Object[0] : indy.bsmArgs;
        if (pts.length < 3) throw new IllegalArgumentException("bootstrap desc has <3 params: " + indy.bsm.getDesc());
        Object[] out = new Object[pts.length];
        out[0] = lookup;
        out[1] = indy.name;
        out[2] = MethodType.fromMethodDescriptorString(indy.desc, loader);
        if (pts.length == 3 + raw.length) {
            for (int i = 0; i < raw.length; i++) out[3 + i] = convert(raw[i], pts[3 + i], loader);
            return out;
        }
        if (pts.length >= 4 && pts[pts.length - 1].isArray()) {
            int fixed = pts.length - 4;
            if (raw.length < fixed) throw new IllegalArgumentException("not enough bootstrap args");
            for (int i = 0; i < fixed; i++) out[3 + i] = convert(raw[i], pts[3 + i], loader);
            Class<?> comp = pts[pts.length - 1].getComponentType();
            int packedCount = varArgs ? raw.length - fixed : Math.max(0, raw.length - fixed);
            Object packed = Array.newInstance(comp, packedCount);
            for (int i = 0; i < packedCount; i++) Array.set(packed, i, convert(raw[fixed + i], comp, loader));
            out[pts.length - 1] = packed;
            return out;
        }
        throw new IllegalArgumentException("bootstrap parameter count mismatch: " + indy.bsm.getDesc() + " args=" + raw.length);
    }

    private static Object convert(Object raw, Class<?> expected, ClassLoader loader) throws Throwable {
        if (raw == null) return null;
        Class<?> boxed = box(expected);
        if (boxed.isInstance(raw)) return raw;
        if (raw instanceof Number n) {
            if (boxed == Integer.class) return n.intValue();
            if (boxed == Long.class) return n.longValue();
            if (boxed == Float.class) return n.floatValue();
            if (boxed == Double.class) return n.doubleValue();
            if (boxed == Short.class) return n.shortValue();
            if (boxed == Byte.class) return n.byteValue();
        }
        if (raw instanceof String s) {
            if (expected == String.class || expected == Object.class) return s;
            if (expected == char.class || expected == Character.class) return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (raw instanceof Type t) {
            if (t.getSort() == Type.METHOD) {
                MethodType mt = MethodType.fromMethodDescriptorString(t.getDescriptor(), loader);
                if (expected == MethodType.class || expected == Object.class) return mt;
            } else {
                Class<?> c = classFor(t, loader);
                if (expected == Class.class || expected == Object.class) return c;
                if (expected == String.class) return t.getDescriptor();
            }
        }
        if (raw instanceof Handle h && (expected == MethodHandle.class || expected == Object.class)) return resolveHandle(h, loader);
        if (expected == Object.class) return raw;
        throw new ClassCastException("cannot convert " + raw.getClass().getName() + " to " + expected.getName());
    }

    private static MethodHandle resolveHandle(Handle h, ClassLoader loader) throws Throwable {
        Class<?> owner = Class.forName(bin(h.getOwner()), false, loader);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
        return switch (h.getTag()) {
            case H_GETFIELD -> lookup.findGetter(owner, h.getName(), classFor(Type.getType(h.getDesc()), loader));
            case H_GETSTATIC -> lookup.findStaticGetter(owner, h.getName(), classFor(Type.getType(h.getDesc()), loader));
            case H_PUTFIELD -> lookup.findSetter(owner, h.getName(), classFor(Type.getType(h.getDesc()), loader));
            case H_PUTSTATIC -> lookup.findStaticSetter(owner, h.getName(), classFor(Type.getType(h.getDesc()), loader));
            case H_INVOKEVIRTUAL, H_INVOKEINTERFACE -> lookup.findVirtual(owner, h.getName(), MethodType.fromMethodDescriptorString(h.getDesc(), loader));
            case H_INVOKESTATIC -> lookup.findStatic(owner, h.getName(), MethodType.fromMethodDescriptorString(h.getDesc(), loader));
            case H_INVOKESPECIAL -> lookup.findSpecial(owner, h.getName(), MethodType.fromMethodDescriptorString(h.getDesc(), loader), owner);
            case H_NEWINVOKESPECIAL -> lookup.findConstructor(owner, MethodType.fromMethodDescriptorString(h.getDesc(), loader));
            default -> throw new UnsupportedOperationException("handle tag " + h.getTag());
        };
    }

    private static URLClassLoader newLoader(Opt opt) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(opt.jar.toUri().toURL());
        for (Path p : opt.classpath) urls.add(p.toUri().toURL());
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private static Class<?> classFor(Type t, ClassLoader loader) throws ClassNotFoundException {
        return switch (t.getSort()) {
            case Type.VOID -> void.class;
            case Type.BOOLEAN -> boolean.class;
            case Type.CHAR -> char.class;
            case Type.BYTE -> byte.class;
            case Type.SHORT -> short.class;
            case Type.INT -> int.class;
            case Type.FLOAT -> float.class;
            case Type.LONG -> long.class;
            case Type.DOUBLE -> double.class;
            case Type.ARRAY -> Class.forName(t.getDescriptor().replace('/', '.'), false, loader);
            case Type.OBJECT -> Class.forName(t.getClassName(), false, loader);
            default -> throw new ClassNotFoundException("bad type " + t);
        };
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == boolean.class) return Boolean.class;
        if (c == char.class) return Character.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        return Void.class;
    }

    private static boolean isObjectOrObjectArray(Type r) {
        return (r.getSort() == Type.OBJECT && "java/lang/Object".equals(r.getInternalName()))
                || (r.getSort() == Type.ARRAY && "[Ljava/lang/Object;".equals(r.getDescriptor()));
    }

    private static String kind(Type r) {
        return switch (r.getSort()) {
            case Type.OBJECT -> "java/lang/String".equals(r.getInternalName()) ? "String" : null;
            case Type.INT -> "int";
            case Type.LONG -> "long";
            case Type.FLOAT -> "float";
            case Type.DOUBLE -> "double";
            default -> null;
        };
    }

    private static boolean matches(Object v, String k) {
        return switch (k) {
            case "String" -> v instanceof String;
            case "int" -> v instanceof Integer;
            case "long" -> v instanceof Long;
            case "float" -> v instanceof Float;
            case "double" -> v instanceof Double;
            default -> false;
        };
    }

    private static <T> T timeout(long ms, Throwing<T> op) throws Throwable {
        if (ms <= 0) return op.get();
        Holder<T> h = new Holder<>();
        CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread(() -> { try { h.value = op.get(); } catch (Throwable e) { h.error = e; } finally { latch.countDown(); } }, "obf-leak-probe-invoke");
        t.setDaemon(true);
        t.start();
        if (!latch.await(ms, TimeUnit.MILLISECONDS)) { t.interrupt(); throw new TimeoutException("invocation timed out after " + ms + " ms"); }
        if (h.error != null) throw h.error;
        return h.value;
    }


    private static Map<String,Object> valueInfo(Object v, int strLimit) {
        return valueInfo(v, strLimit, 0, new IdentityHashMap<>());
    }

    private static Map<String,Object> valueInfo(Object v, int strLimit, int depth, IdentityHashMap<Object,Boolean> seen) {
        Map<String,Object> m = new LinkedHashMap<>();
        if (v == null) { m.put("type", "null"); m.put("value", null); return m; }
        Class<?> c = v.getClass();
        m.put("type", c.getName());
        if (c.isArray()) {
            int n = Array.getLength(v);
            m.put("arrayLength", n);
            List<Object> sample = new ArrayList<>();
            if (depth < DEPTH) {
                for (int i = 0; i < Math.min(n, SAMPLE); i++) sample.add(shortValue(Array.get(v, i), strLimit, depth + 1, seen));
            }
            m.put("sample", sample);
            m.put("sampleTruncated", n > sample.size());
            return m;
        }
        if (track(v)) {
            if (seen.containsKey(v)) { m.put("cycle", true); return m; }
            seen.put(v, Boolean.TRUE);
        }
        if (v instanceof CharSequence s) {
            String x = s.toString();
            m.put("length", x.length());
            m.put("value", trunc(x, strLimit));
            m.put("truncated", x.length() > Math.max(0, strLimit));
        } else if (v instanceof Character ch) {
            m.put("value", ch.toString());
            m.put("codePoint", (int) ch.charValue());
        } else if (v instanceof Float f) {
            m.put("value", Float.isFinite(f) ? f : f.toString());
            m.put("rawBits", String.format(Locale.ROOT, "0x%08x", Float.floatToRawIntBits(f)));
        } else if (v instanceof Double d) {
            m.put("value", Double.isFinite(d) ? d : d.toString());
            m.put("rawBits", String.format(Locale.ROOT, "0x%016x", Double.doubleToRawLongBits(d)));
        } else if (v instanceof Number || v instanceof Boolean) {
            m.put("value", v);
        } else if (v instanceof Class<?> cl) {
            m.put("name", cl.getName());
        } else {
            String x = String.valueOf(v);
            m.put("repr", trunc(x, strLimit));
            m.put("truncated", x.length() > Math.max(0, strLimit));
        }
        return m;
    }

    private static Object shortValue(Object v, int strLimit, int depth, IdentityHashMap<Object,Boolean> seen) {
        if (v == null || v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte || v instanceof Boolean) return v;
        if (v instanceof Float f) return Map.of("type", "float", "value", Float.isFinite(f) ? f : f.toString(), "rawBits", String.format(Locale.ROOT, "0x%08x", Float.floatToRawIntBits(f)));
        if (v instanceof Double d) return Map.of("type", "double", "value", Double.isFinite(d) ? d : d.toString(), "rawBits", String.format(Locale.ROOT, "0x%016x", Double.doubleToRawLongBits(d)));
        if (v instanceof CharSequence s) return Map.of("type", v.getClass().getName(), "length", s.length(), "value", trunc(s.toString(), strLimit), "truncated", s.length() > Math.max(0, strLimit));
        if (depth > DEPTH) return Map.of("type", v.getClass().getName(), "repr", trunc(String.valueOf(v), strLimit), "depthTruncated", true);
        return valueInfo(v, strLimit, depth, seen);
    }

    private static boolean track(Object v) {
        return !(v instanceof String) && !(v instanceof Number) && !(v instanceof Boolean) && !(v instanceof Character) && !(v instanceof Enum<?>);
    }

    private static String trunc(String x, int limit) {
        int n = Math.max(0, limit);
        return x.length() <= n ? x : x.substring(0, n);
    }

    private static void inc(Map<String,Integer> m, String k) { m.put(k, m.getOrDefault(k, 0) + 1); }
    private static String bin(String internal) { return internal.replace('/', '.'); }

    private static Throwable unwrap(Throwable t) {
        while (t instanceof InvocationTargetException ite && ite.getTargetException() != null) t = ite.getTargetException();
        return t;
    }

    private static Map<String,Object> error(Throwable t) {
        t = unwrap(t);
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("type", t.getClass().getName());
        m.put("message", t.getMessage() == null ? "" : t.getMessage());
        return m;
    }

    private static Map<String,Object> methodMap(String owner, MethodNode m) {
        Map<String,Object> x = new LinkedHashMap<>();
        x.put("class", bin(owner));
        x.put("method", m.name);
        x.put("desc", m.desc);
        x.put("access", access(m.access));
        return x;
    }

    private static void addMethodExample(List<Object> examples, int detailLimit, String owner, MethodNode m, MethodInsnNode mi) {
        if (examples.size() >= detailLimit) return;
        Map<String,Object> x = methodMap(owner, m);
        x.put("callOwner", mi.owner);
        x.put("callName", mi.name);
        x.put("callDesc", mi.desc);
        x.put("opcode", mi.getOpcode());
        examples.add(x);
    }

    private static boolean looksLikeRuntimeApiName(String name) {
        if (name == null) return false;
        return switch (name) {
            case "_cs", "_ci", "_cl", "_cf", "_cd",
                    "_rcs", "_rci", "_rcl", "_rcf", "_rcd",
                    "_rs", "_ri", "_rl", "_rf", "_rd",
                    "_v", "_vx", "_nx", "_j", "_n", "_ki", "_kl",
                    "_d", "_o", "_gh", "_rm", "_rv" -> true;
            default -> name.startsWith("_vp$") || name.startsWith("_rc");
        };
    }

    private static String methodKey(String owner, String name, String desc) {
        return owner + "." + name + desc;
    }

    private static boolean isVmEntryDescriptor(String desc) {
        return DESC_VM_ENTRY_ARRAY.equals(desc) || DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(desc);
    }

    private static String vmEntryKind(String desc) {
        if (DESC_VM_ENTRY_ARRAY.equals(desc)) return "objectArray";
        if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(desc)) return "objectObjectInt";
        return null;
    }

    private static boolean isProgramFactoryObjectArrayShape(MethodNode m) {
        if (!DESC_PROGRAM_FACTORY.equals(m.desc) || m.instructions == null) return false;
        if ((m.access & ACC_STATIC) == 0) return false;
        return opcodeCount(m, ANEWARRAY, "java/lang/Object") > 0
                && opcodeCount(m, AASTORE, null) >= 4
                && opcodeCount(m, ARETURN, null) > 0;
    }

    private static int opcodeCount(MethodNode m, int opcode, String typeDesc) {
        if (m.instructions == null) return 0;
        int n = 0;
        for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
            if (p.getOpcode() != opcode) continue;
            if (typeDesc != null) {
                if (!(p instanceof TypeInsnNode ti) || !typeDesc.equals(ti.desc)) continue;
            }
            n++;
        }
        return n;
    }

    private static VmCallsiteMatch findVmCallsiteShape(MethodNode m, Set<String> knownProgramFactories) {
        if (m.instructions == null) return null;
        String factoryTarget = null;
        boolean sawObjectArgsArray = false;
        for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
            if (p instanceof MethodInsnNode mi && mi.getOpcode() == INVOKESTATIC) {
                String key = methodKey(mi.owner, mi.name, mi.desc);
                if (DESC_PROGRAM_FACTORY.equals(mi.desc)
                        && (knownProgramFactories.isEmpty() || knownProgramFactories.contains(key))) {
                    factoryTarget = key;
                    sawObjectArgsArray = false;
                    continue;
                }
                if (factoryTarget != null && sawObjectArgsArray && isVmEntryDescriptor(mi.desc)) {
                    return new VmCallsiteMatch(factoryTarget, vmEntryKind(mi.desc), mi.owner, mi.name, mi.desc);
                }
            } else if (factoryTarget != null
                    && p instanceof TypeInsnNode ti
                    && ti.getOpcode() == ANEWARRAY
                    && "java/lang/Object".equals(ti.desc)) {
                sawObjectArgsArray = true;
            }
        }
        return null;
    }

    private static boolean isPublicStatic(int access) {
        return (access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC);
    }

    private static boolean returnsCallSite(String desc) {
        Type r = Type.getReturnType(desc);
        return isObjectType(r, OWNER_CALLSITE) || isObjectType(r, OWNER_CONSTANT_CALLSITE);
    }

    private static boolean isObjectType(Type t, String internalName) {
        return t.getSort() == Type.OBJECT && internalName.equals(t.getInternalName());
    }

    private static boolean isBootstrapDescriptor(String desc) {
        if (!returnsCallSite(desc)) return false;
        Type[] args = Type.getArgumentTypes(desc);
        return args.length >= 3
                && isObjectType(args[0], OWNER_LOOKUP)
                && isObjectType(args[1], "java/lang/String")
                && isObjectType(args[2], OWNER_METHOD_TYPE);
    }

    private static String runtimeApiCategory(MethodNode m) {
        String desc = m.desc;
        Type ret = Type.getReturnType(desc);
        Type[] args = Type.getArgumentTypes(desc);
        if (isBootstrapDescriptor(desc)) return "bootstrapCallSite";
        if (DESC_VM_ENTRY_ARRAY.equals(desc)) return "vmEntryObjectArray";
        if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(desc)) return "vmEntryObjectObjectInt";
        if (DESC_PROGRAM_FACTORY.equals(desc) && isProgramFactoryObjectArrayShape(m)) return "vmProgramFactoryObjectArray";
        if (DESC_OBJECT.equals(desc)) return "noArgMetadataObject";
        if (DESC_OBJECT_ARRAY.equals(desc)) return "noArgMetadataObjectArray";
        if (isObjectType(ret, OWNER_INPUT_STREAM)) return "resourceInputStream";
        if (ret.getSort() == Type.ARRAY && "[Ljava/lang/String;".equals(ret.getDescriptor())) return "metadataStringArray";
        if (isObjectType(ret, "java/lang/Object") && desc.contains("Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;")) return "reflectiveDispatch";
        if (isObjectType(ret, "java/lang/String")) {
            if ("(Ljava/lang/String;)Ljava/lang/String;".equals(desc)) return "metadataStringDecoder";
            if (args.length > 0 && isObjectType(args[0], "java/lang/String") && desc.contains("I")) return "stringDecryptor";
            if (desc.contains("[Ljava/lang/Object;")) return "stringConcatRecipe";
            return looksLikeRuntimeApiName(m.name) ? "shortNameStringRuntimeApi" : null;
        }
        if (ret.getSort() >= Type.BOOLEAN && ret.getSort() <= Type.DOUBLE && looksLikeRuntimeApiName(m.name)) {
            return "shortNamePrimitiveRuntimeApi";
        }
        if (returnsCallSite(desc)) return "callSiteReturn";
        if (desc.contains("Ljava/lang/invoke/")) return "javaLangInvokeApi";
        if (looksLikeRuntimeApiName(m.name)) return "shortNameRuntimeApi";
        return null;
    }

    private static boolean isRuntimeLeakProneDescriptor(String desc) {
        Type ret = Type.getReturnType(desc);
        return returnsCallSite(desc)
                || DESC_OBJECT.equals(desc)
                || DESC_OBJECT_ARRAY.equals(desc)
                || isVmEntryDescriptor(desc)
                || DESC_PROGRAM_FACTORY.equals(desc)
                || isObjectType(ret, "java/lang/Object")
                || isObjectType(ret, "java/lang/String")
                || isObjectType(ret, OWNER_INPUT_STREAM)
                || desc.contains("Ljava/lang/invoke/")
                || desc.contains("[Ljava/lang/Object;");
    }

    private static Map<String,Object> descriptorStability(int siteCount, Map<String,Integer> descriptors,
                                                          Map<String,Integer> handles) {
        Map<String,Object> m = new LinkedHashMap<>();
        String dominant = null;
        int dominantSites = 0;
        for (Map.Entry<String,Integer> e : descriptors.entrySet()) {
            if (e.getValue() > dominantSites) {
                dominant = e.getKey();
                dominantSites = e.getValue();
            }
        }
        m.put("siteCount", siteCount);
        m.put("distinctDescriptors", descriptors.size());
        m.put("singleDescriptor", siteCount > 0 && descriptors.size() == 1);
        m.put("dominantDescriptor", dominant);
        m.put("dominantDescriptorSites", dominantSites);
        m.put("dominantDescriptorShare", siteCount <= 0 ? 0.0d : ((double) dominantSites) / (double) siteCount);
        m.put("distinctBootstrapHandles", handles.size());
        m.put("singleBootstrapHandle", siteCount > 0 && handles.size() == 1);
        m.put("descriptorCounts", descriptors);
        return m;
    }

    private static List<String> access(int a) {
        List<String> f = new ArrayList<>();
        if ((a & ACC_PUBLIC) != 0) f.add("public");
        if ((a & ACC_PRIVATE) != 0) f.add("private");
        if ((a & ACC_PROTECTED) != 0) f.add("protected");
        if ((a & ACC_STATIC) != 0) f.add("static");
        if ((a & ACC_FINAL) != 0) f.add("final");
        if ((a & ACC_NATIVE) != 0) f.add("native");
        if ((a & ACC_SYNTHETIC) != 0) f.add("synthetic");
        if ((a & ACC_BRIDGE) != 0) f.add("bridge");
        return f;
    }

    private static String handle(Handle h) { return h.getOwner() + "." + h.getName() + h.getDesc() + "#tag" + h.getTag(); }

    private static Object bsmArg(Object a) {
        if (a == null || a instanceof String || a instanceof Number || a instanceof Boolean) return a;
        if (a instanceof Type t) return Map.of("asmType", t.toString(), "sort", t.getSort());
        if (a instanceof Handle h) return handle(h);
        return String.valueOf(a);
    }

    private static String json(Object v) { StringBuilder b = new StringBuilder(4096); addJson(b, v); return b.toString(); }

    private static void addJson(StringBuilder b, Object v) {
        if (v == null) b.append("null");
        else if (v instanceof String s) q(b, s);
        else if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) b.append(v);
        else if (v instanceof Float f) { if (Float.isFinite(f)) b.append(f); else q(b, f.toString()); }
        else if (v instanceof Double d) { if (Double.isFinite(d)) b.append(d); else q(b, d.toString()); }
        else if (v instanceof Number n) b.append(n);
        else if (v instanceof Map<?,?> m) {
            b.append('{'); boolean first = true;
            for (Map.Entry<?,?> e : m.entrySet()) { if (!first) b.append(','); first = false; q(b, String.valueOf(e.getKey())); b.append(':'); addJson(b, e.getValue()); }
            b.append('}');
        } else if (v instanceof Iterable<?> it) {
            b.append('['); boolean first = true;
            for (Object x : it) { if (!first) b.append(','); first = false; addJson(b, x); }
            b.append(']');
        } else if (v.getClass().isArray()) {
            b.append('['); int n = Array.getLength(v);
            for (int i = 0; i < n; i++) { if (i > 0) b.append(','); addJson(b, Array.get(v, i)); }
            b.append(']');
        } else q(b, String.valueOf(v));
    }

    private static void q(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20 || Character.isSurrogate(c)) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); else b.append(c); }
            }
        }
        b.append('"');
    }

    @FunctionalInterface interface Throwing<T> { T get() throws Throwable; }
    private static final class Holder<T> { T value; Throwable error; }
    private record CInfo(String entry, ClassNode node, int bytes) {}
    private record VmCallsiteMatch(String factoryTarget, String entryKind, String entryOwner, String entryName,
                                   String entryDesc) {}

    private static final class MC {
        final String owner, name, desc; final int access;
        MC(String owner, String name, String desc, int access) { this.owner = owner; this.name = name; this.desc = desc; this.access = access; }
        Map<String,Object> map() { Map<String,Object> m = new LinkedHashMap<>(); m.put("class", bin(owner)); m.put("method", name); m.put("desc", desc); m.put("access", access(access)); return m; }
    }

    private static final class IC {
        final String owner, method, methodDesc; final InvokeDynamicInsnNode indy;
        IC(String owner, String method, String methodDesc, InvokeDynamicInsnNode indy) { this.owner = owner; this.method = method; this.methodDesc = methodDesc; this.indy = indy; }
        Map<String,Object> map(boolean args) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("class", bin(owner)); m.put("method", method); m.put("methodDesc", methodDesc);
            m.put("indyName", indy.name); m.put("indyDesc", indy.desc); m.put("returnKind", kind(Type.getReturnType(indy.desc)));
            if (indy.bsm != null) m.put("bootstrap", handle(indy.bsm));
            m.put("bootstrapArgCount", indy.bsmArgs == null ? 0 : indy.bsmArgs.length);
            if (args && indy.bsmArgs != null) { List<Object> xs = new ArrayList<>(); for (Object a : indy.bsmArgs) xs.add(bsmArg(a)); m.put("bootstrapArgs", xs); }
            return m;
        }
    }

    private static final class StaticScan {
        int noArgObjectArrayDescriptor, noArgObjectDescriptor, privateStaticNoArgObject, privateStaticNoArgObjectArray;
        final List<MC> metadataCandidates = new ArrayList<>();
        final List<Object> noArgObjectArrayExamples = new ArrayList<>(), metadataExamples = new ArrayList<>();
        Map<String,Object> map() { Map<String,Object> m = new LinkedHashMap<>(); m.put("noArgObjectArrayDescriptor", noArgObjectArrayDescriptor); m.put("noArgObjectDescriptor", noArgObjectDescriptor); m.put("privateStaticNoArgObject", privateStaticNoArgObject); m.put("privateStaticNoArgObjectArray", privateStaticNoArgObjectArray); m.put("privateStaticNoArgObjectOrObjectArrayCandidates", metadataCandidates.size()); m.put("noArgObjectArrayDescriptorExamples", noArgObjectArrayExamples); m.put("metadataCandidateExamples", metadataExamples); return m; }
    }

    private static final class IndyScan {
        int totalSites, sitesWithBootstrap, sitesMissingBootstrap, constantCandidatesWithBootstrap,
                customBootstrapHandleArguments;
        final Set<String> bootstrapOwners = new LinkedHashSet<>();
        final Map<String,Integer> returnSites = kinds(), zeroArgConstantReturnSites = kinds(),
                bootstrapOwnerSites = new TreeMap<>(),
                bootstrapDescriptors = new TreeMap<>(), bootstrapHandles = new TreeMap<>(),
                constantCandidateBootstrapDescriptors = new TreeMap<>(),
                constantCandidateBootstrapHandles = new TreeMap<>(),
                constantCandidateBootstrapDescriptorsByKind = new TreeMap<>();
        final List<IC> constantCandidates = new ArrayList<>(); final List<Object> constantExamples = new ArrayList<>();
        Set<String> bootstrapMethodKeys() {
            Set<String> out = new LinkedHashSet<>();
            for (String h : bootstrapHandles.keySet()) {
                int hash = h.lastIndexOf("#tag");
                out.add(hash >= 0 ? h.substring(0, hash) : h);
            }
            return out;
        }
        Map<String,Object> map() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("totalSites", totalSites);
            m.put("sitesWithBootstrap", sitesWithBootstrap);
            m.put("sitesMissingBootstrap", sitesMissingBootstrap);
            m.put("bootstrapOwners", bootstrapOwners);
            m.put("uniqueBootstrapOwners", bootstrapOwners.size());
            m.put("bootstrapOwnerSites", bootstrapOwnerSites);
            m.put("bootstrapDescriptors", bootstrapDescriptors);
            m.put("bootstrapHandles", bootstrapHandles);
            m.put("customBootstrapHandleArguments", customBootstrapHandleArguments);
            m.put("bootstrapDescriptorStability", descriptorStability(sitesWithBootstrap, bootstrapDescriptors, bootstrapHandles));
            m.put("returnSites", returnSites);
            m.put("zeroArgConstantReturnSites", zeroArgConstantReturnSites);
            m.put("zeroArgConstantCandidates", constantCandidates.size());
            m.put("constantCandidatesWithBootstrap", constantCandidatesWithBootstrap);
            m.put("constantCandidateBootstrapDescriptors", constantCandidateBootstrapDescriptors);
            m.put("constantCandidateBootstrapHandles", constantCandidateBootstrapHandles);
            m.put("constantCandidateBootstrapDescriptorsByKind", constantCandidateBootstrapDescriptorsByKind);
            m.put("constantCandidateBootstrapDescriptorStability",
                    descriptorStability(constantCandidatesWithBootstrap, constantCandidateBootstrapDescriptors,
                            constantCandidateBootstrapHandles));
            m.put("constantCandidateExamples", constantExamples);
            return m;
        }
    }

    private static final class CallSiteShapes {
        int indySites, zeroArgConstantCandidates, constantCallSiteNew, constantCallSiteCtor, methodHandlesConstant,
                callSiteTargetAccess, callSiteTargetAccessMethods, callSiteReturningMethods, indyBootstrapMethods,
                constantStyleMethods, constantCallSiteAndMethodHandlesConstantMethods,
                constantStyleCallSiteReturningMethods, constantStyleBootstrapMethodsReferencedByIndy,
                riskyStaticBootstrapShapes;
        final Map<String,Integer> constantCallSiteConstructorDescriptors = new TreeMap<>(),
                methodHandlesConstantDescriptors = new TreeMap<>();
        final List<Object> examples = new ArrayList<>(), callSiteAccessExamples = new ArrayList<>(),
                methodShapeExamples = new ArrayList<>();
        Map<String,Object> map() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("indySites", indySites);
            m.put("zeroArgConstantCandidates", zeroArgConstantCandidates);
            m.put("constantCallSiteNew", constantCallSiteNew);
            m.put("constantCallSiteCtor", constantCallSiteCtor);
            m.put("constantCallSiteConstructorDescriptors", constantCallSiteConstructorDescriptors);
            m.put("methodHandlesConstant", methodHandlesConstant);
            m.put("methodHandlesConstantDescriptors", methodHandlesConstantDescriptors);
            m.put("callSiteTargetAccess", callSiteTargetAccess);
            m.put("callSiteTargetAccessMethods", callSiteTargetAccessMethods);
            m.put("callSiteReturningMethods", callSiteReturningMethods);
            m.put("indyBootstrapMethods", indyBootstrapMethods);
            m.put("constantStyleMethods", constantStyleMethods);
            m.put("constantCallSiteAndMethodHandlesConstantMethods", constantCallSiteAndMethodHandlesConstantMethods);
            m.put("constantStyleCallSiteReturningMethods", constantStyleCallSiteReturningMethods);
            m.put("constantStyleBootstrapMethodsReferencedByIndy", constantStyleBootstrapMethodsReferencedByIndy);
            m.put("riskyStaticBootstrapShapes", riskyStaticBootstrapShapes);
            m.put("examples", examples);
            m.put("methodShapeExamples", methodShapeExamples);
            m.put("callSiteAccessExamples", callSiteAccessExamples);
            return m;
        }
    }

    private static final class VmAbiShape {
        int publicStaticMethods, invokestaticCallSites, nativeBridgeNativeMethods, namedNativeBridgeNativeMethods;
        int objectArrayPublicStaticMethods, objectArrayInvokestaticCallSites, objectArrayNativeBridgeNativeMethods;
        int objectObjectIntPublicStaticMethods, objectObjectIntInvokestaticCallSites, objectObjectIntNativeMethods;
        int programFactoryDescriptorMethods, programFactoryObjectArrayShapeCandidates,
                privateStaticSyntheticProgramFactories, programFactoryCallSites,
                referencedProgramFactoryCallSites, referencedProgramFactoryUniqueTargets;
        int vmCallsiteShapeCandidates, vmCallsiteObjectArrayEntryCandidates,
                vmCallsiteObjectObjectIntEntryCandidates;
        final Set<String> referencedProgramFactories = new LinkedHashSet<>();
        final Map<String,Integer> invokestaticOwners = new TreeMap<>(), invokestaticTargets = new TreeMap<>(),
                objectObjectIntInvokestaticOwners = new TreeMap<>(),
                objectObjectIntInvokestaticTargets = new TreeMap<>(),
                programFactoryCallTargets = new TreeMap<>(),
                vmCallsiteEntryOwners = new TreeMap<>(),
                vmCallsiteProgramFactoryTargets = new TreeMap<>();
        final List<Object> publicStaticMethodExamples = new ArrayList<>(),
                invokestaticCallSiteExamples = new ArrayList<>(),
                nativeBridgeNativeMethodExamples = new ArrayList<>(),
                objectObjectIntPublicStaticMethodExamples = new ArrayList<>(),
                objectObjectIntInvokestaticCallSiteExamples = new ArrayList<>(),
                objectObjectIntNativeMethodExamples = new ArrayList<>(),
                programFactoryExamples = new ArrayList<>(),
                referencedProgramFactoryExamples = new ArrayList<>(),
                vmCallsiteShapeExamples = new ArrayList<>();
        Map<String,Object> map() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("descriptor", DESC_VM_ENTRY_ARRAY);
            m.put("javaShape", "([Object;[Object;)Object");
            m.put("objectObjectIntDescriptor", DESC_VM_ENTRY_OBJECT_OBJECT_INT);
            m.put("objectObjectIntJavaShape", "(Object,Object,int)Object");
            m.put("publicStaticMethods", publicStaticMethods);
            m.put("invokestaticCallSites", invokestaticCallSites);
            m.put("nativeBridgeNativeMethods", nativeBridgeNativeMethods);
            m.put("namedNativeBridgeNativeMethods", namedNativeBridgeNativeMethods);
            m.put("objectArrayPublicStaticMethods", objectArrayPublicStaticMethods);
            m.put("objectArrayInvokestaticCallSites", objectArrayInvokestaticCallSites);
            m.put("objectArrayNativeBridgeNativeMethods", objectArrayNativeBridgeNativeMethods);
            m.put("objectObjectIntPublicStaticMethods", objectObjectIntPublicStaticMethods);
            m.put("objectObjectIntInvokestaticCallSites", objectObjectIntInvokestaticCallSites);
            m.put("objectObjectIntNativeMethods", objectObjectIntNativeMethods);
            m.put("invokestaticOwners", invokestaticOwners);
            m.put("invokestaticTargets", invokestaticTargets);
            m.put("objectObjectIntInvokestaticOwners", objectObjectIntInvokestaticOwners);
            m.put("objectObjectIntInvokestaticTargets", objectObjectIntInvokestaticTargets);
            m.put("programFactoryDescriptorMethods", programFactoryDescriptorMethods);
            m.put("programFactoryObjectArrayShapeCandidates", programFactoryObjectArrayShapeCandidates);
            m.put("privateStaticSyntheticProgramFactories", privateStaticSyntheticProgramFactories);
            m.put("programFactoryCallSites", programFactoryCallSites);
            m.put("referencedProgramFactoryCallSites", referencedProgramFactoryCallSites);
            m.put("referencedProgramFactoryUniqueTargets", referencedProgramFactoryUniqueTargets);
            m.put("programFactoryCallTargets", programFactoryCallTargets);
            m.put("referencedProgramFactories", referencedProgramFactories);
            m.put("vmCallsiteShapeCandidates", vmCallsiteShapeCandidates);
            m.put("vmCallsiteObjectArrayEntryCandidates", vmCallsiteObjectArrayEntryCandidates);
            m.put("vmCallsiteObjectObjectIntEntryCandidates", vmCallsiteObjectObjectIntEntryCandidates);
            m.put("vmCallsiteEntryOwners", vmCallsiteEntryOwners);
            m.put("vmCallsiteProgramFactoryTargets", vmCallsiteProgramFactoryTargets);
            m.put("publicStaticMethodExamples", publicStaticMethodExamples);
            m.put("invokestaticCallSiteExamples", invokestaticCallSiteExamples);
            m.put("nativeBridgeNativeMethodExamples", nativeBridgeNativeMethodExamples);
            m.put("objectObjectIntPublicStaticMethodExamples", objectObjectIntPublicStaticMethodExamples);
            m.put("objectObjectIntInvokestaticCallSiteExamples", objectObjectIntInvokestaticCallSiteExamples);
            m.put("objectObjectIntNativeMethodExamples", objectObjectIntNativeMethodExamples);
            m.put("programFactoryExamples", programFactoryExamples);
            m.put("referencedProgramFactoryExamples", referencedProgramFactoryExamples);
            m.put("vmCallsiteShapeExamples", vmCallsiteShapeExamples);
            return m;
        }
    }

    private static final class RuntimeApiRisk {
        int indySites, shortRuntimeApiNames, runtimeMethodHandlesConstant, runtimeConstantCallSiteCtor, constantCallSiteTypeRefs,
                runtimeReflectionLookups, runtimeReflectionInvokes, riskyStaticBootstrapShapes,
                publicStaticMethodsAll, bootstrapOwnerClasses, bootstrapOwnerRuntimeClasses, runtimeCandidateClasses,
                publicStaticMethodsOnBootstrapOwners, publicStaticMethodsOnRuntimeLikeClasses,
                enumerablePublicStaticRuntimeApis, publicStaticBootstrapApis, publicStaticNoArgMetadataApis,
                publicStaticLeakProneDescriptors,
                directRuntimeBootstrapForwardCalls,
                dominantBootstrapOwnerSites, bootstrapOwnersWithVmEntryMethods,
                bootstrapOwnersWithVmEntryCallSites, bootstrapOwnersWithProgramFactories,
                bootstrapOwnersWithNativeVmEntries, bootstrapVmCentralityRiskClasses,
                bootstrapVmCentralityRiskScore;
        String dominantBootstrapOwner;
        double dominantBootstrapOwnerShare;
        final Map<String,Integer> apiCategories = new TreeMap<>(), apiDescriptors = new TreeMap<>(),
                directRuntimeBootstrapForwardOwners = new TreeMap<>();
        final List<Object> examples = new ArrayList<>(), apiNameExamples = new ArrayList<>(), reflectionExamples = new ArrayList<>(),
                publicStaticApiExamples = new ArrayList<>(), runtimeClassExamples = new ArrayList<>(),
                bootstrapCentralityExamples = new ArrayList<>(), directRuntimeBootstrapForwardExamples = new ArrayList<>();
        Map<String,Object> map() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("indySites", indySites);
            m.put("publicStaticMethodsAll", publicStaticMethodsAll);
            m.put("bootstrapOwnerClasses", bootstrapOwnerClasses);
            m.put("dominantBootstrapOwner", dominantBootstrapOwner);
            m.put("dominantBootstrapOwnerSites", dominantBootstrapOwnerSites);
            m.put("dominantBootstrapOwnerShare", dominantBootstrapOwnerShare);
            m.put("bootstrapOwnerRuntimeClasses", bootstrapOwnerRuntimeClasses);
            m.put("runtimeCandidateClasses", runtimeCandidateClasses);
            m.put("publicStaticMethodsOnBootstrapOwners", publicStaticMethodsOnBootstrapOwners);
            m.put("publicStaticMethodsOnRuntimeLikeClasses", publicStaticMethodsOnRuntimeLikeClasses);
            m.put("enumerablePublicStaticRuntimeApis", enumerablePublicStaticRuntimeApis);
            m.put("publicStaticBootstrapApis", publicStaticBootstrapApis);
            m.put("publicStaticNoArgMetadataApis", publicStaticNoArgMetadataApis);
            m.put("publicStaticLeakProneDescriptors", publicStaticLeakProneDescriptors);
            m.put("directRuntimeBootstrapForwardCalls", directRuntimeBootstrapForwardCalls);
            m.put("directRuntimeBootstrapForwardOwners", directRuntimeBootstrapForwardOwners);
            m.put("apiCategories", apiCategories);
            m.put("apiDescriptors", apiDescriptors);
            m.put("shortRuntimeApiNames", shortRuntimeApiNames);
            m.put("runtimeMethodHandlesConstant", runtimeMethodHandlesConstant);
            m.put("runtimeConstantCallSiteCtor", runtimeConstantCallSiteCtor);
            m.put("constantCallSiteTypeRefs", constantCallSiteTypeRefs);
            m.put("runtimeReflectionLookups", runtimeReflectionLookups);
            m.put("runtimeReflectionInvokes", runtimeReflectionInvokes);
            m.put("riskyStaticBootstrapShapes", riskyStaticBootstrapShapes);
            m.put("bootstrapOwnersWithVmEntryMethods", bootstrapOwnersWithVmEntryMethods);
            m.put("bootstrapOwnersWithVmEntryCallSites", bootstrapOwnersWithVmEntryCallSites);
            m.put("bootstrapOwnersWithProgramFactories", bootstrapOwnersWithProgramFactories);
            m.put("bootstrapOwnersWithNativeVmEntries", bootstrapOwnersWithNativeVmEntries);
            m.put("bootstrapVmCentralityRiskClasses", bootstrapVmCentralityRiskClasses);
            m.put("bootstrapVmCentralityRiskScore", bootstrapVmCentralityRiskScore);
            m.put("examples", examples);
            m.put("apiNameExamples", apiNameExamples);
            m.put("reflectionExamples", reflectionExamples);
            m.put("publicStaticApiExamples", publicStaticApiExamples);
            m.put("runtimeClassExamples", runtimeClassExamples);
            m.put("bootstrapCentralityExamples", bootstrapCentralityExamples);
            m.put("directRuntimeBootstrapForwardExamples", directRuntimeBootstrapForwardExamples);
            return m;
        }
    }

    private static final class InvokeSummary {
        boolean enabled; int candidates, invoked, leaked, failed, nullResults, mismatchedResults, nonEmptyArrayLeaks; String disabledReason;
        final Map<String,Integer> attemptedByKind = new TreeMap<>(), leakedByKind = new TreeMap<>(), failedByKind = new TreeMap<>();
        final List<Object> examples = new ArrayList<>(), failures = new ArrayList<>();
        static InvokeSummary enabled(int c) { InvokeSummary s = new InvokeSummary(); s.enabled = true; s.candidates = c; return s; }
        static InvokeSummary disabled(String why) { InvokeSummary s = new InvokeSummary(); s.enabled = false; s.disabledReason = why; return s; }
        Map<String,Object> map() { Map<String,Object> m = new LinkedHashMap<>(); m.put("enabled", enabled); if (!enabled) { m.put("disabledReason", disabledReason); return m; } m.put("candidates", candidates); m.put("attemptedByKind", attemptedByKind); m.put("invoked", invoked); m.put("leaked", leaked); m.put("leakedByKind", leakedByKind); m.put("failed", failed); m.put("failedByKind", failedByKind); m.put("nullResults", nullResults); m.put("mismatchedResults", mismatchedResults); if (nonEmptyArrayLeaks > 0) m.put("nonEmptyArrayLeaks", nonEmptyArrayLeaks); m.put("examples", examples); m.put("failures", failures); return m; }
    }

    private static Map<String,Integer> kinds() { Map<String,Integer> m = new LinkedHashMap<>(); m.put("String",0); m.put("int",0); m.put("long",0); m.put("float",0); m.put("double",0); return m; }

    private static final class Summary {
        final Opt opt; boolean ok = true; int classCount; StaticScan staticScan; IndyScan indyScan; CallSiteShapes callSiteShapes; VmAbiShape vmAbiShape; RuntimeApiRisk runtimeApiRisk; InvokeSummary metadataReflection, indyOracle; final List<Object> errors = new ArrayList<>();
        Summary(Opt opt) { this.opt = opt; }
        Map<String,Object> map() { Map<String,Object> m = new LinkedHashMap<>(); m.put("schema", SCHEMA); m.put("generatedAt", Instant.now().toString()); m.put("ok", ok); m.put("jar", opt.jar == null ? null : opt.jar.toAbsolutePath().normalize().toString()); m.put("classpath", opt.classpath.stream().map(p -> p.toAbsolutePath().normalize().toString()).toList()); m.put("settings", Map.of("invokeMetadata", opt.invokeMetadata, "invokeIndy", opt.invokeIndy, "invokeTimeoutMs", opt.invokeTimeoutMs, "detailLimit", opt.detailLimit, "valueStringLimit", opt.valueStringLimit)); m.put("classCount", classCount); m.put("methodDescriptorCounts", staticScan == null ? Collections.emptyMap() : staticScan.map()); m.put("metadataReflection", metadataReflection == null ? Collections.emptyMap() : metadataReflection.map()); m.put("invokedynamicScan", indyScan == null ? Collections.emptyMap() : indyScan.map()); m.put("callSiteShapes", callSiteShapes == null ? Collections.emptyMap() : callSiteShapes.map()); m.put("vmAbiShape", vmAbiShape == null ? Collections.emptyMap() : vmAbiShape.map()); m.put("runtimeApiRisk", runtimeApiRisk == null ? Collections.emptyMap() : runtimeApiRisk.map()); m.put("invokedynamicBootstrapOracle", indyOracle == null ? Collections.emptyMap() : indyOracle.map()); m.put("errors", errors); return m; }
    }

    private static final class Opt {
        Path jar; final List<Path> classpath = new ArrayList<>(); boolean invokeMetadata = true, invokeIndy = true, failOnProbeError, help; int detailLimit = 50, valueStringLimit = 160; long invokeTimeoutMs = 2000L;
        static Opt parse(String[] args) {
            Opt o = new Opt();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--help".equals(a) || "-h".equals(a)) o.help = true;
                else if ("--jar".equals(a)) o.jar = Path.of(val(args, ++i, a));
                else if (a.startsWith("--jar=")) o.jar = Path.of(a.substring(6));
                else if ("--classpath".equals(a) || "--cp".equals(a)) addCp(o, val(args, ++i, a));
                else if (a.startsWith("--classpath=")) addCp(o, a.substring(12));
                else if (a.startsWith("--cp=")) addCp(o, a.substring(5));
                else if ("--no-metadata-invoke".equals(a)) o.invokeMetadata = false;
                else if ("--no-indy-invoke".equals(a)) o.invokeIndy = false;
                else if ("--fail-on-probe-error".equals(a)) o.failOnProbeError = true;
                else if ("--detail-limit".equals(a)) o.detailLimit = nonNegInt(val(args, ++i, a), a);
                else if (a.startsWith("--detail-limit=")) o.detailLimit = nonNegInt(a.substring(15), a);
                else if ("--value-string-limit".equals(a)) o.valueStringLimit = nonNegInt(val(args, ++i, a), a);
                else if (a.startsWith("--value-string-limit=")) o.valueStringLimit = nonNegInt(a.substring(21), a);
                else if ("--invoke-timeout-ms".equals(a)) o.invokeTimeoutMs = nonNegLong(val(args, ++i, a), a);
                else if (a.startsWith("--invoke-timeout-ms=")) o.invokeTimeoutMs = nonNegLong(a.substring(20), a);
                else throw new IllegalArgumentException("Unknown argument: " + a);
            }
            if (o.jar != null) { o.jar = o.jar.toAbsolutePath().normalize(); if (!Files.isRegularFile(o.jar)) throw new IllegalArgumentException("Jar does not exist: " + o.jar); }
            List<Path> n = new ArrayList<>(); for (Path p : o.classpath) { Path q = p.toAbsolutePath().normalize(); if (!Files.exists(q)) throw new IllegalArgumentException("Classpath entry does not exist: " + q); n.add(q); } o.classpath.clear(); o.classpath.addAll(n);
            return o;
        }
        static String usage() { return "Usage: java ... " + ObfuscationLeakProbe.class.getName() + " --jar <obfuscated.jar> [--cp <path" + File.pathSeparator + "...>] [--no-metadata-invoke] [--no-indy-invoke] [--detail-limit N] [--value-string-limit N] [--invoke-timeout-ms N]"; }
        static void addCp(Opt o, String raw) { if (raw == null || raw.isBlank()) return; for (String x : raw.split(java.util.regex.Pattern.quote(File.pathSeparator))) if (!x.isBlank()) o.classpath.add(Path.of(x)); }
        static String val(String[] args, int i, String opt) { if (i >= args.length) throw new IllegalArgumentException("Missing value for " + opt); return args[i]; }
        static int nonNegInt(String s, String opt) { int v = Integer.parseInt(s); if (v < 0) throw new IllegalArgumentException(opt + " must be >= 0"); return v; }
        static long nonNegLong(String s, String opt) { long v = Long.parseLong(s); if (v < 0) throw new IllegalArgumentException(opt + " must be >= 0"); return v; }
    }
}
