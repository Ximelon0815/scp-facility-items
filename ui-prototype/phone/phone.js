// ============================================================
//  Melon Phone 界面交互逻辑(网页原型版 + AUI 桥接版)
//  AUI 规范: Rhino JS,var + function,DOMContentLoaded 初始化
//  动态内容一律 createElement + textContent,不用 innerHTML
//  桥接模式: 数据由 Java 写入 #phoneData 等隐藏元素,本页轮询解析;
//            操作写入 #melonActionQueue 由 Java 消费后发网络包
// ============================================================

// 调试探针: 确认外部 script 是否被 AUI 加载并执行
try { document.body.setAttribute("data-phone-loaded", "1"); } catch (e) { document.body.setAttribute("data-phone-err", "1"); }

// 难点: AUI 中只有 HTML 解析时的 <canvas> 标签才是 Canvas 类(有 getContext/setWidth),
// document.createElement("canvas") 返回的只是普通 Element。这里在脚本加载时缓存引用,
// 防止进入贪吃蛇后 clearNode(gameBody) 把 canvas 从 DOM 移除导致 getElementById 失效。
var snakeCanvasEl = null;
try { snakeCanvasEl = document.getElementById("snakeCanvas"); } catch (e) { snakeCanvasEl = null; }

// 难点: AUI 的文档级文本选择(TextSelection)是内部监听器, 在目标元素冒泡阶段执行,
// 且不检查 defaultPrevented —— 双击/左键拖拽仍会选中文本出现蓝色框, CSS user-select
// 也拦不住(computed 已是 none 但引擎仍建立选区)。网页标准做法: 在捕获阶段拦截
// 左键 mousedown 并 stopImmediatePropagation, 阻止事件传播到目标, TextSelection
// 收不到事件就无法建立选区。不影响 click(mouseup 独立派发)与右键插旗(button=1 不拦)。
document.addEventListener("mousedown", function (event) {
  if (event.button === 0) {
    event.preventDefault();
    event.stopImmediatePropagation();
  }
}, true);

// ---- 数据容器(正式接入时来自 SyncPhoneData / SyncNearbyPlayers / SyncChatData) ----
// 示例数据已全部清空, 页面显示空态, 等待服务端真实数据
var mock = {
  friends: [],
  pending: [],
  nearby: [],
  dms: {},
  rooms: [],
  tasks: [],
  notices: [],
  anomalies: { instances: [] },
  myResearch: null
};

// 玩家信息(正式接入时来自服务端同步包 + Lightman's Currency 余额 + ID 卡部门)
// 示例值已清空, 等待服务端真实数据
var mockPlayer = {
  id: "",
  name: "",
  balance: "",
  dept: "",
  position: "",
  level: 0
};

// 每日任务上限:按 id 卡等级(1级3个 / 2级4个 / 3级6个 / 4级8个 / 5级10个)
var TASK_LIMIT_BY_LEVEL = { 1: 3, 2: 4, 3: 6, 4: 8, 5: 10 };

// 各部门任务池(正式接入时按玩家 ID 卡部门选取,每日 06:00 刷新)
// 示例任务已全部清空, 等待服务端下发
var mockTaskPools = {};

var state = {
  page: "home",
  commTab: "contacts",
  currentRoomId: null,
  currentDmUuid: null,  // 当前私聊对象 uuid(非空=处于私聊模式)
  currentGameId: null,
  myName: "我",
  reportDraft: { anomalyInstanceId: "", title: "", content: "", status: "idle" },
  departmentApplications: { items: [] },
  pendingCardQueue: { items: [] },
  researchDocuments: { items: [] },
  managementOffice: { records: [] },
  departmentTasks: { items: [] },
  dispatches: { items: [] },
  vitalAlerts: { items: [] },
  reinforcements: { items: [] },
  procurements: { items: [] },
  facilityPayments: { items: [] }
};

// 注: 滚动容器(chatMessages / page-content / chat-members / icon-grid)的滚轮滚动
// 与自动滚底已全部移交给 Java 侧 PhoneScrollController 接管(引擎 setScrollTop 自带
// 0.2/帧缓动, 丝滑且不会因 overflow:hidden 被拉回顶部)。此处不再有 JS 滚动代码。

// 设置内联样式。AUI 引擎的 JS 桥接不支持 el.style.x = v(不会进入布局),
// 必须走引擎的 setInlineStyleProperty(增量合并)才会被布局引擎读取并触发重排;
// 浏览器里则退回原生 el.style。setStyle(el, { width:"100px", marginLeft:"auto" })
function setStyle(el, props) {
  if (!el) { return; }
  if (typeof el.setInlineStyleProperty === "function") {
    // 引擎侧:合并成一次 setAttribute("style", ...),避免每个属性一次
    // setInlineStyleProperty 触发 Style.clone+解析+重排(进入游戏大量调用会卡顿)
    var cur = el.getAttribute("style") || "";
    var map = {};
    var pairs = cur.split(";");
    for (var i = 0; i < pairs.length; i++) {
      var p = pairs[i];
      var idx = p.indexOf(":");
      if (idx > 0) { map[p.substring(0, idx).trim()] = p.substring(idx + 1).trim(); }
    }
    for (var k in props) {
      var v = props[k];
      if (v !== null && v !== undefined && v !== "") { map[k] = v; }
    }
    var parts = [];
    for (var k2 in map) { parts.push(k2 + ":" + map[k2]); }
    el.setAttribute("style", parts.join(";"));
  } else {
    for (var k3 in props) { el.style[k3] = props[k3]; }
  }
}

// ---- 游戏桥接(Java PhoneBridgeClient 读写) ----
var bridgeMode = false;
var bridgeLastData = "";
var actionQueue = [];

// 轮询:服务端数据变化 → 应用;动作队列被 Java 消费 → 清空
// 难点: 同 tickClock——AUI 的 setInterval 在 Rhino 里不可靠, 改用 setTimeout 递归,
//       且间隔从 1s 放宽到 2s 减少主线程任务投递压力(数据本身是事件驱动推送, 无需 1s 高频)。
// 性能(2026-08-22): getElementById 每次都是 Java 桥接调用, 缓存元素引用避免每轮查询。
var bridgeLastLb = "";
var bridgePollCounter = 0;
var bridgeTimer = null;
var bridgeElData = null;
var bridgeElLb = null;
var bridgeElQueue = null;
// 单次检查所有 bridge 数据(不启动定时器)。init 首次打开时同步调用一次,
// 避免开局用空 mock 渲染主屏导致玩家信息空白; bridgePoll 轮询也复用本函数。
function bridgePollOnce() {
  var dataEl = bridgeElData || (bridgeElData = document.getElementById("phoneData"));
  if (dataEl) {
    var t = dataEl.textContent || "";
    if (t !== bridgeLastData) {
      bridgeLastData = t;
      if (t) { applyBridgeData(t); }
    }
  }
  var lbEl = bridgeElLb || (bridgeElLb = document.getElementById("leaderboardData"));
  if (lbEl) {
    var lt = lbEl.textContent || "";
    if (lt !== bridgeLastLb) {
      bridgeLastLb = lt;
      if (lt) { applyLeaderboardData(lt); }
    }
  }
  var qEl = bridgeElQueue || (bridgeElQueue = document.getElementById("melonActionQueue"));
  if (qEl && actionQueue.length > 0) {
    var v = qEl.value || "";
    if (v === "") { actionQueue = []; }
  }
  // 每 6 秒重试一次未加载成功的头像(2s 间隔下 %3)
  bridgePollCounter++;
  if (bridgePollCounter % 3 === 0) { refreshAvatars(); }
}

function bridgePoll() {
  bridgePollOnce();
  // 性能(2026-08-24): 数据由 Java 主动推送(事件驱动), 轮询只是兜底, 2s→5s 减少渲染线程定时器压力
  bridgeTimer = setTimeout(bridgePoll, 5000);
}

// 页面操作 → 写入动作队列,Java 轮询消费后发网络包
function pushAction(action) {
  actionQueue.push(action);
  var qEl = bridgeElQueue || (bridgeElQueue = document.getElementById("melonActionQueue"));
  if (qEl) { qEl.value = JSON.stringify(actionQueue); }
}

function pushReportSubmit(anomalyInstanceId, title, content) {
  state.reportDraft.anomalyInstanceId = anomalyInstanceId || "";
  state.reportDraft.title = title || "";
  state.reportDraft.content = content || "";
  state.reportDraft.status = "queued";
  pushAction({ t: "report_submit", anomalyInstanceId: state.reportDraft.anomalyInstanceId, title: state.reportDraft.title, content: state.reportDraft.content });
}

function isBridgeFriend(uuid) {
  for (var i = 0; i < mock.friends.length; i++) {
    if (mock.friends[i].uuid === uuid) { return true; }
  }
  return false;
}

// 服务端榜单数据覆盖本地模拟
function applyLeaderboardData(jsonText) {
  var data;
  try { data = JSON.parse(jsonText); } catch (err) { return; }
  if (data.leaderboards) {
    for (var game in data.leaderboards) {
      if (!mockLeaderboards[game]) { mockLeaderboards[game] = {}; }
      var g = data.leaderboards[game];
      for (var diff in g) {
        var arr = [];
        for (var i = 0; i < g[diff].length; i++) {
          arr.push({ name: g[diff][i].name, score: g[diff][i].score, isMe: g[diff][i].isMe === true });
        }
        mockLeaderboards[game][diff] = arr;
      }
    }
  }
  var modal = $("leaderboardModal");
  if (modal && modal.className.indexOf("open") >= 0) { renderLeaderboard(); }
}

// 服务端下发的每日任务
var bridgeTasks = [];

// 应用服务端全量数据并重渲染
function applyBridgeData(jsonText) {
  var data;
  try { data = JSON.parse(jsonText); } catch (err) { return; }
  if (data.player) {
    mockPlayer.id = data.player.id || mockPlayer.id;
    mockPlayer.name = data.player.name || mockPlayer.name;
    mockPlayer.dept = data.player.dept || "";
    mockPlayer.position = data.player.position || "";
    if (typeof data.player.level === "number") { mockPlayer.level = data.player.level; } else { mockPlayer.level = 0; }
    mockPlayer.balance = data.player.balance;
    // 本人名称跟随 ID 卡名称(服务端 player.name = CardName), 用于聊天"我"的消息署名与房主判断
    state.myName = mockPlayer.name;
  }
  if (data.friends) {
    mock.friends = [];
    for (var i = 0; i < data.friends.length; i++) {
      mock.friends.push({ uuid: data.friends[i].uuid, name: data.friends[i].name, online: data.friends[i].online === true });
    }
  }
  if (data.pending) {
    mock.pending = [];
    for (var j = 0; j < data.pending.length; j++) {
      mock.pending.push({ uuid: data.pending[j].uuid, name: data.pending[j].name });
    }
  }
  if (data.nearby) {
    nearbyReceived = true;
    mock.nearby = [];
    for (var k = 0; k < data.nearby.length; k++) {
      var n = data.nearby[k];
      mock.nearby.push({ uuid: n.uuid, name: n.name, dist: parseFloat(n.dist) || 0, isFriend: isBridgeFriend(n.uuid) });
    }
  } else if (bridgeMode && !nearbyReceived) {
    // 桥接模式首次全量同步不含 nearby(服务端不主动扫描):清掉网页预览假数据
    mock.nearby = [];
  }
  if (data.rooms) {
    mock.rooms = [];
    for (var r = 0; r < data.rooms.length; r++) {
      var room = data.rooms[r];
      var members = [];
      for (var m = 0; m < room.members.length; m++) {
        members.push({ uuid: room.members[m].uuid, name: room.members[m].name });
      }
      var messages = [];
      for (var mi = 0; mi < room.messages.length; mi++) {
        var msg = room.messages[mi];
        messages.push({ sender: msg.sender, name: msg.name, time: msg.time, content: msg.content, mention: msg.mention === true });
      }
      mock.rooms.push({ id: room.id, name: room.name, owner: room.ownerName || "?", isOwner: room.isOwner === true, members: members, unread: room.unread || 0, messages: messages });
    }
  }
  if (data.anomalies && data.anomalies.instances) {
    mock.anomalies = { instances: data.anomalies.instances };
  }
  if (data.myResearch) {
    mock.myResearch = data.myResearch;
  }
  if (data.visibleNotices || data.notices) {
    var ns = data.visibleNotices || data.notices || [];
    mock.notices = [];
    for (var ni = 0; ni < ns.length; ni++) {
      mock.notices.push(ns[ni]);
    }
  }
  if (data.tasks) {
    bridgeTasks = [];
    for (var ti = 0; ti < data.tasks.length; ti++) {
      var t = data.tasks[ti];
      bridgeTasks.push({ id: t.id, name: t.name, desc: t.desc, cur: t.cur, goal: t.goal, state: t.state, reward: t.reward });
    }
  }
  state.departmentApplications = data.departmentApplications || { items: [] };
  state.pendingCardQueue = data.pendingCardQueue || { items: [] };
  state.researchDocuments = data.researchDocuments || { items: [] };
  state.managementOffice = data.managementOffice || { records: [], skeleton: true };
  state.departmentTasks = data.departmentTasks || { items: [], skeleton: true };
  state.dispatches = data.dispatches || { items: [], skeleton: true };
  state.vitalAlerts = data.vitalAlerts || { items: [], skeleton: true };
  state.reinforcements = data.reinforcements || { items: [], skeleton: true };
  state.procurements = data.procurements || { items: [], skeleton: true };
  state.facilityPayments = data.facilityPayments || { items: [], skeleton: true };
  renderPlayerInfo();
  // 性能(2026-08-24): 只强制渲染当前可见的 comm tab, 其他标脏(切 tab 时延迟渲染)。
  // 减少 comm 常驻 DOM 规模 → 切页布局更快。
  renderContacts(true);
  if (state.commTab === "nearby") { renderNearby(true); } else { renderDirty.nearby = true; }
  if (state.commTab === "rooms") { renderRooms(true); } else { renderDirty.rooms = true; }
  renderTasks();
  renderImportant();
  renderDept();
  renderReportForm();
  updateUnread();
  updateHomeBadges();
  // 当前房间被解散:移出后自动返回群聊列表
  if (state.page === "chat" && state.currentRoomId && !findRoom(state.currentRoomId)) {
    state.currentRoomId = null;
    gotoPage("comm");
    switchCommTab("rooms");
  }
}

// ---- 工具 ----
function $(id) { return document.getElementById(id); }

// 难点: 输入框赋值必须同时清 property 和 attribute。浏览器只有 value= 会更新
// 显示文本(setAttribute 只改 attribute 不改 property); AUI 反之——value= 直接写
// public 字段会绕过光标重置(cursor 残留导致 insertText 对空串 substring 崩溃),
// 必须 setAttribute("value") 触发 syncTextAttribute 把 cursor 归零。
function setInputValue(node, v) {
  node.value = v;
  node.setAttribute("value", v);
}

function el(tag, className, text) {
  var node = document.createElement(tag);
  if (className) { node.className = className; }
  if (text !== undefined && text !== null) { node.textContent = "" + text; }
  return node;
}

function clearNode(node) {
  while (node.firstChild) { node.removeChild(node.firstChild); }
}
function listLimit(list, max) {
  list = list || [];
  max = Number(max || 0);
  if (max <= 0 || list.length <= max) { return list; }
  return list.slice(0, max);
}
function appendMoreHint(parent, list, max, label) {
  list = list || [];
  if (parent && list.length > max) {
    parent.appendChild(el("div", "empty-tip", (label || "列表") + "仅显示前 " + max + " 条，剩余 " + (list.length - max) + " 条请使用筛选或等待分页查看。"));
  }
}

function pad2(n) { return n < 10 ? "0" + n : "" + n; }

function fmtTime() {
  var d = new Date();
  return pad2(d.getHours()) + ":" + pad2(d.getMinutes());
}

function fmtDate() {
  var d = new Date();
  var weeks = ["日", "一", "二", "三", "四", "五", "六"];
  return (d.getMonth() + 1) + "月" + d.getDate() + "日 周" + weeks[d.getDay()];
}

function fmtDateShort() {
  var d = new Date();
  return pad2(d.getMonth() + 1) + "." + pad2(d.getDate());
}

// ---- 页面导航 ----
var NAV_KEYS = { home: 0, comm: 1, log: 2, report: 3, dept: 4, fun: 5 };

