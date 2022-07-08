
package com.atakmap.android.update;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.update.sorters.ProductInformationComparator;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages a list of App Updates
 *
 *
 */
public class ProductInformationAdapter extends BaseAdapter {

    private static final String TAG = "ProductInformationAdapter";

    private static final long LARGE_FILE = 10 * 1024 * 1024;

    private static class ProductStatus {
        String message;
        STATUS_COLOR overall;
        CURRENT_STATUS current;
        UPDATE_AVAILABILITY availability;

        public ProductStatus(String message, STATUS_COLOR overall,
                CURRENT_STATUS current, UPDATE_AVAILABILITY availability) {
            this.message = message;
            this.overall = overall;
            this.current = current;
            this.availability = availability;
        }

        @Override
        public String toString() {
            return overall.toString() + ", " + message;
        }
    }

    private enum STATUS_COLOR {
        Current(0x1600FF00),
        UpdateAvailable(0x16FFFF00),
        NotCompatible(0x16FF0000),
        NotInstalled(0x00000000);

        private final int color;

        STATUS_COLOR(int color) {
            this.color = color;
        }

        public int getColor() {
            return this.color;
        }
    }

    private enum CURRENT_STATUS {
        Installed(R.string.app_mgmt_filter_installed), //app is installed
        NotInstalled(R.string.app_mgmt_filter_Notinstalled), //app is not installed
        Loaded(R.string.app_mgmt_filter_loaded), //plugin is loaded
        NotLoaded(R.string.app_mgmt_filter_NotLoaded), //plugin is not loaed
        NotCompatible(R.string.app_mgmt_filter_NotCompatible); //app/plugin is not compatible
        //UpdateAvailable(R.string.app_mgmt_filter_Outofdate);

        private final int stringResource;

        CURRENT_STATUS(int stringResource) {
            this.stringResource = stringResource;
        }

        @Override
        public String toString() {
            return MapView.getMapView().getContext().getString(stringResource);
        }
    }

    private enum UPDATE_AVAILABILITY {
        Current(R.string.app_mgmt_filter_Current),
        //TODO remove this one, and just use UpdateAvailable?
        InstallAvailable(R.string.app_mgmt_filter_available), //plugin is installed, and may be loaded
        NotCompatible(R.string.app_mgmt_filter_NotCompatible),
        UpdateAvailable(R.string.app_mgmt_filter_update_available);

        private final int stringResource;

        UPDATE_AVAILABILITY(int stringResource) {
            this.stringResource = stringResource;
        }

        @Override
        public String toString() {
            return MapView.getMapView().getContext().getString(stringResource);
        }
    }

    private enum STATUS_FILTER {
        All(R.string.app_mgmt_filter_All),
        Current(R.string.app_mgmt_filter_Current),
        UpdateAvailable(R.string.app_mgmt_filter_update_available),
        Loaded(R.string.app_mgmt_filter_loaded),
        NotLoaded(R.string.app_mgmt_filter_NotLoaded),
        NotInstalled(R.string.app_mgmt_filter_Notinstalled),
        NotCompatible(R.string.app_mgmt_filter_NotCompatible),
        Installed(R.string.app_mgmt_filter_installed);

        private final int stringResource;

        STATUS_FILTER(int stringResource) {
            this.stringResource = stringResource;
        }

        @Override
        public String toString() {
            return MapView.getMapView().getContext().getString(stringResource);
        }
    }

    public enum Mode {
        All(R.string.app_mgmt_filter_All),
        MultiInstall(R.string.app_mgmt_multi_install), //Products which can be installed/updated
        MultiUninstall(R.string.app_mgmt_multi_uninstall), //Products which can be uninstalled
        MultiLoad(R.string.app_mgmt_multi_load), //Plugins which are installed and can be loaded/unloaded
        //MultiUnload(R.string.app_mgmt_multi_unload),
        ResolveIncompatible(R.string.app_mgmt_multi_resolveIncompat); //Products which are installed and incompat

        private final int stringResource;

        Mode(int stringResource) {
            this.stringResource = stringResource;
        }

        @Override
        public String toString() {
            return MapView.getMapView().getContext().getString(stringResource);
        }
    }

    private final MapView _mapView;

    /**
     * Context used for dialog creation
     */
    private final AppMgmtActivity _context;
    private final LayoutInflater _inflater;
    private List<ProductInformation> _appList;
    private List<ProductInformation> _filteredAppList;
    private final ArrayList<ProductInformation> selected = new ArrayList<>();
    private String _searchTerms, _filterRepoTerm;
    private STATUS_FILTER _filterTerm;
    private Mode _mode;
    private boolean portrait;

    /**
     * Initial list of apps
     *
     * @param mapView the mapView
     * @param context the context
     * @param list this list
     */
    ProductInformationAdapter(MapView mapView,
            final AppMgmtActivity context, List<ProductInformation> list) {
        _mapView = mapView;
        _context = context;
        _appList = list;
        _filteredAppList = list;
        _mode = Mode.All;
        _inflater = LayoutInflater.from(mapView.getContext());
        portrait = false;
    }

    public void setPortrait(boolean portrait) {
        this.portrait = portrait;
    }

    public void dispose() {
        if (_appList != null)
            _appList.clear();
        if (_filteredAppList != null)
            _filteredAppList.clear();
    }

    public void resetSearch() {
        _searchTerms = null;
        _filterTerm = null;
        _filterRepoTerm = null;
        _mode = Mode.All;
        refresh(null);
    }

    public void setMode(Mode mode) {
        _mode = mode;
        selected.clear();

        //check boxes for currently loaded plugins
        if (_mode == Mode.MultiLoad) {
            for (ProductInformation app : _appList) {
                if (app.isPlugin()) {
                    CURRENT_STATUS current = getCurrentStatus(app);
                    if (current == CURRENT_STATUS.Loaded) {
                        selected.add(app);
                    }
                }
            }
        }

        //TODO check that at least one checkbox will be displayed...
        refresh(null);
    }

    public Mode getMode() {
        return _mode;
    }

    public void search(String terms) {
        if (!FileSystemUtils.isEmpty(terms))
            terms = terms.toLowerCase(LocaleUtil.getCurrent());
        _searchTerms = terms;
        refresh(null);
    }

