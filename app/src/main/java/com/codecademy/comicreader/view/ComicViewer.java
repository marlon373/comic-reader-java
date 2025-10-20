package com.codecademy.comicreader.view;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.codecademy.comicreader.R;
import com.codecademy.comicreader.databinding.ComicViewerBinding;
import com.codecademy.comicreader.dialog.InfoDialog;
import com.codecademy.comicreader.dialog.SelectPageDialog;
import com.codecademy.comicreader.theme.ThemeManager;
import com.codecademy.comicreader.utils.SystemUtil;
import com.codecademy.comicreader.view.sources.BitmapPageSource;
import com.codecademy.comicreader.view.sources.CBRPageSource;
import com.codecademy.comicreader.view.sources.CBZPageSource;
import com.codecademy.comicreader.view.sources.ComicPageSource;
import com.codecademy.comicreader.view.sources.PDFPageSource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

// add horizontal when auto-rotate android
public class ComicViewer extends AppCompatActivity {

    private ComicViewerBinding binding;
    private ViewPager2 viewPager;
    private PageRendererAdapter adapter;
    private ComicPageSource pageSource;
    private String comicPath;
    private Slider slider;
    private View comicViewProgress;
    private TextView tvPageNumber;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private ExecutorService executor;

    private static final String PREFS_NAME = "comicPrefs";
    private static final String KEY_THEME = "isNightMode";
    private static final String SCROLL_TYPE = "isScrolling";
    private static final String KEY_LAST_PAGE = "last_page_";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        executor = SystemUtil.createSmartExecutor(this); // or getSharedExecutor(this)

        ThemeManager.applyTheme(this);

        binding = ComicViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewPager = findViewById(R.id.viewPager_comic);
        slider = findViewById(R.id.slider_page_scroll);
        comicViewProgress = findViewById(R.id.progress_overlay);
        tvPageNumber = findViewById(R.id.tv_num_page);

