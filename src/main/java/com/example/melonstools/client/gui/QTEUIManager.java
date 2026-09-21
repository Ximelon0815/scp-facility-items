package com.example.melonstools.client.gui;

import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.Minecraft;

public class QTEUIManager {
    public static void openQTEScreen(StartQTEPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        
        switch (packet.getType()) {
            case FISHING_BARS:
                mc.setScreen(new FishingBarsQTEScreen(packet));
                break;
            case DBD_SKILL_CHECK:
                mc.setScreen(new DBDSkillCheckQTEScreen(packet));
                break;
            case MASHING:
                mc.setScreen(new MashingQTEScreen(packet));
                break;
            case SEQUENCE:
                mc.setScreen(new SequenceQTEScreen(packet));
                break;
            case BALANCE:
                mc.setScreen(new BalanceQTEScreen(packet));
                break;
            case MEMORY:
                mc.setScreen(new MemoryQTEScreen(packet));
                break;
            case WIRE_CONNECT:
                mc.setScreen(new WireConnectQTEScreen(packet));
                break;
            case CHARGE_RELEASE:
                mc.setScreen(new ChargeReleaseQTEScreen(packet));
                break;
            case CIPHER:
                mc.setScreen(new CipherQTEScreen(packet));
                break;
            case CALIBRATE:
                mc.setScreen(new CalibrateQTEScreen(packet));
                break;
        }
    }
}



