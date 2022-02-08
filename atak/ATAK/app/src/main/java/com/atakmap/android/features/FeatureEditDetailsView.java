
package com.atakmap.android.features;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconDatabase;
import com.atakmap.android.icons.UserIconSet;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.SqliteMapDataRef;
import com.atakmap.android.user.ExpandableGridView;
import com.atakmap.android.user.icon.IconsetAdapterBase;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The drop-down view used to edit feature styles
 */
public class FeatureEditDetailsView extends LinearLayout {

    private final static String TAG = "FeatureEditDetailsView";

    private final static int SHOW_IMAGE = 1 << 0;
    private final static int SHOW_STROKE_WIDTH = 1 << 1;
    private final static int SHOW_OPACITY = 1 << 2;

    private final Context _context;

    private ImageButton _imageButton;
    private ImageButton _colorButton;
    private SeekBar _lineThicknessSeekBar;
    private SeekBar _opacitySeekBar;
    private TextView _title;

    private TableRow _imageButtonTableRow;
    private LinearLayout _lineThicknessLayout;

    private AlertDialog _iconDialog;

    private ArrayList<IconsetAdapterBase> _userIconsetAdapters;

    private boolean _initialized = false;

    // Update requests that are busy being executed
    private final Map<Integer, UpdateRequest> _updatesBusy = new HashMap<>();

    // Copied to update thread
    private long _fid = -1;
    private long[] _fsids = null;
    private FeatureDataStore _db;
    private IconPointStyle _iconStyle;
    private BasicStrokeStyle _strokeStyle;
    private BasicFillStyle _fillStyle;
    private CompositeStyle _compositeStyle;

    public FeatureEditDetailsView(Context context) {
        super(context);
        _context = context;
    }

    public FeatureEditDetailsView(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
        _context = context;
    }

