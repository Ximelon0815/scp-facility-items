package com.example.melonstools.idcard.block.entity;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.idcard.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, com.example.melonstools.MelonsTools.MODID);

    public static final RegistryObject<BlockEntityType<SCPKeycardReaderBlockEntity>> SCP_KEYCARD_READER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("scp_keycard_reader_block_entity",
                    () -> BlockEntityType.Builder.of(SCPKeycardReaderBlockEntity::new,
                            ModBlocks.SCP_KEYCARD_READER_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<SCPLockedKeycardReaderBlockEntity>> SCP_LOCKED_KEYCARD_READER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("scp_locked_keycard_reader_block_entity",
                    () -> BlockEntityType.Builder.of(SCPLockedKeycardReaderBlockEntity::new,
                            ModBlocks.SCP_LOCKED_KEYCARD_READER_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<SCPCheckpointKeycardReaderBlockEntity>> SCP_CP_KEYCARD_READER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("scp_cp_keycard_reader_block_entity",
                    () -> BlockEntityType.Builder.of(SCPCheckpointKeycardReaderBlockEntity::new,
                            ModBlocks.SCP_CP_KEYCARD_READER_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
