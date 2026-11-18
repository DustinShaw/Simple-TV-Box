package win.movie8.tv;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

/**
 * Converts a Chinese string to its pinyin initials using the GB2312 code-point
 * boundaries. The first 23 GB2312 level-1 groups are ordered by pinyin, so a
 * character's 2-byte code maps directly to its initial. No third-party library
 * is needed (important: this app targets API 23, where android.icu
 * Transliterator is unavailable). Non-Chinese characters are kept as-is.
 */
final class PinyinInitials {
    // First code of each pinyin initial in GB2312 level-1 (a..z without i,u,v).
    private static final int[] BOUNDARIES = {
            0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE, 0xBBF7, 0xBFA6, 0xC0AC, 0xC2E8,
            0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA, 0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1
    };
    private static final char[] LETTERS = "abcdefghjklmnopqrstwxyz".toCharArray();

    private PinyinInitials() { }

    /** Returns the lowercase pinyin initials for Chinese chars; other chars are lowercased and kept. */
    static String of(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String initial = initialOf(c);
            if (initial != null) out.append(initial.charAt(0));
            else if (Character.isLetterOrDigit(c)) out.append(Character.toLowerCase(c));
            // Skip spaces and punctuation so "The Star" and "肖申克 的救赎" normalize cleanly.
        }
        return out.toString();
    }

    private static String initialOf(char c) {
        byte[] bytes;
        try { bytes = String.valueOf(c).getBytes("GB2312"); }
        catch (UnsupportedEncodingException e) { return null; }
        if (bytes.length != 2) return null; // not a GB2312 double-byte (Chinese) char
        int code = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        for (int i = BOUNDARIES.length - 1; i >= 0; i--) if (code >= BOUNDARIES[i]) return String.valueOf(LETTERS[i]);
        return null;
    }

    /** True when the query (letters) matches the start of the title's initial sequence. */
    static boolean matches(String title, String query) {
        return of(title).toLowerCase(Locale.ROOT).startsWith(query.toLowerCase(Locale.ROOT));
    }
}
