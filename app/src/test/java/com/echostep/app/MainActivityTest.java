package com.echostep.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

import com.echostep.app.data.SampleTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {
    @Test
    public void homeScreenBuilds() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        View content = activity.findViewById(android.R.id.content);
        assertNotNull(content);
        assertTrue(containsText(content, "EchoStep"));
        assertTrue(containsText(content, "生成听力试卷"));
    }

    @Test
    public void settingsDialogOpens() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Method method = MainActivity.class.getDeclaredMethod("showSettings");
        method.setAccessible(true);
        method.invoke(activity);
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
    }

    @Test
    public void resultScreenBuilds() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Field testField = MainActivity.class.getDeclaredField("test");
        testField.setAccessible(true);
        testField.set(activity, SampleTest.create());
        Method method = MainActivity.class.getDeclaredMethod("showResult");
        method.setAccessible(true);
        method.invoke(activity);
        View content = activity.findViewById(android.R.id.content);
        assertTrue(containsText(content, "本卷成绩"));
        assertTrue(containsText(content, "答对了") || containsText(content, "答错了"));
    }

    @Test
    public void examScreenBuilds() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Field testField = MainActivity.class.getDeclaredField("test");
        testField.setAccessible(true);
        testField.set(activity, SampleTest.create());
        Method method = MainActivity.class.getDeclaredMethod("renderExamForTest");
        method.setAccessible(true);
        method.invoke(activity);
        View content = activity.findViewById(android.R.id.content);
        assertTrue(containsText(content, "考试音频"));
        assertTrue(containsText(content, "玛丽假期去了哪里？"));
        assertTrue(containsText(content, "已答 0 / 5"));
        assertTrue(containsText(content, "交卷并查看解析"));
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void captureExamScreenshot() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Field testField = MainActivity.class.getDeclaredField("test");
        testField.setAccessible(true);
        testField.set(activity, SampleTest.create());
        Method method = MainActivity.class.getDeclaredMethod("renderExamForTest");
        method.setAccessible(true);
        method.invoke(activity);
        capture(activity, 1080, 4200, "exam.png");
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void captureSettingsScreenshot() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Method method = MainActivity.class.getDeclaredMethod("showSettings");
        method.setAccessible(true);
        method.invoke(activity);
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        android.view.Window window = dialog.getWindow();
        assertNotNull(window);
        View decor = window.getDecorView();
        int width = 1080;
        int height = 2400;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        decor.draw(canvas);
        File dir = new File("build/screenshots");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, "settings.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void captureResultScreenshot() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Field testField = MainActivity.class.getDeclaredField("test");
        testField.setAccessible(true);
        testField.set(activity, SampleTest.create());
        Field answersField = MainActivity.class.getDeclaredField("answers");
        answersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> answers = (java.util.Map<String, Integer>) answersField.get(activity);
        answers.put("q1", 0);
        answers.put("q2", 1);
        answers.put("q3", 1);
        answers.put("q4", 1);
        answers.put("q5", 2);
        Method method = MainActivity.class.getDeclaredMethod("showResult");
        method.setAccessible(true);
        method.invoke(activity);
        capture(activity, 1080, 3200, "result.png");
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void captureHomeScreenshot() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        capture(activity, 1080, 2400, "home.png");
    }

    private static void capture(MainActivity activity, int width, int height, String name) throws Exception {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        decor.draw(canvas);
        File dir = new File("build/screenshots");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        assertTrue(new File(dir, name).length() > 1000);
    }

    private static boolean containsText(View view, String needle) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().contains(needle)) return true;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), needle)) return true;
            }
        }
        return false;
    }
}
