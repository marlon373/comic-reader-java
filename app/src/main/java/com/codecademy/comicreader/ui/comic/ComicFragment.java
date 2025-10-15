package com.codecademy.comicreader.ui.comic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.codecademy.comicreader.data.ComicDatabase;
import com.codecademy.comicreader.databinding.FragmentComicBinding;
import com.codecademy.comicreader.dialog.SortDialog;
import com.codecademy.comicreader.model.Comic;
import com.codecademy.comicreader.model.Folder;
import com.codecademy.comicreader.ui.library.LibraryViewModel;
import com.codecademy.comicreader.ui.recent.RecentViewModel;
import com.codecademy.comicreader.utils.SystemUtil;
import com.codecademy.comicreader.view.ComicViewer;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class ComicFragment extends Fragment {

    private FragmentComicBinding binding;
    private final List<Comic> comicFiles = new ArrayList<>();
    private boolean isGridView;
    private ComicViewModel comicViewModel;
    private LibraryViewModel libraryViewModel;
    private RecentViewModel recentViewModel;
    private ComicAdapter comicAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService;

    private static final String PREFS_NAME = "ComicPrefs";
    private static final String KEY_DISPLAY_MODE = "isGridView";
    public static final String KEY_SORT_MODE = "isAscending";

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentComicBinding.inflate(inflater, container, false);
        View root = binding.getRoot();


        executorService = SystemUtil.createSmartExecutor(requireContext());
        loadPreferences();
        setupRecyclerView();

        comicAdapter = new ComicAdapter(new ArrayList<>(), this::onComicClicked, isGridView, requireContext(),executorService);
        binding.rvComicDisplay.setAdapter(comicAdapter);


        updateComicsView();
        loadSortPreferences();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        comicViewModel = new ViewModelProvider(this).get(ComicViewModel.class);
        recentViewModel = new ViewModelProvider(requireActivity()).get(RecentViewModel.class);
        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);

        // Folder added → Full rescan
        libraryViewModel.getFolderAdded().observe(getViewLifecycleOwner(), added -> {
            if (Boolean.TRUE.equals(added)) {
                Log.d("ComicFragment", "Folder was added — scanning...");
                scanAndUpdateComics(true);
                libraryViewModel.resetFolderAddedFlag();
            }
        });

        // Folder list changed → just refresh UI
        libraryViewModel.getFolders().observe(getViewLifecycleOwner(), folders -> {
            if (folders != null && !folders.isEmpty()) {
                Log.d("ComicFragment", "Folders available — refreshing comic view.");
                updateComicsView();

                executorService.execute(() -> {
                    ComicDatabase db = ComicDatabase.getInstance(requireContext());
                    boolean comicsEmpty = db.comicDao().getAllComics().isEmpty();
                    if (comicsEmpty) {
                        mainHandler.post(() -> {
                            Log.d("ComicFragment", "No comics in DB → initial full scan.");
                            scanAndUpdateComics(true);
                        });
                    }
                });
            }
        });

        // Folder removed → Full rescan
        libraryViewModel.getFolderRemoved().observe(getViewLifecycleOwner(), removed -> {
            if (Boolean.TRUE.equals(removed)) {
                comicFiles.clear();
                comicAdapter.updateComicList(new ArrayList<>());

                List<Folder> folders = libraryViewModel.getFolders().getValue();
                if (folders == null || folders.isEmpty()) {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvScanningBanner.setVisibility(View.GONE);
                } else {
                    mainHandler.postDelayed(() -> scanAndUpdateComics(true), 350);
                }

                libraryViewModel.notifyFolderRemovedHandled();
            }
        });

        // Swipe refresh → Full rescan
        binding.swipeRefresh.setOnRefreshListener(() -> {
            Log.d("ComicFragment", "Swipe-to-refresh triggered.");
            updateComicsView();

            List<Folder> folders = libraryViewModel.getFolders().getValue();
            if (folders == null || folders.isEmpty()) {
                Log.d("ComicFragment", "No folders → skip scan.");
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                binding.tvScanningBanner.setVisibility(View.GONE);
                return;
            }

            // Only scan if folders exist
            scanAndUpdateComics(true);

            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong("last_scan_timestamp", System.currentTimeMillis()).apply();
        });

        // UI messages
        comicViewModel.getNoComicsMessage().observe(getViewLifecycleOwner(), msg -> binding.tvShowNoComicsFound.setText(msg));
        comicViewModel.getAddOnLibraryMessage().observe(getViewLifecycleOwner(), msg -> binding.tvAddOnLibrary.setText(msg));
        comicViewModel.getNoComicFolderMessage().observe(getViewLifecycleOwner(), msg -> binding.tvShowNoComicsFolderFound.setText(msg));
    }


    // Display add folder first lunch
    private boolean isFirstAppLaunch() {
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        return !prefs.getBoolean("has_launched_before", false);
    }

    private void onComicClicked(Comic comic) {
        // 1. Save to recent (already existing)
        recentViewModel.addComicToRecent(comic);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> recentPaths = new LinkedHashSet<>(prefs.getStringSet("recent_paths", new LinkedHashSet<>()));
        recentPaths.remove(comic.getPath());
        recentPaths.add(comic.getPath());

        if (recentPaths.size() > 20) {
            while (recentPaths.size() > 20) {
                recentPaths.remove(recentPaths.iterator().next());
            }
        }
        prefs.edit().putStringSet("recent_paths", recentPaths).apply();

        // 2. Start ComicViewer activity
        Intent intent = new Intent(requireContext(), ComicViewer.class);
        intent.putExtra("comicPath", comic.getPath());
        startActivity(intent);
    }


    // Load display mode preference
    private void loadPreferences() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true);
    }

    // Toggle between grid and list
    public void toggleDisplayMode() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true);

        isGridView = !isGridView;
        prefs.edit().putBoolean(KEY_DISPLAY_MODE, isGridView).apply();

        Log.d("ComicFragment", "toggleDisplayMode: Switched to " + (isGridView ? "Grid View" : "List View"));

        // Fully reset the RecyclerView layout
        setupRecyclerView();

        // Reload comics to reflect latest state
        updateComicsView(); // Ensure comics are updated when switching layouts

        // Create a new adapter to force rebind with the correct layout
        comicAdapter = new ComicAdapter(comicFiles, this::onComicClicked, isGridView, requireContext(), executorService);
        binding.rvComicDisplay.setAdapter(comicAdapter);

        comicAdapter.notifyDataSetChanged(); // Ensure UI refresh
    }

    // Updates RecyclerView LayoutManager
    private void setupRecyclerView() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true);

        Log.d("ComicFragment", "setupRecyclerView: Applying " + (isGridView ? "Grid View" : "List View"));

        // Get current device orientation
        RecyclerView.LayoutManager layoutManager = getLayoutManager(isGridView);

        binding.rvComicDisplay.setLayoutManager(layoutManager);
        binding.rvComicDisplay.setHasFixedSize(true);
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

    // Updates the comic list from Room database
    private void updateComicsView() {
        Context context = getContext();
        if (context == null) {
            Log.w("ComicFragment", "Context is null, skipping updateComicsView");
            return;
        }
        executorService.execute(() -> {
            ComicDatabase db = ComicDatabase.getInstance(context);
            List<Comic> cachedComics = db.comicDao().getAllComics();
            List<Comic> validComics = new ArrayList<>();

            SharedPreferences prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE);
            Set<String> removedPaths = prefs.getStringSet("removed_paths", new HashSet<>());

            for (Comic comic : cachedComics) {
                if (removedPaths.contains(comic.getPath())) continue;

                DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(comic.getPath()));
                if (file != null && file.exists()) {
                    validComics.add(comic);
                } else {
                    db.comicDao().deleteComicByPath(comic.getPath());
                }
            }
            mainHandler.post(() -> {
                if (!isAdded() || getActivity() == null) return;
                updateComicsList(validComics);
            });
        });

    }

    // Scanning or Rescanning
    private void scanAndUpdateComics(boolean fullRescan) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvScanningBanner.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            ComicDatabase db = ComicDatabase.getInstance(requireContext());
            List<Folder> folders = libraryViewModel.getFolders().getValue();

            if (folders == null || folders.isEmpty()) {
                db.comicDao().deleteAll();
                mainHandler.post(() -> updateComicsList(new ArrayList<>()));
                return;
            }

            SharedPreferences removedPrefs = requireContext().getSharedPreferences("removed_comics", Context.MODE_PRIVATE);
            Set<String> removedPaths = new HashSet<>(removedPrefs.getStringSet("removed_paths", new HashSet<>()));

            Set<String> currentFolderPaths = folders.stream().map(Folder::getPath).collect(Collectors.toSet());

            //  Clean up comics from removed folders
            removedPaths.removeIf(path -> currentFolderPaths.stream().anyMatch(path::startsWith));
            removedPrefs.edit().putStringSet("removed_paths", removedPaths).apply();

            for (Comic comic : db.comicDao().getAllComics()) {
                if (currentFolderPaths.stream().noneMatch(p -> comic.getPath().startsWith(p))) {
                    db.comicDao().deleteComicByPath(comic.getPath());
                }
            }

            List<Comic> newComics = new ArrayList<>();
            Set<String> existingPaths = db.comicDao().getAllComics().stream()
                    .map(Comic::getPath)
                    .collect(Collectors.toSet());

            SharedPreferences scanPrefs = requireContext().getSharedPreferences("FolderScanPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor scanEditor = scanPrefs.edit();

            for (Folder folder : folders) {
                DocumentFile dir = DocumentFile.fromTreeUri(requireContext(), Uri.parse(folder.getPath()));
                if (dir == null || !dir.exists() || !dir.isDirectory()) continue;

                long lastScan = scanPrefs.getLong(folder.getPath(), 0);
                boolean shouldScanAll = fullRescan || dir.lastModified() > lastScan;

                if (shouldScanAll) {
                    scanFolderRecursively(dir, newComics, new HashSet<>(existingPaths));
                    scanEditor.putLong(folder.getPath(), System.currentTimeMillis());
                }
            }

            if (!newComics.isEmpty()) db.comicDao().insertAll(newComics);

            List<Comic> finalList = db.comicDao().getAllComics();
            scanEditor.apply();

            mainHandler.post(() -> {
                updateComicsList(finalList);
                binding.progressBar.setVisibility(View.GONE);
                binding.tvScanningBanner.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
            });
        });
    }

    // Recursive scan helper
    private void scanFolderRecursively(
            DocumentFile directory,
            List<Comic> comics,
            Set<String> existingComicPaths
    ) {
        List<Comic> batch = new ArrayList<>();
        ComicDatabase db = ComicDatabase.getInstance(requireContext());

        for (DocumentFile file : directory.listFiles()) {
            if (file.isDirectory()) {
                scanFolderRecursively(file, comics, existingComicPaths);
            } else if (file.getName() != null &&
                    (file.getName().endsWith(".cbr") ||
                            file.getName().endsWith(".cbz") ||
                            file.getName().endsWith(".pdf"))) {

                String path = file.getUri().toString();
                if (existingComicPaths.contains(path)) continue;

                String title = file.getName();
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(new Date(file.lastModified()));
                String size = formatFileSize(file.length());
                String format = title.substring(title.lastIndexOf('.') + 1);

                Comic comic = new Comic(title, path, date, size, format);
                comics.add(comic);
                batch.add(comic);

                if (batch.size() >= 10) {
                    db.comicDao().insertAll(batch);
                    List<Comic> uiBatch = new ArrayList<>(batch);
                    batch.clear();
                    mainHandler.post(() -> {
                        if (isAdded()) comicAdapter.appendComics(uiBatch);
                    });
                }
            }
        }

        if (!batch.isEmpty()) {
            db.comicDao().insertAll(batch);
            List<Comic> finalBatch = new ArrayList<>(batch);
            mainHandler.post(() -> {
                if (isAdded()) comicAdapter.appendComics(finalBatch);
            });
        }
    }

    // Updates the comic list from Room database
    private void updateComicsList(List<Comic> newComics) {
        if (!isAdded()) {
            Log.w("ComicFragment", "Fragment not attached, skipping updateComicsList.");
            return;
        }

        executorService.execute(() -> {
            Context context = getContext();
            if (context == null) {
                Log.w("ComicFragment", "Context is null in background thread, skipping update.");
                return;
            }

            SharedPreferences prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE);
            Set<String> removedPaths = new HashSet<>(prefs.getStringSet("removed_paths", new HashSet<>()));
            Set<String> seenPaths = new HashSet<>();
            List<Comic> validComics = new ArrayList<>();

            for (Comic comic : newComics) {
                String path = comic.getPath();

                if (removedPaths.contains(path)) {
                    Log.d("ComicFragment", "Skipping removed comic: " + comic.getName());
                    continue;
                }

                if (!seenPaths.add(path)) {
                    Log.d("ComicFragment", "Skipping duplicate comic: " + path);
                    continue;
                }

                DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(path));
                if (file != null && file.exists()) {
                    validComics.add(comic);
                } else {
                    Log.w("ComicFragment", "Skipping missing comic: " + comic.getName());
                }
            }

            mainHandler.post(() -> {
                if (!isAdded() || getActivity() == null) return;

                DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ComicDiffCallback(comicFiles, validComics));

                comicFiles.clear();
                comicFiles.addAll(validComics);

                diffResult.dispatchUpdatesTo(comicAdapter);
                updateUIVisibility();
                loadSortPreferences();
                Log.d("ComicFragment", "Displayed comic list updated. Total: " + comicFiles.size());
            });

        });
    }

    // Detect manually remove file or folder
    private boolean areAllFoldersInvalid(List<Folder> folders) {
        if (folders == null || folders.isEmpty()) return true;

        for (Folder folder : folders) {
            DocumentFile dir = DocumentFile.fromTreeUri(requireContext(), Uri.parse(folder.getPath()));
            if (dir != null && dir.exists() && dir.isDirectory()) {
                return false; // At least one valid folder
            }
        }
        return true; // All folders are missing or invalid
    }

    // Updates UI visibility based on whether comics exist
    private void updateUIVisibility() {
        if (binding == null) {
            Log.e("ComicFragment", "Binding is null in updateUIVisibility");
            return;
        }

        TextView noComicsMessage = binding.tvShowNoComicsFound;
        TextView addOnLibraryMessage = binding.tvAddOnLibrary;
        TextView noComicsFolderMessage = binding.tvShowNoComicsFolderFound;

        // Hide all initially
        noComicsMessage.setVisibility(View.GONE);
        addOnLibraryMessage.setVisibility(View.GONE);
        noComicsFolderMessage.setVisibility(View.GONE);
        binding.rvComicDisplay.setVisibility(View.GONE);

        boolean isLoading = binding.progressBar.getVisibility() == View.VISIBLE;

        if (isLoading) {
            // Don't show any message if loading
            binding.tvShowNoComicsFound.setVisibility(View.GONE);
            binding.tvAddOnLibrary.setVisibility(View.GONE);
            binding.tvShowNoComicsFolderFound.setVisibility(View.GONE);
            return;
        }

        //  If comics are found, show only RecyclerView
        if (!comicFiles.isEmpty()) {
            binding.rvComicDisplay.setVisibility(View.VISIBLE);
            Log.d("ComicFragment", "Comics found, showing RecyclerView.");
            return;
        }

        //  Get folders from ViewModel
        List<Folder> folders = libraryViewModel.getFolders().getValue();
        if (folders == null) {
            Log.w("ComicFragment", "Folders list is null. Skipping UI update.");
            return;
        }

        if (isFirstAppLaunch()) {
            addOnLibraryMessage.setVisibility(View.VISIBLE);
            Log.d("ComicFragment", "First launch: showing Add-on Library message.");
            return; // Skip other checks
        }

        //  If no valid folders
        if (areAllFoldersInvalid(folders)) {
            noComicsFolderMessage.setVisibility(View.VISIBLE);
            Log.d("ComicFragment", "No valid folders found, showing 'No Comic Folder Found'.");
        } else {
            //  Valid folders exist but no comics
            noComicsMessage.setVisibility(View.VISIBLE);
            Log.d("ComicFragment", "Folders exist but no comics, showing 'No Comics Found'.");
        }
    }


    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes <= 0) return "0 KB";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeInBytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(sizeInBytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    // Formats file sizes for display
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

    private void loadSortPreferences() {
        if (comicFiles.isEmpty()) {
            Log.w("ComicFragment", "loadSortPreferences: comicFiles not ready. Skipping sort.");
            return;
        }

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String criteria = prefs.getString("sort_criteria", "name");
        boolean isAscending = prefs.getBoolean(KEY_SORT_MODE, true);

        Log.d("ComicFragment", "loadSortPreferences: criteria=" + criteria + ", ascending=" + isAscending);

        applySorting(criteria, isAscending);
    }

    private void applySorting(String criteria, boolean isAscending) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("sort_criteria", criteria)
                .putBoolean(KEY_SORT_MODE, isAscending)
                .apply();

        if (comicFiles.isEmpty()) {
            Log.w("ComicFragment", "applySorting: comicFiles is null or empty. Skipping sort.");
            return;
        }

        Comparator<Comic> comparator = switch (criteria) {
            case "size" -> Comparator.comparingLong(comic -> parseFileSize(comic.getSize()));
            case "date" -> Comparator.comparing(Comic::getDate);
            case "name" -> Comparator.comparing(Comic::getName, String.CASE_INSENSITIVE_ORDER);
            default -> {
                Log.w("ComicFragment", "applySorting: Unknown sort criteria: " + criteria + ", defaulting to name");
                yield Comparator.comparing(Comic::getName, String.CASE_INSENSITIVE_ORDER);
            }
        };
        comicFiles.sort(isAscending ? comparator : comparator.reversed());

        if (comicAdapter != null) {
            comicAdapter.updateComicList(new ArrayList<>(comicFiles)); // Clone for safe adapter use
        }

        Log.d("ComicFragment", "applySorting: Sorted by " + criteria + " | Ascending: " + isAscending);
    }

    // Sort arrange: name, size, date
    public void showSortDialog() {
        SortDialog sortDialog = SortDialog.newInstance();

        sortDialog.setOnSortListener((criteria, isAscending) -> {
            Log.d("ComicFragment", "showSortDialog: User selected sort - " + criteria + ", Ascending: " + isAscending);
            applySorting(criteria, isAscending);
        });

        sortDialog.show(getParentFragmentManager(), "SortDialog");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("ComicFragment", "onResume: checking comics state...");

        // Always validate DB + refresh UI
        updateComicsView();

        // Skip scanning on very first app launch
        if (isFirstAppLaunch()) {
            Log.d("ComicFragment", "First app launch → skip onResume scan.");
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastScanTime = prefs.getLong("last_scan_timestamp", 0);
        long now = System.currentTimeMillis();

        List<Folder> folders = libraryViewModel.getFolders().getValue();
        if (folders == null || folders.isEmpty()) {
            Log.d("ComicFragment", "No folders in library → skipping scan.");
            return;
        }

        SharedPreferences scanPrefs = requireContext().getSharedPreferences("FolderScanPrefs", Context.MODE_PRIVATE);
        boolean shouldRescan = false;

        for (Folder folder : folders) {
            DocumentFile dir = DocumentFile.fromTreeUri(requireContext(), Uri.parse(folder.getPath()));
            if (dir == null || !dir.exists() || !dir.isDirectory()) continue;

            long lastModified = dir.lastModified();
            long lastScan = scanPrefs.getLong(folder.getPath(), 0);

            if (lastModified > lastScan) {
                Log.d("ComicFragment", "Folder changed: " + folder.getPath());
                shouldRescan = true;
                break;
            }
        }

        if (shouldRescan || now - lastScanTime >= 5 * 60_000) { // fallback: 5 min
            Log.d("ComicFragment", "Triggering rescan on resume.");
            scanAndUpdateComics(false);
            prefs.edit().putLong("last_scan_timestamp", now).apply();
        } else {
            Log.d("ComicFragment", "Skipping rescan on resume (no changes detected).");
        }
    }

    @Override
    public void onDestroyView() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = null; // prevent accidental reuse
        }
        super.onDestroyView();
        binding = null;
    }
}