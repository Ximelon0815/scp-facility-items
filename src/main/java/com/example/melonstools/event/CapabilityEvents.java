package com.example.melonstools.event;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.capability.PlayerPhone;
import com.example.melonstools.capability.PlayerPhoneProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class CapabilityEvents {

    @Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                if (!event.getObject().getCapability(PlayerPhoneProvider.PLAYER_PHONE).isPresent()) {
                    PlayerPhoneProvider provider = new PlayerPhoneProvider();
                    // 回填玩家自己的 UUID, 供"不能把自己加为好友"防御使用
                    provider.setSelfUuid(((Player) event.getObject()).getUUID());
                    event.addCapability(new ResourceLocation(MelonsTools.MODID, "player_phone"), provider);
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerPhone.class);
        }
    }
}
