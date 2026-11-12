package app.inkwell.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.provider.CalendarContract;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import java.lang.ref.WeakReference;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The launcher's own widgets, which work offline and are drawn in your theme. They sit in the same
 * slots and stacks as Android widgets. Their ids are negative: -(type + 10 * n).
 */
final class BuiltinWidgets {
    static final int AGENDA = 1, MONTH = 2, MUSIC = 3, BATTERY = 4, NOTES = 5, ANALOG = 6, WORDS = 7;
    static final String[] NAMES = {"", "Agenda (calendar events)", "Month calendar", "Music player", "Battery",
            "Notes", "Analog clock", "Words clock"};
    private static final int[] HEIGHT_DP = {0, 130, 270, 84, 76, 150, 230, 96};
    private static final List<WeakReference<View>> live = new ArrayList<>();

    private BuiltinWidgets() {}

    static int type(int id) { return (-id) % 10; }

    static int newId(int type) {
        int n = Prefs.getInt("builtinSeq", 0) + 1;
        Prefs.put("builtinSeq", n);
        return -(type + 10 * n);
    }

    static String label(int id) { return NAMES[type(id)]; }

    static int defaultHeightDp(int id) { return HEIGHT_DP[type(id)]; }

    static void forget(int id) { Prefs.put("note_" + id, ""); }

    /** Redraws every live built-in widget: call each minute and when notifications or music change. */
    static void refreshAll() {
        for (int i = live.size() - 1; i >= 0; i--) {
            View v = live.get(i).get();
            if (v == null) { live.remove(i); continue; }
            if (v instanceof AgendaView) ((AgendaView) v).refresh();
            else if (v instanceof MediaBar) ((MediaBar) v).refresh();
            else if (v instanceof ClockView) ((ClockView) v).setState(true, System.currentTimeMillis(), null);
            else v.invalidate();
        }
    }

    static View create(Context c, int id) {
        View v;
        switch (type(id)) {
            case AGENDA: {
                AgendaView a = new AgendaView(c);
                a.setWidget(true);
                a.setOnTap(() -> openCalendar(c));
                a.refresh();
                v = a;
                break;
            }
            case MONTH: v = new MonthView(c); break;
            case MUSIC: {
                MediaBar m = new MediaBar(c);
                m.setAlways(true);
                m.refresh();
                v = m;
                break;
            }
            case BATTERY: v = new BatteryView(c); break;
            case NOTES: v = new NotesView(c, id); break;
            case ANALOG:
            case WORDS: {
                ClockView k = new ClockView(c);
                k.setStyleOverride(type(id) == ANALOG ? "analog" : "words");
                k.setState(true, System.currentTimeMillis(), null);
                v = k;
                break;
            }
            default: return null;
        }
        live.add(new WeakReference<>(v));
        return v;
    }

