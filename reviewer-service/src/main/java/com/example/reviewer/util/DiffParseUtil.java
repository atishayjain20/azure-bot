package com.example.reviewer.util;

import java.util.ArrayList;
import java.util.List;

public final class DiffParseUtil {

    private DiffParseUtil() {}

    public static List<HunkLine> extractAddedLinesWithContext(String unifiedDiff, int contextRadius) {
        List<HunkLine> results = new ArrayList<>();
        if (unifiedDiff == null || unifiedDiff.isBlank()) return results;
        String[] lines = unifiedDiff.split("\r?\n");

        int currentRightLine = 0;
        List<String> hunkBuffer = new ArrayList<>();
        List<Integer> rightLineNumbers = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("@@")) {
                flushHunk(results, hunkBuffer, rightLineNumbers, contextRadius);
                hunkBuffer.clear();
                rightLineNumbers.clear();
                int plusIdx = line.indexOf(" +");
                int at2 = line.lastIndexOf("@@");
                if (plusIdx > 0 && at2 > plusIdx + 2) {
                    String right = line.substring(plusIdx + 2, at2).trim();
                    String startStr = right.split(",")[0];
                    try { currentRightLine = Integer.parseInt(startStr); } catch (NumberFormatException e) { currentRightLine = 0; }
                } else {
                    currentRightLine = 0;
                }
                continue;
            }
            if (line.startsWith("+") && !line.startsWith("+++ ")) {
                hunkBuffer.add(line);
                rightLineNumbers.add(currentRightLine);
                currentRightLine++;
            } else if (line.startsWith("-") && !line.startsWith("--- ")) {
                hunkBuffer.add(line);
            } else {
                hunkBuffer.add(line);
                currentRightLine++;
            }
        }
        flushHunk(results, hunkBuffer, rightLineNumbers, contextRadius);
        return results;
    }

    private static void flushHunk(List<HunkLine> out, List<String> hunkBuf, List<Integer> rightNums, int radius) {
        if (hunkBuf.isEmpty()) return;
        for (int idx = 0, seenAdds = 0; idx < hunkBuf.size(); idx++) {
            String line = hunkBuf.get(idx);
            if (line.startsWith("+") && !line.startsWith("+++ ")) {
                int rightLine = rightNums.get(seenAdds);
                seenAdds++;
                int start = Math.max(0, idx - radius);
                int end = Math.min(hunkBuf.size(), idx + radius + 1);
                StringBuilder ctx = new StringBuilder();
                for (int j = start; j < end; j++) {
                    String l = hunkBuf.get(j);
                    if (l.startsWith("--- ") || l.startsWith("+++ ") || l.startsWith("@@")) continue;
                    ctx.append(l).append('\n');
                }
                out.add(new HunkLine(rightLine, ctx.toString())) ;
            }
        }
    }

    public static final class HunkLine {
        public final int rightLine;
        public final String context;
        public HunkLine(int rightLine, String context) {
            this.rightLine = rightLine;
            this.context = context;
        }
    }
}
