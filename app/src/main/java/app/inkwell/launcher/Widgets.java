package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;

/**
 * Hosts Android app widgets. Slots: "home" (above the clock), "app:<pkg>" and "folder:<id>"
 * (inside that item's pop-up). Widget ids belong to this device, so they are not part of backups.
 */
public final class Widgets {
    /** Above the clock. */
    public static final String HOME = "home";
    /** Below the clock. */
    public static final String HOME_BELOW = "homeBelow";
    public static final int REQ_BIND = 101, REQ_CONFIG = 102, REQ_CONFIG_DIRECT = 103;
    private static final int HOST_ID = 0x1CE;
    private static final String KEY = "widgets";

    private static Context ctx;
    private static AppWidgetHost host;
    private static AppWidgetManager mgr;
    private static String pendingSlot;
    private static int pendingId = -1;

    private Widgets() {}

    public static void init(Context c) {
        ctx = c.getApplicationContext();
        mgr = AppWidgetManager.getInstance(ctx);
        host = new AppWidgetHost(ctx, HOST_ID);
    }

    public static void start() { try { host.startListening(); } catch (Exception ignored) { } }
    public static void stop() { try { host.stopListening(); } catch (Exception ignored) { } }

    // ---- slot storage ----

    private static JSONObject read() {
        try { return new JSONObject(Prefs.getStr(KEY, "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    public static List<Integer> ids(String slot) {
        List<Integer> out = new ArrayList<>();
        JSONArray a = read().optJSONArray(slot);
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optInt(i));
        return out;
    }

    private static void save(String slot, List<Integer> ids) {
        JSONObject o = read();
        try {
            if (ids.isEmpty()) o.remove(slot); else o.put(slot, new JSONArray(ids));
        } catch (JSONException ignored) { }
        Prefs.put(KEY, o.toString());
    }

    public static void remove(String slot, int id) {
        List<Integer> l = ids(slot);
        l.remove(Integer.valueOf(id));
        save(slot, l);
        if (Prefs.getInt(Prefs.CLOCK_WIDGET, -1) == id) Prefs.put(Prefs.CLOCK_WIDGET, -1);
        JSONObject h = opts();
        h.remove(String.valueOf(id));
        Prefs.put(OPTS, h.toString());
        if (id < 0) BuiltinWidgets.forget(id);
        else try { host.deleteAppWidgetId(id); } catch (Exception ignored) { }
    }

    public static void clearSlot(String slot) {
        for (int id : ids(slot)) {
            if (id < 0) BuiltinWidgets.forget(id);
            else try { host.deleteAppWidgetId(id); } catch (Exception ignored) { }
        }
        save(slot, new ArrayList<>());
    }

    public static void clearAll() {
        Iterator<String> it = read().keys();
        List<String> slots = new ArrayList<>();
        while (it.hasNext()) slots.add(it.next());
        for (String s : slots) clearSlot(s);
    }

    // ---- views ----

    public static String label(int id) {
        if (id < 0) return BuiltinWidgets.label(id);
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        return info == null ? "Widget (not connected)" : String.valueOf(info.loadLabel(ctx.getPackageManager()));
    }

    /** A live widget view, or null if the widget no longer exists (its app was removed). */
    public static View view(Context c, int id) {
        if (id < 0) return BuiltinWidgets.create(c, id);
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        if (info == null) return placeholder(c, id);
        try {
            final AppWidgetHostView v = host.createView(c, id, info);
            // Widgets lay themselves out from the size the host reports; without it many render blank.
            final float density = c.getResources().getDisplayMetrics().density;
            v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
                int w = r - l, h = b - t;
                if (w <= 0 || h <= 0 || (w == or - ol && h == ob - ot)) return;
                int wd = (int) (w / density), hd = (int) (h / density);
                v.updateAppWidgetSize(null, wd, hd, wd, hd);
            });
            return styled(c, id, v);
        } catch (Exception e) {
            return placeholder(c, id);
        }
    }

    // ---- making other apps' widgets readable on this theme ----

    public static final String[] LOOKS = {"Normal colors", "Grayscale", "Grayscale, inverted (light text becomes dark)", "Black and white, high contrast"};
    public static final String[] BACKDROPS = {"None (see-through)", "Theme background", "Black", "White"};

    public static int look(int id) { return opt(id, "m", 0); }
    public static void setLook(int id, int mode) { setOpt(id, "m", mode); }
    public static int backdrop(int id) { return opt(id, "b", 0); }
    public static void setBackdrop(int id, int mode) { setOpt(id, "b", mode); }

