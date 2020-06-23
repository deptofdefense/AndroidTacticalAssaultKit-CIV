
package com.atakmap.android.gui;

import android.content.DialogInterface;
import android.view.Window;
import android.widget.LinearLayout.LayoutParams;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.webkit.WebViewClient;
import android.graphics.Bitmap;
import android.app.AlertDialog;
import android.content.Context;

public class WebViewer {
    public static String TAG = "WebViewer";

    /**
     * Given a uri and a context, render the uri in an about dialog.
     * @param uri the string uri for the content.
     * @param context the context to be used to construct the components.
     */
    public static void show(final String uri, final Context context) {
        show(uri, context, 100);
    }

    public static void show(final String uri, final Context context,
            int scale) {
        show(uri, context, scale, null);
    }

    /**
     * Given a uri and a context, render the uri in an about dialog.
     * @param uri the string uri for the content.
     * @param context the context to be used to construct the components.
     * @param scale the initial scale of the page
     * @param action the action to take when the dialog is dismissed.
     */
    public static void show(final String uri, final Context context,
            int scale, final Runnable action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        final WebView v = createWebView(uri, context);
        v.loadUrl(uri);
        v.setInitialScale(scale);

        builder.setView(v);
        builder.setPositiveButton(com.atakmap.app.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (action != null)
                            action.run();
                    }
                })
                .setOnCancelListener(
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                if (action != null)
                                    action.run();
                            }
                        });

        final AlertDialog dialog = builder.create();

        dialog.show();

        Window w = dialog.getWindow();
        if (w != null)
            w.setLayout(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);

    }

    private static WebView createWebView(final String uri,
            final Context context) {
        // must be created using the application context otherwise this will fail
        WebView htmlViewer = new WebView(context);
        htmlViewer.setVerticalScrollBarEnabled(true);
        htmlViewer.setHorizontalScrollBarEnabled(true);

        WebSettings webSettings = htmlViewer.getSettings();

        // do not enable per security guidelines
        //webSettings.setAllowFileAccessFromFileURLs(true);
        //webSettings.setAllowUniversalAccessFromFileURLs(true);

        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        htmlViewer.setWebChromeClient(new ChromeClient());

        // cause subsequent calls to loadData not to fail - without this
        // the web view would remain inconsistent on subsequent concurrent opens
        htmlViewer.loadUrl("about:blank");
        htmlViewer.setWebViewClient(new Client());

        htmlViewer.loadUrl(uri);
        htmlViewer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        return htmlViewer;
    }

    private static class Client extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            //Log.d(TAG, "started retrieving: " + url);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            //Log.d(TAG, "shouldOverride: " + url);
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            //Log.d(TAG, "ended retrieving: " + url);
            super.onPageFinished(view, url);
        }

    }

    private static class ChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            //Log.d(TAG, consoleMessage.message() + " -- From line "
            //        + consoleMessage.lineNumber() + " of "
            //        + consoleMessage.sourceId());
            return super.onConsoleMessage(consoleMessage);
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            //Log.d(TAG, "loading progress: " + newProgress);
        }
    }

}
