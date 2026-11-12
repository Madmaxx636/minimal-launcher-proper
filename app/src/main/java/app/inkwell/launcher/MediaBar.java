package app.inkwell.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Now-playing strip at the bottom of home: title, artist and the player's own controls. Play/pause is
 * always shown; previous and next only if the player supports them (a radio has no previous); and up to
 * two of the player's custom actions, such as like and dislike, sit on either side.
 */
public class MediaBar extends View {
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean always;
    /** The player this bar belongs to. Without one it follows whichever player the launcher picks. */
    private MediaController pinned;
    private boolean lineOnTop = true;
    private Runnable onLongPress;
    private boolean longFired;
    private final Runnable longPress = () -> {
        longFired = true;
        if (onLongPress != null) onLongPress.run();
    };
    /** The cover as the player gave it, and a square copy cut to the size it is drawn at. */
    /** Where the cover on show came from, for the status line in settings. */
    static volatile String artSource = "none yet";
    private Bitmap art, artCrop, artCropFrom;
    private int artCropSize;
    private final Paint artPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private String uriShown = "";
    private Bitmap uriBitmap;
    private final RectF prev = new RectF(), play = new RectF(), next = new RectF(), label = new RectF();
    /** Buttons for the player's custom actions; the action that each one runs is kept beside it. */
    private final RectF leftCustom = new RectF(), rightCustom = new RectF();
    private PlaybackState.CustomAction leftAction, rightAction;
    private final Map<String, Drawable> customIcons = new HashMap<>();

    public MediaBar(Context c) {
        super(c);
        refresh();
    }

    private static float k() { return Prefs.scalePct() / 100f; }

    /** Widget mode: stay visible even when nothing is playing. */
    public void setAlways(boolean a) { always = a; }

    public void setOnLongPress(Runnable r) { onLongPress = r; }

    public void setController(MediaController c) { pinned = c; }

    private MediaController current() { return pinned != null ? pinned : NotifService.media; }

    /** The divider line sits on the side that faces the app list: top when the bar is at the bottom. */
    public void setLineOnTop(boolean top) {
        lineOnTop = top;
        invalidate();
    }

    public void refresh() {
        boolean show = always || (current() != null && Prefs.getBool(Prefs.MEDIA, true));
        setVisibility(show ? VISIBLE : GONE);
        loadArt();
        invalidate();
    }

    /** Artwork from the session, else from an image address on the device, else from the player's notification. */
    private void loadArt() {
        MediaController mc = current();
        if (mc == null || !Prefs.getBool(Prefs.MEDIA_ART, true)) {
            art = null;
            artSource = mc == null ? "nothing playing" : "turned off";
            return;
        }
        Bitmap b = null;
        String source = "none (the player's icon is shown)";
        MediaMetadata md = mc.getMetadata();
        if (md != null) {
            for (String key : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                b = md.getBitmap(key);
                if (b != null) { source = "from the player"; break; }
            }
            if (b == null) {
                b = bitmapFromUri(md);
                if (b != null) source = "from an image file on the device";
            }
        }
        if (b == null) {
            b = NotifService.artFor(mc.getPackageName());
            if (b != null) source = "from the player's notification";
        }
        art = b;
        artSource = source;
    }

