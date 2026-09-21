package com.example.melonstools.data;

import com.example.melonstools.department.DepartmentPolicy;
import com.example.melonstools.utils.PhoneUtils;
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
import java.util.Locale;

/** Phase 1-C universal department application data. Server-side authority only; never writes ID-card NBT. */
public class DepartmentApplicationSavedData extends SavedData {
    public static final String DATA_NAME = "melon_department_applications";
    public static final String TYPE_JOIN_DEPARTMENT = "JOIN_DEPARTMENT";
    public static final String TYPE_RESEARCH_EXPERIMENT = "RESEARCH_EXPERIMENT";
    public static final String TYPE_RESEARCH_PURCHASE = "RESEARCH_PURCHASE";
    public static final String RISK_LOW = "low";
    public static final String RISK_HIGH = "high";
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_DEPT_REVIEW = "dept_review";
    public static final String STATUS_LOGISTICS_CONFIRM = "logistics_confirm";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_CARD_CHANGE_REQUIRED = "card_change_required";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_ARCHIVED = "archived";

    private final List<Application> applications = new ArrayList<>();
    private int sequence;

    public static DepartmentApplicationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DepartmentApplicationSavedData::load, DepartmentApplicationSavedData::new, DATA_NAME);
    }

    public static DepartmentApplicationSavedData load(CompoundTag tag) {
        DepartmentApplicationSavedData data = new DepartmentApplicationSavedData();
        data.sequence = tag.getInt("Sequence");
        ListTag list = tag.getList("Applications", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.applications.add(Application.load(list.getCompound(i)));
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Sequence", sequence);
        ListTag list = new ListTag();
        for (Application a : applications) list.add(a.save());
        tag.put("Applications", list);
        return tag;
    }

    public List<Application> applications() { return Collections.unmodifiableList(applications); }

    public Application submit(ServerPlayer applicant, String type, String targetDepartment, String reason) {
        if (applicant == null) return null;
        String normalizedType = normalizeType(type);
        if (!TYPE_JOIN_DEPARTMENT.equals(normalizedType)) return null;

        DepartmentPolicy.Identity actor = DepartmentPolicy.identity(PhoneUtils.getPlayerDepartment(applicant), PhoneUtils.getPlayerPosition(applicant), PhoneUtils.getPlayerIdCardLevel(applicant));
        if (!actor.isTemporary()) return null;

        String target = DepartmentPolicy.normalizeDepartment(targetDepartment);
        if (!DepartmentPolicy.isJoinTargetDepartment(target)) return null;
        if (hasOpenJoinApplication(applicant.getUUID().toString())) return null;

        long now = System.currentTimeMillis();
        Application a = new Application();
        a.id = "app-" + (++sequence);
        a.type = TYPE_JOIN_DEPARTMENT;
        a.title = "加入" + target + "申请";
        a.description = safe(reason);
        a.reason = safe(reason);
        a.status = STATUS_DEPT_REVIEW;
        a.applicantUuid = applicant.getUUID().toString();
        a.applicantName = applicant.getScoreboardName();
        a.applicantDepartment = actor.department();
        a.applicantPosition = actor.position();
        a.applicantLevel = actor.level();
        a.targetDepartment = target;
        a.targetLevel = 1;
        a.targetPosition = defaultLevelOnePosition(target);
        a.createdAt = now;
        a.updatedAt = now;
        a.steps.add(ApprovalStep.pending("dept", "target_department_supervisor", target, 4));
        a.steps.add(ApprovalStep.pending("logistics", "logistics_staffing_confirmation", DepartmentPolicy.DEPT_LOGISTICS, 4));
        applications.add(a);
        setDirty();
        return a;
    }

    public Application submitResearchSkeleton(ServerPlayer applicant, JsonObject input) {
        if (applicant == null || input == null) return null;
        String type = normalizeType(input.has("type") ? input.get("type").getAsString() : TYPE_RESEARCH_EXPERIMENT);
        if (!TYPE_RESEARCH_EXPERIMENT.equals(type) && !TYPE_RESEARCH_PURCHASE.equals(type)) return null;
        DepartmentPolicy.Identity actor = DepartmentPolicy.identity(applicant);
        if (!DepartmentPolicy.DEPT_RESEARCH.equals(actor.department()) || actor.level() < 1 || actor.level() > 4) return null;
        long now = System.currentTimeMillis();
        Application a = new Application();
        a.id = "app-" + (++sequence);
        a.type = type;
        a.title = limit(input.has("title") ? safe(input.get("title").getAsString()) : (TYPE_RESEARCH_EXPERIMENT.equals(type) ? "科研实验申请" : "科研物资申请"), 120);
        a.description = limit(input.has("description") ? safe(input.get("description").getAsString()) : "", 8000);
        a.reason = limit(input.has("reason") ? safe(input.get("reason").getAsString()) : a.description, 8000);
        a.status = STATUS_DEPT_REVIEW;
        a.applicantUuid = applicant.getUUID().toString();
        a.applicantName = applicant.getScoreboardName();
        a.applicantDepartment = actor.department();
        a.applicantPosition = actor.position();
        a.applicantLevel = actor.level();
        a.riskLevel = normalizeRisk(input.has("riskLevel") ? safe(input.get("riskLevel").getAsString()) : RISK_LOW);
        a.relatedAnomalyId = input.has("anomalyInstanceId") ? safe(input.get("anomalyInstanceId").getAsString()) : (input.has("relatedAnomalyId") ? safe(input.get("relatedAnomalyId").getAsString()) : "");
        if (TYPE_RESEARCH_EXPERIMENT.equals(type) && (a.relatedAnomalyId.isBlank() || com.example.melonstools.anomaly.AnomalySavedData.get(applicant.serverLevel()).getInstance(a.relatedAnomalyId) == null)) return null;
        a.relatedReportId = input.has("relatedReportId") ? safe(input.get("relatedReportId").getAsString()) : "";
        a.requestedPlayers = input.has("requestedPlayers") ? input.get("requestedPlayers").toString() : "[]";
        a.requestedItems = input.has("requestedItems") ? input.get("requestedItems").toString() : "[]";
        a.targetDepartment = input.has("targetDepartment") ? DepartmentPolicy.normalizeDepartment(input.get("targetDepartment").getAsString()) : "";
        boolean facilityExtra = input.has("highValue") && input.get("highValue").getAsBoolean() || input.has("highRisk") && input.get("highRisk").getAsBoolean() || RISK_HIGH.equals(a.riskLevel);
        a.approvalRoute = TYPE_RESEARCH_EXPERIMENT.equals(type) ? (RISK_HIGH.equals(a.riskLevel) ? "research_supervisor4>facility_director5" : "research_supervisor4") : (facilityExtra ? "research_supervisor4>logistics_supervisor4>facility_director5" : "research_supervisor4>logistics_supervisor4");
        a.createdAt = now;
        a.updatedAt = now;
        a.steps.add(ApprovalStep.pending("research", "research_supervisor_review", DepartmentPolicy.DEPT_RESEARCH, 4));
        if (TYPE_RESEARCH_PURCHASE.equals(type)) a.steps.add(ApprovalStep.pending("materials", "logistics_execution_confirm", DepartmentPolicy.DEPT_LOGISTICS, 4));
        if (TYPE_RESEARCH_EXPERIMENT.equals(type) && RISK_HIGH.equals(a.riskLevel)) a.steps.add(ApprovalStep.pending("facility", "facility_director_review", DepartmentPolicy.DEPT_MANAGEMENT, 5));
        if (TYPE_RESEARCH_EXPERIMENT.equals(type) && !a.targetDepartment.isBlank()) a.steps.add(ApprovalStep.pending("staffing_" + a.targetDepartment, "target_department_supervisor", a.targetDepartment, 4));
        if (TYPE_RESEARCH_EXPERIMENT.equals(type) && !"[]".equals(a.requestedItems)) a.steps.add(ApprovalStep.pending("materials", "logistics_material_confirm", DepartmentPolicy.DEPT_LOGISTICS, 4));
        if (TYPE_RESEARCH_PURCHASE.equals(type) && facilityExtra) a.steps.add(ApprovalStep.pending("facility", "facility_director_high_value_or_risk", DepartmentPolicy.DEPT_MANAGEMENT, 5));
        applications.add(a);
        setDirty();
        return a;
    }

    public boolean cancel(String id, ServerPlayer actor) {
        Application a = find(id);
        if (a == null || actor == null) return false;
        if (!a.applicantUuid.equals(actor.getUUID().toString())) return false;
        if (!isCancellable(a.status)) return false;
        a.status = STATUS_CANCELLED;
        addDecision(a, actor, STATUS_CANCELLED, "applicant_cancel", "cancel", "applicant");
        setDirty();
        return true;
    }

    public boolean reject(String id, ServerPlayer actor, String comment) {
        Application a = find(id);
        if (a == null || isTerminal(a.status)) return false;
        ApprovalStep step = nextPendingStep(a);
        if (step == null || !canDecideStep(actor, step, a.targetDepartment)) return false;
        step.actorUuid = actor.getUUID().toString();
        step.actorName = actor.getScoreboardName();
        step.status = STATUS_REJECTED;
        step.comment = safe(comment);
        step.decidedAt = System.currentTimeMillis();
        a.status = STATUS_REJECTED;
        a.updatedAt = step.decidedAt;
        setDirty();
        return true;
    }

    /** Approves the next routed step. Facility director/OP may complete all remaining steps in one call. */
    public Application approve(String id, ServerPlayer actor, String comment) {
        Application a = find(id);
        if (a == null || actor == null || isTerminal(a.status)) return null;
        boolean elevated = actor.hasPermissions(2) || DepartmentPolicy.isFacilityDirector(actor);
        boolean changed = false;
        for (ApprovalStep step : a.steps) {
            if (!isPendingStep(step)) continue;
            if (!elevated && !canDecideStep(actor, step, a.targetDepartment)) return null;
            approveStep(step, actor, comment, elevated ? "facility_or_op_route" : "normal_route");
            changed = true;
            if (!elevated) break;
        }
        if (!changed) return null;
        if (allStepsApproved(a)) {
            if (TYPE_JOIN_DEPARTMENT.equals(a.type)) {
                a.status = STATUS_CARD_CHANGE_REQUIRED;
                a.targetLevel = 1;
                a.targetPosition = defaultLevelOnePosition(a.targetDepartment);
            } else {
                a.status = STATUS_APPROVED;
            }
        } else {
            ApprovalStep next = nextPendingStep(a);
            a.status = next != null && "logistics".equals(next.stepId) ? STATUS_LOGISTICS_CONFIRM : STATUS_DEPT_REVIEW;
        }
        a.updatedAt = System.currentTimeMillis();
        setDirty();
        return a;
    }

    public Application approve(String id, ServerPlayer actor) { return approve(id, actor, ""); }

    public Application find(String id) { for (Application a : applications) if (a.id.equals(id)) return a; return null; }

    public JsonObject toJsonFor(ServerPlayer viewer) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        boolean op = viewer != null && viewer.hasPermissions(2);
        String vu = viewer == null ? "" : viewer.getUUID().toString();
        for (Application a : applications) if (op || a.applicantUuid.equals(vu) || canViewAsApprover(viewer, a)) arr.add(a.toJson());
        root.add("items", arr);
        root.addProperty("stateMachine", "submitted/dept_review/logistics_confirm/card_change_required/completed or rejected/cancelled/archived");
        root.addProperty("joinRule", "0级后勤编制临时/受试人员仅可申请安保/科研/后勤；目标为1级职位；终端不改卡NBT");
        return root;
    }

    private boolean hasOpenJoinApplication(String applicantUuid) {
        for (Application a : applications) {
            if (applicantUuid.equals(a.applicantUuid) && TYPE_JOIN_DEPARTMENT.equals(a.type) && !isTerminal(a.status)) return true;
        }
        return false;
    }

    private boolean canViewAsApprover(ServerPlayer viewer, Application a) {
        if (viewer == null) return false;
        ApprovalStep step = nextPendingStep(a);
        return step != null && canDecideStep(viewer, step, a.targetDepartment);
    }

    private static boolean canDecideStep(ServerPlayer actor, ApprovalStep step, String targetDepartment) {
        if (actor == null) return false;
        if (actor.hasPermissions(2) || DepartmentPolicy.isFacilityDirector(actor)) return true;
        DepartmentPolicy.Identity id = DepartmentPolicy.identity(actor);
        if ("dept".equals(step.stepId)) return DepartmentPolicy.isDeptSupervisor(id.department(), id.position(), id.level()) && id.department().equals(DepartmentPolicy.normalizeDepartment(targetDepartment));
        if ("research".equals(step.stepId)) return DepartmentPolicy.isResearchSupervisor(id.department(), id.position(), id.level());
        if ("materials".equals(step.stepId) || "logistics".equals(step.stepId)) return DepartmentPolicy.isLogisticsSupervisor(id.department(), id.position(), id.level());
        if ("facility".equals(step.stepId)) return DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level());
        if (step.stepId != null && step.stepId.startsWith("staffing_")) return DepartmentPolicy.isDeptSupervisor(id.department(), id.position(), id.level()) && id.department().equals(DepartmentPolicy.normalizeDepartment(step.department));
        return false;
    }

    private static ApprovalStep nextPendingStep(Application a) { for (ApprovalStep s : a.steps) if (isPendingStep(s)) return s; return null; }
    private static boolean isPendingStep(ApprovalStep s) { return STATUS_SUBMITTED.equals(s.status) || STATUS_DEPT_REVIEW.equals(s.status) || STATUS_LOGISTICS_CONFIRM.equals(s.status); }
    private static boolean allStepsApproved(Application a) { for (ApprovalStep s : a.steps) if (!STATUS_APPROVED.equals(s.status)) return false; return true; }
    private static void approveStep(ApprovalStep step, ServerPlayer actor, String comment, String route) { step.actorUuid = actor.getUUID().toString(); step.actorName = actor.getScoreboardName(); step.status = STATUS_APPROVED; step.decidedAt = System.currentTimeMillis(); step.comment = safe(comment); step.route = route; }
    private static boolean isCancellable(String status) { return STATUS_SUBMITTED.equals(status) || STATUS_DEPT_REVIEW.equals(status) || STATUS_LOGISTICS_CONFIRM.equals(status); }
    private static boolean isTerminal(String status) { return STATUS_REJECTED.equals(status) || STATUS_CANCELLED.equals(status) || STATUS_CARD_CHANGE_REQUIRED.equals(status) || STATUS_COMPLETED.equals(status) || STATUS_ARCHIVED.equals(status); }
    private static String normalizeType(String type) { String v = safe(type).trim(); if (v.isEmpty() || "join_department".equalsIgnoreCase(v)) return TYPE_JOIN_DEPARTMENT; return v.toUpperCase(Locale.ROOT); }
    private static String normalizeRisk(String risk) { return RISK_HIGH.equalsIgnoreCase(safe(risk)) ? RISK_HIGH : RISK_LOW; }
    private static String defaultLevelOnePosition(String dept) { return DepartmentPolicy.defaultPositionForLevel(DepartmentPolicy.normalizeDepartment(dept), 1); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String limit(String s, int max) { String v = safe(s).trim(); return v.length() <= max ? v : v.substring(0, max); }

    private static void addDecision(Application a, ServerPlayer actor, String status, String comment, String stepId, String role) {
        ApprovalStep step = ApprovalStep.pending(stepId, role, "", 0);
        step.actorUuid = actor == null ? "" : actor.getUUID().toString();
        step.actorName = actor == null ? "SYSTEM" : actor.getScoreboardName();
        step.status = status;
        step.decidedAt = System.currentTimeMillis();
        step.comment = safe(comment);
        a.steps.add(step);
        a.updatedAt = step.decidedAt;
    }

    public static class ApprovalStep {
        public String stepId = "", role = "", department = "", positionRequired = "", actorUuid = "", actorName = "", status = STATUS_SUBMITTED, comment = "", parallelGroup = "", route = "";
        public int minLevel;
        public long decidedAt;
        static ApprovalStep pending(String stepId, String role, String department, int minLevel) { ApprovalStep s = new ApprovalStep(); s.stepId = stepId; s.role = role; s.department = department; s.minLevel = minLevel; s.status = "logistics".equals(stepId) ? STATUS_LOGISTICS_CONFIRM : STATUS_DEPT_REVIEW; return s; }
        public CompoundTag save() { CompoundTag t = new CompoundTag(); t.putString("StepId", stepId); t.putString("Role", role); t.putString("Department", department); t.putInt("MinLevel", minLevel); t.putString("PositionRequired", positionRequired); t.putString("ActorUuid", actorUuid); t.putString("ActorName", actorName); t.putString("Status", status); t.putLong("DecidedAt", decidedAt); t.putString("Comment", comment); t.putString("ParallelGroup", parallelGroup); t.putString("Route", route); return t; }
        public static ApprovalStep load(CompoundTag t) { ApprovalStep s = new ApprovalStep(); s.stepId=t.getString("StepId"); s.role=t.getString("Role"); s.department=t.getString("Department"); s.minLevel=t.getInt("MinLevel"); s.positionRequired=t.getString("PositionRequired"); s.actorUuid=t.getString("ActorUuid"); s.actorName=t.getString("ActorName"); s.status=t.getString("Status"); if(s.status.isBlank()) s.status = STATUS_SUBMITTED; s.decidedAt=t.getLong("DecidedAt"); s.comment=t.getString("Comment"); s.parallelGroup=t.getString("ParallelGroup"); s.route=t.getString("Route"); return s; }
        public JsonObject toJson() { JsonObject o = new JsonObject(); o.addProperty("stepId", stepId); o.addProperty("role", role); o.addProperty("department", department); o.addProperty("minLevel", minLevel); o.addProperty("positionRequired", positionRequired); o.addProperty("actorUuid", actorUuid); o.addProperty("actorName", actorName); o.addProperty("status", status); o.addProperty("decidedAt", decidedAt); o.addProperty("comment", comment); o.addProperty("route", route); return o; }
    }

    public static class Application {
        public String id = "", type = "", title = "", description = "", applicantUuid = "", applicantName = "", applicantDepartment = "", applicantPosition = "", targetDepartment = "", targetPosition = "", reason = "", status = STATUS_DRAFT, cardQueueId = "";
        public String riskLevel = "", relatedAnomalyId = "", relatedReportId = "", requestedPlayers = "[]", requestedItems = "[]", approvalRoute = "";
        public int applicantLevel, targetLevel = 1;
        public long createdAt, updatedAt;
        public List<ApprovalStep> steps = new ArrayList<>();
        public CompoundTag save() { CompoundTag t = new CompoundTag(); t.putString("Id", id); t.putString("Type", type); t.putString("Title", title); t.putString("Description", description); t.putString("ApplicantUuid", applicantUuid); t.putString("ApplicantName", applicantName); t.putString("ApplicantDepartment", applicantDepartment); t.putString("ApplicantPosition", applicantPosition); t.putInt("ApplicantLevel", applicantLevel); t.putString("TargetDepartment", targetDepartment); t.putString("TargetPosition", targetPosition); t.putInt("TargetLevel", targetLevel); t.putString("Reason", reason); t.putString("RiskLevel", riskLevel); t.putString("RelatedAnomalyId", relatedAnomalyId); t.putString("RelatedReportId", relatedReportId); t.putString("RequestedPlayers", requestedPlayers); t.putString("RequestedItems", requestedItems); t.putString("ApprovalRoute", approvalRoute); t.putString("Status", status); t.putString("CardQueueId", cardQueueId); t.putLong("CreatedAt", createdAt); t.putLong("UpdatedAt", updatedAt); ListTag l = new ListTag(); for (ApprovalStep s : steps) l.add(s.save()); t.put("Steps", l); return t; }
        public static Application load(CompoundTag t) { Application a = new Application(); a.id=t.getString("Id"); a.type=t.getString("Type"); if (a.type.isEmpty()) a.type = TYPE_JOIN_DEPARTMENT; a.title=t.getString("Title"); a.description=t.getString("Description"); a.applicantUuid=t.getString("ApplicantUuid"); a.applicantName=t.getString("ApplicantName"); a.applicantDepartment=t.getString("ApplicantDepartment"); a.applicantPosition=t.getString("ApplicantPosition"); a.applicantLevel=t.getInt("ApplicantLevel"); a.targetDepartment=t.getString("TargetDepartment"); a.targetPosition=t.getString("TargetPosition"); a.targetLevel=t.getInt("TargetLevel"); if(a.targetLevel<=0)a.targetLevel=1; a.reason=t.getString("Reason"); a.riskLevel=t.getString("RiskLevel"); a.relatedAnomalyId=t.getString("RelatedAnomalyId"); a.relatedReportId=t.getString("RelatedReportId"); a.requestedPlayers=t.getString("RequestedPlayers"); if(a.requestedPlayers.isEmpty())a.requestedPlayers="[]"; a.requestedItems=t.getString("RequestedItems"); if(a.requestedItems.isEmpty())a.requestedItems="[]"; a.approvalRoute=t.getString("ApprovalRoute"); a.status=t.getString("Status"); if(a.status.isBlank()) a.status = STATUS_DEPT_REVIEW; a.cardQueueId=t.getString("CardQueueId"); a.createdAt=t.getLong("CreatedAt"); a.updatedAt=t.getLong("UpdatedAt"); ListTag l=t.getList("Steps", Tag.TAG_COMPOUND); for(int i=0;i<l.size();i++) a.steps.add(ApprovalStep.load(l.getCompound(i))); return a; }
        public JsonObject toJson() { JsonObject o = new JsonObject(); o.addProperty("id", id); o.addProperty("type", type); o.addProperty("title", title); o.addProperty("description", description); o.addProperty("applicantUuid", applicantUuid); o.addProperty("applicantName", applicantName); o.addProperty("applicantDepartment", applicantDepartment); o.addProperty("applicantPosition", applicantPosition); o.addProperty("applicantLevel", applicantLevel); o.addProperty("targetDepartment", targetDepartment); o.addProperty("targetPosition", targetPosition); o.addProperty("targetLevel", targetLevel); o.addProperty("reason", reason); o.addProperty("riskLevel", riskLevel); o.addProperty("relatedAnomalyId", relatedAnomalyId); o.addProperty("relatedReportId", relatedReportId); o.addProperty("requestedPlayers", requestedPlayers); o.addProperty("requestedItems", requestedItems); o.addProperty("approvalRoute", approvalRoute); o.addProperty("status", status); o.addProperty("cardQueueId", cardQueueId); o.addProperty("createdAt", createdAt); o.addProperty("updatedAt", updatedAt); JsonArray arr = new JsonArray(); for (ApprovalStep s : steps) arr.add(s.toJson()); o.add("steps", arr); return o; }
    }
}
