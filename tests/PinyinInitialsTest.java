package win.movie8.tv;

import java.util.Arrays;
import java.util.List;

public final class PinyinInitialsTest {
    public static void main(String[] args) {
        String[][] cases = {
                {"最近更新", "zjgx"}, {"电影", "dy"}, {"电视剧", "dsj"}, {"综艺", "zy"},
                {"动漫", "dm"}, {"搜索", "ss"}, {"播放记录", "bfjl"}, {"你好世界", "nhsj"},
                {"肖申克的救赎", "xskdjs"}, {"The Star", "thestar"}, {"bad", "bad"}
        };
        for (String[] pair : cases) {
            String got = PinyinInitials.of(pair[0]);
            if (!got.equals(pair[1])) throw new AssertionError(pair[0] + " -> " + got + " expected " + pair[1]);
        }
        if (!PinyinInitials.matches("肖申克的救赎", "xs")) throw new AssertionError("initials prefix");
        if (!PinyinInitials.matches("The Star", "TH")) throw new AssertionError("literal case-insensitive");
        if (PinyinInitials.matches("肖申克的救赎", "zz")) throw new AssertionError("non-match");
        System.out.println("PinyinInitialsTest passed");
    }
}
