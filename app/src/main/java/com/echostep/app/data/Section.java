package com.echostep.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Section {
    public static final class TableRow {
        public int number;
        public String label = "";
        public String prefix = "";
        public String suffix = "";
    }

    public String id;
    public int index;
    public String type = "";
    public String instruction;
    /** Legacy single audio text; new papers use materials. */
    public String audioText;
    public List<String> materials = new ArrayList<>();
    public String tableTitle = "";
    public List<TableRow> tableRows = new ArrayList<>();
    public List<Question> questions = new ArrayList<>();

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("index", index);
        json.put("type", type);
        json.put("instruction", instruction);
        JSONArray materialArray = new JSONArray();
        for (String material : materials) materialArray.put(material);
        json.put("materials", materialArray);
        JSONObject table = new JSONObject();
        table.put("title", tableTitle);
        JSONArray rows = new JSONArray();
        for (TableRow row : tableRows) {
            JSONObject item = new JSONObject();
            item.put("number", row.number);
            item.put("label", row.label);
            item.put("prefix", row.prefix);
            item.put("suffix", row.suffix);
            rows.put(item);
        }
        table.put("rows", rows);
        json.put("table", table);
        JSONArray array = new JSONArray();
        for (Question q : questions) array.put(q.toJson());
        json.put("questions", array);
        return json;
    }

    public static Section fromJson(JSONObject json) {
        Section s = new Section();
        s.id = json.optString("id");
        s.index = json.optInt("index");
        s.type = json.optString("type", "");
        s.instruction = json.optString("instruction");
        s.audioText = json.optString("audio_text");
        JSONArray materialArray = json.optJSONArray("materials");
        if (materialArray != null) {
            for (int i = 0; i < materialArray.length(); i++) {
                s.materials.add(materialArray.optString(i));
            }
        }
        if (s.materials.isEmpty() && s.audioText != null && !s.audioText.isEmpty()) {
            s.materials.add(s.audioText);
        }
        JSONObject table = json.optJSONObject("table");
        if (table != null) {
            s.tableTitle = table.optString("title", "");
            JSONArray rows = table.optJSONArray("rows");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject item = rows.optJSONObject(i);
                    TableRow row = new TableRow();
                    row.number = item.optInt("number");
                    row.label = item.optString("label", "");
                    row.prefix = item.optString("prefix", "");
                    row.suffix = item.optString("suffix", "");
                    s.tableRows.add(row);
                }
            }
        }
        JSONArray array = json.optJSONArray("questions");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                s.questions.add(Question.fromJson(array.optJSONObject(i)));
            }
        }
        return s;
    }
}
