// ============================================
// 终端 GUI 逻辑 (Control 粗野主义)
// 桥接模式: 读取 #terminalData (Java->页面), 动作写入 #terminalActionQueue (页面->Java)
// 浏览器预览: 无 #terminalData 时用本地 mock 兜底。
// AUI 兼容: 无 forEach/arrow; addEventListener 回调 this 非元素, 一律闭包捕获。
// ============================================

var bridgeMode = false;
var bridgeLastData = "";
var actionQueue = [];
var previewMode = false;
var selectedReportId = "";
var selectedHomeNoticeId = "";
var selectedAnomalyInstanceId = "";
var anomalyDetailMode = false;
var anomalyArchiveExpanded = false;
var terminalState = {
  isOp: false,
  canEditHQTasks: false,
  hqTasks: { items: [] },
  hqTaskEditor: { enabled:false, canEdit:false, opOnly:true },
  canManageAnomalies: false,
  canExpandFacility: false,
  canPublishNotices: false,
  canManageNotices: false,
  notices: { items: [], activeCount:0, archivedCount:0, expiredCount:0, canPublish:false, canManage:false, canHardDelete:false },
  facilityFund: { balanceText: "0", logs: [] },
  reportPanel: { selectedReportId: "", lastAction: "idle" },
  staffOnline: {
    total: 0,
    departments: { "未知":0, "管理部门":0, "科研部门":0, "安保部门":0, "后勤部门":0 },
    players: []
  },
  canViewResearchDetails: false,
  canSubmitReports: false,
  canReviewReports: false,
  anomalies: { codexStatus:{loaded:0,failed:0,errors:[]}, instances:[], reports:[] },
  researchDepartment: { researchSummary:{}, researchAnomalies:[], researchReports:[], maintenanceQueue:[] },
  researchDocuments: { items: [], stateMachine:"draft -> submitted -> published/archived/superseded" },
  departmentApplications: { items: [] },
  pendingCardQueue: { items: [] },
  operationAudit: { items: [], maxHistory: 1000 },
  departmentTasks: { items: [] },
  dispatches: { items: [] },
  vitalAlerts: { items: [] },
  reinforcements: { items: [] },
  procurements: { items: [] },
  procurementSummary: { ledgerBalanceText: "0", reservedBudgetText: "0", availableBudgetText: "0", pendingCount: 0 },
  facilityPayments: { items: [] },
  // Phase 3 payroll window skeleton; Java remains the only state/permission source.
  facilityPayroll: { summary: { balanceText: "0", pendingCount: 0, submittedCount: 0, approvedCount: 0, paidTotal: 0 }, batches: [], lines: [], capabilities: {} }
};

// ---- 禁止拖动/缩放时产生浏览器或 AUI 文本选区 ----
document.addEventListener("selectstart", function(e) { if (e.preventDefault) e.preventDefault(); return false; }, true);
document.addEventListener("dragstart", function(e) { if (e.preventDefault) e.preventDefault(); return false; }, true);
document.addEventListener("mousedown", function(e) {
  if (e.button === 0 && e.preventDefault) e.preventDefault();
}, true);

// ---- 工具 ----
function $(id) { return document.getElementById(id); }
function el(tag, cls, text) {
  var n = document.createElement(tag);
  if (cls) n.className = cls;
  if (text !== undefined && text !== null) n.textContent = "" + text;
  return n;
}
function clearNode(n) { while (n.firstChild) n.removeChild(n.firstChild); }
function pad2(n) { return n < 10 ? "0" + n : "" + n; }
function fmtTime() {
  var d = new Date();
  return pad2(d.getHours()) + ":" + pad2(d.getMinutes());
}
function esc(v) {
  return String(v === undefined || v === null ? "" : v).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/\"/g,"&quot;");
}
function pct(v, max) {
  var n = Number(v || 0), m = Number(max || 100);
  if (m <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round(n * 100 / m)));
}
function listLimit(list, max) {
  list = list || [];
  max = Number(max || 0);
  if (max <= 0 || list.length <= max) return list;
  return list.slice(0, max);
}
function listMoreHint(list, max, label) {
  list = list || [];
  if (list.length <= max) return "";
  return "<div class='empty-tip'>" + esc(label || "列表") + "仅显示前 " + max + " 条，剩余 " + (list.length - max) + " 条请使用筛选或等待分页查看。</div>";
}
function formatDateTime(ts) {
  var n = Number(ts || 0); if (!n) return "-";
  var d = new Date(n);
  return pad2(d.getMonth()+1)+"-"+pad2(d.getDate())+" "+pad2(d.getHours())+":"+pad2(d.getMinutes());
}

// Tabler Icons 内联 SVG(MIT)。不走 CDN/npm, 合并进 AUI 页面离线使用。
var ICONS = {
  "clipboard-list":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M9 5h-2a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-12a2 2 0 0 0 -2 -2h-2'/><path d='M9 5a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2a2 2 0 0 1 -2 2h-2a2 2 0 0 1 -2 -2'/><path d='M9 12l.01 0'/><path d='M13 12l2 0'/><path d='M9 16l.01 0'/><path d='M13 16l2 0'/></svg>",
  "users-group":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M10 13a2 2 0 1 0 4 0a2 2 0 0 0 -4 0'/><path d='M8 21v-1a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v1'/><path d='M15 5a2 2 0 1 0 4 0a2 2 0 0 0 -4 0'/><path d='M17 10h2a2 2 0 0 1 2 2v1'/><path d='M5 5a2 2 0 1 0 4 0a2 2 0 0 0 -4 0'/><path d='M3 13v-1a2 2 0 0 1 2 -2h2'/></svg>",
  "cash-banknote":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0'/><path d='M3 8a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2l0 -8'/><path d='M18 12h.01'/><path d='M6 12h.01'/></svg>",
  "building-bank":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M3 21l18 0'/><path d='M3 10l18 0'/><path d='M5 6l7 -3l7 3'/><path d='M4 10l0 11'/><path d='M20 10l0 11'/><path d='M8 14l0 3'/><path d='M12 14l0 3'/><path d='M16 14l0 3'/></svg>",
  "package-import":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M12 21l-8 -4.5v-9l8 -4.5l8 4.5v4.5'/><path d='M12 12l8 -4.5'/><path d='M12 12v9'/><path d='M12 12l-8 -4.5'/><path d='M22 18h-7'/><path d='M18 15l-3 3l3 3'/></svg>",
  "biohazard":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0'/><path d='M11.939 14c0 .173 .048 .351 .056 .533l0 .217a4.75 4.75 0 0 1 -4.533 4.745l-.217 0m-4.75 -4.75a4.75 4.75 0 0 1 7.737 -3.693m6.513 8.443a4.75 4.75 0 0 1 -4.69 -5.503l-.06 0m1.764 -2.944a4.75 4.75 0 0 1 7.731 3.477l0 .217m-11.195 -3.813a4.75 4.75 0 0 1 -1.828 -7.624l.164 -.172m6.718 0a4.75 4.75 0 0 1 -1.665 7.798'/></svg>",
  "file-description":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M14 3v4a1 1 0 0 0 1 1h4'/><path d='M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2'/><path d='M9 17h6'/><path d='M9 13h6'/></svg>",
  "id":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M3 7a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3l0 -10'/><path d='M7 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0'/><path d='M15 8l2 0'/><path d='M15 12l2 0'/><path d='M7 16l10 0'/></svg>",
  "speakerphone":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M18 8a3 3 0 0 1 0 6'/><path d='M10 8v11a1 1 0 0 1 -1 1h-1a1 1 0 0 1 -1 -1v-5'/><path d='M12 8l4.524 -3.77a.9 .9 0 0 1 1.476 .692v12.156a.9 .9 0 0 1 -1.476 .692l-4.524 -3.77h-8a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h8'/></svg>",
  "grid-dots":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M4 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M18 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M4 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/><path d='M18 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0'/></svg>",
  "terminal-2":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M8 9l3 3l-3 3'/><path d='M13 15l3 0'/><path d='M3 6a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2l0 -12'/></svg>",
  "reload":"<svg class='tb-ico' xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M19.933 13.041a8 8 0 1 1 -9.925 -8.788c3.899 -1 7.935 1.007 9.425 4.747'/><path d='M20 4v5h-5'/></svg>"
};
function ico(key){ return ICONS[key] || "<span>"+esc(key)+"</span>"; }
function renderStaticIcons(){ var nodes=document.querySelectorAll("[data-ico]"); for(var i=0;i<nodes.length;i++){ var k=nodes[i].getAttribute("data-ico"); nodes[i].innerHTML=ico(k); } }

// ---- 窗口内容定义(与预览一致) ----
var WIN = {
  daily:{ title:"总部设施任务", icon:"clipboard-list", btn:[["同步指令","ghost","requestTerminal"],["任务编辑器","accent","openHqEditor"]],
    content:function(){ return renderHqTaskWindow(); }
  },
  hqEditor:{ title:"总部任务编辑器", icon:"clipboard-list", btn:[["保存当前任务","accent","hqEditorSave"],["补足任务","ghost","hqEditorFill"],["强制完成","ghost","hqEditorComplete"],["强制失败","ghost","hqEditorFail"],["删除任务","ghost","hqEditorDelete"]],
    content:function(){ return renderHqEditorWindow(); }
  },
  dept:{ title:"部门调度台", icon:"users-group", btn:[["刷新","ghost","requestTerminal"]],
    content:function(){ return renderDepartmentOpsPhase2Skeleton(); }
  },
  applications:{ title:"审批 / 待换卡 / 审计", icon:"file-description", btn:[["刷新","ghost","requestTerminal"]],
    content:function(){ return renderDepartmentApplicationWindow(); }
  },
  management:{ title:"文书与公告中心", icon:"file-description", btn:[["刷新","ghost","requestTerminal"]],
    content:function(){ return renderManagementOfficeWindow(); }
  },
  salary:{ title:"薪资结算", icon:"cash-banknote", btn:[["同步批次","ghost","requestTerminal"]],
    content:function(){
      var pay=terminalState.facilityPayroll||{summary:{},batches:[],lines:[]}; var sum=pay.summary||{}; var h="<div class='payroll-kpi-row'><div class='kpi-sm'><div class='k-val'>"+esc(sum.balanceText||"0")+"</div><div class='k-sub'>设施资金（服务端）</div></div><div class='kpi-sm'><div class='k-val'>"+Number(sum.pendingCount||0)+"</div><div class='k-sub'>待处理批次</div></div><div class='kpi-sm'><div class='k-val'>"+Number(sum.paidTotal||0)+"</div><div class='k-sub'>已付合计</div></div></div><div class='perm-note'>"+esc(pay.paymentBlockedReason||'付款由服务端校验')+"</div><button class='term-btn ghost' onclick='pushAction({t:\"payroll_sync\"})'>刷新</button><table class='ptable'><tr><th>批次</th><th>期间</th><th>总额</th><th>状态</th><th>动作</th></tr>"; var bs=pay.batches||[]; if(!bs.length) h+="<tr><td colspan='5'>暂无薪资批次</td></tr>"; for(var i=0;i<bs.length;i++){var b=bs[i];h+="<tr><td>"+esc(b.batchId)+"</td><td>"+esc(b.periodLabel)+"</td><td>"+esc(b.total)+"</td><td>"+esc(b.status)+"</td><td>"+"<span class='muted'>付款待事务API验证</span>"+"</td></tr>";} h+="</table>"; return h;

      var h="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>¥"+(fund.balanceText||"0")+"</div><div class='k-sub'>可用设施资金</div></div><div class='kpi-sm'><div class='k-val'>--</div><div class='k-sub'>待结算批次</div></div><div class='kpi-sm'><div class='k-val'>ID</div><div class='k-sub'>发至个人ID卡账户</div></div></div>";
      h+="<table class='ptable'><tr><th>对象</th><th>账户</th><th>金额</th><th style='text-align:right'>状态</th></tr>";
      var s=[["管理部门","ID卡个人账户","待配置","待发放"],["科研部门","ID卡个人账户","待配置","待发放"],["安保部门","ID卡个人账户","待配置","待发放"],["后勤部门(含0级临时)","ID卡个人账户","待配置","待发放"]];
      for(var i=0;i<s.length;i++){
        h+="<tr><td><span class='cell-dept'>"+s[i][0]+"</span></td><td>"+s[i][1]+"</td><td>"+s[i][2]+"</td><td style='text-align:right'>"+s[i][3]+"</td></tr>";
      }
      h+="</table>";
      return h;
    }
  },
  facility:{ title:"设施资金", icon:"building-bank", btn:[["终端专用账户","ghost"]],
    content:function(){
      var fund = terminalState.facilityFund || { balanceText:"0", logs:[] };
      var bal = fund.balanceText || String(fund.balance || 0);
      var h="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>¥"+bal+"</div><div class='k-sub'>设施全局资金 · 仅终端可操作</div></div><div class='kpi-sm'><div class='k-val'>"+(fund.logs ? fund.logs.length : 0)+"</div><div class='k-sub'>近期流水</div></div><div class='kpi-sm'><div class='k-val'>LOCK</div><div class='k-sub'>无实体银行卡</div></div></div>";
      h+="<table class='ptable'><tr><th>时间</th><th>事项</th><th>对象</th><th style='text-align:right'>金额</th></tr>";
      var l=fund.logs || [];
      if(l.length===0){
        h+="<tr><td colspan='4'>暂无设施资金流水</td></tr>";
      } else {
        for(var i=0;i<l.length && i<8;i++){
          var amt = Number(l[i].amount || 0);
          var sign = amt > 0 ? "+" : "";
          h+="<tr><td>"+(l[i].time||"")+"</td><td>"+(l[i].note||l[i].type||"")+"</td><td>"+(l[i].target||"")+"</td><td style='text-align:right'>"+sign+amt+"</td></tr>";
        }
      }
      h+="</table>";
      return h;
    }
  },
  purchase:{ title:"采购与资金中心", icon:"package-import", btn:[["+ 采购申请","accent","procurementCreateSubmit"],["+ 设施付款","ghost","facilityPaymentCreateSubmit"]],
    content:function(){ return renderProcurementFundWindow(); }
  },
  staff:{ title:"在线员工", icon:"id", btn:[["ID卡快照缓存","ghost"]],
    content:function(){
      var st = terminalState.staffOnline || {total:0,departments:{},players:[]};
      var h="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>"+st.total+"</div><div class='k-sub'>当前在线员工</div></div></div>";
      h+="<table class='ptable'><tr><th>玩家</th><th>部门</th><th>职位</th><th style='text-align:right'>等级</th></tr>";
      var p=st.players || [];
      if(p.length===0){
        h+="<tr><td colspan='4'>暂无在线玩家快照</td></tr>";
      } else {
        for(var i=0;i<p.length;i++){
          var name=p[i].cardName||p[i].name||p[i].playerName||"?";
          var dept=p[i].department||"";
          var pos=p[i].position||"";
          var lv=Number(p[i].level||0);
          h+="<tr><td>"+name+"</td><td><span class='cell-dept'>"+dept+"</span></td><td>"+pos+"</td><td style='text-align:right'>"+lv+"</td></tr>";
        }
      }
      h+="</table>";
      return h;
    }
  },
  notice:{ title:"公告下发", icon:"speakerphone", btn:[["刷新","ghost","requestTerminal"]],
    content:function(){ return renderNoticeWindow(); }
  },
  noticeDetail:{ title:"设施公告详情", icon:"speakerphone", btn:[["查看全部公告","ghost","openNoticeManager"]],
    content:function(){ return renderHomeNoticeDetail(); }
  },
  research:{ title:"科研部门总览", icon:"microscope", btn:[["刷新","ghost","requestTerminal"]],
    content:function(){ return renderResearchWindow(); }
  },
  anomaly:{ title:"异常物总览", icon:"biohazard", btn:[["重载档案","accent","reloadCodex"]],
    content:function(){ return anomalyDetailMode ? renderAnomalyDetail() : renderAnomalyOverviewWindow(); }
  },
  reports:{ title:"实验报告收件箱", icon:"file-description", btn:[["刷新","ghost","requestTerminal"]],
    content:function(){
      var a=terminalState.anomalies||{reports:[]}; var r=a.reports||[]; if(!selectedReportId&&r.length) selectedReportId=r[0].reportId;
      var picked=null; for(var p=0;p<r.length;p++){ if(r[p].reportId===selectedReportId) picked=r[p]; }
      var can=terminalState.canReviewReports===true;
      var h="<div class='report-inbox'><div class='report-list'>";
      if(r.length===0){ h+="<div class='empty-tip'>暂无实验报告。</div>"; }
      for(var i=0;i<r.length;i++){ var it=r[i]; var cls='report-row '+((it.reportId===selectedReportId)?'active':'');
        h+="<div class='"+cls+"' onclick='selectReport(\""+esc(it.reportId)+"\")'><b>"+esc(it.title||it.reportId)+"</b><span>"+esc(it.status||'pending')+" · "+esc(it.authorName||'')+"</span><small>"+esc(it.anomalyInstanceId||'')+"</small></div>";
      }
      h+="</div><div class='report-detail'>";
      if(!picked){ h+="<div class='empty-tip'>选择左侧报告查看详情。</div>"; }
      else { h+="<div class='report-detail-head'><h3>"+esc(picked.title||picked.reportId)+"</h3><span class='status-pill'>"+esc(picked.status||'pending')+"</span></div>";
        h+="<div class='report-meta'><span>ID "+esc(picked.reportId)+"</span><span>异常 "+esc(picked.anomalyInstanceId)+"</span><span>作者 "+esc(picked.authorName)+"</span><span>提交 "+esc(picked.submittedAt||'')+"</span><span>审核 "+esc(picked.reviewerName||'-')+"</span><span>时间 "+esc(picked.reviewedAt||'-')+"</span></div>";
        h+="<pre class='report-body'>"+esc(picked.content||'')+"</pre>";
        h+="<div class='report-meta'><span>archivePath: "+esc(picked.archivePath||'-')+"</span><span>rejectReason: "+esc(picked.rejectReason||'-')+"</span></div>";
        if(can){ h+="<div class='report-actions'><button onclick='reportApprove(\""+esc(picked.reportId)+"\",5)'>通过</button><button onclick='reportRejectPrompt(\""+esc(picked.reportId)+"\")'>驳回</button><button onclick='reportArchive(\""+esc(picked.reportId)+"\")'>归档</button></div>"; }
        else { h+="<div class='perm-note'>当前身份只能查看，无法审核。</div>"; }
      }
      h+="</div></div>"; return h;
    }
  }
};

