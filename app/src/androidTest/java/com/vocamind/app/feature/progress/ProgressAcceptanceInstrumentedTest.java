package com.vocamind.app.feature.progress;

import android.app.Dialog;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.*;
import com.vocamind.app.core.*;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.user.*;
import com.vocamind.app.data.user.entity.*;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.progress.*;
import com.vocamind.app.feature.quiz.QuizViewModel;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Runs only in the explicitly isolated QA package; real factories, Room and navigation. */
@RunWith(AndroidJUnit4.class) @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ProgressAcceptanceInstrumentedTest {
 private Context context;
 private String originalAirplane,originalWifi;
 @Before public void isolated() throws Exception {
  context=ApplicationProvider.getApplicationContext();
  Assume.assumeTrue("Requires -Pprogress02DeviceIsolation=true; never touch user app data", "com.vocamind.app.progress02validation".equals(context.getPackageName()));
  originalAirplane=shell("settings get global airplane_mode_on").trim();
  originalWifi=shell("settings get global wifi_on").trim();
  // A System UI ANR is an emulator problem, not an application pass. Dismiss only that dialog.
  android.view.accessibility.AccessibilityNodeInfo active=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
  if(active!=null && !active.findAccessibilityNodeInfosByText("System UI").isEmpty()) {
   for(var n:active.findAccessibilityNodeInfosByText("Wait")) n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
  }
  shell("input keyevent 4");
  shell("settings put global airplane_mode_on 1");
  shell("svc wifi disable");
  waitFor(() -> { try { return "0".equals(shell("settings get global wifi_on").trim()) && shell("dumpsys connectivity").contains("Active default network: none"); } catch(Exception e) { throw new RuntimeException(e); } });
  assertOffline();
  try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"progress02-offline.txt"))) {
   out.write(("airplane="+shell("settings get global airplane_mode_on")+"wifi="+shell("settings get global wifi_on")+shell("dumpsys connectivity")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
  android.accessibilityservice.AccessibilityServiceInfo info=InstrumentationRegistry.getInstrumentation().getUiAutomation().getServiceInfo();
  info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
  InstrumentationRegistry.getInstrumentation().getUiAutomation().setServiceInfo(info);
 }
 @After public void restoreNetwork() throws Exception {
  if(originalAirplane!=null) shell("settings put global airplane_mode_on "+originalAirplane);
  if(originalWifi!=null && !"0".equals(originalWifi) && !"3".equals(originalWifi)) shell("svc wifi enable");
 }
 private void assertOffline() throws Exception {
  assertEquals(1,Settings.Global.getInt(context.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0));
  assertEquals(0,Settings.Global.getInt(context.getContentResolver(),"wifi_on",-1));
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^\\s*Active default network:\\s*(\\S+)\\s*$").matcher(shell("dumpsys connectivity"));
  assertTrue("Missing network evidence",m.find());assertEquals("none",m.group(1));
 }
 private static String shell(String command) throws Exception {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
   byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1) bytes.write(b,0,n);
  }
  return bytes.toString("UTF-8");
 }
 private static void waitFor(BooleanSupplier ready) {
  long deadline=SystemClock.elapsedRealtime()+30000;
  while(SystemClock.elapsedRealtime()<deadline) { if(ready.getAsBoolean()) return;SystemClock.sleep(60); }
  fail("Timed out awaiting UI condition");
 }
 private static void navigate(ActivityScenario<MainActivity> s,int destination) {
  s.onActivity(a -> ((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController().navigate(destination));
 }
 private static void back(ActivityScenario<MainActivity> s) { s.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed()); }
 private static boolean content(ActivityScenario<MainActivity> s,int count) {
  boolean[] ready={false};s.onActivity(a -> {
   RecyclerView list=a.findViewById(R.id.progress_list);
   ready[0]=list!=null && list.getVisibility()==View.VISIBLE && list.getAdapter()!=null && list.getAdapter().getItemCount()==count;
  });return ready[0];
 }
 private static View row(ActivityScenario<MainActivity> s,int position) {
  AtomicReference<View> out=new AtomicReference<>();
  s.onActivity(a -> ((RecyclerView)a.findViewById(R.id.progress_list)).scrollToPosition(position));
  waitFor(() -> { s.onActivity(a -> { RecyclerView.ViewHolder h=((RecyclerView)a.findViewById(R.id.progress_list)).findViewHolderForAdapterPosition(position);if(h!=null) out.set(h.itemView); });return out.get()!=null; });
  return out.get();
 }
 private static String text(View v,int id) { return ((TextView)v.findViewById(id)).getText().toString(); }
 private void metrics(ActivityScenario<MainActivity> s,long total,long learning,long reviewing,long mastered,long... boxes) {
  View header=row(s,0);
  s.onActivity(a -> {
   assertEquals(a.getString(R.string.progress_counts,total,learning,reviewing,mastered),text(header,R.id.progress_counts));
   int[] ids={R.id.box_0,R.id.box_1,R.id.box_2,R.id.box_3,R.id.box_4,R.id.box_5};
   for(int i=0;i<6;i++) assertTrue(text(header,ids[i]).endsWith(": "+boxes[i]));
   assertEquals(a.getString(R.string.progress_mastered_explanation),text(header,R.id.mastered_explanation));
   assertTrue(((RecyclerView)a.findViewById(R.id.progress_list)).getAdapter().hasStableIds());
  });
 }
 private static void refresh(ActivityScenario<MainActivity> s) { s.onActivity(a -> a.findViewById(R.id.progress_refresh).performClick()); }
 private void screenshot(String name) throws Exception {
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  waitFor(() -> {
   android.view.accessibility.AccessibilityNodeInfo active=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
   return active!=null && context.getPackageName().contentEquals(active.getPackageName());
  });
  SystemClock.sleep(300);
  shell("screencap -p /data/local/tmp/progress02-"+name+".png");
 }
 private static QuizViewModel.State quiz(ActivityScenario<MainActivity> s) {
  AtomicReference<QuizViewModel.State> out=new AtomicReference<>();
  s.onActivity(a -> out.set(new ViewModelProvider(a).get(QuizViewModel.class).state().getValue()));return out.get();
 }
 private static void phase(ActivityScenario<MainActivity> s,QuizViewModel.Phase phase) {
  waitFor(() -> { QuizViewModel.State value=quiz(s);if(value!=null && (value.phase==QuizViewModel.Phase.ERROR || value.phase==QuizViewModel.Phase.SAVE_ERROR)) fail(value.error);return value!=null && value.phase==phase; });
 }
 private static String signature(ProgressSnapshot snapshot) {
  StringBuilder b=new StringBuilder(Arrays.toString(snapshot.getSummary().getBoxCounts()));
  for(QuizHistoryItem q:snapshot.getQuizHistory()) b.append('|').append(q.getAttemptId()).append(':').append(q.getCompletedAtEpochMillis()).append(':').append(q.getQuestionCount()).append(':').append(q.getCorrectCount()).append(':').append(q.getScorePercent());
  return b.toString();
 }
 @Test public void aFullProductionFlow() throws Exception {
  UserDatabase db=UserDatabase.open(context);
  try(CatalogProvider catalog=new CatalogProvider(context);RepositoryProvider repos=new RepositoryProvider(context,catalog)) {
   assertTrue("A fresh isolated install is required",db.userDao().allProgress().isEmpty());
   assertTrue(db.userDao().quizzes().isEmpty());
   try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
    s.onActivity(a -> a.findViewById(R.id.open_progress).performClick());waitFor(() -> content(s,1));
    metrics(s,0,0,0,0,0,0,0,0,0,0);View header=row(s,0);
    assertEquals(View.VISIBLE,header.findViewById(R.id.progress_empty).getVisibility());
    assertEquals(View.VISIBLE,header.findViewById(R.id.history_empty).getVisibility());screenshot("01-empty");
    QuizAttempt only=new QuizAttempt();only.completedAt=1000;only.questionCount=4;only.correctCount=3;only.scorePercent=75;db.userDao().insertQuiz(only);
    refresh(s);waitFor(() -> content(s,2));metrics(s,0,0,0,0,0,0,0,0,0,0);
    assertEquals(View.GONE,row(s,0).findViewById(R.id.history_empty).getVisibility());screenshot("02-history-only");
    db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM QuizAttempt");
    CatalogDatabase catalogDb=CatalogDatabase.open(context,"catalog.db");
    List<String> words=new ArrayList<>();List<String> packs=new ArrayList<>();
    try {
     for(var w:catalogDb.catalogDao().allVocabulary()) words.add(w.id);
     for(var p:catalogDb.catalogDao().packs()) packs.add(p.id);
     assertEquals(150,words.size());assertTrue(packs.size()>1);
     // Add an explicit cross-pack membership in the QA-only catalog copy.
     for(int i=0;i<2;i++) catalogDb.getOpenHelper().getWritableDatabase().execSQL("INSERT OR IGNORE INTO PackWord (packId,vocabularyId,orderNo) VALUES (?,?,?)",new Object[]{packs.get(i),words.get(0),999});
     assertTrue(catalogDb.catalogDao().words(packs.get(0)).stream().anyMatch(w -> w.id.equals(words.get(0))));
     assertTrue(catalogDb.catalogDao().words(packs.get(1)).stream().anyMatch(w -> w.id.equals(words.get(0))));
    } finally { catalogDb.close(); }
    for(String p:packs) db.userDao().activatePack(p,1000);
    db.userDao().pausePack(packs.get(0));
    assertTrue(repos.vocabulary().setSaved(packs.get(0),words.get(0),true).isSuccess());
    assertNull(db.userDao().progress(words.get(0)).learnedAt);
    int id=0;int[] counts={2,1,3,2,1,3};
    for(int box=0;box<6;box++) for(int n=0;n<counts[box];n++) {
     if(id++==0) continue;UserWordProgress p=new UserWordProgress();p.vocabularyId=words.get(id-1);p.box=box;p.dueAt=0;db.userDao().saveProgress(p);
    }
    refresh(s);waitFor(() -> content(s,1));metrics(s,12,3,5,4,2,1,3,2,1,3);
    assertEquals(View.VISIBLE,row(s,0).findViewById(R.id.history_empty).getVisibility());screenshot("03-progress-only");
    assertTrue(repos.vocabulary().setSaved(packs.get(0),words.get(0),false).isSuccess());
    assertNotNull(db.userDao().progress(words.get(0)));assertEquals(12,db.userDao().allProgress().size());
    long[] ids=new long[12];
    for(int i=0;i<12;i++) {
     QuizAttempt q=new QuizAttempt();q.completedAt=1794096000000L;q.questionCount=i==1?4:i==2?6:10;
     q.correctCount=i==1?3:i==2?2:7;q.scorePercent=i==1?75:i==2?33:70;ids[i]=db.userDao().insertQuiz(q);
    }
    refresh(s);waitFor(() -> content(s,13));metrics(s,12,3,5,4,2,1,3,2,1,3);screenshot("04-fixture-top");
    for(int i=0;i<12;i++) {
     final int position=i;View item=row(s,i+1);
     s.onActivity(a -> {
      RecyclerView list=a.findViewById(R.id.progress_list);assertEquals(ids[11-position],list.getAdapter().getItemId(position+1));
      int expected=11-position==1?75:11-position==2?33:70;
      assertTrue(text(item,R.id.history_result).contains(expected+"%"));
     });
    }
    screenshot("05-history-last");s.recreate();waitFor(() -> content(s,13));metrics(s,12,3,5,4,2,1,3,2,1,3);
    // A real production review transaction while Progress is on the back stack.
    navigate(s,R.id.todayFragment);
    repos.recordReview(Clock.fixed(Instant.ofEpochMilli(1794096000000L),ZoneOffset.UTC),ZoneOffset.UTC).execute(words.get(2),Rating.REMEMBERED);
    back(s);waitFor(() -> content(s,13));metrics(s,12,2,6,4,2,0,4,2,1,3);
    assertEquals(1,db.userDao().reviews().size());
    // Exercise the newly merged Today -> Flashcard UI and its production writer.
    navigate(s,R.id.todayFragment);
    waitFor(() -> { boolean[] ready={false};s.onActivity(a -> { View start=a.findViewById(R.id.start);ready[0]=start!=null && start.getVisibility()==View.VISIBLE; });return ready[0]; });
    s.onActivity(a -> a.findViewById(R.id.start).performClick());
    waitFor(() -> { boolean[] ready={false};s.onActivity(a -> { View card=a.findViewById(R.id.card);ready[0]=card!=null && card.getVisibility()==View.VISIBLE; });return ready[0]; });
    s.onActivity(a -> a.findViewById(R.id.card).performClick());
    s.onActivity(a -> a.findViewById(R.id.rate_hard).performClick());
    waitFor(() -> db.userDao().reviews().size()==2);
    screenshot("06a-flashcard-saved");back(s);back(s);waitFor(() -> content(s,13));
    metrics(s,12,2,6,4,2,0,4,2,1,3);assertEquals(12,db.userDao().allProgress().size());
    // Corrupt data is a visible error, never a zero or filtered-out record.
    db.getOpenHelper().getWritableDatabase().execSQL("UPDATE QuizAttempt SET scorePercent=1 WHERE id=?",new Object[]{ids[0]});
    refresh(s);waitFor(() -> { boolean[] error={false};s.onActivity(a -> error[0]=a.findViewById(R.id.progress_error).getVisibility()==View.VISIBLE && a.findViewById(R.id.progress_list).getVisibility()==View.GONE);return error[0]; });screenshot("06-error");
    db.getOpenHelper().getWritableDatabase().execSQL("UPDATE QuizAttempt SET scorePercent=70 WHERE id=?",new Object[]{ids[0]});
    s.onActivity(a -> a.findViewById(R.id.progress_retry).performClick());waitFor(() -> content(s,13));
    for(String p:packs) db.userDao().activatePack(p,1000);
    navigate(s,R.id.quizFragment);phase(s,QuizViewModel.Phase.ANSWERING);assertEquals(10,quiz(s).session.getQuestions().size());
    for(int i=0;i<10;i++) {
     if(i>0) SystemClock.sleep(650);s.onActivity(a -> a.findViewById(R.id.quiz_option_1).performClick());
     if(i<9) { phase(s,QuizViewModel.Phase.ANSWERED);s.onActivity(a -> a.findViewById(R.id.quiz_next).performClick());phase(s,QuizViewModel.Phase.ANSWERING); }
    }
    phase(s,QuizViewModel.Phase.RESULT);var result=quiz(s).session.getResult().get();screenshot("07-quiz-result");
    assertEquals(13,db.userDao().quizzes().size());var persisted=db.userDao().quizzes().stream().filter(q -> q.completionKey!=null).findFirst().get();
    assertEquals(result.getCorrectCount(),persisted.correctCount);assertEquals(result.getScorePercent(),persisted.scorePercent);
    assertEquals(12,db.userDao().allProgress().size());assertEquals(2,db.userDao().reviews().size());
    s.recreate();phase(s,QuizViewModel.Phase.RESULT);back(s);waitFor(() -> content(s,14));
    metrics(s,12,2,6,4,2,0,4,2,1,3);assertEquals(13,db.userDao().quizzes().size());
    View latest=row(s,1);s.onActivity(a -> assertEquals(a.getString(R.string.progress_quiz_result,result.getQuestionCount(),result.getCorrectCount(),result.getWrongCount(),result.getScorePercent()),text(latest,R.id.history_result)));
    navigate(s,R.id.quizFragment);phase(s,QuizViewModel.Phase.RESULT);
    s.onActivity(a -> a.findViewById(R.id.quiz_action).performClick());phase(s,QuizViewModel.Phase.ANSWERING);
    SystemClock.sleep(650);s.onActivity(a -> a.findViewById(R.id.quiz_option_2).performClick());phase(s,QuizViewModel.Phase.ANSWERED);
    back(s);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    // Dialog buttons live in another window; access through UiAutomation without extra dependencies.
    android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
    List<android.view.accessibility.AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByViewId("android:id/button1");
    assertFalse("Exit confirmation missing",buttons.isEmpty());assertTrue(buttons.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
    waitFor(() -> content(s,14));assertEquals(13,db.userDao().quizzes().size());
    metrics(s,12,2,6,4,2,0,4,2,1,3);screenshot("08-completed-only");
    assertOffline();ProgressReadResult snapshot=repos.progressUseCase().execute();assertTrue(snapshot.isSuccess());
    assertTrue(context.getSharedPreferences("progress02-evidence",0).edit().putString("signature",signature(snapshot.getSnapshot())).commit());
    try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"progress02-expected.txt"))) { out.write(signature(snapshot.getSnapshot()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
   }
  } finally { db.close(); }
 }
 @Test public void bPersistenceProbe() throws Exception {
  String expected=context.getSharedPreferences("progress02-evidence",0).getString("signature",null);assertNotNull("Run aFullProductionFlow first",expected);
  try(CatalogProvider c=new CatalogProvider(context);RepositoryProvider r=new RepositoryProvider(context,c);ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
   ProgressReadResult read=r.progressUseCase().execute();assertTrue(read.isSuccess());assertEquals(expected,signature(read.getSnapshot()));
   s.onActivity(a -> a.findViewById(R.id.open_progress).performClick());waitFor(() -> content(s,14));
   metrics(s,12,2,6,4,2,0,4,2,1,3);row(s,13);screenshot("09-persisted-history");assertOffline();
  }
 }
 @Test public void cSmallDarkLargeFont() throws Exception {
  assertTrue(context.getResources().getConfiguration().fontScale>=1.9f);
  assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
   s.onActivity(a -> a.findViewById(R.id.open_progress).performClick());waitFor(() -> content(s,14));
   View header=row(s,0);
   s.onActivity(a -> {
    assertTrue(a.getResources().getConfiguration().screenWidthDp<=360);
    View refresh=a.findViewById(R.id.progress_refresh);
    assertTrue(refresh.getHeight()>=48*a.getResources().getDisplayMetrics().density);
    assertNoEllipsis(header);
   });screenshot("10-dark-large-top");
   s.onActivity(a -> { RecyclerView list=a.findViewById(R.id.progress_list);list.scrollBy(0,header.getHeight()-list.getHeight()); });
   screenshot("11-dark-large-boxes");row(s,13);screenshot("12-dark-large-history");
   s.onActivity(a -> a.findViewById(R.id.progress_refresh).performClick());waitFor(() -> content(s,14));
  }
 }
 private static void assertNoEllipsis(View view) {
  if(view instanceof TextView && ((TextView)view).getLayout()!=null) {
   android.text.Layout layout=((TextView)view).getLayout();
   for(int i=0;i<layout.getLineCount();i++) assertEquals("Clipped label",0,layout.getEllipsisCount(i));
  }
  if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) assertNoEllipsis(((ViewGroup)view).getChildAt(i));
 }

 @Test public void dActualLandscapeRotation() throws Exception {
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
   s.onActivity(a -> a.findViewById(R.id.open_progress).performClick());waitFor(() -> content(s,14));
   s.onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
   waitFor(() -> { boolean[] landscape={false};s.onActivity(a -> landscape[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE);return landscape[0]; });
   waitFor(() -> content(s,14));metrics(s,12,2,6,4,2,0,4,2,1,3);row(s,13);screenshot("13-landscape-history");
   s.onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
   waitFor(() -> { boolean[] portrait={false};s.onActivity(a -> portrait[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT);return portrait[0]; });
   waitFor(() -> content(s,14));metrics(s,12,2,6,4,2,0,4,2,1,3);
  }
 }

}
