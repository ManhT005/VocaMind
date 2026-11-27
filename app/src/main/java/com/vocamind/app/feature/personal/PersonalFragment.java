package com.vocamind.app.feature.personal;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.format.DateFormat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.core.QuizViewModelFactory;
import com.vocamind.app.feature.quiz.QuizViewModel;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vocamind.app.R;
import com.vocamind.app.feature.account.AccountViewModel;
import com.vocamind.app.core.PersonalData;
import com.vocamind.app.feature.packs.PacksFragment;
import com.vocamind.app.feature.vocabulary.VocabularyDetailFragment;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PersonalFragment extends Fragment {
    private static final int[] THEME_LABELS = {
            R.string.personal_system, R.string.personal_light, R.string.personal_dark};
    private static final int[] THEMES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (isAdded() && getView() != null) render();
            });
    private LinearLayout content;
    private int generation;
    private boolean resetting;

    public PersonalFragment() {
        super(R.layout.fragment_personal);
    }

    @Override public void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (getView() == null) return;
        content = requireView().findViewById(R.id.personal_content);
        content.removeAllViews();
        generation++;
        var nav = NavHostFragment.findNavController(this);
        var current = nav.getCurrentDestination();
        if (current == null) return;
        int destination = current.getId();
        requireView().findViewById(R.id.personal_back_button).setOnClickListener(v -> nav.popBackStack());
        TextView title = requireView().findViewById(R.id.personal_title);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        if (destination == R.id.settingsFragment) title.setText(R.string.personal_settings);
        else if (destination == R.id.savedWordsFragment) title.setText(R.string.personal_savedWords);
        else if (destination == R.id.activePacksFragment) title.setText(R.string.personal_activePacks);
        else title.setText(R.string.personal_profile);
        if (destination == R.id.settingsFragment) showSettings();
        else if (destination == R.id.savedWordsFragment) showSavedWords();
        else if (destination == R.id.activePacksFragment) showActivePacks();
        else showProfile();
    }

    private void showProfile() {
        String name = PersonalPreferences.open(requireContext()).getString("display_name", "");
        String displayName = name.isEmpty() ? getString(R.string.personal_guest) : name;
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView avatar = new TextView(requireContext());
        avatar.setTextAppearance(R.style.TextAppearance_VocaMind_Section);
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.ui_icon_tile);
        avatar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.detail_card_surface)));
        avatar.setMinimumWidth(dp(48));
        avatar.setMinimumHeight(dp(48));
        avatar.setText(displayName.substring(0, displayName.offsetByCodePoints(0, 1))
                .toUpperCase(getResources().getConfiguration().getLocales().get(0)));
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(avatar, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout details = new LinearLayout(requireContext());
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(dp(12));
        header.addView(details, params);
        TextView identity = new TextView(requireContext());
        identity.setTextAppearance(R.style.TextAppearance_VocaMind_Section);
        identity.setText(displayName);
        details.addView(identity);
        TextView subtitle = new TextView(requireContext());
        subtitle.setTextAppearance(R.style.TextAppearance_VocaMind_Caption);
        subtitle.setText(R.string.personal_local_profile);
        details.addView(subtitle);
        LinearLayout profile = new LinearLayout(requireContext());
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setBackgroundResource(R.drawable.ui_hero);
        profile.setPadding(dp(16), dp(16), dp(16), dp(12));
        profile.addView(header, new LinearLayout.LayoutParams(-1, -2));
        button(profile, getString(R.string.personal_edit_name), () -> editName(name));
        add(profile);
        iconLink(R.string.personal_savedWords, R.id.savedWordsFragment, R.drawable.ic_detail_bookmark);
        iconLink(R.string.personal_activePacks, R.id.activePacksFragment, R.drawable.ic_ui_words);
        if (AccountViewModel.isAvailable()) {
            iconLink(R.string.account_title, R.id.accountFragment, R.drawable.ic_ui_profile);
        }
        iconLink(R.string.personal_settings, R.id.settingsFragment, R.drawable.ic_ui_settings);
    }

    private void editName(String name) {
        TextInputLayout field = new TextInputLayout(requireContext());
        field.setHint(getString(R.string.personal_edit_name));
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        input.setText(name);
        field.addView(input, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout container = new FrameLayout(requireContext());
        container.setPadding(dp(24), dp(12), dp(24), 0);
        container.addView(field, new FrameLayout.LayoutParams(-1, -2));
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.personal_edit_name)
                .setView(container).setNegativeButton(R.string.personal_cancel, null)
                .setPositiveButton(R.string.personal_save, (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    PersonalPreferences.open(requireContext()).edit().putString("display_name", value).apply();
                    render();
                }).show();
    }

    private void showSettings() {
        LinearLayout appearance = section(R.string.personal_theme, R.drawable.ic_ui_settings);
        int selected = 0;
        for (int i = 0; i < THEMES.length; i++) {
            if (THEMES[i] == PersonalPreferences.nightMode(requireContext())) selected = i;
        }
        final int current = selected;
        button(appearance, getString(THEME_LABELS[current]), () -> {
            String[] labels = new String[THEME_LABELS.length];
            for (int i = 0; i < labels.length; i++) labels[i] = getString(THEME_LABELS[i]);
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.personal_theme)
                    .setSingleChoiceItems(labels, current, (dialog, index) -> {
                        dialog.dismiss();
                        PersonalPreferences.open(requireContext()).edit().putInt("night_mode", THEMES[index]).apply();
                        ((AppCompatActivity) requireActivity()).getDelegate().setLocalNightMode(THEMES[index]);
                        render();
                    }).setNegativeButton(R.string.personal_cancel, null).show();
        });

        LinearLayout learning = section(R.string.ui_settings_learning, R.drawable.ic_ui_learn);
        text(learning, getString(R.string.personal_daily_goal), R.style.TextAppearance_VocaMind_DetailLabel);
        MaterialButton goal = button(learning, getString(R.string.personal_loading), this::render);
        goal.setEnabled(false);
        Context app = requireContext().getApplicationContext();
        read(() -> PersonalData.dailyGoal(app), value -> configureGoal(goal, value), () -> {
            goal.setEnabled(true);
            goal.setText(R.string.personal_retry);
        });

        showReminderSettings(section(R.string.personal_notifications, R.drawable.ic_ui_calendar));

        LinearLayout about = section(R.string.personal_about, R.drawable.ic_ui_profile);
        text(about, getString(R.string.personal_version, getString(R.string.app_name), versionName()),
                R.style.TextAppearance_VocaMind_Body);
        button(about, getString(R.string.attribution),
                () -> NavHostFragment.findNavController(this).navigate(R.id.attributionFragment));
        showResetSettings(section(R.string.ui_settings_data, R.drawable.ic_ui_words));
    }

    private void showReminderSettings(LinearLayout section) {
        Context context = requireContext();
        boolean enabled = StudyReminder.enabled(context);
        LocalTime time = StudyReminder.time(context);
        var toggle = new com.google.android.material.materialswitch.MaterialSwitch(context);
        toggle.setText(R.string.reminder_title);
        toggle.setMinHeight(dp(48));
        toggle.setChecked(enabled);
        add(section, toggle);
        toggle.setOnCheckedChangeListener((view, on) -> {
            StudyReminder.configure(context, on, StudyReminder.time(context));
            render();
            if (on && !StudyReminder.hasPermission(context)) requestReminderPermission();
        });

        String pattern = DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a";
        String label = time.format(DateTimeFormatter.ofPattern(pattern,
                getResources().getConfiguration().getLocales().get(0)));
        button(section, getString(R.string.reminder_hour, label), () -> {
            TimePickerDialog dialog = new TimePickerDialog(context, (picker, hour, minute) -> {
                StudyReminder.configure(context, StudyReminder.enabled(context), LocalTime.of(hour, minute));
                render();
            }, time.getHour(), time.getMinute(), DateFormat.is24HourFormat(context));
            dialog.setTitle(R.string.reminder_pick_hour);
            dialog.show();
        });
        if (enabled && !StudyReminder.canNotify(context)) {
            body(section, R.string.reminder_blocked);
            button(section, getString(R.string.reminder_open_system), () -> startActivity(
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())));
        }
    }

    private void requestReminderPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.reminder_permission_reason)
                    .setPositiveButton(R.string.reminder_continue, (dialog, which) ->
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton(R.string.personal_cancel, null).show();
        } else {
            var prefs = PersonalPreferences.open(requireContext());
            if (!prefs.getBoolean("reminder_permission_requested", false)) {
                prefs.edit().putBoolean("reminder_permission_requested", true).apply();
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void showResetSettings(LinearLayout section) {
        MaterialButton reset = button(section, getString(R.string.reset_title), () ->
                new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.reset_title)
                        .setMessage(R.string.reset_confirm_description)
                        .setNegativeButton(R.string.personal_cancel, null)
                        .setPositiveButton(R.string.reset_confirm, (dialog, which) -> resetResults())
                        .show());
        reset.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_error_surface));
    }

    private void resetResults() {
        if (resetting) return;
        resetting = true;
        Context app = requireContext().getApplicationContext();
        var quiz = new ViewModelProvider(requireActivity(), new QuizViewModelFactory(app, requireActivity()))
                .get(QuizViewModel.class);
        quiz.resetLearning(() -> PersonalData.resetLearningResults(app), success -> {
            resetting = false;
            Toast.makeText(app, success ? R.string.reset_done : R.string.personal_save_error,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void configureGoal(MaterialButton button, int value) {
        button.setEnabled(true);
        button.setText(getString(R.string.personal_words_day, value));
        button.setOnClickListener(v -> {
            int[] targets = {5, 10, 15, 20};
            String[] titles = new String[targets.length];
            int checked = -1;
            for (int i = 0; i < targets.length; i++) {
                titles[i] = getString(R.string.personal_words_day, targets[i]);
                if (value == targets[i]) checked = i;
            }
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.personal_daily_goal)
                    .setSingleChoiceItems(titles, checked, (dialog, index) -> {
                        dialog.dismiss();
                        button.setEnabled(false);
                        Context app = requireContext().getApplicationContext();
                        read(() -> PersonalData.saveDailyGoal(app, targets[index]),
                                saved -> configureGoal(button, saved), () -> {
                                    button.setEnabled(true);
                                    Toast.makeText(requireContext(), R.string.personal_save_error, Toast.LENGTH_SHORT).show();
                                });
                    }).setNegativeButton(R.string.personal_cancel, null).show();
        });
    }

    private void showSavedWords() {
        TextView status = body(R.string.personal_loading);
        Context app = requireContext().getApplicationContext();
        read(() -> PersonalData.savedWords(app), words -> {
            if (words.isEmpty()) status.setText(R.string.personal_empty_saved);
            else content.removeView(status);
            for (var word : words) {
                button(getString(R.string.personal_word_row, word.lemma, word.meaningVi),
                        () -> NavHostFragment.findNavController(this).navigate(R.id.vocabularyDetailFragment,
                                VocabularyDetailFragment.searchArguments(word.stableId)));
            }
        }, () -> showReadError(status));
    }

    private void showActivePacks() {
        TextView status = body(R.string.personal_loading);
        Context app = requireContext().getApplicationContext();
        read(() -> PersonalData.activePacks(app), packs -> {
            if (packs.isEmpty()) status.setText(R.string.personal_empty_packs);
            else content.removeView(status);
            for (var state : packs) {
                var pack = state.getPack();
                button(pack.title, () -> {
                    Bundle args = new Bundle();
                    args.putString(PacksFragment.ARG_PACK_ID, pack.id);
                    args.putString(PacksFragment.ARG_PACK_TITLE, pack.title);
                    NavHostFragment.findNavController(this).navigate(R.id.packDetailFragment, args);
                });
            }
            link(R.string.packs, R.id.packsFragment);
        }, () -> showReadError(status));
    }

    private void showReadError(TextView status) {
        status.setText(R.string.personal_read_error);
        button(getString(R.string.personal_retry), this::render);
    }

    private <T> void read(Supplier<T> operation, Consumer<T> success, Runnable failure) {
        int request = generation;
        worker.execute(() -> {
            try {
                T result = operation.get();
                main.post(() -> { if (content != null && request == generation) success.accept(result); });
            } catch (RuntimeException error) {
                main.post(() -> { if (content != null && request == generation) failure.run(); });
            }
        });
    }

    private String versionName() {
        Context context = requireContext();
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "—";
        }
    }

    private LinearLayout section(@StringRes int label, int icon) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(16));
        card.setBackgroundResource(R.drawable.ui_row);
        TextView title = text(card, getString(label), R.style.TextAppearance_VocaMind_Section);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        title.setCompoundDrawablePadding(dp(10));
        title.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.shell_accent)));
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        add(card);
        return card;
    }

    private TextView body(@StringRes int label) {
        return body(content, label);
    }

    private TextView body(LinearLayout parent, @StringRes int label) {
        return text(parent, getString(label), R.style.TextAppearance_VocaMind_Body);
    }

    private TextView text(LinearLayout parent, String label, int appearance) {
        TextView text = new TextView(requireContext());
        text.setTextAppearance(appearance);
        text.setText(label);
        add(parent, text);
        return text;
    }

    private void link(@StringRes int label, int destination) {
        button(getString(label), () -> NavHostFragment.findNavController(this).navigate(destination));
    }

    private void iconLink(@StringRes int label, int destination, int icon) {
        MaterialButton row = button(getString(label), () -> NavHostFragment.findNavController(this).navigate(destination));
        row.setIconResource(icon);
        row.setIconSize(getResources().getDimensionPixelSize(R.dimen.ui_action_icon_size));
    }

    private MaterialButton button(String label, Runnable action) {
        return button(content, label, action);
    }

    private MaterialButton button(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.item_personal_action, parent, false);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        add(parent, button);
        return button;
    }

    private void add(View view) {
        add(content, view);
    }

    private void add(LinearLayout parent, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onDestroyView() {
        content = null;
        generation++;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
