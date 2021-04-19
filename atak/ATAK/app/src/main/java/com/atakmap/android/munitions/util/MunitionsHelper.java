
package com.atakmap.android.munitions.util;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.munitions.RangeRing;
import com.atakmap.android.munitions.TargetMunitionsDetailHandler;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper methods for managing munitions
 */
public class MunitionsHelper {

    final static public String WPN_DISPLAY = "remarks_munitionDisplayEnabled";

    public static final String NINELINE = "nineline";
    public static final String FIVELINE = "fiveline";
    public static final String TARGET = "";

    private final MapView _mapView;
    private final MapGroup _mapGroup;
    private final PointMapItem _target;
    private final String _fromLine;
    private final String _mapKey;
    private final Map<String, Object> _map;

    public MunitionsHelper(MapView mapView, PointMapItem target,
            String fromLine) {
        _mapView = mapView;
        _mapGroup = mapView.getRootGroup().findMapGroup("Weapons");
        _target = target;

        // In case the metadata key is passed in instead
        if (fromLine != null && fromLine.endsWith("Munitions"))
            fromLine = fromLine.substring(0, fromLine.indexOf("Munitions"))
                    .toLowerCase(LocaleUtil.getCurrent());

        // No line redirects
        if (fromLine == null || fromLine.equals("target")
                || fromLine.equals("noLine"))
            fromLine = TARGET;

        _fromLine = fromLine;

        if (NINELINE.equals(fromLine))
            _mapKey = "nineLineMunitions";
        else if (FIVELINE.equals(fromLine))
            _mapKey = "fiveLineMunitions";
        else
            _mapKey = "targetMunitions";

        Map<String, Object> map = target.getMetaMap(_mapKey);
        if (map == null)
            target.setMetaMap(_mapKey, map = new HashMap<>());
        _map = map;
    }

    /**
     * Add a weapon to the munitions map
     * @param categoryName Weapon category name
     * @param weaponName Weapon name
     * @param description Description
     * @param inner Inner (prone) radius
     * @param outer Outer (standing) radius
     */
    @SuppressWarnings("unchecked")
    public void addWeapon(String categoryName, String weaponName,
            String description, int inner, int outer) {
        Map<String, Object> category = (Map<String, Object>) _map
                .get(categoryName);
        if (category == null) {
            _map.put(categoryName, category = new HashMap<>());
            persistMap();
        }

        WeaponData weapon;
        Map<String, Object> wepMap = (Map<String, Object>) category
                .get(weaponName);
        if (wepMap == null) {
            // Going by the syntax from the DangerCloseLibrary\res\xml\ordnance_table.xml file
            weapon = new WeaponData(categoryName);
            weapon.name = weaponName;
            weapon.description = description;
            weapon.prone = inner;
            weapon.standing = outer;
            category.put(weaponName, weapon.toMap());
        } else {
            weapon = new WeaponData(categoryName, wepMap);
        }

        // Add range ring and/or update visibility
        RangeRing r = findRangeRing(weaponName);
        if (r == null)
            r = createRangeRing(weapon);
        r.setStandingProneVisible(isVisible());
    }

    /**
     * Remove a weapon from the munitions map
     * This method will also remove the category map if it's empty
     * @param categoryName Category name
     * @param weaponName Weapon name
     * @return True if the weapon was removed, false if it didn't exist
     */
    @SuppressWarnings("unchecked")
    public void removeWeapon(String categoryName, String weaponName) {
        Map<String, Object> category = (Map<String, Object>) _map
                .get(categoryName);
        if (category == null)
            return;

        if (category.remove(weaponName) == null)
            return;

        if (category.isEmpty()) {
            _map.remove(categoryName);
            persistMap();
        }

        // Remove range ring
        RangeRing r = findRangeRing(weaponName);
        if (r != null)
            r.remove();
    }

    /**
     * Remove all weapons and associated range rings
     */
    public void removeAllWeapons() {
        _target.removeMetaData(_mapKey);
        removeRangeRings();
    }

    /**
     * Get the total number of weapons
     * @return Total weapons count
     */
    @SuppressWarnings("unchecked")
    public int getWeaponsCount() {
        int count = 0;
        for (Map.Entry<String, Object> e : _map.entrySet()) {
            Map<String, Object> categoryData = (Map<String, Object>) e
                    .getValue();
            if (categoryData != null)
                count += categoryData.size();
        }
        return count;
    }

    /**
     * Set munitions visibility
     * @param visible True if visible
     */
    public void setVisible(boolean visible) {
        _target.setMetaBoolean(getVisibilityKey(), visible);
    }

    /**
     * Get munitions visibility
     * @param defaultVisible True if this set of munitions is visible by default
     * @return True if visible
     */
    public boolean isVisible(boolean defaultVisible) {
        return _target.getMetaBoolean(getVisibilityKey(), defaultVisible);
    }

    public boolean isVisible() {
        return isVisible(hasNoLine(_fromLine));
    }

    /**
     * Get the metadata key used for visibility - depends on line type
     * @return Visibility key
     */
    private String getVisibilityKey() {
        if (hasNoLine(_fromLine))
            return TargetMunitionsDetailHandler.TARGET_MUNITIONS_VISIBLE;
        else
            return _fromLine + WPN_DISPLAY;
    }

