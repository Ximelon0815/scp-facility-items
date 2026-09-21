package com.example.melonstools.network;

import com.example.melonstools.capability.PlayerPhoneProvider;
import com.example.melonstools.data.ChatRoomManager;
import com.example.melonstools.data.DailyTaskManager;
import com.example.melonstools.data.LeaderboardManager;
import com.example.melonstools.qte.QTELogicManager;
import com.example.melonstools.utils.PhoneUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPacketHandler {
    public static void handleQTEResult(QTEResultPacket packet, ServerPlayer sender) {
        if (sender != null) {
            QTELogicManager.handleClientResult(sender, packet.getQteId(), packet.isSuccess(), packet.getScore());
            // QTE 成功推进每日任务
            if (packet.isSuccess()) {
                DailyTaskManager.get(sender.serverLevel()).onEvent(sender, "qte");
                syncPhoneFull(sender);
            }
        }
    }

    // 服务端附近扫描 CD:玩家 UUID -> 上次扫描时间(毫秒)。防止绕过客户端反复扫描。
    private static final ConcurrentHashMap<UUID, Long> LAST_NEARBY_SCAN = new ConcurrentHashMap<>();
    private static final long NEARBY_COOLDOWN_MS = 10_000L;

    public static void handleRequestNearbyPlayers(RequestNearbyPlayersPacket packet, ServerPlayer sender) {
        if (sender == null) return;
        // 服务端 10 秒 CD:客户端按钮也有 10s 冷却,这里双保险
        long now = System.currentTimeMillis();
        Long last = LAST_NEARBY_SCAN.get(sender.getUUID());
        if (last != null && now - last < NEARBY_COOLDOWN_MS) {
            return;
        }
        LAST_NEARBY_SCAN.put(sender.getUUID(), now);
        // 仅本次请求计算并下发附近列表(10 格内且排除好友),页面立即刷新
        syncPhoneFull(sender, true);
    }

    /** 扫描玩家 10 格内、未添加好友、且持有身份卡的玩家(仅由"刷新附近"显式触发,不主动扫描)。
     *  仅显示身份卡上的名称(CardName), 不显示玩家 ID。 */
    private static JsonArray buildNearbyArray(ServerPlayer player) {
        JsonArray nearby = new JsonArray();
        PlayerPhoneProvider.getPhone(player).ifPresent(phone -> {
            for (Player pl : player.level().players()) {
                if (pl == player) continue;
                if (pl.distanceTo(player) > 10.0) continue;
                if (phone.isFriend(pl.getUUID())) continue;
                // 在线员工身份均从ID卡读取
                net.minecraft.world.item.ItemStack card = com.example.melonstools.utils.PhoneUtils.findIdentityCard(pl);
                if (card.isEmpty()) continue;
                // 显示身份卡上的名称; 卡无名称则回退玩家名
                String cardName = com.example.melonstools.idcard.item.custom.IdentityCardItem.getCardName(card);
                if (cardName == null || cardName.isEmpty()) cardName = pl.getScoreboardName();
                JsonObject o = new JsonObject();
                o.addProperty("uuid", pl.getUUID().toString());
                o.addProperty("name", cardName);
                o.addProperty("dist", String.format("%.1f", pl.distanceTo(player)));
                nearby.add(o);
            }
        });
        return nearby;
    }

    public static void handleFriendAction(FriendActionPacket packet, ServerPlayer sender) {
        if (sender == null) return;
        UUID target = packet.getTargetUuid();
        // 防御: 不能对自己操作好友(单人世界或异常请求路径会触发"自己加自己为好友")
        if (target == null || target.equals(sender.getUUID())) return;

        PlayerPhoneProvider.getPhone(sender).ifPresent(senderPhone -> {
            ServerPlayer targetPlayer = sender.server.getPlayerList().getPlayer(target);
            if (targetPlayer != null) {
                PlayerPhoneProvider.getPhone(targetPlayer).ifPresent(targetPhone -> {
                    switch (packet.getAction()) {
                        case ADD:
                            if (!senderPhone.isFriend(target) && !targetPhone.getPendingRequests().contains(sender.getUUID())) {
                                targetPhone.addPendingRequest(sender.getUUID());
                                syncPhoneFull(targetPlayer);
                            }
                            break;
                        case ACCEPT:
                            if (senderPhone.getPendingRequests().contains(target)) {
                                senderPhone.removePendingRequest(target);
                                senderPhone.addFriend(target);
                                targetPhone.addFriend(sender.getUUID());
                                // 好友任务推进
                                DailyTaskManager.get(sender.serverLevel()).onEvent(sender, "friend");
                                syncPhoneFull(sender);
                                syncPhoneFull(targetPlayer);
                            }
                            break;
                        case REJECT:
                            if (senderPhone.getPendingRequests().contains(target)) {
                                senderPhone.removePendingRequest(target);
                                syncPhoneFull(sender);
                            }
                            break;
                        case REMOVE:
                            if (senderPhone.isFriend(target)) {
                                senderPhone.removeFriend(target);
                                targetPhone.removeFriend(sender.getUUID());
                                syncPhoneFull(sender);
                                syncPhoneFull(targetPlayer);
                            }
                            break;
                    }
                });
            } else {
                // Handle offline target for REMOVE
                if (packet.getAction() == FriendActionPacket.Action.REMOVE) {
                    senderPhone.removeFriend(target);
                    syncPhoneFull(sender);
                }
            }
        });
    }

    public static void handleChatAction(ChatActionPacket packet, ServerPlayer sender) {
        if (sender == null) return;
        ChatRoomManager manager = ChatRoomManager.get(sender.serverLevel());

        switch (packet.getAction()) {
            case CREATE_ROOM:
                if (PhoneUtils.getPlayerIdCardLevel(sender) < 3) {
                    sender.sendSystemMessage(Component.literal("You need an ID Card level of at least 3 to create a chat room."));
                    return;
                }
                String roomId = UUID.randomUUID().toString();
                String roomName = packet.getData();
                ChatRoomManager.ChatRoom newRoom = new ChatRoomManager.ChatRoom(roomId, roomName, sender.getUUID(), packet.getAvatar());
                manager.addRoom(newRoom);
                syncPhoneFull(sender);
                break;
            case SEND_MESSAGE:
                ChatRoomManager.ChatRoom room = manager.getRoom(packet.getRoomId());
                if (room != null && room.members.contains(sender.getUUID())) {
                    // 消息发送者名称显示ID卡上的名称(CardName)
                    String senderName = PhoneUtils.getPlayerName(sender);
                    if (senderName == null || senderName.isEmpty()) senderName = sender.getScoreboardName();
                    ChatRoomManager.ChatMessage msg = new ChatRoomManager.ChatMessage(
                            sender.getUUID(), senderName, System.currentTimeMillis(), packet.getData()
                    );
                    room.addMessage(msg);
                    manager.setDirty();
                    // 聊天任务推进
                    DailyTaskManager.get(sender.serverLevel()).onEvent(sender, "chat");
                    // 广播全量给在线成员
                    for (UUID memberId : room.members) {
                        ServerPlayer member = sender.server.getPlayerList().getPlayer(memberId);
                        if (member != null) {
                            syncPhoneFull(member);
                        }
                    }
                }
                break;
            case LEAVE_ROOM:
                ChatRoomManager.ChatRoom leaveRoom = manager.getRoom(packet.getRoomId());
                if (leaveRoom != null && leaveRoom.members.contains(sender.getUUID())) {
                    leaveRoom.members.remove(sender.getUUID());
                    manager.setDirty();
                    syncPhoneFull(sender);
                    if (leaveRoom.members.isEmpty()) {
                        manager.removeRoom(leaveRoom.roomId);
                    } else {
                        for (UUID memberId : leaveRoom.members) {
                            ServerPlayer member = sender.server.getPlayerList().getPlayer(memberId);
                            if (member != null) {
                                syncPhoneFull(member);
                            }
                        }
                    }
                }
                break;
            case KICK_MEMBER:
                ChatRoomManager.ChatRoom kickRoom = manager.getRoom(packet.getRoomId());
                if (kickRoom != null && kickRoom.owner != null && kickRoom.owner.equals(sender.getUUID())) {
                    UUID target;
                    try {
                        target = UUID.fromString(packet.getData());
                    } catch (Exception e) {
                        break;
                    }
                    if (!target.equals(kickRoom.owner)) {
                        kickRoom.members.remove(target);
                        manager.setDirty();
                        ServerPlayer kicked = sender.server.getPlayerList().getPlayer(target);
                        if (kicked != null) {
                            syncPhoneFull(kicked);
                        }
                        for (UUID memberId : kickRoom.members) {
                            ServerPlayer member = sender.server.getPlayerList().getPlayer(memberId);
                            if (member != null) {
                                syncPhoneFull(member);
                            }
                        }
                    }
                }
                break;
            case RENAME_ROOM:
                ChatRoomManager.ChatRoom renameRoom = manager.getRoom(packet.getRoomId());
                if (renameRoom != null && renameRoom.owner != null && renameRoom.owner.equals(sender.getUUID())) {
                    renameRoom.roomName = packet.getData();
                    manager.setDirty();
                    for (UUID memberId : renameRoom.members) {
                        ServerPlayer member = sender.server.getPlayerList().getPlayer(memberId);
                        if (member != null) {
                            syncPhoneFull(member);
                        }
                    }
                }
                break;
            case JOIN_ROOM:
                // 输入频道名称加入
                String joinName = packet.getData() == null ? "" : packet.getData().trim();
                ChatRoomManager.ChatRoom joinRoom = manager.findRoomByName(joinName);
                if (joinRoom != null && !joinRoom.members.contains(sender.getUUID())) {
                    joinRoom.members.add(sender.getUUID());
                    manager.setDirty();
                    for (UUID memberId : joinRoom.members) {
                        ServerPlayer member = sender.server.getPlayerList().getPlayer(memberId);
                        if (member != null) {
                            syncPhoneFull(member);
                        }
                    }
                }
                break;
            case INVITE_MEMBER:
                // 房主拉好友入群
                ChatRoomManager.ChatRoom inviteRoom = manager.getRoom(packet.getRoomId());
                if (inviteRoom != null && inviteRoom.owner != null && inviteRoom.owner.equals(sender.getUUID())) {
                    UUID inviteTarget;
                    try {
                        inviteTarget = UUID.fromString(packet.getData());
                    } catch (Exception e) {
                        break;
                    }
                    boolean isFriend = PlayerPhoneProvider.getPhone(sender)
                            .map(phone -> phone.isFriend(inviteTarget))
                            .orElse(false);
                    if (isFriend && !inviteRoom.members.contains(inviteTarget)) {
                        inviteRoom.members.add(inviteTarget);
                        manager.setDirty();
                        for (UUID memberId : inviteRoom.members) {
                            ServerPlayer member = sender.server.getPlayerList().getPlayer(memberId);
                            if (member != null) {
                                syncPhoneFull(member);
                            }
                        }
                    }
                }
                break;
            case DISSOLVE_ROOM:
                // 房主解散群聊
                ChatRoomManager.ChatRoom dissolveRoom = manager.getRoom(packet.getRoomId());
                if (dissolveRoom != null && dissolveRoom.owner != null && dissolveRoom.owner.equals(sender.getUUID())) {
                    List<UUID> membersSnapshot = new ArrayList<>(dissolveRoom.members);
                    manager.removeRoom(dissolveRoom.roomId);
                    for (UUID memberId : membersSnapshot) {
                        ServerPlayer member = sender.server.getPlayerList().getPlayer(memberId);
                        if (member != null) {
                            syncPhoneFull(member);
                        }
                    }
                }
                break;
            case MARK_READ:
                // 玩家查看群聊:记录已读时间戳,该群聊旧 @ 消息不再在主页重要消息中提示
                ChatRoomManager.ChatRoom readRoom = manager.getRoom(packet.getRoomId());
                if (readRoom != null && readRoom.members.contains(sender.getUUID())) {
                    readRoom.lastRead.put(sender.getUUID(), System.currentTimeMillis());
                    manager.setDirty();
                    syncPhoneFull(sender);
                }
                break;
        }
    }

    /** 玩家打开手机时请求全量数据 */
    public static void handleRequestPhoneFull(ServerPlayer sender) {
        if (sender != null) {
            syncPhoneFull(sender);
        }
    }

    /** 提交实验报告：科研部门所有等级（1-5）或 OP。 */
    public static void handleSubmitReport(SubmitReportPacket packet, ServerPlayer sender) {
        if (sender == null) return;
        if (!canSubmitResearchReports(sender)) return;
        com.example.melonstools.anomaly.AnomalySavedData data = com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel());
        if (data.submitReport(packet.getAnomalyInstanceId(), sender, packet.getTitle(), packet.getContent()) != null) {
            syncPhoneFull(sender);
        }
    }

    /** 领取每日任务奖励 */
    public static void handleClaimTask(ClaimTaskPacket packet, ServerPlayer sender) {
        if (sender == null) return;
        if (DailyTaskManager.get(sender.serverLevel()).claim(sender, packet.getTaskId())) {
            // TODO: 接入 Lightman's Currency 发奖励
            syncPhoneFull(sender);
        }
    }

    private static boolean hasCardPermission(ServerPlayer player, String department, int minLevel) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(player);
        return com.example.melonstools.department.DepartmentPolicy.normalizeDepartment(department).equals(id.department()) && id.level() >= minLevel;
    }

    private static boolean canPublishNotice(ServerPlayer player) {
        return canPublishNotice(player, com.example.melonstools.department.DepartmentPolicy.NOTICE_ORDINARY);
    }

    private static boolean canPublishNotice(ServerPlayer player, String noticeLevel) {
        return canPublishNotice(player, noticeLevel, "ALL");
    }

    private static boolean canPublishNotice(ServerPlayer player, String noticeLevel, String targetDepartment) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(player);
        String nl = com.example.melonstools.department.DepartmentPolicy.normalizeNoticeLevel(noticeLevel);
        if (com.example.melonstools.department.DepartmentPolicy.NOTICE_DEPARTMENT.equals(nl)) {
            return com.example.melonstools.department.DepartmentPolicy.isDeptSupervisor(id.department(), id.position(), id.level())
                    && id.department().equals(com.example.melonstools.department.DepartmentPolicy.normalizeDepartment(targetDepartment));
        }
        return com.example.melonstools.department.DepartmentPolicy.canPublishNotice(id.department(), id.position(), id.level(), noticeLevel, false);
    }

    private static boolean canManageNotice(ServerPlayer player) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(player);
        return com.example.melonstools.department.DepartmentPolicy.isClerk(id.department(), id.position(), id.level())
                || com.example.melonstools.department.DepartmentPolicy.isDeptSupervisor(id.department(), id.position(), id.level())
                || com.example.melonstools.department.DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level());
    }

    private static boolean canManageNotice(ServerPlayer player, com.example.melonstools.data.FacilityNoticeSavedData.Notice notice) {
        if (player == null || notice == null) return false;
        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(player);
        return com.example.melonstools.department.DepartmentPolicy.canManageNotice(id.department(), id.position(), id.level(), player.getUUID(), notice.noticeLevel, notice.ownerUuid, notice.targetDepartment, player.hasPermissions(2));
    }

    private static boolean canDeleteNotice(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    private static boolean canExpandFacility(ServerPlayer player) {
        return com.example.melonstools.department.DepartmentPolicy.canExpandFacility(player);
    }

    private static boolean canManageAnomalies(ServerPlayer player) {
        return com.example.melonstools.department.DepartmentPolicy.canManageAnomalies(player);
    }

    private static boolean canViewResearchDetails(ServerPlayer player) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(player);
        return com.example.melonstools.department.DepartmentPolicy.DEPT_RESEARCH.equals(id.department()) && id.level() >= 1 && id.level() <= 4;
    }

    private static boolean canSubmitResearchReports(ServerPlayer player) {
        if (player == null) return false;
        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(player);
        return com.example.melonstools.department.DepartmentPolicy.canSubmitReport(id.department(), id.position(), id.level(), player.hasPermissions(2));
    }

    /** 实验报告审核仅限设施终端中的设施主管5级或 OP，不按科研/其他4级放宽。 */
    private static boolean canReviewResearchReports(ServerPlayer player) {
        return com.example.melonstools.department.DepartmentPolicy.canReviewReport(player);
    }

    private static boolean canClaimHqTask(ServerPlayer player, com.example.melonstools.data.HqTaskSavedData.HqTask task) {
        if (player == null || task == null) return false;
        if (player.hasPermissions(2)) return true;
        String dept = task.department == null ? "ALL" : task.department;
        if ("ALL".equalsIgnoreCase(dept)) return true;
        return hasCardPermission(player, dept, task.level);
    }

    /** 处理终端动作(JSON 数组)。目前支持:任务/资金/异常物等终端动作 */
    public static void handleTerminalAction(TerminalActionPacket packet, ServerPlayer sender) {
        if (sender == null) return;
        try {
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(packet.getJson()).getAsJsonArray();
            for (com.google.gson.JsonElement e : arr) {
                com.google.gson.JsonObject o = e.getAsJsonObject();
                if (!o.has("t")) continue;
                String t = o.get("t").getAsString();
                com.example.melonstools.data.TaskConfigManager tm = com.example.melonstools.data.TaskConfigManager.get(sender.serverLevel());
                com.example.melonstools.data.FacilityFundManager fm = com.example.melonstools.data.FacilityFundManager.get(sender.serverLevel());
                com.example.melonstools.data.HqTaskSavedData hq = com.example.melonstools.data.HqTaskSavedData.get(sender.serverLevel());
                switch (t) {
                    case "task_save": {
                        if (!sender.hasPermissions(2)) break;
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        if (id == null || id.isEmpty()) id = tm.nextId();
                        com.example.melonstools.data.TaskConfigManager.TaskTemplate template = new com.example.melonstools.data.TaskConfigManager.TaskTemplate(
                                id,
                                o.get("name").getAsString(),
                                o.has("desc") ? o.get("desc").getAsString() : "",
                                o.has("type") ? o.get("type").getAsString() : "qte",
                                o.has("goal") ? o.get("goal").getAsInt() : 1,
                                o.has("reward") ? o.get("reward").getAsString() : "",
                                o.has("dept") ? o.get("dept").getAsString() : "ALL",
                                o.has("daily") ? o.get("daily").getAsBoolean() : true,
                                true
                        );
                        tm.upsert(template);
                        break;
                    }
                    case "task_delete": {
                        if (!sender.hasPermissions(2)) break;
                        String id = o.get("id").getAsString();
                        tm.remove(id);
                        break;
                    }
                    case "task_toggle": {
                        if (!sender.hasPermissions(2)) break;
                        String id = o.get("id").getAsString();
                        com.example.melonstools.data.TaskConfigManager.TaskTemplate tpl = tm.findById(id);
                        if (tpl != null) tm.setEnabled(id, !tpl.enabled);
                        break;
                    }
                    case "fund_deposit": {
                        if (!sender.hasPermissions(2)) break;
                        long amount = o.has("amount") ? o.get("amount").getAsLong() : 0L;
                        String note = o.has("note") ? o.get("note").getAsString() : "管理员入账";
                        if (fm.deposit(amount, sender.getScoreboardName(), note)) {
                            com.example.melonstools.data.HqTaskSavedData.recordFundBalance(sender.serverLevel(), fm.getBalance());
                        }
                        break;
                    }
                    case "fund_withdraw": {
                        if (!sender.hasPermissions(2)) break;
                        long amount = o.has("amount") ? o.get("amount").getAsLong() : 0L;
                        String note = o.has("note") ? o.get("note").getAsString() : "管理员扣款";
                        if (fm.withdraw(amount, sender.getScoreboardName(), note)) {
                            com.example.melonstools.data.HqTaskSavedData.recordFundBalance(sender.serverLevel(), fm.getBalance());
                        }
                        break;
                    }
                    case "salary_pay": {
                        if (!sender.hasPermissions(2)) break;
                        long amount = o.has("amount") ? o.get("amount").getAsLong() : 0L;
                        String targetName = o.has("target") ? o.get("target").getAsString() : "";
                        java.util.UUID targetUuid = null;
                        if (o.has("uuid")) {
                            try { targetUuid = java.util.UUID.fromString(o.get("uuid").getAsString()); } catch (Throwable ignored) { }
                        }
                        if (targetUuid == null) {
                            targetUuid = com.example.melonstools.idcard.LCIntegration.resolvePlayerUUID(sender.server, targetName);
                        }
                        String note = o.has("note") ? o.get("note").getAsString() : "工资发放";
                        if (false && fm.paySalary(sender.serverLevel(), targetUuid, targetName, amount, sender.getScoreboardName(), note)) {
                            com.example.melonstools.data.HqTaskSavedData.recordFundBalance(sender.serverLevel(), fm.getBalance());
                        }
                        break;
                    }
                    case "payroll_batch_create": { if(!com.example.melonstools.department.DepartmentPolicy.isFacilityDirector(sender)&&!sender.hasPermissions(2)){sender.sendSystemMessage(Component.literal("§c无薪资批次创建权限。"));break;} com.example.melonstools.data.PayrollSavedData pd=com.example.melonstools.data.PayrollSavedData.get(sender.serverLevel()); com.example.melonstools.data.PayrollSavedData.Batch pb=pd.createBatch(sender,stringField(o,"periodLabel"),stringField(o,"note")); auditPayroll(sender,t,pb==null?"":pb.batchId,pb==null?"failed":"success","create"); sender.sendSystemMessage(Component.literal(pb==null?"§c薪资批次创建失败。":"§a薪资批次已创建："+pb.batchId)); syncTerminalData(sender); break; }
                     case "payroll_batch_add_line": { com.example.melonstools.data.PayrollSavedData pd=com.example.melonstools.data.PayrollSavedData.get(sender.serverLevel()); boolean ok=pd.addLine(sender,stringField(o,"batchId"),stringField(o,"targetName"),stringField(o,"targetUuid"),longField(o,"amount"),stringField(o,"reason")); auditPayroll(sender,t,stringField(o,"batchId"),ok?"success":"failed","add_line"); syncTerminalData(sender); break; }
                     case "payroll_batch_remove_line": { com.example.melonstools.data.PayrollSavedData pd=com.example.melonstools.data.PayrollSavedData.get(sender.serverLevel()); boolean ok=pd.removeLine(sender,stringField(o,"batchId"),stringField(o,"lineId")); auditPayroll(sender,t,stringField(o,"batchId"),ok?"success":"failed","remove_line"); syncTerminalData(sender); break; }
                     case "payroll_batch_submit": { com.example.melonstools.data.PayrollSavedData pd=com.example.melonstools.data.PayrollSavedData.get(sender.serverLevel()); boolean ok=pd.submit(sender,stringField(o,"batchId"),longField(o,"expectedVersion")); auditPayroll(sender,t,stringField(o,"batchId"),ok?"success":"failed","submit"); syncTerminalData(sender); break; }
                     case "payroll_batch_approve": { com.example.melonstools.data.PayrollSavedData pd=com.example.melonstools.data.PayrollSavedData.get(sender.serverLevel()); boolean ok=pd.approve(sender,stringField(o,"batchId"),longField(o,"expectedVersion")); auditPayroll(sender,t,stringField(o,"batchId"),ok?"success":"failed","approve"); syncTerminalData(sender); break; }
                     case "payroll_batch_cancel": { com.example.melonstools.data.PayrollSavedData pd=com.example.melonstools.data.PayrollSavedData.get(sender.serverLevel()); boolean ok=pd.cancel(sender,stringField(o,"batchId"),longField(o,"expectedVersion")); auditPayroll(sender,t,stringField(o,"batchId"),ok?"success":"failed","cancel"); syncTerminalData(sender); break; }
                     case "payroll_batch_retry": { com.example.melonstools.data.PayrollSavedData pd=com.example.melonstools.data.PayrollSavedData.get(sender.serverLevel()); boolean ok=pd.retry(sender,stringField(o,"batchId"),longField(o,"expectedVersion")); auditPayroll(sender,t,stringField(o,"batchId"),ok?"success":"failed","retry"); syncTerminalData(sender); break; }
                     case "payroll_sync": { syncTerminalData(sender); break; }
                    case "payroll_batch_pay": { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, o.has("batchId") ? o.get("batchId").getAsString() : "", "denied", "LC退款/事务API未验证，禁止付款", "", ""); sender.sendSystemMessage(Component.literal("§c付款已安全拒绝：LC退款/事务API未验证，不允许部分付款。")); syncTerminalData(sender); break; }
                    case "anomaly_codex_reload": {
                        if (!requireAnomalyPermission(sender, t, "codex")) break;
                        com.example.melonstools.anomaly.AnomalyCodexManager.reload();
                        auditAnomaly(sender, t, "codex", "success", "", "", "reloaded");
                        sender.sendSystemMessage(Component.literal("§a异常档案已重载。"));
                        syncAnomalyClients(sender);
                        break;
                    }
                    case "anomaly_slot_unlock": {
                        String slotId = stringField(o, "slotId");
                        if (!canExpandFacility(sender)) { auditAnomaly(sender, t, slotId, "denied", "facility expansion permission", "", ""); sendAnomalyResult(sender, false, "无设施扩建权限"); break; }
                        com.example.melonstools.anomaly.AnomalySavedData ad = com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel());
                        com.example.melonstools.anomaly.AnomalySavedData.RoomSlot slot = ad.getSlot(slotId);
                        if (slot == null || slot.unlocked) { auditAnomaly(sender, t, slotId, "failed", "slot missing or already unlocked", "", ""); sendAnomalyResult(sender, false, "隔间不存在或已解锁"); break; }
                        if (fm.getBalance() < slot.unlockCost || !fm.withdraw(slot.unlockCost, sender.getScoreboardName(), "扩建收容隔间 " + slotId)) { auditAnomaly(sender, t, slotId, "failed", "insufficient facility funds", "locked", "locked"); sendAnomalyResult(sender, false, "设施资金不足"); break; }
                        boolean unlocked = ad.unlockSlot(slotId);
                        if (!unlocked) fm.deposit(slot.unlockCost, sender.getScoreboardName(), "扩建失败自动回滚 " + slotId);
                        auditAnomaly(sender, t, slotId, unlocked ? "success" : "failed", unlocked ? "" : "unlock rejected; funds rolled back", "locked", unlocked ? "unlocked" : "locked");
                        if (unlocked) com.example.melonstools.data.HqTaskSavedData.recordEvent(sender.serverLevel(), com.example.melonstools.data.HqTaskSavedData.EVENT_SLOT_UNLOCKED);
                        com.example.melonstools.data.HqTaskSavedData.recordFundBalance(sender.serverLevel(), fm.getBalance());
                        sendAnomalyResult(sender, unlocked, unlocked ? "隔间扩建完成" : "隔间扩建失败");
                        syncAnomalyClients(sender);
                        break;
                    }
                    case "anomaly_slot_add":
                    case "anomaly_instance_add":
                    case "anomaly_add": {
                        String codexId = stringField(o, "codexId"), slotId = stringField(o, "slotId");
                        if (!requireAnomalyPermission(sender, t, slotId)) break;
                        com.example.melonstools.anomaly.AnomalySavedData.AnomalyInstance added = com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel()).addInstanceToSlot(codexId, slotId);
                        auditAnomaly(sender, t, added == null ? slotId : added.instanceId, added == null ? "failed" : "success", added == null ? "invalid codex/slot or occupied" : "", "empty", added == null ? "empty" : com.example.melonstools.anomaly.AnomalySavedData.auditSummary(added));
                        if (added != null) com.example.melonstools.data.HqTaskSavedData.recordEvent(sender.serverLevel(), com.example.melonstools.data.HqTaskSavedData.EVENT_ANOMALY_CONTAINED);
                        sendAnomalyResult(sender, added != null, added != null ? "异常物已放置" : "放置失败：检查档案与隔间状态");
                        syncAnomalyClients(sender);
                        break;
                    }
                    case "anomaly_slot_remove":
                    case "anomaly_instance_delete": {
                        String instanceId = stringField(o, "instanceId");
                        if (!requireAnomalyPermission(sender, t, instanceId)) break;
                        if (!"true".equalsIgnoreCase(stringField(o, "confirmed"))) { auditAnomaly(sender, t, instanceId, "failed", "second confirmation required", "", ""); sendAnomalyResult(sender, false, "移除需要二次确认"); break; }
                        com.example.melonstools.anomaly.AnomalySavedData ad = com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel());
                        String before = com.example.melonstools.anomaly.AnomalySavedData.auditSummary(ad.getInstance(instanceId));
                        boolean ok = ad.removeInstance(instanceId);
                        auditAnomaly(sender, t, instanceId, ok ? "success" : "failed", ok ? limitActionNote(stringField(o,"note")) : "unknown instance", before, ok ? "removed" : before);
                        sendAnomalyResult(sender, ok, ok ? "异常物已移出隔间" : "异常实例不存在");
                        syncAnomalyClients(sender);
                        break;
                    }
                    case "anomaly_detail_request": { syncTerminalData(sender); break; }
                    case "anomaly_params_update":
                    case "anomaly_mark_maintained":
                    case "anomaly_emergency_set":
                    case "anomaly_instance_move": {
                        handleAnomalyMutation(t, o, sender);
                        break;
                    }
                    case "notice_publish": {
                        String noticeLevel = o.has("noticeLevel") ? o.get("noticeLevel").getAsString() : (o.has("level") ? o.get("level").getAsString() : com.example.melonstools.department.DepartmentPolicy.NOTICE_ORDINARY);
                        String title = o.has("title") ? o.get("title").getAsString() : "";
                        String content = o.has("content") ? o.get("content").getAsString() : (o.has("body") ? o.get("body").getAsString() : "");
                        String targetDepartment = o.has("targetDepartment") ? o.get("targetDepartment").getAsString() : "ALL";
                        if (!canPublishNotice(sender, noticeLevel, targetDepartment)) break;
                        long expiresAt = o.has("expiresAt") ? o.get("expiresAt").getAsLong() : 0L;
                        if (expiresAt <= 0L && o.has("expireHours")) {
                            long hours = o.get("expireHours").getAsLong();
                            if (hours > 0L) expiresAt = System.currentTimeMillis() + hours * 60L * 60L * 1000L;
                        }
                        boolean pinned = o.has("pinned") && o.get("pinned").getAsBoolean();
                        com.example.melonstools.data.FacilityNoticeSavedData.Notice notice = com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).publish(sender, title, content, targetDepartment, expiresAt, pinned, noticeLevel);
                        if (notice != null) {
                            com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_publish", notice.id, "success", notice.noticeLevel, "", notice.status);
                            com.example.melonstools.data.HqTaskSavedData.recordEvent(sender.serverLevel(), com.example.melonstools.data.HqTaskSavedData.EVENT_NOTICE_PUBLISHED);
                            syncAllPhones(sender.server);
                        } else {
                            com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_publish", "notice", "failed", "invalid notice payload", "", "");
                        }
                        break;
                    }
                    case "notice_update": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        com.example.melonstools.data.FacilityNoticeSavedData.Notice existingNotice = com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).findById(id);
                        if (!canManageNotice(sender, existingNotice)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, id, "denied", "notice permission", "", ""); break; }
                        String title = o.has("title") ? o.get("title").getAsString() : "";
                        String content = o.has("content") ? o.get("content").getAsString() : (o.has("body") ? o.get("body").getAsString() : "");
                        String targetDepartment = o.has("targetDepartment") ? o.get("targetDepartment").getAsString() : "ALL";
                        long expiresAt = o.has("expiresAt") ? o.get("expiresAt").getAsLong() : 0L;
                        if (expiresAt <= 0L && o.has("expireHours")) {
                            long hours = o.get("expireHours").getAsLong();
                            if (hours > 0L) expiresAt = System.currentTimeMillis() + hours * 60L * 60L * 1000L;
                        }
                        boolean pinned = o.has("pinned") && o.get("pinned").getAsBoolean();
                        String noticeLevel = o.has("noticeLevel") ? o.get("noticeLevel").getAsString() : null;
                        if (noticeLevel != null && !canPublishNotice(sender, noticeLevel, targetDepartment)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_update", id, "denied", "cannot escalate notice level", "", ""); break; }
                        if (com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).update(id, title, content, targetDepartment, expiresAt, pinned, noticeLevel)) {
                            com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_update", id, "success", "", "", "updated");
                            syncAllPhones(sender.server);
                        }
                        break;
                    }
                    case "notice_pin":
                    case "notice_unpin": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        com.example.melonstools.data.FacilityNoticeSavedData.Notice existingNotice = com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).findById(id);
                        if (!canManageNotice(sender, existingNotice)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, id, "denied", "notice permission", "", ""); break; }
                        boolean pinned = "notice_pin".equals(t);
                        if (o.has("pinned")) pinned = o.get("pinned").getAsBoolean();
                        if (com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).setPinned(id, pinned)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, id, "success", "", "", pinned ? "pinned" : "unpinned"); syncAllPhones(sender.server); }
                        break;
                    }
                    case "notice_archive": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        com.example.melonstools.data.FacilityNoticeSavedData.Notice existingNotice = com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).findById(id);
                        if (!canManageNotice(sender, existingNotice)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, id, "denied", "notice permission", "", ""); break; }
                        if (com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).archive(id)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_archive", id, "success", "", "", "archived"); syncAllPhones(sender.server); }
                        break;
                    }
                    case "notice_expire": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        com.example.melonstools.data.FacilityNoticeSavedData.Notice existingNotice = com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).findById(id);
                        if (!canManageNotice(sender, existingNotice)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, id, "denied", "notice permission", "", ""); break; }
                        if (com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).expire(id)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_expire", id, "success", "", "", "expired"); syncAllPhones(sender.server); }
                        break;
                    }
                    case "notice_delete": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        if (!canDeleteNotice(sender)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_delete", id, "denied", "OP hard delete only", "", ""); break; }
                        if (com.example.melonstools.data.FacilityNoticeSavedData.get(sender.serverLevel()).delete(id)) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "notice_delete", id, "success", "hard delete", "", "deleted"); syncAllPhones(sender.server); }
                        break;
                    }
                    case "mgmt_archive_create":
                    case "mgmt_archive_stub": {
                        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(sender);
                        boolean allowed = com.example.melonstools.department.DepartmentPolicy.isClerk(id.department(), id.position(), id.level()) || com.example.melonstools.department.DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level()) || sender.hasPermissions(2);
                        if (!allowed) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, "managementOffice", "denied", "no archive permission", "", ""); break; }
                        com.example.melonstools.data.ManagementOfficeSavedData.AdminRecord r = com.example.melonstools.data.ManagementOfficeSavedData.get(sender.serverLevel()).createArchive(sender, o.has("title") ? o.get("title").getAsString() : "", o.has("body") ? o.get("body").getAsString() : "", o.has("sourceType") ? o.get("sourceType").getAsString() : "manual", o.has("sourceId") ? o.get("sourceId").getAsString() : "");
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, r.id, "success", "", "", r.status);
                        syncTerminalData(sender);
                        break;
                    }
                    case "mgmt_archive_archive": {
                        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(sender);
                        boolean allowed = com.example.melonstools.department.DepartmentPolicy.isClerk(id.department(), id.position(), id.level()) || com.example.melonstools.department.DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level()) || sender.hasPermissions(2);
                        String rid = o.has("id") ? o.get("id").getAsString() : "";
                        if (!allowed) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, rid, "denied", "no archive permission", "", ""); break; }
                        boolean ok = com.example.melonstools.data.ManagementOfficeSavedData.get(sender.serverLevel()).archiveRecord(rid, sender);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, rid, ok ? "success" : "failed", "archive admin record", "", ok ? "archived" : "");
                        syncTerminalData(sender);
                        break;
                    }
                    case "mgmt_meeting_create":
                    case "mgmt_meeting_invite_stub": {
                        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(sender);
                        boolean director = com.example.melonstools.department.DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level());
                        boolean clerk = com.example.melonstools.department.DepartmentPolicy.isClerk(id.department(), id.position(), id.level());
                        boolean high = o.has("highPriority") && o.get("highPriority").getAsBoolean();
                        if (!(sender.hasPermissions(2) || director || (clerk && !high))) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, "managementOffice", "denied", "no meeting permission", "", ""); break; }
                        com.example.melonstools.data.ManagementOfficeSavedData.AdminRecord r = com.example.melonstools.data.ManagementOfficeSavedData.get(sender.serverLevel()).createMeeting(sender, o.has("title") ? o.get("title").getAsString() : "", o.has("body") ? o.get("body").getAsString() : "", o.has("startAt") ? o.get("startAt").getAsLong() : 0L, o.has("location") ? o.get("location").getAsString() : "", o.has("participants") ? o.get("participants").getAsString() : "", high);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, r.id, "success", "combat interception not connected", "", r.status);
                        syncTerminalData(sender);
                        break;
                    }
                    case "mgmt_meeting_respond": {
                        String rid = o.has("id") ? o.get("id").getAsString() : "";
                        boolean ok = com.example.melonstools.data.ManagementOfficeSavedData.get(sender.serverLevel()).respondMeeting(rid, sender, o.has("response") ? o.get("response").getAsString() : "accepted", o.has("note") ? o.get("note").getAsString() : "");
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, rid, ok ? "success" : "failed", "meeting receipt", "", o.has("response") ? o.get("response").getAsString() : "accepted");
                        syncTerminalData(sender);
                        break;
                    }
                    case "mgmt_policy_publish":
                    case "mgmt_policy_stub": {
                        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(sender);
                        if (!(sender.hasPermissions(2) || com.example.melonstools.department.DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level()))) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, "managementOffice", "denied", "no policy permission", "", ""); break; }
                        com.example.melonstools.data.ManagementOfficeSavedData.AdminRecord r = com.example.melonstools.data.ManagementOfficeSavedData.get(sender.serverLevel()).publishPolicy(sender, o.has("title") ? o.get("title").getAsString() : "", o.has("body") ? o.get("body").getAsString() : "");
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, r.id, "success", "", "", r.status);
                        syncTerminalData(sender);
                        break;
                    }
                    case "mgmt_policy_close": {
                        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(sender);
                        String rid = o.has("id") ? o.get("id").getAsString() : "";
                        if (!(sender.hasPermissions(2) || com.example.melonstools.department.DepartmentPolicy.isFacilityDirector(id.department(), id.position(), id.level()))) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, rid, "denied", "no policy permission", "", ""); break; }
                        boolean ok = com.example.melonstools.data.ManagementOfficeSavedData.get(sender.serverLevel()).closePolicy(rid, sender);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, rid, ok ? "success" : "failed", "close policy", "", ok ? "closed" : "");
                        syncTerminalData(sender);
                        break;
                    }
                    case "hq_task_save": {
                        if (!sender.hasPermissions(2)) break; // 总部任务编辑严格 OP-only, 不走 ID 卡等级
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        if (id == null || id.isBlank()) id = hq.nextId();
                        com.example.melonstools.data.HqTaskSavedData.HqTask task = new com.example.melonstools.data.HqTaskSavedData.HqTask(
                                id,
                                o.has("title") ? o.get("title").getAsString() : (o.has("name") ? o.get("name").getAsString() : ""),
                                o.has("desc") ? o.get("desc").getAsString() : "",
                                o.has("status") ? o.get("status").getAsString() : com.example.melonstools.data.HqTaskSavedData.STATUS_ACTIVE,
                                o.has("progress") ? o.get("progress").getAsInt() : 0,
                                o.has("goal") ? o.get("goal").getAsInt() : 1,
                                o.has("rewardFunds") ? o.get("rewardFunds").getAsLong() : 0L,
                                o.has("department") ? o.get("department").getAsString() : "ALL",
                                o.has("level") ? o.get("level").getAsInt() : 1,
                                o.has("eventType") ? o.get("eventType").getAsString() : com.example.melonstools.data.HqTaskSavedData.EVENT_MANUAL
                        );
                        hq.upsert(task);
                        break;
                    }
                    case "hq_task_delete": {
                        if (!sender.hasPermissions(2)) break;
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        hq.remove(id);
                        break;
                    }
                    case "hq_task_claim": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        com.example.melonstools.data.HqTaskSavedData.HqTask task = hq.findById(id);
                        if (!canClaimHqTask(sender, task)) break;
                        long reward = hq.claimReward(id);
                        if (reward >= 0L) {
                            if (reward > 0L) {
                                fm.deposit(reward, sender.getScoreboardName(), "总部任务奖励 " + (task == null ? id : task.title));
                                com.example.melonstools.data.HqTaskSavedData.recordFundBalance(sender.serverLevel(), fm.getBalance());
                            }
                            sender.sendSystemMessage(Component.literal("§a总部任务奖励已结算。"));
                        }
                        break;
                    }
                    case "hq_task_complete":
                    case "hq_task_force_complete": {
                        if (!sender.hasPermissions(2)) break;
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        hq.forceComplete(id);
                        break;
                    }
                    case "hq_task_fail":
                    case "hq_task_force_fail": {
                        if (!sender.hasPermissions(2)) break;
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        hq.forceFail(id);
                        break;
                    }
                    case "hq_task_fill": {
                        if (!sender.hasPermissions(2)) break;
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        hq.fillProgress(id);
                        break;
                    }
                    case "report_approve": {
                        if (!canReviewResearchReports(sender)) break;
                        String reportId = o.has("reportId") ? o.get("reportId").getAsString() : "";
                        int researchDelta = o.has("researchDelta") ? o.get("researchDelta").getAsInt() : 5;
                        com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel()).approveReport(reportId, sender, researchDelta);
                        break;
                    }
                    case "report_reject": {
                        if (!canReviewResearchReports(sender)) break;
                        String reportId = o.has("reportId") ? o.get("reportId").getAsString() : "";
                        String reason = o.has("reason") ? o.get("reason").getAsString() : "";
                        com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel()).rejectReport(reportId, sender, reason);
                        break;
                    }
                    case "report_archive": {
                        if (!canReviewResearchReports(sender)) break;
                        String reportId = o.has("reportId") ? o.get("reportId").getAsString() : "";
                        com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel()).archiveReport(reportId, sender);
                        break;
                    }
                    case "application_submit":
                    case "dept_app_submit":
                    case "research_application_submit": {
                        com.example.melonstools.data.DepartmentApplicationSavedData apps = com.example.melonstools.data.DepartmentApplicationSavedData.get(sender.serverLevel());
                        com.example.melonstools.data.OperationAuditSavedData audit = com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel());
                        String type = o.has("type") ? o.get("type").getAsString() : "join_department";
                        String targetDepartment = o.has("targetDepartment") ? o.get("targetDepartment").getAsString() : "";
                        String reason = o.has("reason") ? o.get("reason").getAsString() : "";
                        com.example.melonstools.data.DepartmentApplicationSavedData.Application app;
                        if ("RESEARCH_EXPERIMENT".equalsIgnoreCase(type) || "research_experiment".equalsIgnoreCase(type) || "RESEARCH_PURCHASE".equalsIgnoreCase(type) || "research_purchase".equalsIgnoreCase(type)) {
                            app = apps.submitResearchSkeleton(sender, o);
                        } else {
                            app = apps.submit(sender, type, targetDepartment, reason);
                        }
                        audit.record(sender, "application_submit", app == null ? targetDepartment : app.id, app == null ? "denied" : "ok", reason, "", app == null ? "" : app.status);
                        break;
                    }
                    case "doc_save_draft":
                    case "research_doc_save":
                    case "doc_submit":
                    case "research_doc_submit":
                    case "academic_bulletin_submit": {
                        com.example.melonstools.data.ResearchDocumentSavedData.ResearchDocument doc = new com.example.melonstools.data.ResearchDocumentSavedData.ResearchDocument();
                        doc.id = o.has("id") ? o.get("id").getAsString() : "";
                        doc.type = "academic_bulletin_submit".equals(t) ? com.example.melonstools.data.ResearchDocumentSavedData.TYPE_ACADEMIC_BULLETIN : (o.has("docType") ? o.get("docType").getAsString() : (o.has("type") ? o.get("type").getAsString() : com.example.melonstools.data.ResearchDocumentSavedData.TYPE_ANOMALY_DOCUMENT));
                        doc.title = o.has("title") ? o.get("title").getAsString() : "";
                        doc.body = o.has("body") ? o.get("body").getAsString() : "";
                        doc.anomalyInstanceId = o.has("anomalyInstanceId") ? o.get("anomalyInstanceId").getAsString() : "";
                        doc.targetType = o.has("targetType") ? o.get("targetType").getAsString() : "";
                        doc.reportId = o.has("reportId") ? o.get("reportId").getAsString() : "";
                        doc.documentId = o.has("documentId") ? o.get("documentId").getAsString() : "";
                        com.example.melonstools.data.ResearchDocumentSavedData data = com.example.melonstools.data.ResearchDocumentSavedData.get(sender.serverLevel());
                        com.example.melonstools.data.ResearchDocumentSavedData.ResearchDocument saved = "academic_bulletin_submit".equals(t) ? data.submitBulletin(sender.serverLevel(), sender, doc) : (("doc_submit".equals(t) || "research_doc_submit".equals(t)) ? data.submit(sender.serverLevel(), sender, doc) : data.saveDraft(sender.serverLevel(), sender, doc));
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, saved == null ? doc.title : saved.id, saved == null ? "denied" : "ok", "research_document", "", saved == null ? "" : saved.status);
                        break;
                    }
                    case "research_doc_revise": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        String title = o.has("title") ? o.get("title").getAsString() : "";
                        String body = o.has("body") ? o.get("body").getAsString() : "";
                        String summary = o.has("summary") ? o.get("summary").getAsString() : "";
                        com.example.melonstools.data.ResearchDocumentSavedData.ResearchDocument saved = com.example.melonstools.data.ResearchDocumentSavedData.get(sender.serverLevel()).revise(sender.serverLevel(), sender, id, title, body, summary);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, id, saved == null ? "denied" : "ok", summary, "", saved == null ? "" : ("v" + saved.version));
                        break;
                    }
                    case "research_doc_archive": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        String reason = o.has("reason") ? o.get("reason").getAsString() : "";
                        com.example.melonstools.data.ResearchDocumentSavedData.ResearchDocument saved = com.example.melonstools.data.ResearchDocumentSavedData.get(sender.serverLevel()).archive(sender, id, reason);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, t, id, saved == null ? "denied" : "ok", reason, "", saved == null ? "" : saved.status);
                        break;
                    }
                    case "doc_query": {
                        break;
                    }
                    case "application_cancel":
                    case "dept_app_cancel": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        boolean ok = com.example.melonstools.data.DepartmentApplicationSavedData.get(sender.serverLevel()).cancel(id, sender);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "application_cancel", id, ok ? "ok" : "denied", "", "", "");
                        break;
                    }
                    case "dept_app_decide": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        String decision = o.has("decision") ? o.get("decision").getAsString() : "";
                        String comment = o.has("comment") ? o.get("comment").getAsString() : (o.has("reason") ? o.get("reason").getAsString() : "");
                        com.example.melonstools.data.DepartmentApplicationSavedData apps = com.example.melonstools.data.DepartmentApplicationSavedData.get(sender.serverLevel());
                        boolean reject = "reject".equalsIgnoreCase(decision) || "rejected".equalsIgnoreCase(decision) || "deny".equalsIgnoreCase(decision) || "denied".equalsIgnoreCase(decision);
                        if (reject) {
                            boolean ok = apps.reject(id, sender, comment);
                            com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "dept_app_decide", id, ok ? "ok" : "denied", "decision=reject;" + comment, "", "rejected");
                            break;
                        }
                        boolean approve = "approve".equalsIgnoreCase(decision) || "approved".equalsIgnoreCase(decision) || "accept".equalsIgnoreCase(decision) || decision.isBlank();
                        if (!approve) {
                            com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "dept_app_decide", id, "denied", "unknown_decision=" + decision, "", "");
                            break;
                        }
                        com.example.melonstools.data.DepartmentApplicationSavedData.Application app = apps.approve(id, sender, comment);
                        String queueId = "";
                        if (app != null && "card_change_required".equals(app.status) && (app.cardQueueId == null || app.cardQueueId.isEmpty())) {
                            com.example.melonstools.data.PendingCardQueueSavedData.Record rec = com.example.melonstools.data.PendingCardQueueSavedData.get(sender.serverLevel()).createFromApplication(app, sender);
                            if (rec != null) { app.cardQueueId = rec.id; apps.setDirty(); queueId = rec.id; }
                            com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "card_queue_created", id, rec == null ? "denied" : "ok", "manual_card_change_required_no_nbt_write", "", queueId);
                        }
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "dept_app_decide", id, app == null ? "denied" : "ok", "decision=approve;" + comment, "", app == null ? "" : app.status + ":" + queueId);
                        break;
                    }
                    case "dept_app_reject": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        String reason = o.has("reason") ? o.get("reason").getAsString() : "";
                        boolean ok = com.example.melonstools.data.DepartmentApplicationSavedData.get(sender.serverLevel()).reject(id, sender, reason);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "dept_app_reject", id, ok ? "ok" : "denied", reason, "", "");
                        break;
                    }
                    case "card_queue_mark_completed": {
                        String id = o.has("id") ? o.get("id").getAsString() : "";
                        boolean allowed = com.example.melonstools.department.DepartmentPolicy.canCompleteCardQueue(
                                com.example.melonstools.utils.PhoneUtils.getPlayerDepartment(sender),
                                com.example.melonstools.utils.PhoneUtils.getPlayerPosition(sender),
                                com.example.melonstools.utils.PhoneUtils.getPlayerIdCardLevel(sender),
                                sender.hasPermissions(2));
                        boolean ok = allowed && com.example.melonstools.data.PendingCardQueueSavedData.get(sender.serverLevel()).markCompleted(id, sender);
                        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "card_queue_mark_completed", id, ok ? "ok" : "denied", "manual_confirmation_only_no_card_nbt_read_or_write", "", "queue_status_only");
                        break;
                    }
                    case "procurement_create":
                    case "procurement_submit":
                    case "procurement_approve":
                    case "procurement_order":
                    case "procurement_receive":
                    case "procurement_complete":
                    case "procurement_reject":
                    case "procurement_cancel":
                    case "facility_payment_create":
                    case "facility_payment_approve":
                    case "facility_payment_pay":
                    case "facility_payment_cancel":
                    case "personal_transfer_request": {
                        handleDepartmentPhase5Action(t, o, sender);
                        break;
                    }
                    case "dept_task_create":
                    case "dept_task_assign":
                    case "dept_task_progress":
                    case "dept_task_claim":
                    case "dept_task_close":
                    case "dispatch_create":
                    case "dispatch_approve":
                    case "dispatch_update_status":
                    case "vital_alert_ack":
                    case "vital_alert_dispatch":
                    case "vital_alert_resolve":
                    case "reinforcement_request":
                    case "reinforcement_decide":
                    case "reinforcement_dispatch":
                    case "reinforcement_close": {
                        handleDepartmentPhase4Action(t, o, sender);
                        break;
                    }
                    case "terminal_request": {
                        // 仅请求当前数据, 不再单独处理
                        break;
                    }
                    default:
                        break;
                }
            }
            // 处理完回推最新模板给该管理员(即时刷新)
            syncTerminalData(sender);
        } catch (Throwable ignored) {
        }
    }

    private static void handleAnomalyMutation(String action, com.google.gson.JsonObject o, ServerPlayer sender) {
        String instanceId = stringField(o, "instanceId");
        if (!requireAnomalyPermission(sender, action, instanceId)) return;
        com.example.melonstools.anomaly.AnomalySavedData data = com.example.melonstools.anomaly.AnomalySavedData.get(sender.serverLevel());
        com.example.melonstools.anomaly.AnomalySavedData.AnomalyInstance inst = data.getInstance(instanceId);
        String before = com.example.melonstools.anomaly.AnomalySavedData.auditSummary(inst);
        String note = limitActionNote(stringField(o, "note"));
        boolean ok = false;
        String failure = "invalid request";
        try {
            if ("anomaly_params_update".equals(action)) {
                Long revision = longField(o, "expectedRevision");
                Integer stability = intField(o, "stability"), max = intField(o, "maxStability"), research = intField(o, "researchProgress");
                Integer interval = intField(o, "maintenanceIntervalSeconds"), loss = intField(o, "lossPerMiss"), emergency = intField(o, "emergencySeconds");
                String state = stringField(o, "state");
                if (inst == null) failure = "unknown instance";
                else if (revision == null || stability == null || max == null || research == null || interval == null || loss == null || emergency == null || state.isBlank()) failure = "missing or non-integer field";
                else if (revision.longValue() != inst.revision) failure = "revision conflict";
                else if (!com.example.melonstools.anomaly.AnomalySavedData.isAllowedState(state.trim().toLowerCase(java.util.Locale.ROOT))) failure = "unknown state";
                else {
                    ok = data.updateInstanceParameters(instanceId, new com.example.melonstools.anomaly.AnomalySavedData.InstanceParameterUpdate(revision, state, stability, max, research, interval, loss, emergency, note), sender);
                    if (!ok) failure = "parameter out of range";
                }
            } else if ("anomaly_mark_maintained".equals(action)) {
                Integer restore = intField(o, "restoreAmount");
                if (restore == null) failure = "missing restoreAmount";
                else { ok = data.markMaintained(instanceId, restore, note, sender); if (!ok) failure = "unknown instance or restoreAmount out of range"; }
            } else if ("anomaly_emergency_set".equals(action)) {
                String mode = stringField(o, "mode");
                ok = data.setEmergency(instanceId, mode, note, sender);
                if (!ok) failure = "invalid emergency transition";
            } else if ("anomaly_instance_move".equals(action)) {
                String targetSlotId = stringField(o, "targetSlotId");
                ok = data.moveInstance(instanceId, targetSlotId, note, sender);
                if (!ok) failure = "target slot missing, locked, occupied or unchanged";
            }
        } catch (RuntimeException ex) {
            failure = "malformed payload";
        }
        String after = com.example.melonstools.anomaly.AnomalySavedData.auditSummary(data.getInstance(instanceId));
        auditAnomaly(sender, action, instanceId, ok ? "success" : "failed", ok ? note : failure, before, after);
        sendAnomalyResult(sender, ok, ok ? "异常物操作完成" : "异常物操作失败：" + failure);
        if (ok) syncAnomalyClients(sender); else syncTerminalData(sender);
    }

    private static boolean requireAnomalyPermission(ServerPlayer sender, String action, String target) {
        if (canManageAnomalies(sender)) return true;
        auditAnomaly(sender, action, target, "denied", "anomaly management permission", "", "");
        sendAnomalyResult(sender, false, "无异常物管理权限");
        return false;
    }

    private static void auditPayroll(ServerPlayer sender, String action, String target, String result, String reason) { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, action, target, result, reason, "", ""); }
    private static void auditAnomaly(ServerPlayer sender, String action, String target, String result, String reason, String before, String after) {
        com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, action, target, result, reason, before, after);
    }

    private static void sendAnomalyResult(ServerPlayer sender, boolean success, String text) {
        sender.sendSystemMessage(Component.literal((success ? "§a" : "§c") + text));
    }

    private static void syncAnomalyClients(ServerPlayer actor) {
        if (actor == null || actor.server == null) return;
        for (ServerPlayer player : actor.server.getPlayerList().getPlayers()) {
            syncTerminalData(player);
            syncPhoneFull(player);
        }
    }

    private static String stringField(com.google.gson.JsonObject o, String key) {
        try { return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : ""; }
        catch (RuntimeException ex) { return ""; }
    }

    private static Integer intField(com.google.gson.JsonObject o, String key) {
        try {
            if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
            String raw = o.get(key).getAsString().trim();
            if (!raw.matches("-?\\d+")) return null;
            return Integer.valueOf(raw);
        } catch (RuntimeException ex) { return null; }
    }

    private static Long longField(com.google.gson.JsonObject o, String key) {
        try {
            if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
            String raw = o.get(key).getAsString().trim();
            if (!raw.matches("-?\\d+")) return null;
            return Long.valueOf(raw);
        } catch (RuntimeException ex) { return null; }
    }

    private static String limitActionNote(String note) {
        String value = note == null ? "" : note.trim();
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private static void handleDepartmentPhase5Action(String action, com.google.gson.JsonObject o, ServerPlayer sender) {
        String id = o.has("id") ? o.get("id").getAsString() : "";
        String note = o.has("note") ? o.get("note").getAsString() : (o.has("reason") ? o.get("reason").getAsString() : "");
        switch (action) {
            case "procurement_create" -> com.example.melonstools.data.ProcurementSavedData.get(sender.serverLevel()).create(sender, o);
             case "procurement_submit" -> { com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, action, id, "denied", "采购提交动作已废止：创建即登记，审批由服务端状态机处理", "", ""); }
            case "procurement_approve" -> com.example.melonstools.data.ProcurementSavedData.get(sender.serverLevel()).approve(id, sender, note);
            case "procurement_order" -> com.example.melonstools.data.ProcurementSavedData.get(sender.serverLevel()).order(id, sender, note);
            case "procurement_receive" -> com.example.melonstools.data.ProcurementSavedData.get(sender.serverLevel()).receive(id, sender, note);
            case "procurement_complete" -> com.example.melonstools.data.ProcurementSavedData.get(sender.serverLevel()).complete(id, sender, note);
            case "procurement_reject" -> com.example.melonstools.data.ProcurementSavedData.get(sender.serverLevel()).reject(id, sender, note);
            case "procurement_cancel" -> com.example.melonstools.data.ProcurementSavedData.get(sender.serverLevel()).cancel(id, sender, note);
            case "facility_payment_create" -> com.example.melonstools.data.FacilityPaymentSavedData.get(sender.serverLevel()).create(sender, o);
            case "facility_payment_approve" -> com.example.melonstools.data.FacilityPaymentSavedData.get(sender.serverLevel()).approve(id, sender, note);
            case "facility_payment_pay" -> com.example.melonstools.data.FacilityPaymentSavedData.get(sender.serverLevel()).pay(id, sender, note);
            case "facility_payment_cancel" -> com.example.melonstools.data.FacilityPaymentSavedData.get(sender.serverLevel()).cancel(id, sender, note);
            case "personal_transfer_request" -> {
                String receiverName = o.has("receiverName") ? o.get("receiverName").getAsString() : "";
                long amount = o.has("amount") ? o.get("amount").getAsLong() : 0L;
                com.example.melonstools.data.PersonalTransferService.TransferResult r = com.example.melonstools.data.PersonalTransferService.execute(sender, receiverName, amount, note);
                com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, "personal_transfer_request", receiverName, r.success() ? "ok" : "denied", r.code(), "", r.detail());
                sender.sendSystemMessage(Component.literal(r.success()
                        ? "§a个人转账成功：§f" + amount + " §7→ §f" + receiverName
                        : "§c个人转账失败：§f" + r.code() + (r.detail().isBlank() ? "" : " §7(" + r.detail() + ")")));
            }
            default -> com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, action, "phase5", "denied", "unknown_phase5_action", "", "");
        }
    }

    private static void handleDepartmentPhase4Action(String action, com.google.gson.JsonObject o, ServerPlayer sender) {
        String id = o.has("id") ? o.get("id").getAsString() : "";
        String status = o.has("status") ? o.get("status").getAsString() : "";
        String note = o.has("note") ? o.get("note").getAsString() : (o.has("reason") ? o.get("reason").getAsString() : "");
        switch (action) {
            case "dept_task_create" -> com.example.melonstools.data.DepartmentTaskSavedData.get(sender.serverLevel()).create(sender, o);
            case "dept_task_assign", "dept_task_claim" -> com.example.melonstools.data.DepartmentTaskSavedData.get(sender.serverLevel()).claim(sender, id);
            case "dept_task_progress" -> {
                if ("start".equalsIgnoreCase(status)) com.example.melonstools.data.DepartmentTaskSavedData.get(sender.serverLevel()).start(sender, id);
                else com.example.melonstools.data.DepartmentTaskSavedData.get(sender.serverLevel()).submit(sender, id, o.has("progress") ? o.get("progress").getAsInt() : 1, note);
            }
            case "dept_task_close" -> com.example.melonstools.data.DepartmentTaskSavedData.get(sender.serverLevel()).close(sender, id, status.isBlank() ? com.example.melonstools.data.DepartmentTaskSavedData.COMPLETED : status, note);
            case "dispatch_create" -> com.example.melonstools.data.DispatchSavedData.get(sender.serverLevel()).create(sender, o);
            case "dispatch_approve" -> com.example.melonstools.data.DispatchSavedData.get(sender.serverLevel()).ack(sender, id, false);
            case "dispatch_update_status" -> {
                if ("cancelled".equalsIgnoreCase(status) || "cancel".equalsIgnoreCase(status)) com.example.melonstools.data.DispatchSavedData.get(sender.serverLevel()).cancel(sender, id, note);
                else com.example.melonstools.data.DispatchSavedData.get(sender.serverLevel()).ack(sender, id, "completed".equalsIgnoreCase(status) || "complete".equalsIgnoreCase(status));
            }
            case "vital_alert_ack" -> com.example.melonstools.data.VitalAlertSavedData.get(sender.serverLevel()).ack(sender, id);
            case "vital_alert_dispatch" -> com.example.melonstools.data.VitalAlertSavedData.get(sender.serverLevel()).dispatch(sender, id, note);
            case "vital_alert_resolve" -> com.example.melonstools.data.VitalAlertSavedData.get(sender.serverLevel()).resolve(sender, id, note);
            case "reinforcement_request" -> com.example.melonstools.data.ReinforcementSavedData.get(sender.serverLevel()).create(sender, o);
            case "reinforcement_decide", "reinforcement_dispatch" -> com.example.melonstools.data.ReinforcementSavedData.get(sender.serverLevel()).claim(sender, id);
            case "reinforcement_close" -> {
                if ("arrived".equalsIgnoreCase(status)) com.example.melonstools.data.ReinforcementSavedData.get(sender.serverLevel()).arrived(sender, id);
                else com.example.melonstools.data.ReinforcementSavedData.get(sender.serverLevel()).resolve(sender, id, note);
            }
            default -> com.example.melonstools.data.OperationAuditSavedData.get(sender.serverLevel()).record(sender, action, "phase4", "denied", "unknown_phase4_action", "", "");
        }
    }

    /** 下发设施终端全量数据(JSON):任务模板 + 全局设施资金。 */
    public static void syncTerminalData(ServerPlayer player) {
        if (player == null) return;
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                com.example.melonstools.data.TaskConfigManager.get(player.serverLevel()).toJson()
        ).getAsJsonObject();
        boolean isOp = player.hasPermissions(2);
        boolean canManageAnomalies = canManageAnomalies(player);
        boolean canViewResearchDetails = canViewResearchDetails(player);
        boolean canSubmitReports = canSubmitResearchReports(player);
        boolean canReviewReports = canReviewResearchReports(player);
        boolean canPublishNotices = canPublishNotice(player);
        boolean canManageNotices = canManageNotice(player);
        com.example.melonstools.department.DepartmentPolicy.Identity terminalIdentity = com.example.melonstools.department.DepartmentPolicy.identity(player);
        root.addProperty("isOp", isOp);
        root.addProperty("canEditHQTasks", isOp);
        root.addProperty("canManageAnomalies", canManageAnomalies);
        root.addProperty("canViewResearchDetails", canViewResearchDetails);
        root.addProperty("canSubmitReports", canSubmitReports);
        root.addProperty("canReviewReports", canReviewReports);
        root.addProperty("canPublishNotices", canPublishNotices);
        root.addProperty("canManageNotices", canManageNotices);
        root.addProperty("canPublishOrdinaryNotice", canPublishNotice(player, com.example.melonstools.department.DepartmentPolicy.NOTICE_ORDINARY));
        root.addProperty("canPublishDepartmentNotice", canPublishNotice(player, com.example.melonstools.department.DepartmentPolicy.NOTICE_DEPARTMENT));
        root.addProperty("canPublishFacilityNotice", canPublishNotice(player, com.example.melonstools.department.DepartmentPolicy.NOTICE_FACILITY));
        root.addProperty("canPublishEmergencyNotice", canPublishNotice(player, com.example.melonstools.department.DepartmentPolicy.NOTICE_EMERGENCY));
        root.addProperty("isManagementClerk", com.example.melonstools.department.DepartmentPolicy.isClerk(terminalIdentity.department(), terminalIdentity.position(), terminalIdentity.level()));
        root.addProperty("canExpandFacility", canExpandFacility(player));
        root.add("facilityFund", com.example.melonstools.data.FacilityFundManager.get(player.serverLevel()).toJson());
        com.example.melonstools.data.HqTaskSavedData hqTasks = com.example.melonstools.data.HqTaskSavedData.get(player.serverLevel());
        root.add("hqTasks", hqTasks.toJson(isOp));
        root.add("hqTaskEditor", hqTasks.editorStateJson(isOp));
        JsonObject staffOnline = com.example.melonstools.data.OnlineStaffCache.toJson();
        com.example.melonstools.anomaly.AnomalySavedData anomalyData = com.example.melonstools.anomaly.AnomalySavedData.get(player.serverLevel());
        root.add("staffOnline", staffOnline);
        root.add("anomalies", anomalyData.toJson(isOp || canManageAnomalies));
        root.add("researchDepartment", anomalyData.buildResearchDepartmentJson(player, staffOnline));
        root.add("notices", com.example.melonstools.data.FacilityNoticeSavedData.get(player.serverLevel()).toTerminalJson(canPublishNotices, canManageNotices, canDeleteNotice(player)));
        root.add("departmentApplications", com.example.melonstools.data.DepartmentApplicationSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("researchDocuments", com.example.melonstools.data.ResearchDocumentSavedData.get(player.serverLevel()).toJsonFor(player, true));
        root.add("pendingCardQueue", com.example.melonstools.data.PendingCardQueueSavedData.get(player.serverLevel()).toJson());
        root.add("operationAudit", com.example.melonstools.data.OperationAuditSavedData.get(player.serverLevel()).toJson());
        root.add("managementOffice", com.example.melonstools.data.ManagementOfficeSavedData.get(player.serverLevel()).toJsonFor(player, true));
        root.add("departmentTasks", com.example.melonstools.data.DepartmentTaskSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("dispatches", com.example.melonstools.data.DispatchSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("vitalAlerts", com.example.melonstools.data.VitalAlertSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("reinforcements", com.example.melonstools.data.ReinforcementSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("procurements", com.example.melonstools.data.ProcurementSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("facilityPayments", com.example.melonstools.data.FacilityPaymentSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("facilityPayroll", com.example.melonstools.data.PayrollSavedData.get(player.serverLevel()).toJsonFor());
        NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncTerminalDataPacket(root.toString()));
    }

    /** 提交小游戏成绩 */
    public static void handleSubmitScore(SubmitScorePacket packet, ServerPlayer sender) {
        if (sender == null) return;
        LeaderboardManager.get(sender.serverLevel()).submit(packet.getGame(), packet.getDiff(), sender.getUUID(), sender.getScoreboardName(), packet.getScore());
    }

    /** 请求全服排行榜 */
    public static void handleRequestLeaderboard(ServerPlayer sender) {
        if (sender == null) return;
        String json = LeaderboardManager.get(sender.serverLevel()).toJson(sender.getUUID());
        NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new SyncLeaderboardPacket(json));
    }

    private static void syncAllPhones(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncPhoneFull(p);
    }

    /** 下发手机全量数据(JSON)。默认不扫描附近(仅显式请求时才计算) */
    public static void syncPhoneFull(ServerPlayer player) {
        syncPhoneFull(player, false);
    }

    public static void syncPhoneFull(ServerPlayer player, boolean includeNearby) {
        NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncPhoneFullPacket(buildPhoneJson(player, includeNearby)));
    }

    private static String resolveName(net.minecraft.server.MinecraftServer server, UUID uuid) {
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) {
            // 在线玩家显示ID卡名称
            String cardName = PhoneUtils.getPlayerName(p);
            if (cardName != null && !cardName.isEmpty()) return cardName;
            return p.getScoreboardName();
        }
        var profile = server.getProfileCache().get(uuid);
        if (profile.isPresent()) return profile.get().getName();
        return "?";
    }

    private static String formatClock(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /** 聚合玩家档案/好友/请求/附近/聊天室为 JSON */
    public static String buildPhoneJson(ServerPlayer player) {
        return buildPhoneJson(player, false);
    }

    public static String buildPhoneJson(ServerPlayer player, boolean includeNearby) {
        JsonObject root = new JsonObject();

        // 玩家档案
        JsonObject profile = new JsonObject();
        profile.addProperty("id", player.getScoreboardName());
        String cardName = PhoneUtils.getPlayerName(player);
        profile.addProperty("name", cardName != null ? cardName : player.getScoreboardName());
        String rawDept = PhoneUtils.getPlayerDepartment(player);
        String rawPosition = PhoneUtils.getPlayerPosition(player);
        int rawLevel = PhoneUtils.getPlayerIdCardLevel(player);
        com.example.melonstools.department.DepartmentPolicy.Identity phoneIdentity = com.example.melonstools.department.DepartmentPolicy.identity(rawDept, rawPosition, rawLevel);
        // 玩家身份始终来自 ID 卡；旧 D级/临聘仅读取规范化为后勤0级临时人员，不写回卡。
        String dept = phoneIdentity.department();
        profile.addProperty("dept", dept);
        profile.addProperty("position", phoneIdentity.position());
        profile.addProperty("level", phoneIdentity.level());
        String balance = "0";
        try {
            if (ModList.get().isLoaded("lightmanscurrency")) {
                // 优先读取玩家实际携带的 LC 银行卡(BankAccount + AccountValidation),
                // 再回退 ID 卡绑定账户,最后回退 LC 钱包余额。
                balance = com.example.melonstools.idcard.LCIntegration.getBestBalanceDisplay(
                        player, com.example.melonstools.utils.PhoneUtils.findIdentityCard(player));
            }
        } catch (Throwable t) {
            balance = "0";
        }
        profile.addProperty("balance", balance);
        root.add("player", profile);

        // 好友与待处理请求
        JsonArray friends = new JsonArray();
        JsonArray pending = new JsonArray();
        PlayerPhoneProvider.getPhone(player).ifPresent(phone -> {
            for (UUID u : phone.getFriends()) {
                // 防御: 过滤历史遗留的"自己"脏数据(早期版本可能把自己写进好友)
                if (u == null || u.equals(player.getUUID())) continue;
                JsonObject o = new JsonObject();
                o.addProperty("uuid", u.toString());
                o.addProperty("name", resolveName(player.getServer(), u));
                o.addProperty("online", player.getServer().getPlayerList().getPlayer(u) != null);
                friends.add(o);
            }
            for (UUID u : phone.getPendingRequests()) {
                if (u == null || u.equals(player.getUUID())) continue;
                JsonObject o = new JsonObject();
                o.addProperty("uuid", u.toString());
                o.addProperty("name", resolveName(player.getServer(), u));
                pending.add(o);
            }
        });
        root.add("friends", friends);
        root.add("pending", pending);

        // 附近玩家:仅在显式请求(点击刷新)时计算并下发,其他全量同步不扫描
        if (includeNearby) {
            root.add("nearby", buildNearbyArray(player));
        }

        // 聊天室
        JsonArray rooms = new JsonArray();
        ChatRoomManager manager = ChatRoomManager.get(player.serverLevel());
        if (manager != null) {
            for (ChatRoomManager.ChatRoom room : manager.getRoomsOfPlayer(player.getUUID())) {
                JsonObject ro = new JsonObject();
                ro.addProperty("id", room.roomId);
                ro.addProperty("name", room.roomName);
                ro.addProperty("ownerName", room.owner != null ? resolveName(player.getServer(), room.owner) : "?");
                ro.addProperty("isOwner", room.owner != null && room.owner.equals(player.getUUID()));
                JsonArray members = new JsonArray();
                for (UUID m : room.members) {
                    JsonObject mo = new JsonObject();
                    mo.addProperty("uuid", m.toString());
                    mo.addProperty("name", resolveName(player.getServer(), m));
                    members.add(mo);
                }
                ro.add("members", members);
                ro.addProperty("unread", 0);
                JsonArray msgs = new JsonArray();
                for (ChatRoomManager.ChatMessage msg : room.messages) {
                    JsonObject mjo = new JsonObject();
                    boolean mine = msg.sender != null && msg.sender.equals(player.getUUID());
                    mjo.addProperty("sender", mine ? "mine" : "other");
                    mjo.addProperty("name", msg.senderName);
                    mjo.addProperty("time", formatClock(msg.timestamp));
                    mjo.addProperty("content", msg.content);
                    // 已读时间戳之前的 @ 不再标记(主页重要消息按此去重)
                    Long readTs = room.lastRead.get(player.getUUID());
                    boolean readMsg = readTs != null && msg.timestamp <= readTs;
                    mjo.addProperty("mention", !readMsg && msg.content.contains("@" + player.getScoreboardName()));
                    msgs.add(mjo);
                }
                ro.add("messages", msgs);
                rooms.add(ro);
            }
        }
        root.add("rooms", rooms);

        // 每日任务
        JsonArray tasks = new JsonArray();
        try {
            for (DailyTaskManager.PlayerTask t : DailyTaskManager.get(player.serverLevel()).getTasksFor(player)) {
                JsonObject to = new JsonObject();
                to.addProperty("id", t.id);
                to.addProperty("name", t.name);
                to.addProperty("desc", t.desc);
                to.addProperty("cur", t.cur);
                to.addProperty("goal", t.goal);
                to.addProperty("state", t.state);
                to.addProperty("reward", t.reward);
                tasks.add(to);
            }
        } catch (Throwable ignored) {
        }
        root.add("tasks", tasks);

        // 个人终端公告：服务端按玩家 ID 卡部门过滤；前端只消费 visibleNotices/notices 渲染。
        JsonArray visibleNotices = com.example.melonstools.data.FacilityNoticeSavedData.get(player.serverLevel()).toVisibleJsonForPlayer(player);
        root.add("visibleNotices", visibleNotices);
        root.add("notices", visibleNotices);

        // 科研部门个人终端异常物数据；完整文本仍由服务端按研究进度筛选。
        boolean scienceDept = "科研部门".equals(dept);
        com.example.melonstools.anomaly.AnomalySavedData anomalyData = com.example.melonstools.anomaly.AnomalySavedData.get(player.serverLevel());
        root.add("anomalies", scienceDept || player.hasPermissions(2) ? anomalyData.toJson(false) : new JsonObject());
        root.add("myResearch", anomalyData.buildMyResearchJson(player));
        root.add("departmentApplications", com.example.melonstools.data.DepartmentApplicationSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("researchDocuments", com.example.melonstools.data.ResearchDocumentSavedData.get(player.serverLevel()).toJsonFor(player, false));
        root.add("pendingCardQueue", com.example.melonstools.data.PendingCardQueueSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("managementOffice", com.example.melonstools.data.ManagementOfficeSavedData.get(player.serverLevel()).toJsonFor(player, false));
        root.add("departmentTasks", com.example.melonstools.data.DepartmentTaskSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("dispatches", com.example.melonstools.data.DispatchSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("vitalAlerts", com.example.melonstools.data.VitalAlertSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("reinforcements", com.example.melonstools.data.ReinforcementSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("procurements", com.example.melonstools.data.ProcurementSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("facilityPayments", com.example.melonstools.data.FacilityPaymentSavedData.get(player.serverLevel()).toJsonFor(player));
        root.add("facilityPayroll", com.example.melonstools.data.PayrollSavedData.get(player.serverLevel()).toJsonFor());
        root.addProperty("anomalyAccess", scienceDept);
        root.addProperty("canViewResearchDetails", canViewResearchDetails(player));
        root.addProperty("canSubmitReports", canSubmitResearchReports(player));
        // 审核只能在设施终端进行；个人终端即使是五级卡也不下发审核权限。
        root.addProperty("canReviewReports", false);
        net.minecraft.world.item.ItemStack suppressor = com.example.melonstools.compat.CuriosCompat.findAnomalySuppressor(player);
        JsonObject suppressorJson = new JsonObject();
        suppressorJson.addProperty("equipped", !suppressor.isEmpty());
        if (!suppressor.isEmpty() && suppressor.hasTag()) {
            var st = suppressor.getTag();
            suppressorJson.addProperty("anomalyId", st.getString(com.example.melonstools.item.AnomalyMagneticFieldSuppressorItem.TAG_ANOMALY_ID));
            suppressorJson.addProperty("anomalyInstanceId", st.getString(com.example.melonstools.item.AnomalyMagneticFieldSuppressorItem.TAG_ANOMALY_INSTANCE_ID));
            suppressorJson.addProperty("roomId", st.getString(com.example.melonstools.item.AnomalyMagneticFieldSuppressorItem.TAG_ROOM_ID));
        }
        root.add("anomalySuppressor", suppressorJson);

        return root.toString();
    }

    public static void syncPhoneData(ServerPlayer player) {
        PlayerPhoneProvider.getPhone(player).ifPresent(phone -> {
            CompoundTag tag = new CompoundTag();
            phone.saveNBTData(tag);
            NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncPhoneDataPacket(tag));
        });
    }

    public static void syncChatData(ChatRoomManager manager, ServerPlayer player, ChatRoomManager.ChatRoom room) {
        NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncChatDataPacket(room.save()));
    }
}
