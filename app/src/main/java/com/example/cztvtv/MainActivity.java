package com.example.cztvtv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String LIVE_PAGE_PREFIX = "https://www.cztv.com/liveTV/";

    // 页面频道顺序与浙江网络电视公开频道列表一致；code 是页面使用的真实 station_code。
    private static final Channel[] CHANNELS = {
            new Channel("101", "浙江卫视"),
            new Channel("102", "钱江都市"),
            new Channel("103", "经济生活"),
            new Channel("104", "教科影视"),
            new Channel("106", "民生休闲"),
            new Channel("107", "新闻"),
            new Channel("108", "少儿频道"),
            new Channel("110", "浙江国际"),
            new Channel("111", "好易购"),
            new Channel("112", "之江纪录")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);
    private final List<TvMenuViews.ChannelCard> channelRows = new ArrayList<>();

    private WebView webView;
    private TextView status;
    private TvLoadingView loadingView;
    private String playerScript;
    private Runnable videoRetry;
    private int playbackCheckGeneration;
    private boolean videoReady;
    private boolean playbackFailed;
    private LinearLayout channelMenu;
    private ProgramGuideManager programGuideManager;
    private TextView guideTitle;
    private TextView guideDate;
    private TextView guideMessage;
    private TextView guideFooter;
    private TextView guideRefresh;
    private ProgramListView guideList;
    private ProgramAdapter programAdapter;
    private ScrollView channelScroll;
    private TextView menuClock;
    private TextView menuDay;
    private TextView watchingInfo;
    private TextView navigationHint;
    private ProgramGuide renderedGuide;
    private int renderedChannelIndex = -1;
    private long nextProgramRowUpdate;
    private long lastProgramRenderTime;
    private boolean guideFocused;
    private boolean pendingProgramFocus;
    private boolean menuUpdatePosted;
    private boolean foregroundActive;
    private int selectedProgramIndex = -1;
    private ProgramGuide.Program programBeforeRefresh;
    private boolean menuVisible;
    private boolean exiting;
    private boolean hasEnteredForeground;
    private boolean reloadOnResume;
    private int currentChannelIndex = 4; // 默认打开 106：民生休闲
    private int selectedChannelIndex = 4;
    private int loadGeneration;

    private final Runnable updateVisibleMenu = () -> {
        menuUpdatePosted = false;
        if (menuVisible && foregroundActive && !exiting) {
            updateMenuRows();
            renderProgramGuide();
        }
    };

    private final Runnable hideStatus = () -> {
        if (status != null) {
            status.setVisibility(View.GONE);
        }
    };

    private final Runnable refreshPrograms = new Runnable() {
        @Override
        public void run() {
            if (!foregroundActive || exiting || programGuideManager == null) {
                return;
            }
            refreshProgramGuide(false);
        }
    };

    private static final class Channel {
        final String code;
        final String name;

        Channel(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        applyImmersiveMode();
        programGuideManager = new ProgramGuideManager(
                new ProgramGuideRepository(), backgroundExecutor, handler::post,
                new ProgramGuideManager.Clock() {
                    @Override
                    public long wallTime() {
                        return System.currentTimeMillis();
                    }

                    @Override
                    public long elapsedTime() {
                        return SystemClock.elapsedRealtime();
                    }
                }, this::onProgramGuideChanged
        );
        TvMenuViews.prepare(this);
        playerScript = loadPlayerScript();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webView = createWebView();
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        loadingView = new TvLoadingView(this);
        root.addView(loadingView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        loadingView.show(currentChannel().name, currentChannelIndex, CHANNELS.length,
                R.string.loading_connecting, false);

        status = createStatusView();
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.gravity = Gravity.TOP | Gravity.START;
        statusParams.setMargins(dp(28), dp(24), dp(28), 0);
        root.addView(status, statusParams);

        channelMenu = createChannelMenu();
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        menuParams.gravity = Gravity.START;
        root.addView(channelMenu, menuParams);

        setContentView(root);
        status.setVisibility(View.GONE);
        channelMenu.setVisibility(View.GONE);
        webView.requestFocus(View.FOCUS_DOWN);
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);

        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setMediaPlaybackRequiresUserGesture(false);
        view.getSettings().setUseWideViewPort(true);
        view.getSettings().setLoadWithOverviewMode(true);
        view.getSettings().setSupportZoom(false);
        view.getSettings().setBuiltInZoomControls(false);
        view.getSettings().setDisplayZoomControls(false);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                if (!isCurrentChannelPage(url)) {
                    return;
                }
                beginLoading(R.string.loading_connecting);
                // 页面加载期间先保持黑屏，避免把网页头部、频道列表短暂显示到电视上。
                v.evaluateJavascript(
                        "(function(){document.documentElement.style.background='#000';"
                                + "if(document.body){document.body.style.visibility='hidden';"
                                + "document.body.style.background='#000';}})()",
                        null
                );
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (isCurrentChannelPage(url) && !playbackFailed) {
                    startVideoCheck(false);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    return false;
                }
                String host = request.getUrl().getHost();
                if (host == null || host.equals("www.cztv.com") || host.endsWith(".cztv.com")) {
                    return false;
                }
                return true;
            }

            @Override
            public void onReceivedError(
                    WebView v,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error
            ) {
                if (request.isForMainFrame()
                        && isCurrentChannelPage(request.getUrl().toString())) {
                    showPlaybackFailure();
                }
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest request,
                                            WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400
                        && isCurrentChannelPage(request.getUrl().toString())) {
                    showPlaybackFailure();
                }
            }
        });

        android.graphics.Bitmap emptyPoster = android.graphics.Bitmap.createBitmap(
                1, 1, android.graphics.Bitmap.Config.ARGB_8888);
        emptyPoster.eraseColor(Color.BLACK);
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public android.graphics.Bitmap getDefaultVideoPoster() {
                return emptyPoster;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("CztvTvPlayer", consoleMessage.message());
                }
                return true;
            }
        });

        view.loadUrl(channelUrl(currentChannelIndex));
        return view;
    }

    private TextView createStatusView() {
        TextView view = TvMenuViews.text(this, 14, TvMenuViews.TEXT, false);
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        view.setBackground(TvMenuViews.surface(this, 0xF21A1E1B, TvMenuViews.BORDER, 12));
        android.graphics.drawable.Drawable signal = getDrawable(R.drawable.live_signal);
        signal.setBounds(0, 0, dp(18), dp(18));
        view.setCompoundDrawables(signal, null, null, null);
        view.setCompoundDrawablePadding(dp(8));
        return view;
    }

    private LinearLayout createChannelMenu() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(32), dp(22), dp(32), dp(16));
        overlay.setBackgroundColor(TvMenuViews.BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.brand_mark);
        header.addView(brand, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(12), 0, 0, 0);
        TextView brandName = TvMenuViews.text(this, 22, TvMenuViews.TEXT, true);
        brandName.setText(R.string.app_name);
        brandText.addView(brandName);
        TextView brandSubtitle = menuText(12, TvMenuViews.MUTED);
        brandSubtitle.setText(R.string.tv_brand_subtitle);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(5);
        brandText.addView(brandSubtitle, subtitleParams);
        header.addView(brandText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        watchingInfo = menuText(13, TvMenuViews.AMBER);
        watchingInfo.setPadding(dp(12), dp(8), dp(12), dp(8));
        watchingInfo.setBackground(TvMenuViews.surface(this, 0xFF25251F, 0xFF514733, 24));
        android.graphics.drawable.Drawable signal = getDrawable(R.drawable.live_signal);
        signal.setBounds(0, 0, dp(21), dp(21));
        watchingInfo.setCompoundDrawables(signal, null, null, null);
        watchingInfo.setCompoundDrawablePadding(dp(6));
        header.addView(watchingInfo);
        LinearLayout clockPanel = new LinearLayout(this);
        clockPanel.setOrientation(LinearLayout.VERTICAL);
        clockPanel.setGravity(Gravity.END);
        clockPanel.setPadding(dp(24), 0, 0, 0);
        menuClock = TvMenuViews.numberText(this, 28, TvMenuViews.TEXT);
        clockPanel.addView(menuClock);
        menuDay = menuText(11, TvMenuViews.MUTED);
        LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dayParams.topMargin = dp(4);
        clockPanel.addView(menuDay, dayParams);
        header.addView(clockPanel);
        overlay.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyParams.topMargin = dp(20);
        overlay.addView(body, bodyParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(16), dp(12), dp(12));
        panel.setBackground(TvMenuViews.surface(this, TvMenuViews.PANEL, TvMenuViews.BORDER, 16));
        float screenDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        body.addView(panel, new LinearLayout.LayoutParams(
                dp(screenDp < 850 ? 260 : 300), LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout section = new LinearLayout(this);
        section.setPadding(dp(8), 0, dp(8), dp(12));
        section.setGravity(Gravity.CENTER_VERTICAL);
        TextView sectionTitle = TvMenuViews.text(this, 15, TvMenuViews.TEXT, true);
        sectionTitle.setText(R.string.tv_all_channels);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = menuText(12, TvMenuViews.MUTED);
        count.setText(getString(R.string.tv_channel_count, CHANNELS.length));
        section.addView(count);
        panel.addView(section);

        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(true);
        channelScroll.setVerticalScrollBarEnabled(false);
        channelScroll.setClipToPadding(false);
        channelScroll.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < CHANNELS.length; i++) {
            final int index = i;
            TvMenuViews.ChannelCard row = new TvMenuViews.ChannelCard(this);
            bindChannelRow(row, index);
            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && !(menuVisible && guideFocused)) {
                    guideFocused = false;
                    selectedChannelIndex = index;
                    updateNavigationHints();
                    if (menuVisible) {
                        refreshSelectedGuide(false);
                    }
                }
            });
            row.setOnClickListener(v -> switchChannel(index));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(8));
            rows.addView(row, rowParams);
            channelRows.add(row);
        }
        channelScroll.addView(rows);
        panel.addView(channelScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout guidePanel = new LinearLayout(this);
        guidePanel.setOrientation(LinearLayout.VERTICAL);
        guidePanel.setPadding(dp(26), dp(4), 0, 0);
        body.addView(guidePanel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView kicker = menuText(12, TvMenuViews.AMBER);
        kicker.setText(R.string.tv_guide_label);
        kicker.setLetterSpacing(0.08f);
        titleGroup.addView(kicker);
        guideTitle = TvMenuViews.text(this, 28, TvMenuViews.TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(7);
        titleGroup.addView(guideTitle, titleParams);
        heading.addView(titleGroup, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        guideRefresh = TvMenuViews.text(this, 14, TvMenuViews.TEXT, true);
        guideRefresh.setText(R.string.guide_refresh);
        guideRefresh.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0);
        guideRefresh.setCompoundDrawablePadding(dp(7));
        guideRefresh.setGravity(Gravity.CENTER);
        guideRefresh.setPadding(dp(16), dp(11), dp(16), dp(11));
        guideRefresh.setFocusable(true);
        guideRefresh.setFocusableInTouchMode(true);
        guideRefresh.setBackground(TvMenuViews.buttonBackground(this));
        guideRefresh.setOnFocusChangeListener((v, focused) -> {
            guideRefresh.setTextColor(focused ? TvMenuViews.INK : TvMenuViews.TEXT);
            guideRefresh.getCompoundDrawables()[0].setTint(
                    focused ? TvMenuViews.INK : TvMenuViews.TEXT);
            if (focused && !pendingProgramFocus) {
                guideFocused = true;
                selectedProgramIndex = -1;
                channelScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                guideList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                updateNavigationHints();
            }
        });
        guideRefresh.setOnClickListener(v -> refreshSelectedGuide(true));
        heading.addView(guideRefresh);
        guidePanel.addView(heading);

        guideDate = menuText(12, TvMenuViews.MUTED);
        guideDate.setPadding(0, dp(10), 0, dp(16));
        guidePanel.addView(guideDate);
        guideList = new ProgramListView();
        guideList.setVerticalScrollBarEnabled(false);
        guideList.setClipToPadding(false);
        guideList.setPadding(dp(2), dp(2), dp(2), dp(2));
        guideList.setDivider(new ColorDrawable(Color.TRANSPARENT));
        guideList.setDividerHeight(dp(6));
        guideList.setSelector(new ColorDrawable(Color.TRANSPARENT));
        guideList.setItemsCanFocus(true);
        guideList.setFocusable(true);
        guideList.setFocusableInTouchMode(true);
        programAdapter = new ProgramAdapter();
        guideList.setAdapter(programAdapter);
        guidePanel.addView(guideList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        guideMessage = menuText(17, TvMenuViews.MUTED);
        guideMessage.setPadding(dp(24), dp(30), dp(24), dp(30));
        guideMessage.setLineSpacing(dp(6), 1f);
        guideMessage.setGravity(Gravity.CENTER);
        guideMessage.setBackground(TvMenuViews.surface(this, TvMenuViews.PANEL, TvMenuViews.BORDER, 16));
        guidePanel.addView(guideMessage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        guideFooter = menuText(12, TvMenuViews.MUTED);
        guideFooter.setPadding(dp(2), dp(12), 0, 0);
        guidePanel.addView(guideFooter);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView disclaimer = menuText(11, TvMenuViews.MUTED);
        disclaimer.setText(R.string.tv_schedule_disclaimer);
        footer.addView(disclaimer);
        navigationHint = menuText(12, 0xFFB8BEB5);
        navigationHint.setGravity(Gravity.END);
        footer.addView(navigationHint, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28));
        footerParams.topMargin = dp(14);
        overlay.addView(footer, footerParams);
        updateNavigationHints();
        updateMenuHeader();
        return overlay;
    }

    private TextView menuText(int size, int color) {
        return TvMenuViews.text(this, size, color, false);
    }

    private String channelProgramText(int index) {
        Channel channel = CHANNELS[index];
        String program = programGuideManager.currentTitle(channel.code);
        if (program == null) {
            boolean loading = programGuideManager.isSummaryLoading()
                    || programGuideManager.state(channel.code).loading;
            program = getString(loading ? R.string.guide_loading_short : R.string.guide_unavailable);
        }
        return program;
    }

    private void bindChannelRow(TvMenuViews.ChannelCard row, int index) {
        row.bind(index, CHANNELS[index].name, channelProgramText(index),
                index == currentChannelIndex);
    }

    private void updateMenuRows() {
        for (int i = 0; i < channelRows.size(); i++) {
            bindChannelRow(channelRows.get(i), i);
        }
        updateMenuHeader();
    }

    private void updateMenuHeader() {
        if (menuClock != null) {
            long now = System.currentTimeMillis();
            TvMenuViews.setTextIfChanged(menuClock, ProgramGuide.displayTime(now));
            TvMenuViews.setTextIfChanged(menuDay, ProgramGuide.displayDayLabel(now));
            TvMenuViews.setTextIfChanged(watchingInfo,
                    getString(R.string.tv_now_watching, currentChannel().name));
        }
    }

    private void updateNavigationHints() {
        if (navigationHint != null) {
            TvMenuViews.setTextIfChanged(navigationHint, getString(!guideFocused ? R.string.tv_remote_channels
                    : selectedProgramIndex < 0 ? R.string.tv_remote_refresh
                    : R.string.tv_remote_programs));
        }
    }

    private void refreshProgramGuide(boolean force) {
        if (!foregroundActive || exiting) {
            return;
        }
        int index = menuVisible ? selectedChannelIndex : currentChannelIndex;
        programGuideManager.refresh(CHANNELS[index].code, force);
        onProgramGuideChanged();
    }

    private void refreshSelectedGuide(boolean force) {
        if (!foregroundActive || exiting) {
            return;
        }
        programGuideManager.refreshGuide(CHANNELS[selectedChannelIndex].code, force);
        onProgramGuideChanged();
    }

    private void onProgramGuideChanged() {
        if (!foregroundActive || exiting || isFinishing()) {
            return;
        }
        // Network/focus callbacks may arrive together. Keep hidden UI untouched and
        // bind the visible menu once on the next main-loop turn.
        if (menuVisible && !menuUpdatePosted) {
            menuUpdatePosted = true;
            handler.post(updateVisibleMenu);
        }
        // New schedules can contain a boundary sooner than the previously scheduled tick.
        handler.removeCallbacks(refreshPrograms);
        handler.postDelayed(refreshPrograms, programGuideManager.nextUpdateDelay());
    }

    private void renderProgramGuide() {
        if (guideTitle == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean clockMovedBack = now < lastProgramRenderTime;
        lastProgramRenderTime = now;
        Channel channel = CHANNELS[selectedChannelIndex];
        ProgramGuideManager.State state = programGuideManager.state(channel.code);
        ProgramGuide guide = state.guide;
        TvMenuViews.setTextIfChanged(guideTitle, getString(R.string.guide_title, channel.name));
        TvMenuViews.setTextIfChanged(guideDate,
                getString(R.string.guide_date, ProgramGuide.displayDate(now)));
        boolean hasPrograms = guide != null && !guide.programs.isEmpty();
        guideList.setVisibility(hasPrograms ? View.VISIBLE : View.GONE);
        guideMessage.setVisibility(hasPrograms ? View.GONE : View.VISIBLE);
        if (!hasPrograms) {
            TvMenuViews.setTextIfChanged(guideMessage, getString(state.loading ? R.string.guide_loading
                    : state.failed ? R.string.guide_error : R.string.guide_empty));
        }
        if (state.loading) {
            TvMenuViews.setTextIfChanged(guideFooter, getString(R.string.guide_updating));
        } else if (state.failed) {
            TvMenuViews.setTextIfChanged(guideFooter, getString(guide == null ? R.string.guide_retry_hint
                    : R.string.guide_cached_error));
        } else if (guide != null) {
            TvMenuViews.setTextIfChanged(guideFooter, getString(R.string.guide_updated,
                    ProgramGuide.displayTime(state.updatedAt), guide.programs.size()));
        } else {
            TvMenuViews.setTextIfChanged(guideFooter, getString(R.string.guide_retry_hint));
        }

        if (renderedGuide != guide || renderedChannelIndex != selectedChannelIndex) {
            boolean sameDayAndChannel = renderedChannelIndex == selectedChannelIndex
                    && renderedGuide != null && guide != null
                    && renderedGuide.date.equals(guide.date);
            if (!sameDayAndChannel) {
                programBeforeRefresh = null;
            }
            int oldFirst = guideList.getFirstVisiblePosition();
            View firstChild = guideList.getChildAt(0);
            int oldTop = firstChild == null ? 0 : firstChild.getTop();
            int oldSelection = selectedProgramIndex;
            ProgramGuide.Program anchor = sameDayAndChannel && oldSelection >= 0
                    && oldSelection < renderedGuide.programs.size()
                    ? renderedGuide.programs.get(oldSelection) : null;
            renderedGuide = guide;
            renderedChannelIndex = selectedChannelIndex;
            nextProgramRowUpdate = hasPrograms ? guide.nextBoundary(now) : 0;
            pendingProgramFocus = guideFocused && hasPrograms && oldSelection >= 0;
            programAdapter.notifyDataSetChanged();
            if (hasPrograms) {
                selectedProgramIndex = sameDayAndChannel
                        ? Math.min(oldSelection, guide.programs.size() - 1) : guide.suggestedIndex(now);
                if (guideFocused && oldSelection < 0) {
                    selectedProgramIndex = -1;
                } else if (anchor != null) {
                    for (int i = 0; i < guide.programs.size(); i++) {
                        ProgramGuide.Program program = guide.programs.get(i);
                        if (program.startTime == anchor.startTime && program.title.equals(anchor.title)) {
                            selectedProgramIndex = i;
                            break;
                        }
                    }
                }
                if (guideFocused) {
                    focusProgramSelection();
                } else if (sameDayAndChannel) {
                    guideList.setSelectionFromTop(Math.min(oldFirst, guide.programs.size() - 1), oldTop);
                } else {
                    guideList.setSelectionFromTop(guide.suggestedIndex(now), 0);
                }
            } else if (guideFocused) {
                selectedProgramIndex = -1;
                guideRefresh.requestFocus();
            }
        }
        if (hasPrograms && (clockMovedBack || now >= nextProgramRowUpdate)) {
            for (int i = 0; i < guideList.getChildCount(); i++) {
                TvMenuViews.ProgramCard row = (TvMenuViews.ProgramCard) guideList.getChildAt(i);
                int position = guideList.getFirstVisiblePosition() + i;
                if (position < guide.programs.size()) {
                    programAdapter.bind(row, position, now);
                }
            }
            nextProgramRowUpdate = guide.nextBoundary(now);
        }
        if (hasPrograms) {
            int current = guide.currentIndex(now);
            View row = programViewAt(current);
            if (row instanceof TvMenuViews.ProgramCard) {
                ((TvMenuViews.ProgramCard) row).updateProgress(programProgress(guide, current, now));
            }
        }
    }

    private int programProgress(ProgramGuide guide, int index, long now) {
        long start = guide.programs.get(index).startTime;
        long duration = guide.endTime(index) - start;
        return duration <= 0 ? 0
                : (int) Math.max(0, Math.min(1000, (now - start) * 1000 / duration));
    }

    private void focusProgramSelection() {
        if (selectedProgramIndex >= 0 && selectedProgramIndex < programAdapter.getCount()) {
            programBeforeRefresh = renderedGuide.programs.get(selectedProgramIndex);
            View row = programViewAt(selectedProgramIndex);
            if (!pendingProgramFocus && row != null && row.getHeight() > 0 && row.getTop() >= 0
                    && row.getBottom() <= guideList.getHeight()) {
                pendingProgramFocus = false;
                guideList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                row.requestFocus();
            } else {
                // A recycled row can only receive focus after ListView lays it out.
                // Hold focus on the list itself while its old focused child is
                // detached, so Android cannot move the focus into another column.
                pendingProgramFocus = true;
                guideList.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                guideList.requestFocus();
                guideList.setSelectionFromTop(selectedProgramIndex, 0);
            }
        } else {
            pendingProgramFocus = false;
            selectedProgramIndex = -1;
            guideList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            guideRefresh.requestFocus();
        }
    }

    private void enterProgramGuide() {
        guideFocused = true;
        channelScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ProgramGuide guide = programGuideManager.state(CHANNELS[selectedChannelIndex].code).guide;
        selectedProgramIndex = guide == null || guide.programs.isEmpty()
                ? -1 : guide.suggestedIndex(System.currentTimeMillis());
        focusProgramSelection();
    }

    private void returnToChannels() {
        guideFocused = false;
        pendingProgramFocus = false;
        channelScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        guideList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        channelRows.get(selectedChannelIndex).requestFocus();
    }

    private void returnToProgramGuide() {
        if (renderedGuide == null || renderedGuide.programs.isEmpty()) {
            returnToChannels();
            return;
        }
        selectedProgramIndex = renderedGuide.suggestedIndex(System.currentTimeMillis());
        if (programBeforeRefresh != null) {
            for (int i = 0; i < renderedGuide.programs.size(); i++) {
                ProgramGuide.Program program = renderedGuide.programs.get(i);
                if (program.startTime == programBeforeRefresh.startTime
                        && program.title.equals(programBeforeRefresh.title)) {
                    selectedProgramIndex = i;
                    break;
                }
            }
        }
        focusProgramSelection();
        updateNavigationHints();
    }

    private void moveProgramSelection(int delta) {
        selectedProgramIndex = Math.max(-1,
                Math.min(programAdapter.getCount() - 1, selectedProgramIndex + delta));
        focusProgramSelection();
    }

    private View programViewAt(int index) {
        int child = index - guideList.getFirstVisiblePosition();
        if (index < 0 || child < 0 || child >= guideList.getChildCount()) {
            return null;
        }
        View row = guideList.getChildAt(child);
        return Integer.valueOf(index).equals(row.getTag()) ? row : null;
    }

    private final class ProgramListView extends ListView {
        ProgramListView() {
            super(MainActivity.this);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (pendingProgramFocus && menuVisible && guideFocused) {
                View row = programViewAt(selectedProgramIndex);
                if (row != null) {
                    pendingProgramFocus = false;
                    setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                    row.requestFocus();
                }
            }
        }
    }

    private final class ProgramAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return renderedGuide == null ? 0 : renderedGuide.programs.size();
        }

        @Override
        public ProgramGuide.Program getItem(int position) {
            return renderedGuide.programs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TvMenuViews.ProgramCard row;
            if (convertView instanceof TvMenuViews.ProgramCard) {
                row = (TvMenuViews.ProgramCard) convertView;
            } else {
                row = new TvMenuViews.ProgramCard(MainActivity.this);
                row.setOnFocusChangeListener((v, focused) -> {
                    if (focused && menuVisible && guideFocused && !pendingProgramFocus) {
                        selectedProgramIndex = (Integer) v.getTag();
                        updateNavigationHints();
                    }
                });
                row.setOnClickListener(v -> switchChannel(selectedChannelIndex));
            }
            bind(row, position, System.currentTimeMillis());
            return row;
        }

        void bind(TvMenuViews.ProgramCard row, int position, long now) {
            ProgramGuide guide = renderedGuide;
            ProgramGuide.Program program = guide.programs.get(position);
            long end = guide.endTime(position);
            String endText = ProgramGuide.displayTime(end);
            if (!guide.date.equals(ProgramGuide.dateAt(end))) {
                endText = end == ProgramGuide.nextDayStart(program.startTime)
                        ? "24:00" : getString(R.string.guide_next_day, endText);
            }
            boolean current = program.startTime <= now && now < end;
            int label = current ? R.string.guide_live
                    : now >= end ? R.string.guide_past : R.string.guide_upcoming;
            row.setTag(position);
            row.bind(program.title, ProgramGuide.displayTime(program.startTime), endText,
                    getString(label), current, now >= end, programProgress(guide, position, now));
        }
    }

    private String loadPlayerScript() {
        try (InputStream input = getAssets().open("player.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Missing packaged player script", error);
        }
    }

    private boolean isCurrentChannelPage(String url) {
        if (url == null) {
            return false;
        }
        String path = android.net.Uri.parse(url).getPath();
        String expected = "/liveTV/" + currentChannel().code;
        return expected.equals(path) || (expected + "/").equals(path);
    }

    private void stopVideoChecks() {
        playbackCheckGeneration++;
        if (videoRetry != null) {
            handler.removeCallbacks(videoRetry);
            videoRetry = null;
        }
    }

    private void beginLoading(int message) {
        videoReady = false;
        playbackFailed = false;
        stopVideoChecks();
        showPlaybackCover(message, false);
    }

    private void showPlaybackCover(int message, boolean failed) {
        if (status != null) {
            handler.removeCallbacks(hideStatus);
            status.setVisibility(View.GONE);
        }
        if (loadingView != null) {
            loadingView.show(currentChannel().name, currentChannelIndex, CHANNELS.length,
                    message, failed);
        }
    }

    private void showPlaybackFailure() {
        if (exiting) {
            return;
        }
        stopVideoChecks();
        videoReady = false;
        playbackFailed = true;
        showPlaybackCover(R.string.loading_failed, true);
    }

    private void startVideoCheck(boolean userInitiated) {
        if (webView == null || exiting || !foregroundActive) {
            return;
        }
        if (userInitiated && playbackFailed) {
            reloadCurrentChannel();
            return;
        }
        stopVideoChecks();
        checkVideoFrame(0, loadGeneration, playbackCheckGeneration, userInitiated);
    }

    private void checkVideoFrame(int attempt, int generation, int checkGeneration,
                                 boolean userInitiated) {
        if (webView == null || exiting || !foregroundActive || generation != loadGeneration
                || checkGeneration != playbackCheckGeneration) {
            return;
        }
        webView.evaluateJavascript(playerScript + "(" + (userInitiated && attempt == 0) + ")", value -> {
            if (webView == null || exiting || !foregroundActive || generation != loadGeneration
                    || checkGeneration != playbackCheckGeneration) {
                return;
            }
            if ("\"playing\"".equals(value)) {
                videoReady = true;
                playbackFailed = false;
                loadingView.setVisibility(View.GONE);
                setStatus(channelBadge());
            } else if ("\"blocked\"".equals(value)) {
                videoReady = false;
                showPlaybackCover(R.string.loading_blocked, true);
            } else if ("\"error\"".equals(value) || (attempt >= 59 && !menuVisible)) {
                showPlaybackFailure();
            } else {
                if (!videoReady && "\"pending\"".equals(value)) {
                    showPlaybackCover(R.string.loading_buffering, false);
                }
                videoRetry = () -> checkVideoFrame(Math.min(attempt + 1, 59), generation,
                        checkGeneration, false);
                handler.postDelayed(videoRetry, menuVisible ? 1000 : 400);
            }
        });
    }

    private void startVideoWithSound() {
        startVideoCheck(true);
    }

    private void switchChannel(int index) {
        int targetIndex = wrapChannelIndex(index);
        if (targetIndex == currentChannelIndex) {
            // 确认当前频道时只收起菜单，保留正在播放的网页和直播状态。
            setMenuVisible(false);
            return;
        }
        currentChannelIndex = targetIndex;
        selectedChannelIndex = currentChannelIndex;
        loadGeneration++;
        setMenuVisible(false);
        refreshProgramGuide(false);
        beginLoading(R.string.loading_switching);
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl(channelUrl(currentChannelIndex));
            webView.requestFocus(View.FOCUS_DOWN);
        }
    }

    private void reloadCurrentChannel() {
        if (webView == null || exiting || !foregroundActive) {
            return;
        }
        loadGeneration++;
        beginLoading(R.string.loading_retrying);
        webView.stopLoading();
        webView.loadUrl(channelUrl(currentChannelIndex));
        webView.requestFocus(View.FOCUS_DOWN);
    }

    private int wrapChannelIndex(int index) {
        if (index < 0) {
            return CHANNELS.length - 1;
        }
        if (index >= CHANNELS.length) {
            return 0;
        }
        return index;
    }

    private Channel currentChannel() {
        return CHANNELS[currentChannelIndex];
    }

    private String channelUrl(int index) {
        return LIVE_PAGE_PREFIX + CHANNELS[index].code;
    }

    private String channelBadge() {
        return "新蓝 TV · " + currentChannel().name
                + " · 频道 " + (currentChannelIndex + 1) + "/" + CHANNELS.length;
    }

    private void setMenuVisible(boolean visible) {
        if (menuVisible == visible) {
            return;
        }
        long started = SystemClock.uptimeMillis();
        menuVisible = visible;
        if (loadingView != null) {
            loadingView.setMotionEnabled(foregroundActive && !visible);
        }
        if (channelMenu == null) {
            return;
        }
        guideFocused = false;
        pendingProgramFocus = false;
        channelScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        guideList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        handler.removeCallbacks(updateVisibleMenu);
        menuUpdatePosted = false;
        if (visible) {
            selectedChannelIndex = currentChannelIndex;
            channelMenu.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            // The opaque guide covers the player. Stop composing its hidden video
            // surface while leaving the page and live audio running.
            if (webView != null) {
                webView.setVisibility(View.INVISIBLE);
            }
            channelMenu.setVisibility(View.VISIBLE);
            updateMenuRows();
            refreshProgramGuide(false);
            renderProgramGuide();
            channelMenu.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (selectedChannelIndex < channelRows.size()) {
                channelRows.get(selectedChannelIndex).requestFocus();
                View current = channelRows.get(selectedChannelIndex);
                channelScroll.post(() -> {
                    if (!menuVisible || guideFocused) {
                        return;
                    }
                    int target = Math.max(0, current.getTop()
                            - (channelScroll.getHeight() - current.getHeight()) / 2);
                    int aligned = 0;
                    for (View row : channelRows) {
                        if (row.getTop() > target) {
                            break;
                        }
                        aligned = row.getTop();
                    }
                    channelScroll.scrollTo(0, aligned);
                });
            }
            handler.removeCallbacks(updateVisibleMenu);
            menuUpdatePosted = false;
        } else {
            if (webView != null) {
                webView.setVisibility(View.VISIBLE);
            }
            channelMenu.setVisibility(View.GONE);
            if (webView != null) {
                webView.requestFocus(View.FOCUS_DOWN);
                if (!videoReady && !playbackFailed) {
                    startVideoCheck(false);
                }
            }
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d("ZtvMenuPerf", (visible ? "open" : "close")
                    + " bindMs=" + (SystemClock.uptimeMillis() - started)
                    + " programCount=" + programAdapter.getCount());
        }
    }

    private void moveMenuSelection(int delta) {
        selectedChannelIndex = wrapChannelIndex(selectedChannelIndex + delta);
        if (selectedChannelIndex < channelRows.size()) {
            channelRows.get(selectedChannelIndex).requestFocus();
        }
    }

    private void exitApplication() {
        // 返回键采用层级退出：菜单打开时只关闭菜单，视频模式才退出应用。
        if (menuVisible) {
            if (guideFocused) {
                returnToChannels();
            } else {
                setMenuVisible(false);
            }
            return;
        }
        if (exiting) {
            return;
        }
        exiting = true;
        finishAndRemoveTask();
    }

    private void setStatus(String message) {
        if (status == null) {
            return;
        }
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideStatus);
        handler.postDelayed(hideStatus, 4200);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isActivationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        // 返回/退出键采用层级退出：先关闭菜单，再结束整个播放任务。
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (down && event.getRepeatCount() == 0) {
                exitApplication();
            }
            return true;
        }

        // 菜单中左右切换频道区与节目单；播放画面消费左右键。
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down && menuVisible && event.getRepeatCount() == 0) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (guideFocused) {
                        pendingProgramFocus = false;
                        selectedProgramIndex = -1;
                        guideList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                        guideRefresh.requestFocus();
                    } else {
                        enterProgramGuide();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (guideFocused) {
                        if (selectedProgramIndex < 0) {
                            returnToProgramGuide();
                        } else {
                            returnToChannels();
                        }
                    }
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (down && event.getRepeatCount() == 0) {
                setMenuVisible(!menuVisible);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down && (event.getRepeatCount() == 0 || (menuVisible && guideFocused))) {
                int delta = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (menuVisible) {
                    if (guideFocused) {
                        moveProgramSelection(delta);
                    } else {
                        moveMenuSelection(delta);
                    }
                } else {
                    switchChannel(currentChannelIndex + delta);
                }
            }
            return true;
        }

        if (isActivationKey(keyCode)) {
            if (down && event.getRepeatCount() == 0) {
                if (menuVisible) {
                    if (guideFocused && selectedProgramIndex < 0) {
                        refreshSelectedGuide(true);
                    } else {
                        switchChannel(selectedChannelIndex);
                    }
                } else {
                    // 同时作为 WebView 自动播放被系统策略拦截时的有声启动键。
                    startVideoWithSound();
                }
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        exitApplication();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foregroundActive = true;
        loadingView.setMotionEnabled(!menuVisible);
        applyImmersiveMode();
        refreshProgramGuide(true);
        if (webView != null) {
            webView.onResume();
            if (hasEnteredForeground && reloadOnResume && !exiting) {
                reloadOnResume = false;
                // Home 键会让 WebView 暂停或丢失视频合成层，回前台时重新建立页面。
                handler.postDelayed(this::reloadCurrentChannel, 180);
            } else {
                hasEnteredForeground = true;
                reloadOnResume = false;
            }
        }
    }

    @Override
    protected void onPause() {
        foregroundActive = false;
        loadingView.setMotionEnabled(false);
        stopVideoChecks();
        handler.removeCallbacks(refreshPrograms);
        if (!exiting && !isFinishing()) {
            reloadOnResume = true;
        }
        if (menuVisible) {
            setMenuVisible(false);
        }
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onUserLeaveHint() {
        // 在部分 TV 固件上按 Home 不一定立即触发完整的 Activity 重建，
        // 这里显式标记，保证再次回到应用时走恢复流程。
        if (!exiting) {
            reloadOnResume = true;
        }
        super.onUserLeaveHint();
    }

    @Override
    protected void onDestroy() {
        foregroundActive = false;
        programGuideManager.close();
        handler.removeCallbacksAndMessages(null);
        backgroundExecutor.shutdownNow();
        if (webView != null) {
            WebView view = webView;
            webView = null;
            view.stopLoading();
            view.onPause();
            view.removeAllViews();
            view.destroy();
        }
        super.onDestroy();
    }
}
