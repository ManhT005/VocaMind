package com.vocamind.app.feature.account;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vocamind.app.R;

public final class AccountFragment extends Fragment {
    private enum Form { SIGN_IN, REGISTER, RESET, PASSWORD, NAME }
    private AccountViewModel model;
    private LinearLayout content;
    private AlertDialog dialog;

    public AccountFragment() {
        super(R.layout.fragment_personal);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        content = view.findViewById(R.id.personal_content);
        view.findViewById(R.id.personal_back_button).setOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        TextView title = view.findViewById(R.id.personal_title);
        title.setText(R.string.account_title);
        ViewCompat.setAccessibilityHeading(title, true);
        model = new ViewModelProvider(this).get(AccountViewModel.class);
        model.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(AccountViewModel.State state) {
        content.removeAllViews();
        if (!state.available) {
            addText(getString(R.string.account_unavailable), R.style.TextAppearance_VocaMind_Body);
            return;
        }
        addText(getString(R.string.account_local_note), R.style.TextAppearance_VocaMind_Body);
        if (state.busy || state.message != 0) {
            TextView status = addText(getString(state.busy ? R.string.account_busy : state.message),
                    R.style.TextAppearance_VocaMind_Body);
            ViewCompat.setAccessibilityLiveRegion(status, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
        boolean enabled = !state.busy;
        if (!state.signedIn) {
            button(R.string.account_sign_in, () -> showForm(Form.SIGN_IN, state), enabled);
            button(R.string.account_register, () -> showForm(Form.REGISTER, state), enabled);
            button(R.string.account_forgot, () -> showForm(Form.RESET, state), enabled);
            return;
        }
        if (state.name != null && !state.name.isEmpty()) {
            addText(state.name, R.style.TextAppearance_VocaMind_Section);
        }
        if (state.email != null) addText(state.email, R.style.TextAppearance_VocaMind_Body);
        addText(getString(state.verified ? R.string.account_verified : R.string.account_unverified),
                R.style.TextAppearance_VocaMind_Body);
        if (!state.verified) {
            button(R.string.account_send_verification, model::sendVerification, enabled);
            button(R.string.account_check_verification, model::checkVerification, enabled);
        }
        button(R.string.personal_edit_name, () -> showForm(Form.NAME, state), enabled);
        if (state.email != null) {
            button(R.string.account_change_password, () -> showForm(Form.PASSWORD, state), enabled);
        }
        button(R.string.account_sign_out, model::signOut, enabled);
    }

    private void showForm(Form form, AccountViewModel.State state) {
        LinearLayout fields = new LinearLayout(requireContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(24), dp(8), dp(24), dp(8));
        Field email = form == Form.SIGN_IN || form == Form.REGISTER || form == Form.RESET
                ? new Field(fields, R.string.account_email, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) : null;
        Field current = form == Form.PASSWORD
                ? new Field(fields, R.string.account_current_password, InputType.TYPE_TEXT_VARIATION_PASSWORD) : null;
        Field password = form == Form.SIGN_IN || form == Form.REGISTER || form == Form.PASSWORD
                ? new Field(fields, form == Form.PASSWORD ? R.string.account_new_password : R.string.account_password,
                        InputType.TYPE_TEXT_VARIATION_PASSWORD) : null;
        Field confirmation = form == Form.REGISTER || form == Form.PASSWORD
                ? new Field(fields, R.string.account_confirm_password, InputType.TYPE_TEXT_VARIATION_PASSWORD) : null;
        Field name = form == Form.NAME ? new Field(fields, R.string.account_name,
                InputType.TYPE_TEXT_FLAG_CAP_WORDS) : null;
        if (name != null) {
            name.edit.setText(state.name);
            name.edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        scroll.addView(fields);
        int title = switch (form) {
            case SIGN_IN -> R.string.account_sign_in;
            case REGISTER -> R.string.account_register;
            case RESET -> R.string.account_forgot;
            case PASSWORD -> R.string.account_change_password;
            case NAME -> R.string.personal_edit_name;
        };
        int confirm = form == Form.RESET ? R.string.account_send_reset
                : form == Form.NAME ? R.string.personal_save : title;
        AlertDialog formDialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(title)
                .setView(scroll).setNegativeButton(R.string.personal_cancel, null)
                .setPositiveButton(confirm, null).create();
        dialog = formDialog;
        formDialog.setOnDismissListener(ignored -> {
            if (dialog == formDialog) dialog = null;
        });
        formDialog.show();
        if (formDialog.getWindow() != null) {
            formDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        formDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String address = email == null ? "" : email.value().trim();
            String secret = password == null ? "" : password.value();
            String old = current == null ? "" : current.value();
            String displayName = name == null ? "" : name.value().trim();
            if (email != null && !Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                email.error(R.string.account_invalid_email);
                return;
            }
            if (current != null && old.isEmpty()) {
                current.error(R.string.account_invalid_password);
                return;
            }
            if (password != null && secret.isEmpty()) {
                password.error(R.string.account_invalid_password);
                return;
            }
            if (confirmation != null && secret.length() < 8) {
                password.error(R.string.account_short_password);
                return;
            }
            if (confirmation != null && !secret.equals(confirmation.value())) {
                confirmation.error(R.string.account_password_mismatch);
                return;
            }
            if (name != null && displayName.isEmpty()) {
                name.error(R.string.account_name_required);
                return;
            }
            formDialog.dismiss();
            switch (form) {
                case SIGN_IN -> model.signIn(address, secret);
                case REGISTER -> model.register(address, secret);
                case RESET -> model.resetPassword(address);
                case PASSWORD -> model.changePassword(old, secret);
                case NAME -> model.updateName(displayName);
            }
        });
    }

    private TextView addText(String value, int appearance) {
        TextView label = new TextView(requireContext());
        label.setTextAppearance(appearance);
        label.setText(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        content.addView(label, params);
        return label;
    }

    private void button(@StringRes int text, Runnable action, boolean enabled) {
        MaterialButton button = (MaterialButton) getLayoutInflater().inflate(
                R.layout.item_personal_action, content, false);
        button.setText(text);
        button.setEnabled(enabled);
        button.setOnClickListener(v -> action.run());
        content.addView(button);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onDestroyView() {
        if (dialog != null) dialog.dismiss();
        content = null;
        super.onDestroyView();
    }

    private final class Field {
        final TextInputLayout layout;
        final TextInputEditText edit;

        Field(LinearLayout parent, @StringRes int hint, int variation) {
            layout = new TextInputLayout(requireContext());
            layout.setHint(getString(hint));
            edit = new TextInputEditText(layout.getContext());
            edit.setSingleLine();
            edit.setInputType(InputType.TYPE_CLASS_TEXT | variation);
            edit.setSaveEnabled(false);
            layout.addView(edit, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8);
            parent.addView(layout, params);
        }

        String value() { return edit.getText() == null ? "" : edit.getText().toString(); }
        void error(@StringRes int error) { layout.setError(getString(error)); edit.requestFocus(); }
    }
}
