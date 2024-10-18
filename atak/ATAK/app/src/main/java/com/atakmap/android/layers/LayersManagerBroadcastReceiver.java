
package com.atakmap.android.layers;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.atakmap.android.contentservices.Service;
import com.atakmap.android.contentservices.ServiceFactory;
import com.atakmap.android.contentservices.ServiceListing;
import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.favorites.FavoriteListAdapter;
import com.atakmap.android.favorites.FavoriteListAdapter.Favorite;
import com.atakmap.android.grg.GRGMapComponent;
import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.LayerSelectionAdapter.OnItemSelectedListener;
import com.atakmap.android.layers.MobileLayerSelectionAdapter.MobileImagerySpec;
import com.atakmap.android.layers.overlay.IlluminationListItem;
import com.atakmap.android.layers.wms.DownloadAndCacheBroadcastReceiver;
import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PanZoomReceiver;
import com.atakmap.android.maps.tilesets.mobac.WebMapLayer;
import com.atakmap.android.util.NotificationIdRecycler;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.annotations.FortifyFinding;
import com.atakmap.app.R;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.CameraController;
import com.atakmap.map.Globe;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.ProxyLayer;
import com.atakmap.map.layer.control.IlluminationControl2;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.tilematrix.TileClient;
import com.atakmap.map.layer.raster.tilematrix.TileClientFactory;
import com.atakmap.map.layer.raster.tilematrix.TileContainerFactory;
import com.atakmap.map.layer.raster.tilematrix.TileContainerSpi;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.math.MathUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.spi.InteractiveServiceProvider;
import com.atakmap.util.Visitor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayersManagerBroadcastReceiver extends DropDownReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        RasterLayer2.OnSelectionChangedListener,
        ProxyLayer.OnProxySubjectChangedListener,
        OnKeyListener, OnStateListener, OnClickListener {

    public final static String ACTION_ERROR_LOADING_LAYERS = "com.atakmap.android.maps.ERROR_LOADING_LAYERS";
    public final static String ACTION_LAYER_LOADING_PROGRESS = "com.atakmap.android.maps.LAYER_PROGRESS";
    public final static String ACTION_GRG_DELETE = "com.atakmap.android.grg.DELETE";
    public final static String ACTION_TOOL_FINISHED = "com.atakmap.android.layers.TOOL_FINISHED";
    public final static String EXTRA_LAYER_NAME = "layerName";
    public final static String EXTRA_PROGRESS_MESSAGE = "layerProgressMsg";
    public final static String ACTION_SELECT_LAYER = "com.atakmap.android.maps.SELECT_LAYER";
    public final static String EXTRA_OFFLINE_ONLY = "offlineOnly";
    public final static String EXTRA_SELECTION = "selection";
    public final static String EXTRA_IS_LOCKED = "isLocked";
    public final static String ACTION_REFRESH_LAYER_MANAGER = "com.atakmap.android.maps.REFRESH_LAYER_MANAGER";
    public final static String ACTION_ADD_FAV = "com.atakmap.android.maps.ACTION_ADD_FAV";
    public final static String ACTION_VIEW_FAV = "com.atakmap.android.maps.ACTION_VIEW_FAV";

    /**
     * If true, specifies that map should "zoom to" the layer on select. If the
     * map resolution is lower than the minimum resolution of the selection,
     * the map will zoom in to the minimum resolution; if the map view does not
     * intersect the bounds of the selection, the map will pan to be centered
     * on the dataset.
     */
    public final static String EXTRA_ZOOM_TO = "zoomTo";

    protected final SharedPreferences _prefs;
    protected DownloadAndCacheBroadcastReceiver downloadRecv = null;

    // these are not visible strings, rather tab identifiers - do not translate
    protected final static String IMAGERY_ID = "imagery";
    protected final static String MOBILE_ID = "mobile";
    final private static String GRG_ID = "grg";
    protected final static String FAVS_ID = "favs";
    protected FavoriteListAdapter _favAdapter;

    public static final int TILE_DOWNLOAD_LIMIT = 300000;
    private final static int ACTIVATED_COLOR = Color.parseColor("#008F00");

    public static final String TAG = "LayersManagerBroadcastReceiver";

    private static final NotificationIdRecycler _notificationId = new NotificationIdRecycler(
            85000, 4);

    protected TabHost tabHost = null;
    protected final AdapterSpec _nativeLayersAdapter;
    protected final AdapterSpec _mobileLayersAdapter;
    protected Button downloadButton;
    protected Button cancelButton;
    protected Button selectButton;
    protected Switch offlineOnlyCheckbox;
    protected OnlineLayersManagerView onlineView;
    protected OnlineLayersDownloadManager downloader;
    private AlertDialog listDialog;
    private LayersManagerView nativeManagerView;
    protected final Set<LayerSelectionAdapter> adapters;
    private final CardLayer rasterLayers;
    private final Map<Layer, LayerSelectionAdapter> layerToAdapter;

    // Ability to read preferences to load username, password and domain for a site
    private final static String ONLINE_USERNAME = "online.username.";

    @FortifyFinding(finding = "Password Management: Hardcoded Password", rational = "This is only a key and not a password")
    private final static String ONLINE_PASSWORD = "online.password.";
    private final static String ONLINE_DOMAIN = "online.domain.";

    public LayersManagerBroadcastReceiver(MapView mapView,
            CardLayer rasterLayers,
            AdapterSpec nativeListAdapter,
            AdapterSpec mobileListAdapter) {

        super(mapView);
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(getMapView()
                        .getContext());

        // Register the listener to change the indicator when preferences change
        _prefs.registerOnSharedPreferenceChangeListener(this);

        this.rasterLayers = rasterLayers;
        _nativeLayersAdapter = nativeListAdapter;
        _nativeLayersAdapter.adapter.setLayersMgrRec(this);
        _mobileLayersAdapter = mobileListAdapter;
        _mobileLayersAdapter.adapter.setLayersMgrRec(this);

        this.adapters = Collections
                .newSetFromMap(
                        new IdentityHashMap<LayerSelectionAdapter, Boolean>());
        this.adapters.add(_nativeLayersAdapter.adapter);
        this.adapters.add(_mobileLayersAdapter.adapter);

        this.layerToAdapter = new IdentityHashMap<>();
        for (LayerSelectionAdapter adapter : this.adapters) {
            this.layerToAdapter.put(adapter.layer, adapter);

            adapter.layer.addOnSelectionChangedListener(this);
        }

        this.rasterLayers.addOnProxySubjectChangedListener(this);
        this.onProxySubjectChanged(this.rasterLayers);
        new RegionShapeTool(getMapView());

        _favAdapter = new FavoriteListAdapter(mapView.getContext());
    }

    protected LayerSelectionAdapter getActiveLayers() {
        for (LayerSelectionAdapter l : this.adapters)
            if (l.isActive())
                return l;
        return _mobileLayersAdapter.adapter;
    }

    @Override
    public void disposeImpl() {
        if (downloader != null)
            downloader.setCallback(null);
        _prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    protected void showImageryHint() {
        showHint(
                "imagery.display",
                getMapView().getContext().getString(R.string.imagery_change),
                ResourceUtil.getString(getMapView().getContext(),
                        R.string.civ_imagery_change_summ,
                        R.string.imagery_change_summ));
    }

    protected void showMobileHint() {
        showHint(
                "mobile.display",
                getMapView().getContext().getString(R.string.mobile_imagery),
                getMapView().getContext().getString(
                        R.string.mobile_imagery_summ));
    }

    /**
     * Show a hint once.
     */
    private void showHint(final String key, final String title,
            final String message) {

        HintDialogHelper.showHint(getMapView().getContext(), title, message,
                key);
    }

    @Override
    public void onReceive(Context ignoreCtx, Intent intent) {

        final Context context = getMapView().getContext();

        if (intent.getAction().contentEquals(
                ACTION_ERROR_LOADING_LAYERS)) {
            showError(intent.getStringExtra("message"));
            return;
        } else if (intent.getAction().contentEquals(ACTION_GRG_DELETE)) {
            String uid = intent.getStringExtra("uid");
            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Cannot delete GRG w/out UID");
                return;
            }

            String uriStr = intent.getStringExtra("uri");
            if (FileSystemUtils.isEmpty(uriStr)) {
                Log.w(TAG, "Cannot delete GRG w/out URI");
                return;
            }
            try {
                if (uriStr.startsWith("file:///file:/"))
                    // ATAK-6059 legacy fix - root issue in GdalLayerInfo.wrap
                    uriStr = "file:///" + uriStr.replace("file:///file:/", "");
                Uri uri = Uri.parse(uriStr);
                String path = uri.getPath();
                if (uri.getScheme().equals("zip"))
                    path = path.substring(0, path.lastIndexOf(".kmz") + 4);

                if (FileSystemUtils.isFile(path)) {
                    File f = new File(path);
                    promptDelete(f);
                } else {
                    Log.w(TAG,
                            "Cannot delete file that doesn't exist: " + path);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse file URI: " + uriStr);
            }

            return;
        } else if (intent.getAction().equals(ACTION_TOOL_FINISHED)) {
            downloader.finishRegionSelect(intent);
            if (downloader.hasRegionShape())
                downloadButton.setEnabled(true);
            return;
        } else if (intent.getAction().contentEquals(ACTION_SELECT_LAYER)) {
            // attempt to locate the layer and selection for the favorite
            LayerSelectionAdapter layerAdapter = null;
            if (intent.getStringExtra(EXTRA_LAYER_NAME) != null) {
                for (LayerSelectionAdapter adapter : LayersManagerBroadcastReceiver.this.adapters) {
                    if (adapter.layer.getName()
                            .equals(intent.getStringExtra(EXTRA_LAYER_NAME))) {
                        layerAdapter = adapter;
                        break;
                    }
                }
            }
            if (layerAdapter == null
                    && intent.getStringExtra(EXTRA_SELECTION) != null) {
                for (LayerSelectionAdapter adapter : LayersManagerBroadcastReceiver.this.adapters) {
                    if (adapter.findByName(
                            intent.getStringExtra(EXTRA_SELECTION)) != null) {
                        layerAdapter = adapter;
                        break;
                    }
                }
            }

            LayerSelection ls = null;
            if (layerAdapter != null)
                ls = layerAdapter
                        .findByName(intent.getStringExtra(EXTRA_SELECTION));
            if (ls != null) {
                int zoomMode = AdapterSpec.ZOOM_NONE;
                if (intent.getBooleanExtra(EXTRA_ZOOM_TO, true))
                    zoomMode = AdapterSpec.ZOOM_ZOOM_CENTER;

                selectLayer(layerAdapter,
                        ls,
                        zoomMode,
                        intent.getBooleanExtra(EXTRA_IS_LOCKED,
                                !layerAdapter.layer.isAutoSelect()));
            }
            if (layerAdapter != null) {
                Log.d(TAG, "ACTION_SELECT_LAYER: " + layerAdapter);
                activateLayer(layerAdapter, false);
            }
            return;
        } else if (intent.getAction().contentEquals(
                ACTION_REFRESH_LAYER_MANAGER)) {

            for (LayerSelectionAdapter adapter : layerToAdapter.values()) {
                adapter.outlinesDatastore.refresh();
                adapter.invalidate(true);
            }

            return;
        } else if (intent.getAction().contentEquals(
                ACTION_ADD_FAV)) {
            Favorite fav = intent.getParcelableExtra("favorite");
            if (fav != null)
                add(fav);

            intent.setExtrasClassLoader(Favorite.class.getClassLoader());
            Favorite[] favs = (Favorite[]) intent
                    .getParcelableArrayExtra("favorites");
            if (favs != null && favs.length > 0) {
                Log.d(TAG, "Adding favorites cnt: " + favs.length);
                add(favs);
            }

            return;
        } else if (intent.getAction().contentEquals(
                ACTION_VIEW_FAV)) {
            intent.setExtrasClassLoader(Favorite.class.getClassLoader());
            Favorite fav = intent.getParcelableExtra("favorite");
            if (fav != null)
                view(fav);

            return;
        }

        if (tabHost != null) {
            onDropDownVisible(true);

            if (isTablet()) {
                showDropDown(tabHost, FIVE_TWELFTHS_WIDTH, FULL_HEIGHT,
                        FULL_WIDTH,
                        FIVE_TWELFTHS_HEIGHT, this);
            } else {
                showDropDown(tabHost, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                        HALF_HEIGHT, this);
            }
            return;
        }

        ViewGroup vGroup = (ViewGroup) LayoutInflater.from(context).inflate(
                R.layout.layers_manager_tabhost, null);

        tabHost = vGroup.findViewById(R.id.ll_tabhost);
        tabHost.setup();

        // create views
        final View nativeView = instantiateNativeMapManagerView(context);
        final View onlineView = instantiateMobileView(context);
        final View favsView = instantiateFavsView(context);

        tabHost.setOnTabChangedListener(new OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                if (tabId.equals(MOBILE_ID)) {
                    ListView lv = onlineView
                            .findViewById(R.id.layers_manager_online_list);

                    final TextView emptyText = onlineView
                            .findViewById(android.R.id.empty);
                    lv.setEmptyView(emptyText);

                    try {
                        lv.setSelection(_mobileLayersAdapter.adapter
                                .selectedIndex());
                        lv.smoothScrollToPosition(
                                _mobileLayersAdapter.adapter.selectedIndex());
                    } catch (Exception e) {
                        Log.d(TAG, "error selecting online map position", e);
                    }
                }

            }
        });

        TabSpec nativeSpec = tabHost.newTabSpec(IMAGERY_ID).setIndicator(
                _prefs.getString("imagery_tab_name", getMapView().getContext()
                        .getString(R.string.imagery)));

        nativeSpec.setContent(new TabContentFactory() {

            @Override
            public View createTabContent(String tag) {
                return nativeView;
            }
        });

        TabSpec onlineSpec = tabHost.newTabSpec(MOBILE_ID)
                .setIndicator(
                        getMapView().getContext().getString(R.string.mobile));

        onlineSpec.setContent(new TabContentFactory() {

            @Override
            public View createTabContent(String tag) {
                return onlineView;
            }
        });

        TabSpec favSpec = tabHost.newTabSpec(FAVS_ID).setIndicator(
                getMapView().getContext().getString(R.string.favs));

        favSpec.setContent(new TabContentFactory() {

            @Override
            public View createTabContent(String tag) {
                return favsView;
            }
        });

        tabHost.addTab(nativeSpec);
        tabHost.addTab(onlineSpec);
        tabHost.addTab(favSpec);

        configureTabLongPressListener(0, _nativeLayersAdapter.adapter);
        configureTabLongPressListener(1, _mobileLayersAdapter.adapter);

        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                if (tabId.equals(MOBILE_ID)) {
                    showMobileHint();
                    activateLayer(_mobileLayersAdapter.adapter, false);
                } else if (tabId.equals(IMAGERY_ID)) {
                    activateLayer(_nativeLayersAdapter.adapter, true);
                }
            }
        });

        // set tab to category with the current view
        if (_nativeLayersAdapter.adapter.isActive())
            tabHost.setCurrentTab(0);
        else if (_mobileLayersAdapter.adapter.isActive())
            tabHost.setCurrentTab(1);

        showImageryHint();

        onDropDownVisible(true);
        if (isTablet()) {
            showDropDown(tabHost, THIRD_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    THIRD_HEIGHT, this);
        } else {
            showDropDown(tabHost, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, this);
        }
    }

    final ClearContentRegistry.ClearContentListener dataMgmtReceiver = new ClearContentRegistry.ClearContentListener() {
        @Override
        public void onClearContent(boolean clearmaps) {

            if (clearmaps)
                LayersMapComponent.getLayersDatabase().clear();
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.startsWith(ONLINE_USERNAME) ||
                key.startsWith(ONLINE_PASSWORD) ||
                key.startsWith(ONLINE_DOMAIN)) {

            String[] arr = key.split("\\.");

            final String username = p.getString(ONLINE_USERNAME + arr[2], null);
            final String password = p.getString(ONLINE_PASSWORD + arr[2], null);
            final String domain = p.getString(ONLINE_DOMAIN + arr[2], null);

            if (username != null && password != null && domain != null) {
                AtakAuthenticationDatabase.saveCredentials(
                        AtakAuthenticationCredentials.TYPE_HTTP_BASIC_AUTH,
                        domain, username, password, false);
                p.edit().remove(ONLINE_USERNAME + arr[2])
                        .remove(ONLINE_PASSWORD + arr[2])
                        .remove(ONLINE_DOMAIN + arr[2]).apply();
            }

        }

        if (key.equals("imagery_tab_name") && tabHost != null) {
            final Context c = getMapView().getContext();
            final String tabName = _prefs.getString(key, c.getString(
                    R.string.imagery));
            // Indicators cannot be set once created, workaround is setting title of widget
            getMapView().post(new Runnable() {
                public void run() {
                    final TabWidget widget = tabHost.getTabWidget();
                    final LinearLayout tabView = (LinearLayout) widget
                            .getChildTabViewAt(0);
                    if (tabView != null) {
                        int count = tabView.getChildCount();
                        if (count > 1) {
                            final View v = tabView.getChildAt(1);
                            if (v instanceof TextView) {
                                TextView tabTitle = (TextView) v;
                                if (tabName.equals(
                                        c.getString(R.string.imagery))) {
                                    // For Imagery tab = Imagery, set indicator to Imagery
                                    tabTitle.setText(
                                            c.getString(R.string.imagery));
                                    //Log.d(TAG, "Imagery tab is Imagery");
                                } else if (tabName
                                        .equals(c.getString(R.string.maps))) {
                                    // For Imagery tab = Maps, set indicator to Maps
                                    tabTitle.setText(
                                            c.getString(R.string.maps));
                                    //Log.d(TAG, "Imagery tab is Maps");
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    protected void configureTabLongPressListener(int tabNum,
            final LayerSelectionAdapter adapter) {
        tabHost.getTabWidget().getChildAt(tabNum)
                .setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        activateLayer(adapter, false);
                        return false;
                    }
                });
    }

    protected void activateLayer(LayerSelectionAdapter adapter,
            boolean forceAuto) {
        this.rasterLayers.show(adapter.layer.getName());

        final boolean auto = !adapter.isLocked() || forceAuto;
        adapter.setLocked(!auto);

        // the tab is changed, request surface refresh
        final SurfaceRendererControl object = getMapView().getRenderer3()
                .getControl(SurfaceRendererControl.class);
        if (object != null)
            object.markDirty(new Envelope(-180d, -90d, 0d, 180d, 90d, 0d),
                    false);
    }

    protected View instantiateFavsView(final Context context) {

        final LinearLayout favView = (LinearLayout) LayoutInflater
                .from(context).inflate(
                        R.layout.fav_list_view, null);

        ListView lv = favView.findViewById(R.id.fav_list);
        lv.setAdapter(_favAdapter);

        lv.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position,
                    long id) {

                FavoriteListAdapter.Favorite fav = (Favorite) _favAdapter
                        .getItem(position);
                view(fav);
            }
        });

        Button button = favView.findViewById(R.id.fav_add);
        button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // add item
                final EditText input = new EditText(context);
                input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                input.setHint(getMapView().getResources().getString(
                        R.string.fav_hint));

                AlertDialog.Builder build = new AlertDialog.Builder(v
                        .getContext())
                                .setTitle(
                                        getMapView().getResources().getString(
                                                R.string.fav_dialogue))
                                .setView(input)
                                .setPositiveButton(R.string.ok,
                                        new DialogInterface.OnClickListener() {

                                            @Override
                                            public void onClick(
                                                    DialogInterface arg0,
                                                    int arg1) {
                                                final MapSceneModel sm = getMapView()
                                                        .getRenderer3()
                                                        .getMapSceneModel(false,
                                                                MapRenderer2.DisplayOrigin.UpperLeft);
                                                final GeoPoint focus = sm.mapProjection
                                                        .inverse(
                                                                sm.camera.target,
                                                                null);
                                                double lat = focus
                                                        .getLatitude();
                                                double lon = focus
                                                        .getLongitude();
                                                double alt = focus
                                                        .isAltitudeValid()
                                                                ? focus
                                                                        .getAltitude()
                                                                : 0d;
                                                double scale = getMapView()
                                                        .getMapScale();
                                                double tilt = getMapView()
                                                        .getMapTilt();
                                                double rotation = getMapView()
                                                        .getMapRotation();

                                                IlluminationControl2 illuminationControl = getMapView()
                                                        .getRenderer3()
                                                        .getControl(
                                                                IlluminationControl2.class);
                                                boolean illuminationEnabled = illuminationControl != null
                                                        && illuminationControl
                                                                .getEnabled();
                                                long illuminationDateTime = illuminationControl != null
                                                        ? illuminationControl
                                                                .getTime()
                                                        : CoordinatedTime
                                                                .currentTimeMillis();

                                                LayerSelectionAdapter active = LayersManagerBroadcastReceiver.this
                                                        .getActiveLayers();

                                                String layer;
                                                String selection = null;
                                                boolean locked = false;
                                                if (_favAdapter != null) {
                                                    layer = active.layer
                                                            .getName();
                                                    LayerSelection ls = active
                                                            .getSelected();
                                                    if (ls != null) {
                                                        selection = ls
                                                                .getName();
                                                        locked = active
                                                                .isLocked();
                                                    }
                                                    add(input.getText()
                                                            .toString(),
                                                            lat, lon, alt,
                                                            scale,
                                                            tilt, rotation,
                                                            layer,
                                                            selection, locked,
                                                            illuminationEnabled,
                                                            illuminationDateTime);
                                                }

                                            }
                                        })

                                .setNegativeButton(R.string.cancel, null);

                build.show();
            }
        });

        return favView;

    }

    protected void view(Favorite fav) {
        if (fav == null) {
            Log.w(TAG, "view fav invalid");
            return;
        }

        Log.d(TAG, "view fav: " + fav);

        getMapView().getMapController().dispatchOnPanRequested();
        final MapRenderer2 renderer = getMapView().getRenderer3();
        final boolean rotations = getMapView().getMapTouchController()
                .getTiltEnabledState() != MapTouchController.STATE_TILT_DISABLED;
        renderer.lookAt(
                new GeoPoint(fav.latitude, fav.longitude, fav.altitude),
                Globe.getMapResolution(getMapView().getDisplayDpi(),
                        fav.zoomLevel),
                rotations ? fav.rotation : 0d,
                rotations ? fav.tilt : 0d,
                true);

        // based on the current date and time set and if the illumination was
        // active for the favorite, reset it.
        _prefs.edit()
                .putBoolean(IlluminationListItem.PREF_KEY_IS_ENABLED,
                        fav.illuminationEnabled)
                .putLong(IlluminationListItem.PREF_KEY_ILLUMINATION_TIME,
                        fav.illuminationDateTime)
                .apply();

        // dispatch an Intent to perform the layer selection based on
        // the properties of the favorite
        Intent selectLayer = new Intent();
        selectLayer.setAction(ACTION_SELECT_LAYER);
        if (fav.layer != null)
            selectLayer.putExtra(EXTRA_LAYER_NAME, fav.layer);
        if (fav.selection != null)
            selectLayer.putExtra(EXTRA_SELECTION, fav.selection);
        selectLayer.putExtra(EXTRA_IS_LOCKED, fav.locked);
        // viewport is set above
        selectLayer.putExtra(EXTRA_ZOOM_TO, false);
        AtakBroadcast.getInstance().sendBroadcast(selectLayer);
    }

    private void add(Favorite favorite) {
        if (_favAdapter == null || favorite == null)
            return;

        _favAdapter.add(favorite);
        _favAdapter.writeList();
    }

    private void add(Favorite[] favorites) {
        if (_favAdapter == null || favorites == null || favorites.length < 1)
            return;

        for (Favorite fav : favorites)
            _favAdapter.add(fav);
        _favAdapter.writeList();
    }

    protected void add(String name, double lat, double lon, double alt,
            double scale, double tilt, double rotation,
            String layer, String selection, boolean locked) {
        add(name, lat, lon, alt, scale, tilt, rotation, layer, selection,
                locked, false,
                System.currentTimeMillis());
    }

    protected void add(String name, double lat, double lon, double alt,
            double scale, double tilt, double rotation,
            String layer, String selection, boolean locked,
            boolean illuminationEnabled, long illuminationDateTime) {
        if (_favAdapter == null)
            return;

        _favAdapter.add(name,
                lat, lon, alt, scale,
                tilt, rotation,
                layer,
                selection, locked,
                illuminationEnabled, illuminationDateTime);
        _favAdapter.writeList();
    }

    private void promptDelete(final File file) {
        if (file == null)
            return;
        Context c = getMapView().getContext();
        AlertDialog.Builder b = new AlertDialog.Builder(c);
        b.setTitle(R.string.delete_grg);
        b.setIcon(R.drawable.ic_menu_delete);
        b.setMessage(c.getString(R.string.delete) + file.getName()
                + c.getString(R.string.question_mark_symbol));
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                delete(file);
                dialog.dismiss();
            }
        });
        b.show();
    }

    private void delete(File f) {
        Log.d(TAG, "Deleting " + f);
        Intent deleteIntent = new Intent();
        deleteIntent.setAction(ImportExportMapComponent.ACTION_DELETE_DATA);
        deleteIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                GRGMapComponent.IMPORTER_CONTENT_TYPE);
        deleteIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                GRGMapComponent.IMPORTER_DEFAULT_MIME_TYPE);
        deleteIntent.putExtra(ImportReceiver.EXTRA_URI,
                Uri.fromFile(f).toString());
        AtakBroadcast.getInstance().sendBroadcast(deleteIntent);
    }

    protected View instantiateNativeMapManagerView(Context context) {
        if (nativeManagerView == null)
            nativeManagerView = instantiateMapManagerView(context,
                    _nativeLayersAdapter);
        return nativeManagerView;
    }

    protected LayersManagerView instantiateMapManagerView(Context context,
            final AdapterSpec adapter) {
        final LayerSelectionAdapter layersAdapter = adapter.adapter;

        final LayersManagerView retval = (LayersManagerView) this
                .instantiateLayersManagerViewImpl(
                        context,
                        R.layout.layers_manager_view,
                        adapter);

        retval.setAutoSelectListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                LayerSelectionAdapter active = LayersManagerBroadcastReceiver.this
                        .getActiveLayers();
                if (active == null)
                    throw new IllegalStateException();

                final boolean locked = active.isLocked();
                active.setLocked(!locked);

                for (LayerSelectionAdapter ls : LayersManagerBroadcastReceiver.this.adapters)
                    ls.notifyDataSetChanged();
            }
        });

        // set initial view lock state
        updateAutoLayer();

        retval.setOnUpLayerClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                retval.setListAdapter(layersAdapter);
                drillOut(layersAdapter, adapter.zoomOnClick);
                retval.scrollToSelection(layersAdapter
                        .selectedIndex());
            }
        });

        retval.setOnDownLayerClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                retval.setListAdapter(layersAdapter);
                drillIn(layersAdapter, adapter.zoomOnClick);
                retval.scrollToSelection(layersAdapter
                        .selectedIndex());
            }
        });

        if (adapter.longClickListener != null)
            retval.setOnItemLongClickListener(adapter.longClickListener);
        layersAdapter.setView(retval);
        return retval;
    }

    protected void drillIn(LayerSelectionAdapter adapter, int zoomTo) {
        this.drillImpl(adapter, zoomTo, 1);
    }

    protected void drillOut(LayerSelectionAdapter adapter, int zoomTo) {
        this.drillImpl(adapter, zoomTo, -1);
    }

    private void drillImpl(LayerSelectionAdapter adapter, int zoomTo,
            int dir) {
        int drillIndex = adapter.selectedIndex() + dir;
        if (drillIndex < adapter.getCount() && drillIndex >= 0)
            selectLayer(adapter, (LayerSelection) adapter.getItem(drillIndex),
                    zoomTo, true);
    }

    private void selectLayer(LayerSelectionAdapter adapter,
            LayerSelection layerSelection,
            int zoomMode,
            boolean locked) {
        double targetScale = Double.NaN;
        GeoPoint targetPoint = null;

        final MapView mapView = getMapView();
        if (zoomMode != AdapterSpec.ZOOM_NONE) {
            double resolution = layerSelection.getMinRes();
            // XXX - apply some fudge factor as tiles aren't
            //       showing up immediately at the reported
            //       native resolution due to the fudging that
            //       occurs in the GLQuadTileNode. We didn't
            //       observe this with legacy since the levels
            //       got clamped, but the aggregate reports all
            //       levels available
            final double fudge = 0.33d;
            resolution *= fudge;

            final Geometry closestCov = LayerSelection.getCoverageNearest(
                    layerSelection, mapView.getCenterPoint().get());
            if (closestCov != null) {
                final double estRes = PanZoomReceiver
                        .estimateScaleToFitResolution(mapView,
                                closestCov.getEnvelope(),
                                (int) (mapView.getWidth() * 0.85d),
                                (int) (mapView.getHeight() * 0.85d));
                if (estRes < resolution)
                    resolution = estRes;
            }

            if (resolution < mapView.getMapResolution()) {
                targetScale = mapView
                        .mapResolutionAsMapScale(resolution);

                // if there is no 'target point', make sure that the
                // view will still show the layer once we're zoomed
                // in
                MapSceneModel model = mapView.getRenderer3().getMapSceneModel(
                        false, MapRenderer2.DisplayOrigin.UpperLeft);
                model = new MapSceneModel(mapView.getDisplayDpi(),
                        model.width,
                        model.height,
                        model.mapProjection,
                        model.mapProjection.inverse(model.camera.target, null),
                        model.focusx,
                        model.focusy,
                        model.camera.azimuth,
                        90d + model.camera.elevation,
                        Globe.getMapResolution(mapView.getDisplayDpi(),
                                targetScale),
                        mapView.isContinuousScrollEnabled());

                PointF scratch = new PointF();

                scratch.x = 0;
                scratch.y = 0;
                GeoPoint upperLeft = model.inverse(scratch, null);
                if (upperLeft == null)
                    upperLeft = mapView.getCenterPoint().get();
                scratch.x = mapView.getWidth();
                scratch.y = 0;
                GeoPoint upperRight = model.inverse(scratch, null);
                if (upperRight == null)
                    upperRight = mapView.getCenterPoint().get();
                scratch.x = mapView.getWidth();
                scratch.y = mapView.getHeight();
                GeoPoint lowerRight = model.inverse(scratch, null);
                if (lowerRight == null)
                    lowerRight = mapView.getCenterPoint().get();
                scratch.x = 0;
                scratch.y = mapView.getHeight();
                GeoPoint lowerLeft = model.inverse(scratch, null);
                if (lowerLeft == null)
                    lowerLeft = mapView.getCenterPoint().get();

                final double north = MathUtils.max(
                        upperLeft.getLatitude(),
                        upperRight.getLatitude(),
                        lowerRight.getLatitude(),
                        lowerLeft.getLatitude());
                final double south = MathUtils.min(
                        upperLeft.getLatitude(),
                        upperRight.getLatitude(),
                        lowerRight.getLatitude(),
                        lowerLeft.getLatitude());
                final double east = MathUtils.max(
                        upperLeft.getLongitude(),
                        upperRight.getLongitude(),
                        lowerRight.getLongitude(),
                        lowerLeft.getLongitude());
                final double west = MathUtils.min(
                        upperLeft.getLongitude(),
                        upperRight.getLongitude(),
                        lowerRight.getLongitude(),
                        lowerLeft.getLongitude());

                if (!LayersMapComponent.intersects(
                        new Envelope[] {
                                new Envelope(
                                        west, south, 0d, east, north, 0d)
                        },
                        layerSelection.getBounds())) {

                    targetPoint = LayerSelection
                            .boundsGetCenterNearest(layerSelection,
                                    mapView.getCenterPoint().get());
                }
            } else if (!LayersMapComponent.isInView(mapView, layerSelection)) {
                targetPoint = LayerSelection
                        .boundsGetCenterNearest(layerSelection,
                                mapView.getCenterPoint().get());
            }
        }

        adapter.setSelected(layerSelection);
        adapter.setLocked(locked);

        // XXX - non-animated
        if (!Double.isNaN(targetScale) && targetPoint != null) {
            getMapView().getMapController().panZoomTo(targetPoint,
                    targetScale,
                    true);
        } else if (targetPoint != null) {
            CameraController.Programmatic.panTo(getMapView().getRenderer3(),
                    targetPoint, true);
        } else if (!Double.isNaN(targetScale)) {
            CameraController.Programmatic.zoomTo(getMapView().getRenderer3(),
                    targetScale, true);
        }

        adapter.sort();
    }

    /**
     * Control the state of the download expanded layout in the mobile 
     * tab.
     */
    protected void setExpandedDownloadState(final boolean enabled) {
        if (enabled) {
            _mobileLayersAdapter.adapter
                    .addOnItemSelectedListener(mobileDownloadSelectionListener);
            ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                    .setExpandedLayout(true);
        } else {
            _mobileLayersAdapter.adapter
                    .removeOnItemSelectedListener(
                            mobileDownloadSelectionListener);
            ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                    .setExpandedLayout(false);
        }
    }

    // set up the online tab
    protected View instantiateMobileView(final Context c) {
        onlineView = (OnlineLayersManagerView) this
                .instantiateLayersManagerViewImpl(c,
                        R.layout.layers_manager_online_view,
                        _mobileLayersAdapter);

        View progressView = onlineView
                .findViewById(R.id.downloadProgressLayout);
        View mobileTools = onlineView
                .findViewById(R.id.mobileTools);
        downloader = new OnlineLayersDownloadManager(getMapView(), this,
                progressView, mobileTools);
        downloadRecv = new DownloadAndCacheBroadcastReceiver(
                downloader, this);

        selectButton = onlineView.findViewById(R.id.selectAreaBtn);

        final CheckBox showAllOffline = onlineView
                .findViewById(R.id.showall);
        showAllOffline
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        _prefs.edit()
                                .putBoolean("mobile.show_all_offline",
                                        isChecked)
                                .apply();
                        ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                                .setOnlyViewportDisplay(!isChecked);
                    }
                });

        showAllOffline.setChecked(_prefs.getBoolean("mobile.show_all_offline",
                false));

        final View moreBar = onlineView.findViewById(R.id.moreBar);
        final ImageButton more = onlineView
                .findViewById(R.id.more);

        offlineOnlyCheckbox = onlineView.findViewById(
                R.id.offlineModeSwitch);
        // set up online/offline switch
        offlineOnlyCheckbox.setTextOff(c.getString(R.string.local));
        offlineOnlyCheckbox.setTextOn(c.getString(R.string.online));
        offlineOnlyCheckbox.setOnCheckedChangeListener(
                new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                                .setOfflineOnly(!isChecked);

                        if (!isChecked) {
                            more.setVisibility(View.GONE);
                            cancelDownloadWorkflow();
                            moreBar.setVisibility(View.GONE);
                            showAllOffline.setVisibility(View.VISIBLE);
                        } else {
                            more.setSelected(false);
                            more.setImageResource(R.drawable.arrow_right);
                            more.setVisibility(View.VISIBLE);
                            showAllOffline.setVisibility(View.GONE);
                        }
                    }
                });

        more.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setSelected(!v.isSelected());
                if (v.isSelected()) {
                    moreBar.setVisibility(View.VISIBLE);
                    more.setImageResource(R.drawable.arrow_down);
                } else {
                    cancelDownloadWorkflow();
                    moreBar.setVisibility(View.GONE);
                    more.setImageResource(R.drawable.arrow_right);
                }
            }
        });

        // set up the cancel button
        cancelButton = onlineView.findViewById(
                R.id.cancelDownloadBtn);
        cancelButton.setOnClickListener(this);

        if (downloader.isDownloading()) {
            cancelButton.setEnabled(true);
            showDownloadProgress(true);
            cancelButton.setText(R.string.cancel);
        } else
            cancelButton.setEnabled(false);

        // set up the download button
        downloadButton = onlineView.findViewById(R.id.downloadBtn);
        downloadButton.setOnClickListener(this);

        // set up the select area button
        selectButton.setOnClickListener(this);

        // set up the add source button
        onlineView.findViewById(R.id.addOnlineSource_btn)
                .setOnClickListener(this);

        offlineOnlyCheckbox.setChecked(
                !((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                        .isOfflineOnly());

        return onlineView;
    }

    /**
     * Cancel all aspects of the download workflow.
     */
    protected void cancelDownloadWorkflow() {
        // cancel the current operation

        setExpandedDownloadState(false);

        // if selecting a rectangle, or a rect has been set but the download has not yet
        // been started
        if (downloader.isSelectingRegion() || downloader.hasRegionShape())
            downloader.cancelRegionSelect();

        downloadButton.setEnabled(false);

        if (!downloader.isDownloading())
            cancelButton.setEnabled(false);
        else
            cancelButton.setText(R.string.cancel);

        ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                .reset();

        // if downloading and no rect set
        if (downloader.isDownloading()) {
            cancelButton.setEnabled(true);
            downloader.stopDownload();
        }

        selectButton.setEnabled(true);
        setRetain(false);
    }

    public static boolean isSelectionDownloadable(LayerSelection ls) {
        if (ls == null)
            return false;
        final boolean quickCheck = (ls.getTag() instanceof MobileImagerySpec
                && !((MobileImagerySpec) ls.getTag()).offlineOnly);
        if (!quickCheck)
            return false;
        return isDownloadable(((MobileImagerySpec) ls.getTag()).desc);
    }

    /**
     * Given a Dataset Descriptor, determine if downloading is supported.
     * @param desc the dataset descriptor
     * @return true if downloading is supported
     */
    public static boolean isDownloadable(DatasetDescriptor desc) {
        if (desc == null)
            return false;

        try {
            // XXX - derive tile client
            TileClient client = null;
            try {
                client = TileClientFactory.create(desc.getUri(), null, null);
                if (client == null)
                    return false;

                final boolean[] retval = new boolean[] {
                        false
                };
                TileContainerFactory.visitCompatibleSpis(
                        new Visitor<Collection<TileContainerSpi>>() {
                            @Override
                            public void visit(
                                    Collection<TileContainerSpi> object) {
                                retval[0] = !object.isEmpty();
                            }
                        }, client);

                return retval[0];
            } finally {
                if (client != null)
                    client.dispose();
            }
        } catch (Exception e) {
            // see ATAK-15809 Playstore Crash: NativeProjection forward
            Log.e(TAG, "error occured with descriptor: " + desc, e);
            return false;
        }
    }

    protected AbstractLayersManagerView instantiateLayersManagerViewImpl(
            final Context context, int viewId,
            final AdapterSpec adapter) {
        AbstractLayersManagerView retval = (AbstractLayersManagerView) LayoutInflater
                .from(context).inflate(viewId, null);

        // set the click listener for items in the list
        retval.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (parent != null) {
                    LayerSelection layerSelection = (LayerSelection) parent
                            .getItemAtPosition(position);
                    selectLayer(adapter.adapter,
                            layerSelection,
                            adapter.zoomOnClick,
                            true);
                }
            }
        });
        retval.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v,
                    int pos, long id) {
                if (parent != null) {
                    LayerSelection layerSelection = (LayerSelection) parent
                            .getItemAtPosition(pos);
                    showDetailsDialog(adapter.adapter, layerSelection);
                }
                return true;
            }
        });
        retval.setListAdapter(adapter.adapter);
        return retval;
    }

    private void showDetailsDialog(LayerSelectionAdapter adapter,
            LayerSelection selection) {
        if (adapter instanceof MobileLayerSelectionAdapter) {
            ((MobileLayerSelectionAdapter) adapter)
                    .showDetailsDialog(selection);
        }
    }

    public void onDownloadComplete() {
        this.enableCancel(false);
    }

    public void onDownloadCanceled() {
        this.enableCancel(false);
    }

    public void onDownloadError() {
        this.enableCancel(false);
    }

    private void enableCancel(boolean isEnabled) {
        if (cancelButton != null) {
            cancelButton.setEnabled(isEnabled);
            if (!isEnabled)
                cancelButton.setText(R.string.cancel);
        }
    }

    public int getTileNum2(String layerUri, double minRes, double maxRes) {
        long tiles = downloader.getTilesOverArea2(layerUri, minRes, maxRes);
        if (tiles > TILE_DOWNLOAD_LIMIT || tiles < 0)
            return TILE_DOWNLOAD_LIMIT + 1;
        else
            return (int) tiles;
    }

    public double estimateMinDownloadResolution(TileMatrix matrix,
            double maxRes) {
        return downloader.estimateMinimumResolution(matrix, maxRes);
    }

    public void receiveDownloadArea() {
        // invalidate the list view and reload with the expanded views
        setExpandedDownloadState(true);

        onlineView.setListAdapter(_mobileLayersAdapter.adapter);
        onlineView.invalidate();
        onlineView.scrollToSelection(_mobileLayersAdapter.adapter
                .selectedIndex());

        mobileDownloadSelectionListener
                .onItemSelected(_mobileLayersAdapter.adapter);
    }

    public void cancelDownloadArea() {
        setExpandedDownloadState(true);
        // invalidate the list view and reload with the expanded views
        // calculate center point
        onlineView.setListAdapter(_mobileLayersAdapter.adapter);
        onlineView.invalidate();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        _mobileLayersAdapter.adapter.setVisible(v);
        _nativeLayersAdapter.adapter.setVisible(v);
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {

        setExpandedDownloadState(false);

        ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter).reset();
        downloader.cancelRegionSelect();
        downloadButton.setEnabled(false);

        if (!downloader.isDownloading())
            cancelButton.setEnabled(false);
        else
            cancelButton.setText(R.string.cancel);
        ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter).reset();
        selectButton.setEnabled(true);
        setRetain(false);
    }

    boolean autoLayerSelectState;

    // update the state of the "auto layer select" button
    void updateAutoLayer() {
        // XXX - we are going to make the autoselect button reflect the state of
        //       the active adapter.

        LayerSelectionAdapter active = this.getActiveLayers();

        if (active == null)
            throw new IllegalStateException();

        final boolean autoLayerSelect = !active.isLocked();
        if (autoLayerSelectState != autoLayerSelect) {
            Toast.makeText(getMapView().getContext(), autoLayerSelect
                    ? R.string.auto_map_select_on
                    : R.string.auto_map_select_off, Toast.LENGTH_LONG).show();
            autoLayerSelectState = autoLayerSelect;
        }

        LayersManagerView[] views = new LayersManagerView[] {
                this.nativeManagerView
        };
        for (LayersManagerView view : views) {
            if (view == null || view.getAutoSelectButton() == null)
                continue;

            view.getAutoSelectButton().invalidate();
            view.getAutoSelectButton().setChecked(autoLayerSelect);
            //            views[i].getAutoSelectButton().setText(buttonText);
        }
    }

    protected static boolean isUniqueLayerName(String name) {
        RasterDataStore dataStore = LayersMapComponent.getLayersDatabase();

        RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
        params.names = Collections.singleton(name);

        return (dataStore.queryDatasetsCount(params) == 0);
    }

    // Query a WMS or WMTS server to find what layers it provides.
    // This is done off of the main thread due to network access.
    protected void queryMapService(String servicePath) {
        servicePath = servicePath.trim();
        if (servicePath.isEmpty())
            return;

        MapServiceTask mapServiceTask = new MapServiceTask(getMapView(),
                new MapServiceTaskCallback() {
                    @Override
                    public void selectQueryLayersToAdd(String uri,
                            ServiceListing querier) {
                        LayersManagerBroadcastReceiver.this
                                .selectQueryLayersToAdd(uri, querier);
                    }
                });
        mapServiceTask.execute(servicePath);
    }

    // Display a dialog to allow the user to select what layers they
    // wish to add.
    private void selectQueryLayersToAdd(String uri, ServiceListing querier) {

        List<Service> sortedLayers = new ArrayList<>(querier.services);

        Collections.sort(sortedLayers, new Comparator<Service>() {
            @Override
            public int compare(Service lhs, Service rhs) {
                boolean lhsIsFolder = (lhs.getName().indexOf('/') >= 0);
                boolean rhsIsFolder = (rhs.getName().indexOf('/') >= 0);
                if (lhsIsFolder && !rhsIsFolder)
                    return 1;
                else if (!lhsIsFolder && rhsIsFolder)
                    return -1;
                else
                    return lhs.getName().compareToIgnoreCase(rhs.getName());
            }
        });

        final Context context = getMapView().getContext();
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View view = inflater.inflate(R.layout.wmswfs_results, null,
                false);
        final EditText searchTerms = view.findViewById(R.id.search_filter);
        final ListView listView = view.findViewById(R.id.results);
        final ServiceAdapter adapter = new ServiceAdapter(context,
                sortedLayers);
        listView.setAdapter(adapter);

        searchTerms.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                adapter.getFilter().filter(s.toString());
            }
        });

        String title = getMapView().getContext().getString(R.string.layers_on)
                + querier.title;
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle(title)
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                List<Service> layerList = adapter.getSelected();

                                // calling right into a static definition to see if these layers
                                // are able to be aggregated.
                                // right now this is a little muddy because it grossly makes
                                // this class away of the WebMapLayerService.   consult with 
                                // CL on if Service contains the static ability to check for 
                                // aggregation.

                                // runs this twice, first without a name to see if aggregation is 
                                // even possible.  This is to limit the polution until CL and I 
                                // formalize the interface changes.
                                Service aggregate = com.atakmap.android.maps.tilesets.mobac.WebMapLayerService
                                        .constructAggregate(null, layerList);
                                if (aggregate != null && layerList.size() > 1) {
                                    promptForAgregation(layerList);
                                } else {
                                    // for each layer the user checked, add that to
                                    // our list of sources
                                    for (Service layer : layerList) {
                                        doAddMapLayer(layer);
                                    }
                                }
                            }

                        })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void promptForAgregation(final List<Service> layers) {
        new AlertDialog.Builder(getMapView().getContext())
                .setCancelable(false)
                .setTitle(R.string.single_multip_layer_prompt)
                .setMessage(
                        R.string.layers_can_be_aggregated)
                .setPositiveButton(R.string.single,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                promptForName(layers);
                            }
                        })
                .setNegativeButton(R.string.multiple,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                for (Service layer : layers)
                                    doAddMapLayer(layer);
                            }
                        })
                .show();
    }

    private void promptForName(final List<Service> layers) {

        final EditText input = new EditText(getMapView().getContext());
        int maxLength = 26;
        input.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(maxLength)
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(
                getMapView().getContext());

        builder.setTitle(R.string.agregated_layer_title);
        builder.setView(input)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                String name = input.getText().toString();
                                if (name.length() == 0)
                                    promptForName(layers);
                                else
                                    doAddMapLayer(
                                            com.atakmap.android.maps.tilesets.mobac.WebMapLayerService
                                                    .constructAggregate(name,
                                                            layers));

                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class ServiceAdapter extends ArrayAdapter<Service> {

        private final List<Service> original = new ArrayList<>();
        private final List<Service> filtered = new ArrayList<>();
        private final Collection<Service> selected = new HashSet<>();

        private Filter filter;

        public ServiceAdapter(Context context, List<Service> services) {
            super(context, R.layout.wmswfs_item, services);
            this.filtered.addAll(services);
            this.original.addAll(services);
        }

        public List<Service> getSelected() {
            return new ArrayList<>(selected);
        }

        private static class ViewHolder {
            TextView textView;
            CheckBox checkbox;
        }

        @Override
        public Filter getFilter() {
            if (filter == null) {
                filter = new ServiceFilter();
            }
            return filter;
        }

        @Override
        public int getCount() {
            return filtered.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {

                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.wmswfs_item, null, false);

                holder = new ViewHolder();
                holder.checkbox = convertView.findViewById(R.id.selectCB);
                holder.textView = convertView.findViewById(R.id.result);
                convertView.setTag(holder);

            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Service s = filtered.get(position);
            holder.textView.setText(s.getName());
            holder.checkbox
                    .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                boolean isChecked) {
                            if (isChecked)
                                selected.add(s);
                            else
                                selected.remove(s);
                        }
                    });
            holder.checkbox.setChecked(selected.contains(s));
            return convertView;
        }

        private class ServiceFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence term) {
                FilterResults results = new FilterResults();
                if (term == null || term.length() == 0) {
                    results.values = original;
                    results.count = original.size();
                } else {
                    String termLower = term.toString().toLowerCase(
                            LocaleUtil.getCurrent());
                    List<Service> filtered = new ArrayList<>();
                    for (Service service : original)
                        if (service.getName()
                                .toLowerCase(LocaleUtil.getCurrent())
                                .contains(termLower))
                            filtered.add(service);
                    results.values = filtered;
                    results.count = filtered.size();
                }
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint,
                    FilterResults results) {
                filtered.clear();
                notifyDataSetChanged();
                filtered.addAll((ArrayList<Service>) results.values);
                notifyDataSetChanged();
            }

        }

    }

    // The user wants to add this Layer as an online source.
    // Create a mobac XML file so it will be persisted, and add it to the
    // online layers dialog so we can see it now.
    private void doAddMapLayer(Service layer) {
        try {
            WebMapLayer.addToLayersDatabase(layer, getMapView().getContext());
        } catch (Exception e) {
            Log.d(TAG, "error adding a layer to the database", e);
            Toast.makeText(
                    getMapView().getContext(),
                    getMapView().getContext().getString(
                            R.string.layer_select_error)
                            + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        // XXX - add to dialog. My android-fu has failed me temporarily.
    }

    protected void startDownload(final String layerTitle) {
        setExpandedDownloadState(false);
        downloader.freezeRegionShape();
        final Map<LayerSelection, Pair<Double, Double>> toDownload = ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                .getLayersToDownload();
        for (Map.Entry<LayerSelection, Pair<Double, Double>> entry : toDownload
                .entrySet()) {
            LayerSelection ls = entry.getKey();
            Pair<Double, Double> minMax = entry.getValue();
            if (minMax != null) {
                downloader.startDownload(layerTitle, null, ls,
                        minMax.first, minMax.second);
                cancelButton.setText(R.string.cancel);
                ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                        .reset();
            }
        }
    }

    protected void createNewLayer() {
        // pop up dialog to get the name of the cache from the user
        final Context c = getMapView().getContext();
        final View title = LayoutInflater.from(c).inflate(
                R.layout.download_layer, getMapView(), false);
        AlertDialog.Builder b = new AlertDialog.Builder(c);
        b.setTitle(R.string.download_layer);
        b.setCancelable(false);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        b.setView(title);
        final AlertDialog d = b.create();
        d.show();
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Action for 'OK' Button
                        String layerTitle = ((EditText) title
                                .findViewById(R.id.newLayerName))
                                        .getText().toString();

                        if (layerTitle.length() == 0) {
                            Toast.makeText(c, R.string.no_layer_name_blank,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String sanitizedLayerTitle = FileSystemUtils
                                .sanitizeWithSpaces(layerTitle);

                        if (!sanitizedLayerTitle.equals(layerTitle)) {
                            Toast.makeText(c, R.string.layer_name_limit,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        layerTitle = sanitizedLayerTitle;

                        // verify unique name with checkUniqueName method
                        if (!isUniqueLayerName(layerTitle)) {
                            Toast.makeText(c,
                                    c.getString(R.string.layer_name_exist)
                                            + layerTitle,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        d.dismiss();
                        downloadButton.setEnabled(false);
                        onlineView.setListAdapter(_mobileLayersAdapter.adapter);
                        onlineView
                                .scrollToSelection(_mobileLayersAdapter.adapter
                                        .selectedIndex());
                        showDownloadProgress(true);
                        startDownload(layerTitle);
                    }
                });
    }

    protected void showDownloadProgress(boolean b) {
        final View mobileTools = onlineView.findViewById(R.id.mobileTools);
        final View progressView = onlineView
                .findViewById(R.id.downloadProgressLayout);
        if (b) {
            mobileTools.setVisibility(View.GONE);
            progressView.setVisibility(View.VISIBLE);
        } else {
            mobileTools.setVisibility(View.VISIBLE);
            progressView.setVisibility(View.GONE);
        }
    }

    private void promptExistingLayer() {
        final Context c = getMapView().getContext();
        RasterDataStore dataStore = LayersMapComponent.getLayersDatabase();
        LinkedList<DatasetDescriptor> existingTilesets = new LinkedList<>();
        RasterDataStore.DatasetDescriptorCursor result = null;
        try {
            RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
            // filter on current selection
            params.imageryTypes = Collections
                    .singleton(_mobileLayersAdapter.adapter.layer
                            .getSelection());
            params.order = Collections.<RasterDataStore.DatasetQueryParameters.Order> singleton(
                    RasterDataStore.DatasetQueryParameters.Name.INSTANCE);
            params.remoteLocalFlag = RasterDataStore.DatasetQueryParameters.RemoteLocalFlag.LOCAL;

            result = dataStore.queryDatasets(params);
            DatasetDescriptor desc;
            while (result.moveToNext()) {
                desc = result.get();
                if (desc.getName().contains(".sqlite"))
                    existingTilesets.add(result.get());
            }
        } finally {
            if (result != null)
                result.close();
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(c,
                android.R.layout.select_dialog_singlechoice);
        if (!existingTilesets.isEmpty()) {
            for (DatasetDescriptor ls : existingTilesets)
                adapter.add(ls.getName().substring(0,
                        ls.getName().indexOf(".sqlite")));
        } else {
            Toast.makeText(c, R.string.no_comp_tileset,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(c);
        b.setTitle(R.string.choose_tileset_dest);
        b.setCancelable(true);
        b.setNegativeButton(R.string.cancel, null);
        b.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String layerTitle = adapter.getItem(which);
                if (FileSystemUtils.isEmpty(layerTitle))
                    layerTitle = c.getString(R.string.new_layer);
                showDownloadProgress(true);
                downloadButton.setEnabled(false);
                onlineView.setListAdapter(_mobileLayersAdapter.adapter);
                startDownload(layerTitle);
            }
        });
        b.show();
    }

    /** toast an error message */
    private void showError(final String e) {

        // Use a NULL in place of the ticker, so the perception of how fast things
        // are scanned and loaded is not adverse.

        NotificationUtil.getInstance().postNotification(
                _notificationId.getNotificationId(),
                R.drawable.select_point_icon,
                NotificationUtil.RED,
                getMapView().getContext().getString(
                        R.string.failed_to_load_layer),
                null, e);

    }

    final OnItemSelectedListener mobileDownloadSelectionListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(final LayerSelectionAdapter adapter) {
            final LayerSelection ls = adapter.getSelected();
            final boolean downloadable = isSelectionDownloadable(ls);
            onlineView.post(new Runnable() {
                @Override
                public void run() {
                    if (!downloader.hasRegionShape())
                        return;
                    downloadButton.setEnabled(downloadable);
                    ((MobileLayerSelectionAdapter) adapter)
                            .setExpandedLayout(downloadable);
                    onlineView.invalidate();

                    // XXX - courtesy for GoogleCRS84Quad datasets
                    if (!downloadable &&
                            (ls != null) &&
                            (ls.getTag() instanceof MobileImagerySpec) &&
                            !((MobileImagerySpec) ls.getTag()).offlineOnly) {

                        Toast.makeText(
                                onlineView.getContext(),
                                "Download from " + ls.getName()
                                        + " not supported.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };

    /**
     * listen for the back button
     **/
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            downloader.cancelRegionSelect();
            downloadButton.setEnabled(false);

            if (!downloader.isDownloading())
                cancelButton.setEnabled(false);
            else
                cancelButton.setText(R.string.cancel);

            ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                    .reset();

            selectButton.setEnabled(true);
            setRetain(false);
            return true;
        }
        return false;
    }

    public static class AdapterSpec {
        public final static int DATASET = 0;
        public final static int IMAGERY_TYPE = 1;

        public final static int ZOOM_NONE = 0;
        public final static int ZOOM_ZOOM = 1;
        public final static int ZOOM_ZOOM_CENTER = 2;

        public final LayerSelectionAdapter adapter;
        public final int zoomOnClick;
        public final OnItemLongClickListener longClickListener;
        public final boolean showOutlinesButton;
        public final int selectionType;

        public AdapterSpec(LayerSelectionAdapter adapter, int selectionType,
                int zoomOnClick, boolean showOutlinesButton,
                OnItemLongClickListener l) {
            this.adapter = adapter;
            switch (selectionType) {
                case DATASET:
                case IMAGERY_TYPE:
                    this.selectionType = selectionType;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            switch (zoomOnClick) {
                case ZOOM_NONE:
                case ZOOM_ZOOM:
                case ZOOM_ZOOM_CENTER:
                    this.zoomOnClick = zoomOnClick;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            this.showOutlinesButton = showOutlinesButton;
            this.longClickListener = l;
        }
    }

    public final static class DatasetIngestCallback implements
            InteractiveServiceProvider.Callback {

        private final File file;
        private boolean canceled;

        public DatasetIngestCallback(File file) {

            this.file = file;
            this.canceled = false;
        }

        public void cancel() {
            this.canceled = true;
        }

        /**********************************************************************/
        // 
        @Override
        public boolean isCanceled() {
            return this.canceled;
        }

        @Override
        public boolean isProbeOnly() {
            return false;
        }

        @Override
        public int getProbeLimit() {
            return 0;
        }

        @Override
        public void setProbeMatch(boolean match) {
        }

        @Override
        public void errorOccurred(String msg, Throwable t) {
            // XXX - post notification ???
        }

        @Override
        public void progress(int itemsProcessed) {
            LayersNotificationManager.notifyImportProgress(this.file,
                    itemsProcessed);
        }
    }

    /**************************************************************************/
    // On Proxy Subject Changed Listener

    @Override
    public void onProxySubjectChanged(ProxyLayer layer) {
        final LayerSelectionAdapter active = this.layerToAdapter
                .get(this.rasterLayers.get());
        for (LayerSelectionAdapter adapter : this.layerToAdapter.values())
            adapter.setActive(adapter == active);
    }

    /**************************************************************************/
    // On Selection Changed Listener

    @Override
    public void onSelectionChanged(final RasterLayer2 layer) {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                final LayerSelectionAdapter active = layerToAdapter
                        .get(layer);

                if (active != null && active.isActive())
                    active.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onClick(View v) {
        Context c = getMapView().getContext();

        // Select region
        if (v == selectButton) {
            downloader.promptSelectRegion();
            ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                    .reset();
            cancelButton.setEnabled(true);
            cancelButton.setText(R.string.cancel);
            downloadButton.setEnabled(false);
            selectButton.setEnabled(false);
            setRetain(true);
        }

        // Download selected region
        else if (v == downloadButton) {
            if (((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                    .isOfflineOnly()) {
                ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                        .setOfflineOnly(false);
                offlineOnlyCheckbox.setChecked(true);
            }

            // TODO test connection to WMS server before proceeding

            // enforce tile limit
            int tileCount = ((MobileLayerSelectionAdapter) _mobileLayersAdapter.adapter)
                    .getTileCount();
            if (tileCount <= 0) {
                Toast.makeText(c, R.string.download_tiles_min,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (tileCount > TILE_DOWNLOAD_LIMIT) {
                Toast.makeText(c, c.getString(R.string.download_limit,
                        TILE_DOWNLOAD_LIMIT), Toast.LENGTH_SHORT).show();
                return;
            }

            selectButton.setEnabled(true);
            setRetain(false);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(c,
                    android.R.layout.select_dialog_singlechoice);
            adapter.add(c.getString(R.string.create_new_tileset));
            adapter.add(c.getString(R.string.add_to_existing_tileset));

            AlertDialog.Builder b = new AlertDialog.Builder(c);
            b.setTitle(R.string.choose_tileset_dest);
            b.setCancelable(true);
            b.setNegativeButton(R.string.cancel, null);
            b.setAdapter(adapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0)
                        createNewLayer();
                    else if (which == 1)
                        promptExistingLayer();
                }
            });
            listDialog = b.create();
            listDialog.show();
            return;
        }

        // Cancel download
        else if (v == cancelButton) {
            cancelDownloadWorkflow();
            return;
        }

        // Add WMS / WMTS map source
        else if (v.getId() == R.id.addOnlineSource_btn) {

            final LayoutInflater inflater = LayoutInflater.from(c);
            final View view = inflater.inflate(R.layout.wmswfs_dialog, null,
                    false);
            final EditText input = view.findViewById(R.id.wmswfs_address);
            // Use the last string the user entered into the box, so that
            // they don't have to start from scratch in case they
            // fat-finger something
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(c);
            String lastEntry = prefs.getString("pref_wms_add_last_entry", "");
            if (lastEntry != null) {
                input.setText(lastEntry);
                input.setSelection(lastEntry.length());
            }
            // pop up a dialog requesting the user to enter a URL to
            // a WMS or WMTS server
            AlertDialog.Builder b = new AlertDialog.Builder(c);
            b.setTitle(R.string.online_source_dialogue);
            b.setView(view);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d,
                                int whichButton) {
                            String s = input.getText().toString();
                            prefs.edit().putString("pref_wms_add_last_entry", s)
                                    .apply();
                            queryMapService(s);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            AlertDialog ad = b.create();
            ad.show();
            AlertDialogHelper.adjustWidth(ad, .90);
        }
    }

    interface MapServiceTaskCallback {
        void selectQueryLayersToAdd(String uri, ServiceListing querier);
    }

    // Map service query uses the network, must use AsyncTask
    static class MapServiceTask
            extends AsyncTask<String, Void, Set<ServiceListing>> {

        private Exception error = null;
        private String url = null;
        private ProgressDialog pd;
        private final MapView mapView;
        private final MapServiceTaskCallback callback;

        public MapServiceTask(@NonNull MapView mapView,
                @NonNull MapServiceTaskCallback callback) {
            this.mapView = mapView;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd = new ProgressDialog(mapView.getContext());
            pd.setTitle(mapView.getContext()
                    .getString(R.string.querying_the_server));
            pd.setMessage(mapView.getContext().getString(R.string.please_wait));
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.show();
        }

        @Override
        protected Set<ServiceListing> doInBackground(
                String... servicePath) {

            try {
                url = servicePath[0];
                return ServiceFactory.queryServices(url, false);
            } catch (Exception e) {
                // error should not occur, but better to be safe
                this.error = e;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Set<ServiceListing> results) {

            if (pd != null) {
                pd.dismiss();
            }
            // If one of our services can be reached, select that
            // layer. Otherwise, provide toasts that show the
            // errors that occurred for both of them.
            if (!results.isEmpty()) {
                final ServiceListing[] svcs = results
                        .toArray(new ServiceListing[0]);

                // if more than one server type was discovered, prompt the
                // user for what server they want
                if (svcs.length > 1) {
                    java.util.Arrays.sort(svcs,
                            new Comparator<ServiceListing>() {
                                @Override
                                public int compare(ServiceListing lhs,
                                        ServiceListing rhs) {
                                    return lhs.serverType
                                            .compareToIgnoreCase(
                                                    rhs.serverType);
                                }
                            });
                    final String[] svcNames = new String[svcs.length];
                    for (int i = 0; i < svcs.length; i++)
                        svcNames[i] = svcs[i].serverType;

                    new AlertDialog.Builder(mapView.getContext())
                            .setCancelable(false)
                            .setTitle(R.string.select_content_type_to_add)
                            .setItems(svcNames,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which) {

                                            callback.selectQueryLayersToAdd(url,
                                                    svcs[which]);
                                        }
                                    })
                            .setOnCancelListener(new OnCancelListener() {
                                @Override
                                public void onCancel(
                                        DialogInterface dialog) {
                                }
                            })
                            .show();

                } else {
                    callback.selectQueryLayersToAdd(url, svcs[0]);
                }
            } else {
                String msg;
                if (error != null) {
                    msg = mapView.getContext().getString(
                            R.string.could_not_query_wms)
                            + error.getMessage();
                } else {
                    msg = "Failed to discover services on " + url;
                }

                Toast.makeText(
                        mapView.getContext(),
                        msg,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