function renderPayrollWindow(){
  var pay=terminalState.facilityPayroll||{summary:{},batches:[],lines:[]},sum=pay.summary||{},bs=pay.batches||[],ls=pay.lines||[];
  var h="<div class='payroll-kpi-row'><div class='kpi-sm'><div class='k-val'>"+esc(sum.balanceText||'0')+"</div><div class='k-sub'>设施资金（服务端）</div></div><div class='kpi-sm'><div class='k-val'>"+Number(sum.pendingCount||0)+"</div><div class='k-sub'>待处理批次</div></div><div class='kpi-sm'><div class='k-val'>"+Number(sum.paidTotal||0)+"</div><div class='k-sub'>已付合计</div></div></div>";
  h+="<div class='perm-note'>"+esc(pay.paymentBlockedReason||'付款由服务端校验')+"</div><div class='payroll-form'><input id='pay_period' placeholder='薪资期间，例如 2026-08'><input id='pay_note' placeholder='批次备注'><button class='mini-btn' onclick='payrollCreateBatch()'>创建批次</button></div>";
  h+="<table class='ptable'><tr><th>批次</th><th>期间</th><th>总额</th><th>状态</th><th>动作</th></tr>";
  if(!bs.length)h+="<tr><td colspan='5'>暂无薪资批次</td></tr>";
  for(var i=0;i<bs.length;i++){var b=bs[i],a="<button class='mini-btn' onclick=\"payrollAddLine('"+esc(b.batchId)+"')\">添加明细</button>";if(b.status==='draft')a+=" <button class='mini-btn' onclick=\"pushAction({t:'payroll_batch_submit',batchId:'"+esc(b.batchId)+"',expectedVersion:"+Number(b.version||0)+"})\">提交</button>";if(b.status==='submitted')a+=" <button class='mini-btn' onclick=\"pushAction({t:'payroll_batch_approve',batchId:'"+esc(b.batchId)+"',expectedVersion:"+Number(b.version||0)+"})\">审批</button>";if(['draft','submitted','approved'].indexOf(b.status)>=0)a+=" <button class='mini-btn' onclick=\"pushAction({t:'payroll_batch_cancel',batchId:'"+esc(b.batchId)+"',expectedVersion:"+Number(b.version||0)+"})\">取消</button>";h+="<tr><td>"+esc(b.batchId)+"</td><td>"+esc(b.periodLabel)+"</td><td>"+esc(b.total)+"</td><td>"+esc(b.status)+"</td><td>"+a+"</td></tr>";}
  h+="</table><div class='payroll-lines'>";for(var j=0;j<ls.length&&j<24;j++){var l=ls[j];h+="<div class='pay-line'><b>"+esc(l.targetName||'-')+"</b><span>"+esc(l.amount||0)+" · "+esc(l.status||'pending')+" · "+esc(l.reason||'')+"</span><button class='mini-btn' onclick=\"pushAction({t:'payroll_batch_remove_line',batchId:'"+esc(l.batchId)+"',lineId:'"+esc(l.lineId)+"'})\">移除</button></div>";}h+="</div>";return h;
}
function payrollCreateBatch(){pushAction({t:'payroll_batch_create',periodLabel:getVal('pay_period'),note:getVal('pay_note')});}
function payrollAddLine(batchId){terminalTextModal('添加工资明细：目标UUID|姓名|金额|原因','UUID|姓名|100|工资',function(v){var a=(v||'').split('|');pushAction({t:'payroll_batch_add_line',batchId:batchId,targetUuid:a[0]||'',targetName:a[1]||'',amount:a[2]||'0',reason:a.slice(3).join('|')||''});});}
WIN.salary.content=function(){return renderPayrollWindow();};
function renderProcurementFundWindow(){
  var procs=(terminalState.procurements&&terminalState.procurements.items)||[], pays=(terminalState.facilityPayments&&terminalState.facilityPayments.items)||[], s=terminalState.procurementSummary||{};
  var h="<div class='procurement-summary'><div><b>¥"+esc(s.ledgerBalanceText||'0')+"</b><small>设施总账</small></div><div><b>¥"+esc(s.reservedBudgetText||'0')+"</b><small>采购已占用</small></div><div><b>¥"+esc(s.availableBudgetText||'0')+"</b><small>采购可用</small></div><div><b>"+Number(s.pendingCount||0)+"</b><small>待处理</small></div></div>";
  h+="<div class='perm-note'>采购必须绑定已批准申请；金额与权限由服务端校验。工资付款保持关闭，不展示个人 LC 账户。</div>";
  h+="<h3>新建采购申请</h3><div class='procurement-form'><input id='proc_category' placeholder='类别 general / research / weapon / emergency'><input id='proc_items' placeholder='物品（逗号分隔）'><input id='proc_amount' type='number' min='1' placeholder='金额'><select id='proc_risk'><option value='low'>低风险</option><option value='high'>高风险</option></select><input id='proc_source' placeholder='来源申请 ID'><input id='proc_reason' placeholder='申请理由'><button class='head-btn accent' onclick='procurementCreateSubmit()'>提交采购申请</button></div>";
  h+="<h3>采购申请</h3><div class='procurement-list'><table class='ptable'><tr><th>ID/来源</th><th>类别/部门</th><th>金额</th><th>状态</th><th>预算/动作</th></tr>";
  if(procs.length===0) h+="<tr><td colspan='5'>暂无采购记录</td></tr>";
  for(var i=0;i<procs.length&&i<16;i++){var p=procs[i],pa=""; if(p.status==='request')pa+=" <button class='mini-btn' onclick='pushAction({t:\"procurement_approve\",id:\""+esc(p.id)+"\"})'>批准</button><button class='mini-btn' onclick='financeReason(\"procurement_reject\",\""+esc(p.id)+"\")'>驳回</button>"; if(p.status==='approved'||p.status==='budget_reserved')pa+=" <button class='mini-btn' onclick='pushAction({t:\"procurement_order\",id:\""+esc(p.id)+"\"})'>下单</button>"; if(p.status==='ordered')pa+=" <button class='mini-btn' onclick='pushAction({t:\"procurement_receive\",id:\""+esc(p.id)+"\"})'>收货确认</button>"; if(p.status==='received')pa+=" <button class='mini-btn' onclick='pushAction({t:\"procurement_complete\",id:\""+esc(p.id)+"\"})'>完成</button>"; if(['completed','rejected','cancelled','failed'].indexOf(p.status)<0)pa+=" <button class='mini-btn' onclick='financeReason(\"procurement_cancel\",\""+esc(p.id)+"\")'>取消</button>"; h+="<tr><td>"+esc(p.id)+"<br><small>"+esc(p.sourceApplicationId||'-')+"</small></td><td>"+esc(p.category)+" / "+esc(p.department)+"</td><td>"+esc(p.amount)+"</td><td><span class='proc-status proc-status-"+esc(p.status)+"'>"+esc(p.status)+"</span></td><td>"+esc(p.budgetStatus||p.reservedBudget||'-')+pa+"</td></tr>";}
  h+="</table></div>"+listMoreHint(procs,16,"采购")+"<h3>设施付款（独立于采购预算）</h3><table class='ptable'><tr><th>ID</th><th>收款人</th><th>金额</th><th>状态</th><th>动作</th></tr>";
  if(pays.length===0) h+="<tr><td colspan='5'>暂无设施付款记录</td></tr>";
  for(var j=0;j<pays.length&&j<16;j++){var x=pays[j],xa=""; if(x.status==='pending')xa+=" <button class='mini-btn' onclick='pushAction({t:\"facility_payment_approve\",id:\""+esc(x.id)+"\"})'>批准</button><button class='mini-btn' onclick='financeReason(\"facility_payment_cancel\",\""+esc(x.id)+"\")'>取消</button>"; if(x.status==='approved')xa+=" <button class='mini-btn' onclick='pushAction({t:\"facility_payment_pay\",id:\""+esc(x.id)+"\"})'>支付</button>"; h+="<tr><td>"+esc(x.id)+"</td><td>"+esc(x.payeeName||x.payeeUuid)+"</td><td>"+esc(x.amount)+"</td><td>"+esc(x.status)+"</td><td>"+xa+"</td></tr>";}
  return h+"</table>"+listMoreHint(pays,16,"设施付款");
}
function procurementCreateSubmit(){ pushAction({t:'procurement_create',category:getVal('proc_category'),items:getVal('proc_items'),amount:getVal('proc_amount'),riskLevel:getVal('proc_risk'),sourceApplicationId:getVal('proc_source'),reason:getVal('proc_reason')}); }
function facilityPaymentCreateSubmit(){ terminalTextModal('设施付款（服务端校验）','sourceApplicationId/payeeUuid/payeeName/amount/reason',function(txt){var a=(txt||'').split('|');pushAction({t:'facility_payment_create',sourceApplicationId:a[0]||'',payeeUuid:a[1]||'',payeeName:a[2]||'',amount:a[3]||'0',reason:a.slice(4).join('|')||''});}); }
function financeReason(t,id){ terminalTextModal('原因/备注','',function(txt){pushAction({t:t,id:id,reason:txt||'',note:txt||''});}); }

/* Phase 2 B skeleton: one shared panel host and static five-tab navigation.
 * C adds field rendering/actions; this layer intentionally has no permission logic. */
function renderDepartmentOpsPhase2Skeleton_Legacy(){
  var st=terminalState.staffOnline||{total:0,departments:{},players:[]};
  var d=st.departments||{};
  var tabs=[['online','在线编制'],['tasks','部门任务'],['dispatch','调度'],['alerts','生命警报'],['reinforcements','增援']];
  var h="<div class='dept-phase2' data-dept-shell='1'>";
  h+="<nav class='dept-tabs' aria-label='部门调度分类'>";
  for(var i=0;i<tabs.length;i++) h+="<button type='button' class='dept-tab"+(i===0?' active':'')+"' data-dept-tab='"+tabs[i][0]+"' aria-selected='"+(i===0?'true':'false')+"'>"+tabs[i][1]+"</button>";
  h+="</nav><div class='dept-panel-host' data-dept-panel='1'>";
  h+="<section class='dept-panel active' data-dept-panel-id='online'><div class='dept-panel-head'><h3>在线编制</h3><span class='dept-kpi'>在线 "+esc(st.total||0)+" 人</span></div>";
  h+="<div class='dept-counts'>管理部门 "+esc(d['管理部门']||0)+" · 科研部门 "+esc(d['科研部门']||0)+" · 安保部门 "+esc(d['安保部门']||0)+" · 后勤部门 "+esc(d['后勤部门']||0)+" · 未知 "+esc(d['未知']||0)+"</div><div class='dept-list' data-dept-list='online'>";
  var players=st.players||[];
  if(!players.length) h+="<div class='empty-tip'>暂无在线编制数据</div>";
  for(var p=0;p<players.length&&p<12;p++){var x=players[p]||{};h+="<div class='dept-row'><b>"+esc(x.cardName||x.name||x.playerName||'-')+"</b><span>"+esc(x.department||'-')+" · "+esc(x.position||'-')+" · Lv."+esc(x.level||0)+"</span></div>";}
  h+="</div></section>";
  var panels=[['tasks','部门任务','departmentTasks'],['dispatch','调度','dispatches'],['alerts','生命警报','vitalAlerts'],['reinforcements','增援','reinforcements']];
  for(var j=0;j<panels.length;j++){var q=panels[j], box=terminalState[q[2]]||{}, items=box.items||[];h+="<section class='dept-panel' data-dept-panel-id='"+q[0]+"' hidden><div class='dept-panel-head'><h3>"+q[1]+"</h3><span class='dept-kpi'>"+esc(items.length)+" 条</span></div><div class='dept-list' data-dept-list='"+q[0]+"'><div class='empty-tip'>C阶段接入服务端字段渲染（当前 "+esc(items.length)+" 条）</div></div></section>";}
  h+="</div></div>";
  return h;
}

function bindDepartmentPhase2Tabs(root){
  if(!root) return;
  var buttons=root.querySelectorAll('[data-dept-tab]');
  for(var i=0;i<buttons.length;i++){(function(btn){btn.onclick=function(){var key=btn.getAttribute('data-dept-tab'), ps=root.querySelectorAll('[data-dept-panel-id]');for(var k=0;k<ps.length;k++){var on=ps[k].getAttribute('data-dept-panel-id')===key;ps[k].hidden=!on;if(on)ps[k].className='dept-panel active';else ps[k].className='dept-panel';}for(var b=0;b<buttons.length;b++){var sel=buttons[b]===btn;buttons[b].className='dept-tab'+(sel?' active':'');buttons[b].setAttribute('aria-selected',sel?'true':'false');}};})(buttons[i]);}
}

