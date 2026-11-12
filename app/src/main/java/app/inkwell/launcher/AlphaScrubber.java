package app.inkwell.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

/** The A-Z strip down the right edge. Touch or drag to jump; letters with no apps are dimmed. */
public class AlphaScrubber extends View {
    public interface Listener {
        void onLetter(char c);
        void onRelease();
    }

    private String letters = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** The letters to show: the alphabet of the phone's language, plus any other initials your apps start with. */
    public static String lettersFor(Set<Character> present) {
        String lang = java.util.Locale.getDefault().getLanguage();
        String base;
        switch (lang) {
            case "ru": case "uk": case "bg": case "be": case "sr": case "mk": case "kk":
                base = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ"; break;
            case "el": base = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ"; break;
            default: base = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        }
        java.util.TreeSet<Character> extra = new java.util.TreeSet<>();
        for (char c : present) if (c != '#' && base.indexOf(c) < 0) extra.add(c);
        StringBuilder sb = new StringBuilder("#").append(base);
        int n = 0;
        for (char c : extra) if (n++ < 12) sb.append(c);
        return sb.toString();
    }

    public void setLetters(String s) { letters = s; invalidate(); }
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Set<Character> present = new HashSet<>();
    private Listener listener;
    private char current;
    private boolean edge;
    private Runnable doubleTap;
    private long lastUp;

    public AlphaScrubber(Context c) {
        super(c);
        applyStyle();
    }

    public void setListener(Listener l) { listener = l; }
    /** Edge mode: draws nothing. It is the invisible strip on home whose drag opens the app list. */
    public void setEdge(boolean e) { edge = e; }
    public void setDoubleTap(Runnable r) { doubleTap = r; }

    public void setPresent(Set<Character> s) { present = s; invalidate(); }

    public void applyStyle() {
        p.setTypeface(Prefs.typeface());
        p.setTextAlign(Paint.Align.CENTER);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (edge) return;
        float h = getHeight() / (float) letters.length();
        p.setTextSize(Math.min(h * 0.7f, Prefs.sp(getContext(), 13)));
        Paint.FontMetrics fm = p.getFontMetrics();
        for (int i = 0; i < letters.length(); i++) {
            char l = letters.charAt(i);
            p.setColor((Prefs.fg() & 0x00FFFFFF) | (present.contains(l) ? 0xFF000000 : 0x55000000));
            c.drawText(String.valueOf(l), getWidth() / 2f, i * h + h / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                getParent().requestDisallowInterceptTouchEvent(true);
                int i = (int) (e.getY() / (getHeight() / (float) letters.length()));
                i = Math.max(0, Math.min(letters.length() - 1, i));
                char l = letters.charAt(i);
                if (l != current) {
                    current = l;
                    if (listener != null) listener.onLetter(l);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                current = 0;
                if (listener != null) listener.onRelease();
                if (e.getActionMasked() == MotionEvent.ACTION_UP && doubleTap != null) {
                    long now = e.getEventTime();
                    if (now - lastUp <= android.view.ViewConfiguration.getDoubleTapTimeout()) { lastUp = 0; doubleTap.run(); }
                    else lastUp = now;
                }
                return true;
        }
        return true;
    }
}
