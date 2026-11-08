package com.vocamind.app.feature.quiz;

import android.os.Bundle;
import com.vocamind.app.domain.quiz.*;
import java.util.ArrayList;

/** Android-only snapshot; reconstruction replays validated domain transitions. */
final class QuizSnapshotMapper {
    static Bundle encode(QuizSession session) {
        Bundle out = new Bundle();
        ArrayList<Bundle> questions = new ArrayList<>();
        for (QuizQuestion q : session.getQuestions()) {
            Bundle row = new Bundle();
            QuizVocabulary v = q.getVocabulary();
            row.putStringArray("v", new String[]{v.getStableId(), v.getLemma(), v.getPos(), v.getMeaning(), v.getContext()});
            row.putString("correct", q.getCorrectOptionId());
            ArrayList<Bundle> options = new ArrayList<>();
            for (QuizOption o : q.getOptions()) {
                Bundle option = new Bundle();
                option.putStringArray("o", new String[]{o.getId(), o.getMeaning(), o.getMeaningKey()});
                options.add(option);
            }
            row.putParcelableArrayList("options", options);
            questions.add(row);
        }
        out.putParcelableArrayList("questions", questions);
        out.putInt("position", session.getPosition());
        Bundle answers = new Bundle();
        session.getAnswers().forEach(answers::putString);
        out.putBundle("answers", answers);
        return out;
    }
    static QuizSession decode(Bundle in) {
        ArrayList<Bundle> rows = in.getParcelableArrayList("questions");
        ArrayList<QuizQuestion> questions = new ArrayList<>();
        for (Bundle row : rows) {
            String[] v = row.getStringArray("v");
            ArrayList<QuizOption> options = new ArrayList<>();
            ArrayList<Bundle> stored = row.getParcelableArrayList("options");
            for (Bundle b : stored) {
                String[] o = b.getStringArray("o");
                options.add(new QuizOption(o[0], o[1], o[2]));
            }
            questions.add(new QuizQuestion(new QuizVocabulary(v[0],v[1],v[2],v[3],v[4]), options,
                    row.getString("correct"), new UnicodeMeaningNormalizer()));
        }
        QuizSession session = new QuizSession(questions);
        int position = in.getInt("position", -1);
        if (position < 0 || position >= questions.size()) throw new IllegalArgumentException("position");
        Bundle answers = in.getBundle("answers");
        int consumed = 0;
        for (int i = 0; i <= position; i++) {
            String id = questions.get(i).getId();
            if (answers.containsKey(id)) {
                QuizSession.Transition t = session.answer(id, answers.getString(id));
                if (t.getOutcome() != QuizSession.Outcome.ACCEPTED) throw new IllegalArgumentException("answer");
                session = t.getSession(); consumed++;
            }
            if (i < position) {
                QuizSession.Transition t = session.next();
                if (t.getOutcome() != QuizSession.Outcome.ACCEPTED) throw new IllegalArgumentException("next");
                session = t.getSession();
            }
        }
        if (consumed != answers.size()) throw new IllegalArgumentException("extra answers");
        return session;
    }
}
