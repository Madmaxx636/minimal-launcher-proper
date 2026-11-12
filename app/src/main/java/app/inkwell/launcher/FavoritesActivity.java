package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Choose and order the apps that sit on the home screen. */
public class FavoritesActivity extends Activity {
    private LinearLayout list;
    private ScrollView sv;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        list = new LinearLayout(this);
        sv = Ui.screen(this, list);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        int scroll = sv.getScrollY();
        Ui.theme(this, sv);
        list.removeAllViews();
        Context c = this;
        Ui.section(list, "Home apps. Drag ≡ to reorder");
        Ui.row(list, "Add apps", "Pick from everything installed", v -> addApps());
        Ui.row(list, "New pop-up folder", "Group apps. Swipe right on it, or tap it, on home.", v -> newFolder());
        final List<String> favs = Prefs.getList(Prefs.FAVS);
        final List<String> shown = new ArrayList<>();
        for (String e : favs) {
            boolean ok = Folders.isFolder(e) ? Folders.exists(Folders.idOf(e)) : AppRepo.find(e) != null;
            if (ok) shown.add(e); // uninstalled apps stay in the saved list in case they come back
        }
        for (int p = 0; p < shown.size(); p++) {
            final int pos = p;
            final String entry = shown.get(p);
            final boolean folder = Folders.isFolder(entry);
            AppRepo.AppEntry a = folder ? null : AppRepo.find(entry);
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(Ui.dragHandle(c, r, pos, shown.size(), (from, to) -> {
                Ui.reorder(favs, shown, from, to);
                save(favs);
            }));
            TextView t = Ui.text(c, folder ? "▸ " + Folders.nameOf(Folders.idOf(entry)) + "  (folder)" : AppRepo.displayLabel(a), 18, Prefs.fg(), false);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            if (folder) {
                r.addView(button("✎", () -> openFolder(Folders.idOf(entry))));
                t.setOnClickListener(v -> openFolder(Folders.idOf(entry)));
            }
            r.addView(button("▲", () -> { if (pos > 0) { Ui.reorder(favs, shown, pos, pos - 1); save(favs); } }));
            r.addView(button("▼", () -> { if (pos < shown.size() - 1) { Ui.reorder(favs, shown, pos, pos + 1); save(favs); } }));
            r.addView(button("✕", () -> { favs.remove(entry); save(favs); }));
            list.addView(r, new LinearLayout.LayoutParams(-1, -2));
            Ui.rule(list, 1);
        }
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

    private void save(List<String> favs) {
        Prefs.putList(Prefs.FAVS, favs);
        build();
    }

    private void openFolder(String id) {
        startActivity(new android.content.Intent(this, FolderActivity.class).putExtra("id", id));
    }

    private void newFolder() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setHint("Folder name");
        new AlertDialog.Builder(this).setTitle("New pop-up folder").setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "Folder";
                    String id = Folders.create(name);
                    List<String> favs = Prefs.getList(Prefs.FAVS);
                    favs.add(Folders.PREFIX + id);
                    Prefs.putList(Prefs.FAVS, favs);
                    build();
                    openFolder(id);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void addApps() {
        final List<String> favs = Prefs.getList(Prefs.FAVS);
        final List<AppRepo.AppEntry> candidates = new ArrayList<>();
        for (AppRepo.AppEntry a : AppRepo.all()) if (!favs.contains(a.pkg)) candidates.add(a);
        final String[] names = new String[candidates.size()];
        for (int i = 0; i < names.length; i++) names[i] = AppRepo.displayLabel(candidates.get(i));
        final boolean[] checked = new boolean[names.length];
        new AlertDialog.Builder(this)
                .setTitle("Add to home")
                .setMultiChoiceItems(names, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton("Add", (d, w) -> {
                    for (int i = 0; i < checked.length; i++) if (checked[i]) favs.add(candidates.get(i).pkg);
                    save(favs);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
