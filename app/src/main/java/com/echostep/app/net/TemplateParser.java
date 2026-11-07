package com.echostep.app.net;

import com.echostep.app.data.ListeningTest;
import com.echostep.app.data.Question;
import com.echostep.app.data.Section;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Parser for the fixed 30 Questions · 5 Sections JSON (30 questions / 5 sections). */
final class TemplateParser {
    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u9fff]");
    private static final Pattern OPTION_PREFIX = Pattern.compile("^\\s*[A-Ca-cＡ-Ｃ]\\s*[\\.\\)、:：]\\s*");
    private static final String[] TYPES = {"word_choice", "short_dialogue", "long_dialogue", "passage", "table_fill"};
    private static final int[] COUNTS = {5, 5, 10, 5, 5};

    private TemplateParser() {
    }

    static ListeningTest parse(String content, String grade, String book, String unitId, String unitTitle)
            throws DeepSeekClient.ParseProblem {
        try {
            JSONObject root = new JSONObject(extract(content));
            JSONArray rawSections = root.optJSONArray("sections");
            if (rawSections == null || rawSections.length() != 5) {
                throw new IllegalArgumentException("听力卷必须有 5 节");
            }
            ListeningTest test = new ListeningTest();
            test.template = "A";
            test.grade = grade;
            test.book = book;
            test.unitId = unitId;
            test.unitTitle = unitTitle;
            test.level = grade + book;
            test.title = root.optString("title", grade + book + " " + unitTitle + " 听力试卷（听力卷）");
            test.topic = root.optString("topic", "");
            int number = 0;
            int words = 0;
            for (int s = 0; s < 5; s++) {
                JSONObject raw = rawSections.optJSONObject(s);
                if (raw == null) throw new IllegalArgumentException("第 " + (s + 1) + " 节不是对象");
                String type = raw.optString("type", TYPES[s]);
                if (!type.equals(TYPES[s])) {
                    throw new IllegalArgumentException("第 " + (s + 1) + " 节类型不正确");
                }
                Section section = new Section();
                section.id = "s" + (s + 1);
                section.index = s + 1;
                section.type = type;
                section.instruction = raw.optString("instruction", defaultInstruction(s));
                JSONArray materials = raw.optJSONArray("materials");
                if (materials == null || materials.length() == 0) {
                    throw new IllegalArgumentException("第 " + (s + 1) + " 节缺少 materials");
                }
                for (int m = 0; m < materials.length(); m++) {
                    JSONObject item = materials.optJSONObject(m);
                    String audio = cleanAudio(item == null ? "" : item.optString("audio_text", ""));
                    if (CJK.matcher(audio).find()) {
                        throw new IllegalArgumentException("听力材料必须是纯英文");
                    }
                    section.materials.add(audio);
                    words += audio.split("\\s+").length;
                }
                section.audioText = String.join("\n", section.materials);

                JSONArray questions = raw.optJSONArray("questions");
                if (questions == null || questions.length() != COUNTS[s]) {
                    throw new IllegalArgumentException("第 " + (s + 1) + " 节应为 " + COUNTS[s] + " 题");
                }
                if (type.equals("table_fill")) {
                    parseTable(raw.optJSONObject("table"), section);
                }
                for (int q = 0; q < questions.length(); q++) {
                    JSONObject rawQ = questions.optJSONObject(q);
                    if (rawQ == null) throw new IllegalArgumentException("题目格式不正确");
                    Question question = new Question();
                    question.number = ++number;
                    question.id = "q" + number;
                    question.materialIndex = rawQ.optInt("material_index", q);
                    if (question.materialIndex < 0 || question.materialIndex >= section.materials.size()) {
                        throw new IllegalArgumentException("第 " + number + " 题 material_index 越界");
                    }
                    if (type.equals("table_fill")) {
                        question.type = "fill";
                        question.question = "";
                        question.answerText = rawQ.optString("answer_text", "").trim();
                        if (question.answerText.isEmpty() || CJK.matcher(question.answerText).find()
                                || question.answerText.length() > 40) {
                            throw new IllegalArgumentException("第 " + number + " 题填词答案不正确");
                        }
                    } else {
                        question.type = "choice";
                        question.question = rawQ.optString("question", "Which word did you hear?").trim();
                        JSONArray options = rawQ.optJSONArray("options");
                        if (options == null || options.length() != 3) {
                            throw new IllegalArgumentException("第 " + number + " 题必须有 3 个选项");
                        }
                        Set<String> unique = new HashSet<>();
                        for (int o = 0; o < 3; o++) {
                            String option = OPTION_PREFIX.matcher(options.optString(o, "").trim()).replaceFirst("").trim();
                            if (option.isEmpty() || !unique.add(option.toLowerCase(Locale.ROOT))) {
                                throw new IllegalArgumentException("第 " + number + " 题选项为空或重复");
                            }
                            question.options.add(option);
                        }
                        question.answer = answerIndex(rawQ.opt("answer"), question.options);
                    }
                    question.explanation = rawQ.optString("explanation", "答案依据录音内容。").trim();
                    section.questions.add(question);
                }
                if (type.equals("word_choice")) {
                    List<String> shuffled = new ArrayList<>(section.materials);
                    List<Question> shuffledQuestions = new ArrayList<>(section.questions);
                    Random random = new Random();
                    for (int attempt = 0; attempt < 16; attempt++) {
                        Collections.shuffle(shuffled, random);
                        Collections.shuffle(shuffledQuestions, random);
                        boolean sequential = true;
                        for (int i = 0; i < shuffledQuestions.size(); i++) {
                            if (shuffledQuestions.get(i).answer != i) { sequential = false; break; }
                        }
                        if (!sequential) break;
                    }
                    section.materials = shuffled;
                    section.audioText = String.join("\n", shuffled);
                    section.questions = shuffledQuestions;
                    for (int i = 0; i < shuffledQuestions.size(); i++) {
                        Question q = shuffledQuestions.get(i);
                        q.number = i + 1;
                        q.id = "q" + q.number;
                        q.materialIndex = i;
                    }
                }
                test.sections.add(section);
            }
            if (number != 30) throw new IllegalArgumentException("听力卷必须是 30 题");
            test.questionCount = 30;
            test.totalScore = 30;
            test.sectionCount = 5;
            test.totalWords = words;
            return test;
        } catch (Exception e) {
            throw new DeepSeekClient.ParseProblem(e.getMessage() == null ? "试卷结构不正确" : e.getMessage(), content);
        }
    }

    private static void parseTable(JSONObject table, Section section) {
        if (table == null) throw new IllegalArgumentException("第五节缺少 table");
        section.tableTitle = table.optString("title", "Information Table");
        JSONArray rows = table.optJSONArray("rows");
        if (rows == null || rows.length() != 5) throw new IllegalArgumentException("信息表必须有 5 行");
        for (int i = 0; i < 5; i++) {
            JSONObject item = rows.optJSONObject(i);
            Section.TableRow row = new Section.TableRow();
            row.number = item.optInt("number", 26 + i);
            row.label = item.optString("label", "");
            row.prefix = item.optString("prefix", "");
            row.suffix = item.optString("suffix", "");
            section.tableRows.add(row);
        }
    }

    private static int answerIndex(Object raw, List<String> options) {
        if (raw == null) throw new IllegalArgumentException("缺少 answer");
        if (raw instanceof Number) {
            int value = ((Number) raw).intValue();
            if (value >= 0 && value < options.size()) return value;
            throw new IllegalArgumentException("answer 超出 A-C 范围");
        }
        String text = String.valueOf(raw).trim()
                .replace('Ａ', 'A').replace('Ｂ', 'B').replace('Ｃ', 'C')
                .toUpperCase(Locale.ROOT).replace("．", ".").replace("。", ".");
        if (text.matches("^[A-C]$")) return text.charAt(0) - 'A';
        if (text.matches("^[A-C][\\.\\)、:：]$")) return text.charAt(0) - 'A';
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(String.valueOf(raw).trim())) return i;
        }
        throw new IllegalArgumentException("answer 不是 A/B/C");
    }

    private static String extract(String content) {
        if (content == null) throw new IllegalArgumentException("模型返回了空内容");
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int newline = cleaned.indexOf('\n');
            if (newline >= 0) cleaned = cleaned.substring(newline + 1);
            int fence = cleaned.lastIndexOf("```");
            if (fence >= 0) cleaned = cleaned.substring(0, fence);
        }
        int start = cleaned.indexOf('{');
        if (start < 0) throw new IllegalArgumentException("模型没有返回 JSON");
        return cleaned.substring(start);
    }

    private static String cleanAudio(String raw) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").replace("\r", "\n");
        text = text.replace("**", "").replace("*", "").replace("`", "").replace("#", "");
        text = text.replaceAll("\\[[^\\]]{0,40}\\]", "");
        StringBuilder builder = new StringBuilder();
        for (String line : text.split("\n")) {
            String cleaned = line.trim().replaceAll("\\s+", " ");
            if (!cleaned.isEmpty()) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(cleaned);
            }
        }
        String result = builder.toString();
        if (result.length() < 5) throw new IllegalArgumentException("audio_text 太短");
        return result;
    }

    private static String defaultInstruction(int index) {
        switch (index) {
            case 0: return "第一节  听句子，选出句子中含有的单词。每个句子读两遍。（5 分）";
            case 1: return "第二节  听短对话，选择最佳答案。每段对话读两遍。（5 分）";
            case 2: return "第三节  听长对话，选择最佳答案。每段对话读两遍。（10 分）";
            case 3: return "第四节  听短文，选择最佳答案。短文读两遍。（5 分）";
            default: return "第五节  听短文，完成下面的信息表。每空一词。短文读三遍。（5 分）";
        }
    }
}
