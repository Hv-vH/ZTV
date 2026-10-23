package com.example.cztvtv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Native TV cards share the same focus treatment and keep navigation in MainActivity. */
final class TvMenuViews {
    static final int BACKGROUND = 0xFF141615;
    static final int PANEL = 0xFF1A1E1B;
    static final int BORDER = 0xFF30372F;
    static final int TEXT = 0xFFF4F1E8;
    static final int MUTED = 0xFFA9AEA7;
    static final int AMBER = 0xFFDDB477;
    static final int INK = 0xFF1B211C;
    private static final int FOCUSED_SECONDARY = 0xFF535C51;
    private static Typeface regular;
    private static Typeface medium;
    private static Typeface numbers;
    private static final ColorStateList LIVE_PROGRESS = ColorStateList.valueOf(AMBER);
    private static final ColorStateList FOCUS_PROGRESS = ColorStateList.valueOf(0xFF846738);
    private static final ColorStateList LIVE_TRACK = ColorStateList.valueOf(0xFF514735);
    private static final ColorStateList FOCUS_TRACK = ColorStateList.valueOf(0xFFD1C8B6);

    private TvMenuViews() {}

    static void prepare(Context context) {
        if (regular != null) {
            return;
        }
        regular = Typeface.createFromAsset(context.getAssets(), "fonts/ztv_han_regular.ttf");
        medium = Typeface.createFromAsset(context.getAssets(), "fonts/ztv_han_medium.ttf");
        numbers = Typeface.createFromAsset(context.getAssets(), "fonts/plex_sans_regular.ttf");
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable surface(Context context, int color, int stroke, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != 0) {
            drawable.setStroke(dp(context, 1), stroke);
        }
        return drawable;
    }