function gotoPage(page) {
  state.page = page;
  var pages = ["home", "comm", "chat", "log", "report", "dept", "fun", "game"];
  for (var i = 0; i < pages.length; i++) {
    var node = $("page-" + pages[i]);
    if (pages[i] === page) { node.className = "page"; }
    else { node.className = "page page-hidden"; }
  }
  // 游戏页归入娱乐导航高亮
  var navPage = (page === "chat") ? "comm" : (page === "game" ? "fun" : page);
  var tabs = document.getElementsByClassName("side-item");
  for (var j = 0; j < tabs.length; j++) {
    if (NAV_KEYS[navPage] === j) { tabs[j].className = "side-item side-active"; }
    else { tabs[j].className = "side-item"; }
  }
  if (page === "comm") { applyCommTab(); }
  // 切到主页时立即检查一次 bridge 数据(服务端可能已推送), 避免玩家信息栏停留在
  // 打开手机时用空 mock 渲染的空白状态。数据已就绪则 applyBridgeData 内部重渲染玩家信息。
  if (page === "home" && bridgeMode) { bridgePollOnce(); }
  // 性能(2026-08-24): comm 子树大(3 个 panel ~133 元素), 切页时 className 切换与
  // 列表重建若同帧叠加,AUI 单帧布局开销大(实测 comm 切换 430ms, 用户环境 2-3 秒)。
  // 渲染延迟一帧: 让 className 切换的布局先提交, 再重建列表, 分摊到两帧。
  if (page === "comm") {
    // 性能(2026-08-24): 从其他页进入 comm 时刷新当前 tab;comm 内 tab 切换也刷新。
    // 同一 tab 重复切换(如 home→comm→home→comm)不重建列表(renderDirty 已缓存)。
    setTimeout(function () {
      if (state.commTab === "contacts") { renderContacts(); }
      else if (state.commTab === "nearby") { renderNearby(true); }
      else if (state.commTab === "rooms") { renderRooms(true); }
    }, 0);
  }
  // 聊天页滚动(滚底)由 Java 侧 PhoneScrollController 自动检测接管
  // 离开游戏页时停止贪吃蛇计时器
  if (page !== "game" && snake && snake.timer) {
    clearTimeout(snake.timer);
    snake.timer = null;
    snake.running = false;
  }
}

// ---- 通讯板块内 tab ----
var lastCommTab = null;
function applyCommTab() {
  var tabs = document.getElementsByClassName("comm-tab");
  for (var i = 0; i < tabs.length; i++) {
    if (tabs[i].getAttribute("data-comm-tab") === state.commTab) {
      tabs[i].className = "comm-tab comm-tab-active";
    } else {
      tabs[i].className = "comm-tab";
    }
  }
  // 性能(2026-08-24): tab 未变化时跳过 panel 的 className 操作——AUI 对 className 变更
  // 会触发 style recalc(遍历子树), 每次切 comm 都重设 3 个 panel 即使没变也浪费。
  if (lastCommTab === state.commTab) { return; }
  lastCommTab = state.commTab;
  var panels = ["contacts", "nearby", "rooms"];
  for (var j = 0; j < panels.length; j++) {
    var panel = $("comm-" + panels[j]);
    if (panels[j] === state.commTab) { panel.className = "comm-panel"; }
    else { panel.className = "comm-panel comm-panel-hidden"; }
  }
  // 列表渲染由 gotoPage 延迟一帧执行(见 gotoPage 的 setTimeout 优化), 此处不重复渲染
}
function switchCommTab(tab) {
  state.commTab = tab;
  gotoPage("comm");
}

// ---- 时钟 ----
// 性能(2026-08-24): 原 setTimeout 递归每 2 秒投递主线程任务, 大页面下积压卡渲染线程。
// 时钟文本改由 Java 侧 PhoneBridgeClient.updateClock 每 0.5 秒更新(#sysTime/#homeTime),
// 此处只做初始化; updateNearbyBtn 冷却倒计时仍保留(仅冷却中需要刷新)。
var clockTimer = null;
function tickClock() {
  var t = fmtTime();
  var s = $("sysTime");
  if (s) { s.textContent = t; }
  var h = $("homeTime");
  if (h) { h.textContent = t; }
  // 冷却倒计时: 仅冷却中需要, 用 setTimeout 递归(频率低, 冷却结束即停)
  if (Date.now() < nearbyCdUntil) {
    updateNearbyBtn();
    clockTimer = setTimeout(tickClock, 1000);
  } else {
    clockTimer = null;
  }
}

function tickDate() {
  $("logDate").textContent = fmtDateShort();
}

// ---- 联系人渲染 ----
// 玩家头像: AUI <texture> 直接绑定 Java 注册的动态纹理(melonstools:avatars/<uuid>),
// 无文件 I/O、无需轮询重试。动态纹理未就绪时回退首字文字头像。
// 性能(2026-08-24): <texture> 元素在 AUI 布局时开销高于纯文本, 通讯列表项多时
// 切页会卡(实测 12 个头像 + 1 texture 使 comm 切换比别页慢 ~220ms)。方案:
// 头像纹理只对"动态纹理已注册"的 uuid 创建——但 JS 无法查询注册状态, 改为:
// 页面初始化时由 Java 通知已注册的头像列表(#avatarReady), JS 只对有纹理的建 <texture>。
// 临时方案: 先禁用 <texture>(文字头像), 待 Java 侧头像注册稳定后再启用。
function buildAvatar(uuid, name, avatarSrc, cls) {
  var box = el("div", cls ? "avatar " + cls : "avatar");
  var hasDynamic = bridgeMode && uuid;
  if (hasDynamic) {
    var tex = el("texture");
    tex.setAttribute("src", "melonstools:avatars/" + uuid);
    box.appendChild(tex);
  }
  box.appendChild(el("span", "avatar-text", name.charAt(0)));
  return box;
}

// 头像由 Java 注册为动态纹理(内存, 无 PNG 文件), 无需轮询重试
function refreshAvatars() {
  return;
}

// 渲染脏标记:数据变化才重建列表。切页/切 tab 时数据未变则跳过,避免切换卡顿。
var renderDirty = { contacts: true, nearby: true, rooms: true };
// 桥接模式:服务端不主动下发附近(仅点刷新才扫描)。此标志标记是否收到过 nearby,用于首次清掉网页预览假数据。
var nearbyReceived = false;

function renderContacts(force) {
  if (!force && !renderDirty.contacts) { return; }
  renderDirty.contacts = false;
  var t0 = Date.now();
  renderRequests();
  renderFriends();
  updateHomeBadges();
  try { console.log("[Perf] renderContacts=" + (Date.now() - t0) + "ms"); } catch (e) {}
}

function renderRequests() {
  var section = $("requestSection");
  var list = $("requestList");
  clearNode(list);
  if (mock.pending.length === 0) {
    section.className = "section section-hidden";
    return;
  }
  section.className = "section";
  $("requestCount").textContent = "" + mock.pending.length;
  for (var i = 0; i < mock.pending.length; i++) {
    list.appendChild(buildRequestItem(mock.pending[i]));
  }
}

function buildRequestItem(req) {
  var row = el("div", "phone-list-item");
  row.appendChild(buildAvatar(req.uuid, req.name, req.avatar, "avatar-gold"));
  var main = el("div", "item-main");
  main.appendChild(el("div", "item-name", req.name));
  main.appendChild(el("div", "item-sub", "请求添加你为好友"));
  row.appendChild(main);
  var actions = el("div", "item-actions");
  var ok = el("button", "mini-btn mini-btn-ok", "接受");
  ok.addEventListener("click", function () { acceptRequest(req.uuid, req.name); });
  var no = el("button", "mini-btn mini-btn-no", "拒绝");
  no.addEventListener("click", function () { rejectRequest(req.uuid, req.name); });
  actions.appendChild(ok);
  actions.appendChild(no);
  row.appendChild(actions);
  return row;
}

function acceptRequest(uuid, name) {
  for (var i = mock.pending.length - 1; i >= 0; i--) {
    if (mock.pending[i].uuid === uuid) { mock.pending.splice(i, 1); }
  }
  mock.friends.push({ uuid: uuid, name: name, online: true });
  if (outgoingSet[uuid]) { delete outgoingSet[uuid]; }
  pushAction({ t: "friend_accept", u: uuid });
  renderContacts(true);
  renderNearby(true);
}

function rejectRequest(uuid, name) {
  for (var i = mock.pending.length - 1; i >= 0; i--) {
    if (mock.pending[i].uuid === uuid) { mock.pending.splice(i, 1); }
  }
  pushAction({ t: "friend_reject", u: uuid });
  renderContacts(true);
}

function renderFriends() {
  var list = $("friendList");
  clearNode(list);
  $("friendCount").textContent = "" + mock.friends.length;
  if (mock.friends.length === 0) {
    list.appendChild(el("div", "empty-tip", "还没有好友,去附近页添加吧"));
    return;
  }
  for (var i = 0; i < mock.friends.length; i++) {
    list.appendChild(buildFriendItem(mock.friends[i]));
  }
}

function buildFriendItem(f) {
  var row = el("div", "phone-list-item friend-item");
  row.appendChild(buildAvatar(f.uuid, f.name, f.avatar, "avatar-green"));
  var main = el("div", "item-main");
  main.appendChild(el("div", "item-name", f.name));
  main.appendChild(el("div", "item-sub", f.online ? "在线" : "离线"));
  row.appendChild(main);
  var actions = el("div", "item-actions");
  var rm = el("button", "mini-btn mini-btn-danger", "移除");
  rm.addEventListener("click", function (event) {
    // 阻止冒泡:点"移除"不触发整行打开私聊
    if (event && event.stopPropagation) { event.stopPropagation(); }
    removeFriend(f.uuid, f.name);
  });
  actions.appendChild(rm);
  row.appendChild(actions);
  // 点击整行打开与该好友的私聊
  row.addEventListener("click", function () { openDm(f); });
  return row;
}

// ---- 私聊 ----
function findDm(uuid) {
  if (!mock.dms[uuid]) {
    mock.dms[uuid] = { name: uuid, messages: [] };
  }
  return mock.dms[uuid];
}

function openDm(friend) {
  state.currentDmUuid = friend.uuid;
  state.currentRoomId = null;
  var dm = findDm(friend.uuid);
  if (!dm.name || dm.name === friend.uuid) { dm.name = friend.name; }
  $("chatTitle").textContent = friend.name;
  // 私聊模式:隐藏成员/设置/邀请等群聊专属 UI,返回按钮回联系人
  $("btnToggleMembers").className = "head-btn hidden";
  $("btnRoomSettings").className = "head-btn hidden";
  $("btnInviteMember").className = "member-invite hidden";
  document.querySelector(".chat-members").className = "chat-members chat-members-hidden";
  var backBtn = $("btnBackToRooms");
  backBtn.textContent = "← 返回联系人";
  backBtn.className = "head-btn";
  var box = $("chatMessages");
  clearNode(box);
  for (var i = 0; i < dm.messages.length; i++) {
    box.appendChild(buildMessage(dm.messages[i]));
  }
  gotoPage("chat");
}

function removeFriend(uuid, name) {
  for (var i = mock.friends.length - 1; i >= 0; i--) {
    if (mock.friends[i].uuid === uuid) { mock.friends.splice(i, 1); }
  }
  for (var j = 0; j < mock.nearby.length; j++) {
    if (mock.nearby[j].uuid === uuid) { mock.nearby[j].isFriend = false; }
  }
  if (outgoingSet[uuid]) { delete outgoingSet[uuid]; }
  pushAction({ t: "friend_remove", u: uuid });
  renderContacts(true);
  renderNearby(true);
}

// ---- 附近玩家 ----
// 刷新冷却(秒),点击后 10 秒内不可再次点击
var nearbyCdUntil = 0;  // 下次可扫描的时间戳(毫秒),到期前禁止刷新
var nearbyCdTimer = null;

function startNearbyCooldownTicker() {
  if (nearbyCdTimer) { return; }
  function step() {
    updateNearbyBtn();
    if (Date.now() < nearbyCdUntil) {
      nearbyCdTimer = setTimeout(step, 1000);
    } else {
      nearbyCdTimer = null;
      updateNearbyBtn();
    }
  }
  nearbyCdTimer = setTimeout(step, 1000);
}

function tryRefreshNearby() {
  var now = Date.now();
  if (now < nearbyCdUntil) { updateNearbyBtn(); startNearbyCooldownTicker(); return; }
  nearbyCdUntil = now + 10000;  // 10 秒 CD
  updateNearbyBtn();
  startNearbyCooldownTicker();
  pushAction({ t: "nearby_refresh" });
  renderNearby(true);
}

function updateNearbyBtn() {
  var btn = $("btnRefreshNearby");
  if (!btn) return;
  var remain = Math.max(0, Math.ceil((nearbyCdUntil - Date.now()) / 1000));
  if (remain > 0) {
    // 禁用视觉由 CSS .head-btn:disabled 提供, 不动态 setStyle opacity
    // (AUI 运行期 opacity<1 触发离屏合成 REORDER, 会导致按钮消失)
    btn.disabled = true;
    btn.textContent = "冷却中 (" + remain + "s)";
  } else {
    btn.disabled = false;
    btn.textContent = "刷新";
  }
}

function renderNearby(force) {
  if (!force && !renderDirty.nearby) { return; }
  // 性能(2026-08-24): 面板不可见时不实际重建 DOM(只标脏), 减少 comm 常驻子树规模。
  // 切到该 tab 时 applyCommTab 的延迟渲染会强制重建。
  if (!force && state.commTab !== "nearby") {
    renderDirty.nearby = true;
    return;
  }
  renderDirty.nearby = false;
  var list = $("nearbyList");
  clearNode(list);
  if (mock.nearby.length === 0) {
    // 桥接模式未扫描过:提示点刷新;已扫描为空:显示无玩家
    list.appendChild(el("div", "empty-tip", (bridgeMode && !nearbyReceived) ? "点击右上角刷新扫描附近玩家" : "附近没有其他玩家"));
    return;
  }
  for (var i = 0; i < mock.nearby.length; i++) {
    list.appendChild(buildNearbyItem(mock.nearby[i]));
  }
}

// 已发送的好友请求(会话内本地状态,对方接受后由服务端同步消除)
var outgoingSet = {};

function buildNearbyItem(p) {
  var row = el("div", "phone-list-item");
  row.appendChild(buildAvatar(p.uuid, p.name, p.avatar, ""));
  var main = el("div", "item-main");
  main.appendChild(el("div", "item-name", p.name));
  main.appendChild(el("div", "item-sub", "距离 " + p.dist + " 格"));
  row.appendChild(main);
  var actions = el("div", "item-actions");
  if (p.isFriend) {
    var tag = el("span", "badge badge-success", "已是好友");
    actions.appendChild(tag);
  } else if (outgoingSet[p.uuid]) {
    var wait = el("span", "badge", "请求已发");
    actions.appendChild(wait);
  } else {
    var add = el("button", "mini-btn mini-btn-ok", "加好友");
    add.addEventListener("click", function () { addFriend(p.uuid, p.name); });
    actions.appendChild(add);
  }
  row.appendChild(actions);
  return row;
}

function addFriend(uuid, name) {
  outgoingSet[uuid] = true;
  pushAction({ t: "friend_add", u: uuid });
  renderNearby(true);
  renderContacts(true);
}


// ---- 群聊 ----
function renderRooms(force) {
  if (!force && !renderDirty.rooms) { return; }
  // 性能(2026-08-24): 面板不可见时不实际重建 DOM, 减少 comm 常驻子树规模
  if (!force && state.commTab !== "rooms") {
    renderDirty.rooms = true;
    return;
  }
  renderDirty.rooms = false;
  var t0 = Date.now();
  var list = $("roomList");
  clearNode(list);
  if (mock.rooms.length === 0) {
    list.appendChild(el("div", "empty-tip", "还没有群聊,点右上角新建或加入"));
    return;
  }
  for (var i = 0; i < mock.rooms.length; i++) {
    list.appendChild(buildRoomItem(mock.rooms[i]));
  }
  updateHomeBadges();
  try { console.log("[Perf] renderRooms=" + (Date.now() - t0) + "ms"); } catch (e) {}
}

