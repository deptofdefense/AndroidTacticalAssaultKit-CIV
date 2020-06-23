
package com.atakmap.android.munitions;

import com.atakmap.android.cot.MarkerDetailHandler;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * Class responsible from handling any target specific munitions
 */
public class TargetMunitionsDetailHandler implements MarkerDetailHandler {

    private static final String TAG = "TargetMunitionsDetailHandler";
    final static public String TARGET_MUNITIONS_VISIBLE = "target_munitions_visible";

    @Override
    public void toCotDetail(final Marker marker, final CotDetail detail) {
        if (!marker.hasMetaValue("targetMunitions"))
            return;
        CotDetail munitions = new CotDetail("targetMunitions");
        Map<String, Object> munitionsData = marker
                .getMetaMap("targetMunitions");
        if (munitionsData == null)
            return;
        for (Map.Entry<String, Object> e : munitionsData.entrySet()) {
            String c = e.getKey();
            CotDetail category = new CotDetail("category");
            category.setAttribute("name", c);
            Map<String, Object> categoryData = (Map<String, Object>) e
                    .getValue();
            if (categoryData == null)
                continue;
            for (Map.Entry<String, Object> e2 : categoryData.entrySet()) {
                String w = e2.getKey();
                CotDetail weapon = new CotDetail("weapon");
                Map<String, Object> weaponData = (Map<String, Object>) categoryData
                        .get(w);
                if (weaponData != null) {
                    weapon.setAttribute("name", w);
                    weapon.setAttribute("description",
                            (String) weaponData.get("description"));
                    weapon.setAttribute("prone",
                            (String) weaponData.get("prone"));
                    weapon.setAttribute("standing",
                            (String) weaponData.get("standing"));
                    category.addChild(weapon);
                }
            }
            munitions.addChild(category);
        }
        boolean b = marker.getMetaBoolean(TARGET_MUNITIONS_VISIBLE,
                false);
        munitions.setAttribute("munitionVisibility", String.valueOf(b));
        detail.addChild(munitions);
    }

    @Override
    public void toMarkerMetadata(Marker marker, CotEvent event,
            CotDetail detail) {
        if (detail == null)
            return;
        //Log.d(TAG, "checking hostile has munitions");
        String s = detail.getAttribute("munitionVisibility");
        boolean b = Boolean.parseBoolean(s);
        marker.setMetaBoolean(TARGET_MUNITIONS_VISIBLE, b);
        //Log.d(TAG, "hostile has munitions" + munitions);
        Map<String, Object> munitionsData = new HashMap<>();
        for (int c = 0; c < detail.childCount(); c++) {
            Map<String, Object> categoryData = new HashMap<>();
            CotDetail category = detail.getChild(c);
            if (category != null) {
                String categoryName = category.getAttribute("name");
                for (int w = 0; w < category.childCount(); w++) {
                    Map<String, Object> weaponData = new HashMap<>();
                    CotDetail weapon = category.getChild(w);
                    if (weapon != null) {
                        String weaponName = weapon.getAttribute("name");
                        weaponData.put("name", weaponName);
                        weaponData.put("description",
                                weapon.getAttribute("description"));
                        weaponData.put("prone",
                                weapon.getAttribute("prone"));
                        weaponData.put("standing",
                                weapon.getAttribute("standing"));
                        weaponData.put("category", categoryName);
                        categoryData.put(weaponName, weaponData);
                        _sendRedIntent(marker, weaponName,
                                categoryName,
                                weapon.getAttribute("prone"),
                                weapon.getAttribute("standing"),
                                weapon.getAttribute("description"));
                    }
                }
                munitionsData.put(categoryName, categoryData);
            }
        }
        marker.setMetaMap("targetMunitions", munitionsData);
    }

    /**
     * Function that sends the intent to make a new range ring
     * @param marker
     * @param weaponName
     * @param categoryName
     * @param prone
     * @param standing
     * @param description
     */
    private void _sendRedIntent(Marker marker, String weaponName,
            String categoryName,
            String prone, String standing, String description) {
        try {
            int standingInt = Integer.parseInt(standing);
            int proneInt;
            if (prone == null) {
                proneInt = standingInt;
            } else {
                proneInt = Integer.parseInt(prone);
            }
            //Log.d(TAG, "received a request to show danger close: " + weaponName);
            DangerCloseReceiver.getInstance().createDangerClose(marker,
                    weaponName, categoryName, description, proneInt,
                    standingInt, false, "", false);

        } catch (NumberFormatException nfe) {
            Log.e(TAG, "exception parsing standing: " + standing + "or prone: "
                    + prone);
        }
    }
}
