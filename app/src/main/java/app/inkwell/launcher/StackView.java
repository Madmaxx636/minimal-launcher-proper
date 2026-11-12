package app.inkwell.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One widget at a time. Slide a finger across it to go to the next or previous widget in the stack.
 * Plain taps still reach the widget itself. There is no animation, so e-ink redraws it once.
 */
public class StackView extends LinearLayout {
    public interface OnSwipe { void swiped(int dir); }

    private OnSwipe onSwipe;
    private Runnable onDismiss;
    private float downX, downY;
    private boolean swiping;

    public StackView(Context c) {
        super(c);
        setOrientation(VERTICAL);
    }

    public void setOnSwipe(OnSwipe s) { onSwipe = s; }

    /** Optional: a swipe up or down throws the whole stack away. */
    public void setOnDismiss(Runnable r) { onDismiss = r; }

    public void setContent(View widget, LinearLayout.LayoutParams lp) { addView(widget, lp); }

    /** Dots under the widget: ● for this one, ○ for the others. */
    public void setPosition(int index, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(i == index ? "●" : "○").append(i < count - 1 ? "  " : "");
        TextView dots = Ui.text(getContext(), sb.toString(), 12, Prefs.fg(), false);
        dots.setGravity(Gravity.CENTER);
        int p = (int) Prefs.dp(getContext(), 4);
        dots.setPadding(0, p, 0, p);
        addView(dots, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                swiping = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                boolean vertical = onDismiss != null && Math.abs(dy) > slop * 1.5f && Math.abs(dy) > Math.abs(dx) * 1.5f;
                boolean sideways = onSwipe != null && Math.abs(dx) > slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.5f;
                if (!swiping && (sideways || vertical)) {
                    swiping = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            }
        }
        return swiping;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_UP: {
                // Judge by the whole gesture: widgets that ignore touches hand it to us without the intercept step.
                float dx = e.getX() - downX, dy = e.getY() - downY;
                boolean sideways = Math.abs(dx) > Prefs.dp(getContext(), 56) && Math.abs(dx) > Math.abs(dy) * 1.5f;
                boolean vertical = Math.abs(dy) > Prefs.dp(getContext(), 56) && Math.abs(dy) > Math.abs(dx) * 1.5f;
                if (sideways && onSwipe != null) onSwipe.swiped(dx < 0 ? 1 : -1);
                else if (vertical && onDismiss != null) onDismiss.run();
                swiping = false;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                swiping = false;
                return true;
        }
        return swiping;
    }
}
