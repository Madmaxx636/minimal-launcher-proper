package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "Add widget": first choose an app (A to Z, Minimal Launcher Proper first), then one of that app's widgets. */
final class WidgetPicker {
    private static final String[] BUILTIN_HINTS = {"", "Your next calendar events", "This month, today circled",
            "Controls for what's playing", "Battery level and charging", "A note you can edit", "A dial with hands", "The time in words"};

    private static final class Row {
        final Drawable icon;
        final String title, subtitle;
        final Object tag;

        Row(Drawable icon, String title, String subtitle, Object tag) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.tag = tag;
        }
    }

    private WidgetPicker() {}

    static void show(final Activity a, final String slot, final Runnable done) {
        final PackageManager pm = a.getPackageManager();
        final Map<String, List<AppWidgetProviderInfo>> byApp = new HashMap<>();
        for (AppWidgetProviderInfo p : Widgets.installedProviders()) {
            if (!Widgets.offeredOnHome(p)) continue;
            String pkg = p.provider.getPackageName();
            List<AppWidgetProviderInfo> l = byApp.get(pkg);
            if (l == null) {
                l = new ArrayList<>();
                byApp.put(pkg, l);
            }
            l.add(p);
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(icon(pm, a.getPackageName()), "Minimal Launcher Proper", (BuiltinWidgets.NAMES.length - 1) + " built-in widgets that work offline", a.getPackageName()));
        List<Row> others = new ArrayList<>();
        for (Map.Entry<String, List<AppWidgetProviderInfo>> e : byApp.entrySet()) {
            int n = e.getValue().size();
            others.add(new Row(icon(pm, e.getKey()), appLabel(pm, e.getKey()), n + (n == 1 ? " widget" : " widgets"), e.getKey()));
        }
        Collections.sort(others, (x, y) -> x.title.compareToIgnoreCase(y.title));
        rows.addAll(others);
        new AlertDialog.Builder(a).setTitle("Add widget: choose an app")
                .setAdapter(adapter(a, rows), (d, w) -> {
                    String pkg = (String) rows.get(w).tag;
                    if (pkg.equals(a.getPackageName())) showBuiltins(a, slot, done);
                    else showWidgets(a, slot, done, rows.get(w).title, byApp.get(pkg));
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** True when the app offers at least one widget that can go on a home screen. */
    static boolean hasWidgets(Context c, String pkg) {
        for (AppWidgetProviderInfo p : Widgets.installedProviders()) {
            if (p.provider.getPackageName().equals(pkg) && Widgets.offeredOnHome(p)) return true;
        }
        return false;
    }

    /** Straight to one app's widgets, for apps that already know which app they want. */
    static void showFor(final Activity a, final String slot, String pkg, final Runnable done) {
        List<AppWidgetProviderInfo> list = new ArrayList<>();
        for (AppWidgetProviderInfo p : Widgets.installedProviders()) {
            if (p.provider.getPackageName().equals(pkg) && Widgets.offeredOnHome(p)) list.add(p);
        }
        if (list.isEmpty()) return;
        showWidgets(a, slot, done, appLabel(a.getPackageManager(), pkg), list);
    }

    private static void showBuiltins(final Activity a, final String slot, final Runnable done) {
        final List<Row> rows = new ArrayList<>();
        for (int t = 1; t < BuiltinWidgets.NAMES.length; t++) rows.add(new Row(null, BuiltinWidgets.NAMES[t], BUILTIN_HINTS[t], t));
        new AlertDialog.Builder(a).setTitle("Minimal Launcher Proper widgets")
                .setAdapter(adapter(a, rows), (d, w) -> {
                    Widgets.addBuiltin(slot, (Integer) rows.get(w).tag);
                    if (done != null) done.run();
                })
                .setNeutralButton("‹  Apps", (d, w) -> show(a, slot, done))
                .setNegativeButton("Cancel", null).show();
    }

    private static void showWidgets(final Activity a, final String slot, final Runnable done, String app, List<AppWidgetProviderInfo> list) {
        final PackageManager pm = a.getPackageManager();
        final float density = a.getResources().getDisplayMetrics().density;
        final List<AppWidgetProviderInfo> sorted = new ArrayList<>(list);
        Collections.sort(sorted, (x, y) -> String.valueOf(x.loadLabel(pm)).compareToIgnoreCase(String.valueOf(y.loadLabel(pm))));
        final List<Row> rows = new ArrayList<>();
        for (AppWidgetProviderInfo p : sorted) {
            int cols = Math.max(1, Math.round((p.minWidth / density + 30) / 70f));
            int lines = Math.max(1, Math.round((p.minHeight / density + 30) / 70f));
            rows.add(new Row(null, String.valueOf(p.loadLabel(pm)), cols + " x " + lines + " cells" + (p.configure != null ? "  ·  has a setup screen" : ""), p));
        }
        new AlertDialog.Builder(a).setTitle(app)
                .setAdapter(adapter(a, rows), (d, w) -> Widgets.bind(a, slot, sorted.get(w)))
                .setNeutralButton("‹  Apps", (d, w) -> show(a, slot, done))
                .setNegativeButton("Cancel", null).show();
    }

    private static Drawable icon(PackageManager pm, String pkg) {
        try { return pm.getApplicationIcon(pkg); } catch (Exception e) { return null; }
    }

    private static String appLabel(PackageManager pm, String pkg) {
        try { return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (Exception e) { return pkg; }
    }

    /** Two-line rows in the dialog's own colours, so they read in any theme. */
    private static ListAdapter adapter(final Context c, final List<Row> rows) {
        return new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                Row r = rows.get(i);
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int p = (int) Prefs.dp(c, 16);
                row.setPadding(p, p / 2, p, p / 2);
                if (r.icon != null) {
                    ImageView iv = new ImageView(c);
                    iv.setImageDrawable(r.icon);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) Prefs.dp(c, 40), (int) Prefs.dp(c, 40));
                    lp.rightMargin = p;
                    row.addView(iv, lp);
                }
                LinearLayout col = new LinearLayout(c);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView title = new TextView(c);
                title.setTextAppearance(android.R.style.TextAppearance_Material_Subhead);
                title.setText(r.title);
                col.addView(title);
                if (r.subtitle != null && !r.subtitle.isEmpty()) {
                    TextView sub = new TextView(c);
                    sub.setTextAppearance(android.R.style.TextAppearance_Material_Caption);
                    sub.setText(r.subtitle);
                    col.addView(sub);
                }
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
                return row;
            }
        };
    }
}
