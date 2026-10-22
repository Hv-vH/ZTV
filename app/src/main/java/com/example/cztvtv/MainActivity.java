package com.example.cztvtv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String LIVE_PAGE_PREFIX = "https://www.cztv.com/liveTV/";
    private static final String CHANNEL_API = "https://p.cztv.com/api/paas/channel/tv";
    private static final int CHANNEL_COUNT = 10;

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
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final List<TextView> channelRows = new ArrayList<>();

    private WebView webView;
    private TextView status;
    private LinearLayout channelMenu;
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

    private static final class Channel {
        final String code;
        final String name;
        volatile String program = "节目单读取中…";

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
                dp(430),
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        menuParams.gravity = Gravity.START;
        root.addView(channelMenu, menuParams);

        setContentView(root);
        status.setVisibility(View.GONE);
        channelMenu.setVisibility(View.GONE);
        webView.requestFocus(View.FOCUS_DOWN);
        loadChannelPrograms();
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
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(36), dp(24), dp(24));
        panel.setBackgroundColor(0xF20A0D12);

        TextView title = new TextView(this);
        title.setText("频道");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView hint = new TextView(this);
        hint.setText("上下选择  ·  OK 播放  ·  左键关闭");
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
                    selectedChannelIndex = index;
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
        return panel;
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
        return (index + 1) + "  " + channel.name + "\n当前节目：" + channel.program;
    }

    private void updateMenuRows() {
        for (int i = 0; i < channelRows.size(); i++) {
            channelRows.get(i).setText(channelRowText(i));
        }
    }

    private void loadChannelPrograms() {
        backgroundExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(CHANNEL_API).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                    JSONObject root = new JSONObject(body.toString());
                    JSONObject content = root.optJSONObject("content");
                    JSONArray list = content == null ? null : content.optJSONArray("list");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject item = list.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }
                            String code = item.optString("station_code");
                            String program = item.optString("station_playing");
                            for (Channel channel : CHANNELS) {
                                if (channel.code.equals(code) && !program.isEmpty()) {
                                    channel.program = program;
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // 节目单只是菜单的辅助信息，读取失败不影响直播播放。
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            runOnUiThread(this::updateMenuRows);
        });
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
        currentChannelIndex = wrapChannelIndex(index);
        selectedChannelIndex = currentChannelIndex;
        loadGeneration++;
        setMenuVisible(false);
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
            return CHANNEL_COUNT - 1;
        }
        if (index >= CHANNEL_COUNT) {
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
                + " · 频道 " + (currentChannelIndex + 1) + "/" + CHANNEL_COUNT;
    }

    private void setMenuVisible(boolean visible) {
        menuVisible = visible;
        if (channelMenu == null) {
            return;
        }
        channelMenu.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            selectedChannelIndex = currentChannelIndex;
            updateMenuRows();
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
            setMenuVisible(false);
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
            if (down) {
                exitApplication();
            }
            return true;
        }

        // 视频模式下左右键明确吞掉，避免网页播放器快进/快退或焦点横向移动。
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down && menuVisible && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                setMenuVisible(false);
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
            if (down && event.getRepeatCount() == 0) {
                int delta = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (menuVisible) {
                    moveMenuSelection(delta);
                } else {
                    switchChannel(currentChannelIndex + delta);
                }
            }
            return true;
        }

        if (isActivationKey(keyCode)) {
            if (down && event.getRepeatCount() == 0) {
                if (menuVisible) {
                    switchChannel(selectedChannelIndex);
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
        applyImmersiveMode();
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
