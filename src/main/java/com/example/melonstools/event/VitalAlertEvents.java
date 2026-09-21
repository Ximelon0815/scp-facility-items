package com.example.melonstools.event;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.data.VitalAlertSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Phase 4-B Forge FORGE-bus listener skeleton for server-side player death vital alerts. */
@Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VitalAlertEvents {
    private VitalAlertEvents() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (VitalAlertSavedData.get(player.serverLevel()).recordDeath(player, event.getSource()) != null) {
                for (ServerPlayer viewer : player.server.getPlayerList().getPlayers()) {
                    com.example.melonstools.network.ServerPacketHandler.syncPhoneFull(viewer);
                    com.example.melonstools.network.ServerPacketHandler.syncTerminalData(viewer);
                }
            }
        }
    }
}
