
package com.atakmap.android.attachment;

import android.content.Intent;
import android.os.SystemClock;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.image.GalleryFileItem;
import com.atakmap.android.image.GalleryItem;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.attachment.export.AttachmentExportWrapper;
import com.atakmap.android.cotdetails.CoTInfoMapComponent;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.image.ImageGalleryFileAdapter;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.image.quickpic.QuickPicReceiver;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AttachmentMapOverlay extends AbstractMapOverlay2 {

    private final static String TAG = "AttachmentMapOverlay";

    private final static Set<String> SEARCH_FIELDS = new HashSet<>();
    private static final int ORDER = 3;

    static {
        SEARCH_FIELDS.add("callsign");
        SEARCH_FIELDS.add("title");
        SEARCH_FIELDS.add("shapeName");
    }

    /**
     * Simple wrapper to store a map item and a list of attachments
     */
    public static class MapItemAttachment implements Exportable {
        private final MapItem mi;
        private final File attachment;

        public MapItemAttachment(MapItem mi, File attachment) {
            this.mi = mi;
            this.attachment = attachment;
        }

        public File getAttachment() {
            return attachment;
        }

        public MapItem getMapItem() {
            return mi;
        }

        public boolean isValid() {
            return mi != null && attachment != null;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(LocaleUtil.getCurrent(), "%s has %s",
                    mi.getUID(),
                    attachment.getAbsolutePath());
        }

        @Override
        public boolean isSupported(Class<?> target) {
            return AttachmentExportWrapper.class.equals(target) ||
                    MissionPackageExportWrapper.class.equals(target) ||
                    Folder.class.equals(target) ||
                    KMZFolder.class.equals(target) ||
                    GPXExportWrapper.class.equals(target) ||
                    OGRFeatureExportWrapper.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class<?> target, ExportFilters filters) {
            if (!isValid())
                return null;

            //apply filters to the map item e.g. bbox
            if (filters != null && filters.filter(this.getMapItem()))
                return null;

            if (AttachmentExportWrapper.class.equals(target)) {
                return new AttachmentExportWrapper(this);
            } else if (MissionPackageExportWrapper.class.equals(target)) {
                MissionPackageExportWrapper w = new MissionPackageExportWrapper();
                //Do not include attachment here, as by default the attacments are automatically
                //included
                //w.getFilepaths().add(this.getAttachment().getAbsolutePath());
                w.getUIDs().add(this.getMapItem().getUID());
                return w;
            } else if (Folder.class.equals(target)) {
                return toKml();
            } else if (KMZFolder.class.equals(target)) {
                return toKmz();
            } else if (GPXExportWrapper.class.equals(target)) {
                return Marker.toGPX((PointMapItem) this.getMapItem());
            } else if (OGRFeatureExportWrapper.class.equals(target)) {
                return Marker.toOgrGeomtry((PointMapItem) this.getMapItem());
            }

            return null;
        }

        protected Folder toKml() {
            try {
                // style element
                Style style = new Style();
                IconStyle istyle = new IconStyle();
                style.setIconStyle(istyle);

                //set white pushpin and Google Earth will tint based on color above
                com.ekito.simpleKML.model.Icon icon = new com.ekito.simpleKML.model.Icon();
                String whtpushpin = MapView.getMapView().getContext()
                        .getString(R.string.whtpushpin);
                icon.setHref(whtpushpin);
                istyle.setIcon(icon);

                String styleId = KMLUtil.hash(style);
                style.setId(styleId);

                // Folder element containing styles, shape and label
                Folder folder = new Folder();
                if (mi.getGroup() != null
                        && !FileSystemUtils.isEmpty(mi.getGroup()
                                .getFriendlyName()))
                    folder.setName(mi.getGroup().getFriendlyName());
                else
                    folder.setName(ATAKUtilities.getDisplayName(mi));
                List<StyleSelector> styles = new ArrayList<>();
                styles.add(style);
                folder.setStyleSelector(styles);
                List<Feature> folderFeatures = new ArrayList<>();
                folder.setFeatureList(folderFeatures);

                // Placemark element
                String uid = mi.getUID();
                Placemark pointPlacemark = new Placemark();
                pointPlacemark.setId(uid);
                pointPlacemark.setName(ATAKUtilities
                        .getDisplayName(mi));
                pointPlacemark.setStyleUrl("#" + styleId);
                pointPlacemark.setVisibility(mi.getVisible() ? 1 : 0);

                Coordinate coord = null;
                String altitudeMode = "absolute";
                if (mi instanceof PointMapItem) {
                    PointMapItem pmi = (PointMapItem) mi;
                    coord = KMLUtil.convertKmlCoord(pmi.getGeoPointMetaData(),
                            false);
                    altitudeMode = KMLUtil
                            .convertAltitudeMode(pmi.getAltitudeMode());
                }
                if (coord == null) {
                    Log.w(TAG, "No marker location set");
                    return null;
                }

                Point centerPoint = new Point();
                centerPoint.setCoordinates(coord);
                centerPoint.setAltitudeMode(altitudeMode);

                List<Geometry> pointGeomtries = new ArrayList<>();
                pointGeomtries.add(centerPoint);
                pointPlacemark.setGeometryList(pointGeomtries);
                folderFeatures.add(pointPlacemark);

                //set an HTML description (e.g. for the Google Earth balloon)
                String desc = Marker.getKMLDescription(
                        (PointMapItem) this.getMapItem(),
                        ATAKUtilities.getDisplayName(mi), null);
                if (!FileSystemUtils.isEmpty(desc)) {
                    pointPlacemark.setDescription(desc);
                }

                return folder;
            } catch (Exception e) {
                Log.e(TAG, "Export of Marker to KML failed with Exception", e);
            }

            return null;
        }

        protected KMZFolder toKmz() {
            try {
                // Folder element containing styles, shape and label
                KMZFolder folder = new KMZFolder();
                if (mi.getGroup() != null
                        && !FileSystemUtils.isEmpty(mi.getGroup()
                                .getFriendlyName()))
                    folder.setName(mi.getGroup().getFriendlyName());
                else
                    folder.setName(ATAKUtilities.getDisplayName(mi));

                // style element
                Style style = new Style();
                IconStyle istyle = new IconStyle();
                Icon micon = null;
                if (mi instanceof Marker) {
                    micon = ((Marker) mi).getIcon();
                }
                if (micon != null) {
                    style.setIconStyle(istyle);

                    //set white pushpin and Google Earth will tint based on color above
                    com.ekito.simpleKML.model.Icon icon = new com.ekito.simpleKML.model.Icon();
                    icon.setHref(
                            "http://maps.google.com/mapfiles/kml/pushpin/wht-pushpin.png");
                    istyle.setIcon(icon);

                    //see if we can set the actual icon for this marker
                    String type = mi.getType();
                    String imageUri = micon.getImageUri(Icon.STATE_DEFAULT);

                    if (!FileSystemUtils.isEmpty(imageUri)) {
                        String kmzIconPath = null;
                        if (imageUri.startsWith("sqlite")) {
                            //query sqlite to get iconset UID and icon filename
                            UserIcon userIcon = UserIcon.GetIcon(imageUri,
                                    false,
                                    MapView.getMapView().getContext());
                            if (userIcon != null && userIcon.isValid()) {
                                kmzIconPath = "icons"
                                        + File.separatorChar
                                        + userIcon
                                                .getIconsetUid()
                                        + "_" + userIcon.getFileName();
                            }
                        } else {
                            File f = new File(imageUri);
                            kmzIconPath = "icons" + File.separatorChar
                                    + type + "_" + f.getName();
                        }

                        if (!FileSystemUtils.isEmpty(kmzIconPath)) {
                            icon.setHref(kmzIconPath);
                        }

                        Pair<String, String> pair = new Pair<>(
                                imageUri, kmzIconPath);
                        if (!folder.getFiles().contains(pair)) {
                            folder.getFiles().add(pair);
                        }
                    }
                }

                String styleId = KMLUtil.hash(style);
                style.setId(styleId);

                // Placemark element
                String uid = mi.getUID();
                Placemark pointPlacemark = new Placemark();
                pointPlacemark.setId(uid);
                pointPlacemark.setName(ATAKUtilities
                        .getDisplayName(mi));
                pointPlacemark.setStyleUrl("#" + styleId);
                pointPlacemark.setVisibility(mi.getVisible() ? 1 : 0);

                Coordinate coord = null;
                String altitudeMode = "absolute";
                if (mi instanceof PointMapItem) {
                    PointMapItem pmi = (PointMapItem) mi;
                    coord = KMLUtil.convertKmlCoord(pmi.getGeoPointMetaData(),
                            false);
                    altitudeMode = KMLUtil
                            .convertAltitudeMode(pmi.getAltitudeMode());
                }
                if (coord == null) {
                    Log.w(TAG, "No marker location set");
                    return null;
                }

                Point centerPoint = new Point();
                centerPoint.setCoordinates(coord);
                centerPoint.setAltitudeMode(altitudeMode);

                List<Geometry> geometryList = new ArrayList<>();
                geometryList.add(centerPoint);
                pointPlacemark.setGeometryList(geometryList);

                List<StyleSelector> styles = new ArrayList<>();
                styles.add(style);
                folder.setStyleSelector(styles);
                List<Feature> folderFeatures = new ArrayList<>();
                folder.setFeatureList(folderFeatures);
                folderFeatures.add(pointPlacemark);

                //now gather attachments
                List<File> attachments = AttachmentManager.getAttachments(uid);
                if (attachments.size() > 0) {
                    for (File file : attachments) {
                        if (ImageDropDownReceiver.ImageFileFilter.accept(
                                file.getParentFile(), file.getName())) {
                            String kmzAttachmentsPath = "attachments"
                                    + File.separatorChar
                                    + uid + File.separatorChar + file.getName();

                            Pair<String, String> pair = new Pair<>(
                                    file.getAbsolutePath(), kmzAttachmentsPath);
                            if (!folder.getFiles().contains(pair))
                                folder.getFiles().add(pair);
                        }
                    } //end attachment loop
                }

                //set an HTML description (e.g. for the Google Earth balloon)
                String desc = Marker
                        .getKMLDescription((PointMapItem) this.getMapItem(),
                                ATAKUtilities.getDisplayName(mi),
                                attachments.toArray(new File[0]));
                if (!FileSystemUtils.isEmpty(desc)) {
                    pointPlacemark.setDescription(desc);
                }

                return folder;

            } catch (Exception e) {
                Log.e(TAG, "Export of Marker to KML failed with Exception", e);
            }

            return null;
        }

        public static MissionPackageExportWrapper toMissionPackage(
                MapItem item) {
            String uid = item.getUID();
            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Skipping null Mission Package item");
                return null;
            }

            return new MissionPackageExportWrapper(true, uid);
        }
    }

    private final MapView _view;

    public AttachmentMapOverlay(MapView view) {
        this._view = view;
    }

    @Override
    public String getIdentifier() {
        return AttachmentMapOverlay.class.getName();
    }

    @Override
    public String getName() {
        return _view.getContext().getString(R.string.media);
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter prefFilter) {

        return new AttachmentOverlayListModel(adapter, prefFilter);
    }

    public class AttachmentOverlayListModel extends AbstractHierarchyListItem2
            implements Search, Export, Delete, Visibility2,
            CoTInfoMapComponent.AttachmentEventListener,
            View.OnClickListener, View.OnLongClickListener {

        private final static String TAG = "AttachmentOverlayListModel";

        private final String path;
        private boolean vizSupported = false;

        AttachmentOverlayListModel(BaseAdapter listener,
                HierarchyListFilter filter) {
            this.path = "\\" + getUID();
            this.asyncRefresh = true;
            refresh(listener, filter);
            CoTInfoMapComponent.getInstance().addAttachmentListener(this);
        }

        @Override
        public String getTitle() {
            return AttachmentMapOverlay.this.getName();
        }

        @Override
        public String getIconUri() {
            return "asset://icons/camera.png";
        }

        @Override
        public int getPreferredListIndex() {
            return ORDER;
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public HierarchyListItem getChildAt(int index) {
            List<HierarchyListItem> children = getChildren();
            if (index < 0 || index >= children.size()) {
                Log.w(TAG, "Unable to find attachment at index: " + index);
                return null;
            }

            AttachmentListItem att = (AttachmentListItem) children.get(index);
            if (att == null || att.getMapItem() == null
                    || !att.getAttachment().isValid()) {
                Log.w(TAG, "Skipping invalid attachment at index: " + index);
                return null;
            }

            return att;
        }

        @Override
        public boolean delete() {
            boolean del = true;
            List<HierarchyListItem> children = getChildren();
            if (FileSystemUtils.isEmpty(children)) {
                Log.d(TAG, "No attachments to delete");
                return false;
            }
            for (HierarchyListItem item : children) {
                MapItemAttachment att = ((AttachmentListItem) item)
                        .getAttachment();
                if (att == null || !att.isValid()) {
                    Log.w(TAG, "Skipping invalid delete");
                    continue;
                }

                del &= AttachmentMapOverlay.delete(att);
            }
            disposeChildren();
            notifyListener();
            return del;
        }

        @Override
        public Object getUserObject() {
            return this;
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if ((clazz.equals(Visibility.class)
                    || clazz.equals(Visibility2.class)) && !this.vizSupported)
                return null;
            return super.getAction(clazz);
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            ListModelExtraHolder h = v != null
                    && v.getTag() instanceof ListModelExtraHolder
                            ? (ListModelExtraHolder) v.getTag()
                            : null;
            if (h == null) {
                h = new ListModelExtraHolder();
                v = LayoutInflater.from(_view.getContext()).inflate(
                        R.layout.attachments_overlay_item, parent, false);
                h.gallery = v.findViewById(
                        R.id.attachments_overlay_item_btnGallery);
                v.setTag(h);
            }
            h.gallery.setEnabled(getChildCount() >= 1);
            h.gallery.setOnLongClickListener(this);
            h.gallery.setOnClickListener(this);
            return v;
        }

        @Override
        public void onClick(View v) {
            List<GalleryItem> items = new ArrayList<>();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof AttachmentListItem) {
                    MapItemAttachment att = ((AttachmentListItem) item)
                            .getAttachment();
                    if (att != null)
                        items.add(new GalleryFileItem(_view,
                                att.getAttachment(), att.getMapItem()));
                }
            }
            ImageGalleryReceiver.displayGallery(_view.getContext()
                    .getString(R.string.attachments), items);
        }

        @Override
        public boolean onLongClick(View v) {
            Toast.makeText(_view.getContext(), _view.getContext()
                    .getString(R.string.gallery_tip),
                    Toast.LENGTH_LONG).show();
            return true;
        }

        @Override
        public void refreshImpl() {
            boolean vizSupported = false;

            // Filter
            List<HierarchyListItem> filtered = new ArrayList<>();
            final long s = SystemClock.elapsedRealtime();
            List<MapItem> items = AttachmentManager.findAttachmentItems(
                    _view.getRootGroup());
            for (MapItem m : items) {
                List<File> files = AttachmentManager.getAttachments(m.getUID());
                if (FileSystemUtils.isEmpty(files))
                    continue;
                for (File f : files) {
                    //skip child directories
                    if (!FileSystemUtils.isFile(f)
                            || IOProviderFactory.isDirectory(f))
                        continue;
                    MapItemAttachment att = new MapItemAttachment(m, f);
                    if (att.isValid()) {
                        AttachmentListItem item = new AttachmentListItem(
                                this, att);
                        if (filter.accept(item)) {
                            filtered.add(item);
                            // Check that this item supports viz toggle
                            if (item.getAction(Visibility.class) != null)
                                vizSupported = true;
                        }
                    }
                }
            }

            final long e = SystemClock.elapsedRealtime();
            Log.d(TAG, "Added: " + filtered.size() + " attachments in "
                    + (e - s) + "ms");

            // Sort
            sortItems(filtered);

            this.vizSupported = vizSupported;

            // Update
            updateChildren(filtered);
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        public void dispose() {
            super.dispose();
            CoTInfoMapComponent.getInstance().removeAttachmentListener(this);
        }

        @Override
        public void onAttachmentAdded(MapItem item) {
            requestRefresh(this.path);
        }

        @Override
        public void onAttachmentRemoved(MapItem item) {
            requestRefresh(this.path);
        }

        /**
         * ******************************************************************
         */
        // Search
        @Override
        public Set<HierarchyListItem> find(String searchterm) {
            final String reterms = "*" + searchterm + "*";

            Set<Long> found = new HashSet<>();
            Set<HierarchyListItem> retval = new HashSet<>();

            List<HierarchyListItem> children = getChildren();
            if (FileSystemUtils.isEmpty(children)) {
                Log.d(TAG, "No attachments to search");
                return retval;
            }
            for (String field : SEARCH_FIELDS) {
                for (HierarchyListItem item : children) {
                    MapItemAttachment att = ((AttachmentListItem) item)
                            .getAttachment();
                    if (att == null || !att.isValid()) {
                        Log.w(TAG, "Skipping invalid attachment");
                        continue;
                    }

                    if (!found.contains(att.getMapItem()
                            .getSerialId())) {
                        //search metadata
                        if (MapGroup.matchItemWithMetaString(
                                att.getMapItem(), field, reterms)) {
                            retval.add(new AttachmentListItem(this, att));
                            found.add(att.getMapItem()
                                    .getSerialId());
                        }
                    }
                }
            }

            //also search attachment filename
            for (HierarchyListItem item : children) {
                MapItemAttachment att = ((AttachmentListItem) item)
                        .getAttachment();
                if (att == null || !att.isValid()) {
                    Log.w(TAG, "Skipping invalid attachment");
                    continue;
                }

                if (!found.contains(att.getMapItem().getSerialId())) {
                    if (att.getAttachment()
                            .getName()
                            .toLowerCase(LocaleUtil.getCurrent())
                            .contains(
                                    searchterm.toLowerCase(LocaleUtil
                                            .getCurrent()))) {
                        retval.add(new AttachmentListItem(this, att));
                        found.add(att.getMapItem()
                                .getSerialId());
                    }
                }
            }

            //also search jpg exif (via cache)
            //final long start = android.os.SystemClock.elapsedRealtime();
            for (HierarchyListItem item : children) {
                MapItemAttachment att = ((AttachmentListItem) item)
                        .getAttachment();
                if (att == null || !att.isValid()) {
                    Log.w(TAG, "Skipping invalid attachment");
                    continue;
                }

                if (!found.contains(att.getMapItem().getSerialId())
                        &&
                        ImageDropDownReceiver.ImageFileFilter.accept(null,
                                att.attachment.getName())) {
                    final File cacheFile = ImageGalleryFileAdapter
                            .getCacheFile(att.attachment, "exif");
                    if (FileSystemUtils.isFile(cacheFile)) {
                        try {
                            String caption = FileSystemUtils
                                    .copyStreamToString(cacheFile);
                            if (!FileSystemUtils.isEmpty(caption)
                                    && caption
                                            .toLowerCase(
                                                    LocaleUtil.getCurrent())
                                            .contains(
                                                    searchterm
                                                            .toLowerCase(
                                                                    LocaleUtil
                                                                            .getCurrent()))) {
                                retval.add(new AttachmentListItem(this, att));
                                found.add(att.getMapItem()
                                        .getSerialId());
                                //Log.d(TAG, "Found EXIf cache hit for: " + att.getAttachment());
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to search cache", e);
                        }
                    }
                }
            }

            //Log.d(TAG, "Attachment caption search time: " + (android.os.SystemClock.elapsedRealtime() - start));

            return retval;
        }

        @Override
        public boolean isSupported(Class<?> target) {
            return AttachmentExportWrapper.class.equals(target) ||
                    MissionPackageExportWrapper.class.equals(target) ||
                    Folder.class.equals(target) ||
                    KMZFolder.class.equals(target) ||
                    GPXExportWrapper.class.equals(target) ||
                    OGRFeatureExportWrapper.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class<?> target, ExportFilters filters)
                throws FormatNotSupportedException {
            if (super.getChildCount() <= 0 || !isSupported(target)) {
                //nothing to export
                return null;
            }

            if (AttachmentExportWrapper.class.equals(target)) {
                return toAttachment(filters);
            } else if (MissionPackageExportWrapper.class.equals(target)) {
                return toMissionPackage(filters);
            } else if (Folder.class.equals(target)) {
                return toKml(filters);
            } else if (KMZFolder.class.equals(target)) {
                return toKmz(filters);
            } else if (GPXExportWrapper.class.equals(target)) {
                return toGpx(filters);
            } else if (OGRFeatureExportWrapper.class.equals(target)) {
                return toOgrGeomtry(filters);
            }

            return null;
        }

        private AttachmentExportWrapper toAttachment(ExportFilters filters) {
            AttachmentExportWrapper f = new AttachmentExportWrapper();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                toAttachment(f, ((AttachmentListItem) item).getAttachment(),
                        filters);

            if (FileSystemUtils.isEmpty(f.getExports()))
                return null;

            return f;
        }

        private void toAttachment(AttachmentExportWrapper f,
                MapItemAttachment item,
                ExportFilters filters) {

            if (item != null
                    && item.isSupported(AttachmentExportWrapper.class)) {
                AttachmentExportWrapper itemWrapper = (AttachmentExportWrapper) item
                        .toObjectOf(AttachmentExportWrapper.class, filters);
                if (itemWrapper != null) {
                    f.add(itemWrapper);
                }
            }
        }

        private MissionPackageExportWrapper toMissionPackage(
                ExportFilters filters) {
            MissionPackageExportWrapper f = new MissionPackageExportWrapper();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                toMissionPackage(f,
                        ((AttachmentListItem) item).getAttachment(),
                        filters);

            if (FileSystemUtils.isEmpty(f.getFilepaths())
                    && FileSystemUtils.isEmpty(f.getUIDs()))
                return null;

            return f;
        }

        private void toMissionPackage(MissionPackageExportWrapper f,
                MapItemAttachment item,
                ExportFilters filters) {

            if (item != null
                    && item.isSupported(MissionPackageExportWrapper.class)) {
                MissionPackageExportWrapper itemWrapper = (MissionPackageExportWrapper) item
                        .toObjectOf(MissionPackageExportWrapper.class,
                                filters);
                if (itemWrapper != null
                        && !FileSystemUtils
                                .isEmpty(itemWrapper.getFilepaths())) {
                    f.getFilepaths().addAll(itemWrapper.getFilepaths());
                }
                if (itemWrapper != null
                        && !FileSystemUtils.isEmpty(itemWrapper.getUIDs())) {
                    f.getUIDs().addAll(itemWrapper.getUIDs());
                }
            }
        }

        public Folder toKml(ExportFilters filters)
                throws FormatNotSupportedException {

            Folder f = new Folder();
            f.setName(AttachmentMapOverlay.this.getName());
            f.setFeatureList(new ArrayList<Feature>());

            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Exportable
                        && ((Exportable) item).isSupported(Folder.class)) {
                    Folder itemFolder = (Folder) ((Exportable) item)
                            .toObjectOf(Folder.class, filters);
                    if (itemFolder != null
                            && itemFolder.getFeatureList() != null
                            && itemFolder.getFeatureList().size() > 0) {
                        f.getFeatureList().add(itemFolder);
                    }
                }
            }

            if (f.getFeatureList().size() < 1) {
                return null;
            }

            return f;
        }

        public Folder toKmz(final ExportFilters filters)
                throws FormatNotSupportedException {

            KMZFolder f = new KMZFolder();
            f.setName(AttachmentMapOverlay.this.getName());
            f.setFeatureList(new ArrayList<Feature>());

            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                //Attempt KMZ, fall back on KML
                if (item instanceof Exportable
                        && ((Exportable) item).isSupported(KMZFolder.class)) {
                    KMZFolder itemFolder = (KMZFolder) ((Exportable) item)
                            .toObjectOf(KMZFolder.class, filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.getFeatureList().add(itemFolder);
                        if (itemFolder.hasFiles()) {
                            f.getFiles().addAll(itemFolder.getFiles());
                        }
                    }
                } else if (item instanceof Exportable
                        && ((Exportable) item).isSupported(Folder.class)) {
                    Folder itemFolder = (Folder) ((Exportable) item)
                            .toObjectOf(Folder.class, filters);
                    if (itemFolder != null
                            && itemFolder.getFeatureList() != null
                            && itemFolder.getFeatureList().size() > 0) {
                        f.getFeatureList().add(itemFolder);
                    }
                }
            }

            if (f.getFeatureList().size() < 1) {
                return null;
            }

            return f;
        }

        private OGRFeatureExportWrapper toOgrGeomtry(ExportFilters filters)
                throws FormatNotSupportedException {
            OGRFeatureExportWrapper f = new OGRFeatureExportWrapper(
                    AttachmentMapOverlay.this.getName());
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Exportable
                        && ((Exportable) item)
                                .isSupported(OGRFeatureExportWrapper.class)) {
                    OGRFeatureExportWrapper itemFolder = (OGRFeatureExportWrapper) ((Exportable) item)
                            .toObjectOf(OGRFeatureExportWrapper.class,
                                    filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.addGeometries(itemFolder);
                    }
                }
            }

            if (f.isEmpty()) {
                return null;
            }

            return f;
        }

        private GPXExportWrapper toGpx(ExportFilters filters)
                throws FormatNotSupportedException {
            GPXExportWrapper f = new GPXExportWrapper();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Exportable
                        && ((Exportable) item)
                                .isSupported(GPXExportWrapper.class)) {
                    GPXExportWrapper itemFolder = (GPXExportWrapper) ((Exportable) item)
                            .toObjectOf(GPXExportWrapper.class, filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.add(itemFolder);
                    }
                }
            }

            if (f.isEmpty()) {
                return null;
            }

            return f;
        }

        @Override
        public boolean setVisible(final boolean visible) {
            boolean ret = true;
            List<HierarchyListItem> children = getChildren();
            if (FileSystemUtils.isEmpty(children)) {
                Log.d(TAG, "No attachments for setVisible");
                return false;
            }
            for (HierarchyListItem item : children) {
                MapItemAttachment att = ((AttachmentListItem) item)
                        .getAttachment();
                if (att == null || !att.isValid())
                    continue;

                ret &= AttachmentMapOverlay.setVisible(visible,
                        att.getMapItem(), _view);
            }

            return ret;
        }
    }

    /**
     * HierarchyListItem for map items which are being tracked
     * Partially based on MapItemHierarchyListItem
     */
    public class AttachmentListItem extends AbstractChildlessListItem
            implements GoTo, Export, Delete, Visibility, MapItemUser {

        private static final String TAG = "AttachmentListItem";
        private final AttachmentOverlayListModel _parent;
        private final MapItemAttachment _attachment;

        AttachmentListItem(AttachmentOverlayListModel parent,
                MapItemAttachment attachment) {
            this._parent = parent;
            this._attachment = attachment;
        }

        @Override
        public boolean goTo(boolean select) {
            if (_attachment == null || !_attachment.isValid()) {
                Log.w(TAG, "Skipping invalid attachment zoom");
                return false;
            }

            ArrayList<Intent> intents = new ArrayList<>(3);
            String uid = _attachment.getMapItem().getUID();

            //zoom map
            intents.add(new Intent("com.atakmap.android.maps.FOCUS")
                    .putExtra("uid", uid)
                    .putExtra("useTightZoom", true));

            intents.add(new Intent("com.atakmap.android.maps.SHOW_DETAILS")
                    .putExtra("uid", uid));

            if (ImageDropDownReceiver.ImageFileFilter.accept(null,
                    _attachment.attachment.getName())) {
                //if image, then also display the image
                intents.add(new Intent(ImageDropDownReceiver.IMAGE_DISPLAY)
                        .putExtra("uid", uid));
            } else {
                //otherwise display list of attachments
                intents.add(new Intent(ImageGalleryReceiver.VIEW_ATTACHMENTS)
                        .putExtra("uid", uid));
            }

            AtakBroadcast.getInstance().sendIntents(intents);

            return true;
        }

        @Override
        public String getTitle() {
            if (this._attachment == null || !this._attachment.isValid()) {
                Log.w(TAG, "Skipping invalid title");
                return "Attachment";
            }

            return _attachment.getAttachment().getName();
        }

        @Override
        public String getIconUri() {
            if (this._attachment == null || !this._attachment.isValid()) {
                Log.w(TAG, "Skipping invalid icon");
                return "asset://icons/generic_doc.png";
            }

            ResourceFile.MIMEType t = ResourceFile
                    .getMIMETypeForFile(this._attachment.getAttachment()
                            .getName());
            if (t == null) {
                Log.w(TAG, "Skipping invalid icon for file: "
                        + this._attachment.getAttachment().getName());
                return "asset://icons/generic_doc.png";
            }

            return t.ICON_URI;
        }

        @Override
        public boolean delete() {
            if (AttachmentMapOverlay.delete(this._attachment)) {
                requestRefresh();
                return true;
            }
            return false;
        }

        @Override
        public Object getUserObject() {
            if (this._attachment == null || !this._attachment.isValid()) {
                Log.w(TAG, "Skipping invalid user object");
                return null;
            }

            return this._attachment.getMapItem();
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            ListItemExtraHolder h = v != null
                    && v.getTag() instanceof ListItemExtraHolder
                            ? (ListItemExtraHolder) v.getTag()
                            : null;
            if (h == null) {
                h = new ListItemExtraHolder();
                v = LayoutInflater.from(_view.getContext()).inflate(
                        R.layout.attachmentoverlay_list_item, parent, false);
                h.title = v.findViewById(
                        R.id.attachment_overlay_list_item_title);
                h.icon = v.findViewById(
                        R.id.attachment_overlay_list_item_icon);
                v.setTag(h);
            }
            h.title.setText(ATAKUtilities.getDisplayName(
                    _attachment.getMapItem()));
            ATAKUtilities.SetIcon(_view.getContext(), h.icon,
                    _attachment.getMapItem());
            return v;
        }

        @Override
        public String getUID() {
            if (this._attachment == null || !this._attachment.isValid()) {
                Log.w(TAG, "Skipping invalid UID");
                return null;
            }

            return this._attachment.getMapItem().getUID() + "-"
                    + this._attachment.getAttachment().getName();
        }

        @Override
        public boolean isSupported(Class<?> target) {
            return !(this._attachment == null || !this._attachment.isValid())
                    && this._attachment.isSupported(target);

        }

        @Override
        public Object toObjectOf(Class<?> target, ExportFilters filters) {
            if (this._attachment == null || !this._attachment.isValid())
                return null;

            return this._attachment.toObjectOf(target, filters);
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if (clazz.equals(Visibility.class) && (_attachment == null
                    || !_attachment.isValid() || !_attachment.getMapItem()
                            .getType()
                            .equals(QuickPicReceiver.QUICK_PIC_IMAGE_TYPE)))
                return null;
            return super.getAction(clazz);
        }

        @Override
        public boolean isVisible() {
            return !(this._attachment == null || !this._attachment.isValid())
                    && AttachmentMapOverlay.isVisible(this._attachment
                            .getMapItem());

        }

        @Override
        public boolean setVisible(boolean visible) {
            return this._attachment == null
                    || !this._attachment.isValid()
                    || AttachmentMapOverlay.setVisible(visible,
                            this._attachment.getMapItem(), _view);

        }

        @Override
        public MapItem getMapItem() {
            return this._attachment.getMapItem();
        }

        public MapItemAttachment getAttachment() {
            return this._attachment;
        }
    }

    /**
     * If its a QuickPic, and if that's the only attachment for that UID then remove the marker
     * Assumes the attachment is deleted prior to this method so attachments remain
     */
    public static void deleteAttachmentMarker(MapItem item) {
        if (item != null && QuickPicReceiver.QUICK_PIC_IMAGE_TYPE
                .equals(item.getType())
                && AttachmentManager.getNumberOfAttachments(item.getUID()) == 0
                && item.removeFromGroup()) {
            Log.d(TAG, "Removing quickpic marker: " + item.getUID());
        }
    }

    private static boolean delete(MapItemAttachment att) {
        if (att == null || !att.isValid()) {
            Log.w(TAG, "Skipping invalid delete");
            return false;
        }

        FileSystemUtils.deleteFile(att.attachment);
        deleteAttachmentMarker(att.getMapItem());
        return true;
    }

    private final static class ItemDistanceComparator implements
            Comparator<MapItemAttachment> {
        private final GeoPoint pointOfInterest;

        ItemDistanceComparator(GeoPoint pointOfInterest) {
            this.pointOfInterest = pointOfInterest;
        }

        @Override
        public int compare(MapItemAttachment i1, MapItemAttachment i2) {
            final boolean validP1 = i1 != null && i1.isValid()
                    && i1.getMapItem() instanceof PointMapItem
                    && ((PointMapItem) i1.getMapItem()).getPoint() != null
                    && ((PointMapItem) i1.getMapItem()).getPoint().isValid();

            final boolean validP2 = i2 != null && i2.isValid()
                    && i2.getMapItem() instanceof PointMapItem
                    && ((PointMapItem) i2.getMapItem()).getPoint() != null
                    && ((PointMapItem) i2.getMapItem()).getPoint().isValid();

            if (validP1 && validP2) {
                final double d1 = GeoCalculations.distanceTo(
                        ((PointMapItem) i1.getMapItem()).getPoint(),
                        this.pointOfInterest);
                final double d2 = GeoCalculations.distanceTo(
                        ((PointMapItem) i2.getMapItem()).getPoint(),
                        this.pointOfInterest);
                if (d1 > d2)
                    return 1;
                else if (d2 > d1)
                    return -1;
            } else if (validP1) {
                return -1;
            } else if (validP2) {
                return 1;
            }

            return 0;
        }
    }

    private static boolean isVisible(MapItem mapItem) {

        //only support visibility for quick pics for now, not generic attachments
        return QuickPicReceiver.QUICK_PIC_IMAGE_TYPE.equals(mapItem.getType())
                && mapItem.getVisible();

    }

    private static boolean setVisible(boolean visible, MapItem mapItem,
            MapView view) {
        //only support visibility for quick pics for now, not generic attachments
        if (!QuickPicReceiver.QUICK_PIC_IMAGE_TYPE.equals(mapItem.getType()))
            return true;

        if (visible != mapItem.getVisible()) {
            mapItem.setVisible(visible);
            mapItem.persist(view.getMapEventDispatcher(), null,
                    AttachmentMapOverlay.class);
        } else {
            mapItem.setVisible(visible);
        }
        return true;
    }

    private static class ListModelExtraHolder {
        ImageButton gallery;
    }

    private static class ListItemExtraHolder {
        TextView title;
        ImageView icon;
    }
}