    static void openCalendar(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    // ---- month calendar ----

    static final class MonthView extends View {
        private final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        MonthView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawColor(Prefs.bg());
            float k = Prefs.scalePct() / 100f, pad = Prefs.dp(getContext(), 16), w = getWidth(), h = getHeight();
            int fg = Prefs.fg(), bg = Prefs.bg();
            p.setTypeface(Prefs.typeface());
            p.setColor(fg);
            Calendar now = Calendar.getInstance();
            float titleSize = Prefs.sp(getContext(), 17) * k;
            p.setTextSize(titleSize);
            cv.drawText(new SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(now.getTime()), pad, titleSize * 1.1f, p);
            int first = now.getFirstDayOfWeek();
            String[] names = new DateFormatSymbols().getShortWeekdays();
            float small = Prefs.sp(getContext(), 12) * k;
            p.setTextSize(small);
            p.setTextAlign(Paint.Align.CENTER);
            float cellW = (w - pad * 2) / 7f, wdY = titleSize * 1.1f + small * 1.9f;
            for (int i = 0; i < 7; i++) {
                String n = names[((first - 1 + i) % 7) + 1];
                cv.drawText(n.substring(0, 1).toUpperCase(Locale.getDefault()), pad + cellW * i + cellW / 2, wdY, p);
            }
            Calendar c1 = (Calendar) now.clone();
            c1.set(Calendar.DAY_OF_MONTH, 1);
            int offset = (c1.get(Calendar.DAY_OF_WEEK) - first + 7) % 7;
            int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
            int rows = (offset + days + 6) / 7;
            float top = wdY + small * 0.6f, cellH = (h - top) / rows;
            float dayText = Prefs.sp(getContext(), 14) * k;
            p.setTextSize(dayText);
            fill.setColor(fg);
            for (int d = 1; d <= days; d++) {
                int col = (offset + d - 1) % 7, row = (offset + d - 1) / 7;
                float cx = pad + cellW * col + cellW / 2, cy = top + cellH * row + cellH / 2;
                boolean today = d == now.get(Calendar.DAY_OF_MONTH);
                if (today) cv.drawCircle(cx, cy, Math.min(cellW, cellH) * 0.44f, fill);
                p.setColor(today ? bg : fg);
                cv.drawText(String.valueOf(d), cx, cy + dayText * 0.35f, p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) openCalendar(getContext());
            return true;
        }
    }

    // ---- battery ----

    static final class BatteryView extends View {
        private final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);

        BatteryView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawColor(Prefs.bg());
            Intent b = getContext().registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = 0;
            boolean charging = false;
            if (b != null) {
                int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                level = l < 0 ? 0 : l * 100 / s;
                int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            }
            float k = Prefs.scalePct() / 100f, pad = Prefs.dp(getContext(), 16);
            p.setTypeface(Prefs.typeface());
            p.setColor(Prefs.fg());
            float size = Prefs.sp(getContext(), 20) * k;
            p.setTextSize(size);
            cv.drawText("Battery  " + level + "%" + (charging ? "  · charging" : ""), pad, size * 1.2f, p);
            float top = size * 1.7f, hgt = Prefs.dp(getContext(), 14), w = getWidth() - pad * 2;
            bar.setColor(Prefs.fg());
            bar.setStyle(Paint.Style.STROKE);
            bar.setStrokeWidth(Prefs.dp(getContext(), 2));
            cv.drawRect(pad, top, pad + w, top + hgt, bar);
            bar.setStyle(Paint.Style.FILL);
            cv.drawRect(pad, top, pad + w * level / 100f, top + hgt, bar);
        }
    }

    // ---- notes ----

    static final class NotesView extends View {
        private final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final int id;

        NotesView(Context c, int id) {
            super(c);
            this.id = id;
        }

        private String text() { return Prefs.getStr("note_" + id, ""); }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawColor(Prefs.bg());
            float k = Prefs.scalePct() / 100f, pad = Prefs.dp(getContext(), 16);
            p.setTypeface(Prefs.typeface());
            String t = text();
            p.setColor(t.isEmpty() ? (Prefs.fg() & 0x00FFFFFF) | 0x88000000 : Prefs.fg());
            p.setTextSize(Prefs.sp(getContext(), 16) * k);
            StaticLayout l = StaticLayout.Builder.obtain(t.isEmpty() ? "Tap to write a note" : t, 0,
                    t.isEmpty() ? 19 : t.length(), p, (int) (getWidth() - pad * 2)).setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
            cv.save();
            cv.translate(pad, Prefs.dp(getContext(), 8));
            l.draw(cv);
            cv.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP || !(getContext() instanceof Activity)) return true;
            final EditText et = new EditText(getContext());
            et.setText(text());
            et.setMinLines(3);
            et.setGravity(android.view.Gravity.TOP);
            new AlertDialog.Builder(getContext()).setTitle("Note").setView(et)
                    .setPositiveButton("Save", (d, w) -> {
                        Prefs.put("note_" + id, et.getText().toString());
                        invalidate();
                    }).setNegativeButton("Cancel", null).show();
            return true;
        }
    }
}