function buildRoomItem(room) {
  var row = el("div", "phone-list-item room-item");
  row.appendChild(buildRoomAvatar(room));
  var main = el("div", "item-main");
  main.appendChild(el("div", "item-name", room.name));
  var lastMsg = room.messages.length > 0 ? room.messages[room.messages.length - 1] : null;
  main.appendChild(el("div", "item-sub", lastMsg ? (lastMsg.name + ": " + lastMsg.content) : "暂无消息"));
  row.appendChild(main);
  if (room.unread > 0) {
    row.appendChild(el("span", "badge badge-danger", "" + room.unread));
  }
  if (room.isOwner === true || room.owner === state.myName) {
    row.appendChild(el("span", "badge badge-danger", "房主"));
  }
  var badge = el("span", "badge", "" + room.members.length + " 人");
  row.appendChild(badge);
  row.addEventListener("click", function () { enterRoom(room.id); });
  return row;
}

// 房间图标: 固定"群"字标识(群聊头像自定义功能已于 2026-08-24 移除)
function buildRoomAvatar(room) {
  var box = el("div", "avatar");
  box.appendChild(el("span", "avatar-text", "群"));
  return box;
}

function enterRoom(roomId) {
  state.currentRoomId = roomId;
  state.currentDmUuid = null;  // 退出私聊模式,恢复群聊 UI
  var room = findRoom(roomId);
  if (!room) { return; }
  room.unread = 0;
  // 查看后清除该群聊的 @ 标记:主页重要消息不再显示该群聊(本地已读)
  for (var mi = 0; mi < room.messages.length; mi++) {
    room.messages[mi].mention = false;
  }
  // 上报服务端已读(桥接模式),服务端同样清除该玩家该群聊的 @ 标记
  pushAction({ t: "room_read", roomId: room.id });
  $("chatTitle").textContent = room.name;
  // 恢复群聊专属 UI(成员按钮、返回群聊文本)
  var backBtn = $("btnBackToRooms");
  backBtn.textContent = "← 返回群聊";
  backBtn.className = "head-btn";
  $("btnToggleMembers").className = "head-btn";
  var box = $("chatMessages");
  clearNode(box);
  for (var i = 0; i < room.messages.length; i++) {
    box.appendChild(buildMessage(room.messages[i]));
  }
  renderMembers(room);
  // 成员栏默认收起
  document.querySelector(".chat-members").className = "chat-members chat-members-hidden";
  // 设置/邀请按钮仅房主可见(邀请在成员栏头部)
  if (room.isOwner === true || room.owner === state.myName) {
    $("btnRoomSettings").className = "head-btn";
    $("btnInviteMember").className = "member-invite";
  } else {
    $("btnRoomSettings").className = "head-btn hidden";
    $("btnInviteMember").className = "member-invite hidden";
  }
  updateUnread();
  renderRooms();
  renderImportant();
  gotoPage("chat");
}

function findRoom(roomId) {
  for (var i = 0; i < mock.rooms.length; i++) {
    if (mock.rooms[i].id === roomId) { return mock.rooms[i]; }
  }
  return null;
}

function buildMessage(msg) {
  var mine = msg.sender === "mine";
  var row = el("div", mine ? "msg-row msg-row-mine" : "msg-row msg-row-other");
  var meta = el("div", "msg-meta", msg.name + " " + msg.time);
  var bubble = el("div", "msg-bubble", msg.content);
  row.appendChild(meta);
  row.appendChild(bubble);
  return row;
}

function sendMessage() {
  var input = $("chatInput");
  var text = input.value;
  if (!text || !text.trim()) { return; }
  // 私聊模式:写入当前私聊会话
  if (state.currentDmUuid) {
    var dm = findDm(state.currentDmUuid);
    dm.messages.push({ sender: "mine", name: state.myName, time: fmtTime(), content: text.trim(), mention: false });
    setInputValue(input, "");
    var box = $("chatMessages");
    box.appendChild(buildMessage(dm.messages[dm.messages.length - 1]));
    pushAction({ t: "dm_send", u: state.currentDmUuid, content: text.trim() });
    return;
  }
  var room = findRoom(state.currentRoomId);
  if (!room) { return; }
  room.messages.push({ sender: "mine", name: state.myName, time: fmtTime(), content: text.trim(), mention: text.indexOf("@") >= 0 });
  setInputValue(input, "");
  var box = $("chatMessages");
  box.appendChild(buildMessage(room.messages[room.messages.length - 1]));
  pushAction({ t: "chat_send", roomId: room.id, content: text.trim() });
}

function createRoom() {
  var input = $("roomNameInput");
  var name = input.value;
  if (!name || !name.trim()) { return; }
  mock.rooms.push({
    id: "r" + Date.now(),
    name: name.trim(),
    owner: state.myName,
    isOwner: true,
    members: [state.myName],
    messages: []
  });
  setInputValue(input, "");
  closeModal("newRoomModal");
  renderRooms();
  pushAction({ t: "room_create", name: name.trim() });
}

// ---- 成员管理(房主制: 只有房主能移出成员,成员不可自主退群) ----
function renderMembers(room) {
  $("memberCount").textContent = "" + room.members.length;
  var list = $("memberList");
  clearNode(list);
  var isOwner = room.isOwner === true || room.owner === state.myName;
  for (var i = 0; i < room.members.length; i++) {
    list.appendChild(buildMemberItem(room, room.members[i], isOwner));
  }
}

function buildMemberItem(room, member, isOwner) {
  var name = typeof member === "string" ? member : member.name;
  var uuid = typeof member === "string" ? "" : member.uuid;
  var row = el("div", "member-item");
  row.appendChild(el("span", "member-name", name));
  if (name === room.owner) {
    row.appendChild(el("span", "member-owner", "房主"));
  } else if (isOwner) {
    var kick = el("button", "member-kick", "移出");
    kick.addEventListener("click", function () { kickMember(room, uuid, name); });
    row.appendChild(kick);
  }
  return row;
}

function kickMember(room, uuid, name) {
  for (var i = room.members.length - 1; i >= 0; i--) {
    var m = room.members[i];
    var n = typeof m === "string" ? m : m.name;
    if (n === name) { room.members.splice(i, 1); }
  }
  pushAction({ t: "room_kick", roomId: room.id, u: uuid });
  renderMembers(room);
  renderRooms();
}

// ---- 成员栏折叠 ----
function toggleMembers() {
  var panel = document.querySelector(".chat-members");
  if (panel.className.indexOf("chat-members-hidden") >= 0) {
    panel.className = "chat-members";
  } else {
    panel.className = "chat-members chat-members-hidden";
  }
}

// ---- 房主群聊设置(改名/换头像/解散) ----
var dissolveArmed = false;
var dissolveTimer = null;

function openRoomSettings() {
  var room = findRoom(state.currentRoomId);
  if (!room) { return; }
  setInputValue($("renameInput"), room.name);
  resetDissolveBtn();
  openModal("roomSettingsModal");
}

function confirmRename() {
  var input = $("renameInput");
  var name = input.value;
  if (!name || !name.trim()) { return; }
  var room = findRoom(state.currentRoomId);
  if (!room) { return; }
  room.name = name.trim();
  $("chatTitle").textContent = room.name;
  closeModal("roomSettingsModal");
  renderRooms();
  renderImportant();
  pushAction({ t: "room_rename", roomId: room.id, name: name.trim() });
}

function resetDissolveBtn() {
  dissolveArmed = false;
  if (dissolveTimer) { clearTimeout(dissolveTimer); dissolveTimer = null; }
  var btn = $("btnDissolveRoom");
  if (btn) { btn.textContent = "解散群聊"; }
}

function dissolveRoom() {
  var room = findRoom(state.currentRoomId);
  if (!room) { return; }
  if (!dissolveArmed) {
    // 防误触:二次点击确认
    dissolveArmed = true;
    $("btnDissolveRoom").textContent = "再点一次确认解散";
    dissolveTimer = setTimeout(resetDissolveBtn, 3000);
    return;
  }
  resetDissolveBtn();
  closeModal("roomSettingsModal");
  if (!bridgeMode) {
    for (var i = mock.rooms.length - 1; i >= 0; i--) {
      if (mock.rooms[i].id === room.id) { mock.rooms.splice(i, 1); }
    }
    state.currentRoomId = null;
    renderRooms();
    gotoPage("comm");
    switchCommTab("rooms");
  } else {
    pushAction({ t: "room_dissolve", roomId: room.id });
  }
}


// ---- 输入名称加入频道 ----
function joinRoomByName() {
  var input = $("joinRoomInput");
  var name = input.value;
  if (!name || !name.trim()) { return; }
  name = name.trim();
  if (!bridgeMode) {
    // 模拟模式:按名称(忽略大小写)查找并加入
    var found = null;
    for (var i = 0; i < mock.rooms.length; i++) {
      if (mock.rooms[i].name.toLowerCase() === name.toLowerCase()) { found = mock.rooms[i]; break; }
    }
    if (!found) {
      $("joinRoomHelp").textContent = "未找到该频道,请确认名称";
      return;
    }
    for (var j = 0; j < found.members.length; j++) {
      var m = found.members[j];
      var n = typeof m === "string" ? m : m.name;
      if (n === state.myName) {
        $("joinRoomHelp").textContent = "你已在该频道中";
        return;
      }
    }
    found.members.push(state.myName);
    found.messages.push({ sender: "other", name: "系统", time: fmtTime(), content: state.myName + " 加入了频道", mention: false });
    renderRooms();
  } else {
    // 桥接模式:交给服务端按名查找并加入
    pushAction({ t: "room_join", name: name });
  }
  setInputValue(input, "");
  $("joinRoomHelp").textContent = "输入准确的频道名称加入";
  closeModal("joinRoomModal");
}

// ---- 邀请好友入群(房主) ----
function openInviteModal() {
  var room = findRoom(state.currentRoomId);
  if (!room) { return; }
  renderInviteList(room);
  openModal("inviteModal");
}

function renderInviteList(room) {
  var list = $("inviteList");
  clearNode(list);
  var candidates = [];
  for (var i = 0; i < mock.friends.length; i++) {
    var f = mock.friends[i];
    var inRoom = false;
    for (var j = 0; j < room.members.length; j++) {
      var m = room.members[j];
      var n = typeof m === "string" ? m : m.name;
      var u = typeof m === "string" ? "" : m.uuid;
      if (n === f.name || (u && u === f.uuid)) { inRoom = true; break; }
    }
    if (!inRoom) { candidates.push(f); }
  }
  if (candidates.length === 0) {
    list.appendChild(el("div", "empty-tip", "没有可邀请的好友"));
    return;
  }
  for (var k = 0; k < candidates.length; k++) {
    list.appendChild(buildInviteItem(room, candidates[k]));
  }
}

function buildInviteItem(room, f) {
  var row = el("div", "phone-list-item");
  row.appendChild(buildAvatar(f.uuid, f.name, f.avatar, "avatar-green"));
  var main = el("div", "item-main");
  main.appendChild(el("div", "item-name", f.name));
  main.appendChild(el("div", "item-sub", f.online ? "在线" : "离线"));
  row.appendChild(main);
  var inv = el("button", "mini-btn mini-btn-ok", "邀请");
  inv.addEventListener("click", function () { inviteFriendToRoom(room, f); });
  row.appendChild(inv);
  return row;
}

function inviteFriendToRoom(room, f) {
  if (!bridgeMode) {
    room.members.push({ uuid: f.uuid, name: f.name });
    room.messages.push({ sender: "other", name: "系统", time: fmtTime(), content: f.name + " 被邀请加入频道", mention: false });
    renderMembers(room);
    renderRooms();
  } else {
    pushAction({ t: "room_invite", roomId: room.id, u: f.uuid });
  }
  closeModal("inviteModal");
}

// ---- 工作日志 ----
function getCurrentTasks() {
  if (bridgeMode) { return bridgeTasks; }
  var pool = mockTaskPools[mockPlayer.dept];
  if (!pool) { return []; }
  // 按 id 卡等级限制每日任务数:1级3 / 2级4 / 3级6 / 4级8 / 5级10
  var limit = TASK_LIMIT_BY_LEVEL[mockPlayer.level] || 3;
  return pool.slice(0, limit);
}

function renderTasks() {
  var list = $("taskList");
  clearNode(list);
  var tasks = getCurrentTasks();
  if (tasks.length === 0) {
    list.appendChild(el("div", "empty-tip", "当前部门暂无任务"));
  }
  for (var i = 0; i < tasks.length; i++) {
    list.appendChild(buildTaskCard(tasks[i]));
  }
  $("logDeptHint").textContent = "当前部门:" + mockPlayer.dept + " · 每日 06:00 刷新任务清单";
  updateLogOverview();
}

function buildTaskCard(task) {
  var card = el("div", "task-card");
  var head = el("div", "task-head");
  head.appendChild(el("div", "task-name", task.name));
  var stateTag = el("span", "task-state", taskStateText(task.state));
  if (task.state === "claimable") { stateTag.className = "task-state task-state-claimable"; }
  if (task.state === "claimed") { stateTag.className = "task-state task-state-claimed"; }
  head.appendChild(stateTag);
  card.appendChild(head);
  card.appendChild(el("div", "task-desc", task.desc));
  var row = el("div", "task-progress-row");
  var track = el("div", "task-track");
  var fill = el("div", "task-fill");
  var pct = Math.min(100, Math.round(task.cur / task.goal * 100));
  setStyle(fill, { width: pct + "%" });
  track.appendChild(fill);
  row.appendChild(track);
  row.appendChild(el("div", "task-count", task.cur + " / " + task.goal));
  card.appendChild(row);
  var foot = el("div", "task-foot");
  foot.appendChild(el("span", "task-reward", "奖励 " + task.reward));
  if (task.state === "claimable") {
    var btn = el("button", "mini-btn mini-btn-ok", "领取");
    btn.addEventListener("click", function () { claimReward(task.id); });
    foot.appendChild(btn);
  } else if (task.state === "claimed") {
    foot.appendChild(el("span", "mini-btn mini-btn-no", "已领取"));
  }
  card.appendChild(foot);
  return card;
}

function taskStateText(s) {
  if (s === "claimable") { return "待领取"; }
  if (s === "claimed") { return "已完成"; }
  return "进行中";
}

function updateLogOverview() {
  var tasks = getCurrentTasks();
  var done = 0;
  for (var i = 0; i < tasks.length; i++) {
    if (tasks[i].state === "claimed") { done++; }
  }
  $("logDone").textContent = "" + done;
  $("logTotal").textContent = "" + tasks.length;
  var pct = tasks.length === 0 ? 0 : Math.round(done / tasks.length * 100);
  setStyle($("logProgressFill"), { width: pct + "%" });
}

function claimReward(taskId) {
  var tasks = getCurrentTasks();
  for (var i = 0; i < tasks.length; i++) {
    if (tasks[i].id === taskId && tasks[i].state === "claimable") {
      tasks[i].state = "claimed";
      pushAction({ t: "task_claim", id: taskId });
    }
  }
  renderTasks();
  updateHomeBadges();
}

// ---- 未读与重要消息 ----
function updateUnread() {
  var total = 0;
  for (var i = 0; i < mock.rooms.length; i++) {
    total += mock.rooms[i].unread || 0;
  }
  var badge = $("commBadge");
  if (total > 0) { badge.className = "nav-badge nav-badge-show"; }
  else { badge.className = "nav-badge"; }
}

function renderImportant() {
  var list = $("importantList");
  clearNode(list);
  // 每个群聊只取最新的一条 @ 消息
  var items = [];
  for (var i = 0; i < mock.rooms.length; i++) {
    var room = mock.rooms[i];
    for (var j = room.messages.length - 1; j >= 0; j--) {
      var msg = room.messages[j];
      if (msg.mention && msg.sender !== "mine") {
        items.push({ room: room, msg: msg });
        break;
      }
    }
  }
  var noticeCount = mock.notices ? mock.notices.length : 0;
  $("impCount").textContent = "" + (items.length + noticeCount);
  if (noticeCount > 0) {
    list.appendChild(el("div", "imp-empty", "设施公告"));
    for (var ni = 0; ni < mock.notices.length && ni < 4; ni++) {
      list.appendChild(buildNoticeImportantItem(mock.notices[ni]));
    }
  }
  if (items.length === 0) {
    if (noticeCount === 0) { list.appendChild(el("div", "imp-empty", "暂无 @ 消息 / 公告")); }
    return;
  }
  // 按时间倒序(最新在上),最多显示 6 个群聊的 @ 消息
  items.sort(function (a, b) {
    var ta = a.msg.time || "";
    var tb = b.msg.time || "";
    return ta < tb ? 1 : (ta > tb ? -1 : 0);
  });
  var max = Math.min(6, items.length);
  for (var k = 0; k < max; k++) {
    list.appendChild(buildImportantItem(items[k]));
  }
}

