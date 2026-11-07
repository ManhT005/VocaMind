package com.vocamind.app.feature.quiz;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.quiz.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Production composition/catalog/Room, restricted to a separate validation installation. */
@RunWith(AndroidJUnit4.class)
public class QuizProductionFlowInstrumentedTest {
    @Test public void offlineFirstOpenThenTenQuestionsRotateReopenAndOneHistoryRow() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        Assume.assumeTrue("Requires -Pquiz03DeviceIsolation=true",context.getPackageName().endsWith(".quiz03validation"));
        assertEquals("Enable airplane mode for offline acceptance",1,
                Settings.Global.getInt(context.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0));
        UserDatabase user=UserDatabase.open(context);
        CatalogDatabase catalog=CatalogDatabase.open(context,"catalog.db");
        try {
            assertTrue("Use a fresh isolated test package; never erase real history",user.userDao().quizzes().isEmpty());
            assertTrue(user.userDao().activePacks().isEmpty());
            try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> a.findViewById(R.id.open_quiz).performClick());
                await(scenario,QuizViewModel.Phase.EMPTY);
                screenshot(context,"01-offline-empty");
                user.userDao().activatePack(catalog.catalogDao().packs().get(0).id,System.currentTimeMillis());
                scenario.onActivity(a -> a.findViewById(R.id.quiz_action).performClick());
                await(scenario,QuizViewModel.Phase.ANSWERING);
                assertEquals(10,state(scenario).session.getQuestions().size());
                for (int i=0;i<10;i++) {
                    String questionId=state(scenario).session.getCurrentQuestion().getId();
                    if (i>0) SystemClock.sleep(650);
                    final int choice=i%4;
                    scenario.onActivity(a -> a.findViewById(new int[]{R.id.quiz_option_1,R.id.quiz_option_2,
                            R.id.quiz_option_3,R.id.quiz_option_4}[choice]).performClick());
                    if (i<9) {
                        await(scenario,QuizViewModel.Phase.ANSWERED);
                        if (i==0) {
                            screenshot(context,"02-feedback");
                            scenario.recreate(); await(scenario,QuizViewModel.Phase.ANSWERED);
                            assertEquals(questionId,state(scenario).session.getCurrentQuestion().getId());
                            scenario.onActivity(a -> assertFalse(a.findViewById(R.id.quiz_option_2).isEnabled()));
                        }
                        scenario.onActivity(a -> a.findViewById(R.id.quiz_next).performClick());
                        await(scenario,QuizViewModel.Phase.ANSWERING);
                    }
                }
                await(scenario,QuizViewModel.Phase.RESULT);
                QuizResult result=state(scenario).session.getResult().get();
                assertEquals(10,result.getQuestionCount()); screenshot(context,"03-result");
                scenario.recreate(); await(scenario,QuizViewModel.Phase.RESULT);
                scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
                scenario.onActivity(a -> a.findViewById(R.id.open_quiz).performClick());
                await(scenario,QuizViewModel.Phase.RESULT);
                assertEquals(1,user.userDao().quizzes().size());
                var stored=user.userDao().quizzes().get(0);
                assertNotNull(stored.completionKey); assertEquals(result.getCorrectCount(),stored.correctCount);
                assertEquals(result.getScorePercent(),stored.scorePercent);
                assertTrue(user.userDao().allProgress().isEmpty()); assertTrue(user.userDao().reviews().isEmpty());
            }
            user.close(); user=UserDatabase.open(context);
            assertEquals(1,user.userDao().quizzes().size());
        } finally { user.close(); catalog.close(); }
    }
    private static QuizViewModel.State state(ActivityScenario<MainActivity> scenario) {
        AtomicReference<QuizViewModel.State> out=new AtomicReference<>();
        scenario.onActivity(a -> out.set(new ViewModelProvider(a).get(QuizViewModel.class).state().getValue()));
        return out.get();
    }
    private static void await(ActivityScenario<MainActivity> scenario,QuizViewModel.Phase expected) {
        long deadline=SystemClock.elapsedRealtime()+30000;
        while (SystemClock.elapsedRealtime()<deadline) {
            QuizViewModel.State state=state(scenario);
            if (state != null && state.phase==expected) return;
            if (state != null && (state.phase==QuizViewModel.Phase.ERROR || state.phase==QuizViewModel.Phase.SAVE_ERROR))
                fail("Unexpected quiz error: "+state.error);
            SystemClock.sleep(50);
        }
        fail("Timed out awaiting "+expected+"; actual="+state(scenario).phase);
    }
    private static void screenshot(Context context,String name) throws Exception {
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory=context.getExternalFilesDir("quiz03-evidence");
        assertNotNull(directory); directory.mkdirs();
        try (FileOutputStream output=new FileOutputStream(new File(directory,name+".png"))) {
            image.compress(Bitmap.CompressFormat.PNG,100,output);
        } finally { image.recycle(); }
    }
}
