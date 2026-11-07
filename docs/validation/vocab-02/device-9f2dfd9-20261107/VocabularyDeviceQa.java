package com.vocamind.app.feature.vocabulary;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.vocamind.app.*;
import com.vocamind.app.core.*;
import com.vocamind.app.data.user.*;
import com.vocamind.app.data.user.entity.*;
import com.vocamind.app.domain.catalog.*;
import com.vocamind.app.domain.common.*;
import com.vocamind.app.domain.vocabulary.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import static org.junit.Assert.*;

/** External, test-only device probe. Refuses to touch the regular app's package/data. */
@RunWith(AndroidJUnit4.class)
public class VocabularyDeviceQa {
    final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    final Context context = instrumentation.getTargetContext();
    MainActivity activity;
    CatalogVocabulary fresh, learned;
    String pack;
    File folder;
    @Before public void setup() throws Exception {
        assertEquals("com.vocamind.app.vocab02qa", context.getPackageName());
        folder = new File(context.getExternalFilesDir(null), "vocab02-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (CatalogProvider catalog = new CatalogProvider(context)) {
            pack = catalog.repository().getPacks().get(0).id;
            List<CatalogVocabulary> words = catalog.repository().getWords(pack);
            fresh = words.get(0); learned = words.get(1);
        }
        assertEquals("Airplane mode must be enabled before QA launch", 1,
            android.provider.Settings.Global.getInt(context.getContentResolver(), "airplane_mode_on", 0));
    }
    void main(Runnable action) { instrumentation.runOnMainSync(action); }
    <T> T onMain(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>(); main(() -> result.set(action.get())); return result.get();
    }
    void waitFor(String reason, BooleanSupplier condition) throws Exception {
        long until = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < until) {
            if (onMain(condition::getAsBoolean)) return;
            Thread.sleep(80);
        }
        throw new AssertionError("Timed out: " + reason);
    }
    MainActivity current() {
        for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
            if (a instanceof MainActivity) return (MainActivity)a;
        return null;
    }
    String text(int id) { View v = current() == null ? null : current().findViewById(id); return v instanceof TextView ? ((TextView)v).getText().toString() : ""; }
    void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync(); Thread.sleep(200);
        android.graphics.Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(folder,name + ".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        }
        bitmap.recycle();
    }
    void log(String name, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(folder,name + ".txt"))) { out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        android.util.Log.i("VOCAB02_QA",name+": "+value);
    }
    void launch() throws Exception {
        Intent intent = new Intent().setClassName(context.getPackageName(),MainActivity.class.getName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity)instrumentation.startActivitySync(intent);
        waitFor("home", () -> current()!=null && current().findViewById(R.id.open_packs)!=null);
    }
    void openWord(int position) throws Exception {
        launch();
        main(() -> current().findViewById(R.id.open_packs).performClick());
        waitFor("pack list", () -> current().findViewById(R.id.pack_list)!=null && ((LinearLayout)current().findViewById(R.id.pack_list)).getChildCount()>0);
        main(() -> ((LinearLayout)current().findViewById(R.id.pack_list)).getChildAt(0).performClick());
        waitFor("word row", () -> {
            RecyclerView list=current().findViewById(R.id.words);
            if(list==null || list.getAdapter()==null || list.getAdapter().getItemCount()<=position) return false;
            list.scrollToPosition(position); return list.findViewHolderForAdapterPosition(position)!=null;
        });
        main(() -> ((RecyclerView)current().findViewById(R.id.words)).findViewHolderForAdapterPosition(position).itemView.performClick());
        waitFor("detail", () -> current().findViewById(R.id.detail_content)!=null && current().findViewById(R.id.detail_content).getVisibility()==View.VISIBLE);
        CatalogVocabulary expected=position==0?fresh:learned;
        assertEquals(expected.lemma,onMain(() -> text(R.id.detail_lemma)));
        assertEquals(expected.pos,onMain(() -> text(R.id.detail_pos)));
        assertEquals(expected.meaningVi,onMain(() -> text(R.id.detail_meaning)));
        assertEquals(expected.exampleEn,onMain(() -> text(R.id.detail_example_en)));
        assertEquals(expected.exampleVi,onMain(() -> text(R.id.detail_example_vi)));
        String ipa=expected.ipa==null||expected.ipa.trim().isEmpty()?"Chưa có phiên âm":expected.ipa;
        assertEquals(ipa,onMain(() -> text(R.id.detail_ipa)));
        assertEquals(expected.stableId,onMain(() -> {
            NavHostFragment host=(NavHostFragment)current().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            return host.getNavController().getCurrentBackStackEntry().getArguments().getString("vocabularyId");
        }));
    }
    void expectSaved(boolean saved) throws Exception { waitFor("confirmed save="+saved, () -> text(R.id.detail_save).equals(saved?"Bỏ lưu":"Lưu") && current().findViewById(R.id.detail_save).isEnabled()); }
    void toggle(boolean target) throws Exception { main(() -> current().findViewById(R.id.detail_save).performClick()); expectSaved(target); }
    void rotate(int orientation) throws Exception {
        main(() -> current().setRequestedOrientation(orientation));
        waitFor("rotation", () -> current()!=null && current().getResources().getConfiguration().orientation ==
            (orientation==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE?2:1) && current().findViewById(R.id.detail_content)!=null && current().findViewById(R.id.detail_content).getVisibility()==View.VISIBLE);
    }
    String snapshot(androidx.sqlite.db.SupportSQLiteDatabase sql) throws Exception {
        StringBuilder out=new StringBuilder();
        List<String> tables=new ArrayList<>();
        try(android.database.Cursor c=sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) { while(c.moveToNext())tables.add(c.getString(0)); }
        for(String table:tables) {
            List<String> rows=new ArrayList<>();
            try(android.database.Cursor c=sql.query("SELECT * FROM `"+table+"`")) {
                while(c.moveToNext()) { StringBuilder row=new StringBuilder(); for(int i=0;i<c.getColumnCount();i++) row.append(c.getColumnName(i)).append('=').append(c.isNull(i)?"NULL":c.getType(i)==4?android.util.Base64.encodeToString(c.getBlob(i),2):c.getString(i)).append('|');rows.add(row.toString()); }
            }
            Collections.sort(rows);out.append(table).append(rows).append('\n');
        }
        return out.toString();
    }
    String catalogSnapshot() throws Exception {
        com.vocamind.app.data.catalog.CatalogDatabase db=com.vocamind.app.data.catalog.CatalogDatabase.open(context,"catalog.db");
        try { return snapshot(db.getOpenHelper().getWritableDatabase()); } finally { db.close(); }
    }
    String learning(UserWordProgress p) { return p.vocabularyId+"|"+p.box+"|"+p.dueAt+"|"+p.learnedAt+"|"+p.lastReviewedAt+"|"+p.reviewCount+"|"+p.consecutiveMistakes+"|"+p.isLeech; }
    String history(UserDao dao) { StringBuilder s=new StringBuilder(); for(ReviewEvent e:dao.reviews())s.append(e.id).append('|').append(e.vocabularyId).append('|').append(e.rating).append('|').append(e.reviewedAt).append('|').append(e.boxBefore).append('|').append(e.boxAfter).append('|').append(e.nextDueAt).append(';');return s.toString(); }
    void persisted(boolean saved) throws Exception {
        UserDatabase db=UserDatabase.open(context);try {
            var p=db.userDao().progress(fresh.stableId);assertNotNull(p);assertEquals(saved,p.isSaved);
            assertEquals(0,p.box);assertNull(p.learnedAt);assertNull(p.lastReviewedAt);assertEquals(0,p.reviewCount);assertEquals(0,p.consecutiveMistakes);assertFalse(p.isLeech);assertTrue(p.dueAt>0);
            assertEquals(1,db.userDao().reviews().size()); // only the explicit learned-word fixture event
            assertTrue(db.userDao().packs().isEmpty());
            log("persisted-"+saved,learning(p)+"|saved="+saved+"|total="+db.userDao().distinctProgressCount());
        } finally {db.close();}
    }
    @Test public void aReadOfflineAndFirstSave() throws Exception {
        UserDatabase db=UserDatabase.open(context);try {
            assertTrue(db.userDao().allProgress().isEmpty());
            UserWordProgress p=new UserWordProgress();p.vocabularyId=learned.stableId;p.box=3;p.dueAt=1800000000000L;p.learnedAt=1700000000000L;p.lastReviewedAt=1700001000000L;p.reviewCount=9;p.consecutiveMistakes=3;p.isLeech=true;p.isSaved=false;
            ReviewEvent e=new ReviewEvent();e.vocabularyId=p.vocabularyId;e.rating="HARD";e.reviewedAt=p.lastReviewedAt;e.boxBefore=3;e.boxAfter=3;e.nextDueAt=p.dueAt;
            db.userDao().recordReview(e,p);
            context.getSharedPreferences("vocab02-qa",0).edit().putString("learning",learning(p)).putString("history",history(db.userDao())).putString("catalog",catalogSnapshot()).commit();
            log("catalog-before",catalogSnapshot());
            log("fixture", "first="+fresh.stableId+" learned="+learned.stableId+" pack="+pack+"\n"+learning(p));
        } finally {db.close();}
        openWord(0);expectSaved(false);screenshot("V02-A-detail-offline");
        main(() -> {
            View save=current().findViewById(R.id.detail_save);save.performClick();
            assertFalse(save.isEnabled());save.performClick();save.performClick();
            current().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });
        waitFor("rotated pending survives", () -> current()!=null && text(R.id.detail_save).equals("Bỏ lưu"));
        expectSaved(true);screenshot("V02-D-save-rotation");rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);expectSaved(true);
        main(() -> current().findViewById(R.id.back).performClick());
        waitFor("Back to words", () -> current().findViewById(R.id.words)!=null);
        openWord(0);expectSaved(true);persisted(true);screenshot("V02-B-saved-reopen");
        log("V02-A-B-D-phase1","PASS: offline actual list/detail fields, null IPA, Back; first-save multi-click pending lock and rotation, persisted saved");
    }
    @Test public void bSavedAfterForceStop() throws Exception {openWord(0);expectSaved(true);persisted(true);screenshot("V02-B-saved-force-stop");}
    @Test public void cSavedAfterRebootThenUnsave() throws Exception {openWord(0);expectSaved(true);persisted(true);screenshot("V02-B-saved-reboot");toggle(false);rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);expectSaved(false);rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);persisted(false);screenshot("V02-B-unsaved");}
    @Test public void dUnsavedAfterForceStop() throws Exception {openWord(0);expectSaved(false);persisted(false);screenshot("V02-B-unsaved-force-stop");}
    @Test public void eUnsavedAfterRebootAndExistingProgress() throws Exception {
        openWord(0);expectSaved(false);persisted(false);screenshot("V02-B-unsaved-reboot");
        openWord(1);expectSaved(false);toggle(true);
        UserDatabase db=UserDatabase.open(context);try { assertEquals(context.getSharedPreferences("vocab02-qa",0).getString("learning",""),learning(db.userDao().progress(learned.stableId)));assertTrue(db.userDao().progress(learned.stableId).isSaved); } finally {db.close();}
        screenshot("V02-C-existing-saved");toggle(false);
        db=UserDatabase.open(context);try {
            assertEquals(context.getSharedPreferences("vocab02-qa",0).getString("learning",""),learning(db.userDao().progress(learned.stableId)));
            assertFalse(db.userDao().progress(learned.stableId).isSaved);
            assertEquals(context.getSharedPreferences("vocab02-qa",0).getString("history",""),history(db.userDao()));
            assertEquals(2,db.userDao().distinctProgressCount());
            log("V02-C-learning-history",learning(db.userDao().progress(learned.stableId))+"\n"+history(db.userDao()));
        } finally {db.close();}
        assertEquals(new String(java.nio.file.Files.readAllBytes(new File(folder,"catalog-before.txt").toPath()),java.nio.charset.StandardCharsets.UTF_8),catalogSnapshot());
        log("V02-C-catalog","PASS: all catalog tables identical to pre-UI baseline, existing learning fields/history identical after Save/Unsave");
        screenshot("V02-C-existing-unsaved");
    }
    @Test public void fControlledErrorsRetryAndIpaRendering() throws Exception {
        launch();ExecutorService worker=Executors.newSingleThreadExecutor();Handler handler=new Handler(Looper.getMainLooper());
        AtomicBoolean persisted=new AtomicBoolean(false),readFailure=new AtomicBoolean(false);AtomicInteger writes=new AtomicInteger();CountDownLatch release=new CountDownLatch(1);
        AtomicReference<VocabularyDetailViewModel> model=new AtomicReference<>();AtomicReference<View> root=new AtomicReference<>();
        main(() -> {
            root.set(LayoutInflater.from(current()).inflate(R.layout.fragment_vocabulary_detail,null));current().setContentView(root.get());
            model.set(new VocabularyDetailViewModel(() -> readFailure.get()?RepoResult.failure(RepoError.READ_FAILED):RepoResult.success(new VocabularyDetail(fresh,persisted.get())), target -> {
                writes.incrementAndGet();try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException ex){throw new RuntimeException(ex);}persisted.set(target);return RepoResult.failure(RepoError.WRITE_FAILED);
            },worker,handler::post,()->{}));
            model.get().state().observeForever(state -> VocabularyDetailFragment.render(root.get(),state));
            root.get().findViewById(R.id.detail_save).setOnClickListener(v->model.get().toggleSaved());root.get().findViewById(R.id.detail_retry).setOnClickListener(v->model.get().refresh());
        });
        try {
            expectSaved(false);
            main(() -> {root.get().findViewById(R.id.detail_save).performClick();root.get().findViewById(R.id.detail_save).performClick();assertFalse(root.get().findViewById(R.id.detail_save).isEnabled());});
            screenshot("V02-D-controlled-pending");release.countDown();
            waitFor("write error",()->model.get().state().getValue().error==RepoError.WRITE_FAILED);screenshot("V02-D-write-error");
            readFailure.set(true);main(()->root.get().findViewById(R.id.detail_retry).performClick());waitFor("read error",()->model.get().state().getValue().error==RepoError.READ_FAILED);
            screenshot("V02-D-read-error");readFailure.set(false);main(()->root.get().findViewById(R.id.detail_retry).performClick());expectSaved(true);assertEquals(1,writes.get());
            CatalogVocabulary ipaFixture=new CatalogVocabulary(fresh.stableId,fresh.lemma,fresh.normalizedLemma,fresh.pos,fresh.meaningVi,fresh.exampleEn,fresh.exampleVi,"/test-fixture/",fresh.sourceId,fresh.contentVersion);
            main(()->VocabularyDetailFragment.render(root.get(),new VocabularyDetailViewModel.State(new VocabularyDetail(ipaFixture,true),false,false,null)));
            assertEquals("/test-fixture/",onMain(()->text(R.id.detail_ipa)));screenshot("V02-A-ipa-present-test-only-render");
            log("V02-D-hooks","PASS on device: delayed worker pending/double click; ambiguous write completion -> error; read failure -> error; Retry read reconciles saved=true, writes=1. Test-only injected ViewModel, not disk failure. IPA-present synthetic render fixture only, catalog unchanged.");
        } finally {release.countDown();worker.shutdownNow();}
    }
    @Test public void gExportEvidence() throws Exception {
        File destination = new File(context.getFilesDir(), "vocab02-export");
        assertTrue(destination.isDirectory() || destination.mkdirs());
        File[] files = folder.listFiles();assertNotNull(files);assertTrue(files.length >= 15);
        for(File file:files) if(file.isFile()) java.nio.file.Files.copy(file.toPath(),new File(destination,file.getName()).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }    @Test public void hLoadingRotationAndReadableExamples() throws Exception {
        openWord(0);expectSaved(false);
        main(()->current().findViewById(R.id.back).performClick());waitFor("list",()->current().findViewById(R.id.words)!=null);
        main(()->{
            RecyclerView list=current().findViewById(R.id.words);list.findViewHolderForAdapterPosition(0).itemView.performClick();
            NavHostFragment host=(NavHostFragment)current().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            host.getChildFragmentManager().executePendingTransactions();
            assertEquals(View.VISIBLE,current().findViewById(R.id.detail_loading).getVisibility());
            current().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });
        waitFor("loading rotation completed",()->current()!=null && current().getResources().getConfiguration().orientation==2 && text(R.id.detail_save).equals("Lưu") && current().findViewById(R.id.detail_content).getVisibility()==View.VISIBLE);
        Thread.sleep(1200);screenshot("V02-D-loading-rotation-stable");expectSaved(false);
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);Thread.sleep(1000);expectSaved(false);
        main(()->((ScrollView)current().findViewById(R.id.detail_scroll)).fullScroll(View.FOCUS_DOWN));
        Thread.sleep(500);assertTrue(onMain(()->current().findViewById(R.id.detail_example_vi).getGlobalVisibleRect(new android.graphics.Rect())));
        screenshot("V02-A-vietnamese-example-scrolled");
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);Thread.sleep(1200);expectSaved(false);screenshot("V02-D-confirmed-landscape");
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);expectSaved(false);
        log("V02-D-loading-confirmed","PASS: orientation requested while loading indicator visible, state reconciles unsaved after rotation; confirmed landscape stable; Vietnamese example reachable by actual scroll.");
    }}