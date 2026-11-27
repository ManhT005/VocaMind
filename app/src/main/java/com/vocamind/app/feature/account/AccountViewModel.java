package com.vocamind.app.feature.account;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.vocamind.app.R;

/** The SDK owns the session. Passwords are used for a request and never persisted by the app. */
public final class AccountViewModel extends ViewModel {
    static final class State {
        final boolean available, busy, signedIn, verified;
        final String email, name;
        @StringRes final int message;

        State(boolean available, boolean busy, FirebaseUser user, @StringRes int message) {
            this.available = available;
            this.busy = busy;
            this.signedIn = user != null;
            this.verified = user != null && user.isEmailVerified();
            this.email = user == null ? null : user.getEmail();
            this.name = user == null ? null : user.getDisplayName();
            this.message = message;
        }
    }

    private final FirebaseAuth auth;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final FirebaseAuth.AuthStateListener authListener = ignored -> publish();
    private boolean busy;
    private int message;

    public AccountViewModel() {
        auth = configuredAuth();
        if (auth != null) {
            auth.setLanguageCode("vi");
            auth.addAuthStateListener(authListener);
        }
        publish();
    }

    public static boolean isAvailable() {
        return configuredAuth() != null;
    }

    private static FirebaseAuth configuredAuth() {
        try {
            return FirebaseAuth.getInstance(FirebaseApp.getInstance());
        } catch (IllegalStateException unconfigured) {
            return null;
        }
    }

    LiveData<State> state() { return state; }

    void signIn(String email, String password) {
        if (!begin(false)) return;
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task ->
                finish(task.isSuccessful() ? 0 : failure(task, R.string.account_credentials_failure)));
    }

    void register(String email, String password) {
        if (!begin(false)) return;
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                finish(failure(task, R.string.account_invalid_registration));
                return;
            }
            FirebaseUser user = task.getResult().getUser();
            if (user == null) {
                finish(R.string.account_failure);
                return;
            }
            user.sendEmailVerification().addOnCompleteListener(sent -> finish(sent.isSuccessful()
                    ? R.string.account_verification_sent : R.string.account_verification_failed));
        });
    }

    void resetPassword(String email) {
        if (!begin(false)) return;
        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            // A missing account must not be distinguishable from an existing one.
            Exception error = task.getException();
            boolean unknownUser = error instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException;
            finish(task.isSuccessful() || unknownUser ? R.string.account_reset_sent : R.string.account_failure);
        });
    }

    void sendVerification() {
        if (!begin(true)) return;
        auth.getCurrentUser().sendEmailVerification().addOnCompleteListener(task ->
                finish(task.isSuccessful() ? R.string.account_verification_sent : R.string.account_failure));
    }

    void checkVerification() {
        if (!begin(true)) return;
        auth.getCurrentUser().reload().addOnCompleteListener(task ->
                finish(task.isSuccessful() ? 0 : R.string.account_failure));
    }

    void updateName(String name) {
        if (!begin(true)) return;
        auth.getCurrentUser().updateProfile(new UserProfileChangeRequest.Builder()
                .setDisplayName(name).build()).addOnCompleteListener(task ->
                finish(task.isSuccessful() ? R.string.account_profile_saved : R.string.account_failure));
    }

    void changePassword(String current, String replacement) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null || user.getEmail() == null || !begin(true)) return;
        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), current))
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        finish(task.getException() instanceof FirebaseAuthInvalidCredentialsException
                                ? R.string.account_current_password_failure : R.string.account_failure);
                        return;
                    }
                    user.updatePassword(replacement).addOnCompleteListener(update ->
                            finish(update.isSuccessful() ? R.string.account_password_changed : R.string.account_failure));
                });
    }

    void signOut() {
        if (!begin(true)) return;
        auth.signOut();
        finish(R.string.account_signed_out);
    }

    private boolean begin(boolean signedIn) {
        if (auth == null || busy || (auth.getCurrentUser() != null) != signedIn) return false;
        busy = true;
        message = 0;
        publish();
        return true;
    }

    private static int failure(Task<?> task, @StringRes int fallback) {
        return task.getException() instanceof FirebaseNetworkException ? R.string.account_failure : fallback;
    }

    private void finish(@StringRes int result) {
        busy = false;
        message = result;
        publish();
    }

    private void publish() {
        state.setValue(new State(auth != null, busy, auth == null ? null : auth.getCurrentUser(), message));
    }

    @Override protected void onCleared() {
        if (auth != null) auth.removeAuthStateListener(authListener);
    }
}
