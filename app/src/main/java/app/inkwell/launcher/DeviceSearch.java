package app.inkwell.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Searches the rest of the device for what you typed: settings screens, app shortcuts, contacts,
 * calendar events and media files. Every source is optional, and the ones that read your data
 * only run after you gave the permission. Nothing is stored and nothing leaves the device.
 * Results are list rows whose "pkg" is "kind:payload", opened by HomeActivity.
 */
final class DeviceSearch {
    interface Cancelled { boolean now(); }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final int PER_SOURCE = 4;

    private static final String[][] SETTINGS = {
            {"Wi-Fi", "wifi wi-fi wireless internet network", Settings.ACTION_WIFI_SETTINGS},
            {"Bluetooth", "bluetooth pair headphones", Settings.ACTION_BLUETOOTH_SETTINGS},
            {"Display", "display screen brightness font size dark theme", Settings.ACTION_DISPLAY_SETTINGS},
            {"Sound", "sound volume ringtone vibration", Settings.ACTION_SOUND_SETTINGS},
            {"Battery saver", "battery saver power", Settings.ACTION_BATTERY_SAVER_SETTINGS},
            {"Storage", "storage space memory", Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
            {"Apps", "apps applications manage uninstall", Settings.ACTION_APPLICATION_SETTINGS},
            {"Default apps", "default apps browser home", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS},
            {"Location", "location gps", Settings.ACTION_LOCATION_SOURCE_SETTINGS},
            {"Accessibility", "accessibility talkback", Settings.ACTION_ACCESSIBILITY_SETTINGS},
            {"Date & time", "date time clock timezone", Settings.ACTION_DATE_SETTINGS},
            {"Language", "language region locale", Settings.ACTION_LOCALE_SETTINGS},
            {"Keyboard", "keyboard input method", Settings.ACTION_INPUT_METHOD_SETTINGS},
            {"Security", "security lock pin password", Settings.ACTION_SECURITY_SETTINGS},
            {"Privacy", "privacy permissions", Settings.ACTION_PRIVACY_SETTINGS},
            {"Accounts & sync", "accounts sync", Settings.ACTION_SYNC_SETTINGS},
            {"About phone", "about phone device info version", Settings.ACTION_DEVICE_INFO_SETTINGS},
            {"Developer options", "developer options debugging usb", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
            {"NFC", "nfc tap pay", Settings.ACTION_NFC_SETTINGS},
            {"Airplane mode", "airplane flight mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS},
            {"VPN", "vpn", Settings.ACTION_VPN_SETTINGS},
            {"Notification access", "notification access listener", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS},
            {"Printing", "print printer", Settings.ACTION_PRINT_SETTINGS},
            {"Home app", "home launcher default", Settings.ACTION_HOME_SETTINGS},
            {"All settings", "settings", Settings.ACTION_SETTINGS},
    };

    private DeviceSearch() {}

    static boolean anyOn() {
        return Prefs.getBool(Prefs.S_SETTINGS, true) || Prefs.getBool(Prefs.S_SHORTCUTS, true) || Prefs.getBool(Prefs.S_CONTACTS, false)
                || Prefs.getBool(Prefs.S_EVENTS, false) || Prefs.getBool(Prefs.S_FILES, false);
    }

    /** Runs off the main thread. done gets the rows, unless the search was replaced by a newer one. */
    static void run(final Context c, final String q, final Cancelled cancelled, final Consumer<List<AppRepo.AppEntry>> done) {
        if (!anyOn()) return;
        WORKER.execute(() -> {
            List<AppRepo.AppEntry> out = new ArrayList<>();
            try { if (Prefs.getBool(Prefs.S_SETTINGS, true)) settings(q, out); } catch (Exception ignored) { }
            try { if (Prefs.getBool(Prefs.S_SHORTCUTS, true) && !cancelled.now()) shortcuts(c, q, out); } catch (Exception ignored) { }
            try { if (Prefs.getBool(Prefs.S_CONTACTS, false) && has(c, Manifest.permission.READ_CONTACTS) && !cancelled.now()) contacts(c, q, out); } catch (Exception ignored) { }
            try { if (Prefs.getBool(Prefs.S_EVENTS, false) && has(c, Manifest.permission.READ_CALENDAR) && !cancelled.now()) events(c, q, out); } catch (Exception ignored) { }
            try { if (Prefs.getBool(Prefs.S_FILES, false) && hasMedia(c) && !cancelled.now()) files(c, q, out); } catch (Exception ignored) { }
            if (!cancelled.now()) done.accept(out);
        });
    }

    private static boolean has(Context c, String perm) { return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }

    static String[] mediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    static boolean hasMedia(Context c) {
        for (String p : mediaPermissions()) if (has(c, p)) return true;
        return false;
    }

    private static void add(List<AppRepo.AppEntry> out, String kind, String payload, String label) {
        out.add(new AppRepo.AppEntry(kind + ":" + payload, "", label));
    }

    private static void settings(String q, List<AppRepo.AppEntry> out) {
        String[] words = AppRepo.sortKey(q).split("\\s+");
        int n = 0;
        for (String[] s : SETTINGS) {
            String hay = AppRepo.sortKey(s[0] + " " + s[1]);
            boolean all = true;
            for (String w : words) if (!w.isEmpty() && !hay.contains(w)) all = false;
            if (all && n++ < PER_SOURCE) add(out, "setting", s[2], "Setting · " + s[0]);
        }
    }

    private static void shortcuts(Context c, String q, List<AppRepo.AppEntry> out) {
        LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (la == null || !la.hasShortcutHostPermission()) return;
        List<ShortcutInfo> all = la.getShortcuts(new LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), Process.myUserHandle());
        if (all == null) return;
        String key = AppRepo.sortKey(q);
        int n = 0;
        for (ShortcutInfo s : all) {
            if (!s.isEnabled() || s.getShortLabel() == null) continue;
            if (!AppRepo.sortKey(String.valueOf(s.getShortLabel())).contains(key)) continue;
            AppRepo.AppEntry app = AppRepo.find(s.getPackage());
            add(out, "shortcut", s.getPackage() + "/" + s.getId(),
                    "Shortcut · " + s.getShortLabel() + (app == null ? "" : " (" + AppRepo.displayLabel(app) + ")"));
            if (++n == PER_SOURCE) break;
        }
    }

    private static void contacts(Context c, String q, List<AppRepo.AppEntry> out) {
        try (Cursor cur = c.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?", new String[]{"%" + q + "%"},
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")) {
            int n = 0;
            while (cur != null && cur.moveToNext() && n++ < PER_SOURCE) {
                Uri lookup = ContactsContract.Contacts.getLookupUri(cur.getLong(0), cur.getString(1));
                if (lookup != null) add(out, "contact", lookup.toString(), "Contact · " + cur.getString(2));
            }
        }
    }

    private static void events(Context c, String q, List<AppRepo.AppEntry> out) {
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_SEARCH_URI.buildUpon();
        ContentUris.appendId(b, now);
        ContentUris.appendId(b, now + 365L * 24 * 3600 * 1000);
        b.appendPath(q);
        try (Cursor cur = c.getContentResolver().query(b.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_ID},
                null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            int n = 0;
            while (cur != null && cur.moveToNext() && n++ < PER_SOURCE) {
                String when = new SimpleDateFormat("EEE d MMM, " + (Prefs.getBool(Prefs.CLOCK24, true) ? "HH:mm" : "h:mm a"), Locale.getDefault())
                        .format(new Date(cur.getLong(1)));
                add(out, "event", String.valueOf(cur.getLong(2)), "Event · " + when + "  " + cur.getString(0));
            }
        }
    }

    private static void files(Context c, String q, List<AppRepo.AppEntry> out) {
        Uri files = MediaStore.Files.getContentUri("external");
        try (Cursor cur = c.getContentResolver().query(files,
                new String[]{MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME},
                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ? AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + " != 0",
                new String[]{"%" + q + "%"}, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")) {
            int n = 0;
            while (cur != null && cur.moveToNext() && n++ < PER_SOURCE) {
                add(out, "file", ContentUris.withAppendedId(files, cur.getLong(0)).toString(), "File · " + cur.getString(1));
            }
        }
    }
}
