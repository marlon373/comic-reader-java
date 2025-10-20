package com.codecademy.comicreader;


import android.content.SharedPreferences;

import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.codecademy.comicreader.model.Folder;
import com.codecademy.comicreader.theme.ThemeManager;
import com.codecademy.comicreader.ui.comic.ComicFragment;
import com.codecademy.comicreader.ui.library.LibraryViewModel;
import com.codecademy.comicreader.ui.recent.RecentFragment;
import com.codecademy.comicreader.utils.FolderUtils;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.codecademy.comicreader.databinding.ActivityMainBinding;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private static final String PREFS_NAME = "comicPrefs";
    private static final String KEY_THEME = "isNightMode";
    public static final String KEY_DISPLAY_MODE = "isGridView";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Forcefully apply app-defined theme
        ThemeManager.applyTheme(this);

        // Initialize LibraryViewModel scoped to the activity
        LibraryViewModel libraryViewModel = new ViewModelProvider(this).get(LibraryViewModel.class);

        // Load folder list from SharedPreferences
        List<Folder> folders = FolderUtils.loadFolders(this);

        // Push folder list into ViewModel so it's shared across fragments
        libraryViewModel.setFolders(folders);

        Log.d("MainActivity", "Folders loaded at startup: " + folders.size());

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_comic, R.id.nav_recent, R.id.nav_library, R.id.nav_settings)
                .setOpenableLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // Listen for destination changes and refresh menu
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            invalidateOptionsMenu(); // Refresh menu when destination changes

        });
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear(); // Clear the previous menu
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);

        NavDestination currentDestination = navController.getCurrentDestination();
        if (currentDestination == null) return super.onPrepareOptionsMenu(menu); // Prevent crashes

        int currentDestinationId = currentDestination.getId();

        if (currentDestinationId == R.id.nav_comic) {
            getMenuInflater().inflate(R.menu.menu_comic, menu);
        } else if (currentDestinationId == R.id.nav_recent) {
            getMenuInflater().inflate(R.menu.menu_recent, menu);
        } else if (currentDestinationId == R.id.nav_library) {
            getMenuInflater().inflate(R.menu.menu_library, menu);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_more) {
            View anchor = findViewById(R.id.action_more);
            NavDestination dest = Navigation.findNavController(this, R.id.nav_host_fragment_content_main).getCurrentDestination();
            if (dest != null) {
                int id = dest.getId();
                if (id == R.id.nav_comic) {
                    showCustomMenu(anchor, R.layout.custom_action_comic);
                } else if (id == R.id.nav_recent) {
                    showCustomMenu(anchor, R.layout.custom_action_recent);
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCustomMenu(View anchor, int layoutResId) {
        View popupView = LayoutInflater.from(this).inflate(layoutResId, null);

        final PopupWindow popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(8); // Optional shadow
        popupWindow.showAsDropDown(anchor);

        // === Action: SORT ===
        View sort = popupView.findViewById(R.id.action_sort);
        if (sort != null) {
            sort.setOnClickListener(v -> {
                openSortDialog();
                popupWindow.dismiss();
            });
        }

        // === Action: DISPLAY (Grid/List) ===
        View display = popupView.findViewById(R.id.action_display);
        if (display != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true);
            ((TextView) display).setText(isGridView ? R.string.action_list : R.string.action_grid);
            ((TextView) display).setCompoundDrawablesWithIntrinsicBounds(
                    isGridView ? R.drawable.ic_menu_item_list : R.drawable.ic_menu_item_grid, 0, 0, 0
            );
            display.setOnClickListener(v -> {
                toggleDisplayMode();
                popupWindow.dismiss();
            });
        }

        // === Action: DAY/NIGHT MODE ===
        View dayNight = popupView.findViewById(R.id.action_day_night);
        if (dayNight != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean isNightMode = prefs.getBoolean(KEY_THEME, false);
            ((TextView) dayNight).setText(isNightMode ? R.string.action_day : R.string.action_night);
            ((TextView) dayNight).setCompoundDrawablesWithIntrinsicBounds(
                    isNightMode ? R.drawable.ic_menu_item_day : R.drawable.ic_menu_item_night, 0, 0, 0
            );
            dayNight.setOnClickListener(v -> {
                toggleDayNightMode(); // Toggle mode
                popupWindow.dismiss();
            });
        }

        // === Action: CLEAR ALL (only in recent layout) ===
        View clearAll = popupView.findViewById(R.id.action_clear_all);
        if (clearAll != null) {
            clearAll.setOnClickListener(v -> {
                openClearAllDialog();
                popupWindow.dismiss();
            });
        }
        // Show the popup below the toolbar icon
        popupWindow.showAsDropDown(anchor, -100, 0, Gravity.END);
    }

    private void toggleDayNightMode() {
        ThemeManager.toggleTheme(this);
    }

    // Share toggleDisPlayMode on comicFragment and recentFragment
    private void toggleDisplayMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true);
        isGridView = !isGridView;
        prefs.edit().putBoolean(KEY_DISPLAY_MODE, isGridView).apply();

        // Toggle fragments
        Fragment navHostFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment instanceof NavHostFragment host) {
            Fragment fragment = host.getChildFragmentManager().getPrimaryNavigationFragment();
            if (fragment instanceof ComicFragment comicFragment) {
                comicFragment.toggleDisplayMode();
            } else if (fragment instanceof RecentFragment recentFragment) {
                recentFragment.toggleDisplayMode();
            }
        }
    }

    // Share sort dialog on comicFragment and recentFragment
    private void openSortDialog() {
        Fragment navHostFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment instanceof NavHostFragment host) {
            Fragment fragment = host.getChildFragmentManager().getPrimaryNavigationFragment();
            if (fragment instanceof ComicFragment comicFragment) {
                comicFragment.showSortDialog();
                Log.d("MainActivity", "Successfully called showSortDialog.");
            } else if (fragment instanceof  RecentFragment recentFragment ) {
                recentFragment.showSortDialog();
                Log.d("MainActivity", "Successfully called showSortDialog.");
            } else {
                Log.e("MainActivity", "ComicFragment not found!");
            }
        }
    }

    private void openClearAllDialog() {
        Fragment navHostFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment instanceof NavHostFragment) {
            Fragment recentFragment = navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
            if (recentFragment instanceof RecentFragment) {
                ((RecentFragment) recentFragment).showClearAllDialog();
                Log.d("MainActivity", "Successfully called showClearAllDialog.");
            } else {
                Log.e("MainActivity", "RecentFragment not found!");
            }
        }
    }

    public void setupHamburgerMenu() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) || super.onSupportNavigateUp();
    }

}
