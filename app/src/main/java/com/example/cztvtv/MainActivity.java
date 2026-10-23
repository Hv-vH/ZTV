package com.example.cztvtv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
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
    private final List<TextView> channelRows = new ArrayList<>();
    private final List<TextView> programRows = new ArrayList<>();

    private WebView webView;
    private TextView status;
    private LinearLayout channelMenu;
    private ProgramGuideManager programGuideManager;
    private TextView guideTitle;
    private TextView guideDate;
    private TextView guideMessage;
    private TextView guideFooter;
    private TextView guideRefresh;
    private ScrollView guideScroll;
    private LinearLayout guideList;
    private ProgramGuide renderedGuide;
    private int renderedChannelIndex = -1;
    private long nextProgramRowUpdate;
    private long lastProgramRenderTime;
    private boolean guideFocused;
    private boolean foregroundActive;
    private int selectedProgramIndex = -1;
    private boolean menuVisible;
    private boolean exiting;
    private boolean hasEnteredForeground;
    private boolean reloadOnResume;
    private int currentChannelIndex = 4; // 默认打开 106：民生休闲
    private int selectedChannelIndex = 4;
    private int loadGeneration;

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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webView = createWebView();
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

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
                setStatus("正在连接 " + currentChannel().name + "…");
                tryStartVideo(0, loadGeneration);
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
                if (request.isForMainFrame()) {
                    setStatus("网页加载失败，请检查电视网络");
                }
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
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
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        view.setBackground(roundBackground(0xB8000000, 12));
        return view;
    }

    private LinearLayout createChannelMenu() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setPadding(dp(24), dp(24), dp(24), dp(20));
        overlay.setBackgroundColor(0xF20A0D12);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        overlay.addView(panel, new LinearLayout.LayoutParams(
                dp(300), LinearLayout.LayoutParams.MATCH_PARENT
        ));

        TextView title = new TextView(this);
        title.setText(R.string.channels);
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView hint = new TextView(this);
        hint.setText(R.string.channel_menu_hint);
        hint.setTextColor(0xFFB9C1CC);
        hint.setTextSize(14);
        hint.setPadding(0, dp(8), 0, dp(18));
        panel.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < CHANNELS.length; i++) {
            final int index = i;
            TextView row = new TextView(this);
            row.setText(channelRowText(index));
            row.setTextColor(Color.WHITE);
            row.setTextSize(17);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(8), dp(12), dp(8));
            row.setMinHeight(dp(66));
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setTag(index);
            row.setBackground(channelRowBackground());
            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    guideFocused = false;
                    selectedChannelIndex = index;
                    if (menuVisible) {
                        refreshSelectedGuide(false);
                    }
                }
            });
            row.setOnClickListener(v -> switchChannel(index));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 0, 0, dp(8));
            rows.addView(row, rowParams);
            channelRows.add(row);
        }

        scrollView.addView(rows, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        LinearLayout guidePanel = new LinearLayout(this);
        guidePanel.setOrientation(LinearLayout.VERTICAL);
        guidePanel.setPadding(dp(28), 0, 0, 0);
        overlay.addView(guidePanel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        guideTitle = menuText(24, Color.WHITE);
        guideTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(guideTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));
        guideRefresh = menuText(16, Color.WHITE);
        guideRefresh.setText(R.string.guide_refresh);
        guideRefresh.setGravity(Gravity.CENTER);
        guideRefresh.setPadding(dp(16), dp(10), dp(16), dp(10));
        guideRefresh.setFocusable(true);
        guideRefresh.setFocusableInTouchMode(true);
        guideRefresh.setBackground(channelRowBackground());
        guideRefresh.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                guideFocused = true;
                selectedProgramIndex = -1;
            }
        });
        guideRefresh.setOnClickListener(v -> refreshSelectedGuide(true));
        heading.addView(guideRefresh, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        guidePanel.addView(heading);

        guideDate = menuText(14, 0xFFB9C1CC);
        guideDate.setPadding(0, dp(8), 0, dp(14));
        guidePanel.addView(guideDate);

        guideScroll = new ScrollView(this);
        guideScroll.setFillViewport(true);
        guideList = new LinearLayout(this);
        guideList.setOrientation(LinearLayout.VERTICAL);
        guideScroll.addView(guideList);
        guidePanel.addView(guideScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        guideMessage = menuText(18, 0xFFB9C1CC);
        guideMessage.setPadding(dp(16), dp(36), dp(16), dp(36));
        guideMessage.setGravity(Gravity.CENTER);
        // Kept outside the scrolling list so an empty/error state never leaves focus on a removed row.
        guidePanel.addView(guideMessage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        guideFooter = menuText(13, 0xFFB9C1CC);
        guideFooter.setPadding(0, dp(12), 0, 0);
        guidePanel.addView(guideFooter);
        TextView guideHint = menuText(13, 0xFFB9C1CC);
        guideHint.setText(R.string.guide_navigation_hint);
        guideHint.setPadding(0, dp(8), 0, 0);
        guidePanel.addView(guideHint);
        return overlay;
    }

    private TextView menuText(int size, int color) {
        TextView text = new TextView(this);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private StateListDrawable channelRowBackground() {
        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(
                new int[]{android.R.attr.state_focused},
                roundBackground(0xFF1769AA, 8)
        );
        stateList.addState(
                new int[]{},
                roundBackground(0xFF20252D, 8)
        );
        return stateList;
    }

    private String channelRowText(int index) {
        Channel channel = CHANNELS[index];
        String program = programGuideManager.currentTitle(channel.code);
        if (program == null) {
            boolean loading = programGuideManager.isSummaryLoading()
                    || programGuideManager.state(channel.code).loading;
            program = getString(loading ? R.string.guide_loading_short : R.string.guide_unavailable);
        }
        return getString(R.string.channel_row, index + 1, channel.name, program);
    }

    private void updateMenuRows() {
        for (int i = 0; i < channelRows.size(); i++) {
            channelRows.get(i).setText(channelRowText(i));
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
        updateMenuRows();
        if (menuVisible) {
            renderProgramGuide();
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
        guideTitle.setText(getString(R.string.guide_title, channel.name));
        guideDate.setText(getString(R.string.guide_date, ProgramGuide.displayDate(now)));
        boolean hasPrograms = guide != null && !guide.programs.isEmpty();
        guideScroll.setVisibility(hasPrograms ? View.VISIBLE : View.GONE);
        guideMessage.setVisibility(hasPrograms ? View.GONE : View.VISIBLE);
        if (!hasPrograms) {
            guideMessage.setText(state.loading ? R.string.guide_loading
                    : state.failed ? R.string.guide_error : R.string.guide_empty);
        }
        if (state.loading) {
            guideFooter.setText(R.string.guide_updating);
        } else if (state.failed) {
            guideFooter.setText(guide == null ? R.string.guide_retry_hint
                    : R.string.guide_cached_error);
        } else if (guide != null) {
            guideFooter.setText(getString(R.string.guide_updated,
                    ProgramGuide.displayTime(state.updatedAt), guide.programs.size()));
        } else {
            guideFooter.setText(R.string.guide_retry_hint);
        }

        if (renderedGuide != guide || renderedChannelIndex != selectedChannelIndex) {
            boolean sameDayAndChannel = renderedChannelIndex == selectedChannelIndex
                    && renderedGuide != null && guide != null
                    && renderedGuide.date.equals(guide.date);
            int oldScroll = guideScroll.getScrollY();
            int oldSelection = selectedProgramIndex;
            ProgramGuide.Program anchor = sameDayAndChannel && oldSelection >= 0
                    && oldSelection < renderedGuide.programs.size()
                    ? renderedGuide.programs.get(oldSelection) : null;
            guideList.removeAllViews();
            programRows.clear();
            renderedGuide = guide;
            renderedChannelIndex = selectedChannelIndex;
            nextProgramRowUpdate = 0;
            if (hasPrograms) {
                for (int i = 0; i < guide.programs.size(); i++) {
                    final int index = i;
                    TextView row = menuText(17, Color.WHITE);
                    row.setPadding(dp(16), dp(10), dp(16), dp(10));
                    row.setMinHeight(dp(64));
                    row.setFocusable(true);
                    row.setFocusableInTouchMode(true);
                    row.setOnFocusChangeListener((v, focused) -> {
                        if (focused) {
                            guideFocused = true;
                            selectedProgramIndex = index;
                        }
                    });
                    row.setOnClickListener(v -> switchChannel(selectedChannelIndex));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(0, 0, 0, dp(6));
                    guideList.addView(row, params);
                    programRows.add(row);
                }
                selectedProgramIndex = sameDayAndChannel
                        ? Math.min(oldSelection, programRows.size() - 1) : guide.suggestedIndex(now);
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
                    guideScroll.post(() -> guideScroll.scrollTo(0, oldScroll));
                } else {
                    TextView current = programRows.get(guide.suggestedIndex(now));
                    guideScroll.post(() -> guideScroll.scrollTo(0, current.getTop()));
                }
            } else if (guideFocused) {
                selectedProgramIndex = -1;
                guideRefresh.requestFocus();
            }
        }
        if (hasPrograms && (clockMovedBack || now >= nextProgramRowUpdate)) {
            int current = guide.currentIndex(now);
            for (int i = 0; i < programRows.size(); i++) {
                ProgramGuide.Program program = guide.programs.get(i);
                long end = guide.endTime(i);
                String endText = ProgramGuide.displayTime(end);
                if (!guide.date.equals(ProgramGuide.dateAt(end))) {
                    endText = end == ProgramGuide.nextDayStart(program.startTime)
                            ? "24:00" : getString(R.string.guide_next_day, endText);
                }
                int label = i == current ? R.string.guide_live
                        : now >= end ? R.string.guide_past : R.string.guide_upcoming;
                TextView row = programRows.get(i);
                row.setText(getString(R.string.guide_program_row,
                        ProgramGuide.displayTime(program.startTime), endText,
                        getString(label), program.title));
                StateListDrawable background = new StateListDrawable();
                background.addState(new int[]{android.R.attr.state_focused},
                        roundBackground(0xFF1769AA, 8));
                background.addState(new int[]{},
                        roundBackground(i == current ? 0xFF16463E : 0xFF20252D, 8));
                row.setBackground(background);
            }
            nextProgramRowUpdate = guide.nextBoundary(now);
        }
    }

    private void focusProgramSelection() {
        if (selectedProgramIndex >= 0 && selectedProgramIndex < programRows.size()) {
            programRows.get(selectedProgramIndex).requestFocus();
        } else {
            selectedProgramIndex = -1;
            guideRefresh.requestFocus();
        }
    }

    private void enterProgramGuide() {
        guideFocused = true;
        ProgramGuide guide = programGuideManager.state(CHANNELS[selectedChannelIndex].code).guide;
        selectedProgramIndex = guide == null || guide.programs.isEmpty()
                ? -1 : guide.suggestedIndex(System.currentTimeMillis());
        focusProgramSelection();
    }

    private void returnToChannels() {
        guideFocused = false;
        channelRows.get(selectedChannelIndex).requestFocus();
    }

    private void moveProgramSelection(int delta) {
        selectedProgramIndex = Math.max(-1,
                Math.min(programRows.size() - 1, selectedProgramIndex + delta));
        focusProgramSelection();
    }

    private void tryStartVideo(int attempt, int generation) {
        if (webView == null || generation != loadGeneration || attempt > 20) {
            return;
        }
        String script = "(function(){"
                + "var v=document.querySelector('video');"
                + "if(!v)return 'waiting';"
                + "var style=document.getElementById('cztv-tv-fullscreen-style');"
                + "if(!style){style=document.createElement('style');"
                + "style.id='cztv-tv-fullscreen-style';"
                + "(document.head||document.documentElement).appendChild(style);}"
                + "style.textContent='html,body{margin:0!important;padding:0!important;"
                + "width:100%!important;height:100%!important;overflow:hidden!important;"
                + "background:#000!important;visibility:visible!important;}"
                + "video{position:fixed!important;left:0!important;top:0!important;"
                + "width:100vw!important;height:100vh!important;max-width:none!important;"
                + "max-height:none!important;object-fit:contain!important;background:#000!important;"
                + "visibility:visible!important;z-index:2147483647!important;}';"
                + "var path=v;"
                + "while(path&&path.parentElement){"
                + "var parent=path.parentElement;"
                + "for(var i=0;i<parent.children.length;i++){"
                + "if(parent.children[i]!==path)parent.children[i].style.display='none';}"
                + "path=parent;"
                + "if(parent===document.body)break;"
                + "}"
                + "path=v;"
                + "while(path&&path!==document.documentElement){"
                + "path.style.margin='0';path.style.padding='0';"
                + "path.style.width='100vw';path.style.height='100vh';"
                + "path.style.maxWidth='none';path.style.maxHeight='none';"
                + "path.style.background='#000';"
                + "if(path!==v){path.style.position='fixed';path.style.left='0';path.style.top='0';}"
                + "path=path.parentElement;"
                + "}"
                + "if(document.body)document.body.style.visibility='visible';"
                + "v.style.display='block';v.style.position='fixed';v.style.left='0';v.style.top='0';"
                + "v.style.width='100vw';v.style.height='100vh';v.style.objectFit='contain';"
                + "v.defaultMuted=false;v.muted=false;v.volume=1;"
                + "try{v.setAttribute('playsinline','');v.setAttribute('webkit-playsinline','');}catch(e){}"
                + "var p=v.play();if(p&&p.catch)p.catch(function(){});"
                + "return v.paused?'pending':'playing';"
                + "})()";
        webView.evaluateJavascript(script, value -> {
            if (generation != loadGeneration || webView == null) {
                return;
            }
            boolean playing = value != null && value.contains("playing");
            if (value == null || value.contains("waiting") || !playing) {
                if (attempt >= 20) {
                    setStatus("按确认键开始有声播放");
                } else {
                    handler.postDelayed(
                            () -> tryStartVideo(attempt + 1, generation),
                            900
                    );
                }
            } else {
                setStatus(channelBadge());
            }
        });
    }

    private void startVideoWithSound() {
        tryStartVideo(0, loadGeneration);
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
        setStatus("正在切换到 " + currentChannel().name + "…");
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl(channelUrl(currentChannelIndex));
            webView.requestFocus(View.FOCUS_DOWN);
        }
    }

    private void reloadCurrentChannel() {
        if (webView == null || exiting) {
            return;
        }
        loadGeneration++;
        setStatus("正在恢复 " + currentChannel().name + "…");
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
        menuVisible = visible;
        if (channelMenu == null) {
            return;
        }
        channelMenu.setVisibility(visible ? View.VISIBLE : View.GONE);
        guideFocused = false;
        if (visible) {
            selectedChannelIndex = currentChannelIndex;
            updateMenuRows();
            refreshProgramGuide(false);
            renderProgramGuide();
            if (selectedChannelIndex < channelRows.size()) {
                channelRows.get(selectedChannelIndex).requestFocus();
            }
        } else if (webView != null) {
            webView.requestFocus(View.FOCUS_DOWN);
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

    private GradientDrawable roundBackground(int color, float radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return background;
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
                        selectedProgramIndex = -1;
                        guideRefresh.requestFocus();
                    } else {
                        enterProgramGuide();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (guideFocused) {
                        returnToChannels();
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
