package com.example.melonstools.block;

import com.example.melonstools.MelonsTools;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MelonsTools.MODID);

    public static final RegistryObject<Block> QTE_REDSTONE_EMITTER = BLOCKS.register("qte_redstone_emitter",
            () -> new QteRedstoneEmitterBlock(BlockBehaviour.Properties.of()
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .noLootTable()
                    .air()));

    // 终端方块(右键打开电脑终端界面)
    public static final RegistryObject<Block> TERMINAL_BLOCK = BLOCKS.register("terminal_block",
            () -> new TerminalBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 6.0F)
                    .noOcclusion()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
