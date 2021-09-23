
package com.atakmap.android.hashtags.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.atakmap.android.gui.NonEmptyEditTextDialog;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hashtags.StickyHashtags;
import com.atakmap.android.hashtags.util.HashtagMap;
import com.atakmap.android.hashtags.util.HashtagSet;
import com.atakmap.android.hashtags.view.HashtagEditText;
import com.atakmap.android.hashtags.view.HashtagDialog;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.HierarchySelectHandler;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MapItemSelectTool;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.AttachmentWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used for managing hashtags in Overlay Manager
 */
public class HashtagMapOverlay extends AbstractMapOverlay2 implements
        HashtagManager.OnUpdateListener, AttachmentWatcher.Listener {

    private static final String TAG = "HashtagMapOverlay";
    protected static final String TAG_CONTENT = "com.atakmap.android.hashtags.overlay.TAG_CONTENT";

    private static final int ORDER = 8;

    private final MapView _mapView;
    protected final Context _context;
    protected final HashtagMap<HashtagListItem> _lists = new HashtagMap<>();
    private final View _listHeader;

    protected ListModel _listModel;
    protected HierarchyListAdapter _om;

    public HashtagMapOverlay(MapView view) {
        _mapView = view;
        _context = view.getContext();

        LayoutInflater inf = LayoutInflater.from(_context);
        _listHeader = inf.inflate(R.layout.hashtag_overlay_header, _mapView,
                false);

        HashtagManager.getInstance().registerUpdateListener(this);
        AttachmentWatcher.getInstance().addListener(this);

        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(TAG_CONTENT, "Callback intent for map item selector");
        AtakBroadcast.getInstance().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String tag = intent.getStringExtra("tag");
                String[] uids = intent.getStringArrayExtra("itemUIDs");
                if (FileSystemUtils.isEmpty(tag)
                        || FileSystemUtils.isEmpty(uids))
                    return;

                // Add tag to selected items
                for (String uid : uids) {
                    MapItem item = _mapView.getRootGroup().deepFindUID(uid);
                    if (item != null) {
                        HashtagSet tags = item.getHashtags();
                        tags.add(tag);
                        item.setHashtags(tags);
                    }
                }
            }
        }, f);

        HierarchySelectHandler.register(getClass(),
                new HashtagListUserSelect(_mapView));
    }

    public void dispose() {
        HashtagManager.getInstance().unregisterUpdateListener(this);
        AttachmentWatcher.getInstance().removeListener(this);
        HierarchySelectHandler.unregister(getClass());
    }

    @Override
    public String getIdentifier() {
        return _context.getString(R.string.hashtags);
    }

    @Override
    public String getName() {
        return getIdentifier();
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
        if (_listModel == null)
            _listModel = new ListModel();
        if (adapter instanceof HierarchyListAdapter)
            _om = (HierarchyListAdapter) adapter;
        _listModel.refresh(adapter, prefFilter);
        return _listModel;
    }

    @Override
    public void onHashtagsUpdate(HashtagContent content) {
        refresh();
    }

    @Override
    public void onAttachmentAdded(File attFile) {
        refresh();
    }

    @Override
    public void onAttachmentRemoved(File attFile) {
        refresh();
    }

    void refresh() {
        if (_om != null && _om.isActive())
            _om.refreshList();
    }

    View getListHeader() {
        return _listHeader;
    }

    MapView getMapView() {
        return _mapView;
    }

    void promptAddTags(final String tag) {
        if (tag == null) {
            final HashtagEditText et = new HashtagEditText(_context);
            et.setContentDescription(_context.getString(R.string.new_hashtag));
            et.setSingleLine(true);
            new NonEmptyEditTextDialog().onClick(et);
            et.addTextChangedListener(new AfterTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    promptAddTags(et.getHashtag());
                }
            });
            return;
        }

        TileButtonDialog d = new TileButtonDialog(_mapView);
        d.addButton(R.drawable.select_from_map, R.string.map_select);
        d.addButton(R.drawable.select_from_overlay, R.string.overlay_title);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                // Map select
                if (w == 0) {
                    Intent i = new Intent(TAG_CONTENT).putExtra("tag", tag);
                    Bundle b = new Bundle();
                    b.putString("prompt", _context.getString(
                            R.string.tag_items, tag));
                    b.putParcelable("callback", i);
                    ToolManagerBroadcastReceiver.getInstance().startTool(
                            MapItemSelectTool.TOOL_NAME, b);
                }

                // Overlays select
                else if (w == 1) {
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            HierarchyListReceiver.MANAGE_HIERARCHY)
                                    .putExtra("hier_userselect_handler",
                                            HashtagMapOverlay.class.getName())
                                    .putExtra("hier_usertag", tag));
                }
            }
        });
        d.show(_context.getString(R.string.add_to_hashtag, tag), null, true);
    }

    void promptStickyTags(String newTag) {
        new HashtagDialog(_mapView)
                .setTitle(_context.getString(R.string.sticky_tags))
                .setDefaultTag(newTag)
                .setTags(StickyHashtags.getInstance().getTags())
                .setCallback(new HashtagDialog.Callback() {
                    @Override
                    public void onSetTags(Collection<String> tags) {
                        StickyHashtags.getInstance().setTags(tags);
                    }
                })
                .show();
    }

    protected class ListModel extends AbstractHierarchyListItem2 implements
            View.OnClickListener, Visibility2, Search, GroupDelete {

        protected boolean _vizSupported;

        protected ListModel() {
            this.asyncRefresh = true;
            this.reusable = true;
        }

        @Override
        public String getTitle() {
            return getIdentifier();
        }

        @Override
        public String getUID() {
            return getIdentifier();
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.ic_hashtag);
        }

        @Override
        public int getPreferredListIndex() {
            return ORDER;
        }

        @Override
        public Object getUserObject() {
            return ListModel.this;
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            return null;
        }

        @Override
        public View getHeaderView() {
            _listHeader.findViewById(R.id.add_hashtag)
                    .setOnClickListener(this);
            View stickyBtn = _listHeader.findViewById(R.id.sticky_hashtag);
            stickyBtn.setVisibility(View.VISIBLE);
            stickyBtn.setOnClickListener(this);
            return _listHeader;
        }

        @Override
        public int getDescendantCount() {
            return 0;
        }

        @Override
        protected void refreshImpl() {
            boolean vizSupported = false;
            List<HierarchyListItem> filtered;
            synchronized (_lists) {
                List<String> tags = HashtagManager.getInstance().getTags(null);
                filtered = new ArrayList<>(tags.size());
                HashtagSet removed = new HashtagSet(tags);
                for (String tag : tags) {
                    HashtagListItem list = _lists.get(tag);
                    if (list == null)
                        _lists.put(tag, list = new HashtagListItem(
                                HashtagMapOverlay.this, tag));
                    list.syncRefresh(_om, this.filter);
                    if (this.filter.accept(list)) {
                        if (list.getAction(Visibility.class) != null)
                            vizSupported = true;
                        filtered.add(list);
                    }
                    removed.remove(tag);
                }
                for (String tag : removed) {
                    HashtagListItem list = _lists.remove(tag);
                    if (list != null)
                        list.dispose();
                }
            }
            _vizSupported = vizSupported;
            sortItems(filtered);
            updateChildren(filtered);
        }

        @Override
        public boolean hideIfEmpty() {
            return this.filter != null && !this.filter.isDefaultFilter();
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if (!_vizSupported && (clazz.equals(Visibility.class)
                    || clazz.equals(Visibility2.class)))
                return null;
            return super.getAction(clazz);
        }

        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<HierarchyListItem> ret = new HashSet<>();
            for (HierarchyListItem item : getChildren()) {
                if (item.getTitle().toLowerCase(LocaleUtil.getCurrent())
                        .contains(terms))
                    ret.add(item);
            }
            return ret;
        }

        @Override
        public void onClick(View v) {
            int id = v.getId();

            if (id == R.id.add_hashtag)
                promptAddTags(null);
            else if (id == R.id.sticky_hashtag)
                promptStickyTags(null);
        }
    }
}
