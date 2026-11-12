package app.inkwell.launcher;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the active notifications and the current media session in memory so the home screen can
 * show dots, a notifications panel and music controls. Nothing is written to disk or sent anywhere.
 */
public class NotifService extends NotificationListenerService {
    public static final class Action {
        public final String title;
        public final PendingIntent intent;

        Action(String title, PendingIntent intent) {
            this.title = title;
            this.intent = intent;
        }
    }

    public static final class Item {
        public String key, pkg, title, text;
        public long when;
        public PendingIntent content;
        public boolean autoCancel, clearable;
        public List<Action> actions = new ArrayList<>();
    }

    private static final List<Item> items = new CopyOnWriteArrayList<>();
    private static volatile NotifService instance;
    public static volatile MediaController media;
    private static volatile List<MediaController> allSessions = Collections.emptyList();
    /** The cover on each player's media notification, kept in memory as a fallback when the session has no artwork. */
    private static volatile Map<String, Bitmap> notificationArt = Collections.emptyMap();

    public static Bitmap artFor(String pkg) { return notificationArt.get(pkg); }

    /**
     * After an app update Android can leave notification access switched on but never reconnect the
     * listener, so no music or notifications arrive. Asking it to rebind fixes that.
     */
    public static void requestRebindIfNeeded(Context c) {
        if (!isEnabled(c) || instance != null) return;
        try {
            NotificationListenerService.requestRebind(new ComponentName(c, NotifService.class));
        } catch (Exception ignored) { }
    }

    /** Plain words for the settings screen: what the launcher can currently see of your music. */
    public static String mediaStatus(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Notification access: ").append(isEnabled(c) ? "on" : "OFF (turn it on in Notification access)");
        sb.append("\nListener connected: ").append(instance != null ? "yes" : "NO (open this screen again, or switch access off and on)");
        List<MediaController> l = allSessions;
        sb.append("\nMusic sessions found: ").append(l.size());
        for (MediaController m : l) sb.append("\n  - ").append(m.getPackageName()).append(" (").append(stateName(m.getPlaybackState())).append(")");
        sb.append("\nShowing: ").append(media == null ? "nothing" : media.getPackageName());
        sb.append("\nArtwork: ").append(MediaBar.artSource);
        return sb.toString();
    }

    private static String stateName(PlaybackState s) {
        if (s == null) return "no state";
        switch (s.getState()) {
            case PlaybackState.STATE_PLAYING: return "playing";
            case PlaybackState.STATE_PAUSED: return "paused";
            case PlaybackState.STATE_BUFFERING: return "buffering";
            case PlaybackState.STATE_STOPPED: return "stopped";
            default: return "state " + s.getState();
        }
    }

