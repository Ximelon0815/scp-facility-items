package com.example.melonstools.utils;

import com.example.melonstools.idcard.item.custom.IdentityCardItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class PhoneUtils {

    /**
     * Finds the player's identity card in main hand, off hand, or Curios slots.
     */
    public static ItemStack findIdentityCard(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof IdentityCardItem) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof IdentityCardItem) {
            return offHand;
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            ItemStack curio = com.example.melonstools.compat.CuriosCompat.findIdentityCard(player);
            if (!curio.isEmpty()) {
                return curio;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns the player's department from their identity card, or null if no card.
     */
    public static String getPlayerDepartment(Player player) {
        ItemStack card = findIdentityCard(player);
        if (!card.isEmpty()) {
            return com.example.melonstools.department.DepartmentPolicy.identity(card).department();
        }
        return null;
    }

    /**
     * Returns the player's position (job title) from their identity card, or null if no card.
     */
    public static String getPlayerPosition(Player player) {
        ItemStack card = findIdentityCard(player);
        if (!card.isEmpty()) {
            return com.example.melonstools.department.DepartmentPolicy.identity(card).position();
        }
        return null;
    }

    /**
     * Returns the highest ID card level found on the player, or 0 if no card.
     */
    public static int getPlayerIdCardLevel(Player player) {
        ItemStack card = findIdentityCard(player);
        if (!card.isEmpty()) {
            return com.example.melonstools.department.DepartmentPolicy.identity(card).level();
        }
        return 0;
    }

    /**
     * Returns the name written on the player's identity card, or null if no card.
     */
    public static String getPlayerName(Player player) {
        ItemStack card = findIdentityCard(player);
        if (!card.isEmpty()) {
            return IdentityCardItem.getCardName(card);
        }
        return null;
    }
}