    static StateListDrawable buttonBackground(Context context) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},
                surface(context, TEXT, TEXT, 12));
        background.addState(new int[]{},
                surface(context, 0xFF252B25, 0xFF444C42, 12));
        return background;
    }

    static TextView text(Context context, int size, int color, boolean emphasized) {
        prepare(context);
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(emphasized ? medium : regular);
        return view;
    }

    static TextView numberText(Context context, int size, int color) {
        TextView view = text(context, size, color, false);
        view.setTypeface(numbers);
        view.setFontFeatureSettings("'tnum'");
        return view;
    }

    abstract static class FocusCard extends LinearLayout {
        boolean playing;
        private final GradientDrawable[] backgrounds = new GradientDrawable[3];
        private int appearance = -1;

        FocusCard(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setGravity(Gravity.CENTER_VERTICAL);
            int[] colors = {0xFF202521, 0xFF29281F, TEXT};
            int[] strokes = {0, 0xFF68583C, TEXT};
            for (int i = 0; i < backgrounds.length; i++) {
                backgrounds[i] = surface(context, colors[i], strokes[i], 12);
            }
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, Rect previous) {
            super.onFocusChanged(gainFocus, direction, previous);
            updateStyle();
        }

        final void updateStyle() {
            updateStyle(false);
        }

        final void updateStyle(boolean contentColorsChanged) {
            boolean focused = hasFocus();
            int next = focused ? 2 : playing ? 1 : 0;
            if (appearance != next) {
                appearance = next;
                setBackground(backgrounds[next]);
                updateContentColors(focused);
            } else if (contentColorsChanged) {
                updateContentColors(focused);
            }
        }

        abstract void updateContentColors(boolean focused);
    }

    static final class ChannelCard extends FocusCard {
        private final TextView number;
        private final TextView name;
        private final TextView program;
        private final TextView badge;
        private final GradientDrawable focusBadge;
        private final GradientDrawable liveBadge;

        ChannelCard(Context context) {
            super(context);
            focusBadge = surface(context, 0xFFDDD8CA, 0, 5);
            liveBadge = surface(context, 0xFF3B3529, 0, 5);
            setOrientation(HORIZONTAL);
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
            setMinimumHeight(dp(context, 72));
            number = numberText(context, 15, MUTED);
            number.setGravity(Gravity.CENTER);
            addView(number, new LayoutParams(dp(context, 30), LayoutParams.MATCH_PARENT));
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(VERTICAL);
            content.setPadding(dp(context, 10), 0, 0, 0);
            addView(content, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout heading = new LinearLayout(context);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            name = text(context, 19, TEXT, true);
            heading.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            badge = text(context, 10, AMBER, true);
            badge.setText(R.string.tv_watching);
            badge.setPadding(dp(context, 6), dp(context, 3), dp(context, 6), dp(context, 3));
            heading.addView(badge);
            content.addView(heading);
            program = text(context, 12, MUTED, false);
            program.setMaxLines(2);
            program.setEllipsize(TextUtils.TruncateAt.END);
            program.setLineSpacing(dp(context, 2), 1f);
            LayoutParams subtitle = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            subtitle.topMargin = dp(context, 7);
            content.addView(program, subtitle);
        }

        void bind(int index, String channelName, String programName, boolean isPlaying) {
            playing = isPlaying;
            setTextIfChanged(number, getContext().getString(R.string.tv_channel_number, index + 1));
            setTextIfChanged(name, channelName);
            setTextIfChanged(program, programName);
            badge.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
            String description = getContext().getString(R.string.channel_row,
                    index + 1, channelName, programName);
            if (!TextUtils.equals(description, getContentDescription())) {
                setContentDescription(description);
            }
            updateStyle();
        }

        @Override
        void updateContentColors(boolean focused) {
            if (name == null) {
                return;
            }
            number.setTextColor(focused ? FOCUSED_SECONDARY : playing ? AMBER : MUTED);
            name.setTextColor(focused ? INK : TEXT);
            program.setTextColor(focused ? FOCUSED_SECONDARY : MUTED);
            badge.setTextColor(focused ? 0xFF50452E : AMBER);
            badge.setBackground(focused ? focusBadge : liveBadge);
        }
    }

    static final class ProgramCard extends FocusCard {
        private final TextView start;
        private final TextView end;
        private final TextView title;
        private final TextView badge;
        private final ProgressBar progress;
        private final GradientDrawable focusBadge;
        private final GradientDrawable liveBadge;
        private final GradientDrawable idleBadge;
        private boolean past;

        ProgramCard(Context context) {
            super(context);
            focusBadge = surface(context, 0xFFDDD8CA, 0, 6);
            liveBadge = surface(context, 0xFF3C3528, 0, 6);
            idleBadge = surface(context, 0xFF2B322B, 0, 6);
            setOrientation(HORIZONTAL);
            setPadding(dp(context, 16), dp(context, 12), dp(context, 14), dp(context, 12));
            setMinimumHeight(dp(context, 68));
            LinearLayout times = new LinearLayout(context);
            times.setOrientation(VERTICAL);
            times.setGravity(Gravity.CENTER_VERTICAL);
            start = numberText(context, 21, TEXT);
            end = numberText(context, 13, MUTED);
            times.addView(start);
            LayoutParams endParams = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            endParams.topMargin = dp(context, 5);
            times.addView(end, endParams);
            addView(times, new LayoutParams(dp(context, 66), LayoutParams.WRAP_CONTENT));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(VERTICAL);
            content.setPadding(dp(context, 12), 0, dp(context, 14), 0);
            addView(content, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            title = text(context, 18, TEXT, true);
            title.setLineSpacing(dp(context, 3), 1f);
            content.addView(title);
            progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setIndeterminate(false);
            progress.setProgressTintList(LIVE_PROGRESS);
            progress.setProgressBackgroundTintList(LIVE_TRACK);
            LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(context, 3));
            progressParams.topMargin = dp(context, 10);
            content.addView(progress, progressParams);

            badge = text(context, 11, MUTED, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5));
            addView(badge);
        }

        void bind(String programTitle, String startTime, String endTime, String stateLabel,
                  boolean isPlaying, boolean hasEnded, int completed) {
            playing = isPlaying;
            boolean contentColorsChanged = past != hasEnded;
            past = hasEnded;
            setTextIfChanged(start, startTime);
            setTextIfChanged(end, endTime);
            setTextIfChanged(title, programTitle);
            setTextIfChanged(badge, stateLabel);
            progress.setVisibility(playing ? View.VISIBLE : View.GONE);
            progress.setProgress(completed);
            setContentDescription(getContext().getString(R.string.guide_program_row,
                    startTime, endTime, stateLabel, programTitle));
            updateStyle(contentColorsChanged);
        }

        void updateProgress(int completed) {
            if (playing) {
                progress.setProgress(completed);
            }
        }

        @Override
        void updateContentColors(boolean focused) {
            if (title == null) {
                return;
            }
            title.setTextColor(focused ? INK : past ? MUTED : TEXT);
            start.setTextColor(focused ? INK : playing ? AMBER : past ? MUTED : TEXT);
            end.setTextColor(focused ? FOCUSED_SECONDARY : MUTED);
            badge.setTextColor(focused ? 0xFF50452E : playing ? AMBER : MUTED);
            badge.setBackground(focused ? focusBadge : playing ? liveBadge : idleBadge);
            progress.setProgressTintList(focused ? FOCUS_PROGRESS : LIVE_PROGRESS);
            progress.setProgressBackgroundTintList(focused ? FOCUS_TRACK : LIVE_TRACK);
        }
    }

    static void setTextIfChanged(TextView view, CharSequence text) {
        if (!TextUtils.equals(view.getText(), text)) {
            view.setText(text);
        }
    }
}