    public void filter(String filter) {
        if (FileSystemUtils.isEmpty(filter)
                || FileSystemUtils.isEquals(filter,
                        _context.getString(R.string.app_mgmt_filter_All))) {
            _filterTerm = null;
        } else if (FileSystemUtils.isEquals(filter,
                _context.getString(R.string.app_mgmt_filter_Current))) {
            _filterTerm = STATUS_FILTER.Current;
        } else if (FileSystemUtils.isEquals(filter,
                _context.getString(
                        R.string.app_mgmt_filter_update_available))) {
            _filterTerm = STATUS_FILTER.UpdateAvailable;
        } else if (FileSystemUtils.isEquals(filter,
                _context.getString(R.string.app_mgmt_filter_loaded))) {
            _filterTerm = STATUS_FILTER.Loaded;
        } else if (FileSystemUtils.isEquals(filter,
                _context.getString(R.string.app_mgmt_filter_NotLoaded))) {
            _filterTerm = STATUS_FILTER.NotLoaded;
        } else if (FileSystemUtils.isEquals(filter,
                _context.getString(R.string.app_mgmt_filter_Notinstalled))) {
            _filterTerm = STATUS_FILTER.NotInstalled;
        } else if (FileSystemUtils.isEquals(filter,
                _context.getString(R.string.app_mgmt_filter_NotCompatible))) {
            _filterTerm = STATUS_FILTER.NotCompatible;
        } else if (FileSystemUtils.isEquals(filter,
                _context.getString(R.string.app_mgmt_filter_installed))) {
            _filterTerm = STATUS_FILTER.Installed;
        } else {
            Log.w(TAG, "Invalid filter: " + filter);
            _filterTerm = null;
        }

        refresh(null);
    }

    public void filterRepo(String filter) {
        _filterRepoTerm = filter;
        refresh(null);
    }

    public void setAllSelected(boolean bSelected) {
        selected.clear();
        if (bSelected) {
            selected.addAll(_filteredAppList);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return _filteredAppList.size();
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        CheckBox checkbox;
        ImageView appIcon;
        TextView label;
        TextView status;
        //Button to load a plugin that is OS installed and ATAK compat, but not currently loaded
        CheckBox loadPluginChk;
        TextView availability;
        ImageButton details;
        ImageButton uninstall;
        ImageView trusted;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ProductInformation app = _filteredAppList.get(position);

        View row = convertView;
        ViewHolder holder;

        if (row == null) {
            if (portrait) {
                row = _inflater.inflate(R.layout.app_mgmt_row_portrait, null);
            } else {
                row = _inflater.inflate(R.layout.app_mgmt_row, null);
            }

            holder = new ViewHolder();
            holder.checkbox = row
                    .findViewById(R.id.app_mgmt_row_checkbox);
            holder.appIcon = row
                    .findViewById(R.id.app_mgmt_row_icon);
            holder.label = row.findViewById(R.id.app_mgmt_row_label);
            holder.status = row
                    .findViewById(R.id.app_mgmt_row_status);
            holder.loadPluginChk = row
                    .findViewById(R.id.app_mgmt_row_loadPlugin);
            holder.availability = row
                    .findViewById(R.id.app_mgmt_row_availability);
            holder.details = row
                    .findViewById(R.id.app_mgmt_row_details);
            holder.uninstall = row
                    .findViewById(R.id.app_mgmt_row_uninstall);
            holder.trusted = row
                    .findViewById(R.id.app_mgmt_row_trusted);

            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        holder.checkbox
                .setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton buttonView,
                                    boolean isChecked) {
                                if (isChecked) {
                                    if (!selected.contains(app))
                                        selected.add(app);
                                } else {
                                    selected.remove(app);
                                }

                                _context.setAllSelected(selected
                                        .size() == _filteredAppList.size());
                            }
                        });

        holder.loadPluginChk.setVisibility(View.GONE);

        //populate current status
        final ProductStatus productStatus = getStatus(app);
        holder.checkbox.setChecked(_mode != Mode.All && selected.contains(app));

        //only display checkboxes for products which are eligible to move into the selected mode
        boolean showCheckbox = appliesToMode(app, productStatus);
        holder.checkbox.setVisibility(showCheckbox ? View.VISIBLE
                : (portrait ? View.GONE : View.INVISIBLE));

