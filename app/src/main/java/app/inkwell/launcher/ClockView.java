package app.inkwell.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The clock block on home, in one of several styles. A view of its own so widgets can sit above
 * or below it. Tapping the time opens the clock app; tapping the date opens the calendar.
 */
public class ClockView extends View {
    public static final String[] STYLE_IDS = {"classic", "large", "compact", "date", "analog", "words"};
    public static final String[] STYLE_NAMES = {
            "Classic: time, then date", "Large: very big time", "Compact: one line",
            "Date first: weekday leads", "Analog: dial with hands", "Words: \"quarter past nine\""};

    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mid = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long millis;
    private String battery;
    private Runnable onLongPress, onTimeTap, onDateTap;
    private float splitY;
    private boolean upperIsTime = true;
    private boolean fired;
    private final Runnable longPress = () -> {
        fired = true;
        if (onLongPress != null) onLongPress.run();
    };

    public ClockView(Context c) { super(c); }

    public void setActions(Runnable longPress, Runnable timeTap, Runnable dateTap) {
        onLongPress = longPress;
        onTimeTap = timeTap;
        onDateTap = dateTap;
    }

    public void setState(boolean show, long now, String batteryText) {
        millis = now;
        battery = batteryText;
        setVisibility(show ? VISIBLE : GONE);
        requestLayout();
        invalidate();
    }

    // ---- text ----

    private String styleOverride;

    /** Lets a clock widget keep its own style whatever the home clock is set to. */
    public void setStyleOverride(String s) { styleOverride = s; }

    private String style() { return styleOverride != null ? styleOverride : Prefs.getStr(Prefs.CLOCK_STYLE, "classic"); }

    private String timeText() {
        return new SimpleDateFormat(Prefs.getBool(Prefs.CLOCK24, true) ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date(millis));
    }