function renderDepartmentOpsTerminalLegacy(){
  var st=terminalState.staffOnline||{departments:{}}; var dpt=st.departments||{};
  var tasks=(terminalState.departmentTasks&&terminalState.departmentTasks.items)||[], ds=(terminalState.dispatches&&terminalState.dispatches.items)||[], va=(terminalState.vitalAlerts&&terminalState.vitalAlerts.items)||[], rf=(terminalState.reinforcements&&terminalState.reinforcements.items)||[];
  var h="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>"+(tasks.length)+"</div><div class='k-sub'>部门任务</div></div><div class='kpi-sm'><div class='k-val'>"+ds.length+"</div><div class='k-sub'>调度</div></div><div class='kpi-sm'><div class='k-val'>"+va.length+"</div><div class='k-sub'>生命警报</div></div><div class='kpi-sm'><div class='k-val'>"+rf.length+"</div><div class='k-sub'>增援</div></div></div>";
  h+="<div class='perm-note'>按钮依据下发数据与身份做前端裁剪；即使误显示，dept_task/dispatch/vital/reinforcement action 仍由服务端硬校验并落审计。</div>";
  h+="<table class='ptable'><tr><th>部门</th><th>在线</th><th style='text-align:right'>状态</th></tr>"; var rows=[["管理部门",dpt["管理部门"]||0,"统筹"],["科研部门",dpt["科研部门"]||0,"实验/文档"],["安保部门",dpt["安保部门"]||0,"戒备/警报"],["后勤部门",dpt["后勤部门"]||0,"补给/0级"],["未知",dpt["未知"]||0,"待登记"]]; for(var i=0;i<rows.length;i++) h+="<tr><td><span class='cell-dept'>"+esc(rows[i][0])+"</span></td><td>"+rows[i][1]+"</td><td style='text-align:right'>"+esc(rows[i][2])+"</td></tr>"; h+="</table>";
  h+="<h3>任务创建</h3><div class='notice-form-shell'><input id='dt_title' placeholder='任务标题'><textarea id='dt_desc' placeholder='任务说明'></textarea><input id='dt_target' placeholder='targetDepartment 安保部门/科研部门/后勤部门/管理部门'><input id='dt_pos' placeholder='targetPosition 可空'><input id='dt_min' placeholder='minLevel 数字'><input id='dt_goal' placeholder='goal 数字'><input id='dt_reward' placeholder='rewardSummary'><button class='head-btn accent' onclick='deptTaskCreate()'>创建部门任务</button></div>";
  h+="<table class='ptable'><tr><th>ID</th><th>任务</th><th>对象/进度</th><th style='text-align:right'>状态/动作</th></tr>"; if(!tasks.length) h+="<tr><td colspan='4'>暂无部门任务</td></tr>"; for(var t=0;t<tasks.length&&t<16;t++){ var x=tasks[t], act=""; if(x.status==='open') act+=" <button class='mini-btn' onclick='pushAction({t:\"dept_task_claim\",id:\""+esc(x.id)+"\"})'>领取</button>"; if(x.status==='open'||x.status==='claimed') act+=" <button class='mini-btn' onclick='pushAction({t:\"dept_task_progress\",id:\""+esc(x.id)+"\",status:\"start\"})'>开始</button>"; if(x.status==='in_progress'||x.status==='claimed') act+=" <button class='mini-btn' onclick='deptTaskSubmitPrompt(\""+esc(x.id)+"\")'>提交</button>"; if(x.status!=='completed') act+=" <button class='mini-btn' onclick='deptTaskClosePrompt(\""+esc(x.id)+"\")'>关闭</button>"; h+="<tr><td>"+esc(x.id)+"</td><td><b>"+esc(x.title||'')+"</b><br><span class='muted'>"+esc(x.description||'')+"</span></td><td>"+esc(x.targetDepartment||'')+" "+esc(x.targetPosition||'')+" Lv."+esc(x.minLevel||0)+"<br>"+esc(x.progress||0)+"/"+esc(x.goal||1)+" "+esc(x.rewardSummary||'')+"</td><td style='text-align:right'>"+esc(x.status||'')+act+"</td></tr>"; } h+="</table>"+listMoreHint(tasks,16,"部门任务");
  h+="<h3>调度创建 / 列表</h3><div class='notice-form-shell'><input id='dp_target' placeholder='targetDepartment'><input id='dp_pos' placeholder='targetPosition 可空'><input id='dp_min' placeholder='minLevel'><input id='dp_pri' placeholder='priority'><input id='dp_loc' placeholder='location'><textarea id='dp_msg' placeholder='message'></textarea><button class='head-btn accent' onclick='dispatchCreate()'>创建调度</button></div>";
  h+="<table class='ptable'><tr><th>ID</th><th>目标</th><th>位置/消息</th><th style='text-align:right'>状态/动作</th></tr>"; if(!ds.length) h+="<tr><td colspan='4'>暂无调度</td></tr>"; for(var d=0;d<ds.length&&d<14;d++){ var y=ds[d], da=""; if(y.status==='pending') da+=" <button class='mini-btn' onclick='pushAction({t:\"dispatch_approve\",id:\""+esc(y.id)+"\"})'>ACK</button>"; if(y.status==='acknowledged'||y.status==='pending') da+=" <button class='mini-btn' onclick='pushAction({t:\"dispatch_update_status\",id:\""+esc(y.id)+"\",status:\"completed\"})'>完成</button><button class='mini-btn' onclick='dispatchCancelPrompt(\""+esc(y.id)+"\")'>取消</button>"; h+="<tr><td>"+esc(y.id)+"</td><td>"+esc(y.sourceDepartment||'')+" → "+esc(y.targetDepartment||'')+" "+esc(y.targetPosition||'')+" Lv."+esc(y.minLevel||0)+"</td><td>"+esc(y.location||'')+"<br><span class='muted'>"+esc(y.message||y.receipt||'')+"</span></td><td style='text-align:right'>"+esc(y.status||'')+da+"</td></tr>";} h+="</table>"+listMoreHint(ds,14,"调度");
  h+="<h3>生命警报</h3><table class='ptable'><tr><th>ID/目标</th><th>身份</th><th>位置</th><th style='text-align:right'>状态/动作</th></tr>"; if(!va.length) h+="<tr><td colspan='4'>暂无可见生命警报</td></tr>"; for(var a=0;a<va.length&&a<14;a++){ var z=va[a], aa=""; if(z.status==='open') aa+=" <button class='mini-btn' onclick='pushAction({t:\"vital_alert_ack\",id:\""+esc(z.id)+"\"})'>ACK</button>"; if(z.status==='open'||z.status==='acknowledged') aa+=" <button class='mini-btn' onclick='vitalDispatchPrompt(\""+esc(z.id)+"\")'>派遣</button>"; if(z.status==='dispatched') aa+=" <button class='mini-btn' onclick='vitalResolvePrompt(\""+esc(z.id)+"\")'>解除</button>"; h+="<tr><td>"+esc(z.id)+"<br><b>"+esc(z.targetName||'')+"</b></td><td>"+esc(z.department||'')+"/"+esc(z.position||'')+" Lv."+esc(z.level||0)+"</td><td>"+esc(z.dimension||'')+" ["+esc(z.x||0)+","+esc(z.y||0)+","+esc(z.z||0)+"]<br><span class='muted'>"+esc(z.damageSummary||z.note||'')+"</span></td><td style='text-align:right'>"+esc(z.status||'')+aa+"</td></tr>";} h+="</table>"+listMoreHint(va,14,"生命警报");
  h+="<h3>增援请求 / 列表</h3><div class='notice-form-shell'><input id='rf_target' placeholder='targetDepartment 默认安保部门'><input id='rf_min' placeholder='minLevel'><input id='rf_count' placeholder='neededCount'><input id='rf_danger' placeholder='dangerLevel'><input id='rf_loc' placeholder='location'><textarea id='rf_desc' placeholder='description'></textarea><button class='head-btn accent' onclick='reinforcementCreate()'>请求增援</button></div>";
  h+="<table class='ptable'><tr><th>ID</th><th>请求/目标</th><th>地点/说明</th><th style='text-align:right'>状态/动作</th></tr>"; if(!rf.length) h+="<tr><td colspan='4'>暂无增援</td></tr>"; for(var r=0;r<rf.length&&r<14;r++){ var q=rf[r], ra=""; if(q.status==='open') ra+=" <button class='mini-btn' onclick='pushAction({t:\"reinforcement_decide\",id:\""+esc(q.id)+"\"})'>接单</button>"; if(q.status==='claimed') ra+=" <button class='mini-btn' onclick='pushAction({t:\"reinforcement_close\",id:\""+esc(q.id)+"\",status:\"arrived\"})'>抵达</button>"; if(q.status==='arrived') ra+=" <button class='mini-btn' onclick='reinforcementResolvePrompt(\""+esc(q.id)+"\")'>解决</button>"; h+="<tr><td>"+esc(q.id)+"</td><td>"+esc(q.requestDepartment||'')+" → "+esc(q.targetDepartment||'')+" Lv."+esc(q.minLevel||0)+" ×"+esc(q.neededCount||1)+"</td><td>"+esc(q.location||'')+"<br><span class='muted'>"+esc(q.description||q.resolution||'')+"</span></td><td style='text-align:right'>"+esc(q.status||'')+ra+"</td></tr>";} h+="</table>"+listMoreHint(rf,14,"增援");
  return h;
}
function deptTaskCreate(){ pushAction({t:'dept_task_create',title:getVal('dt_title'),description:getVal('dt_desc'),targetDepartment:getVal('dt_target'),targetPosition:getVal('dt_pos'),minLevel:Number(getVal('dt_min')||0),goal:Number(getVal('dt_goal')||1),rewardSummary:getVal('dt_reward')}); }
function deptTaskSubmitPrompt(id){ terminalTextModal('提交任务进度','输入进度数字；第二行起作为备注',function(v){ var n=parseInt(v,10); pushAction({t:'dept_task_progress',id:id,progress:isNaN(n)?1:n,note:v||''}); }); }
function deptTaskClosePrompt(id){ terminalTextModal('关闭任务','输入 completed/failed/cancelled，换行后可写原因',function(v){ var p=(v||'completed').split('\n'); pushAction({t:'dept_task_close',id:id,status:p[0]||'completed',note:v||''}); }); }
function dispatchCreate(){ pushAction({t:'dispatch_create',targetDepartment:getVal('dp_target'),targetPosition:getVal('dp_pos'),minLevel:Number(getVal('dp_min')||0),priority:getVal('dp_pri')||'normal',location:getVal('dp_loc'),message:getVal('dp_msg')}); }
function dispatchCancelPrompt(id){ terminalTextModal('取消调度原因','reason/note',function(v){ pushAction({t:'dispatch_update_status',id:id,status:'cancelled',note:v||''}); }); }
function vitalDispatchPrompt(id){ terminalTextModal('警报派遣备注','note',function(v){ pushAction({t:'vital_alert_dispatch',id:id,note:v||''}); }); }
function vitalResolvePrompt(id){ terminalTextModal('警报解除备注','note',function(v){ pushAction({t:'vital_alert_resolve',id:id,note:v||''}); }); }
function reinforcementCreate(){ pushAction({t:'reinforcement_request',targetDepartment:getVal('rf_target'),minLevel:Number(getVal('rf_min')||0),neededCount:Number(getVal('rf_count')||1),dangerLevel:getVal('rf_danger')||'normal',location:getVal('rf_loc'),description:getVal('rf_desc')}); }
function reinforcementResolvePrompt(id){ terminalTextModal('增援解决备注','resolution/note',function(v){ pushAction({t:'reinforcement_close',id:id,status:'resolved',note:v||''}); }); }
function renderManagementOfficeWindow(){
  var m=terminalState.managementOffice||{records:[],personnelEditEnabled:false,combatMeetingInterceptionEnabled:false};
  var rec=m.records||[];
  var met=m.metrics||{};
  var h="<div class='perm-note'>文书归档/会议/运营方针均走服务端权限与审计；人员档案直接编辑禁用；会议战斗状态拦截暂缓。</div>";
  h+="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>"+(met.archiveCount||0)+"</div><div class='k-sub'>归档</div></div><div class='kpi-sm'><div class='k-val'>"+(met.meetingCount||0)+"</div><div class='k-sub'>会议</div></div><div class='kpi-sm'><div class='k-val'>"+(met.policyCount||0)+"</div><div class='k-sub'>方针</div></div><div class='kpi-sm'><div class='k-val'>"+(met.auditCount||0)+"</div><div class='k-sub'>审计</div></div></div>";
  h+="<div class='notice-form-shell'><input id='mgmt_title' placeholder='标题'><input id='mgmt_source_type' placeholder='来源类型 sourceType'><input id='mgmt_source_id' placeholder='来源ID sourceId'><input id='mgmt_start' placeholder='会议时间戳 startAt，可空'><input id='mgmt_location' placeholder='会议地点'><input id='mgmt_participants' placeholder='参与部门或玩家，逗号分隔'><textarea id='mgmt_body' placeholder='正文/说明'></textarea><label class='notice-check'><input id='mgmt_high' type='checkbox'> 高优先级会议（设施主管/OP）</label><div>";
  h+="<button class='head-btn accent' "+(m.canCreateArchive?'':'disabled')+" onclick='mgmtCreateArchive()'>创建文书归档</button> <button class='head-btn ghost' "+(m.canCreateNormalMeeting?'':'disabled')+" onclick='mgmtCreateMeeting()'>创建会议邀请</button> <button class='head-btn ghost' "+(m.canPublishPolicy?'':'disabled')+" onclick='mgmtPublishPolicy()'>发布运营方针</button></div></div>";
  h+="<table class='ptable'><tr><th>ID</th><th>类型</th><th>标题/来源</th><th>会议/回执</th><th style='text-align:right'>状态/动作</th></tr>";
  if(rec.length===0) h+="<tr><td colspan='5'>暂无文书/会议/运营方针记录</td></tr>";
  for(var i=0;i<rec.length&&i<18;i++){ var r=rec[i]; var act=''; if(r.type==='ADMIN_ARCHIVE'&&r.status!=='archived'&&m.canArchiveAdminRecord) act+=" <button class='mini-btn' onclick='pushAction({t:\"mgmt_archive_archive\",id:\""+esc(r.id||'')+"\"})'>归档</button>"; if(r.type==='MEETING_INVITE') act+=" <button class='mini-btn' onclick='pushAction({t:\"mgmt_meeting_respond\",id:\""+esc(r.id||'')+"\",response:\"accepted\"})'>回执参加</button><button class='mini-btn' onclick='pushAction({t:\"mgmt_meeting_respond\",id:\""+esc(r.id||'')+"\",response:\"declined\"})'>回执拒绝</button>"; if(r.type==='OPERATING_POLICY'&&r.status==='active'&&m.canClosePolicy) act+=" <button class='mini-btn' onclick='pushAction({t:\"mgmt_policy_close\",id:\""+esc(r.id||'')+"\"})'>结束方针</button>"; h+="<tr><td>"+esc(r.id||'')+"</td><td>"+esc(r.type||'')+"</td><td><b>"+esc(r.title||'')+"</b><br><span class='muted'>"+esc(r.sourceType||'')+" / "+esc(r.sourceId||'')+"</span></td><td>"+esc(r.location||'')+" "+esc(r.participants||'')+"<br><span class='muted'>"+esc(r.receiptActorName||'')+" "+esc(r.receiptNote||r.combatInterceptionNote||'')+"</span></td><td style='text-align:right'>"+esc(r.status||'')+act+"</td></tr>"; }
  h+="</table>";
  return h;
}

