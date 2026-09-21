package com.example.melonstools.client;

import com.example.melonstools.MelonsTools;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家头像(仅客户端): 每次进入服务器后按当前皮肤重新截取脸部, 缓存为 PNG, 并注册为
 * DynamicTexture, 供 AUI 页面 &lt;texture src="melonstools:avatars/&lt;uuid&gt;"&gt; 直接引用。
 * <p>
 * 设计要点:
 * - 前端 src 保持稳定的 ResourceLocation, 不直接读裸文件;
 * - 登录新会话时清空本模组头像缓存, 避免玩家换皮肤后继续显示旧脸;
 * - 同一会话内每个 UUID 只生成/注册一次, 避免重复网络与图像处理;
 * - 生成失败有短冷却, 避免皮肤服务不可达时反复起异步任务。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AvatarRenderer {

    /** 动态纹理命名空间前缀: melonstools:avatars/&lt;uuid&gt; */
    public static final String TEXTURE_NAMESPACE = "melonstools";
    public static final String TEXTURE_PATH_PREFIX = "avatars/";

    private static final Set<UUID> PROCESSING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> READY = ConcurrentHashMap.newKeySet();
    /** 失败冷却:离线/皮肤不可达时避免每次 phoneData 同步都重新起网络任务。 */
    private static final Map<UUID, Long> FAILED_UNTIL = new ConcurrentHashMap<>();
    private static final long FAILURE_COOLDOWN_MS = 60_000L;

    /** 登录新会话时清空头像状态和旧 PNG, 下一次显示头像会按当前皮肤重新截取。 */
    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        resetSessionAvatars();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PROCESSING.clear();
        READY.clear();
        FAILED_UNTIL.clear();
    }

    private static void resetSessionAvatars() {
        PROCESSING.clear();
        READY.clear();
        FAILED_UNTIL.clear();
        try {
            File dir = cacheDir();
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
                if (files != null) {
                    for (File f : files) {
                        try {
                            Files.deleteIfExists(f.toPath());
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 请求生成玩家头像动态纹理(幂等:已注册/正在生成/失败冷却中则跳过) */
    public static void ensureAvatar(UUID uuid, String fallbackName) {
        if (uuid == null) return;
        long now = System.currentTimeMillis();
        Long failedUntil = FAILED_UNTIL.get(uuid);
        if (failedUntil != null) {
            if (failedUntil > now) return;
            FAILED_UNTIL.remove(uuid);
        }
        if (READY.contains(uuid) || !PROCESSING.add(uuid)) return;
        CompletableFuture.runAsync(() -> {
            boolean ok = false;
            try {
                ok = generate(uuid, fallbackName);
            } catch (Throwable ignored) {
            } finally {
                if (!ok && !READY.contains(uuid)) {
                    FAILED_UNTIL.put(uuid, System.currentTimeMillis() + FAILURE_COOLDOWN_MS);
                }
                PROCESSING.remove(uuid);
            }
        });
    }

    /** 头像动态纹理的 ResourceLocation(melonstools:avatars/&lt;uuid&gt;) */
    public static ResourceLocation avatarLocation(UUID uuid) {
        return new ResourceLocation(TEXTURE_NAMESPACE, TEXTURE_PATH_PREFIX + uuid);
    }

    private static boolean generate(UUID uuid, String fallbackName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        GameProfile profile = null;
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) profile = info.getProfile();
        }
        if (profile == null) {
            profile = new GameProfile(uuid, fallbackName == null ? "" : fallbackName);
        }

        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
                mc.getSkinManager().getInsecureSkinInformation(profile);
        MinecraftProfileTexture skin = textures.get(MinecraftProfileTexture.Type.SKIN);
        if (skin == null) return false;

        NativeImage raw = loadSkinImage(skin);
        if (raw == null) return false;
        try {
            NativeImage face = cutFace(raw);
            saveAvatarPng(uuid, face);
            mc.execute(() -> registerTexture(mc, uuid, face));
            return true;
        } finally {
            raw.close();
        }
    }

    private static NativeImage cutFace(NativeImage raw) {
        int scale = 4; // 8x8 脸部放大到 32x32
        NativeImage face = new NativeImage(NativeImage.Format.RGBA, 8 * scale, 8 * scale, false);
        boolean hasHat = raw.getWidth() == 64 && raw.getHeight() == 64;
        for (int x = 0; x < face.getWidth(); x++) {
            for (int y = 0; y < face.getHeight(); y++) {
                int color = raw.getPixelRGBA(8 + x / scale, 8 + y / scale);
                if (hasHat) {
                    int hat = raw.getPixelRGBA(40 + x / scale, 8 + y / scale);
                    if ((hat >>> 24) != 0) color = hat;
                }
                face.setPixelRGBA(x, y, color);
            }
        }
        return face;
    }

    private static File cacheDir() {
        return new File(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().toFile(), "melonstools/avatar-cache");
    }

    private static File avatarFile(UUID uuid) {
        return new File(cacheDir(), uuid.toString() + ".png");
    }

    /** 保存本次进服截到的头像 PNG。失败不影响内存纹理显示。 */
    private static void saveAvatarPng(UUID uuid, NativeImage face) {
        try {
            File dir = cacheDir();
            if (!dir.isDirectory()) Files.createDirectories(dir.toPath());
            File file = avatarFile(uuid);
            Files.deleteIfExists(file.toPath());
            face.writeToFile(file.toPath());
        } catch (Throwable ignored) {
        }
    }

    /** 渲染线程: 把脸部 NativeImage 注册为动态纹理。 */
    private static void registerTexture(Minecraft mc, UUID uuid, NativeImage face) {
        try {
            ResourceLocation loc = avatarLocation(uuid);
            DynamicTexture texture = new DynamicTexture(face);
            texture.bind();
            mc.getTextureManager().register(loc, texture);
            READY.add(uuid);
        } catch (Throwable ignored) {
            face.close();
        }
    }

    /** 先读 MC 皮肤缓存,失败则直接从网络下载 */
    private static NativeImage loadSkinImage(MinecraftProfileTexture skin) {
        String hash = skin.getHash();
        if (hash != null && hash.length() > 2) {
            File cache = new File(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().toFile(),
                    "assets/skins/" + hash.substring(0, 2) + "/" + hash);
            if (cache.isFile()) {
                try (InputStream in = Files.newInputStream(cache.toPath())) {
                    return NativeImage.read(in);
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(skin.getUrl()).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Minecraft Mod)");
            try (InputStream in = conn.getInputStream()) {
                return NativeImage.read(in);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}
