
package com.atakmap.android.update;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.app.preferences.AppMgmtSettingsActivity;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Activity to manage plugins and other TAK apps
 *
 * 
 */
public class AppMgmtActivity extends MetricActivity {

    protected static final String TAG = "AppMgmtActivity";

    public static final String SYNC = "com.atakmap.android.update.SYNC";

    private SharedPreferences _prefs;
    private TextView _remoteRepoUrl;
    private ImageButton _statusRemoteRepo;
    private View _updateServerLayout;
    private TextView _title;
    private TextView _lastSynced;
    private TextView _productCount;
    private View _searchForm;
    private EditText _msgSearchTxt;
    private Spinner _statusFilter;
    private Spinner _repoFilter;
    private ImageButton _msgSearchClear;
    private CheckBox _app_mgmt_select_all;

    private ListView _msgListView;
    protected ProductInformationAdapter _appAdapter;
    private ProductProviderManager _providerManager;

    // com/atak/plugins/impl/LifecycleMapComponent makes use of it.
    private static Context activityContext;

    /**
     * Returns the Activity if the AppMgmtActivity is actually running otherwise null.
     */
    public static Context getActivityContext() {
        return activityContext;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        boolean portrait = false;

        final MapView mapView = MapView.getMapView();

        if (mapView != null
                && AtakActionBarListData
                        .getOrientation(mapView
                                .getContext()) == AtakActionBarMenuData.Orientation.portrait) {
            int screenSize = getResources().getConfiguration().screenLayout &
                    Configuration.SCREENLAYOUT_SIZE_MASK;
            if (screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE
                    || screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE) {
                setContentView(R.layout.app_mgmt_layout);
            } else {
                setContentView(R.layout.app_mgmt_layout_portrait);
                portrait = true;
            }
        } else {
            setContentView(R.layout.app_mgmt_layout);
        }

        _prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // look to configured orientation, based on user settings
        AtakPreferenceFragment.setOrientation(this);
        AtakPreferenceFragment.setSoftKeyIllumination(this);

        File rootDir = FileSystemUtils.getItem("support/apks");
        if (!IOProviderFactory.exists(rootDir)) {
            if (!IOProviderFactory.mkdir(rootDir)) {
                Log.d(TAG, "could not make the root support directory: " +
                        rootDir);
            }
        }

        //TODO display indicator that update server can be reached...
        //kick off an async task to ping/HTTP HEAD. If success, then green, otherwise red
        //re-ping, and re-sync if user changes the URL

        _updateServerLayout = findViewById(R.id.app_mgmt_update_server_layout);
        _updateServerLayout.setVisibility(_prefs.getBoolean(
                "appMgmtEnableUpdateServer", false) ? View.VISIBLE : View.GONE);
        _title = findViewById(R.id.app_mgmt_title);
        _title.setVisibility(_prefs.getBoolean("appMgmtEnableUpdateServer",
                false) ? View.GONE : View.VISIBLE);

        _remoteRepoUrl = findViewById(R.id.app_mgmt_repo);
        _remoteRepoUrl.setText(_prefs.getString("atakUpdateServerUrl",
                getString(R.string.atakUpdateServerUrlDefault)));
        _statusRemoteRepo = findViewById(
                R.id.app_mgmt_server_status);

        _statusRemoteRepo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                final boolean bLastSuccess = _prefs.getBoolean(
                        "repoRemoteServerLastStatus", false);
                final String remoteUrl = _prefs.getString(
                        "atakUpdateServerUrl",
                        getString(R.string.atakUpdateServerUrlDefault));
                long repoRemoteServerLastTime = _prefs.getLong(
                        "repoRemoteServerLastTimeMillis", -1);

                String messageTime = "";
                if (repoRemoteServerLastTime > 0) {
                    long millisNow = (new CoordinatedTime()).getMilliseconds();
                    long millisAgo = millisNow - repoRemoteServerLastTime;
                    messageTime = ". " + MathUtils.GetTimeRemainingOrDateString(
                            millisNow, millisAgo,
                            true);
                }

                String message;
                if (bLastSuccess) {
                    message = "Last sync was successful with Update Server: "
                            + remoteUrl + messageTime;
                } else if (FileSystemUtils.isEmpty(remoteUrl)) {
                    message = "Disabled, set Update Server URL and try again";
                } else {
                    //get base error message
                    message = _prefs.getString("repoRemoteServerLastReason",
                            null);
                    if (FileSystemUtils.isEmpty(message)) {
                        message = "Check connection and try again: "
                                + remoteUrl;
                    }

                    //wrap error message
                    message = "Last sync failed. " + message + messageTime;
                }

                new AlertDialog.Builder(AppMgmtActivity.this)
                        .setIcon(
                                bLastSuccess ? R.drawable.importmgr_status_green
                                        : R.drawable.importmgr_status_red)
                        .setTitle("Update Server Status")
                        .setMessage(message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });

        _lastSynced = findViewById(R.id.app_mgmt_last_synced);
        _productCount = findViewById(R.id.app_mgmt_product_count);

        _searchForm = findViewById(R.id.app_mgmt_auto_syncsearchForm);
        _searchForm.setVisibility(View.GONE);

        _msgSearchTxt = findViewById(R.id.app_mgmt_search_text);

        _msgSearchTxt.setFocusable(true);
        _msgSearchTxt.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                searchProducts(s.toString());
            }
        });
        _msgSearchTxt
                .setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean b) {
                        InputMethodManager imm = (InputMethodManager) AppMgmtActivity.this
                                .getSystemService(Context.INPUT_METHOD_SERVICE);

                        if (imm != null) {
                            if (b)
                                imm.showSoftInput(view,
                                        InputMethodManager.SHOW_IMPLICIT);
                            else
                                imm.hideSoftInputFromWindow(
                                        view.getWindowToken(), 0);
                        }
                    }
                });

        _msgSearchClear = findViewById(R.id.app_mgmt_exit_search);
        _msgSearchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSearch();
            }
        });

        _statusFilter = findViewById(R.id.app_mgmt_status_filter);
        _statusFilter
                .setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent,
                                    View view, int position, long id) {
                                filterProducts((String) _statusFilter
                                        .getSelectedItem());
                                if (view instanceof TextView)
                                    ((TextView) view).setTextColor(Color.WHITE);
                            }

                            @Override
                            public void onNothingSelected(
                                    AdapterView<?> parent) {
                                filterProducts(getString(
                                        R.string.app_mgmt_filter_All));
                            }
                        });

        _repoFilter = findViewById(R.id.app_mgmt_repo_filter);
        _repoFilter
                .setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent,
                                    View view, int position, long id) {
                                filterRepo(
                                        (String) _repoFilter.getSelectedItem());
                                if (view instanceof TextView)
                                    ((TextView) view).setTextColor(Color.WHITE);
                            }

                            @Override
                            public void onNothingSelected(
                                    AdapterView<?> parent) {
                                filterRepo(getString(
                                        R.string.app_mgmt_filter_All));
                            }
                        });

        final ApkUpdateComponent updateComponent = ApkUpdateComponent
                .getInstance();
        if (updateComponent == null
                || updateComponent.getProviderManager() == null) {
            Log.w(TAG, "Initialization not yet complete");
            Toast.makeText(this, "Not initialized, please try again",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        _providerManager = updateComponent.getProviderManager();

        //get cache of available products
        final List<ProductInformation> products = _providerManager
                .getAllProducts();

        if (_appAdapter == null) {
            Log.d(TAG, "creating app adapter, app count: " + products.size());
            _appAdapter = new ProductInformationAdapter(mapView,
                    this, products);
            _appAdapter.setPortrait(portrait);
        }

        _app_mgmt_select_all = findViewById(
                R.id.app_mgmt_select_all);
        _app_mgmt_select_all.setOnCheckedChangeListener(_select_all_listener);

        _msgListView = findViewById(R.id.app_mgmt_listview);

        View header = getLayoutInflater()
                .inflate(R.layout.app_mgmt_row_header, null);
        if (!portrait)
            _msgListView.addHeaderView(header);

        // ----Set autoscroll of listview when a new message arrives----//
        //_msgListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        //_msgListView.setStackFromBottom(true);
        _msgListView.setAdapter(_appAdapter);

        // setup action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.app_mgmt_text1));
            actionBar.setSubtitle(R.string.app_mgmt_text2);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(ApkUpdateReceiver.APP_ADDED,
                "Intent to indicate an app or plugin has been added");
        intentFilter.addAction(ApkUpdateReceiver.APP_REMOVED,
                "Intent to indicate an app or plugin has been removed");
        AtakBroadcast.getInstance().registerReceiver(_packageReceiver,
                intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter
                .addAction(ProductProviderManager.PRODUCT_REPOS_REFRESHED,
                        "Intent to indicate the list of available apps and plugins has been updated");
        AtakBroadcast.getInstance().registerReceiver(_refreshReceiver,
                intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(SYNC,
                "Intent to initiate a sync with all app repositories");
        AtakBroadcast.getInstance().registerReceiver(_syncReceiver,
                intentFilter);

        updateStatusBtn();
        setLastSyncTime();
        setproductCount(products);

        //see if a specific pacakge was requested
        ProductInformation requested;
        String requestedPacakge = getIntent().getStringExtra("package");
        String requestedPacakgeTitle = getIntent().getStringExtra(
                "packageTitle");
        if (!FileSystemUtils.isEmpty(requestedPacakge)) {
            Log.d(TAG, "Searching for requested package: " + requestedPacakge);
            final ProductProviderManager.Provider provider = _providerManager
                    .getProvider(requestedPacakge);
            if (provider == null) {
                Log.w(TAG,
                        "Cannot find provider for requested package: "
                                + requestedPacakge);
                String title = getString(R.string.app_mgmt_not_found_title);
                if (!FileSystemUtils.isEmpty(requestedPacakgeTitle)) {
                    title = requestedPacakgeTitle + " " + title;
                }
                final String message = getString(R.string.app_mgmt_not_found)
                        + ":\n" + requestedPacakge;
                new AlertDialog.Builder(this)
                        .setIcon(R.drawable.ic_menu_apps)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            } else {
                requested = provider.get().getProduct(requestedPacakge);
                if (requested == null) {
                    Log.w(TAG,
                            "Cannot find provider information for requested product: "
                                    + requestedPacakge);

                    String title = getString(R.string.app_mgmt_not_found_title);
                    if (!FileSystemUtils.isEmpty(requestedPacakgeTitle)) {
                        title = requestedPacakgeTitle + " " + title;
                    }
                    final String message = getString(
                            R.string.app_mgmt_not_found)
                            + ":\n" + requestedPacakge;
                    new AlertDialog.Builder(this)
                            .setIcon(R.drawable.ic_menu_apps)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                    return;
                } else {
                    Log.d(TAG, "Showing overview for requested package: "
                            + requestedPacakge);
                    _appAdapter.showAppOverview(requested);
                }
            }
        } else {
            Log.d(TAG, "No requested package");
        }
        activityContext = this;
    }

    private final CompoundButton.OnCheckedChangeListener _select_all_listener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            if (_appAdapter == null)
                return;

            ProductInformationAdapter.Mode mode = _appAdapter.getMode();
            if (mode == null || mode == ProductInformationAdapter.Mode.All) {
                Log.w(TAG, "_app_mgmt_select_all onCheckedChanged ignored");
                return;
            }

            _appAdapter.setAllSelected(b);
        }
    };

    private void updateStatusBtn() {

        ProductInformationAdapter.Mode mode = null;
        if (_appAdapter != null)
            mode = _appAdapter.getMode();

        if (mode != null && mode != ProductInformationAdapter.Mode.All) {
            //either display server layout, or a generic title
            _updateServerLayout.setVisibility(View.GONE);
            _title.setVisibility(View.VISIBLE);
            _title.setText(mode.toString());
            _app_mgmt_select_all.setVisibility(View.VISIBLE);
        } else {

            //either display server layout, or a generic title
            _updateServerLayout.setVisibility(_prefs.getBoolean(
                    "appMgmtEnableUpdateServer", false) ? View.VISIBLE
                            : View.GONE);
            _title.setVisibility(_prefs.getBoolean("appMgmtEnableUpdateServer",
                    false) ? View.GONE : View.VISIBLE);
            _title.setText(R.string.app_mgmt_app_plugins);
            _app_mgmt_select_all.setVisibility(View.GONE);
            _app_mgmt_select_all.setChecked(false);
        }

        if (_prefs != null) {
            _statusRemoteRepo.setImageResource(_prefs.getBoolean(
                    "repoRemoteServerLastStatus", false)
                            ? R.drawable.importmgr_status_green
                            : R.drawable.importmgr_status_red);
        }
    }

    void setAllSelected(boolean bAll) {
        ProductInformationAdapter.Mode mode = _appAdapter.getMode();
        if (mode == null || mode == ProductInformationAdapter.Mode.All) {
            Log.w(TAG, "_app_mgmt_select_all setAllSelected ignored");
            return;
        }

        _app_mgmt_select_all.setOnCheckedChangeListener(null);
        _app_mgmt_select_all.setChecked(bAll);
        _app_mgmt_select_all.setOnCheckedChangeListener(_select_all_listener);
    }

    private void setLastSyncTime() {
        if (_prefs != null) {
            long last = _prefs.getLong("repoLastSyncTime", -1);
            if (last > 0) {
                long millisNow = System.currentTimeMillis();
                long millisAgo = millisNow - last;

                _lastSynced
                        .setText(String.format(getString(R.string.last_updated),
                                MathUtils.GetTimeRemainingOrDateString(
                                        millisNow,
                                        millisAgo, true)));
            } else {
                _lastSynced.setText(
                        String.format(getString(R.string.last_updated), "--"));
            }
        }
    }

    private void setproductCount(List<ProductInformation> products) {
        if (products == null)
            _productCount.setText(String.format(
                    getString(R.string.product_count), _appAdapter.getCount()));
        else
            _productCount.setText(String.format(
                    getString(R.string.product_count), products.size()));
    }

    ProductProviderManager getProviderManager() {
        return _providerManager;
    }

    private void searchProducts(String terms) {
        if (_appAdapter != null)
            _appAdapter.search(terms);
    }

    private void filterProducts(String filter) {
        if (_appAdapter != null)
            _appAdapter.filter(filter);
    }

    private void filterRepo(String filter) {
        if (_appAdapter != null)
            _appAdapter.filterRepo(filter);
    }

    @Override
    protected void onDestroy() {

        activityContext = null;

        try {
            if (_packageReceiver != null)
                AtakBroadcast.getInstance()
                        .unregisterReceiver(_packageReceiver);
            if (_refreshReceiver != null)
                AtakBroadcast.getInstance()
                        .unregisterReceiver(_refreshReceiver);
            if (_syncReceiver != null)
                AtakBroadcast.getInstance().unregisterReceiver(_syncReceiver);
        } catch (NullPointerException npe) {
            Log.d(TAG, "severe error occurred during shutdown", npe);
        }

        //TODO how can I remove myself as a listener?
        //_providerManager.removeSyncListener(this);

        try {
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy error", e);
        }

        if (_appAdapter != null)
            _appAdapter.dispose();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        AtakPreferenceFragment.setOrientation(this);
        super.onResume();
        refresh(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_mgmt_menu_options, menu);

        boolean bMultiSelect = false;
        if (_appAdapter != null
                && _appAdapter.getMode() != ProductInformationAdapter.Mode.All)
            bMultiSelect = true;

        menu.findItem(R.id.app_mgmt_syncnow).setVisible(!bMultiSelect);
        menu.findItem(R.id.app_mgmt_search).setVisible(!bMultiSelect);
        menu.findItem(R.id.app_mgmt_multiselect).setVisible(!bMultiSelect);
        menu.findItem(R.id.app_mgmt_edit).setVisible(!bMultiSelect);
        menu.findItem(R.id.app_mgmt_about).setVisible(!bMultiSelect);

        menu.findItem(R.id.app_mgmt_multi_done).setVisible(bMultiSelect);
        menu.findItem(R.id.app_mgmt_multi_cancel).setVisible(bMultiSelect);

        //this.invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final int i = item.getItemId();

        if (i == android.R.id.home) {
            finish();
            return true;
        }

        if (i == R.id.app_mgmt_syncnow) {
            sync();

        } else if (i == R.id.app_mgmt_search) {
            toggleSearch();

        } else if (i == R.id.app_mgmt_multiselect) {
            beginMultiselect();

        } else if (i == R.id.app_mgmt_edit) {
            editSettings();

        } else if (i == R.id.app_mgmt_about) {
            aboutDevice();

        } else if (i == R.id.app_mgmt_multi_done) {
            multiDone();

        } else if (i == R.id.app_mgmt_multi_cancel) {
            multiCancel();

        } else {
            Log.w(TAG, "onOptionsItemSelected invalid option");
        }

        return true;
    }

    private void aboutDevice() {
        ATAKConstants.displayAbout(this, false);
    }

    private void multiCancel() {
        setMode(ProductInformationAdapter.Mode.All);
    }

    private void multiDone() {
        ProductInformationAdapter.Mode mode = _appAdapter.getMode();
        if (mode == null || mode == ProductInformationAdapter.Mode.All) {
            Log.w(TAG, "multiDone invalid mode");
            return;
        }

        final List<ProductInformation> selected = _appAdapter.getSelected();
        Log.d(TAG,
                "multiDone " + mode + ", size: " + selected.size());

        //TODO wizard may need to notify user that ATAK must be restarted, and then force exit

        switch (mode) {
            case MultiInstall:
                new ProductInformationWizard(
                        this,
                        _providerManager,
                        getString(R.string.installProduct),
                        String.format(getString(R.string.installBegin),
                                selected.size()),
                        getString(R.string.installWizardHint),
                        getString(R.string.installProduct),
                        new ProductInformationWizardHandler(
                                getString(R.string.installProduct),
                                selected) {
                            @Override
                            public void process(ProductInformation app) {
                                Log.d(TAG,
                                        "process install: " + app.toString());

                                final ProductProviderManager.Provider provider = _providerManager
                                        .getProvider(app.getPackageName());
                                if (provider == null) {
                                    Log.w(TAG,
                                            "Cannot install without provider: "
                                                    + app);
                                    Toast.makeText(AppMgmtActivity.this,
                                            String.format(getString(
                                                    R.string.failed_to_install),
                                                    app.getSimpleName()),
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                provider.install(app);
                            }
                        }).begin(selected);

                break;
            case MultiUninstall:
                new ProductInformationWizard(
                        this,
                        _providerManager,
                        this.getString(R.string.uninstall),
                        String.format(getString(R.string.uninstallBegin),
                                selected.size()),
                        getString(R.string.uninstallWizardHint),
                        "Uninstall: ",
                        new ProductInformationWizardHandler(
                                getString(R.string.uninstall),
                                selected) {

                            @Override
                            public void process(ProductInformation app) {
                                Log.d(TAG,
                                        "process uninstall: " + app.toString());

                                final ProductProviderManager.Provider provider = _providerManager
                                        .getProvider(app.getPackageName());
                                if (provider == null) {
                                    Log.w(TAG,
                                            "Cannot uninstall without provider: "
                                                    + app);
                                    Toast.makeText(AppMgmtActivity.this,
                                            "Failed to uninstall: "
                                                    + app.getSimpleName(),
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                provider.uninstall(app);
                            }
                        }).begin(selected);
                break;
            case MultiLoad:
                final List<ProductInformation> unselected = _appAdapter
                        .getUnselected();

                new ProductInformationWizard(
                        this,
                        _providerManager,
                        getString(R.string.loadPlugins),
                        "Click 'Begin' to load the "
                                + selected.size()
                                + " products listed below. Note other plugins will be unloaded",
                        "This Product Wizard may be used to simplify the process to load (and unloadProduct) desired plugins. At each step in the wizard, please wait for the previous step to complete before continuing.",
                        "Load: ",
                        new ProductInformationWizardHandler(
                                getString(R.string.loadPlugins),
                                selected) {
                            @Override
                            public boolean preProcess() {
                                //none selected is OK
                                Log.d(TAG, "preProcess load");
                                return true;
                            }

                            @Override
                            public void process(ProductInformation app) {
                                if (AtakPluginRegistry.get().isPluginLoaded(
                                        app.getPackageName())) {
                                    Log.d(TAG,
                                            "process Already loaded: "
                                                    + app);
                                    return;
                                }

                                Log.d(TAG, "process load: " + app);
                                SideloadedPluginProvider.installProduct(app);
                            }

                            @Override
                            public void postProcess() {
                                Log.d(TAG, "postProcess load");

                                //now unloadProduct unselected plugins
                                if (!FileSystemUtils.isEmpty(unselected)) {
                                    Log.d(TAG, "unloading plugins: "
                                            + unselected.size());

                                    int count = 0;
                                    for (ProductInformation p : unselected) {
                                        if (p.isPlugin()
                                                && AtakPluginRegistry
                                                        .get()
                                                        .isPluginLoaded(
                                                                p.getPackageName())) {
                                            SideloadedPluginProvider
                                                    .unloadProduct(p);
                                            count++;
                                        } else {
                                            Log.d(TAG,
                                                    "postProcess Already unloaded: "
                                                            + p);
                                        }
                                    }

                                    if (count > 0) {
                                        new AlertDialog.Builder(
                                                AppMgmtActivity.this)
                                                        .setIcon(
                                                                com.atakmap.android.util.ATAKConstants
                                                                        .getIconId())
                                                        .setTitle(
                                                                "Plugins Unloaded")
                                                        .setMessage(
                                                                count
                                                                        + " plugins were unloaded")
                                                        .setPositiveButton(
                                                                R.string.ok,
                                                                null)
                                                        .show();
                                    }
                                }

                                setMode(ProductInformationAdapter.Mode.All);
                                refresh(true);
                            }
                        }).begin(selected);
                break;
            case ResolveIncompatible:
                //check that we have at least one product to resolve
                if (FileSystemUtils.isEmpty(selected)) {
                    setMode(ProductInformationAdapter.Mode.All);
                    refresh(true);

                    String message = "No products selected";
                    Log.d(TAG, "multiDone resolveIncompat: " + message);
                    Toast.makeText(AppMgmtActivity.this, message,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Collection<String> incompatiblePlugins = new ArrayList<>();
                for (ProductInformation app : selected) {
                    incompatiblePlugins.add(app.getPackageName());
                }

                new IncompatiblePluginWizard(this, _providerManager, false)
                        .prompt(incompatiblePlugins);
                break;
            case All:
            default:
                break;

        }

        //reset the UI
        setMode(ProductInformationAdapter.Mode.All);
    }

    private void beginMultiselect() {
        //give user options: install, uninstall, load, unloadProduct
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setIcon(com.atakmap.android.util.ATAKConstants.getIconId())
                .setTitle(R.string.multiselect_dialogue)
                .setItems(new String[] {
                        getString(R.string.app_mgmt_multi_install),
                        getString(R.string.app_mgmt_multi_uninstall),
                        getString(R.string.app_mgmt_multi_load),
                        //getString(R.string.app_mgmt_multi_unload),
                        getString(R.string.app_mgmt_multi_resolveIncompat)
                },
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();

                                ProductInformationAdapter.Mode mode = ProductInformationAdapter.Mode.All;
                                switch (which) {
                                    case 0:
                                        mode = ProductInformationAdapter.Mode.MultiInstall;
                                        break;
                                    case 1:
                                        mode = ProductInformationAdapter.Mode.MultiUninstall;
                                        break;
                                    case 2:
                                        mode = ProductInformationAdapter.Mode.MultiLoad;
                                        break;
                                    case 3:
                                        //                                        mode = ProductInformationAdapter.Mode.MultiUnload;
                                        //                                        break;
                                        //                                    case 4:
                                        mode = ProductInformationAdapter.Mode.ResolveIncompatible;
                                        break;
                                    default:
                                        mode = ProductInformationAdapter.Mode.All;
                                }

                                setMode(mode);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();
    }

    private void setMode(ProductInformationAdapter.Mode mode) {

        //TODO support multiselect during search/filter mode? Or end search when entering multiselect mode?
        Log.d(TAG, "setMode: " + mode.toString());
        toggleSearch(false);
        _appAdapter.setMode(mode);
        updateStatusBtn();

        //now update action bar based on multi-select mode
        invalidateOptionsMenu();
    }

    private void editSettings() {
        Log.d(TAG, "editSettings");
        startActivity(new Intent(this, AppMgmtSettingsActivity.class));
    }

    private void toggleSearch() {
        toggleSearch(_searchForm.getVisibility() != View.VISIBLE);
    }

    private void toggleSearch(boolean bEnabled) {
        Log.d(TAG, "toggleSearch: " + bEnabled);
        _searchForm.setVisibility(bEnabled ? View.VISIBLE : View.GONE);
        _msgSearchTxt.setText("");
        _statusFilter.setSelection(0);
        _repoFilter.setSelection(0);
        if (_appAdapter != null)
            _appAdapter.resetSearch();
    }

    private void sync() {
        if (_providerManager == null) {
            Log.w(TAG, "Cannot sync w/out provider manager");
            return;
        }

        new AlertDialog.Builder(this)
                .setIcon(com.atakmap.android.util.ATAKConstants.getIconId())
                .setTitle("Sync Packages")
                .setMessage(
                        "Sync local device with available product repositories now?")
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Log.d(TAG, "Sync now");
                                _providerManager
                                        .sync(AppMgmtActivity.this, false,
                                                null);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refresh(boolean bDeep) {
        if (_providerManager == null) {
            Log.w(TAG, "Cannot refresh, not initialized");
            return;
        }

        Log.d(TAG, "refresh: " + bDeep);
        List<ProductInformation> products = null;
        if (bDeep) {
            products = _providerManager.getAllProducts();
        }

        if (_appAdapter != null)
            _appAdapter.refresh(products);

        if (_remoteRepoUrl != null && _prefs != null)
            _remoteRepoUrl.setText(_prefs.getString("atakUpdateServerUrl",
                    getString(R.string.atakUpdateServerUrlDefault)));
        updateStatusBtn();
        setLastSyncTime();
        setproductCount(products);
    }

    private final BroadcastReceiver _packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ApkUpdateReceiver.APP_ADDED.equals(intent.getAction())) {
                String s = intent.getStringExtra("package");
                Log.d(TAG, "APP_ADDED: " + s);
                refresh(true);
            } else if (ApkUpdateReceiver.APP_REMOVED
                    .equals(intent.getAction())) {
                String s = intent.getStringExtra("package");
                Log.d(TAG, "APP_REMOVED: " + s);
                refresh(true);
            }
        }
    };

    private final BroadcastReceiver _refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "_refreshReceiver onReceive");
            refresh(true);
        }
    };

    private final BroadcastReceiver _syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "_syncReceiver onReceive");

            String updateUrl = intent.getStringExtra("url");
            if (!FileSystemUtils.isEmpty(updateUrl)) {
                //TODO note this may be duplicate but AppMgmtPreferenceFragment sends this intent
                //prior to persisting the pref state. Trying to avoid a race condition
                _prefs.edit().putString("atakUpdateServerUrl", updateUrl)
                        .apply();
            }
            refresh(true);
            sync();
        }
    };

    private abstract class ProductInformationWizardHandler implements
            ProductInformationWizard.IProductInformationWizardHandler {

        private final List<ProductInformation> _apps;
        private final String _title;

        ProductInformationWizardHandler(String title,
                List<ProductInformation> apps) {
            _apps = apps;
            _title = title;
        }

        @Override
        public boolean preProcess() {
            //check that we have at least one product to install
            if (FileSystemUtils.isEmpty(_apps)) {
                setMode(ProductInformationAdapter.Mode.All);
                refresh(true);

                String message = "No products selected";
                Log.d(TAG, "multiDone preProcess: " + message);
                Toast.makeText(AppMgmtActivity.this, message,
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            Log.d(TAG, "preProcess");
            return true;
        }

        @Override
        public void postProcess() {
            Log.d(TAG, "postProcess");
            setMode(ProductInformationAdapter.Mode.All);
            refresh(true);

            new AlertDialog.Builder(AppMgmtActivity.this)
                    .setIcon(com.atakmap.android.util.ATAKConstants.getIconId())
                    .setTitle(_title + " Complete")
                    .setMessage("Processed " + _apps.size() + " products")
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }

        @Override
        public void cancel() {
            Log.d(TAG, "cancel");
            setMode(ProductInformationAdapter.Mode.All);
            refresh(true);
        }
    }

}
