package com.example.melonstools;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MelonsTools.MODID)
public class MelonsTools {
    public static final String MODID = "melonstools";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MelonsTools() {
        // 难点(2026-08-22): AUI 字体光栅化默认按基础字号(9px)生成位图再放大绘制,
        // 大字号(主页背景 Λ 水印 220px、时钟 74~112px)放大倍数大 → 显示模糊。
        // 开启 apricityui.fontRaster.targetPhysical 后按"字号×文档像素缩放"光栅化,
        // 绘制时还原物理尺寸 → 任意字号/缩放都清晰。必须在 AUI 首次绘制文字前设置。
        System.setProperty("apricityui.fontRaster.targetPhysical", "true");

        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, com.example.melonstools.config.QteConfig.SPEC);
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // QTE Registries
        com.example.melonstools.item.ModItems.register(modEventBus);
        modEventBus.addListener(com.example.melonstools.item.ModItems::addCreative);
        com.example.melonstools.block.ModBlocks.register(modEventBus);
        com.example.melonstools.block.entity.ModBlockEntities.register(modEventBus);
        
        // ID Card Registries
        com.example.melonstools.idcard.item.ModItems.register(modEventBus);
        com.example.melonstools.idcard.block.ModBlocks.register(modEventBus);
        com.example.melonstools.idcard.block.entity.ModBlockEntities.register(modEventBus);
        com.example.melonstools.idcard.item.ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.example.melonstools.network.NetworkManager.registerPackets();
            com.example.melonstools.idcard.network.ModNetworkHandler.register();
        });
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        com.example.melonstools.anomaly.AnomalyCodexManager.reload();
        LOGGER.info("Loaded anomaly codex on server start");
    }

    @SubscribeEvent
    public void onBlockInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        com.example.melonstools.qte.QTELogicManager.onBlockInteract(event);
    }

    @SubscribeEvent
    public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            com.example.melonstools.qte.QTELogicManager.tickServer();
            com.example.melonstools.data.OnlineStaffCache.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerLogOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.example.melonstools.qte.QTELogicManager.cleanUpPlayer(player);
            com.example.melonstools.data.OnlineStaffCache.remove(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.example.melonstools.qte.QTEBlockManager manager = com.example.melonstools.qte.QTEBlockManager.get(player.serverLevel());
            if (manager != null) {
                java.util.List<net.minecraft.core.BlockPos> boundBlocks = new java.util.ArrayList<>(manager.getAllBindings().keySet());
                com.example.melonstools.network.NetworkManager.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.example.melonstools.network.SyncBoundBlocksPacket(boundBlocks)
                );
            }
            com.example.melonstools.data.OnlineStaffCache.refresh(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.example.melonstools.qte.QTEBlockManager manager = com.example.melonstools.qte.QTEBlockManager.get(player.serverLevel());
            if (manager != null) {
                java.util.List<net.minecraft.core.BlockPos> boundBlocks = new java.util.ArrayList<>(manager.getAllBindings().keySet());
                com.example.melonstools.network.NetworkManager.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.example.melonstools.network.SyncBoundBlocksPacket(boundBlocks)
                );
            }
            com.example.melonstools.qte.SelectionManager.clearSelection(player);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            event.enqueueWork(() -> {
                if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) { com.example.melonstools.compat.CuriosCompat.registerRenderer(); }
            });
        }
        
        @SubscribeEvent
        public static void onKeyRegister(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(com.example.melonstools.idcard.client.render.IdentityCardHudOverlay.TOGGLE_KEY);
        }
    }
}
