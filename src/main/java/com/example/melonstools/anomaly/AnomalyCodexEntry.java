package com.example.melonstools.anomaly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** 服务端热加载的异常物档案条目。完整文本只在服务端保存,同步时按研究进度筛选。 */
public class AnomalyCodexEntry {
    public String id = "";
    public String targetId = "";
    public String displayName = "";
    public String zone = "light";
    public String icon = "";
    public String summary = "";
    public final List<String> containment = new ArrayList<>();
    public final List<ResearchStage> researchStages = new ArrayList<>();

    public static class ResearchStage {
        public int progress;
        public String text = "";

        public ResearchStage(int progress, String text) {
            this.progress = progress;
            this.text = text == null ? "" : text;
        }
    }

    public static AnomalyCodexEntry fromJson(JsonObject obj) {
        AnomalyCodexEntry entry = new AnomalyCodexEntry();
        entry.id = string(obj, "id");
        entry.targetId = string(obj, "targetId");
        entry.displayName = string(obj, "displayName");
        entry.zone = string(obj, "zone");
        if (entry.zone.isEmpty()) entry.zone = "light";
        entry.icon = string(obj, "icon");
        entry.summary = string(obj, "summary");
        if (obj.has("containment") && obj.get("containment").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("containment")) {
                if (!el.isJsonNull()) entry.containment.add(el.getAsString());
            }
        }
        if (obj.has("researchStages") && obj.get("researchStages").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("researchStages")) {
                if (!el.isJsonObject()) continue;
                JsonObject s = el.getAsJsonObject();
                entry.researchStages.add(new ResearchStage(s.has("progress") ? s.get("progress").getAsInt() : 0, string(s, "text")));
            }
        }
        return entry;
    }

    public JsonObject toVisibleJson(int researchProgress, boolean includeLocked) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("targetId", targetId);
        obj.addProperty("displayName", displayName);
        obj.addProperty("zone", zone);
        obj.addProperty("icon", icon);
        obj.addProperty("summary", summary);
        JsonArray containmentArr = new JsonArray();
        for (String line : containment) containmentArr.add(line);
        obj.add("containment", containmentArr);
        JsonArray stages = new JsonArray();
        for (ResearchStage stage : researchStages) {
            if (!includeLocked && stage.progress > researchProgress) continue;
            JsonObject s = new JsonObject();
            s.addProperty("progress", stage.progress);
            s.addProperty("text", stage.text);
            s.addProperty("unlocked", stage.progress <= researchProgress);
            stages.add(s);
        }
        obj.add("researchStages", stages);
        return obj;
    }

    public boolean valid() {
        return !id.isBlank() && !targetId.isBlank();
    }

    private static String string(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }
}
