package com.brennan.nowplayingarchive;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HistoryAdapter extends BaseAdapter {
    interface SongMenuListener {
        void onMenu(View anchor, Song song);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SONG = 1;

    private final Context context;
    private final SongMenuListener listener;
    private final boolean groupByDate;
    private final List<Object> rows = new ArrayList<>();

    HistoryAdapter(Context context, List<Song> songs, boolean groupByDate,
                   SongMenuListener listener) {
        this.context = context;
        this.groupByDate = groupByDate;
        this.listener = listener;
        setSongs(songs);
    }

    void setSongs(List<Song> songs) {
        rows.clear();
        if (!groupByDate) {
            rows.addAll(songs);
            notifyDataSetChanged();
            return;
        }
        LocalDate previous = null;
        for (Song song : songs) {
            LocalDate date = parseDate(song.recognizedAt);
            if (date != null && !date.equals(previous)) {
                rows.add(formatHeader(date));
                previous = date;
            }
            rows.add(song);
        }
        notifyDataSetChanged();
    }

    @Override public int getCount() { return rows.size(); }
    @Override public Object getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) {
        Object item = rows.get(position);
        return item instanceof Song ? ((Song) item).id : -position - 1L;
    }
    @Override public int getViewTypeCount() { return 2; }
    @Override public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_SONG;
    }
    @Override public boolean isEnabled(int position) { return getItemViewType(position) == TYPE_SONG; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (getItemViewType(position) == TYPE_HEADER) {
            TextView header = convertView instanceof TextView ? (TextView) convertView : makeHeader();
            header.setText((String) rows.get(position));
            return header;
        }
        Song song = (Song) rows.get(position);
        SongHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof SongHolder)) {
            convertView = makeSongRow();
        }
        holder = (SongHolder) convertView.getTag();
        holder.title.setText(song.title);
        String when = groupByDate ? song.time
                : formatSearchTimestamp(song.recognizedAt, song.time);
        String subtitle = song.artist == null || song.artist.isEmpty()
                ? when : song.artist + " • " + when;
        holder.subtitle.setText(subtitle);
        holder.menu.setContentDescription(song.title + ". Media overflow button");
        holder.menu.setOnClickListener(view -> listener.onMenu(view, song));
        return convertView;
    }

    private TextView makeHeader() {
        TextView view = new TextView(context);
        view.setTextColor(context.getColor(R.color.np_secondary));
        view.setTextSize(18);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.BOTTOM);
        view.setPadding(dp(24), dp(8), dp(20), dp(10));
        view.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        return view;
    }

    private View makeSongRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(5), dp(16), dp(5));
        row.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(82)));

        FrameLayout artFrame = new FrameLayout(context);
        artFrame.setBackgroundResource(R.drawable.rounded_placeholder);
        ImageView art = new ImageView(context);
        art.setImageResource(R.drawable.ic_album_note);
        art.setContentDescription("Placeholder album art");
        art.setPadding(dp(11), dp(11), dp(11), dp(11));
        artFrame.addView(art, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(artFrame, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(16), 0, dp(6), 0);
        TextView title = new TextView(context);
        title.setTextColor(context.getColor(R.color.np_primary));
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView subtitle = new TextView(context);
        subtitle.setTextColor(context.getColor(R.color.np_secondary));
        subtitle.setTextSize(15);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));
        labels.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(56), 1));

        ImageButton menu = new ImageButton(context);
        menu.setImageResource(R.drawable.ic_more_vert);
        menu.setScaleType(ImageView.ScaleType.CENTER);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        row.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(48)));

        row.setTag(new SongHolder(title, subtitle, menu));
        return row;
    }

    private String formatHeader(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Today";
        if (date.equals(today.minusDays(1))) return "Yesterday";
        String pattern = date.getYear() == today.getYear()
                ? "EEEE, MMMM d" : "EEEE, MMMM d, yyyy";
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
    }

    private String formatSearchTimestamp(String recognizedAt, String time) {
        LocalDate date = parseDate(recognizedAt);
        if (date == null) return time;
        LocalDate today = LocalDate.now();
        String day;
        if (date.equals(today)) day = "Today";
        else if (date.equals(today.minusDays(1))) day = "Yesterday";
        else if (date.getYear() == today.getYear()) {
            day = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()));
        } else {
            day = date.format(DateTimeFormatter.ofPattern(
                    "MMM d, yyyy", Locale.getDefault()));
        }
        return day + ", " + time;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class SongHolder {
        final TextView title;
        final TextView subtitle;
        final ImageButton menu;
        SongHolder(TextView title, TextView subtitle, ImageButton menu) {
            this.title = title;
            this.subtitle = subtitle;
            this.menu = menu;
        }
    }
}
