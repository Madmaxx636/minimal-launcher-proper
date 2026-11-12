package app.inkwell.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads ADW/Nova-style icon packs (the appfilter.xml format most packs use) from installed apps. */
public final class IconPacks {
    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME"};

    private static String loadedPack;
    private static Resources packRes;
    private static final Map<String, String> drawables = new HashMap<>();

    private IconPacks() {}

    /** package -> label of every installed icon pack. */
    public static Map<String, String> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, String> out = new LinkedHashMap<>();
        for (String a : ACTIONS) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(a), 0)) {
                out.put(ri.activityInfo.packageName, String.valueOf(ri.loadLabel(pm)));
            }
        }
        return out;
    }

    private static synchronized void ensureLoaded(Context c, String pack) {
        if (pack.equals(loadedPack)) return;
        loadedPack = pack;
        packRes = null;
        drawables.clear();
        try {
            PackageManager pm = c.getPackageManager();
            packRes = pm.getResourcesForApplication(pack);
            XmlPullParser p;
            int id = packRes.getIdentifier("appfilter", "xml", pack);
            if (id != 0) {
                p = packRes.getXml(id);
            } else {
                InputStream in = packRes.getAssets().open("appfilter.xml");
                p = XmlPullParserFactory.newInstance().newPullParser();
                p.setInput(in, "utf-8");
            }
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev != XmlPullParser.START_TAG || !"item".equals(p.getName())) continue;
                String comp = p.getAttributeValue(null, "component");
                String draw = p.getAttributeValue(null, "drawable");
                if (comp == null || draw == null) continue;
                int s = comp.indexOf('{'), e = comp.indexOf('}');
                if (s < 0 || e < 0) continue;
                drawables.put(comp.substring(s + 1, e), draw);
            }
        } catch (Exception ignored) {
            packRes = null;
        }
    }

    /** The pack's icon for an app, or null if the pack has none (the original icon is used instead). */
    public static synchronized Drawable icon(Context c, String pack, AppRepo.AppEntry a) {
        if (pack == null || pack.isEmpty()) return null;
        ensureLoaded(c, pack);
        if (packRes == null) return null;
        String name = drawables.get(a.pkg + "/" + a.cls);
        if (name == null) return null;
        int id = packRes.getIdentifier(name, "drawable", pack);
        if (id == 0) return null;
        try { return packRes.getDrawable(id, null); } catch (Exception e) { return null; }
    }

    public static synchronized void reset() { loadedPack = null; }
}
