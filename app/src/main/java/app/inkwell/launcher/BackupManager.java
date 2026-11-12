package app.inkwell.launcher;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The launcher's own backup (every setting, favorites, hidden apps, renames, theme) plus a tolerant
 * importer for files exported from other launchers such as Niagara.
 */
public final class BackupManager {
    public static final String FORMAT = "inkwell-launcher";
    public static final String THEME_FORMAT = "inkwell-theme";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int FAV = 0, HIDDEN = 1, OTHER = 2;
    private static final Pattern PKG = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");

    private BackupManager() {}

    public static final class Found {
        public final Set<String> favs = new LinkedHashSet<>();
        public final Set<String> hidden = new LinkedHashSet<>();
        public final Set<String> other = new LinkedHashSet<>();
        /** True when no list was labelled "favorites", so the order below is a guess. */
        public boolean guessed;

        public List<String> finalFavorites() {
            Set<String> src = favs.isEmpty() ? other : favs;
            guessed = favs.isEmpty() && !other.isEmpty();
            List<String> out = new ArrayList<>();
            for (String p : src) if (!hidden.contains(p) && out.size() < 40) out.add(p);
            return out;
        }
    }

    public static byte[] export() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("format", FORMAT);
        o.put("version", 1);
        o.put("prefs", Prefs.exportAll());
        return o.toString(2).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] exportTheme() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("format", THEME_FORMAT);
        o.put("version", 1);
        o.put("prefs", Prefs.exportTheme());
        return o.toString(2).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("File is too large");
        }
        return out.toByteArray();
    }

    /** Restores a Minimal Launcher Proper backup. Returns false if the file isn't one. */
    public static boolean restoreNative(byte[] data) {
        try {
            Object v = new JSONTokener(new String(data, StandardCharsets.UTF_8)).nextValue();
            if (!(v instanceof JSONObject)) return false;
            JSONObject o = (JSONObject) v;
            String fmt = o.optString("format");
            if (!FORMAT.equals(fmt) && !THEME_FORMAT.equals(fmt)) return false;
            JSONObject p = o.optJSONObject("prefs");
            if (p == null) return false;
            Prefs.importAll(p);
            Prefs.put(Prefs.SETUP_DONE, true);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Looks for installed-app package names inside any file (JSON, XML, text, or a zip of those).
     * Keys named like "hidden" or "favorites" classify the package names beneath them.
     */
    public static Found scanForeign(byte[] data) {
        Set<String> installed = AppRepo.installedPackages();
        Found f = new Found();
        if (data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            try (java.util.zip.GZIPInputStream g = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(data))) {
                data = readAll(g);
            } catch (IOException ignored) { }
        }
        if (data.length > 4 && data[0] == 'P' && data[1] == 'K') {
            try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    scanText(new String(readAll(z), StandardCharsets.ISO_8859_1), installed, f);
                }
            } catch (IOException ignored) { }
        } else {
            scanText(new String(data, StandardCharsets.UTF_8), installed, f);
        }
        return f;
    }

    private static void scanText(String s, Set<String> installed, Found f) {
        try {
            Object v = new JSONTokener(s).nextValue();
            if (v instanceof JSONObject || v instanceof org.json.JSONArray) {
                walk(v, OTHER, installed, f);
                return;
            }
        } catch (JSONException ignored) { }
        collect(s, OTHER, installed, f);
    }

    private static void walk(Object node, int inherited, Set<String> installed, Found f) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                collect(k, inherited, installed, f);
                walk(o.opt(k), classify(k, inherited), installed, f);
            }
        } else if (node instanceof org.json.JSONArray) {
            org.json.JSONArray a = (org.json.JSONArray) node;
            for (int i = 0; i < a.length(); i++) walk(a.opt(i), inherited, installed, f);
        } else if (node instanceof String) {
            collect((String) node, inherited, installed, f);
        }
    }

    private static int classify(String key, int inherited) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.contains("hidden") || k.contains("hide") || k.contains("blacklist") || k.contains("blocklist")) return HIDDEN;
        if (k.contains("fav") || k.contains("pinned") || k.contains("dock") || k.contains("shortcut")) return FAV;
        return inherited;
    }

    private static void collect(String s, int cat, Set<String> installed, Found f) {
        Matcher m = PKG.matcher(s);
        while (m.find()) {
            String p = m.group();
            if (!installed.contains(p)) continue;
            (cat == FAV ? f.favs : cat == HIDDEN ? f.hidden : f.other).add(p);
        }
    }

    public static void apply(List<String> favs, Set<String> hidden) {
        Prefs.putList(Prefs.FAVS, favs);
        if (!hidden.isEmpty()) Prefs.putList(Prefs.HIDDEN, new ArrayList<>(hidden));
        Prefs.put(Prefs.SETUP_DONE, true);
    }
}
