package com.brennan.nowplayingarchive;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import java.util.Locale;

/** Applies the Google Material 3 variable typography used by Pixel Now Playing. */
final class PixelTypography {
    private static Typeface googleSansFlex;

    private PixelTypography() {}

    static void apply(TextView view, float sizeSp, int weight, boolean rounded) {
        view.setTextSize(sizeSp);
        view.setTypeface(font(view.getContext()));
        view.setLetterSpacing(0);
        view.setFontVariationSettings(String.format(Locale.US,
                "'GRAD' 0, 'ROND' %d, 'opsz' %.1f, 'slnt' 0, 'wdth' 100, 'wght' %d",
                rounded ? 100 : 0, sizeSp, weight));
    }

    private static synchronized Typeface font(Context context) {
        if (googleSansFlex == null) {
            try {
                googleSansFlex = context.getResources().getFont(
                        R.font.google_sans_flex_variable);
            } catch (RuntimeException unavailable) {
                googleSansFlex = Typeface.create("google-sans-text", Typeface.NORMAL);
            }
        }
        return googleSansFlex;
    }
}
