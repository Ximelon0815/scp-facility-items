package com.example.melonstools.anomaly;

import com.example.melonstools.department.DepartmentPolicy;

/** Dependency-free Phase 1 policy/state regression checks, executable by the existing test harness. */
public final class AnomalyDetailPhase1SkeletonTest {
    private AnomalyDetailPhase1SkeletonTest() {
    }

    public static boolean skeletonCompiles() {
        AnomalySavedData.InstanceParameterUpdate update = new AnomalySavedData.InstanceParameterUpdate(
                0L, AnomalySavedData.STATE_STABLE, 100, 100, 0, 120, 20, 30, ""
        );
        return update.expectedRevision() == 0L
                && AnomalySavedData.isAllowedState(AnomalySavedData.STATE_STABLE)
                && AnomalySavedData.isAllowedState(AnomalySavedData.STATE_WARNING)
                && AnomalySavedData.isAllowedState(AnomalySavedData.STATE_EMERGENCY)
                && AnomalySavedData.isAllowedState(AnomalySavedData.STATE_DISABLED)
                && !AnomalySavedData.isAllowedState("deleted")
                && DepartmentPolicy.canManageAnomalies("科研部门", "科研主管", 4, false)
                && !DepartmentPolicy.canManageAnomalies("科研部门", "研究员", 3, false)
                && DepartmentPolicy.canManageAnomalies("管理部门", "伦理检察官", 4, false)
                && !DepartmentPolicy.canManageAnomalies("管理部门", "书记官", 3, false)
                && !DepartmentPolicy.canManageAnomalies("安保部门", "安保主管", 4, false)
                && !DepartmentPolicy.canManageAnomalies("后勤部门", "后勤主管", 4, false)
                && !DepartmentPolicy.canExpandFacility("科研部门", "科研主管", 4, false)
                && DepartmentPolicy.canExpandFacility("管理部门", "伦理检察官", 4, false)
                && !DepartmentPolicy.canReviewReport("科研部门", "科研主管", 4, false);
    }
}
