package com.vocamind.app.feature.search;
import android.os.Looper;
import java.time.Duration;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk = {26,35}) @SQLiteMode(SQLiteMode.Mode.NATIVE)
public class SearchReadRecoveryTest extends SearchReadRecoveryContract {
    @Override protected void tick() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.yield(); }
}