    private String pattern(String skeleton) {
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault()).format(new Date(millis));
    }

    /** Date and battery, as the small line under the time. Null when both are off. */
    private String infoLine(boolean withDate) {
        List<String> bits = new ArrayList<>();
        if (withDate && Prefs.getBool(Prefs.DATE, true)) bits.add(java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(new Date(millis)));
        if (battery != null) bits.add(battery);
        return bits.isEmpty() ? null : android.text.TextUtils.join("  ·  ", bits);
    }

    static String words(int hour24, int minute) {
        String[] h = {"twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven"};
        int hr = hour24 % 12, m = Math.round(minute / 5f) * 5;
        if (m == 60) { hr = (hr + 1) % 12; m = 0; }
        String next = h[(hr + 1) % 12];
        switch (m) {
            case 0: return h[hr] + " o'clock";
            case 5: return "five past " + h[hr];
            case 10: return "ten past " + h[hr];
            case 15: return "quarter past " + h[hr];
            case 20: return "twenty past " + h[hr];
            case 25: return "twenty-five past " + h[hr];
            case 30: return "half past " + h[hr];
            case 35: return "twenty-five to " + next;
            case 40: return "twenty to " + next;
            case 45: return "quarter to " + next;
            case 50: return "ten to " + next;
            default: return "five to " + next;
        }
    }

    // ---- sizes ----

    private float clockPx() {
        return Prefs.sp(getContext(), 22) * Prefs.scalePct() / 100f * 1.9f * Prefs.getInt(Prefs.CLOCK_SCALE, 100) / 100f;
    }

    private float smallPx() {
        float list = Prefs.sp(getContext(), 22) * Prefs.scalePct() / 100f * Prefs.getInt(Prefs.LIST_SCALE, 100) / 100f;
        return Math.max(Prefs.sp(getContext(), 13), list * 0.55f);
    }

    private float dp(float v) { return Prefs.dp(getContext(), v); }

    private float dial() { return clockPx() * 2.4f; }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) height());
    }

    private float height() {
        boolean line = infoLine(true) != null;
        switch (style()) {
            case "large": return clockPx() * 1.8f * 1.1f + (line ? smallPx() * 1.7f : 0) + dp(8);
            case "compact": return clockPx() * 0.9f * 1.6f + dp(8);
            case "date": return clockPx() * 0.95f * 1.15f + clockPx() * 0.5f * 1.25f + smallPx() * 1.7f + dp(8);
            case "analog": return dial() + dp(16) + (line ? smallPx() * 1.7f : 0);
            case "words": return clockPx() * 0.75f * 1.4f + (line ? smallPx() * 1.7f : 0) + dp(8);
            default: return clockPx() * 1.1f + (line ? smallPx() * 1.7f : 0) + dp(8);
        }
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Prefs.bg());
        int fg = Prefs.fg();
        float pad = dp(Prefs.getInt(Prefs.MARGIN, 24));
        for (TextPaint p : new TextPaint[]{big, mid, small}) {
            p.setTypeface(Prefs.typeface());
            p.setColor(fg);
        }
        small.setTextSize(smallPx());
        String line = infoLine(true);
        switch (style()) {
            case "large": drawStacked(c, timeText(), clockPx() * 1.8f, line, pad); break;
            case "compact": drawCompact(c, pad); break;
            case "date": drawDate(c, pad); break;
            case "analog": drawAnalog(c, line, pad); break;
            case "words": drawWords(c, line, pad); break;
            default: drawStacked(c, timeText(), clockPx(), line, pad);
        }
    }

    private void drawStacked(Canvas c, String time, float size, String line, float pad) {
        big.setTextSize(size);
        float top = size + dp(4);
        c.drawText(time, x(big.measureText(time), pad), top, big);
        if (line != null) c.drawText(line, x(small.measureText(line), pad), top + smallPx() * 1.5f, small);
        splitY = size + dp(12);
        upperIsTime = true;
    }

    private void drawCompact(Canvas c, float pad) {
        List<String> bits = new ArrayList<>();
        bits.add(timeText());
        if (Prefs.getBool(Prefs.DATE, true)) bits.add(pattern("EEEdMMM"));
        if (battery != null) bits.add(battery);
        String s = android.text.TextUtils.join("   ·   ", bits);
        float size = clockPx() * 0.9f;
        mid.setTextSize(size);
        while (mid.measureText(s) > getWidth() - pad * 2 && size > dp(12)) mid.setTextSize(size -= dp(1));
        c.drawText(s, x(mid.measureText(s), pad), size * 1.2f, mid);
        splitY = getHeight();
        upperIsTime = true;
    }

    private void drawDate(Canvas c, float pad) {
        float bigSize = clockPx() * 0.95f, midSize = clockPx() * 0.5f;
        String weekday = pattern("EEEE"), day = pattern("dMMMM");
        big.setTextSize(bigSize);
        mid.setTextSize(midSize);
        float y = bigSize * 1.0f;
        c.drawText(weekday, x(big.measureText(weekday), pad), y, big);
        y += midSize * 1.25f;
        c.drawText(day, x(mid.measureText(day), pad), y, mid);
        splitY = y + dp(8);
        upperIsTime = false; // date lines above, so tapping them opens the calendar
        List<String> bits = new ArrayList<>();
        bits.add(timeText());
        if (battery != null) bits.add(battery);
        String s = android.text.TextUtils.join("  ·  ", bits);
        c.drawText(s, x(small.measureText(s), pad), y + smallPx() * 1.5f + dp(4), small);
    }

    private void drawWords(Canvas c, String line, float pad) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        String s = words(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        float size = clockPx() * 0.75f;
        big.setTextSize(size);
        while (big.measureText(s) > getWidth() - pad * 2 && size > dp(14)) big.setTextSize(size -= dp(1));
        float top = clockPx() * 0.75f + dp(4);
        c.drawText(s, x(big.measureText(s), pad), top, big);
        if (line != null) c.drawText(line, x(small.measureText(line), pad), top + smallPx() * 1.5f, small);
        splitY = clockPx() * 0.75f * 1.4f;
        upperIsTime = true;
    }

    private void drawAnalog(Canvas c, String line, float pad) {
        float d = dial(), r = d / 2f;
        float cx, cy = dp(8) + r;
        switch (Prefs.align()) {
            case "center": cx = getWidth() / 2f; break;
            case "right": cx = getWidth() - pad - r; break;
            default: cx = pad + r;
        }
        stroke.setColor(Prefs.fg());
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(dp(2.5f));
        c.drawCircle(cx, cy, r - stroke.getStrokeWidth(), stroke);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            float inner = r * (i % 3 == 0 ? 0.80f : 0.88f), outer = r * 0.94f;
            stroke.setStrokeWidth(dp(i % 3 == 0 ? 3f : 1.5f));
            c.drawLine(cx + (float) Math.sin(a) * inner, cy - (float) Math.cos(a) * inner,
                    cx + (float) Math.sin(a) * outer, cy - (float) Math.cos(a) * outer, stroke);
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        int m = cal.get(Calendar.MINUTE), h = cal.get(Calendar.HOUR);
        hand(c, cx, cy, r * 0.52f, (h * 30 + m * 0.5f), dp(6));
        hand(c, cx, cy, r * 0.80f, m * 6f, dp(3.5f));
        stroke.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, dp(5), stroke);
        if (line != null) {
            small.setTextSize(smallPx());
            c.drawText(line, x(small.measureText(line), pad), dp(8) + d + smallPx() * 1.3f + dp(4), small);
        }
        splitY = dp(8) + d;
        upperIsTime = true;
    }

    private void hand(Canvas c, float cx, float cy, float len, float degrees, float width) {
        double a = Math.toRadians(degrees);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(width);
        c.drawLine(cx, cy, cx + (float) Math.sin(a) * len, cy - (float) Math.cos(a) * len, stroke);
    }

    private float x(float w, float pad) {
        switch (Prefs.align()) {
            case "center": return (getWidth() - w) / 2f;
            case "right": return getWidth() - pad - w;
            default: return pad;
        }
    }

    // ---- touch ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPress);
                if (fired) return true;
                boolean upper = e.getY() < splitY;
                Runnable r = upper == upperIsTime ? onTimeTap : onDateTap;
                if (r != null) r.run();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                return true;
        }
        return true;
    }
}
