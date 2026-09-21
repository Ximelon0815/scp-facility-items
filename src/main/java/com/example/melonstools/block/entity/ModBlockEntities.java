package com.example.melonstools.block.entity;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MelonsTools.MODID);

    public static final RegistryObject<BlockEntityType<QteRedstoneEmitterBlockEntity>> QTE_REDSTONE_EMITTER =
            BLOCK_ENTITIES.register("qte_redstone_emitter",
                    () -> BlockEntityType.Builder.of(QteRedstoneEmitterBlockEntity::new, ModBlocks.QTE_REDSTONE_EMITTER.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
