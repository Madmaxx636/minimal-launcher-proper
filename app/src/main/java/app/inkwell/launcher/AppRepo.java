package app.inkwell.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.LruCache;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The list of launchable apps, with the user's hidden/renamed state applied. */
public final class AppRepo {
    public static final class AppEntry {
        public final String pkg;
        public final String cls;
        public final String label;
        /** A pop-up folder. pkg is "folder:<id>"; there is no real app behind it. */
        public final boolean folder;

        AppEntry(String pkg, String cls, String label) { this(pkg, cls, label, false); }

        AppEntry(String pkg, String cls, String label, boolean folder) {
            this.pkg = pkg;
            this.cls = cls;
            this.label = label;
            this.folder = folder;
        }

        public ComponentName component() { return new ComponentName(pkg, cls); }
    }

    private static Context ctx;
    private static Map<String, AppEntry> byPkg = new LinkedHashMap<>();
    private static final LruCache<String, Bitmap> icons = new LruCache<>(60);

    private AppRepo() {}

    public static void init(Context c) {
        ctx = c.getApplicationContext();
        reload();
    }

    public static synchronized void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, AppEntry> m = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || m.containsKey(pkg)) continue;
            m.put(pkg, new AppEntry(pkg, ri.activityInfo.name, String.valueOf(ri.loadLabel(pm))));
        }
        byPkg = m;
        icons.evictAll();
    }

    /** Saves a picture as this app's icon (centre-cropped to a square), or removes it when bitmap is null. */
    public static void setCustomIcon(String pkg, Bitmap bitmap) {
        java.io.File dir = new java.io.File(ctx.getFilesDir(), "icons");
        java.io.File f = new java.io.File(dir, pkg + ".png");
        if (bitmap == null) {
            f.delete();
        } else {
            dir.mkdirs();
            int s = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap sq = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - s) / 2, (bitmap.getHeight() - s) / 2, s, s);
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
                Bitmap.createScaledBitmap(sq, 192, 192, true).compress(Bitmap.CompressFormat.PNG, 100, o);
            } catch (java.io.IOException ignored) { }
        }
        clearIconCache();
    }

    public static boolean hasCustomIcon(String pkg) {
        return new java.io.File(ctx.getFilesDir(), "icons/" + pkg + ".png").exists();
    }

    public static void clearIconCache() {
        icons.evictAll();
        IconPacks.reset();
    }

    public static synchronized AppEntry find(String pkg) { return byPkg.get(pkg); }

    public static synchronized Set<String> installedPackages() { return new HashSet<>(byPkg.keySet()); }

    public static String displayLabel(AppEntry a) {
        if (a.folder) return a.label;
        return Prefs.getRenames().optString(a.pkg, a.label);
    }

    /** Every launchable app, sorted by what the user sees. */
    public static synchronized List<AppEntry> all() {
        List<AppEntry> l = new ArrayList<>(byPkg.values());
        sort(l);
        return l;
    }

    public static synchronized List<AppEntry> visible() {
        Set<String> hidden = new HashSet<>(Prefs.getList(Prefs.HIDDEN));
        List<AppEntry> l = new ArrayList<>();
        for (AppEntry a : byPkg.values()) if (!hidden.contains(a.pkg)) l.add(a);
        sort(l);
        return l;
    }

    public static synchronized List<AppEntry> favorites() {
        List<AppEntry> l = new ArrayList<>();
        for (String p : Prefs.getList(Prefs.FAVS)) {
            if (Folders.isFolder(p)) {
                String id = Folders.idOf(p);
                if (Folders.exists(id)) l.add(new AppEntry(p, "", Folders.nameOf(id), true));
                continue;
            }
            AppEntry a = byPkg.get(p);
            if (a != null) l.add(a);
        }
        return l;
    }

    private static void sort(List<AppEntry> l) {
        final org.json.JSONObject renames = Prefs.getRenames();
        Collections.sort(l, (a, b) -> sortKey(renames.optString(a.pkg, a.label))
                .compareTo(sortKey(renames.optString(b.pkg, b.label))));
    }

    public static String sortKey(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** A-Z for letters, '#' for anything else. */
    public static char letterOf(String label) {
        String k = sortKey(label).trim();
        if (k.isEmpty()) return '#';
        char c = Character.toUpperCase(k.charAt(0));
        return Character.isLetter(c) ? c : '#';
    }

    /** Notification count for an app, or the total across a folder's apps. */
    public static int badgeCount(AppEntry a) {
        if (!a.folder) return NotifService.count(a.pkg);
        int n = 0;
        for (String p : Folders.apps(Folders.idOf(a.pkg))) n += NotifService.count(p);
        return n;
    }

    public static Bitmap icon(AppEntry a, int px) {
        if (a.folder) return null;
        String pack = Prefs.getStr(Prefs.ICON_PACK, "");
        String key = pack + "|" + a.pkg + "@" + px;
        Bitmap b = icons.get(key);
        if (b != null) return b;
        java.io.File custom = new java.io.File(ctx.getFilesDir(), "icons/" + a.pkg + ".png");
        if (custom.exists()) {
            Bitmap cb = BitmapFactory.decodeFile(custom.getPath());
            if (cb != null) {
                b = Bitmap.createScaledBitmap(cb, px, px, true);
                icons.put(key, b);
                return b;
            }
        }
        try {
            Drawable d = IconPacks.icon(ctx, pack, a);
            if (d == null) d = ctx.getPackageManager().getActivityIcon(a.component());
            b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, px, px);
            d.draw(c);
            icons.put(key, b);
        } catch (Exception e) {
            return null;
        }
        return b;
    }

    public static void launch(Context c, AppEntry a) {
        if (a.folder) return;
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.component())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "Can't open " + displayLabel(a), Toast.LENGTH_SHORT).show();
        }
    }

    public static void launchPackage(Context c, String pkg) {
        AppEntry a = find(pkg);
        if (a != null) launch(c, a);
    }

    /** Phone, messages, camera, settings and a browser, where the device has them. */
    public static List<String> guessStarterFavorites() {
        PackageManager pm = ctx.getPackageManager();
        List<Intent> probes = new ArrayList<>();
        probes.add(new Intent(Intent.ACTION_DIAL));
        probes.add(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING));
        probes.add(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
        probes.add(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER));
        probes.add(new Intent(Settings.ACTION_SETTINGS));
        List<String> out = new ArrayList<>();
        for (Intent p : probes) {
            ResolveInfo ri = pm.resolveActivity(p, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                if (byPkg.containsKey(pkg) && !out.contains(pkg)) out.add(pkg);
            }
        }
        return out;
    }
}
