package com.vocamind.app.domain.quiz;

import java.util.*;

/** Immutable transitions: caller replaces its snapshot with Transition.getSession(). */
public final class QuizSession {
    public enum State { IN_PROGRESS, COMPLETED, ABANDONED }
    public enum Outcome { ACCEPTED, INVALID_QUESTION, INVALID_OPTION, ALREADY_ANSWERED, ANSWER_REQUIRED, TERMINAL }
    public static final class Transition {
        private final QuizSession session;
        private final Outcome outcome;
        private Transition(QuizSession session, Outcome outcome) { this.session = session; this.outcome = outcome; }
        public QuizSession getSession() { return session; }
        public Outcome getOutcome() { return outcome; }
    }
    private final List<QuizQuestion> questions;
    private final Map<String, String> answers;
    private final int position;
    private final State state;
    private final QuizResult result;
    public QuizSession(Collection<QuizQuestion> input) {
        if (input == null || input.size() < 4 || input.size() > 10) throw new IllegalArgumentException("4..10 questions required");
        Set<String> ids = new HashSet<>();
        List<QuizQuestion> copy = new ArrayList<>(input);
        for (QuizQuestion q : copy) if (q == null || !ids.add(q.getId())) throw new IllegalArgumentException("unique vocabulary required");
        questions = Collections.unmodifiableList(copy);
        answers = Collections.emptyMap(); position = 0; state = State.IN_PROGRESS; result = null;
    }
    private QuizSession(QuizSession old, Map<String, String> answers, int position, State state) {
        this.questions = old.questions;
        this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
        this.position = position; this.state = state;
        int correct = 0;
        for (QuizQuestion q : questions) if (q.getCorrectOptionId().equals(answers.get(q.getId()))) correct++;
        result = state == State.COMPLETED ? new QuizResult(questions.size(), correct) : null;
    }
    public Transition answer(String questionId, String optionId) {
        if (state != State.IN_PROGRESS) return new Transition(this, Outcome.TERMINAL);
        QuizQuestion q = getCurrentQuestion();
        if (!q.getId().equals(questionId)) return new Transition(this, Outcome.INVALID_QUESTION);
        if (answers.containsKey(questionId)) return new Transition(this, Outcome.ALREADY_ANSWERED);
        if (!q.hasOption(optionId)) return new Transition(this, Outcome.INVALID_OPTION);
        Map<String, String> updated = new LinkedHashMap<>(answers); updated.put(questionId, optionId);
        return new Transition(new QuizSession(this, updated, position,
                position == questions.size() - 1 ? State.COMPLETED : State.IN_PROGRESS), Outcome.ACCEPTED);
    }
    public Transition next() {
        if (state != State.IN_PROGRESS) return new Transition(this, Outcome.TERMINAL);
        if (!answers.containsKey(getCurrentQuestion().getId())) return new Transition(this, Outcome.ANSWER_REQUIRED);
        return new Transition(new QuizSession(this, answers, position + 1, state), Outcome.ACCEPTED);
    }
    public Transition abandon() {
        return state != State.IN_PROGRESS ? new Transition(this, Outcome.TERMINAL)
                : new Transition(new QuizSession(this, answers, position, State.ABANDONED), Outcome.ACCEPTED);
    }
    public List<QuizQuestion> getQuestions() { return questions; }
    public Map<String, String> getAnswers() { return answers; }
    public QuizQuestion getCurrentQuestion() { return questions.get(position); }
    public int getPosition() { return position; }
    public State getState() { return state; }
    public Optional<QuizResult> getResult() { return Optional.ofNullable(result); }
}