function buildImportantItem(item) {
  var row = el("div", "imp-item");
  row.appendChild(el("span", "imp-icon", "@"));
  row.appendChild(el("span", "imp-room", item.room.name));
  row.appendChild(el("div", "imp-content", item.msg.name + ": " + item.msg.content));
  row.appendChild(el("span", "imp-time", item.msg.time));
  if (item.room.unread > 0) {
    row.appendChild(el("span", "imp-dot"));
  }
  row.addEventListener("click", function () { enterRoom(item.room.id); });
  return row;
}

function buildNoticeImportantItem(notice) {
  var row = el("div", "imp-item");
  row.appendChild(el("span", "imp-icon", notice && notice.pinned ? "★" : "!"));
  row.appendChild(el("span", "imp-room", notice && notice.targetDepartment ? notice.targetDepartment : "全部门"));
  row.appendChild(el("div", "imp-content", notice && notice.title ? notice.title : "未命名公告"));
  row.appendChild(el("span", "imp-time", notice && notice.authorName ? notice.authorName : "公告"));
  if (notice && notice.pinned) { row.className = "imp-item notice-pinned"; }
  row.addEventListener("click", function () { showNoticeDetail(notice); });
  return row;
}

function showNoticeDetail(notice) {
  if (!notice) { return; }
  var text = (notice.title || "未命名公告") + "\n" +
    "范围：" + (notice.targetDepartment || "全部门") + "\n" +
    "发布：" + (notice.publisherName || notice.authorName || "公告") + "\n" +
    "有效至：" + (notice.expiresAtText || "永久") + "\n\n" +
    (notice.body || notice.content || "");
  showPhoneTextModal("公告详情", text);
}

function showPhoneTextModal(title, text) {
  var old = document.getElementById("phoneTextModal");
  if (old && old.parentNode) { old.parentNode.removeChild(old); }
  var wrap = document.createElement("div");
  wrap.id = "phoneTextModal";
  wrap.className = "modal-backdrop open";
  wrap.innerHTML = '<div class="modal"><div class="modal-head"><h3>' + esc(title || "详情") + '</h3><button class="modal-close" id="phoneTextModalClose">×</button></div><div class="modal-body"><textarea class="modal-input" readonly style="height:180px;box-sizing:border-box;">' + esc(text || "") + '</textarea></div></div>';
  document.body.appendChild(wrap);
  var close = document.getElementById("phoneTextModalClose");
  if (close) { close.onclick = function () { if (wrap.parentNode) { wrap.parentNode.removeChild(wrap); } }; }
}


// ---- 部门工作需求数据(占位内容,后续填充; 示例已清空, 等待服务端) ----
var mockDeptData = {};

// ---- 部门板块渲染 ----
function renderDept() {
  var body = $("deptBody");
  clearNode(body);
  $("deptCurrentBadge").textContent = mockPlayer.dept;
  var data = mockDeptData[mockPlayer.dept];
  renderDepartmentApplicationShell(body);
  renderDepartmentOperations(body);
  renderPersonalTransferForm(body);
  if (mockPlayer.dept === "科研部门") {
    renderMyResearchWork(body);
    return;
  }
  if (mockPlayer.dept === "管理部门") {
    renderManagementWorkShell(body);
    return;
  }
  if (mockPlayer.dept === "安保部门") {
    renderSecurityReadonlyProcurementHint(body);
  }
  if (mockPlayer.dept === "后勤部门" || mockPlayer.dept === "后勤编制" || Number(mockPlayer.level||0) <= 0) {
    renderLogisticsWorkHint(body);
  }
  if (!data) {
    return;
  }
  // 部门简讯
  var intro = el("div", "dept-intro");
  intro.appendChild(el("div", "dept-intro-kicker", "DEPARTMENT BRIEF"));
  intro.appendChild(el("div", "dept-intro-text", data.desc));
  body.appendChild(intro);
  // 工作需求列表
  var head = el("div", "section-head dept-section-head");
  head.appendChild(document.createTextNode("工作需求"));
  head.appendChild(el("span", "ctrl-tag", "" + data.requests.length));
  body.appendChild(head);
  for (var i = 0; i < data.requests.length; i++) {
    body.appendChild(buildDeptRequest(data.requests[i]));
  }
  // 扩展区占位
  var ext = el("div", "dept-extend");
  ext.appendChild(el("div", "dept-extend-hint", "部门扩展入口已预留，按服务端权限逐步显示。"));
  body.appendChild(ext);
}

