package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Process;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

/**
 * What the quick button can do. A target is stored as text: an app's package name, "folder:<id>" for a
 * pop-up folder, "shortcut:<package>/<id>" for one of an app's own shortcuts, or "action:<name>".
 */
final class ButtonTargets {
    static final String[] ACTION_IDS = {"search", "drawer", "panel", "lock", "flash"};
    static final String[] ACTION_NAMES = {"Search", "All apps", "Notifications panel", "Lock screen", "Clear ghosting"};
    /** Short words drawn inside the button, since actions have no icon. */
    static final String[] ACTION_GLYPHS = {"Find", "Apps", "Inbox", "Lock", "Clear"};

    private static final String NEW_FOLDER = "new-folder";

    private ButtonTargets() {}

    static boolean isAction(String t) { return t.startsWith("action:"); }
    static boolean isShortcut(String t) { return t.startsWith("shortcut:"); }

    static String actionOf(String t) { return t.substring("action:".length()); }

    static String packageOf(String t) {
        if (isShortcut(t)) return t.substring("shortcut:".length(), t.indexOf('/'));
        return t;
    }

    static String glyph(String t) {
        int i = java.util.Arrays.asList(ACTION_IDS).indexOf(actionOf(t));
        return i < 0 ? "?" : ACTION_GLYPHS[i];
    }

    /** Words for a target, for settings and menus. */
    static String label(String t) {
        if (t == null || t.isEmpty()) return "Nothing";
        if (isAction(t)) {
            int i = java.util.Arrays.asList(ACTION_IDS).indexOf(actionOf(t));
            return i < 0 ? "Unknown action" : ACTION_NAMES[i];
        }
        if (Folders.isFolder(t)) return Folders.exists(Folders.idOf(t)) ? "Pop-up: " + Folders.nameOf(Folders.idOf(t)) : "Pop-up (deleted)";
        if (isShortcut(t)) {
            AppRepo.AppEntry a = AppRepo.find(packageOf(t));
            return "Shortcut in " + (a == null ? packageOf(t) : AppRepo.displayLabel(a));
        }
        AppRepo.AppEntry a = AppRepo.find(t);
        return a == null ? "Nothing (app not installed)" : AppRepo.displayLabel(a);
    }

    /** Starts one of an app's shortcuts. Returns false if it can't be started. */
    static boolean startShortcut(Context c, String t) {
        try {
            String pkg = packageOf(t), id = t.substring(t.indexOf('/') + 1);
            ((LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE)).startShortcut(pkg, id, null, null, Process.myUserHandle());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Asks what the button should do and stores the answer under key. */
    static void pick(final Activity a, String title, final String key, final Runnable done) {
        final List<String> values = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        values.add("");
        names.add("Nothing");
        for (int i = 0; i < ACTION_IDS.length; i++) {
            values.add("action:" + ACTION_IDS[i]);
            names.add(ACTION_NAMES[i]);
        }
        values.add(NEW_FOLDER);
        names.add("＋  New pop-up folder…");
        for (String id : Folders.allIds()) {
            values.add(Folders.PREFIX + id);
            names.add("▸ " + Folders.nameOf(id) + "  (pop-up folder)");
        }
        for (AppRepo.AppEntry app : AppRepo.all()) {
            values.add(app.pkg);
            names.add(AppRepo.displayLabel(app));
        }
        new AlertDialog.Builder(a).setTitle(title)
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    String v = values.get(w);
                    if (NEW_FOLDER.equals(v)) newFolder(a, key, done);
                    else if (!v.isEmpty() && !isAction(v) && !Folders.isFolder(v)) appOrShortcut(a, v, key, done);
                    else store(key, v, done);
                }).show();
    }

    private static void store(String key, String value, Runnable done) {
        Prefs.put(key, value);
        if (done != null) done.run();
    }

    /** An app can be opened, or if it offers shortcuts (such as "New tab"), one of those can be the target. */
    private static void appOrShortcut(final Activity a, final String pkg, final String key, final Runnable done) {
        final List<ShortcutInfo> shortcuts = PopupPanel.shortcuts(a, pkg);
        if (shortcuts.isEmpty()) {
            store(key, pkg, done);
            return;
        }
        String[] items = new String[shortcuts.size() + 1];
        items[0] = "Open " + AppRepo.displayLabel(AppRepo.find(pkg));
        for (int i = 0; i < shortcuts.size(); i++) items[i + 1] = String.valueOf(shortcuts.get(i).getShortLabel());
        new AlertDialog.Builder(a).setTitle(AppRepo.displayLabel(AppRepo.find(pkg)))
                .setItems(items, (d, w) -> store(key, w == 0 ? pkg : "shortcut:" + pkg + "/" + shortcuts.get(w - 1).getId(), done))
                .show();
    }

    private static void newFolder(final Activity a, final String key, final Runnable done) {
        final EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setHint("Folder name");
        new AlertDialog.Builder(a).setTitle("New pop-up folder").setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    String id = Folders.create(name.isEmpty() ? "Folder" : name);
                    store(key, Folders.PREFIX + id, done);
                    a.startActivity(new Intent(a, FolderActivity.class).putExtra("id", id)); // choose its apps
                })
                .setNegativeButton("Cancel", null).show();
    }
}
