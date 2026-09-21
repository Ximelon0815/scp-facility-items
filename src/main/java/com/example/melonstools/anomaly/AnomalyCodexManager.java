package com.example.melonstools.anomaly;

import com.example.melonstools.MelonsTools;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 服务端异常物档案库。读取 config/melonstools/anomalies/*.json。 */
public class AnomalyCodexManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, AnomalyCodexEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, String> ERRORS = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("melonstools").resolve("anomalies");
    }

    public static synchronized void reload() {
        ENTRIES.clear();
        ERRORS.clear();
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ERRORS.put(dir.toString(), e.getMessage());
            loaded = true;
            return;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .forEach(AnomalyCodexManager::loadOne);
        } catch (IOException e) {
            ERRORS.put(dir.toString(), e.getMessage());
        }
        loaded = true;
        MelonsTools.LOGGER.info("Loaded {} anomaly codex entries, {} errors", ENTRIES.size(), ERRORS.size());
    }

    private static void loadOne(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            AnomalyCodexEntry entry = AnomalyCodexEntry.fromJson(obj);
            if (!entry.valid()) {
                ERRORS.put(path.getFileName().toString(), "missing required id/targetId");
                return;
            }
            ENTRIES.put(entry.id, entry);
        } catch (Throwable t) {
            ERRORS.put(path.getFileName().toString(), t.getMessage());
        }
    }

    public static void ensureLoaded() {
        if (!loaded) reload();
    }

    public static AnomalyCodexEntry get(String id) {
        ensureLoaded();
        return ENTRIES.get(id);
    }

    public static Collection<AnomalyCodexEntry> all() {
        ensureLoaded();
        return Collections.unmodifiableCollection(ENTRIES.values());
    }

    public static Map<String, String> errors() {
        ensureLoaded();
        return Collections.unmodifiableMap(ERRORS);
    }

    public static JsonObject statusJson() {
        ensureLoaded();
        JsonObject root = new JsonObject();
        root.addProperty("loaded", ENTRIES.size());
        root.addProperty("failed", ERRORS.size());
        var errs = new com.google.gson.JsonArray();
        for (Map.Entry<String, String> e : ERRORS.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("file", e.getKey());
            o.addProperty("message", e.getValue());
            errs.add(o);
        }
        root.add("errors", errs);
        return root;
    }
}