function renderDepartmentOperations(body) {
  var lv=Number(mockPlayer.level||0), dept=mockPlayer.dept||"", pos=mockPlayer.position||"", myId=(mockPlayer.id||mockPlayer.name||"");
  var tasks=(state.departmentTasks&&state.departmentTasks.items)||[], dispatches=(state.dispatches&&state.dispatches.items)||[], alerts=(state.vitalAlerts&&state.vitalAlerts.items)||[], reinf=(state.reinforcements&&state.reinforcements.items)||[];
  var head=el("div","section-head dept-section-head"); head.appendChild(document.createTextNode("部门行动台")); head.appendChild(el("span","ctrl-tag","行动")); body.appendChild(head);
  body.appendChild(el("div","task-desc","任务/调度/生命警报/增援均只提交服务端 action；按钮隐藏只做便利，权限以服务端校验为准。"));
  var th=el("div","section-head dept-section-head"); th.appendChild(document.createTextNode("可见部门任务")); th.appendChild(el("span","ctrl-tag",tasks.length)); body.appendChild(th);
  if(!tasks.length) body.appendChild(el("div","empty-tip","暂无可见部门任务。"));
  for(var i=0;i<listLimit(tasks,6).length;i++){ var t=listLimit(tasks,6)[i]; var c=el("div","dept-request"); c.appendChild(el("div","task-name",(t.title||t.id||"部门任务")+" · "+(t.status||"open"))); c.appendChild(el("div","task-desc",(t.targetDepartment||"")+" Lv."+(t.minLevel||0)+" · "+(t.description||"")+" · "+(t.progress||0)+"/"+(t.goal||1)+" · "+(t.rewardSummary||""))); var r=el("div","report-submit-row",""); if(t.status==="open"){ var b=el("button","mini-btn","领取"); b.onclick=(function(id){return function(){enqueueAction({t:"dept_task_claim",id:id});};})(t.id); r.appendChild(b); } if(t.status==="open"||t.status==="claimed"){ var s=el("button","mini-btn","开始"); s.onclick=(function(id){return function(){enqueueAction({t:"dept_task_progress",id:id,status:"start"});};})(t.id); r.appendChild(s); } if(t.status==="in_progress"||t.status==="claimed"){ var inp=el("input","modal-input",""); inp.id="deptTaskProgress_"+i; inp.placeholder="进度数值"; r.appendChild(inp); var sub=el("button","mini-btn","提交"); sub.onclick=(function(id,idx){return function(){enqueueAction({t:"dept_task_progress",id:id,progress:Number($("deptTaskProgress_"+idx).value||1),note:"phone submit"});};})(t.id,i); r.appendChild(sub); } c.appendChild(r); body.appendChild(c); }
  appendMoreHint(body,tasks,6,"部门任务");
  var dh=el("div","section-head dept-section-head"); dh.appendChild(document.createTextNode("我的调度")); dh.appendChild(el("span","ctrl-tag",dispatches.length)); body.appendChild(dh);
  if(!dispatches.length) body.appendChild(el("div","empty-tip","暂无本人/可见调度。"));
  for(var d=0;d<dispatches.length&&d<5;d++){ var x=dispatches[d]; var dc=el("div","dept-request"); dc.appendChild(el("div","task-name",(x.id||"调度")+" · "+(x.status||"pending"))); dc.appendChild(el("div","task-desc",(x.sourceDepartment||"")+" → "+(x.targetDepartment||"")+" "+(x.targetPosition||"")+" Lv."+(x.minLevel||0)+" · "+(x.location||"")+" · "+(x.message||""))); if(x.status==="pending"||x.status==="acknowledged"){ var ack=el("button","mini-btn",x.status==="pending"?"ACK":"完成"); ack.onclick=(function(id,done){return function(){enqueueAction({t:done?"dispatch_update_status":"dispatch_approve",id:id,status:done?"completed":"acknowledged"});};})(x.id,x.status==="acknowledged"); dc.appendChild(ack); } body.appendChild(dc); }
  appendMoreHint(body,dispatches,5,"调度");
  if(dept==="安保部门"&&lv>=1){ var ah=el("div","section-head dept-section-head"); ah.appendChild(document.createTextNode("生命体征警报")); ah.appendChild(el("span","ctrl-tag",alerts.length)); body.appendChild(ah); if(!alerts.length) body.appendChild(el("div","empty-tip","暂无可见生命警报。")); for(var a=0;a<alerts.length&&a<5;a++){ var al=alerts[a]; var ac=el("div","dept-request"); ac.appendChild(el("div","task-name",(al.targetName||al.id||"警报")+" · "+(al.status||"open"))); ac.appendChild(el("div","task-desc",(al.department||"")+"/"+(al.position||"")+" Lv."+(al.level||0)+" · "+(al.dimension||"")+" ["+(al.x||0)+","+(al.y||0)+","+(al.z||0)+"] · "+(al.damageSummary||""))); if(al.status==="open"){ var ab=el("button","mini-btn","ACK"); ab.onclick=(function(id){return function(){enqueueAction({t:"vital_alert_ack",id:id});};})(al.id); ac.appendChild(ab); } if(lv>=3&&(al.status==="open"||al.status==="acknowledged")){ var db=el("button","mini-btn","派遣"); db.onclick=(function(id){return function(){enqueueAction({t:"vital_alert_dispatch",id:id,note:"phone dispatch"});};})(al.id); ac.appendChild(db); } if(lv>=3&&al.status==="dispatched"){ var rb=el("button","mini-btn","解除"); rb.onclick=(function(id){return function(){enqueueAction({t:"vital_alert_resolve",id:id,note:"phone resolve"});};})(al.id); ac.appendChild(rb); } body.appendChild(ac); } appendMoreHint(body,alerts,5,"生命体征警报");}
  var rh=el("div","section-head dept-section-head"); rh.appendChild(document.createTextNode("可见增援")); rh.appendChild(el("span","ctrl-tag",reinf.length)); body.appendChild(rh); if(!reinf.length) body.appendChild(el("div","empty-tip","暂无可见增援。")); for(var rr=0;rr<reinf.length&&rr<5;rr++){ var q=reinf[rr]; var qc=el("div","dept-request"); qc.appendChild(el("div","task-name",(q.id||"增援")+" · "+(q.status||"open"))); qc.appendChild(el("div","task-desc",(q.requestDepartment||"")+" → "+(q.targetDepartment||"")+" Lv."+(q.minLevel||0)+" ×"+(q.neededCount||1)+" · "+(q.location||"")+" · "+(q.description||""))); if(q.status==="open"){ var cb=el("button","mini-btn","接单"); cb.onclick=(function(id){return function(){enqueueAction({t:"reinforcement_decide",id:id});};})(q.id); qc.appendChild(cb); } if(q.status==="claimed"){ var ar=el("button","mini-btn","抵达"); ar.onclick=(function(id){return function(){enqueueAction({t:"reinforcement_close",id:id,status:"arrived"});};})(q.id); qc.appendChild(ar); } if(q.status==="arrived"){ var rs=el("button","mini-btn","解决"); rs.onclick=(function(id){return function(){enqueueAction({t:"reinforcement_close",id:id,status:"resolved",note:"phone resolve"});};})(q.id); qc.appendChild(rs); } body.appendChild(qc); }
  appendMoreHint(body,reinf,5,"增援");
}
function renderPersonalTransferForm(body) {
  var head=el("div","section-head dept-section-head"); head.appendChild(document.createTextNode("个人LC转账")); head.appendChild(el("span","ctrl-tag","余额 "+fmtBalance(mockPlayer.balance))); body.appendChild(head);
  var card=el("div","dept-request"); card.appendChild(el("div","task-desc","从当前ID卡绑定的个人LC账户转账到另一玩家账户。发送人由服务端固定为当前玩家，不使用设施资金。"));
  var target=el("input","modal-input",""); target.id="personalTransferReceiver"; target.placeholder="收款玩家名"; card.appendChild(target);
  var amount=el("input","modal-input",""); amount.id="personalTransferAmount"; amount.setAttribute("type","number"); amount.setAttribute("min","1"); amount.setAttribute("max","1000000"); amount.placeholder="金额"; card.appendChild(amount);
  var note=el("input","modal-input",""); note.id="personalTransferNote"; note.placeholder="备注（可选）"; card.appendChild(note);
  var btn=el("button","head-btn","确认转账"); btn.onclick=function(){ var receiver=$("personalTransferReceiver").value||""; var value=Number($("personalTransferAmount").value||0); if(!receiver||value<=0){ showPhoneTextModal("转账提示","请填写收款玩家名和有效金额。" ); return; } enqueueAction({t:"personal_transfer_request",receiverName:receiver,amount:Math.floor(value),note:$("personalTransferNote").value||""}); showPhoneTextModal("转账请求已提交","服务端将校验账户、余额和收款人。最终结果会发送到聊天栏。" ); }; card.appendChild(btn); body.appendChild(card);
}
function renderSecurityReadonlyProcurementHint(body){ var c=el("div","dept-request"); c.appendChild(el("div","task-name","武装采购")); c.appendChild(el("div","task-desc","安保武装采购须先走真实采购/业务申请，个人端只发起/查看状态；不伪造库存或直接发物品，收货为人工确认审计。")); body.appendChild(c); }
function renderLogisticsWorkHint(body){
  var procs=(state.procurements&&state.procurements.items)||[], pays=(state.facilityPayments&&state.facilityPayments.items)||[];
  body.appendChild(el("div","empty-tip","采购必须关联已批准申请；后勤3低值执行，后勤4高值/危险/紧急执行，设施主管5终批大额/高风险。"));
  var h=el("div","dept-request"); h.appendChild(el("div","task-name","采购/资金待办 · "+procs.length+" / "+pays.length)); h.appendChild(el("div","task-desc","状态机：request→approved→ordered→received→completed；reject/cancel/pay 均由服务端审计。设施资金与个人LC转账分离。")); body.appendChild(h);
  for(var i=0;i<procs.length&&i<5;i++){var p=procs[i],c=el("div","dept-request");c.appendChild(el("div","task-name",(p.id||"采购")+" · "+(p.status||"request")+" · "+(p.amount||0)));c.appendChild(el("div","task-desc",(p.category||"")+" / "+(p.riskLevel||"low")+" / source="+(p.sourceApplicationId||"")));var r=el("div","report-submit-row",""); if(p.status==='request'){var ap=el("button","mini-btn","批准");ap.onclick=(function(id){return function(){enqueueAction({t:"procurement_approve",id:id});};})(p.id);r.appendChild(ap);} if(p.status==='approved'){var od=el("button","mini-btn","下单");od.onclick=(function(id){return function(){enqueueAction({t:"procurement_order",id:id});};})(p.id);r.appendChild(od);} if(p.status==='ordered'){var rc=el("button","mini-btn","人工收货");rc.onclick=(function(id){return function(){enqueueAction({t:"procurement_receive",id:id,note:"phone manual receive"});};})(p.id);r.appendChild(rc);} if(p.status==='received'){var cm=el("button","mini-btn","完成");cm.onclick=(function(id){return function(){enqueueAction({t:"procurement_complete",id:id});};})(p.id);r.appendChild(cm);} c.appendChild(r);body.appendChild(c);} appendMoreHint(body,procs,5,"采购"); appendMoreHint(body,pays,5,"付款");
}
function myResearchStageText(v) { if (v <= 0) return "未知"; if (v < 25) return "基础"; if (v < 60) return "深入"; if (v < 100) return "高级"; return "完成"; }
function renderManagementWorkShell(body) {
  var m = state.managementOffice || mock.managementOffice || {records:[]};
  var p = state.player || mockPlayer || {}; var pos=p.position||"";
  var head = el("div", "section-head dept-section-head");
  var titleText = pos.indexOf("道德助理")>=0 ? "管理调度 / 只读" : (pos.indexOf("伦理检察官")>=0 ? "伦理监督只读" : (pos.indexOf("安全代理")>=0 ? "主管指令只读" : (pos.indexOf("设施主管")>=0 ? "设施运营方针" : "书记官文书台")));
  head.appendChild(document.createTextNode(titleText));
  head.appendChild(el("span", "ctrl-tag", "文书"));
  body.appendChild(head);
  var note = "书记官3：文书归档、普通公告、普通协调会议；设施主管5/OP：运营方针、一级广播和高优先级会议。道德助理/伦理检察官/安全代理本阶段只读占位。会议战斗状态拦截未接入。";
  body.appendChild(el("div", "task-desc", note));
  var met=m.metrics||{}; body.appendChild(el("div", "empty-tip", "行政仪表盘(只读)：归档 "+(met.archiveCount||0)+" / 会议 "+(met.meetingCount||0)+" / 方针 "+(met.policyCount||0)+" / 公告 "+(met.noticeCount||0)));
  var canEditMgmt = m.canCreateArchive || m.canCreateNormalMeeting || m.canPublishPolicy;
  if (!canEditMgmt) { body.appendChild(el("div", "empty-tip", "当前管理岗位为监督/只读身份：不显示书记官编辑按钮。")); }
  if (!canEditMgmt) { var ro = m.records || []; if (!ro.length) body.appendChild(el("div", "empty-tip", "暂无行政记录。")); for (var ri=0;ri<ro.length && ri<8;ri++) body.appendChild(el("div", "empty-tip", (ro[ri].type||"record")+" · "+(ro[ri].title||ro[ri].id||"")+" · "+(ro[ri].status||""))); appendMoreHint(body, ro, 8, "行政记录"); return; }
  var card = el("div", "dept-request");
  card.appendChild(el("div", "task-name", "文书/会议表单"));
  var inp = el("input", "modal-input", ""); inp.id="mgmtPhoneTitle"; inp.placeholder="标题"; card.appendChild(inp);
  var bodyIn = document.createElement("textarea"); bodyIn.className="modal-input"; bodyIn.id="mgmtPhoneBody"; bodyIn.placeholder="正文/说明"; card.appendChild(bodyIn);
  var loc = el("input", "modal-input", ""); loc.id="mgmtPhoneLocation"; loc.placeholder="会议地点"; card.appendChild(loc);
  var part = el("input", "modal-input", ""); part.id="mgmtPhoneParticipants"; part.placeholder="参与部门或玩家"; card.appendChild(part);
  var row = el("div", "report-submit-row", "");
  var archive = el("button", "mini-btn", "创建归档"); archive.disabled=!m.canCreateArchive; archive.onclick=function(){ enqueueAction({t:"mgmt_archive_create", title:$("mgmtPhoneTitle").value||"", body:$("mgmtPhoneBody").value||"", sourceType:"phone", sourceId:"management"}); };
  var meeting = el("button", "mini-btn", "普通会议"); meeting.disabled=!m.canCreateNormalMeeting; meeting.onclick=function(){ enqueueAction({t:"mgmt_meeting_create", title:$("mgmtPhoneTitle").value||"", body:$("mgmtPhoneBody").value||"", location:$("mgmtPhoneLocation").value||"", participants:$("mgmtPhoneParticipants").value||""}); };
  var policy = el("button", "mini-btn", "发布方针"); policy.disabled=!m.canPublishPolicy; policy.onclick=function(){ enqueueAction({t:"mgmt_policy_publish", title:$("mgmtPhoneTitle").value||"", body:$("mgmtPhoneBody").value||""}); };
  row.appendChild(archive); row.appendChild(meeting); row.appendChild(policy); card.appendChild(row); body.appendChild(card);
  var rec = m.records || [];
  if (!rec.length) body.appendChild(el("div", "empty-tip", "暂无行政记录。"));
  for (var i=0;i<rec.length && i<8;i++) {
    var txt=(rec[i].type||"record")+" · "+(rec[i].title||rec[i].id||"")+" · "+(rec[i].status||"");
    var item=el("div", "empty-tip", txt); body.appendChild(item);
  }
}
function renderDepartmentApplicationShell(body) {
  var apps = (state.departmentApplications && state.departmentApplications.items) || [];
  var queueAll = (state.pendingCardQueue && state.pendingCardQueue.items) || [];
  var myName = (mockPlayer && (mockPlayer.name || mockPlayer.id)) || "";
  var queue = [];
  for (var qi=0; qi<queueAll.length; qi++) if (!queueAll[qi].targetName || queueAll[qi].targetName === myName) queue.push(queueAll[qi]);
  var isTemp = Number(mockPlayer.level || 0) === 0 && (mockPlayer.dept === "后勤部门" || mockPlayer.dept === "后勤编制") && (mockPlayer.position === "临时人员" || mockPlayer.position === "受试人员");
  var head = el("div", "section-head dept-section-head");
  head.appendChild(document.createTextNode("我的申请 / 待换卡"));
  head.appendChild(el("span", "ctrl-tag", apps.length + " / " + queue.length));
  body.appendChild(head);
  if (isTemp) {
    var form = el("div", "dept-request");
    form.appendChild(el("div", "task-name", "加入部门申请"));
    form.appendChild(el("div", "task-desc", "仅0级临时/受试人员可提交；可选安保/科研/后勤，不能申请管理。审批通过只产生待换卡记录，不改ID卡。"));
    var select = document.createElement("select"); select.className = "modal-input"; select.id = "deptJoinTarget";
    var opts = ["安保部门", "科研部门", "后勤部门"];
    for (var oi=0; oi<opts.length; oi++) { var op=document.createElement("option"); op.value=opts[oi]; op.textContent=opts[oi]; select.appendChild(op); }
    var reason = document.createElement("textarea"); reason.className = "modal-input"; reason.id = "deptJoinReason"; reason.placeholder = "申请理由";
    form.appendChild(select); form.appendChild(reason);
    var row = el("div", "report-submit-row", "");
    var btn = el("button", "head-btn", "提交入职申请");
    btn.onclick = function(){ enqueueAction({ t:"dept_app_submit", type:"JOIN_DEPARTMENT", targetDepartment:$("deptJoinTarget").value, reason:$("deptJoinReason").value || "" }); };
    row.appendChild(btn); form.appendChild(row); body.appendChild(form);
  } else {
    body.appendChild(el("div", "empty-tip", "当前身份仅显示自己的申请历史；入职提交表只对后勤0级临时/受试人员开放。"));
  }
  if (!apps.length) body.appendChild(el("div", "empty-tip", "暂无申请记录"));
  for (var i=0;i<apps.length && i<6;i++) {
    var a=apps[i];
    var line = (a.id||"申请") + " · " + (a.status||"submitted") + " · 目标 " + (a.targetDepartment||"-") + " / " + (a.targetPosition||"") + " Lv." + (a.targetLevel||1);
    var item = el("div", "empty-tip", line);
    if (isTemp && (a.status === "dept_review" || a.status === "logistics_confirm" || a.status === "submitted")) {
      var cancel = el("button", "mini-btn", "取消");
      cancel.onclick = (function(id){ return function(){ enqueueAction({ t:"dept_app_cancel", id:id }); }; })(a.id);
      item.appendChild(cancel);
    }
    body.appendChild(item);
  }
  appendMoreHint(body, apps, 6, "申请记录");
  for (var q=0;q<queue.length && q<4;q++) body.appendChild(el("div", "empty-tip", "待换卡 " + (queue[q].id||"") + " · " + (queue[q].status||"") + " · " + (queue[q].targetDepartment||"") + "/" + (queue[q].targetPosition||"") + "（人工确认，不写卡）"));
  appendMoreHint(body, queue, 4, "待换卡队列");
}
function renderMyResearchWork(body) {
  var data = mock.myResearch || {};
  var sum = data.researchSummary || {};
  var anomalies = data.researchAnomalies || [];
  var reports = data.researchReports || [];
  var queue = data.maintenanceQueue || [];
  var intro = el("div", "dept-intro");
  intro.appendChild(el("div", "dept-intro-kicker", "MY RESEARCH WORK"));
  intro.appendChild(el("div", "dept-intro-text", "科研部门所有等级均可提交实验报告，并查看自己提交过的全部报告及审核状态；不显示他人报告正文。"));
  body.appendChild(intro);
  var head = el("div", "section-head dept-section-head");
  head.appendChild(document.createTextNode("科研摘要"));
  head.appendChild(el("span", "ctrl-tag", "异常物 " + (sum.anomalyCount || anomalies.length)));
  body.appendChild(head);
  var card = el("div", "dept-request");
  card.appendChild(el("div", "task-desc", "待审报告 " + (sum.pendingReports || 0) + " / 可见报告 " + reports.length + " / 维护提醒 " + queue.length));
  body.appendChild(card);
  var reportHead = el("div", "section-head dept-section-head");
  reportHead.appendChild(document.createTextNode("填写实验报告"));
  reportHead.appendChild(el("span", "ctrl-tag", "科研部门专用"));
  body.appendChild(reportHead);
  if (sum.canSubmitReports) {
    var submit = el("div", "report-submit-card research-inline-report");
    submit.appendChild(el("div", "task-desc", "科研部门1—4级均可填写。报告仅绑定一个异常物；审核只在设施终端由设施主管5/OP处理。"));
    var anomalyLabel = el("label", "modal-label", "异常物实例");
    anomalyLabel.setAttribute("for", "reportAnomalySelect");
    submit.appendChild(anomalyLabel);
    var anomalySelect = el("select", "modal-input report-input", "");
    anomalySelect.id = "reportAnomalySelect";
    submit.appendChild(anomalySelect);
    var titleLabel = el("label", "modal-label", "标题");
    titleLabel.setAttribute("for", "reportTitleInput");
    submit.appendChild(titleLabel);
    var titleInput = el("input", "modal-input report-input", "");
    titleInput.id = "reportTitleInput";
    titleInput.setAttribute("type", "text");
    titleInput.setAttribute("maxlength", "120");
    titleInput.setAttribute("placeholder", "输入报告标题");
    submit.appendChild(titleInput);
    var contentLabel = el("label", "modal-label", "正文");
    contentLabel.setAttribute("for", "reportContentInput");
    submit.appendChild(contentLabel);
    var contentInput = el("textarea", "modal-input report-textarea", "");
    contentInput.id = "reportContentInput";
    contentInput.setAttribute("maxlength", "12000");
    contentInput.setAttribute("placeholder", "记录观察、实验过程与结论");
    submit.appendChild(contentInput);
    var submitRow = el("div", "report-submit-row", "");
    var submitHint = el("span", "page-hint", "请选择异常物并填写报告。");
    submitHint.id = "reportSubmitHint";
    submitRow.appendChild(submitHint);
    var submitButton = el("button", "head-btn", "提交报告");
    submitButton.id = "btnSubmitReport";
    submitButton.onclick = submitReportFromForm;
    submitRow.appendChild(submitButton);
    submit.appendChild(submitRow);
    body.appendChild(submit);
    renderReportForm();
  }
  for (var i = 0; i < listLimit(reports, 8).length; i++) {
    var r = reports[i];
    var rc = el("div", "dept-request");
    rc.appendChild(el("div", "task-name", r.title || r.reportId || "未命名报告"));
    rc.appendChild(el("div", "task-desc", "状态 " + (r.status || "pending") + " · 异常 " + (r.anomalyInstanceId || "-") + " · 审核 " + (r.reviewerName || "-")));
    body.appendChild(rc);
  }
  appendMoreHint(body, reports, 8, "实验报告");
  var docHead = el("div", "section-head dept-section-head");
  docHead.appendChild(document.createTextNode("异常文档 / 学术短讯"));
  docHead.appendChild(el("span", "ctrl-tag", "容器"));
  body.appendChild(docHead);
  var docs = (state.researchDocuments && state.researchDocuments.items) || [];
  var lv = Number(mockPlayer.level || 0);
  if (sum.canSubmitReports) renderResearchApplicationForms(body, anomalies);
  if (lv >= 4) renderAnomalyDocEditor(body, anomalies);
  if (lv >= 3) renderBulletinEditor(body, docs, reports);
  if (docs.length === 0) { body.appendChild(el("div", "empty-tip", "暂无异常文档或学术短讯。")); }
  for (var di = 0; di < docs.length && di < 10; di++) {
    var d = docs[di];
    var dc = el("div", "dept-request research-work-card");
    dc.appendChild(el("div", "task-name", d.title || d.id || "科研文档"));
    dc.appendChild(el("div", "task-desc", (d.type || "") + " · " + (d.status || "draft") + " · v" + (d.version || 1) + " · 异常 " + (d.anomalyInstanceId || "-") + " · report " + (d.reportId || "-") + " · doc " + (d.documentId || "-")));
    if (d.body) dc.appendChild(el("div", "task-desc", d.body));
    if (lv >= 4 && d.type === "anomaly_document" && d.status === "submitted") {
      var rb = el("button", "mini-btn", "修订");
      rb.onclick = (function(doc){ return function(){ fillResearchDocForm(doc, true); }; })(d);
      dc.appendChild(rb);
    }
    body.appendChild(dc);
  }
  appendMoreHint(body, docs, 10, "科研文档/短讯");
  var ah = el("div", "section-head dept-section-head");
  ah.appendChild(document.createTextNode("可见异常物摘要"));
  ah.appendChild(el("span", "ctrl-tag", "" + anomalies.length));
  body.appendChild(ah);
  for (var j = 0; j < anomalies.length && j < 6; j++) {
    var a = anomalies[j];
    var ac = el("div", "dept-request research-work-card");
    ac.appendChild(el("div", "task-name", a.displayName || a.codexId || a.instanceId || "异常物"));
    ac.appendChild(el("div", "task-desc", "阶段 " + (a.researchStage || myResearchStageText(a.researchProgress || 0)) + " · 稳定 " + ((typeof a.stabilityPercent === "number") ? a.stabilityPercent : "-") + "%"));
    body.appendChild(ac);
  }
  var mh = el("div", "section-head dept-section-head");
  mh.appendChild(document.createTextNode("待维护摘要"));
  mh.appendChild(el("span", "ctrl-tag", "" + queue.length));
  body.appendChild(mh);
  if (queue.length === 0) { body.appendChild(el("div", "empty-tip", "暂无可见维护提醒。")); }
  for (var q = 0; q < queue.length && q < 4; q++) {
    var m = queue[q];
    var qc = el("div", "dept-request research-maint-card");
    qc.appendChild(el("div", "task-name", m.displayName || m.instanceId || "异常物"));
    qc.appendChild(el("div", "task-desc", "维护 " + (m.maintenanceStatus || "unknown") + " · 最近 " + (m.lastMaintenanceText || "未验证")));
    body.appendChild(qc);
  }
}

