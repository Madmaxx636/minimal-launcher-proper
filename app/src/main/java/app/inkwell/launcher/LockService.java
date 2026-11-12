package app.inkwell.launcher;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/** Only used to call the system "lock screen" / "notifications" global actions. Reads nothing. */
public class LockService extends AccessibilityService {
    private static volatile LockService instance;

    public static boolean isRunning() { return instance != null; }

    public static boolean lock() {
        LockService s = instance;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && s != null && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    public static boolean notifications() {
        LockService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    @Override protected void onServiceConnected() { instance = this; }
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
}
