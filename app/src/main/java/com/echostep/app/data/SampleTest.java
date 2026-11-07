package com.echostep.app.data;

import java.util.Arrays;

public final class SampleTest {
    private SampleTest() {
    }

    public static ListeningTest create() {
        ListeningTest test = new ListeningTest();
        test.id = ListeningTest.newId();
        test.title = "八年级上册 Unit 1 听力示例";
        test.topic = "暑假旅行";
        test.grade = "八年级";
        test.book = "上册";
        test.unitId = "8A-U1";
        test.unitTitle = "Unit 1 Where did you go on vacation?";
        test.level = "八年级上册";
        test.model = "sample";
        test.searchWarning = "";
        test.createdAt = System.currentTimeMillis();
        test.questionCount = 5;

        Section s1 = new Section();
        s1.id = "s1";
        s1.index = 1;
        s1.instruction = "听第一段对话，回答第 1-3 题。";
        s1.audioText = "Tom: Hi, Mary. How was your summer vacation?\n"
                + "Mary: It was great! I went to the mountains with my family.\n"
                + "Tom: Did you climb the mountain?\n"
                + "Mary: Yes. We got up at five thirty and watched the sunrise. It was beautiful, but I was really tired.\n"
                + "Tom: Sounds wonderful. How long did you stay there?\n"
                + "Mary: For four days. We also cooked dinner outside every evening.";
        s1.questions.add(question(1, "Where did Mary go on vacation?", "玛丽假期去了哪里？",
                "To the mountains.", "To the beach.", "To a museum.", "To her hometown.",
                0, "玛丽说她和家人去了山里（went to the mountains），因此选 A。"));
        s1.questions.add(question(2, "What time did Mary and her family get up?", "玛丽和家人几点起床？",
                "At 5:00.", "At 5:30.", "At 6:00.", "At 6:30.",
                1, "玛丽说 they got up at five thirty，即 5:30，因此选 B。"));
        s1.questions.add(question(3, "How long did Mary stay in the mountains?", "玛丽在山里待了多久？",
                "For two days.", "For three days.", "For four days.", "For a week.",
                2, "玛丽回答 For four days，因此选 C。"));

        Section s2 = new Section();
        s2.id = "s2";
        s2.index = 2;
        s2.instruction = "听第二段独白，回答第 4-5 题。";
        s2.audioText = "Hi, I'm Jack. Last Friday our class visited the city science museum. "
                + "We met at the school gate at eight in the morning and took a bus there. "
                + "The museum was really big. First, we watched a show about robots. "
                + "A robot answered our questions and even danced for us. That was my favorite part. "
                + "After lunch, we saw an exhibition about space. I learned how astronauts live in space. "
                + "We got back to school at four in the afternoon. It was a tiring but wonderful day.";
        s2.questions.add(question(4, "How did Jack's class go to the museum?", "杰克的班级怎么去博物馆的？",
                "By bike.", "By bus.", "By subway.", "On foot.",
                1, "杰克说 they took a bus there，因此选 B。"));
        s2.questions.add(question(5, "What did Jack like most about the museum?", "杰克最喜欢博物馆的什么？",
                "The space exhibition.", "The lunch.", "The robot show.", "The museum shop.",
                2, "杰克说机器人表演 That was my favorite part，因此选 C。"));

        test.sections.add(s1);
        test.sections.add(s2);
        test.sectionCount = 2;
        test.totalWords = 153;
        return test;
    }

    private static Question question(int number, String english, String chinese,
                                     String a, String b, String c, String d,
                                     int answer, String explanation) {
        Question q = new Question();
        q.id = "q" + number;
        q.number = number;
        q.question = english;
        q.questionZh = chinese;
        q.options = Arrays.asList(a, b, c, d);
        q.answer = answer;
        q.explanation = explanation;
        return q;
    }
}
