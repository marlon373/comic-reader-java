package com.codecademy.comicreader.ui.library;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;


import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.codecademy.comicreader.MainActivity;
import com.codecademy.comicreader.R;
import com.codecademy.comicreader.databinding.FragmentLibraryBinding;
import com.codecademy.comicreader.dialog.RemoveFolderDialog;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.codecademy.comicreader.data.ComicDatabase;
import com.codecademy.comicreader.data.LibraryDatabase;
import com.codecademy.comicreader.model.Folder;
import com.codecademy.comicreader.data.dao.LibraryDao;
import com.codecademy.comicreader.utils.FolderUtils;
import com.codecademy.comicreader.view.ComicViewer;

public class LibraryFragment extends Fragment {

    private FragmentLibraryBinding binding;
    private final List<Folder> folderItems = new ArrayList<>();
    private LibraryFolderAdapter libraryFolderAdapter;
    private final Stack<Uri> folderHistory = new Stack<>();
    private Folder selectedFolder; // To track the currently selected folder
    private boolean isItemSelected = false; // Track selection state for action_delete visibility
    private boolean isNavigating = false;
    private LibraryViewModel libraryViewModel;

    // Room database and DAO
    private LibraryDatabase libraryDatabase;
    private LibraryDao folderItemDao;

    // ExecutorService for background tasks
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Folder picker launcher
    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    assert uri != null;
                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    addFolder(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Initialize Room database and DAO
        libraryDatabase = LibraryDatabase.getInstance(requireContext());
        folderItemDao = libraryDatabase.folderItemDao();


        setupRecyclerView();

        // FAB click opens the folder picker
        binding.fab.setOnClickListener(v -> openFolderPicker());

        // Load saved folders
        loadSavedFolders();

        // Menu provider
        requireActivity().addMenuProvider(new androidx.core.view.MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_library, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuItem deleteItem = menu.findItem(R.id.action_delete);
                if (deleteItem != null) {
                    deleteItem.setVisible(isItemSelected && selectedFolder != null);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int itemId = menuItem.getItemId();

                if (itemId == android.R.id.home) {
                    if (!folderHistory.isEmpty()) {
                        folderHistory.pop();

                        if (!folderHistory.isEmpty()) {
                            Uri parentUri = folderHistory.peek();
                            folderItems.clear();
                            loadFolderContents(parentUri);
                        } else {
                            resetToFolderList();
                        }
                        return true;
                    }
                } else if (itemId == R.id.action_delete) {
                    showRemoveFolderDialog();
                    return true;
                }

                return false;
            }
        }, getViewLifecycleOwner());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Now it's safe to access ViewModel scoped to activity
        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);

        // Observes ViewModel text changes and updates UI
        libraryViewModel.getAddFolderLibrary().observe(getViewLifecycleOwner(), message -> {
            binding.tvInstruction.setText(message); // Update the message text
            updateEmptyMessageVisibility(); // Ensure visibility based on folder list
        });

        //  Observe folders or perform setup
        libraryViewModel.getFolders().observe(getViewLifecycleOwner(), folders -> {
            Log.d("LibraryFragment", "Observed folders: " + folders.size());

            folderItems.clear();
            folderItems.addAll(folders);
            libraryFolderAdapter.notifyDataSetChanged();

            updateEmptyMessageVisibility();
        });
    }

    // Updates RecyclerView LayoutManager based on orientation
    private void setupRecyclerView() {
        if (binding == null) return;

        // Get the current orientation
        int orientation = getResources().getConfiguration().orientation;

        //  Portrait = 3 columns | Landscape = 5 columns
        int spanCount = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 5 : 3;

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
        binding.rvComicLibrary.setLayoutManager(gridLayoutManager);
        binding.rvComicLibrary.setHasFixedSize(true);

        // Adapter setup (if not yet set)
        if (libraryFolderAdapter == null) {
            libraryFolderAdapter = new LibraryFolderAdapter(folderItems, this::onFolderClicked, this::onFolderLongClicked);
            binding.rvComicLibrary.setAdapter(libraryFolderAdapter);
        } else {
            binding.rvComicLibrary.setAdapter(libraryFolderAdapter);
        }
    }

    // Updates the toolbar name dynamically
    private void updateToolbarTitle(String title) {
        if (getActivity() instanceof AppCompatActivity) {
            Objects.requireNonNull(((AppCompatActivity) getActivity()).getSupportActionBar()).setTitle(title);
        }
    }

    // Enables or disables the back button in the toolbar
    private void setToolbarBackButtonEnabled(boolean enabled) {
        if (getActivity() instanceof AppCompatActivity activity) {
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
                activity.getSupportActionBar().setHomeAsUpIndicator(null); // Use default back arrow
            }
        }
    }

    // Shows or hides the empty folder message based on folder availability
    private void updateEmptyMessageVisibility() {
        TextView emptyTextView = binding.tvInstruction;
        if (folderItems.isEmpty()) {
            emptyTextView.setVisibility(View.VISIBLE);  // Show message if no folders
        } else {
            emptyTextView.setVisibility(View.GONE);    // Hide message if folders exist
        }
    }

    // Hide add-folder on comicFragment
    private void setFirstAppLaunchCompleted() {
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("has_launched_before", true).apply();
    }

    // Opens Android’s folder picker
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        folderPickerLauncher.launch(intent);
    }

    // Adds a new folder and saves it in the database
    private void addFolder(Uri uri) {
        DocumentFile directory = DocumentFile.fromTreeUri(requireContext(), uri);
        if (directory != null && directory.isDirectory()) {
            // Check if the folder already exists in the list
            for (Folder item : folderItems) {
                if (item.getPath().equals(uri.toString())) {
                    Toast.makeText(requireContext(), "Folder already exists", Toast.LENGTH_SHORT).show();
                    return; // Prevent duplicate addition
                }
            }
            // Check if the folder already exists in the database
            executorService.execute(() -> {
                Folder existingFolder = folderItemDao.getFolderByPath(uri.toString());
                if (existingFolder != null) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Folder already exists in database", Toast.LENGTH_SHORT).show());
                    return;
                }
                // If folder is unique, add it
                Folder newItem = new Folder(directory.getName(), uri.toString(), true);
                folderItems.add(newItem);
                folderItemDao.insert(newItem);

                requireActivity().runOnUiThread(() -> {
                    libraryFolderAdapter.notifyDataSetChanged();
                    FolderUtils.saveFolders(requireContext(), folderItems);
                    libraryViewModel.setFolders(folderItems); // Ensure LiveData update
                    libraryViewModel.notifyFolderAdded(); // Trigger ComicFragment
                    updateEmptyMessageVisibility();
                });
                Log.d("LibraryFragment", "Folder saved in DB: " + directory.getName());
            });
        }
        setFirstAppLaunchCompleted();
    }

    // Loads saved folders from the database
    private void loadSavedFolders() {
        executorService.execute(() -> {
        List<Folder> savedEntities = folderItemDao.getAllFolders();
        List<Folder> newFolderItems = new ArrayList<>();

        for (Folder entity : savedEntities) {
            DocumentFile directory = DocumentFile.fromTreeUri(requireContext(), Uri.parse(entity.getPath()));
            if (directory != null && directory.isDirectory()) {
                newFolderItems.add(new Folder(entity.getName(), entity.getPath(), true));
            }
        }

        requireActivity().runOnUiThread(() -> {
            folderItems.clear();
            folderItems.addAll(newFolderItems);
            libraryFolderAdapter.notifyDataSetChanged();
            FolderUtils.saveFolders(requireContext(), newFolderItems);
            libraryViewModel.setFolders(newFolderItems); // Ensure ComicFragment updates
            updateEmptyMessageVisibility();

            // Set launch complete only if folders were loaded
            if (!newFolderItems.isEmpty()) {
                setFirstAppLaunchCompleted();
            }
        });
        });
    }

    // Loads the contents of a selected folder
    private void loadFolderContents(Uri uri) {
        if (!folderHistory.isEmpty() && folderHistory.peek().equals(uri)) {
            folderItems.clear();
        } else {
            folderHistory.push(uri);
        }

        isNavigating = true;
        folderItems.clear();

        DocumentFile directory = DocumentFile.fromTreeUri(requireContext(), uri);
        if (directory != null && directory.isDirectory()) {
            for (DocumentFile file : directory.listFiles()) {
                folderItems.add(new Folder(file.getName(), file.getUri().toString(), file.isDirectory()));
            }

            binding.tvLibrary.setVisibility(View.GONE);
            setToolbarBackButtonEnabled(true);
            updateToolbarTitle(directory.getName());
        } else {
            Toast.makeText(requireContext(), "Unable to open folder", Toast.LENGTH_SHORT).show();
        }

        binding.fab.setVisibility(View.GONE);
        libraryFolderAdapter.notifyDataSetChanged();

    }

    // Resets the view back to the folder list
    private void resetToFolderList() {
        isNavigating = false;
        folderHistory.clear();
        folderItems.clear();
        loadSavedFolders();

        setToolbarBackButtonEnabled(false);
        binding.tvLibrary.setVisibility(View.VISIBLE);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setupHamburgerMenu();
        }

        binding.fab.setVisibility(View.VISIBLE);
        libraryFolderAdapter.notifyDataSetChanged();
    }

    // Open CBR,CBZ and PDF file
    private void openComicBook(String filePath) {
        Intent intent = new Intent(requireContext(), ComicViewer.class);
        intent.putExtra("comicPath", filePath);
        startActivity(intent);
    }

    // Handles folder and file clicks
    private void onFolderClicked(Folder item) {
        //  Ignore normal clicks when an item is selected (selection mode)
        if (isItemSelected) {
            // Optional: clear selection if clicked again
            selectedFolder = null;
            isItemSelected = false;
            libraryFolderAdapter.setSelectedFolder(null);
            requireActivity().invalidateMenu(); // hide delete action
            return;
        }

        //  Continue normal navigation if not in selection mode
        if (item.isFolder()) {
            loadFolderContents(Uri.parse(item.getPath())); // Navigate into folder
        } else {
            String filePath = item.getPath();
            if (filePath.endsWith(".cbr") || filePath.endsWith(".cbz") || filePath.endsWith(".pdf")) {
                openComicBook(filePath);
            } else {
                Toast.makeText(getContext(), "Unsupported file type: " + filePath, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Handles folder long-click for selection
    private void onFolderLongClicked(Folder item, View itemView) {
        //  Do not allow selection mode while navigating inside subfolders
        if (isNavigating) return;

        //  Toggle selection
        if (selectedFolder != null && selectedFolder == item) {
            selectedFolder = null;
            isItemSelected = false;
        } else {
            selectedFolder = item;
            isItemSelected = true;
        }

        libraryFolderAdapter.setSelectedFolder(selectedFolder);
        requireActivity().invalidateMenu(); // update delete menu
    }

    public void showRemoveFolderDialog() {
        if (selectedFolder != null) {
            RemoveFolderDialog dialog = RemoveFolderDialog.newInstances(Uri.parse(selectedFolder.getPath()));

            dialog.setOnFolderRemoveListener(folderPath -> executorService.execute(() -> {
                Folder entity = folderItemDao.getFolderByPath(folderPath);
                if (entity != null) {
                    // Step 1: Delete the folder from the library DB
                    folderItemDao.delete(entity);

                    // Step 2: Delete comics from the deleted folder
                    ComicDatabase db = ComicDatabase.getInstance(requireContext());
                    db.comicDao().deleteComicsByFolderPath(folderPath);

                    // Step 3: Remove all "removed" comic paths under this folder
                    SharedPreferences prefs = requireContext().getSharedPreferences("removed_comics", Context.MODE_PRIVATE);
                    Set<String> removedPaths = new HashSet<>(prefs.getStringSet("removed_paths", new HashSet<>()));
                    removedPaths.removeIf(path -> path.startsWith(folderPath)); // Clear related comic removals
                    prefs.edit().putStringSet("removed_paths", removedPaths).apply();

                    // Step 4: Update UI and ViewModel
                    requireActivity().runOnUiThread(() -> {
                        folderItems.removeIf(item -> item.getPath().equals(folderPath));
                        libraryFolderAdapter.notifyDataSetChanged();
                        FolderUtils.saveFolders(requireContext(), folderItems);
                        libraryViewModel.setFolders(new ArrayList<>(folderItems)); //  Triggers ComicFragment to reload
                        libraryViewModel.notifyFolderRemoved(); // Let ComicFragment know about the removal

                        // Clear selection state
                        selectedFolder = null;
                        isItemSelected = false;
                        requireActivity().invalidateOptionsMenu(); // Refresh the menu to hide delete

                        updateEmptyMessageVisibility();
                    });

                    Log.d("LibraryFragment", "Folder removed from database and removed comic paths cleared: " + folderPath);
                } else {
                    Log.e("LibraryFragment", "Folder not found in database: " + folderPath);
                }
            }));
            dialog.show(getChildFragmentManager(), "RemoveFolderDialog");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (!folderHistory.isEmpty()) {
                            folderHistory.pop();

                            if (!folderHistory.isEmpty()) {
                                Uri parentUri = folderHistory.peek();
                                folderItems.clear();
                                loadFolderContents(parentUri);
                            } else {
                                resetToFolderList();
                            }
                        } else {
                            requireActivity().finish();
                        }
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow(); // Cleanup resources
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}