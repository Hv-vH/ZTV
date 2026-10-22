package com.example.cztvtv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A native cover stays above the website until the first video frame is ready. */
final class TvLoadingView extends FrameLayout {
    private final TextView channel;
    private final TextView message;
    private final TextView count;
    private final View line;
    private int displayedIndex = -1;
    private int displayedTotal;

    TvLoadingView(Context context) {
        super(context);
        setBackgroundColor(TvMenuViews.BACKGROUND);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        TextView brand = TvMenuViews.text(context, 18, TvMenuViews.TEXT, true);
        brand.setText(R.string.app_name);
        LayoutParams brandParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        brandParams.gravity = Gravity.TOP | Gravity.START;
        brandParams.setMargins(dp(32), dp(29), 0, 0);
        addView(brand, brandParams);

        count = TvMenuViews.numberText(context, 14, TvMenuViews.MUTED);
        LayoutParams countParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        countParams.gravity = Gravity.TOP | Gravity.END;
        countParams.setMargins(0, dp(32), dp(32), 0);
        addView(count, countParams);

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.brand_mark);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        logoParams.bottomMargin = dp(23);
        center.addView(logo, logoParams);
        channel = TvMenuViews.text(context, 28, TvMenuViews.TEXT, true);
        channel.setGravity(Gravity.CENTER);
        center.addView(channel);
        message = TvMenuViews.text(context, 15, TvMenuViews.MUTED, false);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(5), 1f);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(12);
        center.addView(message, messageParams);
        FrameLayout progress = new FrameLayout(context);
        progress.setBackgroundColor(0xFF343B32);
        View segment = new View(context);
        segment.setBackgroundColor(TvMenuViews.AMBER);
        LayoutParams segmentParams = new LayoutParams(dp(44), LayoutParams.MATCH_PARENT);
        segmentParams.leftMargin = dp(46);
        progress.addView(segment, segmentParams);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(156), dp(2));
        progressParams.topMargin = dp(28);
        center.addView(progress, progressParams);
        line = progress;
        LayoutParams centerParams = new LayoutParams(dp(420), LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(center, centerParams);

        LinearLayout bottom = new LinearLayout(context);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = TvMenuViews.text(context, 12, TvMenuViews.AMBER, false);
        live.setText(R.string.loading_live_label);
        Drawable signal = context.getDrawable(R.drawable.live_signal);
        signal.setBounds(0, 0, dp(21), dp(21));
        live.setCompoundDrawables(signal, null, null, null);
        live.setCompoundDrawablePadding(dp(6));
        bottom.addView(live);
        TextView remote = TvMenuViews.text(context, 12, TvMenuViews.MUTED, false);
        remote.setText(R.string.loading_remote_hint);
        remote.setGravity(Gravity.END);
        bottom.addView(remote, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LayoutParams bottomParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = Gravity.BOTTOM;
        bottomParams.setMargins(dp(32), 0, dp(32), dp(32));
        addView(bottom, bottomParams);
    }

    void show(String name, int index, int total, int messageId, boolean failed) {
        TvMenuViews.setTextIfChanged(channel, name);
        TvMenuViews.setTextIfChanged(message, getContext().getText(messageId));
        if (displayedIndex != index || displayedTotal != total) {
            displayedIndex = index;
            displayedTotal = total;
            String prefix = getContext().getString(R.string.tv_channel_number, index + 1);
            SpannableString label = new SpannableString(prefix + " / " + total);
            label.setSpan(new ForegroundColorSpan(TvMenuViews.AMBER), 0, prefix.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            count.setText(label);
        }
        line.setVisibility(failed ? View.INVISIBLE : View.VISIBLE);
        setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return TvMenuViews.dp(getContext(), value);
    }
}
