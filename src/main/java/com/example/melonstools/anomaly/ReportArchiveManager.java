package com.example.melonstools.anomaly;

import com.example.melonstools.MelonsTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 报告留档与导出骨架：负责安全路径、文件名清洗和基础写入。 */
public final class ReportArchiveManager {
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FILE_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ReportArchiveManager() {
    }

    public static Path reportRoot(ServerLevel level) {
        Path base = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        String worldSafe = PathSanitizer.safeSegment(level == null ? "world" : level.dimension().location().toString());
        return base.resolve("melonstools").resolve("reports").resolve(worldSafe).normalize();
    }

    public static Path exportToFile(ServerLevel level, AnomalySavedData.ResearchReport report, AnomalySavedData.AnomalyInstance instance) {
        Path root = reportRoot(level);
        String day = LocalDate.now().format(DAY_FMT);
        String stamp = LocalDateTime.now().format(FILE_TIME_FMT);
        String author = PathSanitizer.safeSegment(report == null ? "author" : report.authorName);
        String instanceId = PathSanitizer.safeSegment(report == null ? "instance" : report.anomalyInstanceId);
        String reportId = PathSanitizer.safeSegment(report == null ? "report" : report.reportId);
        Path outDir = root.resolve(day).normalize();
        Path out = outDir.resolve(stamp + "_" + author + "_" + instanceId + "_" + reportId + ".md").normalize();
        if (!out.startsWith(root) || !outDir.startsWith(root)) {
            return null;
        }
        try {
            Files.createDirectories(outDir);
            int collision = 1;
            while (Files.exists(out)) {
                out = outDir.resolve(stamp + "_" + author + "_" + instanceId + "_" + reportId + "_" + collision + ".md").normalize();
                if (!out.startsWith(root)) return null;
                collision++;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# 实验报告\n\n");
            if (report != null) {
                sb.append("- reportId: ").append(report.reportId).append('\n');
                sb.append("- anomalyInstanceId: ").append(report.anomalyInstanceId).append('\n');
                sb.append("- author: ").append(report.authorName).append('\n');
                sb.append("- status: ").append(report.status).append('\n');
                sb.append("- submittedAt: ").append(report.submittedAt).append('\n');
                sb.append("- reviewedAt: ").append(report.reviewedAt).append('\n');
                sb.append("- reviewer: ").append(report.reviewerName).append('\n');
                sb.append("- rejectReason: ").append(report.rejectReason).append('\n');
                sb.append("- archivePath: ").append(report.archivePath).append('\n');
            }
            if (instance != null) {
                sb.append("- researchProgress: ").append(instance.researchProgress).append('\n');
            }
            sb.append("\n## 正文\n\n");
            sb.append(report == null || report.content == null ? "" : report.content);
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
            return out;
        } catch (Exception e) {
            MelonsTools.LOGGER.warn("[Report] exportToFile failed: {}", e.getMessage());
            return null;
        }
    }
}
