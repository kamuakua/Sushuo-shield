package biz.sushuo.shield;

import imgui.ImGui;
import imgui.app.Application;
import imgui.app.Configuration;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import javax.swing.JFileChooser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** ImGui front end for the existing JAR protection pipeline. */
public final class GuiMain extends Application {
    private static final String[] MODES = {
            "jvm", "sushuo1337", "balanced", "compat", "minecraft", "minecraft-max",
            "jnic", "zkm26", "vmp", "stacked", "all", "max"
    };

    private final ImString input = new ImString(1024);
    private final ImString output = new ImString(1024);
    private final ImString report = new ImString(1024);
    private final ImString seed = new ImString("auto", 64);
    private final ImString license = new ImString(256);
    private final ImInt mode = new ImInt(0);
    private final ImBoolean jvmPhantom = new ImBoolean(true);
    private final ImBoolean antiAi = new ImBoolean(true);
    private final ImBoolean references = new ImBoolean(true);
    private final ImBoolean encryptResources = new ImBoolean(true);
    private final ImBoolean requireNative = new ImBoolean(false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sushuo-shield-gui-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<String> logs = new ArrayList<>();
    private Future<?> job;
    private volatile boolean running;
    private volatile String status = "Ready";
    private volatile ObfuscationResult result;
    private volatile Throwable failure;

    public static void launchWindow() {
        Application.launch(new GuiMain());
    }

    public static void main(String[] args) {
        launchWindow();
    }

    @Override
    protected void configure(Configuration config) {
        config.setTitle("Sushuo Shield");
        config.setWidth(1120);
        config.setHeight(760);
    }

    @Override
    protected void preRun() {
        ImGui.styleColorsDark();
        ImGui.getIO().setIniFilename(null);
        appendLog("GUI ready. Select a JAR and choose a protection mode.");
    }

    @Override
    protected void postRun() {
        executor.shutdownNow();
    }

    @Override
    public void process() {
        pollJob();
        ImGui.setNextWindowSize(1080, 700);
        if (ImGui.begin("Sushuo Shield - Protection Workspace")) {
            ImGui.text("JAR protection");
            ImGui.sameLine(760);
            if (running) {
                ImGui.textColored(0.95f, 0.72f, 0.28f, 1.0f, status);
            } else if (failure != null) {
                ImGui.textColored(0.95f, 0.35f, 0.35f, 1.0f, status);
            } else {
                ImGui.textColored(0.35f, 0.85f, 0.55f, 1.0f, status);
            }
            ImGui.separator();

            pathRow("Input JAR", input, "input", false);
            pathRow("Output JAR", output, "output", true);
            pathRow("Report", report, "report", true);

            ImGui.text("Mode");
            ImGui.sameLine(110);
            ImGui.setNextItemWidth(260);
            ImGui.combo("##mode", mode, MODES);
            ImGui.sameLine();
            ImGui.text(mode.get() == 0
                    ? "JVM mode keeps execution in the Java runtime."
                    : "all/stacked combines the protection layers in one pass.");

            ImGui.text("Seed");
            ImGui.sameLine(110);
            ImGui.setNextItemWidth(260);
            ImGui.inputText("##seed", seed);
            ImGui.sameLine();
            ImGui.text("Use auto or a numeric seed.");

            ImGui.text("License key");
            ImGui.sameLine(110);
            ImGui.setNextItemWidth(260);
            ImGui.inputText("##license", license);

            ImGui.separator();
            ImGui.text("Protection layers");
            ImGui.checkbox("JVM Phantom preprocessing", jvmPhantom);
            ImGui.sameLine(310);
            ImGui.checkbox("Anti-deobfuscation noise", antiAi);
            ImGui.sameLine(590);
            ImGui.checkbox("Reference indirection", references);
            ImGui.checkbox("Encrypt resources", encryptResources);
            ImGui.sameLine(310);
            ImGui.checkbox("Require native VM", requireNative);

            ImGui.separator();
            ImGui.beginDisabled(running);
            if (ImGui.button("Protect", 130, 34)) {
                startProtection();
            }
            ImGui.sameLine();
            if (ImGui.button("Clear log", 130, 34)) {
                synchronized (logs) {
                    logs.clear();
                }
            }
            ImGui.endDisabled();

            if (result != null && !running) {
                ImGui.sameLine(300);
                ImGui.text(String.format("classes %d  virtualized %d  strings %d  numbers %d",
                        result.classCount(), result.virtualizedMethods(), result.encryptedStrings(),
                        result.obfuscatedNumbers()));
            }

            ImGui.separator();
            ImGui.text("Activity");
            boolean activityVisible = ImGui.beginChild("##activity", 0, 170, true);
            if (activityVisible) {
                synchronized (logs) {
                    for (String line : logs) {
                        ImGui.textWrapped(line);
                    }
                }
            }
            ImGui.endChild();
            if (failure != null && !running) {
                ImGui.textWrapped(failure.toString());
            }
        }
        ImGui.end();
    }

    private void pathRow(String label, ImString value, String id, boolean save) {
        ImGui.text(label);
        ImGui.sameLine(110);
        ImGui.setNextItemWidth(-110);
        ImGui.inputText("##" + id, value);
        ImGui.sameLine();
        ImGui.beginDisabled(running);
        if (ImGui.button("Browse##" + id, 92, 0)) {
            chooseFile(value, save);
        }
        ImGui.endDisabled();
    }

    private void chooseFile(ImString target, boolean save) {
        JFileChooser chooser = new JFileChooser();
        if (target.isNotEmpty()) {
            chooser.setSelectedFile(new File(target.get()));
        }
        int result = save ? chooser.showSaveDialog(null) : chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            target.set(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void startProtection() {
        if (running) {
            return;
        }
        try {
            String[] args = buildCliArgs();
            ObfuscationOptions parsed = CliParser.parse(args);
            result = null;
            failure = null;
            running = true;
            status = "Protecting...";
            appendLog("Starting " + MODES[mode.get()] + " protection.");
            job = executor.submit(() -> {
                try {
                    result = new JarObfuscator().obfuscate(parsed);
                    status = "Completed";
                    appendLog("Protection completed: " + parsed.output());
                } catch (Throwable throwable) {
                    failure = throwable;
                    status = "Failed";
                    appendLog("Protection failed: " + throwable.getMessage());
                }
            });
        } catch (Throwable throwable) {
            failure = throwable;
            status = "Invalid configuration";
            appendLog("Configuration error: " + throwable.getMessage());
        }
    }

    private String[] buildCliArgs() {
        List<String> args = new ArrayList<>();
        args.add(input.get());
        args.add(output.get());
        args.add("--mode");
        args.add(MODES[mode.get()]);
        if (seed.isNotEmpty() && !seed.get().equalsIgnoreCase("auto")) {
            args.add("--seed");
            args.add(seed.get());
        }
        if (report.isNotEmpty()) {
            args.add("--report-file");
            args.add(report.get());
        }
        if (license.isNotEmpty()) {
            args.add("--license-key");
            args.add(license.get());
        }
        if (jvmPhantom.get()) {
            args.add("--jvm-phantom");
        } else {
            args.add("--no-jvm-phantom");
        }
        if (antiAi.get()) {
            args.add("--anti-ai");
        } else {
            args.add("--no-anti-ai");
        }
        if (references.get()) {
            args.add("--reference-obfuscation");
        } else {
            args.add("--no-reference-obfuscation");
        }
        if (encryptResources.get()) {
            args.add("--resource-encryption");
        } else {
            args.add("--no-resource-encryption");
        }
        if (requireNative.get()) {
            args.add("--require-native-vm");
        } else {
            args.add("--no-require-native-vm");
        }
        return args.toArray(String[]::new);
    }

    private void pollJob() {
        if (!running || job == null || !job.isDone()) {
            return;
        }
        running = false;
        try {
            job.get();
        } catch (Exception ignored) {
            // The worker already recorded the failure for the UI.
        }
    }

    private void appendLog(String line) {
        synchronized (logs) {
            logs.add(line == null ? "" : line);
            while (logs.size() > 200) {
                logs.remove(0);
            }
        }
    }
}
