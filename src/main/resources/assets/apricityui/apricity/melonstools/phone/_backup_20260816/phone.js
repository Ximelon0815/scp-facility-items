// ============================================================
//  Melon Phone 界面交互逻辑(网页原型版 + AUI 桥接版)
//  AUI 规范: Rhino JS,var + function,DOMContentLoaded 初始化
//  动态内容一律 createElement + textContent,不用 innerHTML
//  桥接模式: 数据由 Java 写入 #phoneData 等隐藏元素,本页轮询解析;
//            操作写入 #melonActionQueue 由 Java 消费后发网络包
// ============================================================

// 调试探针: 确认外部 script 是否被 AUI 加载并执行
try { document.body.setAttribute("data-phone-loaded", "1"); } catch (e) { document.body.setAttribute("data-phone-err", "1"); }

// ---- 模拟数据(正式接入时来自 SyncPhoneData / SyncNearbyPlayers / SyncChatData) ----
var mock = {
  friends: [
    { uuid: "f-steve", name: "Steve", online: true, avatar: "avatars/sample1.png" },
    { uuid: "f-alex", name: "Alex", online: true, avatar: "avatars/sample2.png" },
    { uuid: "f-xigua", name: "西瓜冰", online: false, avatar: "avatars/sample1.png" },
    { uuid: "f-hero", name: "Herobrine", online: false, avatar: "avatars/sample2.png" }
  ],
  pending: [
    { uuid: "p-griefer", name: "Griefer_2009", avatar: "avatars/sample2.png" },
    { uuid: "p-luren", name: "路人甲", avatar: "avatars/sample1.png" }
  ],
  nearby: [
    { uuid: "n-lava", name: "熔岩行者", dist: 6.4, isFriend: false, avatar: "avatars/sample2.png" },
    { uuid: "n-xigua", name: "西瓜冰", dist: 9.6, isFriend: true, avatar: "avatars/sample1.png" },
    { uuid: "n-zhang", name: "矿工老张", dist: 7.3, isFriend: false, avatar: "avatars/sample1.png" },
    { uuid: "n-ling", name: "末影小灵", dist: 9.9, isFriend: false, avatar: "avatars/sample2.png" }
  ],
  rooms: [
    {
      id: "r1",
      name: "服务器闲聊",
      avatar: "minecraft:diamond_sword",
      icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAAJFBMVEUAAAAOPzYIJSAz68uk/fArx6weincVY1VoTh5JNhUoHguJZycazUdMAAAAAXRSTlMAQObYZgAAAExJREFUGNN1jsENwDAMAgGndpLuv29/jZFa/+4QwkA/ks5jyPky8cHxwyQAxslZNAaYiux9Zlb4A0XjNcVu1r0hvYacGwB0Rq0OSC4ehwkBE0lygnsAAAAASUVORK5CYII=",
      owner: "Steve",
      members: ["Steve", "Alex", "西瓜冰", "我"],
      unread: 2,
      messages: [
        { sender: "other", name: "Steve", time: "10:02", content: "@我 今晚打末影龙吗?", mention: true },
        { sender: "other", name: "Alex", time: "10:05", content: "带上我,我准备了 20 瓶药水。", mention: false },
        { sender: "mine", name: "我", time: "10:06", content: "行,八点集合,先到先得。", mention: false },
        { sender: "other", name: "西瓜冰", time: "10:11", content: "@我 记得多带点末影珍珠。", mention: true }
      ]
    },
    {
      id: "r2",
      name: "QTE 挑战组",
      owner: "我",
      members: ["我", "矿工老张"],
      unread: 1,
      messages: [
        { sender: "other", name: "矿工老张", time: "09:15", content: "@我 昨晚那个连打 QTE 你过了吗?", mention: true },
        { sender: "mine", name: "我", time: "09:20", content: "过了,手都要按断了。", mention: false }
      ]
    }
  ],
  tasks: [
    { id: "t1", name: "完成 3 次 QTE 挑战", desc: "参与并完成任意 QTE 挑战", cur: 2, goal: 3, state: "active", reward: "50 金" },
    { id: "t2", name: "发送 5 条群聊消息", desc: "在群聊频道中发送消息", cur: 5, goal: 5, state: "claimable", reward: "10 金" },
    { id: "t3", name: "添加 1 位好友", desc: "通过附近玩家列表添加好友", cur: 1, goal: 1, state: "claimed", reward: "经验奖励" },
    { id: "t4", name: "使用 ID 卡开门 10 次", desc: "用身份卡通过任意门禁", cur: 3, goal: 10, state: "active", reward: "80 金" }
  ]
};

// 玩家信息(正式接入时来自服务端同步包 + Lightman's Currency 余额 + ID 卡部门)
var mockPlayer = {
  id: "Melon_0624",
  balance: 1234,
  dept: "科研部门",
  level: 3
};

var deptList = ["管理部门", "科研部门", "安保部门", "后勤部门", "D级部门"];

