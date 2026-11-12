package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Edit one pop-up folder: add apps, reorder them, remove them, rename, show it in the app list. */
public class FolderActivity extends Activity {
    private String id;
    private LinearLayout list;
    private ScrollView sv;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        id = getIntent().getStringExtra("id");
        if (id == null || !Folders.exists(id)) {
            finish();
            return;
        }
        list = new LinearLayout(this);
        sv = Ui.screen(this, list);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (id != null && Folders.exists(id)) {
            Widgets.onChanged = this::build;
            build();
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (Widgets.onResult(this, req, res)) build();
    }

    private void build() {
        final int scroll = sv.getScrollY();
        Ui.theme(this, sv);
        list.removeAllViews();
        Context c = this;
        Ui.row(list, "‹  Back", null, v -> finish());
        Ui.section(list, Folders.nameOf(id));
        Ui.row(list, "Add apps", "Pick from everything installed", v -> addApps());
        Ui.row(list, "Rename folder", Folders.nameOf(id), v -> rename());
        Ui.row(list, "Show in app list", Folders.inList(id) ? "On. The folder appears among your apps." : "Off", v -> {
            Folders.setInList(id, !Folders.inList(id));
            build();
        });
        Ui.section(list, "Apps. Drag ≡ to reorder");
        final List<String> apps = Folders.apps(id);
        final List<String> shown = new ArrayList<>();
        for (String p : apps) if (AppRepo.find(p) != null) shown.add(p);
        for (int i = 0; i < shown.size(); i++) {
            final int pos = i;
            final String pkg = shown.get(i);
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(Ui.dragHandle(c, r, pos, shown.size(), (from, to) -> {
                Ui.reorder(apps, shown, from, to);
                save(apps);
            }));
            r.addView(Ui.text(c, AppRepo.displayLabel(AppRepo.find(pkg)), 18, Prefs.fg(), false), new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(button("▲", () -> { if (pos > 0) { Ui.reorder(apps, shown, pos, pos - 1); save(apps); } }));
            r.addView(button("▼", () -> { if (pos < shown.size() - 1) { Ui.reorder(apps, shown, pos, pos + 1); save(apps); } }));
            r.addView(button("✕", () -> { apps.remove(pkg); save(apps); }));
            list.addView(r, new LinearLayout.LayoutParams(-1, -2));
            Ui.rule(list, 1);
        }
        if (shown.isEmpty()) Ui.row(list, "Empty", "Tap Add apps to fill this folder.", null);
        Ui.section(list, "Widgets in this folder. Drag ≡ to reorder");
        WidgetSection.build(this, list, Folders.PREFIX + id, false, this::build);
        Ui.section(list, "Danger");
        Ui.row(list, "Delete folder", "The apps themselves are not uninstalled.", v ->
                new AlertDialog.Builder(this).setTitle("Delete " + Folders.nameOf(id) + "?")
                        .setPositiveButton("Delete", (d, w) -> { Folders.delete(id); finish(); })
                        .setNegativeButton("Cancel", null).show());
        sv.post(() -> sv.scrollTo(0, scroll));
    }

    private TextView button(String s, Runnable r) {
        TextView t = Ui.text(this, s, 20, Prefs.fg(), false);
        t.setGravity(Gravity.CENTER);
        int pad = (int) Prefs.dp(this, 16);
        t.setPadding(pad, pad, pad, pad);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void save(List<String> apps) {
        Folders.setApps(id, apps);
        build();
    }

    private void rename() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(Folders.nameOf(id));
        et.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Rename folder").setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) Folders.rename(id, s);
                    build();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void addApps() {
        final List<String> have = Folders.apps(id);
        final List<AppRepo.AppEntry> candidates = new ArrayList<>();
        for (AppRepo.AppEntry a : AppRepo.all()) if (!have.contains(a.pkg)) candidates.add(a);
        final String[] names = new String[candidates.size()];
        for (int i = 0; i < names.length; i++) names[i] = AppRepo.displayLabel(candidates.get(i));
        final boolean[] checked = new boolean[names.length];
        new AlertDialog.Builder(this).setTitle("Add to " + Folders.nameOf(id))
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("Add", (d, w) -> {
                    for (int i = 0; i < checked.length; i++) if (checked[i]) have.add(candidates.get(i).pkg);
                    save(have);
                })
                .setNegativeButton("Cancel", null).show();
    }
}
