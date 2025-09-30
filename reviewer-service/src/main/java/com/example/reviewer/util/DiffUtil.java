package com.example.reviewer.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.UnifiedDiffUtils;

public final class DiffUtil {

    private DiffUtil() {}

    public static String unifiedDiff(byte[] baseContent, byte[] targetContent, String filePath) {
        String base = baseContent == null ? "" : new String(baseContent, StandardCharsets.UTF_8);
        String target = targetContent == null ? "" : new String(targetContent, StandardCharsets.UTF_8);
        List<String> a = Arrays.asList(base.split("\r?\n", -1));
        List<String> b = Arrays.asList(target.split("\r?\n", -1));
        String normalized = filePath == null ? "file" : (filePath.startsWith("/") ? filePath.substring(1) : filePath);

        Patch<String> patch = DiffUtils.diff(a, b);
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
                "a/" + normalized,
                "b/" + normalized,
                a,
                patch,
                3
        );
        if (unified.isEmpty()) {
            return "--- a/" + normalized + System.lineSeparator() +
                   "+++ b/" + normalized + System.lineSeparator();
        }
        return String.join(System.lineSeparator(), unified) + System.lineSeparator();
    }
}
