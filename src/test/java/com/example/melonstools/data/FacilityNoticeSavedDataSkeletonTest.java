package com.example.melonstools.data;

/** B 阶段公告系统编译测试桩：不依赖 JUnit，仅确保 test 源集可编译。 */
public final class FacilityNoticeSavedDataSkeletonTest {
    private FacilityNoticeSavedDataSkeletonTest() {
    }

    public static boolean skeletonCompiles() {
        return FacilityNoticeSavedData.DATA_NAME.equals("melon_facility_notices");
    }
}
