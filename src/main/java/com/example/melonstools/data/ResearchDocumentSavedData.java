package com.example.melonstools.data;

import com.example.melonstools.anomaly.AnomalySavedData;
import com.example.melonstools.department.DepartmentPolicy;
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

/** Phase 2-C real research document business data: anomaly documents and academic bulletins. */
public class ResearchDocumentSavedData extends SavedData {
    public static final String DATA_NAME = "melon_research_documents";
    public static final String TYPE_ANOMALY_DOCUMENT = "anomaly_document";
    public static final String TYPE_DOCUMENT_REVISION = "document_revision";
    public static final String TYPE_ACADEMIC_BULLETIN = "academic_bulletin";
    public static final String TARGET_REPORT = "report";
    public static final String TARGET_DOCUMENT = "document";
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_ARCHIVED = "archived";

    private final List<ResearchDocument> documents = new ArrayList<>();
    private int sequence;
    public static ResearchDocumentSavedData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(ResearchDocumentSavedData::load, ResearchDocumentSavedData::new, DATA_NAME); }
    public static ResearchDocumentSavedData load(CompoundTag tag) { ResearchDocumentSavedData data = new ResearchDocumentSavedData(); data.sequence = tag.getInt("Sequence"); ListTag list = tag.getList("Documents", Tag.TAG_COMPOUND); for (int i=0;i<list.size();i++) data.documents.add(ResearchDocument.load(list.getCompound(i))); return data; }
    @Override public CompoundTag save(CompoundTag tag) { tag.putInt("Sequence", sequence); ListTag list = new ListTag(); for (ResearchDocument doc:documents) list.add(doc.save()); tag.put("Documents", list); return tag; }
    public List<ResearchDocument> documents() { return Collections.unmodifiableList(documents); }
    public ResearchDocument find(String id) { for (ResearchDocument d:documents) if (d.id.equals(id)) return d; return null; }

    public ResearchDocument saveDraft(ServerLevel level, ServerPlayer actor, ResearchDocument input) {
        if (level == null || actor == null || input == null) return null;
        input.type = normalizeType(input.type);
        if (!TYPE_ANOMALY_DOCUMENT.equals(input.type)) return null;
        if (!canWriteAnomalyDraft(actor, input)) return null;
        if (!validAnomaly(level, input.anomalyInstanceId)) return null;
        String title = limit(input.title == null ? "" : input.title.trim(), 120), body = limit(input.body == null ? "" : input.body.trim(), 20000);
        if (title.isEmpty() || body.isEmpty()) return null;
        long now = System.currentTimeMillis();
        ResearchDocument doc = (input.id == null || input.id.isBlank()) ? null : find(input.id);
        if (doc == null) { doc = new ResearchDocument(); doc.id = "rdoc-" + (++sequence); documents.add(doc); stampAuthor(actor, doc, now); doc.version = 1; }
        if (!STATUS_DRAFT.equals(doc.status)) return null;
        doc.type = TYPE_ANOMALY_DOCUMENT; doc.title = title; doc.body = body; doc.anomalyInstanceId = input.anomalyInstanceId; doc.status = STATUS_DRAFT; doc.updatedAt = now;
        setDirty(); return doc;
    }

    public ResearchDocument submit(ServerLevel level, ServerPlayer actor, ResearchDocument input) {
        ResearchDocument doc = saveDraft(level, actor, input);
        if (doc == null) return null;
        doc.status = STATUS_SUBMITTED; doc.updatedAt = System.currentTimeMillis(); setDirty(); return doc;
    }

    public ResearchDocument revise(ServerLevel level, ServerPlayer actor, String id, String title, String body, String summary) {
        ResearchDocument doc = find(id);
        if (level == null || actor == null || doc == null || !TYPE_ANOMALY_DOCUMENT.equals(doc.type) || !STATUS_SUBMITTED.equals(doc.status)) return null;
        if (!canWrite(actor, TYPE_ANOMALY_DOCUMENT) || !validAnomaly(level, doc.anomalyInstanceId)) return null;
        String nt = limit(title == null ? "" : title.trim(), 120), nb = limit(body == null ? "" : body.trim(), 20000);
        if (nt.isEmpty() || nb.isEmpty()) return null;
        Revision rev = new Revision(); rev.version = doc.version; rev.actorUuid = actor.getUUID().toString(); rev.actorName = actor.getScoreboardName(); rev.summary = limit(summary == null || summary.isBlank() ? ("修订前: " + doc.title) : summary, 500); rev.createdAt = System.currentTimeMillis(); rev.title = doc.title; rev.body = doc.body; doc.revisions.add(rev);
        doc.version++; doc.title = nt; doc.body = nb; doc.updatedAt = rev.createdAt; setDirty(); return doc;
    }

    public ResearchDocument archive(ServerPlayer actor, String id, String reason) {
        ResearchDocument doc = find(id);
        if (actor == null || doc == null || !TYPE_ANOMALY_DOCUMENT.equals(doc.type)) return null;
        if (!(actor.hasPermissions(2) || DepartmentPolicy.isFacilityDirector(actor))) return null;
        doc.status = STATUS_ARCHIVED; doc.archiveReason = limit(reason, 500); doc.updatedAt = System.currentTimeMillis(); setDirty(); return doc;
    }

    public ResearchDocument submitBulletin(ServerLevel level, ServerPlayer actor, ResearchDocument input) {
        if (level == null || actor == null || input == null || !canWrite(actor, TYPE_ACADEMIC_BULLETIN)) return null;
        String title = limit(input.title == null ? "" : input.title.trim(), 120), body = limit(input.body == null ? "" : input.body.trim(), 8000);
        boolean hasReport = input.reportId != null && !input.reportId.isBlank() && AnomalySavedData.get(level).getReport(input.reportId) != null;
        boolean hasDoc = input.documentId != null && !input.documentId.isBlank() && find(input.documentId) != null;
        if (title.isEmpty() || body.isEmpty() || hasReport == hasDoc) return null;
        ResearchDocument doc = new ResearchDocument(); doc.id = "rdoc-" + (++sequence); doc.type = TYPE_ACADEMIC_BULLETIN; doc.title = title; doc.body = body; doc.status = STATUS_SUBMITTED; doc.targetType = hasReport ? TARGET_REPORT : TARGET_DOCUMENT; doc.reportId = hasReport ? input.reportId : ""; doc.documentId = hasDoc ? input.documentId : ""; stampAuthor(actor, doc, System.currentTimeMillis()); documents.add(doc); setDirty(); return doc;
    }

    public JsonObject toJsonFor(ServerPlayer viewer, boolean terminalSide) { JsonObject root = new JsonObject(); JsonArray arr = new JsonArray(); for (ResearchDocument doc:documents) if (canView(viewer, doc, terminalSide)) arr.add(doc.toJson()); root.add("items", arr); root.addProperty("stateMachine", "anomaly_document: draft -> submitted -> archived; revise records immutable revisions; bulletin: submitted immutable/no delete"); return root; }
    public boolean canWrite(ServerPlayer actor, String type) { if (actor == null) return false; DepartmentPolicy.Identity id = DepartmentPolicy.identity(actor); boolean op = actor.hasPermissions(2); if (TYPE_ANOMALY_DOCUMENT.equals(type)) return DepartmentPolicy.canWriteAnomalyDoc(id.department(), id.position(), id.level(), op); if (TYPE_ACADEMIC_BULLETIN.equals(type)) return DepartmentPolicy.canWriteBulletin(id.department(), id.position(), id.level(), op); return false; }
    private boolean canWriteAnomalyDraft(ServerPlayer actor, ResearchDocument input) { if (!canWrite(actor, TYPE_ANOMALY_DOCUMENT)) return false; if (actor.hasPermissions(2)) return true; ResearchDocument old = input.id == null ? null : find(input.id); return old == null || old.authorUuid.equals(actor.getUUID().toString()); }
    private boolean canView(ServerPlayer viewer, ResearchDocument doc, boolean terminalSide) { if (viewer == null || doc == null) return false; if (viewer.hasPermissions(2) || (terminalSide && DepartmentPolicy.isFacilityDirector(viewer))) return true; DepartmentPolicy.Identity id = DepartmentPolicy.identity(viewer); if (DepartmentPolicy.DEPT_RESEARCH.equals(id.department()) && id.level() >= 1) return STATUS_SUBMITTED.equals(doc.status) || STATUS_ARCHIVED.equals(doc.status) || doc.authorUuid.equals(viewer.getUUID().toString()); return false; }
    private static boolean validAnomaly(ServerLevel level, String id) { return id != null && !id.isBlank() && AnomalySavedData.get(level).getInstance(id) != null; }
    private static String normalizeType(String t) { return TYPE_ACADEMIC_BULLETIN.equals(t) ? TYPE_ACADEMIC_BULLETIN : TYPE_ANOMALY_DOCUMENT; }
    private static String limit(String s, int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(0, max); }
    private static void stampAuthor(ServerPlayer actor, ResearchDocument doc, long now) { DepartmentPolicy.Identity id = DepartmentPolicy.identity(actor); if (doc.createdAt <= 0L) doc.createdAt = now; doc.updatedAt = now; doc.authorUuid = actor.getUUID().toString(); doc.authorName = actor.getScoreboardName(); doc.authorDepartment = id.department(); doc.authorPosition = id.position(); doc.authorLevel = id.level(); doc.authorSnapshot = id.department()+"/"+id.position()+"/L"+id.level(); }

    public static class ResearchDocument { public String id="", type=TYPE_ANOMALY_DOCUMENT, title="", body="", status=STATUS_DRAFT, authorUuid="", authorName="", authorDepartment="", authorPosition="", authorSnapshot="", anomalyInstanceId="", parentDocumentId="", targetType="", reportId="", documentId="", archiveReason=""; public int authorLevel, version=1; public long createdAt, updatedAt; public final List<Revision> revisions = new ArrayList<>();
        public CompoundTag save() { CompoundTag t = new CompoundTag(); t.putString("Id",id);t.putString("Type",type);t.putString("Title",title);t.putString("Body",body);t.putString("Status",status);t.putString("AuthorUuid",authorUuid);t.putString("AuthorName",authorName);t.putString("AuthorDepartment",authorDepartment);t.putString("AuthorPosition",authorPosition);t.putInt("AuthorLevel",authorLevel);t.putString("AuthorSnapshot",authorSnapshot);t.putString("AnomalyInstanceId",anomalyInstanceId);t.putString("ParentDocumentId",parentDocumentId);t.putString("TargetType",targetType);t.putString("ReportId",reportId);t.putString("DocumentId",documentId);t.putString("ArchiveReason", archiveReason);t.putInt("Version",version);t.putLong("CreatedAt",createdAt);t.putLong("UpdatedAt",updatedAt); ListTag list=new ListTag(); for(Revision r:revisions) list.add(r.save()); t.put("Revisions",list); return t; }
        public static ResearchDocument load(CompoundTag t) { ResearchDocument d = new ResearchDocument(); d.id=t.getString("Id");d.type=t.getString("Type");d.title=t.getString("Title");d.body=t.getString("Body");d.status=t.getString("Status");d.authorUuid=t.getString("AuthorUuid");d.authorName=t.getString("AuthorName");d.authorDepartment=t.getString("AuthorDepartment");d.authorPosition=t.getString("AuthorPosition");d.authorLevel=t.getInt("AuthorLevel");d.authorSnapshot=t.getString("AuthorSnapshot");d.anomalyInstanceId=t.getString("AnomalyInstanceId");d.parentDocumentId=t.getString("ParentDocumentId");d.targetType=t.getString("TargetType");d.reportId=t.getString("ReportId");d.documentId=t.getString("DocumentId");d.archiveReason=t.getString("ArchiveReason");d.version=t.getInt("Version"); if(d.version<=0)d.version=1; d.createdAt=t.getLong("CreatedAt");d.updatedAt=t.getLong("UpdatedAt"); ListTag list=t.getList("Revisions", Tag.TAG_COMPOUND); for(int i=0;i<list.size();i++) d.revisions.add(Revision.load(list.getCompound(i))); return d; }
        public JsonObject toJson() { JsonObject o=new JsonObject(); o.addProperty("id",id);o.addProperty("type",type);o.addProperty("title",title);o.addProperty("body",body);o.addProperty("status",status);o.addProperty("authorUuid",authorUuid);o.addProperty("authorName",authorName);o.addProperty("authorDepartment",authorDepartment);o.addProperty("authorPosition",authorPosition);o.addProperty("authorLevel",authorLevel);o.addProperty("authorSnapshot",authorSnapshot);o.addProperty("anomalyInstanceId",anomalyInstanceId);o.addProperty("parentDocumentId",parentDocumentId);o.addProperty("targetType",targetType);o.addProperty("reportId",reportId);o.addProperty("documentId",documentId);o.addProperty("archiveReason", archiveReason);o.addProperty("version",version);o.addProperty("createdAt",createdAt);o.addProperty("updatedAt",updatedAt); JsonArray arr=new JsonArray(); for(Revision r:revisions) arr.add(r.toJson()); o.add("revisions",arr); return o; }}
    public static class Revision { public int version; public String actorUuid="", actorName="", summary="", title="", body=""; public long createdAt; public CompoundTag save(){ CompoundTag t=new CompoundTag(); t.putInt("Version",version);t.putString("ActorUuid",actorUuid);t.putString("ActorName",actorName);t.putString("Summary",summary);t.putString("Title",title);t.putString("Body",body);t.putLong("CreatedAt",createdAt);return t;} public static Revision load(CompoundTag t){ Revision r=new Revision();r.version=t.getInt("Version");r.actorUuid=t.getString("ActorUuid");r.actorName=t.getString("ActorName");r.summary=t.getString("Summary");r.title=t.getString("Title");r.body=t.getString("Body");r.createdAt=t.getLong("CreatedAt");return r;} public JsonObject toJson(){ JsonObject o=new JsonObject();o.addProperty("version",version);o.addProperty("actorUuid",actorUuid);o.addProperty("actorName",actorName);o.addProperty("summary",summary);o.addProperty("title",title);o.addProperty("body",body);o.addProperty("createdAt",createdAt);return o; }}
}
