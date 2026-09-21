package com.example.melonstools.anomaly;

/** 安全路径清洗骨架：只保留适合文件名/目录名的字符。 */
public final class PathSanitizer {
    private PathSanitizer() {
    }

    public static String safeSegment(String input) {
        if (input == null || input.isBlank()) return "unknown";
        StringBuilder out = new StringBuilder();
        int limit = Math.min(input.length(), 80);
        for (int i = 0; i < limit; i++) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ' ' || c >= 0x4e00) {
                out.append(c == ' ' ? '_' : c);
            } else {
                out.append('_');
            }
        }
        String s = out.toString().replaceAll("_+", "_");
        while (s.startsWith(".")) s = s.substring(1);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.equals("..") || s.contains("..")) s = s.replace("..", "_");
        return s.isBlank() ? "unknown" : s;
    }
}