    /** Only images already on the device: this app has no internet access, so web addresses are skipped. */
    private Bitmap bitmapFromUri(MediaMetadata md) {
        for (String key : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
            String u = md.getString(key);
            if (u == null || u.isEmpty()) continue;
            if (u.equals(uriShown)) return uriBitmap;
            Uri uri = Uri.parse(u);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("content") || scheme.equals("file") || scheme.equals("android.resource"))) continue;
            uriShown = u;
            uriBitmap = decodeSmall(uri);
            return uriBitmap;
        }
        return null;
    }

    private Bitmap decodeSmall(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContext().getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            o.inSampleSize = Math.max(1, Math.min(o.outWidth, o.outHeight) / 400);
            o.inJustDecodeBounds = false;
            try (java.io.InputStream in = getContext().getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap squareArt(int side) {
        if (art == null) return null;
        if (artCrop != null && artCropFrom == art && artCropSize == side) return artCrop;
        int s = Math.min(art.getWidth(), art.getHeight());
        Bitmap sq = Bitmap.createBitmap(art, (art.getWidth() - s) / 2, (art.getHeight() - s) / 2, s, s);
        artCrop = Bitmap.createScaledBitmap(sq, side, side, true);
        artCropFrom = art;
        artCropSize = side;
        return artCrop;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (Prefs.dp(getContext(), 84) * k()));
    }

    @Override
    protected void onDraw(Canvas c) {
        MediaController mc = current();
        if (mc == null) {
            if (always) {
                c.drawColor(Prefs.bg());
                text.setTypeface(Prefs.typeface());
                text.setColor((Prefs.fg() & 0x00FFFFFF) | 0xAA000000);
                text.setTextSize(Prefs.sp(getContext(), 17) * k());
                c.drawText("Nothing playing", Prefs.dp(getContext(), 24), getHeight() / 2f + text.getTextSize() * 0.35f, text);
            }
            return;
        }
        int w = getWidth(), h = getHeight(), fg = Prefs.fg();
        c.drawColor(Prefs.bg());
        shape.setColor(fg);
        shape.setStyle(Paint.Style.FILL);
        float rule = Prefs.dp(getContext(), 2);
        c.drawRect(0, lineOnTop ? 0 : h - rule, w, lineOnTop ? rule : h, shape);

        float btn = Prefs.dp(getContext(), 64) * k(), pad = Prefs.dp(getContext(), 8);
        PlaybackState state = mc.getPlaybackState();
        boolean hasPrev = supports(state, PlaybackState.ACTION_SKIP_TO_PREVIOUS);
        boolean hasNext = supports(state, PlaybackState.ACTION_SKIP_TO_NEXT);
        List<PlaybackState.CustomAction> custom = state == null
                ? Collections.<PlaybackState.CustomAction>emptyList() : state.getCustomActions();
        layoutButtons(w, h, btn, pad, hasPrev, hasNext, custom);
        // the cover on the left, as a square. With no cover, the player's own icon stands in for it.
        float textLeft = Prefs.dp(getContext(), 24);
        if (Prefs.getBool(Prefs.MEDIA_ART, true)) {
            int side = (int) (h * 0.72f);
            float left = Prefs.dp(getContext(), 14), top = (h - side) / 2f;
            Bitmap cover = squareArt(side);
            if (cover == null) {
                AppRepo.AppEntry player = AppRepo.find(mc.getPackageName());
                cover = player == null ? null : AppRepo.icon(player, side);
            }
            if (cover != null) {
                artPaint.setColorFilter(IconStyle.filterFor(Prefs.getStr(Prefs.MEDIA_ART_LOOK, "gray")));
                c.drawBitmap(cover, left, top, artPaint);
                shape.setStyle(Paint.Style.STROKE);
                shape.setStrokeWidth(Prefs.dp(getContext(), 1.5f));
                c.drawRect(left, top, left + cover.getWidth(), top + cover.getHeight(), shape);
                shape.setStyle(Paint.Style.FILL);
                textLeft = left + side + Prefs.dp(getContext(), 14);
            }
        }
        float edge = leftmostButton();
        // On a narrow bar the title matters more than the extra buttons.
        if (edge - pad - textLeft < Prefs.dp(getContext(), 80) && (leftAction != null || rightAction != null)) {
            layoutButtons(w, h, btn, pad, hasPrev, hasNext, Collections.<PlaybackState.CustomAction>emptyList());
            edge = leftmostButton();
        }
        label.set(textLeft, 0, edge - pad, h);

        MediaMetadata md = mc.getMetadata();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (title == null || title.isEmpty()) title = "Playing";
        text.setTypeface(Prefs.typeface());
        text.setColor(fg);
        text.setTextSize(Prefs.sp(getContext(), 17) * k());
        float avail = label.width();
        float ty = artist == null || artist.isEmpty() ? h / 2f + text.getTextSize() * 0.35f : h / 2f - Prefs.dp(getContext(), 4) * k();
        c.drawText(TextUtils.ellipsize(title, text, avail, TextUtils.TruncateAt.END).toString(), label.left, ty, text);
        if (artist != null && !artist.isEmpty()) {
            text.setTextSize(Prefs.sp(getContext(), 13) * k());
            c.drawText(TextUtils.ellipsize(artist, text, avail, TextUtils.TruncateAt.END).toString(),
                    label.left, h / 2f + Prefs.dp(getContext(), 18) * k(), text);
        }

        float s = Prefs.dp(getContext(), 11) * k(), cy = h / 2f;
        if (!prev.isEmpty()) {
            triangle(c, prev.centerX() + s * 0.5f, cy, s * 0.9f, false);
            c.drawRect(prev.centerX() - s * 1.05f, cy - s * 0.9f, prev.centerX() - s * 0.65f, cy + s * 0.9f, shape);
        }
        if (!next.isEmpty()) {
            triangle(c, next.centerX() - s * 0.5f, cy, s * 0.9f, true);
            c.drawRect(next.centerX() + s * 0.65f, cy - s * 0.9f, next.centerX() + s * 1.05f, cy + s * 0.9f, shape);
        }
        if (isPlaying(mc)) {
            c.drawRect(play.centerX() - s * 0.8f, cy - s, play.centerX() - s * 0.25f, cy + s, shape);
            c.drawRect(play.centerX() + s * 0.25f, cy - s, play.centerX() + s * 0.8f, cy + s, shape);
        } else {
            triangle(c, play.centerX() - s * 0.2f, cy, s, true);
        }
        drawCustom(c, leftCustom, leftAction, mc.getPackageName(), fg, cy);
        drawCustom(c, rightCustom, rightAction, mc.getPackageName(), fg, cy);
    }

    /**
     * Lays the buttons out from the right edge: [custom] [previous] play [next] [custom]. With two custom
     * actions the first goes on the left and the second on the right; a single one goes on the right.
     */
    private void layoutButtons(int w, int h, float btn, float pad, boolean hasPrev, boolean hasNext,
                               List<PlaybackState.CustomAction> custom) {
        // The last two: a player that lists replay, dislike, like has its two rating buttons shown.
        leftAction = custom.size() >= 2 ? custom.get(custom.size() - 2) : null;
        rightAction = custom.isEmpty() ? null : custom.get(custom.size() - 1);
        leftCustom.setEmpty();
        prev.setEmpty();
        next.setEmpty();
        rightCustom.setEmpty();
        float x = w - pad;
        if (rightAction != null) { rightCustom.set(x - btn, 0, x, h); x -= btn; }
        if (hasNext) { next.set(x - btn, 0, x, h); x -= btn; }
        play.set(x - btn, 0, x, h);
        x -= btn;
        if (hasPrev) { prev.set(x - btn, 0, x, h); x -= btn; }
        if (leftAction != null) leftCustom.set(x - btn, 0, x, h);
    }

    private float leftmostButton() {
        float m = play.left;
        if (!prev.isEmpty()) m = Math.min(m, prev.left);
        if (!leftCustom.isEmpty()) m = Math.min(m, leftCustom.left);
        return m;
    }

    /** A player that reports no actions at all is assumed to support everything, as before. */
    private static boolean supports(PlaybackState s, long action) {
        if (s == null) return true;
        long actions = s.getActions();
        return actions == 0 || (actions & action) != 0;
    }

    /** The action's own icon tinted to the launcher's ink colour, or its name if the icon can't be loaded. */
    private void drawCustom(Canvas c, RectF r, PlaybackState.CustomAction a, String pkg, int fg, float cy) {
        if (a == null || r.isEmpty()) return;
        float half = Prefs.dp(getContext(), 14) * k();
        Drawable d = customIcon(pkg, a.getIcon());
        if (d != null) {
            d.setTint(fg);
            d.setBounds((int) (r.centerX() - half), (int) (cy - half), (int) (r.centerX() + half), (int) (cy + half));
            d.draw(c);
            return;
        }
        CharSequence name = a.getName();
        if (name == null) return;
        text.setTypeface(Prefs.typeface());
        text.setColor(fg);
        text.setTextSize(Prefs.sp(getContext(), 11) * k());
        String s = TextUtils.ellipsize(name, text, r.width(), TextUtils.TruncateAt.END).toString();
        c.drawText(s, r.centerX() - text.measureText(s) / 2f, cy + text.getTextSize() * 0.35f, text);
    }

    private Drawable customIcon(String pkg, int id) {
        String key = pkg + "/" + id;
        if (customIcons.containsKey(key)) return customIcons.get(key);
        Drawable d = null;
        try {
            d = getContext().getPackageManager().getResourcesForApplication(pkg).getDrawable(id, null);
            if (d != null) d = d.mutate();
        } catch (Exception ignored) { }
        customIcons.put(key, d);
        return d;
    }

    /** A triangle of half-height r whose tip points right (or left) from cx. */
    private void triangle(Canvas c, float cx, float cy, float r, boolean right) {
        Path p = new Path();
        float d = right ? 1 : -1;
        p.moveTo(cx - d * r, cy - r);
        p.lineTo(cx - d * r, cy + r);
        p.lineTo(cx + d * r, cy);
        p.close();
        c.drawPath(p, shape);
    }

    private static boolean isPlaying(MediaController mc) {
        PlaybackState s = mc.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            longFired = false;
            postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout());
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) removeCallbacks(longPress);
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        removeCallbacks(longPress);
        if (longFired) return true;
        MediaController mc = current();
        if (mc == null) return true;
        float x = e.getX();
        try {
            MediaController.TransportControls t = mc.getTransportControls();
            if (prev.contains(x, e.getY())) t.skipToPrevious();
            else if (next.contains(x, e.getY())) t.skipToNext();
            else if (play.contains(x, e.getY())) { if (isPlaying(mc)) t.pause(); else t.play(); }
            else if (leftAction != null && leftCustom.contains(x, e.getY())) t.sendCustomAction(leftAction.getAction(), leftAction.getExtras());
            else if (rightAction != null && rightCustom.contains(x, e.getY())) t.sendCustomAction(rightAction.getAction(), rightAction.getExtras());
            else openPlayer(mc);
        } catch (Exception ignored) { }
        return true;
    }

    /**
     * Opens the app that is playing, the same way tapping it in the app list does. The screen a player
     * offers for its session is not used: sending it reports no failure when Android blocks it, so a tap
     * could do nothing at all.
     */
    private void openPlayer(MediaController mc) {
        String pkg = mc.getPackageName();
        if (AppRepo.find(pkg) != null) {
            AppRepo.launchPackage(getContext(), pkg);
            return;
        }
        android.content.Intent open = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
        try {
            if (open == null) throw new IllegalStateException();
            getContext().startActivity(open.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "Can't open " + pkg, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
