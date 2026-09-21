package com.example.melonstools.department;

import com.example.melonstools.idcard.item.custom.IdentityCardItem;
import com.example.melonstools.utils.PhoneUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Phase 0-B unified department identity/permission skeleton.
 *
 * <p>This class is intentionally read-only: it never writes CardDepartment,
 * CardPosition, CardLevel, or any other ID-card NBT. C phase may expand the
 * matrices and migration hints, but all terminal/server permission checks should
 * route through these helpers instead of ad-hoc string/level checks.</p>
 */
public final class DepartmentPolicy {
    public static final String DEPT_MANAGEMENT = "管理部门";
    public static final String DEPT_RESEARCH = "科研部门";
    public static final String DEPT_SECURITY = "安保部门";
    public static final String DEPT_LOGISTICS = "后勤部门";
    public static final String DEPT_TEMP_LEGACY = "临时聘用人员";
    public static final String DEPT_D_CLASS_LEGACY = "D级部门";

    public static final String POS_TEMP = "临时人员";
    public static final String POS_TEST_SUBJECT = "受试人员";
    public static final String POS_CLERK = "书记官";
    public static final String POS_ETHICS_ASSISTANT = "道德助理";
    public static final String POS_ETHICS_INSPECTOR = "伦理检察官";
    public static final String POS_SAFETY_AGENT = "安全代理";
    public static final String POS_FACILITY_DIRECTOR = "设施主管";

    public static final String NOTICE_ORDINARY = "ordinary";
    public static final String NOTICE_DEPARTMENT = "department";
    public static final String NOTICE_FACILITY = "facility";
    public static final String NOTICE_EMERGENCY = "emergency";

    private DepartmentPolicy() {
    }

    public record Identity(String department, String position, int level, boolean legacyTemporary) {
        public boolean isTemporary() {
            return level <= 0 && DEPT_LOGISTICS.equals(department);
        }
    }

    public static Identity identity(String department, String position, int level) {
        boolean legacyTemporary = isLegacyTemporaryDepartment(department) || isTemporaryPosition(position);
        String normalizedDepartment = legacyTemporary ? DEPT_LOGISTICS : normalizeDepartment(department);
        int normalizedLevel = effectiveLevel(department, position, level);
        String normalizedPosition = normalizePosition(normalizedDepartment, position, normalizedLevel, legacyTemporary);
        return new Identity(normalizedDepartment, normalizedPosition, normalizedLevel, legacyTemporary);
    }

    public static Identity identity(ItemStack card) {
        if (card == null || card.isEmpty()) return identity("", "", 0);
        return identity(
                IdentityCardItem.getCardDepartment(card),
                IdentityCardItem.getCardPosition(card),
                IdentityCardItem.getCardLevel(card)
        );
    }

    public static Identity identity(ServerPlayer player) {
        if (player == null) return identity("", "", 0);
        ItemStack card = PhoneUtils.findIdentityCard(player);
        return identity(card);
    }

    public static String normalizeDepartment(String rawDepartment) {
        if (rawDepartment == null) return "";
        String value = rawDepartment.trim();
        if (value.isEmpty() || "未知".equals(value)) return "";
        if (isLegacyTemporaryDepartment(value)) return DEPT_LOGISTICS;
        return switch (value) {
            case DEPT_MANAGEMENT, "管理" -> DEPT_MANAGEMENT;
            case DEPT_RESEARCH, "科研" -> DEPT_RESEARCH;
            case DEPT_SECURITY, "安保" -> DEPT_SECURITY;
            case DEPT_LOGISTICS, "后勤", "后勤编制" -> DEPT_LOGISTICS;
            default -> value;
        };
    }

    public static String normalizePosition(String department, String rawPosition, int level) {
        return normalizePosition(department, rawPosition, level, isTemporaryPosition(rawPosition));
    }

    private static String normalizePosition(String department, String rawPosition, int level, boolean legacyTemporary) {
        String value = rawPosition == null ? "" : rawPosition.trim();
        if (legacyTemporary || (DEPT_LOGISTICS.equals(department) && level <= 0)) {
            if (POS_TEST_SUBJECT.equals(value)) return POS_TEST_SUBJECT;
            return POS_TEMP;
        }
        return value;
    }

    public static String effectiveDepartmentForCard(String cardDepartment, String cardPosition, int cardLevel) {
        return identity(cardDepartment, cardPosition, cardLevel).department();
    }

