import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class MetadataDescriptorProbe implements Opcodes {
    private static final String DESC_VM_ENTRY_ARRAY = "([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String DESC_VM_ENTRY_OBJECT_OBJECT_INT = "(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;";
    private static final String DESC_PROGRAM_FACTORY = "(I)Ljava/lang/Object;";

    private MetadataDescriptorProbe() {
    }

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        int classes = 0;
        int methods = 0;
        int objectArrayDescriptors = 0;
        int classesWithObjectArrayDescriptors = 0;

        int vmAbiPublicStaticMethods = 0; // legacy alias: old Object[],Object[] VM entry only
        int vmAbiInvokestaticCallSites = 0; // legacy alias: old Object[],Object[] VM entry only
        int vmAbiNativeBridgeNativeMethods = 0; // legacy alias: old Object[],Object[] native only

        int vmAbiObjectArrayPublicStaticMethods = 0;
        int vmAbiObjectArrayInvokestaticCallSites = 0;
        int vmAbiObjectArrayNativeBridgeNativeMethods = 0;
        int vmAbiObjectObjectIntPublicStaticMethods = 0;
        int vmAbiObjectObjectIntInvokestaticCallSites = 0;
        int vmAbiObjectObjectIntNativeMethods = 0;

        int programFactoryDescriptorMethods = 0;
        int programFactoryObjectArrayShapeCandidates = 0;
        int privateStaticSyntheticProgramFactories = 0;
        int programFactoryCallSites = 0;
        int referencedProgramFactoryCallSites = 0;
        int vmCallsiteShapeCandidates = 0;
        int vmCallsiteObjectArrayEntryCandidates = 0;
        int vmCallsiteObjectObjectIntEntryCandidates = 0;
        int invokedynamicSites = 0;
        int uniqueBootstrapOwners = 0;
        int bootstrapVmCentralityRiskClasses = 0;

        Set<String> programFactories = new LinkedHashSet<>();
        Set<String> referencedProgramFactories = new LinkedHashSet<>();
        Set<String> bootstrapOwners = new LinkedHashSet<>();

        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entryName.endsWith(".class") || entryName.equals("module-info.class")) {
                    continue;
                }

                ClassNode classNode = new ClassNode();
                try (InputStream input = jf.getInputStream(entry)) {
                    new ClassReader(input).accept(classNode, ClassReader.SKIP_FRAMES);
                }

                for (Object methodObject : classNode.methods) {
                    MethodNode method = (MethodNode) methodObject;
                    if (isProgramFactoryObjectArrayShape(method)) {
                        programFactories.add(methodKey(classNode.name, method.name, method.desc));
                    }
                    if (method.instructions != null) {
                        for (AbstractInsnNode instruction = method.instructions.getFirst();
                             instruction != null;
                             instruction = instruction.getNext()) {
                            if (instruction instanceof InvokeDynamicInsnNode indy && indy.bsm != null) {
                                invokedynamicSites++;
                                bootstrapOwners.add(indy.bsm.getOwner());
                            }
                        }
                    }
                }
            }
        }

        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entryName.endsWith(".class") || entryName.equals("module-info.class")) {
                    continue;
                }

                ClassNode classNode = new ClassNode();
                try (InputStream input = jf.getInputStream(entry)) {
                    new ClassReader(input).accept(classNode, ClassReader.SKIP_FRAMES);
                }

                classes++;
                int classDescriptorCount = 0;
                boolean classBootstrapOwner = bootstrapOwners.contains(classNode.name);
                int classVmEntryMethods = 0;
                int classVmEntryCallSites = 0;
                int classProgramFactories = 0;
                int classNativeVmEntries = 0;
                for (Object methodObject : classNode.methods) {
                    MethodNode method = (MethodNode) methodObject;
                    methods++;
                    if ("()[Ljava/lang/Object;".equals(method.desc)) {
                        objectArrayDescriptors++;
                        classDescriptorCount++;
                        System.out.println("OBJECT_ARRAY_DESCRIPTOR "
                                + classNode.name.replace('/', '.')
                                + "#"
                                + method.name
                                + method.desc);
                    }
                    if (DESC_VM_ENTRY_ARRAY.equals(method.desc)
                            && (method.access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)) {
                        vmAbiPublicStaticMethods++;
                        vmAbiObjectArrayPublicStaticMethods++;
                        classVmEntryMethods++;
                        System.out.println("VM_ABI_OBJECT_ARRAY_PUBLIC_STATIC_METHOD " + printable(classNode, method));
                    } else if (DESC_VM_ENTRY_ARRAY.equals(method.desc)) {
                        classVmEntryMethods++;
                    }
                    if (DESC_VM_ENTRY_ARRAY.equals(method.desc) && (method.access & ACC_NATIVE) != 0) {
                        vmAbiNativeBridgeNativeMethods++;
                        vmAbiObjectArrayNativeBridgeNativeMethods++;
                        classNativeVmEntries++;
                        System.out.println("VM_ABI_OBJECT_ARRAY_NATIVE_METHOD " + printable(classNode, method));
                    }
                    if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(method.desc)
                            && (method.access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)) {
                        vmAbiObjectObjectIntPublicStaticMethods++;
                        classVmEntryMethods++;
                        System.out.println("VM_ABI_OBJECT_OBJECT_INT_PUBLIC_STATIC_METHOD " + printable(classNode, method));
                    } else if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(method.desc)) {
                        classVmEntryMethods++;
                    }
                    if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(method.desc) && (method.access & ACC_NATIVE) != 0) {
                        vmAbiObjectObjectIntNativeMethods++;
                        classNativeVmEntries++;
                        System.out.println("VM_ABI_OBJECT_OBJECT_INT_NATIVE_METHOD " + printable(classNode, method));
                    }
                    if (DESC_PROGRAM_FACTORY.equals(method.desc)) {
                        programFactoryDescriptorMethods++;
                        if (isProgramFactoryObjectArrayShape(method)) {
                            programFactoryObjectArrayShapeCandidates++;
                            classProgramFactories++;
                            if ((method.access & (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)) == (ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC)) {
                                privateStaticSyntheticProgramFactories++;
                            }
                            System.out.println("PROGRAM_FACTORY_OBJECT_ARRAY_SHAPE " + printable(classNode, method));
                        }
                    }
                    if (method.instructions != null) {
                        VmCallsiteMatch shape = findVmCallsiteShape(method, programFactories);
                        if (shape != null) {
                            vmCallsiteShapeCandidates++;
                            if ("objectArray".equals(shape.entryKind)) {
                                vmCallsiteObjectArrayEntryCandidates++;
                            } else if ("objectObjectInt".equals(shape.entryKind)) {
                                vmCallsiteObjectObjectIntEntryCandidates++;
                            }
                            System.out.println("VM_CALLSITE_TRIAD " + printable(classNode, method)
                                    + " -> " + shape.factoryTarget + " -> " + shape.entryKind);
                        }
                        for (AbstractInsnNode instruction = method.instructions.getFirst();
                             instruction != null;
                             instruction = instruction.getNext()) {
                            if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC) {
                                if (DESC_VM_ENTRY_ARRAY.equals(call.desc)) {
                                    vmAbiInvokestaticCallSites++;
                                    vmAbiObjectArrayInvokestaticCallSites++;
                                    classVmEntryCallSites++;
                                    System.out.println("VM_ABI_OBJECT_ARRAY_INVOKESTATIC_CALL_SITE "
                                            + printable(classNode, method)
                                            + " -> "
                                            + call.owner.replace('/', '.')
                                            + "#"
                                            + call.name
                                            + call.desc);
                                } else if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(call.desc)) {
                                    vmAbiObjectObjectIntInvokestaticCallSites++;
                                    classVmEntryCallSites++;
                                    System.out.println("VM_ABI_OBJECT_OBJECT_INT_INVOKESTATIC_CALL_SITE "
                                            + printable(classNode, method)
                                            + " -> "
                                            + call.owner.replace('/', '.')
                                            + "#"
                                            + call.name
                                            + call.desc);
                                } else if (DESC_PROGRAM_FACTORY.equals(call.desc)) {
                                    programFactoryCallSites++;
                                    String key = methodKey(call.owner, call.name, call.desc);
                                    if (programFactories.contains(key)) {
                                        referencedProgramFactoryCallSites++;
                                        referencedProgramFactories.add(key);
                                    }
                                }
                            }
                        }
                    }
                }
                if (classBootstrapOwner && (classVmEntryMethods > 0 || classVmEntryCallSites > 0 || classProgramFactories > 0 || classNativeVmEntries > 0)) {
                    bootstrapVmCentralityRiskClasses++;
                    System.out.println("BOOTSTRAP_VM_CENTRALITY_CLASS "
                            + classNode.name.replace('/', '.')
                            + " vmEntryMethods=" + classVmEntryMethods
                            + " vmEntryCallSites=" + classVmEntryCallSites
                            + " programFactories=" + classProgramFactories
                            + " nativeVmEntries=" + classNativeVmEntries);
                }
                if (classDescriptorCount > 0) {
                    classesWithObjectArrayDescriptors++;
                }
            }
        }
        uniqueBootstrapOwners = bootstrapOwners.size();

        System.out.println("classes=" + classes
                + " methods=" + methods
                + " objectArrayDescriptors=" + objectArrayDescriptors
                + " classesWithObjectArrayDescriptors=" + classesWithObjectArrayDescriptors
                + " vmAbiPublicStaticMethods=" + vmAbiPublicStaticMethods
                + " vmAbiInvokestaticCallSites=" + vmAbiInvokestaticCallSites
                + " vmAbiNativeBridgeNativeMethods=" + vmAbiNativeBridgeNativeMethods
                + " vmAbiObjectArrayPublicStaticMethods=" + vmAbiObjectArrayPublicStaticMethods
                + " vmAbiObjectArrayInvokestaticCallSites=" + vmAbiObjectArrayInvokestaticCallSites
                + " vmAbiObjectArrayNativeBridgeNativeMethods=" + vmAbiObjectArrayNativeBridgeNativeMethods
                + " vmAbiObjectObjectIntPublicStaticMethods=" + vmAbiObjectObjectIntPublicStaticMethods
                + " vmAbiObjectObjectIntInvokestaticCallSites=" + vmAbiObjectObjectIntInvokestaticCallSites
                + " vmAbiObjectObjectIntNativeMethods=" + vmAbiObjectObjectIntNativeMethods
                + " programFactoryDescriptorMethods=" + programFactoryDescriptorMethods
                + " programFactoryObjectArrayShapeCandidates=" + programFactoryObjectArrayShapeCandidates
                + " privateStaticSyntheticProgramFactories=" + privateStaticSyntheticProgramFactories
                + " programFactoryCallSites=" + programFactoryCallSites
                + " referencedProgramFactoryCallSites=" + referencedProgramFactoryCallSites
                + " referencedProgramFactoryUniqueTargets=" + referencedProgramFactories.size()
                + " vmCallsiteShapeCandidates=" + vmCallsiteShapeCandidates
                + " vmCallsiteObjectArrayEntryCandidates=" + vmCallsiteObjectArrayEntryCandidates
                + " vmCallsiteObjectObjectIntEntryCandidates=" + vmCallsiteObjectObjectIntEntryCandidates
                + " invokedynamicSites=" + invokedynamicSites
                + " uniqueBootstrapOwners=" + uniqueBootstrapOwners
                + " bootstrapVmCentralityRiskClasses=" + bootstrapVmCentralityRiskClasses);

        if (objectArrayDescriptors != 0) {
            System.exit(2);
        }
    }

    private static String printable(ClassNode classNode, MethodNode method) {
        return classNode.name.replace('/', '.') + "#" + method.name + method.desc;
    }

    private static String methodKey(String owner, String name, String desc) {
        return owner + "." + name + desc;
    }

    private static boolean isProgramFactoryObjectArrayShape(MethodNode method) {
        if (!DESC_PROGRAM_FACTORY.equals(method.desc) || method.instructions == null) {
            return false;
        }
        if ((method.access & ACC_STATIC) == 0) {
            return false;
        }
        return opcodeCount(method, ANEWARRAY, "java/lang/Object") > 0
                && opcodeCount(method, AASTORE, null) >= 4
                && opcodeCount(method, ARETURN, null) > 0;
    }

    private static int opcodeCount(MethodNode method, int opcode, String typeDesc) {
        if (method.instructions == null) {
            return 0;
        }
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() != opcode) {
                continue;
            }
            if (typeDesc != null) {
                if (!(instruction instanceof TypeInsnNode typeInsn) || !typeDesc.equals(typeInsn.desc)) {
                    continue;
                }
            }
            count++;
        }
        return count;
    }

    private static VmCallsiteMatch findVmCallsiteShape(MethodNode method, Set<String> knownProgramFactories) {
        if (method.instructions == null) {
            return null;
        }
        String factoryTarget = null;
        boolean sawObjectArgsArray = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC) {
                String key = methodKey(call.owner, call.name, call.desc);
                if (DESC_PROGRAM_FACTORY.equals(call.desc)
                        && (knownProgramFactories.isEmpty() || knownProgramFactories.contains(key))) {
                    factoryTarget = key;
                    sawObjectArgsArray = false;
                    continue;
                }
                if (factoryTarget != null && sawObjectArgsArray) {
                    if (DESC_VM_ENTRY_ARRAY.equals(call.desc)) {
                        return new VmCallsiteMatch(factoryTarget, "objectArray");
                    }
                    if (DESC_VM_ENTRY_OBJECT_OBJECT_INT.equals(call.desc)) {
                        return new VmCallsiteMatch(factoryTarget, "objectObjectInt");
                    }
                }
            } else if (factoryTarget != null
                    && instruction instanceof TypeInsnNode typeInsn
                    && typeInsn.getOpcode() == ANEWARRAY
                    && "java/lang/Object".equals(typeInsn.desc)) {
                sawObjectArgsArray = true;
            }
        }
        return null;
    }

    private static final class VmCallsiteMatch {
        final String factoryTarget;
        final String entryKind;

        VmCallsiteMatch(String factoryTarget, String entryKind) {
            this.factoryTarget = factoryTarget;
            this.entryKind = entryKind;
        }
    }
}
