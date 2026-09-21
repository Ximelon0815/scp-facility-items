package com.example.melonstools.anomaly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/** 当前世界/站点的异常物状态、稳定度、研究进度和实验报告。 */
public class AnomalySavedData extends SavedData {
    public static final String DATA_NAME = "melon_anomaly_site";
    public static final String STATE_STABLE = "stable";
    public static final String STATE_WARNING = "warning";
    public static final String STATE_EMERGENCY = "emergency";
    public static final String STATE_DISABLED = "disabled";

    /** B-stage transport model. C must implement validation, revision conflicts, atomic mutation and audit. */
    public record InstanceParameterUpdate(
            long expectedRevision,
            String state,
            int stability,
            int maxStability,
            int researchProgress,
            int maintenanceIntervalSeconds,
            int lossPerMiss,
            int emergencySeconds,
            String note
    ) { }

    public static class AnomalyInstance {
        public String instanceId;
        public String codexId;
        public String zone = "light";
        public String slotId = "";
        public int layoutX = 0;
        public int layoutY = 0;
        public int stability = 100;
        public int maxStability = 100;
        public int researchProgress = 0;
        public String state = "stable";
        public long lastMaintenanceAt = 0L;
        public int maintenanceIntervalSeconds = 120;
        public int lossPerMiss = 20;
        public int emergencySeconds = 30;
        public long emergencyStartedAt = 0L;
        public long updatedAt = 0L;
        public UUID updatedByUuid = null;
        public String updatedByName = "";
        public long revision = 0L;
        public String operatorNote = "";

        public AnomalyInstance(String instanceId, String codexId) {
            this.instanceId = instanceId;
            this.codexId = codexId;
        }
    }

    public static class RoomSlot {
        public String slotId;
        public String zone;
        public int layoutX;
        public int layoutY;
        public long unlockCost;
        public boolean unlocked;

        public RoomSlot(String slotId, String zone, int layoutX, int layoutY, long unlockCost, boolean unlocked) {
            this.slotId = slotId;
            this.zone = zone;
            this.layoutX = layoutX;
            this.layoutY = layoutY;
            this.unlockCost = unlockCost;
            this.unlocked = unlocked;
        }
    }

    public static class ResearchReport {
        public String reportId;
        public String anomalyInstanceId;
        public UUID authorUuid;
        public String authorName;
        public String title;
        public String content;
        public long submittedAt;
        public String status = "pending";
        public UUID reviewerUuid;
        public String reviewerName;
        public long reviewedAt;
        public String rejectReason;
        public String archivePath;
    }

    private final Map<String, AnomalyInstance> instances = new LinkedHashMap<>();
    private final Map<String, RoomSlot> slots = new LinkedHashMap<>();
    private final Map<String, ResearchReport> reports = new LinkedHashMap<>();

    private void ensureDefaultSlots() {
        addDefaultSlot("L-01", "light", 40, 38, 0, true);
        addDefaultSlot("L-02", "light", 150, 38, 0, true);
        addDefaultSlot("L-03", "light", 260, 38, 1000, false);
        addDefaultSlot("L-04", "light", 370, 38, 1500, false);
        addDefaultSlot("L-05", "light", 480, 38, 2500, false);
        addDefaultSlot("H-01", "heavy", 95, 170, 0, true);
        addDefaultSlot("H-02", "heavy", 255, 170, 3000, false);
        addDefaultSlot("H-03", "heavy", 415, 170, 5000, false);
    }

    private void addDefaultSlot(String id, String zone, int x, int y, long cost, boolean unlocked) {
        if (!slots.containsKey(id)) slots.put(id, new RoomSlot(id, zone, x, y, cost, unlocked));
    }

    public static AnomalySavedData get(ServerLevel level) {
        AnomalySavedData data = level.getDataStorage().computeIfAbsent(AnomalySavedData::load, AnomalySavedData::new, DATA_NAME);
        data.ensureDefaultSlots();
        return data;
    }

    public AnomalyInstance addInstance(String codexId, String zone, int layoutX, int layoutY) {
        String id = "anm_inst_" + System.currentTimeMillis();
        AnomalyInstance inst = new AnomalyInstance(id, codexId);
        inst.zone = zone == null || zone.isBlank() ? "light" : zone;
        inst.layoutX = layoutX;
        inst.layoutY = layoutY;
        inst.lastMaintenanceAt = System.currentTimeMillis();
        instances.put(id, inst);
        setDirty();
        return inst;
    }

    public AnomalyInstance addInstanceToSlot(String codexId, String slotId) {
        ensureDefaultSlots();
        RoomSlot slot = slots.get(slotId);
        if (slot == null || !slot.unlocked || isSlotOccupied(slotId) || codexId == null || codexId.isBlank()) return null;
        AnomalyCodexManager.ensureLoaded();
        if (AnomalyCodexManager.get(codexId.trim()) == null) return null;
        AnomalyInstance inst = addInstance(codexId, slot.zone, slot.layoutX, slot.layoutY);
        inst.slotId = slot.slotId;
        setDirty();
        return inst;
    }

