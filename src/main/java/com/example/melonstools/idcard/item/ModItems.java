package com.example.melonstools.idcard.item;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.idcard.item.custom.IdentityCardItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, com.example.melonstools.MelonsTools.MODID);

    public static final RegistryObject<Item> IDENTITY_CARD = ITEMS.register("identity_card",
            () -> new IdentityCardItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SCREWDRIVER = ITEMS.register("screwdriver",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SCP_KEYCARD_READER_BLOCK_ITEM = ITEMS.register("scp_keycard_reader_block",
            () -> new net.minecraft.world.item.BlockItem(com.example.melonstools.idcard.block.ModBlocks.SCP_KEYCARD_READER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> SCP_LOCKED_KEYCARD_READER_BLOCK_ITEM = ITEMS.register("scp_locked_keycard_reader_block",
            () -> new net.minecraft.world.item.BlockItem(com.example.melonstools.idcard.block.ModBlocks.SCP_LOCKED_KEYCARD_READER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> SCP_CP_KEYCARD_READER_BLOCK_ITEM = ITEMS.register("scp_cp_keycard_reader_block",
            () -> new net.minecraft.world.item.BlockItem(com.example.melonstools.idcard.block.ModBlocks.SCP_CP_KEYCARD_READER_BLOCK.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
