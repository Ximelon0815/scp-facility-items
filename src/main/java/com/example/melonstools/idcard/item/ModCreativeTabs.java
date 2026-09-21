package com.example.melonstools.idcard.item;

import com.example.melonstools.MelonsTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, com.example.melonstools.MelonsTools.MODID);

    public static final RegistryObject<CreativeModeTab> MELON_TAB = CREATIVE_MODE_TABS.register("melon_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> ModItems.IDENTITY_CARD.get().getDefaultInstance())
                    .title(Component.translatable("creativetab.melon_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.IDENTITY_CARD.get());
                        output.accept(ModItems.SCREWDRIVER.get());
                        output.accept(ModItems.SCP_KEYCARD_READER_BLOCK_ITEM.get());
                        output.accept(ModItems.SCP_LOCKED_KEYCARD_READER_BLOCK_ITEM.get());
                        output.accept(ModItems.SCP_CP_KEYCARD_READER_BLOCK_ITEM.get());
                        output.accept(com.example.melonstools.item.ModItems.QTE_BINDER.get());
                        output.accept(com.example.melonstools.item.ModItems.MOBILE_PHONE.get());
                        output.accept(com.example.melonstools.item.ModItems.ANOMALY_MAGNETIC_FIELD_SUPPRESSOR.get());
                        output.accept(com.example.melonstools.item.ModItems.TERMINAL_BLOCK.get());
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
