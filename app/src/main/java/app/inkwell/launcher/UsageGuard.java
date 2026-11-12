package app.inkwell.launcher;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.Calendar;
import java.util.List;

/** Usage Breaker: how long an app has been in the foreground today, read from Android's own usage stats. */
final class UsageGuard {
    private UsageGuard() {}

    static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** Minutes in the foreground since midnight, or -1 if usage access isn't granted. */
    static long minutesToday(Context c, String pkg) {
        if (!hasAccess(c)) return -1;
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        UsageStatsManager u = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> l = u.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start.getTimeInMillis(), System.currentTimeMillis());
        long ms = 0;
        if (l != null) for (UsageStats s : l) if (s.getPackageName().equals(pkg)) ms += s.getTotalTimeInForeground();
        return ms / 60000;
    }
}
