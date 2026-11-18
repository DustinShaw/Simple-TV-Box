package win.movie8.tv;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the local title pool used by TV-style first-letter search: watch
 * history, past search terms and cached home-list titles. No network needed,
 * so suggestions appear instantly as the user types letters on the on-screen
 * remote keyboard. The remote API only accepts Chinese keywords, so pinyin
 * initials can only be resolved locally.
 */
final class LocalTitleIndex {
    private static final String[] SECTIONS = {"recent", "movie", "series", "variety", "anime"};
    private static final int MAX_POOL = 400;
    private static final int MAX_RESULTS = 12;

    private LocalTitleIndex() { }

    static List<String> titles(Context context) {
        Set<String> unique = new LinkedHashSet<>();
        // Search terms first: they are the most intentional titles.
        for (String term : SearchHistory.items(context)) add(unique, term);
        // Watch-history movie names next.
        for (org.json.JSONObject item : HistoryStore.items(context)) add(unique, item.optString("name", ""));
        // Cached home lists last (largest source).
        for (String section : SECTIONS)
            for (Movie movie : ListPreviewCache.read(section)) add(unique, movie.name);
        return new ArrayList<>(unique);
    }

    /** Returns up to 12 unique titles whose pinyin initials or literal prefix start with the query. */
    static List<String> suggest(List<String> pool, String query) {
        if (query == null || query.isEmpty()) return Collections.emptyList();
        String key = query.toLowerCase(java.util.Locale.ROOT);
        List<String> exact = new ArrayList<>(), prefix = new ArrayList<>();
        for (String title : pool) {
            if (title == null || title.isEmpty()) continue;
            String initials = PinyinInitials.of(title).toLowerCase(java.util.Locale.ROOT);
            String literal = title.toLowerCase(java.util.Locale.ROOT);
            if (initials.equals(key) || literal.equals(key)) { if (!exact.contains(title)) exact.add(title); }
            else if (initials.startsWith(key) || literal.startsWith(key)) { if (!prefix.contains(title)) prefix.add(title); }
        }
        List<String> result = new ArrayList<>(exact);
        for (String title : prefix) { if (result.size() >= MAX_RESULTS) break; result.add(title); }
        return result;
    }

    private static void add(Set<String> set, String value) {
        if (value != null && !value.trim().isEmpty() && set.size() < MAX_POOL) set.add(value.trim());
    }
}
