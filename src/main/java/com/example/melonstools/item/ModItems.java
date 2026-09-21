package com.example.melonstools.item;

import com.example.melonstools.MelonsTools;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MelonsTools.MODID);

    public static final RegistryObject<Item> QTE_BINDER = ITEMS.register("qte_binder",
            () -> new QteBinderItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> MOBILE_PHONE = ITEMS.register("mobile_phone",
            () -> new MobilePhoneItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ANOMALY_MAGNETIC_FIELD_SUPPRESSOR = ITEMS.register("anomaly_magnetic_field_suppressor",
            () -> new AnomalyMagneticFieldSuppressorItem(new Item.Properties().stacksTo(1)));

    // 终端方块物品(右键打开电脑终端界面)
    public static final RegistryObject<Item> TERMINAL_BLOCK = ITEMS.register("terminal_block",
            () -> new net.minecraft.world.item.BlockItem(com.example.melonstools.block.ModBlocks.TERMINAL_BLOCK.get(),
                    new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Items are now added to the custom tab in ModCreativeTabs
    }
}
