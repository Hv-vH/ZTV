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
    static final int TEXT = 0xFFF2F5FC;
    static final int MUTED = 0xFF8E9DB5;
    static final int BLUE = 0xFF639BFF;
    static final int MINT = 0xFF59DFC3;
    private static final int FOCUSED_SECONDARY = 0xFFD9E8FF;

    private TvMenuViews() {}

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
                surface(context, 0xFF3478EB, 0xFF9CBFFF, 12));
        background.addState(new int[]{},
                surface(context, 0xFF1B2940, 0xFF32435F, 12));
        return background;
    }

    static TextView text(Context context, int size, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (medium) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    abstract static class FocusCard extends LinearLayout {
        boolean playing;

        FocusCard(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setGravity(Gravity.CENTER_VERTICAL);
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, Rect previous) {
            super.onFocusChanged(gainFocus, direction, previous);
            updateStyle();
        }

        final void updateStyle() {
            boolean focused = hasFocus();
            int[] colors = focused ? new int[]{0xFF377FF4, 0xFF2965CF}
                    : playing ? new int[]{0xFF153B3D, 0xFF112B30}
                    : new int[]{0xFF172236, 0xFF141E30};
            GradientDrawable background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, colors);
            background.setCornerRadius(dp(getContext(), 12));
            background.setStroke(dp(getContext(), focused ? 1.5f : 1f),
                    focused ? 0xFF9BBFFF : playing ? 0xFF2B6863 : 0xFF223149);
            setBackground(background);
            setElevation(dp(getContext(), focused ? 5 : 0));
            updateContentColors(focused);
        }

        abstract void updateContentColors(boolean focused);
    }

    static final class ChannelCard extends FocusCard {
        private final TextView number;
        private final TextView name;
        private final TextView program;
        private final TextView badge;

        ChannelCard(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
            setMinimumHeight(dp(context, 72));
            number = text(context, 14, MUTED, true);
            number.setGravity(Gravity.CENTER);
            addView(number, new LayoutParams(dp(context, 30), LayoutParams.MATCH_PARENT));
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(VERTICAL);
            content.setPadding(dp(context, 10), 0, 0, 0);
            addView(content, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout heading = new LinearLayout(context);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            name = text(context, 18, TEXT, true);
            heading.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            badge = text(context, 10, MINT, true);
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
            number.setText(getContext().getString(R.string.tv_channel_number, index + 1));
            name.setText(channelName);
            program.setText(programName);
            badge.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
            setContentDescription(getContext().getString(R.string.channel_row,
                    index + 1, channelName, programName));
            updateStyle();
        }

        @Override
        void updateContentColors(boolean focused) {
            if (name == null) {
                return;
            }
            number.setTextColor(focused ? FOCUSED_SECONDARY : playing ? MINT : MUTED);
            name.setTextColor(TEXT);
            program.setTextColor(focused ? FOCUSED_SECONDARY : MUTED);
            badge.setTextColor(focused ? TEXT : MINT);
            badge.setBackground(surface(getContext(),
                    focused ? 0x33203970 : 0xFF174D45, 0, 5));
        }
    }

    static final class ProgramCard extends FocusCard {
        private final TextView start;
        private final TextView end;
        private final TextView title;
        private final TextView badge;
        private final ProgressBar progress;
        private boolean past;

        ProgramCard(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(dp(context, 16), dp(context, 12), dp(context, 14), dp(context, 12));
            setMinimumHeight(dp(context, 68));
            LinearLayout times = new LinearLayout(context);
            times.setOrientation(VERTICAL);
            times.setGravity(Gravity.CENTER_VERTICAL);
            start = text(context, 19, TEXT, true);
            end = text(context, 12, MUTED, false);
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
            progress.setProgressTintList(ColorStateList.valueOf(MINT));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2B5151));
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
            past = hasEnded;
            start.setText(startTime);
            end.setText(endTime);
            title.setText(programTitle);
            badge.setText(stateLabel);
            progress.setVisibility(playing ? View.VISIBLE : View.GONE);
            progress.setProgress(completed);
            setContentDescription(getContext().getString(R.string.guide_program_row,
                    startTime, endTime, stateLabel, programTitle));
            updateStyle();
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
            title.setTextColor(focused || !past ? TEXT : 0xFFA8B5CA);
            start.setTextColor(focused ? TEXT : playing ? MINT : past ? MUTED : TEXT);
            end.setTextColor(focused ? FOCUSED_SECONDARY : MUTED);
            badge.setTextColor(focused ? TEXT : playing ? MINT : MUTED);
            badge.setBackground(surface(getContext(),
                    focused ? 0x33203970 : playing ? 0xFF174D45 : 0xFF202E44, 0, 6));
            progress.setProgressTintList(ColorStateList.valueOf(focused ? 0xFFD5EBFF : MINT));
        }
    }
}
