package com.lrceditor.mobile.model;

import java.util.Locale;

public final class LrcFileNames {

    public static final String MIME_TYPE = "text/x-lrc";

    private LrcFileNames() {
    }

    public static String withLrcExtension(String name) {
        String candidate = cleanName(name);
        if (candidate.isEmpty()) candidate = "lyrics";

        boolean stripped;
        do {
            stripped = false;
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".lrc")) {
                candidate = candidate.substring(0, candidate.length() - 4).trim();
                stripped = true;
            } else if (lower.endsWith(".txt")) {
                candidate = candidate.substring(0, candidate.length() - 4).trim();
                stripped = true;
            }
        } while (stripped);

        if (candidate.isEmpty()) candidate = "lyrics";
        return candidate + ".lrc";
    }

    public static boolean hasLrcExtension(String name) {
        return cleanName(name).toLowerCase(Locale.ROOT).endsWith(".lrc");
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        String cleaned = name.trim().replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) cleaned = cleaned.substring(slash + 1).trim();
        return cleaned;
    }
}