    /**
     * Many widgets are drawn for a wallpaper: light text on nothing. On a white e-ink screen that looks
     * blank. A solid backdrop and a colour filter make them readable.
     */
    private static View styled(Context c, int id, AppWidgetHostView v) {
        FrameLayout f = new FrameLayout(c);
        switch (backdrop(id)) {
            case 1: f.setBackgroundColor(Prefs.bg()); break;
            case 2: f.setBackgroundColor(0xFF000000); break;
            case 3: f.setBackgroundColor(0xFFFFFFFF); break;
            default: break;
        }
        ColorMatrix cm = null;
        switch (look(id)) {
            case 1:
                cm = new ColorMatrix();
                cm.setSaturation(0);
                break;
            case 2:
                cm = new ColorMatrix();
                cm.setSaturation(0);
                cm.postConcat(new ColorMatrix(new float[]{-1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0}));
                break;
            case 3: {
                float k = 4f, t = 127.5f - 127.5f * k;
                cm = new ColorMatrix(new float[]{0.299f * k, 0.587f * k, 0.114f * k, 0, t, 0.299f * k, 0.587f * k, 0.114f * k, 0, t,
                        0.299f * k, 0.587f * k, 0.114f * k, 0, t, 0, 0, 0, 1, 0});
                break;
            }
            default: break;
        }
        if (cm != null) {
            Paint p = new Paint();
            p.setColorFilter(new ColorMatrixColorFilter(cm));
            v.setLayerType(View.LAYER_TYPE_HARDWARE, p);
        }
        f.addView(v, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    /** Shown instead of nothing when Android no longer has the widget (its app was removed or disabled). */
    private static View placeholder(final Context c, final int id) {
        TextView t = Ui.text(c, "⚠  This widget isn't connected.\nAndroid no longer has it. Tap to remove it.", 14, Prefs.fg(), false);
        int p = (int) Prefs.dp(c, 12);
        t.setPadding(p, p, p, p);
        android.graphics.drawable.GradientDrawable box = new android.graphics.drawable.GradientDrawable();
        box.setColor(Prefs.bg());
        box.setStroke((int) Prefs.dp(c, 1.5f), Prefs.fg());
        t.setBackground(box);
        t.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle("Remove this widget?")
                .setMessage(describe(id))
                .setPositiveButton("Remove", (d, w) -> {
                    String slot = slotOf(id);
                    if (slot != null) remove(slot, id);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("Keep", null).show());
        return t;
    }

    /** Called after a widget is removed from outside the edit menu. HomeActivity sets it to redraw. */
    public static Runnable onChanged;

    private static String slotOf(int id) {
        Iterator<String> it = read().keys();
        while (it.hasNext()) {
            String s = it.next();
            if (ids(s).contains(id)) return s;
        }
        return null;
    }

    /** Plain-language facts about a widget, for the "About this widget" screen. */
    /** The app a widget belongs to, or null for the built-in ones and widgets Android no longer knows. */
    public static String packageOf(int id) {
        if (id < 0) return null;
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        return info == null ? null : info.provider.getPackageName();
    }

    public static String describe(int id) {
        if (id < 0) return BuiltinWidgets.label(id) + "\nBuilt into Minimal Launcher Proper, so it always works offline.";
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        if (info == null) {
            return "Android has no widget with id " + id + ".\nThe app that provides it may have been removed, disabled or "
                    + "restricted. Remove it and add it again.";
        }
        float d = ctx.getResources().getDisplayMetrics().density;
        return String.valueOf(info.loadLabel(ctx.getPackageManager())) + "\n"
                + "App: " + info.provider.getPackageName() + "\n"
                + "Provider: " + info.provider.getClassName() + "\n"
                + "Preferred size: " + (int) (info.minWidth / d) + " x " + (int) (info.minHeight / d) + " dp\n"
                + "Setup screen: " + (info.configure != null ? "yes" : "no") + "\n"
                + "Android widget id: " + id;
    }

    private static final String STACKS = "widgetStacks";
    private static final Map<String, Integer> stackAt = new HashMap<>();

    public static boolean stacked(String slot) {
        try { return new JSONObject(Prefs.getStr(STACKS, "{}")).optBoolean(slot, true); } catch (JSONException e) { return true; }
    }

    public static void setStacked(String slot, boolean on) {
        try {
            JSONObject o = new JSONObject(Prefs.getStr(STACKS, "{}"));
            o.put(slot, on);
            Prefs.put(STACKS, o.toString());
        } catch (JSONException ignored) { }
    }

    public static int stackIndex(String slot, int count) {
        Integer i = stackAt.get(slot);
        return Math.max(0, Math.min(count - 1, i == null ? 0 : i));
    }

    /** Lays the slot's widgets into box: a swipeable stack when there are two or more, else one after another. */
    public static void fill(final Context c, LinearLayout box, final String slot, int availWidthPx, final Runnable changed) {
        fill(c, box, slot, availWidthPx, changed, 100);
    }

    /** heightPct scales every widget's height (the media widgets on home have a size setting of their own). */
    public static void fill(final Context c, LinearLayout box, final String slot, int availWidthPx, final Runnable changed, final int heightPct) {
        box.removeAllViews();
        List<Integer> all = ids(slot);
        if (stacked(slot) && all.size() >= 2) {
            final int n = all.size(), cur = stackIndex(slot, n);
            int id = all.get(cur);
            View v = view(c, id);
            StackView sv = new StackView(c);
            if (v != null) sv.setContent(v, params(id, availWidthPx, heightPct));
            sv.setPosition(cur, n);
            sv.setOnSwipe(dir -> {
                int i = cur + dir;
                stackAt.put(slot, i < 0 ? n - 1 : i >= n ? 0 : i);
                changed.run();
            });
            box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (int id : all) {
                View v = view(c, id);
                if (v != null) box.addView(v, params(id, availWidthPx, heightPct));
            }
        }
    }

    private static final String OPTS = "widgetOpts";

    private static JSONObject opts() {
        try { return new JSONObject(Prefs.getStr(OPTS, "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    private static int opt(int id, String k, int def) {
        JSONObject o = opts().optJSONObject(String.valueOf(id));
        return o == null ? def : o.optInt(k, def);
    }

    private static void setOpt(int id, String k, int v) {
        JSONObject all = opts();
        try {
            JSONObject o = all.optJSONObject(String.valueOf(id));
            if (o == null) o = new JSONObject();
            o.put(k, v);
            all.put(String.valueOf(id), o);
        } catch (JSONException ignored) { }
        Prefs.put(OPTS, all.toString());
    }

    /** The height you set by hand, else the widget's own preferred height (kept between 72 and 280 dp). */
    public static int heightDp(int id) {
        int manual = opt(id, "h", -1);
        if (manual > 0) return manual;
        if (id < 0) return BuiltinWidgets.defaultHeightDp(id);
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        int dp = info == null ? 72 : (int) (info.minHeight / ctx.getResources().getDisplayMetrics().density);
        return Math.max(72, Math.min(280, dp));
    }

    public static void setHeightDp(int id, int dp) { setOpt(id, "h", dp); }
    public static int widthPct(int id) { return opt(id, "w", 100); }
    public static void setWidthPct(int id, int pct) { setOpt(id, "w", pct); }
    /** 0 left, 1 center, 2 right. */
    public static int align(int id) { return opt(id, "a", 1); }
    public static void setAlign(int id, int a) { setOpt(id, "a", a); }

    public static int heightPx(int id) { return (int) Prefs.dp(ctx, heightDp(id)); }

    /** Size, alignment and spacing for a widget laid out in a vertical column avail pixels wide. */
    public static LinearLayout.LayoutParams params(int id, int availWidthPx) {
        return params(id, availWidthPx, 100);
    }

    public static LinearLayout.LayoutParams params(int id, int availWidthPx, int heightPct) {
        int margin = (int) Prefs.dp(ctx, Prefs.getInt(Prefs.W_MARGIN, 16));
        int gap = (int) Prefs.dp(ctx, Prefs.getInt(Prefs.W_GAP, 8));
        int inner = Math.max(1, availWidthPx - 2 * margin);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, inner * widthPct(id) / 100), Math.max(1, heightPx(id) * heightPct / 100));
        int a = align(id);
        lp.gravity = a == 0 ? Gravity.START : a == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL;
        lp.setMargins(margin, gap / 2, margin, gap / 2);
        return lp;
    }

    /** Replaces the order of a slot. */
    public static void setIds(String slot, List<Integer> ids) { save(slot, ids); }

    /** Moves a widget earlier (-1) or later (+1) within its slot. */
    public static void move(String slot, int id, int dir) {
        List<Integer> l = ids(slot);
        int i = l.indexOf(id), j = i + dir;
        if (i < 0 || j < 0 || j >= l.size()) return;
        Collections.swap(l, i, j);
        save(slot, l);
    }

    /** Moves a widget between slots without deleting it. */
    public static void moveToSlot(String from, String to, int id) {
        List<Integer> a = ids(from);
        a.remove(Integer.valueOf(id));
        save(from, a);
        List<Integer> b = ids(to);
        b.add(id);
        save(to, b);
    }

    // ---- adding ----

    static List<AppWidgetProviderInfo> installedProviders() { return mgr.getInstalledProviders(); }

    /** Skips widgets Android marks as lock-screen only or hidden from pickers: they can't work on a home screen. */
    static boolean offeredOnHome(AppWidgetProviderInfo p) {
        if (p.widgetCategory != 0 && (p.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) return false;
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (p.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER) != 0);
    }

    static void addBuiltin(String slot, int type) {
        List<Integer> l = ids(slot);
        l.add(BuiltinWidgets.newId(type));
        save(slot, l);
    }

    private static final String PENDING = "widgetPending";
    private static final String PROBLEM = "widgetProblem";
    /** Name of the widget being added, for the problem report. */
    private static String pendingName;

    /** The last thing that went wrong while adding a widget, kept so it can be read after the pop-up is gone. */
    public static String lastProblem() { return Prefs.getStr(PROBLEM, ""); }
    public static void clearProblem() { Prefs.put(PROBLEM, ""); }

    private static void problem(String text) {
        Prefs.put(PROBLEM, (pendingName == null ? "" : pendingName + ": ") + text);
    }

    /** True while an existing widget's setup screen is open again, so cancelling must not delete the widget. */
    private static boolean reconfiguring;
    /** True once the setup screen has also been tried the second way. */
    private static boolean triedDirect;
    private static long configAt;

    /** Keeps the widget being added across a restart of this app: its setup screen can use a lot of memory. */
    private static void remember() { Prefs.put(PENDING, pendingId < 0 || reconfiguring ? "" : pendingSlot + "|" + pendingId); }

    private static void restore() {
        if (pendingId >= 0) return;
        String s = Prefs.getStr(PENDING, "");
        int bar = s.lastIndexOf('|');
        if (bar <= 0) return;
        pendingSlot = s.substring(0, bar);
        try { pendingId = Integer.parseInt(s.substring(bar + 1)); } catch (NumberFormatException e) { pendingId = -1; }
    }

    private static String reason(Exception e) {
        if (e == null) return "";
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    private static void fail(Context c, String message) {
        problem(message);
        cancel();
        Toast.makeText(c, message, Toast.LENGTH_LONG).show();
    }

    static void bind(Activity a, String slot, AppWidgetProviderInfo info) {
        pendingSlot = slot;
        pendingName = String.valueOf(info.loadLabel(a.getPackageManager())) + " (" + info.provider.getPackageName() + ")";
        reconfiguring = false;
        triedDirect = false;
        try {
            pendingId = host.allocateAppWidgetId();
            remember();
            if (mgr.bindAppWidgetIdIfAllowed(pendingId, info.provider)) {
                configureOrFinish(a, info);
            } else {
                a.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), REQ_BIND);
            }
        } catch (Exception e) {
            fail(a, "Couldn't add that widget. " + reason(e));
        }
    }

    private static void configureOrFinish(Activity a, AppWidgetProviderInfo info) {
        if (info == null) {
            fail(a, "Android didn't connect that widget. Its app may be restricted or disabled.");
            return;
        }
        if (info.configure == null) {
            finish(a);
            return;
        }
        launchConfig(a, info);
    }

    // ---- the widget's own setup screen ----

    /** True if the widget has a setup screen of its own (buttons, colours and so on). */
    public static boolean hasSetup(int id) {
        if (id < 0) return false;
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        return info != null && info.configure != null;
    }

    /** Opens the setup screen of a widget that is already added. Cancelling leaves the widget as it was. */
    public static void reconfigure(Activity a, int id) {
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        if (info == null || info.configure == null) {
            Toast.makeText(a, "This widget has no setup screen.", Toast.LENGTH_SHORT).show();
            return;
        }
        reconfiguring = true;
        triedDirect = false;
        pendingName = String.valueOf(info.loadLabel(a.getPackageManager())) + " (" + info.provider.getPackageName() + ")";
        pendingId = id;
        pendingSlot = slotOf(id);
        launchConfig(a, info);
    }

    /** Opens the setup screen the way Android intends for a launcher. */
    private static void launchConfig(Activity a, AppWidgetProviderInfo info) {
        configAt = SystemClock.elapsedRealtime();
        try {
            host.startAppWidgetConfigureActivityForResult(a, pendingId, 0, REQ_CONFIG, null);
        } catch (Exception e) {
            tryDirect(a, info, e);
        }
    }

    /**
     * Some widgets only open their setup screen when started by name, so try that second.
     * The screen must allow other apps to open it, which most do.
     */
    private static void tryDirect(Activity a, AppWidgetProviderInfo info, Exception why) {
        if (!triedDirect && info != null && info.configure != null) {
            triedDirect = true;
            configAt = SystemClock.elapsedRealtime();
            try {
                a.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId), REQ_CONFIG_DIRECT);
                return;
            } catch (Exception e) {
                why = e;
            }
        }
        if (reconfiguring) {
            problem("couldn't open its setup screen. " + reason(why));
            clearPending();
            Toast.makeText(a, "Couldn't open its setup screen. " + reason(why), Toast.LENGTH_LONG).show();
        } else {
            problem("setup screen couldn't open (" + reason(why) + "). Added with default settings.");
            Toast.makeText(a, "Its setup screen couldn't open (" + reason(why) + "). Added with default settings.", Toast.LENGTH_LONG).show();
            finish(a);
        }
    }

    private static void clearPending() {
        reconfiguring = false;
        triedDirect = false;
        pendingId = -1;
        remember();
    }

    private static void finish(Context c) {
        if (pendingId < 0) return;
        if (mgr.getAppWidgetInfo(pendingId) == null) {
            fail(c, "Android didn't connect that widget. Its app may be restricted or disabled.");
            return;
        }
        List<Integer> l = ids(pendingSlot);
        l.add(pendingId);
        save(pendingSlot, l);
        clearPending();
    }

    private static void cancel() {
        if (pendingId >= 0 && !reconfiguring) {
            try { host.deleteAppWidgetId(pendingId); } catch (Exception ignored) { }
        }
        clearPending();
    }

    private static boolean setupIsOptional(AppWidgetProviderInfo info) {
        return info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0;
    }

    /** Returns true if the result belonged to the widget flow. */
    public static boolean onResult(Activity a, int req, int res) {
        if (req != REQ_BIND && req != REQ_CONFIG && req != REQ_CONFIG_DIRECT) return false;
        restore();
        if (pendingId < 0) return true; // nothing was waiting
        if (req == REQ_BIND) {
            if (res == Activity.RESULT_OK) configureOrFinish(a, mgr.getAppWidgetInfo(pendingId));
            else fail(a, "Permission to add the widget wasn't given.");
            return true;
        }
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(pendingId);
        boolean closedAtOnce = SystemClock.elapsedRealtime() - configAt < 1200;
        if (res == Activity.RESULT_OK) {
            if (reconfiguring) clearPending(); else finish(a);
        } else if (req == REQ_CONFIG && closedAtOnce && !triedDirect && info != null) {
            tryDirect(a, info, null); // it closed by itself: try opening it by name instead
        } else if (reconfiguring) {
            problem(closedAtOnce ? "setup screen closed by itself right away" : "setup closed without saving");
            clearPending();
            Toast.makeText(a, closedAtOnce ? "The widget's setup screen closed by itself right away." : "Setup closed without saving.",
                    Toast.LENGTH_LONG).show();
        } else if (setupIsOptional(info)) {
            finish(a);
        } else {
            offerDefaults(a, closedAtOnce);
        }
        return true;
    }

    /**
     * A setup screen that closes without confirming may have been backed out of, or may have closed itself.
     * Either way, most widgets work with their default settings, so let the user decide.
     */
    private static void offerDefaults(final Activity a, boolean closedAtOnce) {
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(pendingId);
        String name = info == null ? "this widget" : String.valueOf(info.loadLabel(a.getPackageManager()));
        new AlertDialog.Builder(a).setTitle("Setup wasn't finished")
                .setMessage("The setup screen for \"" + name + "\" "
                        + (closedAtOnce ? "closed by itself right away.\n\nAdd it anyway with its default settings? You can open its setup again later from Edit."
                        : "closed without confirming.\n\nAdd it anyway with its default settings? Many widgets work fine that way."))
                .setPositiveButton("Add anyway", (d, w) -> {
                    finish(a);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("Discard", (d, w) -> { problem("setup screen closed without confirming; discarded"); cancel(); })
                .setOnCancelListener(d -> { problem("setup screen closed without confirming; discarded"); cancel(); })
                .show();
    }
}
