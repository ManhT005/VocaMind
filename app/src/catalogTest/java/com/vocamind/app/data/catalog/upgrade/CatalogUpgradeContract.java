package com.vocamind.app.data.catalog.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.data.catalog.*;
import com.vocamind.app.data.repository.UserDataMapper;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.data.user.entity.*;
import com.vocamind.app.domain.quiz.QuizVocabulary;
import org.json.*;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** All files and SQLite writes are isolated from real catalog/userdata and source assets. */
public abstract class CatalogUpgradeContract {
    protected abstract InputStream openCandidate(String name) throws IOException;
    protected abstract InputStream openUserSchema() throws IOException;
    private Context context;
    private File root, generations, userdata, legacy, a, b;
    private String userName;
    private UserDatabase user;
    private ExecutorService worker;
    private static final String ID = "90562a98e8686267";
    private static final String NEW_ID = "1111111111111111";
    @Before public void setup() throws Exception {
        context = ApplicationProvider.getApplicationContext(); worker = Executors.newSingleThreadExecutor();
        root = Files.createTempDirectory(context.getCacheDir().toPath(), "data08-").toFile();
        generations = new File(root, "generations"); legacy = new File(root, "legacy.db");
        userName = "data08-" + UUID.randomUUID() + ".db"; userdata = context.getDatabasePath(userName);
        run(() -> {
            a = new File(root, "A"); b = new File(root, "B"); a.mkdir(); b.mkdir();
            for (String name : CatalogStartupCoordinator.REQUIRED) {
                try (InputStream in = context.getAssets().open("database/" + name)) { CatalogGenerationStore.copy(in, new File(a, name)); }
                Files.copy(new File(a, name).toPath(), new File(b, name).toPath());
            }
            try (SQLiteDatabase sql = SQLiteDatabase.openDatabase(new File(b, "catalog.db").getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                sql.execSQL("UPDATE LearningPack SET description='Generation B', version='1.0.1'");
                sql.execSQL("UPDATE VocabularyEntry SET contentVersion='1.0.1'");
            }
            JSONObject meta = json(b, "catalog.metadata.json"); meta.put("contentVersion", "1.0.1"); write(b, "catalog.metadata.json", meta);
            refresh(b); return null;
        });
    }
    @After public void cleanup() throws Exception {
        try { run(() -> { if (user != null) user.close(); return null; }); }
        finally {
            worker.shutdownNow(); context.deleteDatabase(userName);
            try (var paths = Files.walk(root.toPath())) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); } }); }
        }
    }
    private <T> T run(Callable<T> task) throws Exception {
        try { return worker.submit(task).get(120, TimeUnit.SECONDS); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) throw (Exception)e.getCause();
            throw new AssertionError(e.getCause());
        }
    }
    private CatalogStartupCoordinator coordinator() throws Exception { return coordinator(step -> {}); }
    private CatalogStartupCoordinator coordinator(CatalogStartupCoordinator.Faults faults) throws Exception {
        return new CatalogStartupCoordinator(context, generations, userdata, legacy, faults);
    }
    private CatalogStartupCoordinator.Source source(File directory) { return name -> new FileInputStream(new File(directory, name)); }
    private JSONObject json(File dir, String name) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8));
    }
    private void write(File dir, String name, JSONObject json) throws Exception {
        Files.write(new File(dir, name).toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
    }
    private void refresh(File dir) throws Exception {
        String dbName = "data08-probe-" + UUID.randomUUID() + ".db";
        CatalogDatabase db = Room.databaseBuilder(context, CatalogDatabase.class, dbName).createFromFile(new File(dir,"catalog.db")).build();
        try {
            List<QuizVocabulary> words = new ArrayList<>();
            for (var row : db.catalogDao().allVocabulary()) words.add(UserDataMapper.quizVocabulary(CatalogMapper.vocabulary(row)));
            JSONObject policy = json(dir, "quiz-policy.json"); policy.put("projectionSha256", QuizPolicyAsset.projectionDigest(words)); write(dir,"quiz-policy.json",policy);
            JSONObject metadata = json(dir,"catalog.metadata.json");
            metadata.put("uniqueVocabularyCount",words.size());
            JSONObject counts = new JSONObject(); int memberships = 0;
            for (var pack : db.catalogDao().packs()) {
                int count = db.catalogDao().wordCount(pack.id); counts.put(pack.id,count); memberships += count;
            }
            metadata.put("packCounts",counts).put("membershipCount",memberships); write(dir,"catalog.metadata.json",metadata);
        } finally { db.close(); context.deleteDatabase(dbName); }
        JSONObject metadata = json(dir,"catalog.metadata.json"); metadata.put("sha256",CatalogGenerationStore.hash(new File(dir,"catalog.db"))); write(dir,"catalog.metadata.json",metadata);
    }
    private void seed() {
        user = UserDatabase.open(context,userName);
        UserPack p = new UserPack(); p.packId="pack_toeic_basic"; p.activatedAt=42; p.isActive=true; user.userDao().savePack(p);
        UserPack paused = new UserPack(); paused.packId="pack_academic"; paused.activatedAt=24; paused.isActive=false; user.userDao().savePack(paused);
        UserWordProgress progress = new UserWordProgress(); progress.vocabularyId=ID; progress.box=2; progress.dueAt=300;
        progress.learnedAt=100L; progress.lastReviewedAt=200L; progress.reviewCount=7; progress.isSaved=true; progress.isLeech=true; progress.consecutiveMistakes=4;
        user.userDao().saveProgress(progress);
        try (var db=SQLiteDatabase.openDatabase(new File(a,"catalog.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
             var rows=db.rawQuery("SELECT id FROM VocabularyEntry WHERE id<>? ORDER BY id",new String[]{ID})) {
            int index=0;
            while(rows.moveToNext()) {
                UserWordProgress other=new UserWordProgress();other.vocabularyId=rows.getString(0);
                other.box=index==0?0:index==1?5:1;other.isSaved=index==0;
                other.learnedAt=index==0?null:101L;other.dueAt=301+index;
                user.userDao().saveProgress(other);index++;
            }
        }
        ReviewEvent event = new ReviewEvent(); event.vocabularyId=ID; event.rating="HARD"; event.reviewedAt=200;event.boxBefore=2;event.boxAfter=2;event.nextDueAt=300;user.userDao().insertReview(event);
        QuizAttempt quiz = new QuizAttempt();quiz.completedAt=200;quiz.questionCount=4;quiz.correctCount=3;quiz.scorePercent=75;user.userDao().insertQuiz(quiz);
        UserPreference pref=user.userDao().preference();pref.dailyNewWords=9;pref.dailyGoalMinutes=25;pref.reminderHour=8;pref.reminderEnabled=true;user.userDao().savePreference(pref);
    }
    private String snapshot() {
        StringBuilder out = new StringBuilder();
        for(String table:Arrays.asList("UserPack","UserWordProgress","ReviewEvent","QuizAttempt","UserPreference")) {
            try(var c=user.getOpenHelper().getReadableDatabase().query("SELECT * FROM "+table+" ORDER BY 1")) {
                while(c.moveToNext())for(int i=0;i<c.getColumnCount();i++)out.append(table).append(':').append(c.getColumnName(i)).append(':').append(c.getType(i)).append('=').append(c.getString(i)).append(';');
            }
        }
        return out.toString();
    }
    private void removeWord(File directory) throws Exception {
        try(var sql=SQLiteDatabase.openDatabase(new File(directory,"catalog.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
            sql.execSQL("DELETE FROM PackWord WHERE vocabularyId=?",new Object[]{ID});
            sql.execSQL("DELETE FROM VocabularyFts WHERE rowid=(SELECT rowid FROM VocabularyEntry WHERE id=?)",new Object[]{ID});
            sql.execSQL("DELETE FROM VocabularyEntry WHERE id=?",new Object[]{ID});
        }
        refresh(directory);
    }
    private void assertReason(CatalogUpgradeException failure,String reason) {
        Throwable cause=failure;
        while(cause!=null) {if(String.valueOf(cause.getMessage()).contains(reason))return;cause=cause.getCause();}
        fail("Expected rejection: "+reason+"; got "+failure);
    }
    @Test public void firstInstallOpensRealBundleWithoutCreatingUserdata() throws Exception {
        run(() -> {
            CatalogGeneration g=coordinator().prepare(source(a)); assertFalse(userdata.exists());
            CatalogDatabase db=g.openDatabase(context); try { assertEquals(4,db.catalogDao().packs().size()); } finally { db.close(); }
            assertEquals(g.id,coordinator().recover().id);
            user=UserDatabase.open(context,userName);var preference=user.userDao().preference();
            assertEquals(5,preference.dailyNewWords);assertEquals(10,preference.dailyGoalMinutes);
            assertFalse(preference.reminderEnabled);assertEquals(20,preference.reminderHour);return null;
        });
    }
    @Test public void upgradeAndRollbackKeepAllFiveUserTablesAndBundleBytes() throws Exception {
        run(() -> {
            var c=coordinator();var old=c.prepare(source(a));seed();String before=snapshot();
            String hash=CatalogGenerationStore.hash(old.bundleFile("catalog.db"));
            var next=c.prepare(source(b)); assertNotEquals(old.id,next.id);assertEquals(before,snapshot());
            var db=next.openDatabase(context);
            try { assertEquals("Generation B",db.catalogDao().pack("pack_toeic_basic").description);
                assertEquals("1.0.1",db.catalogDao().vocabulary(ID).contentVersion); }
            finally { db.close(); }
            assertEquals(hash,CatalogGenerationStore.hash(old.bundleFile("catalog.db")));
            assertEquals(old.id,c.rollback().id);assertEquals(before,snapshot());
            user.close();user=UserDatabase.open(context,userName);assertEquals(before,snapshot());
            System.out.println("DATA08_EVIDENCE "+new JSONObject().put("sdk",android.os.Build.VERSION.SDK_INT)
                    .put("generationA",old.id).put("generationB",next.id)
                    .put("bundleASha256",hash).put("bundleBSha256",CatalogGenerationStore.hash(next.bundleFile("catalog.db")))
                    .put("snapshotBeforeSha256",CatalogStartupCoordinator.digest(before))
                    .put("snapshotAfterSha256",CatalogStartupCoordinator.digest(snapshot())));
            return null;
        });
    }
    @Test public void exportedUserdataV1SurvivesCatalogUpgradeMigrationAndRollback() throws Exception {
        run(() -> {
            var coordinator = coordinator();
            var old = coordinator.prepare(source(a));
            LegacyUserdataFixture.create(context, userName, openUserSchema(), ID);
            String before = LegacyUserdataFixture.snapshot(context, userName, 1, true);
            var upgraded = coordinator.prepare(source(b));
            assertNotEquals(old.id, upgraded.id);
            assertEquals(before, LegacyUserdataFixture.snapshot(context, userName, 1, true));

            UserDatabase migrated = UserDatabase.open(context, userName);
            migrated.close();
            assertEquals(before, LegacyUserdataFixture.snapshot(context, userName, 2, true));
            LegacyUserdataFixture.verifyCompletionKey(context, userName);
            String after = LegacyUserdataFixture.snapshot(context, userName, 2, false);

            assertEquals(old.id, coordinator.rollback().id);
            assertEquals(old.id, coordinator().recover().id);
            assertEquals(after, LegacyUserdataFixture.snapshot(context, userName, 2, false));
            return null;
        });
    }

    @Test public void repeatedPrepareIsIdempotent() throws Exception {
        run(() -> { var c=coordinator();var first=c.prepare(source(a));seed();String before=snapshot();
            var noStage=coordinator(step -> {throw new AssertionError("Identical bundle must not be staged: "+step);});
            assertEquals(first.id,noStage.prepare(source(a)).id);assertEquals(before,snapshot());
            assertEquals(1,Arrays.stream(generations.listFiles()).filter(File::isDirectory).count());return null; });
    }
    @Test public void failureAtEveryBoundaryPreservesOldAndUserdata() throws Exception {
        run(() -> {
            var initial=coordinator().prepare(source(a));seed();String before=snapshot();
            for(var step:CatalogStartupCoordinator.Step.values()) {
                var broken=coordinator(point -> { if(point==step)throw new IOException("Injected "+step); });
                assertThrows(CatalogUpgradeException.class,()->broken.prepare(source(b)));
                assertEquals(initial.id,coordinator().recover().id);assertEquals(before,snapshot());
            }
            return null;
        });
    }
    @Test public void interruptedPublicationRecoversPendingGenerationOnNewCoordinator() throws Exception {
        run(() -> {
            coordinator().prepare(source(a));seed();String before=snapshot();
            var crashing=coordinator(step -> { if(step==CatalogStartupCoordinator.Step.AFTER_PUBLISH)throw new SimulatedDeath(); });
            assertThrows(SimulatedDeath.class,()->crashing.prepare(source(b)));
            assertEquals("PENDING",new JSONObject(new String(Files.readAllBytes(new File(generations,"active.json").toPath()),StandardCharsets.UTF_8)).getString("phase"));
            var recovered=coordinator().recover();assertEquals("1.0.1",new JSONObject(recovered.text("catalog.metadata.json")).getString("contentVersion"));
            assertEquals(before,snapshot());return null;
        });
    }
    @Test public void simulatedDeathAtEveryBoundaryRecoversPersistedSelection() throws Exception {
        run(() -> {
            seed();String before=snapshot();
            for(var step:CatalogStartupCoordinator.Step.values()) {
                File isolated=new File(root,"crash-"+step);
                var initial=new CatalogStartupCoordinator(context,isolated,userdata,legacy,point -> {});
                var old=initial.prepare(source(a));
                var interrupted=new CatalogStartupCoordinator(context,isolated,userdata,legacy,point -> {if(point==step)throw new SimulatedDeath();});
                assertThrows(SimulatedDeath.class,()->interrupted.prepare(source(b)));
                var restarted=new CatalogStartupCoordinator(context,isolated,userdata,legacy,point -> {});
                var recovered=restarted.recover();
                if(step.ordinal()<CatalogStartupCoordinator.Step.AFTER_PUBLISH.ordinal())assertEquals(old.id,recovered.id);
                else assertEquals("1.0.1",new JSONObject(recovered.text("catalog.metadata.json")).getString("contentVersion"));
                assertEquals(before,snapshot());
            }
            return null;
        });
    }
    @Test public void partialCopyIoFailureNeverPublishesHalfGeneration() throws Exception {
        run(() -> {
            var old=coordinator().prepare(source(a));seed();String before=snapshot();
            CatalogStartupCoordinator.Source broken=name -> {
                if(!"catalog.db".equals(name))return source(b).open(name);
                return new InputStream(){int copied;
                    @Override public int read() throws IOException {if(copied++>=16)throw new IOException("ENOSPC injected during copy");return 0;}
                };
            };
            assertReason(assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(broken)),"ENOSPC injected");
            assertEquals(old.id,coordinator().recover().id);assertEquals(before,snapshot());return null;
        });
    }
    @Test public void pendingCorruptCandidateFallsBackToVerifiedOld() throws Exception {
        run(() -> {
            var old=coordinator().prepare(source(a));seed();String before=snapshot();
            var crashing=coordinator(step -> {if(step==CatalogStartupCoordinator.Step.AFTER_PUBLISH)throw new SimulatedDeath();});
            assertThrows(SimulatedDeath.class,()->crashing.prepare(source(b)));
            String active=json(generations,"active.json").getString("active");
            Files.write(new File(generations,active+"/bundle/content-credits.json").toPath(),"bad".getBytes(StandardCharsets.UTF_8));
            assertEquals(old.id,coordinator().recover().id);assertEquals(before,snapshot());
            var repaired=coordinator().prepare(source(b));
            assertEquals(active,repaired.id);assertEquals(before,snapshot());
            assertEquals(active,coordinator().recover().id);
            assertEquals(1,Arrays.stream(generations.listFiles()).filter(f -> f.getName().startsWith("rejected-")).count());return null;
        });
    }
    @Test public void referencedVocabularyRemovalIsRejected() throws Exception {
        run(() -> {
            var old=coordinator().prepare(source(a));seed();String before=snapshot();
            removeWord(b);assertReason(assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b))),"referenced vocabulary");
            assertEquals(old.id,coordinator().recover().id);assertEquals(before,snapshot());return null;
        });
    }
    @Test public void savedOnlyAndHistoryOnlyReferencesAlsoBlockRemoval() throws Exception {
        run(() -> {
            var old=coordinator().prepare(source(a));seed();removeWord(b);
            var sql=user.getOpenHelper().getWritableDatabase();
            sql.execSQL("DELETE FROM ReviewEvent WHERE vocabularyId=?",new Object[]{ID});
            sql.execSQL("UPDATE UserWordProgress SET learnedAt=NULL,isSaved=1 WHERE vocabularyId=?",new Object[]{ID});
            String saved=snapshot();
            assertReason(assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b))),"referenced vocabulary");
            assertEquals(saved,snapshot());
            sql.execSQL("DELETE FROM UserWordProgress WHERE vocabularyId=?",new Object[]{ID});
            ReviewEvent event=new ReviewEvent();event.vocabularyId=ID;event.rating="REMEMBERED";event.reviewedAt=999;event.nextDueAt=1000;user.userDao().insertReview(event);
            String history=snapshot();
            assertReason(assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b))),"referenced vocabulary");
            assertEquals(old.id,coordinator().recover().id);assertEquals(history,snapshot());return null;
        });
    }
    @Test public void pausedPackRemovalAlsoBlocksUpgrade() throws Exception {
        run(() -> {
            var old=coordinator().prepare(source(a));seed();String before=snapshot();
            try(var sql=SQLiteDatabase.openDatabase(new File(b,"catalog.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
                sql.execSQL("DELETE FROM PackWord WHERE packId='pack_academic'");sql.execSQL("DELETE FROM LearningPack WHERE id='pack_academic'");
            }
            refresh(b);assertReason(assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b))),"referenced pack");
            assertEquals(old.id,coordinator().recover().id);assertEquals(before,snapshot());return null;
        });
    }
    @Test public void missingFtsPostingIsRejectedBeforePublication() throws Exception {
        run(() -> {
            var old=coordinator().prepare(source(a));seed();String before=snapshot();
            try(var sql=SQLiteDatabase.openDatabase(new File(b,"catalog.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
                sql.execSQL("DELETE FROM VocabularyFts WHERE rowid=(SELECT rowid FROM VocabularyEntry WHERE id=?)",new Object[]{ID});
            }
            refresh(b);assertReason(assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b))),"FTS mapping");
            assertEquals(old.id,coordinator().recover().id);assertEquals(before,snapshot());return null;
        });
    }
    @Test public void realCandidateIsLimitedToQaAndNeverPromotesApproval() throws Exception {
        run(() -> {
            File candidate=new File(root,"qa-input");assertTrue(candidate.mkdir());
            for(String name:CatalogStartupCoordinator.FILES) {
                try(InputStream in=openCandidate(name)){CatalogGenerationStore.copy(in,new File(candidate,name));}
            }
            android.content.ContextWrapper qa=new android.content.ContextWrapper(context) {
                @Override public String getPackageName(){return context.getPackageName()+".qa";}
                @Override public Context getApplicationContext(){return this;}
            };
            var c=new CatalogStartupCoordinator(qa,generations,userdata,legacy,step -> {});
            var selected=c.prepare(source(candidate));
            assertFalse(new JSONObject(selected.text("manifest.json")).getBoolean("releaseAuthorized"));
            var db=selected.openDatabase(qa);try{assertEquals(150,db.catalogDao().allVocabulary().size());}finally{db.close();}
            File blocked=new File(root,"non-qa");
            var production=new CatalogStartupCoordinator(context,blocked,userdata,legacy,step -> {});
            assertReason(assertThrows(CatalogUpgradeException.class,()->production.prepare(source(candidate))),"not authorized");
            assertFalse(userdata.exists());return null;
        });
    }
    @Test public void rollbackRejectsNewReferencesWithoutErasingThem() throws Exception {
        run(() -> {
            coordinator().prepare(source(a));seed();
            try(var sql=SQLiteDatabase.openDatabase(new File(b,"catalog.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
                sql.execSQL("INSERT INTO VocabularyEntry(rowid,id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                        +"SELECT 99,'"+NEW_ID+"','delta','delta','noun','đồng bằng',exampleEn,exampleVi,NULL,sourceId,'1.0.1' FROM VocabularyEntry LIMIT 1");
                sql.execSQL("INSERT INTO VocabularyFts(rowid,lemma,normalizedLemma,meaningVi) SELECT rowid,lemma,normalizedLemma,meaningVi FROM VocabularyEntry WHERE id='"+NEW_ID+"'");
            }
            refresh(b);var newer=coordinator().prepare(source(b));
            UserWordProgress row=new UserWordProgress();row.vocabularyId=NEW_ID;row.box=0;user.userDao().saveProgress(row);
            String before=snapshot();assertThrows(CatalogUpgradeException.class,()->coordinator().rollback());
            assertEquals(newer.id,coordinator().recover().id);assertEquals(before,snapshot());return null;
        });
    }
    @Test public void invalidMetadataCreditsPolicyAndSchemaAreRejected() throws Exception {
        run(() -> {
            var old=coordinator().prepare(source(a));seed();String before=snapshot();
            for(String name:Arrays.asList("catalog.metadata.json","content-credits.json","quiz-policy.json")) {
                byte[] good=Files.readAllBytes(new File(b,name).toPath());
                Files.write(new File(b,name).toPath(),"{}".getBytes(StandardCharsets.UTF_8));
                assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b)));
                Files.write(new File(b,name).toPath(),good);
                assertEquals(old.id,coordinator().recover().id);assertEquals(before,snapshot());
            }
            try(var sql=SQLiteDatabase.openDatabase(new File(b,"catalog.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){sql.setVersion(99);}
            // Do not refresh through Room: a matching checksum still must not bypass schema rejection.
            JSONObject meta=json(b,"catalog.metadata.json");meta.put("sha256",CatalogGenerationStore.hash(new File(b,"catalog.db")));write(b,"catalog.metadata.json",meta);
            assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b)));assertEquals(before,snapshot());return null;
        });
    }
    @Test public void matchingLegacyIsImportedButMismatchedLegacyIsNotOverwritten() throws Exception {
        run(() -> {
            Files.copy(new File(a,"catalog.db").toPath(),legacy.toPath());seed();String before=snapshot();String hash=CatalogGenerationStore.hash(legacy);
            coordinator().prepare(source(a));assertEquals(hash,CatalogGenerationStore.hash(legacy));assertEquals(before,snapshot());return null;
        });
    }
    @Test public void legacySchemaMismatchFailsWithoutReset() throws Exception {
        run(() -> {
            Files.copy(new File(a,"catalog.db").toPath(),legacy.toPath());
            try(var sql=SQLiteDatabase.openDatabase(legacy.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){sql.setVersion(1);}
            seed();String before=snapshot();String hash=CatalogGenerationStore.hash(legacy);
            assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(a)));
            assertEquals(before,snapshot());assertEquals(hash,CatalogGenerationStore.hash(legacy));
            byte[] corrupt="invalid legacy sqlite".getBytes(StandardCharsets.UTF_8);Files.write(legacy.toPath(),corrupt);
            assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(a)));
            assertArrayEquals(corrupt,Files.readAllBytes(legacy.toPath()));assertEquals(before,snapshot());return null;
        });
    }
    @Test public void concurrentStartupSelectsOneGeneration() throws Exception {
        run(() -> {
            coordinator().prepare(source(a));ExecutorService parallel=Executors.newFixedThreadPool(2);
            try {
                List<Callable<String>> tasks=Arrays.asList(()->coordinator().prepare(source(b)).id,()->coordinator().prepare(source(b)).id);
                var results=parallel.invokeAll(tasks,60,TimeUnit.SECONDS);assertEquals(results.get(0).get(),results.get(1).get());
                assertEquals(results.get(0).get(),coordinator().recover().id);
            } finally {parallel.shutdownNow();}return null;
        });
    }
    @Test public void corruptUserdataIsNeverRecreatedOrDeleted() throws Exception {
        run(() -> {
            coordinator().prepare(source(a));seed();user.close();
            byte[] broken="not sqlite".getBytes(StandardCharsets.UTF_8);Files.write(userdata.toPath(),broken);
            assertThrows(CatalogUpgradeException.class,()->coordinator().prepare(source(b)));
            assertArrayEquals(broken,Files.readAllBytes(userdata.toPath()));return null;
        });
    }
    static final class SimulatedDeath extends Error { }
}
