
package com.atakmap.android.routes.nav;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.cot.RouteDetailHandler;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;

/**
 * Class to handle the serialization and deserialization of navigation cues inside of Routes.
 */
public class NavigationCueHandler extends RouteDetailHandler {

    public static final String TAG = "NavigationCueHandler";

    // part of the __navcue
    public static final String ID = "id";
    public static final String VOICE_CUE = "voice";
    public static final String TEXT_CUE = "text";

    public static final String TRIGGER_MODE = "mode";
    public static final String TRIGGER_VALUE = "value";

    public NavigationCueHandler() {
        super("__navcues");
    }

    @Override
    public CotDetail toCotDetail(Route route) {
        CotDetail navDetail = new CotDetail("__navcues");

        for (Map.Entry<String, NavigationCue> navCue : route
                .getNavigationCues().entrySet()) {
            String uid = navCue.getKey();
            NavigationCue cue = navCue.getValue();

            CotDetail nodeDetail = new CotDetail("__navcue");

            if (uid == null) {
                throw new NullPointerException(
                        "Cannot store a nav cue with a null ID");
            }

            if (uid.isEmpty()) {
                throw new IllegalArgumentException("Cannot store an empty UID");
            }

            nodeDetail.setAttribute(ID, uid);

            nodeDetail.setAttribute(VOICE_CUE, cue.getVoiceCue());
            nodeDetail.setAttribute(TEXT_CUE, cue.getTextCue());

            for (NavigationCue.ConditionalNavigationCue cCue : cue
                    .getCues()) {
                CotDetail triggerDetail = new CotDetail("trigger");
                triggerDetail.setAttribute(TRIGGER_MODE, cCue.getTriggerMode()
                        .toValue().toString());
                triggerDetail.setAttribute(TRIGGER_VALUE,
                        String.valueOf(cCue.getTriggerValue()));
                nodeDetail.addChild(triggerDetail);
            }

            navDetail.addChild(nodeDetail);
        }

        return navDetail;
    }

    @Override
    public ImportResult toRouteMetadata(Route route, CotDetail navCues) {
        List<CotDetail> children = navCues.getChildren();
        Map<String, NavigationCue> cues = new HashMap<>(children.size());
        for (CotDetail navCue : children) {
            //Log.d(TAG, "child " + i + ": " + navCueDetail.toString());

            // Get the raw fields
            String id = navCue.getAttribute(ID);
            String voiceCue = navCue.getAttribute(VOICE_CUE);
            String textCue = navCue.getAttribute(TEXT_CUE);

            if (id == null || voiceCue == null || textCue == null) {
                continue;
            }

            NavigationCue cue = new NavigationCue(id, voiceCue, textCue);
            //Log.d(TAG, "restoring: " + id + " " + voiceCue);

            List<CotDetail> navChildren = navCue.getChildren();
            for (CotDetail d : navChildren) {
                String rawTriggerMode = d.getAttribute(TRIGGER_MODE);
                String rawTriggerValue = d.getAttribute(TRIGGER_VALUE);

                if (rawTriggerMode == null || rawTriggerValue == null) {
                    continue;
                }
                // Clean the raw fields up
                NavigationCue.TriggerMode triggerMode = NavigationCue.TriggerMode
                        .fromValue(rawTriggerMode.charAt(0));
                int triggerValue = Integer.parseInt(rawTriggerValue);
                //Log.d(TAG, "shb: " + " " + voiceCue + " " + rawTriggerMode + " " + rawTriggerValue);
                cue.addCue(triggerMode, triggerValue);
            }
            cues.put(id, cue);
        }
        route.setNavigationCues(cues);
        return ImportResult.SUCCESS;
    }
}
