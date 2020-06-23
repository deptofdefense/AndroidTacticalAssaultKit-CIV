package plugins.host;

import android.R;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import plugins.core.model.Plugin;
import plugins.host.register.PackagePluginRegistrar;
import plugins.host.register.PackagePluginScanner;
import plugins.host.register.PackagePluginScanner.PackageScanMonitor;

import java.util.List;

/**
 *
 * ListActivity that can be used to display the loaded plugin/extensions.
 *
 * It also provides a menu item to begin a scan for new plugins on a separate thread. This scan
 * will automatically notify the user that the app needs to be restarted if plugins are added or removed.
 */
public class PluginListActivity extends ListActivity {

    
    private ArrayAdapter<String> adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new ArrayAdapter<String>(this, R.layout.simple_list_item_1, R.id.text1);
        setListAdapter(adapter);
        loadPlugins();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        final Intent intent = getIntent();
        if( intent != null ) {
            String action = intent.getAction();
            if( action != null && action.endsWith(Intents.ACTION_SCAN) ) {
                scanForPlugins();
            }
        }
    }

    /**
     * Load scan menu
     *
     * @param menu Menu for this activity to add the menuitem to
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Scan").setIcon(R.drawable.ic_menu_search).setAlphabeticShortcut('s');
        return true;
    }

    /**
     * Handle selection of the scan menu item
     *
     * @param item selected menu item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        scanForPlugins();
        return true;
    }

    /**
     * Load the plugins into a list for displaying in the ListActivity
     */
    private void loadPlugins() {
        adapter.clear();
        PluginRegistry registry = PluginRegistry.getInstance(this);
        List<Plugin> plugins = registry.getPlugins();
        StringBuilder tmp = new StringBuilder();
        for( Plugin plugin : plugins ) {
            tmp.setLength(0);
            tmp.append(plugin.getDescriptor().getPluginId())
            .append(" v")
            .append(plugin.getDescriptor().getVersion());
            adapter.add(tmp.toString());
        }
    }

    /**
     *
     * Initiate a new scan for plugins in a thread, updating the display the as each package is scanned.
     *
     */
    private void scanForPlugins() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setCancelable(true);
        dialog.setIndeterminate(true);
        dialog.setTitle("Scanning for plugins...");
        dialog.setMessage("Scanning");
        ScannerMonitor monitor = new ScannerMonitor(dialog);
        dialog.setOnCancelListener(monitor);
        PackagePluginRegistrar registrar = new PackagePluginRegistrar(this);
        registrar.setForceRegister(true);
        final PackagePluginScanner scanner = new PackagePluginScanner(registrar, monitor);
        new Thread("PluginScanner") {
            public void run() {
                scanner.scan();
                runOnUiThread(new Runnable() {
                    public void run() {
                        loadPlugins();
                        dialog.dismiss();
                        finish();
                        try {
                            startActivity(new Intent(PluginListActivity.this, ProcessKillActivity.class));
                        } catch ( Exception e ) {
                            // this can happen if this activity isn't registered
                            e.printStackTrace();
                        }
                    }
                });
            }
        }.start();
        dialog.show();
    }


    private final class ScannerMonitor implements PackageScanMonitor, OnCancelListener {

        private boolean cancel;
        private ProgressDialog progressDialog;

        public ScannerMonitor(ProgressDialog progressDialog) {
            this.progressDialog = progressDialog;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            this.cancel = true;
        }

        @Override
        public void onPackageScanStart(final PackageInfo info) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if( !cancel ) {
                        progressDialog.setMessage("Scanning " + info.packageName);
                    }
                }
            });
        }

        @Override
        public void onPackageScanEnd(PackageInfo info) {
        }

        @Override
        public boolean isCanceled() {
            return cancel;
        }

    }
}
