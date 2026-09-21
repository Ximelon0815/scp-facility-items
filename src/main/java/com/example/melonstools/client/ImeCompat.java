package com.example.melonstools.client;

import com.example.melonstools.MelonsTools;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * 原生 Windows 输入法(IME)管理: AUI 屏幕(手机/终端)打开时激活输入法, 关闭时恢复。
 * <p>
 * 目标(2026-08-24 用户要求): 玩家**直接切换系统输入法**(如 Win+Space 切到中文)
 * 即可在手机/终端聊天框输入中文, **不依赖任何额外 mod**。
 * <p>
 * 原理:
 * 1. 通过 JNA 直接操作 Win32 Imm32 API —— 确保游戏窗口关联了 IME 上下文
 *    (被 IMBlocker 这类 mod 解绑时重新关联系统默认 IME), 并把当前输入法
 *    的转换状态切到 native(中文) 模式, 关闭时恢复打开前的状态。
 * 2. 若检测到 IMBlocker(输入法冲突修复, 会把非白名单屏幕强制切成英文),
 *    同时调用其 API 请求解锁(反射, 无编译依赖)。
 * <p>
 * JNA 由 jarJar 打包进本 mod(META-INF/jarjar/), 运行时仅对本 mod 可见,
 * 客户端无需安装/配置任何额外 mod 或库; 全部调用包 try/catch, 任何失败静默跳过。
 */
public class ImeCompat {

    private static final boolean JNA_LOADED;
    private static final boolean IMBLOCKER_LOADED;

    /** 打开 AUI 屏幕前记录的输入法转换状态, 关闭时恢复 */
    private static int savedConversion = -1;
    private static int savedSentence = -1;

    static {
        boolean jna = false;
        try {
            Class.forName("com.sun.jna.Native");
            jna = true;
        } catch (Throwable ignored) {
        }
        JNA_LOADED = jna;

        boolean imb = false;
        try {
            Class.forName("io.github.reserveword.imblocker.common.IMManager");
            imb = true;
        } catch (Throwable ignored) {
        }
        IMBLOCKER_LOADED = imb;
    }

    // ---- Win32 Imm32 API (JNA 绑定) ----
    // IME_CMODE_ALPHANUMERIC = 0x0000, IME_CMODE_NATIVE = 0x0002(中文/本地模式)
    private static final int IME_CMODE_NATIVE = 0x0002;

    private interface Imm32 extends Library {
        Imm32 INSTANCE = Native.load("Imm32", Imm32.class);

        Pointer ImmGetContext(Pointer hwnd);

        boolean ImmReleaseContext(Pointer hwnd, Pointer himc);

        boolean ImmSetConversionStatus(Pointer himc, int fdwConversion, int fdwSentence);

        void ImmGetConversionStatus(Pointer himc, IntByReference lpdwConversion, IntByReference lpdwSentence);

        Pointer ImmAssociateContext(Pointer hwnd, Pointer himc);
    }

    /** AUI 屏幕打开/获得焦点时调用 */
    public static void enableIme() {
        if (IMBLOCKER_LOADED) {
            try {
                imblockerSetState(true);
            } catch (Throwable ignored) {
            }
        }
        if (!JNA_LOADED) return;
        try {
            Pointer hwnd = getGameWindow();
            if (hwnd == null) return;
            Pointer ctx = Imm32.INSTANCE.ImmGetContext(hwnd);
            if (ctx == null) {
                // IME 上下文被解绑(如 IMBlocker 的 ImmAssociateContext(hwnd, NULL)):
                // 重新关联系统默认 IME 上下文, 恢复输入法可用
                Pointer def = Imm32.INSTANCE.ImmGetContext(Pointer.NULL);
                if (def != null) {
                    Imm32.INSTANCE.ImmAssociateContext(hwnd, def);
                    ctx = Imm32.INSTANCE.ImmGetContext(hwnd);
                }
            }
            if (ctx == null) return;
            // 记录打开前的转换状态, 供关闭时恢复
            if (savedConversion < 0) {
                IntByReference conv = new IntByReference();
                IntByReference sent = new IntByReference();
                Imm32.INSTANCE.ImmGetConversionStatus(ctx, conv, sent);
                savedConversion = conv.getValue();
                savedSentence = sent.getValue();
            }
            // 切到 native(中文) 转换模式: 玩家切到中文输入法后即可直接组合输入
            Imm32.INSTANCE.ImmSetConversionStatus(ctx, IME_CMODE_NATIVE, 0);
            Imm32.INSTANCE.ImmReleaseContext(hwnd, ctx);
        } catch (Throwable ignored) {
        }
    }

    /** 离开 AUI 屏幕时调用 */
    public static void disableIme() {
        if (IMBLOCKER_LOADED) {
            try {
                imblockerSetState(false);
            } catch (Throwable ignored) {
            }
        }
        if (!JNA_LOADED) return;
        try {
            Pointer hwnd = getGameWindow();
            if (hwnd == null) return;
            Pointer ctx = Imm32.INSTANCE.ImmGetContext(hwnd);
            if (ctx != null) {
                if (savedConversion >= 0) {
                    Imm32.INSTANCE.ImmSetConversionStatus(ctx, savedConversion, Math.max(0, savedSentence));
                }
                savedConversion = -1;
                savedSentence = -1;
                Imm32.INSTANCE.ImmReleaseContext(hwnd, ctx);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 当前游戏窗口的 Win32 HWND(LWJGL GLFW 原生扩展, 恒可用); 取不到返回 null */
    private static Pointer getGameWindow() {
        long handle = GLFW.glfwGetCurrentContext();
        if (handle == 0L) return null;
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(handle);
        return hwnd == 0L ? null : new Pointer(hwnd);
    }

    /** IMBlocker 存在时请求其解锁/锁定输入法(与其白名单机制同源) */
    private static void imblockerSetState(boolean enabled) throws Exception {
        Class<?> container = Class.forName("io.github.reserveword.imblocker.common.gui.FocusContainer");
        Object minecraft = container.getField("MINECRAFT").get(null);
        container.getMethod("setPreferredState", boolean.class).invoke(minecraft, enabled);
        Class<?> manager = Class.forName("io.github.reserveword.imblocker.common.IMManager");
        manager.getMethod("setState", boolean.class).invoke(null, enabled);
    }

    @Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class Events {

        @SubscribeEvent
        public static void onScreenOpening(ScreenEvent.Opening event) {
            Screen oldScreen = event.getScreen();
            Screen newScreen = event.getNewScreen();
            if (newScreen instanceof com.sighs.apricityui.screen.ApricityScreen) {
                enableIme();
            } else if (oldScreen instanceof com.sighs.apricityui.screen.ApricityScreen) {
                // AUI 屏幕被其他屏幕直接替换(如打开设置): 恢复输入法状态
                disableIme();
            }
        }

        @SubscribeEvent
        public static void onScreenClosing(ScreenEvent.Closing event) {
            if (event.getScreen() instanceof com.sighs.apricityui.screen.ApricityScreen) {
                disableIme();
            }
        }
    }
}
