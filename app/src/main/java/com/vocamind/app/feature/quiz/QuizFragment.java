package com.vocamind.app.feature.quiz;

import android.os.Bundle;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import android.view.View;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vocamind.app.R;
import com.vocamind.app.core.QuizViewModelFactory;
import com.vocamind.app.domain.quiz.*;

public class QuizFragment extends Fragment {
    private QuizViewModel model;
    private androidx.appcompat.app.AlertDialog reviewDialog;
    androidx.appcompat.app.AlertDialog exitDialog;
    public QuizFragment() { super(R.layout.fragment_quiz); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model=new ViewModelProvider(requireActivity(),viewModelFactory()).get(QuizViewModel.class);
        model.onOpened();
        view.findViewById(R.id.quiz_back).setOnClickListener(v -> back());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        int[] ids={R.id.quiz_option_1,R.id.quiz_option_2,R.id.quiz_option_3,R.id.quiz_option_4};
        for (int i=0;i<ids.length;i++) {
            final int index=i;
            view.findViewById(ids[i]).setOnClickListener(v -> {
                QuizViewModel.State s=model.state().getValue();
                if (s == null || s.phase != QuizViewModel.Phase.ANSWERING) return;
                QuizQuestion q=s.session.getCurrentQuestion();
                model.answer(q.getId(),q.getOptions().get(index).getId());
            });
        }
        view.findViewById(R.id.quiz_next).setOnClickListener(v -> model.next());
        view.findViewById(R.id.quiz_action).setOnClickListener(v -> {
            if (model.state().getValue().phase == QuizViewModel.Phase.SAVE_ERROR) model.retrySave();
            else model.start();
        });
        view.findViewById(R.id.quiz_review).setOnClickListener(v -> {
            QuizViewModel.State state = model.state().getValue();
            if (state != null && state.phase == QuizViewModel.Phase.RESULT) {
                showWrongAnswers(state.session);
            }
        });
        model.state().observe(getViewLifecycleOwner(),s -> render(view,s,ids));
    }
    protected ViewModelProvider.Factory viewModelFactory() {
        return new QuizViewModelFactory(requireContext(),requireActivity());
    }
    private void render(View view, QuizViewModel.State s, int[] ids) {
        boolean question=s.session != null;
        boolean terminal=question && s.session.getState() == QuizSession.State.COMPLETED;
        view.findViewById(R.id.quiz_loading).setVisibility(s.phase == QuizViewModel.Phase.LOADING || s.phase == QuizViewModel.Phase.SAVING ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.quiz_question).setVisibility(question && !terminal ? View.VISIBLE : View.GONE);
        TextView status=view.findViewById(R.id.quiz_status);
        status.setVisibility(question && !terminal ? View.GONE : View.VISIBLE);
        TextView progressLabel = view.findViewById(R.id.quiz_progress_label);
        progressLabel.setVisibility(question && !terminal ? View.VISIBLE : View.GONE);
        Button action=view.findViewById(R.id.quiz_action);
        action.setVisibility(s.phase == QuizViewModel.Phase.EMPTY || s.phase == QuizViewModel.Phase.ERROR
                || s.phase == QuizViewModel.Phase.SAVE_ERROR || s.phase == QuizViewModel.Phase.RESULT ? View.VISIBLE : View.GONE);
        action.setText(s.phase == QuizViewModel.Phase.RESULT ? R.string.quiz_restart : R.string.quiz_retry);
        boolean canReview = s.phase == QuizViewModel.Phase.RESULT && terminal
                && s.session.getResult().get().getWrongCount() > 0;
        view.findViewById(R.id.quiz_review).setVisibility(canReview ? View.VISIBLE : View.GONE);
        TextView score = view.findViewById(R.id.quiz_score);
        score.setVisibility(terminal ? View.VISIBLE : View.GONE);
        if (s.phase == QuizViewModel.Phase.SAVE_ERROR) action.setText(R.string.ui_retry_save);
        switch (s.phase) {
            case LOADING: status.setText(R.string.quiz_generating); break;
            case EMPTY: status.setText(R.string.quiz_empty); break;
            case ERROR: status.setText(R.string.quiz_error); break;
            default:
                if (terminal) {
                    QuizResult r=s.session.getResult().get();
                    score.setText(getString(R.string.ui_quiz_score, r.getScorePercent()));
                    String persistence=getString(s.phase == QuizViewModel.Phase.RESULT ? R.string.quiz_saved
                            : s.phase == QuizViewModel.Phase.SAVING ? R.string.quiz_saving : R.string.quiz_save_error);
                    status.setText(getString(R.string.quiz_result_status,
                            getString(R.string.quiz_result, r.getQuestionCount(), r.getCorrectCount(), r.getWrongCount()),
                            persistence));
                } else progressLabel.setText(getString(R.string.quiz_progress,s.session.getPosition()+1,s.session.getQuestions().size()));
        }
        if (!question || terminal) return;
        LinearProgressIndicator progress = view.findViewById(R.id.quiz_progress_bar);
        progress.setMax(s.session.getQuestions().size());
        progress.setProgress(s.session.getPosition() + 1);
        QuizQuestion q=s.session.getCurrentQuestion();
        QuizVocabulary vocabulary=q.getVocabulary();
        ((TextView)view.findViewById(R.id.quiz_prompt)).setText(vocabulary.getLemma());
        ((TextView)view.findViewById(R.id.quiz_pos)).setText(vocabulary.getPos());
        ((TextView)view.findViewById(R.id.quiz_context)).setText(vocabulary.getContext());
        String selected=s.session.getAnswers().get(q.getId());
        for (int i=0;i<4;i++) {
            QuizOption o=q.getOptions().get(i);
            MaterialButton button = view.findViewById(ids[i]);
            boolean isCorrect = q.getCorrectOptionId().equals(o.getId());
            boolean showCorrect = selected != null && isCorrect;
            boolean showWrong = !isCorrect && o.getId().equals(selected);
            int surface = showCorrect ? R.color.success : showWrong ? R.color.error_surface : R.color.detail_card_surface;
            int ink = showCorrect ? R.color.on_success : showWrong ? R.color.on_error_surface : R.color.shell_text_primary;
            button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), surface)));
            button.setTextColor(ContextCompat.getColor(requireContext(), ink));
            String optionLabel = getString(R.string.ui_quiz_lettered_option, (char) ('A' + i), o.getMeaning());
            button.setText(showCorrect ? getString(R.string.ui_answer_correct, optionLabel)
                    : showWrong ? getString(R.string.ui_answer_selected, optionLabel) : optionLabel);
            button.setIconResource(showCorrect ? R.drawable.ic_ui_check : showWrong ? R.drawable.ic_ui_close : 0);
            button.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), ink)));
            button.setEnabled(s.phase == QuizViewModel.Phase.ANSWERING);
        }
        Button next=view.findViewById(R.id.quiz_next);
        next.setEnabled(s.phase == QuizViewModel.Phase.ANSWERED);
    }
    private void showWrongAnswers(QuizSession session) {
        if (reviewDialog != null && reviewDialog.isShowing()) return;
        List<String> rows = new ArrayList<>();
        List<QuizQuestion> questions = session.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion question = questions.get(i);
            String chosenId = session.getAnswers().get(question.getId());
            if (chosenId == null || question.getCorrectOptionId().equals(chosenId)) continue;

            QuizVocabulary word = question.getVocabulary();
            for (QuizOption option : question.getOptions()) {
                if (!option.getId().equals(chosenId)) continue;
                String row = getString(R.string.ui_quiz_review_row, i + 1, word.getLemma(),
                        word.getPos(), option.getMeaning(), word.getMeaning());
                if (!word.getContext().trim().isEmpty()) {
                    row += "\n" + getString(R.string.ui_quiz_review_example, word.getContext());
                }
                rows.add(row);
                break;
            }
        }
        if (rows.isEmpty()) return;
        reviewDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ui_quiz_review)
                .setMessage(TextUtils.join("\n\n", rows))
                .setPositiveButton(R.string.ui_quiz_review_close, null)
                .show();
    }

    private void back() {
        if (!model.isInProgress()) { leave(); return; }
        if (exitDialog != null && exitDialog.isShowing()) return;
        exitDialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.quiz_exit_title)
                .setMessage(R.string.quiz_exit_message).setNegativeButton(R.string.quiz_continue,(d,w) -> {})
                .setPositiveButton(R.string.quiz_exit,(d,w) -> { model.abandon(); leave(); }).show();
    }
    private void leave() { NavHostFragment.findNavController(this).popBackStack(); }
    @Override public void onDestroyView() {
        if (exitDialog != null) { exitDialog.dismiss(); exitDialog=null; }
        if (reviewDialog != null) { reviewDialog.dismiss(); reviewDialog = null; }
        super.onDestroyView();
    }
}
