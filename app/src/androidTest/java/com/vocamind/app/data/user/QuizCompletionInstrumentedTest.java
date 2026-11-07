package com.vocamind.app.data.user;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.data.user.entity.QuizAttempt;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class QuizCompletionInstrumentedTest {
    @Test public void exportedV1MigratesWithoutLosingHistoryAndRetryIsIdempotent() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        String name="quiz03-migration-"+java.util.UUID.randomUUID()+".db";
        try {
            try (var input=InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                    .open("com.vocamind.app.data.user.UserDatabase/1.json");
                 SQLiteDatabase raw=context.openOrCreateDatabase(name,0,null)) {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[4096]; int count;
                while ((count=input.read(buffer)) != -1) bytes.write(buffer,0,count);
                JSONObject schema=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getJSONObject("database");
                JSONArray entities=schema.getJSONArray("entities");
                for (int i=0;i<entities.length();i++) {
                    JSONObject entity=entities.getJSONObject(i);
                    raw.execSQL(entity.getString("createSql").replace("${TABLE_NAME}",entity.getString("tableName")));
                    JSONArray indices=entity.getJSONArray("indices");
                    for (int j=0;j<indices.length();j++) raw.execSQL(indices.getJSONObject(j).getString("createSql")
                            .replace("${TABLE_NAME}",entity.getString("tableName")));
                }
                JSONArray setup=schema.getJSONArray("setupQueries");
                for (int i=0;i<setup.length();i++) raw.execSQL(setup.getString(i));
                raw.execSQL("INSERT INTO QuizAttempt(completedAt,questionCount,correctCount,scorePercent) VALUES(41,4,3,75),(42,4,2,50)");
                raw.execSQL("INSERT INTO UserWordProgress(vocabularyId,box,dueAt,learnedAt,lastReviewedAt,consecutiveMistakes,isLeech,reviewCount,isSaved) VALUES('legacy',2,100,1,2,0,0,3,1)");
                raw.setVersion(1);
            }
            UserDatabase db=UserDatabase.open(context,name);
            try {
                assertEquals(2,db.userDao().quizzes().size());
                for (QuizAttempt legacy : db.userDao().quizzes()) assertNull(legacy.completionKey);
                assertEquals(3,db.userDao().progress("legacy").reviewCount);
                QuizAttempt row=new QuizAttempt(); row.completionKey="session-key";
                row.completedAt=43; row.questionCount=4; row.correctCount=3; row.scorePercent=75;
                long id=db.userDao().insertQuizOnce(row);
                assertEquals(id,db.userDao().insertQuizOnce(row));
                row.completedAt++;
                assertThrows(UserDao.CompletionConflict.class,() -> db.userDao().insertQuizOnce(row));
                assertEquals(3,db.userDao().quizzes().size());
            } finally { db.close(); }
        } finally { context.deleteDatabase(name); }
    }
}
