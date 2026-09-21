package com.example.melonstools.data;

import com.example.melonstools.idcard.LCIntegration;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server-authoritative LC personal bank transfer service. Never uses FacilityFundManager. */
public final class PersonalTransferService {
    private PersonalTransferService() {}
    public static final long MAX_TRANSFER = 1_000_000L;

    public record TransferRequest(UUID senderUuid, String senderName, UUID receiverUuid, String receiverName, long amount, String note) {}
    public record TransferResult(boolean success, String code, String detail) {}

    public static TransferRequest request(ServerPlayer sender, UUID receiverUuid, String receiverName, long amount, String note) {
        return new TransferRequest(sender == null ? null : sender.getUUID(), sender == null ? "" : sender.getScoreboardName(), receiverUuid, receiverName == null ? "" : receiverName.trim(), amount, note == null ? "" : note);
    }

    public static TransferResult execute(ServerPlayer sender, String receiverName, long amount, String note) {
        if (sender == null) return new TransferResult(false, "invalid_sender", "sender required");
        MinecraftServer server = sender.server;
        UUID receiver = LCIntegration.resolvePlayerUUID(server, receiverName);
        return execute(request(sender, receiver, receiverName, amount, note));
    }

    public static TransferResult execute(TransferRequest request) {
        if (request == null || request.senderUuid() == null) return new TransferResult(false, "invalid_sender", "sender required");
        if (request.receiverUuid() == null || request.receiverName().isBlank()) return new TransferResult(false, "receiver_not_found", "receiver player name could not be resolved by server");
        if (request.senderUuid().equals(request.receiverUuid())) return new TransferResult(false, "self_transfer", "cannot transfer to self");
        if (request.amount() <= 0L) return new TransferResult(false, "invalid_amount", "amount must be positive");
        if (request.amount() > MAX_TRANSFER) return new TransferResult(false, "amount_too_large", "amount exceeds server transfer limit " + MAX_TRANSFER);
        if (!LCIntegration.isLCLoaded()) return new TransferResult(false, "lc_missing", "LightmansCurrency is not loaded; no money was deducted");
        try {
            BankReference senderRef = LCIntegration.getLinkedBankAccountFor(request.senderUuid());
            BankReference receiverRef = LCIntegration.getLinkedBankAccountFor(request.receiverUuid());
            IBankAccount senderAccount = senderRef == null ? null : senderRef.get();
            IBankAccount receiverAccount = receiverRef == null ? null : receiverRef.get();
            if (senderAccount == null || receiverAccount == null) return new TransferResult(false, "account_unavailable", "sender or receiver LC PlayerBankReference account unavailable; no money was deducted");
            MoneyValue value = LCIntegration.mainCoinValue(request.amount());
            if (!senderAccount.getMoneyStorage().containsValue(value)) return new TransferResult(false, "insufficient_funds", "sender bank balance is insufficient; no money was deducted");
            // LC MoneyStorage.extractMoney returns the remainder not extracted. Simulate first; abort if not exact.
            MoneyValue remainder = senderAccount.getMoneyStorage().extractMoney(value, true);
            if (!remainder.isEmpty()) return new TransferResult(false, "withdraw_simulation_failed", "LC could not guarantee exact withdrawal; no money was deducted");
            MoneyValue actualRemainder = senderAccount.getMoneyStorage().extractMoney(value, false);
            if (!actualRemainder.isEmpty()) return new TransferResult(false, "withdraw_execution_failed", "LC withdrawal did not complete exactly; receiver was not credited");
            try {
                receiverAccount.depositMoney(value);
            } catch (Throwable depositFailure) {
                try {
                    senderAccount.depositMoney(value);
                    return new TransferResult(false, "receiver_deposit_failed_rolled_back", "receiver deposit failed; sender withdrawal was rolled back");
                } catch (Throwable rollbackFailure) {
                    return new TransferResult(false, "receiver_deposit_failed_rollback_failed", "CRITICAL: receiver deposit failed and rollback also failed; manual audit correction required");
                }
            }
            return new TransferResult(true, "ok", "transferred " + request.amount() + " from " + request.senderName() + " to " + request.receiverName());
        } catch (Throwable t) {
            return new TransferResult(false, "lc_transfer_error", "LC transfer failed safely: " + t.getClass().getSimpleName());
        }
    }
}
