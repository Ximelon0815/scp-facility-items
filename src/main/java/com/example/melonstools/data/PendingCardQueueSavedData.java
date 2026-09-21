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

/** Phase 1-C pending card/manual card-change queue. This data never reads or writes ID-card NBT. */
public class PendingCardQueueSavedData extends SavedData {
    public static final String DATA_NAME = "melon_pending_card_queue";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_VOID = "void";

    private final List<Record> records = new ArrayList<>();
    private int sequence;

    public static PendingCardQueueSavedData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(PendingCardQueueSavedData::load, PendingCardQueueSavedData::new, DATA_NAME); }
    public static PendingCardQueueSavedData load(CompoundTag tag) { PendingCardQueueSavedData d = new PendingCardQueueSavedData(); d.sequence = tag.getInt("Sequence"); ListTag list = tag.getList("Records", Tag.TAG_COMPOUND); for (int i = 0; i < list.size(); i++) d.records.add(Record.load(list.getCompound(i))); return d; }
    @Override public CompoundTag save(CompoundTag tag) { tag.putInt("Sequence", sequence); ListTag list = new ListTag(); for (Record r : records) list.add(r.save()); tag.put("Records", list); return tag; }
    public List<Record> records() { return Collections.unmodifiableList(records); }

    public Record createFromApplication(DepartmentApplicationSavedData.Application app, ServerPlayer actor) {
        if (app == null) return null;
        for (Record existing : records) if (app.id.equals(existing.sourceApplicationId) && STATUS_PENDING.equals(existing.status)) return existing;
        Record r = new Record();
        r.id = "cardq-" + (++sequence);
        r.sourceApplicationId = app.id;
        r.targetUuid = app.applicantUuid;
        r.targetName = app.applicantName;
        r.currentDepartment = app.applicantDepartment;
        r.currentPosition = app.applicantPosition;
        r.currentLevel = app.applicantLevel;
        r.newDepartment = app.targetDepartment;
        r.newPosition = app.targetPosition;
        r.newLevel = app.targetLevel <= 0 ? 1 : app.targetLevel;
        r.reason = "application_approved_manual_card_change";
        r.status = STATUS_PENDING;
        r.createdAt = System.currentTimeMillis();
        r.createdBy = actor == null ? "SYSTEM" : actor.getScoreboardName();
        r.manualConfirmationNote = "终端只记录当前身份与目标身份；实际换卡/制卡为线下人工流程，本队列不检查、不读取、不写入ID卡NBT。";
        records.add(r);
        setDirty();
        return r;
    }

    public boolean markCompleted(String id, ServerPlayer actor) {
        Record r = find(id);
        if (r == null || !STATUS_PENDING.equals(r.status)) return false;
        r.status = STATUS_COMPLETED;
        r.handledBy = actor == null ? "SYSTEM" : actor.getScoreboardName();
        r.handledByUuid = actor == null ? "" : actor.getUUID().toString();
        r.handledAt = System.currentTimeMillis();
        r.manualConfirmationNote = "人工确认流程完成；此操作未检查、未读取、未写入ID卡NBT。";
        setDirty();
        return true;
    }

    public Record find(String id) { for (Record r : records) if (r.id.equals(id)) return r; return null; }
    public JsonObject toJson() { JsonObject root = new JsonObject(); JsonArray arr = new JsonArray(); for (Record r : records) arr.add(r.toJson()); root.add("items", arr); root.addProperty("note", "待换卡队列只做人工流程确认，不写卡NBT"); return root; }
    public JsonObject toJsonFor(ServerPlayer viewer) { JsonObject root = new JsonObject(); JsonArray arr = new JsonArray(); String uuid = viewer == null ? "" : viewer.getUUID().toString(); boolean op = viewer != null && viewer.hasPermissions(2); for (Record r : records) if (op || uuid.equals(r.targetUuid)) arr.add(r.toJson()); root.add("items", arr); root.addProperty("note", "个人终端仅下发本人待换卡状态；人工确认，不写卡NBT"); return root; }

    public static class Record {
        public String id = "", sourceApplicationId = "", targetUuid = "", targetName = "", currentDepartment = "", currentPosition = "", newDepartment = "", newPosition = "", reason = "", status = STATUS_PENDING, createdBy = "", handledBy = "", handledByUuid = "", manualConfirmationNote = "";
        public int currentLevel, newLevel = 1;
        public long createdAt, handledAt;
        public CompoundTag save() { CompoundTag t = new CompoundTag(); t.putString("Id", id); t.putString("SourceApplicationId", sourceApplicationId); t.putString("TargetUuid", targetUuid); t.putString("TargetName", targetName); t.putString("CurrentDepartment", currentDepartment); t.putString("CurrentPosition", currentPosition); t.putInt("CurrentLevel", currentLevel); t.putString("NewDepartment", newDepartment); t.putString("NewPosition", newPosition); t.putInt("NewLevel", newLevel); t.putString("Reason", reason); t.putString("Status", status); t.putLong("CreatedAt", createdAt); t.putString("CreatedBy", createdBy); t.putString("HandledBy", handledBy); t.putString("HandledByUuid", handledByUuid); t.putLong("HandledAt", handledAt); t.putString("ManualConfirmationNote", manualConfirmationNote); return t; }
        public static Record load(CompoundTag t) { Record r = new Record(); r.id=t.getString("Id"); r.sourceApplicationId=t.getString("SourceApplicationId"); r.targetUuid=t.getString("TargetUuid"); r.targetName=t.getString("TargetName"); r.currentDepartment=t.getString("CurrentDepartment"); r.currentPosition=t.getString("CurrentPosition"); r.currentLevel=t.getInt("CurrentLevel"); r.newDepartment=t.getString("NewDepartment"); r.newPosition=t.getString("NewPosition"); r.newLevel=t.getInt("NewLevel"); if(r.newLevel<=0) r.newLevel=1; r.reason=t.getString("Reason"); r.status=t.getString("Status"); r.createdAt=t.getLong("CreatedAt"); r.createdBy=t.getString("CreatedBy"); r.handledBy=t.getString("HandledBy"); r.handledByUuid=t.getString("HandledByUuid"); r.handledAt=t.getLong("HandledAt"); r.manualConfirmationNote=t.getString("ManualConfirmationNote"); return r; }
        public JsonObject toJson() { JsonObject o = new JsonObject(); o.addProperty("id", id); o.addProperty("sourceApplicationId", sourceApplicationId); o.addProperty("targetUuid", targetUuid); o.addProperty("targetName", targetName); o.addProperty("currentDepartment", currentDepartment); o.addProperty("currentPosition", currentPosition); o.addProperty("currentLevel", currentLevel); o.addProperty("targetDepartment", newDepartment); o.addProperty("targetPosition", newPosition); o.addProperty("targetLevel", newLevel); o.addProperty("reason", reason); o.addProperty("status", status); o.addProperty("createdAt", createdAt); o.addProperty("createdBy", createdBy); o.addProperty("handledBy", handledBy); o.addProperty("handledByUuid", handledByUuid); o.addProperty("handledAt", handledAt); o.addProperty("manualConfirmationNote", manualConfirmationNote); return o; }
    }
}