function researchOptions(select, anomalies) {
  clearNode(select);
  for (var i=0;i<(anomalies||[]).length;i++) { var a=anomalies[i]; var op=document.createElement("option"); op.value=a.instanceId||""; op.textContent=(a.displayName||a.codexId||"异常物") + " / " + (a.instanceId||""); select.appendChild(op); }
}
function renderResearchApplicationForms(body, anomalies) {
  var h=el("div","section-head dept-section-head"); h.appendChild(document.createTextNode("实验申请 / 研究物资申请")); h.appendChild(el("span","ctrl-tag","科研1-4")); body.appendChild(h);
  var f=el("div","dept-request research-work-card"); f.appendChild(el("div","task-name","实验申请表"));
  var s=el("select","modal-input",""); s.id="researchExpAnomaly"; researchOptions(s, anomalies); f.appendChild(s);
  var risk=el("select","modal-input",""); risk.id="researchExpRisk"; var lo=document.createElement("option"); lo.value="low"; lo.textContent="LOW 低危"; risk.appendChild(lo); var hi=document.createElement("option"); hi.value="high"; hi.textContent="HIGH 高危"; risk.appendChild(hi); f.appendChild(risk);
  var td=el("select","modal-input",""); td.id="researchExpTargetDept"; ["","安保部门","后勤部门","科研部门"].forEach(function(v){var op=document.createElement("option");op.value=v;op.textContent=v||"无人员调配";td.appendChild(op);}); f.appendChild(td);
  var items=el("textarea","modal-input report-textarea",""); items.id="researchExpItems"; items.placeholder="物资需求(JSON/文本，非空追加后勤主管4)"; f.appendChild(items);
  var desc=el("textarea","modal-input report-textarea",""); desc.id="researchExpDesc"; desc.placeholder="实验目的、步骤、安全措施"; f.appendChild(desc);
  var b=el("button","head-btn","提交实验申请"); b.onclick=function(){ enqueueAction({t:"research_application_submit",type:"RESEARCH_EXPERIMENT",title:"科研实验申请",anomalyInstanceId:$("researchExpAnomaly").value,riskLevel:$("researchExpRisk").value,targetDepartment:$("researchExpTargetDept").value,requestedItems:$("researchExpItems").value?[$("researchExpItems").value]:[],description:$("researchExpDesc").value||""});}; f.appendChild(b); body.appendChild(f);
  var p=el("div","dept-request research-work-card"); p.appendChild(el("div","task-name","研究物资申请")); var pd=el("textarea","modal-input report-textarea",""); pd.id="researchPurchaseDesc"; pd.placeholder="物资、用途、预算/风险说明"; p.appendChild(pd); var hv=el("select","modal-input",""); hv.id="researchPurchaseRisk"; ["普通","高价值/高风险"].forEach(function(v,i){var op=document.createElement("option");op.value=i?"high":"low";op.textContent=v;hv.appendChild(op);}); p.appendChild(hv); var pb=el("button","head-btn","提交物资申请"); pb.onclick=function(){ enqueueAction({t:"research_application_submit",type:"RESEARCH_PURCHASE",title:"研究物资申请",description:$("researchPurchaseDesc").value||"",riskLevel:$("researchPurchaseRisk").value,requestedItems:[$("researchPurchaseDesc").value||""]});}; p.appendChild(pb); body.appendChild(p);
}
function renderAnomalyDocEditor(body, anomalies) {
  var f=el("div","dept-request research-work-card"); f.appendChild(el("div","task-name","异常文档草稿 / 提交 / 修订")); var id=el("input","modal-input",""); id.id="researchDocId"; id.placeholder="修订时自动填入文档ID，新建留空"; f.appendChild(id); var s=el("select","modal-input",""); s.id="researchDocAnomaly"; researchOptions(s, anomalies); f.appendChild(s); var title=el("input","modal-input",""); title.id="researchDocTitle"; title.placeholder="标题"; f.appendChild(title); var bodyIn=el("textarea","modal-input report-textarea",""); bodyIn.id="researchDocBody"; bodyIn.placeholder="文档正文"; f.appendChild(bodyIn); var sum=el("input","modal-input",""); sum.id="researchDocSummary"; sum.placeholder="修订摘要"; f.appendChild(sum); var save=el("button","mini-btn","保存草稿"); save.onclick=function(){ enqueueAction({t:"research_doc_save",id:$("researchDocId").value,title:$("researchDocTitle").value,body:$("researchDocBody").value,anomalyInstanceId:$("researchDocAnomaly").value});}; var sub=el("button","mini-btn","提交文档"); sub.onclick=function(){ enqueueAction({t:"research_doc_submit",id:$("researchDocId").value,title:$("researchDocTitle").value,body:$("researchDocBody").value,anomalyInstanceId:$("researchDocAnomaly").value});}; var rev=el("button","mini-btn","提交修订"); rev.onclick=function(){ enqueueAction({t:"research_doc_revise",id:$("researchDocId").value,title:$("researchDocTitle").value,body:$("researchDocBody").value,summary:$("researchDocSummary").value});}; f.appendChild(save); f.appendChild(sub); f.appendChild(rev); body.appendChild(f);
}
function fillResearchDocForm(d) { if ($("researchDocId")) { $("researchDocId").value=d.id||""; $("researchDocTitle").value=d.title||""; $("researchDocBody").value=d.body||""; if ($("researchDocAnomaly")) $("researchDocAnomaly").value=d.anomalyInstanceId||""; } }
function renderBulletinEditor(body, docs, reports) { var f=el("div","dept-request research-work-card"); f.appendChild(el("div","task-name","学术短讯提交")); var title=el("input","modal-input",""); title.id="bulletinTitle"; title.placeholder="短讯标题"; f.appendChild(title); var tgt=el("select","modal-input",""); tgt.id="bulletinTarget"; (reports||[]).forEach(function(r){var op=document.createElement("option");op.value="report:"+(r.reportId||"");op.textContent="报告 "+(r.title||r.reportId);tgt.appendChild(op);}); (docs||[]).forEach(function(d){if(d.type==="anomaly_document"){var op=document.createElement("option");op.value="document:"+(d.id||"");op.textContent="文档 "+(d.title||d.id);tgt.appendChild(op);}}); f.appendChild(tgt); var text=el("textarea","modal-input report-textarea",""); text.id="bulletinBody"; text.placeholder="短讯正文；提交后不可编辑/删除，只能发新短讯更正"; f.appendChild(text); var b=el("button","head-btn","提交短讯"); b.onclick=function(){ var v=$("bulletinTarget").value||":"; var p=v.split(":"); enqueueAction({t:"academic_bulletin_submit",title:$("bulletinTitle").value,body:$("bulletinBody").value,reportId:p[0]==="report"?p[1]:"",documentId:p[0]==="document"?p[1]:""});}; f.appendChild(b); body.appendChild(f); }

function buildDeptRequest(req) {
  var card = el("div", "dept-request");
  var top = el("div", "task-head");
  top.appendChild(el("div", "task-name", req.title));
  var tag = el("span", "task-state", req.tag);
  if (req.tag === "待处理") { tag.className = "task-state task-state-claimable"; }
  top.appendChild(tag);
  card.appendChild(top);
  card.appendChild(el("div", "task-desc", req.desc));
  return card;
}

// ---- 娱乐模块 ----
var mockGames = [
  { id: "minesweeper", name: "扫雷", desc: "经典扫雷 · 右键插旗", glyph: "⚑" },
  { id: "memory", name: "记忆翻牌", desc: "配对卡片 · 考验记忆力", glyph: "▣" },
  { id: "g2048", name: "2048", desc: "合并数字方块 · 挑战极限", glyph: "▦" },
  { id: "snake", name: "贪吃蛇", desc: "经典街机 · 别咬到自己", glyph: "∿" }
];

// ---- 难度系统 ----
var difficultyNames = { easy: "简单", normal: "普通", hard: "困难" };
var currentDifficulty = "normal";

// 各游戏难度参数
// 扫雷行列: 高度受游戏内容区(~340px)限制, 行数过多会被裁剪。
// 2026-08-21: 普通/困难删减行数(12→10, 11→9), 雷数按比例调整:
//   普通 144格/30雷(20.8%) → 120格/25雷; 困难 264格/50雷(18.9%) → 216格/41雷。
var MS_DIFF = { easy: [8, 8, 10], normal: [12, 10, 25], hard: [24, 9, 40] };
var MM_DIFF = { easy: [4, 4], normal: [6, 6], hard: [8, 6] };
var G_DIFF = { easy: 6, normal: 5, hard: 4 };
var SN_DIFF = { easy: 220, normal: 160, hard: 110 };

// 成绩列名与排序方向(asc=true 越小越好)
var gameMeta = {
  minesweeper: { scoreLabel: "用时", asc: true },
  memory: { scoreLabel: "步数", asc: true },
  g2048: { scoreLabel: "分数", asc: false },
  snake: { scoreLabel: "分数", asc: false }
};

// ---- 排行榜(正式接入时由服务端下发, 本地不再内置模拟榜单) ----
var mockLeaderboards = {};

function getLeaderboard() {
  var game = state.currentGameId;
  var lb = mockLeaderboards[game];
  if (!lb) { return []; }
  return lb[currentDifficulty] || [];
}

// 记录成绩:去重自己的旧成绩,插入排序,截断 top 10
function recordScore(score) {
  var game = state.currentGameId;
  var lb = mockLeaderboards[game];
  if (!lb) { return; }
  var list = lb[currentDifficulty];
  if (!list) { return; }
  for (var i = list.length - 1; i >= 0; i--) {
    if (list[i].isMe) { list.splice(i, 1); }
  }
  list.push({ name: mockPlayer.id, score: score, isMe: true });
  var asc = gameMeta[game] ? gameMeta[game].asc : false;
  list.sort(function (a, b) { return asc ? a.score - b.score : b.score - a.score; });
  while (list.length > 10) { list.pop(); }
  // 桥接模式:成绩提交服务端
  pushAction({ t: "score_submit", game: state.currentGameId, diff: currentDifficulty, score: score });
  // 榜单弹窗打开时实时刷新
  var modal = $("leaderboardModal");
  if (modal && modal.className.indexOf("open") >= 0) { renderLeaderboard(); }
}

function switchDifficulty(diff) {
  if (!difficultyNames[diff]) { return; }
  currentDifficulty = diff;
  updateDiffTabs();
  if (state.page === "game" && state.currentGameId) {
    renderGame(state.currentGameId);
  }
}

function updateDiffTabs() {
  var tabs = document.getElementsByClassName("diff-tab");
  for (var i = 0; i < tabs.length; i++) {
    if (tabs[i].getAttribute("data-diff") === currentDifficulty) {
      tabs[i].className = "diff-tab diff-tab-active";
    } else {
      tabs[i].className = "diff-tab";
    }
  }
}

// ---- 排行榜弹窗 ----
function openLeaderboard() {
  if (bridgeMode) { pushAction({ t: "lb_request" }); }
  renderLeaderboard();
  openModal("leaderboardModal");
}

function renderLeaderboard() {
  var game = state.currentGameId;
  var meta = gameMeta[game];
  var title = "";
  for (var i = 0; i < mockGames.length; i++) {
    if (mockGames[i].id === game) { title = mockGames[i].name; }
  }
  $("lbTitle").textContent = " · " + title + " · " + difficultyNames[currentDifficulty];
  var list = $("lbList");
  clearNode(list);
  var data = getLeaderboard();
  if (data.length === 0) {
    list.appendChild(el("div", "lb-empty", "暂无数据"));
    return;
  }
  for (var j = 0; j < data.length; j++) {
    list.appendChild(buildLbRow(j + 1, data[j], meta.scoreLabel));
  }
}

function buildLbRow(rank, entry, scoreLabel) {
  var row = el("div", entry.isMe ? "lb-row lb-me" : "lb-row");
  var rankEl = el("div", "lb-rank", "" + rank);
  if (rank <= 3) { rankEl.className = "lb-rank lb-rank-top"; }
  row.appendChild(rankEl);
  var name = el("div", "lb-name", entry.name);
  if (entry.isMe) { name.textContent = entry.name + " (我)"; }
  row.appendChild(name);
  row.appendChild(el("div", "lb-score", entry.score + " " + scoreLabel));
  return row;
}

// ---- 游戏入口 ----
function renderGames() {
  var list = $("gameList");
  // 幂等(2026-08-24 AUI 崩溃修复): 只建一次, 不 clearNode —— 游戏列表是 grid,
  // 全量 removeChild+重建与布局测量交错同样有摘除 grid 项崩溃的风险。
  if (list.children.length === 0) {
    for (var i = 0; i < mockGames.length; i++) {
      list.appendChild(buildGameCard(mockGames[i]));
    }
  }
}

function buildGameCard(game) {
  var coming = game.id.indexOf("coming") === 0;
  var card = el("div", coming ? "game-card game-card-disabled" : "game-card");
  card.appendChild(el("div", "game-glyph", game.glyph));
  var info = el("div", "game-info");
  info.appendChild(el("div", "game-name", game.name));
  info.appendChild(el("div", "game-desc", game.desc));
  card.appendChild(info);
  if (!coming) {
    card.addEventListener("click", function () { openGame(game.id); });
  }
  return card;
}

function openGame(gameId) {
  var game = null;
  for (var i = 0; i < mockGames.length; i++) {
    if (mockGames[i].id === gameId) { game = mockGames[i]; }
  }
  if (!game) { return; }
  state.currentGameId = gameId;
  $("gameTitle").textContent = game.name;
  updateDiffTabs();
  renderGame(gameId);
  gotoPage("game");
}

function renderGame(gameId) {
  var box = $("gameBody");
  clearNode(box);
  if (gameId === "minesweeper") { buildMinesweeper(box); }
  else if (gameId === "memory") { buildMemory(box); }
  else if (gameId === "g2048") { build2048(box); }
  else if (gameId === "snake") { buildSnake(box); }
  else { box.appendChild(el("div", "empty-tip", "该游戏开发中")); }
}

// 游戏网格:尺寸与列数由 CSS 类控制(ms-8/ms-12/ms-16、mm-4/5/6、g2-4/5/6),
// JS 只切换 className,不写任何内联样式,从根源避免 setAttribute 触发引擎重排导致的卡顿

/* ---- 扫雷 ---- */
var ms = null;

function buildMinesweeper(box) {
  var bar = el("div", "game-bar");
  bar.appendChild(el("span", "game-stat", "剩余雷数"));
  var mineStat = el("span", "game-stat");
  mineStat.id = "msMineStat";
  bar.appendChild(mineStat);
  var restart = el("button", "head-btn", "重新开始");
  restart.id = "msRestart";
  bar.appendChild(restart);
  var result = el("span", "game-result");
  result.id = "msResult";
  bar.appendChild(result);
  box.appendChild(bar);
  var grid = el("div", "ms-grid");
  grid.id = "msGrid";
  box.appendChild(grid);
  box.appendChild(el("div", "game-stat game-hint", "左键翻开 · 右键插旗"));
  restart.addEventListener("click", startMinesweeper);
  startMinesweeper();
}

// 分散布雷:棋盘按 4x4 区块均分雷数(每区块雷数 ±1),避免纯随机扎堆导致过难
// 支持长方形棋盘(cols x rows)
function msPlaceMines(cols, rows, mineTotal) {
  var mines = [];
  var block = 4;
  var zones = [];
  for (var rb = 0; rb < rows; rb += block) {
    var rEnd = Math.min(rb + block, rows);
    for (var cb = 0; cb < cols; cb += block) {
      var cEnd = Math.min(cb + block, cols);
      var zoneCells = [];
      for (var r = rb; r < rEnd; r++) {
        for (var c = cb; c < cEnd; c++) {
          zoneCells.push(r * cols + c);
        }
      }
      zones.push(zoneCells);
    }
  }
  var per = Math.floor(mineTotal / zones.length);
  var extra = mineTotal % zones.length;
  for (var zi = 0; zi < zones.length; zi++) {
    var need = per + (zi < extra ? 1 : 0);
    var pool = zones[zi].slice();
    for (var k = 0; k < need && pool.length > 0; k++) {
      var p = Math.floor(Math.random() * pool.length);
      mines.push(pool[p]);
      pool.splice(p, 1);
    }
  }
  // 兜底:极端情况从全局补齐
  while (mines.length < mineTotal) {
    var idx = Math.floor(Math.random() * cols * rows);
    if (mines.indexOf(idx) < 0) { mines.push(idx); }
  }
  return mines;
}

// 延迟布雷:排除安全区(点击格及其周围 8 格)后,按 4x4 区块均分雷数
function msPlaceMinesExcluding(cols, rows, mineTotal, safeIdx) {
  var mines = [];
  var safe = {};
  safe[safeIdx] = true;
  var nb = msNeighbors(safeIdx);
  for (var ni = 0; ni < nb.length; ni++) { safe[nb[ni]] = true; }
  var block = 4;
  var zones = [];
  for (var rb = 0; rb < rows; rb += block) {
    var rEnd = Math.min(rb + block, rows);
    for (var cb = 0; cb < cols; cb += block) {
      var cEnd = Math.min(cb + block, cols);
      var zoneCells = [];
      for (var r = rb; r < rEnd; r++) {
        for (var c = cb; c < cEnd; c++) {
          var cellIdx = r * cols + c;
          if (!safe[cellIdx]) { zoneCells.push(cellIdx); }
        }
      }
      zones.push(zoneCells);
    }
  }
  var per = Math.floor(mineTotal / zones.length);
  var extra = mineTotal % zones.length;
  for (var zi = 0; zi < zones.length; zi++) {
    var need = per + (zi < extra ? 1 : 0);
    var pool = zones[zi].slice();
    for (var k = 0; k < need && pool.length > 0; k++) {
      var p = Math.floor(Math.random() * pool.length);
      mines.push(pool[p]);
      pool.splice(p, 1);
    }
  }
  // 兜底:从全局非安全区补齐
  while (mines.length < mineTotal) {
    var idx2 = Math.floor(Math.random() * cols * rows);
    if (mines.indexOf(idx2) < 0 && !safe[idx2]) { mines.push(idx2); }
  }
  return mines;
}

function startMinesweeper() {
  var d = MS_DIFF[currentDifficulty] || [8, 8, 10];
  var cols = d[0];
  var rows = d[1];
  var mineTotal = d[2];
  ms = { cols: cols, rows: rows, mines: [], revealed: [], flags: [], over: false, startTime: Date.now(), placed: false, mineTotal: mineTotal };
  // 延迟布雷:首次点击时再布(排除点击格及周围,保证第一次必安全且能展开)
  for (var i = 0; i < cols * rows; i++) {
    ms.revealed.push(false);
    ms.flags.push(false);
  }
  $("msResult").textContent = "";
  $("msResult").className = "game-result";
  renderMsGrid();
}

