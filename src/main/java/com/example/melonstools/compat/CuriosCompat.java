package com.example.melonstools.compat;

import com.example.melonstools.idcard.item.custom.IdentityCardItem;
import com.example.melonstools.item.MobilePhoneItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;

public class CuriosCompat {

    public static ItemStack findIdentityCard(Player player) {
        Optional<SlotResult> curioResult = CuriosApi.getCuriosHelper().findFirstCurio(player, item -> item.getItem() instanceof IdentityCardItem);
        if (curioResult.isPresent()) {
            return curioResult.get().stack();
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findPhone(Player player) {
        Optional<SlotResult> curioResult = CuriosApi.getCuriosHelper().findFirstCurio(player, item -> item.getItem() instanceof MobilePhoneItem);
        if (curioResult.isPresent()) {
            return curioResult.get().stack();
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findAnomalySuppressor(Player player) {
        Optional<SlotResult> curioResult = CuriosApi.getCuriosHelper().findFirstCurio(player, item -> item.getItem() instanceof com.example.melonstools.item.AnomalyMagneticFieldSuppressorItem);
        if (curioResult.isPresent()) {
            return curioResult.get().stack();
        }
        return ItemStack.EMPTY;
    }

    public static void registerRenderer() {
        top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
            com.example.melonstools.idcard.item.ModItems.IDENTITY_CARD.get(), 
            com.example.melonstools.idcard.client.render.IdentityCardCurioRenderer::new
        );
    }
}