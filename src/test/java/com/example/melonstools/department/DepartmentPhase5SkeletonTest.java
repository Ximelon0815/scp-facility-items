package com.example.melonstools.department;

import com.example.melonstools.data.FacilityPaymentSavedData;
import com.example.melonstools.data.PersonalTransferService;
import com.example.melonstools.data.ProcurementSavedData;

import java.util.UUID;

/** Phase 5 compile-time smoke checks; this project does not depend on JUnit. */
public final class DepartmentPhase5SkeletonTest {
    private DepartmentPhase5SkeletonTest() {}

    public static boolean procurementAndPaymentConstantsExist() {
        return "request".equals(ProcurementSavedData.REQUEST)
                && "approved".equals(ProcurementSavedData.APPROVED)
                && "paid".equals(FacilityPaymentSavedData.PAID)
                && DepartmentPolicy.hasApprovedApplicationSource("app-1");
    }

    public static boolean personalTransferServiceExposesAuditedExecution() {
        var request = new PersonalTransferService.TransferRequest(
                UUID.randomUUID(), "a", UUID.randomUUID(), "b", 10L, "note");
        var result = PersonalTransferService.execute(request);
        return result != null && !"lc_api_unverified".equals(result.code());
    }

    public static boolean skeletonCompiles() {
        return procurementAndPaymentConstantsExist()
                && personalTransferServiceExposesAuditedExecution();
    }
}
