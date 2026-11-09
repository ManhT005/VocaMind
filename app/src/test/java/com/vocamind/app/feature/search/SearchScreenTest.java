package com.vocamind.app.feature.search;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import android.os.Looper;
import java.time.Duration;
@RunWith(RobolectricTestRunner.class) @Config(sdk = {26,35}) @SQLiteMode(SQLiteMode.Mode.NATIVE)
public class SearchScreenTest extends SearchScreenContract {
    @Override protected void tick() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.yield(); }
    @org.junit.After public void retainHostTrace() {
        for (var log : org.robolectric.shadows.ShadowLog.getLogsForTag("SearchTiming"))
            System.out.println("SEARCH01_HOST_TRACE " + log.msg);
    }
}