    public boolean unlockSlot(String slotId) {
        ensureDefaultSlots();
        RoomSlot slot = slots.get(slotId);
        if (slot == null || slot.unlocked) return false;
        slot.unlocked = true;
        setDirty();
        return true;
    }

    public boolean removeInstance(String instanceId) {
        if (instances.remove(instanceId) == null) return false;
        setDirty();
        return true;
    }

    public boolean isSlotOccupied(String slotId) {
        for (AnomalyInstance inst : instances.values()) {
            if (slotId != null && slotId.equals(inst.slotId)) return true;
        }
        return false;
    }

    public RoomSlot getSlot(String id) { ensureDefaultSlots(); return slots.get(id); }
    public Collection<RoomSlot> getSlots() { ensureDefaultSlots(); return slots.values(); }

    public AnomalyInstance getInstance(String id) {
        return instances.get(id);
    }

    public static boolean isAllowedState(String state) {
        return STATE_STABLE.equals(state) || STATE_WARNING.equals(state)
                || STATE_EMERGENCY.equals(state) || STATE_DISABLED.equals(state);
    }

    /** Validates the complete request before mutating, so rejected edits cannot partially apply. */
    public boolean updateInstanceParameters(String instanceId, InstanceParameterUpdate update, ServerPlayer operator) {
        AnomalyInstance inst = instances.get(instanceId);
        if (inst == null || update == null || operator == null) return false;
        String state = update.state() == null ? "" : update.state().trim().toLowerCase(java.util.Locale.ROOT);
        String note = limit(update.note() == null ? "" : update.note().trim(), 300);
        if (update.expectedRevision() != inst.revision || !isAllowedState(state)) return false;
        if (update.maxStability() < 1 || update.maxStability() > 100000) return false;
        if (update.stability() < 0 || update.stability() > update.maxStability()) return false;
        if (update.researchProgress() < 0 || update.researchProgress() > 100) return false;
        if (update.maintenanceIntervalSeconds() < 10 || update.maintenanceIntervalSeconds() > 86400) return false;
        if (update.lossPerMiss() < 0 || update.lossPerMiss() > update.maxStability()) return false;
        if (update.emergencySeconds() < 5 || update.emergencySeconds() > 3600) return false;

        inst.state = state;
        inst.maxStability = update.maxStability();
        inst.stability = update.stability();
        inst.researchProgress = update.researchProgress();
        inst.maintenanceIntervalSeconds = update.maintenanceIntervalSeconds();
        inst.lossPerMiss = update.lossPerMiss();
        inst.emergencySeconds = update.emergencySeconds();
        if (!STATE_EMERGENCY.equals(state)) inst.emergencyStartedAt = 0L;
        else if (inst.emergencyStartedAt <= 0L) inst.emergencyStartedAt = System.currentTimeMillis();
        touch(inst, operator, note);
        setDirty();
        return true;
    }

    /** Moves slotId, zone and coordinates as one server-thread transaction. */
    public boolean moveInstance(String instanceId, String targetSlotId, String note, ServerPlayer operator) {
        ensureDefaultSlots();
        AnomalyInstance inst = instances.get(instanceId);
        RoomSlot target = slots.get(targetSlotId);
        if (inst == null || target == null || operator == null || !target.unlocked) return false;
        if (target.slotId.equals(inst.slotId) || isSlotOccupied(target.slotId)) return false;
        inst.slotId = target.slotId;
        inst.zone = target.zone;
        inst.layoutX = target.layoutX;
        inst.layoutY = target.layoutY;
        touch(inst, operator, note);
        setDirty();
        return true;
    }

    /** Emergency timestamps are generated only on the server. */
    public boolean setEmergency(String instanceId, String mode, String note, ServerPlayer operator) {
        AnomalyInstance inst = instances.get(instanceId);
        if (inst == null || operator == null || mode == null) return false;
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        if ("start".equals(normalized)) {
            if (STATE_DISABLED.equals(inst.state)) return false;
            inst.state = STATE_EMERGENCY;
            inst.emergencyStartedAt = System.currentTimeMillis();
        } else if ("clear".equals(normalized)) {
            if (!STATE_EMERGENCY.equals(inst.state) && inst.emergencyStartedAt <= 0L) return false;
            inst.emergencyStartedAt = 0L;
            int percent = stabilityPercent(inst);
            inst.state = inst.stability <= 0 ? STATE_DISABLED : (percent < 50 ? STATE_WARNING : STATE_STABLE);
        } else return false;
        touch(inst, operator, note);
        setDirty();
        return true;
    }

