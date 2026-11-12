package app.inkwell.launcher;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The widgets of one place (a folder, an app's pop-up, or a spot on home) as rows you can drag into
 * order, edit, remove and add to. It is the same layout the apps use, so it works the same way.
 */
final class WidgetSection {
    private WidgetSection() {}

    /**
     * Adds the rows to list. The hosting activity must pass widget results on to Widgets.onResult,
     * because adding a widget opens other screens. rebuild redraws the whole screen.
     */
    static void build(final Activity a, final LinearLayout list, final String slot, final boolean acrossClock, final Runnable rebuild) {
        Context c = a;
        final List<Integer> ids = Widgets.ids(slot);
        Ui.row(list, "＋  Add widget", "Pick an app, then one of its widgets", v -> WidgetPicker.show(a, slot, rebuild));
        if (ids.size() >= 2) {
            Ui.row(list, "Stack them", Widgets.stacked(slot) ? "On. Swipe across them one at a time." : "Off. All shown one under another.", v -> {
                Widgets.setStacked(slot, !Widgets.stacked(slot));
                rebuild.run();
            });
        }
        final List<String> shown = new ArrayList<>();
        for (int id : ids) shown.add(String.valueOf(id));
        for (int i = 0; i < ids.size(); i++) {
            final int pos = i, id = ids.get(i);
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(Ui.dragHandle(c, r, pos, shown.size(), (from, to) -> {
                List<String> full = new ArrayList<>(shown);
                Ui.reorder(full, shown, from, to);
                List<Integer> out = new ArrayList<>();
                for (String s : full) out.add(Integer.parseInt(s));
                Widgets.setIds(slot, out);
                rebuild.run();
            }));
            r.addView(Ui.text(c, Widgets.label(id), 18, Prefs.fg(), false), new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(button(a, "▲", () -> { Widgets.move(slot, id, -1); rebuild.run(); }));
            r.addView(button(a, "▼", () -> { Widgets.move(slot, id, 1); rebuild.run(); }));
            if (acrossClock) {
                r.addView(button(a, "⇅", () -> {
                    Widgets.moveToSlot(slot, slot.equals(Widgets.HOME) ? Widgets.HOME_BELOW : Widgets.HOME, id);
                    rebuild.run();
                }));
            }
            r.addView(button(a, "✎", () -> WidgetMenu.show(a, slot, id, rebuild)));
            r.addView(button(a, "✕", () -> { Widgets.remove(slot, id); rebuild.run(); }));
            list.addView(r, new LinearLayout.LayoutParams(-1, -2));
            Ui.rule(list, 1);
        }
        if (ids.isEmpty()) Ui.row(list, "No widgets yet", null, null);
        else if (acrossClock) Ui.row(list, "⇅ moves a widget to the other side of the clock. ✎ opens its options.", null, null);
    }

    private static TextView button(Activity a, String s, Runnable r) {
        TextView t = Ui.text(a, s, 20, Prefs.fg(), false);
        t.setGravity(Gravity.CENTER);
        int pad = (int) Prefs.dp(a, 14);
        t.setPadding(pad, pad, pad, pad);
        t.setOnClickListener(v -> r.run());
        return t;
    }
}
