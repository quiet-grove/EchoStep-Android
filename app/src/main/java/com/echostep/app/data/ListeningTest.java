package com.echostep.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ListeningTest {
    public String id;
    public String title;
    public String topic;
    public String grade;
    public String book;
    public String unitId;
    public String unitTitle;
    public String level;
    public String template = "A";
    public int totalScore = 30;
    public String model;
    public long createdAt;
    public int questionCount;
    public int sectionCount;
    public int totalWords;
    public List<Section> sections = new ArrayList<>();
    public List<Source> sources = new ArrayList<>();
    public String searchWarning;

    public static final class Source {
        public String title;
        public String url;
        public String siteName;

        public JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("title", title);
            json.put("url", url);
            json.put("site_name", siteName);
            return json;
        }

        public static Source fromJson(JSONObject json) {
            Source source = new Source();
            source.title = json.optString("title");
            source.url = json.optString("url");
            source.siteName = json.optString("site_name");
            return source;
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public List<Question> allQuestions() {
        List<Question> list = new ArrayList<>();
        for (Section section : sections) {
            list.addAll(section.questions);
        }
        return list;
    }

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("topic", topic);
        json.put("grade", grade);
        json.put("book", book);
        json.put("unit_id", unitId);
        json.put("unit_title", unitTitle);
        json.put("level", level);
        json.put("template", template);
        json.put("total_score", totalScore);
        json.put("model", model);
        json.put("created_at", createdAt);
        json.put("question_count", questionCount);
        json.put("section_count", sectionCount);
        json.put("total_words", totalWords);
        json.put("search_warning", searchWarning);
        JSONArray sectionArray = new JSONArray();
        for (Section section : sections) {
            sectionArray.put(section.toJson());
        }
        json.put("sections", sectionArray);
        JSONArray sourceArray = new JSONArray();
        for (Source source : sources) {
            sourceArray.put(source.toJson());
        }
        json.put("search_sources", sourceArray);
        return json;
    }

    public static ListeningTest fromJson(JSONObject json) {
        ListeningTest test = new ListeningTest();
        test.id = json.optString("id");
        test.title = json.optString("title");
        test.topic = json.optString("topic");
        test.grade = json.optString("grade");
        test.book = json.optString("book");
        test.unitId = json.optString("unit_id");
        test.unitTitle = json.optString("unit_title");
        test.level = json.optString("level");
        test.template = json.optString("template", "A");
        test.totalScore = json.optInt("total_score", 30);
        test.model = json.optString("model");
        test.createdAt = json.optLong("created_at");
        test.questionCount = json.optInt("question_count");
        test.sectionCount = json.optInt("section_count");
        test.totalWords = json.optInt("total_words");
        test.searchWarning = json.optString("search_warning");
        JSONArray sectionArray = json.optJSONArray("sections");
        if (sectionArray != null) {
            for (int i = 0; i < sectionArray.length(); i++) {
                test.sections.add(Section.fromJson(sectionArray.optJSONObject(i)));
            }
        }
        JSONArray sourceArray = json.optJSONArray("search_sources");
        if (sourceArray != null) {
            for (int i = 0; i < sourceArray.length(); i++) {
                test.sources.add(Source.fromJson(sourceArray.optJSONObject(i)));
            }
        }
        return test;
    }
}