function mgmtPayloadBase(){ return {title:getVal('mgmt_title'),body:getVal('mgmt_body')}; }
function mgmtCreateArchive(){ var p=mgmtPayloadBase(); p.t='mgmt_archive_create'; p.sourceType=getVal('mgmt_source_type')||'manual'; p.sourceId=getVal('mgmt_source_id')||''; pushAction(p); }
function mgmtCreateMeeting(){ var p=mgmtPayloadBase(); p.t='mgmt_meeting_create'; p.startAt=Number(getVal('mgmt_start')||0); p.location=getVal('mgmt_location')||''; p.participants=getVal('mgmt_participants')||''; var hi=$('mgmt_high'); p.highPriority=hi?hi.checked:false; pushAction(p); }
function mgmtPublishPolicy(){ var p=mgmtPayloadBase(); p.t='mgmt_policy_publish'; pushAction(p); }
function renderDepartmentApplicationWindow(){
  var apps=(terminalState.departmentApplications&&terminalState.departmentApplications.items)||[];
  var queue=(terminalState.pendingCardQueue&&terminalState.pendingCardQueue.items)||[];
  var audit=(terminalState.operationAudit&&terminalState.operationAudit.items)||[];
  var h="<div class='perm-note'>入职申请按目标部门4级主管→后勤4编制确认路由；设施主管5/OP可完成两步但留审计。通过后只生成目标1级待换卡记录；mark_completed为人工确认，不检查/不写ID卡NBT。</div>";
  h+="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>"+apps.length+"</div><div class='k-sub'>申请</div></div><div class='kpi-sm'><div class='k-val'>"+queue.length+"</div><div class='k-sub'>待换卡</div></div><div class='kpi-sm'><div class='k-val'>"+audit.length+"</div><div class='k-sub'>审计</div></div></div>";
  h+="<table class='ptable'><tr><th>申请ID</th><th>申请人</th><th>目标</th><th style='text-align:right'>状态/动作</th></tr>";
  if(apps.length===0) h+="<tr><td colspan='4'>暂无申请记录</td></tr>";
  for(var i=0;i<apps.length&&i<8;i++){ var a=apps[i]; var step=''; if(a.steps){ for(var si=0;si<a.steps.length;si++){ if(a.steps[si].status==='dept_review'||a.steps[si].status==='logistics_confirm'||a.steps[si].status==='submitted'){ step=a.steps[si].role||a.steps[si].stepId; break; } } } h+="<tr><td>"+esc(a.id)+"</td><td>"+esc(a.applicantName||'')+"</td><td>"+esc(a.targetDepartment||'')+" / "+esc(a.targetPosition||'')+" Lv."+esc(a.targetLevel||1)+"<br><span class='muted'>"+esc(a.reason||'')+"</span></td><td style='text-align:right'>"+esc(a.status||'')+" "+esc(step)+" <button class='mini-btn' onclick='deptApprovePrompt(\""+esc(a.id)+"\")'>批准</button><button class='mini-btn' onclick='deptRejectPrompt(\""+esc(a.id)+"\")'>驳回</button></td></tr>"; }
  h+="</table><table class='ptable'><tr><th>队列ID</th><th>玩家</th><th>目标卡</th><th style='text-align:right'>状态</th></tr>";
  if(queue.length===0) h+="<tr><td colspan='4'>暂无待换卡记录</td></tr>";
  for(var q=0;q<queue.length&&q<8;q++){ var c=queue[q]; h+="<tr><td>"+esc(c.id)+"</td><td>"+esc(c.targetName||'')+"</td><td>"+esc(c.targetDepartment||'')+" / "+esc(c.targetPosition||'')+" / Lv."+esc(c.targetLevel||1)+"</td><td style='text-align:right'>"+esc(c.status||'')+" <button class='mini-btn' onclick='pushAction({t:\"card_queue_mark_completed\",id:\""+esc(c.id)+"\"})'>标记完成</button></td></tr>"; }
  h+="</table>"; return h;
}
function terminalTextModal(title, placeholder, cb){
  var old=document.getElementById('terminalTextModal'); if(old&&old.parentNode) old.parentNode.removeChild(old);
  var wrap=document.createElement('div'); wrap.id='terminalTextModal';
  wrap.style.cssText='position:absolute;left:0;top:0;right:0;bottom:0;z-index:9999;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;';
  wrap.innerHTML='<div style="width:420px;max-width:86%;background:#101722;border:1px solid #7fdcff;padding:14px;box-shadow:0 0 24px rgba(127,220,255,.25)"><div style="font-weight:bold;margin-bottom:8px">'+esc(title||'输入')+'</div><textarea id="terminalTextModalInput" style="width:100%;height:96px;box-sizing:border-box;background:#071018;color:#dff7ff;border:1px solid #36566a" placeholder="'+esc(placeholder||'')+'"></textarea><div style="text-align:right;margin-top:10px"><button class="mini-btn" id="terminalTextModalCancel">取消</button> <button class="mini-btn" id="terminalTextModalOk">确定</button></div></div>';
  document.body.appendChild(wrap);
  var input=document.getElementById('terminalTextModalInput'); if(input) input.focus();
  document.getElementById('terminalTextModalCancel').onclick=function(){ if(wrap.parentNode) wrap.parentNode.removeChild(wrap); };
  document.getElementById('terminalTextModalOk').onclick=function(){ var v=input?input.value:''; if(wrap.parentNode) wrap.parentNode.removeChild(wrap); cb(v); };
}
function deptApprovePrompt(id){ terminalTextModal('审批意见（目标部门主管通过后流转后勤确认；设施主管/OP可完成两步）','可留空',function(comment){ pushAction({t:'dept_app_decide',id:id,decision:'approve',comment:comment||''}); }); }
function deptRejectPrompt(id){ terminalTextModal('驳回原因','请输入驳回原因',function(reason){ pushAction({t:'dept_app_decide',id:id,decision:'reject',reason:reason||''}); }); }
function researchStageText(v){ if(v<=0) return '未知'; if(v<50) return '基础'; if(v<100) return '深入'; return '完成'; }
function maintenanceStatusText(s){ if(s==='urgent') return '紧急'; if(s==='due') return '需要维护'; if(s==='ok') return '正常'; return '未确认'; }
function renderResearchWindow(){
  var data=terminalState.researchDepartment||{};
  var sum=data.researchSummary||{};
  var anomalies=data.researchAnomalies||[];
  var reports=data.researchReports||[];
  var docs=(terminalState.researchDocuments&&terminalState.researchDocuments.items)||[];
  var queue=data.maintenanceQueue||[];
  var stats=data.reportStatusStats||{};
  var canDetail=sum.canViewSensitiveDetails===true || terminalState.canViewResearchDetails===true;
  var h="<div class='perm-note'>科研终端：全局查询异常文档/短讯/实验申请状态；报告审核仅设施主管5/OP；不包含异常实时参数页面。</div>";
  h+="<div class='research-kpis'><div class='research-kpi'><b>"+(sum.onlineResearchers||0)+"</b><span>在线科研人数</span></div><div class='research-kpi'><b>"+(sum.anomalyCount||anomalies.length)+"</b><span>异常物总数</span></div><div class='research-kpi warn'><b>"+(sum.lowStabilityCount||0)+"</b><span>低稳定度</span></div><div class='research-kpi'><b>"+(sum.pendingReports||0)+"</b><span>待审核报告</span></div></div>";
  if(!canDetail){ h+="<div class='empty-tip'>当前身份无科研详情权限：仅显示基础统计，不展示异常物细节、维护队列或报告正文。</div>"; return h; }
  h+="<table class='ptable research-table'><tr><th>异常物</th><th>隔间</th><th>研究</th><th>稳定度</th><th style='text-align:right'>维护</th></tr>";
  if(anomalies.length===0){ h+="<tr><td colspan='5'>暂无异常物研究数据。</td></tr>"; }
  for(var i=0;i<anomalies.length && i<12;i++){ var a=anomalies[i]; var sp=(typeof a.stabilityPercent==='number')?a.stabilityPercent:pct(a.stability,a.maxStability); var stage=a.researchStage||researchStageText(a.researchProgress||0); var cls=sp<30?'danger':(sp<50?'warn':''); h+="<tr class='"+cls+"'><td><b>"+esc(a.displayName||a.codexId||a.instanceId)+"</b><small>"+esc(a.instanceId||'')+"</small></td><td>"+esc(a.slotId||'-')+"</td><td><span class='stage-badge'>"+esc(stage)+"</span> "+esc(a.researchProgress||0)+"%</td><td>"+sp+"%</td><td style='text-align:right'>"+maintenanceStatusText(a.maintenanceStatus)+"</td></tr>"; }
  h+="</table>";
  h+="<div class='research-grid'><div class='research-card'><h3>维护队列</h3>";
  if(queue.length===0){ h+="<div class='empty-tip'>暂无需要维护项目。</div>"; }
  for(var q=0;q<queue.length && q<6;q++){ var m=queue[q]; var b=m.boundQte||{}; var bs=(typeof b==='object')?(b.summary||b.status||'未绑定/未验证'):b; h+="<div class='maint-row'><b>"+esc(m.displayName||m.instanceId)+"</b><span>"+maintenanceStatusText(m.maintenanceStatus)+" · 稳定 "+esc(m.stabilityPercent)+"%</span><small>最近维护 "+esc(m.lastMaintenanceText||formatDateTime(m.lastMaintenanceAt))+" · QTE "+esc(bs)+"</small></div>"; }
  h+="</div><div class='research-card'><h3>报告状态 / 入口</h3><div class='report-stat-line'>pending "+(stats.pending||0)+" · approved "+(stats.approved||0)+" · rejected "+(stats.rejected||0)+" · archived "+(stats.archived||0)+"</div>";
  h+="<button class='mini-btn' onclick='openWin(\"reports\")'>打开实验报告收件箱</button>";
  if(sum.canReviewReports===true||terminalState.canReviewReports===true){ h+="<div class='perm-note'>具备五级审核权限，可在设施终端收件箱执行通过/驳回/归档。</div>"; } else { h+="<div class='perm-note'>当前身份不可审核，仅查看报告状态。</div>"; }
  for(var r=0;r<reports.length && r<5;r++){ var rr=reports[r]; h+="<div class='report-mini'><b>"+esc(rr.title||rr.reportId)+"</b><span>"+esc(rr.status||'pending')+" · "+esc(rr.authorName||'')+"</span></div>"; }
  h+="</div><div class='research-card'><h3>科研文档 / 学术短讯</h3><div class='perm-note'>异常文档有修订历史；短讯提交后不可编辑/删除，用新短讯更正。</div>";
  if(docs.length===0){ h+="<div class='empty-tip'>暂无科研文档或学术短讯。</div>"; }
  for(var d=0;d<docs.length && d<8;d++){ var doc=docs[d]; h+="<div class='report-mini'><b>"+esc(doc.title||doc.id)+"</b><span>"+esc(doc.type||'')+" · "+esc(doc.status||'draft')+" · v"+esc(doc.version||1)+" · 异常 "+esc(doc.anomalyInstanceId||'-')+"</span><small>target "+esc(doc.targetType||'-')+" / report "+esc(doc.reportId||'-')+" / doc "+esc(doc.documentId||'-')+" / revisions "+(((doc.revisions)||[]).length)+"</small>"; if((terminalState.isOp||terminalState.canReviewReports) && doc.type==='anomaly_document' && doc.status!=='archived'){ h+="<button class='mini-btn' onclick='researchDocArchivePrompt(\""+esc(doc.id)+"\")'>归档纠错</button>"; } h+="</div>"; }
  var apps=(terminalState.departmentApplications&&terminalState.departmentApplications.items)||[]; h+="<h3>实验/物资申请状态</h3>"; var ac=0; for(var ai=0;ai<apps.length && ac<8;ai++){ var ap=apps[ai]; if(ap.type==='RESEARCH_EXPERIMENT'||ap.type==='RESEARCH_PURCHASE'){ ac++; h+="<div class='report-mini'><b>"+esc(ap.title||ap.id)+"</b><span>"+esc(ap.type)+" · "+esc(ap.status)+" · 风险 "+esc(ap.riskLevel||'low')+"</span><small>异常 "+esc(ap.relatedAnomalyId||'-')+" · route "+esc(ap.approvalRoute||'-')+"</small></div>"; }} if(!ac){ h+="<div class='empty-tip'>暂无实验/物资申请。</div>"; }
  h+="</div></div>";
  return h;
}
function noticeItems(){ var d=terminalState.notices||{}; return d.items||[]; }
function noticeCanPublish(){ return terminalState.canPublishNotices===true || (terminalState.notices&&terminalState.notices.canPublish===true); }
function noticeCanManage(){ return terminalState.canManageNotices===true || (terminalState.notices&&terminalState.notices.canManage===true); }
function noticeCanDelete(){ return terminalState.isOp===true || (terminalState.notices&&terminalState.notices.canHardDelete===true); }
function findNotice(id){ var list=noticeItems(); for(var i=0;i<list.length;i++){ if(list[i].id===id) return list[i]; } return null; }
function noticeStatusText(s){ if(s==='archived') return '已归档'; if(s==='expired') return '已过期'; if(s==='deleted') return '已删除'; return '发布中'; }
function homeNoticeItems(){
  var all=noticeItems(), active=[];
  for(var i=0;i<all.length;i++){
    var status=all[i].status||'active';
    if(status==='active'||status==='published') active.push(all[i]);
  }
  active.sort(function(a,b){ if(!!a.pinned!==!!b.pinned) return a.pinned?-1:1; return Number(b.publishedAt||0)-Number(a.publishedAt||0); });
  return active;
}
function updateHomeNotices(){
  var list=homeNoticeItems(), count=$('homeNoticeCount'), box=$('homeNoticeList');
  if(count) count.textContent=pad2(list.length);
  if(!box) return;
  if(list.length===0){ box.innerHTML="<div class='empty-tip'>暂无有效设施公告</div>"; return; }
  var h='', max=Math.min(4,list.length);
  for(var i=0;i<max;i++){
    var n=list[i];
    h+="<button class='home-notice-item"+(n.pinned?' pinned':'')+"' onclick='openHomeNotice(\""+esc(n.id)+"\")'>";
    h+="<span class='home-notice-title'>"+(n.pinned?'★ ':'')+esc(n.title||'未命名公告')+"</span>";
    h+="<span class='home-notice-meta'>"+esc(n.targetDepartment||'全部门')+" · "+esc(n.publishedAtText||formatDateTime(n.publishedAt))+"</span></button>";
  }
  if(list.length>max) h+="<button class='home-notice-more' data-open-win='notice'>另有 "+(list.length-max)+" 条公告 · 查看全部</button>";
  box.innerHTML=h;
  var more=box.querySelector('.home-notice-more'); if(more) more.addEventListener('click',function(){openWin('notice');});
}
function openHomeNotice(id){ selectedHomeNoticeId=id||''; refreshWindow('noticeDetail'); openWin('noticeDetail'); }
function renderHomeNoticeDetail(){
  var n=findNotice(selectedHomeNoticeId)||homeNoticeItems()[0]||null;
  if(!n) return "<div class='empty-tip'>该公告不存在或已失效。</div>";
  var h="<div class='notice-detail standalone-notice-detail'><div class='notice-detail-head'><h3>"+(n.pinned?'★ ':'')+esc(n.title||'')+"</h3><span class='status-pill'>"+noticeStatusText(n.status)+"</span></div>";
  h+="<div class='notice-meta'><span>范围 "+esc(n.targetDepartment||'全部门')+"</span><span>发布 "+esc(n.publisherName||n.authorName||'-')+"</span><span>时间 "+esc(n.publishedAtText||formatDateTime(n.publishedAt))+"</span><span>有效至 "+esc(n.expiresAtText||'永久')+"</span></div>";
  h+="<pre class='notice-body'>"+esc(n.body||n.content||'')+"</pre></div>";
  return h;
}
function renderNoticeWindow(){
  var data=terminalState.notices||{items:[]}; var list=noticeItems(); var picked=findNotice(data.selectedId)||list[0]||null;
  var h="<div class='perm-note'>公告权限：书记官3仅普通且仅本人编辑/撤回/归档；部门通告仅目标部门主管4；设施/紧急仅设施主管5或OP；硬删除仅OP。旧公告缺level按普通兼容但无owner不可被书记官夺取。</div>";
  h+="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>"+(data.activeCount||0)+"</div><div class='k-sub'>发布中</div></div><div class='kpi-sm'><div class='k-val'>"+(data.archivedCount||0)+"</div><div class='k-sub'>已归档</div></div><div class='kpi-sm'><div class='k-val'>"+(data.expiredCount||0)+"</div><div class='k-sub'>已过期</div></div></div>";
  h+="<div class='notice-console'><div class='notice-list'>";
  if(list.length===0){ h+="<div class='empty-tip'>暂无公告。</div>"; }
  for(var i=0;i<list.length;i++){ var n=list[i]; var cls='notice-row '+(picked&&picked.id===n.id?'active ':'')+(n.pinned?'pinned ':'')+(n.status||'active');
    h+="<button class='"+cls+"' onclick='selectNotice(\""+esc(n.id)+"\")'><b>"+(n.pinned?'★ ':'')+esc(n.title||'未命名公告')+"</b><span>"+esc(n.targetDepartment||'全部门')+" · "+noticeStatusText(n.status)+"</span><small>"+esc(n.publishedAtText||formatDateTime(n.publishedAt))+" / 至 "+esc(n.expiresAtText||'永久')+"</small></button>";
  }
  h+="</div><div class='notice-detail'>";
  if(!picked){ h+="<div class='empty-tip'>选择左侧公告查看详情，或填写下方表单发布。</div>"; }
  else { h+="<div class='notice-detail-head'><h3>"+(picked.pinned?'★ ':'')+esc(picked.title||'')+"</h3><span class='status-pill'>"+noticeStatusText(picked.status)+"</span></div>";
    h+="<div class='notice-meta'><span>范围 "+esc(picked.targetDepartment||'全部门')+"</span><span>发布 "+esc(picked.publisherName||picked.authorName||'-')+"</span><span>时间 "+esc(picked.publishedAtText||formatDateTime(picked.publishedAt))+"</span><span>有效至 "+esc(picked.expiresAtText||'永久')+"</span></div>";
    h+="<pre class='notice-body'>"+esc(picked.body||picked.content||'')+"</pre>";
    h+="<div class='notice-actions'>";
    if(noticeCanManage()){ h+="<button class='mini-btn' onclick='loadNoticeToForm(\""+esc(picked.id)+"\")'>载入编辑</button><button class='mini-btn' onclick='noticeAction(\""+esc(picked.id)+"\",\""+(picked.pinned?'notice_unpin':'notice_pin')+"\")'>"+(picked.pinned?'取消置顶':'置顶')+"</button><button class='mini-btn' onclick='noticeAction(\""+esc(picked.id)+"\",\"notice_archive\")'>归档</button><button class='mini-btn' onclick='noticeAction(\""+esc(picked.id)+"\",\"notice_expire\")'>标记过期</button>"; }
    if(noticeCanDelete()){ h+="<button class='mini-btn danger' onclick='noticeAction(\""+esc(picked.id)+"\",\"notice_delete\")'>硬删除</button>"; }
    h+="</div>";
  }
  h+="</div></div>";
  var disabled=noticeCanPublish()?"":"disabled"; var manageHint=noticeCanPublish()?"":"<div class='perm-note'>当前身份无公告发布权限。</div>";
  h+="<div class='notice-form-shell'><input id='notice_id' placeholder='编辑ID（发布新公告请留空）'><input id='notice_title' maxlength='80' placeholder='标题 1-80字' "+disabled+"><select id='notice_level' "+disabled+"><option value='ordinary'>普通行政通知</option><option value='department'>二级部门通告</option><option value='facility'>一级设施广播</option><option value='emergency'>紧急警戒/封锁/疏散</option></select><input id='notice_target' placeholder='目标部门/ALL/全部门' "+disabled+"><input id='notice_expire_hours' placeholder='有效小时，留空=永久' "+disabled+"><label class='notice-check'><input id='notice_pinned' type='checkbox' "+disabled+"> 置顶</label><textarea id='notice_content' maxlength='4000' placeholder='正文 1-4000字' "+disabled+"></textarea><div><button class='head-btn accent' "+disabled+" onclick='noticePublishShell()'>发布新公告</button> <button class='head-btn ghost' "+(noticeCanManage()?"":"disabled")+" onclick='noticeUpdateShell()'>保存编辑</button></div>"+manageHint+"</div>";
  return h;
}
function selectNotice(id){ if(!terminalState.notices) terminalState.notices={items:[]}; terminalState.notices.selectedId=id||''; refreshWindow('notice'); }
function loadNoticeToForm(id){ var n=findNotice(id); if(!n) return; setVal('notice_id',n.id); setVal('notice_title',n.title||''); setVal('notice_level',n.noticeLevel||'ordinary'); setVal('notice_target',n.targetDepartment||'ALL'); setVal('notice_content',n.body||n.content||''); setVal('notice_expire_hours',''); var p=$('notice_pinned'); if(p) p.checked=n.pinned===true; }
function noticePayload(t){ var hours=Number(getVal('notice_expire_hours')||0); var p=$('notice_pinned'); return {t:t,id:getVal('notice_id'),title:getVal('notice_title'),content:getVal('notice_content'),noticeLevel:getVal('notice_level')||'ordinary',targetDepartment:getVal('notice_target')||'ALL',expireHours:hours,pinned:p?p.checked:false}; }
function hqItems(){ var hq=terminalState.hqTasks||{}; return hq.items||hq.tasks||[]; }
function hqCanEdit(){ return terminalState.isOp === true && (terminalState.canEditHQTasks === true || (terminalState.hqTaskEditor&&terminalState.hqTaskEditor.canEdit===true)); }
function statusText(s){ if(s==="claimable") return "可结算"; if(s==="completed") return "已完成"; if(s==="failed") return "已失败"; return "进行中"; }
function eventText(e){ var m={manual:"手动",anomaly_maintenance:"异常物维护",report_submitted:"报告提交",report_approved:"报告审核",slot_unlocked:"隔间解锁",anomaly_contained:"异常物放置",notice_published:"公告发布",fund_reached:"资金达标"}; return m[e]||e||"手动"; }
function renderHqTaskWindow(){
  var items=hqItems(); var active=0, done=0; for(var i=0;i<items.length;i++){ if(items[i].status==="completed") done++; else if(items[i].status!=="failed") active++; }
  var h="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>"+items.length+"</div><div class='k-sub'>总部下发</div></div><div class='kpi-sm'><div class='k-val'>"+active+"</div><div class='k-sub'>进行中</div></div><div class='kpi-sm'><div class='k-val'>"+done+"</div><div class='k-sub'>已完成</div></div></div>";
  if(hqCanEdit()) h+="<div class='editor-entry'><button class='head-btn accent' onclick='openWin(\"hqEditor\")'>任务编辑器</button><span>OP ONLY · 保存/删除/强制完成/强制失败/补足</span></div>";
  h+="<div class='hq-list'>";
  if(items.length===0){ h+="<div class='empty-tip'>暂无总部任务</div>"; }
  for(var j=0;j<items.length;j++){ var t=items[j]; var p=pct(t.progress,t.goal); var cls="hq-task "+(t.status||"active");
    h+="<div class='"+cls+"' onclick='selectHqTask(\""+esc(t.id)+"\")'><div class='hq-task-head'><b>"+esc(t.title||t.name||t.id)+"</b><span>"+statusText(t.status)+"</span></div>";
    h+="<div class='hq-task-desc'>"+esc(t.desc||t.description||"")+"</div><div class='hq-task-meta'><span>事件 "+esc(eventText(t.eventType||"manual"))+"</span><span>部门 "+esc(t.department||"ALL")+"</span><span>等级 "+esc(t.level||1)+"</span><span>奖励 ¥"+esc(t.rewardFunds||0)+"</span></div>";
    h+="<div class='hq-progress'><span style='width:"+p+"%'></span></div><div class='hq-progress-text'>"+esc(t.progress||0)+" / "+esc(t.goal||1)+" · "+p+"%</div>";
    if((t.status||"")==="claimable") h+="<button class='mini-btn hq-claim-btn' onclick='event.stopPropagation();claimHqTask(\""+esc(t.id)+"\")'>结算奖励</button>";
    h+="</div>";
  }
  h+="</div>"; return h;
}
function renderHqEditorWindow(){
  if(!hqCanEdit()) return "<div class='perm-note'>总部任务编辑器仅 OP 可用。</div>";
  var items=hqItems(); var opts="<option value=''>新建任务</option>"; for(var i=0;i<items.length;i++){ opts+="<option value='"+esc(items[i].id)+"'>"+esc(items[i].title||items[i].id)+"</option>"; }
  var h="<div class='hq-editor'><label>选择任务<select id='hq_edit_pick' onchange='loadHqEditorSelection()'>"+opts+"</select></label>";
  h+="<label>ID<input id='hq_edit_id' placeholder='留空则新建自动生成'></label><label>标题<input id='hq_edit_title'></label><label>描述<textarea id='hq_edit_desc'></textarea></label>";
  h+="<label>状态<select id='hq_edit_status'><option value='active'>进行中</option><option value='claimable'>可结算</option><option value='completed'>已完成</option><option value='failed'>已失败</option></select></label>";
  h+="<label>推进事件<select id='hq_edit_event'><option value='manual'>手动</option><option value='anomaly_maintenance'>异常物维护</option><option value='report_submitted'>报告提交</option><option value='report_approved'>报告审核</option><option value='slot_unlocked'>隔间解锁</option><option value='anomaly_contained'>异常物放置</option><option value='notice_published'>公告发布</option><option value='fund_reached'>资金达标</option></select></label>";
  h+="<div class='form-grid'><label>进度<input id='hq_edit_progress' value='0'></label><label>目标<input id='hq_edit_goal' value='1'></label><label>奖励资金<input id='hq_edit_reward' value='0'></label><label>部门<input id='hq_edit_dept' value='ALL'></label><label>等级<input id='hq_edit_level' value='1'></label></div>";
  h+="<div class='perm-note'>所有编辑动作都会发送到服务端并由 OP 权限再次校验。</div></div>"; return h;
}
function findHqTask(id){ var items=hqItems(); for(var i=0;i<items.length;i++) if(items[i].id===id) return items[i]; return null; }
function selectHqTask(id){ if(!hqCanEdit()) return; openWin("hqEditor"); setTimeout(function(){ var p=$("hq_edit_pick"); if(p){ p.value=id; loadHqEditorSelection(); } }, 30); }
function loadHqEditorSelection(){ var p=$("hq_edit_pick"); var t=p?findHqTask(p.value):null; setVal("hq_edit_id", t?t.id:""); setVal("hq_edit_title", t?(t.title||t.name):""); setVal("hq_edit_desc", t?(t.desc||t.description):""); setVal("hq_edit_status", t?(t.status||"active"):"active"); setVal("hq_edit_event", t?(t.eventType||"manual"):"manual"); setVal("hq_edit_progress", t?t.progress:0); setVal("hq_edit_goal", t?t.goal:1); setVal("hq_edit_reward", t?t.rewardFunds:0); setVal("hq_edit_dept", t?(t.department||"ALL"):"ALL"); setVal("hq_edit_level", t?(t.level||1):1); }
function setVal(id,v){ var n=$(id); if(n) n.value=(v===undefined||v===null)?"":String(v); }
function getVal(id){ var n=$(id); return n?n.value:""; }
function hqEditorAction(kind){ if(!hqCanEdit()) return; var id=getVal("hq_edit_id"); if(kind==="save"){ pushAction({t:"hq_task_save",id:id,title:getVal("hq_edit_title"),desc:getVal("hq_edit_desc"),status:getVal("hq_edit_status"),eventType:getVal("hq_edit_event"),progress:Number(getVal("hq_edit_progress")||0),goal:Number(getVal("hq_edit_goal")||1),rewardFunds:Number(getVal("hq_edit_reward")||0),department:getVal("hq_edit_dept"),level:Number(getVal("hq_edit_level")||1)}); } else if(id || kind==="hq_task_fill") { pushAction({t:kind,id:id}); } }
function claimHqTask(id){ if(!id) return; pushAction({t:"hq_task_claim",id:id}); }
function selectReport(id){ selectedReportId=id||""; refreshWindow("reports"); }
function reportApprove(id, delta){ if(!id) return; pushAction({t:"report_approve", reportId:id, researchDelta: delta || 5 }); }
function reportReject(id, reason){ if(!id) return; pushAction({t:"report_reject", reportId:id, reason: reason || "" }); }
function reportRejectPrompt(id){ terminalTextModal('驳回原因','请输入驳回原因',function(reason){ reportReject(id, reason||''); }); }
function reportArchive(id){ if(!id) return; pushAction({t:"report_archive", reportId:id }); }
function researchDocArchivePrompt(id){ terminalTextModal('归档纠错原因','说明归档原因',function(reason){ pushAction({t:'research_doc_archive', id:id, reason:reason||''}); }); }

