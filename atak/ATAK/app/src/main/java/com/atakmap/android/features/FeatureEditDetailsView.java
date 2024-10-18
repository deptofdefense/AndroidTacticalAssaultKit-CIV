
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
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore2;
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
import java.util.HashSet;
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

    // Edit requests that are passed into this class
    private final List<FeatureEditRequest> _requests = new ArrayList<>();

    // Update requests that are busy being executed
    private final Map<Integer, UpdateRequest> _updatesBusy = new HashMap<>();

    // Copied to update thread
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
     * @param db The database that will be used for getting/editing features.
     */
    public void setItems(long[] fsids, String title, FeatureDataStore2 db) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureSetFilter = new FeatureSetQueryParameters();
        params.featureSetFilter.ids = new HashSet<>();
        for (long fsid : fsids)
            params.featureSetFilter.ids.add(fsid);
        setRequests(title, Collections.singletonList(
                new FeatureEditRequest(db, params)));
    }

    /**
     * Uses the given feature set IDs to populate the view with the correct edit widgets and information.
     * @param fsids The feature set IDs that will be used to populate the view.
     * @param title The title that will be displayed on the view.
     * @param db The FeatureDataStore that will be used for getting/editing features.
     */
    public void setItems(long[] fsids, String title, FeatureDataStore db) {
        setItems(fsids, title, Adapters.adapt(db));
    }

    /**
     * Uses the given featureID to populate the view with the correct edit widgets and information.
     * @param fid The featureID that will be used to populate the view.
     * @param title The title that will be displayed on the view.
     * @param db The database that will be used for getting/editing features.
     */
    public void setItem(long fid, String title, FeatureDataStore2 db) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.ids = Collections.singleton(fid);
        setRequests(title, Collections.singletonList(
                new FeatureEditRequest(db, params)));
    }

    /**
     * Uses the given featureID to populate the view with the correct edit widgets and information.
     * @param fid The featureID that will be used to populate the view.
     * @param title The title that will be displayed on the view.
     * @param db The FeatureDataStore that will be used for getting/editing features.
     */
    public void setItem(long fid, String title, FeatureDataStore db) {
        setItem(fid, title, Adapters.adapt(db));
    }

    /**
     * Set feature edit requests for this view
     * @param title Title to display
     * @param requests List of requests
     */
    void setRequests(String title, List<FeatureEditRequest> requests) {
        _requests.clear();

        int visibleFlags = 0;
        for (FeatureEditRequest req : requests) {
            try (FeatureCursor cursor = req.queryFeatures()) {
                while (cursor.moveToNext())
                    visibleFlags |= _addStyleFromFeature(cursor.get());
            } catch (Exception e) {
                Log.e(TAG, "Failed to query features", e);
                return;
            }
        }

        _requests.addAll(requests);
        _setDefaultStyles();
        _init();
        _title.setText(title);
        _resetVisibleUI();
        _setVisibleElements(visibleFlags);
    }

    /**
     * Check if this view isn't ready to be setup
     * @return True if clear
     */
    public boolean isClear() {
        return _requests.isEmpty();
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

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final View v = inflater.inflate(R.layout.edit_feature_point_dialog,
                null);
        final ViewPager viewPager = v.findViewById(
                R.id.editFeaturePointDialogViewPager);

        // Buttons to navigate around the icon sets
        ImageButton prev = v.findViewById(R.id.nav_prev);
        ImageButton next = v.findViewById(R.id.nav_next);
        prev.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                navViewPager(viewPager, -1);
            }
        });
        next.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                navViewPager(viewPager, 1);
            }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.iconset);
        b.setView(v);
        _iconDialog = b.create();

        viewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return _userIconsetAdapters.size();
            }

            @Override
            public void setPrimaryItem(@NonNull ViewGroup container,
                    int position, @NonNull Object object) {
                super.setPrimaryItem(container, position, object);
                // Update the dialog's layout title
                TextView title = v.findViewById(
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
        viewPager.setCurrentItem(0);

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

    /**
     * Navigate to the page with the given offset
     * @param viewPager View pager
     * @param offset Page offset from current (will wrap around)
     */
    private void navViewPager(ViewPager viewPager, int offset) {
        PagerAdapter adapter = viewPager.getAdapter();
        int item = viewPager.getCurrentItem() + offset;
        int count = adapter != null ? adapter.getCount() : Integer.MAX_VALUE;
        if (item < 0)
            item = count - 1;
        else if (item >= count)
            item = 0;
        viewPager.setCurrentItem(item, true);
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
        StrokeWeightModifier mod = new StrokeWeightModifier(strokeWeight);
        _strokeStyle = (BasicStrokeStyle) mod.modifyStyle(_strokeStyle);
        requestUpdate(mod);
    }

    private void _updateOpacity(int percent) {
        OpacityModifier mod = new OpacityModifier(percent);
        _fillStyle = (BasicFillStyle) mod.modifyStyle(_fillStyle);
        requestUpdate(mod);
    }

    private void _updateColor(int color) {
        _setColorButtonColor(color);
        ColorModifier mod = new ColorModifier(color);
        _iconStyle = (IconPointStyle) mod.modifyStyle(_iconStyle);
        _strokeStyle = (BasicStrokeStyle) mod.modifyStyle(_strokeStyle);
        _fillStyle = (BasicFillStyle) mod.modifyStyle(_fillStyle);
        requestUpdate(mod);
    }

    private void _updateIcon(String iconImageUri) {
        IconModifier mod = new IconModifier(iconImageUri);
        _iconStyle = (IconPointStyle) mod.modifyStyle(_iconStyle);
        requestUpdate(mod);
    }

    private void requestUpdate(final StyleModifier styleMod) {
        final UpdateRequest req = new UpdateRequest(
                new ArrayList<>(_requests), styleMod);

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

        // Update the composite style for good measure
        _compositeStyle = new CompositeStyle(new Style[] {
                _strokeStyle, _fillStyle
        });
    }

    /* Feature update thread */

    private final ExecutorService updateThreads = Executors
            .newFixedThreadPool(8, new NamedThreadFactory(
                    TAG + "WorkerThread"));

    private static class UpdateRequest implements Runnable {

        private final List<FeatureEditRequest> _requests;
        private final StyleModifier _styleMod;
        private boolean _canceled;

        UpdateRequest(@NonNull List<FeatureEditRequest> requests,
                @NonNull StyleModifier styleMod) {
            _requests = requests;
            _styleMod = styleMod;
        }

        public void cancel() {
            _canceled = true;
        }

        @Override
        public void run() {
            // TODO: Support for bulk updateFeatures - see ATAK-15042
            //  For large feature sets containing around 1000 items this is a very
            //  slow loop that can take 5-10 minutes to finish.
            for (FeatureEditRequest req : _requests) {
                try (FeatureCursor cursor = req.queryFeatures()) {

                    // Loop through features
                    while (cursor.moveToNext()) {

                        // Check if request cancelled
                        if (_canceled)
                            return;

                        // Update style for this feature
                        updateStyle(req.database, cursor);
                    }

                    // Sleep for 15ms to leave the database lock open for a bit
                    // Otherwise UI lockup can occur when querying this database elsewhere
                    Thread.sleep(15);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update style", e);
                }
            }
        }

        private void updateStyle(FeatureDataStore2 db, FeatureCursor cursor)
                throws DataStoreException {
            Feature feature = cursor.get();
            Style style = feature.getStyle();
            Style newStyle = _styleMod.modifyStyle(style);
            if (newStyle != null && newStyle != style)
                db.updateFeature(feature.getId(),
                        FeatureDataStore2.PROPERTY_FEATURE_STYLE, null,
                        null, newStyle, null, 0);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            UpdateRequest that = (UpdateRequest) o;
            return Objects.equals(_requests, that._requests);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_requests);
        }
    }

    /**
     * Interface for taking an existing style and modifying it
     */
    private abstract static class StyleModifier {

        /**
         * Modify a feature's style
         * @param style Input style
         * @return Modified style or null/same as input if not modified
         */
        public Style modifyStyle(Style style) {
            // By default composite styles are broken down into their individual
            // parts and looped back into this modifier
            if (style instanceof CompositeStyle) {
                CompositeStyle cs = (CompositeStyle) style;
                return modifyCompositeStyle(cs, this);
            }
            return style;
        }
    }

    /**
     * Used for stroke weight updates
     */
    private static class StrokeWeightModifier extends StyleModifier {

        private final float _strokeWeight;

        StrokeWeightModifier(float strokeWeight) {
            _strokeWeight = strokeWeight;
        }

        @Override
        public Style modifyStyle(Style style) {
            if (style instanceof BasicStrokeStyle) {
                BasicStrokeStyle bss = (BasicStrokeStyle) style;
                return new BasicStrokeStyle(bss.getColor(), _strokeWeight);
            }
            return super.modifyStyle(style);
        }
    }

    /**
     * Used for fill opacity updates
     */
    private static class OpacityModifier extends StyleModifier {

        private final int _opacity;

        OpacityModifier(int opacity) {
            _opacity = opacity;
        }

        @Override
        public Style modifyStyle(Style style) {
            if (style instanceof BasicFillStyle) {
                BasicFillStyle bfs = (BasicFillStyle) style;
                int color = (bfs.getColor() & 0xFFFFFF) | (_opacity << 24);
                return new BasicFillStyle(color);
            }
            return super.modifyStyle(style);
        }
    }

    /**
     * Used for general color updates
     */
    private static class ColorModifier extends StyleModifier {

        private final int _color;

        ColorModifier(int color) {
            _color = color;
        }

        @Override
        public Style modifyStyle(Style style) {
            if (style instanceof IconPointStyle) {
                IconPointStyle ips = (IconPointStyle) style;
                return new IconPointStyle(_color, ips.getIconUri());
            } else if (style instanceof BasicStrokeStyle) {
                BasicStrokeStyle bss = (BasicStrokeStyle) style;
                return new BasicStrokeStyle(_color, bss.getStrokeWidth());
            } else if (style instanceof BasicFillStyle) {
                BasicFillStyle bfs = (BasicFillStyle) style;
                int fillColor = (_color & 0xFFFFFF)
                        | (bfs.getColor() & 0xFF000000);
                return new BasicFillStyle(fillColor);
            }
            return super.modifyStyle(style);
        }
    }

    /**
     * Used for icon URI updates
     */
    private static class IconModifier extends StyleModifier {

        private final String _iconUri;

        IconModifier(String iconUri) {
            _iconUri = iconUri;
        }

        @Override
        public Style modifyStyle(Style style) {
            if (style instanceof IconPointStyle) {
                IconPointStyle ips = (IconPointStyle) style;
                return new IconPointStyle(ips.getColor(), _iconUri);
            }
            return super.modifyStyle(style);
        }
    }

    /**
     * Apply a {@link StyleModifier} to a composite style's components
     * @param cs Composite style to modify
     * @param modifier Style modifier
     * @return Modified composite style or the same as input if not modified
     */
    private static CompositeStyle modifyCompositeStyle(
            CompositeStyle cs, StyleModifier modifier) {
        List<Style> styleList = new ArrayList<>();
        boolean modified = false;
        for (int i = 0; i < cs.getNumStyles(); i++) {
            Style s = cs.getStyle(i);
            Style mod = modifier.modifyStyle(s);
            if (mod != null && mod != s) {
                s = mod;
                modified = true;
            }
            styleList.add(s);
        }
        return modified ? new CompositeStyle(styleList.toArray(
                new Style[0])) : cs;
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

        private final AdapterView.OnItemClickListener _onClickListener = new AdapterView.OnItemClickListener() {
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
