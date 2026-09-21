package com.example.melonstools.network;

import com.example.melonstools.MelonsTools;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkManager {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MelonsTools.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        INSTANCE.registerMessage(packetId++, StartQTEPacket.class, StartQTEPacket::toBytes, StartQTEPacket::new, StartQTEPacket::handle);
        INSTANCE.registerMessage(packetId++, QTEResultPacket.class, QTEResultPacket::toBytes, QTEResultPacket::new, QTEResultPacket::handle);
        INSTANCE.registerMessage(packetId++, SyncBoundBlocksPacket.class, SyncBoundBlocksPacket::toBytes, SyncBoundBlocksPacket::new, SyncBoundBlocksPacket::handle);
        INSTANCE.registerMessage(packetId++, OpenBinderUIPacket.class, OpenBinderUIPacket::toBytes, OpenBinderUIPacket::new, OpenBinderUIPacket::handle);
        INSTANCE.registerMessage(packetId++, SaveBinderConfigPacket.class, SaveBinderConfigPacket::toBytes, SaveBinderConfigPacket::new, SaveBinderConfigPacket::handle);
        
        INSTANCE.registerMessage(packetId++, RequestNearbyPlayersPacket.class, RequestNearbyPlayersPacket::toBytes, RequestNearbyPlayersPacket::new, RequestNearbyPlayersPacket::handle);
        INSTANCE.registerMessage(packetId++, SyncNearbyPlayersPacket.class, SyncNearbyPlayersPacket::toBytes, SyncNearbyPlayersPacket::new, SyncNearbyPlayersPacket::handle);
        INSTANCE.registerMessage(packetId++, FriendActionPacket.class, FriendActionPacket::toBytes, FriendActionPacket::new, FriendActionPacket::handle);
        INSTANCE.registerMessage(packetId++, SyncPhoneDataPacket.class, SyncPhoneDataPacket::toBytes, SyncPhoneDataPacket::new, SyncPhoneDataPacket::handle);
        INSTANCE.registerMessage(packetId++, ChatActionPacket.class, ChatActionPacket::toBytes, ChatActionPacket::new, ChatActionPacket::handle);
        INSTANCE.registerMessage(packetId++, SyncChatDataPacket.class, SyncChatDataPacket::toBytes, SyncChatDataPacket::new, SyncChatDataPacket::handle);
        INSTANCE.registerMessage(packetId++, RequestPhoneFullPacket.class, RequestPhoneFullPacket::toBytes, RequestPhoneFullPacket::new, RequestPhoneFullPacket::handle);
        INSTANCE.registerMessage(packetId++, SyncPhoneFullPacket.class, SyncPhoneFullPacket::toBytes, SyncPhoneFullPacket::new, SyncPhoneFullPacket::handle);
        INSTANCE.registerMessage(packetId++, ClaimTaskPacket.class, ClaimTaskPacket::toBytes, ClaimTaskPacket::new, ClaimTaskPacket::handle);
        INSTANCE.registerMessage(packetId++, SubmitScorePacket.class, SubmitScorePacket::toBytes, SubmitScorePacket::new, SubmitScorePacket::handle);
        INSTANCE.registerMessage(packetId++, SubmitReportPacket.class, SubmitReportPacket::toBytes, SubmitReportPacket::new, SubmitReportPacket::handle);
        INSTANCE.registerMessage(packetId++, RequestLeaderboardPacket.class, RequestLeaderboardPacket::toBytes, RequestLeaderboardPacket::new, RequestLeaderboardPacket::handle);
        INSTANCE.registerMessage(packetId++, SyncLeaderboardPacket.class, SyncLeaderboardPacket::toBytes, SyncLeaderboardPacket::new, SyncLeaderboardPacket::handle);
        INSTANCE.registerMessage(packetId++, TerminalActionPacket.class, TerminalActionPacket::toBytes, TerminalActionPacket::new, TerminalActionPacket::handle);
        INSTANCE.registerMessage(packetId++, SyncTerminalDataPacket.class, SyncTerminalDataPacket::toBytes, SyncTerminalDataPacket::new, SyncTerminalDataPacket::handle);
    }
}
