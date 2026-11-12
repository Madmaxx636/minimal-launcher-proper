package app.inkwell.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All settings live here so they can be exported and restored as one JSON blob. */
public final class Prefs {
    public static final String BG = "bg", FG = "fg", ACC = "acc", FONT = "font", SCALE = "scale",
            BOLD = "bold", UPPER = "upper", DIVIDERS = "dividers", ALIGN = "align", GAP = "gap",
            ICONS = "icons", BADGES = "badges", CLOCK = "clock", CLOCK24 = "clock24", DATE = "date",
            BATTERY = "battery", VALIGN = "valign", PAGED = "paged", VOLKEYS = "volkeys",
            FLASH_EVERY = "flashEvery", HIDE_STATUS = "hideStatus", G_UP = "gUp", G_DOWN = "gDown",
            G_LEFT = "gLeft", G_RIGHT = "gRight", G_DOUBLE = "gDouble", FAVS = "favs",
            HIDDEN = "hidden", MEDIA = "media", FOLDERS = "folders",
            LIST_SCALE = "listScale", CLOCK_SCALE = "clockScale", ICON_SCALE = "iconScale", MARGIN = "margin",
            W_MARGIN = "widgetMargin", W_GAP = "widgetGap",
            LEFT_HAND = "leftHand", HOME_ACTION = "homeAction", SEARCH_HIDDEN = "searchHidden",
            CLOCK_OFFSET = "clockOffset", BTN_ON = "buttonOn", MEDIA_ART = "mediaArt", MEDIA_ART_LOOK = "mediaArtLook", SETTINGS_ALL = "settingsAll", TYPE_SEARCH = "typeSearch", S_WEB = "srcWeb", S_CONTACTS = "srcContacts", S_EVENTS = "srcEvents",
            S_SETTINGS = "srcSettings", S_SHORTCUTS = "srcShortcuts", S_FILES = "srcFiles", WEB_ENGINE = "webEngine", WEB_URL = "webUrl", FLASH_STYLE = "flashStyle", FLASH_MS = "flashMs", FLASH_CYCLES = "flashCycles",
            FLASH_PAGES = "flashPages", FLASH_MINUTES = "flashMinutes", FLASH_RETURN = "flashReturn", FLASH_SCREEN = "flashScreen", MEDIA_BAR_POS = "mediaBarPos", MEDIA_STYLE = "mediaStyle", ICON_SHAPE = "iconShape", ICON_LOOK = "iconLook", ICON_RING = "iconRing",
            FOLDER_STYLE = "folderStyle", POP_BORDER = "popBorder", POP_CORNERS = "popCorners", BTN_SHAPE = "buttonShape",
            BTN_BORDER = "buttonBorder", BTN_FILL = "buttonFill", BTN_CONTENT = "buttonContent", BTN_WORD = "buttonWord", MEDIA_HOME = "mediaHome", MEDIA_HOME_POS = "mediaHomePos", BTN_POS = "buttonPos", BTN_SIZE = "buttonSize", CLOCK_STYLE = "clockStyle", AGENDA = "agenda",
            BREAK_ON = "breakOn", BREAK_APPS = "breakApps", BREAK_MIN = "breakMin", CLOCK_APP = "clockApp", CAL_APP = "calApp", BTN = "buttonTap", BTN_UP = "buttonUp", ICON_PACK = "iconPack", CLOCK_WIDGET = "clockWidget", RENAMES = "renames", SETUP_DONE = "setupDone", THEME = "theme";

    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            BG, FG, ACC, FONT, SCALE, BOLD, UPPER, DIVIDERS, ALIGN, GAP, ICONS, BADGES, CLOCK, CLOCK24,
            DATE, BATTERY, VALIGN, PAGED, VOLKEYS, FLASH_EVERY, HIDE_STATUS, G_UP, G_DOWN, G_LEFT,
            G_RIGHT, G_DOUBLE, FAVS, HIDDEN, MEDIA, FOLDERS, LIST_SCALE, CLOCK_SCALE, ICON_SCALE, MARGIN, W_MARGIN, W_GAP,
            LEFT_HAND, HOME_ACTION, SEARCH_HIDDEN, CLOCK_OFFSET, BTN_ON, MEDIA_ART, MEDIA_ART_LOOK, TYPE_SEARCH, S_WEB, S_CONTACTS, S_EVENTS, S_SETTINGS, S_SHORTCUTS, S_FILES, WEB_ENGINE, WEB_URL, FLASH_STYLE, FLASH_MS, FLASH_CYCLES, FLASH_PAGES, FLASH_MINUTES, FLASH_RETURN, FLASH_SCREEN, MEDIA_BAR_POS, MEDIA_STYLE, ICON_SHAPE, ICON_LOOK, ICON_RING, FOLDER_STYLE, POP_BORDER, POP_CORNERS, BTN_SHAPE, BTN_BORDER,
            BTN_FILL, BTN_CONTENT, BTN_WORD, MEDIA_HOME, MEDIA_HOME_POS, BTN_POS, BTN_SIZE, BTN, BTN_UP, ICON_PACK, CLOCK_STYLE, AGENDA, BREAK_ON, BREAK_APPS, BREAK_MIN,
            CLOCK_APP, CAL_APP, RENAMES, SETUP_DONE, THEME));

    public static final String[] PRESET_IDS = {"paper", "ink", "newsprint", "sepia", "niagara"};
    public static final String[] PRESET_NAMES = {
            "Paper: black on white", "Ink: white on black", "Newsprint: soft gray",
            "Sepia: for color e-ink", "Niagara-style: big, light, black"};

    /** Keys that make up a theme, for sharing a look without sharing favorites. */
    public static final String[] THEME_KEYS = {THEME, BG, FG, ACC, FONT, SCALE, LIST_SCALE, CLOCK_SCALE, ICON_SCALE, BOLD,
            UPPER, DIVIDERS, ALIGN, GAP, MARGIN, ICONS, VALIGN, ICON_PACK, HIDE_STATUS, CLOCK24, DATE, BATTERY, CLOCK, CLOCK_STYLE,
            ICON_SHAPE, ICON_LOOK, ICON_RING, FOLDER_STYLE, POP_BORDER, POP_CORNERS, BTN_SHAPE, BTN_BORDER, BTN_FILL, BTN_CONTENT, BTN_WORD};

    private static SharedPreferences sp;
    private static java.io.File fontFile;
    private static Typeface customBase;

    private Prefs() {}

    public static void init(Context c) {
        sp = c.getSharedPreferences("inkwell", Context.MODE_PRIVATE);
        // Suggestions and Plug and Play were removed, so forget whatever they had stored (how often apps were opened).
        sp.edit().remove("usage").remove("noSuggest").remove("suggest").remove("plugOn").apply();
        fontFile = new java.io.File(c.getFilesDir(), "custom_font");
    }

    public static int getInt(String k, int d) { return sp.getInt(k, d); }
    public static boolean getBool(String k, boolean d) { return sp.getBoolean(k, d); }
    public static String getStr(String k, String d) { return sp.getString(k, d); }
    public static void put(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public static void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public static void put(String k, String v) { sp.edit().putString(k, v).apply(); }
    public static boolean has(String k) { return sp.contains(k); }
    public static void clearAll() { sp.edit().clear().apply(); }

    // ---- look ----
    public static int bg() { return getInt(BG, 0xFFFFFFFF); }
    public static int fg() { return getInt(FG, 0xFF000000); }
    public static int accent() { return getInt(ACC, 0xFF000000); }
    public static int scalePct() { return getInt(SCALE, 100); }
    public static String align() { return getStr(ALIGN, "left"); }

    public static Typeface typeface() {
        String fam;
        String f = getStr(FONT, "medium");
        if (f.equals("custom") && fontFile.exists()) {
            try {
                if (customBase == null) customBase = Typeface.createFromFile(fontFile);
                return Typeface.create(customBase, getBool(BOLD, false) ? Typeface.BOLD : Typeface.NORMAL);
            } catch (RuntimeException e) {
                customBase = null; // unreadable file: fall back to the default below
            }
        }
        switch (f) {
            case "light": fam = "sans-serif-light"; break;
            case "regular": fam = "sans-serif"; break;
            case "condensed": fam = "sans-serif-condensed"; break;
            case "serif": fam = "serif"; break;
            case "mono": fam = "monospace"; break;
            case "casual": fam = "casual"; break;
            default: fam = "sans-serif-medium";
        }
        return Typeface.create(fam, getBool(BOLD, false) ? Typeface.BOLD : Typeface.NORMAL);
    }

    public static final String[] FONT_IDS = {"medium", "regular", "light", "condensed", "serif", "mono", "casual"};
    public static final String[] FONT_NAMES = {"Sans medium (crisp)", "Sans regular", "Sans light", "Condensed", "Serif", "Monospace", "Casual"};

    /** Saves a font file picked by the user and switches to it. Returns false if it isn't a usable font. */
    public static boolean installFont(byte[] data) {
        try {
            java.io.File tmp = new java.io.File(fontFile.getPath() + ".tmp");
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(tmp)) { o.write(data); }
            Typeface.createFromFile(tmp); // throws if the file isn't a font
            if (!tmp.renameTo(fontFile)) return false;
            customBase = null;
            put(FONT, "custom");
            put(THEME, "custom");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JSONObject exportTheme() throws JSONException {
        JSONObject all = exportAll(), out = new JSONObject();
        for (String k : THEME_KEYS) if (all.has(k)) out.put(k, all.get(k));
        return out;
    }

    public static void applyPreset(String id) {
        SharedPreferences.Editor e = sp.edit();
        e.putString(THEME, id);
        e.putBoolean(BOLD, false).putBoolean(UPPER, false).putBoolean(ICONS, false)
                .putString(ALIGN, "left").putInt(SCALE, 100).putInt(GAP, 6).putBoolean(DIVIDERS, true)
                .putString(FONT, "medium")
                .putInt(LIST_SCALE, 100).putInt(CLOCK_SCALE, 100).putInt(ICON_SCALE, 100).putInt(MARGIN, 24)
                .putString(ICON_SHAPE, "original").putString(ICON_LOOK, "gray").putString(ICON_RING, "none").putString(FOLDER_STYLE, "glyph")
                .putString(POP_BORDER, "normal").putString(POP_CORNERS, "square").putString(BTN_SHAPE, "circle")
                .putString(BTN_BORDER, "normal").putString(BTN_FILL, "theme").putString(BTN_CONTENT, "icon");
        switch (id) {
            case "ink":
                e.putInt(BG, 0xFF000000).putInt(FG, 0xFFFFFFFF).putInt(ACC, 0xFFFFFFFF);
                break;
            case "newsprint":
                e.putInt(BG, 0xFFDDDDDD).putInt(FG, 0xFF111111).putInt(ACC, 0xFF111111).putString(FONT, "serif");
                break;
            case "sepia":
                e.putInt(BG, 0xFFF4ECD8).putInt(FG, 0xFF3B2F1E).putInt(ACC, 0xFF8A4B14).putString(FONT, "serif");
                break;
            case "niagara":
                e.putInt(BG, 0xFF000000).putInt(FG, 0xFFFFFFFF).putInt(ACC, 0xFFFFFFFF)
                        .putString(FONT, "regular").putInt(SCALE, 130).putInt(GAP, 10).putBoolean(DIVIDERS, false);
                break;
            default: // paper
                e.putInt(BG, 0xFFFFFFFF).putInt(FG, 0xFF000000).putInt(ACC, 0xFF000000);
        }
        e.apply();
    }

    // ---- lists ----
    public static List<String> getList(String k) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(getStr(k, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    public static void putList(String k, List<String> v) {
        put(k, new JSONArray(v).toString());
    }

    public static JSONObject getRenames() {
        try { return new JSONObject(getStr(RENAMES, "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    public static void setRename(String pkg, String name) {
        JSONObject o = getRenames();
        try {
            if (name == null || name.trim().isEmpty()) o.remove(pkg); else o.put(pkg, name.trim());
        } catch (JSONException ignored) { }
        put(RENAMES, o.toString());
    }

    // ---- backup ----
    public static JSONObject exportAll() throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> en : sp.getAll().entrySet()) {
            if (!KNOWN.contains(en.getKey())) continue;
            o.put(en.getKey(), en.getValue());
        }
        return o;
    }

    public static void importAll(JSONObject o) {
        SharedPreferences.Editor e = sp.edit();
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!KNOWN.contains(k)) continue;
            Object v = o.opt(k);
            if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else if (v instanceof Number) e.putInt(k, ((Number) v).intValue());
            else if (v instanceof String) e.putString(k, (String) v);
        }
        e.apply();
    }

    public static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }
}