function msNeighbors(idx) {
  var cols = ms.cols;
  var rows = ms.rows;
  var r = Math.floor(idx / cols);
  var c = idx % cols;
  var out = [];
  for (var dr = -1; dr <= 1; dr++) {
    for (var dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) { continue; }
      var nr = r + dr;
      var nc = c + dc;
      if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
        out.push(nr * cols + nc);
      }
    }
  }
  return out;
}

function msMineCount(idx) {
  var n = msNeighbors(idx);
  var c = 0;
  for (var i = 0; i < n.length; i++) {
    if (ms.mines.indexOf(n[i]) >= 0) { c++; }
  }
  return c;
}

function msReveal(idx) {
  if (ms.over || ms.revealed[idx] || ms.flags[idx]) { return; }
  // 首次点击才布雷:排除点击格及其周围,保证第一次点击不踩雷且能展开
  if (!ms.placed) {
    ms.mines = msPlaceMinesExcluding(ms.cols, ms.rows, ms.mineTotal, idx);
    ms.placed = true;
  }
  ms.revealed[idx] = true;
  if (ms.mines.indexOf(idx) >= 0) {
    ms.over = true;
    for (var i = 0; i < ms.mines.length; i++) { ms.revealed[ms.mines[i]] = true; }
    $("msResult").className = "game-result game-result-lose";
    renderMsGrid();
    return;
  }
  if (msMineCount(idx) === 0) {
    var n = msNeighbors(idx);
    for (var j = 0; j < n.length; j++) { msReveal(n[j]); }
  }
  // 胜利判断
  var revealedTotal = 0;
  for (var k = 0; k < ms.revealed.length; k++) {
    if (ms.revealed[k]) { revealedTotal++; }
  }
  if (!ms.over && revealedTotal === ms.rows * ms.cols - ms.mines.length) {
    ms.over = true;
    var secs = Math.round((Date.now() - ms.startTime) / 1000);
    recordScore(secs);
    $("msResult").textContent = "胜利 " + secs + "s";
    $("msResult").className = "game-result game-result-win";
  }
  renderMsGrid();
}

function msToggleFlag(idx) {
  if (ms.over || ms.revealed[idx]) { return; }
  ms.flags[idx] = !ms.flags[idx];
  renderMsGrid();
}

function renderMsGrid() {
  var grid = $("msGrid");
  if (!grid) { return; }
  var total = ms.rows * ms.cols;
  // 难点(2026-08-24 AUI 崩溃修复): 手机页 grid 子元素严禁 removeChild —— AUI 布局
  // 测量可能恰逢定时器/事件回调执行 removeChild 把 grid 项摘除, 测量时 parentElement
  // 变 null → NPE(Size.shouldFillAvailableBlockWidth)。改为首次一次性建满最大格子数
  // (困难 24x9=216), 索引闭包=格子位置, 复用后位置不变监听永远有效; 之后只更新
  // className/text, 多余格子用 gcell-off(display:none) 隐藏, 永不摘除。
  var kids = grid.children;
  if (kids.length === 0) {
    for (var idx = 0; idx < 216; idx++) {
      (function (i) {
        var cell = el("div", "ms-cell");
        cell.addEventListener("click", function () { msReveal(i); });
        // 右键插旗(mousedown): AUI 的右键 button=1(非标准 2), 兼容两者
        cell.addEventListener("mousedown", function (event) {
          if (event.button === 1 || event.button === 2) { msToggleFlag(i); }
        });
        grid.appendChild(cell);
      })(idx);
    }
  }
  grid.className = "ms-grid ms-" + ms.cols;
  var flagged = 0;
  for (var f = 0; f < ms.flags.length; f++) { if (ms.flags[f]) { flagged++; } }
  $("msMineStat").textContent = "" + (ms.mineTotal - flagged);
  // 增量更新格子状态
  for (var j = 0; j < 216; j++) {
    var c = kids[j];
    if (!c) { continue; }
    if (j >= total) {
      if (c.className !== "ms-cell gcell-off") { c.className = "ms-cell gcell-off"; }
      continue;
    }
    var cls = "ms-cell";
    var txt = "";
    if (ms.revealed[j]) {
      cls += " ms-cell-open";
      if (ms.mines.indexOf(j) >= 0) {
        cls += " ms-cell-mine";
        txt = "●";
      } else {
        var n = msMineCount(j);
        if (n > 0) {
          txt = "" + n;
          if (n === 1) { cls += " ms-cell-n1"; }
          else if (n === 2) { cls += " ms-cell-n2"; }
          else if (n === 3) { cls += " ms-cell-n3"; }
          else { cls += " ms-cell-n4"; }
        }
      }
    } else if (ms.flags[j]) {
      cls += " ms-cell-flag";
      txt = "⚑";
    }
    if (c.className !== cls) { c.className = cls; }
    if (c.textContent !== txt) { c.textContent = txt; }
  }
}

/* ---- 记忆翻牌 ---- */
var mm = null;
var mmSymbols = ["△", "□", "○", "◇", "★", "▲", "■", "●", "◆", "◎", "♢", "♧", "✦", "✧", "❖", "✚", "✛", "✢", "✣", "✤", "✱", "❍", "⬢", "♠"];

function buildMemory(box) {
  var bar = el("div", "game-bar");
  var steps = el("span", "game-stat");
  steps.id = "mmSteps";
  bar.appendChild(steps);
  var restart = el("button", "head-btn", "重新开始");
  restart.id = "mmRestart";
  bar.appendChild(restart);
  var result = el("span", "game-result");
  result.id = "mmResult";
  bar.appendChild(result);
  box.appendChild(bar);
  var grid = el("div", "mm-grid");
  grid.id = "mmGrid";
  box.appendChild(grid);
  restart.addEventListener("click", startMemory);
  startMemory();
}

function startMemory() {
  var d = MM_DIFF[currentDifficulty] || [4, 4];
  var cols = d[0];
  var rows = d[1];
  var pairs = cols * rows / 2;
  var symbols = [];
  for (var s = 0; s < pairs; s++) { symbols.push(mmSymbols[s]); }
  var cards = [];
  for (var i = 0; i < symbols.length; i++) {
    cards.push(symbols[i]);
    cards.push(symbols[i]);
  }
  // 洗牌
  for (var j = cards.length - 1; j > 0; j--) {
    var k = Math.floor(Math.random() * (j + 1));
    var tmp = cards[j];
    cards[j] = cards[k];
    cards[k] = tmp;
  }
  mm = { cards: cards, cols: cols, flipped: [], done: [], steps: 0, lock: false, first: -1 };
  $("mmResult").textContent = "";
  $("mmResult").className = "game-result";
  renderMemoryGrid();
}

function renderMemoryGrid() {
  var grid = $("mmGrid");
  if (!grid) { return; }
  grid.className = "mm-grid mm-" + mm.cols;
  $("mmSteps").textContent = "步数 " + mm.steps;
  // 同扫雷(2026-08-24 AUI 崩溃修复): 首次一次性建满最大卡数(困难 8x6=48),
  // 索引闭包=卡片位置, 之后只更新 className/text, 永不 removeChild(翻牌翻转
  // 的 500ms setTimeout 回调恰是 AUI 布局测量中途摘除 grid 项的高发场景)。
  var kids = grid.children;
  if (kids.length === 0) {
    for (var idx = 0; idx < 48; idx++) {
      (function (i) {
        var card = el("div", "mm-card");
        card.addEventListener("click", function () { mmFlip(i); });
        grid.appendChild(card);
      })(idx);
    }
  }
  for (var j = 0; j < 48; j++) {
    var c = kids[j];
    if (!c) { continue; }
    if (j >= mm.cards.length) {
      if (c.className !== "mm-card gcell-off") { c.className = "mm-card gcell-off"; }
      continue;
    }
    var cls = "mm-card";
    var txt = "?";
    if (mm.done.indexOf(j) >= 0) {
      cls += " mm-card-flipped mm-card-done";
      txt = mm.cards[j];
    } else if (mm.flipped.indexOf(j) >= 0) {
      cls += " mm-card-flipped";
      txt = mm.cards[j];
    }
    if (c.className !== cls) { c.className = cls; }
    if (c.textContent !== txt) { c.textContent = txt; }
  }
}

function mmFlip(idx) {
  if (mm.lock || mm.flipped.indexOf(idx) >= 0 || mm.done.indexOf(idx) >= 0) { return; }
  mm.flipped.push(idx);
  if (mm.flipped.length === 2) {
    mm.steps++;
    var a = mm.flipped[0];
    var b = mm.flipped[1];
    if (mm.cards[a] === mm.cards[b]) {
      mm.done.push(a);
      mm.done.push(b);
      mm.flipped = [];
      if (mm.done.length === mm.cards.length) {
        recordScore(mm.steps);
        $("mmResult").textContent = "完成 · " + mm.steps + " 步";
        $("mmResult").className = "game-result game-result-win";
      }
      renderMemoryGrid();
    } else {
      mm.lock = true;
      renderMemoryGrid();
      setTimeout(function () {
        mm.flipped = [];
        mm.lock = false;
        renderMemoryGrid();
      }, 500);
    }
  } else {
    renderMemoryGrid();
  }
}


/* ---- 2048 ---- */
var g2048 = null;

function build2048(box) {
  var bar = el("div", "game-bar");
  var score = el("span", "game-stat");
  score.id = "g2048Score";
  bar.appendChild(score);
  var restart = el("button", "head-btn", "重新开始");
  restart.id = "g2048Restart";
  bar.appendChild(restart);
  var result = el("span", "game-result");
  result.id = "g2048Result";
  bar.appendChild(result);
  box.appendChild(bar);
  var grid = el("div", "g2048-grid");
  grid.id = "g2048Grid";
  box.appendChild(grid);
  box.appendChild(el("div", "game-stat game-hint", "方向键 / WASD / 小键盘移动"));
  restart.addEventListener("click", start2048);
  start2048();
}

function start2048() {
  var n = G_DIFF[currentDifficulty] || 4;
  var board = [];
  for (var r = 0; r < n; r++) {
    var row = [];
    for (var c = 0; c < n; c++) { row.push(0); }
    board.push(row);
  }
  g2048 = { size: n, board: board, score: 0, over: false, won: false };
  $("g2048Result").textContent = "";
  $("g2048Result").className = "game-result";
  spawn2048Tile();
  spawn2048Tile();
  render2048Grid();
}

function spawn2048Tile() {
  var empty = [];
  for (var r = 0; r < g2048.size; r++) {
    for (var c = 0; c < g2048.size; c++) {
      if (g2048.board[r][c] === 0) { empty.push([r, c]); }
    }
  }
  if (empty.length === 0) { return; }
  var p = empty[Math.floor(Math.random() * empty.length)];
  g2048.board[p[0]][p[1]] = Math.random() < 0.9 ? 2 : 4;
}

function mergeLine(line) {
  var nums = [];
  for (var i = 0; i < line.length; i++) {
    if (line[i] !== 0) { nums.push(line[i]); }
  }
  var out = [];
  for (var j = 0; j < nums.length; j++) {
    if (j + 1 < nums.length && nums[j] === nums[j + 1]) {
      out.push(nums[j] * 2);
      g2048.score += nums[j] * 2;
      j++;
    } else {
      out.push(nums[j]);
    }
  }
  while (out.length < line.length) { out.push(0); }
  return out;
}

function move2048(dir) {
  if (!g2048 || g2048.over) { return; }
  var n = g2048.size;
  var moved = false;
  if (dir === "left" || dir === "right") {
    for (var r = 0; r < n; r++) {
      var line = [];
      for (var c = 0; c < n; c++) { line.push(g2048.board[r][c]); }
      if (dir === "right") { line.reverse(); }
      var merged = mergeLine(line);
      if (dir === "right") { merged.reverse(); }
      for (var c2 = 0; c2 < n; c2++) {
        if (g2048.board[r][c2] !== merged[c2]) { moved = true; }
        g2048.board[r][c2] = merged[c2];
      }
    }
  } else {
    for (var c3 = 0; c3 < n; c3++) {
      var line2 = [];
      for (var r2 = 0; r2 < n; r2++) { line2.push(g2048.board[r2][c3]); }
      if (dir === "down") { line2.reverse(); }
      var merged2 = mergeLine(line2);
      if (dir === "down") { merged2.reverse(); }
      for (var r3 = 0; r3 < n; r3++) {
        if (g2048.board[r3][c3] !== merged2[r3]) { moved = true; }
        g2048.board[r3][c3] = merged2[r3];
      }
    }
  }
  if (moved) {
    spawn2048Tile();
    render2048Grid();
    check2048End();
  }
}

function has2048Move() {
  var n = g2048.size;
  for (var r = 0; r < n; r++) {
    for (var c = 0; c < n; c++) {
      if (g2048.board[r][c] === 0) { return true; }
      if (c < n - 1 && g2048.board[r][c] === g2048.board[r][c + 1]) { return true; }
      if (r < n - 1 && g2048.board[r][c] === g2048.board[r + 1][c]) { return true; }
    }
  }
  return false;
}

function check2048End() {
  for (var r = 0; r < g2048.size; r++) {
    for (var c = 0; c < g2048.size; c++) {
      if (g2048.board[r][c] === 2048 && !g2048.won) {
        g2048.won = true;
        $("g2048Result").textContent = "达成 2048!";
        $("g2048Result").className = "game-result game-result-win";
        return;
      }
    }
  }
  if (!has2048Move()) {
    g2048.over = true;
    recordScore(g2048.score);
    $("g2048Result").textContent = "无路可走";
    $("g2048Result").className = "game-result game-result-lose";
  }
}

function render2048Grid() {
  var grid = $("g2048Grid");
  if (!grid) { return; }
  grid.className = "g2048-grid g2-" + g2048.size;
  $("g2048Score").textContent = "得分 " + g2048.score;
  // 同扫雷(2026-08-24 AUI 崩溃修复): 首次一次性建满最大格数(6x6=36),
  // 之后只更新 className/text, 永不 removeChild(方向键事件与布局测量
  // 可能同帧交错, 全量 clearNode+重建是崩溃高发路径)。
  var n = g2048.size;
  var total = n * n;
  var kids = grid.children;
  if (kids.length === 0) {
    for (var idx = 0; idx < 36; idx++) {
      grid.appendChild(el("div", "g2048-cell"));
    }
  }
  for (var i = 0; i < 36; i++) {
    var c = kids[i];
    if (!c) { continue; }
    if (i >= total) {
      if (c.className !== "g2048-cell gcell-off") { c.className = "g2048-cell gcell-off"; }
      continue;
    }
    var v = g2048.board[Math.floor(i / n)][i % n];
    var cls = "g2048-cell";
    var txt = "";
    if (v > 0) {
      txt = "" + v;
      if (v <= 2048) { cls += " g2048-t" + v; }
    }
    if (c.className !== cls) { c.className = cls; }
    if (c.textContent !== txt) { c.textContent = txt; }
  }
}


/* ---- 贪吃蛇 ---- */
var snake = null;

function buildSnake(box) {
  var bar = el("div", "game-bar");
  var score = el("span", "game-stat");
  score.id = "snakeScore";
  bar.appendChild(score);
  var toggle = el("button", "head-btn", "暂停");
  toggle.id = "snakeToggleBtn";
  bar.appendChild(toggle);
  var restart = el("button", "head-btn", "重新开始");
  restart.id = "snakeRestart";
  bar.appendChild(restart);
  var result = el("span", "game-result");
  result.id = "snakeResult";
  bar.appendChild(result);
  box.appendChild(bar);
  // 难点: AUI 的 document.createElement("canvas") 只返回普通 Element, 没有 getContext,
  // 只有 HTML 解析时的 <canvas> 标签才被替换为 Canvas 类, 因此 canvas 在 index.html
  // 静态声明, 脚本加载时已缓存引用(snakeCanvasEl)。每次进入贪吃蛇 renderGame 都会
  // clearNode(gameBody) 把 canvas 移出 DOM, 这里统一重新挂载。
  var canvas = snakeCanvasEl;
  if (canvas) { box.appendChild(canvas); }
  box.appendChild(el("div", "game-stat game-hint", "方向键 / WASD / 小键盘转向"));
  toggle.addEventListener("click", function () {
    if (!snake) { return; }
    if (snake.running) {
      snake.running = false;
      if (snake.timer) { clearTimeout(snake.timer); snake.timer = null; }
      toggle.textContent = "开始";
    } else {
      snake.running = true;
      toggle.textContent = "暂停";
      snake.timer = setTimeout(snakeTick, 180);
    }
  });
  restart.addEventListener("click", startSnake);
  startSnake();
}

