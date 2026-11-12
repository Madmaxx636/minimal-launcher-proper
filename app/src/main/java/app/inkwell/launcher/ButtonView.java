package app.inkwell.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * The Niagara-style quick button: tap runs one action, long-press edits it, and a swipe up runs a second
 * action. Its shape, border, fill and contents are all settings, and the contents can be left empty.
 */
public class ButtonView extends View {
    public static final String[] SHAPE_IDS = {"circle", "rounded", "square"};
    public static final String[] SHAPE_NAMES = {"Circle", "Rounded square", "Square"};
    public static final String[] BORDER_IDS = {"none", "thin", "normal", "thick"};
    public static final String[] BORDER_NAMES = {"None", "Thin", "Normal", "Thick"};
    public static final String[] FILL_IDS = {"theme", "inverse", "none"};
    public static final String[] FILL_NAMES = {"Theme background", "Inverted (text color)", "See-through"};
    public static final String[] CONTENT_IDS = {"icon", "word", "none"};
    public static final String[] CONTENT_NAMES = {"The app's icon", "A short word of my own", "Nothing, just the shape"};

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF box = new RectF();
    private final IconStyle icons;
    private String target = "";
    private Runnable onTap, onLong, onSwipeUp;
    private float downX, downY;
    private boolean longFired;
    private final Runnable longPress = () -> {
        longFired = true;
        if (onLong != null) onLong.run();
    };

    public ButtonView(Context c) {
        super(c);
        icons = new IconStyle(c);
        ring.setStyle(Paint.Style.STROKE);
    }

    public void setActions(Runnable tap, Runnable longPress, Runnable swipeUp) {
        onTap = tap;
        onLong = longPress;
        onSwipeUp = swipeUp;
    }

    public void setTarget(String t) {
        target = t == null ? "" : t;
        invalidate();
    }

    public static int sizePx(Context c) {
        return (int) (Prefs.dp(c, Prefs.getInt(Prefs.BTN_SIZE, 56)) * Prefs.scalePct() / 100f);
    }

    private float borderPx() {
        switch (Prefs.getStr(Prefs.BTN_BORDER, "normal")) {
            case "none": return 0;
            case "thin": return Prefs.dp(getContext(), 1);
            case "thick": return Prefs.dp(getContext(), 4);
            default: return Prefs.dp(getContext(), 2);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        int fg = Prefs.fg(), bg = Prefs.bg();
        String fillMode = Prefs.getStr(Prefs.BTN_FILL, "theme");
        int ink = fillMode.equals("inverse") ? bg : fg;
        float bw = borderPx(), half = bw / 2f;

        box.set(half, half, s - half, s - half);
        shape.reset();
        switch (Prefs.getStr(Prefs.BTN_SHAPE, "circle")) {
            case "rounded": shape.addRoundRect(box, s * 0.28f, s * 0.28f, Path.Direction.CW); break;
            case "square": shape.addRect(box, Path.Direction.CW); break;
            default: shape.addCircle(s / 2f, s / 2f, s / 2f - half, Path.Direction.CW);
        }
        if (!fillMode.equals("none")) {
            fill.setColor(fillMode.equals("inverse") ? fg : bg);
            c.drawPath(shape, fill);
        }
        if (bw > 0) {
            ring.setColor(fg);
            ring.setStrokeWidth(bw);
            c.drawPath(shape, ring);
        }

        String content = Prefs.getStr(Prefs.BTN_CONTENT, "icon");
        if (content.equals("none")) return; // an empty button: just the shape
        if (content.equals("word")) {
            drawText(c, s, ink, Prefs.getStr(Prefs.BTN_WORD, ""));
            return;
        }
        boolean action = ButtonTargets.isAction(target);
        AppRepo.AppEntry app = target.isEmpty() || Folders.isFolder(target) || action ? null : AppRepo.find(ButtonTargets.packageOf(target));
        Bitmap icon = app == null ? null : AppRepo.icon(app, (int) (s * 0.55f));
        if (icon != null) {
            icons.reload();
            icons.drawIcon(c, icon, (s - icon.getWidth()) / 2f, (s - icon.getHeight()) / 2f, icon.getWidth(), ink);
            return;
        }
        drawText(c, s, ink, target.isEmpty() ? "+" : Folders.isFolder(target) ? "▸" : action ? ButtonTargets.glyph(target) : "?");
    }

    private void drawText(Canvas c, float s, int ink, String text) {
        if (text == null || text.isEmpty()) return;
        glyph.setColor(ink);
        glyph.setTextAlign(Paint.Align.CENTER);
        glyph.setTypeface(Prefs.typeface());
        glyph.setTextSize(s * (text.length() > 1 ? 0.26f : 0.45f)); // short words are drawn smaller than a single sign
        float max = s * 0.78f;
        while (glyph.measureText(text) > max && glyph.getTextSize() > 8) glyph.setTextSize(glyph.getTextSize() - 1);
        Paint.FontMetrics fm = glyph.getFontMetrics();
        c.drawText(text, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, glyph);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longFired = false;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) removeCallbacks(longPress);
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPress);
                if (longFired) return true;
                boolean up = downY - e.getY() > Prefs.dp(getContext(), 40) && Math.abs(e.getX() - downX) < Math.abs(e.getY() - downY);
                Runnable r = up ? onSwipeUp : onTap;
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
