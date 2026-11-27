package com.vocamind.app;

import android.os.Bundle;
import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.vocamind.app.feature.personal.PersonalPreferences;
import com.vocamind.app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_OPEN_TODAY = "com.vocamind.app.action.OPEN_TODAY";

    private ActivityMainBinding binding;
    private NavController navigation;

    public void selectStudyTab() {
        binding.bottomTabs.setSelectedItemId(R.id.todayFragment);
    }

    private boolean openHomeTab() {
        var current = navigation.getCurrentDestination();
        if (current == null) return false;
        if (current.getId() == R.id.homeFragment) return true;

        if (navigation.popBackStack(R.id.homeFragment, false, true)) return true;
        // A restored navigation state can lack the original Home entry.
        var options = new androidx.navigation.NavOptions.Builder().setLaunchSingleTop(true).build();
        navigation.navigate(R.id.homeFragment, null, options);
        var destination = navigation.getCurrentDestination();
        return destination != null && destination.getId() == R.id.homeFragment;
    }

    private void updateTabs() {
        if (navigation.getCurrentDestination() == null) return;
        int destination = navigation.getCurrentDestination().getId();
        boolean tab = binding.bottomTabs.getMenu().findItem(destination) != null;
        binding.bottomTabs.setVisibility(tab ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(PersonalPreferences.nightMode(this));
        super.onCreate(savedInstanceState);
        // The shell owns system bar and IME insets on every supported Android version.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navigation = host.getNavController();
        NavigationUI.setupWithNavController(binding.bottomTabs, navigation);
        binding.bottomTabs.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.homeFragment) {
                return openHomeTab();
            }
            return NavigationUI.onNavDestinationSelected(item, navigation);
        });
        binding.bottomTabs.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.homeFragment) openHomeTab();
        });
        navigation.addOnDestinationChangedListener((controller, destination, arguments) -> {
            updateTabs();
            if (destination.getId() != R.id.vocabularyFragment) {
                WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                        .hide(WindowInsetsCompat.Type.ime());
            }
        });

        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            View content = binding.getRoot().findViewById(R.id.nav_host_fragment);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) content.getLayoutParams();
            params.width = Math.round(560 * getResources().getDisplayMetrics().density);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            content.setLayoutParams(params);
        }
        boolean lightBars = getResources().getBoolean(R.bool.shell_light_system_bars);
        var bars = WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        bars.setAppearanceLightStatusBars(lightBars);
        bars.setAppearanceLightNavigationBars(lightBars);
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomTabs, (view, insets) -> insets);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
        openTodayFromIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openTodayFromIntent(intent);
    }

    private void openTodayFromIntent(Intent intent) {
        if (intent != null && ACTION_OPEN_TODAY.equals(intent.getAction())) {
            intent.setAction(null);
            selectStudyTab();
        }
    }
}