function startSnake() {
  if (snake && snake.timer) { clearTimeout(snake.timer); }
  snake = { size: 16, dir: { x: 1, y: 0 }, nextDir: { x: 1, y: 0 }, body: [{x:7,y:8},{x:6,y:8},{x:5,y:8}], food: null, score: 0, running: true, timer: null, inputQueue: [] };
  $("snakeResult").textContent = "";
  $("snakeResult").className = "game-result";
  var btn = $("snakeToggleBtn");
  if (btn) { btn.textContent = "暂停"; }
  spawnSnakeFood();
  renderSnake();
  snake.timer = setTimeout(snakeTick, SN_DIFF[currentDifficulty] || 160);
}

function snakeTick() {
  if (!snake || !snake.running) { return; }
  // 难点: 输入缓冲队列, 每个 tick 消费一个方向。玩家快速连按时(如贴墙"上+左"),
  // 两次按键都会入队, 不会被旧方向误判吞掉, 蛇先上后左贴着边缘转向, 不会撞墙。
  if (snake.inputQueue && snake.inputQueue.length > 0) {
    snake.dir = snake.inputQueue.shift();
    snake.nextDir = snake.dir;
  } else {
    snake.dir = snake.nextDir;
  }
  var head = { x: snake.body[0].x + snake.dir.x, y: snake.body[0].y + snake.dir.y };
  if (head.x < 0 || head.x >= snake.size || head.y < 0 || head.y >= snake.size) { snakeEnd(); return; }
  for (var i = 0; i < snake.body.length; i++) {
    if (snake.body[i].x === head.x && snake.body[i].y === head.y) { snakeEnd(); return; }
  }
  snake.body.unshift(head);
  if (snake.food && head.x === snake.food.x && head.y === snake.food.y) {
    snake.score += 10;
    spawnSnakeFood();
  } else {
    snake.body.pop();
  }
  renderSnake();
  // 难点: setInterval 在 AUI Rhino 不可靠, 用 setTimeout 递归
  if (snake && snake.running) { snake.timer = setTimeout(snakeTick, SN_DIFF[currentDifficulty] || 160); }
}

function snakeEnd() {
  snake.running = false;
  if (snake.timer) { clearTimeout(snake.timer); snake.timer = null; }
  recordScore(snake.score);
  $("snakeResult").textContent = "结束 · 分数 " + snake.score;
  $("snakeResult").className = "game-result game-result-lose";
  var btn = $("snakeToggleBtn");
  if (btn) { btn.textContent = "开始"; }
}

function spawnSnakeFood() {
  var empty = [];
  for (var x = 0; x < snake.size; x++) {
    for (var y = 0; y < snake.size; y++) {
      var on = false;
      for (var i = 0; i < snake.body.length; i++) {
        if (snake.body[i].x === x && snake.body[i].y === y) { on = true; }
      }
      if (!on) { empty.push({ x: x, y: y }); }
    }
  }
  if (empty.length === 0) { snakeEnd(); return; }
  snake.food = empty[Math.floor(Math.random() * empty.length)];
}

function renderSnake() {
  var canvas = snakeCanvasEl || $("snakeCanvas");
  if (!canvas || !snake) { return; }
  var ctx = canvas.getContext("2d");
  var cell = 14; // 16 格 × 14px = 224px, 与 CSS 尺寸一致
  // 难点: AUI Rhino 中 ctx.fillStyle = x 是字段赋值(无该字段会报错),
  // 必须调用 setFillStyle; 浏览器原生则相反(有 fillStyle 字段无 setFillStyle)。
  function setFill(c) {
    if (ctx.setFillStyle) { ctx.setFillStyle(c); }
    else { ctx.fillStyle = c; }
  }
  setFill("#0f0f13");
  ctx.fillRect(0, 0, 224, 224);
  if (snake.food) {
    setFill("#c1272d");
    ctx.fillRect(snake.food.x * cell, snake.food.y * cell, cell - 1, cell - 1);
  }
  for (var i = 0; i < snake.body.length; i++) {
    setFill(i === 0 ? "#e6e6ea" : "#9fbf8f");
    ctx.fillRect(snake.body[i].x * cell, snake.body[i].y * cell, cell - 1, cell - 1);
  }
  $("snakeScore").textContent = "分数 " + snake.score;
}

function setSnakeDir(x, y) {
  if (!snake) { return; }
  // 难点: 反向检查用"队列最后一个方向"而非当前 dir, 否则快速连按时
  // (蛇向右, 先按上再按左) 第二次按键被旧方向误判为掉头吞掉, 贴墙时撞墙失败。
  var last = snake.inputQueue && snake.inputQueue.length > 0
    ? snake.inputQueue[snake.inputQueue.length - 1]
    : snake.nextDir;
  if (last.x === x && last.y === y) { return; }
  if (last.x === -x && last.y === -y) { return; }
  if (snake.inputQueue.length < 3) { snake.inputQueue.push({ x: x, y: y }); }
  snake.nextDir = { x: x, y: y };
}

// 方向输入统一分发(2048 移动 / 贪吃蛇转向)
function handleGameDir(dir) {
  if (state.currentGameId === "g2048") {
    if (dir === "up") { move2048("up"); }
    else if (dir === "down") { move2048("down"); }
    else if (dir === "left") { move2048("left"); }
    else if (dir === "right") { move2048("right"); }
  } else if (state.currentGameId === "snake") {
    if (dir === "up") { setSnakeDir(0, -1); }
    else if (dir === "down") { setSnakeDir(0, 1); }
    else if (dir === "left") { setSnakeDir(-1, 0); }
    else if (dir === "right") { setSnakeDir(1, 0); }
  }
}

// ---- Modal ----
function openModal(id) {
  $(id).className = "modal-backdrop open";
}

function closeModal(id) {
  $(id).className = "modal-backdrop";
}


// ---- 主屏玩家档案 ----
function fmtNum(n) {
  var s = "" + n;
  var out = "";
  var count = 0;
  for (var i = s.length - 1; i >= 0; i--) {
    out = s.charAt(i) + out;
    count++;
    if (count % 3 === 0 && i > 0) { out = "," + out; }
  }
  return out;
}

function renderPlayerInfo() {
  $("playerId").textContent = mockPlayer.name || mockPlayer.id;
  $("playerPosition").textContent = mockPlayer.position || "";
  $("playerDept").textContent = mockPlayer.dept || "";
  $("playerBalance").textContent = fmtBalance(mockPlayer.balance);
}

function fmtBalance(v) {
  if (typeof v === "number") { return fmtNum(v); }
  return "" + v;
}

// 原型演示:点击部门循环切换,看不同部门任务池(正式接入后删除)
// 已删除: 部门跟随玩家身上 ID 卡, 不允许在信息栏手动切换

// ---- 主屏统计 ----
function updateHomeBadges() {
  var tasks = getCurrentTasks();
  var logDone = 0;
  for (var i = 0; i < tasks.length; i++) {
    if (tasks[i].state === "claimed") { logDone++; }
  }
  $("statLog").textContent = logDone + "/" + tasks.length;
  var fill = $("statFill");
  if (fill) {
    var pct = tasks.length ? Math.round((logDone / tasks.length) * 100) : 0;
    setStyle(fill, { width: pct + "%" });
  }
}


function renderReportForm() {
  var sel = $("reportAnomalySelect");
  if (!sel) { return; }
  clearNode(sel);
  var list = (mock.anomalies && mock.anomalies.instances) ? mock.anomalies.instances : [];
  if (list.length === 0) {
    var emptyOpt = el("option", "", "暂无可提交的异常物实例");
    emptyOpt.setAttribute("value", "");
    sel.appendChild(emptyOpt);
    return;
  }
  for (var i = 0; i < list.length; i++) {
    var inst = list[i];
    var opt = el("option", "", (inst.codexId || "异常物") + " · " + (inst.instanceId || ""));
    opt.setAttribute("value", inst.instanceId || "");
    sel.appendChild(opt);
  }
}

function submitReportFromForm() {
  var sel = $("reportAnomalySelect");
  var titleEl = $("reportTitleInput");
  var contentEl = $("reportContentInput");
  var hint = $("reportSubmitHint");
  var instanceId = sel ? (sel.value || sel.getAttribute("value") || "") : "";
  var title = titleEl ? (titleEl.value || "") : "";
  var content = contentEl ? (contentEl.value || "") : "";
  if (!instanceId || !title || !content) { if (hint) hint.textContent = "请先选择异常物并填写标题/正文。"; return; }
  pushReportSubmit(instanceId, title, content);
  if (hint) hint.textContent = "报告已加入提交队列，等待服务端确认。";
  if (titleEl) setInputValue(titleEl, "");
  if (contentEl) setInputValue(contentEl, "");
}

// ---- 初始化 ----
function init() {
  document.body.setAttribute("data-init-1", "1");
  // 页面跳转按钮
  var gotos = document.querySelectorAll("[data-goto]");
  for (var i = 0; i < gotos.length; i++) {
    (function (node) {
      node.addEventListener("click", function () {
        gotoPage(node.getAttribute("data-goto"));
      });
    })(gotos[i]);
  }
  // modal 关闭按钮
  var closes = document.querySelectorAll("[data-close]");
  for (var j = 0; j < closes.length; j++) {
    (function (node) {
      node.addEventListener("click", function () {
        closeModal(node.getAttribute("data-close"));
      });
    })(closes[j]);
  }

  $("btnRefreshNearby").addEventListener("click", tryRefreshNearby);
  if ($("btnSubmitReport")) { $("btnSubmitReport").addEventListener("click", submitReportFromForm); }
  $("btnNewRoom").addEventListener("click", function () { openModal("newRoomModal"); });
  $("btnCreateRoom").addEventListener("click", createRoom);
  $("btnJoinRoom").addEventListener("click", function () { openModal("joinRoomModal"); });
  $("btnConfirmJoin").addEventListener("click", joinRoomByName);
  $("btnInviteMember").addEventListener("click", openInviteModal);
  $("btnSendMsg").addEventListener("click", sendMessage);
  $("btnToggleMembers").addEventListener("click", toggleMembers);
  $("btnRoomSettings").addEventListener("click", openRoomSettings);
  $("btnConfirmRename").addEventListener("click", confirmRename);
  $("btnDissolveRoom").addEventListener("click", dissolveRoom);
  $("btnBackToRooms").addEventListener("click", function () {
    // 私聊模式返回联系人 tab,群聊模式返回群聊列表
    if (state.currentDmUuid) {
      state.currentDmUuid = null;
      $("btnBackToRooms").textContent = "← 返回群聊";
      state.commTab = "contacts";
      gotoPage("comm");
    } else {
      state.commTab = "rooms";
      gotoPage("comm");
    }
  });
  $("btnGotoNearby").addEventListener("click", function () { switchCommTab("nearby"); });
  // 通讯板块 tab
  var commTabs = document.querySelectorAll("[data-comm-tab]");
  for (var k = 0; k < commTabs.length; k++) {
    (function (node) {
      node.addEventListener("click", function () {
        switchCommTab(node.getAttribute("data-comm-tab"));
      });
    })(commTabs[k]);
  }
  $("chatInput").addEventListener("keydown", function (event) {
    if (event.key === "Enter") { sendMessage(); }
  });

  document.body.setAttribute("data-init-2", "1");
  tickClock();
  tickDate();
  document.body.setAttribute("data-init-3", "1");
  updateNearbyBtn();

  // 滚动容器(chatMessages / page-content / chat-members / icon-grid)的滚轮与滚底
  // 已由 Java 侧 PhoneScrollController 接管, 此处无需 bindHiddenScroll。
  document.body.setAttribute("data-init-4", "1");
  // 桥接模式:数据由 Java 全量下发,本地仅轮询;网页预览:使用本地模拟数据
  bridgeMode = !!document.getElementById("phoneData");
  document.body.setAttribute("data-init-bm", "1");
  if (!bridgeMode) {
    renderContacts(true);
    // 懒渲染: 附近/群聊面板等切到对应 tab 时才渲染(初始仅 contacts 常驻)
    renderDirty.nearby = true;
    renderDirty.rooms = true;
  } else {
    // 桥接模式首次打开: 服务端可能已把全量数据写入 #phoneData(打开手机时 Java 主动推送),
    // 但页面 init 在数据解析前就用空 mock 渲染了主屏 → 玩家信息空白。
    // 立即同步读取一次 phoneData(不带递归), 数据已就绪则马上应用并重渲染, 不依赖 5s 后的 bridgePoll。
    bridgePollOnce();
  }
  renderTasks();
  document.body.setAttribute("data-init-t", "1");
  renderPlayerInfo();
  document.body.setAttribute("data-init-p", "1");
  renderImportant();
  document.body.setAttribute("data-init-i", "1");
  renderDept();
  document.body.setAttribute("data-init-d", "1");
  renderGames();
  document.body.setAttribute("data-init-g", "1");
  updateUnread();
  document.body.setAttribute("data-init-5", "1");
  if (bridgeMode) {
    // 初次打开: 服务端全量数据经网络往返才写入 #phoneData, init 时可能还没到,
    // 先用高频探测几次(每次检测到数据就重渲染玩家信息), 命中后切回 5s 低频兜底。
    bridgePollOnce();
    if (((bridgeElData && bridgeElData.textContent) || "").length === 0) {
      var probe = 0;
      var probeTimer = setInterval(function () {
        probe++;
        bridgePollOnce();
        if (((bridgeElData && bridgeElData.textContent) || "").length > 0 || probe >= 6) {
          clearInterval(probeTimer);
          bridgeTimer = setTimeout(bridgePoll, 5000);
        }
      }, 500);
    } else {
      bridgeTimer = setTimeout(bridgePoll, 5000);
    }
  }
  $("btnBackToFun").addEventListener("click", function () { gotoPage("fun"); });
  $("btnLeaderboard").addEventListener("click", openLeaderboard);
  document.body.setAttribute("data-init-6", "1");
  // 难度切换
  var diffTabs = document.querySelectorAll(".diff-tab");
  for (var dt = 0; dt < diffTabs.length; dt++) {
    (function (node) {
      node.addEventListener("click", function () {
        switchDifficulty(node.getAttribute("data-diff"));
      });
    })(diffTabs[dt]);
  }
  // 游戏页键盘方向控制(方向键 / WASD / 小键盘 2468)
  document.addEventListener("keydown", function (event) {
    if (state.page !== "game") { return; }
    var k = event.key || "";
    var code = event.keyCode || 0;
    if (k === "ArrowLeft" || k === "a" || k === "A" || code === 37 || code === 100) { handleGameDir("left"); }
    else if (k === "ArrowUp" || k === "w" || k === "W" || code === 38 || code === 104) { handleGameDir("up"); }
    else if (k === "ArrowRight" || k === "d" || k === "D" || code === 39 || code === 102) { handleGameDir("right"); }
    else if (k === "ArrowDown" || k === "s" || k === "S" || code === 40 || code === 98) { handleGameDir("down"); }
  });
  // 终端内阻止浏览器右键菜单(扫雷右键插旗用)
  document.addEventListener("contextmenu", function (event) { event.preventDefault(); });
  gotoPage("home");
  document.body.setAttribute("data-init-7", "1");

  // 页面就绪后立即显示。AUI 的 Document 是直接 create 的,window.load 事件可能不触发,
  // 不能依赖 load/setTimeout 才添加 phone-ready(body 默认 visibility:hidden 会白屏)。
  showPhone();
  document.body.setAttribute("data-init-8", "1");
}

// AUI 中 DOMContentLoaded 事件不触发(参考电脑终端 gui.js 的做法):
// 页面脚本在 HTML 解析完成后才执行,此时 DOM 已就绪,直接顶层调用 init()
init();

// 页面就绪后显示(load 事件优先,setTimeout 兜底,避免开屏闪现未样式化文本)
// 用 className 字符串方式,不依赖 classList(与电脑终端 gui.js 一致,AUI 最稳)
function showPhone() {
  var body = document.body;
  if (!body) { return; }
  var cls = body.getAttribute("class") || "";
  if (cls.indexOf("phone-ready") < 0) {
    body.setAttribute("class", (cls + " phone-ready").replace(/\s{2,}/g, " ").trim());
  }
}
window.addEventListener("load", showPhone);
setTimeout(showPhone, 500);

