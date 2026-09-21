package com.example.melonstools.client;

import com.example.melonstools.MelonsTools;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机页面滚动控制器(纯 Java 实现, 不依赖页面 JS)。
 * <p>
 * 背景: 为隐藏滚动条, phone.css 把滚动容器 overflow-y 设为 hidden。AUI 引擎的滚动
 * 指标(scrollHeight)只在 mayRenderScrollbar() 为 true 时由 LayoutCommit 提交更新,
 * overflow:hidden 下 scrollHeight 恒为 0 → 引擎 setScrollTop 的 limit=0, 会每帧拉回
 * 顶部。JS 侧绕引擎自绘滚动又受 Rhino 限制且不丝滑。
 * <p>
 * 方案(全部 Java 侧):
 * 1. 手动维护滚动容器的 scrollHeight 字段(引擎公开字段), 使引擎 setScrollTop 的
 *    limit = scrollHeight - 可视高 正确;
 * 2. 滚轮事件由 Java 监听, delta 累加后走 setScrollTop —— 引擎自带 0.2/帧缓动,
 *    滚动天然丝滑, 且不会被拉回;
 * 3. 自动滚底(打开聊天页 / 新消息到达)由 Java 检测。
 * <p>
 * 性能(2026-08-22 优化): 布局查询(getBoundingClientRect / getComputedStyle)开销大,
 * 不能在客户端 tick 每帧全量重算。改为:
 * - 滚动容器只绑定一次(按 refreshGeneration 检测 DOM 重建);
 * - 滚动指标(maxScroll)懒刷新: 仅在"需要时"(滚轮触发 / 打开聊天页 / 内容变化)
 *   才重新测量, 平时直接用缓存;
 * - 每 tick 只做 O(1) 的可见性/消息数检测(走引擎字段, 不触发布局)。
 * <p>
 * 线程规则: 一切 DOM 操作必须在客户端线程(tick 由 PhoneBridgeClient 在客户端线程调用)。
 */
public class PhoneScrollController {

    /** 需要接管滚动的容器选择器(与页面 JS 原 bindHiddenScroll 一致)。 */
    private static final String[] SCROLL_SELECTORS = {
            "#chatMessages",
            ".page-content",
            ".chat-members",
            ".icon-grid"
    };

    /** 滚轮 delta 缩放(引擎 deltaY 偏大, 乘系数获得合适手感)。 */
    private static final double WHEEL_SCALE = 0.5d;

    /** 容器 -> 已绑定事件的 generation(页面 refresh 重建 DOM 后需要重新绑定)。 */
    private static final Map<Document, Long> boundGeneration = new HashMap<>();

    /** 容器 -> 当前最大滚动位置缓存(懒刷新, 不每帧测量)。 */
    private static final Map<Element, Double> maxScrollCache = new HashMap<>();

    /** 聊天页可见性状态(检测 hidden→flex 切换)。 */
    private static final Map<Document, Boolean> chatVisiblePrev = new HashMap<>();

    /** 聊天消息数缓存(检测新消息到达)。 */
    private static final Map<Document, Integer> chatMsgCountPrev = new HashMap<>();

    /** 用户是否已手动滚过聊天页(自动滚底不打断用户阅读)。 */
    private static final Map<Document, Boolean> chatUserScrolled = new HashMap<>();

    /** 等待布局提交后滚底的标志。 */
    private static final Map<Document, Boolean> pendingChatBottom = new HashMap<>();

    private PhoneScrollController() {
    }

    /**
     * 客户端 tick 调用(PhoneBridgeClient 每 tick 调)。本方法只做 O(1) 轻量检测,
     * 不触发布局查询, 可安全每 tick 调用。
     */
    private static final Map<Document, Integer> autoScrollTick = new HashMap<>();

