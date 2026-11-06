package com.lrceditor.mobile.model;

import java.util.Locale;

public final class LrcFileNames {

    public static final String MIME_TYPE = "text/x-lrc";

    private LrcFileNames() {
    }

    public static String withLrcExtension(String name) {
        return withExtension(name, "lrc");
    }

    public static String withExtension(String name, String extension) {
        String candidate = cleanName(name);
        if (candidate.isEmpty()) candidate = "lyrics";

        boolean stripped;
        do {
            stripped = false;
            String lower = candidate.toLowerCase(Locale.ROOT);
            String[] known = {".lrc", ".txt", ".srt", ".ass"};
            for (String suffix : known) {
                if (lower.endsWith(suffix)) {
                    candidate = candidate.substring(
                            0, candidate.length() - suffix.length()).trim();
                    stripped = true;
                    break;
                }
            }
        } while (stripped);

        if (candidate.isEmpty()) candidate = "lyrics";
        return candidate + "." + extension;
    }

    public static boolean hasLrcExtension(String name) {
        return hasExtension(name, "lrc");
    }

    public static boolean hasExtension(String name, String extension) {
        return cleanName(name).toLowerCase(Locale.ROOT)
                .endsWith("." + extension.toLowerCase(Locale.ROOT));
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        String cleaned = name.trim().replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) cleaned = cleaned.substring(slash + 1).trim();
        return cleaned;
    }
}
