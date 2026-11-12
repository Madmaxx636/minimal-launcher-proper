package app.inkwell.launcher;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.widget.Toast;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeActivity extends Activity {
    private FrameLayout root;
    private AppListView home, drawerList;
    private LinearLayout drawer;
    private EditText search;
    private AlphaScrubber scrubber;
    /** One music bar per app that has music going, stacked in one place. */
    private FrameLayout mediaBars;
    private MediaBar shownBar;
    private String barsKey = "";
    private boolean offering;
    private int barIndex;
    /** Apps whose music bar was closed with the X, and whether they were playing when it was closed. */
    private final java.util.Map<String, Boolean> closedBars = new java.util.HashMap<>();
    private NotifPanel panel;
    private PopupPanel popup;
    private LinearLayout homeLayer, mediaBox, widgetTop, widgetBottom;
    /** Which apps' widgets are on show because they are playing, and where the box sits. */
    private String mediaKey = "", mediaPlaced = "", barPlaced = "";
    private ClockView clockView;
    private AgendaView agendaView;
    private AlphaScrubber edgeScrubber;
    private ButtonView quickButton;
    /** Rows from the optional device-wide sources for the text in the search box, and which text they belong to. */
    private List<AppRepo.AppEntry> extra = new ArrayList<>();
    private String extraQuery = "";
    private int searchGen;
    private int transitions;
    private boolean ticking, resumedNow, leftHome;
    /** When the screen last turned off and when this screen last stopped: close together means the screen woke us, not an app. */
    private long screenOffAt, stoppedAt;
    private int minutesOnHome;
    private String pendingIconPkg;
    private static final int REQ_ICON = 201;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateHeader();
            int every = Prefs.getInt(Prefs.FLASH_MINUTES, 0);
            if (every > 0 && resumedNow && drawer.getVisibility() != View.VISIBLE && ++minutesOnHome % every == 0) Eink.flash(HomeActivity.this);
        }
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { screenOffAt = SystemClock.elapsedRealtime(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            AppRepo.reload();
            refreshData();
        }
    };

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        firstRunSetup();

        root = new FrameLayout(this);
        root.setFitsSystemWindows(true);

        home = new AppListView(this);
        home.setListener(new AppListView.Listener() {
            @Override public void onClick(AppRepo.AppEntry a) { open(a); }
            @Override public void onLongClick(AppRepo.AppEntry a) { showAppMenu(a, true); }
            @Override public void onSwipe(int dir) { homeSwipe(dir); }
            @Override public void onDoubleTapEmpty() { runAction(Prefs.getStr(Prefs.G_DOUBLE, "lock")); }
            @Override public void onLongPressEmpty() { showHomeMenu(); }
            @Override public boolean onRowSwipeRight(AppRepo.AppEntry a) { showPopup(a); return true; }
            @Override public boolean onRowSwipeLeft(AppRepo.AppEntry a) {
                // Swipe left on an app with notifications clears them.
                if (AppRepo.badgeCount(a) == 0) return false;
                if (a.folder) for (String p : Folders.apps(Folders.idOf(a.pkg))) NotifService.dismissAll(p);
                else NotifService.dismissAll(a.pkg);
                return true;
            }
        });
        homeLayer = new LinearLayout(this);
        homeLayer.setOrientation(LinearLayout.VERTICAL);
        widgetTop = new LinearLayout(this);
        widgetTop.setOrientation(LinearLayout.VERTICAL);
        homeLayer.addView(widgetTop, new LinearLayout.LayoutParams(-1, -2));
        clockView = new ClockView(this);
        clockView.setActions(this::showHomeMenu, this::openClockApp, this::openCalendar);
        homeLayer.addView(clockView, new LinearLayout.LayoutParams(-1, -2));
        agendaView = new AgendaView(this);
        agendaView.setOnTap(this::openCalendar);
        homeLayer.addView(agendaView, new LinearLayout.LayoutParams(-1, -2));
        widgetBottom = new LinearLayout(this);
        widgetBottom.setOrientation(LinearLayout.VERTICAL);
        homeLayer.addView(widgetBottom, new LinearLayout.LayoutParams(-1, -2));
        homeLayer.addView(home, new LinearLayout.LayoutParams(-1, 0, 1f));
        mediaBars = new FrameLayout(this);
        // the button sits above the music bar, so it has to move whenever the bar's height changes
        mediaBars.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop && quickButton != null) quickButton.post(this::placeButton);
        });
        homeLayer.addView(mediaBars, new LinearLayout.LayoutParams(-1, -2));
        mediaBox = new LinearLayout(this);
        mediaBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(homeLayer, new FrameLayout.LayoutParams(-1, -1));
        placeMediaBar();
        placeMediaBox();

        // Invisible strip down the edge: drag it and the app list opens at that letter, like Niagara.
        edgeScrubber = new AlphaScrubber(this);
        edgeScrubber.setEdge(true);
        edgeScrubber.setListener(new AlphaScrubber.Listener() {
            @Override public void onLetter(char c) {
                if (drawer.getVisibility() != View.VISIBLE) showDrawer();
                drawerList.jumpToLetter(c);
                drawerList.setBubble(c);
            }
            @Override public void onRelease() { drawerList.setBubble((char) 0); }
        });
        root.addView(edgeScrubber, new FrameLayout.LayoutParams((int) Prefs.dp(this, 28), -1, Gravity.END));

        quickButton = new ButtonView(this);
        quickButton.setActions(() -> {
            // a button with nothing set up yet opens its setup instead of doing nothing
            if (Prefs.getStr(Prefs.BTN, "").isEmpty()) editButton();
            else runTarget(Prefs.getStr(Prefs.BTN, ""));
        }, this::editButton,
                () -> runTarget(Prefs.getStr(Prefs.BTN_UP, "")));
        root.addView(quickButton, new FrameLayout.LayoutParams(ButtonView.sizePx(this), ButtonView.sizePx(this), Gravity.BOTTOM | Gravity.START));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) {
                startExtraSearch();
                refreshDrawer(true);
            }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            AppRepo.AppEntry pick = drawerList.chosen(); // the row picked with the arrow keys, else the first
            if (pick != null) { hideKeyboard(); open(pick); }
            return true;
        });
        search.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (code) {
                case KeyEvent.KEYCODE_ESCAPE: hideDrawer(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: drawerList.moveSelection(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP: drawerList.moveSelection(-1); return true;
                default: return false;
            }
        });
        drawer.addView(search, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout body = new FrameLayout(this);
        drawerList = new AppListView(this);
        drawerList.setListener(new AppListView.Listener() {
            @Override public void onClick(AppRepo.AppEntry a) { hideKeyboard(); open(a); }
            @Override public void onLongClick(AppRepo.AppEntry a) { showAppMenu(a, false); }
            @Override public void onSwipe(int dir) { if (dir == AppListView.DOWN || dir == AppListView.RIGHT) hideDrawer(); }
            @Override public void onLongPressEmpty() { openSettings(); }
        });
        int scrubW = (int) Prefs.dp(this, 28);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.rightMargin = scrubW;
        body.addView(drawerList, lp);
        scrubber = new AlphaScrubber(this);
        scrubber.setDoubleTap(() -> { if (LockService.isRunning()) LockService.lock(); }); // double-tap the alphabet to sleep
        scrubber.setListener(new AlphaScrubber.Listener() {
            @Override public void onLetter(char c) { drawerList.jumpToLetter(c); drawerList.setBubble(c); }
            @Override public void onRelease() { drawerList.setBubble((char) 0); }
        });
        body.addView(scrubber, new FrameLayout.LayoutParams(scrubW, -1, Gravity.END));
        drawer.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        panel = new NotifPanel(this, this::hidePanel);
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));

        popup = new PopupPanel(this, new PopupPanel.Host() {
            @Override public void closePopup() { hidePopup(); }
            @Override public void launch(AppRepo.AppEntry a) { hidePopup(); open(a); }
        });
        popup.setVisibility(View.GONE);
        root.addView(popup, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Widgets.start();
    }

    @Override
    protected void onStop() {
        Widgets.stop();
        leftHome = true;
        stoppedAt = SystemClock.elapsedRealtime();
        super.onStop();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ICON) {
            if (res == RESULT_OK && data != null && data.getData() != null && pendingIconPkg != null) saveIcon(data.getData());
            return;
        }
        if (Widgets.onResult(this, req, res)) {
            refreshWidgets();
            if (popup.getVisibility() == View.VISIBLE) popup.rebuild();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        unregisterReceiver(screenOff);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumedNow = true;
        NotifService.requestRebindIfNeeded(this);
        Widgets.onChanged = this::widgetsChanged;
        applyTheme();
        placeMediaBar();
        placeMediaBox();
        refreshData();
        if (popup.getVisibility() == View.VISIBLE) popup.rebuild(); // a folder may have been edited
        // Refresh the panel when coming back from another app, or when the screen woke up, if asked to.
        boolean wokeUp = screenOffAt > 0 && Math.abs(stoppedAt - screenOffAt) < 2000;
        boolean fromApp = leftHome && !wokeUp;
        if ((fromApp && Prefs.getBool(Prefs.FLASH_RETURN, false)) || (wokeUp && Prefs.getBool(Prefs.FLASH_SCREEN, false))) {
            root.postDelayed(() -> Eink.flash(HomeActivity.this), 250);
        }
        leftHome = false;
        screenOffAt = 0;
        if (!ticking) {
            registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
            ticking = true;
        }
        NotifService.onChange = () -> {
            home.invalidate();
            drawerList.invalidate();
            refreshMediaBars();
            BuiltinWidgets.refreshAll();
            refreshMediaWidgets(false);
            quickButton.post(HomeActivity.this::placeButton);
            if (panel.getVisibility() == View.VISIBLE) panel.rebuild();
            if (popup.getVisibility() == View.VISIBLE) popup.rebuild();
        };
        if (!Prefs.getBool("welcomed", false)) {
            Prefs.put("welcomed", true);
            welcome();
        }
    }

    @Override
    protected void onPause() {
        resumedNow = false;
        NotifService.onChange = null;
        if (ticking) {
            unregisterReceiver(tick);
            ticking = false;
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Home was pressed. Android marks "brought back from another app" with a flag; without it, and with the
        // window in front, we were already on the home screen. This is the same test other launchers use.
        boolean alreadyOnHome = (resumedNow || hasWindowFocus()) && (i.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0;
        boolean closedSomething = popup.getVisibility() == View.VISIBLE || panel.getVisibility() == View.VISIBLE
                || drawer.getVisibility() == View.VISIBLE;
        if (popup.getVisibility() == View.VISIBLE) hidePopup();
        if (panel.getVisibility() == View.VISIBLE) hidePanel();
        if (drawer.getVisibility() == View.VISIBLE) hideDrawer();
        if (closedSomething) return; // Home closes what is open, like Back does
        if (!alreadyOnHome) {
            home.pageBy(-100); // back from another app: start at the top of the list
            return;
        }
        switch (Prefs.getStr(Prefs.HOME_ACTION, "search")) {
            case "search": showSearch(); break;
            case "drawer": showDrawer(); break;
            case "top": home.pageBy(-100); break;
            default: break;
        }
    }

    private void firstRunSetup() {
        if (!Prefs.getBool(Prefs.SETUP_DONE, false)) {
            if (!Prefs.has(Prefs.FAVS)) Prefs.putList(Prefs.FAVS, AppRepo.guessStarterFavorites());
            Prefs.put(Prefs.SETUP_DONE, true);
        }
    }

    private void welcome() {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to Minimal Launcher Proper")
                .setMessage(getString(R.string.tagline) + "\n\nSwipe up to search. Drag along the right edge for all apps. Swipe right on an app for its pop-up. "
                        + "Long-press the clock for widgets and settings. Already set up in Niagara? You can bring your favorites over.")
                .setPositiveButton("Import from Niagara", (d, w) ->
                        startActivity(new Intent(this, SettingsActivity.class).putExtra("flow", "niagara")))
                .setNegativeButton("Later", null)
                .show();
    }

    // ---- theme & data ----

    private void applyTheme() {
        int bg = Prefs.bg(), fg = Prefs.fg();
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Color.luminance(bg) > 0.5f) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (Prefs.getBool(Prefs.HIDE_STATUS, false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        home.applyStyle();
        home.setValign(Prefs.getStr(Prefs.VALIGN, "top"));
        drawerList.applyStyle();
        scrubber.applyStyle();
        placeEdges();
        drawer.setBackgroundColor(bg);
        placeButton();

        search.setTextColor(fg);
        search.setHintTextColor((fg & 0x00FFFFFF) | 0x88000000);
        search.setTypeface(Prefs.typeface());
        search.setTextSize(18 * Prefs.scalePct() / 100f);
        GradientDrawable box = new GradientDrawable();
        box.setColor(bg);
        box.setStroke((int) Prefs.dp(this, 2), fg);
        search.setBackground(box);
        int p = (int) Prefs.dp(this, 12);
        search.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) search.getLayoutParams();
        int m = (int) Prefs.dp(this, 16);
        lp.setMargins(m, (int) Prefs.dp(this, 8), m, (int) Prefs.dp(this, 8));
    }

    /** Left-hand mode puts the alphabet and its edge strip on the left, and Move clock sets the top spacing. */
    private void placeEdges() {
        boolean left = Prefs.getBool(Prefs.LEFT_HAND, false);
        int w = (int) Prefs.dp(this, 28);
        FrameLayout.LayoutParams e = (FrameLayout.LayoutParams) edgeScrubber.getLayoutParams();
        e.gravity = left ? Gravity.START : Gravity.END;
        edgeScrubber.setLayoutParams(e);
        FrameLayout.LayoutParams s = (FrameLayout.LayoutParams) scrubber.getLayoutParams();
        s.gravity = left ? Gravity.START : Gravity.END;
        scrubber.setLayoutParams(s);
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) drawerList.getLayoutParams();
        l.leftMargin = left ? w : 0;
        l.rightMargin = left ? 0 : w;
        drawerList.setLayoutParams(l);
        LinearLayout.LayoutParams t = (LinearLayout.LayoutParams) widgetTop.getLayoutParams();
        t.topMargin = (int) Prefs.dp(this, Prefs.getInt(Prefs.CLOCK_OFFSET, 0));
        widgetTop.setLayoutParams(t);
    }

    /** The quick button sits in the bottom corner away from the alphabet, above the music bar when it is showing. */
    private void placeButton() {
        boolean on = Prefs.getBool(Prefs.BTN_ON, false);
        quickButton.setVisibility(on ? View.VISIBLE : View.GONE);
        quickButton.setTarget(Prefs.getStr(Prefs.BTN, ""));
        int size = ButtonView.sizePx(this), m = (int) Prefs.dp(this, 16);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) quickButton.getLayoutParams();
        lp.width = size;
        lp.height = size;
        String pos = Prefs.getStr(Prefs.BTN_POS, "auto");
        int side;
        if (pos.equals("start")) side = Gravity.START;
        else if (pos.equals("end")) side = Gravity.END;
        else if (pos.equals("center")) side = Gravity.CENTER_HORIZONTAL;
        else side = Prefs.getBool(Prefs.LEFT_HAND, false) ? Gravity.END : Gravity.START; // opposite the alphabet
        lp.gravity = Gravity.BOTTOM | side;
        lp.leftMargin = m;
        lp.rightMargin = m;
        boolean barAtBottom = Prefs.getStr(Prefs.MEDIA_BAR_POS, "bottom").equals("bottom");
        lp.bottomMargin = m + (barAtBottom && mediaBars.getVisibility() == View.VISIBLE ? mediaBars.getHeight() : 0);
        quickButton.setLayoutParams(lp);
    }

    private void runTarget(String t) {
        if (t == null || t.isEmpty()) return;
        if (ButtonTargets.isAction(t)) {
            switch (ButtonTargets.actionOf(t)) {
                case "search": showSearch(); break;
                case "drawer": showDrawer(); break;
                case "panel": showPanel(null); break;
                case "lock": runAction("lock"); break;
                case "flash": Eink.flash(this); break;
                default: break;
            }
        } else if (ButtonTargets.isShortcut(t)) {
            if (!ButtonTargets.startShortcut(this, t)) Toast.makeText(this, "Can't open that shortcut", Toast.LENGTH_SHORT).show();
        } else if (Folders.isFolder(t)) {
            String id = Folders.idOf(t);
            if (Folders.exists(id)) showPopup(new AppRepo.AppEntry(t, "", Folders.nameOf(id), true));
        } else {
            AppRepo.AppEntry a = AppRepo.find(t);
            if (a != null) open(a);
        }
    }

    private void editButton() {
        final String[] pos = {"auto", "start", "center", "end"};
        final String[] posNames = {"Automatic (opposite the alphabet)", "Left", "Center", "Right"};
        final String[] names = {
                "Tap:  " + ButtonTargets.label(Prefs.getStr(Prefs.BTN, "")),
                "Swipe up:  " + ButtonTargets.label(Prefs.getStr(Prefs.BTN_UP, "")),
                "Position",
                "Turn the button off"};
        new AlertDialog.Builder(this).setTitle("Quick button")
                .setItems(names, (d, w) -> {
                    if (w == 0) ButtonTargets.pick(this, "Tap action", Prefs.BTN, this::placeButton);
                    else if (w == 1) ButtonTargets.pick(this, "Swipe-up action", Prefs.BTN_UP, this::placeButton);
                    else if (w == 2) new AlertDialog.Builder(this).setTitle("Button position")
                            .setItems(posNames, (d2, p) -> { Prefs.put(Prefs.BTN_POS, pos[p]); placeButton(); }).show();
                    else { Prefs.put(Prefs.BTN_ON, false); placeButton(); }
                }).show();
    }

    /** From the long-press menu: turns the button on if it is off, then opens its options. */
    private void quickButtonMenu() {
        if (!Prefs.getBool(Prefs.BTN_ON, false)) {
            Prefs.put(Prefs.BTN_ON, true);
            placeButton();
        }
        editButton();
    }



    private void saveIcon(Uri uri) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) { android.graphics.BitmapFactory.decodeStream(in, null, o); }
            o.inSampleSize = Math.max(1, Math.min(o.outWidth, o.outHeight) / 512);
            o.inJustDecodeBounds = false;
            android.graphics.Bitmap bmp;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) { bmp = android.graphics.BitmapFactory.decodeStream(in, null, o); }
            if (bmp == null) throw new java.io.IOException();
            AppRepo.setCustomIcon(pendingIconPkg, bmp);
            refreshData();
            Toast.makeText(this, Prefs.getBool(Prefs.ICONS, false) ? "Icon changed" : "Icon saved. Turn on app icons in settings to see it.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshData() {
        home.setItems(AppRepo.favorites(), false);
        updateHeader();
        refreshMediaBars();
        quickButton.post(this::placeButton);
        refreshWidgets();
        refreshDrawer(false);
    }

    private void refreshDrawer(boolean reset) {
        List<AppRepo.AppEntry> list = filtered();
        drawerList.setItems(list, reset);
        Set<Character> present = new HashSet<>();
        for (AppRepo.AppEntry a : list) present.add(AppRepo.letterOf(AppRepo.displayLabel(a)));
        scrubber.setPresent(present);
        String letters = AlphaScrubber.lettersFor(present);
        scrubber.setLetters(letters);
        edgeScrubber.setLetters(letters);
        scrubber.setVisibility(search.getText().length() > 0 ? View.INVISIBLE : View.VISIBLE);
    }

    private List<AppRepo.AppEntry> filtered() {
        String raw = search.getText().toString().trim();
        String q = AppRepo.sortKey(raw);
        List<AppRepo.AppEntry> out = new ArrayList<>();
        List<AppRepo.AppEntry> folders = Folders.listEntries();
        if (q.isEmpty()) {
            out.addAll(AppRepo.visible());
            out.addAll(folders); // folders set to "Show in app list" sit among the apps
            java.util.Collections.sort(out, (x, y) -> AppRepo.sortKey(AppRepo.displayLabel(x)).compareTo(AppRepo.sortKey(AppRepo.displayLabel(y))));
            out.add(settingsEntry());
            return out;
        }
        boolean web = Prefs.getBool(Prefs.S_WEB, true);
        String calc = Calc.eval(raw);
        if (calc != null) out.add(new AppRepo.AppEntry("calc:" + calc, "", "= " + calc));
        String link = web ? WebSearch.asLink(raw) : null;
        if (link != null) out.add(new AppRepo.AppEntry("url:" + link, "", "Open · " + link.replaceFirst("^https?://", "")));
        // Niagara keeps hidden apps searchable unless you turn that off.
        List<AppRepo.AppEntry> pool = new ArrayList<>(Prefs.getBool(Prefs.SEARCH_HIDDEN, true) ? AppRepo.all() : AppRepo.visible());
        pool.addAll(folders);
        List<AppRepo.AppEntry> starts = new ArrayList<>(), contains = new ArrayList<>();
        for (AppRepo.AppEntry a : pool) {
            String k = AppRepo.sortKey(AppRepo.displayLabel(a));
            if (k.startsWith(q)) starts.add(a);
            else if (k.contains(q)) contains.add(a);
        }
        out.addAll(starts);
        out.addAll(contains);
        if ("launcher settings".contains(q)) out.add(settingsEntry());
        if (raw.equals(extraQuery)) out.addAll(extra); // settings screens, shortcuts, contacts, events, files
        if (web) out.add(new AppRepo.AppEntry("web:" + raw, "", "Search the web for \"" + raw + "\""));
        return out;
    }

    /** Looks beyond the apps on a background thread; the rows are added when they arrive. */
    private void startExtraSearch() {
        final String raw = search.getText().toString().trim();
        final int gen = ++searchGen;
        if (raw.length() < 2) {
            extra = new ArrayList<>();
            extraQuery = "";
            return;
        }
        DeviceSearch.run(this, raw, () -> gen != searchGen, results -> runOnUiThread(() -> {
            if (gen != searchGen) return;
            extra = results;
            extraQuery = raw;
            refreshDrawer(false);
        }));
    }

    private AppRepo.AppEntry settingsEntry() {
        return new AppRepo.AppEntry("inkwell:settings", "", "Launcher settings");
    }

    private void updateHeader() {
        if (home == null) return;
        int cw = Prefs.getInt(Prefs.CLOCK_WIDGET, -1);
        boolean widgetIsClock = cw >= 0 && (Widgets.ids(Widgets.HOME).contains(cw) || Widgets.ids(Widgets.HOME_BELOW).contains(cw));
        String batt = null;
        if (Prefs.getBool(Prefs.BATTERY, true)) {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (lvl >= 0) batt = lvl * 100 / sc + "%";
            }
        }
        agendaView.refresh();
        BuiltinWidgets.refreshAll();
        clockView.setState(!widgetIsClock && Prefs.getBool(Prefs.CLOCK, true), System.currentTimeMillis(), batt);
    }

    private void open(AppRepo.AppEntry a) {
        if (a.folder) { showPopup(a); return; }
        if (a.pkg.equals("inkwell:settings")) { hideDrawer(); openSettings(); return; }
        if (a.pkg.startsWith("calc:")) {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("result", a.pkg.substring(5)));
            Toast.makeText(this, "Copied " + a.pkg.substring(5), Toast.LENGTH_SHORT).show();
            return;
        }
        if (openSpecial(a)) return;
        if (breakFor(a)) return;
        launchApp(a);
    }

    /**
     * Opens a search result that is not an app: a web search, a link, a contact, an event, a settings
     * screen, an app shortcut or a media file. Returns false for ordinary apps.
     */
    private boolean openSpecial(AppRepo.AppEntry a) {
        int colon = a.pkg.indexOf(':');
        if (colon < 0) return false;
        String kind = a.pkg.substring(0, colon), value = a.pkg.substring(colon + 1);
        Intent go;
        try {
            switch (kind) {
                case "web": go = new Intent(Intent.ACTION_VIEW, Uri.parse(WebSearch.urlFor(value))); break;
                case "url":
                case "contact": go = new Intent(Intent.ACTION_VIEW, Uri.parse(value)); break;
                case "event": go = new Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.parseLong(value))); break;
                case "setting": go = new Intent(value); break;
                case "file": {
                    Uri u = Uri.parse(value);
                    go = new Intent(Intent.ACTION_VIEW).setDataAndType(u, getContentResolver().getType(u)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    break;
                }
                case "shortcut":
                    if (!ButtonTargets.startShortcut(this, a.pkg)) Toast.makeText(this, "Can't open that shortcut", Toast.LENGTH_SHORT).show();
                    closeSearch();
                    return true;
                default: return false;
            }
            startActivity(go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show();
        }
        closeSearch();
        return true;
    }

    private void closeSearch() {
        if (drawer.getVisibility() == View.VISIBLE) {
            hideKeyboard();
            drawer.setVisibility(View.GONE);
            search.setText("");
        }
    }

    /** Usage Breaker: a gentle pause before opening an app you've used past your daily limit. */
    private boolean breakFor(final AppRepo.AppEntry a) {
        if (!Prefs.getBool(Prefs.BREAK_ON, false) || !Prefs.getList(Prefs.BREAK_APPS).contains(a.pkg)) return false;
        long mins = UsageGuard.minutesToday(this, a.pkg);
        int limit = Prefs.getInt(Prefs.BREAK_MIN, 30);
        if (mins < 0 || mins < limit) return false;
        new AlertDialog.Builder(this).setTitle("Take a moment")
                .setMessage("You've spent " + mins + " minutes in " + AppRepo.displayLabel(a) + " today. Your limit is " + limit + ".")
                .setPositiveButton("Open anyway", (d, w) -> launchApp(a))
                .setNegativeButton("Not now", null).show();
        return true;
    }

    private void launchApp(AppRepo.AppEntry a) {
        if (drawer.getVisibility() == View.VISIBLE) {
            drawer.setVisibility(View.GONE);
            search.setText("");
        }
        AppRepo.launch(this, a);
        overridePendingTransition(0, 0);
    }

    private void showSearch() {
        showDrawer();
        search.requestFocus();
        search.postDelayed(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT), 150);
    }

    private void openClockApp() {
        String pkg = Prefs.getStr(Prefs.CLOCK_APP, "");
        if (!pkg.isEmpty() && AppRepo.find(pkg) != null) { AppRepo.launchPackage(this, pkg); return; }
        try { startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) { }
    }

    private void openCalendar() {
        String pkg = Prefs.getStr(Prefs.CAL_APP, "");
        if (!pkg.isEmpty() && AppRepo.find(pkg) != null) { AppRepo.launchPackage(this, pkg); return; }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    private void showDrawer() {
        drawer.setVisibility(View.VISIBLE);
        drawerList.setItems(filtered(), true);
        maybeFlash();
    }

    private void showPopup(AppRepo.AppEntry a) {
        popup.show(a);
        popup.setVisibility(View.VISIBLE);
    }

    private void hidePopup() {
        popup.clear();
        popup.setVisibility(View.GONE);
    }


    private void widgetsChanged() {
        refreshWidgets();
        if (popup.getVisibility() == View.VISIBLE) popup.rebuild();
    }

    /** The music bar (previous, play, next) at the bottom, or above the clock, below it, or above the app list. */
    private void placeMediaBar() {
        String pos = Prefs.getStr(Prefs.MEDIA_BAR_POS, "bottom");
        if (pos.equals(barPlaced) && mediaBars.getParent() == homeLayer) return;
        barPlaced = pos;
        homeLayer.removeView(mediaBars);
        int at;
        if (pos.equals("clock")) at = homeLayer.indexOfChild(clockView);
        else if (pos.equals("below")) at = homeLayer.indexOfChild(agendaView) + 1;
        else if (pos.equals("list")) at = homeLayer.indexOfChild(home);
        else at = homeLayer.getChildCount();
        homeLayer.addView(mediaBars, at, new LinearLayout.LayoutParams(-1, -2));
        if (shownBar != null) shownBar.setLineOnTop(pos.equals("bottom"));
    }

    /**
     * One music bar. When several apps have music going, swipe sideways to move between them (dots show
     * where you are). A swipe away hides the one on screen until that app starts playing again.
     */
    private void refreshMediaBars() {
        List<MediaController> all = NotifService.activeSessions(8);
        if (Prefs.getBool(Prefs.MEDIA, true)) for (MediaController mc : all) noteMediaApp(mc.getPackageName());
        for (java.util.Iterator<java.util.Map.Entry<String, Boolean>> it = closedBars.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<String, Boolean> closed = it.next();
            MediaController still = null;
            for (MediaController mc : all) if (mc.getPackageName().equals(closed.getKey())) still = mc;
            if (still == null || (!closed.getValue() && barPlaying(still))) it.remove();
        }
        List<MediaController> active = new ArrayList<>();
        if (Prefs.getBool(Prefs.MEDIA, true)) {
            List<String> asWidget = widgetApps();
            for (MediaController mc : all) {
                String pkg = mc.getPackageName();
                if (!closedBars.containsKey(pkg) && !asWidget.contains(pkg)) active.add(mc);
            }
        }
        if (barIndex >= active.size()) barIndex = 0;
        StringBuilder key = new StringBuilder().append(barIndex).append(':');
        for (MediaController mc : active) key.append(mc.getPackageName()).append(',');
        if (!key.toString().equals(barsKey)) {
            barsKey = key.toString();
            mediaBars.removeAllViews();
            shownBar = null;
            if (!active.isEmpty()) {
                final MediaController mc = active.get(barIndex);
                final String pkg = mc.getPackageName();
                final int count = active.size();
                shownBar = new MediaBar(this);
                shownBar.setController(mc);
                shownBar.setLineOnTop(Prefs.getStr(Prefs.MEDIA_BAR_POS, "bottom").equals("bottom"));
                shownBar.setOnLongPress(() -> {
                    AppRepo.AppEntry app = AppRepo.find(pkg);
                    if (app != null) showPopup(app);
                });
                StackView stack = new StackView(this);
                stack.setContent(shownBar, new LinearLayout.LayoutParams(-1, -2));
                if (count > 1) stack.setPosition(barIndex, count);
                final Runnable dismiss = () -> {
                    closedBars.put(pkg, barPlaying(mc));
                    refreshMediaBars();
                    quickButton.post(this::placeButton);
                };
                // Swipe sideways to flip between players (with only one, that throws it away); swipe up or down to close.
                stack.setOnSwipe(dir -> {
                    if (count == 1) {
                        dismiss.run();
                        return;
                    }
                    barIndex = (barIndex + dir + count) % count;
                    refreshMediaBars();
                });
                stack.setOnDismiss(dismiss);
                mediaBars.addView(stack, new FrameLayout.LayoutParams(-1, -2));
            }
        }
        if (shownBar != null) shownBar.refresh();
        mediaBars.setVisibility(shownBar == null ? View.GONE : View.VISIBLE);
        updateFit();
    }

    /** With music controls or a media widget on home, the app list shrinks so every app still fits. */
    private void updateFit() {
        boolean media = shownBar != null || (mediaBox != null && mediaBox.getChildCount() > 0);
        home.setFitAll(media && Prefs.getBool(Prefs.FIT_MEDIA, true));
    }

    /**
     * The first time an app plays music, and it offers widgets, ask once whether to use its widget on home
     * while it plays. Every media app seen is remembered, which is also what Settings lists.
     */
    private void noteMediaApp(final String pkg) {
        List<String> seen = Prefs.getList(Prefs.MEDIA_ASKED);
        if (seen.contains(pkg)) return;
        seen.add(pkg);
        Prefs.putList(Prefs.MEDIA_ASKED, seen);
        final AppRepo.AppEntry app = AppRepo.find(pkg);
        if (offering || !resumedNow || app == null || !Prefs.getStr(Prefs.MEDIA_STYLE, "widget").equals("widget")
                || !Widgets.ids("app:" + pkg).isEmpty() || !WidgetPicker.hasWidgets(this, pkg)) return;
        offering = true;
        new AlertDialog.Builder(this).setTitle("Use " + AppRepo.displayLabel(app) + "'s widget?")
                .setMessage("It has a widget. Show it on the home screen while it plays, instead of the launcher's music controls. "
                        + "You can change this later in Settings, under Media app widgets.")
                .setPositiveButton("Choose widget", (d, w) -> WidgetPicker.showFor(this, "app:" + pkg, pkg, this::refreshWidgets))
                .setNegativeButton("Not now", null)
                .setOnDismissListener(d -> offering = false).show();
    }

    private static boolean barPlaying(MediaController mc) {
        android.media.session.PlaybackState s = mc.getPlaybackState();
        return s != null && (s.getState() == android.media.session.PlaybackState.STATE_PLAYING
                || s.getState() == android.media.session.PlaybackState.STATE_BUFFERING);
    }

    /** Apps whose own widget stands in for the launcher's music bar while they play (if that style is chosen). */
    private List<String> widgetApps() {
        List<String> out = new ArrayList<>();
        if (!Prefs.getStr(Prefs.MEDIA_STYLE, "widget").equals("widget")) return out;
        for (MediaController mc : NotifService.activeSessions(8)) {
            String pkg = mc.getPackageName();
            if (!closedBars.containsKey(pkg) && !Widgets.ids("app:" + pkg).isEmpty()) out.add(pkg);
        }
        return out;
    }

    /** Puts the "while it plays" widgets above the clock, below it, or above the app list, as chosen. */
    private void placeMediaBox() {
        String pos = Prefs.getStr(Prefs.MEDIA_HOME_POS, "list");
        if (pos.equals(mediaPlaced) && mediaBox.getParent() == homeLayer) return;
        mediaPlaced = pos;
        homeLayer.removeView(mediaBox);
        int at;
        if (pos.equals("clock")) at = homeLayer.indexOfChild(clockView);
        else if (pos.equals("below")) at = homeLayer.indexOfChild(agendaView) + 1;
        else at = homeLayer.indexOfChild(home);
        homeLayer.addView(mediaBox, at, new LinearLayout.LayoutParams(-1, -2));
    }

    /**
     * An app's pop-up widgets appear on home while that app has media playing or paused, if you turned
     * that on for it. force redraws them even when the set of playing apps has not changed.
     */
    private void refreshMediaWidgets(boolean force) {
        List<String> active = widgetApps();
        StringBuilder key = new StringBuilder();
        for (String pkg : active) key.append(pkg).append(',');
        if (!force && key.toString().equals(mediaKey)) return;
        mediaKey = key.toString();
        mediaBox.removeAllViews();
        int w = getResources().getDisplayMetrics().widthPixels;
        for (final String pkg : active) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            Widgets.fill(this, col, "app:" + pkg, w, () -> refreshMediaWidgets(true));
            // swipe up or down to send it away until that app plays again
            StackView away = new StackView(this);
            away.setContent(col, new LinearLayout.LayoutParams(-1, -2));
            away.setOnDismiss(() -> {
                for (MediaController mc : NotifService.activeSessions(8)) {
                    if (mc.getPackageName().equals(pkg)) closedBars.put(pkg, barPlaying(mc));
                }
                refreshMediaBars();
                refreshMediaWidgets(true);
                quickButton.post(this::placeButton);
            });
            mediaBox.addView(away, new LinearLayout.LayoutParams(-1, -2));
        }
        updateFit();
    }

    private void widgetsAndClockChanged() {
        widgetsChanged();
        updateHeader();
    }

    private void refreshWidgets() {
        fillWidgets(widgetTop, Widgets.HOME);
        fillWidgets(widgetBottom, Widgets.HOME_BELOW);
        refreshMediaWidgets(true);
    }

    private void fillWidgets(LinearLayout box, String slot) {
        Widgets.fill(this, box, slot, getResources().getDisplayMetrics().widthPixels, this::refreshWidgets);
    }

    private void pickClockStyle() {
        int cur = Math.max(0, java.util.Arrays.asList(ClockView.STYLE_IDS).indexOf(Prefs.getStr(Prefs.CLOCK_STYLE, "classic")));
        new AlertDialog.Builder(this).setTitle("Clock style")
                .setSingleChoiceItems(ClockView.STYLE_NAMES, cur, (d, w) -> {
                    Prefs.put(Prefs.CLOCK_STYLE, ClockView.STYLE_IDS[w]);
                    d.dismiss();
                    updateHeader();
                }).show();
    }

    private void showHomeMenu() {
        final List<String> names = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        names.add("Launcher settings");
        acts.add(this::openSettings);
        names.add("Clock style");
        acts.add(this::pickClockStyle);
        names.add("Reorder widgets");
        acts.add(() -> startActivity(new Intent(this, WidgetOrderActivity.class)));
        names.add("Quick button");
        acts.add(this::quickButtonMenu);
        names.add("Move clock");
        acts.add(() -> Ui.slider(this, "Move clock", 0, 400, 4, " dp", Prefs.getInt(Prefs.CLOCK_OFFSET, 0), 0,
                v -> { Prefs.put(Prefs.CLOCK_OFFSET, v); placeEdges(); }));
        names.add("Add widget above the clock");
        acts.add(() -> WidgetPicker.show(this, Widgets.HOME, this::widgetsChanged));
        names.add("Add widget below the clock");
        acts.add(() -> WidgetPicker.show(this, Widgets.HOME_BELOW, this::widgetsChanged));
        for (final String slot : new String[]{Widgets.HOME, Widgets.HOME_BELOW}) {
            for (final int id : Widgets.ids(slot)) {
                names.add("Edit \"" + Widgets.label(id) + "\"");
                acts.add(() -> WidgetMenu.show(this, slot, id, this::widgetsAndClockChanged));
            }
        }
        names.add("Clear ghosting");
        acts.add(() -> Eink.flash(this));
        new AlertDialog.Builder(this)
                .setItems(names.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .show();
    }

    private void showPanel(String pkg) {
        if (!NotifService.isEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Turn on notification access")
                    .setMessage("Minimal Launcher Proper needs notification access to show notifications and music controls. "
                            + "Everything stays in memory on this device and is never stored or sent anywhere.")
                    .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        panel.show(pkg);
        panel.setVisibility(View.VISIBLE);
        maybeFlash();
    }

    private void hidePanel() {
        panel.setVisibility(View.GONE);
        maybeFlash();
    }

    private void hideDrawer() {
        search.setShowSoftInputOnFocus(true);
        hideKeyboard();
        search.setText("");
        drawer.setVisibility(View.GONE);
        maybeFlash();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private void maybeFlash() {
        int every = Prefs.getInt(Prefs.FLASH_EVERY, 0);
        if (every > 0 && ++transitions % every == 0) Eink.flash(this);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(0, 0);
    }

    private void homeSwipe(int dir) {
        switch (dir) {
            case AppListView.UP: runAction(Prefs.getStr(Prefs.G_UP, "search")); break;
            case AppListView.DOWN: runAction(Prefs.getStr(Prefs.G_DOWN, "notifications")); break;
            case AppListView.LEFT: AppRepo.launchPackage(this, Prefs.getStr(Prefs.G_LEFT, "")); break;
            case AppListView.RIGHT: AppRepo.launchPackage(this, Prefs.getStr(Prefs.G_RIGHT, "")); break;
        }
    }

    private void runAction(String action) {
        switch (action) {
            case "drawer": showDrawer(); break;
            case "search": showSearch(); break;
            case "flash": Eink.flash(this); break;
            case "panel": showPanel(null); break;
            case "notifications": expandNotifications(); break;
            case "lock":
                if (!LockService.lock()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Turn on screen lock")
                            .setMessage("Android only lets an app lock the screen through an accessibility service. "
                                    + "Turn on \"Minimal Launcher Proper: double-tap to lock\" in Accessibility settings. It reads nothing on screen.")
                            .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                            .setNegativeButton("Cancel", null)
                            .show();
                }
                break;
            default: break;
        }
    }

    @SuppressLint("WrongConstant") // "statusbar" is the framework's own service name; there is no public constant
    private void expandNotifications() {
        try {
            Object sbm = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm);
        } catch (Exception e) {
            LockService.notifications();
        }
    }

    // ---- keys ----

    /**
     * Start typing on a physical keyboard and the search opens with what you typed, like Niagara.
     * Letters from the on-screen keyboard never come here, so touch use is unchanged.
     */
    private boolean typedSearch(KeyEvent ev) {
        if (!Prefs.getBool(Prefs.TYPE_SEARCH, true) || ev.getRepeatCount() > 0) return false;
        if (popup.getVisibility() == View.VISIBLE || panel.getVisibility() == View.VISIBLE || search.hasFocus()) return false;
        InputDevice d = ev.getDevice();
        if (d == null || d.isVirtual() || (d.getSources() & InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD
                || d.getKeyboardType() != InputDevice.KEYBOARD_TYPE_ALPHABETIC) return false;
        if (ev.isCtrlPressed() || ev.isAltPressed() || ev.isMetaPressed()) return false;
        int ch = ev.getUnicodeChar();
        if (ch <= 0 || Character.isISOControl(ch) || (Character.isWhitespace(ch) && search.getText().length() == 0)) return false;
        if (drawer.getVisibility() != View.VISIBLE) showDrawer();
        search.setShowSoftInputOnFocus(false); // a physical keyboard is in use, so keep the on-screen one off the list
        search.requestFocus();
        search.append(new String(Character.toChars(ch)));
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent ev) {
        if (typedSearch(ev)) return true;
        if (code == KeyEvent.KEYCODE_ESCAPE && drawer.getVisibility() == View.VISIBLE) {
            hideDrawer();
            return true;
        }
        AppListView active = drawer.getVisibility() == View.VISIBLE ? drawerList : home;
        boolean inPanel = panel.getVisibility() == View.VISIBLE;
        boolean vol = Prefs.getBool(Prefs.VOLKEYS, true);
        if (code == KeyEvent.KEYCODE_PAGE_DOWN || (vol && code == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (inPanel) panel.pageBy(1); else active.pageBy(1);
            return true;
        }
        if (code == KeyEvent.KEYCODE_PAGE_UP || (vol && code == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (inPanel) panel.pageBy(-1); else active.pageBy(-1);
            return true;
        }
        return super.onKeyDown(code, ev);
    }

    @Override
    public void onBackPressed() {
        if (popup.getVisibility() == View.VISIBLE) hidePopup();
        else if (panel.getVisibility() == View.VISIBLE) hidePanel();
        else if (drawer.getVisibility() == View.VISIBLE) hideDrawer();
    }

    // ---- per-app menu ----

    private void showAppMenu(AppRepo.AppEntry a, boolean fromHome) {
        final List<String> favs = Prefs.getList(Prefs.FAVS);
        final boolean isFav = favs.contains(a.pkg);
        final List<String> names = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (a.folder) {
            final String id = Folders.idOf(a.pkg);
            names.add("Edit folder (apps, widgets, order)");
            acts.add(() -> startActivity(new Intent(this, FolderActivity.class).putExtra("id", id)));
            names.add("Rename folder");
            acts.add(() -> renameFolder(id));
            names.add("Delete folder");
            acts.add(() -> Folders.delete(id));
            new AlertDialog.Builder(this).setTitle(a.label)
                    .setItems(names.toArray(new String[0]), (d, which) -> { acts.get(which).run(); refreshData(); })
                    .show();
            return;
        }
        if (fromHome) {
            names.add("Edit favorites");
            acts.add(() -> startActivity(new Intent(this, FavoritesActivity.class)));
        }
        int n = NotifService.count(a.pkg);
        if (n > 0) {
            names.add("Notifications (" + n + ")");
            acts.add(() -> showPanel(a.pkg));
        }
        names.add("Widgets & pop-up settings");
        acts.add(() -> startActivity(new Intent(this, AppPopupActivity.class).putExtra("pkg", a.pkg)));
        names.add(isFav ? "Remove from favorites" : "Favorite");
        acts.add(() -> {
            if (isFav) favs.remove(a.pkg); else favs.add(a.pkg);
            Prefs.putList(Prefs.FAVS, favs);
        });
        names.add("Rename");
        acts.add(() -> rename(a));
        names.add("Change icon");
        acts.add(() -> {
            final String[] opts = AppRepo.hasCustomIcon(a.pkg) ? new String[]{"Choose an image…", "Back to the original icon"} : new String[]{"Choose an image…"};
            new AlertDialog.Builder(this).setTitle("Icon for " + AppRepo.displayLabel(a)).setItems(opts, (d, w) -> {
                if (w == 0) {
                    pendingIconPkg = a.pkg;
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_ICON);
                } else {
                    AppRepo.setCustomIcon(a.pkg, null);
                    refreshData();
                }
            }).show();
        });
        names.add("Hide from app list");
        acts.add(() -> {
            List<String> hidden = Prefs.getList(Prefs.HIDDEN);
            if (!hidden.contains(a.pkg)) hidden.add(a.pkg);
            Prefs.putList(Prefs.HIDDEN, hidden);
        });
        names.add("App info");
        acts.add(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", a.pkg, null))));
        names.add("Uninstall");
        acts.add(() -> startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", a.pkg, null))));
        new AlertDialog.Builder(this)
                .setTitle(AppRepo.displayLabel(a))
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    acts.get(which).run();
                    refreshData();
                })
                .show();
    }

    private void renameFolder(final String id) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(Folders.nameOf(id));
        et.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Rename folder").setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) Folders.rename(id, s);
                    refreshData();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void rename(AppRepo.AppEntry a) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(AppRepo.displayLabel(a));
        et.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Rename " + a.label)
                .setMessage("Leave empty to restore the original name.")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String s = et.getText().toString().trim();
                    Prefs.setRename(a.pkg, s.equals(a.label) ? "" : s);
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
