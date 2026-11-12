package app.inkwell.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * A list drawn straight to the canvas. No fling, no overscroll glow, no ripples, no animation:
 * content changes in whole pages (or whole rows), which is what e-ink panels refresh cleanly.
 */
public class AppListView extends View {
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    public abstract static class Listener {
        public void onClick(AppRepo.AppEntry a) {}
        public void onLongClick(AppRepo.AppEntry a) {}
        public void onSwipe(int dir) {}
        /** Swipe right starting on a row. Return true if handled, otherwise it counts as a plain swipe. */
        public boolean onRowSwipeRight(AppRepo.AppEntry a) { return false; }
        /** Swipe left starting on a row. */
        public boolean onRowSwipeLeft(AppRepo.AppEntry a) { return false; }
        public void onDoubleTapEmpty() {}
        public void onLongPressEmpty() {}
    }

    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint();
    private final Paint fill = new Paint();
    private final IconStyle iconStyle;

    private List<AppRepo.AppEntry> items = new ArrayList<>();
    private Listener listener;
    private char bubble;

    private int bg, fg, accent, scalePct;
    private boolean paged, dividers, upper, showIcons, badges;
    private String align, valign = "top";
    private float textPx, iconPx, rowH, pad;
    private int gapDp;

    // layout, recomputed by layout()
    private int perPage = 1;
    private float startY, footerH;
    private int first;

