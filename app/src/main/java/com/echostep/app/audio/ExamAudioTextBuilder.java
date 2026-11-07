package com.echostep.app.audio;

import com.echostep.app.data.AppConfig;
import com.echostep.app.data.ListeningTest;
import com.echostep.app.data.Question;
import com.echostep.app.data.Section;

import java.util.ArrayList;
import java.util.List;

public final class ExamAudioTextBuilder {
    public static final String MIC_TEST =
            "Hello, this is a microphone test. Testing, one, two, three. " +
            "The quick brown fox jumps over the lazy dog. " +
            "Can you hear me clearly? If so, we are ready to begin.";

    public static final class SpeechSegment {
        public final String text;
        public final String lang;
        public final int pauseMs;
        public final int repeats;

        SpeechSegment(String text, String lang, int pauseMs, int repeats) {
            this.text = text;
            this.lang = lang;
            this.pauseMs = pauseMs;
            this.repeats = repeats;
        }
    }

    private ExamAudioTextBuilder() {
    }

    public static List<SpeechSegment> segments(ListeningTest test, AppConfig config) {
        List<SpeechSegment> list = new ArrayList<>();
        if (config.soundcheck) {
            list.add(new SpeechSegment(MIC_TEST, "en", 1500, 1));
        }
        list.add(new SpeechSegment("听力考试现在开始。请同学们翻到第 1 页。", "zh", 2500, 1));
        for (Section section : test.sections) {
            list.add(new SpeechSegment(defaultInstruction(section), "zh", 2500, 1));
            String type = section.type == null ? "" : section.type;
            if (type.equals("word_choice") || type.equals("word_match") || type.equals("short_dialogue")) {
                for (int m = 0; m < section.materials.size(); m++) {
                    int number = questionNumber(section, m);
                    if (number > 0) list.add(new SpeechSegment("第 " + number + " 题。", "zh", 900, 1));
                    list.add(new SpeechSegment(section.materials.get(m), "en", 5000, 2));
                }
            } else if (type.equals("long_dialogue")) {
                for (int m = 0; m < section.materials.size(); m++) {
                    int[] range = questionRange(section, m);
                    if (range[0] > 0) {
                        String cue = range[0] == range[1]
                                ? "听下面一段对话，回答第 " + range[0] + " 题。"
                                : "听下面一段对话，回答第 " + range[0] + " 至 " + range[1] + " 题。";
                        list.add(new SpeechSegment(cue, "zh", 1800, 1));
                    }
                    int pause = 8000 * Math.max(1, range[1] - range[0] + 1);
                    list.add(new SpeechSegment(section.materials.get(m), "en", pause, 2));
                }
            } else if (type.equals("table_fill")) {
                int[] range = questionRange(section, 0);
                if (range[0] > 0) list.add(new SpeechSegment("第 " + range[0] + " 至 " + range[1] + " 题。", "zh", 900, 1));
                for (String material : section.materials) {
                    list.add(new SpeechSegment(material, "en", 30000, 3));
                }
            } else {
                int[] range = questionRange(section, 0);
                if (range[0] > 0) list.add(new SpeechSegment("第 " + range[0] + " 至 " + range[1] + " 题。", "zh", 900, 1));
                for (String material : section.materials) {
                    list.add(new SpeechSegment(material, "en", 30000, 2));
                }
            }
            list.add(new SpeechSegment("第" + section.index + "节到此结束。", "zh", 2200, 1));
        }
        list.add(new SpeechSegment("听力考试到此结束。请同学们继续作答笔试部分。", "zh", 1200, 1));
        return list;
    }

    public static String build(ListeningTest test, AppConfig config) {
        StringBuilder builder = new StringBuilder();
        for (SpeechSegment segment : segments(test, config)) {
            for (int i = 0; i < segment.repeats; i++) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(segment.text);
            }
        }
        return builder.toString();
    }

    public static int ratePercent(float speed) {
        return Math.max(-50, Math.min(50, Math.round((speed - 1.0f) * 100)));
    }

    private static int questionNumber(Section section, int materialIndex) {
        for (Question q : section.questions) {
            if (q.materialIndex == materialIndex) return q.number;
        }
        return 0;
    }

    private static int[] questionRange(Section section, int materialIndex) {
        int first = 0;
        int last = 0;
        for (Question q : section.questions) {
            if (q.materialIndex != materialIndex) continue;
            if (first == 0) first = q.number;
            last = q.number;
        }
        return new int[]{first, last};
    }

    private static String defaultInstruction(Section section) {
        if (section.instruction != null && !section.instruction.trim().isEmpty()) return section.instruction;
        switch (section.type == null ? "" : section.type) {
            case "word_choice":
            case "word_match": return "第一节  听句子，选出句子中含有的单词。每个句子读两遍。（5 分）";
            case "short_dialogue": return "第二节  听短对话，选择最佳答案。每段对话读两遍。（5 分）";
            case "long_dialogue": return "第三节  听长对话，选择最佳答案。每段对话读两遍。（10 分）";
            case "table_fill": return "第五节  听短文，完成下面的信息表。每空一词。短文读三遍。（5 分）";
            default: return "第四节  听短文，选择最佳答案。短文读两遍。（5 分）";
        }
    }
}
