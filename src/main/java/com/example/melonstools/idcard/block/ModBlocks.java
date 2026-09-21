package com.example.melonstools.idcard.block;

import com.example.melonstools.MelonsTools;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, com.example.melonstools.MelonsTools.MODID);

    public static final RegistryObject<Block> SCP_KEYCARD_READER_BLOCK = BLOCKS.register("scp_keycard_reader_block",
            () -> new SCPKeycardReaderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> SCP_LOCKED_KEYCARD_READER_BLOCK = BLOCKS.register("scp_locked_keycard_reader_block",
            () -> new SCPLockedKeycardReaderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> SCP_CP_KEYCARD_READER_BLOCK = BLOCKS.register("scp_cp_keycard_reader_block",
            () -> new SCPCheckpointKeycardReaderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F).requiresCorrectToolForDrops().noOcclusion()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
