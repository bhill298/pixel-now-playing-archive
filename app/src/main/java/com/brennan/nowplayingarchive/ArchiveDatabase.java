package com.brennan.nowplayingarchive;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class ArchiveDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "now_playing_archive.db";
    private static final int DB_VERSION = 1;

    ArchiveDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE songs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "dedup_key TEXT NOT NULL UNIQUE," +
                "recognized_at TEXT NOT NULL," +
                "date_label TEXT," +
                "recognized_time TEXT," +
                "title TEXT NOT NULL," +
                "artist TEXT NOT NULL DEFAULT ''," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "source_serial TEXT)");
        db.execSQL("CREATE INDEX songs_recognized_at ON songs(recognized_at DESC)");
        db.execSQL("CREATE INDEX songs_title_artist ON songs(title, artist)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 has no migrations.
    }

    int importJson(InputStream input) throws Exception {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[16384];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                text.append(buffer, 0, count);
            }
        }

        JSONObject root = new JSONObject(text.toString());
        JSONArray entries = root.getJSONArray("entries");
        String sourceSerial = root.optJSONObject("source") == null
                ? "" : root.optJSONObject("source").optString("serial", "");
        SQLiteDatabase db = getWritableDatabase();
        int inserted = 0;
        db.beginTransaction();
        try {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.getJSONObject(i);
                String recognizedAt = item.optString("recognized_at_local", "");
                String title = item.optString("title", "").trim();
                String artist = item.optString("artist", "").trim();
                if (recognizedAt.isEmpty() || title.isEmpty()) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("dedup_key", digest(recognizedAt + "\u001f" + title + "\u001f" + artist));
                values.put("recognized_at", recognizedAt);
                values.put("date_label", item.optString("date_label", ""));
                values.put("recognized_time", item.optString("time", ""));
                values.put("title", title);
                values.put("artist", artist);
                values.put("source_serial", sourceSerial);
                boolean favorite = item.optBoolean("favorite", false);
                values.put("favorite", favorite ? 1 : 0);
                long rowId = db.insertWithOnConflict("songs", null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (rowId != -1) {
                    inserted++;
                } else if (favorite) {
                    ContentValues favoriteValue = new ContentValues();
                    favoriteValue.put("favorite", 1);
                    db.update("songs", favoriteValue, "dedup_key=?",
                            new String[]{values.getAsString("dedup_key")});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return inserted;
    }

    List<Song> query(String search, String timeFilter, boolean favoritesOnly) {
        return query(search, timeFilter, "Any day", "", "", "", favoritesOnly);
    }

    List<Song> query(String search, String timeFilter, String dayFilter,
                     String specificDay, String fromTime, String toTime,
                     boolean favoritesOnly) {
        List<String> clauses = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        if (favoritesOnly) {
            clauses.add("favorite=1");
        }
        if (search != null && !search.trim().isEmpty()) {
            clauses.add("(title LIKE ? ESCAPE '\\' OR artist LIKE ? ESCAPE '\\')");
            String escaped = search.trim().replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_");
            arguments.add("%" + escaped + "%");
            arguments.add("%" + escaped + "%");
        }
        if ("Morning".equals(timeFilter)) {
            clauses.add("recognized_time >= '05:00' AND recognized_time < '12:00'");
        } else if ("Afternoon".equals(timeFilter)) {
            clauses.add("recognized_time >= '12:00' AND recognized_time < '17:00'");
        } else if ("Evening".equals(timeFilter)) {
            clauses.add("recognized_time >= '17:00' AND recognized_time < '21:00'");
        } else if ("Night time".equals(timeFilter)) {
            clauses.add("(recognized_time >= '21:00' OR recognized_time < '05:00')");
        } else if ("Specific time".equals(timeFilter)) {
            if (!fromTime.isEmpty() && !toTime.isEmpty()) {
                if (fromTime.compareTo(toTime) <= 0) {
                    clauses.add("recognized_time >= ? AND recognized_time <= ?");
                } else {
                    clauses.add("(recognized_time >= ? OR recognized_time <= ?)");
                }
                arguments.add(fromTime);
                arguments.add(toTime);
            } else if (!fromTime.isEmpty()) {
                clauses.add("recognized_time >= ?");
                arguments.add(fromTime);
            } else if (!toTime.isEmpty()) {
                clauses.add("recognized_time <= ?");
                arguments.add(toTime);
            }
        }
        LocalDateTime cutoff = null;
        if ("Last day".equals(dayFilter)) {
            cutoff = LocalDateTime.now().minusDays(1);
        } else if ("Last 7 days".equals(dayFilter)) {
            cutoff = LocalDateTime.now().minusDays(7);
        } else if ("Last 30 days".equals(dayFilter)) {
            cutoff = LocalDateTime.now().minusDays(30);
        }
        if (cutoff != null) {
            clauses.add("recognized_at >= ?");
            arguments.add(cutoff.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
        } else if ("Specific day".equals(dayFilter) && !specificDay.isEmpty()) {
            LocalDate date = LocalDate.parse(specificDay);
            clauses.add("recognized_at >= ? AND recognized_at < ?");
            arguments.add(date + "T00:00");
            arguments.add(date.plusDays(1) + "T00:00");
        }
        String selection = clauses.isEmpty() ? null : String.join(" AND ", clauses);
        String[] args = arguments.toArray(new String[0]);
        List<Song> songs = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "songs", null, selection, args, null, null,
                "recognized_at DESC, id DESC")) {
            while (cursor.moveToNext()) {
                Song song = new Song();
                song.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                song.recognizedAt = cursor.getString(cursor.getColumnIndexOrThrow("recognized_at"));
                song.dateLabel = cursor.getString(cursor.getColumnIndexOrThrow("date_label"));
                song.time = cursor.getString(cursor.getColumnIndexOrThrow("recognized_time"));
                song.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                song.artist = cursor.getString(cursor.getColumnIndexOrThrow("artist"));
                song.favorite = cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) != 0;
                songs.add(song);
            }
        }
        return songs;
    }

    void setFavorite(long id, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put("favorite", favorite ? 1 : 0);
        getWritableDatabase().update("songs", values, "id=?",
                new String[]{Long.toString(id)});
    }

    void delete(long id) {
        getWritableDatabase().delete("songs", "id=?", new String[]{Long.toString(id)});
    }

    void clear() {
        getWritableDatabase().delete("songs", null, null);
    }

    int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM songs", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    void exportJson(OutputStream output) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema_version", 1);
        root.put("complete", true);
        root.put("entry_count", count());
        JSONArray entries = new JSONArray();
        for (Song song : query("", "Any time", false)) {
            JSONObject item = new JSONObject();
            item.put("recognized_at_local", song.recognizedAt);
            item.put("date_label", song.dateLabel == null ? "" : song.dateLabel);
            item.put("time", song.time == null ? "" : song.time);
            item.put("title", song.title);
            item.put("artist", song.artist);
            item.put("favorite", song.favorite);
            entries.put(item);
        }
        root.put("entries", entries);
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(root.toString(2));
        }
    }

    private static String digest(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
