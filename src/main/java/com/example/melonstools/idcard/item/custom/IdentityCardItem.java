package com.example.melonstools.idcard.item.custom;

import io.github.lightman314.lightmanscurrency.api.capability.money.CapabilityMoneyHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IdentityCardItem extends Item {
    public IdentityCardItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    @Nullable
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable net.minecraft.nbt.CompoundTag tag) {
        // 当安装 LightmansCurrency 时, 让 ID 卡作为其银行卡(挂 MONEY_HANDLER capability)。
        // 挂上此 capability 后, LC 会自动把它识别为可支付的银行卡/容器(商人交易、ATM、钱包等)。
        if (com.example.melonstools.idcard.LCIntegration.isLCLoaded()) {
            return CapabilityMoneyHandler.createProvider(new com.example.melonstools.idcard.LCBankCardMoneyHandler(stack));
        }
        return super.initCapabilities(stack, tag);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        // LC 联动: ID 卡就是银行卡。旧卡/未编辑卡如果没有 CardBankUUID,服务端自动绑定为持卡玩家本人,
        // 这样 LC 的 MONEY_HANDLER capability 能稳定定位玩家个人银行账户并支持存取/支付。
        if (!level.isClientSide && entity instanceof Player player && com.example.melonstools.idcard.LCIntegration.isLCLoaded()) {
            if (com.example.melonstools.idcard.LCIntegration.getBankCardUUID(stack) == null) {
                com.example.melonstools.idcard.LCIntegration.setBankCardUUID(stack, player.getUUID());
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pUsedHand);

        if (pLevel.isClientSide) {
            if (pPlayer.hasPermissions(2)) {
                openScreen(itemstack);
            } else {
                pPlayer.displayClientMessage(Component.translatable("message.melonmeg.keycard_reader.not_owner"), true);
            }
        }

        return InteractionResultHolder.sidedSuccess(itemstack, pLevel.isClientSide());
    }

    private void openScreen(ItemStack itemStack) {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.example.melonstools.client.ClientHooks.openIdentityCardScreen(itemStack));
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        if (pStack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = pStack.getTag();
            if (tag.contains("CardName")) {
                pTooltipComponents.add(Component.translatable("gui.melonstools.identity_card.name").append(" ").append(tag.getString("CardName")).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            if (tag.contains("CardDepartment")) {
                pTooltipComponents.add(Component.translatable("gui.melonstools.identity_card.department").append(" ").append(tag.getString("CardDepartment")).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            if (tag.contains("CardPosition")) {
                pTooltipComponents.add(Component.translatable("gui.melonstools.identity_card.position").append(" ").append(tag.getString("CardPosition")).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            if (tag.contains("CardLevel")) {
                int level = tag.getInt("CardLevel");
                Component levelText = level <= 0
                        ? Component.translatable("gui.melonstools.identity_card.no_access")
                        : Component.literal(String.valueOf(level));
                pTooltipComponents.add(Component.translatable("gui.melonstools.identity_card.level").append(" ").append(levelText).withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        } else {
            pTooltipComponents.add(Component.literal("\u00A78\u7A7A\u767D\u8EAB\u4EFD\u5361"));
        }
    }

    public static int getCardLevel(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("CardLevel")) {
            return stack.getTag().getInt("CardLevel");
        }
        return 0;
    }

    public static String getCardDepartment(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("CardDepartment")) {
            return stack.getTag().getString("CardDepartment");
        }
        return null;
    }

    public static String getCardPosition(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("CardPosition")) {
            return stack.getTag().getString("CardPosition");
        }
        return null;
    }

    public static String getCardName(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("CardName")) {
            return stack.getTag().getString("CardName");
        }
        return null;
    }
}