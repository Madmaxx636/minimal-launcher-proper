package app.inkwell.launcher;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Pattern;

/**
 * Web search without the launcher going online: it only builds an address and hands it to your
 * browser. The launcher itself has no internet permission.
 */
final class WebSearch {
    static final String[] ENGINE_IDS = {"duckduckgo", "google", "bing", "startpage", "brave", "ecosia", "wikipedia", "custom"};
    static final String[] ENGINE_NAMES = {"DuckDuckGo", "Google", "Bing", "Startpage", "Brave Search", "Ecosia", "Wikipedia", "My own address"};
    private static final String[] TEMPLATES = {
            "https://duckduckgo.com/?q=%s", "https://www.google.com/search?q=%s", "https://www.bing.com/search?q=%s",
            "https://www.startpage.com/do/search?q=%s", "https://search.brave.com/search?q=%s",
            "https://www.ecosia.org/search?q=%s", "https://en.wikipedia.org/w/index.php?search=%s", ""};
    private static final Pattern LINK = Pattern.compile("(?i)^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(/\\S*)?$");

    private WebSearch() {}

    static String urlFor(String query) {
        String id = Prefs.getStr(Prefs.WEB_ENGINE, "duckduckgo");
        String template = "https://duckduckgo.com/?q=%s";
        for (int i = 0; i < ENGINE_IDS.length; i++) if (ENGINE_IDS[i].equals(id)) template = TEMPLATES[i];
        if (id.equals("custom")) {
            String mine = Prefs.getStr(Prefs.WEB_URL, "");
            template = mine.contains("%s") ? mine : "https://duckduckgo.com/?q=%s";
        }
        String enc;
        try { enc = URLEncoder.encode(query, "UTF-8"); } catch (UnsupportedEncodingException e) { enc = query; }
        return template.replace("%s", enc);
    }

    /** The typed text as an address if it looks like one ("example.com/page"), else null. */
    static String asLink(String text) {
        String t = text.trim();
        if (!LINK.matcher(t).matches()) return null;
        return t.toLowerCase().startsWith("http") ? t : "https://" + t;
    }
}