    public static int effectiveLevel(String cardDepartment, String cardPosition, int cardLevel) {
        if (isLegacyTemporaryDepartment(cardDepartment) || isTemporaryPosition(cardPosition)) return 0;
        int level = Math.max(0, cardLevel);
        String dept = normalizeDepartment(cardDepartment);
        if (DEPT_MANAGEMENT.equals(dept)) {
            if (level == 1 || level == 2) return 3;
            return Math.min(level, 5);
        }
        if (DEPT_RESEARCH.equals(dept) || DEPT_SECURITY.equals(dept) || DEPT_LOGISTICS.equals(dept)) {
            return Math.min(level, 4);
        }
        return level;
    }

    public static boolean isLegacyTemporaryDepartment(String rawDepartment) {
        return DEPT_TEMP_LEGACY.equals(rawDepartment) || DEPT_D_CLASS_LEGACY.equals(rawDepartment);
    }

    public static boolean isTemporaryPosition(String rawPosition) {
        return POS_TEMP.equals(rawPosition) || POS_TEST_SUBJECT.equals(rawPosition);
    }

    public static boolean isTemporaryIdentity(String department, String position, int level) {
        return identity(department, position, level).isTemporary();
    }

    public static boolean isFacilityDirector(String department, String position, int level) {
        Identity id = identity(department, position, level);
        return DEPT_MANAGEMENT.equals(id.department()) && POS_FACILITY_DIRECTOR.equals(id.position()) && id.level() == 5;
    }

    public static boolean isFacilityDirector(ServerPlayer player) {
        Identity id = identity(player);
        return isFacilityDirector(id.department(), id.position(), id.level());
    }

    public static boolean isDeptSupervisor(String department, String position, int level) {
        Identity id = identity(department, position, level);
        if (isFacilityDirector(id.department(), id.position(), id.level())) return true;
        return switch (id.department()) {
            case DEPT_SECURITY -> isSecuritySupervisor(id.department(), id.position(), id.level());
            case DEPT_RESEARCH -> isResearchSupervisor(id.department(), id.position(), id.level());
            case DEPT_LOGISTICS -> isLogisticsSupervisor(id.department(), id.position(), id.level());
            default -> false;
        };
    }

    public static boolean canSubmitReport(String department, String position, int level, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        return DEPT_RESEARCH.equals(id.department()) && id.level() >= 1 && id.level() <= 4;
    }

    public static boolean canReviewReport(String department, String position, int level, boolean isOp) {
        return isOp || isFacilityDirector(department, position, level);
    }

