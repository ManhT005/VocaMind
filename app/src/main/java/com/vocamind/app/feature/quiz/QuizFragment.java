package com.vocamind.app.feature.quiz;

import android.os.Bundle;
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
        model.state().observe(getViewLifecycleOwner(),s -> render(view,s,ids));
    }
    protected ViewModelProvider.Factory viewModelFactory() {
        return new QuizViewModelFactory(requireContext(),requireActivity());
    }
    private void render(View view, QuizViewModel.State s, int[] ids) {
        boolean question=s.session != null;
        boolean terminal=question && s.session.getState() == QuizSession.State.COMPLETED;
        view.findViewById(R.id.quiz_loading).setVisibility(s.phase == QuizViewModel.Phase.LOADING || s.phase == QuizViewModel.Phase.SAVING ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.quiz_question).setVisibility(question ? View.VISIBLE : View.GONE);
        TextView status=view.findViewById(R.id.quiz_status);
        Button action=view.findViewById(R.id.quiz_action);
        action.setVisibility(s.phase == QuizViewModel.Phase.EMPTY || s.phase == QuizViewModel.Phase.ERROR
                || s.phase == QuizViewModel.Phase.SAVE_ERROR || s.phase == QuizViewModel.Phase.RESULT ? View.VISIBLE : View.GONE);
        action.setText(s.phase == QuizViewModel.Phase.RESULT ? R.string.quiz_restart : R.string.quiz_retry);
        switch (s.phase) {
            case LOADING: status.setText(R.string.quiz_generating); break;
            case EMPTY: status.setText(R.string.quiz_empty); break;
            case ERROR: status.setText(R.string.quiz_error); break;
            default:
                if (terminal) {
                    QuizResult r=s.session.getResult().get();
                    String persistence=getString(s.phase == QuizViewModel.Phase.RESULT ? R.string.quiz_saved
                            : s.phase == QuizViewModel.Phase.SAVING ? R.string.quiz_saving : R.string.quiz_save_error);
                    status.setText(getString(R.string.quiz_result_status,
                            getString(R.string.quiz_result,r.getQuestionCount(),r.getCorrectCount(),r.getWrongCount(),r.getScorePercent()),
                            persistence));
                } else status.setText(getString(R.string.quiz_progress,s.session.getPosition()+1,s.session.getQuestions().size()));
        }
        if (!question) return;
        QuizQuestion q=s.session.getCurrentQuestion();
        QuizVocabulary vocabulary=q.getVocabulary();
        ((TextView)view.findViewById(R.id.quiz_prompt)).setText(vocabulary.getLemma());
        ((TextView)view.findViewById(R.id.quiz_context)).setText(getString(R.string.quiz_context_format,
                vocabulary.getPos(),vocabulary.getContext()));
        String selected=s.session.getAnswers().get(q.getId());
        String correct="";
        for (int i=0;i<4;i++) {
            QuizOption o=q.getOptions().get(i);
            Button button=view.findViewById(ids[i]);
            button.setText(o.getMeaning()); button.setEnabled(s.phase == QuizViewModel.Phase.ANSWERING);
            if (q.getCorrectOptionId().equals(o.getId())) correct=o.getMeaning();
        }
        ((TextView)view.findViewById(R.id.quiz_feedback)).setText(selected == null ? ""
                : getString(selected.equals(q.getCorrectOptionId()) ? R.string.quiz_correct : R.string.quiz_wrong,correct));
        Button next=view.findViewById(R.id.quiz_next);
        next.setVisibility(terminal ? View.GONE : View.VISIBLE);
        next.setEnabled(s.phase == QuizViewModel.Phase.ANSWERED);
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
        super.onDestroyView();
    }
}
