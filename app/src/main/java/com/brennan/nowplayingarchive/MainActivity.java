package com.brennan.nowplayingarchive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedDispatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 1001;
    private static final int REQUEST_EXPORT = 1002;

    private ArchiveDatabase database;
    private EditText searchField;
    private Button dayFilterButton;
    private Button timeFilterButton;
    private HistoryAdapter historyAdapter;
    private boolean favoritesOnly;
    private boolean searchMode;
    private boolean settingsMode;
    private String dayFilter = "Any day";
    private String specificDay = "";
    private String timeFilter = "Any time";
    private String fromTime = "";
    private String toTime = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = new ArchiveDatabase(this);
        getWindow().setStatusBarColor(getColor(R.color.np_background));
        getWindow().setNavigationBarColor(getColor(R.color.np_background));
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        showHistory(false);
    }

    private void showHistory(boolean favorites) {
        searchMode = false;
        settingsMode = false;
        favoritesOnly = favorites;
        dayFilter = "Any day";
        specificDay = "";
        timeFilter = "Any time";
        fromTime = "";
        toTime = "";
        LinearLayout page = basePage();
        page.addView(toolbar(favorites ? "Favorites" : "History", false));

        if (!favorites) {
            LinearLayout searchRow = new LinearLayout(this);
            searchRow.setGravity(Gravity.CENTER_VERTICAL);
            searchRow.setPadding(dp(24), dp(8), dp(24), dp(8));
            searchField = new EditText(this);
            searchField.setHint("Search history");
            searchField.setTextSize(20);
            searchField.setSingleLine(true);
            searchField.setTextColor(getColor(R.color.np_primary));
            searchField.setHintTextColor(getColor(R.color.np_secondary));
            searchField.setBackgroundResource(R.drawable.rounded_surface);
            searchField.setPadding(dp(24), 0, dp(16), 0);
            searchField.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_search, 0, 0, 0);
            searchField.setCompoundDrawablePadding(dp(12));
            searchField.setFocusable(false);
            searchField.setOnClickListener(view -> showSearch());
            searchRow.addView(searchField, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
            page.addView(searchRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        } else {
            searchField = null;
        }

        FrameLayout content = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(112));
        List<Song> songs = database.query("", timeFilter, favoritesOnly);
        historyAdapter = new HistoryAdapter(this, songs, this::showSongMenu);
        list.setAdapter(historyAdapter);
        content.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (songs.isEmpty()) {
            TextView empty = bodyText(favorites
                    ? "Songs you favorite will appear here"
                    : "Import a Now Playing export to begin");
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        content.addView(bottomNavigation(favorites ? 1 : 0));
        page.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
    }

    private void showSearch() {
        searchMode = true;
        settingsMode = false;
        favoritesOnly = false;
        dayFilter = "Any day";
        specificDay = "";
        timeFilter = "Any time";
        fromTime = "";
        toTime = "";

        LinearLayout page = basePage();
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        searchBar.setBackgroundResource(R.drawable.rounded_surface);
        ImageButton back = iconButton(R.drawable.ic_arrow_back, "Close search");
        back.setOnClickListener(view -> showHistory(false));
        searchBar.addView(back, new LinearLayout.LayoutParams(dp(56), dp(56)));

        searchField = new EditText(this);
        searchField.setHint("Search history");
        searchField.setTextSize(20);
        searchField.setSingleLine(true);
        searchField.setTextColor(getColor(R.color.np_primary));
        searchField.setHintTextColor(getColor(R.color.np_secondary));
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchBar.addView(searchField, new LinearLayout.LayoutParams(0, dp(64), 1));
        ImageButton clear = iconButton(R.drawable.ic_close, "Clear search");
        clear.setOnClickListener(view -> searchField.setText(""));
        searchBar.addView(clear, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout.LayoutParams searchBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        searchBarParams.setMargins(dp(16), dp(8), dp(16), 0);
        page.addView(searchBar, searchBarParams);

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(24), dp(10), dp(24), dp(10));
        dayFilterButton = flatButton("Day ▾");
        timeFilterButton = flatButton("Time ▾");
        dayFilterButton.setOnClickListener(this::showDayFilter);
        timeFilterButton.setOnClickListener(this::showTimeFilter);
        filters.addView(dayFilterButton, new LinearLayout.LayoutParams(dp(116), dp(52)));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(dp(116), dp(52));
        timeParams.setMarginStart(dp(10));
        filters.addView(timeFilterButton, timeParams);
        page.addView(filters, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

        FrameLayout content = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(112));
        historyAdapter = new HistoryAdapter(this,
                database.query("", timeFilter, dayFilter, specificDay,
                        fromTime, toTime, false), this::showSongMenu);
        list.setAdapter(historyAdapter);
        content.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(bottomNavigation(0));
        page.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchField.requestFocus();
        searchField.post(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT));
    }

    private LinearLayout toolbar(String title, boolean showBack) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), dp(8), dp(20), 0);
        if (showBack) {
            ImageButton back = iconButton(R.drawable.ic_arrow_back, "Navigate up");
            back.setOnClickListener(view -> showHistory(false));
            bar.addView(back, new LinearLayout.LayoutParams(dp(56), dp(56)));
        }
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(getColor(R.color.np_primary));
        heading.setTextSize(32);
        heading.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, dp(68), 1);
        if (showBack) headingParams.setMarginStart(dp(8));
        bar.addView(heading, headingParams);
        if (!showBack) {
            ImageButton settings = iconButton(R.drawable.ic_settings, "Settings");
            settings.setBackgroundResource(R.drawable.rounded_surface);
            settings.setOnClickListener(view -> showSettings());
            bar.addView(settings, new LinearLayout.LayoutParams(dp(60), dp(60)));
        }
        return bar;
    }

    private View bottomNavigation(int selected) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        nav.setBackgroundResource(R.drawable.rounded_surface);
        int[] icons = {R.drawable.ic_history, R.drawable.ic_favorite};
        String[] labels = {"History", "Favorites"};
        for (int i = 0; i < icons.length; i++) {
            ImageButton button = iconButton(icons[i], labels[i]);
            boolean isSelected = i == selected;
            if (isSelected) button.setBackgroundResource(R.drawable.rounded_selected);
            button.setColorFilter(getColor(isSelected
                    ? R.color.np_on_nav_selected : R.color.np_secondary));
            final int tab = i;
            button.setOnClickListener(view -> showHistory(tab == 1));
            nav.addView(button, new LinearLayout.LayoutParams(dp(68), dp(56)));
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(152), dp(68), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(20);
        nav.setLayoutParams(params);
        return nav;
    }

    private void showSongMenu(View anchor, Song song) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(song.favorite ? "Remove from favorites" : "Add to favorites");
        menu.getMenu().add("Share");
        menu.getMenu().add("Remove from history");
        menu.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if (action.contains("favorites")) {
                database.setFavorite(song.id, !song.favorite);
                refresh();
            } else if (action.equals("Share")) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT,
                        song.title + (song.artist.isEmpty() ? "" : " — " + song.artist));
                startActivity(Intent.createChooser(share, "Share song"));
            } else if (action.equals("Remove from history")) {
                new AlertDialog.Builder(this)
                        .setMessage("Remove this song from the archive?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (dialog, which) -> {
                            database.delete(song.id);
                            if (searchMode) refresh();
                            else showHistory(favoritesOnly);
                        }).show();
            }
            return true;
        });
        menu.show();
    }

    private void showDayFilter(View anchor) {
        String[] options = {"Last day", "Last 7 days", "Last 30 days", "Specific day"};
        showFilterPopup(anchor, options, dayFilter, option -> {
            if (option.equals(dayFilter)) {
                dayFilter = "Any day";
                specificDay = "";
                dayFilterButton.setText("Day ▾");
                refresh();
                return;
            }
            if ("Specific day".equals(option)) {
                LocalDate initial = specificDay.isEmpty()
                        ? LocalDate.now() : LocalDate.parse(specificDay);
                new DatePickerDialog(this, (picker, year, month, day) -> {
                    LocalDate selected = LocalDate.of(year, month + 1, day);
                    dayFilter = "Specific day";
                    specificDay = selected.toString();
                    dayFilterButton.setText(selected.format(
                            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) + " ▾");
                    refresh();
                }, initial.getYear(), initial.getMonthValue() - 1,
                        initial.getDayOfMonth()).show();
            } else {
                dayFilter = option;
                specificDay = "";
                dayFilterButton.setText(option + " ▾");
                refresh();
            }
        });
    }

    private void showTimeFilter(View anchor) {
        String fromLabel = fromTime.isEmpty() ? "From" : "From  " + fromTime;
        String toLabel = toTime.isEmpty() ? "To" : "To  " + toTime;
        String[] options = {"Morning", "Afternoon", "Evening", "Night time",
                "Specific time", fromLabel, toLabel};
        showFilterPopup(anchor, options, timeFilter, option -> {
            if (option.startsWith("From")) {
                pickSpecificTime(true);
            } else if (option.startsWith("To")) {
                pickSpecificTime(false);
            } else if ("Specific time".equals(option)) {
                if ("Specific time".equals(timeFilter)) {
                    clearTimeFilter();
                    return;
                }
                timeFilter = "Specific time";
                pickSpecificTime(true);
            } else {
                if (option.equals(timeFilter)) {
                    clearTimeFilter();
                    return;
                }
                timeFilter = option;
                fromTime = "";
                toTime = "";
                timeFilterButton.setText(option + " ▾");
                refresh();
            }
        });
    }

    private void clearTimeFilter() {
        timeFilter = "Any time";
        fromTime = "";
        toTime = "";
        timeFilterButton.setText("Time ▾");
        refresh();
    }

    private void pickSpecificTime(boolean from) {
        String current = from ? fromTime : toTime;
        LocalTime initial = current.isEmpty() ? (from ? LocalTime.of(9, 0)
                : LocalTime.of(17, 0)) : LocalTime.parse(current);
        new TimePickerDialog(this, (picker, hour, minute) -> {
            String selected = String.format(Locale.US, "%02d:%02d", hour, minute);
            if (from) fromTime = selected;
            else toTime = selected;
            timeFilter = "Specific time";
            String label;
            if (!fromTime.isEmpty() && !toTime.isEmpty()) label = fromTime + "–" + toTime;
            else if (!fromTime.isEmpty()) label = "From " + fromTime;
            else label = "To " + toTime;
            timeFilterButton.setText(label + " ▾");
            refresh();
        }, initial.getHour(), initial.getMinute(), true).show();
    }

    private void showFilterPopup(View anchor, String[] options, String selected,
                                 Consumer<String> listener) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setBackgroundResource(R.drawable.rounded_filter_popup);
        PopupWindow popup = new PopupWindow(menu, dp(252),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        for (String option : options) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(22), 0, dp(16), 0);
            boolean checked = option.equals(selected);
            if (checked) row.setBackgroundResource(R.drawable.rounded_filter_selected);

            TextView label = bodyText(option);
            label.setTextSize(18);
            label.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
            if (checked) {
                ImageView check = new ImageView(this);
                check.setImageResource(R.drawable.ic_check);
                check.setColorFilter(getColor(R.color.np_primary));
                check.setContentDescription("Selected");
                row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(28)));
            }
            row.setOnClickListener(view -> {
                popup.dismiss();
                listener.accept(option);
            });
            menu.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        }
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        popup.showAsDropDown(anchor, 0, dp(4));
    }

    private void refresh() {
        if (historyAdapter == null) return;
        String query = searchField == null ? "" : searchField.getText().toString();
        historyAdapter.setSongs(database.query(query, timeFilter, dayFilter,
                specificDay, fromTime, toTime, favoritesOnly));
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (searchMode || settingsMode) showHistory(false);
        else finishAfterTransition();
    }

    private void showSettings() {
        settingsMode = true;
        searchMode = false;
        LinearLayout page = basePage();
        page.addView(toolbar("Settings", true));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(24));
        content.addView(settingButton("Import history", "Merge a JSON export into this archive", view -> pickImport()));
        content.addView(settingButton("Export combined history", "Save all imported records as JSON", view -> pickExport()));
        content.addView(settingButton("Clear history", database.count() + " saved songs", view -> confirmClear()));
        TextView privacy = bodyText(
                "Your archive stays on this device. Imports are merged by timestamp, title, and artist. " +
                "No music data is sent over the network.");
        privacy.setPadding(dp(12), dp(40), dp(12), 0);
        content.addView(privacy);
        page.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
    }

    private View settingButton(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(24), dp(14), dp(24), dp(14));
        item.setBackgroundResource(R.drawable.rounded_surface);
        TextView heading = bodyText(title);
        heading.setTextSize(21);
        heading.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        TextView detail = bodyText(subtitle);
        detail.setTextSize(16);
        detail.setTextColor(getColor(R.color.np_secondary));
        item.addView(heading);
        item.addView(detail);
        item.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
        params.bottomMargin = dp(12);
        item.setLayoutParams(params);
        return item;
    }

    private void pickImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private void pickExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "now-playing-combined-export.json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            ProgressDialog progress = ProgressDialog.show(
                    this, "Importing history", "Merging songs…", true, false);
            new Thread(() -> {
                try {
                    int added;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        added = database.importJson(input);
                    }
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(this, "Imported " + added + " new songs",
                                Toast.LENGTH_LONG).show();
                        showHistory(false);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        showFileError(error);
                    });
                }
            }).start();
        } else if (requestCode == REQUEST_EXPORT) {
            ProgressDialog progress = ProgressDialog.show(
                    this, "Exporting history", "Writing combined archive…", true, false);
            new Thread(() -> {
                try {
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        database.exportJson(output);
                    }
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(this, "Combined export saved", Toast.LENGTH_LONG).show();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        showFileError(error);
                    });
                }
            }).start();
        }
    }

    private void showFileError(Exception error) {
        new AlertDialog.Builder(this)
                .setTitle("Could not process file")
                .setMessage(error.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This removes every imported song from this app.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    database.clear();
                    showHistory(false);
                }).show();
    }

    private LinearLayout basePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(getColor(R.color.np_background));
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.requestApplyInsets();
        return page;
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setContentDescription(description);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        button.setPadding(dp(16), dp(16), dp(16), dp(16));
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        return button;
    }

    private Button flatButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(getColor(R.color.np_primary));
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.rounded_surface);
        return button;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.np_primary));
        view.setTextSize(18);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
