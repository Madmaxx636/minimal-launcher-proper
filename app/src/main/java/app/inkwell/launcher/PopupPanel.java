package app.inkwell.launcher;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Niagara-style pop-up: opened by swiping right on a favorite. For an app it shows that app's
 * notifications, shortcuts and pinned widgets; for a folder it also lists the folder's apps.
 */
public class PopupPanel extends FrameLayout {
    public interface Host {
        void closePopup();
        void launch(AppRepo.AppEntry a);
    }

    /** ScrollView that never takes more than a fixed share of the screen. */
    private static final class CappedScroll extends ScrollView {
        CappedScroll(Context c) { super(c); }

        @Override
        protected void onMeasure(int w, int h) {
            int cap = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }

    private final Host host;
    private final LinearLayout box;
    private AppRepo.AppEntry current;
    private ScrollView bodyScroll;
    /** Where the list was scrolled to; rebuilding it (music changed, a widget updated) must not throw that away. */
    private int keepScroll;
    private boolean touching, rebuildQueued;
    private long lastScrollAt;
    /** What the pop-up last showed. Music and widgets ping constantly; if nothing visible changed, leave it alone. */
    private String shownSig = "";

    public PopupPanel(Context c, Host host) {
        super(c);
        this.host = host;
        setClickable(true);
        setOnClickListener(v -> host.closePopup()); // tap outside the box to dismiss
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        int m = (int) Prefs.dp(c, 16);
        lp.setMargins(m, m, m, m);
        addView(box, lp);
    }

    /** Closing forgets everything, scroll position included, so the next one starts fresh at the top. */
    public void clear() {
        current = null;
        keepScroll = 0;
        bodyScroll = null;
        shownSig = "";
        box.removeAllViews();
    }

    public void show(AppRepo.AppEntry a) {
        current = a;
        keepScroll = 0;
        bodyScroll = null; // a freshly opened pop-up starts at the top, not where the last one was left
        shownSig = signature();
        build();
    }

    /**
     * Music and widgets ask for this all the time. Rebuilding under a moving finger makes the list jump,
     * so while it is being touched or still scrolling the rebuild waits until it settles.
     */
    public void rebuild() {
        if (current == null) return;
        if (signature().equals(shownSig)) return;
        if (touching || android.os.SystemClock.uptimeMillis() - lastScrollAt < 400) {
            if (!rebuildQueued) {
                rebuildQueued = true;
                postDelayed(() -> {
                    rebuildQueued = false;
                    rebuild();
                }, 400);
            }
            return;
        }
        shownSig = signature();
        build();
    }

    /** Everything that decides what the pop-up draws, apart from what the widgets draw inside themselves. */
    private String signature() {
        AppRepo.AppEntry a = current;
        String slot = a.folder ? a.pkg : "app:" + a.pkg;
        StringBuilder sb = new StringBuilder(a.pkg).append('|').append(AppRepo.displayLabel(a)).append('|')
                .append(Prefs.fg()).append(Prefs.bg()).append(Prefs.getStr(Prefs.POP_BORDER, "normal"))
                .append(Prefs.getStr(Prefs.POP_CORNERS, "square")).append(Prefs.getStr("widgetOpts", "")).append('|');
        List<Integer> ids = Widgets.ids(slot);
        sb.append(ids);
        if (ids.size() >= 2) sb.append(Widgets.stacked(slot)).append(Widgets.stackIndex(slot, ids.size()));
        List<String> pkgs = a.folder ? Folders.apps(Folders.idOf(a.pkg)) : Collections.singletonList(a.pkg);
        for (String pk : pkgs) {
            sb.append('|').append(pk).append(NotifService.count(pk));
            for (NotifService.Item it : NotifService.items(pk)) sb.append(it.key).append(it.title).append(it.text).append(it.when);
        }
        return sb.toString();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        int a = e.getActionMasked();
        if (a == android.view.MotionEvent.ACTION_DOWN) touching = true;
        else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
            touching = false;
            lastScrollAt = android.os.SystemClock.uptimeMillis();
        }
        return super.dispatchTouchEvent(e);
    }

