package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final int REQ_CAL = 77, REQ_CONTACTS = 80, REQ_EVENTS = 81, REQ_FILES = 82;
    private static final int REQ_EXPORT = 1, REQ_RESTORE = 2, REQ_NIAGARA = 3, REQ_EXPORT_THEME = 4, REQ_FONT = 5;
    private static final String NIAGARA_PKG = "bitpit.launcher";

    /** The scrolling screen. Its three children never change: the Back row, the search box and the body. */
    private LinearLayout screen, body;
    private ScrollView sv;
    private TextView backRow;
    private EditText searchBox;
    /** Where the row helpers add rows while the pages are being built, and the essentials/extras pair being filled. */
    private LinearLayout list;
    private LinearLayout[] page;
    private final Map<String, LinearLayout[]> pages = new LinkedHashMap<>();
    private String current = "", query = "";
    private boolean showAll;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        showAll = Prefs.getBool(Prefs.SETTINGS_ALL, false);
        screen = new LinearLayout(this);
        sv = Ui.screen(this, screen);
        int pad = (int) Prefs.dp(this, 16);
        backRow = new TextView(this);
        backRow.setPadding(pad, (int) Prefs.dp(this, 14), pad, (int) Prefs.dp(this, 14));
        backRow.setOnClickListener(v -> goBack());
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Search settings");
        searchBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable e) {
                String q = e.toString().trim();
                if (q.equals(query)) return;
                query = q;
                build(true);
            }
        });
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        screen.addView(backRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(pad, 0, pad, pad / 2);
        screen.addView(searchBox, sp);
        screen.addView(body, new LinearLayout.LayoutParams(-1, -2));
        setContentView(sv);
        if ("niagara".equals(getIntent().getStringExtra("flow"))) {
            current = "backup";
            niagaraIntro();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // ---- the screen: a fixed skeleton, with only the body rebuilt ----

    private static final String[] CATS = {"look", "home", "widgets", "search", "eink", "gestures", "apps", "backup", "about"};
    private static final String[] CAT_TITLES = {"Look & theme", "Home screen", "Widgets", "Search & keyboard", "E-ink & refresh",
            "Gestures", "Apps & privacy", "Backup & Niagara", "About"};
    private static final String[] CAT_HINTS = {
            "Colors, font, text size, icons and styles",
            "Home apps, clock, music controls, quick button",
            "Adding, ordering and sizing widgets",
            "Type to search, the Home key, web and device search",
            "Page turning and clearing ghosting",
            "Swipes and taps",
            "Hidden apps and daily usage limits",
            "Export, import, share a theme, reset",
            "Version and privacy"};

    private void goBack() {
        if (!query.isEmpty()) {
            searchBox.setText(""); // the search box rebuilds the screen when it empties
        } else if (!current.isEmpty()) {
            current = "";
            build(true);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() { goBack(); }

    private void build() { build(false); }

    private void build(boolean toTop) {
        final int scroll = toTop ? 0 : sv.getScrollY();
        Ui.theme(this, sv);
        restyle();
        buildPages();
        body.removeAllViews();
        if (!query.isEmpty()) showResults();
        else if (current.isEmpty()) showMain();
        else showCategory(current);
        sv.post(() -> sv.scrollTo(0, scroll));
    }

    private void restyle() {
        int fg = Prefs.fg(), bg = Prefs.bg();
        float k = Prefs.scalePct() / 100f;
        backRow.setText(current.isEmpty() && query.isEmpty() ? "‹  Back to home" : "‹  Back to all settings");
        backRow.setTextColor(fg);
        backRow.setTypeface(Prefs.typeface());
        backRow.setTextSize(18 * k);
        searchBox.setTextColor(fg);
        searchBox.setHintTextColor((fg & 0x00FFFFFF) | 0x88000000);
        searchBox.setTypeface(Prefs.typeface());
        searchBox.setTextSize(16 * k);
        android.graphics.drawable.GradientDrawable box = new android.graphics.drawable.GradientDrawable();
        box.setColor(bg);
        box.setStroke((int) Prefs.dp(this, 2), fg);
        searchBox.setBackground(box);
        int p = (int) Prefs.dp(this, 12);
        searchBox.setPadding(p, p, p, p);
    }

    private String titleOf(String id) { return CAT_TITLES[indexOf(CATS, id)]; }

    private void open(String id) {
        current = id;
        build(true);
    }

    private void setShowAll(boolean all) {
        showAll = all;
        Prefs.put(Prefs.SETTINGS_ALL, all);
        build();
    }

    private void showMain() {
        for (int i = 0; i < CATS.length; i++) {
            final String id = CATS[i];
            Ui.row(body, CAT_TITLES[i] + "  ›", CAT_HINTS[i], v -> open(id));
        }
        Ui.row(body, "Settings shown", showAll ? "Every option. Tap to show only the essentials." : "Just the essentials. Tap to show every option.",
                v -> setShowAll(!showAll));
    }

    private int countRows(LinearLayout rows) {
        int n = 0;
        for (int i = 0; i < rows.getChildCount(); i++) if (rows.getChildAt(i).getTag() instanceof String[]) n++;
        return n;
    }

    private void showCategory(String id) {
        LinearLayout[] p = pages.get(id);
        Ui.section(body, titleOf(id));
        body.addView(p[0]);
        int more = countRows(p[1]);
        if (more == 0) return;
        if (showAll) {
            Ui.section(body, "More options");
            body.addView(p[1]);
        } else {
            Ui.row(body, "More options  (" + more + ")", "Tap to show every option, here and in all settings.", v -> setShowAll(true));
        }
    }

    /** Search looks through every row, including the ones Simple mode hides, and shows them under their category. */
    private void showResults() {
        String[] words = AppRepo.sortKey(query).split("\\s+");
        boolean any = false;
        for (String id : CATS) {
            List<View> hits = new ArrayList<>();
            for (LinearLayout rows : pages.get(id)) {
                for (int i = 0; i < rows.getChildCount(); i++) {
                    View cell = rows.getChildAt(i);
                    if (!(cell.getTag() instanceof String[])) continue;
                    String[] t = (String[]) cell.getTag();
                    String hay = AppRepo.sortKey(t[0] + " " + t[1]);
                    boolean all = true;
                    for (String w : words) if (!w.isEmpty() && !hay.contains(w)) all = false;
                    if (all) hits.add(cell);
                }
            }
            if (hits.isEmpty()) continue;
            any = true;
            Ui.section(body, titleOf(id));
            for (View h : hits) {
                ((android.view.ViewGroup) h.getParent()).removeView(h);
                body.addView(h);
            }
        }
        if (!any) Ui.row(body, "Nothing found", "Try another word, like \"icon\", \"refresh\" or \"widget\".", null);
    }

    /** Starts a category: rows added next go to its essentials list until more() switches to the extras. */
    private void begin(String id) {
        page = new LinearLayout[]{rows(), rows()};
        pages.put(id, page);
        list = page[0];
    }

    private LinearLayout rows() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** Rows added after this one are options for people who want every setting. */
    private void more() { list = page[1]; }

    private void buildPages() {
        pages.clear();

        // ---- Look & theme ----
        begin("look");
        String cur = Prefs.getStr(Prefs.THEME, "custom");
        int pi = indexOf(Prefs.PRESET_IDS, cur);
        Ui.row(list, "Theme preset", pi >= 0 ? Prefs.PRESET_NAMES[pi] : "Custom", v ->
                new AlertDialog.Builder(this).setTitle("Theme preset")
                        .setItems(Prefs.PRESET_NAMES, (d, w) -> { Prefs.applyPreset(Prefs.PRESET_IDS[w]); build(); }).show());
        colorRow("Background color", Prefs.BG, Prefs.bg());
        colorRow("Text color", Prefs.FG, Prefs.fg());
        int fi = Math.max(0, indexOf(Prefs.FONT_IDS, Prefs.getStr(Prefs.FONT, "medium")));
        final String fontNow = Prefs.getStr(Prefs.FONT, "medium").equals("custom") ? "Your font file" : Prefs.FONT_NAMES[fi];
        Ui.row(list, "Font", fontNow, v -> {
            final String[] items = new String[Prefs.FONT_NAMES.length + 1];
            System.arraycopy(Prefs.FONT_NAMES, 0, items, 0, Prefs.FONT_NAMES.length);
            items[items.length - 1] = "Choose a font file (.ttf / .otf)…";
            new AlertDialog.Builder(this).setTitle("Font").setItems(items, (d, w) -> {
                if (w == Prefs.FONT_NAMES.length) {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_FONT);
                } else {
                    Prefs.put(Prefs.FONT, Prefs.FONT_IDS[w]);
                    Prefs.put(Prefs.THEME, "custom");
                    build();
                }
            }).show();
        });
        sliderRow("Size of everything", "Text, music bar, notifications and settings", Prefs.SCALE, 50, 300, 5, "%", 100);
        toggle("Show app icons", Prefs.ICONS, false);
        choice("Alignment", Prefs.ALIGN, new String[]{"Left", "Center", "Right"}, new String[]{"left", "center", "right"}, "left");
        more();
        colorRow("Accent color (notification dots)", Prefs.ACC, Prefs.accent());
        toggle("Bold text", Prefs.BOLD, false);
        toggle("UPPERCASE labels", Prefs.UPPER, false);
        toggle("Lines between apps", Prefs.DIVIDERS, true);
        toggle("Hide status bar", Prefs.HIDE_STATUS, false);
        Ui.section(list, "Size & spacing");
        sliderRow("App list text", "Home and all-apps list, on top of the above", Prefs.LIST_SCALE, 50, 300, 5, "%", 100);
        sliderRow("Clock size", "The time and date on home", Prefs.CLOCK_SCALE, 50, 400, 5, "%", 100);
        sliderRow("Icon size", "When icons are on", Prefs.ICON_SCALE, 50, 300, 5, "%", 100);
        sliderRow("Row spacing", "Space above and below each app", Prefs.GAP, 0, 60, 2, " dp", 6);
        sliderRow("Side margin", "Space at the left and right edges", Prefs.MARGIN, 0, 96, 4, " dp", 24);
        Ui.row(list, "Reset sizes", "Back to 100% and default spacing", v -> {
            for (String k : new String[]{Prefs.SCALE, Prefs.LIST_SCALE, Prefs.CLOCK_SCALE, Prefs.ICON_SCALE}) Prefs.put(k, 100);
            Prefs.put(Prefs.GAP, 6);
            Prefs.put(Prefs.MARGIN, 24);
            build();
        });
        Ui.section(list, "Icons and folders");
        final Map<String, String> packs = IconPacks.installed(this);
        String curPack = Prefs.getStr(Prefs.ICON_PACK, "");
        Ui.row(list, "Icon pack", curPack.isEmpty() ? "None (original icons)" : (packs.containsKey(curPack) ? packs.get(curPack) : curPack), v -> {
            final List<String> ids = new ArrayList<>(), names = new ArrayList<>();
            ids.add("");
            names.add("None (original icons)");
            for (Map.Entry<String, String> e : packs.entrySet()) { ids.add(e.getKey()); names.add(e.getValue()); }
            if (ids.size() == 1) names.set(0, "None. No icon packs are installed.");
            new AlertDialog.Builder(this).setTitle("Icon pack").setItems(names.toArray(new String[0]), (d, w) -> {
                Prefs.put(Prefs.ICON_PACK, ids.get(w));
                AppRepo.clearIconCache();
                build();
            }).show();
        });
        choice("Icon shape", Prefs.ICON_SHAPE, IconStyle.SHAPE_NAMES, IconStyle.SHAPE_IDS, "original");
        choice("Icon colors", Prefs.ICON_LOOK, IconStyle.LOOK_NAMES, IconStyle.LOOK_IDS, "gray");
        choice("Icon outline", Prefs.ICON_RING, IconStyle.RING_NAMES, IconStyle.RING_IDS, "none");
        choice("Folders in lists", Prefs.FOLDER_STYLE, IconStyle.FOLDER_NAMES, IconStyle.FOLDER_IDS, "glyph");
        choice("Pop-up border", Prefs.POP_BORDER, new String[]{"None", "Thin", "Normal", "Thick"},
                new String[]{"none", "thin", "normal", "thick"}, "normal");
        choice("Pop-up corners", Prefs.POP_CORNERS, new String[]{"Square", "Rounded"}, new String[]{"square", "rounded"}, "square");

        // ---- Home screen ----
        begin("home");
        Ui.row(list, "Home apps", Prefs.getList(Prefs.FAVS).size() + " apps. Choose, group and reorder.", v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        toggle("Show clock", Prefs.CLOCK, true);
        choice("Clock style", Prefs.CLOCK_STYLE, ClockView.STYLE_NAMES, ClockView.STYLE_IDS, "classic");
        toggle("24-hour clock", Prefs.CLOCK24, true);
        toggle("Show date", Prefs.DATE, true);
        toggle("Show battery", Prefs.BATTERY, true);
        toggle("Left-hand mode", Prefs.LEFT_HAND, false);
        toggle("Show music controls", Prefs.MEDIA, true);
        toggle("Show artwork in music controls", Prefs.MEDIA_ART, true);
        Ui.row(list, "Music controls: what the launcher sees", NotifService.mediaStatus(this) + "\n(tap to check again)", v -> {
            NotifService.requestRebindIfNeeded(this);
            build();
        });
        toggle("Notification dots", Prefs.BADGES, true);
        Ui.row(list, "Notification access", NotifService.isEnabled(this)
                ? "On. Powers dots, the notifications panel and music controls."
                : "Off. Tap to turn on.", v ->
                new AlertDialog.Builder(this).setTitle("Notification access")
                        .setMessage("Minimal Launcher Proper reads your notifications and the current music session to draw dots, "
                                + "the notifications panel and the music bar. It all stays in memory on this device and is never stored or sent anywhere.")
                        .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                        .setNegativeButton("Cancel", null).show());
        toggle("Quick button (a round button, like Niagara's)", Prefs.BTN_ON, false);
        more();
        choice("List position", Prefs.VALIGN, new String[]{"Top", "Middle", "Bottom"}, new String[]{"top", "center", "bottom"}, "top");
        sliderRow("Move clock", "Space above the clock and its widgets", Prefs.CLOCK_OFFSET, 0, 400, 4, " dp", 0);
        Ui.row(list, "Calendar agenda", Prefs.getBool(Prefs.AGENDA, false) && AgendaView.hasPermission(this)
                ? "On. Your next events show under the clock." : "Off. Tap to show your next events under the clock.", v -> {
            if (Prefs.getBool(Prefs.AGENDA, false)) { Prefs.put(Prefs.AGENDA, false); build(); return; }
            Prefs.put(Prefs.AGENDA, true);
            if (!AgendaView.hasPermission(this)) requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, REQ_CAL);
            else build();
        });
        appRow("App the clock opens", Prefs.CLOCK_APP, "Android's default");
        appRow("App the date opens", Prefs.CAL_APP, "Android's default");
        choice("Music controls style", Prefs.MEDIA_STYLE, new String[]{"The launcher's controls", "The app's own widget (launcher controls until one is set up)", "Both: the widget and the launcher's controls"},
                new String[]{"bar", "widget", "both"}, "widget");
        toggle("Shrink the app list to fit when music shows", Prefs.FIT_MEDIA, true);
        Ui.row(list, "Media app widgets", mediaWidgetSummary(), v -> pickMediaApp());
        choice("Music controls position", Prefs.MEDIA_BAR_POS, new String[]{"Bottom of the screen", "Above the clock", "Below the clock", "Above the app list"},
                new String[]{"bottom", "clock", "below", "list"}, "bottom");
        choice("Artwork colors", Prefs.MEDIA_ART_LOOK, IconStyle.LOOK_NAMES, IconStyle.LOOK_IDS, "gray");
        Ui.section(list, "Quick button");
        Ui.row(list, "Tap", ButtonTargets.label(Prefs.getStr(Prefs.BTN, "")), v ->
                ButtonTargets.pick(this, "Tap action", Prefs.BTN, this::build));
        Ui.row(list, "Swipe up", ButtonTargets.label(Prefs.getStr(Prefs.BTN_UP, "")), v ->
                ButtonTargets.pick(this, "Swipe-up action", Prefs.BTN_UP, this::build));
        choice("Quick button position", Prefs.BTN_POS, new String[]{"Automatic (opposite the alphabet)", "Left", "Center", "Right"},
                new String[]{"auto", "start", "center", "end"}, "auto");
        sliderRow("Quick button size", "", Prefs.BTN_SIZE, 40, 96, 4, " dp", 56);
        choice("Quick button shape", Prefs.BTN_SHAPE, ButtonView.SHAPE_NAMES, ButtonView.SHAPE_IDS, "circle");
        choice("Quick button border", Prefs.BTN_BORDER, ButtonView.BORDER_NAMES, ButtonView.BORDER_IDS, "normal");
        choice("Quick button fill", Prefs.BTN_FILL, ButtonView.FILL_NAMES, ButtonView.FILL_IDS, "theme");
        choice("Quick button contents", Prefs.BTN_CONTENT, ButtonView.CONTENT_NAMES, ButtonView.CONTENT_IDS, "glyph");
        if (Prefs.getStr(Prefs.BTN_CONTENT, "glyph").equals("glyph")) choice("Quick button icon", Prefs.BTN_GLYPH, ButtonView.GLYPH_NAMES, ButtonView.GLYPH_IDS, "target");
        if (Prefs.getStr(Prefs.BTN_CONTENT, "glyph").equals("word")) textRow("Quick button word", Prefs.BTN_WORD, "Up to 6 characters", 6);

        // ---- Widgets ----
        begin("widgets");
        Ui.row(list, "Adding and editing widgets", "Long-press the clock on home. Add a widget, or choose Edit on one to change its size, colors, order and place.", null);
        Ui.row(list, "Reorder widgets", "Drag them into order, above or below the clock", v ->
                startActivity(new Intent(this, WidgetOrderActivity.class)));
        String problem = Widgets.lastProblem();
        Ui.row(list, "Last widget problem", problem.isEmpty() ? "None" : problem + "\n(tap to clear)", problem.isEmpty() ? null : v -> {
            Widgets.clearProblem();
            build();
        });
        more();
        sliderRow("Space between widgets", "Vertical gap", Prefs.W_GAP, 0, 60, 2, " dp", 8);
        sliderRow("Widget side margin", "Space at the left and right of widgets", Prefs.W_MARGIN, 0, 96, 4, " dp", 16);
        choice("App widgets appear (while their app plays)", Prefs.MEDIA_HOME_POS, new String[]{"Above the clock", "Below the clock", "Above the app list", "Bottom of the screen"},
                new String[]{"clock", "below", "list", "bottom"}, "list");
        sliderRow("Media widget height", "Makes the widgets shown on home taller or shorter. 100% is the size you set for each widget.", Prefs.MEDIA_W_SCALE, 50, 200, 10, "%", 100);
        choice("Show media widgets while", Prefs.MEDIA_SHOW, new String[]{"Playing or paused", "Only while playing"}, new String[]{"any", "playing"}, "any");
        choice("Hide after being paused for", Prefs.MEDIA_STALE, new String[]{"Never", "5 minutes", "15 minutes", "30 minutes", "1 hour"},
                new String[]{"0", "5", "15", "30", "60"}, "0");

        // ---- Search & keyboard ----
        begin("search");
        toggle("Start typing to search (physical keyboard)", Prefs.TYPE_SEARCH, true);
        choice("Pressing Home while already on home", Prefs.HOME_ACTION,
                new String[]{"Open search", "Open all apps", "Go to the top of the list", "Do nothing"},
                new String[]{"search", "drawer", "top", "none"}, "search");
        toggle("Show hidden apps in search", Prefs.SEARCH_HIDDEN, true);
        toggle("Web search and links", Prefs.S_WEB, true);
        Ui.row(list, "Keys", "Type anywhere on home to search. Arrow keys pick a result, Enter opens it, Esc closes search.", null);
        Ui.row(list, "Privacy", "Searching stays on this device. Web search only hands your words to your browser; this app never goes online.", null);
        more();
        choice("Web search engine", Prefs.WEB_ENGINE, WebSearch.ENGINE_NAMES, WebSearch.ENGINE_IDS, "duckduckgo");
        if (Prefs.getStr(Prefs.WEB_ENGINE, "duckduckgo").equals("custom")) {
            textRow("Your search address", Prefs.WEB_URL, "Put %s where the search words go. For example https://example.com/?q=%s", 200);
        }
        toggle("Settings screens (Wi-Fi, Bluetooth, Display…)", Prefs.S_SETTINGS, true);
        toggle("App shortcuts (such as New tab)", Prefs.S_SHORTCUTS, true);
        sourceRow("Contacts", Prefs.S_CONTACTS, new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        sourceRow("Calendar events", Prefs.S_EVENTS, new String[]{android.Manifest.permission.READ_CALENDAR}, REQ_EVENTS);
        sourceRow("Media files (photos, video, music)", Prefs.S_FILES, DeviceSearch.mediaPermissions(), REQ_FILES);

        // ---- E-ink & refresh ----
        begin("eink");
        toggle("Turn pages instead of scrolling", Prefs.PAGED, true);
        toggle("Volume / page keys turn pages", Prefs.VOLKEYS, true);
        Ui.row(list, "Refresh now", "Flashes the screen once, as set under More options. Use it to test your settings.", v -> Eink.flash(this));
        more();
        choice("Refresh style", Prefs.FLASH_STYLE, Eink.STYLE_NAMES, Eink.STYLE_IDS, "bw");
        sliderRow("Speed of each flash step", "Time the screen stays black or white. Slower panels need longer.", Prefs.FLASH_MS, 80, 600, 20, " ms", 220);
        sliderRow("Flashes per refresh", "More flashes clear stubborn ghosting.", Prefs.FLASH_CYCLES, 1, 3, 1, "", 1);
        sliderRow("Refresh every N list opens or closes", "0 turns it off.", Prefs.FLASH_EVERY, 0, 20, 1, "", 0);
        sliderRow("Refresh every N page turns", "0 turns it off.", Prefs.FLASH_PAGES, 0, 30, 1, "", 0);
        sliderRow("Refresh every N minutes on the home screen", "0 turns it off.", Prefs.FLASH_MINUTES, 0, 60, 1, " min", 0);
        toggle("Refresh when I return home from an app", Prefs.FLASH_RETURN, false);
        toggle("Refresh when the screen wakes up", Prefs.FLASH_SCREEN, false);

        // ---- Gestures ----
        begin("gestures");
        choice("Swipe up", Prefs.G_UP, new String[]{"Open search", "Open all apps", "Refresh the screen", "Nothing"},
                new String[]{"search", "drawer", "flash", "none"}, "search");
        choice("Swipe down", Prefs.G_DOWN, new String[]{"Android notification shade", "Launcher notifications", "Nothing"},
                new String[]{"notifications", "panel", "none"}, "notifications");
        appRow("Swipe left", Prefs.G_LEFT);
        appRow("Swipe right", Prefs.G_RIGHT);
        choice("Double-tap empty space", Prefs.G_DOUBLE, new String[]{"Lock screen", "Open all apps", "Refresh the screen", "Nothing"},
                new String[]{"lock", "drawer", "flash", "none"}, "lock");
        Ui.row(list, "Lock screen access", LockService.isRunning() ? "Enabled" : "Not enabled. Tap to open Accessibility settings.", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // ---- Apps & privacy ----
        begin("apps");
        Ui.row(list, "Hidden apps", Prefs.getList(Prefs.HIDDEN).size() + " hidden from the app list", v -> hiddenDialog());
        Ui.row(list, "Reset renamed apps", null, v -> {
            Prefs.put(Prefs.RENAMES, "{}");
            toast("Names restored");
        });
        more();
        Ui.section(list, "Usage Breaker");
        toggle("Pause before opening apps you've overused", Prefs.BREAK_ON, false);
        sliderRow("Daily limit", "Minutes per app per day before the pause appears", Prefs.BREAK_MIN, 5, 240, 5, " min", 30);
        Ui.row(list, "Apps to watch", Prefs.getList(Prefs.BREAK_APPS).size() + " chosen", v -> {
            final List<AppRepo.AppEntry> apps = AppRepo.all();
            final List<String> chosen = Prefs.getList(Prefs.BREAK_APPS);
            String[] names = new String[apps.size()];
            final boolean[] on = new boolean[apps.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = AppRepo.displayLabel(apps.get(i));
                on[i] = chosen.contains(apps.get(i).pkg);
            }
            new AlertDialog.Builder(this).setTitle("Apps to watch").setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                    .setPositiveButton("Save", (d, w) -> {
                        List<String> out = new ArrayList<>();
                        for (int i = 0; i < on.length; i++) if (on[i]) out.add(apps.get(i).pkg);
                        Prefs.putList(Prefs.BREAK_APPS, out);
                        build();
                    }).setNegativeButton("Cancel", null).show();
        });
        Ui.row(list, "Usage access", UsageGuard.hasAccess(this) ? "On" : "Off. Needed to know how long an app has been used. Tap to turn on.", v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        // ---- Backup & Niagara ----
        begin("backup");
        Ui.row(list, "Import from Niagara", "Bring over favorites and hidden apps", v -> niagaraIntro());
        Ui.row(list, "Export my setup", "Theme, settings, favorites, hidden apps, names, as a file", v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "minimal-launcher-" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json");
            startActivityForResult(i, REQ_EXPORT);
        });
        Ui.row(list, "Restore my setup or import a theme", "From a file exported here", v ->
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_RESTORE));
        Ui.row(list, "Share my theme", "Just the look (colors, font, sizes), no favorites", v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, "minimal-launcher-theme.json");
            startActivityForResult(i, REQ_EXPORT_THEME);
        });
        Ui.row(list, "Make Minimal Launcher Proper the home app", "Opens Android's default home app setting", v -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        Ui.row(list, "Reset everything", "Back to defaults", v ->
                new AlertDialog.Builder(this).setTitle("Reset all settings?")
                        .setMessage("Theme, favorites, hidden apps and names go back to defaults.")
                        .setPositiveButton("Reset", (d, w) -> {
                            Widgets.clearAll();
                            Prefs.clearAll();
                            Prefs.putList(Prefs.FAVS, AppRepo.guessStarterFavorites());
                            Prefs.put(Prefs.SETUP_DONE, true);
                            Prefs.put("welcomed", true);
                            build();
                        })
                        .setNegativeButton("Cancel", null).show());

        // ---- About ----
        begin("about");
        Ui.row(list, "Minimal Launcher Proper 1.0", getString(R.string.tagline) + "\nNo internet permission. No accounts, ads or analytics.", null);
    }

    // ---- row helpers ----

    /** A search source that reads your data. It stays off until you allow the permission it needs. */
    private void sourceRow(String title, final String key, final String[] perms, final int req) {
        final boolean on = Prefs.getBool(key, false);
        Ui.row(list, title, on ? "On" : "Off. Needs permission when you turn it on.", v -> {
            if (on) {
                Prefs.put(key, false);
                build();
                return;
            }
            Prefs.put(key, true);
            boolean granted = false;
            for (String p : perms) if (checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) build(); else requestPermissions(perms, req);
        });
    }

    private void textRow(final String title, final String key, String hint, final int max) {
        final String cur = Prefs.getStr(key, "");
        Ui.row(list, title, cur.isEmpty() ? "(empty)" : cur, v -> {
            final EditText et = new EditText(this);
            et.setSingleLine(true);
            et.setText(cur);
            et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
            new AlertDialog.Builder(this).setTitle(title).setMessage(hint).setView(et)
                    .setPositiveButton("Save", (d, w) -> {
                        Prefs.put(key, et.getText().toString().trim());
                        build();
                    }).setNegativeButton("Cancel", null).show();
        });
    }

    private void sliderRow(String title, String hint, final String key, final int min, final int max,
                           final int step, final String unit, final int def) {
        final int cur = Prefs.getInt(key, def);
        Ui.row(list, title + ":  " + cur + unit, hint, v ->
                Ui.slider(this, title, min, max, step, unit, cur, def, val -> {
                    Prefs.put(key, val);
                    Prefs.put(Prefs.THEME, "custom");
                    build();
                }));
    }

    private void toggle(String title, String key, boolean def) {
        boolean on = Prefs.getBool(key, def);
        Ui.row(list, title, on ? "On" : "Off", v -> {
            Prefs.put(key, !on);
            if (isLookKey(key)) Prefs.put(Prefs.THEME, "custom");
            build();
        });
    }

    private static boolean isLookKey(String k) {
        return k.equals(Prefs.BOLD) || k.equals(Prefs.UPPER) || k.equals(Prefs.DIVIDERS) || k.equals(Prefs.ICONS);
    }

    /** Media apps worth offering a widget for: the ones in a media-like folder, plus any that has played music. */
    private List<String> mediaApps() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>(Prefs.getList(Prefs.MEDIA_ASKED));
        for (String id : Folders.allIds()) {
            String n = Folders.nameOf(id).toLowerCase(java.util.Locale.ROOT);
            if (n.contains("media") || n.contains("music") || n.contains("audio") || n.contains("podcast")) out.addAll(Folders.apps(id));
        }
        List<String> keep = new ArrayList<>();
        for (String pkg : out) if (AppRepo.find(pkg) != null && WidgetPicker.hasWidgets(this, pkg)) keep.add(pkg);
        return keep;
    }

    private String mediaWidgetSummary() {
        List<String> apps = mediaApps();
        int set = 0;
        for (String p : apps) if (!Widgets.ids("app:" + p).isEmpty()) set++;
        return apps.isEmpty() ? "No media apps with widgets found yet. Play something and they appear here."
                : set + " of " + apps.size() + " media apps with widgets are set up. Tap to choose.";
    }

    private void pickMediaApp() {
        final List<String> apps = mediaApps();
        if (apps.isEmpty()) {
            Toast.makeText(this, "Nothing found yet. Play something, or put music apps in a folder named Media or Music.", Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> off = Prefs.getList(Prefs.MEDIA_OFF);
        final String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) {
            String p = apps.get(i);
            names[i] = AppRepo.displayLabel(AppRepo.find(p))
                    + (off.contains(p) ? "   · off, uses launcher controls" : Widgets.ids("app:" + p).isEmpty() ? "" : "   ✓ widget set");
        }
        new AlertDialog.Builder(this).setTitle("Media app widgets")
                .setItems(names, (d, w) -> {
                    final String pkg = apps.get(w);
                    final boolean isOff = off.contains(pkg);
                    String[] actions = {"Choose its widget…", isOff ? "Use its widget again" : "Don't use its widget (launcher controls instead)"};
                    new AlertDialog.Builder(this).setTitle(AppRepo.displayLabel(AppRepo.find(pkg)))
                            .setItems(actions, (d2, a) -> {
                                if (a == 0) {
                                    WidgetPicker.showFor(this, "app:" + pkg, pkg, this::build);
                                } else {
                                    if (isOff) off.remove(pkg); else off.add(pkg);
                                    Prefs.putList(Prefs.MEDIA_OFF, off);
                                    build();
                                }
                            }).show();
                })
                .setNegativeButton("Close", null).show();
    }

    private void choice(String title, String key, String[] names, String[] values, String def) {
        final int idx = Math.max(0, indexOf(values, Prefs.getStr(key, def)));
        Ui.row(list, title, names[idx], v ->
                new AlertDialog.Builder(this).setTitle(title)
                        .setSingleChoiceItems(names, idx, (d, w) -> {
                            Prefs.put(key, values[w]);
                            if (key.equals(Prefs.ALIGN)) Prefs.put(Prefs.THEME, "custom");
                            d.dismiss();
                            build();
                        }).show());
    }

    private void colorRow(String title, String key, int current) {
        Ui.row(list, title, String.format("#%06X", current & 0xFFFFFF), v -> {
            final EditText et = new EditText(this);
            et.setSingleLine(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            et.setText(String.format("#%06X", current & 0xFFFFFF));
            new AlertDialog.Builder(this).setTitle(title)
                    .setMessage("Hex color, like #FFFFFF. Pure black and white are sharpest on e-ink; Kaleido color panels can take any color.")
                    .setView(et)
                    .setPositiveButton("Save", (d, w) -> {
                        try {
                            Prefs.put(key, Color.parseColor(et.getText().toString().trim()) | 0xFF000000);
                            Prefs.put(Prefs.THEME, "custom");
                            build();
                        } catch (IllegalArgumentException e) {
                            toast("That isn't a valid color");
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
        });
    }

    private void appRow(String title, String key) { appRow(title, key, "Nothing"); }

    private void appRow(String title, String key, final String none) {
        String pkg = Prefs.getStr(key, "");
        AppRepo.AppEntry cur = AppRepo.find(pkg);
        Ui.row(list, title, cur == null ? none : AppRepo.displayLabel(cur), v -> {
            final List<AppRepo.AppEntry> apps = AppRepo.all();
            String[] names = new String[apps.size() + 1];
            names[0] = none;
            for (int i = 0; i < apps.size(); i++) names[i + 1] = AppRepo.displayLabel(apps.get(i));
            new AlertDialog.Builder(this).setTitle(title).setItems(names, (d, w) -> {
                Prefs.put(key, w == 0 ? "" : apps.get(w - 1).pkg);
                build();
            }).show();
        });
    }

    private void hiddenDialog() {
        final List<AppRepo.AppEntry> apps = AppRepo.all();
        final List<String> hidden = Prefs.getList(Prefs.HIDDEN);
        String[] names = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = AppRepo.displayLabel(apps.get(i));
            checked[i] = hidden.contains(apps.get(i).pkg);
        }
        new AlertDialog.Builder(this).setTitle("Hide from app list")
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("Save", (d, w) -> {
                    List<String> out = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) out.add(apps.get(i).pkg);
                    Prefs.putList(Prefs.HIDDEN, out);
                    build();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---- Niagara import ----

    private void niagaraIntro() {
        boolean installed = getPackageManager().getLaunchIntentForPackage(NIAGARA_PKG) != null;
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Import from Niagara")
                .setMessage("Niagara keeps its data private, so Minimal Launcher Proper can't read it directly. What works:\n\n"
                        + "1. In Niagara open Settings > Advanced > Backup & Restore > Create backup. It saves a .nlb file in Downloads. Choose that file here. Minimal Launcher Proper looks inside it for app names and matches them to the apps installed on this device. Niagara doesn't document the file format, so this is a best guess and you get to review the result before anything changes.\n\n"
                        + "2. Otherwise pick your home apps by hand. Minimal Launcher Proper starts you with the usual ones.\n\n"
                        + "Your Niagara theme can't be copied. The \"Niagara-style\" preset is the closest match.")
                .setPositiveButton("Choose file", (d, w) ->
                        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_NIAGARA))
                .setNegativeButton("Pick by hand", (d, w) -> startActivity(new Intent(this, FavoritesActivity.class)));
        if (installed) {
            b.setNeutralButton("Open Niagara", (d, w) -> startActivity(getPackageManager().getLaunchIntentForPackage(NIAGARA_PKG)));
        }
        b.show();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        boolean granted = false;
        for (int r : results) if (r == android.content.pm.PackageManager.PERMISSION_GRANTED) granted = true;
        if (!granted) {
            if (req == REQ_CAL) Prefs.put(Prefs.AGENDA, false);
            else if (req == REQ_CONTACTS) Prefs.put(Prefs.S_CONTACTS, false);
            else if (req == REQ_EVENTS) Prefs.put(Prefs.S_EVENTS, false);
            else if (req == REQ_FILES) Prefs.put(Prefs.S_FILES, false);
            toast("That permission was not allowed, so it stays off");
        }
        build();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (Widgets.onResult(this, req, res)) {
            build();
            return;
        }
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT_THEME) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(BackupManager.exportTheme());
                }
                toast("Theme exported");
            } else if (req == REQ_FONT) {
                byte[] font;
                try (InputStream in = getContentResolver().openInputStream(uri)) { font = BackupManager.readAll(in); }
                toast(Prefs.installFont(font) ? "Font applied" : "That file isn't a usable font");
                build();
            } else if (req == REQ_EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(BackupManager.export());
                }
                toast("Setup exported");
            } else {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bytes = BackupManager.readAll(in);
                }
                if (BackupManager.restoreNative(bytes)) {
                    toast("Setup restored");
                    build();
                } else if (req == REQ_RESTORE) {
                    toast("That isn't a Minimal Launcher Proper backup");
                } else {
                    confirmNiagara(bytes);
                }
            }
        } catch (Exception e) {
            toast("Couldn't read that file: " + e.getMessage());
        }
    }

    private void confirmNiagara(byte[] bytes) {
        final BackupManager.Found f = BackupManager.scanForeign(bytes);
        final List<String> favs = f.finalFavorites();
        if (favs.isEmpty() && f.hidden.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Nothing found")
                    .setMessage("That file doesn't name any apps installed on this device. Pick your home apps by hand instead.")
                    .setPositiveButton("Pick by hand", (d, w) -> startActivity(new Intent(this, FavoritesActivity.class)))
                    .setNegativeButton("Close", null).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : favs) {
            AppRepo.AppEntry a = AppRepo.find(p);
            if (a != null) sb.append("• ").append(AppRepo.displayLabel(a)).append('\n');
        }
        String lead = f.guessed
                ? "No list in the file was labelled as favorites, so these are every installed app it mentions, in file order. Check the order afterwards.\n\n"
                : "Home apps found:\n";
        String hid = f.hidden.isEmpty() ? "" : "\n" + f.hidden.size() + " hidden app(s) will also be hidden.";
        new AlertDialog.Builder(this).setTitle("Apply to Minimal Launcher Proper?")
                .setMessage(lead + sb + hid)
                .setPositiveButton("Apply", (d, w) -> {
                    BackupManager.apply(favs, f.hidden);
                    toast("Imported " + favs.size() + " apps");
                    build();
                })
                .setNegativeButton("Cancel", null).show();
    }
}
