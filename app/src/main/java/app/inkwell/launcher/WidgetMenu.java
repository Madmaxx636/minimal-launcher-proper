package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/** The options for one widget: size, alignment, colours, setup screen, position and removal. */
final class WidgetMenu {
    private WidgetMenu() {}

    static void show(final Activity a, final String slot, final int id, final Runnable changed) {
        final List<String> names = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        names.add("Height:  " + Widgets.heightDp(id) + " dp");
        acts.add(() -> Ui.slider(a, "Height", 40, 800, 10, " dp", Widgets.heightDp(id), 120,
                v -> { Widgets.setHeightDp(id, v); changed.run(); }));
        names.add("Width:  " + Widgets.widthPct(id) + "%");
        acts.add(() -> Ui.slider(a, "Width", 30, 100, 5, "%", Widgets.widthPct(id), 100,
                v -> { Widgets.setWidthPct(id, v); changed.run(); }));
        final String[] align = {"Left", "Center", "Right"};
        names.add("Alignment:  " + align[Widgets.align(id)]);
        acts.add(() -> new AlertDialog.Builder(a).setTitle("Alignment")
                .setItems(align, (d, w) -> { Widgets.setAlign(id, w); changed.run(); }).show());
        if (id > 0) {
            names.add("Colors:  " + Widgets.LOOKS[Widgets.look(id)]);
            acts.add(() -> new AlertDialog.Builder(a).setTitle("Colors")
                    .setItems(Widgets.LOOKS, (d, w) -> { Widgets.setLook(id, w); changed.run(); }).show());
            names.add("Background:  " + Widgets.BACKDROPS[Widgets.backdrop(id)]);
            acts.add(() -> new AlertDialog.Builder(a).setTitle("Background behind the widget")
                    .setMessage("If the widget looks blank, its text is probably light on see-through. Try a black background, or Grayscale, inverted.")
                    .setItems(Widgets.BACKDROPS, (d, w) -> { Widgets.setBackdrop(id, w); changed.run(); }).show());
            if (Widgets.hasSetup(id)) {
                names.add("Widget settings (its own setup screen)");
                acts.add(() -> Widgets.reconfigure(a, id));
            }
            names.add("About this widget");
            acts.add(() -> new AlertDialog.Builder(a).setTitle(Widgets.label(id)).setMessage(Widgets.describe(id))
                    .setPositiveButton("OK", null).show());
        }
        names.add("Move up");
        acts.add(() -> { Widgets.move(slot, id, -1); changed.run(); });
        names.add("Move down");
        acts.add(() -> { Widgets.move(slot, id, 1); changed.run(); });
        if (slot.equals(Widgets.HOME) || slot.equals(Widgets.HOME_BELOW)) {
            final boolean above = slot.equals(Widgets.HOME);
            names.add(above ? "Move below the clock" : "Move above the clock");
            acts.add(() -> { Widgets.moveToSlot(slot, above ? Widgets.HOME_BELOW : Widgets.HOME, id); changed.run(); });
            final boolean isClock = Prefs.getInt(Prefs.CLOCK_WIDGET, -1) == id;
            names.add(isClock ? "Show the clock again" : "Use instead of the clock");
            acts.add(() -> { Prefs.put(Prefs.CLOCK_WIDGET, isClock ? -1 : id); changed.run(); });
        }
        names.add("Remove");
        acts.add(() -> { Widgets.remove(slot, id); changed.run(); });
        new AlertDialog.Builder(a).setTitle(Widgets.label(id))
                .setItems(names.toArray(new String[0]), (d, which) -> acts.get(which).run()).show();
    }
}
