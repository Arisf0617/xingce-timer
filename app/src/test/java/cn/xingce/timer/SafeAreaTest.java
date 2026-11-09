package cn.xingce.timer;

import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SafeAreaTest {
    @Test public void webContentStaysInsideBarsCutoutsAndKeyboard() {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ViewGroup content = activity.findViewById(android.R.id.content);
            ViewGroup root = (ViewGroup) content.getChildAt(0);
            WebView web = (WebView) root.getChildAt(0);
            // Robolectric's WebView provider stubs setFrame. A normal native view
            // with identical params verifies position, while the actual WebView
            // verifies that its measured viewport excludes the unsafe area.
            View probe = new View(activity);
            root.addView(probe, new FrameLayout.LayoutParams(-1, -1));
            // Isolate the container from the test window's queued layout traversal.
            content.removeView(root);
            // Portrait: the cutout is taller than the status bar (dynamic island).
            check(root, web, 1080, 2400, Insets.of(0, 80, 0, 0),
                    Insets.of(0, 132, 0, 0), Insets.of(0, 0, 0, 72), Insets.NONE,
                    0, 132, 1080, 2328);
            // Landscape: the camera cutout occupies the left edge.
            check(root, web, 2400, 1080, Insets.of(0, 60, 0, 0),
                    Insets.of(132, 0, 0, 0), Insets.of(0, 0, 72, 0), Insets.NONE,
                    132, 60, 2328, 1080);
            // Keyboard: settings / record-name inputs must stay above the IME.
            check(root, web, 1080, 2400, Insets.of(0, 80, 0, 0),
                    Insets.NONE, Insets.of(0, 0, 0, 72), Insets.of(0, 0, 0, 900),
                    0, 80, 1080, 1500);
        }
    }

    private void check(ViewGroup root, WebView web, int width, int height,
                       Insets status, Insets cutout, Insets navigation, Insets keyboard,
                       int left, int top, int right, int bottom) {
        WindowInsets insets = new WindowInsets.Builder()
                .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), status)
                .setInsetsIgnoringVisibility(WindowInsets.Type.displayCutout(), cutout)
                .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), navigation)
                .setInsets(WindowInsets.Type.ime(), keyboard).build();
        assertTrue(root.dispatchApplyWindowInsets(insets).isConsumed());
        assertEquals("native top padding; source=" + insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()), top, root.getPaddingTop());
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        View probe = root.getChildAt(1);
        assertEquals(left, probe.getLeft());
        assertEquals(top, probe.getTop());
        assertEquals(right, probe.getRight());
        assertEquals(bottom, probe.getBottom());
        assertEquals(right-left, web.getMeasuredWidth());
        assertEquals(bottom-top, web.getMeasuredHeight());
    }
}
