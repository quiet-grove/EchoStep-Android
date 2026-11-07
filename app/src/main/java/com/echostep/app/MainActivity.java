package com.echostep.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.echostep.app.audio.AudioStore;
import com.echostep.app.audio.ExamAudioTextBuilder;
import com.echostep.app.audio.LocalPiperTts;
import com.echostep.app.audio.PiperModelManager;
import com.echostep.app.data.AppConfig;
import com.echostep.app.data.ListeningTest;
import com.echostep.app.data.Question;
import com.echostep.app.data.SampleTest;
import com.echostep.app.data.Section;
import com.echostep.app.data.TestStore;
import com.echostep.app.data.UnitCatalog;
import com.echostep.app.net.DeepSeekClient;
import com.echostep.app.net.EdgeTtsClient;
import com.echostep.app.ui.Ui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private View loadingOverlay;
    private TextView loadingText;
    private AppConfig config;
    private UnitCatalog catalog;
    private TestStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService ttsExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String screen = "home";
    private String grade;
    private String book;
    private int unitIndex;
    private int count;
    private boolean modelDownloading;
    private float speed;

    private ListeningTest test;
    private final Map<String, Integer> answers = new LinkedHashMap<>();
    private final Map<String, String> fillAnswers = new LinkedHashMap<>();
    private final Map<String, List<TextView>> optionViews = new LinkedHashMap<>();

    private MediaPlayer player;
    private File audioFile;
    private boolean audioPreparing;
    private boolean audioLocked;
    private boolean exitedExam;
    private long audioRequest;
    private Runnable progressRunnable;
    private int audioDuration;
    private TextView playButton;
    private TextView audioStatus;
    private TextView audioTime;
    private SeekBar audioSeek;

    private TextView timerView;
    private long examStarted;
    private long examElapsed;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!"exam".equals(screen)) return;
            examElapsed = (SystemClock.elapsedRealtime() - examStarted) / 1000L;
            if (timerView != null) timerView.setText(formatTime(examElapsed));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.GREEN);
        getWindow().setNavigationBarColor(Ui.PAPER);
        Ui.lightNavigationBar(getWindow());
        config = AppConfig.load(this);
        catalog = UnitCatalog.load(this);
        store = new TestStore(this);
        grade = config.grade;
        book = config.book;
        unitIndex = config.unitIndex;
        count = config.count;
        speed = config.speed;
        if (catalog.grade(grade) == null && !catalog.grades().isEmpty()) {
            grade = catalog.grades().get(0).id;
        }
        if (!catalog.books(grade).contains(book) && !catalog.books(grade).isEmpty()) {
            book = catalog.books(grade).get(0);
        }
        buildRoot();
        showHome();
        root.post(this::maybePromptModelDownload);
    }

    private void maybePromptModelDownload() {
        if (config.modelPromptShown || PiperModelManager.isInstalled(this)) return;
        config.modelPromptShown = true;
        config.save();
        new AlertDialog.Builder(this)
                .setTitle("下载本地语音模型？")
                .setMessage("线上 Edge 是首选；下载本地 ncnn Piper 后，Edge 失败时也能离线朗读。\n\n需要从 hf-mirror.com 下载英文 + 中文模型，约 65 MB。也可以先跳过，之后在设置里下载。")
                .setPositiveButton("下载", (d, w) -> startModelDownload(null))
                .setNegativeButton("暂不", null)
                .show();
    }

    private void startModelDownload(TextView statusView) {
        if (modelDownloading) return;
        modelDownloading = true;
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(14), dp(20), dp(4));
        TextView status = Ui.text(this, "准备下载…", 11, Ui.INK_SOFT, false);
        box.addView(status);
        box.addView(bar, topParams(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("下载本地语音模型")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        new Thread(() -> {
            try {
                PiperModelManager.download(this, (message, percent) -> runOnUiThread(() -> {
                    status.setText(message);
                    bar.setProgress(Math.max(0, Math.min(100, percent)));
                }));
                runOnUiThread(() -> {
                    modelDownloading = false;
                    dialog.dismiss();
                    if (statusView != null) statusView.setText(modelStatusText());
                    toast("本地 Piper 模型已就绪");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    modelDownloading = false;
                    dialog.dismiss();
                    if (statusView != null) statusView.setText(modelStatusText());
                    toast("模型下载失败：" + shortMessage(e));
                });
            }
        }, "piper-model-download").start();
    }

    private String modelStatusText() {
        if (PiperModelManager.isInstalled(this)) {
            return "已安装 · " + PiperModelManager.formatSize(PiperModelManager.installedBytes(this));
        }
        return "未安装 · 可选，下载后 Edge 失败时离线朗读";
    }

    private void buildRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.PAPER);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = Ui.column(this);
        content.setPadding(dp(18), dp(10), dp(18), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadingOverlay = buildLoadingOverlay();
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadingOverlay.setVisibility(View.GONE);
        setContentView(root);
    }

    private View buildLoadingOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x880F1A14);
        overlay.setClickable(true);
        LinearLayout card = Ui.column(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(26), dp(28), dp(24));
        card.setBackground(Ui.rounded(this, Ui.PAPER_CARD, 20, 0x33FFFFFF, 1));
        ProgressBar progress = new ProgressBar(this);
        progress.getIndeterminateDrawable().setColorFilter(Ui.ORANGE, android.graphics.PorterDuff.Mode.SRC_IN);
        card.addView(progress, new LinearLayout.LayoutParams(dp(38), dp(38)));
        loadingText = Ui.text(this, "正在准备试卷…", 13, Ui.INK_SOFT, true);
        loadingText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(14);
        card.addView(loadingText, textParams);
        TextView tip = Ui.text(this, "DeepSeek 正在原创命题，通常需要 10-40 秒", 10, Ui.MUTED, false);
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipParams.topMargin = dp(7);
        card.addView(tip, tipParams);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        cardParams.leftMargin = dp(34);
        cardParams.rightMargin = dp(34);
        overlay.addView(card, cardParams);
        return overlay;
    }

    private void showLoading(String message) {
        if (loadingText != null) loadingText.setText(message);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void showHome() {
        stopTimer();
        releasePlayer();
        audioPreparing = false;
        audioRequest++;
        screen = "home";
        answers.clear();
        fillAnswers.clear();
        content.removeAllViews();
        add(content, topbarHome(), 0);
        add(content, heroCard(), 16);
        add(content, setupCard(), 16);
        add(content, apiCard(), 12);
        add(content, ttsCard(), 12);
        add(content, footer(), 24);
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_UP));
    }

    private View topbarHome() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(0, dp(6), 0, dp(4));
        LinearLayout brand = Ui.column(this);
        TextView title = Ui.text(this, "EchoStep", 22, Ui.INK, true);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setLetterSpacing(-0.03f);
        TextView sub = Ui.text(this, "英语听力工坊", 9, Ui.MUTED, false);
        sub.setLetterSpacing(0.24f);
        brand.addView(title);
        brand.addView(sub);
        brand.setOnClickListener(v -> showHome());
        bar.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView history = navAction("历史");
        history.setOnClickListener(v -> showHistory());
        bar.addView(history, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        TextView settings = navAction("设置");
        settings.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        settingsParams.leftMargin = dp(4);
        bar.addView(settings, settingsParams);
        return bar;
    }

    private TextView navAction(String text) {
        TextView view = Ui.text(this, text, 12, Ui.INK_SOFT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(Ui.rounded(this, 0x00FFFFFF, 10, 0, 0));
        return view;
    }

    private View heroCard() {
        LinearLayout hero = Ui.column(this);
        hero.setPadding(dp(23), dp(25), dp(23), dp(23));
        hero.setBackground(Ui.hero(this));

        TextView eyebrow = Ui.text(this, "LISTEN · THINK · ANSWER", 9, 0xFFEFB786, true);
        eyebrow.setLetterSpacing(0.22f);
        hero.addView(eyebrow);

        TextView title = Ui.text(this, "把课本，\n变成一场听力考试。", 29, 0xFFF7F3E8, true);
        title.setLineSpacing(dp(4), 1.0f);
        title.setLetterSpacing(-0.04f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(14);
        hero.addView(title, titleParams);

        TextView sub = Ui.text(this,
                "选年级与单元，DeepSeek 按教材边界原创命题；Edge 在线语音朗读，做完自动批改。",
                12.5f, 0xFFD4DFD4, false);
        sub.setLineSpacing(dp(4), 1.0f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(12);
        hero.addView(sub, subParams);

        LinearLayout tags = Ui.row(this);
        tags.setPadding(0, dp(18), 0, 0);
        tags.addView(heroTag("人教版 Go for it!"));
        tags.addView(heroTag("七 · 八 · 九年级"));
        tags.addView(heroTag("在线 TTS"));
        hero.addView(tags);
        return hero;
    }

    private TextView heroTag(String text) {
        TextView view = Ui.text(this, text, 9.5f, 0xFFD7E2D7, false);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable bg = Ui.rounded(this, 0x14FFFFFF, 99, 0x33FFFFFF, 1);
        view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(7);
        view.setLayoutParams(params);
        return view;
    }

    private View setupCard() {
        LinearLayout card = Ui.card(this);
        card.addView(sectionHeader("01", "选择考试范围",
                "年级不同，词汇、语法和题型边界也不同", "年级即难度 · 严格不超纲"));
        card.addView(gradeRow(), topParams(14));

        LinearLayout selectors = Ui.row(this);
        Spinner bookSpinner = spinner(catalog.books(grade));
        Spinner unitSpinner = spinner(new ArrayList<>());
        bookSpinner.setSelection(Math.max(0, catalog.books(grade).indexOf(book)));
        bookSpinner.setOnItemSelectedListener(new SimpleItemSelected(position -> {
            String selected = catalog.books(grade).get(position);
            if (!selected.equals(book)) {
                book = selected;
                unitIndex = 0;
                config.book = book;
                config.unitIndex = 0;
                config.save();
                fillUnits(unitSpinner);
            }
        }));
        fillUnits(unitSpinner);
        unitSpinner.setOnItemSelectedListener(new SimpleItemSelected(position -> {
            unitIndex = position;
            config.unitIndex = position;
        }));

        LinearLayout bookBox = field("册", bookSpinner, 0.38f);
        LinearLayout unitBox = field("单元", unitSpinner, 1f);
        selectors.addView(bookBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.38f));
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.62f);
        unitParams.leftMargin = dp(10);
        selectors.addView(unitBox, unitParams);
        card.addView(selectors, topParams(12));

        String scope = catalog.scope(grade);
        TextView scopeView = Ui.text(this, "本卷知识边界  " + scope, 10.5f, Ui.MUTED, false);
        scopeView.setLineSpacing(dp(3), 1.0f);
        scopeView.setPadding(dp(11), dp(9), dp(11), dp(9));
        scopeView.setBackground(Ui.rounded(this, Ui.MINT_SOFT, 10, 0xFFD9E4CF, 1));
        card.addView(scopeView, topParams(12));

        card.addView(Ui.divider(this));
        card.addView(sectionHeader("02", "设置这场练习",
                "固定 30 题，听力只播一次", ""));
        card.addView(countRow(), topParams(12));
        card.addView(speedRow(), topParams(13));
        card.addView(toggleRow("整套音频包含试音", "固定英文麦克风测试", config.soundcheck,
                checked -> {
                    config.soundcheck = checked;
                    config.save();
                }), topParams(7));
        card.addView(toggleRow("联网搜索最新素材", "调用 DeepSeek 官方 Search API，为命题提供新鲜话题", config.webSearch,
                checked -> {
                    config.webSearch = checked;
                    config.save();
                }), topParams(0));

        EditText topic = Ui.input(this, "自定义主题（可选），例如：校园运动会、周末计划……");
        LinearLayout.LayoutParams topicParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        topicParams.topMargin = dp(12);
        card.addView(topic, topicParams);

        LinearLayout actions = Ui.row(this);
        Button generate = Ui.button(this, "生成听力试卷", true);
        generate.setOnClickListener(v -> generateTest(topic.getText().toString().trim()));
        actions.addView(generate, new LinearLayout.LayoutParams(0, dp(48), 1f));
        card.addView(actions, topParams(14));
        TextView fine = Ui.text(this, "生成约需 10-40 秒 · 听力卷 · 交卷后自动批改", 9.5f, Ui.MUTED_2, false);
        fine.setGravity(Gravity.CENTER);
        card.addView(fine, topParams(9));
        return card;
    }

    private View sectionHeader(String number, String title, String subtitle, String note) {
        LinearLayout row = Ui.row(this);
        TextView step = Ui.text(this, number, 9.5f, 0xFFF6F1E5, true);
        step.setGravity(Gravity.CENTER);
        step.setBackground(Ui.rounded(this, Ui.GREEN, 99, 0, 0));
        row.addView(step, new LinearLayout.LayoutParams(dp(29), dp(29)));
        LinearLayout titles = Ui.column(this);
        TextView heading = Ui.heading(this, title, 18);
        titles.addView(heading);
        TextView sub = Ui.text(this, subtitle, 10.5f, Ui.MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(10);
        row.addView(titles, titleParams);
        if (note != null && !note.isEmpty()) {
            TextView badge = Ui.text(this, note, 9, Ui.GREEN_2, true);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(Ui.rounded(this, Ui.MINT_SOFT, 99, 0xFFD9E4CF, 1));
            row.addView(badge);
        }
        return row;
    }

    private View gradeRow() {
        LinearLayout row = Ui.row(this);
        for (UnitCatalog.Grade item : catalog.grades()) {
            TextView chip = Ui.chip(this, item.id);
            chip.setTextSize(12.5f);
            chip.setPadding(dp(8), dp(12), dp(8), dp(12));
            if (item.id.equals(grade)) {
                Ui.styleChip(chip, true);
            }
            chip.setOnClickListener(v -> {
                grade = item.id;
                config.grade = grade;
                book = catalog.books(grade).isEmpty() ? "" : catalog.books(grade).get(0);
                config.book = book;
                unitIndex = 0;
                config.unitIndex = 0;
                config.save();
                showHome();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
            if (row.getChildCount() > 0) params.leftMargin = dp(6);
            row.addView(chip, params);
        }
        return row;
    }

    private LinearLayout field(String label, Spinner spinner, float weight) {
        LinearLayout box = Ui.column(this);
        TextView text = Ui.text(this, label, 10.5f, Ui.INK_SOFT, true);
        box.addView(text);
        FrameLayout holder = new FrameLayout(this);
        holder.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView arrow = Ui.text(this, "▾", 14, Ui.MUTED, true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setClickable(false);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(
                dp(34), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        holder.addView(arrow, arrowParams);
        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        holderParams.topMargin = dp(6);
        box.addView(holder, holderParams);
        return box;
    }

    private Spinner spinner(List<String> values) {
        Spinner spinner = new Spinner(this);
        spinner.setBackground(Ui.rounded(this, 0xFFFDFBF5, 10, Ui.LINE, 1));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        TextAdapter adapter = new TextAdapter(values);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundDrawable(Ui.rounded(this, Ui.PAPER_CARD, 12, Ui.LINE, 1));
        return spinner;
    }

    private void fillUnits(Spinner unitSpinner) {
        List<String> titles = new ArrayList<>();
        for (UnitCatalog.Unit unit : catalog.units(grade, book)) titles.add(unit.title);
        unitSpinner.setAdapter(new TextAdapter(titles));
        if (unitIndex >= titles.size()) unitIndex = 0;
        unitSpinner.setSelection(Math.max(0, unitIndex));
    }

    private View countRow() {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(11), dp(10), dp(11), dp(10));
        box.setBackground(Ui.rounded(this, Ui.MINT_SOFT, 10, 0xFFD7E3CC, 1));
        box.addView(Ui.text(this, "30 题 / 30 分 / 30 分 · 5 节", 11.5f, Ui.GREEN, true));
        box.addView(Ui.text(this, "1–5 辨音选词 · 6–10 短对话 · 11–20 长对话 · 21–25 短文 · 26–30 填词（听三遍）",
                9.5f, Ui.MUTED, false));
        return box;
    }

    private View speedRow() {
        LinearLayout box = Ui.column(this);
        LinearLayout labelRow = Ui.row(this);
        labelRow.addView(Ui.text(this, "语速", 10.5f, Ui.INK_SOFT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Ui.text(this, formatSpeed(speed), 10.5f, Ui.ORANGE_DEEP, true);
        labelRow.addView(value);
        box.addView(labelRow);
        SeekBar seek = new SeekBar(this);
        seek.setMax(90);
        seek.setProgress(Math.round((speed - 0.6f) * 100));
        seek.setPadding(0, dp(6), 0, 0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speed = 0.6f + progress / 100f;
                value.setText(formatSpeed(speed));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                config.speed = speed;
                config.save();
            }
        });
        box.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(39)));
        LinearLayout marks = Ui.row(this);
        marks.addView(Ui.text(this, "慢", 9, Ui.MUTED_2, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView normal = Ui.text(this, "正常", 9, Ui.MUTED_2, false);
        normal.setGravity(Gravity.CENTER);
        marks.addView(normal, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView fast = Ui.text(this, "快", 9, Ui.MUTED_2, false);
        fast.setGravity(Gravity.END);
        marks.addView(fast, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(marks);
        return box;
    }

    private View toggleRow(String title, String subtitle, boolean checked, SwitchListener listener) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(8), 0, dp(2));
        LinearLayout text = Ui.column(this);
        text.addView(Ui.text(this, title, 12, Ui.INK_SOFT, true));
        TextView sub = Ui.text(this, subtitle, 9.5f, Ui.MUTED, false);
        sub.setPadding(0, dp(1), 0, 0);
        text.addView(sub);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ORANGE));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onChanged(isChecked));
        row.addView(toggle);
        return row;
    }

    private View apiCard() {
        LinearLayout card = Ui.card(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.label(this, "DEEPSEEK API"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView dot = Ui.text(this, "●", 10, config.hasKey() ? Ui.GREEN_3 : Ui.ORANGE, true);
        head.addView(dot);
        card.addView(head);
        TextView title = Ui.heading(this, config.hasKey() ? "已就绪，可以出卷" : "先填入 API Key", 15);
        title.setPadding(0, dp(8), 0, 0);
        card.addView(title);
        TextView sub = Ui.text(this,
                config.hasKey()
                        ? "密钥仅保存在这台手机。当前模型 " + config.model + "。"
                        : "使用 DeepSeek 官方 API 自主出卷；也可以先听示例。",
                10.5f, Ui.MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(10));
        card.addView(sub);
        if (config.hasKey()) {
            TextView key = Ui.text(this, config.maskKey(), 11, Ui.INK_SOFT, false);
            key.setTypeface(Typeface.MONOSPACE);
            key.setPadding(dp(10), dp(8), dp(10), dp(8));
            key.setBackground(Ui.rounded(this, 0xFFFDFBF5, 8, Ui.LINE, 1));
            card.addView(key);
        }
        Button manage = Ui.button(this, config.hasKey() ? "管理密钥与模型" : "填写密钥", true);
        manage.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.topMargin = dp(10);
        card.addView(manage, params);
        return card;
    }

    private View ttsCard() {
        LinearLayout card = Ui.card(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.label(this, "ONLINE TTS"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView size = Ui.text(this, "0 MB", 9, Ui.GREEN_2, true);
        size.setPadding(dp(8), dp(4), dp(8), dp(4));
        size.setBackground(Ui.rounded(this, Ui.MINT_SOFT, 99, 0xFFD8E4CE, 1));
        head.addView(size);
        card.addView(head);
        TextView title = Ui.heading(this, "Edge 在线语音 · Ava", 15);
        title.setPadding(0, dp(8), 0, 0);
        card.addView(title);
        TextView sub = Ui.text(this, "优先使用线上 Edge；失败时可回退到本地 ncnn Piper。",
                10.5f, Ui.MUTED, false);
        sub.setPadding(0, dp(4), 0, 0);
        card.addView(sub);
        return card;
    }

    private View footer() {
        TextView view = Ui.text(this, "EchoStep · 本地运行的英语听力练习工具\nDeepSeek 命题 + Edge 在线语音", 9,
                Ui.MUTED_2, false);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout.LayoutParams topParams(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        return params;
    }

    private void add(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    private void generateTest(String topic) {
        if (!config.hasKey()) {
            toast("请先填写 DeepSeek API Key");
            showSettings();
            return;
        }
        List<UnitCatalog.Unit> units = catalog.units(grade, book);
        if (units.isEmpty()) {
            toast("请选择教材单元");
            return;
        }
        UnitCatalog.Unit unit = units.get(Math.max(0, Math.min(unitIndex, units.size() - 1)));
        config.grade = grade;
        config.book = book;
        config.unitIndex = unitIndex;
        config.count = count;
        config.speed = speed;
        config.save();
        showLoading("正在准备试卷…");
        executor.execute(() -> {
            try {
                DeepSeekClient client = new DeepSeekClient(config.normalizedBase(), config.apiKey, config.model);
                DeepSeekClient.Generation generation = client.generate(
                        grade, book, unit.id, unit.title, unit.note, 30, topic, catalog.scope(grade), config.webSearch, null);
                store.save(generation.test);
                runOnUiThread(() -> {
                    hideLoading();
                    test = generation.test;
                    answers.clear();
                    fillAnswers.clear();
                    config.lastTestId = test.id;
                    config.save();
                    showExam();
                    if (!generation.searchWarning.isEmpty()) {
                        toast(generation.searchWarning);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideLoading();
                    toast(e.getMessage() == null ? "生成失败，请重试" : e.getMessage());
                    if (e instanceof DeepSeekClient.ApiException
                            && (((DeepSeekClient.ApiException) e).status == 401
                            || ((DeepSeekClient.ApiException) e).status == 402)) {
                        showSettings();
                    }
                });
            }
        });
    }

    private void loadSample() {
        test = SampleTest.create();
        answers.clear();
        try {
            store.save(test);
        } catch (Exception ignored) {
        }
        config.lastTestId = test.id;
        config.save();
        showExam();
    }

    private void showExam() {
        if (test == null) {
            showHome();
            return;
        }
        prepareExamState();
        renderExamUi();
        startExamAudio();
    }

    /** Package-private hook used by Robolectric layout tests; does not touch the network. */
    void renderExamForTest() {
        prepareExamState();
        renderExamUi();
    }

    private void prepareExamState() {
        releasePlayer();
        stopTimer();
        audioLocked = test != null && getSharedPreferences("echostep", MODE_PRIVATE)
                .getBoolean("audio_started_" + test.id, false);
        screen = "exam";
        answers.clear();
        fillAnswers.clear();
        optionViews.clear();
        examElapsed = 0;
    }

    private void renderExamUi() {
        content.removeAllViews();
        content.addView(examTopbar());
        if ((test.searchWarning != null && !test.searchWarning.isEmpty()) || !test.sources.isEmpty()) {
            content.addView(searchNote(), topParams(10));
        }
        content.addView(audioCard(), topParams(12));
        TextView answered = Ui.text(this, "已答 0 / " + test.questionCount, 10, Ui.MUTED, true);
        answered.setTag("answered");
        content.addView(answered, topParams(7));
        for (Section section : test.sections) {
            content.addView(sectionCard(section), topParams(14));
        }
        Button submit = Ui.button(this, "交卷并查看解析", true);
        submit.setOnClickListener(v -> submitExam());
        content.addView(submit, topParams(16));
        TextView tip = Ui.text(this, "交卷后才会显示正确答案、解析和听力原文。", 9.5f, Ui.MUTED_2, false);
        tip.setGravity(Gravity.CENTER);
        content.addView(tip, topParams(8));
        startTimer();
        updateAnswered();
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_UP));
    }

    private void startExamAudio() {
        audioPreparing = false;
        audioRequest++;
        audioFile = AudioStore.fileFor(this, test, config);
        if (audioFile.exists() && audioFile.length() > 2000) {
            prepareAudio(audioFile);
        } else {
            startAudioSynthesis();
        }
    }

    private View examTopbar() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(0, dp(5), 0, dp(4));
        TextView back = Ui.text(this, "← 返回", 12, Ui.MUTED, true);
        back.setPadding(dp(2), dp(8), dp(10), dp(8));
        back.setOnClickListener(v -> confirmLeaveExam());
        bar.addView(back);
        LinearLayout titleBox = Ui.column(this);
        TextView kicker = Ui.text(this, test.grade + test.book + " · " + test.unitTitle + " · " + test.questionCount + " 题",
                8.5f, Ui.ORANGE_DEEP, true);
        kicker.setLetterSpacing(0.05f);
        titleBox.addView(kicker);
        TextView title = Ui.heading(this, test.title, 16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBox.addView(title);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(6);
        bar.addView(titleBox, titleParams);
        timerView = Ui.text(this, "00:00", 11, Ui.MUTED, false);
        timerView.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.addView(timerView);
        return bar;
    }

    private View searchNote() {
        String warning = test.searchWarning == null ? "" : test.searchWarning;
        String text;
        if (!warning.isEmpty() && test.sources.isEmpty()) {
            text = warning;
        } else {
            StringBuilder builder = new StringBuilder("已联网参考 " + test.sources.size() + " 条素材作为话题灵感。");
            for (int i = 0; i < Math.min(3, test.sources.size()); i++) {
                ListeningTest.Source source = test.sources.get(i);
                builder.append("  [").append(i + 1).append("] ")
                        .append(source.siteName == null || source.siteName.isEmpty() ? "来源" : source.siteName);
            }
            if (!warning.isEmpty()) builder.append("（").append(warning).append("）");
            text = builder.toString();
        }
        TextView note = Ui.text(this, text, 9.5f, Ui.GREEN_2, false);
        note.setPadding(dp(11), dp(8), dp(11), dp(8));
        note.setBackground(Ui.rounded(this, Ui.MINT_SOFT, 9, 0xFFDBE4D3, 1));
        return note;
    }

    private View audioCard() {
        LinearLayout card = Ui.column(this);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        card.setBackground(Ui.rounded(this, 0xFFF8FBF3, 17, 0xFFCDDDC5, 1));

        TextView badge = Ui.text(this, "● 试音 + 整套试卷", 8.5f, Ui.ORANGE_DEEP, true);
        badge.setLetterSpacing(0.1f);
        card.addView(badge);
        TextView title = Ui.heading(this, "考试音频", 17);
        title.setPadding(0, dp(4), 0, 0);
        card.addView(title);
        TextView sub = Ui.text(this, "英文麦克风试音后开始。听力只播一次，开始后不可暂停、拖动或回放。",
                9.5f, Ui.MUTED, false);
        sub.setPadding(0, dp(3), 0, dp(10));
        card.addView(sub);

        LinearLayout playerRow = Ui.row(this);
        playButton = Ui.text(this, "▶", 17, 0xFFF8F3E6, true);
        playButton.setGravity(Gravity.CENTER);
        playButton.setBackground(Ui.rounded(this, Ui.GREEN, 99, 0, 0));
        playButton.setOnClickListener(v -> toggleAudio());
        playerRow.addView(playButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout statusBox = Ui.column(this);
        audioStatus = Ui.text(this, "正在准备整套音频…", 10.5f, Ui.GREEN_2, true);
        audioTime = Ui.text(this, "00:00 / --:--", 9.5f, Ui.MUTED, false);
        audioTime.setPadding(0, dp(2), 0, 0);
        statusBox.addView(audioStatus);
        statusBox.addView(audioTime);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusParams.leftMargin = dp(11);
        playerRow.addView(statusBox, statusParams);
        card.addView(playerRow);

        audioSeek = new SeekBar(this);
        audioSeek.setMax(1000);
        audioSeek.setPadding(0, dp(8), 0, 0);
        audioSeek.setEnabled(false);
        audioSeek.setVisibility(View.GONE);
        audioSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null && audioDuration > 0) {
                    player.seekTo((int) ((long) audioDuration * progress / 1000L));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(audioSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        TextView foot = Ui.text(this, "英文试音 · 听力只播一次 · 不可暂停/回放", 8.5f, Ui.MUTED_2, false);
        card.addView(foot);
        return card;
    }

    private View sectionCard(Section section) {
        LinearLayout card = Ui.card(this);
        LinearLayout header = Ui.row(this);
        TextView number = Ui.text(this, String.format(Locale.US, "%02d", section.index), 9, Ui.GREEN, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(Ui.rounded(this, Ui.MINT, 8, 0, 0));
        header.addView(number, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.heading(this, "Section " + section.index, 15));
        TextView instruction = Ui.text(this, section.instruction, 10.5f, Ui.MUTED, false);
        instruction.setPadding(0, dp(2), 0, 0);
        titles.addView(instruction);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(9);
        header.addView(titles, titleParams);
        header.addView(Ui.text(this, section.questions.size() + " 题", 9.5f, Ui.MUTED_2, false));
        card.addView(header);

        if ("table_fill".equals(section.type)) {
            card.addView(tableFillView(section), topParams(10));
        } else {
            for (Question question : section.questions) {
                card.addView(questionView(question), topParams(15));
            }
        }
        return card;
    }

    private View tableFillView(Section section) {
        LinearLayout box = Ui.column(this);
        if (section.tableTitle != null && !section.tableTitle.isEmpty()) {
            TextView title = Ui.text(this, section.tableTitle, 11, Ui.GREEN, true);
            title.setPadding(dp(10), dp(8), dp(10), dp(8));
            title.setBackground(Ui.rounded(this, Ui.GREEN, 8, 0, 0));
            box.addView(title);
        }
        for (Section.TableRow row : section.tableRows) {
            Question question = null;
            for (Question q : section.questions) if (q.number == row.number) question = q;
            if (question == null) continue;
            final Question fillQuestion = question;
            LinearLayout line = Ui.row(this);
            line.setPadding(dp(8), dp(6), dp(8), dp(6));
            TextView label = Ui.text(this, row.label, 10, Ui.INK_SOFT, true);
            line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (row.prefix != null && !row.prefix.isEmpty()) line.addView(Ui.text(this, row.prefix, 10, Ui.MUTED, false));
            EditText input = new EditText(this);
            input.setTextSize(11);
            input.setSingleLine(true);
            input.setHint("第 " + row.number + " 题");
            input.setPadding(dp(6), 0, dp(6), 0);
            input.setText(fillAnswers.containsKey(fillQuestion.id) ? fillAnswers.get(fillQuestion.id) : "");
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String value = s.toString().trim();
                    if (value.isEmpty()) fillAnswers.remove(fillQuestion.id); else fillAnswers.put(fillQuestion.id, value);
                    updateAnswered();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(82), dp(32));
            line.addView(input, inputParams);
            if (row.suffix != null && !row.suffix.isEmpty()) line.addView(Ui.text(this, row.suffix, 10, Ui.MUTED, false));
            line.setBackground(Ui.rounded(this, Ui.PAPER, 7, 0xFFE5E0D2, 1));
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineParams.topMargin = dp(4);
            box.addView(line, lineParams);
        }
        return box;
    }

    private View questionView(Question question) {
        LinearLayout box = Ui.column(this);
        LinearLayout head = Ui.row(this);
        TextView number = Ui.text(this, String.valueOf(question.number), 10, Ui.INK_SOFT, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(Ui.rounded(this, Ui.PAPER, 99, 0, 0));
        head.addView(number, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout text = Ui.column(this);
        text.addView(Ui.text(this, question.question, 12.5f, Ui.INK, true));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(8);
        head.addView(text, textParams);
        box.addView(head);

        List<TextView> options = new ArrayList<>();
        String[] letters = {"A", "B", "C"};
        for (int i = 0; i < question.options.size(); i++) {
            final int index = i;
            TextView option = Ui.text(this, letters[i] + ". " + question.options.get(i), 12, Ui.INK_SOFT, false);
            option.setPadding(dp(11), dp(10), dp(11), dp(10));
            Ui.setSelectableBackground(option, false, false, false);
            option.setOnClickListener(v -> selectAnswer(question, index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(7);
            box.addView(option, params);
            options.add(option);
        }
        optionViews.put(question.id, options);
        return box;
    }

    private void selectAnswer(Question question, int index) {
        if (!"exam".equals(screen)) return;
        answers.put(question.id, index);
        List<TextView> options = optionViews.get(question.id);
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                boolean selected = i == index;
                Ui.setSelectableBackground(options.get(i), selected, false, false);
                options.get(i).setTextColor(selected ? Ui.GREEN : Ui.INK_SOFT);
            }
        }
        updateAnswered();
    }

    private int answeredCount() {
        int count = answers.size();
        for (String value : fillAnswers.values()) if (value != null && !value.trim().isEmpty()) count++;
        return count;
    }

    private void updateAnswered() {
        if (test == null) return;
        TextView countView = content.findViewWithTag("answered");
        if (countView != null) {
            countView.setText("已答 " + answeredCount() + " / " + test.questionCount);
        }
    }

    private void submitExam() {
        if (test == null) return;
        int unanswered = test.questionCount - answeredCount();
        if (unanswered > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("还有 " + unanswered + " 题没做")
                    .setMessage("现在交卷也可以，空题会按答错计算。")
                    .setPositiveButton("仍然交卷", (dialog, which) -> { exitedExam = false; showResult(); })
                    .setNegativeButton("再检查一下", null)
                    .show();
        } else {
            exitedExam = false;
            showResult();
        }
    }

    private void showResult() {
        stopTimer();
        releasePlayer();
        screen = "result";
        if (test == null) {
            showHome();
            return;
        }
        int score = 0;
        for (Question q : test.allQuestions()) {
            if ("fill".equals(q.type)) {
                String selected = fillAnswers.get(q.id);
                if (selected != null && selected.trim().equalsIgnoreCase(q.answerText.trim())) score++;
            } else {
                Integer selected = answers.get(q.id);
                if (selected != null && selected == q.answer) score++;
            }
        }
        int total = test.questionCount;
        int percent = total == 0 ? 0 : Math.round(score * 100f / total);

        content.removeAllViews();
        content.addView(resultTopbar());
        content.addView(scoreCard(score, total, percent), topParams(10));
        for (Section section : test.sections) {
            content.addView(resultSection(section), topParams(14));
        }
        LinearLayout actions = Ui.row(this);
        Button again = Ui.button(this, "再来一套", true);
        again.setOnClickListener(v -> generateTest(""));
        actions.addView(again, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button home = Ui.button(this, "回到首页", false);
        home.setOnClickListener(v -> showHome());
        LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        homeParams.leftMargin = dp(8);
        actions.addView(home, homeParams);
        content.addView(actions, topParams(16));
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_UP));
    }

    private View resultTopbar() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(0, dp(5), 0, dp(4));
        TextView back = Ui.text(this, "← 首页", 12, Ui.MUTED, true);
        back.setPadding(dp(2), dp(8), dp(10), dp(8));
        back.setOnClickListener(v -> showHome());
        bar.addView(back);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.heading(this, exitedExam ? "已退出 · 本卷成绩" : "本卷成绩", 17));
        titles.addView(Ui.text(this, test.title, 9.5f, Ui.MUTED, false));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(8);
        bar.addView(titles, titleParams);
        bar.addView(Ui.text(this, formatTime(examElapsed), 11, Ui.MUTED, false));
        return bar;
    }

    private View scoreCard(int score, int total, int percent) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.row(this);
        LinearLayout scoreBox = Ui.column(this);
        scoreBox.setGravity(Gravity.CENTER);
        scoreBox.setPadding(dp(18), dp(15), dp(18), dp(15));
        scoreBox.setBackground(Ui.rounded(this, Ui.MINT_SOFT, 99, 0xFFD8E4CE, 1));
        TextView value = Ui.text(this, String.valueOf(score), 48, Ui.GREEN, true);
        value.setGravity(Gravity.CENTER);
        scoreBox.addView(value);
        TextView slash = Ui.text(this, "/ " + total, 11, Ui.MUTED, false);
        slash.setGravity(Gravity.CENTER);
        scoreBox.addView(slash);
        row.addView(scoreBox, new LinearLayout.LayoutParams(dp(112), dp(112)));

        LinearLayout copy = Ui.column(this);
        String title = percent == 100 ? "满分，漂亮。" : percent >= 80 ? "做得不错。" : percent >= 60 ? "继续磨耳朵。" : "慢一点，也没关系。";
        TextView heading = Ui.heading(this, title, 21);
        copy.addView(heading);
        TextView message = Ui.text(this, percent == 100 ? "全对。你的耳朵已经跟上了英语的节奏。"
                        : percent >= 80 ? "很稳。再精听一下错题里的关键句。"
                        : percent >= 60 ? "基础不错。把错题原文读一遍，再盲听一次。"
                        : "别急，先把原文看懂，再逐句跟读。",
                10.5f, Ui.MUTED, false);
        message.setPadding(0, dp(4), 0, 0);
        copy.addView(message);
        LinearLayout stats = Ui.row(this);
        stats.setPadding(0, dp(13), 0, 0);
        stats.addView(stat(String.valueOf(score), "答对"));
        stats.addView(stat(String.valueOf(total - score), "答错"));
        stats.addView(stat(percent + "%", "正确率"));
        copy.addView(stats);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(15);
        row.addView(copy, copyParams);
        card.addView(row);
        return card;
    }

    private View stat(String value, String label) {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.text(this, value, 17, Ui.INK, true));
        box.addView(Ui.text(this, label, 8.5f, Ui.MUTED_2, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(18);
        box.setLayoutParams(params);
        return box;
    }

    private View resultSection(Section section) {
        LinearLayout card = Ui.card(this);
        LinearLayout header = Ui.row(this);
        TextView number = Ui.text(this, String.format(Locale.US, "%02d", section.index), 9, Ui.GREEN, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(Ui.rounded(this, Ui.MINT, 8, 0, 0));
        header.addView(number, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.heading(this, "Section " + section.index, 15));
        titles.addView(Ui.text(this, section.instruction, 10.5f, Ui.MUTED, false));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(9);
        header.addView(titles, titleParams);
        card.addView(header);
        if ("table_fill".equals(section.type)) {
            card.addView(resultFillTable(section), topParams(10));
        } else {
            for (Question question : section.questions) {
                card.addView(resultQuestion(question), topParams(14));
            }
        }
        TextView toggle = Ui.text(this, "查看本段听力原文", 10, Ui.GREEN_2, true);
        toggle.setPadding(0, dp(12), 0, dp(2));
        TextView transcript = Ui.text(this, section.audioText, 11, Ui.INK_SOFT, false);
        transcript.setLineSpacing(dp(4), 1.0f);
        transcript.setPadding(dp(10), dp(9), dp(10), dp(9));
        transcript.setBackground(Ui.rounded(this, 0xFFFDFBF5, 9, Ui.LINE, 1));
        transcript.setVisibility(View.GONE);
        toggle.setOnClickListener(v -> {
            boolean show = transcript.getVisibility() != View.VISIBLE;
            transcript.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "收起本段听力原文" : "查看本段听力原文");
        });
        card.addView(toggle);
        card.addView(transcript, topParams(5));
        return card;
    }

    private View resultFillTable(Section section) {
        LinearLayout box = Ui.column(this);
        for (Section.TableRow row : section.tableRows) {
            Question q = null;
            for (Question item : section.questions) if (item.number == row.number) q = item;
            if (q == null) continue;
            String selected = fillAnswers.get(q.id);
            boolean correct = selected != null && selected.trim().equalsIgnoreCase(q.answerText.trim());
            LinearLayout line = Ui.row(this);
            line.setPadding(dp(8), dp(6), dp(8), dp(6));
            line.addView(Ui.text(this, row.label, 10, Ui.INK_SOFT, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView answer = Ui.text(this, (selected == null ? "未填" : selected), 10,
                    correct ? Ui.GREEN : Ui.RED, true);
            answer.setPadding(dp(7), dp(3), dp(7), dp(3));
            answer.setBackground(Ui.rounded(this, correct ? 0xFFE8F4E3 : 0xFFFBE7E2, 6, 0, 0));
            line.addView(answer);
            if (!correct) line.addView(Ui.text(this, "  正确：" + q.answerText, 9.5f, Ui.MUTED, false));
            line.setBackground(Ui.rounded(this, Ui.PAPER, 7, 0xFFE5E0D2, 1));
            box.addView(line, topParams(4));
            TextView exp = Ui.text(this, q.explanation, 9.5f, Ui.MUTED_2, false);
            exp.setPadding(dp(7), dp(2), 0, dp(2));
            box.addView(exp);
        }
        return box;
    }

    private View resultQuestion(Question question) {
        LinearLayout box = Ui.column(this);
        LinearLayout head = Ui.row(this);
        TextView number = Ui.text(this, String.valueOf(question.number), 10, Ui.INK_SOFT, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(Ui.rounded(this, Ui.PAPER, 99, 0, 0));
        head.addView(number, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout text = Ui.column(this);
        text.addView(Ui.text(this, question.question, 12.5f, Ui.INK, true));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(8);
        head.addView(text, textParams);
        box.addView(head);

        Integer selected = answers.get(question.id);
        boolean correct = selected != null && selected == question.answer;
        String[] letters = {"A", "B", "C"};
        LinearLayout line = Ui.row(this);
        line.setPadding(dp(32), dp(8), 0, 0);
        TextView pill = Ui.text(this, correct ? "答对了" : "答错了", 9.5f, correct ? 0xFF2F6B43 : 0xFF9B3E32, true);
        pill.setPadding(dp(7), dp(3), dp(7), dp(3));
        pill.setBackground(Ui.rounded(this, correct ? 0xFFE8F4E3 : 0xFFFBE7E2, 99,
                correct ? 0xFFBCD8C1 : 0xFFECC4BB, 1));
        line.addView(pill);
        TextView answerText = Ui.text(this,
                (selected == null ? "未作答" : "你的答案：" + letters[selected]) +
                        "    正确答案：" + letters[question.answer] + ". " + question.options.get(question.answer),
                10, Ui.MUTED, false);
        LinearLayout.LayoutParams answerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        answerParams.leftMargin = dp(7);
        line.addView(answerText, answerParams);
        box.addView(line);

        TextView explanation = Ui.text(this, question.explanation, 10.5f, Ui.INK_SOFT, false);
        explanation.setLineSpacing(dp(3), 1.0f);
        explanation.setPadding(dp(10), dp(8), dp(10), dp(8));
        explanation.setBackground(Ui.rounded(this, Ui.PAPER, 0, 0, 0));
        LinearLayout.LayoutParams expParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        expParams.leftMargin = dp(32);
        expParams.topMargin = dp(7);
        box.addView(explanation, expParams);
        return box;
    }

    private void startTimer() {
        stopTimer();
        examStarted = SystemClock.elapsedRealtime();
        examElapsed = 0;
        if (timerView != null) timerView.setText("00:00");
        handler.post(timerRunnable);
    }

    private void stopTimer() {
        handler.removeCallbacks(timerRunnable);
    }

    private void confirmLeaveExam() {
        int answered = answeredCount();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("退出考试？")
                .setMessage("听力开始后不能回放。退出后可以批改已选题目，也可以直接退出。")
                .setNegativeButton("继续答题", null);
        if (answered > 0) {
            builder.setPositiveButton("批改已选（" + answered + "）并退出", (dialog, which) -> {
                exitedExam = true;
                showResult();
            });
            builder.setNeutralButton("直接退出", (dialog, which) -> showHome());
        } else {
            builder.setPositiveButton("直接退出", (dialog, which) -> showHome());
        }
        builder.show();
    }

    @Override
    public void onBackPressed() {
        if ("exam".equals(screen)) {
            confirmLeaveExam();
        } else if ("result".equals(screen)) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ audio
    private void startAudioSynthesis() {
        if (test == null || audioPreparing) return;
        final long request = ++audioRequest;
        final String testId = test.id;
        final File edgeFile = AudioStore.fileFor(this, test, config);
        final File localFile = AudioStore.localFileFor(this, test, config);
        if (edgeFile.exists() && edgeFile.length() > 2000) {
            prepareAudio(edgeFile);
            return;
        }
        if (localFile.exists() && localFile.length() > 2000) {
            prepareAudio(localFile);
            return;
        }
        audioPreparing = true;
        if (audioStatus != null) audioStatus.setText("正在合成整套考试音频，首次约 30-60 秒…");
        if (playButton != null) playButton.setText("…");
        final String text = ExamAudioTextBuilder.build(test, config);
        final int rate = ExamAudioTextBuilder.ratePercent(config.speed);
        ttsExecutor.execute(() -> {
            File result = edgeFile;
            try {
                new EdgeTtsClient().synthesize(text, edgeFile, rate);
            } catch (Exception edgeError) {
                if (!PiperModelManager.isInstalled(MainActivity.this)) {
                    runOnUiThread(() -> {
                        if (request != audioRequest) return;
                        audioPreparing = false;
                        if (audioStatus != null) {
                            audioStatus.setText("线上 Edge 失败，且本地模型未下载：" + shortMessage(edgeError));
                        }
                        if (playButton != null) playButton.setText("↻");
                    });
                    return;
                }
                runOnUiThread(() -> {
                    if (request == audioRequest && audioStatus != null) {
                        audioStatus.setText("线上 Edge 不可用，正在使用本地 Piper…");
                    }
                });
                try {
                    result = LocalPiperTts.synthesize(MainActivity.this, test, config, localFile);
                } catch (Exception localError) {
                    runOnUiThread(() -> {
                        if (request != audioRequest) return;
                        audioPreparing = false;
                        if (audioStatus != null) {
                            audioStatus.setText("线上 Edge 和本地 Piper 都失败：" + shortMessage(localError));
                        }
                        if (playButton != null) playButton.setText("↻");
                    });
                    return;
                }
            }
            final File finished = result;
            runOnUiThread(() -> {
                if (request == audioRequest) audioPreparing = false;
                if (request == audioRequest && test != null && testId.equals(test.id) && "exam".equals(screen)) {
                    if (audioStatus != null) audioStatus.setText(finished == localFile ? "本地 Piper 音频已就绪" : "音频已就绪");
                    prepareAudio(finished);
                }
            });
        });
    }

    private void prepareAudio(File file) {
        releasePlayer();
        audioFile = file;
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                audioDuration = mp.getDuration();
                if (audioStatus != null) audioStatus.setText(audioLocked ? "本场听力已开始，按考试规则不可回放" : "整套音频已就绪 · 点击开始（含英文试音）");
                if (audioTime != null) audioTime.setText("00:00 / " + formatTime(audioDuration / 1000));
                if (playButton != null) {
                    playButton.setText("▶");
                    playButton.setEnabled(!audioLocked);
                }
                if (audioSeek != null) {
                    audioSeek.setProgress(0);
                    startProgressUpdates();
                }
            });
            player.setOnCompletionListener(mp -> {
                if (audioStatus != null) audioStatus.setText("整套音频播放完毕 · 可以交卷");
                if (playButton != null) playButton.setText("▶");
                if (audioSeek != null) audioSeek.setProgress(1000);
                if (audioTime != null) audioTime.setText(formatTime(audioDuration / 1000) + " / " + formatTime(audioDuration / 1000));
            });
            player.setOnErrorListener((mp, what, extra) -> {
                if (audioStatus != null) audioStatus.setText("音频播放失败");
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            if (audioStatus != null) audioStatus.setText("音频加载失败：" + shortMessage(e));
        }
    }

    private void toggleAudio() {
        if (audioLocked) return;
        if (player == null) {
            if (audioFile != null && audioFile.exists()) {
                prepareAudio(audioFile);
            } else {
                startAudioSynthesis();
            }
            return;
        }
        try {
            if (!player.isPlaying()) {
                player.start();
                audioLocked = true;
                if (test != null) {
                    getSharedPreferences("echostep", MODE_PRIVATE).edit()
                            .putBoolean("audio_started_" + test.id, true).apply();
                }
                if (playButton != null) {
                    playButton.setText("❚❚");
                    playButton.setEnabled(false);
                }
                if (audioStatus != null) audioStatus.setText("考试音频播放中 · 不可回放");
            }
        } catch (Exception e) {
            if (audioStatus != null) audioStatus.setText("播放失败：" + shortMessage(e));
        }
    }

    private void startProgressUpdates() {
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && "exam".equals(screen)) {
                    try {
                        if (player.isPlaying()) {
                            int position = player.getCurrentPosition();
                            if (audioSeek != null && audioDuration > 0) {
                                audioSeek.setProgress((int) ((long) position * 1000L / audioDuration));
                            }
                            if (audioTime != null) {
                                audioTime.setText(formatTime(position / 1000) + " / " + formatTime(audioDuration / 1000));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    handler.postDelayed(this, 500);
                }
            }
        };
        handler.post(progressRunnable);
    }

    private void releasePlayer() {
        if (progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        audioDuration = 0;
        if (playButton != null) playButton.setText("▶");
    }

    // --------------------------------------------------------------- settings
    private void showSettings() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(18), dp(24), dp(18), dp(24));
        ScrollView scrollView = new ScrollView(this);
        LinearLayout panel = Ui.column(this);
        panel.setPadding(dp(20), dp(20), dp(20), dp(18));
        panel.setBackground(Ui.rounded(this, Ui.PAPER_CARD, 22, 0x33FFFFFF, 1));
        scrollView.addView(panel);
        wrapper.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(wrapper);

        LinearLayout head = Ui.row(this);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.label(this, "SETTINGS"));
        titles.addView(Ui.heading(this, "设置", 22));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "×", 24, Ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        panel.addView(head);

        panel.addView(sectionTitle("DeepSeek API"));
        EditText key = Ui.input(this, "sk-……");
        key.setText(config.apiKey);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSelection(key.getText().length());
        panel.addView(key, topParams(8));
        TextView keyHelp = Ui.text(this, "密钥只保存在本机 SharedPreferences，不会上传到第三方。", 9, Ui.MUTED_2, false);
        keyHelp.setPadding(0, dp(4), 0, dp(8));
        panel.addView(keyHelp);

        EditText base = Ui.input(this, "https://api.deepseek.com");
        base.setText(config.baseUrl);
        panel.addView(base, topParams(6));
        String[] models = {"deepseek-flash", "deepseek-v4-pro"};
        Spinner modelSpinner = spinner(java.util.Arrays.asList(models));
        modelSpinner.setSelection(Math.max(0, java.util.Arrays.asList(models).indexOf(config.model)));
        panel.addView(field("模型", modelSpinner, 1f), topParams(8));

        panel.addView(sectionTitle("本地语音（备用）"));
        TextView modelStatus = Ui.text(this, modelStatusText(), 9.5f, Ui.MUTED, false);
        modelStatus.setPadding(0, dp(4), 0, dp(4));
        panel.addView(modelStatus);
        LinearLayout modelActions = Ui.row(this);
        Button modelDownload = Ui.button(this, PiperModelManager.isInstalled(this) ? "重新下载模型" : "下载模型", false);
        modelDownload.setOnClickListener(v -> startModelDownload(modelStatus));
        modelActions.addView(modelDownload, new LinearLayout.LayoutParams(0, dp(38), 1f));
        Button modelDelete = Ui.button(this, "删除模型", false);
        modelDelete.setOnClickListener(v -> {
            PiperModelManager.delete(this);
            modelStatus.setText(modelStatusText());
            toast("本地模型已删除");
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        deleteParams.leftMargin = dp(8);
        modelActions.addView(modelDelete, deleteParams);
        panel.addView(modelActions, topParams(5));
        TextView modelHint = Ui.text(this, "优先使用线上 Edge；只有在 Edge 失败时才用本地 ncnn Piper。模型从 hf-mirror.com 下载。",
                8.5f, Ui.MUTED_2, false);
        modelHint.setPadding(0, dp(3), 0, dp(2));
        panel.addView(modelHint);

        panel.addView(sectionTitle("音频与联网"));
        TextView rule = Ui.text(this, "听力卷 只读中文节指令和英文材料，不朗读题干和选项。", 9, Ui.MUTED, false);
        rule.setPadding(0, dp(4), 0, dp(4));
        panel.addView(rule);
        panel.addView(toggleRow("整套音频包含试音", "固定英文麦克风测试", config.soundcheck,
                checked -> config.soundcheck = checked), topParams(4));
        panel.addView(toggleRow("联网搜索最新素材", "使用 DeepSeek 官方 Search API", config.webSearch,
                checked -> config.webSearch = checked), topParams(0));

        TextView result = Ui.text(this, "", 9.5f, Ui.MUTED, false);
        result.setPadding(0, dp(8), 0, 0);
        panel.addView(result);

        LinearLayout actions = Ui.row(this);
        Button testButton = Ui.button(this, "测试连接", false);
        testButton.setOnClickListener(v -> {
            String keyText = key.getText().toString().trim();
            String baseText = base.getText().toString().trim();
            if (keyText.isEmpty()) {
                toast("请先填写 API Key");
                return;
            }
            testButton.setEnabled(false);
            testButton.setText("连接中…");
            result.setText("");
            executor.execute(() -> {
                try {
                    DeepSeekClient client = new DeepSeekClient(baseText, keyText, models[modelSpinner.getSelectedItemPosition()]);
                    client.testConnection();
                    runOnUiThread(() -> {
                        result.setTextColor(Ui.GREEN_2);
                        result.setText("连接成功");
                        testButton.setEnabled(true);
                        testButton.setText("测试连接");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        result.setTextColor(Ui.RED);
                        result.setText(shortMessage(e));
                        testButton.setEnabled(true);
                        testButton.setText("测试连接");
                    });
                }
            });
        });
        actions.addView(testButton, new LinearLayout.LayoutParams(0, dp(40), 1f));
        Button save = Ui.button(this, "保存设置", true);
        save.setOnClickListener(v -> {
            config.apiKey = key.getText().toString().trim();
            config.baseUrl = base.getText().toString().trim();
            config.model = models[modelSpinner.getSelectedItemPosition()];
            config.save();
            dialog.dismiss();
            showHome();
            toast("设置已保存");
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        saveParams.leftMargin = dp(8);
        actions.addView(save, saveParams);
        panel.addView(actions, topParams(12));

        TextView clear = Ui.text(this, "清除本机密钥", 10, Ui.RED, true);
        clear.setPadding(0, dp(12), 0, 0);
        clear.setOnClickListener(v -> {
            config.apiKey = "";
            config.save();
            key.setText("");
            toast("本机密钥已清除");
        });
        panel.addView(clear);

        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.42f);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.copyFrom(window.getAttributes());
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(params);
            }
        });
        dialog.show();
    }

    private TextView sectionTitle(String value) {
        TextView text = Ui.text(this, value, 11, Ui.GREEN, true);
        text.setPadding(0, dp(16), 0, 0);
        return text;
    }

    // ---------------------------------------------------------------- history
    private void showHistory() {
        List<TestStore.Item> items = store.list();
        if (items.isEmpty()) {
            toast("还没有试卷，先生成一套吧");
            return;
        }
        String[] labels = new String[items.size()];
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        for (int i = 0; i < items.size(); i++) {
            TestStore.Item item = items.get(i);
            labels[i] = item.title + "\n" + item.grade + item.book + " · " + item.questionCount + " 题 · "
                    + format.format(new Date(item.createdAt));
        }
        new AlertDialog.Builder(this)
                .setTitle("历史试卷")
                .setItems(labels, (dialog, which) -> {
                    ListeningTest loaded = store.get(items.get(which).id);
                    if (loaded == null) {
                        toast("试卷读取失败");
                        return;
                    }
                    test = loaded;
                    answers.clear();
                    showExam();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ------------------------------------------------------------------ misc
    private String selectedUnitTitle() {
        List<UnitCatalog.Unit> units = catalog.units(grade, book);
        if (units.isEmpty()) return "";
        return units.get(Math.max(0, Math.min(unitIndex, units.size() - 1))).title;
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }

    private static String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String formatSpeed(float speed) {
        return String.format(Locale.US, "%.2f×", speed).replace(".00", ".0");
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return "未知错误";
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
            if (playButton != null) playButton.setText("▶");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        executor.shutdownNow();
        ttsExecutor.shutdownNow();
    }

    private interface SwitchListener {
        void onChanged(boolean checked);
    }

    private static class SimpleItemSelected implements android.widget.AdapterView.OnItemSelectedListener {
        private final java.util.function.IntConsumer consumer;

        SimpleItemSelected(java.util.function.IntConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            consumer.accept(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }

    private class TextAdapter extends BaseAdapter {
        private final List<String> values;

        TextAdapter(List<String> values) {
            this.values = values;
        }

        @Override public int getCount() { return values.size(); }
        @Override public Object getItem(int position) { return values.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
            view.setText(values.get(position));
            view.setTextSize(13);
            view.setTextColor(Ui.INK);
            view.setSingleLine(true);
            view.setEllipsize(android.text.TextUtils.TruncateAt.END);
            view.setPadding(dp(6), 0, dp(6), 0);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinHeight(dp(44));
            return view;
        }
    }
}