// ---- 窗口管理 ----
var winLayer = null;
var taskbarTasks = null;
var wins = [];
var zTop = 10;

function inlineStyleValue(node, propertyName, fallback) {
  try {
    var raw=node&&node.getAttribute?String(node.getAttribute("style")||""):"";
    var parts=raw.split(";");
    for(var i=0;i<parts.length;i++){
      var pair=parts[i].split(":");
      if(pair.length>=2&&pair[0].replace(/\s/g,"")===propertyName) return pair.slice(1).join(":").replace(/^\s+|\s+$/g,"");
    }
  } catch(ignored) {}
  return fallback;
}
function setInlineStyle(node, propertyName, value) {
  if(!node||!node.getAttribute) return;
  try{
    var raw=String(node.getAttribute("style")||""), parts=raw.split(";"), kept=[];
    for(var i=0;i<parts.length;i++){
      var part=parts[i], idx=part.indexOf(":");
      if(idx<0) continue;
      var key=part.substring(0,idx).replace(/\s/g,"").toLowerCase();
      if(key!==propertyName.toLowerCase()) kept.push(part);
    }
    if(value!==null&&value!=="") kept.push(propertyName+":"+value);
    node.setAttribute("style",kept.join(";")+";");
  }catch(ignored){}
}
function terminalViewportSize() {
  var bw = Number(document.body && document.body.clientWidth);
  var bh = Number(document.body && document.body.clientHeight);
  // ApricityUI不稳定地暴露clientWidth/clientHeight，优先回退到根元素实际布局框。
  if (!(bw > 100 && bw < 10000) || !(bh > 100 && bh < 10000)) {
    try {
      var rootBox = document.documentElement && document.documentElement.getBoundingClientRect();
      if (rootBox) {
        if (!(bw > 100 && bw < 10000)) bw = Number(rootBox.width);
        if (!(bh > 100 && bh < 10000)) bh = Number(rootBox.height);
      }
    } catch (ignored) {}
  }
  if (!(bw > 100 && bw < 10000)) bw = 1920;
  if (!(bh > 100 && bh < 10000)) bh = 1080;
  return { width:bw, height:bh };
}

function openWin(key) {
  var w = WIN[key];
  if (!w) return;
  for (var i = 0; i < wins.length; i++) { if (wins[i].key === key) { focusWin(wins[i]); return; } }
  var el = document.createElement("div");
  el.className = "win";
  el.innerHTML =
    "<div class='win-titlebar'><div class='win-title'><span class='glyph svg-glyph'>" + ico(w.icon) + "</span><span>" + w.title + "</span></div>" +
    "<div class='win-btns'><button class='win-btn min'>−</button><button class='win-btn max'>□</button><button class='win-btn close'>×</button></div></div>" +
    "<div class='win-body'>" + w.content() + "</div><div class='win-foot'></div>" +
    "<div class='resize-handle resize-n' data-dir='n'></div><div class='resize-handle resize-s' data-dir='s'></div>" +
    "<div class='resize-handle resize-e' data-dir='e'></div><div class='resize-handle resize-w' data-dir='w'></div>" +
    "<div class='resize-handle resize-ne' data-dir='ne'></div><div class='resize-handle resize-nw' data-dir='nw'></div>" +
    "<div class='resize-handle resize-se' data-dir='se'></div><div class='resize-handle resize-sw' data-dir='sw'></div>";
  var foot = el.querySelector(".win-foot");
  var btns = w.btn || [];
  for (var j = 0; j < btns.length; j++) {
    if ((btns[j][2] === "openHqEditor" || key === "hqEditor") && !hqCanEdit()) continue;
    var b = document.createElement("button");
    b.className = "head-btn " + btns[j][1];
    b.textContent = btns[j][0];
    if (btns[j][2]) {
      (function(actionName, btnEl){ btnEl.addEventListener("click", function(){ runHeadAction(actionName); }); })(btns[j][2], b);
    }
    foot.appendChild(b);
  }
  var off = wins.length * 10;
  // 默认窗口约为旧尺寸 1.5 倍: 600x330, 接近 640x360 固定视口的完整应用窗口
  var viewport = terminalViewportSize();
  var bw = viewport.width;
  var bh = viewport.height;
  var defaultW = Math.min(600, bw - 24);
  var defaultH = Math.min(330, bh - 42);
  el.style.width = defaultW + "px";
  el.style.height = defaultH + "px";
  el.style.left = Math.max(8, bw / 2 - defaultW / 2 + off) + "px";
  el.style.top = Math.max(8, bh / 2 - defaultH / 2 + off) + "px";
  winLayer.appendChild(el);
   if (key === 'dept') bindDepartmentPhase2Tabs(el.querySelector('[data-dept-shell]'));
  var rec = { key: key, el: el, taskBtn: null, normalBox: null };
  wins.push(rec);

  // AUI标题栏控制使用mousedown；它比动态元素的click分派稳定，并先于拖拽处理。
  (function(rec2, el2) {
    var minBtn=el2.querySelector(".win-btn.min"), maxBtn=el2.querySelector(".win-btn.max"), closeBtn=el2.querySelector(".win-btn.close");
    if(minBtn) minBtn.addEventListener("mousedown",function(e){if(e.stopPropagation)e.stopPropagation();minimizeWin(rec2);});
    if(maxBtn) maxBtn.addEventListener("mousedown",function(e){if(e.stopPropagation)e.stopPropagation();toggleMaximizeWin(rec2);});
    if(closeBtn) closeBtn.addEventListener("mousedown",function(e){if(e.stopPropagation)e.stopPropagation();closeWin(rec2);});
    el2.addEventListener("mousedown", function() { focusWin(rec2); });
  })(rec, el);
  try { bindDrag(el, el.querySelector(".win-titlebar")); } catch (dragError) {}
  try { bindResize(el); } catch (resizeError) {}

  // 参考电脑KJS：任务按钮预先存在于HTML，窗口打开时只切换display和active。
  var tb=$("taskWin_"+key);
  if(tb){
    rec.taskBtn=tb;
    setInlineStyle(tb,"display","flex");
  }
  focusWin(rec);
}