    /**
     * 客户端 tick 调用(PhoneBridgeClient 每 tick 调)。本方法只做 O(1) 轻量检测,
     * 不触发布局查询, 可安全每 tick 调用。
     */
    public static void tick(Document doc) {
        if (doc == null) return;
        ensureBound(doc);
        // 性能(2026-08-24): 滚底检测不需要每 tick(20次/秒), 降频到 5 tick(0.25s)
        // 减少 getElementById/getComputedStyle/getChildren 的桥接调用, 降低渲染线程开销
        Integer t = autoScrollTick.get(doc);
        int n = (t == null ? 0 : t) + 1;
        autoScrollTick.put(doc, n);
        if (n % 5 != 0) return;
        detectAutoScroll(doc);
    }

    /** 按 generation 检测 DOM 重建, 仅重建时重新查询 + 绑定事件。 */
    private static void ensureBound(Document doc) {
        long gen = doc.getRefreshGeneration();
        Long bound = boundGeneration.get(doc);
        if (bound != null && bound == gen) return;
        boundGeneration.put(doc, gen);
        maxScrollCache.clear();
        bindScrollContainers(doc);
        chatVisiblePrev.remove(doc);
        chatMsgCountPrev.remove(doc);
        chatUserScrolled.put(doc, false);
        pendingChatBottom.put(doc, true);
    }

    /** 滚动容器绑定 wheel 事件: delta 累加后走引擎 setScrollTop(自带缓动动画)。 */
    private static void bindScrollContainers(Document doc) {
        for (String selector : SCROLL_SELECTORS) {
            List<Element> elements = doc.querySelectorAll(selector);
            for (Element el : elements) {
                if (el == null) continue;
                el.addEventListener("wheel", event -> handleWheel(doc, el, event));
            }
        }
    }

    private static void handleWheel(Document doc, Element el, com.sighs.apricityui.event.Event event) {
        if (!(event instanceof MouseEvent mouse)) return;
        double delta = mouse.deltaY;
        if (delta == 0) return;
        event.preventDefault();
        // 用户手动滚动聊天页: 自动滚底不再打断阅读(其他容器滚动不影响聊天)
        if ("chatMessages".equals(el.id)) {
            chatUserScrolled.put(doc, true);
        }
        // 懒刷新: 滚轮触发时重新测量一次(内容可能已变化), 平时不测
        double max = recomputeMaxScroll(el);
        if (max <= 0) return;
        double cur = el.getScrollTop();
        double next = clamp(cur + delta * WHEEL_SCALE, 0, max);
        // 引擎 setScrollTop: 更新 targetScrollTop + registerActiveScroll,
        // 渲染循环每帧缓动逼近(SCROLL_EASING_FACTOR=0.2) → 丝滑, 不会被拉回。
        // 双保险: 同时写字段(target=当前)避免极端情况下引擎缓动不生效。
        el.setScrollTop(next);
        el.scrollTop = next;
        el.targetScrollTop = next;
        el.getRenderer().invalidateScrollVersion();
        try {
            doc.markDirty(el, Drawer.REPAINT | Drawer.HITTEST);
        } catch (Throwable ignored) {
        }
    }

    /** 自动滚底检测: 聊天页打开或新消息到达时, 布局就绪后定位到底部。 */
    private static void detectAutoScroll(Document doc) {
        Element pageChat = doc.getElementById("page-chat");
        if (pageChat == null) return;
        boolean visible = isVisibleFast(pageChat);
        chatVisiblePrev.put(doc, visible);
        if (!visible) {
            pendingChatBottom.put(doc, true); // 下次打开时滚底
            return;
        }
        Element chat = doc.getElementById("chatMessages");
        if (chat == null) return;

        // 新消息到达(自己发送 appendChild): 消息数增加 → 触发滚底
        int count = chat.getChildren().size();
        Integer prevCount = chatMsgCountPrev.get(doc);
        if (prevCount != null && count > prevCount && !Boolean.TRUE.equals(chatUserScrolled.get(doc))) {
            pendingChatBottom.put(doc, true);
        }
        chatMsgCountPrev.put(doc, count);

        // 需要滚底且布局就绪 → 滚底; 布局未就绪(max<=0)保持 pending, 下 tick 重试
        if (Boolean.TRUE.equals(pendingChatBottom.get(doc))) {
            double max = recomputeMaxScroll(chat);
            if (max > 0) {
                scrollContainerToBottom(doc, "chatMessages");
                pendingChatBottom.put(doc, false);
            }
        }
    }

