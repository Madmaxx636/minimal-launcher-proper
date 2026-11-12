package app.inkwell.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.CalendarContract;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Next few calendar events under the clock. Read from the on-device calendar only; nothing is stored. */
public class AgendaView extends View {
    private int maxRows = 3;
    private boolean widget;
    private final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> rows = new ArrayList<>();
    private Runnable onTap;

    public AgendaView(Context c) { super(c); }

    public void setOnTap(Runnable r) { onTap = r; }

    /** Widget mode: always shown, up to five events, and it explains itself when it can't show any. */
    public void setWidget(boolean w) {
        widget = w;
        maxRows = w ? 5 : 3;
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public void refresh() {
        rows.clear();
        boolean perm = hasPermission(getContext());
        if (widget) {
            if (!perm) rows.add("Tap to allow calendar access");
            else {
                load();
                if (rows.isEmpty()) rows.add("No events in the next two days");
            }
            setVisibility(VISIBLE);
        } else {
            if (Prefs.getBool(Prefs.AGENDA, false) && perm) load();
            setVisibility(rows.isEmpty() ? GONE : VISIBLE);
        }
        requestLayout();
        invalidate();
    }

    private void load() {
        long now = System.currentTimeMillis();
        android.net.Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now);
        ContentUris.appendId(b, now + 48L * 3600 * 1000);
        Calendar today = Calendar.getInstance();
        try (Cursor c = getContext().getContentResolver().query(b.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY},
                null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext() && rows.size() < maxRows) {
                String title = c.getString(0);
                long begin = c.getLong(1);
                boolean allDay = c.getInt(2) == 1;
                Calendar when = Calendar.getInstance();
                when.setTimeInMillis(begin);
                boolean sameDay = when.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && when.get(Calendar.YEAR) == today.get(Calendar.YEAR);
                String time = allDay ? "All day" : new SimpleDateFormat(Prefs.getBool(Prefs.CLOCK24, true) ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date(begin));
                String day = sameDay ? "" : new SimpleDateFormat("EEE ", Locale.getDefault()).format(new Date(begin));
                rows.add(day + time + "   " + (title == null || title.isEmpty() ? "(no title)" : title));
            }
        } catch (SecurityException | IllegalArgumentException ignored) { }
    }

    private float size() {
        return Math.max(Prefs.sp(getContext(), 14), Prefs.sp(getContext(), 22) * Prefs.scalePct() / 100f * Prefs.getInt(Prefs.LIST_SCALE, 100) / 100f * 0.62f);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (rows.size() * size() * 1.55f + Prefs.dp(getContext(), 8)));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Prefs.bg());
        p.setTypeface(Prefs.typeface());
        p.setColor(Prefs.fg());
        p.setTextSize(size());
        float pad = Prefs.dp(getContext(), Prefs.getInt(Prefs.MARGIN, 24));
        for (int i = 0; i < rows.size(); i++) {
            String s = TextUtils.ellipsize(rows.get(i), p, getWidth() - pad * 2, TextUtils.TruncateAt.END).toString();
            float x;
            switch (Prefs.align()) {
                case "center": x = (getWidth() - p.measureText(s)) / 2f; break;
                case "right": x = getWidth() - pad - p.measureText(s); break;
                default: x = pad;
            }
            c.drawText(s, x, (i + 1) * size() * 1.55f - size() * 0.2f, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        if (widget && !hasPermission(getContext())) {
            getContext().startActivity(new android.content.Intent(getContext(), SettingsActivity.class)); // the agenda row asks for access
        } else if (onTap != null) {
            onTap.run();
        }
        return true;
    }
}