function findAnomalyInstance(id){
  var list=(terminalState.anomalies&&terminalState.anomalies.instances)||[];
  for(var i=0;i<list.length;i++) if(list[i].instanceId===id) return list[i];
  return null;
}
function renderAnomalyOverviewWindow(){
  var a=terminalState.anomalies||{codexStatus:{loaded:0,failed:0},instances:[],slots:[]},st=a.codexStatus||{},inst=a.instances||[],slots=a.slots||[],byId={},locked=0;
  for(var i=0;i<inst.length;i++) byId[inst[i].instanceId]=inst[i];
  for(var s=0;s<slots.length;s++) if(!slots[s].unlocked) locked++;
  var h="<div class='kpi-row-sm'><div class='kpi-sm'><div class='k-val'>"+inst.length+"</div><div class='k-sub'>已收容异常物</div></div><div class='kpi-sm'><div class='k-val'>"+(slots.length-locked)+"/"+slots.length+"</div><div class='k-sub'>已解锁隔间</div></div><div class='kpi-sm'><div class='k-val'>"+(st.loaded||0)+"</div><div class='k-sub'>档案加载 · 失败 "+(st.failed||0)+"</div></div></div>";
  h+="<div class='perm-note'>异常管理：OP / 管理4+ / 科研4；设施扩建：OP / 管理4+；报告审核仍仅设施主管5 / OP。</div>";
  h+="<div class='slot-map'><div class='slot-zone-title'>轻收容区</div>"+renderSlotZone(slots,byId,'light')+"<div class='slot-zone-title heavy'>重收容区</div>"+renderSlotZone(slots,byId,'heavy')+"</div>";
  return h;
}
function renderAnomalyDetail(){
  var a=findAnomalyInstance(selectedAnomalyInstanceId); if(!a){ anomalyDetailMode=false; return renderAnomalyOverviewWindow(); }
  var c=a.codex||{},can=terminalState.canManageAnomalies===true,p=pct(a.stability,a.maxStability),h="",slots=(terminalState.anomalies&&terminalState.anomalies.slots)||[];
  h+="<div class='anomaly-detail-head'><button class='mini-btn' onclick='closeAnomalyDetail()'>← 返回</button><div><b>"+esc(c.displayName||a.codexId||a.instanceId)+"</b><small>"+esc(a.instanceId)+" · v"+esc(a.revision||0)+" · 更新 "+esc(formatDateTime(a.updatedAt))+" / "+esc(a.updatedByName||'-')+"</small></div><span class='status-pill'>"+esc(a.state||'stable')+"</span></div>";
  h+="<div class='anomaly-detail-body'><section class='anomaly-readonly'><div class='anomaly-id-grid'><span>档案ID<b>"+esc(a.codexId)+"</b></span><span>隔间<b>"+esc(a.slotId||'-')+"</b></span><span>区域/坐标<b>"+esc(a.zone||'-')+" ["+esc(a.layoutX)+","+esc(a.layoutY)+"]</b></span><span>研究阶段<b>"+esc(researchStageText(a.researchProgress||0))+" · "+esc(a.researchProgress||0)+"%</b></span></div><div class='mini-bar'><span style='width:"+p+"%'></span></div><p>稳定度 "+esc(a.stability)+" / "+esc(a.maxStability)+" · 维护 "+esc(formatDateTime(a.lastMaintenanceAt))+"</p><div class='anomaly-summary'>"+esc(c.summary||'暂无档案摘要')+"</div>";
  h+="<button class='mini-btn archive-toggle' onclick='toggleAnomalyArchive()'>"+(anomalyArchiveExpanded?'收起档案详情':'展开收容措施 / 已解锁研究阶段')+"</button>";
  if(anomalyArchiveExpanded){ h+="<div class='anomaly-archive'>"+renderArchiveList('收容措施',c.containment||[])+renderArchiveList('研究阶段',c.researchStages||[])+"</div>"; }
  h+="</section><section class='anomaly-editor'><div class='anomaly-form-grid'>"+anomalyField('状态','an_state',a.state,'stable/warning/emergency/disabled',can)+anomalyField('稳定度','an_stability',a.stability,'0..max',can)+anomalyField('最大稳定度','an_max',a.maxStability,'1..100000',can)+anomalyField('研究进度','an_research',a.researchProgress,'0..100',can)+anomalyField('维护间隔秒','an_interval',a.maintenanceIntervalSeconds,'10..86400',can)+anomalyField('失误损失','an_loss',a.lossPerMiss,'0..max',can)+anomalyField('紧急秒数','an_emergency',a.emergencySeconds,'5..3600',can)+"<label class='anomaly-field wide'>变更备注<textarea id='an_note' "+(can?'':'disabled')+">"+esc(a.operatorNote||'')+"</textarea><small>最多300字；actor与时间戳仅由服务端生成</small></label></div>";
  h+="<div class='anomaly-move'><label class='anomaly-field wide'>迁移至空闲隔间<select id='an_target_slot' "+(can?'':'disabled')+"><option value=''>选择目标隔间</option>"; for(var i=0;i<slots.length;i++){var s=slots[i];if(s.unlocked&&!s.occupiedInstanceId&&s.slotId!==a.slotId)h+="<option value='"+esc(s.slotId)+"'>"+esc(s.slotId)+" · "+esc(s.zone)+"</option>";} h+="</select></label><button class='mini-btn danger' "+(can?'':'disabled')+" onclick='anomalyMoveConfirm()'>确认迁移</button></div>";
  h+="<div class='anomaly-danger-row'><button class='mini-btn' "+(can?'':'disabled')+" onclick='anomalyMarkMaintained()'>标记现在已维护</button><button class='mini-btn danger' "+(can?'':'disabled')+" onclick=\"anomalyEmergencyConfirm('start')\">进入紧急</button><button class='mini-btn' "+(can?'':'disabled')+" onclick=\"anomalyEmergencyConfirm('clear')\">清除紧急</button></div></section></div>";
  h+="<div class='anomaly-action-bar'><button class='head-btn ghost' onclick='refreshWindow(\"anomaly\")'>撤销未保存</button><button class='head-btn accent' "+(can?'':'disabled')+" onclick='anomalySaveParams()'>保存参数</button></div>";
  return h;
}
function anomalyField(label,id,value,hint,enabled){ return "<label class='anomaly-field'>"+esc(label)+"<input id='"+id+"' value='"+esc(value===undefined?'':value)+"' "+(enabled?'':'disabled')+"><small>"+esc(hint)+"</small></label>"; }
function renderArchiveList(title,list){ var h="<h4>"+esc(title)+"</h4>"; if(!list||!list.length)return h+"<div class='empty-tip'>暂无已解锁内容</div>"; for(var i=0;i<list.length;i++){var item=list[i];h+="<div class='archive-line'>"+esc(typeof item==='string'?item:(item.title||item.name||item.summary||JSON.stringify(item)))+"</div>";} return h; }
function toggleAnomalyArchive(){ anomalyArchiveExpanded=!anomalyArchiveExpanded; refreshWindow('anomaly'); }
function openAnomalyDetail(id){ selectedAnomalyInstanceId=id||''; anomalyDetailMode=true; anomalyArchiveExpanded=false; pushAction({t:'anomaly_detail_request',instanceId:selectedAnomalyInstanceId}); refreshWindow('anomaly'); }
function closeAnomalyDetail(){ anomalyDetailMode=false; anomalyArchiveExpanded=false; refreshWindow('anomaly'); }
function anomalySaveParams(){ var a=findAnomalyInstance(selectedAnomalyInstanceId); if(!a)return; pushAction({t:'anomaly_params_update',instanceId:a.instanceId,expectedRevision:String(a.revision||0),state:getVal('an_state'),stability:getVal('an_stability'),maxStability:getVal('an_max'),researchProgress:getVal('an_research'),maintenanceIntervalSeconds:getVal('an_interval'),lossPerMiss:getVal('an_loss'),emergencySeconds:getVal('an_emergency'),note:getVal('an_note')}); }
function anomalyMarkMaintained(){ terminalTextModal('标记现在已维护','输入恢复量（1..最大稳定度），第二行可写备注',function(v){var lines=(v||'').split('\n');pushAction({t:'anomaly_mark_maintained',instanceId:selectedAnomalyInstanceId,restoreAmount:lines[0]||'',note:lines.slice(1).join('\n')||getVal('an_note')});}); }
function anomalyEmergencyConfirm(mode){ terminalTextModal(mode==='start'?'危险确认：进入紧急状态':'确认清除紧急状态','输入 CONFIRM 后可换行填写备注',function(v){var lines=(v||'').split('\n');if(lines[0]!=='CONFIRM')return;pushAction({t:'anomaly_emergency_set',instanceId:selectedAnomalyInstanceId,mode:mode,note:lines.slice(1).join('\n')||getVal('an_note')});}); }
function anomalyMoveConfirm(){ var target=getVal('an_target_slot'); if(!target)return; terminalTextModal('危险确认：迁移至 '+target,'输入 MOVE 后可换行填写备注',function(v){var lines=(v||'').split('\n');if(lines[0]!=='MOVE')return;pushAction({t:'anomaly_instance_move',instanceId:selectedAnomalyInstanceId,targetSlotId:target,note:lines.slice(1).join('\n')||getVal('an_note')});}); }

function renderSlotZone(slots, byId, zone) {
  var h="<div class='slot-grid'>";
  var count=0;
  for(var i=0;i<slots.length;i++){
    var slot=slots[i]; if(slot.zone!==zone) continue; count++;
    var inst=byId[slot.occupiedInstanceId]||null;
    var cls="contain-slot"+(slot.unlocked?"":" locked")+(inst?" occupied":"");
    h+="<div class='"+cls+"'>";
    h+="<div class='slot-head'><span>"+esc(slot.slotId)+"</span><span>"+(zone==="heavy"?"HEAVY":"LIGHT")+"</span></div>";
    if(!slot.unlocked){
      h+="<div class='slot-name'>未扩建隔间</div><div class='slot-meta'>费用 ¥"+(slot.unlockCost||0)+"</div>";
      h+="<button class='mini-btn slot-btn' "+(terminalState.canExpandFacility?"":"disabled")+" onclick=\"terminalUnlockSlot('"+esc(slot.slotId)+"')\">支付设施资金解锁</button>";
    } else if(inst){
      var c=inst.codex||{}; var name=c.displayName||inst.codexId||"未知异常物"; var p=pct(inst.stability,inst.maxStability);
      h+="<div class='slot-name'>"+esc(name)+"</div><div class='slot-meta'>"+esc(inst.state||"stable")+" · 研究 "+(inst.researchProgress||0)+"%</div>";
      h+="<div class='mini-bar slot-stab'><span style='width:"+p+"%'></span></div>";
      h+="<button class='mini-btn slot-btn' onclick=\"openAnomalyDetail('"+esc(inst.instanceId)+"')\">查看详情 / 高级参数</button>";
      h+="<button class='mini-btn slot-btn danger' "+(terminalState.canManageAnomalies?"":"disabled")+" onclick=\"terminalRemoveAnomaly('"+esc(inst.instanceId)+"')\">移出隔间</button>";
    } else {
      h+="<div class='slot-name'>空闲隔间</div><div class='slot-meta'>输入档案ID放置异常物</div>";
      h+="<input class='slot-input' id='codex_"+esc(slot.slotId)+"' value='' placeholder='档案ID'>";
      h+="<button class='mini-btn slot-btn' "+(terminalState.canManageAnomalies?"":"disabled")+" onclick=\"terminalAddAnomalyToSlot('"+esc(slot.slotId)+"')\">放置异常物</button>";
    }
    h+="</div>";
  }
  if(count===0) h+="<div class='empty-tip'>暂无隔间</div>";
  h+="</div>";
  return h;
}
function terminalUnlockSlot(slotId){ pushAction({t:"anomaly_slot_unlock",slotId:slotId}); }
function terminalAddAnomalyToSlot(slotId){ var input=$("codex_"+slotId); var codex=input?input.value:""; if(codex) pushAction({t:"anomaly_slot_add",slotId:slotId,codexId:codex}); }
function terminalRemoveAnomaly(instanceId){ terminalTextModal('危险确认：移除异常实例 '+instanceId,'输入 REMOVE 后可换行填写备注',function(v){var lines=(v||'').split('\n');if(lines[0]!=='REMOVE')return;pushAction({t:'anomaly_slot_remove',instanceId:instanceId,confirmed:true,note:lines.slice(1).join('\n')});}); }
function noticePublishShell(){ if(!noticeCanPublish()) return; var a=noticePayload('notice_publish'); a.id=''; pushAction(a); }
function noticeUpdateShell(){ if(!noticeCanManage()) return; var id=getVal('notice_id'); if(!id) return; var a=noticePayload('notice_update'); pushAction(a); }
function noticeAction(id, kind){ if(!id||!kind) return; if((kind==='notice_delete'&&!noticeCanDelete())||(kind!=='notice_delete'&&!noticeCanManage())) return; pushAction({t:kind, id:id}); }

function runHeadAction(actionName) {
  if (actionName === "reloadCodex") {
    pushAction({ t:"anomaly_codex_reload" });
  } else if (actionName === "addSampleAnomaly") {
    pushAction({ t:"anomaly_add", codexId:"anm_001", zone:"light", layoutX:80, layoutY:80 });
  } else if (actionName === "requestTerminal") {
    pushAction({ t:"terminal_request" });
  } else if (actionName === "noticePublish") {
    noticePublishShell();
  } else if (actionName === "openNoticeManager") {
    openWin("notice");
  } else if (actionName === "openHqEditor") {
    if (hqCanEdit()) openWin("hqEditor");
  } else if (actionName === "hqEditorSave") {
    hqEditorAction("save");
  } else if (actionName === "hqEditorFill") {
    hqEditorAction("hq_task_fill");
  } else if (actionName === "hqEditorComplete") {
    hqEditorAction("hq_task_force_complete");
  } else if (actionName === "hqEditorFail") {
    hqEditorAction("hq_task_force_fail");
  } else if (actionName === "hqEditorDelete") {
    hqEditorAction("hq_task_delete");
  }
}

