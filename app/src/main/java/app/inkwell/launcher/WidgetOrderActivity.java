package app.inkwell.launcher;

import android.app.Activity;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Put widgets in order. Without a slot it shows both home spots (above and below the clock); with one
 * it shows just that spot.
 */
public class WidgetOrderActivity extends Activity {
    private LinearLayout list;
    private ScrollView sv;
    private String only;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        only = getIntent().getStringExtra("slot");
        list = new LinearLayout(this);
        sv = Ui.screen(this, list);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (only == null) {
            section(Widgets.HOME);
            section(Widgets.HOME_BELOW);
        } else {
            section(only);
        }
        sv.post(() -> sv.scrollTo(0, scroll));
    }

    private String nameOf(String slot) {
        if (slot.equals(Widgets.HOME)) return "Above the clock";
        if (slot.equals(Widgets.HOME_BELOW)) return "Below the clock";
        if (Folders.isFolder(slot)) return Folders.nameOf(Folders.idOf(slot));
        AppRepo.AppEntry a = AppRepo.find(slot.startsWith("app:") ? slot.substring(4) : slot);
        return a == null ? "Pop-up" : AppRepo.displayLabel(a);
    }

    private void section(String slot) {
        Ui.section(list, nameOf(slot) + ". Drag ≡ to reorder");
        WidgetSection.build(this, list, slot, slot.equals(Widgets.HOME) || slot.equals(Widgets.HOME_BELOW), this::build);
    }
}