    // touch state
    private float downX, downY;
    private int startFirst, pressed = -1, downRow = -1, selected = -1;
    private boolean dragging, longFired;
    private long lastEmptyTap;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            longFired = true;
            if (pressed >= 0 && first + pressed < items.size()) {
                int idx = pressed;
                pressed = -1;
                invalidate();
                if (listener != null) listener.onLongClick(items.get(first + idx));
            } else if (listener != null) {
                listener.onLongPressEmpty();
            }
        }
    };

    public AppListView(Context c) {
        super(c);
        setFocusable(false);
        iconStyle = new IconStyle(c);
        applyStyle();
    }

    public void setListener(Listener l) { listener = l; }
    public void setValign(String v) { valign = v; invalidate(); }

    /** The row picked with the arrow keys of a physical keyboard, or -1 if none has been picked. */
    public void setSelected(int i) {
        selected = i;
        layout();
        if (i >= 0 && (i < first || i >= first + perPage)) {
            first = paged ? (i / perPage) * perPage : Math.max(0, i - perPage + 1);
            clampFirst();
        }
        invalidate();
    }

    public void moveSelection(int dir) {
        if (items.isEmpty()) return;
        setSelected(Math.max(0, Math.min(items.size() - 1, (selected < 0 ? -1 : selected) + dir)));
    }

    /** The row Enter would open: the one picked with the arrow keys, else the first. */
    public AppRepo.AppEntry chosen() {
        if (items.isEmpty()) return null;
        return items.get(selected >= 0 && selected < items.size() ? selected : 0);
    }

    public void setItems(List<AppRepo.AppEntry> l, boolean resetPosition) {
        items = l;
        if (resetPosition) { first = 0; selected = -1; }
        clampFirst();
        invalidate();
    }

    public void setBubble(char c) { bubble = c; invalidate(); }

    public void applyStyle() {
        bg = Prefs.bg();
        fg = Prefs.fg();
        accent = Prefs.accent();
        scalePct = Prefs.scalePct();
        paged = Prefs.getBool(Prefs.PAGED, true);
        dividers = Prefs.getBool(Prefs.DIVIDERS, true);
        upper = Prefs.getBool(Prefs.UPPER, false);
        showIcons = Prefs.getBool(Prefs.ICONS, false);
        badges = Prefs.getBool(Prefs.BADGES, true);
        align = Prefs.align();
        gapDp = Prefs.getInt(Prefs.GAP, 6);
        Context c = getContext();
        textPx = Prefs.sp(c, 22) * scalePct / 100f * Prefs.getInt(Prefs.LIST_SCALE, 100) / 100f;
        iconPx = textPx * 1.15f * Prefs.getInt(Prefs.ICON_SCALE, 100) / 100f;
        rowH = Math.max(textPx * 1.5f, showIcons ? iconPx * 1.15f : 0) + Prefs.dp(c, gapDp * 2);
        pad = Prefs.dp(c, Prefs.getInt(Prefs.MARGIN, 24));
        text.setTypeface(Prefs.typeface());
        small.setTypeface(Prefs.typeface());
        text.setTextSize(textPx);
        small.setTextSize(Math.max(Prefs.sp(c, 13), textPx * 0.55f));
        line.setStrokeWidth(Math.max(1f, Prefs.dp(c, 1)));
        iconStyle.reload();
        invalidate();
    }

    // ---- paging ----

    public int pageCount() { return Math.max(1, (items.size() + perPage - 1) / perPage); }

    private void clampFirst() {
        int max = Math.max(0, items.size() - perPage);
        if (first > max) first = max;
        if (first < 0) first = 0;
    }

    /** dir = +1 next page, -1 previous. Returns false if already at the end. */
    public boolean pageBy(int dir) {
        layout();
        int target = first + dir * perPage;
        if (dir > 0 && first + perPage >= items.size()) return false;
        if (dir < 0 && first == 0) return false;
        first = Math.max(0, Math.min(target, Math.max(0, items.size() - 1)));
        clampFirst();
        invalidate();
        if (getContext() instanceof android.app.Activity) Eink.pageTurned((android.app.Activity) getContext());
        return true;
    }

    public void jumpToLetter(char letter) {
        for (int i = 0; i < items.size(); i++) {
            char l = AppRepo.letterOf(AppRepo.displayLabel(items.get(i)));
            if (l == letter || (letter != '#' && l > letter && l != '#')) {
                first = i;
                clampFirst();
                invalidate();
                return;
            }
        }
    }

    private void layout() {
        float h = getHeight();
        footerH = (paged && items.size() > perPage) ? Prefs.dp(getContext(), 28) : 0;
        float avail = h - footerH;
        perPage = Math.max(1, (int) (avail / rowH));
        clampFirst();
        int shown = Math.min(perPage, items.size() - first);
        float top = 0;
        if (items.size() <= perPage) {
            float slack = avail - shown * rowH;
            if ("center".equals(valign)) top += slack / 2f;
            else if ("bottom".equals(valign)) top += slack;
        }
        startY = top;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layout();
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas cv) {
        layout();
        float w = getWidth();
        cv.drawColor(bg);

        Paint.FontMetrics fm = text.getFontMetrics();
        float iconSize = iconPx;
        int n = Math.min(perPage, items.size() - first);
        for (int i = 0; i < n; i++) {
            AppRepo.AppEntry a = items.get(first + i);
            float y0 = startY + i * rowH;
            boolean isPressed = i == pressed || first + i == selected;
            if (isPressed) {
                fill.setColor(fg);
                cv.drawRect(0, y0, w, y0 + rowH, fill);
            }
            int ink = isPressed ? bg : fg;
            text.setColor(ink);

            String label = AppRepo.displayLabel(a);
            if (upper) label = label.toUpperCase(java.util.Locale.getDefault());
            int count = badges ? AppRepo.badgeCount(a) : 0;
            float badgeW = count > 0 ? textPx * 0.9f + (count > 1 ? small.measureText(String.valueOf(count)) + textPx * 0.2f : 0) : 0;
            boolean icon = showIcons && !(a.folder && iconStyle.folderIsPlain());
            float iconW = icon ? iconSize + pad * 0.5f : 0;
            float maxText = w - pad * 2 - iconW - badgeW;
            CharSequence shown = TextUtils.ellipsize(label, text, maxText, TextUtils.TruncateAt.END);
            float tw = text.measureText(shown, 0, shown.length());
            float content = iconW + tw + badgeW;
            float x = alignedX(content, w);
            float baseline = y0 + (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent;

            if (icon) {
                float top = y0 + (rowH - iconSize) / 2f;
                if (a.folder) {
                    iconStyle.drawFolder(cv, a.pkg, x, top, iconSize, ink);
                } else {
                    Bitmap ic = AppRepo.icon(a, (int) iconSize);
                    if (ic != null) iconStyle.drawIcon(cv, ic, x, top, ic.getWidth(), ink);
                }
                x += iconW;
            }
            cv.drawText(shown, 0, shown.length(), x, baseline, text);
            x += tw;
            if (count > 0) {
                fill.setColor(isPressed ? bg : accent);
                float r = textPx * 0.17f;
                cv.drawCircle(x + textPx * 0.35f, y0 + rowH / 2f, r, fill);
                if (count > 1) {
                    small.setColor(ink);
                    cv.drawText(String.valueOf(count), x + textPx * 0.35f + r + textPx * 0.15f,
                            y0 + rowH / 2f + small.getTextSize() * 0.35f, small);
                }
            }
            if (dividers && !isPressed && i < n - 1) {
                line.setColor((fg & 0x00FFFFFF) | 0x40000000);
                cv.drawLine(pad, y0 + rowH - 1, w - pad, y0 + rowH - 1, line);
            }
        }

        if (footerH > 0) {
            small.setColor(fg);
            String s = (first / perPage + 1) + " / " + pageCount();
            cv.drawText(s, w - pad - small.measureText(s), getHeight() - footerH * 0.3f, small);
        }

        if (bubble != 0) {
            float s = Prefs.dp(getContext(), 96);
            RectF r = new RectF((w - s) / 2, (getHeight() - s) / 2, (w + s) / 2, (getHeight() + s) / 2);
            fill.setColor(fg);
            cv.drawRoundRect(r, s / 8, s / 8, fill);
            text.setColor(bg);
            text.setTextSize(s * 0.6f);
            String b = String.valueOf(bubble);
            Paint.FontMetrics bf = text.getFontMetrics();
            cv.drawText(b, r.centerX() - text.measureText(b) / 2, r.centerY() - (bf.ascent + bf.descent) / 2, text);
            text.setTextSize(textPx);
        }
    }

    private float alignedX(float contentW, float w) {
        switch (align) {
            case "center": return (w - contentW) / 2f;
            case "right": return w - pad - contentW;
            default: return pad;
        }
    }

    // ---- touch ----

    private int hit(float y) {
        if (y < startY) return -1;
        int i = (int) ((y - startY) / rowH);
        int n = Math.min(perPage, items.size() - first);
        return (i >= 0 && i < n) ? i : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        float swipeMin = Prefs.dp(getContext(), 48);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                layout();
                downX = e.getX();
                downY = e.getY();
                startFirst = first;
                dragging = false;
                longFired = false;
                pressed = hit(downY);
                downRow = pressed >= 0 ? first + pressed : -1;
                if (pressed >= 0) invalidate();
                removeCallbacks(longPress);
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    dragging = true;
                    removeCallbacks(longPress);
                    if (pressed >= 0) { pressed = -1; invalidate(); }
                }
                if (dragging && !paged && Math.abs(dy) > Math.abs(dx)) {
                    int nf = startFirst - Math.round(dy / rowH);
                    int max = Math.max(0, items.size() - perPage);
                    nf = Math.max(0, Math.min(nf, max));
                    if (nf != first) { first = nf; invalidate(); }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPress);
                int idx = pressed;
                pressed = -1;
                invalidate();
                if (longFired) return true;
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!dragging) {
                    if (idx >= 0 && first + idx < items.size()) {
                        if (listener != null) listener.onClick(items.get(first + idx));
                    } else {
                        long now = e.getEventTime();
                        if (now - lastEmptyTap <= ViewConfiguration.getDoubleTapTimeout()) {
                            lastEmptyTap = 0;
                            if (listener != null) listener.onDoubleTapEmpty();
                        } else {
                            lastEmptyTap = now;
                        }
                    }
                } else if (Math.abs(dy) >= Math.abs(dx)) {
                    if (Math.abs(dy) >= swipeMin || (!paged && first != startFirst)) {
                        boolean up = dy < 0;
                        boolean moved;
                        if (paged) moved = pageBy(up ? 1 : -1);
                        else moved = first != startFirst;
                        if (!moved && Math.abs(dy) >= swipeMin && listener != null) listener.onSwipe(up ? UP : DOWN);
                    }
                } else if (Math.abs(dx) >= swipeMin && listener != null) {
                    boolean handled = false;
                    if (downRow >= 0 && downRow < items.size()) {
                        AppRepo.AppEntry row = items.get(downRow);
                        handled = dx > 0 ? listener.onRowSwipeRight(row) : listener.onRowSwipeLeft(row);
                    }
                    if (!handled) listener.onSwipe(dx < 0 ? LEFT : RIGHT);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                pressed = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
}