    /**
     * Create a range ring for a specific weapon
     * @param weapon Weapon data
     * @return Range ring
     */
    public RangeRing createRangeRing(WeaponData weapon) {
        RangeRing r = new RangeRing(_mapView, _mapGroup, _target, weapon.name,
                weapon.prone, weapon.standing, _fromLine);
        r.setCategory(weapon.category);
        return r;
    }

    /**
     * Find all range rings attached to the target
     * @return List of range rings
     */
    public List<RangeRing> findRangeRings() {
        List<RangeRing> rings = new ArrayList<>();
        for (MapItem mi : _mapGroup.getItems()) {
            if (!(mi instanceof RangeRing))
                continue;

            RangeRing r = (RangeRing) mi;

            String targetUID = r.getMetaString("target", "");
            if (!_target.getUID().equals(targetUID))
                continue;

            String fromLine = r.getFromLine();
            if (hasNoLine(fromLine) && hasNoLine(_fromLine)
                    || FileSystemUtils.isEquals(fromLine, _fromLine))
                rings.add(r);
        }
        return rings;
    }

    /**
     * Find the range ring for the given weapon name
     * @param weaponName Weapon name
     * @return Range ring or null if not found
     */
    public RangeRing findRangeRing(String weaponName) {
        MapItem mi = _mapView.getRootGroup().deepFindUID(
                _target.getUID() + "." + weaponName + "." + _fromLine);
        return mi instanceof RangeRing ? (RangeRing) mi : null;
    }

    /**
     * Remove the range ring from the map (and map view)
     * @param r Range ring
     * @param removeWeapon True to remove the matching weapon entry
     */
    public void removeRangeRing(RangeRing r, boolean removeWeapon) {
        r.remove();

        // Remove the weapon entry
        if (removeWeapon) {
            final String category = r.getCategory();
            final String wepName = r.getWeaponName();
            forEachCategory(new CategoryConsumer() {
                @Override
                public void forCategory(String categoryName,
                        Map<String, Object> map) {
                    if (category == null || categoryName.equals(category))
                        map.remove(wepName);
                }
            });
        }
    }

    /**
     * Set the visibility of all range rings
     * @param visible True if visible
     */
    public void setRangeRingsVisible(boolean visible) {
        for (RangeRing r : findRangeRings())
            r.setStandingProneVisible(visible);
    }

    /**
     * Add range rings based on weapon metadata
     */
    public void addRangeRings(final boolean visible) {
        forEachWeapon(new WeaponConsumer() {
            @Override
            public void forWeapon(WeaponData data) {
                RangeRing r = findRangeRing(data.name);
                if (r == null)
                    r = createRangeRing(data);
                r.setStandingProneVisible(visible);
            }
        });
    }

    public void addRangeRings() {
        addRangeRings(isVisible());
    }

    /**
     * Remove all range rings that apply to this target (and line)
     * Does NOT remove the weapon entries
     */
    public void removeRangeRings() {
        for (RangeRing r : findRangeRings())
            r.remove();
    }

    /**
     * Persist the target
     */
    public void persist() {
        _target.persist(_mapView.getMapEventDispatcher(), null, getClass());
    }

    /**
     * Persist the metadata map to the target marker
     */
    private void persistMap() {
        // getMetaMap returns a copy, so we need to call this after making
        // modifications to the base map
        _target.setMetaMap(_mapKey, _map);
    }

    /**
     * Loop through each category in the metadata map
     * @param consumer Consumer
     */
    @SuppressWarnings("unchecked")
    public void forEachCategory(CategoryConsumer consumer) {
        List<String> empty = new ArrayList<>();
        for (Map.Entry<String, Object> e : _map.entrySet()) {
            Object o = e.getValue();
            if (!(o instanceof Map))
                continue;
            String catName = e.getKey();
            Map<String, Object> catMap = (Map<String, Object>) o;
            consumer.forCategory(catName, catMap);

            // The consumer may have removed data from this category
            // Check if empty and flag for removal if so
            if (catMap.isEmpty())
                empty.add(catName);
        }

        // Remove empty categories
        for (String catName : empty)
            _map.remove(catName);
        persistMap();
    }

    /**
     * Loop through each weapon in the metadata map
     * @param consumer Consumer
     */
    @SuppressWarnings("unchecked")
    public void forEachWeapon(final WeaponConsumer consumer) {
        forEachCategory(new CategoryConsumer() {
            @Override
            public void forCategory(String categoryName,
                    Map<String, Object> map) {
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    Object o = e.getValue();
                    if (!(o instanceof Map))
                        continue;
                    Map<String, Object> wepData = (Map<String, Object>) o;
                    WeaponData weapon = new WeaponData(categoryName, wepData);
                    if (!weapon.isValid())
                        continue;
                    consumer.forWeapon(weapon);
                }
            }
        });
    }

    // "No line" has 3 different representations: null, <empty string>, or "noLine"
    public static boolean hasNoLine(String fromLine) {
        return FileSystemUtils.isEmpty(fromLine) || fromLine.equals("noLine");
    }
}