function windowControlByKey(key, action) {
  for(var i=0;i<wins.length;i++){
    if(wins[i].key!==key) continue;
    if(action==="min") minimizeWin(wins[i]);
    else if(action==="max") toggleMaximizeWin(wins[i]);
    else if(action==="close") closeWin(wins[i]);
    return;
  }
}
function focusWin(rec) {
  for (var i = 0; i < wins.length; i++) {
    try{ wins[i].el.classList.remove("focused"); }catch(ignoredEl){}
    try{ if(wins[i].taskBtn) wins[i].taskBtn.classList.remove("active"); }catch(ignoredTask){}
  }
  try{ rec.el.classList.add("focused"); }catch(ignoredFocus){}
  try{ if(rec.taskBtn) rec.taskBtn.classList.add("active"); }catch(ignoredActive){}
  try{ if(rec.el.classList.contains("minimized")) rec.el.classList.remove("minimized"); }catch(ignoredMin){}
  var z=++zTop;
  try{
    var raw=String(rec.el.getAttribute("style")||"").replace(/z-index\s*:[^;]+;?/gi,"");
    rec.el.setAttribute("style",raw+";z-index:"+z+";");
  }catch(ignoredZ){}
}
function minimizeWin(rec) { rec.el.classList.add("minimized"); if(rec.taskBtn) rec.taskBtn.classList.remove("active"); }
function restoreWin(rec) { focusWin(rec); }
function toggleMaximizeWin(rec) {
  focusWin(rec);
  var isMax=rec.el.getAttribute("data-maximized")==="1";
  if(isMax){
    rec.el.setAttribute("data-maximized","0");
    rec.el.setAttribute("class","win focused");
    rec.el.setAttribute("style",rec.normalStyle||"width:600px;height:330px;left:8px;top:8px;");
    var mb=rec.el.querySelector(".win-btn.max"); if(mb) mb.textContent="□";
  } else {
    rec.normalStyle=String(rec.el.getAttribute("style")||"width:600px;height:330px;left:8px;top:8px;");
    rec.el.setAttribute("data-maximized","1");
    rec.el.setAttribute("class","win focused maximized");
    var viewport={width:1920,height:1080};
    try{ viewport=terminalViewportSize(); }catch(viewportError){}
    var maxW=Math.max(240,viewport.width-16);
    var maxH=Math.max(130,viewport.height-68);
    rec.el.setAttribute("style","width:"+maxW+"px;height:"+maxH+"px;left:8px;top:8px;z-index:"+(++zTop)+";");
    var mb2=rec.el.querySelector(".win-btn.max"); if(mb2) mb2.textContent="❐";
  }
}
function closeWin(rec) {
  rec.el.setAttribute("style",String(rec.el.getAttribute("style")||"")+";display:none;");
  var winParent=rec.el.parentElement||rec.el.parentNode;
  if(winParent){ try{ winParent.removeChild(rec.el); }catch(removeError){} }
  if(rec.taskBtn){ setInlineStyle(rec.taskBtn,"display","none"); try{rec.taskBtn.classList.remove("active");}catch(ignoredTask){} }
  var idx = wins.indexOf(rec);
  if (idx >= 0) wins.splice(idx, 1);
  if (wins.length > 0) focusWin(wins[wins.length - 1]);
}
function bindDrag(win, handle) {
  var sx = 0, sy = 0, ol = 0, ot = 0, drag = false;
  handle.addEventListener("mousedown", function(e) { if (win.classList.contains("maximized")) return; drag = true; sx = e.clientX; sy = e.clientY; var r = win.getBoundingClientRect(); ol = r.left; ot = r.top; });
  document.addEventListener("mousemove", function(e) { if (!drag || win.classList.contains("maximized")) return; win.style.left = (ol + (e.clientX - sx)) + "px"; win.style.top = (ot + (e.clientY - sy)) + "px"; });
  document.addEventListener("mouseup", function() { drag = false; });
}
function bindResize(win) {
  var handles = win.querySelectorAll(".resize-handle");
  var resizing = false, dir = "", sx = 0, sy = 0, sw = 0, sh = 0, sl = 0, st = 0;
  var minW = 360, minH = 210;
  for (var i = 0; i < handles.length; i++) {
    (function(h) {
      h.addEventListener("mousedown", function(e) {
        if (e.stopPropagation) e.stopPropagation();
        if (win.classList.contains("maximized")) return;
        resizing = true;
        dir = h.getAttribute("data-dir") || "se";
        sx = e.clientX; sy = e.clientY;
        var r = win.getBoundingClientRect();
        sw = r.width; sh = r.height; sl = r.left; st = r.top;
      });
    })(handles[i]);
  }
  document.addEventListener("mousemove", function(e) {
    if (!resizing || win.classList.contains("maximized")) return;
    var dx = e.clientX - sx;
    var dy = e.clientY - sy;
    var nw = sw, nh = sh, nl = sl, nt = st;
    if (dir.indexOf("e") >= 0) nw = sw + dx;
    if (dir.indexOf("s") >= 0) nh = sh + dy;
    if (dir.indexOf("w") >= 0) { nw = sw - dx; nl = sl + dx; }
    if (dir.indexOf("n") >= 0) { nh = sh - dy; nt = st + dy; }
    if (nw < minW) { if (dir.indexOf("w") >= 0) nl -= (minW - nw); nw = minW; }
    if (nh < minH) { if (dir.indexOf("n") >= 0) nt -= (minH - nh); nh = minH; }
    win.style.width = nw + "px";
    win.style.height = nh + "px";
    win.style.left = nl + "px";
    win.style.top = nt + "px";
  });
  document.addEventListener("mouseup", function() { resizing = false; });
}

// ---- 桥接 ----
function previewRecordHqEvent(eventType, amount){
  var list=hqItems(); var changed=false; var delta=Number(amount||1);
  for(var i=0;i<list.length;i++){
    var t=list[i]; if((t.status||"active")!=="active") continue;
    if((t.eventType||"manual")!==eventType) continue;
    t.progress=Math.min(Number(t.goal||1),Number(t.progress||0)+delta);
    if(Number(t.progress||0)>=Number(t.goal||1)) t.status="claimable";
    changed=true;
  }
  if(changed){ updateHqOverview(); refreshWindow("daily"); refreshWindow("hqEditor"); }
  return changed;
}
function previewConsumeAction(action) {
  if (!previewMode || !action) return false;
  if (action.t && action.t.indexOf("hq_task_") === 0) {
    var list=hqItems(); var id=action.id||""; var t=findHqTask(id);
    if(action.t==="hq_task_save") { if(!id){ id="preview_hq_"+Date.now(); action.id=id; } var nt={id:id,title:action.title||"未命名任务",desc:action.desc||"",status:action.status||"active",eventType:action.eventType||"manual",progress:Number(action.progress||0),goal:Number(action.goal||1),rewardFunds:Number(action.rewardFunds||0),department:action.department||"ALL",level:Number(action.level||1)}; if(nt.status==="active"&&nt.progress>=nt.goal)nt.status="claimable"; if(t){ for(var i=0;i<list.length;i++) if(list[i].id===id) list[i]=nt; } else list.push(nt); }
    else if(action.t==="hq_task_delete") { for(var d=list.length-1;d>=0;d--) if(list[d].id===id) list.splice(d,1); }
    else if(action.t==="hq_task_fill") { if(t){ t.progress=Math.max(Number(t.progress||0),Number(t.goal||1)); if(t.status==="active")t.status="claimable"; } else if(list.length===0){ list.push({id:"hq_seed_notice_publish",title:"发布设施公告",desc:"管理部门发布一次设施公告。",status:"active",eventType:"notice_published",progress:0,goal:1,rewardFunds:200,department:"管理部门",level:3}); } }
    else if(t && action.t==="hq_task_claim" && t.status==="claimable") { t.status="completed"; if(!terminalState.facilityFund)terminalState.facilityFund={balance:0,balanceText:"0",logs:[]}; var rw=Number(t.rewardFunds||0); terminalState.facilityFund.balance=Number(terminalState.facilityFund.balance||0)+rw; terminalState.facilityFund.balanceText=String(terminalState.facilityFund.balance); if(!terminalState.facilityFund.logs)terminalState.facilityFund.logs=[]; terminalState.facilityFund.logs.unshift({time:"PREVIEW",type:"deposit",target:"设施资金",amount:rw,operator:"预览",note:"总部任务奖励 "+(t.title||t.id)}); }
    else if(t && action.t==="hq_task_force_complete") { t.status="completed"; t.progress=Math.max(Number(t.progress||0),Number(t.goal||1)); }
    else if(t && action.t==="hq_task_force_fail") { t.status="failed"; }
    updateHqOverview(); refreshWindow("daily"); refreshWindow("hqEditor"); refreshWindow("facility"); return true;
  }
  if(action.t&&action.t.indexOf('notice_')===0) {
    if(!terminalState.notices) terminalState.notices={items:[],activeCount:0,archivedCount:0,expiredCount:0,canPublish:true,canManage:true,canHardDelete:true};
    var ns=terminalState.notices.items||[]; var nid=action.id||''; var found=null; for(var ni=0;ni<ns.length;ni++){ if(ns[ni].id===nid) found=ns[ni]; }
    if(action.t==='notice_publish' && action.title && action.content){ found={id:'preview_notice_'+Date.now(),title:action.title,body:action.content,content:action.content,targetDepartment:action.targetDepartment||'全部门',publisherName:'PREVIEW',authorName:'PREVIEW',publishedAt:Date.now(),publishedAtText:'PREVIEW',expiresAtText:action.expireHours>0?(action.expireHours+'小时后'):'永久',pinned:action.pinned===true,status:'active'}; ns.unshift(found); previewRecordHqEvent('notice_published',1); }
    else if(found && action.t==='notice_update' && action.title && action.content){ found.title=action.title; found.body=action.content; found.content=action.content; found.targetDepartment=action.targetDepartment||found.targetDepartment; found.pinned=action.pinned===true; }
    else if(found && action.t==='notice_pin'){ found.pinned=true; }
    else if(found && action.t==='notice_unpin'){ found.pinned=false; }
    else if(found && action.t==='notice_archive'){ found.status='archived'; }
    else if(found && action.t==='notice_expire'){ found.status='expired'; }
    else if(found && action.t==='notice_delete'){ for(var nd=ns.length-1;nd>=0;nd--) if(ns[nd].id===nid) ns.splice(nd,1); }
    terminalState.notices.items=ns; terminalState.notices.activeCount=0; terminalState.notices.archivedCount=0; terminalState.notices.expiredCount=0; for(var nc=0;nc<ns.length;nc++){ if(ns[nc].status==='archived')terminalState.notices.archivedCount++; else if(ns[nc].status==='expired')terminalState.notices.expiredCount++; else terminalState.notices.activeCount++; }
    updateHomeNotices(); refreshWindow('notice'); refreshWindow('noticeDetail'); return true;
  }
  if (!terminalState.anomalies) return false;
  var a = terminalState.anomalies;
  if (!a.slots) a.slots=[]; if (!a.instances) a.instances=[];
  if (action.t === "anomaly_codex_reload" || action.t === "terminal_request") { refreshWindow("anomaly"); return true; }
  if (action.t === "anomaly_slot_unlock") {
    for (var i=0;i<a.slots.length;i++) if (a.slots[i].slotId === action.slotId) { a.slots[i].unlocked = true; }
    previewRecordHqEvent("slot_unlocked",1);
    refreshWindow("anomaly"); updateAnomalyOverview(); return true;
  }
  if (action.t === "anomaly_slot_add") {
    var slot=null; for (var s=0;s<a.slots.length;s++) if (a.slots[s].slotId === action.slotId) slot=a.slots[s];
    if (!slot || !slot.unlocked || slot.occupiedInstanceId) return true;
    var id="preview_"+Date.now();
    var inst={instanceId:id,codexId:action.codexId,slotId:action.slotId,zone:slot.zone,layoutX:slot.layoutX,layoutY:slot.layoutY,stability:100,maxStability:100,researchProgress:0,state:"stable",codex:{displayName:action.codexId}};
    a.instances.push(inst); slot.occupiedInstanceId=id;
    previewRecordHqEvent("anomaly_contained",1);
    refreshWindow("anomaly"); updateAnomalyOverview(); return true;
  }
  if (action.t === "anomaly_slot_remove") {
    for (var j=a.instances.length-1;j>=0;j--) if (a.instances[j].instanceId === action.instanceId) a.instances.splice(j,1);
    for (var k=0;k<a.slots.length;k++) if (a.slots[k].occupiedInstanceId === action.instanceId) a.slots[k].occupiedInstanceId="";
    refreshWindow("anomaly"); updateAnomalyOverview(); return true;
  }
  return false;
}
function pushAction(action) {
  if (previewConsumeAction(action)) return;
  actionQueue.push(action);
  var qEl = $("terminalActionQueue");
  if (qEl) qEl.value = JSON.stringify(actionQueue);
}
function refreshWindow(key) {
  if (!WIN[key]) return;
  for (var i=0;i<wins.length;i++) {
    if (wins[i].key === key) {
      var body = wins[i].el.querySelector(".win-body");
      if (body) { body.innerHTML = WIN[key].content(); if (key === 'dept') bindDepartmentPhase2Tabs(body.querySelector('[data-dept-shell]')); }
    }
  }
}
function updateAnomalyOverview() {
  var a=terminalState.anomalies||{instances:[],codexStatus:{loaded:0,failed:0}};
  var inst=a.instances||[];
  var el=$("anomalyCountVal"); if(el) el.textContent=String(inst.length);
  var sub=$("anomalyCountSub"); if(sub) sub.textContent="档案 "+((a.codexStatus&&a.codexStatus.loaded)||0)+" · 失败 "+((a.codexStatus&&a.codexStatus.failed)||0);
}
function updateHqOverview() {
  var items=hqItems(); var tv=$("taskVal"); if(tv) tv.textContent=pad2(items.length);
  refreshWindow("daily"); refreshWindow("hqEditor");
}
function updateStaffOverview() {
  var st = terminalState.staffOnline || { total:0, departments:{} };
  var totalEl = $("staffTotalVal");
  if (totalEl) totalEl.textContent = String(st.total || 0);
  var subEl = $("staffTotalSub");
  if (subEl) subEl.textContent = "身份均由ID卡同步";
  var deptEl = $("deptCounts");
  if (deptEl) {
    var d = st.departments || {};
    deptEl.textContent = "未"+(d["未知"]||0)+" / 管"+(d["管理部门"]||0)+" / 研"+(d["科研部门"]||0)+" / 安"+(d["安保部门"]||0)+" / 后"+(d["后勤部门"]||0)+"(含0级临时)";
  }
}

