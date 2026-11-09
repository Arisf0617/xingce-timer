package cn.xingce.timer;

import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ResetSessionTest {
    @Test public void resetClearsCurrentSessionButKeepsSavedHistoryAndSettings() {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            MainActivity.Bridge bridge = ReflectionHelpers.getField(activity, "bridge");
            assertEquals("", bridge.action("start", ""));
            bridge.engine.spent[0] = 120000;
            assertEquals("", bridge.action("finish", "已保存的练习"));
            String history = bridge.history();
            assertEquals("", bridge.action("configure", "[120000,180000,600000,240000,360000,300000]"));
            long[] settings = bridge.engine.budgets.clone();
            assertEquals("", bridge.action("start", ""));
            bridge.engine.spent[0] = 660000;
            assertEquals("", bridge.action("select", "3"));
            bridge.engine.spent[3] = 180000;
            assertNotEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            assertEquals("", bridge.action("discard", ""));
            assertEquals(0, bridge.engine.elapsed());
            assertEquals(0, bridge.engine.startedAt);
            assertEquals(0, bridge.engine.active);
            assertFalse(bridge.engine.running);
            assertArrayEquals(settings, bridge.engine.budgets);
            assertEquals(history, bridge.history());
            assertEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            MainActivity.Bridge restored = activity.new Bridge();
            assertEquals(0, restored.engine.elapsed());
            assertEquals(0, restored.engine.startedAt);
            assertFalse(restored.engine.running);
            assertArrayEquals(settings, restored.engine.budgets);
            assertEquals(history, restored.history());
        }
    }
}
