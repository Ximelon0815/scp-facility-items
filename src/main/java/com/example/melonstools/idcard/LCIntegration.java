package com.example.melonstools.idcard;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyView;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.common.capability.wallet.WalletCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.UUID;

/**
 * MelonsTools ID 卡 × LightmansCurrency 的桥接类。<br>
 * 只有安装了 LC (ModList.isLoaded("lightmanscurrency")) 时相关方法才有意义;
 * 未安装时一律返回 null / false, 不影响 ID 卡本身的其它功能。
 */
public class LCIntegration {

    public static boolean isLCLoaded() {
        return ModList.get().isLoaded("lightmanscurrency");
    }

    /** ID 卡 NBT 中保存银行卡绑定玩家的键。与皮肤绑定(CardSkinName)严格区分。 */
    public static final String NBT_KEY_BANK_UUID = "CardBankUUID";

    /**
     * 读取 ID 卡当前绑定的玩家 UUID。
     * @return 绑定玩家的 UUID; 卡未绑定或为空时返回 null。
     */
    public static UUID getBankCardUUID(ItemStack cardStack) {
        if (cardStack != null && cardStack.hasTag()) {
            CompoundTag tag = cardStack.getTag();
            if (tag.contains(NBT_KEY_BANK_UUID)) {
                try {
                    return UUID.fromString(tag.getString(NBT_KEY_BANK_UUID));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 写入/清除 ID 卡的银行卡绑定。
     * @param uuid 目标玩家的 UUID; 传 null 表示清除绑定。
     */
    public static void setBankCardUUID(ItemStack cardStack, UUID uuid) {
        CompoundTag tag = cardStack.getOrCreateTag();
        if (uuid != null) {
            tag.putString(NBT_KEY_BANK_UUID, uuid.toString());
        } else {
            tag.remove(NBT_KEY_BANK_UUID);
        }
        cardStack.setTag(tag);
    }

    /**
     * 根据指定玩家 UUID 构建其个人银行账户引用(LC 的 PlayerBankReference)。<br>
     * 不依赖卡实例, 便于服务端读取余额时直接使用。
     */
    public static BankReference getLinkedBankAccountFor(UUID uuid) {
        if (uuid == null) return null;
        return PlayerBankReference.of(uuid).flagAsClient();
    }

    /**
     * 获取 ID 卡绑定的银行账户。卡无绑定则回退为<b>持卡玩家本人</b>的账户。<br>
     * 供余额显示 / 交易等使用。
     * @param player 当前上下文玩家(用于自动绑定回退)。
     */
    public static BankReference getLinkedBankAccount(ItemStack cardStack, Player player) {
        if (isLCLoaded()) {
            UUID bound = getBankCardUUID(cardStack);
            if (bound != null) {
                return getLinkedBankAccountFor(bound);
            }
            // 未显式绑定: 自动指向持卡人本人的账户
            return getLinkedBankAccountFor(player != null ? player.getUUID() : null);
        }
        return null;
    }

    /**
     * 解析 ID 卡绑定账户的真实余额文本。失败或账户为空时返回 null。
     * <p>
     * 注意(2026-08-24 修复): 之前用 {@link IBankAccount#getBalanceText()}(LC 的 GUI 文案),
     * 账户有钱时返回 "Balance: $xxx", 账户没钱时返回 "Balance: Nothing"(LC 原生空账户文案),
     * 且永远非 null, 导致 buildPhoneJson 不会回退到 LC 钱包余额 → 余额栏显示一串英文。
     * 现改用 {@link IBankAccount#getStoredMoney()}(MoneyView) 取数值, 格式化为干净的金额文本;
     * 账户为空返回 null, 由调用方回退钱包。
     */
    public static String getLinkedBankAccountBalance(ItemStack cardStack, Player player) {
        if (!isLCLoaded()) return null;
        BankReference ref = getLinkedBankAccount(cardStack, player);
        IBankAccount account = ref == null ? null : ref.get();
        if (account == null) return null;
        return formatMoneyView(account.getStoredMoney());
    }

    /**
     * 读取个人终端应显示的 LC 余额。
     * <p>
     * 目标设计: 不给玩家发 LC 原版银行卡, MelonsTools ID 卡本身就是银行卡。
     * 优先级:
     * 1) MelonsTools ID 卡绑定账户(CardBankUUID,旧卡由 IdentityCardItem.inventoryTick 自动绑定持卡人本人);
     * 2) LC 钱包余额;
     * 3) 全空则显示 "0"。
     */
    public static String getBestBalanceDisplay(Player player, ItemStack identityCard) {
        if (!isLCLoaded()) return "0";
        try {
            ensureIdentityCardBankBinding(identityCard, player);

            String idCardBalance = getLinkedBankAccountBalance(identityCard, player);
            if (idCardBalance != null) return idCardBalance;

            return getWalletBalanceDisplay(player);
        } catch (Throwable t) {
            return "0";
        }
    }

    /** 旧 ID 卡没有 CardBankUUID 时自动绑定为持卡人本人, 保证 ID 卡可直接当 LC 银行卡用。 */
    public static void ensureIdentityCardBankBinding(ItemStack identityCard, Player player) {
        if (!isLCLoaded() || identityCard == null || identityCard.isEmpty() || player == null) return;
        if (getBankCardUUID(identityCard) == null) {
            setBankCardUUID(identityCard, player.getUUID());
        }
    }

    /** 将设施资金/工资系统的长整型金额转换为 LC 主币种 MoneyValue。 */
    public static io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue mainCoinValue(long amount) {
        if (amount <= 0) return io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue.empty();
        return CoinValue.fromNumber("main", amount);
    }

    /** 向指定玩家的 LC 个人银行账户打款。用于设施终端发工资。 */
    public static boolean depositToPlayerBank(UUID uuid, long amount) {
        if (!isLCLoaded() || uuid == null || amount <= 0) return false;
        try {
            BankReference ref = getLinkedBankAccountFor(uuid);
            IBankAccount account = ref == null ? null : ref.get();
            if (account == null) return false;
            account.depositMoney(mainCoinValue(amount));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 读取 LC 钱包余额并格式化为显示文本; 空钱包返回 "0"。
     * 用于 ID 卡账户为空时回退(玩家可能把硬币放在钱包)。
     */
    public static String getWalletBalanceDisplay(Player player) {
        if (!isLCLoaded()) return "0";
        try {
            String s = formatMoneyView(WalletCapability.getWalletMoney(player));
            return s == null ? "0" : s;
        } catch (Throwable t) {
            return "0";
        }
    }

    /** 把 {@link MoneyView} 各货币金额格式化为可读文本; 空视图返回 null。 */
    private static String formatMoneyView(MoneyView view) {
        if (view == null || view.isEmpty()) return null;
        List<Component> lines = view.getAllText();
        if (lines == null || lines.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Component line : lines) {
            String s = line.getString();
            if (s != null && !s.isEmpty() && !"Nothing".equals(s)) {
                if (sb.length() > 0) sb.append("、");
                sb.append(s);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 将玩家名解析为 UUID。优先查在线玩家, 否则回退服务端 ProfileCache。<br>
     * 解析失败返回 null。
     */
    public static UUID resolvePlayerUUID(MinecraftServer server, String name) {
        if (server == null || name == null || name.isEmpty()) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        try {
            var profile = server.getProfileCache().get(name);
            if (profile != null && profile.isPresent()) return profile.get().getId();
        } catch (Throwable ignored) {
        }
        return null;
    }

}
