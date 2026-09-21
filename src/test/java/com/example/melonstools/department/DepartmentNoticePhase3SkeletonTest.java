package com.example.melonstools.department;

import java.util.UUID;

/** Phase 3-B notice permission smoke tests; no JUnit dependency required. */
public final class DepartmentNoticePhase3SkeletonTest {
    private DepartmentNoticePhase3SkeletonTest() {}

    public static boolean clerk3OrdinaryAllowed() {
        return DepartmentPolicy.canPublishNotice("管理部门", "书记官", 3, DepartmentPolicy.NOTICE_ORDINARY, false);
    }

    public static boolean ethicsAssistant3OrdinaryDenied() {
        return !DepartmentPolicy.canPublishNotice("管理部门", "道德助理", 3, DepartmentPolicy.NOTICE_ORDINARY, false);
    }

    public static boolean supervisor4DepartmentAllowed() {
        return DepartmentPolicy.canPublishNotice("安保部门", "安保主管", 4, DepartmentPolicy.NOTICE_DEPARTMENT, false);
    }

    public static boolean management4TitlesDoNotPublishNotices() {
        return !DepartmentPolicy.canPublishNotice("管理部门", "伦理检察官", 4, DepartmentPolicy.NOTICE_ORDINARY, false)
                && !DepartmentPolicy.canPublishNotice("管理部门", "安全代理", 4, DepartmentPolicy.NOTICE_DEPARTMENT, false);
    }

    public static boolean facilityDirector5FacilityAllowed() {
        return DepartmentPolicy.canPublishNotice("管理部门", "设施主管", 5, DepartmentPolicy.NOTICE_FACILITY, false);
    }

    public static boolean clerk3EmergencyDenied() {
        return !DepartmentPolicy.canPublishNotice("管理部门", "书记官", 3, DepartmentPolicy.NOTICE_EMERGENCY, false);
    }

    public static boolean clerkOnlyManagesOwnOrdinaryNotice() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        return DepartmentPolicy.canManageNotice("管理部门", "书记官", 3, owner, DepartmentPolicy.NOTICE_ORDINARY, owner, "ALL", false)
                && !DepartmentPolicy.canManageNotice("管理部门", "书记官", 3, owner, DepartmentPolicy.NOTICE_ORDINARY, other, "ALL", false)
                && !DepartmentPolicy.canManageNotice("管理部门", "书记官", 3, owner, DepartmentPolicy.NOTICE_ORDINARY, null, "ALL", false)
                && !DepartmentPolicy.canManageNotice("管理部门", "书记官", 3, owner, DepartmentPolicy.NOTICE_EMERGENCY, owner, "ALL", false);
    }

    public static boolean skeletonCompiles() {
        return clerk3OrdinaryAllowed()
                && ethicsAssistant3OrdinaryDenied()
                && supervisor4DepartmentAllowed()
                && management4TitlesDoNotPublishNotices()
                && facilityDirector5FacilityAllowed()
                && clerk3EmergencyDenied()
                && clerkOnlyManagesOwnOrdinaryNotice();
    }
}
