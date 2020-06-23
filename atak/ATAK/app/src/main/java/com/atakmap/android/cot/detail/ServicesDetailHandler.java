
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO: Documentation
 * Related to {@link RequestDetailHandler}
 */
class ServicesDetailHandler extends CotDetailHandler {

    ServicesDetailHandler() {
        super("__services");
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        // Not exported?
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String notify = "";
        String taskType = "t-s-i"; // TODO AS make this configurable
        List<CotDetail> children = detail.getChildren();
        for (CotDetail child : children) {
            if (child != null && child.getElementName().equals("ipfeature")
                    && child.getAttribute("type").startsWith("s-" + taskType)) {
                CotDetail n = child.getChild(0);
                if (n != null) {
                    String uri = n.getAttribute("uri");
                    if (uri != null && uri.startsWith("cot://")) {
                        notify = uri.substring(6).replace(";", ":");
                        break;
                    }
                }
            }
            // <__services>
            // <ipfeature desc="UAV Tasking" type="s-t-s-i">
            // <sink mime="application/x-cot" uri="cot://192.168.40.182:8088;tcp"/>
            // </ipfeature>
            // </__services>
        }
        if (notify.equals(""))
            return ImportResult.FAILURE;

        item.setType(event.getType());
        item.setMetaString("notify", notify);
        item.setMetaString("taskType", taskType);
        item.setMetaDouble("alt", event.getCotPoint().getHae());

        ArrayList<String> tasks = new ArrayList<>(); // TODO AS this is wrong
        tasks.add("ISR");
        item.setMetaStringArrayList("tasks", tasks);
        return ImportResult.SUCCESS;
    }
}
