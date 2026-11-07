package com.echostep.app.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UnitCatalog {
    public static final class Grade {
        public final String id;
        public final String tagline;
        public final List<String> books;
        public final String scope;

        Grade(String id, String tagline, List<String> books, String scope) {
            this.id = id;
            this.tagline = tagline;
            this.books = books;
            this.scope = scope;
        }
    }

    public static final class Unit {
        public final String id;
        public final String title;
        public final String note;

        Unit(String id, String title, String note) {
            this.id = id;
            this.title = title;
            this.note = note;
        }
    }

    private final List<Grade> grades = new ArrayList<>();
    private final Map<String, List<Unit>> books = new LinkedHashMap<>();
    private final Map<String, Grade> gradeMap = new LinkedHashMap<>();

    private UnitCatalog() {
    }

    public static UnitCatalog load(Context context) {
        UnitCatalog catalog = new UnitCatalog();
        try {
            InputStream stream = context.getAssets().open("curriculum.json");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            stream.close();
            JSONObject root = new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
            JSONArray grades = root.getJSONArray("grades");
            for (int i = 0; i < grades.length(); i++) {
                JSONObject item = grades.getJSONObject(i);
                List<String> bookList = new ArrayList<>();
                JSONArray rawBooks = item.getJSONArray("books");
                for (int j = 0; j < rawBooks.length(); j++) {
                    bookList.add(rawBooks.getString(j));
                }
                Grade grade = new Grade(
                        item.getString("id"),
                        item.optString("tagline", ""),
                        bookList,
                        item.optString("scope", ""));
                catalog.grades.add(grade);
                catalog.gradeMap.put(grade.id, grade);
            }
            JSONObject rawBooks = root.getJSONObject("books");
            for (java.util.Iterator<String> it = rawBooks.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject rawBook = rawBooks.getJSONObject(key);
                JSONArray units = rawBook.getJSONArray("units");
                List<Unit> list = new ArrayList<>();
                for (int i = 0; i < units.length(); i++) {
                    JSONObject unit = units.getJSONObject(i);
                    list.add(new Unit(unit.optString("id", ""), unit.getString("title"), unit.optString("note", "")));
                }
                catalog.books.put(key, list);
            }
        } catch (Exception ignored) {
            // The UI still works with the fallback grade names.
        }
        return catalog;
    }

    public List<Grade> grades() {
        return grades;
    }

    public Grade grade(String id) {
        return gradeMap.get(id);
    }

    public List<String> books(String grade) {
        Grade item = gradeMap.get(grade);
        return item == null ? new ArrayList<>() : item.books;
    }

    public List<Unit> units(String grade, String book) {
        List<Unit> list = books.get(grade + book);
        return list == null ? new ArrayList<>() : list;
    }

    public String scope(String grade) {
        Grade item = gradeMap.get(grade);
        return item == null ? "" : item.scope;
    }

    public String tagline(String grade) {
        Grade item = gradeMap.get(grade);
        return item == null ? "" : item.tagline;
    }
}