    private void build() {
        final Context c = getContext();
        final AppRepo.AppEntry a = current;
        if (bodyScroll != null && bodyScroll.getParent() != null) keepScroll = bodyScroll.getScrollY();
        final int fg = Prefs.fg(), bg = Prefs.bg();
        final String slot = a.folder ? a.pkg : "app:" + a.pkg;
        final List<String> pkgs = a.folder ? Folders.apps(Folders.idOf(a.pkg)) : Collections.singletonList(a.pkg);

        setBackgroundColor(0x00000000);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(bg);
        String border = Prefs.getStr(Prefs.POP_BORDER, "normal");
        float stroke = border.equals("none") ? 0 : border.equals("thin") ? 1 : border.equals("thick") ? 4 : 2;
        if (stroke > 0) frame.setStroke((int) Prefs.dp(c, stroke), fg);
        frame.setCornerRadius(Prefs.getStr(Prefs.POP_CORNERS, "square").equals("rounded") ? Prefs.dp(c, 16) : 0);
        box.setBackground(frame);
        box.setClipToOutline(true); // keeps rows and widgets inside rounded corners
        box.removeAllViews();

        int p = (int) Prefs.dp(c, 14);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(p, p / 2, p / 2, p / 2);
        head.addView(Ui.text(c, AppRepo.displayLabel(a), 22, fg, true), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(headButton("✕", host::closePopup));
        box.addView(head);
        rule(box, 2);

        final ScrollView scroll = new CappedScroll(c);
        bodyScroll = scroll;
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> lastScrollAt = android.os.SystemClock.uptimeMillis());
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new LayoutParams(-1, -2));
        box.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        if (!a.folder) body.addView(row("Open " + AppRepo.displayLabel(a), () -> host.launch(a)));

        // widgets pinned to this pop-up. Two or more are a stack you swipe across.
        // Adding, ordering and removing them is done from the Edit screen, so nothing here changes anything.
        final int avail = getResources().getDisplayMetrics().widthPixels - (int) Prefs.dp(c, 32);
        LinearLayout wbox = new LinearLayout(c);
        wbox.setOrientation(LinearLayout.VERTICAL);
        Widgets.fill(c, wbox, slot, avail, this::rebuild);
        if (wbox.getChildCount() > 0) {
            body.addView(wbox, new LinearLayout.LayoutParams(-1, -2));
            rule(body, 1);
        }

        // notifications
        List<NotifService.Item> ns = new ArrayList<>();
        for (String pk : pkgs) ns.addAll(NotifService.items(pk));
        Collections.sort(ns, (x, y) -> Long.compare(y.when, x.when));
        if (!ns.isEmpty()) {
            body.addView(section("Notifications"));
            int shown = 0;
            for (NotifService.Item it : ns) {
                if (shown++ == 6) break;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(p / 2, p / 4, p / 2, p / 4);
                body.addView(NotifPanel.card(c, it, host::closePopup), lp);
            }
        }

        // the app's own shortcuts
        if (!a.folder) {
            List<ShortcutInfo> sc = shortcuts(c, a.pkg);
            if (!sc.isEmpty()) {
                body.addView(section("Shortcuts"));
                for (final ShortcutInfo s : sc) {
                    body.addView(row(String.valueOf(s.getShortLabel()), () -> {
                        host.closePopup();
                        try {
                            ((LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE)).startShortcut(s, null, null);
                        } catch (Exception ignored) { }
                    }));
                }
            }
        } else {
            body.addView(section("Apps"));
            boolean any = false;
            for (String pk : pkgs) {
                final AppRepo.AppEntry app = AppRepo.find(pk);
                if (app == null) continue;
                any = true;
                int n = NotifService.count(pk);
                body.addView(row(AppRepo.displayLabel(app) + (n > 0 ? "   • " + n : ""), () -> host.launch(app)));
            }
            if (!any) body.addView(row("Empty. Long-press the folder and choose Edit folder to add apps.", null));
        }

        if (keepScroll > 0) {
            // put the list back where it was before it first draws, so it never flashes at the top
            scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                    scroll.scrollTo(0, keepScroll);
                    return true;
                }
            });
        }
    }

    static List<ShortcutInfo> shortcuts(Context c, String pkg) {
        List<ShortcutInfo> out = new ArrayList<>();
        try {
            LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (la == null || !la.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> l = la.getShortcuts(q, Process.myUserHandle());
            if (l != null) for (ShortcutInfo s : l) if (s.isEnabled() && out.size() < 5) out.add(s);
        } catch (Exception ignored) { }
        return out;
    }

    private TextView headButton(String s, Runnable r) {
        TextView t = Ui.text(getContext(), s, 17, Prefs.fg(), false);
        int p = (int) Prefs.dp(getContext(), 12);
        t.setPadding(p, p, p, p);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private View section(String s) {
        Context c = getContext();
        TextView t = Ui.text(c, s.toUpperCase(java.util.Locale.getDefault()), 12, Prefs.fg(), true);
        int p = (int) Prefs.dp(c, 14);
        t.setPadding(p, p, p, p / 4);
        return t;
    }

    private View row(String s, Runnable r) {
        Context c = getContext();
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(c, s, 18, Prefs.fg(), false);
        int p = (int) Prefs.dp(c, 14);
        t.setPadding(p, p * 3 / 4, p, p * 3 / 4);
        l.addView(t);
        rule(l, 1);
        if (r != null) l.setOnClickListener(v -> r.run());
        return l;
    }

    private void rule(LinearLayout parent, int px) {
        View v = new View(getContext());
        v.setBackgroundColor((Prefs.fg() & 0x00FFFFFF) | (px > 1 ? 0xFF000000 : 0x40000000));
        parent.addView(v, new LinearLayout.LayoutParams(-1, px));
    }
}
