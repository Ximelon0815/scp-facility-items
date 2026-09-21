package com.example.melonstools.department;

/** Phase 4-B permission/state-machine smoke tests; no JUnit dependency required. */
public final class DepartmentPhase4SkeletonTest {
    private DepartmentPhase4SkeletonTest() {}

    public static boolean security4CanPublishSecurityTask() {
        return DepartmentPolicy.canPublishDepartmentTask("安保部门", "安保主管", 4, "安保部门", false);
    }

    public static boolean security4CannotPublishResearchTask() {
        return !DepartmentPolicy.canPublishDepartmentTask("安保部门", "安保主管", 4, "科研部门", false);
    }

    public static boolean facilityDirectorCanCrossDispatch() {
        return DepartmentPolicy.canCreateDispatch("管理部门", "设施主管", 5, "科研部门", false);
    }

    public static boolean vitalAlertVisibilityAndManagement() {
        return DepartmentPolicy.canViewVitalAlerts("安保部门", "警员", 1, false)
                && DepartmentPolicy.canManageVitalAlert("安保部门", "中士", 3, false)
                && !DepartmentPolicy.canManageVitalAlert("科研部门", "博士", 4, false);
    }

    public static boolean skeletonCompiles() {
        return security4CanPublishSecurityTask()
                && security4CannotPublishResearchTask()
                && facilityDirectorCanCrossDispatch()
                && vitalAlertVisibilityAndManagement();
    }
}
