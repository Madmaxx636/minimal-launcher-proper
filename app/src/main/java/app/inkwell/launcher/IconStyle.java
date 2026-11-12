package app.inkwell.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.List;

/**
 * How app icons are drawn: their shape, their colours and an outline, plus how a folder looks in a list.
 * Call reload() after the settings may have changed.
 */
final class IconStyle {
    static final String[] SHAPE_IDS = {"original", "circle", "rounded", "square"};
    static final String[] SHAPE_NAMES = {"As the app made it", "Circle", "Rounded square", "Square"};
    static final String[] LOOK_IDS = {"gray", "color", "bw", "invert"};
    static final String[] LOOK_NAMES = {"Grayscale", "Original colors", "Black and white, high contrast", "Inverted grayscale"};
    static final String[] RING_IDS = {"none", "thin", "thick"};
    static final String[] RING_NAMES = {"None", "Thin", "Thick"};
    static final String[] FOLDER_IDS = {"glyph", "preview", "plain"};
    static final String[] FOLDER_NAMES = {"Folder shape", "Preview of its apps", "Plain text, no icon"};

    private final Context ctx;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private String shape = "original", ring = "none", folder = "glyph";

    IconStyle(Context c) {
        ctx = c;
        stroke.setStyle(Paint.Style.STROKE);
        reload();
    }

    void reload() {
        shape = Prefs.getStr(Prefs.ICON_SHAPE, "original");
        ring = Prefs.getStr(Prefs.ICON_RING, "none");
        folder = Prefs.getStr(Prefs.FOLDER_STYLE, "glyph");
        bitmapPaint.setColorFilter(filterFor(Prefs.getStr(Prefs.ICON_LOOK, "gray")));
    }

    /** True if folders get no icon at all, so their text should not leave a gap for one. */
    boolean folderIsPlain() { return folder.equals("plain"); }

    static ColorMatrixColorFilter filterFor(String look) {
        ColorMatrix m = new ColorMatrix();
        switch (look) {
            case "color": return null;
            case "invert":
                m.setSaturation(0);
                m.postConcat(new ColorMatrix(new float[]{-1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0}));
                break;
            case "bw": {
                float k = 4f, t = 127.5f - 127.5f * k;
                m.set(new float[]{0.299f * k, 0.587f * k, 0.114f * k, 0, t, 0.299f * k, 0.587f * k, 0.114f * k, 0, t,
                        0.299f * k, 0.587f * k, 0.114f * k, 0, t, 0, 0, 0, 1, 0});
                break;
            }
            default: m.setSaturation(0);
        }
        return new ColorMatrixColorFilter(m);
    }

    private float ringPx() { return ring.equals("thick") ? Prefs.dp(ctx, 3) : ring.equals("thin") ? Prefs.dp(ctx, 1) : 0; }

    private void shapeInto(Path p, float l, float t, float s) {
        p.reset();
        rect.set(l, t, l + s, t + s);
        switch (shape) {
            case "circle": p.addCircle(l + s / 2f, t + s / 2f, s / 2f, Path.Direction.CW); break;
            case "rounded": p.addRoundRect(rect, s * 0.22f, s * 0.22f, Path.Direction.CW); break;
            default: p.addRect(rect, Path.Direction.CW);
        }
    }

    /** Draws an app icon (a bitmap already scaled to s) with the chosen shape, colours and outline. */
    void drawIcon(Canvas c, Bitmap b, float l, float t, float s, int ringColor) {
        boolean clip = !shape.equals("original");
        if (clip) {
            c.save();
            shapeInto(path, l, t, s);
            c.clipPath(path);
        }
        c.drawBitmap(b, l, t, bitmapPaint);
        if (clip) c.restore();
        float rp = ringPx();
        if (rp > 0) {
            stroke.setColor(ringColor);
            stroke.setStrokeWidth(rp);
            shapeInto(path, l + rp / 2f, t + rp / 2f, s - rp);
            c.drawPath(path, stroke);
        }
    }

    /** A folder in a list: an outlined folder, or a small preview of the apps inside. */
    void drawFolder(Canvas c, String folderPkg, float l, float t, float s, int ink) {
        if (folder.equals("plain")) return;
        float sw = Math.max(Prefs.dp(ctx, 1.5f), s * 0.06f);
        stroke.setColor(ink);
        stroke.setStrokeWidth(sw);
        if (folder.equals("preview")) {
            rect.set(l + sw / 2, t + sw / 2, l + s - sw / 2, t + s - sw / 2);
            c.drawRoundRect(rect, s * 0.2f, s * 0.2f, stroke);
            List<String> apps = Folders.apps(Folders.idOf(folderPkg));
            float mini = s * 0.34f, gap = s * 0.08f, x0 = l + (s - 2 * mini - gap) / 2f, y0 = t + (s - 2 * mini - gap) / 2f;
            int n = 0;
            for (String pkg : apps) {
                AppRepo.AppEntry a = AppRepo.find(pkg);
                Bitmap ic = a == null ? null : AppRepo.icon(a, (int) mini);
                if (ic == null) continue;
                c.drawBitmap(ic, x0 + (n % 2) * (mini + gap), y0 + (n / 2) * (mini + gap), bitmapPaint);
                if (++n == 4) break;
            }
            return;
        }
        // folder shape: a body with a tab
        rect.set(l + sw / 2, t + s * 0.24f, l + s - sw / 2, t + s - sw / 2 - s * 0.08f);
        c.drawRoundRect(rect, s * 0.1f, s * 0.1f, stroke);
        rect.set(l + sw / 2, t + s * 0.08f + sw / 2, l + s * 0.46f, t + s * 0.3f);
        c.drawRoundRect(rect, s * 0.06f, s * 0.06f, stroke);
    }
}
