package app.inkwell.launcher;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Plain text rows in the user's theme: no ripples, no switches, no shadows. */
final class Ui {
    private Ui() {}

    static ScrollView screen(android.app.Activity a, LinearLayout list) {
        ScrollView sv = new ScrollView(a);
        sv.setFitsSystemWindows(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));
        return sv;
    }

    static void theme(android.app.Activity a, View scroll) {
        int bg = Prefs.bg();
        a.getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        a.getWindow().setStatusBarColor(bg);
        a.getWindow().setNavigationBarColor(bg);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (android.graphics.Color.luminance(bg) > 0.5f) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
        scroll.setBackgroundColor(bg);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp * Prefs.scalePct() / 100f);
        t.setTextColor(color);
        t.setTypeface(Prefs.typeface(), bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return t;
    }

    /** Value picker: a slider plus -/+ buttons, because e-ink panels are poor at dragging. */
    static void slider(android.app.Activity a, String title, final int min, final int max, final int step,
                       final String unit, int current, final int def, final java.util.function.IntConsumer onSave) {
        final int steps = (max - min) / step;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) Prefs.dp(a, 20);
        box.setPadding(p, p, p, 0);
        final TextView value = new TextView(a);
        value.setTextSize(30);
        value.setGravity(android.view.Gravity.CENTER);
        box.addView(value, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(a);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        final TextView minus = new TextView(a), plus = new TextView(a);
        for (TextView t : new TextView[]{minus, plus}) {
            t.setTextSize(30);
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(p, p / 2, p, p / 2);
        }
        minus.setText("−");
        plus.setText("+");
        final android.widget.SeekBar bar = new android.widget.SeekBar(a);
        bar.setMax(steps);
        bar.setProgress(Math.max(0, Math.min(steps, (current - min) / step)));
        row.addView(minus);
        row.addView(bar, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(plus);
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        final Runnable show = () -> value.setText((min + bar.getProgress() * step) + unit);
        show.run();
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int v, boolean user) { show.run(); }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        minus.setOnClickListener(v -> bar.setProgress(bar.getProgress() - 1));
        plus.setOnClickListener(v -> bar.setProgress(bar.getProgress() + 1));
        new android.app.AlertDialog.Builder(a).setTitle(title).setView(box)
                .setPositiveButton("Save", (d, w) -> onSave.accept(min + bar.getProgress() * step))
                .setNeutralButton("Default", (d, w) -> onSave.accept(def))
                .setNegativeButton("Cancel", null).show();
    }

    /** Niagara's drag handle: press and drag the row up or down, release to drop it. */
    static TextView dragHandle(Context c, final View row, final int pos, final int count,
                               final java.util.function.BiConsumer<Integer, Integer> onMove) {
        TextView t = text(c, "≡", 24, Prefs.fg(), true);
        t.setGravity(android.view.Gravity.CENTER);
        int p = (int) Prefs.dp(c, 14);
        t.setPadding(p, p, p, p);
        t.setOnTouchListener(new View.OnTouchListener() {
            float startY;

            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startY = e.getRawY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        row.setBackgroundColor((Prefs.fg() & 0x00FFFFFF) | 0x30000000);
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        row.setTranslationY(e.getRawY() - startY);
                        return true;
                    case android.view.MotionEvent.ACTION_UP: {
                        float dy = e.getRawY() - startY;
                        row.setTranslationY(0);
                        row.setBackgroundColor(0);
                        int to = Math.max(0, Math.min(count - 1, pos + Math.round(dy / Math.max(1, row.getHeight()))));
                        if (to != pos) onMove.accept(pos, to);
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_CANCEL:
                        row.setTranslationY(0);
                        row.setBackgroundColor(0);
                        return true;
                }
                return false;
            }
        });
        return t;
    }

    /**
     * Moves the shown[from] entry to shown[to]'s place inside the full list, leaving entries that
     * aren't on screen (for example uninstalled apps) where they were.
     */
    static void reorder(java.util.List<String> full, java.util.List<String> shown, int from, int to) {
        java.util.List<String> order = new java.util.ArrayList<>(shown);
        order.add(to, order.remove(from));
        java.util.Set<String> onScreen = new java.util.HashSet<>(shown);
        int k = 0;
        for (int i = 0; i < full.size(); i++) if (onScreen.contains(full.get(i))) full.set(i, order.get(k++));
    }

    static void section(LinearLayout list, String title) {
        Context c = list.getContext();
        TextView t = text(c, title.toUpperCase(java.util.Locale.getDefault()), 13, Prefs.fg(), true);
        int p = (int) Prefs.dp(c, 16);
        t.setPadding(p, (int) Prefs.dp(c, 24), p, (int) Prefs.dp(c, 6));
        list.addView(t);
        rule(list, 2);
    }

    static void rule(LinearLayout list, int px) {
        View v = new View(list.getContext());
        v.setBackgroundColor((Prefs.fg() & 0x00FFFFFF) | (px > 1 ? 0xFF000000 : 0x40000000));
        list.addView(v, new LinearLayout.LayoutParams(-1, px));
    }

    /** A tappable row: big title, smaller current value underneath. */
    static TextView row(LinearLayout list, String title, String value, View.OnClickListener click) {
        Context c = list.getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        int p = (int) Prefs.dp(c, 16);
        r.setPadding(p, (int) Prefs.dp(c, 12), p, (int) Prefs.dp(c, 12));
        r.addView(text(c, title, 18, Prefs.fg(), false));
        TextView v = null;
        if (value != null && !value.isEmpty()) {
            v = text(c, value, 14, (Prefs.fg() & 0x00FFFFFF) | 0xB0000000, false);
            r.addView(v);
        }
        r.setClickable(click != null);
        if (click != null) {
            r.setOnClickListener(click);
            r.setOnTouchListener((view, ev) -> {
                int a = ev.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) view.setBackgroundColor((Prefs.fg() & 0x00FFFFFF) | 0x30000000);
                else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) view.setBackgroundColor(0);
                return false;
            });
        }
        // The row and its divider travel together, so a screen can move or hide whole rows (settings search does).
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(r, new LinearLayout.LayoutParams(-1, -2));
        rule(cell, 1);
        cell.setTag(new String[]{title, value == null ? "" : value});
        list.addView(cell, new LinearLayout.LayoutParams(-1, -2));
        return v;
    }
}