    public FeatureEditDetailsView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        _context = context;
    }

    public FeatureEditDetailsView(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        _context = context;
    }

    /**
     * Uses the given feature set IDs to populate the view with the correct edit widgets and information.
     * @param fsids The feature set IDs that will be used to populate the view.
     * @param title The title that will be displayed on the view.
     * @param db The FeatureDataStore that will be used for getting/editing features.
     */
    public void setItems(long[] fsids, String title, FeatureDataStore db) {
        _clearMapItems();
        _fsids = fsids;
        _db = db;
        int visibleFlags = 0;
        try (FeatureCursor cursor = _db.queryFeatures(getQueryParameters())) {
            while (cursor.moveToNext()) {
                visibleFlags |= _addStyleFromFeature(cursor.get());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query features");
            }
        _setDefaultStyles();
        _init();
        _title.setText(title);
        _resetVisibleUI();
        _setVisibleElements(visibleFlags);
    }

    /**
     * Uses the given featureID to populate the view with the correct edit widgets and information.
     * @param fid The featureID that will be used to populate the view.
     * @param title The title that will be displayed on the view.
     * @param db The FeatureDataStore that will be used for getting/editing features.
     */
    public void setItem(long fid, String title, FeatureDataStore db) {
        _clearMapItems();
        _db = db;
        _fid = fid;
        Feature feature = _db.getFeature(_fid);
        if (feature == null) {
            // TODO: Exit cleanly in this scenario
            return;
        }

        _fsids = new long[] {
                feature.getFeatureSetId()
        };
        int visibleFlags = _addStyleFromFeature(feature);
        _setDefaultStyles();
        _init();

        _title.setText(title);

        _resetVisibleUI();
        _setVisibleElements(visibleFlags);
    }

    /**
     * @deprecated Use {@link #setItems(long[], String, FeatureDataStore)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setItems(String[] fsids, String title, FeatureDataStore db) {
        long[] ids = new long[fsids.length];
        for (int i = 0; i < ids.length; i++)
            ids[i] = Long.parseLong(fsids[i]);
        setItems(ids, title, db);
    }

    private int _addStyleFromFeature(Feature feature) {
        int visibleFlags = 0;
        List<Style> styles;
        if (feature.getStyle() instanceof CompositeStyle) {
            styles = new ArrayList<>();
            CompositeStyle compositeStyle = (CompositeStyle) feature.getStyle();
            _compositeStyle = compositeStyle;
            for (int i = 0; i < compositeStyle.getNumStyles(); i++) {
                styles.add(compositeStyle.getStyle(i));
            }
        } else {
            styles = Collections.singletonList(feature.getStyle());
        }
        for (Style style : styles) {
            if (style instanceof IconPointStyle) {
                visibleFlags |= SHOW_IMAGE;
                _iconStyle = (IconPointStyle) style;
            } else if (style instanceof BasicStrokeStyle) {
                visibleFlags |= SHOW_STROKE_WIDTH;
                _strokeStyle = (BasicStrokeStyle) style;
            } else if (style instanceof BasicFillStyle) {
                visibleFlags |= SHOW_OPACITY;
                _fillStyle = (BasicFillStyle) style;
            }
        }
        return visibleFlags;
    }

    private void _setDefaultStyles() {
        if (_iconStyle == null) {
            _iconStyle = new IconPointStyle(Color.WHITE, "");
        }
        if (_strokeStyle == null) {
            _strokeStyle = new BasicStrokeStyle(Color.WHITE, 1);
        }
        if (_fillStyle == null) { 
            _fillStyle = new BasicFillStyle(Color.WHITE);
        }
        if (_compositeStyle == null) {
            _compositeStyle = new CompositeStyle(new Style[] {
                    _strokeStyle, _fillStyle
            });
        }
    }

    private FeatureQueryParameters getQueryParameters() {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureSetIds = new ArrayList<>();
        for (long fsid : _fsids)
            params.featureSetIds.add(fsid);
        if (_fid != -1)
            params.featureIds = Collections.singleton(_fid);
        return params;
        }

    private void _clearMapItems() {
        _fid = -1;
        _fsids = null;
    }

    private void _init() {
        if (_initialized) {
            return;
        }
        _initialized = true;

        _title = findViewById(R.id.editFeatureViewTitle);

        _imageButton = findViewById(R.id.editFeatureViewImageButton);
        _colorButton = findViewById(R.id.editFeatureViewColorButton);
        _lineThicknessSeekBar = findViewById(
                R.id.editFeatureViewLineThicknessSeek);
        _opacitySeekBar = findViewById(R.id.editFeatureViewOpacitySeek);

        _imageButtonTableRow = findViewById(
                R.id.editFeatureViewImageButtonLayout);
        _lineThicknessLayout = findViewById(
                R.id.editFeatureViewLineThicknessLayout);

        _userIconsetAdapters = new ArrayList<>();

        _userIconsetAdapters.add(new ResourceIconAdapter(_context,
                _context.getString(R.string.civ_cot2525C), Arrays.asList(
                        R.drawable.unknown,
                        R.drawable.neutral,
                        R.drawable.hostile,
                        R.drawable.friendly)));

        _userIconsetAdapters.add(new ResourceIconAdapter(_context,
                _context.getString(R.string.mission), Arrays.asList(
                        R.drawable.green_flag,
                        R.drawable.sensor,
                        R.drawable.ic_menu_binos)));

        _userIconsetAdapters.add(new ResourceIconAdapter(_context,
                _context.getString(R.string.spot_map), Arrays.asList(
                        R.drawable.enter_location_spot_white,
                        R.drawable.enter_location_spot_yellow,
                        R.drawable.enter_location_spot_orange,
                        R.drawable.enter_location_spot_brown,
                        R.drawable.enter_location_spot_red,
                        R.drawable.enter_location_spot_magenta,
                        R.drawable.enter_location_spot_blue,
                        R.drawable.enter_location_spot_cyan,
                        R.drawable.enter_location_spot_green,
                        R.drawable.enter_location_spot_grey,
                        R.drawable.enter_location_spot_black)));

        List<UserIconSet> iconsets = UserIconDatabase.instance(
                MapView.getMapView().getContext()).getIconSets(true, false);
        if (!FileSystemUtils.isEmpty(iconsets)) {
            for (UserIconSet iconset : iconsets) {
                if (iconset != null && iconset.isValid()) {
                    IconsetAdapter iconsetAdapter = new IconsetAdapter(_context,
                            iconset.getName(), new ArrayList<>());
                    iconsetAdapter.setIcons(iconset.getIcons());
                    _userIconsetAdapters.add(iconsetAdapter);
                }
            }
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        View alertView = inflater.inflate(R.layout.edit_feature_point_dialog,
                null);
        b.setTitle(R.string.iconset);
        _iconDialog = b.create();
        _iconDialog.setContentView(R.layout.edit_feature_point_dialog);
        _iconDialog.setView(alertView);
        _iconDialog.create();
        ViewPager alertViewPager = _iconDialog
                .findViewById(R.id.editFeaturePointDialogViewPager);

        alertViewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return _userIconsetAdapters.size();
            }

            @Override
            public void setPrimaryItem(@NonNull ViewGroup container,
                    int position, @NonNull Object object) {
                super.setPrimaryItem(container, position, object);
                // Update the dialog's layout title
                TextView title = alertView.findViewById(
                        R.id.editFeaturePointDialogCurrentIconsetName);
                title.setText(_userIconsetAdapters.get(position).getName());
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container,
                    int position) {

                View view = inflater.inflate(
                        R.layout.edit_feature_point_dialog_viewpager_layout,
                        container, false);
                container.addView(view);
                ExpandableGridView gridView = view
                        .findViewById(R.id.editFeaturePointDialogIconGrid);
                gridView.setAdapter(_userIconsetAdapters.get(position));
                gridView.setExpanded(true);

                gridView.setOnItemClickListener(_userIconsetAdapters
                        .get(position).getOnItemClickListener());
                return view;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position,
                    @NonNull Object object) {
                container.removeView((View) object);
            }

            @Override
            public boolean isViewFromObject(@NonNull View view,
                    @NonNull Object object) {
                return view == object;
            }
        });
        alertViewPager.setCurrentItem(0);

        _lineThicknessSeekBar
                .setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        if (!fromUser) {
                            return;
                        }
                        _updateThickness(progress);
                    }
                });

        _opacitySeekBar
                .setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        if (!fromUser) {
                            return;
                        }
                        _updateOpacity(progress);
                    }
                });

        _colorButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder b = new AlertDialog.Builder(getContext());
                b.setTitle(R.string.select_a_color);
                ColorPalette palette = new ColorPalette(getContext(),
                        _getColor(), true);
                b.setView(palette);
                final AlertDialog d = b.show();
                ColorPalette.OnColorSelectedListener l = new ColorPalette.OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int color, String label) {
                        d.dismiss();
                        _updateColor(color);
                    }
                };
                palette.setOnColorSelectedListener(l);
            }
        });
        _imageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                _iconDialog.show();
            }
        });
    }

    private int _getColor() {
        if (_imageButtonTableRow.getVisibility() == VISIBLE) {
            return _iconStyle.getColor();
        } else if (_opacitySeekBar.getVisibility() == VISIBLE) {
            return _fillStyle.getColor();
        } else if (_lineThicknessLayout.getVisibility() == VISIBLE) {
            return _strokeStyle.getColor();
        }
        return 0;
    }

    private void _setColorButtonColor(int color) {
        _colorButton.setColorFilter((color & 0xFFFFFF) | 0xFF000000,
                PorterDuff.Mode.MULTIPLY);
    }

    private void _resetVisibleUI() {
        _imageButtonTableRow.setVisibility(GONE);
        _lineThicknessLayout.setVisibility(GONE);
        _opacitySeekBar.setVisibility(GONE);
    }

    private void _setVisibleElements(int visibleFlags) {
        if (MathUtils.hasBits(visibleFlags, SHOW_IMAGE)) {
            _imageButtonTableRow.setVisibility(VISIBLE);
            _setColorButtonColor(_iconStyle.getColor());
            _imageButton.setImageDrawable(new BitmapDrawable(null,
                    ATAKUtilities.getUriBitmap(_iconStyle.getIconUri())));
            _imageButton.refreshDrawableState();
        }
        if (MathUtils.hasBits(visibleFlags, SHOW_STROKE_WIDTH)) {
            _lineThicknessLayout.setVisibility(VISIBLE);
            _setColorButtonColor(_strokeStyle.getColor());
            _lineThicknessSeekBar.setProgress(
                    (int) ((_strokeStyle.getStrokeWidth() - 1.0) * 10.0));
        }
        if (MathUtils.hasBits(visibleFlags, SHOW_OPACITY)) {
            _opacitySeekBar.setVisibility(VISIBLE);
            _setColorButtonColor(_fillStyle.getColor());
            _opacitySeekBar.setProgress(Color.alpha(_fillStyle.getColor()));
        }
    }

    private void _updateThickness(int percent) {
        float strokeWeight = (percent / 10f) + 1;
        _strokeStyle = new BasicStrokeStyle(_strokeStyle.getColor(),
                strokeWeight);
        _compositeStyle = new CompositeStyle(new Style[] {
                _strokeStyle, _fillStyle
        });
        requestUpdate();
    }

    private void _updateOpacity(int percent) {
        int color = (_fillStyle.getColor() & 0x00FFFFFF) | (percent << 24);
        _fillStyle = new BasicFillStyle(color);
        _compositeStyle = new CompositeStyle(new Style[] {
                _strokeStyle, _fillStyle
        });
        requestUpdate();
    }

    private void _updateColor(int color) {
        _setColorButtonColor(color);
        _iconStyle = new IconPointStyle(color, _iconStyle.getIconUri());
        _strokeStyle = new BasicStrokeStyle(color,
                _strokeStyle.getStrokeWidth());

        int fillColor;
        if (_fillStyle != null)
            fillColor = (color & 0xFFFFFF)
                | (_fillStyle.getColor() & 0xFF000000);
        else
            fillColor = (color & 0xFFFFFF);

        if (_opacitySeekBar.getVisibility() == VISIBLE)
            _opacitySeekBar.setProgress(Color.alpha(fillColor));
        _fillStyle = new BasicFillStyle(fillColor);
        _compositeStyle = new CompositeStyle(new Style[] {
                _strokeStyle, _fillStyle
        });
        requestUpdate();
    }

    private void _updateIcon(String iconImageUri) {
        _iconStyle = new IconPointStyle(_iconStyle.getColor(), iconImageUri);
        requestUpdate();
    }

    private void requestUpdate() {
        final UpdateRequest req = new UpdateRequest(_db, getQueryParameters(),
                _compositeStyle, _fillStyle, _strokeStyle, _iconStyle);

        // Add update request and cancel existing
        synchronized (_updatesBusy) {
            int code = req.hashCode();
            UpdateRequest existing = _updatesBusy.get(code);
            if (existing != null)
                existing.cancel();
            _updatesBusy.put(code, req);
        }

        // Send update request to update pool
        updateThreads.execute(new Runnable() {
            @Override
            public void run() {
                req.run();
                synchronized (_updatesBusy) {
                    int code = req.hashCode();
                    UpdateRequest existing = _updatesBusy.get(code);
                    if (existing == req)
                        _updatesBusy.remove(code);
                }
            }
        });
    }

    /* Feature update thread */

    private final ExecutorService updateThreads = Executors
            .newFixedThreadPool(8, new NamedThreadFactory(
                    TAG + "WorkerThread"));

    private static class UpdateRequest implements Runnable {

        private final FeatureDataStore _db;
        private final FeatureQueryParameters _params;
        private final Style[] _styles;
        private boolean _canceled;

        UpdateRequest(FeatureDataStore db, FeatureQueryParameters params,
                Style... styles) {
            _db = db;
            _params = params;
            _styles = styles;
        }

        public void cancel() {
            _canceled = true;
        }

        @Override
        public void run() {
            // TODO: Support for bulk updateFeatures - see ATAK-15042
            //  For large feature sets containing around 1000 items this is a very
            //  slow loop that can take 5-10 minutes to finish.
            try (FeatureCursor cursor = _db.queryFeatures(_params)) {
            while (cursor.moveToNext()) {
                    if (_canceled)
                        return;

                Feature feature = cursor.get();
                Style style = feature.getStyle();

                    // Update applicable style
                    for (Style s : _styles) {
                        if (s != null && s.getClass().isInstance(style)) {
                            _db.updateFeature(feature.getId(), s);
                            break;
                }
            }

                    // Sleep for 15ms to leave the database lock open for a bit
                    // Otherwise UI lockup can occur when querying this database elsewhere
                    Thread.sleep(15);
                }
        } catch (Exception e) {
                Log.d(TAG, "Failed to update style", e);
        } finally {
            _db.refresh();
        }
    }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            UpdateRequest that = (UpdateRequest) o;
            return Objects.equals(_db.getUri(), that._db.getUri())
                    && Objects.equals(_params.featureSetIds,
                            that._params.featureSetIds)
                    && Objects.equals(_params.featureIds,
                            that._params.featureIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_db.getUri(), _params.featureSetIds,
                    _params.featureIds);
        }
    }

    /* Icon adapters */

    private class IconsetAdapter extends IconsetAdapterBase {
        private final AdapterView.OnItemClickListener _onClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                String iconQuery = UserIcon.GetIconBitmapQueryFromIconsetPath(
                        ((UserIcon) getItem(position)).getIconsetPath(),
                        _context);
                String iconUri = new SqliteMapDataRef(UserIconDatabase
                        .instance(_context).getDatabaseName(), iconQuery)
                                .toUri();
                _imageButton.setImageDrawable(new BitmapDrawable(null,
                        ATAKUtilities.getUriBitmap(iconUri)));
                _iconDialog.dismiss();
                _updateIcon(iconUri);
            }
        };
        private final String _name;

        public IconsetAdapter(Context c, String name, List<UserIcon> icons) {
            super(c, icons);
            _name = name;
        }

        @Override
        public void setIcons(List<UserIcon> icons) {
            mGroupIcons = icons;
            notifyDataSetChanged();
        }

        @Override
        public AdapterView.OnItemClickListener getOnItemClickListener() {
            return _onClickListener;
        }

        @Override
        public String getName() {
            return _name;
        }
    }

    private class ResourceIconAdapter extends IconsetAdapterBase {
        private final ArrayList<ImageView> _icons;
        private final ArrayList<Integer> _resources;
        private final String _name;

        private AdapterView.OnItemClickListener _onClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                String uri = ATAKUtilities.getResourceUri(mContext,
                        _resources.get(position));
                _imageButton.setImageDrawable(
                        _context.getDrawable(_resources.get(position)));
                _iconDialog.dismiss();
                _updateIcon(uri);
            }
        };

        public ResourceIconAdapter(Context context, String name,
                List<Integer> resourceIDs) {
            super(context, null);
            _icons = new ArrayList<>();
            _resources = new ArrayList<>();
            _name = name;
            addIcons(resourceIDs);
        }

        @Override
        public void setIcons(List<UserIcon> icons) {

        }

        public void addIcons(List<Integer> resourceIDs) {
            _resources.addAll(resourceIDs);
            for (int id : resourceIDs) {
                ImageView imageView = new ImageView(mContext);
                imageView.setImageDrawable(mContext.getDrawable(id));
                _icons.add(imageView);
            }
        }

        @Override
        public int getCount() {
            return _icons.size();
        }

        @Override
        public Object getItem(int position) {
            return _icons.get(position);
        }

        @Override
        public long getItemId(int position) {
            return _icons.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return _icons.get(position);
        }

        @Override
        public AdapterView.OnItemClickListener getOnItemClickListener() {
            return _onClickListener;
        }

        @Override
        public String getName() {
            return _name;
        }
    }
}