    /** 轻量可见性检测: 直接读引擎 style 字段, 不触发布局重算。 */
    private static boolean isVisibleFast(Element el) {
        try {
            // getComputedStyle 在 AUI 中返回缓存样式, 不会强制重排
            String display = el.getComputedStyle().display;
            return display != null && !"none".equals(display);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 重新测量并缓存最大滚动位置(滚轮/滚底时调用, 平时不测)。 */
    private static double recomputeMaxScroll(Element el) {
        double contentH = measureContentHeight(el);
        double viewH = measureViewportHeight(el);
        double max = Math.max(0, contentH - viewH);
        // 难点: overflow:hidden 下引擎不更新 scrollHeight(恒 0), 而 setScrollTop 内部
        // limit = scrollHeight - 可视高 → 0 - 可视高 < 0 → clamp 到 0 → 滚轮被忽略/拉回。
        // 必须把手动测得的内容高真正写入引擎的 scrollHeight 公开字段, 让 limit 正确。
        try {
            el.scrollHeight = contentH;
        } catch (Throwable ignored) {
        }
        if (max > 0) {
            maxScrollCache.put(el, max);
        }
        return max;
    }

    /** 计算最大滚动位置 = 内容高 - 可视高(与页面 JS chatScrollLimit 等价, 但走 Java)。 */
    private static double computeMaxScroll(Element el) {
        double contentH = measureContentHeight(el);
        double viewH = measureViewportHeight(el);
        return Math.max(0, contentH - viewH);
    }

    /**
     * 内容高: 直接子元素最小 top 到最大 bottom 的差值。
     * 所有子元素共享同一祖先 scrollTop 偏移, 差值不受滚动影响。
     */
    private static double measureContentHeight(Element el) {
        List<Element> children = el.getChildren();
        if (children.isEmpty()) return el.scrollHeight;
        Double minTop = null;
        double maxBottom = 0;
        for (Element child : children) {
            if (child == null) continue;
            try {
                Element.DOMRect rect = child.getBoundingClientRect();
                if (rect.height > 0) {
                    if (minTop == null || rect.top < minTop) minTop = rect.top;
                    if (rect.bottom > maxBottom) maxBottom = rect.bottom;
                }
            } catch (Throwable ignored) {
                // 子元素布局未提交时跳过
            }
        }
        if (minTop == null || maxBottom <= 0) return el.scrollHeight;
        return Math.max(0, maxBottom - minTop);
    }

    /** 可视高: 自身 border-box 高 - padding(上+下)。 */
    private static double measureViewportHeight(Element el) {
        double viewH = 0;
        try {
            Element.DOMRect rect = el.getBoundingClientRect();
            if (rect != null) viewH = Math.max(0, rect.height);
        } catch (Throwable ignored) {
            viewH = 0;
        }
        if (viewH <= 0) return el.scrollHeight;
        double pad = 0;
        try {
            com.sighs.apricityui.style.Style style = el.getComputedStyle();
            if (style != null) {
                pad += parsePx(style.paddingTop);
                pad += parsePx(style.paddingBottom);
            }
        } catch (Throwable ignored) {
            pad = 0;
        }
        return Math.max(0, viewH - pad);
    }

    private static double parsePx(String value) {
        if (value == null || value.isBlank() || "unset".equals(value)) return 0;
        try {
            return Double.parseDouble(value.replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 立即定位滚动容器到底部(无动画), 并刷新缓存。
     * 与 JS setScrollTopImmediate 等价: 写字段 + 失效 Position 缓存 + 触发重绘。
     */
    public static void scrollContainerToBottom(Document doc, String id) {
        Element el = doc.getElementById(id);
        if (el == null) return;
        double max = computeMaxScroll(el);
        if (max <= 0) return;
        el.scrollTop = max;
        el.targetScrollTop = max;
        el.getRenderer().invalidateScrollVersion();
        try {
            doc.markDirty(el, Drawer.REPAINT | Drawer.HITTEST);
        } catch (Throwable ignored) {
        }
        maxScrollCache.put(el, max);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