        holder.status.setText(productStatus.current.toString());
        if (_mode != Mode.All) {
            holder.loadPluginChk.setVisibility(View.GONE);
            holder.status.setVisibility(View.VISIBLE);
        } else if (productStatus.current == CURRENT_STATUS.NotLoaded
                || productStatus.current == CURRENT_STATUS.Loaded) {
            holder.loadPluginChk.setVisibility(View.VISIBLE);
            holder.status.setVisibility(View.GONE);
            holder.loadPluginChk
                    .setChecked(productStatus.current == CURRENT_STATUS.Loaded);
            holder.loadPluginChk
                    .setText(productStatus.current == CURRENT_STATUS.Loaded
                            ? "Loaded"
                            : "Not Loaded");
            if (productStatus.current == CURRENT_STATUS.Loaded) {
                holder.loadPluginChk.setTextColor(Color.GREEN);
            } else {
                holder.loadPluginChk.setTextColor(Color.LTGRAY);
            }
            holder.loadPluginChk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v instanceof CheckBox) {
                        if (((CheckBox) v).isChecked())
                            loadPlugin(app);
                        else
                            unloadPlugin(app);
                        notifyDataSetChanged();
                    }
                }
            });
        } else {
            holder.loadPluginChk.setVisibility(View.GONE);
            holder.status.setVisibility(View.VISIBLE);
        }

        if (app.productType == ProductInformation.ProductType.plugin &&
                AppMgmtUtils.isInstalled(_context, app.getPackageName())) {
            final int message;

            if (AtakPluginRegistry.verifyTrust(_context, app.packageName)) {
                holder.trusted.setImageDrawable(
                        _context.getDrawable(R.drawable.trusted));
                message = R.string.officially_signed;
            } else {
                holder.trusted.setImageDrawable(
                        _context.getDrawable(R.drawable.untrusted));
                message = R.string.not_officially_signed;
            }

            holder.trusted.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                            _context);
                    alertBuilder
                            .setTitle(R.string.plugin_warning)
                            .setMessage(message)
                            .setPositiveButton(R.string.ok, null);
                    alertBuilder.create().show();

                }
            });
        } else {
            holder.trusted.setImageDrawable(
                    _context.getDrawable(R.drawable.disabled_trust));
            holder.trusted.setOnClickListener(null);
        }

        //now populate update availability
        holder.availability.setText(productStatus.availability.toString());
        row.setBackgroundColor(productStatus.overall.color);

        //populate row data
        Drawable d = app.getIcon();
        if (d != null) {
            holder.appIcon.setImageDrawable(d);
        } else {
            Log.d(TAG, "Icon not found: " + app.getIconUri());
            holder.appIcon.setImageResource(
                    app.isPlugin() ? R.drawable.ic_menu_plugins
                            : R.drawable.ic_menu_apps);
        }

        final boolean fCanInstall = canInstall(app);
        final boolean fCanUpdate = canUpdate(app);
        final boolean fCanUninstall = canUninstall(app);

        holder.label.setText(app.getSimpleName());

        if (_mode == Mode.All) {
            //not multi select mode
            holder.uninstall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    uninstall(app);
                }
            });
            holder.uninstall.setVisibility(fCanUninstall ? View.VISIBLE
                    : View.INVISIBLE);

            //touch listeners to view details
            holder.details.setVisibility(View.VISIBLE);
            holder.details.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAppOverview(app, productStatus, fCanInstall,
                            fCanUpdate, fCanUninstall);
                }
            });

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAppOverview(app, productStatus, fCanInstall,
                            fCanUpdate, fCanUninstall);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    showAppOverview(app, productStatus, fCanInstall,
                            fCanUpdate, fCanUninstall);
                    return true;
                }
            });
        } else if (holder.checkbox.getVisibility() == View.VISIBLE) {
            //multi-select mode, this row is checkable
            holder.uninstall.setVisibility(View.INVISIBLE);

            //touch listeners to toggle checkbox
            final ViewHolder fHolder = holder;
            holder.details.setVisibility(View.INVISIBLE);
            holder.details.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fHolder.checkbox.setChecked(!fHolder.checkbox.isChecked());
                }
            });

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fHolder.checkbox.setChecked(!fHolder.checkbox.isChecked());
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    fHolder.checkbox.setChecked(!fHolder.checkbox.isChecked());
                    return true;
                }
            });
        } else {
            //multi-select mode, this row is not checkable
            holder.uninstall.setVisibility(View.INVISIBLE);

            //touch listeners to toggle checkbox
            holder.details.setVisibility(View.INVISIBLE);
            holder.details.setOnClickListener(null);

            row.setOnClickListener(null);
            row.setOnLongClickListener(null);
        }

        return row;
    }

    private boolean appliesToMode(ProductInformation app, ProductStatus s) {

        // Apps which can be installed
        if (_mode == Mode.MultiInstall)
            return s.availability == UPDATE_AVAILABILITY.InstallAvailable
                    || s.availability == UPDATE_AVAILABILITY.UpdateAvailable;

        // Apps which can be uninstalled
        else if (_mode == Mode.MultiUninstall)
            return canUninstall(app);

        // Apps which can be loaded/unloaded
        else if (_mode == Mode.MultiLoad)
            return app.isPlugin() && (s.current == CURRENT_STATUS.Loaded
                    || s.current == CURRENT_STATUS.NotLoaded);

        // Incompatible apps
        else if (_mode == Mode.ResolveIncompatible)
            return app.isInstalled() && isLocalIncompat(app);

        return false;
    }

    public List<ProductInformation> getSelected() {
        List<ProductInformation> temp = new ArrayList<>(selected);
        Collections.sort(temp, new ProductInformationComparator());
        return temp;
    }

    public List<ProductInformation> getUnselected() {
        List<ProductInformation> temp = new ArrayList<>(_appList);
        temp.removeAll(selected);
        Collections.sort(temp, new ProductInformationComparator());
        return temp;
    }

    private static STATUS_COLOR getStatusColor(
            UPDATE_AVAILABILITY availability) {
        switch (availability) {
            case Current: {
                return STATUS_COLOR.Current;
            }
            case InstallAvailable: {
                return STATUS_COLOR.UpdateAvailable;
            }
            case NotCompatible: {
                return STATUS_COLOR.NotCompatible;
            }
            case UpdateAvailable: {
                return STATUS_COLOR.UpdateAvailable;
            }
            default:
                return STATUS_COLOR.NotInstalled;
        }
    }

    private STATUS_COLOR getStatusColor(CURRENT_STATUS status) {
        switch (status) {
            case Installed: {
                return STATUS_COLOR.Current;
            }
            case NotInstalled: {
                return STATUS_COLOR.NotInstalled;
            }
            case Loaded: {
                return STATUS_COLOR.Current;
            }
            case NotLoaded: {
                return STATUS_COLOR.UpdateAvailable;
            }
            case NotCompatible: {
                return STATUS_COLOR.NotCompatible;
            }
            default:
                return STATUS_COLOR.NotInstalled;
        }
    }

    private ProductStatus getStatus(ProductInformation app) {
        return getStatus(app, getCurrentStatus(app), getAvailability(app));
    }

    private ProductStatus getStatus(ProductInformation app,
            CURRENT_STATUS status, UPDATE_AVAILABILITY availability) {

        switch (status) {
            case Installed: {
                switch (availability) {
                    case Current: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_installed_current);
                        return new ProductStatus(message, STATUS_COLOR.Current,
                                status, availability);
                    }
                    case InstallAvailable: {
                        String message = app.getSimpleName()
                                + " "
                                + getVersionString(app)
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_installed_available);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case NotCompatible: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_installed_notcompat)
                                + ", " + app.getInCompatibilityReason();
                        return new ProductStatus(message, STATUS_COLOR.Current,
                                status, availability);
                    }
                    case UpdateAvailable: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_installed_updateavailable)
                                + ", " + getVersionString(app);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    default:
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled);
                        return new ProductStatus(message,
                                STATUS_COLOR.NotInstalled, status,
                                availability);
                }
            }
            case NotInstalled: {
                switch (availability) {
                    case Current: {
                        //this one shouldn't happen
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case InstallAvailable: {
                        String message = app.getSimpleName()
                                + getVersionString(app)
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled_available);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case NotCompatible: {
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled_notcompat)
                                + ", " + app.getInCompatibilityReason();
                        return new ProductStatus(message,
                                STATUS_COLOR.NotCompatible, status,
                                availability);
                    }
                    case UpdateAvailable: {
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled_updateavailable)
                                + ", " + getVersionString(app);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    default:
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled);
                        return new ProductStatus(message,
                                STATUS_COLOR.NotInstalled, status,
                                availability);
                }
            }
            case Loaded: {
                switch (availability) {
                    case Current: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_loaded_current);
                        return new ProductStatus(message, STATUS_COLOR.Current,
                                status, availability);
                    }
                    case InstallAvailable: {
                        //this one shouldn't happen
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_loaded_available);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case NotCompatible: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_loaded_notcompat)
                                + ", " + app.getInCompatibilityReason();
                        return new ProductStatus(message, STATUS_COLOR.Current,
                                status, availability);
                    }
                    case UpdateAvailable: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_loaded_updateavailable)
                                + ", " + getVersionString(app);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    default:
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled);
                        return new ProductStatus(message,
                                STATUS_COLOR.NotInstalled, status,
                                availability);
                }
            }
            case NotLoaded: {
                switch (availability) {
                    case Current: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notloaded_current);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case InstallAvailable: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notloaded_available);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case NotCompatible: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notloaded_notcompat)
                                + ", " + app.getInCompatibilityReason();
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case UpdateAvailable: {
                        String message = app.getSimpleName()
                                + getLocalVersionString(app)
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notloaded_updateavailable)
                                + ", " + getVersionString(app);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    default:
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled);
                        return new ProductStatus(message,
                                STATUS_COLOR.NotInstalled, status,
                                availability);
                }
            }
            case NotCompatible: {

                switch (availability) {
                    case Current: {
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notcompat_current)
                                + ", " + getLocalIncompatReason(app);
                        return new ProductStatus(message,
                                STATUS_COLOR.NotCompatible, status,
                                availability);
                    }
                    case InstallAvailable: {
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notcompat_available)
                                + ", " + getLocalIncompatReason(app);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    case NotCompatible: {
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notcompat_notcompat)
                                + ", " + app.getInCompatibilityReason();
                        return new ProductStatus(message,
                                STATUS_COLOR.NotCompatible, status,
                                availability);
                    }
                    case UpdateAvailable: {
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notcompat_updateavailable)
                                + ", " + getVersionString(app);
                        return new ProductStatus(message,
                                STATUS_COLOR.UpdateAvailable, status,
                                availability);
                    }
                    default:
                        String message = app.getSimpleName()
                                + " "
                                + _context
                                        .getString(
                                                R.string.app_mgmt_message_notinstalled);
                        return new ProductStatus(message,
                                STATUS_COLOR.NotInstalled, status,
                                availability);
                }
            }
            default:
                String message = app.getSimpleName()
                        + " "
                        + _context
                                .getString(
                                        R.string.app_mgmt_message_notinstalled);
                return new ProductStatus(message, STATUS_COLOR.NotInstalled,
                        status, availability);
        }
    }

    private boolean isLocalIncompat(ProductInformation app) {
        return FileSystemUtils.isEmpty(getLocalIncompatReason(app));
    }

    private String getLocalIncompatReason(ProductInformation app) {
        String currentTakRequirement = AtakPluginRegistry.getPluginApiVersion(
                _context, app.getPackageName(), false);

        if (!app.isSignatureValid()) {
            return "The plugin is not signed correctly and will not be loaded.";
        }

        if (AtakPluginRegistry.isTakCompatible(app.getPackageName(),
                currentTakRequirement)) {
            return "requires TAK v"
                    + AtakPluginRegistry.stripPluginApiVersion(app
                            .getTakRequirement());
        }

        return "";
    }

    private String getVersionString(ProductInformation app) {
        String version = "";
        if (!FileSystemUtils.isEmpty(app.getVersion()))
            version = app.getVersion();
        if (app.getRevision() != AppMgmtUtils.APP_NOT_INSTALLED)
            version += " (" + app.getRevision() + ") ";
        if (!FileSystemUtils.isEmpty(version))
            version = " v" + version;
        return version;
    }

    private String getLocalVersionString(ProductInformation app) {
        String version = "";
        if (AppMgmtUtils.isInstalled(_context, app.getPackageName())) {
            version = " v"
                    + AppMgmtUtils.getAppVersionName(_context,
                            app.getPackageName());
            int temp2 = AppMgmtUtils.getAppVersionCode(_context,
                    app.getPackageName());
            if (temp2 != AppMgmtUtils.APP_NOT_INSTALLED) {
                version += " (" + temp2 + ") ";
            }
        }

        return version;
    }

    private boolean canInstall(ProductInformation app) {
        //check for update available
        return (app.isCompatible() && !app.isInstalled())
                //also check if plugin is installed by not loaded
                || (app.isPlugin()
                        && app.isInstalled()
                        && AtakPluginRegistry.isTakCompatible(_context,
                                app.getPackageName())
                        && !AtakPluginRegistry.get().isPluginLoaded(
                                app.getPackageName()));

    }

    private boolean canUpdate(ProductInformation app) {
        return app.isCompatible() && app.isInstalled()
                && app.getInstalledVersion() < app.getRevision();
    }

    private boolean canUninstall(ProductInformation app) {

        // do not allow a user to uninstall the base package
        if (AtakPluginRegistry.isAtak(app.getPackageName())) {
            return false;
        }

        return app.isInstalled();
    }

    private UPDATE_AVAILABILITY getAvailability(ProductInformation app) {
        if (!app.isCompatible()) {
            //TODO should we enforce compatability for non-plugins? or just display that an update is available? Or make it a pref?
            return UPDATE_AVAILABILITY.NotCompatible;
        } else if (!app.isInstalled()) {
            return UPDATE_AVAILABILITY.InstallAvailable;
        } else if (app.getInstalledVersion() < app.getRevision()) {
            return UPDATE_AVAILABILITY.UpdateAvailable;
        } else {
            return UPDATE_AVAILABILITY.Current;
        }
    }

    private CURRENT_STATUS getCurrentStatus(ProductInformation app) {
        if (app.isPlugin()) {
            if (!app.isInstalled()) {
                return CURRENT_STATUS.NotInstalled;
            } else {
                //plugin is OS installed

                boolean signatureValid = app.isSignatureValid();
                boolean bCurrentCompat = AtakPluginRegistry.isTakCompatible(
                        _context, app.getPackageName());
                boolean bPluginLoaded = AtakPluginRegistry.get()
                        .isPluginLoaded(app.getPackageName());

                if (!signatureValid)
                    return CURRENT_STATUS.NotCompatible;

                if (!bCurrentCompat) {
                    return CURRENT_STATUS.NotCompatible;

                } else if (bPluginLoaded) {
                    return CURRENT_STATUS.Loaded;

                } else {
                    //OS installed, and ATAK compatible, but not loaded in ATAK
                    return CURRENT_STATUS.NotLoaded;
                }
            }
        } else {
            //set current app status, we do not track compatibility of installed non-plugins
            if (!app.isInstalled()) {
                return CURRENT_STATUS.NotInstalled;
            } else {
                return CURRENT_STATUS.Installed;
            }
        }
    }

    private void loadPlugin(final ProductInformation plugin) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(_context)
                .setTitle("Load " + plugin.getSimpleName())
                .setMessage(
                        plugin.getSimpleName()
                                + " is already installed into Android and is compatible, would you like to load this plugin into "
                                + _context.getString(R.string.app_name)
                                + " now?");

        Drawable icon = plugin.getIcon();
        if (icon != null) {
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, icon));
        } else {
            dialog.setIcon(plugin.isPlugin() ? R.drawable.ic_menu_plugins
                    : R.drawable.ic_menu_apps);
        }

        dialog.setPositiveButton(R.string.load,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "Loading per user: " + plugin);
                        if (!SideloadedPluginProvider.installProduct(plugin)) {
                            Toast.makeText(_context,
                                    "Failed to load " + plugin.getSimpleName(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                //user cancelled, just refresh the check boxes
                                AtakBroadcast
                                        .getInstance()
                                        .sendBroadcast(
                                                new Intent(
                                                        ProductProviderManager.PRODUCT_REPOS_REFRESHED));

                            }
                        })
                .setCancelable(false)
                .show();
    }

    private void unloadPlugin(final ProductInformation plugin) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(_context)
                .setTitle(String.format(
                        _context.getString(R.string.unload_plugin_title),
                        plugin.getSimpleName()))
                .setMessage(String.format(
                        _context.getString(R.string.unload_plugin_message),
                        plugin.getSimpleName()));

        Drawable icon = plugin.getIcon();
        if (icon != null) {
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, icon));
        } else {
            dialog.setIcon(plugin.isPlugin() ? R.drawable.ic_menu_plugins
                    : R.drawable.ic_menu_apps);
        }

        dialog.setPositiveButton(R.string.unload_plugin,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "Unloading per user: " + plugin);
                        SideloadedPluginProvider.unloadProduct(plugin);
                    }
                })
                .setNegativeButton(R.string.cancel, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                //user cancelled, just refresh the check boxes
                                AtakBroadcast
                                        .getInstance()
                                        .sendBroadcast(
                                                new Intent(
                                                        ProductProviderManager.PRODUCT_REPOS_REFRESHED));
                            }
                        })
                .setCancelable(false)
                .show();
    }

    /**
     * Given a package name, show the application overview information.
     * @param pkg given a package attempt to show details about it.
     */
    public void showAppOverview(String pkg) {
        for (ProductInformation pi : _appList) {
            if (pi.getPackageName().equals(pkg)) {
                showAppOverview(pi);
                return;
            }
        }
    }

    public void showAppOverview(final ProductInformation app) {

        final ProductInformationAdapter.ProductStatus productStatus = getStatus(
                app);
        final boolean fCanInstall = canInstall(app);
        final boolean fCanUpdate = canUpdate(app);
        final boolean fCanUninstall = canUninstall(app);
        showAppOverview(app, productStatus, fCanInstall,
                fCanUpdate, fCanUninstall);
    }

    /**
     * Display app overview dialog
     *
     * @param app
     * @param bCanInstall
     * @param bCanUpdate
     * @param bCanUninstall
     */
    private void showAppOverview(final ProductInformation app,
            final ProductStatus status,
            final boolean bCanInstall, final boolean bCanUpdate,
            final boolean bCanUninstall) {
        if (app == null || !app.isValid()) {
            Log.w(TAG, "Cannot show overview for invalid app");
            Toast.makeText(_context,
                    "Invalid app",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG,
                "showAppOverview: " + app + ", "
                        + status.toString());

        View detailView = _inflater.inflate(R.layout.app_mgmt_product_overview,
                null);
        String temp = app.getDescription();
        View descView = detailView
                .findViewById(R.id.app_mgmt_product_description_layout);
        if (FileSystemUtils.isEmpty(temp)) {
            descView.setVisibility(View.GONE);
            setTextView(
                    detailView
                            .findViewById(R.id.app_mgmt_product_desc),
                    "");
        } else {
            descView.setVisibility(View.VISIBLE);
            setTextView(
                    detailView
                            .findViewById(R.id.app_mgmt_product_desc),
                    temp);
        }

        temp = status.message;
        TextView statusText = detailView
                .findViewById(R.id.app_mgmt_product_status);
        setTextView(statusText, temp);

        detailView.findViewById(R.id.app_mgmt_product_status_layout)
                .setBackgroundColor(status.overall.color);

        Button detailsBtn = detailView
                .findViewById(R.id.app_mgmt_product_details_button);

        final AlertDialog.Builder dialog = new AlertDialog.Builder(_context)
                .setTitle(app.getSimpleName())
                .setView(detailView);

        Drawable icon = app.getIcon();
        if (icon != null) {
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, icon));
        } else {
            dialog.setIcon(app.isPlugin() ? R.drawable.ic_menu_plugins
                    : R.drawable.ic_menu_apps);
        }

        if (bCanInstall) {
            String message = "Install";
            if (app.isPlugin() && app.isInstalled()) {
                message = "Load";
            }

            dialog.setPositiveButton(message,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            install(app);
                        }
                    });
        }
        if (bCanUpdate) {
            dialog.setPositiveButton("Update",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Update: " + app);
                            install(app);
                        }
                    });
        }
        if (bCanUninstall) {
            dialog.setNeutralButton("Uninstall", // implemented
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Uninstall: " + app);
                            uninstall(app);
                        }
                    });
        }
        dialog.setNegativeButton(R.string.cancel, null);

        dialog.setCancelable(false);

        final AlertDialog dlg = dialog.create();

        detailsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                showAppDetails(app, status, bCanInstall, bCanUpdate,
                        bCanUninstall);
            }
        });

        // set dialog dims appropriately based on device size
        WindowManager.LayoutParams screenLP = new WindowManager.LayoutParams();
        Window w = dlg.getWindow();
        if (w != null) {
            screenLP.copyFrom(w.getAttributes());
            screenLP.width = WindowManager.LayoutParams.MATCH_PARENT;
            screenLP.height = WindowManager.LayoutParams.MATCH_PARENT;
            w.setAttributes(screenLP);
        }

        try {
            dlg.show();
        } catch (Exception ignored) {
        }
    }

    /**
     * Display app details dialog
     *  @param app
     * @param status
     * @param bCanInstall
     * @param bCanUpdate
     * @param bCanUninstall
     */
    private void showAppDetails(final ProductInformation app,
            ProductStatus status, final boolean bCanInstall,
            final boolean bCanUpdate, final boolean bCanUninstall) {
        if (app == null || !app.isValid()) {
            Log.w(TAG, "Cannot show details for invalid app");
            Toast.makeText(_context,
                    "Invalid app",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "showAppDetails: " + app);

        View detailView = _inflater.inflate(R.layout.app_mgmt_product_details,
                null);
        String temp = app.isPlugin()
                ? (_context.getString(R.string.app_name) + " Plugin")
                : (app.getPlatform().toString() + " " + app.getProductType()
                        .toString());
        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_platform),
                temp);

        temp = "";
        if (!FileSystemUtils.isEmpty(app.getVersion()))
            temp = app.getVersion();
        if (app.getRevision() != AppMgmtUtils.APP_NOT_INSTALLED)
            temp += " (" + app.getRevision() + ")";
        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_version),
                temp);

        temp = "";
        if (app.hasFileSize())
            temp = MathUtils.GetLengthString(app.getFileSize());
        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_size),
                temp);

        temp = app.getDescription();
        View descView = detailView
                .findViewById(R.id.app_mgmt_product_description_layout);
        if (FileSystemUtils.isEmpty(temp)) {
            descView.setVisibility(View.GONE);
            setTextView(
                    detailView
                            .findViewById(R.id.app_mgmt_product_desc),
                    "");
        } else {
            descView.setVisibility(View.VISIBLE);
            setTextView(
                    detailView
                            .findViewById(R.id.app_mgmt_product_desc),
                    temp);
        }

        temp = new CoordinatedTime(
                AppMgmtUtils.getInstalledDate(_context, app.getPackageName()))
                        .toString();
        TextView installDate = detailView
                .findViewById(R.id.app_mgmt_product_installdate);
        setTextView(installDate, temp);

        temp = new CoordinatedTime(
                AppMgmtUtils.getUpdateDate(_context, app.getPackageName()))
                        .toString();
        TextView updateDate = detailView
                .findViewById(R.id.app_mgmt_product_updatedate);
        setTextView(updateDate, temp);

        int view = View.VISIBLE;
        if (!AppMgmtUtils.isInstalled(_context, app.getPackageName())) {
            view = View.GONE;
        }
        updateDate.setVisibility(view);
        installDate.setVisibility(view);
        detailView.findViewById(R.id.app_mgmt_product_updatedate_label)
                .setVisibility(view);
        detailView.findViewById(R.id.app_mgmt_product_installdate_label)
                .setVisibility(view);

        Button certificateInfo = detailView
                .findViewById(R.id.app_mgmt_product_certinfo);
        final String[] sigs = AppMgmtUtils.getSignatureInfo(_context,
                app.getPackageName());
        if (sigs.length == 0) {
            certificateInfo.setVisibility(View.GONE);
        } else {
            certificateInfo.setVisibility(View.VISIBLE);
            certificateInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StringBuilder msg = new StringBuilder();
                    for (String sig : sigs) {
                        msg.append(sig);
                        msg.append("\n\n");
                    }

                    final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                            _context);
                    alertBuilder.setTitle(app.getSimpleName())
                            .setMessage(msg.toString())
                            .setPositiveButton(R.string.ok, null);
                    alertBuilder.create().show();
                }
            });
        }

        //set status of currently installed
        temp = "";
        if (AppMgmtUtils.isInstalled(_context, app.getPackageName())) {
            temp = AppMgmtUtils.getAppVersionName(_context,
                    app.getPackageName());
            int temp2 = AppMgmtUtils.getAppVersionCode(_context,
                    app.getPackageName());
            if (temp2 != AppMgmtUtils.APP_NOT_INSTALLED) {
                temp += " (" + temp2 + ")";
            }
        }

        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_installedVersion),
                temp);
        if (app.isInstalled()) {
            String currentTakRequirement = AtakPluginRegistry
                    .getPluginApiVersion(_context, app.getPackageName(), false);
            if (FileSystemUtils.isEmpty(currentTakRequirement)) {
                setTextView(
                        detailView
                                .findViewById(
                                        R.id.app_mgmt_product_installed_takreq),
                        "NA");
                ((ImageView) detailView
                        .findViewById(
                                R.id.app_mgmt_product_installed_takreq_compatible))
                                        .setImageResource(
                                                R.drawable.importmgr_status_green);
            } else {
                boolean bCurrentCompat = AtakPluginRegistry.isTakCompatible(
                        app.getPackageName(), currentTakRequirement);

                setTextView(
                        detailView
                                .findViewById(
                                        R.id.app_mgmt_product_installed_takreq),
                        currentTakRequirement);
                ((ImageView) detailView
                        .findViewById(
                                R.id.app_mgmt_product_installed_takreq_compatible))
                                        .setImageResource(
                                                bCurrentCompat
                                                        ? R.drawable.importmgr_status_green
                                                        : R.drawable.importmgr_status_red);
            }
        } else {
            setTextView(
                    detailView
                            .findViewById(
                                    R.id.app_mgmt_product_installed_takreq),
                    "");
            detailView.findViewById(
                    R.id.app_mgmt_product_installed_takreq_compatible)
                    .setVisibility(View.GONE);
        }

        //set status of available update
        temp = app.getOsRequirement() < 0 ? ""
                : (androidValueOf(app.getOsRequirement()));
        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_osreq),
                temp);
        ((ImageView) detailView
                .findViewById(R.id.app_mgmt_product_osreq_compatible))
                        .setImageResource(
                                app.isOsCompatible()
                                        ? R.drawable.importmgr_status_green
                                        : R.drawable.importmgr_status_yellow);

        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_takreq),
                app.getTakRequirement());
        ((ImageView) detailView
                .findViewById(R.id.app_mgmt_product_takreq_compatible))
                        .setImageResource(
                                app.isApiCompatible()
                                        ? R.drawable.importmgr_status_green
                                        : R.drawable.importmgr_status_red);

        ((TextView) detailView
                .findViewById(R.id.app_mgmt_product_signature_compatible))
                        .setText(
                                app.isSignatureValid()
                                        ? R.string.signature_valid
                                        : R.string.signature_invalid);
        ((ImageView) detailView
                .findViewById(R.id.app_mgmt_product_signature))
                        .setImageResource(
                                app.isSignatureValid()
                                        ? R.drawable.importmgr_status_green
                                        : R.drawable.importmgr_status_red);

        temp = "";
        if (app.getParent() != null) {
            temp = app.getParent().getRepoType();
        }
        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_repo),
                temp);
        setTextView(
                detailView
                        .findViewById(R.id.app_mgmt_product_package),
                app.getPackageName());

        //matches getView() logic above
        TextView currentLabel = detailView
                .findViewById(R.id.app_mgmt_product_currentlabel);
        View currentLayout = detailView
                .findViewById(R.id.app_mgmt_product_currentlayout);

        TextView availableLabel = detailView
                .findViewById(R.id.app_mgmt_product_availablelabel);
        View availableLayout = detailView
                .findViewById(R.id.app_mgmt_product_availablelayout);

        currentLabel.setText(status.current.toString());
        currentLayout.setBackgroundColor(getStatusColor(status.current).color);

        availableLabel.setText(status.availability.toString());
        availableLayout
                .setBackgroundColor(getStatusColor(status.availability).color);

        Button statusBtn = detailView
                .findViewById(R.id.app_mgmt_product_status_button);
        statusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ATAKConstants.displayAbout(_context, false);
            }
        });

        AlertDialog.Builder dialog = new AlertDialog.Builder(_context)
                .setTitle(app.getSimpleName())
                .setView(detailView);

        Drawable icon = app.getIcon();
        if (icon != null) {
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, icon));
        } else {
            dialog.setIcon(app.isPlugin() ? R.drawable.ic_menu_plugins
                    : R.drawable.ic_menu_apps);
        }

        if (bCanInstall) {
            String message = "Install Product";
            if (app.isPlugin() && app.isInstalled()) {
                message = "Load";
            }

            dialog.setPositiveButton(message,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            install(app);
                        }
                    });
        }
        if (bCanUpdate) {
            dialog.setPositiveButton(R.string.update,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Update: " + app);
                            install(app);
                        }
                    });
        }
        if (bCanUninstall) {
            dialog.setNeutralButton(R.string.uninstall, // implemented
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Uninstall: " + app);
                            uninstall(app);
                        }
                    });
        }

        dialog.setNegativeButton("Cancel", null);

        dialog.setCancelable(false);

        final AlertDialog dlg = dialog.create();

        // set dialog dims appropriately based on device size
        WindowManager.LayoutParams screenLP = new WindowManager.LayoutParams();
        Window w = dlg.getWindow();
        if (w != null) {
            screenLP.copyFrom(w.getAttributes());
            screenLP.width = WindowManager.LayoutParams.MATCH_PARENT;
            screenLP.height = WindowManager.LayoutParams.MATCH_PARENT;
            w.setAttributes(screenLP);
        }
        dlg.show();
    }

    private static String androidValueOf(final int version) {
        switch (version) {
            case 19:
                return "Android 4.4 (KitKat)";
            case 21:
                return "Android 5.0 (Lollipop)";
            case 22:
                return "Android 5.1 (Lollipop)";
            case 23:
                return "Android 6.0 (Marshmallow)";
            case 24:
                return "Android 7.0 (Nougat)";
            case 25:
                return "Android 7.1 (Nougat)";
            case 26:
                return "Android 8.0 (Oreo)";
            case 27:
                return "Android 8.1 (Oreo)";
            case 28:
                return "Android 9 (Pie)";
            case 29:
                return "Android 10";
            default:
                return "Greater than Android 10";
        }
    }

    private void setTextView(TextView viewById, String s) {
        viewById.setText(s == null ? "" : s);
    }

    private void install(final ProductInformation app) {

        final ProductProviderManager.Provider provider = _context
                .getProviderManager().getProvider(app);
        if (provider == null) {
            Log.w(TAG, "Cannot installProduct without provider: "
                    + app);
            Toast.makeText(_context,
                    "Failed to install: " + app.getSimpleName(),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String prefix = "Install Product ";
        if (app.isPlugin() && app.isInstalled()) {
            prefix = "Load ";
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(_context)
                .setTitle(prefix + (app.isPlugin() ? "Plugin" : "App"))
                .setMessage(
                        prefix + app.getSimpleName() + _mapView.getContext()
                                .getString(R.string.question_mark_symbol));

        Drawable icon = app.getIcon();
        if (icon != null)
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, icon));
        else
            dialog.setIcon(app.isPlugin() ? R.drawable.ic_menu_plugins
                    : R.drawable.ic_menu_apps);

        dialog.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "installProduct: " + app);

                        if (provider.isRemote()
                                && app.getFileSize() > LARGE_FILE) {
                            final String message = _context.getString(
                                    R.string.app_mgmt_update_large_file_message,
                                    app.getSimpleName(),
                                    MathUtils.GetLengthString(
                                            app.getFileSize()));

                            new AlertDialog.Builder(_context)
                                    .setTitle(
                                            R.string.app_mgmt_update_large_file_title)
                                    .setMessage(message)
                                    .setPositiveButton(R.string.download,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(
                                                        DialogInterface dialogInterface,
                                                        int i) {
                                                    provider.install(app);

                                                }
                                            })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show();
                        } else {
                            provider.install(app);
                        }
                    }
                });

        dialog.setNegativeButton(R.string.cancel, null);
        dialog.setCancelable(false);

        try {
            dialog.show();
        } catch (Exception ignored) {
        }
    }

    private void uninstall(final ProductInformation app) {

        final ProductProviderManager.Provider provider = _context
                .getProviderManager().getProvider(app);
        if (provider == null) {
            Log.w(TAG, "Cannot Uninstall without provider: " + app);
            Toast.makeText(_context,
                    "Failed to uninstall: " + app.getSimpleName(),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(_context)
                .setTitle("Uninstall " + (app.isPlugin() ? "Plugin" : "App"))
                .setMessage("Uninstall " + app.getSimpleName() +
                        _context.getString(R.string.question_mark_symbol));

        Drawable icon = app.getIcon();
        if (icon != null)
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, icon));
        else
            dialog.setIcon(app.isPlugin() ? R.drawable.ic_menu_plugins
                    : R.drawable.ic_menu_apps);

        dialog.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "Uninstall: " + app);
                        try {
                            SideloadedPluginProvider.unloadProduct(app);
                            provider.uninstall(app);

                        } catch (Exception e) {
                            Log.w(TAG,
                                    "Error during uninstall: " + app,
                                    e);
                        }
                    }
                });

        dialog.setNegativeButton(R.string.cancel, null);
        dialog.setCancelable(false);

        try {
            dialog.show();
        } catch (Exception ignored) {
        }
    }

    public void refresh(List<ProductInformation> products) {
        //TODO check isValid on these here

        if (!FileSystemUtils.isEmpty(products)) {
            Log.d(TAG,
                    "Refreshing product list and UI, count: "
                            + products.size());
            _appList = products;
        } else {
            Log.d(TAG, "Just refreshing UI");
        }

        //TODO remove some logging after resting
        Log.d(TAG, "_appList " + _appList.size());

        //first filter by repo
        List<ProductInformation> repoFiltered = new ArrayList<>();
        if (FileSystemUtils.isEmpty(_filterRepoTerm)
                ||
                FileSystemUtils.isEquals(_filterRepoTerm, _mapView.getContext()
                        .getString(R.string.app_mgmt_filter_All))) {
            repoFiltered.addAll(_appList);
        } else {
            Log.d(TAG, "Filter repo: " + _filterRepoTerm);
            for (ProductInformation app : _appList) {
                if (app.getParent() != null
                        && app.getParent().isRepoType(_filterRepoTerm)) {
                    repoFiltered.add(app);
                }
            }
        }

        //TODO remove some logging after resting
        Log.d(TAG, "Repo filtered " + repoFiltered.size());

        // Now filter by mode
        List<ProductInformation> modeFiltered = new ArrayList<>();
        if (_mode == null || _mode == Mode.All) {
            modeFiltered.addAll(repoFiltered);
        } else {
            Log.d(TAG, "Filter mode: " + _mode);
            for (ProductInformation app : repoFiltered) {
                boolean passes = appliesToMode(app, getStatus(app));
                if (passes)
                    modeFiltered.add(app);
            }
        }

        //TODO remove some logging after resting
        Log.d(TAG, "Mode filtered " + modeFiltered.size());

        //now filter by status
        List<ProductInformation> statusFiltered = new ArrayList<>();
        if (_filterTerm == null || _filterTerm == STATUS_FILTER.All) {
            statusFiltered.addAll(modeFiltered);
        } else {
            Log.d(TAG, "Filter terms: " + _filterTerm);
            for (ProductInformation app : modeFiltered) {
                ProductStatus status = getStatus(app);
                switch (_filterTerm) {
                    case Current:
                        if (status.overall == STATUS_COLOR.Current)
                            statusFiltered.add(app);
                        break;
                    case Loaded:
                        if (status.current == CURRENT_STATUS.Loaded)
                            statusFiltered.add(app);
                        break;
                    case NotLoaded:
                        if (status.current == CURRENT_STATUS.NotLoaded)
                            statusFiltered.add(app);
                        break;
                    case NotCompatible:
                        if (status.overall == STATUS_COLOR.NotCompatible)
                            statusFiltered.add(app);
                        break;
                    case NotInstalled:
                        if (!app.isInstalled())
                            statusFiltered.add(app);
                        break;
                    case UpdateAvailable:
                        if (status.overall == STATUS_COLOR.UpdateAvailable)
                            statusFiltered.add(app);
                        break;
                    case Installed:
                        if (app.isInstalled())
                            statusFiltered.add(app);
                        break;
                    default:
                    case All:
                        statusFiltered.add(app);
                        break;
                }
            }
        }

        //TODO remove some logging after resting
        Log.d(TAG, "Status filtered " + statusFiltered.size());

        // Filter by search terms
        final ArrayList<ProductInformation> searched = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(_searchTerms)) {
            Log.d(TAG, "Search terms: " + _searchTerms);
            for (ProductInformation app : statusFiltered) {
                if (app.search(_searchTerms))
                    searched.add(app);
            }
        } else {
            searched.addAll(statusFiltered);
        }

        //TODO remove some logging after resting
        Log.d(TAG, "searched " + searched.size());

        Collections.sort(searched, new ProductInformationComparator());
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                //sort by time
                _filteredAppList = searched;
                notifyDataSetChanged();
            }
        });
    }
}
