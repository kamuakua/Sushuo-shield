package biz.sushuo.shield;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

final class ResourceRewriter {
    private ResourceRewriter() {
    }

    static String rewriteName(String name, Map<String, String> classNames) {
        if (name.startsWith("META-INF/services/")) {
            String service = name.substring("META-INF/services/".length());
            String mapped = classNames.get(service.replace('.', '/'));
            if (mapped != null) {
                return "META-INF/services/" + mapped.replace('/', '.');
            }
        }
        return name;
    }

    static byte[] rewriteBytes(
            String name,
            byte[] bytes,
            Map<String, String> classNames,
            ObfuscationOptions options
    ) {
        if (options.minecraftMode() && MinecraftProtector.preserveResourceText(name)) {
            return bytes;
        }
        byte[] rewritten = bytes;
        if (isManifest(name)) {
            rewritten = rewriteManifest(rewritten, classNames);
        }
        if (options.rewriteTextResources() && isTextResource(name, rewritten.length)) {
            rewritten = rewriteText(rewritten, classNames);
        }
        return rewritten;
    }

    private static byte[] rewriteManifest(byte[] bytes, Map<String, String> classNames) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));
            stripDigestAttributes(manifest.getMainAttributes());
            for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
                stripDigestAttributes(entry.getValue());
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            manifest.write(output);
            return rewriteText(output.toByteArray(), classNames);
        } catch (IOException ignored) {
            return rewriteText(bytes, classNames);
        }
    }

    private static void stripDigestAttributes(Attributes attributes) {
        List<Object> toRemove = new ArrayList<>();
        for (Object key : attributes.keySet()) {
            String name = key.toString();
            if (name.endsWith("-Digest") || name.equalsIgnoreCase("Magic")) {
                toRemove.add(key);
            }
        }
        for (Object key : toRemove) {
            attributes.remove(key);
        }
    }

    private static byte[] rewriteText(byte[] bytes, Map<String, String> classNames) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<Map.Entry<String, String>> mappings = classNames.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed())
                .toList();
        for (Map.Entry<String, String> mapping : mappings) {
            String oldInternal = mapping.getKey();
            String newInternal = mapping.getValue();
            text = text.replace(oldInternal, newInternal);
            text = text.replace(oldInternal.replace('/', '.'), newInternal.replace('/', '.'));
            text = text.replace("L" + oldInternal + ";", "L" + newInternal + ";");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isManifest(String name) {
        return name.equalsIgnoreCase("META-INF/MANIFEST.MF");
    }

    private static boolean isTextResource(String name, int length) {
        if (length > 5 * 1024 * 1024) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mf")
                || lower.endsWith(".txt")
                || lower.endsWith(".properties")
                || lower.endsWith(".json")
                || lower.endsWith(".xml")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".toml")
                || lower.endsWith(".cfg")
                || lower.endsWith(".conf")
                || lower.startsWith("meta-inf/services/");
    }
}
