package com.example.melonstools.department;

/** Phase 0-B unified department policy compile-time smoke tests; no JUnit dependency required. */
public final class DepartmentPolicySkeletonTest {
    private DepartmentPolicySkeletonTest() {
    }

    public static boolean facilityDirectorCanReviewReports() {
        return DepartmentPolicy.canReviewReport("管理部门", "设施主管", 5, false);
    }

    public static boolean researchLevelFourCannotReviewReports() {
        return !DepartmentPolicy.canReviewReport("科研部门", "博士", 4, false)
                && !DepartmentPolicy.canReviewReport("科研部门", "科研主管", 4, false);
    }

    public static boolean opCanReviewReports() {
        return DepartmentPolicy.canReviewReport("科研部门", "博士", 4, true);
    }

    public static boolean temporaryZeroLevelIsRecognizedReadOnly() {
        DepartmentPolicy.Identity legacyDClass = DepartmentPolicy.identity("D级部门", "", 3);
        DepartmentPolicy.Identity legacyTemp = DepartmentPolicy.identity("临时聘用人员", "", 2);
        DepartmentPolicy.Identity explicitSubject = DepartmentPolicy.identity("后勤编制", "受试人员", 1);
        return legacyDClass.isTemporary()
                && legacyTemp.isTemporary()
                && explicitSubject.isTemporary()
                && "后勤部门".equals(legacyDClass.department())
                && legacyDClass.level() == 0;
    }

    public static boolean skeletonCompiles() {
        return facilityDirectorCanReviewReports()
                && researchLevelFourCannotReviewReports()
                && opCanReviewReports()
                && temporaryZeroLevelIsRecognizedReadOnly();
    }
}