    public boolean markMaintained(String instanceId, int restoreAmount, String note, ServerPlayer operator) {
        AnomalyInstance inst = instances.get(instanceId);
        if (inst == null || operator == null || restoreAmount < 1 || restoreAmount > inst.maxStability) return false;
        inst.stability = Math.min(inst.maxStability, inst.stability + restoreAmount);
        inst.lastMaintenanceAt = System.currentTimeMillis();
        inst.emergencyStartedAt = 0L;
        inst.state = stabilityPercent(inst) < 50 ? STATE_WARNING : STATE_STABLE;
        touch(inst, operator, note);
        setDirty();
        return true;
    }

    private static void touch(AnomalyInstance inst, ServerPlayer operator, String note) {
        inst.updatedAt = System.currentTimeMillis();
        inst.updatedByUuid = operator.getUUID();
        inst.updatedByName = operator.getScoreboardName();
        inst.operatorNote = limit(note == null ? "" : note.trim(), 300);
        inst.revision++;
    }

    public static String auditSummary(AnomalyInstance inst) {
        if (inst == null) return "missing";
        return "rev=" + inst.revision + ",state=" + inst.state + ",stability=" + inst.stability + "/" + inst.maxStability
                + ",research=" + inst.researchProgress + ",slot=" + inst.slotId + ",interval=" + inst.maintenanceIntervalSeconds
                + ",loss=" + inst.lossPerMiss + ",emergencySeconds=" + inst.emergencySeconds;
    }

    public ResearchReport getReport(String id) {
        return reports.get(id);
    }

    public void maintenanceSuccess(String instanceId, int restoreAmount) {
        AnomalyInstance inst = instances.get(instanceId);
        if (inst == null) return;
        inst.stability = Math.min(inst.maxStability, Math.max(0, inst.stability + Math.max(1, restoreAmount)));
        inst.lastMaintenanceAt = System.currentTimeMillis();
        if (inst.stability > 0) {
            inst.state = "stable";
            inst.emergencyStartedAt = 0L;
        }
        setDirty();
    }

    public ResearchReport submitReport(String instanceId, ServerPlayer author, String title, String content) {
        if (author == null || !instances.containsKey(instanceId)) return null;
        String cleanTitle = title == null ? "" : title.trim();
        String cleanContent = content == null ? "" : content.trim();
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return null;
        ResearchReport report = new ResearchReport();
        report.reportId = "report_" + System.currentTimeMillis();
        report.anomalyInstanceId = instanceId;
        report.authorUuid = author.getUUID();
        report.authorName = author.getScoreboardName();
        report.title = limit(cleanTitle, 120);
        report.content = limit(cleanContent, 12000);
        report.submittedAt = System.currentTimeMillis();
        report.status = "pending";
        reports.put(report.reportId, report);
        report.archivePath = exportToFile(author.serverLevel(), report.reportId);
        com.example.melonstools.data.HqTaskSavedData.recordEvent(author.serverLevel(), com.example.melonstools.data.HqTaskSavedData.EVENT_REPORT_SUBMITTED);
        setDirty();
        return report;
    }

