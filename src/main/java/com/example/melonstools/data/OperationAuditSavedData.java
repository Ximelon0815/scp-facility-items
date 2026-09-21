package com.example.melonstools.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Phase 1-B audit SavedData skeleton. Append-only for normal business flows. */
public class OperationAuditSavedData extends SavedData {
    public static final String DATA_NAME = "melon_operation_audit";
    public static final int MAX_HISTORY = 1000;

    private final List<Entry> entries = new ArrayList<>();
    private int sequence;

    public static OperationAuditSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(OperationAuditSavedData::load, OperationAuditSavedData::new, DATA_NAME);
    }

    public static OperationAuditSavedData load(CompoundTag tag) {
        OperationAuditSavedData data = new OperationAuditSavedData();
        data.sequence = tag.getInt("Sequence");
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.entries.add(Entry.load(list.getCompound(i)));
        data.prune();
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Sequence", sequence);
        ListTag list = new ListTag();
        for (Entry e : entries) list.add(e.save());
        tag.put("Entries", list);
        return tag;
    }

    public List<Entry> entries() { return Collections.unmodifiableList(entries); }

    public Entry record(ServerPlayer actor, String action, String target, String result, String reason, String before, String after) {
        Entry e = new Entry();
        e.id = "audit-" + (++sequence);
        e.actorUuid = actor == null ? "" : actor.getUUID().toString();
        e.actorName = actor == null ? "SYSTEM" : actor.getScoreboardName();
        e.action = safe(action); e.target = safe(target); e.result = safe(result); e.reason = safe(reason);
        e.before = safe(before); e.after = safe(after); e.timestamp = System.currentTimeMillis();
        entries.add(e); prune(); setDirty(); return e;
    }

    private void prune() { while (entries.size() > MAX_HISTORY) entries.remove(0); }

    public JsonObject toJson() {
        JsonObject root = new JsonObject(); root.addProperty("maxHistory", MAX_HISTORY);
        JsonArray arr = new JsonArray();
        for (int i = entries.size() - 1; i >= 0; i--) arr.add(entries.get(i).toJson());
        root.add("items", arr); return root;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static class Entry {
        public String id = "", actorUuid = "", actorName = "", action = "", target = "", result = "", reason = "", before = "", after = "";
        public long timestamp;
        public CompoundTag save() { CompoundTag t = new CompoundTag(); t.putString("Id",id); t.putString("ActorUuid",actorUuid); t.putString("ActorName",actorName); t.putString("Action",action); t.putString("Target",target); t.putString("Result",result); t.putString("Reason",reason); t.putString("Before",before); t.putString("After",after); t.putLong("Timestamp",timestamp); return t; }
        public static Entry load(CompoundTag t) { Entry e = new Entry(); e.id=t.getString("Id"); e.actorUuid=t.getString("ActorUuid"); e.actorName=t.getString("ActorName"); e.action=t.getString("Action"); e.target=t.getString("Target"); e.result=t.getString("Result"); e.reason=t.getString("Reason"); e.before=t.getString("Before"); e.after=t.getString("After"); e.timestamp=t.getLong("Timestamp"); return e; }
        public JsonObject toJson() { JsonObject o = new JsonObject(); o.addProperty("id",id); o.addProperty("actorUuid",actorUuid); o.addProperty("actorName",actorName); o.addProperty("action",action); o.addProperty("target",target); o.addProperty("result",result); o.addProperty("reason",reason); o.addProperty("before",before); o.addProperty("after",after); o.addProperty("timestamp",timestamp); return o; }
    }
}
