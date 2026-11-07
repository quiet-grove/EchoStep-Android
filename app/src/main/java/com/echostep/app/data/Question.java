package com.echostep.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Question {
    public String id;
    public int number;
    public String type = "choice";
    public int materialIndex;
    public String question;
    public String questionZh;
    public List<String> options = new ArrayList<>();
    public int answer;
    public String answerText = "";
    public String explanation;

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("number", number);
        json.put("type", type);
        json.put("material_index", materialIndex);
        json.put("question", question);
        json.put("question_zh", questionZh);
        json.put("options", new JSONArray(options));
        json.put("answer", answer);
        json.put("answer_text", answerText);
        json.put("explanation", explanation);
        return json;
    }

    public static Question fromJson(JSONObject json) {
        Question q = new Question();
        q.id = json.optString("id");
        q.number = json.optInt("number");
        q.type = json.optString("type", "choice");
        q.materialIndex = json.optInt("material_index", 0);
        q.question = json.optString("question");
        q.questionZh = json.optString("question_zh");
        JSONArray array = json.optJSONArray("options");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                q.options.add(array.optString(i));
            }
        }
        q.answer = json.optInt("answer", -1);
        q.answerText = json.optString("answer_text", "");
        q.explanation = json.optString("explanation");
        return q;
    }
}
