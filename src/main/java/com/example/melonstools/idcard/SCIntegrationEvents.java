package com.example.melonstools.idcard;

import com.example.melonstools.idcard.item.custom.IdentityCardItem;
import net.geforcemods.securitycraft.blockentities.KeycardReaderBlockEntity;
import net.geforcemods.securitycraft.blocks.KeycardReaderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.example.melonstools.MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SCIntegrationEvents {

    @SubscribeEvent
    public static void onRightClickReader(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Player player = event.getEntity();

        if (state.getBlock() instanceof KeycardReaderBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof KeycardReaderBlockEntity readerBe) {

                if (readerBe.isDisabled() || readerBe.isDenied(player)) {
                    return;
                }

                ItemStack handStack = event.getItemStack();
                int cardLevel = 0;
                boolean hasIdentityCard = false;

                if (handStack.getItem() instanceof IdentityCardItem) {
                    cardLevel = IdentityCardItem.getCardLevel(handStack);
                    hasIdentityCard = true;
                } else {
                    if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
                        ItemStack curioStack = com.example.melonstools.compat.CuriosCompat.findIdentityCard(player);
                        if (!curioStack.isEmpty()) {
                            cardLevel = IdentityCardItem.getCardLevel(curioStack);
                            hasIdentityCard = true;
                        }
                    }
                }

                if (hasIdentityCard) {
                    boolean[] acceptedLevels = readerBe.getAcceptedLevels();
                    boolean accessGranted = false;
                    for (int i = 0; i <= cardLevel - 1 && i < acceptedLevels.length; i++) {
                        if (acceptedLevels[i]) {
                            accessGranted = true;
                            break;
                        }
                    }
                    
                    if (accessGranted) {
                        boolean isOwner = readerBe.isOwnedBy(player);
                        net.geforcemods.securitycraft.api.Owner oldOwner = readerBe.getOwner();
                        
                        if (!isOwner) {
                            readerBe.setOwner(player.getUUID().toString(), player.getName().getString());
                        }
                        
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        event.setCanceled(true);
                        
                        if (!isOwner) {
                            readerBe.setOwner(oldOwner.getUUID(), oldOwner.getName());
                        }
                    } else {
                        player.displayClientMessage(Component.translatable("message.melonmeg.keycard_reader.access_denied", cardLevel), true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }
}
