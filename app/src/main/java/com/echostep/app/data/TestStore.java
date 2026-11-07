package com.echostep.app.data;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TestStore {
    public static final class Item {
        public String id;
        public String title;
        public String grade;
        public String book;
        public String unitTitle;
        public int questionCount;
        public long createdAt;
        public String model;
    }

    private final File directory;

    public TestStore(Context context) {
        directory = new File(context.getFilesDir(), "tests");
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public File directory() {
        return directory;
    }

    public void save(ListeningTest test) throws Exception {
        if (test.id == null || test.id.isEmpty()) {
            test.id = ListeningTest.newId();
        }
        File file = new File(directory, test.id + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(test.toJson().toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    public ListeningTest get(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        File file = new File(directory, id + ".json");
        if (!file.exists()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(readFile(file));
            return ListeningTest.fromJson(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<Item> list() {
        List<Item> items = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return items;
        }
        for (File file : files) {
            try {
                JSONObject json = new JSONObject(readFile(file));
                Item item = new Item();
                item.id = json.optString("id", file.getName().replace(".json", ""));
                item.title = json.optString("title", "未命名试卷");
                item.grade = json.optString("grade");
                item.book = json.optString("book");
                item.unitTitle = json.optString("unit_title");
                item.questionCount = json.optInt("question_count");
                item.createdAt = json.optLong("created_at", file.lastModified());
                item.model = json.optString("model");
                items.add(item);
            } catch (Exception ignored) {
            }
        }
        items.sort(Comparator.comparingLong((Item item) -> item.createdAt).reversed());
        return items;
    }

    public boolean delete(String id) {
        return new File(directory, id + ".json").delete();
    }

    private static String readFile(File file) throws Exception {
        byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
        return new String(data, StandardCharsets.UTF_8);
    }
}
