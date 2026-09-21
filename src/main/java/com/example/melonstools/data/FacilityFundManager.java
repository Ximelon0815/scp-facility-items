package com.example.melonstools.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.UUID;

/**
 * 设施资金全局账户。
 * <p>
 * 设计边界:设施资金不是实体银行卡,不挂 MONEY_HANDLER,不允许玩家绕过终端自由存取。
 * 它只是服务端 SavedData 中的全局数值,所有增减都必须通过设施终端动作进入。
 * 工资发放时从这里扣款,再直接打入玩家 ID 卡绑定的 LC 个人账户。
 */
public class FacilityFundManager extends SavedData {
    private static final String DATA_NAME = "melon_facility_fund";
    private static final int MAX_LOGS = 80;

    private long balance = 0L;
    private long reservedBudget = 0L;
    private final LinkedList<FundLog> logs = new LinkedList<>();
    public synchronized long getReservedBudget(){return reservedBudget;}
    public synchronized boolean reserveBudget(long amount,String operator,String source){if(amount<=0||balance-reservedBudget<amount)return false;reservedBudget=safeAdd(reservedBudget,amount);addLog("reserve", "采购预算", amount, operator, source);setDirty();return true;}
    public synchronized boolean releaseBudget(long amount,String operator,String source){if(amount<=0||reservedBudget<amount)return false;reservedBudget-=amount;addLog("release", "采购预算", -amount, operator, source);setDirty();return true;}

    public static class FundLog {
        public long time;
        public String type;
        public String target;
        public long amount;
        public String operator;
        public String note;

        public FundLog(long time, String type, String target, long amount, String operator, String note) {
            this.time = time;
            this.type = safe(type);
            this.target = safe(target);
            this.amount = amount;
            this.operator = safe(operator);
            this.note = safe(note);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Time", time);
            tag.putString("Type", type);
            tag.putString("Target", target);
            tag.putLong("Amount", amount);
            tag.putString("Operator", operator);
            tag.putString("Note", note);
            return tag;
        }

        public static FundLog load(CompoundTag tag) {
            return new FundLog(
                    tag.getLong("Time"),
                    tag.getString("Type"),
                    tag.getString("Target"),
                    tag.getLong("Amount"),
                    tag.getString("Operator"),
                    tag.getString("Note")
            );
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("time", new SimpleDateFormat("MM-dd HH:mm").format(new Date(time)));
            o.addProperty("type", type);
            o.addProperty("target", target);
            o.addProperty("amount", amount);
            o.addProperty("operator", operator);
            o.addProperty("note", note);
            return o;
        }
    }

    public long getBalance() {
        return balance;
    }

    public boolean deposit(long amount, String operator, String note) {
        if (amount <= 0) return false;
        balance = safeAdd(balance, amount);
        addLog("deposit", "设施资金", amount, operator, note);
        setDirty();
        return true;
    }

    public boolean withdraw(long amount, String operator, String note) {
        return withdrawWithSource(amount, operator, note, "");
    }

    /** Convert a previously reserved procurement budget into an actual ledger withdrawal. */
    public synchronized boolean withdrawReserved(long amount,String operator,String note,String sourceApplicationId){if(amount<=0||balance<amount||reservedBudget<amount)return false;balance-=amount;reservedBudget-=amount;String taggedNote=safe(note);if(sourceApplicationId!=null&&!sourceApplicationId.isBlank())taggedNote+=" sourceApplicationId="+sourceApplicationId;addLog("withdraw_reserved","设施资金",-amount,operator,taggedNote);setDirty();return true;}

    /** Atomic facility-fund withdrawal with an auditable business-source tag. */
    public boolean withdrawWithSource(long amount, String operator, String note, String sourceApplicationId) {
        if (amount <= 0 || balance - reservedBudget < amount) return false;
        balance -= amount;
        String taggedNote = safe(note);
        if (sourceApplicationId != null && !sourceApplicationId.isBlank()) taggedNote = taggedNote + " sourceApplicationId=" + sourceApplicationId;
        addLog("withdraw", "设施资金", -amount, operator, taggedNote);
        setDirty();
        return true;
    }

    /** 从设施资金扣款并向玩家 LC 个人账户发工资。 */
    public boolean paySalary(ServerLevel level, UUID targetUuid, String targetName, long amount, String operator, String note) {
        if (level == null || targetUuid == null || amount <= 0 || balance < amount) return false;
        if (!com.example.melonstools.idcard.LCIntegration.depositToPlayerBank(targetUuid, amount)) return false;
        balance -= amount;
        addLog("salary", targetName == null || targetName.isBlank() ? targetUuid.toString() : targetName, -amount, operator, note);
        setDirty();
        return true;
    }

    private void addLog(String type, String target, long amount, String operator, String note) {
        logs.addFirst(new FundLog(System.currentTimeMillis(), type, target, amount, operator, note));
        while (logs.size() > MAX_LOGS) logs.removeLast();
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("balance", balance);
        root.addProperty("balanceText", String.valueOf(balance));
        JsonArray arr = new JsonArray();
        for (FundLog log : logs) arr.add(log.toJson());
        root.add("logs", arr);
        return root;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Balance", balance);
        tag.putLong("ReservedBudget", reservedBudget);
        ListTag list = new ListTag();
        for (FundLog log : logs) list.add(log.save());
        tag.put("Logs", list);
        return tag;
    }

    public static FacilityFundManager load(CompoundTag tag) {
        FacilityFundManager manager = new FacilityFundManager();
        manager.balance = Math.max(0L, tag.getLong("Balance"));
        manager.reservedBudget = Math.max(0L, tag.getLong("ReservedBudget"));
        if (tag.contains("Logs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Logs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) manager.logs.add(FundLog.load(list.getCompound(i)));
        }
        return manager;
    }

    public static FacilityFundManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FacilityFundManager::load, FacilityFundManager::new, DATA_NAME);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }
}