    /** True while an app has a media session that is playing, buffering or paused. */
    /** Apps that have music going (playing, buffering or paused), playing ones first, at most max of them. */
    public static List<MediaController> activeSessions(int max) {
        List<MediaController> playing = new ArrayList<>(), paused = new ArrayList<>();
        for (MediaController c : allSessions) {
            PlaybackState s = c.getPlaybackState();
            if (s == null) continue;
            int st = s.getState();
            if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) playing.add(c);
            else if (st == PlaybackState.STATE_PAUSED) paused.add(c);
        }
        playing.addAll(paused);
        MediaController one = media;
        if (playing.isEmpty() && one != null) playing.add(one); // a player that has a session but reports no state yet
        return playing.size() > max ? new ArrayList<>(playing.subList(0, max)) : playing;
    }

    public static boolean isActive(String pkg) {
        for (MediaController c : allSessions) {
            if (!pkg.equals(c.getPackageName())) continue;
            PlaybackState s = c.getPlaybackState();
            if (s == null) continue;
            int st = s.getState();
            if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_BUFFERING) return true;
        }
        return false;
    }
    public static volatile Runnable onChange;

    private MediaSessionManager sessions;
    private final List<MediaController> registered = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { fire(); }
        @Override public void onMetadataChanged(android.media.MediaMetadata m) { fire(); }
        @Override public void onSessionDestroyed() { refreshSessions(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pickMedia;

    // ---- queries ----

    public static int count(String pkg) {
        int n = 0;
        for (Item i : items) if (i.pkg.equals(pkg)) n++;
        return n;
    }

    /** Newest first. A null package means every app. */
    public static List<Item> items(String pkg) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (pkg == null || i.pkg.equals(pkg)) out.add(i);
        Collections.sort(out, (a, b) -> Long.compare(b.when, a.when));
        return out;
    }

    public static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    public static void dismiss(String key) {
        NotifService s = instance;
        if (s != null) {
            try { s.cancelNotification(key); } catch (Exception ignored) { }
        }
    }

    public static void dismissAll(String pkg) {
        for (Item i : items(pkg)) if (i.clearable) dismiss(i.key);
    }

    // ---- listener callbacks ----

    @Override
    public void onListenerConnected() {
        instance = this;
        recount();
        try {
            sessions = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            sessions.addOnActiveSessionsChangedListener(sessionsListener, new ComponentName(this, NotifService.class));
            refreshSessions();
        } catch (SecurityException ignored) { }
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        items.clear();
        media = null;
        fire();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { recount(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { recount(); }

    private void recount() {
        List<Item> next = new ArrayList<>();
        Map<String, Bitmap> art = new HashMap<>();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification n : active) {
                    try { captureArt(n, art); } catch (Exception ignored) { }
                    Item it = build(n);
                    if (it != null) next.add(it);
                }
            }
        } catch (Exception ignored) { }
        items.clear();
        items.addAll(next);
        notificationArt = art;
        fire();
    }

    /** Media notifications are not listed as notifications, but their large icon is usually the cover. */
    private void captureArt(StatusBarNotification sbn, Map<String, Bitmap> art) {
        Notification n = sbn.getNotification();
        boolean media = Notification.CATEGORY_TRANSPORT.equals(n.category)
                || "android.app.Notification$MediaStyle".equals(n.extras.getString(Notification.EXTRA_TEMPLATE));
        if (!media || art.containsKey(sbn.getPackageName())) return;
        Icon icon = n.getLargeIcon();
        if (icon == null) return;
        try {
            Drawable d = icon.loadDrawable(this);
            if (d == null) return;
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                art.put(sbn.getPackageName(), ((BitmapDrawable) d).getBitmap());
                return;
            }
            Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 256, 256);
            d.draw(new Canvas(b));
            art.put(sbn.getPackageName(), b);
        } catch (Exception ignored) { }
    }

    private static Item build(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return null;
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return null;
        Bundle e = n.extras;
        CharSequence title = e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null || text.length() == 0) text = e.getCharSequence(Notification.EXTRA_TEXT);
        if (text == null || text.length() == 0) {
            CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) text = android.text.TextUtils.join("\n", lines);
        }
        boolean noTitle = title == null || title.length() == 0;
        boolean noText = text == null || text.length() == 0;
        if (noTitle && noText) return null;

        Item it = new Item();
        it.key = sbn.getKey();
        it.pkg = sbn.getPackageName();
        it.title = noTitle ? "" : title.toString();
        it.text = noText ? "" : text.toString();
        it.when = sbn.getPostTime();
        it.content = n.contentIntent;
        it.autoCancel = (n.flags & Notification.FLAG_AUTO_CANCEL) != 0;
        it.clearable = sbn.isClearable();
        if (n.actions != null) {
            for (Notification.Action a : n.actions) {
                // Reply-style actions need a text box the panel doesn't have.
                if (a.actionIntent == null || a.title == null) continue;
                if (a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) continue;
                if (it.actions.size() < 3) it.actions.add(new Action(a.title.toString(), a.actionIntent));
            }
        }
        return it;
    }

    // ---- media ----

    private void refreshSessions() {
        try {
            pickMedia(sessions.getActiveSessions(new ComponentName(this, NotifService.class)));
        } catch (Exception ignored) { }
    }

    private void pickMedia(List<MediaController> list) {
        for (MediaController old : registered) old.unregisterCallback(mediaCallback);
        registered.clear();
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(mediaCallback); // every app's changes matter, not only the one on show
                registered.add(c);
                PlaybackState s = c.getPlaybackState();
                if (best == null && s != null && s.getState() == PlaybackState.STATE_PLAYING) best = c;
            }
            if (best == null) {
                for (MediaController c : list) if (c.getMetadata() != null) { best = c; break; }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        allSessions = list == null ? Collections.<MediaController>emptyList() : new ArrayList<>(list);
        media = best;
        fire();
    }

    private void fire() {
        final Runnable r = onChange;
        if (r != null) main.post(r);
    }
}
