package com.example.melonstools.department;

/** Phase 1-B compile-only test stub; D/C may replace with real GameTest/JUnit checks. */
public class DepartmentPhase1SkeletonTest {
    public static void main(String[] args) {
        if (!DepartmentPolicy.DEPT_LOGISTICS.equals(DepartmentPolicy.normalizeDepartment("后勤编制"))) {
            throw new AssertionError("后勤编制应归一为后勤部门");
        }
        if (DepartmentPolicy.effectiveLevel(DepartmentPolicy.DEPT_MANAGEMENT, "书记官", 1) != 3) {
            throw new AssertionError("管理部门无1-2级");
        }
        if (DepartmentPolicy.effectiveLevel(DepartmentPolicy.DEPT_RESEARCH, "博士", 5) != 4) {
            throw new AssertionError("科研最高4级");
        }
        if (DepartmentPolicy.isJoinTargetDepartment(DepartmentPolicy.DEPT_MANAGEMENT)) {
            throw new AssertionError("0级入职申请不能申请管理部门");
        }
        if (!"警员".equals(DepartmentPolicy.defaultPositionForLevel(DepartmentPolicy.DEPT_SECURITY, 1))) {
            throw new AssertionError("安保1级目标职位应为警员");
        }
        if (!"初级研究员".equals(DepartmentPolicy.defaultPositionForLevel(DepartmentPolicy.DEPT_RESEARCH, 1))) {
            throw new AssertionError("科研1级目标职位应为初级研究员");
        }
        if (!"勤务".equals(DepartmentPolicy.defaultPositionForLevel(DepartmentPolicy.DEPT_LOGISTICS, 1))) {
            throw new AssertionError("后勤1级目标职位应为勤务");
        }
    }
}