// 各部门任务池(正式接入时按玩家 ID 卡部门选取,每日 06:00 刷新)
var mockTaskPools = {
  "管理部门": [
    { id: "m1", name: "审批 3 张门禁申请", desc: "在门禁终端完成权限审批", cur: 1, goal: 3, state: "active", reward: "60 金" },
    { id: "m2", name: "主持 1 场部门会议", desc: "在群聊频道发送 5 条消息", cur: 3, goal: 5, state: "active", reward: "30 金" },
    { id: "m3", name: "处理 2 起异常事件", desc: "完成任意 QTE 挑战", cur: 2, goal: 2, state: "claimable", reward: "50 金" }
  ],
  "科研部门": [
    { id: "r1", name: "完成 2 次校准 QTE", desc: "参与并完成校准类 QTE 挑战", cur: 1, goal: 2, state: "active", reward: "50 金" },
    { id: "r2", name: "破译 3 组密码", desc: "完成密码类 QTE 挑战", cur: 0, goal: 3, state: "active", reward: "40 金" },
    { id: "r3", name: "记录 10 条实验数据", desc: "发送 10 条群聊消息", cur: 10, goal: 10, state: "claimable", reward: "30 金" },
    { id: "r4", name: "合作用户测试", desc: "添加 1 位好友", cur: 0, goal: 1, state: "active", reward: "经验奖励" }
  ],
  "安保部门": [
    { id: "s1", name: "完成 4 次战斗训练", desc: "完成连打类 QTE 挑战", cur: 1, goal: 4, state: "active", reward: "70 金" },
    { id: "s2", name: "检查 5 处门禁", desc: "用身份卡通过任意门禁", cur: 5, goal: 5, state: "claimable", reward: "50 金" },
    { id: "s3", name: "上报巡逻情况", desc: "在群聊频道发送 3 条消息", cur: 0, goal: 3, state: "active", reward: "20 金" }
  ],
  "后勤部门": [
    { id: "l1", name: "采集 16 份基础物资", desc: "破坏并收集基础资源方块", cur: 6, goal: 16, state: "active", reward: "40 金" },
    { id: "l2", name: "建造 8 个设施方块", desc: "放置任意设施相关方块", cur: 0, goal: 8, state: "active", reward: "60 金" },
    { id: "l3", name: "完成 2 次体能测试", desc: "完成任意 QTE 挑战", cur: 2, goal: 2, state: "claimable", reward: "30 金" }
  ],
  "D级部门": [
    { id: "d1", name: "完成 2 次服从测试", desc: "完成任意 QTE 挑战", cur: 0, goal: 2, state: "active", reward: "20 金" },
    { id: "d2", name: "搬运 6 份物资", desc: "采集基础资源方块", cur: 0, goal: 6, state: "active", reward: "15 金" },
    { id: "d3", name: "保持通讯 1 次", desc: "发送 1 条群聊消息", cur: 1, goal: 1, state: "claimable", reward: "5 金" }
  ]
};

var state = {
  page: "home",
  commTab: "contacts",
  currentRoomId: null,
  currentGameId: null,
  myName: "我",
  iconPickTarget: "create"
};

