
package com.atakmap.android.munitions;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.android.importfiles.sort.ImportTXTSort;
import com.atakmap.android.menu.MenuCapabilities;

/**
 * The functionality to scan the map and provide for warnings during 
 * danger close scenarios.    This component is responsible for 
 * maintaining the dange close calculation thread and management of 
 * the related widgets.
 */
public class DangerCloseMapComponent extends AbstractMapComponent {

    private DangerCloseReceiver _dcReceiver;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        MenuCapabilities.registerSupported("capability.weapons");

        ImportTXTSort.addSignature("<Custom_Threat_Rings",
                DangerCloseAdapter.DIRNAME, null);

        FileSystemUtils.ensureDataDirectory(DangerCloseAdapter.DIRNAME, false);

        CotDetailManager.getInstance().registerHandler("targetMunitions",
                new TargetMunitionsDetailHandler());

        MapGroup mapGroup = new DefaultMapGroup("Weapons");
        mapGroup.setMetaBoolean("addToObjList", true);
        mapGroup.setMetaBoolean("permaGroup", true);
        view.getMapOverlayManager().addMarkersOverlay(
                new DefaultMapGroupOverlay(view, mapGroup,
                        "asset://icons/blast_rings.png"));

        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(DangerCloseReceiver.ADD,
                "Add a weapon to a target",
                new DocumentedExtra[] {
                        new DocumentedExtra("targetUID", "Hostile marker UID",
                                false, String.class),
                        new DocumentedExtra("weaponName", "Weapon name",
                                false, String.class),
                        new DocumentedExtra("inner", "Inner range (meters)",
                                false, Integer.class),
                        new DocumentedExtra("outer", "Outer range (meters)",
                                false, Integer.class),
                        new DocumentedExtra("defaultVis",
                                "False to hide on creation",
                                true, Boolean.class)
                });
        f.addAction(DangerCloseReceiver.TOGGLE,
                "Toggle weapon on a target - similar to add but with extra functionality",
                new DocumentedExtra[] {
                        new DocumentedExtra("target", "Hostile marker UID",
                                false, String.class),
                        new DocumentedExtra("name",
                                "Weapon name",
                                false, String.class),
                        new DocumentedExtra("category",
                                "Weapon category name",
                                false, String.class),
                        new DocumentedExtra("description",
                                "Weapon description",
                                true, String.class),
                        new DocumentedExtra("innerRange",
                                "Inner range (meters)",
                                false, Integer.class),
                        new DocumentedExtra("outerRange",
                                "Outer range (meters)",
                                false, Integer.class),
                        new DocumentedExtra("remove",
                                "True to remove ring instead of creating",
                                true, Boolean.class),
                        new DocumentedExtra("persist",
                                "True to persist marker afterwards",
                                true, Boolean.class),
                        new DocumentedExtra("fromLine",
                                "Either \"fiveline\", \"nineline\", or null",
                                true, String.class)
                });
        f.addAction(DangerCloseReceiver.REMOVE,
                "Remove a range ring",
                new DocumentedExtra[] {
                        new DocumentedExtra("targetUID", "Hostile marker UID",
                                false, String.class),
                        new DocumentedExtra("weaponName", "Weapon name",
                                false, String.class),
                        new DocumentedExtra("fromLine",
                                "Either \"fiveline\", \"nineline\", or null",
                                true, String.class)
                });
        f.addAction(DangerCloseReceiver.REMOVE_WEAPON,
                "Remove a range ring AND the weapon entry from the target",
                new DocumentedExtra[] {
                        new DocumentedExtra("targetUID", "Hostile marker UID",
                                false, String.class),
                        new DocumentedExtra("weaponName", "Weapon name",
                                false, String.class),
                        new DocumentedExtra("fromLine",
                                "Either \"fiveline\", \"nineline\", or null",
                                true, String.class)
                });
        f.addAction(DangerCloseReceiver.REMOVE_ALL,
                "Remove a specific weapon from all targets",
                new DocumentedExtra[] {
                        new DocumentedExtra("weapon",
                                "Weapon name",
                                false, String.class),
                        new DocumentedExtra("category",
                                "Weapon category name",
                                false, String.class)
                });
        f.addAction(DangerCloseReceiver.OPEN,
                "Open the REDs and MSDs drop-down",
                new DocumentedExtra[] {
                        new DocumentedExtra("targetUID",
                                "Hostile marker UID",
                                false, String.class),
                        new DocumentedExtra("fromLine",
                                "Either \"fiveline\", \"nineline\", or null",
                                true, String.class),
                        new DocumentedExtra("category",
                                "One of the category strings 'Fixed', 'Rotary', .. ' Minimum Safe Distances'",
                                false, String.class)
                });
        f.addAction(DangerCloseReceiver.TOGGLE_LABELS,
                "Toggle labels on a range ring",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid", "Range ring UID",
                                false, String.class)
                });

        _dcReceiver = new DangerCloseReceiver(view);
        registerReceiver(context, _dcReceiver, f);
    }

    @Override
    public void onDestroyImpl(Context context, MapView view) {
    }

}
