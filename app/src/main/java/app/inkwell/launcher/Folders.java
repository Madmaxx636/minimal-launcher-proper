package app.inkwell.launcher;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Pop-up folders. A favorite whose entry is "folder:<id>" opens a pop-up listing the folder's apps,
 * their notifications and any widgets pinned to it.
 */
public final class Folders {
    public static final String PREFIX = "folder:";

    private Folders() {}

    private static JSONObject read() {
        try { return new JSONObject(Prefs.getStr(Prefs.FOLDERS, "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    private static void write(JSONObject o) { Prefs.put(Prefs.FOLDERS, o.toString()); }

    public static boolean isFolder(String entry) { return entry.startsWith(PREFIX); }
    public static String idOf(String entry) { return entry.substring(PREFIX.length()); }
    public static boolean exists(String id) { return read().has(id); }

    public static String nameOf(String id) {
        JSONObject f = read().optJSONObject(id);
        return f == null ? "Folder" : f.optString("name", "Folder");
    }

    public static List<String> apps(String id) {
        List<String> out = new ArrayList<>();
        JSONObject f = read().optJSONObject(id);
        if (f == null) return out;
        JSONArray a = f.optJSONArray("apps");
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    /** Creates an empty folder and returns its id. */
    public static String create(String name) {
        String id = Long.toString(System.currentTimeMillis(), 36);
        save(id, name, new ArrayList<>());
        return id;
    }

    private static void save(String id, String name, List<String> apps) {
        JSONObject all = read();
        try {
            JSONObject old = all.optJSONObject(id);
            JSONObject f = new JSONObject();
            f.put("name", name);
            f.put("apps", new JSONArray(apps));
            if (old != null && old.optBoolean("inList")) f.put("inList", true);
            all.put(id, f);
        } catch (JSONException ignored) { }
        write(all);
    }

    public static boolean inList(String id) {
        JSONObject f = read().optJSONObject(id);
        return f != null && f.optBoolean("inList");
    }

    public static void setInList(String id, boolean on) {
        JSONObject all = read();
        JSONObject f = all.optJSONObject(id);
        if (f == null) return;
        try { f.put("inList", on); all.put(id, f); } catch (JSONException ignored) { }
        write(all);
    }

    public static List<String> allIds() {
        List<String> out = new ArrayList<>();
        java.util.Iterator<String> it = read().keys();
        while (it.hasNext()) out.add(it.next());
        return out;
    }

    /** Folders that were set to appear in the app list, as list rows. */
    public static List<AppRepo.AppEntry> listEntries() {
        List<AppRepo.AppEntry> out = new ArrayList<>();
        java.util.Iterator<String> it = read().keys();
        while (it.hasNext()) {
            String id = it.next();
            if (inList(id)) out.add(new AppRepo.AppEntry(PREFIX + id, "", nameOf(id), true));
        }
        return out;
    }

    public static void rename(String id, String name) { save(id, name, apps(id)); }
    public static void setApps(String id, List<String> apps) { save(id, nameOf(id), apps); }

    public static void delete(String id) {
        JSONObject all = read();
        all.remove(id);
        write(all);
        List<String> favs = Prefs.getList(Prefs.FAVS);
        favs.remove(PREFIX + id);
        Prefs.putList(Prefs.FAVS, favs);
        Widgets.clearSlot(PREFIX + id);
    }

}