    private static String limit(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    public ResearchReport approveReport(String reportId, ServerPlayer reviewer, int researchDelta) {
        ResearchReport report = reports.get(reportId);
        if (report == null || reviewer == null) return null;
        if (!"pending".equals(report.status)) return null;
        AnomalyInstance inst = instances.get(report.anomalyInstanceId);
        if (inst == null) return null;
        report.status = "approved";
        report.reviewerUuid = reviewer.getUUID();
        report.reviewerName = reviewer.getScoreboardName();
        report.reviewedAt = System.currentTimeMillis();
        report.rejectReason = null;
        if (researchDelta > 0) {
            inst.researchProgress = Math.min(100, inst.researchProgress + researchDelta);
        }
        exportToFile(reviewer.serverLevel(), report.reportId);
        com.example.melonstools.data.HqTaskSavedData.recordEvent(reviewer.serverLevel(), com.example.melonstools.data.HqTaskSavedData.EVENT_REPORT_APPROVED);
        setDirty();
        return report;
    }

    public ResearchReport rejectReport(String reportId, ServerPlayer reviewer, String reason) {
        ResearchReport report = reports.get(reportId);
        if (report == null || reviewer == null) return null;
        if (!"pending".equals(report.status)) return null;
        report.status = "rejected";
        report.reviewerUuid = reviewer.getUUID();
        report.reviewerName = reviewer.getScoreboardName();
        report.reviewedAt = System.currentTimeMillis();
        report.rejectReason = limit(reason == null ? "" : reason.trim(), 300);
        exportToFile(reviewer.serverLevel(), report.reportId);
        setDirty();
        return report;
    }

    public ResearchReport archiveReport(String reportId, ServerPlayer reviewer) {
        ResearchReport report = reports.get(reportId);
        if (report == null || reviewer == null) return null;
        report.status = "archived";
        report.reviewerUuid = reviewer.getUUID();
        report.reviewerName = reviewer.getScoreboardName();
        report.reviewedAt = System.currentTimeMillis();
        report.archivePath = exportToFile(reviewer.serverLevel(), reportId);
        setDirty();
        return report;
    }

    public String exportToFile(ServerLevel level, String reportId) {
        ResearchReport report = reports.get(reportId);
        if (report == null) return null;
        AnomalyInstance inst = instances.get(report.anomalyInstanceId);
        java.nio.file.Path out = ReportArchiveManager.exportToFile(level, report, inst);
        String path = out == null ? null : out.toString();
        report.archivePath = path;
        return path;
    }

    public static String researchStageFor(int researchProgress) {
        if (researchProgress <= 0) return "未知";
        if (researchProgress < 50) return "基础";
        if (researchProgress < 100) return "深入";
        return "完成";
    }

    public static int stabilityPercent(AnomalyInstance inst) {
        if (inst == null || inst.maxStability <= 0) return 0;
        return Math.max(0, Math.min(100, (int)Math.round(inst.stability * 100.0D / inst.maxStability)));
    }

    public static String maintenanceStatusFor(AnomalyInstance inst, long now) {
        if (inst == null) return "unknown";
        int pct = stabilityPercent(inst);
        long dueAt = inst.lastMaintenanceAt + Math.max(1, inst.maintenanceIntervalSeconds) * 1000L;
        if (pct < 30 || now >= dueAt) return "urgent";
        if (pct < 50) return "due";
        return "ok";
    }

    /**
     * B 阶段科研部门聚合 JSON 骨架：只从 AnomalySavedData/staffOnline/报告数据派生，
     * 不创建第二份状态源。C 阶段负责补齐更精细的权限过滤和 QTE 绑定摘要。
     */
    public JsonObject buildResearchDepartmentJson(ServerPlayer viewer, JsonObject staffOnline) {
        AnomalyCodexManager.ensureLoaded();
        boolean isOp = viewer != null && viewer.hasPermissions(2);
        String dept = viewer == null ? "" : com.example.melonstools.utils.PhoneUtils.getPlayerDepartment(viewer);
        String position = viewer == null ? "" : com.example.melonstools.utils.PhoneUtils.getPlayerPosition(viewer);
        int level = viewer == null ? 0 : com.example.melonstools.utils.PhoneUtils.getPlayerIdCardLevel(viewer);
        com.example.melonstools.department.DepartmentPolicy.Identity viewerIdentity = com.example.melonstools.department.DepartmentPolicy.identity(dept, position, level);
        boolean inResearch = com.example.melonstools.department.DepartmentPolicy.DEPT_RESEARCH.equals(viewerIdentity.department());
        boolean canViewResearchDetails = isOp || (inResearch && viewerIdentity.level() >= 2);
        boolean canSubmitReports = com.example.melonstools.department.DepartmentPolicy.canSubmitReport(dept, position, level, isOp);
        boolean canReviewReports = com.example.melonstools.department.DepartmentPolicy.canReviewReport(dept, position, level, isOp);
        boolean canManageAnomalies = com.example.melonstools.department.DepartmentPolicy.canManageAnomalies(dept, position, level, isOp);
        long now = System.currentTimeMillis();

        JsonObject root = new JsonObject();
        JsonObject summary = new JsonObject();
        JsonObject reportStats = new JsonObject();
        int pendingReports = 0;
        int lowStability = 0;
        for (ResearchReport report : reports.values()) {
            String status = report.status == null || report.status.isBlank() ? "pending" : report.status;
            reportStats.addProperty(status, reportStats.has(status) ? reportStats.get(status).getAsInt() + 1 : 1);
            if ("pending".equals(status)) pendingReports++;
        }
        for (AnomalyInstance inst : instances.values()) if (stabilityPercent(inst) < 50) lowStability++;
        int onlineResearchers = 0;
        if (staffOnline != null && staffOnline.has("departments") && staffOnline.get("departments").isJsonObject()) {
            JsonObject deps = staffOnline.getAsJsonObject("departments");
            if (deps.has("科研部门")) onlineResearchers = deps.get("科研部门").getAsInt();
        }
        summary.addProperty("onlineResearchers", onlineResearchers);
        summary.addProperty("submittedReports", reports.size());
        summary.addProperty("pendingReports", pendingReports);
        summary.addProperty("anomalyCount", instances.size());
        summary.addProperty("lowStabilityCount", lowStability);
        summary.addProperty("canViewResearchPage", true);
        summary.addProperty("canViewSensitiveDetails", canViewResearchDetails);
        summary.addProperty("canSubmitReports", canSubmitReports);
        summary.addProperty("canReviewReports", canReviewReports);
        summary.addProperty("canManageAnomalies", canManageAnomalies);
        summary.addProperty("canWriteAnomalyDoc", com.example.melonstools.department.DepartmentPolicy.canWriteAnomalyDoc(dept, position, level, isOp));
        summary.addProperty("canWriteAcademicBulletin", com.example.melonstools.department.DepartmentPolicy.canWriteBulletin(dept, position, level, isOp));
        summary.addProperty("isOp", isOp);
        root.add("researchSummary", summary);
        root.add("reportStatusStats", reportStats);

        Map<String, JsonObject> qteByAnomaly = buildQteBindingSummary(viewer);
        JsonArray researchAnomalies = new JsonArray();
        List<JsonObject> maintenanceItems = new ArrayList<>();
        for (AnomalyInstance inst : instances.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("instanceId", inst.instanceId);
            o.addProperty("codexId", canViewResearchDetails ? inst.codexId : "");
            AnomalyCodexEntry entry = AnomalyCodexManager.get(inst.codexId);
            o.addProperty("displayName", entry != null && entry.displayName != null && !entry.displayName.isBlank() ? entry.displayName : inst.codexId);
            o.addProperty("slotId", inst.slotId);
            o.addProperty("zone", inst.zone);
            o.addProperty("researchProgress", inst.researchProgress);
            o.addProperty("researchStage", researchStageFor(inst.researchProgress));
            o.addProperty("stability", inst.stability);
            o.addProperty("maxStability", inst.maxStability);
            o.addProperty("stabilityPercent", stabilityPercent(inst));
            o.addProperty("state", inst.state);
            o.addProperty("lastMaintenanceAt", canViewResearchDetails ? inst.lastMaintenanceAt : 0L);
            o.addProperty("lastMaintenanceText", canViewResearchDetails ? formatTimestamp(inst.lastMaintenanceAt) : "未验证");
            String maintenanceStatus = maintenanceStatusFor(inst, now);
            o.addProperty("maintenanceStatus", maintenanceStatus);
            o.addProperty("maintenanceDue", canViewResearchDetails && !"ok".equals(maintenanceStatus));
            JsonObject boundQte = qteByAnomaly.get(inst.instanceId);
            o.add("boundQte", boundQte == null ? unboundQteSummary() : boundQte);
            if (canViewResearchDetails) {
                if (entry != null) o.add("codex", entry.toVisibleJson(inst.researchProgress, canManageAnomalies));
            }
            researchAnomalies.add(o);
            if (canViewResearchDetails && !"ok".equals(maintenanceStatus)) maintenanceItems.add(o.deepCopy());
        }
        maintenanceItems.sort((a, b) -> {
            int ap = a.has("stabilityPercent") ? a.get("stabilityPercent").getAsInt() : 100;
            int bp = b.has("stabilityPercent") ? b.get("stabilityPercent").getAsInt() : 100;
            return Integer.compare(ap, bp);
        });
        JsonArray maintenanceQueue = new JsonArray();
        for (JsonObject item : maintenanceItems) maintenanceQueue.add(item);
        root.add("researchAnomalies", researchAnomalies);
        root.add("maintenanceQueue", maintenanceQueue);

        JsonArray researchReports = new JsonArray();
        UUID viewerUuid = viewer == null ? null : viewer.getUUID();
        for (ResearchReport report : reports.values()) {
            boolean mine = viewerUuid != null && viewerUuid.equals(report.authorUuid);
            JsonObject o = new JsonObject();
            o.addProperty("reportId", report.reportId);
            o.addProperty("anomalyInstanceId", report.anomalyInstanceId);
            o.addProperty("title", report.title);
            o.addProperty("status", report.status);
            o.addProperty("submittedAt", report.submittedAt);
            o.addProperty("authorName", canViewResearchDetails ? report.authorName : (mine ? report.authorName : ""));
            o.addProperty("reviewerName", report.reviewerName);
            o.addProperty("reviewedAt", report.reviewedAt);
            o.addProperty("rejectReason", mine || canReviewReports ? report.rejectReason : "");
            o.addProperty("archivePath", canReviewReports ? report.archivePath : "");
            o.addProperty("content", canReviewReports || (mine && canSubmitReports) ? report.content : "");
            if (canViewResearchDetails || mine) researchReports.add(o);
        }
        root.add("researchReports", researchReports);
        return root;
    }

    private Map<String, JsonObject> buildQteBindingSummary(ServerPlayer viewer) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        if (viewer == null) return out;
        try {
            com.example.melonstools.qte.QTEBlockManager manager = com.example.melonstools.qte.QTEBlockManager.get(viewer.serverLevel());
            if (manager == null) return out;
            for (Map.Entry<net.minecraft.core.BlockPos, com.example.melonstools.qte.QTEBlockManager.BoundQTE> e : manager.getAllBindings().entrySet()) {
                com.example.melonstools.qte.QTEBlockManager.BoundQTE q = e.getValue();
                if (q == null || !"anomaly_maintenance".equals(q.bindingMode) || q.anomalyInstanceId == null || q.anomalyInstanceId.isBlank()) continue;
                JsonObject o = new JsonObject();
                o.addProperty("status", "verified");
                o.addProperty("bound", true);
                o.addProperty("summary", "已绑定维护点");
                o.addProperty("restoreAmount", q.anomalyRestoreAmount);
                o.addProperty("cooldownSeconds", q.anomalyCooldownSeconds);
                o.addProperty("minCardLevel", q.anomalyMinCardLevel);
                o.addProperty("requiredDepartment", q.anomalyRequiredDepartment);
                // 仅输出安全摘要；不把坐标/命令等 QTE 配置暴露到科研页。
                out.put(q.anomalyInstanceId, o);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static JsonObject unboundQteSummary() {
        JsonObject o = new JsonObject();
        o.addProperty("status", "unbound");
        o.addProperty("bound", false);
        o.addProperty("summary", "未绑定/未验证");
        return o;
    }

    private static String formatTimestamp(long ts) {
        if (ts <= 0L) return "未记录";
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());
        return fmt.format(java.time.Instant.ofEpochMilli(ts));
    }

    public JsonObject buildMyResearchJson(ServerPlayer viewer) {
        com.example.melonstools.department.DepartmentPolicy.Identity viewerIdentity = viewer == null ? com.example.melonstools.department.DepartmentPolicy.identity("", "", 0) : com.example.melonstools.department.DepartmentPolicy.identity(viewer);
        boolean myResearchDept = viewer != null && (viewer.hasPermissions(2) || (com.example.melonstools.department.DepartmentPolicy.DEPT_RESEARCH.equals(viewerIdentity.department()) && viewerIdentity.level() >= 1 && viewerIdentity.level() <= 4));
        if (!myResearchDept) {
            JsonObject root = new JsonObject();
            JsonObject summary = new JsonObject();
            summary.addProperty("canViewResearchPage", false);
            summary.addProperty("canViewSensitiveDetails", false);
            summary.addProperty("canSubmitReports", false);
            summary.addProperty("canReviewReports", false);
            root.add("researchSummary", summary);
            root.add("researchAnomalies", new JsonArray());
            root.add("maintenanceQueue", new JsonArray());
            root.add("researchReports", new JsonArray());
            root.add("reportStatusStats", new JsonObject());
            return root;
        }
        JsonObject root = buildResearchDepartmentJson(viewer, null);
        // 个人终端只提供提交与本人报告查看；审核功能严格留在设施终端。
        if (root.has("researchSummary") && root.get("researchSummary").isJsonObject()) {
            root.getAsJsonObject("researchSummary").addProperty("canReviewReports", false);
        }
        JsonArray mineReports = new JsonArray();
        UUID viewerUuid = viewer == null ? null : viewer.getUUID();
        boolean canSubmitReports = viewer != null && com.example.melonstools.department.DepartmentPolicy.canSubmitReport(viewerIdentity.department(), viewerIdentity.position(), viewerIdentity.level(), viewer.hasPermissions(2));
        for (ResearchReport report : reports.values()) {
            if (viewerUuid == null || !viewerUuid.equals(report.authorUuid)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("reportId", report.reportId);
            o.addProperty("anomalyInstanceId", report.anomalyInstanceId);
            o.addProperty("title", report.title);
            o.addProperty("status", report.status);
            o.addProperty("submittedAt", report.submittedAt);
            o.addProperty("reviewerName", report.reviewerName);
            o.addProperty("reviewedAt", report.reviewedAt);
            o.addProperty("rejectReason", report.rejectReason);
            o.addProperty("content", canSubmitReports ? report.content : "");
            mineReports.add(o);
        }
        root.add("researchReports", mineReports);
        // TODO(C): trim/rename remaining fields for personal terminal UX; never include other players' report content.
        return root;
    }

    public JsonObject toJson(boolean includeAdmin) {
        AnomalyCodexManager.ensureLoaded();
        JsonObject root = new JsonObject();
        root.add("codexStatus", AnomalyCodexManager.statusJson());
        ensureDefaultSlots();
        JsonArray slotArr = new JsonArray();
        for (RoomSlot slot : slots.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("slotId", slot.slotId);
            o.addProperty("zone", slot.zone);
            o.addProperty("layoutX", slot.layoutX);
            o.addProperty("layoutY", slot.layoutY);
            o.addProperty("unlockCost", slot.unlockCost);
            o.addProperty("unlocked", slot.unlocked);
            String occupied = "";
            for (AnomalyInstance inst : instances.values()) if (slot.slotId.equals(inst.slotId)) { occupied = inst.instanceId; break; }
            o.addProperty("occupiedInstanceId", occupied);
            slotArr.add(o);
        }
        root.add("slots", slotArr);
        JsonArray arr = new JsonArray();
        for (AnomalyInstance inst : instances.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("instanceId", inst.instanceId);
            o.addProperty("codexId", inst.codexId);
            o.addProperty("slotId", inst.slotId);
            o.addProperty("zone", inst.zone);
            o.addProperty("layoutX", inst.layoutX);
            o.addProperty("layoutY", inst.layoutY);
            o.addProperty("stability", inst.stability);
            o.addProperty("maxStability", inst.maxStability);
            o.addProperty("researchProgress", inst.researchProgress);
            o.addProperty("state", inst.state);
            o.addProperty("lastMaintenanceAt", inst.lastMaintenanceAt);
            o.addProperty("stabilityPercent", stabilityPercent(inst));
            o.addProperty("researchStage", researchStageFor(inst.researchProgress));
            o.addProperty("maintenanceStatus", maintenanceStatusFor(inst, System.currentTimeMillis()));
            o.addProperty("nextMaintenanceAt", inst.lastMaintenanceAt + Math.max(10, inst.maintenanceIntervalSeconds) * 1000L);
            o.addProperty("emergencyActive", STATE_EMERGENCY.equals(inst.state) && inst.emergencyStartedAt > 0L);
            o.addProperty("emergencyEndsAt", inst.emergencyStartedAt > 0L ? inst.emergencyStartedAt + Math.max(5, inst.emergencySeconds) * 1000L : 0L);
            if (includeAdmin) {
                o.addProperty("maintenanceIntervalSeconds", inst.maintenanceIntervalSeconds);
                o.addProperty("lossPerMiss", inst.lossPerMiss);
                o.addProperty("emergencySeconds", inst.emergencySeconds);
                o.addProperty("emergencyStartedAt", inst.emergencyStartedAt);
                o.addProperty("updatedAt", inst.updatedAt);
                o.addProperty("updatedByUuid", inst.updatedByUuid == null ? "" : inst.updatedByUuid.toString());
                o.addProperty("updatedByName", inst.updatedByName);
                o.addProperty("revision", inst.revision);
                o.addProperty("operatorNote", inst.operatorNote);
            }
            AnomalyCodexEntry entry = AnomalyCodexManager.get(inst.codexId);
            if (entry != null) o.add("codex", entry.toVisibleJson(inst.researchProgress, includeAdmin));
            arr.add(o);
        }
        root.add("instances", arr);
        JsonArray reportArr = new JsonArray();
        for (ResearchReport r : reports.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("reportId", r.reportId);
            o.addProperty("anomalyInstanceId", r.anomalyInstanceId);
            o.addProperty("authorName", r.authorName);
            o.addProperty("title", r.title);
            o.addProperty("content", includeAdmin ? r.content : "");
            o.addProperty("submittedAt", r.submittedAt);
            o.addProperty("status", r.status);
            o.addProperty("reviewerName", r.reviewerName);
            o.addProperty("reviewedAt", r.reviewedAt);
            o.addProperty("rejectReason", r.rejectReason);
            o.addProperty("archivePath", r.archivePath);
            reportArr.add(o);
        }
        root.add("reports", reportArr);
        return root;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag instList = new ListTag();
        for (AnomalyInstance inst : instances.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("instanceId", inst.instanceId);
            t.putString("codexId", inst.codexId);
            t.putString("slotId", inst.slotId);
            t.putString("zone", inst.zone);
            t.putInt("layoutX", inst.layoutX);
            t.putInt("layoutY", inst.layoutY);
            t.putInt("stability", inst.stability);
            t.putInt("maxStability", inst.maxStability);
            t.putInt("researchProgress", inst.researchProgress);
            t.putString("state", inst.state);
            t.putLong("lastMaintenanceAt", inst.lastMaintenanceAt);
            t.putInt("maintenanceIntervalSeconds", inst.maintenanceIntervalSeconds);
            t.putInt("lossPerMiss", inst.lossPerMiss);
            t.putInt("emergencySeconds", inst.emergencySeconds);
            t.putLong("emergencyStartedAt", inst.emergencyStartedAt);
            t.putLong("updatedAt", inst.updatedAt);
            if (inst.updatedByUuid != null) t.putUUID("updatedByUuid", inst.updatedByUuid);
            t.putString("updatedByName", inst.updatedByName == null ? "" : inst.updatedByName);
            t.putLong("revision", inst.revision);
            t.putString("operatorNote", inst.operatorNote == null ? "" : inst.operatorNote);
            instList.add(t);
        }
        tag.put("instances", instList);
        ListTag slotList = new ListTag();
        ensureDefaultSlots();
        for (RoomSlot slot : slots.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("slotId", slot.slotId);
            t.putString("zone", slot.zone);
            t.putInt("layoutX", slot.layoutX);
            t.putInt("layoutY", slot.layoutY);
            t.putLong("unlockCost", slot.unlockCost);
            t.putBoolean("unlocked", slot.unlocked);
            slotList.add(t);
        }
        tag.put("slots", slotList);
        ListTag reportList = new ListTag();
        for (ResearchReport r : reports.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("reportId", r.reportId);
            t.putString("anomalyInstanceId", r.anomalyInstanceId);
            t.putUUID("authorUuid", r.authorUuid);
            t.putString("authorName", r.authorName);
            t.putString("title", r.title);
            t.putString("content", r.content);
            t.putLong("submittedAt", r.submittedAt);
            t.putString("status", r.status);
            if (r.reviewerUuid != null) t.putUUID("reviewerUuid", r.reviewerUuid);
            t.putString("reviewerName", r.reviewerName);
            t.putLong("reviewedAt", r.reviewedAt);
            t.putString("rejectReason", r.rejectReason);
            t.putString("archivePath", r.archivePath);
            reportList.add(t);
        }
        tag.put("reports", reportList);
        return tag;
    }

    public static AnomalySavedData load(CompoundTag tag) {
        AnomalySavedData data = new AnomalySavedData();
        ListTag instList = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < instList.size(); i++) {
            CompoundTag t = instList.getCompound(i);
            AnomalyInstance inst = new AnomalyInstance(t.getString("instanceId"), t.getString("codexId"));
            inst.slotId = t.getString("slotId");
            inst.zone = t.getString("zone");
            inst.layoutX = t.getInt("layoutX");
            inst.layoutY = t.getInt("layoutY");
            inst.maxStability = Math.max(1, Math.min(100000, t.getInt("maxStability")));
            inst.stability = Math.max(0, Math.min(inst.maxStability, t.getInt("stability")));
            inst.researchProgress = Math.max(0, Math.min(100, t.getInt("researchProgress")));
            String loadedState = t.getString("state");
            inst.state = isAllowedState(loadedState) ? loadedState : STATE_STABLE;
            inst.lastMaintenanceAt = t.getLong("lastMaintenanceAt");
            inst.maintenanceIntervalSeconds = t.contains("maintenanceIntervalSeconds") ? t.getInt("maintenanceIntervalSeconds") : 120;
            inst.lossPerMiss = t.contains("lossPerMiss") ? t.getInt("lossPerMiss") : 20;
            inst.emergencySeconds = t.contains("emergencySeconds") ? t.getInt("emergencySeconds") : 30;
            inst.emergencyStartedAt = t.getLong("emergencyStartedAt");
            inst.updatedAt = t.contains("updatedAt") ? t.getLong("updatedAt") : 0L;
            inst.updatedByUuid = t.hasUUID("updatedByUuid") ? t.getUUID("updatedByUuid") : null;
            inst.updatedByName = t.contains("updatedByName") ? t.getString("updatedByName") : "";
            inst.revision = t.contains("revision") ? t.getLong("revision") : 0L;
            inst.operatorNote = t.contains("operatorNote") ? t.getString("operatorNote") : "";
            data.instances.put(inst.instanceId, inst);
        }
        data.ensureDefaultSlots();
        ListTag slotList = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slotList.size(); i++) {
            CompoundTag t = slotList.getCompound(i);
            String id = t.getString("slotId");
            RoomSlot slot = data.slots.get(id);
            if (slot != null) {
                slot.unlocked = t.getBoolean("unlocked");
            } else if (!id.isBlank()) {
                data.slots.put(id, new RoomSlot(id, t.getString("zone"), t.getInt("layoutX"), t.getInt("layoutY"), t.getLong("unlockCost"), t.getBoolean("unlocked")));
            }
        }
        ListTag reportList = tag.getList("reports", Tag.TAG_COMPOUND);
        for (int i = 0; i < reportList.size(); i++) {
            CompoundTag t = reportList.getCompound(i);
            ResearchReport r = new ResearchReport();
            r.reportId = t.getString("reportId");
            r.anomalyInstanceId = t.getString("anomalyInstanceId");
            r.authorUuid = t.hasUUID("authorUuid") ? t.getUUID("authorUuid") : UUID.randomUUID();
            r.authorName = t.getString("authorName");
            r.title = t.getString("title");
            r.content = t.getString("content");
            r.submittedAt = t.getLong("submittedAt");
            r.status = t.getString("status");
            r.reviewerUuid = t.hasUUID("reviewerUuid") ? t.getUUID("reviewerUuid") : null;
            r.reviewerName = t.getString("reviewerName");
            r.reviewedAt = t.getLong("reviewedAt");
            r.rejectReason = t.getString("rejectReason");
            r.archivePath = t.getString("archivePath");
            data.reports.put(r.reportId, r);
        }
        return data;
    }
}