        // Bottom sheet
        View bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setPeekHeight(125); // Height when collapsed
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);


        // Toggle sheet visibility on tap
        findViewById(R.id.container).setOnClickListener(v -> {
            int currentState = bottomSheetBehavior.getState();
            if (currentState == BottomSheetBehavior.STATE_EXPANDED || currentState == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        setupControls();
        updateScrollTypeIcon();
        updateScrollTypeOrientation();

        comicPath = getIntent().getStringExtra("comicPath");
        if (comicPath != null) {
            Uri uri = Uri.parse(comicPath);
            String ext = comicPath.substring(comicPath.lastIndexOf('.') + 1).toLowerCase();

            switch (ext) {
                case "cbz": loadCBZLazy(uri); break;
                case "cbr": loadCBRLazy(uri); break;
                case "pdf": loadPDFLazy(uri); break;
                default: showToast("Unsupported file type: " + ext); break;
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupControls() {
        binding.imgbtnFirstPage.setOnClickListener(v -> viewPager.setCurrentItem(0));
        binding.imgbtnLastPage.setOnClickListener(v -> viewPager.setCurrentItem(pageSource.getPageCount() - 1));
        binding.imgbtnNextPage.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() + 1));
        binding.imgbtnBackPage.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() - 1));

        binding.imgbtnInfo.setOnClickListener(v -> {
            String path = getIntent().getStringExtra("comicPath");
            if (path != null) {
                Uri uri = Uri.parse(path);
                showInfoDialog(uri);
            }
        });

        binding.imgbtnDarkMode.setOnClickListener(v -> toggleDayNightMode());
        updateDayNightIcon();

        binding.imgbtnScrollType.setOnClickListener(v -> {
            SharedPreferences pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean isScrolling = pref.getBoolean(SCROLL_TYPE, false);

            // Toggle and save
            pref.edit().putBoolean(SCROLL_TYPE, !isScrolling).apply();

            // Update icon and scroll direction
            updateScrollTypeOrientation();
            updateScrollTypeIcon();
        });

        binding.imgbtnSelectPage.setOnClickListener(v -> showSelectPageDialog());

        binding.viewPagerComic.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                Log.d("ComicViewer", "Switched to page: " + position);
                tvPageNumber.setText((position + 1) + " / " + pageSource.getPageCount());
                slider.setValue(position); // sync slider position
                if (adapter != null) adapter.resetZoomAt(position);

                // Save current page
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putInt(KEY_LAST_PAGE, position).apply();
            }
        });

        slider.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float touchX = event.getX();

                // Calculate thumb X position manually
                float sliderWidth = slider.getWidth() - slider.getPaddingLeft() - slider.getPaddingRight();
                float valueFraction = (slider.getValue() - slider.getValueFrom()) / (slider.getValueTo() - slider.getValueFrom());
                float thumbX = slider.getPaddingLeft() + valueFraction * sliderWidth;

                float tolerance = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 24, view.getResources().getDisplayMetrics()
                );

                if (Math.abs(touchX - thumbX) > tolerance) {
                    return true; //  Block tap
                }
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick(); // Required for accessibility
            }

            return false; // Allow drag behavior
        });

        binding.sliderPageScroll.addOnChangeListener((sliderView, value, fromUser) -> {
            if (fromUser) {
                int page = (int) value;
                viewPager.setCurrentItem(page, true);
            }
        });

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean volumeScroll = prefs.getBoolean("scroll_with_volume", false);

            if (volumeScroll) {
                int currentPage = viewPager.getCurrentItem();
                if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (currentPage > 0) viewPager.setCurrentItem(currentPage - 1);
                    return true; // Consume event
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (pageSource != null && currentPage < pageSource.getPageCount() - 1) viewPager.setCurrentItem(currentPage + 1);
                    return true; // Consume event
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void loadCBZLazy(Uri uri) {
        runOnUiThread(() -> comicViewProgress.setVisibility(View.VISIBLE));
        executor.execute(() -> {
            try {
                CBZPageSource source = new CBZPageSource(this, uri);
                runOnUiThread(() -> {
                    updateAdapter(source);
                    comicViewProgress.setVisibility(View.GONE);
                    preloadPages();
                });
            } catch (Exception e) {
                e.printStackTrace();
                showError("CBZ", e);
            }
        });
    }

    private void loadCBRLazy(Uri uri) {
        runOnUiThread(() -> comicViewProgress.setVisibility(View.VISIBLE));
        executor.execute(() -> {
            try {
                CBRPageSource source = new CBRPageSource(this, uri); // let it handle temp file
                runOnUiThread(() -> {
                    updateAdapter(source);
                    comicViewProgress.setVisibility(View.GONE);
                    preloadPages();
                });
            } catch (Exception e) {
                e.printStackTrace();
                showError("CBR", e);
            }
        });
    }

    private void loadPDFLazy(Uri uri) {
        runOnUiThread(() -> comicViewProgress.setVisibility(View.VISIBLE));
        executor.execute(() -> {
            try {
                PDFPageSource source = new PDFPageSource(this, uri); // stream-based version
                runOnUiThread(() -> {
                    updateAdapter(source);
                    comicViewProgress.setVisibility(View.GONE);
                    preloadPages();
                });
            } catch (Exception e) {
                e.printStackTrace();
                showError("PDF", e);
            }
        });
    }

    private void preloadPages() {
        executor.execute(() -> {
            for (int i = 0; i < 3 && i < pageSource.getPageCount(); i++) {
                pageSource.getPageBitmap(i); // triggers rendering
            }
        });
    }

    private void showError(String type, Exception e) {
        String message = "Error loading " + type + ": " + e.getMessage();
        Log.e("ComicViewer", message, e);
        runOnUiThread(() -> {
            showToast(message);
            comicViewProgress.setVisibility(View.GONE);
        });
    }

    private void updateAdapter(ComicPageSource pageSource) {
        this.pageSource = pageSource;
        adapter = new PageRendererAdapter(this, pageSource,executor);
        viewPager.setAdapter(adapter);

        // Slider setup
        slider.setValueFrom(0);
        slider.setValueTo(pageSource.getPageCount() - 1);
        slider.setStepSize(1);
        slider.setValue(0);
        slider.setTickVisible(false); // Not available, so use custom style
        tvPageNumber.setText("1 / " + pageSource.getPageCount());

        // Restore last page (if enabled)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean resume = prefs.getBoolean("open_last_file", false);
        if (resume) {
            int lastPage = prefs.getInt(KEY_LAST_PAGE, 0);
            if (lastPage >= 0 && lastPage < pageSource.getPageCount()) {
                viewPager.setCurrentItem(lastPage, true);
                slider.setValue(lastPage); // sync slider
            }
        }

        // Bottom sheet restore
        View bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheet.setEnabled(true);
        bottomSheet.setClickable(true);
        bottomSheet.post(() -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
    }

    private void showInfoDialog(Uri uri) {
        executor.execute(() -> {
            try {
                String name = "Unknown";
                String size = "Unknown";
                String date = "Unknown";

                // Get name and size using ContentResolver
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) name = cursor.getString(nameIndex);
                        if (sizeIndex >= 0) {
                            long bytes = cursor.getLong(sizeIndex);
                            size = formatFileSize(bytes);
                        }
                    }
                    cursor.close();
                }

                // Try to get last modified date
                DocumentFile docFile = DocumentFile.fromSingleUri(this, uri);
                if (docFile != null && docFile.lastModified() > 0) {
                    date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(new Date(docFile.lastModified()));
                }
                String finalName = name;
                String finalSize = size;
                String finalDate = date;
                String displayPath = uri.getPath();

                try {
                    String docId = DocumentsContract.getDocumentId(uri); // e.g., "primary:Comics/Batman.cbz"
                    String[] parts = docId.split(":");

                    if (parts.length == 2) {
                        String volume = parts[0];
                        String relativePath = parts[1];

                        if (volume.equals("primary")) {
                            displayPath = "/storage/emulated/0/" + relativePath;
                        } else {// SD card or USB OTG with volume ID like "E45B-17F1"
                            displayPath = "/storage/" + volume + "/" + relativePath;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                    // Fallback: use file name or URI as last resort
                    if (docFile != null && docFile.getName() != null) {
                        displayPath = docFile.getName(); // e.g., "Batman.cbz"
                    } else {
                        displayPath = uri.toString(); // fallback to full content:// URI
                    }
                }

                String finalPath = displayPath;

                runOnUiThread(() -> {
                    InfoDialog dialog = InfoDialog.newInstance(finalName, finalPath, finalDate, finalSize);
                    dialog.show(getSupportFragmentManager(), "InfoDialog");
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showToast("Failed to show info dialog: " + e.getMessage()));
            }
        });
    }

    // Formats file sizes for display
    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes <= 0) return "0 KB";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeInBytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(sizeInBytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private void toggleDayNightMode() {
        ThemeManager.toggleTheme(this);
    }

    private void updateDayNightIcon() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isNightMode = prefs.getBoolean(KEY_THEME, false); // Get the current mode

        // Change the icon based on the current mode
        if (isNightMode) {
            binding.imgbtnDarkMode.setImageResource(R.drawable.ic_bottom_menu_day);  // Night mode icon, change to day

        } else {
            binding.imgbtnDarkMode.setImageResource(R.drawable.ic_bottom_menu_night);  // Day mode icon, change to night
        }
    }


    private void updateScrollTypeOrientation() {
        SharedPreferences pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isScrolling = pref.getBoolean(SCROLL_TYPE, false);

        if (isScrolling) {
            viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        } else {
            viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        }
    }

    private void updateScrollTypeIcon() {
        SharedPreferences pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isScrolling = pref.getBoolean(SCROLL_TYPE, false);

        if (isScrolling) {
            binding.imgbtnScrollType.setImageResource(R.drawable.ic_bottom_menu_vertical);
        } else {
            binding.imgbtnScrollType.setImageResource(R.drawable.ic_bottom_menu_horizontal);
        }
    }


    private void showSelectPageDialog() {
        SelectPageDialog.newInstance(pageNumber -> {
            if (pageSource != null && pageNumber >= 0 && pageNumber < pageSource.getPageCount()) {
                viewPager.setCurrentItem(pageNumber);
            } else {
                showToast("Page number out of range");
            }
        }).show(getSupportFragmentManager(), "select_page");
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (adapter != null) adapter.shutdownExecutor(); // clears cache only

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow(); // ComicViewer owns it
        }

        if (pageSource instanceof PDFPageSource) {
            ((PDFPageSource) pageSource).close();
        } else if (pageSource instanceof CBRPageSource) {
            ((CBRPageSource) pageSource).close();
        } else if (pageSource instanceof BitmapPageSource source) {
            source.clear();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Dismiss SelectPageDialog if visible
        Fragment selectPageDialog = getSupportFragmentManager().findFragmentByTag("select_page");
        if (selectPageDialog instanceof DialogFragment) {
            ((DialogFragment) selectPageDialog).dismissAllowingStateLoss();
        }

        // Dismiss InfoDialog if visible
        Fragment infoDialog = getSupportFragmentManager().findFragmentByTag("InfoDialog");
        if (infoDialog instanceof DialogFragment) {
            ((DialogFragment) infoDialog).dismissAllowingStateLoss();
        }
    }

}