function pollBridge() {
  var dataEl = $("terminalData");
  if (dataEl) {
    var t = dataEl.textContent || "";
    if (t !== bridgeLastData) {
      bridgeLastData = t;
      if (t) applyBridgeData(t);
    }
  }
  var qEl = $("terminalActionQueue");
  if (qEl && actionQueue.length > 0) {
    var v = qEl.value || "";
    if (v === "") actionQueue = [];
  }
  setTimeout(pollBridge, 2000);
}
function applyBridgeData(jsonText) {
  var data;
  try { data = JSON.parse(jsonText); } catch (err) { return; }
  if (typeof data.isOp === "boolean") {
    terminalState.isOp = data.isOp;
  }
  if (typeof data.canEditHQTasks === "boolean") terminalState.canEditHQTasks = data.canEditHQTasks;
  if (data.hqTaskEditor) terminalState.hqTaskEditor = data.hqTaskEditor;
  if (data.hqTasks) { terminalState.hqTasks = data.hqTasks; updateHqOverview(); }
  if (typeof data.canManageAnomalies === "boolean") terminalState.canManageAnomalies = data.canManageAnomalies;
  if (typeof data.canViewResearchDetails === "boolean") terminalState.canViewResearchDetails = data.canViewResearchDetails;
  if (typeof data.canSubmitReports === "boolean") terminalState.canSubmitReports = data.canSubmitReports;
  if (typeof data.canReviewReports === "boolean") terminalState.canReviewReports = data.canReviewReports;
  if (typeof data.canExpandFacility === "boolean") terminalState.canExpandFacility = data.canExpandFacility;
  if (typeof data.canPublishNotices === "boolean") terminalState.canPublishNotices = data.canPublishNotices;
  if (typeof data.canManageNotices === "boolean") terminalState.canManageNotices = data.canManageNotices;
  if (data.notices) { terminalState.notices = data.notices; updateHomeNotices(); refreshWindow("notice"); refreshWindow("noticeDetail"); }
  if (data.templates) {
    // 服务端任务模板数据(阶段2+ 使用; 当前窗口内容仍为静态预览)
  }
  if (data.anomalies) {
    terminalState.anomalies = data.anomalies;
    updateAnomalyOverview();
    refreshWindow("anomaly");
    refreshWindow("reports");
  }
  if (data.researchDepartment) {
    terminalState.researchDepartment = data.researchDepartment;
    refreshWindow("research");
  }
  if (data.researchDocuments) { terminalState.researchDocuments = data.researchDocuments; refreshWindow("research"); }
  if (data.departmentApplications) { terminalState.departmentApplications = data.departmentApplications; refreshWindow("applications"); }
  if (data.pendingCardQueue) { terminalState.pendingCardQueue = data.pendingCardQueue; refreshWindow("applications"); }
  if (data.operationAudit) { terminalState.operationAudit = data.operationAudit; refreshWindow("applications"); }
  if (data.managementOffice) { terminalState.managementOffice = data.managementOffice; refreshWindow("management"); }
  if (data.departmentTasks) { terminalState.departmentTasks = data.departmentTasks; refreshWindow("dept"); }
  if (data.dispatches) { terminalState.dispatches = data.dispatches; refreshWindow("dept"); }
  if (data.vitalAlerts) { terminalState.vitalAlerts = data.vitalAlerts; refreshWindow("dept"); }
  if (data.reinforcements) { terminalState.reinforcements = data.reinforcements; refreshWindow("dept"); }
  if (data.procurements) { terminalState.procurements = data.procurements; if(data.procurements.summary) terminalState.procurementSummary=data.procurements.summary; refreshWindow("purchase"); }
  if (data.facilityPayments) { terminalState.facilityPayments = data.facilityPayments; refreshWindow("purchase"); }
  if (data.facilityPayroll) { terminalState.facilityPayroll = data.facilityPayroll; refreshWindow("salary"); }
  if (data.staffOnline) {
    terminalState.staffOnline = data.staffOnline;
    updateStaffOverview();
    for (var si=0;si<wins.length;si++) {
      if (wins[si].key === "staff") {
        var sbody = wins[si].el.querySelector(".win-body");
        if (sbody) sbody.innerHTML = WIN.staff.content();
      }
    }
  }
  if (data.facilityFund) {
    terminalState.facilityFund = data.facilityFund;
    var fv = $("fundVal");
    if (fv) fv.textContent = String(data.facilityFund.balanceText || data.facilityFund.balance || "0");
    // 已打开设施资金窗口时重绘窗口内容, 避免仍显示旧余额/旧流水
    for (var i=0;i<wins.length;i++) {
      if (wins[i].key === "facility") {
        var body = wins[i].el.querySelector(".win-body");
        if (body) body.innerHTML = WIN.facility.content();
      }
    }
  }
}

// ---- 事件绑定 ----
function bindEvents() {
  winLayer = $("winLayer");
  taskbarTasks = $("taskbarTasks");
  // 参考电脑KJS：静态任务栏按钮仅切换显示状态，不动态创建/删除DOM。
  var taskNodes=document.querySelectorAll(".task-btn[data-task-win]");
  for(var ti=0;ti<taskNodes.length;ti++){
    (function(taskNode){
      taskNode.addEventListener("click",function(){
        var key=taskNode.getAttribute("data-task-win"), rec=null;
        for(var wi=0;wi<wins.length;wi++){ if(wins[wi].key===key){rec=wins[wi];break;} }
        if(!rec) return;
        if(rec.el.classList.contains("minimized")) restoreWin(rec);
        else if(rec.el.classList.contains("focused")) minimizeWin(rec);
        else focusWin(rec);
      });
    })(taskNodes[ti]);
  }
  // 四功能大按钮
  var mods = document.querySelectorAll(".mod-btn[data-win]");
  for (var i = 0; i < mods.length; i++) {
    (function(btn) {
      btn.addEventListener("click", function() { openWin(btn.getAttribute("data-win")); });
    })(mods[i]);
  }
  // 主页待办/概览快捷入口
  var quicks = document.querySelectorAll("[data-open-win]");
  for (var qi = 0; qi < quicks.length; qi++) {
    (function(q) {
      q.addEventListener("click", function() { openWin(q.getAttribute("data-open-win")); });
    })(quicks[qi]);
  }
  // 功能菜单项
  var items = document.querySelectorAll(".menu-item[data-win]");
  for (var j = 0; j < items.length; j++) {
    (function(item) {
      item.addEventListener("click", function() {
        openWin(item.getAttribute("data-win"));
        $("funcMenu").classList.remove("show");
      });
    })(items[j]);
  }
  // 功能菜单开关
  $("btnFunc").addEventListener("click", function() { $("funcMenu").classList.toggle("show"); });
  $("miClose").addEventListener("click", function() {
    for (var k = 0; k < wins.length; k++) wins[k].el.classList.add("minimized");
    $("funcMenu").classList.remove("show");
  });
  $("miMinAll").addEventListener("click", function() {
    for (var k = 0; k < wins.length; k++) {
      wins[k].el.classList.add("minimized");
      if(wins[k].taskBtn) wins[k].taskBtn.classList.remove("active");
    }
    $("funcMenu").classList.remove("show");
  });
  // 点击外部关闭菜单(AUI 无 closest, 用父链遍历)
  document.addEventListener("click", function(e) {
    var el2 = e.target;
    var inMenu = false;
    var inBtn = false;
    while (el2 && el2.getAttribute) {
      if (el2.id === "funcMenu") { inMenu = true; break; }
      if (el2.id === "btnFunc") { inBtn = true; break; }
      el2 = el2.parentElement || el2.parentNode;
    }
    if (!inMenu && !inBtn) $("funcMenu").classList.remove("show");
  });
  // 时钟
  var tc = $("taskTime");
  function tick() {
    var x = new Date();
    tc.textContent = pad2(x.getHours()) + ":" + pad2(x.getMinutes());
  }
  setInterval(tick, 1000);
  tick();
}

// ---- 初始化 ----
function seedPreviewTerminalData() {
  terminalState.isOp = true;
  terminalState.canEditHQTasks = true;
  terminalState.hqTaskEditor = { enabled:true, canEdit:true, opOnly:true };
  terminalState.canPublishNotices = true; terminalState.canManageNotices = true;
  terminalState.notices = {items:[],activeCount:0,archivedCount:0,expiredCount:0,canPublish:true,canManage:true,canHardDelete:true};
  if (!terminalState.hqTasks || !terminalState.hqTasks.items || terminalState.hqTasks.items.length === 0) {
    terminalState.hqTasks = { version:2, items:[
      {id:"hq_seed_anomaly_maintenance",title:"异常物稳定维护",desc:"完成一次异常物维护，让任意异常物恢复稳定度。",status:"active",eventType:"anomaly_maintenance",progress:0,goal:1,rewardFunds:500,department:"科研部门",level:2},
      {id:"hq_seed_slot_unlock",title:"扩建收容隔间",desc:"管理部门解锁一个新的收容隔间。",status:"active",eventType:"slot_unlocked",progress:0,goal:1,rewardFunds:1200,department:"管理部门",level:4},
      {id:"hq_seed_notice_publish",title:"发布设施公告",desc:"管理部门发布一次设施公告。",status:"active",eventType:"notice_published",progress:0,goal:1,rewardFunds:200,department:"管理部门",level:3}
    ] };
  }
}
function initTerminal() {
  try { previewMode = (window.location && (window.location.href.indexOf("preview_slots") >= 0 || window.location.href.indexOf("facility_preview_standalone") >= 0 || window.location.protocol === "file:" || window.location.hostname === "127.0.0.1" || window.location.hostname === "localhost")); } catch(e) { previewMode = false; }
  bridgeMode = !!document.getElementById("terminalData");
  if (previewMode) seedPreviewTerminalData();
  renderStaticIcons();
  bindEvents();
  updateHqOverview();
  updateHomeNotices();
  if (bridgeMode) {
    pollBridge();
  }
}
function phase5List(key,label){var b=terminalState[key]||{},a=b.items||[],h="<section class='phase5-panel'><h3>"+esc(label)+" <span class='phase5-count'>"+a.length+" 条</span></h3>";if(!a.length)return h+"<div class='empty-tip'>暂无"+esc(label)+"数据</div></section>";for(var i=0;i<a.length&&i<16;i++){var x=a[i]||{};h+="<article class='phase5-record'><b>"+esc(x.title||x.id||x.alertId||x.applicationId||'-')+"</b><span class='phase5-status'>"+esc(x.status||'未标记')+"</span><small>"+esc(x.description||x.note||x.location||x.reason||x.targetDepartment||'服务端快照')+"</small></article>";}return h+listMoreHint(a,16,label)+"</section>";}
function phase5ActionForm(title,fields,action){var h="<section class='phase5-panel'><h3>"+esc(title)+"</h3><div class='phase5-form'>";for(var i=0;i<fields.length;i++)h+="<input id='"+esc(fields[i][0])+"' placeholder='"+esc(fields[i][1])+"'>";h+="<button class='head-btn accent' onclick=\"phase5Submit('"+esc(action)+"',[";for(var j=0;j<fields.length;j++)h+=(j?',':'')+"'"+esc(fields[j][0])+"'";return h+"])\">提交</button></div></section>";}
function phase5Submit(action,ids){var p={t:action};for(var i=0;i<ids.length;i++)p[ids[i]]=getVal(ids[i]);pushAction(p);}
function renderSecurityPhase5Window(){return "<div class='phase5-shell'><div class='perm-note'>安保页仅展示服务端警报、调度、增援与在线快照；权限和状态由服务端校验。</div>"+phase5List('vitalAlerts','生命警报')+phase5List('dispatches','调度记录')+phase5List('reinforcements','增援请求')+phase5List('staffOnline','值班/在线快照')+phase5ActionForm('新建调度',[['p5_sec_target','目标部门'],['p5_sec_location','地点'],['p5_sec_message','说明']],'dispatch_create')+"</div>";}
function renderLogisticsPhase5Window(){var h="<div class='phase5-shell'><div class='perm-note'>后勤页复用采购与部门任务真实状态；收货不自动生成库存，工资付款保持关闭。</div>"+phase5List('procurements','采购申请')+phase5List('departmentTasks','后勤任务');return h+phase5ActionForm('提交采购申请',[['p5_log_category','类别'],['p5_log_items','物品'],['p5_log_amount','金额'],['p5_log_sourceApplicationId','来源申请ID'],['p5_log_reason','理由']],'procurement_create')+"</div>";}
function renderTemporaryPhase5Window(){var s=terminalState.staffOnline||{},p=s.players||[];var h="<div class='phase5-shell'><div class='perm-note'>身份字段只读ID卡；转正仅生成待换卡记录，不写卡、不展示无卡状态。</div>"+phase5List('departmentApplications','转正/部门申请')+phase5List('pendingCardQueue','待换卡队列')+"<section class='phase5-panel'><h3>临聘/受试人员档案快照</h3>";if(!p.length)h+="<div class='empty-tip'>暂无服务端人员快照</div>";for(var i=0;i<p.length;i++){var x=p[i]||{};h+="<article class='phase5-record'><b>"+esc(x.cardName||x.name||x.playerName||'-')+"</b><small>"+esc(x.department||'-')+" · "+esc(x.position||'-')+" · 等级 "+esc(x.level||0)+"（ID卡）</small></article>";}return h+"</section>"+phase5ActionForm('提交转正申请',[['p5_tmp_reason','申请理由']],'dept_app_submit')+"</div>";}
WIN.security={title:'安保警报与调度',icon:'biohazard',btn:[['刷新','ghost','requestTerminal']],content:function(){return renderSecurityPhase5Window();}}; WIN.logistics={title:'后勤采购与任务',icon:'package-import',btn:[['刷新','ghost','requestTerminal']],content:function(){return renderLogisticsPhase5Window();}}; WIN.temporary={title:'临聘与待换卡档案',icon:'id',btn:[['刷新','ghost','requestTerminal']],content:function(){return renderTemporaryPhase5Window();}};
initTerminal();

/* Phase 2 C implementation is kept inline for the AUI merge entry. */
/* Phase 2 C: service-supplied department fields and action collection. */
function deptText(v){return esc(v===undefined||v===null?'':v);} function deptStatus(s){return "<span class='dept-status dept-status-"+deptText(s||'unknown')+"'>"+deptText(s||'unknown')+"</span>";} function deptTime(x){return deptText(x.timeText||x.updatedAtText||x.createdAtText||x.time||formatDateTime(x.updatedAt||x.createdAt));} function deptButton(label,obj){return "<button type='button' class='mini-btn' onclick='pushAction("+JSON.stringify(obj)+")'>"+esc(label)+"</button>";} function deptItems(k){var b=terminalState[k]||{};return b.items||[];} function deptRows(a,f,e){if(!a.length)return "<div class='empty-tip'>"+esc(e)+"</div>";var h='';for(var i=0;i<a.length&&i<12;i++)h+=f(a[i]||{});return h+listMoreHint(a,12,'记录');}
function renderDepartmentOpsPhase2Skeleton(){var s=terminalState.staffOnline||{},d=s.departments||{},t=[['online','在线编制'],['tasks','部门任务'],['dispatch','调度'],['alerts','生命警报'],['reinforcements','增援']],h="<div class='dept-phase2' data-dept-shell='1'><nav class='dept-tabs'>";for(var i=0;i<t.length;i++)h+="<button type='button' class='dept-tab"+(i?'':' active')+"' data-dept-tab='"+t[i][0]+"' aria-selected='"+(i?'false':'true')+"'>"+t[i][1]+"</button>";h+="</nav><div class='dept-panel-host'><section class='dept-panel active' data-dept-panel-id='online'><div class='dept-panel-head'><h3>在线编制</h3><span class='dept-kpi'>在线 "+deptText(s.total||0)+" 人</span></div><div class='dept-counts'>管理 "+(d['管理部门']||0)+" · 科研 "+(d['科研部门']||0)+" · 安保 "+(d['安保部门']||0)+" · 后勤 "+(d['后勤部门']||0)+" · 未知 "+(d['未知']||0)+"</div>"+deptRows(s.players||[],function(x){return "<div class='dept-row'><b>"+deptText(x.cardName||x.name||x.playerName||'-')+"</b><span>"+deptText(x.department||'-')+" · "+deptText(x.position||'-')+" · Lv."+deptText(x.level||0)+"</span></div>"},'暂无在线编制数据')+"</section>";var p=[['tasks','部门任务','departmentTasks'],['dispatch','调度','dispatches'],['alerts','生命警报','vitalAlerts'],['reinforcements','增援','reinforcements']];for(var j=0;j<p.length;j++){var a=deptItems(p[j][2]);h+="<section class='dept-panel' data-dept-panel-id='"+p[j][0]+"' hidden><div class='dept-panel-head'><h3>"+p[j][1]+"</h3><span class='dept-kpi'>"+a.length+" 条</span></div><div class='dept-list'>"+deptRows(a,function(x){var ac='';if(x.status==='open')ac+=deptButton('领取/接单',{t:p[j][2]==='departmentTasks'?'dept_task_claim':(p[j][2]==='reinforcements'?'reinforcement_decide':'vital_alert_ack'),id:x.id});if(x.status==='claimed')ac+=deptButton('抵达',{t:'reinforcement_close',id:x.id,status:'arrived'});if(x.status==='dispatched')ac+="<button class='mini-btn' onclick='vitalResolvePrompt("+JSON.stringify(x.id)+")'>解决</button>";return "<article class='dept-card'><b>"+deptText(x.title||x.targetName||x.id)+"</b> "+deptStatus(x.status)+" <small>"+deptTime(x)+"</small><div class='dept-meta'>"+deptText(x.sourceDepartment||x.requestDepartment||x.department)+" → "+deptText(x.targetDepartment||x.targetPosition||'')+" · Lv."+deptText(x.minLevel||x.level||0)+" · 进度 "+deptText(x.progress||0)+"/"+deptText(x.goal||0)+"</div><p>"+deptText(x.description||x.message||x.damageSummary||x.location||x.note)+"</p><div class='dept-actions'>"+ac+"</div></article>"},'暂无'+p[j][1])+"</div></section>";}return h+'</div></div>';}


