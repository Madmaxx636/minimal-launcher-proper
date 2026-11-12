package app.inkwell.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

/** Full-screen list of notifications as cards, in the user's theme. Tap opens, X dismisses. */
public class NotifPanel extends LinearLayout {
    public interface Host {
        void closePanel();
    }

    private final Host host;
    private final ScrollView scroll;
    private final LinearLayout cards;
    private final LinearLayout header;
    private String filterPkg;

    public NotifPanel(Context c, Host host) {
        super(c);
        this.host = host;
        setOrientation(VERTICAL);
        setClickable(true); // block touches reaching the home list underneath
        header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, new LayoutParams(-1, -2));
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        cards = new LinearLayout(c);
        cards.setOrientation(VERTICAL);
        scroll.addView(cards, new LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1f));
    }

    public void show(String pkg) {
        filterPkg = pkg;
        scroll.scrollTo(0, 0);
        rebuild();
    }

    public void pageBy(int dir) {
        scroll.scrollBy(0, dir * (scroll.getHeight() - (int) Prefs.dp(getContext(), 48)));
    }

    public void rebuild() {
        Context c = getContext();
        int fg = Prefs.fg(), bg = Prefs.bg();
        setBackgroundColor(bg);
        int pad = (int) Prefs.dp(c, 16);

        header.removeAllViews();
        header.setPadding(pad, (int) Prefs.dp(c, 8), pad / 2, (int) Prefs.dp(c, 8));
        AppRepo.AppEntry app = filterPkg == null ? null : AppRepo.find(filterPkg);
        TextView title = Ui.text(c, app == null ? "Notifications" : AppRepo.displayLabel(app), 22, fg, true);
        header.addView(title, new LayoutParams(0, -2, 1f));
        header.addView(headerButton("Clear all", () -> { NotifService.dismissAll(filterPkg); }));
        header.addView(headerButton("Close", host::closePanel));

        cards.removeAllViews();
        List<NotifService.Item> list = NotifService.items(filterPkg);
        if (!NotifService.isEnabled(c)) {
            note("Notification access is off. Turn it on in Minimal Launcher Proper settings to see notifications here.");
        } else if (list.isEmpty()) {
            note("Nothing new.");
        }
        for (NotifService.Item it : list) cards.addView(card(getContext(), it, host::closePanel), cardParams());
    }

    private void note(String s) {
        TextView t = Ui.text(getContext(), s, 17, Prefs.fg(), false);
        int p = (int) Prefs.dp(getContext(), 24);
        t.setPadding(p, p, p, p);
        cards.addView(t);
    }

    private LayoutParams cardParams() {
        LayoutParams lp = new LayoutParams(-1, -2);
        int m = (int) Prefs.dp(getContext(), 12);
        lp.setMargins(m, m / 2, m, m / 2);
        return lp;
    }

    private TextView headerButton(String s, Runnable r) {
        TextView t = Ui.text(getContext(), s, 16, Prefs.fg(), false);
        int p = (int) Prefs.dp(getContext(), 12);
        t.setPadding(p, p, p, p);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    public static View card(Context c, final NotifService.Item it, final Runnable onOpen) {
        int fg = Prefs.fg();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        int p = (int) Prefs.dp(c, 14);
        card.setPadding(p, p, p, p);
        GradientDrawable box = new GradientDrawable();
        box.setColor(Prefs.bg());
        box.setStroke((int) Prefs.dp(c, 1.5f), fg);
        card.setBackground(box);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        AppRepo.AppEntry app = AppRepo.find(it.pkg);
        String who;
        if (app != null) who = AppRepo.displayLabel(app);
        else {
            try {
                who = String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(it.pkg, 0)));
            } catch (Exception e) {
                who = it.pkg;
            }
        }
        String when = DateFormat.getTimeFormat(c).format(new Date(it.when));
        top.addView(Ui.text(c, (who + "  ·  " + when).toUpperCase(java.util.Locale.getDefault()), 12, fg, true), new LayoutParams(0, -2, 1f));
        if (it.clearable) {
            TextView x = Ui.text(c, "✕", 18, fg, false);
            x.setPadding(p, 0, 0, 0);
            x.setOnClickListener(v -> NotifService.dismiss(it.key));
            top.addView(x);
        }
        card.addView(top);

        if (!it.title.isEmpty()) {
            TextView t = Ui.text(c, it.title, 18, fg, true);
            t.setPadding(0, p / 2, 0, 0);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(t);
        }
        if (!it.text.isEmpty()) {
            TextView t = Ui.text(c, it.text, 16, fg, false);
            t.setPadding(0, p / 4, 0, 0);
            t.setMaxLines(6);
            t.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(t);
        }
        if (!it.actions.isEmpty()) {
            LinearLayout row = new LinearLayout(c);
            row.setPadding(0, p, 0, 0);
            for (final NotifService.Action a : it.actions) {
                TextView b = Ui.text(c, a.title, 14, fg, true);
                GradientDrawable bb = new GradientDrawable();
                bb.setColor(Prefs.bg());
                bb.setStroke((int) Prefs.dp(c, 1), fg);
                b.setBackground(bb);
                b.setPadding(p, p / 2, p, p / 2);
                b.setOnClickListener(v -> send(a.intent));
                LayoutParams lp = new LayoutParams(-2, -2);
                lp.rightMargin = p / 2;
                row.addView(b, lp);
            }
            card.addView(row);
        }
        card.setOnClickListener(v -> {
            send(it.content);
            if (it.autoCancel) NotifService.dismiss(it.key);
            onOpen.run();
        });
        return card;
    }

    private static void send(PendingIntent pi) {
        if (pi == null) return;
        try { pi.send(); } catch (PendingIntent.CanceledException ignored) { }
    }
}
