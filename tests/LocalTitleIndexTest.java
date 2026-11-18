package win.movie8.tv;

import java.util.Arrays;
import java.util.List;

public final class LocalTitleIndexTest {
    public static void main(String[] args) {
        List<String> pool = Arrays.asList("肖申克的救赎", "星际穿越", "阿甘正传", "The Star", "你好世界", "", null, "肖申克的救赎");
        List<String> xs = LocalTitleIndex.suggest(pool, "xs");
        if (!xs.equals(Arrays.asList("肖申克的救赎"))) throw new AssertionError("xs: " + xs);
        List<String> x = LocalTitleIndex.suggest(pool, "x");
        if (!x.contains("肖申克的救赎") || !x.contains("星际穿越")) throw new AssertionError("x: " + x);
        List<String> ag = LocalTitleIndex.suggest(pool, "ag");
        if (!ag.contains("阿甘正传")) throw new AssertionError("ag: " + ag);
        if (!LocalTitleIndex.suggest(pool, "the").contains("The Star")) throw new AssertionError("literal: " + ag);
        if (!LocalTitleIndex.suggest(pool, "").isEmpty()) throw new AssertionError("blank query");
        if (!LocalTitleIndex.suggest(pool, "zz").isEmpty()) throw new AssertionError("no match");
        System.out.println("LocalTitleIndexTest passed");
    }
}
