package com.example.melonstools.idcard.client.render;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.idcard.item.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import com.mojang.authlib.GameProfile;

@Mod.EventBusSubscriber(modid = MelonsTools.MODID, value = Dist.CLIENT)
public class IdentityCardHudOverlay {

    public static boolean showIdentityCard = true;
    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.melonstools.toggle_identity_card",
            GLFW.GLFW_KEY_I,
            "key.categories.melonstools"
    );
    
    private static final Map<String, GameProfile> profileCache = new HashMap<>();

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        while (TOGGLE_KEY.consumeClick()) {
            showIdentityCard = !showIdentityCard;
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!showIdentityCard) return;

        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        HitResult hitResult = mc.hitResult;

        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult) hitResult;
            Entity targetEntity = entityHitResult.getEntity();

            if (targetEntity instanceof Player targetPlayer) {
                if (mc.player != null && mc.player.distanceTo(targetPlayer) <= 3.0f) {
                    ItemStack cardStack = ItemStack.EMPTY;
                    if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
                        ItemStack curioStack = com.example.melonstools.compat.CuriosCompat.findIdentityCard(targetPlayer);
                        if (!curioStack.isEmpty()) {
                            cardStack = curioStack;
                        }
                    }
                    
                    if (!cardStack.isEmpty()) {
                        CompoundTag tag = cardStack.getTag();
                        
                        if (tag != null) {
                            String name = tag.contains("CardName") ? tag.getString("CardName") : "???";
                            String dept = tag.contains("CardDepartment") ? tag.getString("CardDepartment") : "???";
                            String pos = tag.contains("CardPosition") ? tag.getString("CardPosition") : "???";
                            String bindName = tag.contains("CardSkinName") ? tag.getString("CardSkinName") : "";
                            
                            renderCardHud(event.getGuiGraphics(), mc, targetPlayer, name, dept, pos, bindName);
                        }
                    }
                }
            }
        }
    }

    private static void renderCardHud(GuiGraphics guiGraphics, Minecraft mc, Player targetPlayer, String name, String dept, String pos, String bindName) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        Font font = mc.font;
        
        int panelWidth = 140;
        int panelHeight = 50;
        int startX = screenWidth - panelWidth - 10;
        int startY = screenHeight / 2 - panelHeight / 2;
        
        guiGraphics.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0x90000000);
        
        ResourceLocation skinLocation;
        if (bindName != null && !bindName.isEmpty()) {
            GameProfile profile = profileCache.get(bindName);
            if (profile == null) {
                profile = new GameProfile(net.minecraft.Util.NIL_UUID, bindName);
                profileCache.put(bindName, profile);
                
                if (mc.getConnection() != null) {
                    net.minecraft.client.multiplayer.PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(bindName);
                    if (playerInfo != null && playerInfo.getProfile() != null && playerInfo.getProfile().isComplete()) {
                        profileCache.put(bindName, playerInfo.getProfile());
                    } else {
                        net.minecraft.Util.backgroundExecutor().execute(() -> {
                            try {
                                java.net.URL url = new java.net.URL("https://api.mojang.com/users/profiles/minecraft/" + bindName);
                                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                                connection.setRequestMethod("GET");
                                connection.setConnectTimeout(5000);
                                connection.setReadTimeout(5000);
                                if (connection.getResponseCode() == 200) {
                                    java.io.InputStreamReader reader = new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
                                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                                    String id = json.get("id").getAsString();
                                    java.util.UUID uuid = java.util.UUID.fromString(
                                        id.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5")
                                    );
                                    GameProfile resolvedProfile = new GameProfile(uuid, bindName);
                                    GameProfile filled = mc.getMinecraftSessionService().fillProfileProperties(resolvedProfile, false);
                                    mc.execute(() -> profileCache.put(bindName, filled));
                                    reader.close();
                                }
                            } catch (Exception e) {
                            }
                        });
                    }
                }
            }
            
            if (mc.getConnection() != null) {
                net.minecraft.client.multiplayer.PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(profile.getId());
                if (playerInfo != null) {
                    skinLocation = playerInfo.getSkinLocation();
                } else {
                    skinLocation = mc.getSkinManager().getInsecureSkinLocation(profile);
                }
            } else {
                skinLocation = mc.getSkinManager().getInsecureSkinLocation(profile);
            }
        } else if (targetPlayer instanceof AbstractClientPlayer clientPlayer) {
            skinLocation = clientPlayer.getSkinTextureLocation();
        } else {
            skinLocation = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(targetPlayer.getUUID());
        }

        RenderSystem.setShaderTexture(0, skinLocation);
        guiGraphics.blit(skinLocation, startX + 5, startY + 9, 32, 32, 8, 8, 8, 8, 64, 64);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        guiGraphics.blit(skinLocation, startX + 5, startY + 9, 32, 32, 40, 8, 8, 8, 64, 64);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        int textX = startX + 45;
        guiGraphics.drawString(font, Component.translatable("gui.melonstools.identity_card.name").append(name), textX, startY + 8, 0xFFFFFF);
        guiGraphics.drawString(font, Component.translatable("gui.melonstools.identity_card.department").append(dept), textX, startY + 22, 0xAAAAAA);
        guiGraphics.drawString(font, Component.translatable("gui.melonstools.identity_card.position").append(pos), textX, startY + 36, 0xAAAAAA);
    }
}