// ---- 群聊头像选择(桥接模式由 Java 下发物品列表,预览用模拟列表) ----
var roomIcon = "";
var iconPage = 0;
var iconSearch = "";
var iconTotalPages = 1;
var mockIconItems = [
  { id: "minecraft:diamond_sword", name: "钻石剑", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAAJFBMVEUAAAAOPzYIJSAz68uk/fArx6weincVY1VoTh5JNhUoHguJZycazUdMAAAAAXRSTlMAQObYZgAAAExJREFUGNN1jsENwDAMAgGndpLuv29/jZFa/+4QwkA/ks5jyPky8cHxwyQAxslZNAaYiux9Zlb4A0XjNcVu1r0hvYacGwB0Rq0OSC4ehwkBE0lygnsAAAAASUVORK5CYII=" },
  { id: "minecraft:apple", name: "苹果", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAIVBMVEUAAAD/lp3/Xmn/HCvdFyV+Nw60Ex51KAKcEBdUJAlUCQ5dkieqAAAAAXRSTlMAQObYZgAAAFxJREFUeNpjQAHsMEYkjFEAoTjapxeA6cTMlnQQq81IUMisHMQwNjZ2TgcxnJ1dnL2AjLIUt5RkMCMtzS05C2Rsmlua2wIQoy3NJQPEYFiR0dbFAAJc5VULkFwAACePFkH36dcVAAAAAElFTkSuQmCC" },
  { id: "minecraft:redstone", name: "红石粉", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAGFBMVEUAAAD/AACqDwFyAABcBwBBBQA0BgUtAAChZ6iRAAAAAXRSTlMAQObYZgAAAElJREFUeNpjwA9YHKAMZxWogKJqAZjhJGQYDhZQUlR1BQk5C5kKB6WDZIyETENAcqWKykGhIBH2IFPXMLC28tSwcgYwKC/HaiEAtf4MFvnbFVwAAAAASUVORK5CYII=" },
  { id: "minecraft:iron_ingot", name: "铁锭", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAhElEQVQ4y9WSwQnAIBAEbcpefFmEJViNJdmNYQ82OeVUzCdkYfEgzhhinPtNYowNfQWhpRRpSqkdQbXWrqZkBy0lgJAdxDKYRcJTVxIdgmC8949glIxQzllAFLMpoMSCWL33FtDID7gC8QygwPoWKLFeV0MdOF4lJVhDCHto9hMdQZ/nAqzufev6TWmcAAAAAElFTkSuQmCC" },
  { id: "minecraft:diamond", name: "钻石", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAIVBMVEUAAAD////V//ah++hK7dks4NggxbUaqqcckZoRcnoUXlN/hNeyAAAAAXRSTlMAQObYZgAAAF1JREFUeNpjwApmzoTQnIJCE8CMiS5GWWABEWNltwVghpCQC4gx0dHFuKwKxHByNm4DM8SMTdpBjEku5eVpXUAGl3taWtkCECM8La0YxGBY4WKcBTaZa9WqBUg2AwByXRcdUQ3W3QAAAABJRU5ErkJggg==" },
  { id: "minecraft:gold_ingot", name: "金锭", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAG1BMVEUAAAD//////eD99V/61krpsRXclhOyZBF1KALSidGuAAAAAXRSTlMAQObYZgAAAFpJREFUeNpjwAHKIRR7uUsBmFtsbGwO4jobGxubNDAUGYOAkgVDibKxsZGSmQdDiZGxknJYGpBhImSalhbiwVBqAqYbGNhDTNNCOxoYGBhKXSM6IOZWNGC3FwCLuxUEBRwUswAAAABJRU5ErkJggg==" },
  { id: "minecraft:ender_pearl", name: "末影珍珠", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAIVBMVEUAAACM9OIszbE0mYglhHQbe2sQXlELTUIMNzAGOTEDJiC5a2thAAAAAXRSTlMAQObYZgAAAFtJREFUeNpjwAo4OqB0swmE1WyS5rIAJGAiVJ7mBWIYas4szwLJOM1aNb0MKLc4HchwAzKWgBhmQAaXe+XMIisGIMOtvFwRpJhhabozUAYktCxtFZACsVYh2wwA2BYa2KT1S0MAAAAASUVORK5CYII=" },
  { id: "minecraft:bread", name: "面包", icon: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAGFBMVEUAAAC8iSeieSSMZh5lSxdXQRRMOBE/Lg5TtFIkAAAAAXRSTlMAQObYZgAAAF9JREFUeNpVyTEOgCAQBMArwH7hBXAvQIi15kj8iIktnd+XOyu32c0s/eL6/rXkZkMAHgqhIuo4EIqKA5DaHMIFcRv6MOo5n4IQ9XEJBuQyDMivbEBe6gTN1W+yLI/BC4w6Di3W9CwdAAAAAElFTkSuQmCC" }
];

// 隐藏滚动条的容器:滚轮事件接管滚动(仅在内容确实超高时滚动,并严格 clamp,防止画面偏移)
function bindHiddenScroll(selector) {
  var nodes = document.querySelectorAll(selector);
  for (var i = 0; i < nodes.length; i++) {
    (function (node) {
      node.addEventListener("wheel", function (event) {
        var d = event.deltaY || 0;
        if (d === 0) { return; }
        // 内容不超高(无滚动范围)时完全不滚动,避免异常 scrollHeight 导致画面偏移
        var sh = node.scrollHeight || 0;
        var ch = node.clientHeight || 0;
        var max = sh - ch;
        if (max <= 0) { return; }
        event.preventDefault();
        var next = (node.scrollTop || 0) + d;
        node.scrollTop = Math.max(0, Math.min(next, max));
      });
    })(nodes[i]);
  }
}

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

// 每 0.5s 轮询:服务端数据变化 → 应用;动作队列被 Java 消费 → 清空
var bridgeLastLb = "";
var bridgeLastIcon = "";
var bridgePollCounter = 0;
function bridgePoll() {
  var dataEl = document.getElementById("phoneData");
  if (dataEl) {
    var t = dataEl.textContent || "";
    if (t !== bridgeLastData) {
      bridgeLastData = t;
      if (t) { applyBridgeData(t); }
    }
  }
  var lbEl = document.getElementById("leaderboardData");
  if (lbEl) {
    var lt = lbEl.textContent || "";
    if (lt !== bridgeLastLb) {
      bridgeLastLb = lt;
      if (lt) { applyLeaderboardData(lt); }
    }
  }
  var iconEl = document.getElementById("iconPickerData");
  if (iconEl) {
    var it = iconEl.textContent || "";
    if (it !== bridgeLastIcon) {
      bridgeLastIcon = it;
      if (it) { applyIconData(it); }
    }
  }
  var qEl = document.getElementById("melonActionQueue");
  if (qEl && actionQueue.length > 0) {
    var v = qEl.value || "";
    if (v === "") { actionQueue = []; }
  }
  // 每 3 秒重试一次未加载成功的头像
  bridgePollCounter++;
  if (bridgePollCounter % 6 === 0) { refreshAvatars(); }
}

// 页面操作 → 写入动作队列,Java 轮询消费后发网络包
function pushAction(action) {
  actionQueue.push(action);
  var qEl = document.getElementById("melonActionQueue");
  if (qEl) { qEl.value = JSON.stringify(actionQueue); }
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
    mockPlayer.dept = data.player.dept || mockPlayer.dept;
    if (typeof data.player.level === "number") { mockPlayer.level = data.player.level; }
    mockPlayer.balance = data.player.balance;
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
    mock.nearby = [];
    for (var k = 0; k < data.nearby.length; k++) {
      var n = data.nearby[k];
      mock.nearby.push({ uuid: n.uuid, name: n.name, dist: parseFloat(n.dist) || 0, isFriend: isBridgeFriend(n.uuid) });
    }
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
      mock.rooms.push({ id: room.id, name: room.name, avatar: room.avatar || "", owner: room.ownerName || "?", isOwner: room.isOwner === true, members: members, unread: room.unread || 0, messages: messages });
    }
  }
  if (data.tasks) {
    bridgeTasks = [];
    for (var ti = 0; ti < data.tasks.length; ti++) {
      var t = data.tasks[ti];
      bridgeTasks.push({ id: t.id, name: t.name, desc: t.desc, cur: t.cur, goal: t.goal, state: t.state, reward: t.reward });
    }
  }
  renderPlayerInfo();
  renderContacts();
  renderNearby();
  renderRooms();
  renderTasks();
  renderImportant();
  renderDept();
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

function el(tag, className, text) {
  var node = document.createElement(tag);
  if (className) { node.className = className; }
  if (text !== undefined && text !== null) { node.textContent = "" + text; }
  return node;
}

function clearNode(node) {
  while (node.firstChild) { node.removeChild(node.firstChild); }
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
var NAV_KEYS = { home: 0, comm: 1, log: 2, dept: 3, fun: 4 };

function gotoPage(page) {
  state.page = page;
  var pages = ["home", "comm", "chat", "log", "dept", "fun", "game"];
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
  if (page === "chat") { scrollChatToBottom(); }
  // 离开游戏页时停止贪吃蛇计时器
  if (page !== "game" && snake && snake.timer) {
    clearInterval(snake.timer);
    snake.running = false;
  }
}

// ---- 通讯板块内 tab ----
function applyCommTab() {
  var tabs = document.getElementsByClassName("comm-tab");
  for (var i = 0; i < tabs.length; i++) {
    if (tabs[i].getAttribute("data-comm-tab") === state.commTab) {
      tabs[i].className = "comm-tab comm-tab-active";
    } else {
      tabs[i].className = "comm-tab";
    }
  }
  var panels = ["contacts", "nearby", "rooms"];
  for (var j = 0; j < panels.length; j++) {
    var panel = $("comm-" + panels[j]);
    if (panels[j] === state.commTab) { panel.className = "comm-panel"; }
    else { panel.className = "comm-panel comm-panel-hidden"; }
  }
  // 切 tab 时刷新对应列表
  if (state.commTab === "contacts") { renderContacts(); }
  else if (state.commTab === "nearby") { renderNearby(); }
  else if (state.commTab === "rooms") { renderRooms(); }
}

function switchCommTab(tab) {
  state.commTab = tab;
  gotoPage("comm");
}

// ---- 时钟 ----
function tickClock() {
  var t = fmtTime();
  $("sysTime").textContent = t;
  $("homeTime").textContent = t;
  // 附近刷新冷却倒计时
  if (nearbyCd > 0) {
    nearbyCd--;
    updateNearbyBtn();
  }
}

function tickDate() {
  $("homeDate").textContent = fmtDate();
  $("sysDate").textContent = fmtDateShort();
  $("logDate").textContent = fmtDateShort();
}

// ---- 联系人渲染 ----
// 玩家头像:皮肤 PNG 覆盖首字文字,图片缺失时显示文字
function buildAvatar(uuid, name, avatarSrc, cls) {
  var box = el("div", cls ? "avatar " + cls : "avatar");
  box.appendChild(el("span", "avatar-text", name.charAt(0)));
  var src = avatarSrc || (uuid ? "avatars/" + uuid + ".png" : null);
  if (src) {
    var img = el("img", "avatar-img");
    img.setAttribute("data-src", src);
    img.src = src;
    img.addEventListener("error", function () { setStyle(img, { display: "none" }); });
    box.appendChild(img);
  }
  return box;
}

// 头像 PNG 由 Java 异步生成,桥接模式下轮询重试未加载成功的图片
function refreshAvatars() {
  var imgs = document.querySelectorAll(".avatar-img, .icon-cell-img");
  for (var i = 0; i < imgs.length; i++) {
    var img = imgs[i];
    if (img.complete && img.naturalWidth > 0) { continue; }
    var tries = parseInt(img.getAttribute("data-tries") || "0", 10);
    if (tries >= 5) { setStyle(img, { display: "none" }); continue; }
    img.setAttribute("data-tries", "" + (tries + 1));
    img.src = (img.getAttribute("data-src") || "") + "?t=" + tries;
  }
}

function renderContacts() {
  renderRequests();
  renderFriends();
  updateHomeBadges();
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
  renderContacts();
  renderNearby();
}

function rejectRequest(uuid, name) {
  for (var i = mock.pending.length - 1; i >= 0; i--) {
    if (mock.pending[i].uuid === uuid) { mock.pending.splice(i, 1); }
  }
  pushAction({ t: "friend_reject", u: uuid });
  renderContacts();
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
  var row = el("div", "phone-list-item");
  row.appendChild(buildAvatar(f.uuid, f.name, f.avatar, "avatar-green"));
  var main = el("div", "item-main");
  main.appendChild(el("div", "item-name", f.name));
  main.appendChild(el("div", "item-sub", f.online ? "在线" : "离线"));
  row.appendChild(main);
  var actions = el("div", "item-actions");
  var rm = el("button", "mini-btn mini-btn-danger", "移除");
  rm.addEventListener("click", function () { removeFriend(f.uuid, f.name); });
  actions.appendChild(rm);
  row.appendChild(actions);
  return row;
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
  renderContacts();
  renderNearby();
}

// ---- 附近玩家 ----
// 刷新冷却(秒),点击后 10 秒内不可再次点击
var nearbyCd = 0;

function tryRefreshNearby() {
  if (nearbyCd > 0) return;
  nearbyCd = 10;
  updateNearbyBtn();
  pushAction({ t: "nearby_refresh" });
  renderNearby();
}

function updateNearbyBtn() {
  var btn = $("btnRefreshNearby");
  if (!btn) return;
  if (nearbyCd > 0) {
    btn.disabled = true;
    setStyle(btn, { opacity: "0.45" });
    btn.textContent = "冷却中 (" + nearbyCd + "s)";
  } else {
    btn.disabled = false;
    setStyle(btn, { opacity: "1" });
    btn.textContent = "刷新";
  }
}

function renderNearby() {
  var list = $("nearbyList");
  clearNode(list);
  if (mock.nearby.length === 0) {
    list.appendChild(el("div", "empty-tip", "附近没有其他玩家"));
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
  renderNearby();
  renderContacts();
}


// ---- 群聊 ----
function renderRooms() {
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

// 房间头像:物品图标 PNG 覆盖默认"群"字
function buildRoomAvatar(room) {
  var box = el("div", "avatar");
  box.appendChild(el("span", "avatar-text", "群"));
  if (room.avatar && (bridgeMode || typeof room.icon === "string")) {
    var src = bridgeMode ? "icons/" + room.avatar.replace(/:/g, "_") + ".png" : room.icon;
    var img = el("img", "avatar-img");
    img.setAttribute("data-src", src);
    img.src = src;
    img.addEventListener("error", function () { setStyle(img, { display: "none" }); });
    box.appendChild(img);
  }
  return box;
}

function enterRoom(roomId) {
  state.currentRoomId = roomId;
  var room = findRoom(roomId);
  if (!room) { return; }
  room.unread = 0;
  $("chatTitle").textContent = room.name;
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

function scrollChatToBottom() {
  var box = $("chatMessages");
  box.scrollTop = box.scrollHeight;
}

function sendMessage() {
  var input = $("chatInput");
  var text = input.value;
  if (!text || !text.trim()) { return; }
  var room = findRoom(state.currentRoomId);
  if (!room) { return; }
  room.messages.push({ sender: "mine", name: state.myName, time: fmtTime(), content: text.trim(), mention: text.indexOf("@") >= 0 });
  input.value = "";
  var box = $("chatMessages");
  box.appendChild(buildMessage(room.messages[room.messages.length - 1]));
  scrollChatToBottom();
  pushAction({ t: "chat_send", roomId: room.id, content: text.trim() });
}

function createRoom() {
  var input = $("roomNameInput");
  var name = input.value;
  if (!name || !name.trim()) { return; }
  mock.rooms.push({
    id: "r" + Date.now(),
    name: name.trim(),
    avatar: roomIcon,
    owner: state.myName,
    isOwner: true,
    members: [state.myName],
    messages: []
  });
  input.value = "";
  closeModal("newRoomModal");
  renderRooms();
  pushAction({ t: "room_create", name: name.trim(), avatar: roomIcon || "" });
  // 重置头像选择
  roomIcon = "";
  resetRoomAvatarPreview();
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
  $("renameInput").value = room.name;
  renderSettingsAvatar(room);
  resetDissolveBtn();
  openModal("roomSettingsModal");
}

function renderSettingsAvatar(room) {
  var box = $("roomSettingsAvatar");
  clearNode(box);
  box.appendChild(el("span", "avatar-text", "群"));
  if (room.avatar && (bridgeMode || typeof room.icon === "string")) {
    var src = bridgeMode ? "icons/" + room.avatar.replace(/:/g, "_") + ".png" : room.icon;
    var img = el("img", "avatar-img");
    img.setAttribute("data-src", src);
    img.src = src;
    img.addEventListener("error", function () { setStyle(img, { display: "none" }); });
    box.appendChild(img);
  }
}

function changeRoomAvatar() {
  state.iconPickTarget = "room";
  openIconPicker();
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
  input.value = "";
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

// ---- 头像选择器 ----
function openIconPicker() {
  iconPage = 0;
  iconSearch = "";
  $("iconSearchInput").value = "";
  if (bridgeMode) {
    pushAction({ t: "icon_pick_page", page: iconPage, search: iconSearch });
  } else {
    renderIconGrid(mockIconItems, 0, 1);
  }
  openModal("iconPickerModal");
}

function searchIcons() {
  iconSearch = $("iconSearchInput").value;
  iconPage = 0;
  if (bridgeMode) {
    pushAction({ t: "icon_pick_page", page: iconPage, search: iconSearch });
  } else {
    var filtered = [];
    var key = iconSearch.trim().toLowerCase();
    for (var i = 0; i < mockIconItems.length; i++) {
      var it = mockIconItems[i];
      if (!key || it.name.toLowerCase().indexOf(key) >= 0 || it.id.toLowerCase().indexOf(key) >= 0) {
        filtered.push(it);
      }
    }
    renderIconGrid(filtered, 0, 1);
  }
}

function flipIconPage(delta) {
  var np = iconPage + delta;
  if (np < 0 || np >= iconTotalPages) { return; }
  iconPage = np;
  if (bridgeMode) {
    pushAction({ t: "icon_pick_page", page: iconPage, search: iconSearch });
  }
}

// Java 下发的物品分页列表
function applyIconData(jsonText) {
  var data;
  try { data = JSON.parse(jsonText); } catch (err) { return; }
  iconPage = data.page || 0;
  iconTotalPages = data.totalPages || 1;
  renderIconGrid(data.items || [], iconPage, iconTotalPages);
}

function renderIconGrid(items, page, totalPages) {
  iconTotalPages = totalPages;
  var grid = $("iconGrid");
  clearNode(grid);
  if (!items || items.length === 0) {
    grid.appendChild(el("div", "empty-tip", "没有匹配的物品"));
    $("iconPageInfo").textContent = "0 / 0";
    return;
  }
  for (var i = 0; i < items.length; i++) {
    grid.appendChild(buildIconItem(items[i]));
  }
  $("iconPageInfo").textContent = (page + 1) + " / " + totalPages;
}

function buildIconItem(it) {
  var cell = el("div", "icon-cell");
  var box = el("div", "icon-cell-box");
  box.appendChild(el("span", "icon-cell-text", it.name.charAt(0)));
  // 桥接模式由 Java 生成 PNG 文件;预览用内嵌贴图 data URI
  var hasSrc = bridgeMode || typeof it.icon === "string";
  if (hasSrc) {
    var src = bridgeMode ? "icons/" + it.id.replace(/:/g, "_") + ".png" : it.icon;
    var img = el("img", "icon-cell-img");
    img.setAttribute("data-src", src);
    img.src = src;
    img.addEventListener("error", function () { setStyle(img, { display: "none" }); });
    box.appendChild(img);
  }
  cell.appendChild(box);
  cell.addEventListener("click", function () { selectRoomIcon(it); });
  return cell;
}

function selectRoomIcon(it) {
  // 群聊设置里换头像:直接应用到当前房间
  if (state.iconPickTarget === "room") {
    var room = findRoom(state.currentRoomId);
    if (room) {
      room.avatar = it.id;
      if (typeof it.icon === "string") { room.icon = it.icon; }
      renderSettingsAvatar(room);
      renderRooms();
      pushAction({ t: "room_set_avatar", roomId: room.id, avatar: it.id });
    }
    closeModal("iconPickerModal");
    return;
  }
  roomIcon = it.id;
  var box = $("roomAvatarPreview");
  clearNode(box);
  box.appendChild(el("span", "avatar-text", it.name.charAt(0)));
  if (bridgeMode || typeof it.icon === "string") {
    var src = bridgeMode ? "icons/" + it.id.replace(/:/g, "_") + ".png" : it.icon;
    var img = el("img", "avatar-img");
    img.setAttribute("data-src", src);
    img.src = src;
    img.addEventListener("error", function () { setStyle(img, { display: "none" }); });
    box.appendChild(img);
  }
  closeModal("iconPickerModal");
}

function resetRoomAvatarPreview() {
  var box = $("roomAvatarPreview");
  clearNode(box);
  box.appendChild(el("span", "avatar-text", "群"));
}

// ---- 工作日志 ----
function getCurrentTasks() {
  if (bridgeMode) { return bridgeTasks; }
  var pool = mockTaskPools[mockPlayer.dept];
  return pool ? pool : [];
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
  var items = [];
  for (var i = 0; i < mock.rooms.length; i++) {
    var room = mock.rooms[i];
    for (var j = 0; j < room.messages.length; j++) {
      var msg = room.messages[j];
      if (msg.mention && msg.sender !== "mine") {
        items.push({ room: room, msg: msg });
      }
    }
  }
  $("impCount").textContent = "" + items.length;
  if (items.length === 0) {
    list.appendChild(el("div", "imp-empty", "暂无 @ 消息"));
    return;
  }
  var max = Math.min(3, items.length);
  for (var k = 0; k < max; k++) {
    list.appendChild(buildImportantItem(items[items.length - 1 - k]));
  }
}

function buildImportantItem(item) {
  var row = el("div", "imp-item");
  row.appendChild(el("span", "imp-room", item.room.name));
  row.appendChild(el("div", "imp-content", item.msg.name + ": " + item.msg.content));
  row.appendChild(el("span", "imp-time", item.msg.time));
  if (item.room.unread > 0) {
    row.appendChild(el("span", "imp-dot"));
  }
  row.addEventListener("click", function () { enterRoom(item.room.id); });
  return row;
}


// ---- 部门工作需求数据(占位内容,后续填充) ----
var mockDeptData = {
  "管理部门": {
    desc: "负责设施运行秩序与人员调配。",
    requests: [
      { title: "门禁权限审批", desc: "处理积压的门禁申请", tag: "待处理" },
      { title: "设施巡查排班", desc: "制定本周巡查轮值表", tag: "进行中" },
      { title: "应急演练筹备", desc: "筹备季度安全演练", tag: "待处理" }
    ]
  },
  "科研部门": {
    desc: "负责实验项目推进与异常研究。",
    requests: [
      { title: "实验设备校准", desc: "校准实验室精密仪器", tag: "进行中" },
      { title: "样本数据分析", desc: "整理上周实验数据", tag: "待处理" },
      { title: "新项目立项申请", desc: "提交下一阶段研究计划", tag: "待处理" }
    ]
  },
  "安保部门": {
    desc: "负责设施安全与人员管控。",
    requests: [
      { title: "巡逻路线检查", desc: "巡检设施重点区域", tag: "进行中" },
      { title: "门禁状态排查", desc: "排查异常门禁记录", tag: "待处理" },
      { title: "收容室例行检查", desc: "检查收容单元状态", tag: "待处理" }
    ]
  },
  "后勤部门": {
    desc: "负责设施物资供应与维护。",
    requests: [
      { title: "物资库存盘点", desc: "盘点仓库库存并上报", tag: "进行中" },
      { title: "设施修缮工单", desc: "修复损坏的设施设备", tag: "待处理" },
      { title: "补给运输计划", desc: "制定本周补给运输计划", tag: "待处理" }
    ]
  },
  "D级部门": {
    desc: "服从设施管理安排,完成基础任务。",
    requests: [
      { title: "日常训练", desc: "参加部门组织的日常训练", tag: "进行中" },
      { title: "区域清洁", desc: "完成指定区域的清洁工作", tag: "待处理" },
      { title: "行为规范学习", desc: "学习设施行为规范条例", tag: "待处理" }
    ]
  }
};

// ---- 部门板块渲染 ----
function renderDept() {
  var body = $("deptBody");
  clearNode(body);
  $("deptCurrentBadge").textContent = mockPlayer.dept;
  var data = mockDeptData[mockPlayer.dept];
  if (!data) {
    body.appendChild(el("div", "empty-tip", "未持卡 · 无法查看部门信息"));
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
  ext.appendChild(el("div", "dept-extend-hint", "更多部门模块待接入"));
  body.appendChild(ext);
}

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
var MS_DIFF = { easy: [8, 10], normal: [12, 30], hard: [16, 60] };
var MM_DIFF = { easy: [4, 4], normal: [5, 4], hard: [6, 4] };
var G_DIFF = { easy: 4, normal: 5, hard: 6 };
var SN_DIFF = { easy: 220, normal: 160, hard: 110 };

// 成绩列名与排序方向(asc=true 越小越好)
var gameMeta = {
  minesweeper: { scoreLabel: "用时", asc: true },
  memory: { scoreLabel: "步数", asc: true },
  g2048: { scoreLabel: "分数", asc: false },
  snake: { scoreLabel: "分数", asc: false }
};

// ---- 模拟全服排行榜(正式接入时由服务端下发) ----
var lbNames = ["Steve", "Alex", "西瓜冰", "矿工老张", "末影小灵", "Herobrine", "熔岩行者"];

function genLB(base, gap, desc) {
  var arr = [];
  for (var i = 0; i < lbNames.length; i++) {
    arr.push({ name: lbNames[i], score: base + i * gap, isMe: false });
  }
  if (desc) { arr.reverse(); }
  return arr;
}

var mockLeaderboards = {
  minesweeper: {
    easy: genLB(24, 7),
    normal: genLB(95, 14),
    hard: genLB(260, 28)
  },
  memory: {
    easy: genLB(12, 2),
    normal: genLB(21, 3),
    hard: genLB(34, 4)
  },
  g2048: {
    easy: genLB(4096, 512, true),
    normal: genLB(8192, 1024, true),
    hard: genLB(16384, 2048, true)
  },
  snake: {
    easy: genLB(90, 15, true),
    normal: genLB(240, 35, true),
    hard: genLB(480, 60, true)
  }
};

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
  clearNode(list);
  for (var i = 0; i < mockGames.length; i++) {
    list.appendChild(buildGameCard(mockGames[i]));
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

function startMinesweeper() {
  var d = MS_DIFF[currentDifficulty] || [8, 10];
  var size = d[0];
  var mineTotal = d[1];
  ms = { size: size, mines: [], revealed: [], flags: [], over: false, startTime: Date.now() };
  // 随机布雷
  var count = 0;
  while (count < mineTotal) {
    var idx = Math.floor(Math.random() * size * size);
    if (ms.mines.indexOf(idx) < 0) {
      ms.mines.push(idx);
      count++;
    }
  }
  for (var i = 0; i < size * size; i++) {
    ms.revealed.push(false);
    ms.flags.push(false);
  }
  $("msResult").textContent = "";
  $("msResult").className = "game-result";
  renderMsGrid();
}

function msNeighbors(idx) {
  var size = ms.size;
  var r = Math.floor(idx / size);
  var c = idx % size;
  var out = [];
  for (var dr = -1; dr <= 1; dr++) {
    for (var dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) { continue; }
      var nr = r + dr;
      var nc = c + dc;
      if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
        out.push(nr * size + nc);
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
  ms.revealed[idx] = true;
  if (ms.mines.indexOf(idx) >= 0) {
    ms.over = true;
    for (var i = 0; i < ms.mines.length; i++) { ms.revealed[ms.mines[i]] = true; }
    $("msResult").textContent = "失败";
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
  if (!ms.over && revealedTotal === ms.size * ms.size - ms.mines.length) {
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
  clearNode(grid);
  grid.className = "ms-grid ms-" + ms.size;
  var flagged = 0;
  for (var i = 0; i < ms.flags.length; i++) {
    if (ms.flags[i]) { flagged++; }
  }
  $("msMineStat").textContent = "" + (ms.mines.length - flagged);
  for (var idx = 0; idx < ms.size * ms.size; idx++) {
    (function (i) {
      var cell = el("div", "ms-cell");
      if (ms.revealed[i]) {
        cell.className = "ms-cell ms-cell-open";
        if (ms.mines.indexOf(i) >= 0) {
          cell.className = "ms-cell ms-cell-open ms-cell-mine";
          cell.textContent = "●";
        } else {
          var n = msMineCount(i);
          if (n > 0) {
            cell.textContent = "" + n;
            if (n === 1) { cell.className += " ms-cell-n1"; }
            else if (n === 2) { cell.className += " ms-cell-n2"; }
            else if (n === 3) { cell.className += " ms-cell-n3"; }
            else { cell.className += " ms-cell-n4"; }
          }
        }
      } else if (ms.flags[i]) {
        cell.className = "ms-cell ms-cell-flag";
        cell.textContent = "⚑";
      }
      cell.addEventListener("click", function () { msReveal(i); });
      // 右键插旗(mousedown button=2,AUI 鼠标事件带 button 属性)
      cell.addEventListener("mousedown", function (event) {
        if (event.button === 2) { msToggleFlag(i); }
      });
      grid.appendChild(cell);
    })(idx);
  }
}

/* ---- 记忆翻牌 ---- */
var mm = null;
var mmSymbols = ["△", "□", "○", "◇", "★", "▲", "■", "●", "◆", "◎", "♢", "♧"];

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
  clearNode(grid);
  grid.className = "mm-grid mm-" + mm.cols;
  $("mmSteps").textContent = "步数 " + mm.steps;
  for (var i = 0; i < mm.cards.length; i++) {
    (function (idx) {
      var card = el("div", "mm-card");
      if (mm.done.indexOf(idx) >= 0) {
        card.className = "mm-card mm-card-flipped mm-card-done";
        card.textContent = mm.cards[idx];
      } else if (mm.flipped.indexOf(idx) >= 0) {
        card.className = "mm-card mm-card-flipped";
        card.textContent = mm.cards[idx];
      } else {
        card.textContent = "?";
      }
      card.addEventListener("click", function () { mmFlip(idx); });
      grid.appendChild(card);
    })(i);
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
  clearNode(grid);
  grid.className = "g2048-grid g2-" + g2048.size;
  $("g2048Score").textContent = "得分 " + g2048.score;
  for (var r = 0; r < g2048.size; r++) {
    for (var c = 0; c < g2048.size; c++) {
      var v = g2048.board[r][c];
      var cell = el("div", "g2048-cell");
      if (v > 0) {
        cell.textContent = "" + v;
        if (v <= 2048) { cell.className = "g2048-cell g2048-t" + v; }
      }
      grid.appendChild(cell);
    }
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
  var canvas = document.createElement("canvas");
  canvas.id = "snakeCanvas";
  canvas.width = 200;
  canvas.height = 200;
  canvas.className = "snake-canvas";
  box.appendChild(canvas);
  box.appendChild(el("div", "game-stat game-hint", "方向键 / WASD / 小键盘转向"));
  toggle.addEventListener("click", function () {
    if (!snake) { return; }
    if (snake.running) {
      snake.running = false;
      clearInterval(snake.timer);
      toggle.textContent = "开始";
    } else {
      snake.running = true;
      toggle.textContent = "暂停";
      snake.timer = setInterval(snakeTick, 180);
    }
  });
  restart.addEventListener("click", startSnake);
  startSnake();
}

function startSnake() {
  if (snake && snake.timer) { clearInterval(snake.timer); }
  snake = { size: 16, dir: { x: 1, y: 0 }, nextDir: { x: 1, y: 0 }, body: [{x:7,y:8},{x:6,y:8},{x:5,y:8}], food: null, score: 0, running: true, timer: null };
  $("snakeResult").textContent = "";
  $("snakeResult").className = "game-result";
  var btn = $("snakeToggleBtn");
  if (btn) { btn.textContent = "暂停"; }
  spawnSnakeFood();
  renderSnake();
  snake.timer = setInterval(snakeTick, SN_DIFF[currentDifficulty] || 160);
}

function snakeTick() {
  if (!snake || !snake.running) { return; }
  snake.dir = snake.nextDir;
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
}

function snakeEnd() {
  snake.running = false;
  if (snake.timer) { clearInterval(snake.timer); }
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
  var canvas = $("snakeCanvas");
  if (!canvas || !snake) { return; }
  var ctx = canvas.getContext("2d");
  var cell = 12.5;
  ctx.fillStyle = "#0f0f13";
  ctx.fillRect(0, 0, 200, 200);
  if (snake.food) {
    ctx.fillStyle = "#c1272d";
    ctx.fillRect(snake.food.x * cell, snake.food.y * cell, cell - 1, cell - 1);
  }
  for (var i = 0; i < snake.body.length; i++) {
    ctx.fillStyle = i === 0 ? "#e6e6ea" : "#9fbf8f";
    ctx.fillRect(snake.body[i].x * cell, snake.body[i].y * cell, cell - 1, cell - 1);
  }
  $("snakeScore").textContent = "分数 " + snake.score;
}

function setSnakeDir(x, y) {
  if (!snake) { return; }
  if (snake.dir.x === -x && snake.dir.y === -y) { return; }
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
  $("playerId").textContent = mockPlayer.id;
  $("playerDept").textContent = mockPlayer.dept;
  $("playerLevel").textContent = levelRoman(mockPlayer.level);
  $("playerBalance").textContent = fmtBalance(mockPlayer.balance);
}

function fmtBalance(v) {
  if (typeof v === "number") { return fmtNum(v); }
  return "" + v;
}

// 权限等级罗马数字标记(1-5,超出显示 V+,无卡显示 -)
var ROMAN_LEVELS = ["", "I", "II", "III", "IV", "V"];
function levelRoman(n) {
  if (n >= 1 && n <= 5) { return ROMAN_LEVELS[n]; }
  if (n > 5) { return "V+"; }
  return "-";
}

// 原型演示:点击部门循环切换,看不同部门任务池(正式接入后删除)
function cycleDepartment() {
  var idx = deptList.indexOf(mockPlayer.dept);
  mockPlayer.dept = deptList[(idx + 1) % deptList.length];
  renderPlayerInfo();
  renderTasks();
  renderDept();
  updateHomeBadges();
}

// ---- 主屏统计 ----
function updateHomeBadges() {
  var tasks = getCurrentTasks();
  var logDone = 0;
  for (var i = 0; i < tasks.length; i++) {
    if (tasks[i].state === "claimed") { logDone++; }
  }
  $("statLog").textContent = logDone + "/" + tasks.length;
}


// ---- 初始化 ----
function init() {
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
  $("btnNewRoom").addEventListener("click", function () { openModal("newRoomModal"); });
  $("btnCreateRoom").addEventListener("click", createRoom);
  $("btnPickIcon").addEventListener("click", function () { state.iconPickTarget = "create"; openIconPicker(); });
  $("btnIconSearch").addEventListener("click", searchIcons);
  $("btnIconPrev").addEventListener("click", function () { flipIconPage(-1); });
  $("btnIconNext").addEventListener("click", function () { flipIconPage(1); });
  $("btnJoinRoom").addEventListener("click", function () { openModal("joinRoomModal"); });
  $("btnConfirmJoin").addEventListener("click", joinRoomByName);
  $("btnInviteMember").addEventListener("click", openInviteModal);
  $("btnSendMsg").addEventListener("click", sendMessage);
  $("btnToggleMembers").addEventListener("click", toggleMembers);
  $("btnRoomSettings").addEventListener("click", openRoomSettings);
  $("btnConfirmRename").addEventListener("click", confirmRename);
  $("btnChangeAvatar").addEventListener("click", changeRoomAvatar);
  $("btnDissolveRoom").addEventListener("click", dissolveRoom);
  $("btnBackToRooms").addEventListener("click", function () {
    state.commTab = "rooms";
    gotoPage("comm");
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

  tickClock();
  tickDate();
  setInterval(tickClock, 1000);
  updateNearbyBtn();

  // 所有滚动容器:隐藏滚动条,滚轮手动滚动
  // 游戏页(game-content)固定布局不参与滚动:引擎 scrollHeight 计算异常时滚轮会把画面滚飞
  bindHiddenScroll(".page-content:not(.game-content)");
  bindHiddenScroll(".chat-messages");
  bindHiddenScroll(".chat-members");
  // 全局阻止滚轮默认行为:防止引擎滚动视口导致整个画面偏移。
  // 列表的手动滚动(bindHiddenScroll)不受影响——它在容器级先执行 scrollTop,此处仅拦截引擎默认滚动
  document.addEventListener("wheel", function (event) {
    if (event && event.preventDefault) { event.preventDefault(); }
  });

  // 桥接模式:数据由 Java 全量下发,本地仅轮询;网页预览:使用本地模拟数据
  bridgeMode = !!document.getElementById("phoneData");
  if (!bridgeMode) {
    renderContacts();
    renderNearby();
    renderRooms();
  }
  renderTasks();
  renderPlayerInfo();
  renderImportant();
  renderDept();
  renderGames();
  updateUnread();
  if (bridgeMode) { setInterval(bridgePoll, 500); }
  $("playerDept").addEventListener("click", cycleDepartment);
  $("btnBackToFun").addEventListener("click", function () { gotoPage("fun"); });
  $("btnLeaderboard").addEventListener("click", openLeaderboard);
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

  // 页面就绪后立即显示。AUI 的 Document 是直接 create 的,window.load 事件可能不触发,
  // 不能依赖 load/setTimeout 才添加 phone-ready(body 默认 visibility:hidden 会白屏)。
  showPhone();
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
