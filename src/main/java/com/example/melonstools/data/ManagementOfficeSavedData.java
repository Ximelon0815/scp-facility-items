package com.example.melonstools.data;

import com.example.melonstools.department.DepartmentPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Phase 3-C management office data: archive, meeting invites, policies and read-only metrics. */
public class ManagementOfficeSavedData extends SavedData {
    public static final String DATA_NAME = "melon_management_office";
    public static final String TYPE_ADMIN_ARCHIVE = "ADMIN_ARCHIVE";
    public static final String TYPE_MEETING_INVITE = "MEETING_INVITE";
    public static final String TYPE_OPERATING_POLICY = "OPERATING_POLICY";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_CLOSED = "closed";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";
    public static final int MAX_TITLE = 100;
    public static final int MAX_BODY = 4000;

    private final List<AdminRecord> records = new ArrayList<>();
    private int sequence;

    public static ManagementOfficeSavedData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(ManagementOfficeSavedData::load, ManagementOfficeSavedData::new, DATA_NAME); }

    public static ManagementOfficeSavedData load(CompoundTag tag) {
        ManagementOfficeSavedData data = new ManagementOfficeSavedData();
        data.sequence = tag.getInt("Sequence");
        ListTag list = tag.getList("Records", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            AdminRecord r = AdminRecord.load(list.getCompound(i));
            if (!r.id.isBlank()) data.records.add(r);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Sequence", sequence);
        ListTag list = new ListTag();
        for (AdminRecord record : records) list.add(record.save());
        tag.put("Records", list);
        return tag;
    }

    public List<AdminRecord> records() { return Collections.unmodifiableList(records); }

    @Nullable public AdminRecord find(String id) { String v=safe(id); for (AdminRecord r: records) if (r.id.equals(v)) return r; return null; }

    public AdminRecord createArchive(ServerPlayer actor, String title, String body, String sourceType, String sourceId) {
        AdminRecord r = base(actor, TYPE_ADMIN_ARCHIVE, title, body); r.status = STATUS_ACTIVE; r.sourceType = limit(sourceType, 60); r.sourceId = limit(sourceId, 80); records.add(r); setDirty(); return r;
    }
    public boolean archiveRecord(String id, ServerPlayer actor) { AdminRecord r=find(id); if (r==null || !TYPE_ADMIN_ARCHIVE.equals(r.type)) return false; r.status=STATUS_ARCHIVED; touch(r, actor); setDirty(); return true; }

    public AdminRecord createMeeting(ServerPlayer actor, String title, String body, long startAt, String location, String participants, boolean highPriority) {
        AdminRecord r = base(actor, TYPE_MEETING_INVITE, title, body); r.status = STATUS_PENDING; r.startAt = Math.max(0L, startAt); r.location = limit(location, 120); r.participants = limit(participants, 600); r.highPriority = highPriority; r.combatInterceptionNote = "战斗/生命体征状态拦截未接入，本邀请仅记录与回执。"; records.add(r); setDirty(); return r;
    }
    public boolean respondMeeting(String id, ServerPlayer actor, String response, String note) { AdminRecord r=find(id); if (r==null || !TYPE_MEETING_INVITE.equals(r.type)) return false; String res=normalizeResponse(response); r.status=res; r.receiptActorUuid=actor==null?null:actor.getUUID(); r.receiptActorName=actor==null?"":actor.getScoreboardName(); r.receiptNote=limit(note, 400); r.receiptAt=System.currentTimeMillis(); touch(r, actor); setDirty(); return true; }

    public AdminRecord publishPolicy(ServerPlayer actor, String title, String body) { AdminRecord r=base(actor, TYPE_OPERATING_POLICY, title, body); r.status=STATUS_ACTIVE; records.add(r); setDirty(); return r; }
    public boolean closePolicy(String id, ServerPlayer actor) { AdminRecord r=find(id); if (r==null || !TYPE_OPERATING_POLICY.equals(r.type)) return false; r.status=STATUS_CLOSED; touch(r, actor); setDirty(); return true; }

    /** Legacy B hook kept for compatibility; creates a real archive record, not a deletable stub. */
    public AdminRecord createStubRecord(String type, String title, UUID actorUuid, String actorName) { AdminRecord r=new AdminRecord(); r.id=nextId(); r.type=normalizeType(type); r.title=limit(title, MAX_TITLE); r.actorUuid=actorUuid; r.actorName=limit(actorName,40); r.createdAt=r.updatedAt=System.currentTimeMillis(); r.status=STATUS_ACTIVE; records.add(r); setDirty(); return r; }

    public JsonObject toJsonFor(ServerPlayer viewer, boolean terminal) {
        DepartmentPolicy.Identity id = DepartmentPolicy.identity(viewer); boolean op = viewer != null && viewer.hasPermissions(2);
        boolean clerk = DepartmentPolicy.isClerk(id.department(), id.position(), id.level()); boolean director = DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level());
        boolean ethics = DepartmentPolicy.isEthicsInspector(id.department(), id.position(), id.level()); boolean safety = DepartmentPolicy.isSafetyAgent(id.department(), id.position(), id.level());
        JsonObject root = new JsonObject(); JsonArray arr = new JsonArray();
        for (AdminRecord r: records) if (terminal || visibleOnPhone(r, clerk, director, ethics, safety, op)) arr.add(r.toJson());
        root.add("records", arr); root.add("metrics", buildMetrics(viewer));
        root.addProperty("recordCount", records.size()); root.addProperty("personnelEditEnabled", false); root.addProperty("combatMeetingInterceptionEnabled", false); root.addProperty("combatMeetingInterceptionNote", "未接入：会议创建当前不会拦截战斗/生命体征状态。"); root.addProperty("skeleton", false);
        root.addProperty("canCreateArchive", clerk || director || op); root.addProperty("canArchiveAdminRecord", clerk || director || op); root.addProperty("canCreateNormalMeeting", clerk || director || op); root.addProperty("canCreateHighPriorityMeeting", director || op); root.addProperty("canPublishPolicy", director || op); root.addProperty("canClosePolicy", director || op); root.addProperty("canHardDelete", false);
        root.addProperty("isReadonlySupervisorPlaceholder", ethics || safety || DepartmentPolicy.POS_ETHICS_ASSISTANT.equals(id.position()));
        return root;
    }
    public JsonObject toJsonSkeleton() { return toJsonFor(null, true); }

    private JsonObject buildMetrics(ServerPlayer viewer) {
        JsonObject m = new JsonObject(); ServerLevel level = viewer == null ? null : viewer.serverLevel();
        int archives=0, meetings=0, policies=0, active=0, closed=0; for (AdminRecord r:records){ if(TYPE_ADMIN_ARCHIVE.equals(r.type))archives++; else if(TYPE_MEETING_INVITE.equals(r.type))meetings++; else if(TYPE_OPERATING_POLICY.equals(r.type))policies++; if(STATUS_ACTIVE.equals(r.status)||STATUS_PENDING.equals(r.status))active++; if(STATUS_ARCHIVED.equals(r.status)||STATUS_CLOSED.equals(r.status)||STATUS_ACCEPTED.equals(r.status)||STATUS_DECLINED.equals(r.status))closed++; }
        m.addProperty("archiveCount", archives); m.addProperty("meetingCount", meetings); m.addProperty("policyCount", policies); m.addProperty("activeLikeCount", active); m.addProperty("closedLikeCount", closed);
        if (level != null) { m.addProperty("noticeCount", FacilityNoticeSavedData.get(level).getNotices().size()); m.addProperty("applicationCount", DepartmentApplicationSavedData.get(level).applications().size()); m.addProperty("auditCount", OperationAuditSavedData.get(level).entries().size()); m.addProperty("fundBalance", FacilityFundManager.get(level).getBalance()); }
        return m;
    }
    private boolean visibleOnPhone(AdminRecord r, boolean clerk, boolean director, boolean ethics, boolean safety, boolean op) { if (op || director) return true; if (clerk) return true; if (ethics || safety) return true; return TYPE_OPERATING_POLICY.equals(r.type) && STATUS_CLOSED.equals(r.status); }
    private AdminRecord base(ServerPlayer actor, String type, String title, String body) { AdminRecord r = new AdminRecord(); r.id=nextId(); r.type=type; r.title=limit(title,MAX_TITLE); r.body=limit(body,MAX_BODY); r.actorUuid=actor==null?null:actor.getUUID(); r.actorName=actor==null?"":actor.getScoreboardName(); r.createdAt=r.updatedAt=System.currentTimeMillis(); return r; }
    private void touch(AdminRecord r, ServerPlayer actor) { r.updatedAt=System.currentTimeMillis(); r.lastActorUuid=actor==null?null:actor.getUUID(); r.lastActorName=actor==null?"":actor.getScoreboardName(); }
    private String nextId() { return "mgmt_" + System.currentTimeMillis() + "_" + (++sequence); }
    private static String normalizeType(String type) { String v=safe(type).trim().toUpperCase(Locale.ROOT); if ("ADMIN_RECORD".equals(v)||"ADMIN_ARCHIVE".equals(v)) return TYPE_ADMIN_ARCHIVE; if ("MEETING_INVITE".equals(v)) return TYPE_MEETING_INVITE; if ("OPERATION_POLICY".equals(v)||"OPERATING_POLICY".equals(v)) return TYPE_OPERATING_POLICY; return TYPE_ADMIN_ARCHIVE; }
    private static String normalizeResponse(String response) { String v=safe(response).trim().toLowerCase(Locale.ROOT); return ("declined".equals(v)||"reject".equals(v)||"拒绝".equals(v)) ? STATUS_DECLINED : STATUS_ACCEPTED; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String limit(String value, int max) { String v=safe(value).trim(); return v.length()>max?v.substring(0,max):v; }

    public static class AdminRecord {
        public String id="", type=TYPE_ADMIN_ARCHIVE, title="", body="", actorName="", status=STATUS_ACTIVE, sourceType="", sourceId="", location="", participants="", receiptActorName="", receiptNote="", lastActorName="", combatInterceptionNote="";
        public UUID actorUuid, receiptActorUuid, lastActorUuid; public long createdAt, updatedAt, startAt, receiptAt; public boolean highPriority;
        public static AdminRecord load(CompoundTag tag) { AdminRecord r=new AdminRecord(); r.id=safe(tag.getString("Id")); r.type=normalizeType(tag.getString("Type")); r.title=safe(tag.getString("Title")); r.body=safe(tag.getString("Body")); if(tag.hasUUID("ActorUuid"))r.actorUuid=tag.getUUID("ActorUuid"); r.actorName=safe(tag.getString("ActorName")); r.createdAt=tag.getLong("CreatedAt"); r.updatedAt=tag.getLong("UpdatedAt"); r.status=safe(tag.getString("Status")).isBlank()?STATUS_ACTIVE:safe(tag.getString("Status")); r.sourceType=safe(tag.getString("SourceType")); r.sourceId=safe(tag.getString("SourceId")); r.startAt=tag.getLong("StartAt"); r.location=safe(tag.getString("Location")); r.participants=safe(tag.getString("Participants")); r.highPriority=tag.getBoolean("HighPriority"); if(tag.hasUUID("ReceiptActorUuid"))r.receiptActorUuid=tag.getUUID("ReceiptActorUuid"); r.receiptActorName=safe(tag.getString("ReceiptActorName")); r.receiptNote=safe(tag.getString("ReceiptNote")); r.receiptAt=tag.getLong("ReceiptAt"); if(tag.hasUUID("LastActorUuid"))r.lastActorUuid=tag.getUUID("LastActorUuid"); r.lastActorName=safe(tag.getString("LastActorName")); r.combatInterceptionNote=safe(tag.getString("CombatInterceptionNote")); return r; }
        public CompoundTag save() { CompoundTag t=new CompoundTag(); t.putString("Id",id); t.putString("Type",type); t.putString("Title",title); t.putString("Body",body); if(actorUuid!=null)t.putUUID("ActorUuid",actorUuid); t.putString("ActorName",actorName); t.putLong("CreatedAt",createdAt); t.putLong("UpdatedAt",updatedAt); t.putString("Status",status); t.putString("SourceType",sourceType); t.putString("SourceId",sourceId); t.putLong("StartAt",startAt); t.putString("Location",location); t.putString("Participants",participants); t.putBoolean("HighPriority",highPriority); if(receiptActorUuid!=null)t.putUUID("ReceiptActorUuid",receiptActorUuid); t.putString("ReceiptActorName",receiptActorName); t.putString("ReceiptNote",receiptNote); t.putLong("ReceiptAt",receiptAt); if(lastActorUuid!=null)t.putUUID("LastActorUuid",lastActorUuid); t.putString("LastActorName",lastActorName); t.putString("CombatInterceptionNote",combatInterceptionNote); return t; }
        public JsonObject toJson() { JsonObject o=new JsonObject(); o.addProperty("id",id); o.addProperty("type",type); o.addProperty("title",title); o.addProperty("body",body); o.addProperty("actorUuid",actorUuid==null?"":actorUuid.toString()); o.addProperty("actorName",actorName); o.addProperty("createdAt",createdAt); o.addProperty("updatedAt",updatedAt); o.addProperty("status",status); o.addProperty("sourceType",sourceType); o.addProperty("sourceId",sourceId); o.addProperty("startAt",startAt); o.addProperty("location",location); o.addProperty("participants",participants); o.addProperty("highPriority",highPriority); o.addProperty("receiptActorUuid",receiptActorUuid==null?"":receiptActorUuid.toString()); o.addProperty("receiptActorName",receiptActorName); o.addProperty("receiptNote",receiptNote); o.addProperty("receiptAt",receiptAt); o.addProperty("lastActorUuid",lastActorUuid==null?"":lastActorUuid.toString()); o.addProperty("lastActorName",lastActorName); o.addProperty("combatInterceptionNote",combatInterceptionNote); return o; }
    }
}
