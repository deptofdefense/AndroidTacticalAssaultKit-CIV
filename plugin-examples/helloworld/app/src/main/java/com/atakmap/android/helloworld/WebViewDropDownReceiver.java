
package com.atakmap.android.helloworld;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.maps.MapView;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.LinearLayout.LayoutParams;
import android.widget.LinearLayout;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.webkit.WebViewClient;
import android.graphics.Bitmap;

import com.atakmap.coremap.log.Log;

public class WebViewDropDownReceiver extends DropDownReceiver implements
        OnStateListener {
    public static final String SHOW_WEBVIEW = "helloworld.example.webview";

    public static final String TAG = "WebViewDropDownReceiver";

    final Context pluginContext;
    final Context appContext;

    private final WebView htmlViewer;

    private final LinearLayout ll;

    public WebViewDropDownReceiver(final MapView mapView,
            final Context context) {
        super(mapView);
        this.pluginContext = context;
        this.appContext = mapView.getContext();

        LayoutInflater inflater = LayoutInflater.from(pluginContext);
        ll = (LinearLayout) inflater.inflate(R.layout.blank_linearlayout, null);

        // must be created using the application context otherwise this will fail
        this.htmlViewer = new WebView(mapView.getContext());
        this.htmlViewer.setVerticalScrollBarEnabled(true);
        this.htmlViewer.setHorizontalScrollBarEnabled(true);

        WebSettings webSettings = this.htmlViewer.getSettings();

        // do not enable per security guidelines
        //webSettings.setAllowFileAccessFromFileURLs(true);
        //webSettings.setAllowUniversalAccessFromFileURLs(true);

        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setGeolocationEnabled(true);
        this.htmlViewer.setWebChromeClient(new ChromeClient());

        // cause subsequent calls to loadData not to fail - without this
        // the web view would remain inconsistent on subsequent concurrent opens
        this.htmlViewer.loadUrl("about:blank");
        this.htmlViewer.setWebViewClient(new Client());

        htmlViewer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        ll.addView(htmlViewer);

    }

    public static class Client extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d(TAG, "started retrieving: " + url);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "shouldOverride: " + url);
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "ended retrieving: " + url);
            super.onPageFinished(view, url);
        }

    }

    private static class ChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d(TAG, consoleMessage.message() + " -- From line "
                    + consoleMessage.lineNumber() + " of "
                    + consoleMessage.sourceId());
            return super.onConsoleMessage(consoleMessage);
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            Log.d(TAG, "loading progress: " + newProgress);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action != null && action.equals(SHOW_WEBVIEW)) {
            showDropDown(ll, HALF_WIDTH, FULL_HEIGHT,
                    FULL_WIDTH, HALF_HEIGHT, false, this);
            this.htmlViewer.loadUrl("about:blank");
            //this.htmlViewer.loadUrl("http://192.168.43.174:3000");
            this.htmlViewer.loadUrl("https://www.whatismybrowser.com/detect");
        }
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }
}
