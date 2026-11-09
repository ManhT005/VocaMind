package com.vocamind.app;

import static org.junit.Assert.assertEquals;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class MainActivityInsetsTest {
    @Test public void keyboardUsesLargerInsetAndClosingItRestoresSystemBars() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            View root = controller.get().findViewById(R.id.shell_root);
            ViewCompat.dispatchApplyWindowInsets(root, new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
                    .setVisible(WindowInsetsCompat.Type.ime(), true).build());
            assertEquals(300, root.getPaddingBottom());
            assertEquals(24, root.getPaddingTop());
            ViewCompat.dispatchApplyWindowInsets(root, new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .setVisible(WindowInsetsCompat.Type.ime(), false).build());
            assertEquals(24, root.getPaddingBottom());
        }
    }
}
