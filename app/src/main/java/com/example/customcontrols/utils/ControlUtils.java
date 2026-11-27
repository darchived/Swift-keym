package com.example.customcontrols.utils;

import android.content.Context;
import android.util.DisplayMetrics;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Self-contained utility helpers replacing MojoLauncher's Tools & Prefs classes.
 */
public final class ControlUtils {

    private ControlUtils() {
    }

    public static float dpToPx(Context context, float dp) {
        if (context == null) return dp * 2.5f;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return dp * metrics.density;
    }

    public static float pxToDp(Context context, float px) {
        if (context == null) return px / 2.5f;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return px / Math.max(1f, metrics.density);
    }

    public static int getScreenWidth(Context context) {
        if (context == null) return 1920;
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight(Context context) {
        if (context == null) return 1080;
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * Evaluates a MojoLauncher / PojavLauncher dynamic coordinate formula such as
     * "${screen_width} * 0.85 - ${width} / 2" or "120".
     */
    public static float evaluateDynamicExpression(
            String expr,
            float screenWidth,
            float screenHeight,
            float controlWidth,
            float controlHeight,
            float marginPx
    ) {
        if (expr == null || expr.trim().isEmpty()) {
            return 0f;
        }
        String sanitized = expr
                .replace("${screen_width}", "screen_width")
                .replace("${screen_height}", "screen_height")
                .replace("${width}", "width")
                .replace("${height}", "height")
                .replace("${preferred_width}", "preferred_width")
                .replace("${preferred_height}", "preferred_height")
                .replace("${bar_height}", "bar_height")
                .replace("${margin}", "margin")
                .replace("${left}", "0")
                .replace("${top}", "0")
                .replace("${right}", "screen_width")
                .replace("${bottom}", "screen_height")
                .trim();

        try {
            Map<String, Double> vars = new HashMap<>();
            vars.put("screen_width", (double) screenWidth);
            vars.put("screen_height", (double) screenHeight);
            vars.put("width", (double) controlWidth);
            vars.put("height", (double) controlHeight);
            vars.put("preferred_width", (double) controlWidth);
            vars.put("preferred_height", (double) controlHeight);
            vars.put("bar_height", 0.0);
            vars.put("margin", (double) marginPx);

            Expression e = new ExpressionBuilder(sanitized)
                    .variables(vars.keySet())
                    .build()
                    .setVariables(vars);
            double result = e.evaluate();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return 0f;
            }
            return (float) result;
        } catch (Throwable ignored) {
            try {
                return Float.parseFloat(sanitized);
            } catch (NumberFormatException e2) {
                return 0f;
            }
        }
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
