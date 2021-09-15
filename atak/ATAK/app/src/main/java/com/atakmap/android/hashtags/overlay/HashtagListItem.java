
package com.atakmap.android.hashtags.overlay;

import android.view.View;

import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.app.R;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * List of content associated with a hashtag
 */
public class HashtagListItem extends AbstractHierarchyListItem2 implements
        View.OnClickListener, Visibility2, Search, Delete {

    protected final HashtagMapOverlay _overlay;
    private final String _tag;

    private boolean _vizSupported;

    public HashtagListItem(HashtagMapOverlay overlay, String tag) {
        _overlay = overlay;
        _tag = tag;
        this.asyncRefresh = true;
        this.reusable = true;
    }

    @Override
    public String getTitle() {
        return _tag;
    }

    @Override
    public String getUID() {
        return _tag.toLowerCase(LocaleUtil.getCurrent());
    }

    @Override
    public String getIconUri() {
        return "gone";
    }

    @Override
    public Object getUserObject() {
        return _tag;
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public boolean hideIfEmpty() {
        return true;
    }

    @Override
    public View getHeaderView() {
        View listHeader = _overlay.getListHeader();
        listHeader.findViewById(R.id.add_hashtag)
                .setOnClickListener(this);
        listHeader.findViewById(R.id.sticky_hashtag).setVisibility(View.GONE);
        return listHeader;
    }

    @Override
    protected void refreshImpl() {
        Set<HashtagContent> contents = HashtagManager.getInstance()
                .findContents(_tag);

        _vizSupported = false;
        List<HierarchyListItem> filtered = new ArrayList<>(contents.size());
        for (HashtagContent c : contents) {
            HierarchyListItem item = new HashtagContentListItem(_overlay, c);
            if (this.filter.accept(item)) {
                _vizSupported = item.getAction(Visibility.class) != null;
                filtered.add(item);
            }
        }

        sortItems(filtered);
        updateChildren(filtered);
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
            _overlay.promptAddTags(_tag);
    }
}
