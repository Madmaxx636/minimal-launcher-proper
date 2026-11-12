package app.inkwell.launcher;

import android.app.Activity;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Settings for an app's pop-up: its widgets, ordered like apps are. A media app's widgets also show on the
 * home screen while it plays.
 */
public class AppPopupActivity extends Activity {
    private String pkg;
    private LinearLayout list;
    private ScrollView sv;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        pkg = getIntent().getStringExtra("pkg");
        if (pkg == null || AppRepo.find(pkg) == null) {
            finish();
            return;
        }
        list = new LinearLayout(this);
        sv = Ui.screen(this, list);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pkg == null || AppRepo.find(pkg) == null) return;
        Widgets.onChanged = this::build;
        build();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (Widgets.onResult(this, req, res)) build();
    }

    private void build() {
        final int scroll = sv.getScrollY();
        Ui.theme(this, sv);
        list.removeAllViews();
        Ui.row(list, "‹  Back", null, v -> finish());
        Ui.section(list, AppRepo.displayLabel(AppRepo.find(pkg)) + " pop-up");
        Ui.row(list, "On the home screen", "These widgets replace the launcher's music controls on home while "
                + AppRepo.displayLabel(AppRepo.find(pkg)) + " has media playing or paused. Swipe one up or down there to send it away. "
                + "Turn this off with Music controls style in Settings.", null);
        if (!NotifService.isEnabled(this)) {
            Ui.row(list, "Notification access is off", "Minimal Launcher Proper needs it to know when an app is playing. Tap to turn it on.", v ->
                    startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
        Ui.section(list, "Widgets. Drag ≡ to reorder");
        WidgetSection.build(this, list, "app:" + pkg, false, this::build);
        sv.post(() -> sv.scrollTo(0, scroll));
    }
}
