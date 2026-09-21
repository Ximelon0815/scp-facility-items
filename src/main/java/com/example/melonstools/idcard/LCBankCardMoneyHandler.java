package com.example.melonstools.idcard;

import io.github.lightman314.lightmanscurrency.api.capability.money.MoneyHandler;
import io.github.lightman314.lightmanscurrency.api.misc.EasyText;
import io.github.lightman314.lightmanscurrency.api.misc.ISidedObject;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyView;
import io.github.lightman314.lightmanscurrency.common.notifications.types.bank.DepositWithdrawNotification;
import io.github.lightman314.lightmanscurrency.common.util.IClientTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * ID 卡的银行账户 Money Handler。<br>
 * 与 LC 原版 {@code ATMCardMoneyHandler} 等价, 但绑定的账户来源是 ID 卡 NBT 中的
 * {@code CardBankUUID} 键(一个玩家 UUID), 再据此构建 {@link PlayerBankReference}。<br>
 * <b>接入点</b>: 只要物品挂了 MONEY_HANDLER capability, LC 就会自动将其识别为可支付的
 * 银行卡/容器(商人交易、ATM、钱包等)。因此本类是 ID 卡成为 LC 银行卡的核心。
 */
public class LCBankCardMoneyHandler extends MoneyHandler implements ISidedObject {

    private boolean isClient = false;
    @Override
    public boolean isClient() { return this.isClient; }

    private final ItemStack card;

    public LCBankCardMoneyHandler(@Nonnull ItemStack card) { this.card = card; }

    private MutableComponent getCardName() { return EasyText.makeMutable(this.card.getHoverName()); }

    @Nonnull
    @Override
    public LCBankCardMoneyHandler flagAsClient() { this.isClient = true; return this; }
    @Nonnull
    @Override
    public LCBankCardMoneyHandler flagAsClient(boolean isClient) { this.isClient = isClient; return this; }
    @Nonnull
    @Override
    public LCBankCardMoneyHandler flagAsClient(@Nonnull IClientTracker tracker) { this.isClient = tracker.isClient(); return this; }

    /**
     * 从 ID 卡 NBT 的 {@code CardBankUUID} 读取绑定的玩家 UUID, 并构建对应的银行账户引用。
     * 若无绑定或引用无效, 返回 null。
     */
    @Nullable
    private BankReference getReference() {
        CompoundTag tag = this.card.getTag();
        if (tag != null && tag.contains("CardBankUUID")) {
            try {
                UUID uuid = UUID.fromString(tag.getString("CardBankUUID"));
                return LCIntegration.getLinkedBankAccountFor(uuid).flagAsClient(this);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    protected IBankAccount getAccount() {
        BankReference reference = this.getReference();
        if (reference != null) {
            IBankAccount account = reference.get();
            // 只要引用能解析出有效账户即视为可用; 具体访问权限由上游交易/存款逻辑校验。
            return account;
        }
        return null;
    }

    @Nonnull
    @Override
    public MoneyValue insertMoney(@Nonnull MoneyValue insertAmount, boolean simulation) {
        IBankAccount account = this.getAccount();
        if (account != null) {
            if (!simulation) {
                account.depositMoney(insertAmount);
                account.pushLocalNotification(new DepositWithdrawNotification.Custom(this.getCardName(), account.getName(), true, insertAmount));
            }
            return MoneyValue.empty();
        }
        return insertAmount;
    }

    @Nonnull
    @Override
    public MoneyValue extractMoney(@Nonnull MoneyValue extractAmount, boolean simulation) {
        IBankAccount account = this.getAccount();
        if (account != null) {
            MoneyValue result = account.getMoneyStorage().extractMoney(extractAmount, simulation);
            MoneyValue amountTaken = extractAmount.subtractValue(result);
            if (!amountTaken.isEmpty() && !simulation)
                account.pushLocalNotification(new DepositWithdrawNotification.Custom(this.getCardName(), account.getName(), false, amountTaken));
            return result;
        }
        return extractAmount;
    }

    @Override
    public boolean isMoneyTypeValid(@Nonnull MoneyValue value) { return true; }

    @Override
    protected void collectStoredMoney(@Nonnull MoneyView.Builder builder) {
        IBankAccount account = this.getAccount();
        if (account != null)
            builder.merge(account.getMoneyStorage());
    }

}
