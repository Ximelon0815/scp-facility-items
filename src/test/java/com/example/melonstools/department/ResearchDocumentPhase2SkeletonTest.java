package com.example.melonstools.department;

import com.example.melonstools.data.DepartmentApplicationSavedData;
import com.example.melonstools.data.ResearchDocumentSavedData;

/** Phase 2-B compile-time smoke tests; no JUnit dependency required. */
public final class ResearchDocumentPhase2SkeletonTest {
    private ResearchDocumentPhase2SkeletonTest() {}

    public static boolean research4CanWriteAnomalyDocument() {
        return DepartmentPolicy.canWriteAnomalyDoc("科研部门", "博士", 4, false)
                && DepartmentPolicy.canWriteAnomalyDoc("科研部门", "科研主管", 4, false);
    }

    public static boolean research3CanWriteAcademicBulletin() {
        return DepartmentPolicy.canWriteBulletin("科研部门", "高级研究员", 3, false);
    }

    public static boolean research2CannotWriteAcademicBulletin() {
        return !DepartmentPolicy.canWriteBulletin("科研部门", "中级研究员", 2, false);
    }

    public static boolean research4CannotReviewReport() {
        return !DepartmentPolicy.canReviewReport("科研部门", "博士", 4, false);
    }

    public static boolean skeletonConstantsExist() {
        return ResearchDocumentSavedData.TYPE_ANOMALY_DOCUMENT.equals("anomaly_document")
                && ResearchDocumentSavedData.TYPE_ACADEMIC_BULLETIN.equals("academic_bulletin")
                && DepartmentApplicationSavedData.TYPE_RESEARCH_EXPERIMENT.equals("RESEARCH_EXPERIMENT")
                && DepartmentApplicationSavedData.TYPE_RESEARCH_PURCHASE.equals("RESEARCH_PURCHASE");
    }

    public static boolean skeletonCompiles() {
        return research4CanWriteAnomalyDocument()
                && research3CanWriteAcademicBulletin()
                && research2CannotWriteAcademicBulletin()
                && research4CannotReviewReport()
                && skeletonConstantsExist();
    }
}