    public static boolean canReviewReport(ServerPlayer player) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        Identity id = identity(player);
        return canReviewReport(id.department(), id.position(), id.level(), false);
    }

    /** Central anomaly-management policy: OP, management level 4+, or research level 4. */
    public static boolean canManageAnomalies(String department, String position, int level, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        return (DEPT_MANAGEMENT.equals(id.department()) && id.level() >= 4)
                || (DEPT_RESEARCH.equals(id.department()) && id.level() == 4);
    }

    public static boolean canManageAnomalies(ServerPlayer player) {
        if (player == null) return false;
        Identity id = identity(player);
        return canManageAnomalies(id.department(), id.position(), id.level(), player.hasPermissions(2));
    }

    /** Facility expansion is deliberately narrower than anomaly management. */
    public static boolean canExpandFacility(String department, String position, int level, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        return DEPT_MANAGEMENT.equals(id.department()) && id.level() >= 4;
    }

    public static boolean canExpandFacility(ServerPlayer player) {
        if (player == null) return false;
        Identity id = identity(player);
        return canExpandFacility(id.department(), id.position(), id.level(), player.hasPermissions(2));
    }

    public static boolean canWriteAnomalyDoc(String department, String position, int level, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        return DEPT_RESEARCH.equals(id.department()) && id.level() == 4;
    }

    public static boolean canWriteBulletin(String department, String position, int level, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        return DEPT_RESEARCH.equals(id.department()) && id.level() >= 3 && id.level() <= 4;
    }

    public static boolean isClerk(String department, String position, int level) {
        Identity id = identity(department, position, level);
        return DEPT_MANAGEMENT.equals(id.department()) && id.level() == 3 && POS_CLERK.equals(id.position());
    }

    public static boolean isEthicsInspector(String department, String position, int level) {
        Identity id = identity(department, position, level);
        return DEPT_MANAGEMENT.equals(id.department()) && id.level() == 4 && POS_ETHICS_INSPECTOR.equals(id.position());
    }

    public static boolean isSafetyAgent(String department, String position, int level) {
        Identity id = identity(department, position, level);
        return DEPT_MANAGEMENT.equals(id.department()) && id.level() == 4 && POS_SAFETY_AGENT.equals(id.position());
    }

    public static String normalizeNoticeLevel(String rawLevel) {
        String value = rawLevel == null ? "" : rawLevel.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "department", "dept", "二级部门通告", "部门通告" -> NOTICE_DEPARTMENT;
            case "facility", "broadcast", "一级设施广播", "设施广播" -> NOTICE_FACILITY;
            case "emergency", "alert", "lockdown", "evacuation", "紧急", "警戒", "封锁", "疏散" -> NOTICE_EMERGENCY;
            default -> NOTICE_ORDINARY;
        };
    }

    public static boolean isOrdinaryNotice(String noticeLevel) { return NOTICE_ORDINARY.equals(normalizeNoticeLevel(noticeLevel)); }
    public static boolean isDepartmentNotice(String noticeLevel) { return NOTICE_DEPARTMENT.equals(normalizeNoticeLevel(noticeLevel)); }
    public static boolean isFacilityNotice(String noticeLevel) { return NOTICE_FACILITY.equals(normalizeNoticeLevel(noticeLevel)); }
    public static boolean isEmergencyNotice(String noticeLevel) { return NOTICE_EMERGENCY.equals(normalizeNoticeLevel(noticeLevel)); }

    public static boolean canPublishNotice(String department, String position, int level, String noticeLevel, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        String nl = normalizeNoticeLevel(noticeLevel);
        if (isFacilityDirector(id.department(), id.position(), id.level())) return true;
        // Phase 3-C: only real clerks may publish ordinary notices. Moral assistants,
        // ethics inspectors and safety agents are oversight/read-only placeholders here.
        if (NOTICE_ORDINARY.equals(nl)) return isClerk(id.department(), id.position(), id.level());
        // Department notices are owned by the exact target department supervisor path in
        // ServerPacketHandler; generic management-4 titles do not auto-gain publish power.
        if (NOTICE_DEPARTMENT.equals(nl)) return isDeptSupervisor(id.department(), id.position(), id.level());
        return false; // facility/emergency broadcast is facility-director or OP only.
    }

    public static boolean canPublishNotice(String department, String position, int level, boolean emergency, boolean isOp) {
        return canPublishNotice(department, position, level, emergency ? NOTICE_EMERGENCY : NOTICE_ORDINARY, isOp);
    }

    public static boolean canManageNotice(String actorDepartment, String actorPosition, int actorLevel, java.util.UUID actorUuid, String noticeLevel, java.util.UUID ownerUuid, String targetDepartment, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(actorDepartment, actorPosition, actorLevel);
        String nl = normalizeNoticeLevel(noticeLevel);
        if (isFacilityDirector(id.department(), id.position(), id.level())) return true;
        if (NOTICE_EMERGENCY.equals(nl) || NOTICE_FACILITY.equals(nl)) return false;
        if (NOTICE_DEPARTMENT.equals(nl)) return isDeptSupervisor(id.department(), id.position(), id.level()) && id.department().equals(normalizeDepartment(targetDepartment));
        if (!NOTICE_ORDINARY.equals(nl)) return false;
        // Legacy notices without OwnerUuid stay ordinary-visible, but a clerk cannot
        // claim them by matching old AuthorUuid or null ownership. Facility director/OP only above.
        if (ownerUuid == null) return false;
        return isClerk(id.department(), id.position(), id.level()) && actorUuid != null && actorUuid.equals(ownerUuid);
    }

    public static boolean canApproveApplicationStep(String department, String position, int level, String targetDepartment, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        if (isFacilityDirector(id.department(), id.position(), id.level())) return true;
        return isDeptSupervisor(id.department(), id.position(), id.level()) && id.department().equals(normalizeDepartment(targetDepartment));
    }

    public static boolean isResearchSupervisor(String department, String position, int level) {
        Identity id = identity(department, position, level);
        return DEPT_RESEARCH.equals(id.department()) && id.level() == 4
                && ("科研主管".equals(id.position()) || "科研部门主管".equals(id.position()));
    }

    public static boolean isSecuritySupervisor(String department, String position, int level) {
        Identity id = identity(department, position, level);
        return DEPT_SECURITY.equals(id.department()) && id.level() == 4
                && ("安保主管".equals(id.position()) || "安保部门主管".equals(id.position()));
    }

    public static boolean isLogisticsSupervisor(String department, String position, int level) {
        Identity id = identity(department, position, level);
        return DEPT_LOGISTICS.equals(id.department()) && id.level() == 4
                && ("后勤主管".equals(id.position()) || "后勤部门主管".equals(id.position()));
    }

    public static boolean canCompleteCardQueue(String department, String position, int level, boolean isOp) {
        return isOp || isFacilityDirector(department, position, level) || isLogisticsSupervisor(department, position, level);
    }

    /** Phase 5-B helper skeleton: low-value procurement can be handled by logistics level 3+, final route filled by C. */
    public static boolean canHandleLowValueProcurement(String department, String position, int level, boolean isOp) {
        if (isOp || isFacilityDirector(department, position, level)) return true;
        Identity id = identity(department, position, level);
        return DEPT_LOGISTICS.equals(id.department()) && id.level() >= 3 && id.level() <= 4;
    }

    /** Phase 5-B helper skeleton: high-value/danger procurement requires logistics 4 or facility-director/OP; weapon checks stay server-side. */
    public static boolean canConfirmHighValueProcurement(String department, String position, int level, boolean isOp) {
        return isOp || isFacilityDirector(department, position, level) || isLogisticsSupervisor(department, position, level);
    }

    /** Phase 5-B helper skeleton: facility payments must reference an approved application before execution. */
    public static boolean canApproveFacilityPayment(String department, String position, int level, long amount, String risk, boolean isOp) {
        if (isOp || isFacilityDirector(department, position, level)) return true;
        Identity id = identity(department, position, level);
        return amount > 0 && amount <= 1000L && canHandleLowValueProcurement(id.department(), id.position(), id.level(), false);
    }

    public static boolean hasApprovedApplicationSource(String sourceApplicationId) {
        return sourceApplicationId != null && !sourceApplicationId.trim().isEmpty();
    }

    public static boolean canPublishDepartmentTask(String department, String position, int level, String targetDepartment, boolean isOp) {
        if (isOp || isFacilityDirector(department, position, level)) return true;
        Identity id = identity(department, position, level);
        return isDeptSupervisor(id.department(), id.position(), id.level()) && id.department().equals(normalizeDepartment(targetDepartment));
    }

    public static boolean canCreateDispatch(String department, String position, int level, String targetDepartment, boolean isOp) {
        if (isOp || isFacilityDirector(department, position, level)) return true;
        Identity id = identity(department, position, level);
        return isDeptSupervisor(id.department(), id.position(), id.level()) && id.department().equals(normalizeDepartment(targetDepartment));
    }

    public static boolean canViewVitalAlerts(String department, String position, int level, boolean isOp) {
        if (isOp || isFacilityDirector(department, position, level)) return true;
        Identity id = identity(department, position, level);
        return DEPT_SECURITY.equals(id.department()) && id.level() >= 1 && id.level() <= 4;
    }

    public static boolean canManageVitalAlert(String department, String position, int level, boolean isOp) {
        if (isOp || isFacilityDirector(department, position, level)) return true;
        Identity id = identity(department, position, level);
        return DEPT_SECURITY.equals(id.department()) && id.level() >= 3 && id.level() <= 4;
    }

    public static boolean canClaimSecurityTask(String department, String position, int level, boolean isOp) {
        if (isOp) return true;
        Identity id = identity(department, position, level);
        return DEPT_SECURITY.equals(id.department()) && id.level() >= 1 && id.level() <= 4;
    }

    public static boolean isJoinTargetDepartment(String department) {
        String dept = normalizeDepartment(department);
        return DEPT_SECURITY.equals(dept) || DEPT_RESEARCH.equals(dept) || DEPT_LOGISTICS.equals(dept);
    }

    public static String defaultPositionForLevel(String department, int level) {
        String dept = normalizeDepartment(department);
        if (level <= 0 && DEPT_LOGISTICS.equals(dept)) return POS_TEMP;
        if (level == 1) {
            return switch (dept) {
                case DEPT_SECURITY -> "警员";
                case DEPT_RESEARCH -> "初级研究员";
                case DEPT_LOGISTICS -> "勤务";
                default -> "";
            };
        }
        return "";
    }
}
