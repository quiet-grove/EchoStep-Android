package com.echostep.app.net;

import com.echostep.app.data.ListeningTest;
import com.echostep.app.data.Question;
import com.echostep.app.data.Section;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DeepSeekClient {
    public static final class ApiException extends Exception {
        public final int status;

        public ApiException(String message, int status) {
            super(message);
            this.status = status;
        }
    }

    public static final class Generation {
        public ListeningTest test;
        public List<ListeningTest.Source> sources = new ArrayList<>();
        public String searchWarning = "";
        public String rawWarning = "";
    }

    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u9fff]");
    private static final Pattern OPTION_PREFIX = Pattern.compile("^\\s*[A-Da-dＡ-Ｄ]\\s*[\\.\\)、:：]\\s*");

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public DeepSeekClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = normalize(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isEmpty() ? "deepseek-flash" : model;
    }

    public Generation generate(String grade, String book, String unitId, String unitTitle,
                               String unitNote, int count, String topic, String scope, boolean webSearch,
                               String searchContextOverride) throws Exception {
        if (apiKey.isEmpty()) {
            throw new ApiException("还没有填写 DeepSeek API Key", 401);
        }
        Generation generation = new Generation();
        String searchContext = searchContextOverride == null ? "" : searchContextOverride;
        if (webSearch && searchContext.isEmpty()) {
            try {
                List<ListeningTest.Source> sources = search(
                        PromptBuilder.searchQuery(grade, book, unitTitle, topic), 5);
                generation.sources = sources;
                searchContext = buildSearchContext(sources);
                if (sources.isEmpty()) {
                    generation.searchWarning = "联网搜索没有返回结果，已根据教材单元直接出卷。";
                }
            } catch (Exception e) {
                generation.searchWarning = "联网搜索失败：" + e.getMessage() + "（已继续离线出卷）";
            }
        }
        JSONArray messages = PromptBuilder.build(grade, book, unitTitle, unitNote, scope, searchContext);
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                JSONObject response = chat(messages, new double[]{0.9, 0.7, 0.55}[attempt]);
                JSONArray choices = response.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    throw new ApiException("DeepSeek 没有返回候选结果", 502);
                }
                JSONObject choice = choices.getJSONObject(0);
                String finish = choice.optString("finish_reason");
                if ("length".equals(finish)) {
                    throw new ApiException("生成内容被截断，请减少题量后重试", 502);
                }
                String content = choice.optJSONObject("message").optString("content", "");
                ListeningTest test = TemplateParser.parse(content, grade, book, unitId, unitTitle);
                test.model = response.optString("model", model);
                test.createdAt = System.currentTimeMillis();
                test.id = ListeningTest.newId();
                test.sources = generation.sources;
                test.searchWarning = generation.searchWarning;
                generation.test = test;
                return generation;
            } catch (ParseProblem e) {
                last = e;
                if (attempt < 2) {
                    try {
                        String content = e.content;
                        if (content != null && !content.isEmpty()) {
                            messages.put(new JSONObject().put("role", "assistant").put("content", content));
                        }
                        messages.put(new JSONObject().put("role", "user")
                                .put("content", PromptBuilder.repairMessage(e.getMessage())));
                    } catch (Exception ignored) {
                    }
                    continue;
                }
            }
        }
        throw new ApiException("AI 生成的内容未通过校验：" + (last == null ? "未知错误" : last.getMessage()), 502);
    }

    private JSONObject chat(JSONArray messages, double temperature) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("response_format", new JSONObject().put("type", "json_object"));
        body.put("temperature", temperature);
        body.put("max_tokens", 16000);
        body.put("stream", false);
        body.put("thinking", new JSONObject().put("type", "disabled"));
        String response = post(chatUrl(), body.toString());
        return new JSONObject(response);
    }

    public List<ListeningTest.Source> search(String query, int count) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("count", Math.max(1, Math.min(10, count)));
        body.put("need_content", false);
        JSONObject response = new JSONObject(post(searchUrl(), body.toString(), 45_000));
        JSONArray results = response.optJSONArray("results");
        List<ListeningTest.Source> sources = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                ListeningTest.Source source = new ListeningTest.Source();
                source.title = item.optString("title", "未命名网页");
                source.url = item.optString("url");
                source.siteName = item.optString("site_name");
                if (source.url.startsWith("http://") || source.url.startsWith("https://")) {
                    sources.add(source);
                }
                // The short summary is used directly by the model; keep it on the object.
                sourceObjectSummary.put(source, item.optString("summary", item.optString("snippet", "")));
            }
        }
        return sources;
    }

    private final java.util.Map<ListeningTest.Source, String> sourceObjectSummary = new java.util.IdentityHashMap<>();

    private String buildSearchContext(List<ListeningTest.Source> sources) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (ListeningTest.Source source : sources) {
            String summary = sourceObjectSummary.get(source);
            if (source.url == null || source.url.isEmpty()) {
                continue;
            }
            String block = "[" + index + "] " + source.title + "\n来源：" + source.siteName
                    + "\n摘要：" + (summary == null ? "" : summary) + "\n";
            if (builder.length() + block.length() > 3600) {
                break;
            }
            builder.append(block);
            index++;
        }
        return builder.toString();
    }

    public JSONObject testConnection() throws Exception {
        URL url = new URL(modelsUrl());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(25_000);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "EchoStep-Android/1.0");
        int status = connection.getResponseCode();
        String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new ApiException(friendly(status, text), status);
        }
        return new JSONObject(text);
    }

    private String post(String url, String body) throws Exception {
        return post(url, body, 200_000);
    }

    private String post(String url, String body, int timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(25_000);
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "EchoStep-Android/1.0");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new ApiException(friendly(status, text), status);
        }
        return text;
    }

    private ListeningTest parse(String content, int count, String grade, String book,
                                String unitId, String unitTitle) throws ParseProblem {
        if (content == null || content.trim().isEmpty()) {
            throw new ParseProblem("模型返回了空内容", null);
        }
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline >= 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            int fence = cleaned.lastIndexOf("```");
            if (fence >= 0) {
                cleaned = cleaned.substring(0, fence);
            }
        }
        int start = cleaned.indexOf('{');
        if (start < 0) {
            throw new ParseProblem("模型没有返回 JSON", content);
        }
        JSONObject root;
        try {
            root = new JSONObject(cleaned.substring(start));
        } catch (Exception e) {
            throw new ParseProblem("模型返回的 JSON 不完整", content);
        }
        JSONArray rawSections = root.optJSONArray("sections");
        if (rawSections == null || rawSections.length() == 0) {
            throw new ParseProblem("缺少 sections 数组", content);
        }
        ListeningTest test = new ListeningTest();
        test.grade = grade;
        test.book = book;
        test.unitId = unitId;
        test.unitTitle = unitTitle;
        test.level = grade + book;
        test.title = root.optString("title", grade + book + " " + unitTitle + " 听力练习");
        test.topic = root.optString("topic", "");
        int questionNumber = 0;
        int totalWords = 0;
        for (int s = 0; s < rawSections.length(); s++) {
            JSONObject rawSection = rawSections.optJSONObject(s);
            if (rawSection == null) {
                throw new ParseProblem("第 " + (s + 1) + " 段不是对象", content);
            }
            Section section = new Section();
            section.id = "s" + (s + 1);
            section.index = s + 1;
            section.instruction = rawSection.optString("instruction", "").trim();
            section.audioText = cleanAudio(rawSection.optString("audio_text",
                    rawSection.optString("script", rawSection.optString("transcript", ""))));
            if (CJK.matcher(section.audioText).find()) {
                throw new ParseProblem("audio_text 必须是纯英文", content);
            }
            JSONArray rawQuestions = rawSection.optJSONArray("questions");
            if (rawQuestions == null || rawQuestions.length() == 0 || rawQuestions.length() > 5) {
                throw new ParseProblem("第 " + (s + 1) + " 段题目数量不正确", content);
            }
            for (int q = 0; q < rawQuestions.length(); q++) {
                JSONObject rawQuestion = rawQuestions.optJSONObject(q);
                if (rawQuestion == null) {
                    throw new ParseProblem("题目格式不正确", content);
                }
                Question question = new Question();
                question.number = ++questionNumber;
                question.id = "q" + questionNumber;
                question.question = rawQuestion.optString("question",
                        rawQuestion.optString("stem", rawQuestion.optString("text", ""))).trim();
                question.questionZh = rawQuestion.optString("question_zh",
                        rawQuestion.optString("question_cn", rawQuestion.optString("chinese_question", ""))).trim();
                if (question.question.isEmpty()) {
                    throw new ParseProblem("第 " + questionNumber + " 题缺少英文题干", content);
                }
                if (question.questionZh.isEmpty() || !CJK.matcher(question.questionZh).find()) {
                    throw new ParseProblem("第 " + questionNumber + " 题的 question_zh 必须是中文", content);
                }
                JSONArray rawOptions = rawQuestion.optJSONArray("options");
                if (rawOptions == null || rawOptions.length() != 4) {
                    throw new ParseProblem("第 " + questionNumber + " 题必须有 4 个选项", content);
                }
                Set<String> unique = new HashSet<>();
                for (int o = 0; o < 4; o++) {
                    String option = rawOptions.optString(o, "").trim();
                    option = OPTION_PREFIX.matcher(option).replaceFirst("");
                    if (option.isEmpty()) {
                        throw new ParseProblem("第 " + questionNumber + " 题存在空选项", content);
                    }
                    String lower = option.toLowerCase(Locale.ROOT);
                    if (!unique.add(lower)) {
                        throw new ParseProblem("第 " + questionNumber + " 题存在重复选项", content);
                    }
                    question.options.add(option);
                }
                question.answer = answerIndex(rawQuestion.opt("answer"), question.options);
                question.explanation = rawQuestion.optString("explanation",
                        rawQuestion.optString("analysis", "答案依据录音内容。")).trim();
                if (question.explanation.isEmpty()) {
                    question.explanation = "答案依据录音内容。";
                }
                section.questions.add(question);
            }
            if (section.instruction == null || section.instruction.isEmpty()) {
                section.instruction = "听第 " + section.index + " 段材料，回答后面的问题。";
            }
            totalWords += section.audioText.split("\\s+").length;
            test.sections.add(section);
        }
        if (questionNumber != count) {
            throw new ParseProblem("题量不符：要求 " + count + " 题，实际生成 " + questionNumber + " 题", content);
        }
        test.questionCount = questionNumber;
        test.sectionCount = test.sections.size();
        test.totalWords = totalWords;
        return test;
    }

    private static int answerIndex(Object raw, List<String> options) throws ParseProblem {
        if (raw == null) {
            throw new ParseProblem("缺少 answer", null);
        }
        if (raw instanceof Number) {
            int value = ((Number) raw).intValue();
            if (value >= 0 && value <= 3) {
                return value;
            }
            throw new ParseProblem("answer 超出 A-D 范围", null);
        }
        String text = String.valueOf(raw).trim()
                .replace('Ａ', 'A').replace('Ｂ', 'B').replace('Ｃ', 'C').replace('Ｄ', 'D')
                .toUpperCase(Locale.ROOT)
                .replace("．", ".").replace("。", ".");
        if (text.matches("^[A-D]$")) {
            return text.charAt(0) - 'A';
        }
        if (text.matches("^[A-D][\\.\\)、:：]$")) {
            return text.charAt(0) - 'A';
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(String.valueOf(raw).trim())) {
                return i;
            }
        }
        throw new ParseProblem("answer 不是 A/B/C/D", null);
    }

    private static String cleanAudio(String raw) throws ParseProblem {
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
        if (result.length() < 30) {
            throw new ParseProblem("audio_text 太短", null);
        }
        return result;
    }

    private String chatUrl() {
        return baseUrl.endsWith("/v1") ? baseUrl + "/chat/completions" : baseUrl + "/chat/completions";
    }

    private String searchUrl() {
        return baseUrl.endsWith("/v1") ? baseUrl + "/tools/search" : baseUrl + "/v1/tools/search";
    }

    private String modelsUrl() {
        return baseUrl.endsWith("/v1") ? baseUrl + "/models" : baseUrl + "/models";
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.isEmpty() ? "https://api.deepseek.com" : result;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static String friendly(int status, String body) {
        String message = "";
        try {
            JSONObject json = new JSONObject(body);
            JSONObject error = json.optJSONObject("error");
            if (error != null) message = error.optString("message", "");
        } catch (Exception ignored) {
        }
        String base;
        switch (status) {
            case 400: base = "请求参数有误"; break;
            case 401: base = "API Key 无效或已过期"; break;
            case 402: base = "账户余额不足"; break;
            case 403: base = "请求被拒绝"; break;
            case 404: base = "接口地址不存在"; break;
            case 429: base = "请求太频繁，请稍后重试"; break;
            case 500:
            case 502:
            case 503:
            case 504: base = "DeepSeek 服务暂时不可用"; break;
            default: base = "请求失败（HTTP " + status + "）";
        }
        return message.isEmpty() || base.contains(message) ? base : base + "：" + message;
    }

    static final class ParseProblem extends Exception {
        final String content;

        ParseProblem(String message, String content) {
            super(message);
            this.content = content;
        }
    }
}
