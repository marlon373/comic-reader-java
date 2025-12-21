package com.codecademy.comicreader.ui.recent;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.codecademy.comicreader.data.ComicDatabase;
import com.codecademy.comicreader.databinding.FragmentRecentBinding;
import com.codecademy.comicreader.dialog.ClearAllRecentDialog;
import com.codecademy.comicreader.dialog.SortDialog;
import com.codecademy.comicreader.model.Comic;
import com.codecademy.comicreader.ui.comic.ComicAdapter;
import com.codecademy.comicreader.utils.SystemUtil;
import com.codecademy.comicreader.view.ComicViewer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class RecentFragment extends Fragment {

    private FragmentRecentBinding binding;
    private ComicAdapter comicAdapter;
    private final List<Comic> recentComics = new ArrayList<>();
    private boolean isGridView;
    private RecentViewModel recentViewModel;
    private ExecutorService executorService;
    private Context appContext;

    private static final String PREFS_NAME = "ComicPrefs";
    private static final String KEY_DISPLAY_MODE = "isGridView";
    public static final String KEY_SORT_MODE = "isAscending";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRecentBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        appContext = requireActivity().getApplicationContext();
        executorService = SystemUtil.createIOExecutor(appContext);

        recentViewModel = new ViewModelProvider(requireActivity()).get(RecentViewModel.class);

        loadPreferences();
        setupRecyclerView();

        comicAdapter = new ComicAdapter(recentComics, this::onComicClicked, isGridView,requireContext());
        binding.rvRecentDisplay.setAdapter(comicAdapter);

        recentViewModel.getRecentComics().observe(getViewLifecycleOwner(), comics -> {
            recentComics.clear();
            recentComics.addAll(comics);
            comicAdapter.notifyDataSetChanged();
        });

        loadSortPreferences();

        return view;
    }

    private void onComicClicked(Comic comic) {
        Intent intent = new Intent(requireContext(), ComicViewer.class);
        intent.putExtra("comicPath", comic.getPath());
        startActivity(intent);
    }

    private void loadPreferences() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true);
    }

    public void toggleDisplayMode() {
        // 1) Toggle display mode in SharedPreferences
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean current = prefs.getBoolean(KEY_DISPLAY_MODE, true);
        boolean newGrid = !current;

        prefs.edit().putBoolean(KEY_DISPLAY_MODE, newGrid).apply();

        android.util.Log.d("ComicFragment",
                "toggleDisplayMode: Switched to " + (newGrid ? "Grid View" : "List View"));

        // 2) Update LayoutManager
        RecyclerView.LayoutManager layoutManager = getLayoutManager(newGrid);
        if (binding != null) {
            binding.rvRecentDisplay.setLayoutManager(layoutManager);
        }

        // 3) Update or create adapter
        if (comicAdapter != null) {
            comicAdapter.isGridView = newGrid;
            comicAdapter.notifyDataSetChanged(); // recreate view holders according to new viewType
        } else {
            comicAdapter = new ComicAdapter(recentComics, this::onComicClicked, newGrid, requireContext());
            if (binding != null) {
                binding.rvRecentDisplay.setAdapter(comicAdapter);
            }
        }

        // 4) Additional UI state already persisted in SharedPreferences
    }

    // Updates RecyclerView LayoutManager
    private void setupRecyclerView() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true);

        Log.d("ComicFragment", "setupRecyclerView: Applying " + (isGridView ? "Grid View" : "List View"));

        // Get current device orientation
        RecyclerView.LayoutManager layoutManager = getLayoutManager(isGridView);

        binding.rvRecentDisplay.setLayoutManager(layoutManager);
        binding.rvRecentDisplay.setHasFixedSize(true);
    }

    private RecyclerView.LayoutManager getLayoutManager(boolean isGridView) {
        int orientation = getResources().getConfiguration().orientation;

        RecyclerView.LayoutManager layoutManager;

        if (isGridView) {
            //  In portrait → 2 columns
            //  In landscape → 4 columns
            int spanCount = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 4 : 2;
            layoutManager = new GridLayoutManager(getContext(), spanCount);
        } else {
            // List mode always 1 column
            layoutManager = new LinearLayoutManager(getContext());
        }
        return layoutManager;
    }

    private void recentLoadDatabase() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> savedPaths = prefs.getStringSet("recent_paths", new LinkedHashSet<>());

        if (savedPaths.isEmpty()) return;

        executorService.execute(() -> {
            ComicDatabase db = ComicDatabase.getInstance(requireContext());
            List<Comic> allComics = db.comicDao().getAllComics();

            Map<String, Comic> comicMap = new HashMap<>();
            for (Comic comic : allComics) {
                comicMap.put(comic.getPath(), comic);
            }

            List<Comic> loadedComics = new ArrayList<>();
            for (String path : savedPaths) {
                if (comicMap.containsKey(path)) {
                    loadedComics.add(comicMap.get(path));
                } else {
                    Log.w("RecentFragment", "Comic not found in DB: " + path);
                }
            }
            // Validate and filter using helper
            List<Comic> validComics = recentComicList(loadedComics);

            // Update saved recent paths
            Set<String> validPaths = validComics.stream()
                    .map(Comic::getPath)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            prefs.edit().putStringSet("recent_paths", validPaths).apply();

            requireActivity().runOnUiThread(() -> recentViewModel.setRecentComics(validComics));
        });
    }

    private List<Comic> recentComicList(List<Comic> newComics) {
        SharedPreferences prefs = appContext.getSharedPreferences("removed_comics", Context.MODE_PRIVATE);
        Set<String> removedPaths = prefs.getStringSet("removed_paths", new HashSet<>());

        List<Comic> validComics = new ArrayList<>();

        for (Comic comic : newComics) {
            if (removedPaths.contains(comic.getPath())) {
                Log.d("RecentFragment", "Skipping removed comic: " + comic.getName());
                continue;
            }

            DocumentFile file = DocumentFile.fromSingleUri(appContext, Uri.parse(comic.getPath()));
            if (file != null && file.exists()) {
                validComics.add(comic);
            } else {
                Log.w("RecentFragment", "File not found or missing: " + comic.getName());
            }
        }

        return validComics;
    }

    private void loadSortPreferences() {
        if (recentComics.isEmpty()) {
            Log.w("RecentFragment", "loadSortPreferences: comicFiles not ready. Skipping sort.");
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String criteria = prefs.getString("sort_criteria", "name");
        boolean isAscending = prefs.getBoolean(KEY_SORT_MODE, true);

        Log.d("RecentFragment", "loadSortPreferences: criteria=" + criteria + ", ascending=" + isAscending);

        applySorting(criteria, isAscending);
    }

    private long parseFileSize(String sizeStr) {
        try {
            String[] parts = sizeStr.split(" ");
            if (parts.length != 2) return 0;
            double number = Double.parseDouble(parts[0].replace(",", ""));
            String unit = parts[1].toUpperCase();

            return switch (unit) {
                case "B" -> (long) number;
                case "KB" -> (long) (number * 1024);
                case "MB" -> (long) (number * 1024 * 1024);
                case "GB" -> (long) (number * 1024 * 1024 * 1024);
                default -> 0;
            };
        } catch (Exception e) {
            Log.e("ComicFragment", "Failed to parse size: " + sizeStr, e);
            return 0;
        }
    }

    private void applySorting(String criteria, boolean isAscending) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("sort_criteria", criteria)
                .putBoolean(KEY_SORT_MODE, isAscending)
                .apply();

        if (recentComics.isEmpty()) {
            Log.w("RecentFragment", "applySorting: comicFiles is null or empty. Skipping sort.");
            return;
        }

        Comparator<Comic> comparator = switch (criteria) {
            case "size" -> Comparator.comparingLong(comic -> parseFileSize(comic.getSize()));
            case "date" -> Comparator.comparing(Comic::getDate);
            case "name" -> Comparator.comparing(Comic::getName, String.CASE_INSENSITIVE_ORDER);
            default -> {
                Log.w("RecentFragment", "applySorting: Unknown sort criteria: " + criteria + ", defaulting to name");
                yield Comparator.comparing(Comic::getName, String.CASE_INSENSITIVE_ORDER);
            }
        };
        recentComics.sort(isAscending ? comparator : comparator.reversed());

        if (comicAdapter != null) {
            comicAdapter.updateComicList(new ArrayList<>(recentComics)); // Clone for safe adapter use
        }

        Log.d("RecentFragment", "applySorting: Sorted by " + criteria + " | Ascending: " + isAscending);
    }

    public void showSortDialog() {
        SortDialog sortDialog = SortDialog.newInstance();

        sortDialog.setOnSortListener((criteria, isAscending) -> {
            Log.d("RecentFragment", "showSortDialog: User selected sort - " + criteria + ", Ascending: " + isAscending);
            applySorting(criteria, isAscending);
        });

        sortDialog.show(getParentFragmentManager(), "SortDialog");
    }

    public void showClearAllDialog() {
        ClearAllRecentDialog dialog = ClearAllRecentDialog.newInstance();

        dialog.setOnClearAllRecentListener(() -> {
            //  Clear from SharedPreferences
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove("recent_paths").apply();
            // Clear ViewModel to refresh the UI
            recentViewModel.setRecentComics(new ArrayList<>());

        });
        dialog.show(getParentFragmentManager(), "ClearAllDialog");
    }

    @Override
    public void onResume() {
        super.onResume();
        recentLoadDatabase();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        comicAdapter = null;
        binding = null;
    }

}

