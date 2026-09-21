package com.example.melonstools.idcard.network;

import com.example.melonstools.MelonsTools;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("melonstools", "idcard_main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SaveIdentityCardPacket.class, id++)
                .encoder(SaveIdentityCardPacket::encode)
                .decoder(SaveIdentityCardPacket::decode)
                .consumerMainThread(SaveIdentityCardPacket::handle)
                .add();
                
        CHANNEL.messageBuilder(SaveReaderConfigPacket.class, id++)
                .encoder(SaveReaderConfigPacket::encode)
                .decoder(SaveReaderConfigPacket::decode)
                .consumerMainThread(SaveReaderConfigPacket::handle)
                .add();
    }
}
