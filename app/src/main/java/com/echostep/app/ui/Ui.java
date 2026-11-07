package com.echostep.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int PAPER = 0xFFF3F0E8;
    public static final int PAPER_CARD = 0xFFFFFDF8;
    public static final int INK = 0xFF18251F;
    public static final int INK_SOFT = 0xFF34463C;
    public static final int GREEN = 0xFF1E4D3B;
    public static final int GREEN_2 = 0xFF2C6A52;
    public static final int GREEN_3 = 0xFF49866C;
    public static final int MINT = 0xFFDFEAD3;
    public static final int MINT_SOFT = 0xFFEEF4E4;
    public static final int ORANGE = 0xFFDF7539;
    public static final int ORANGE_DEEP = 0xFFB95A28;
    public static final int ORANGE_SOFT = 0xFFF7E2D1;
    public static final int RED = 0xFFB94A3C;
    public static final int RED_SOFT = 0xFFF8E2DE;
    public static final int MUTED = 0xFF6E786F;
    public static final int MUTED_2 = 0xFF90988F;
    public static final int LINE = 0xFFE2DCCD;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(context, 3), 1f);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        return view;
    }

    public static TextView heading(Context context, String value, float size) {
        TextView view = text(context, value, size, INK, true);
        view.setLetterSpacing(-0.02f);
        return view;
    }

    public static TextView label(Context context, String value) {
        TextView view = text(context, value, 10.5f, MUTED, true);
        view.setLetterSpacing(0.08f);
        return view;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout card(Context context) {
        LinearLayout layout = column(context);
        layout.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        layout.setBackground(rounded(context, PAPER_CARD, 20, 0xFFDCD5C6, 1));
        return layout;
    }

    public static GradientDrawable rounded(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * 2f);
        if (strokeDp > 0) {
            drawable.setStroke((int) strokeDp, strokeColor);
        }
        return drawable;
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(context, strokeDp), strokeColor);
        }
        return drawable;
    }

    public static GradientDrawable hero(Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF173E2F, 0xFF1E4D3B, 0xFF2C6A52});
        drawable.setCornerRadius(dp(context, 24));
        return drawable;
    }

    public static Button button(Context context, String value, boolean primary) {
        Button button = new Button(context);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(primary ? 14f : 12.5f);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setTextColor(primary ? 0xFFF8F3E6 : INK_SOFT);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setStateListAnimator(null);
        GradientDrawable content = rounded(context, primary ? GREEN : 0xFFFBF8F0,
                primary ? 13 : 12, primary ? GREEN : 0xFFD3CBB8, 1);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(primary ? 0x33FFFFFF : 0x221E4D3B),
                content, null);
        button.setBackground(ripple);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48));
        button.setLayoutParams(params);
        return button;
    }

    public static TextView chip(Context context, String value) {
        TextView chip = text(context, value, 12, MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        chip.setBackground(rounded(context, 0xFFFAF7EF, 10, LINE, 1));
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
    }

    public static void styleChip(TextView chip, boolean selected) {
        if (selected) {
            chip.setTextColor(0xFFF8F3E6);
            chip.setBackground(rounded(chip.getContext(), GREEN, 10, GREEN, 1));
        } else {
            chip.setTextColor(MUTED);
            chip.setBackground(rounded(chip.getContext(), 0xFFFAF7EF, 10, LINE, 1));
        }
    }

    public static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(MUTED_2);
        input.setTextColor(INK);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        input.setBackground(rounded(context, 0xFFFDFBF5, 11, LINE, 1));
        input.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46)));
        return input;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.topMargin = dp(context, 14);
        params.bottomMargin = dp(context, 14);
        view.setLayoutParams(params);
        return view;
    }

    public static void margin(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            params.setMargins(dp(view.getContext(), left), dp(view.getContext(), top),
                    dp(view.getContext(), right), dp(view.getContext(), bottom));
            view.setLayoutParams(params);
        }
    }

    public static void weight(View view, float weight) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) raw).weight = weight;
            ((LinearLayout.LayoutParams) raw).width = 0;
            view.setLayoutParams(raw);
        }
    }

    public static void fullWidth(View view, int heightDp) {
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(view.getContext(), heightDp)));
    }

    public static void setSelectableBackground(View view, boolean selected, boolean correct, boolean wrong) {
        int fill;
        int stroke;
        if (correct) {
            fill = 0xFFEDF7E9;
            stroke = 0xFF5C9870;
        } else if (wrong) {
            fill = 0xFFFDF0ED;
            stroke = 0xFFD69A8E;
        } else if (selected) {
            fill = 0xFFEEF6E6;
            stroke = GREEN_2;
        } else {
            fill = 0xFFFDFBF5;
            stroke = LINE;
        }
        view.setBackground(rounded(view.getContext(), fill, 11, stroke, selected || correct || wrong ? 1.5f : 1));
    }

    public static void lightNavigationBar(android.view.Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
}
