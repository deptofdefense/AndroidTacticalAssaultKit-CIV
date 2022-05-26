
package com.atakmap.android.toolbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.coremap.log.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ToolManagerBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = "ToolManagerBroadcastReceiver";

    protected ToolManagerBroadcastReceiver() {
    }

    synchronized public static ToolManagerBroadcastReceiver getInstance() {
        if (_instance == null) {
            _instance = new ToolManagerBroadcastReceiver();
        }
        return _instance;
    }

    public void registerTool(String toolbarIdentifier, Tool tool) {
        tools.put(toolbarIdentifier, tool);
    }

    public void unregisterTool(String toolbarIdentifier) {
        tools.remove(toolbarIdentifier);
    }

    /**
     * Starts a tool synchronously (without going through an intent)
     * @param identifier Tool identifier
     * @param extras Tool extras
     */
    public void startTool(String identifier, Bundle extras) {
        // Make sure to close the radial (usually handled by MapMenuReceiver END_TOOL intent listener)
        if (MapMenuReceiver.getInstance() != null)
            MapMenuReceiver.getInstance().hideMenu();

        // Begin the tool synchronously
        beginTool(identifier, extras);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;
        String toolId = intent.getStringExtra("tool");
        String method = intent.getStringExtra("method");
        switch (action) {
            case BEGIN_TOOL:
                if (toolId != null)
                    // Log.d(TAG,"BEGIN TOOL: "+intent.getExtras().getString("tool"));
                    beginTool(toolId, intent.getExtras());
                break;
            case BEGIN_SUB_TOOL:
                if (toolId != null)
                    beginSubTool(toolId, intent.getExtras());

                break;
            case END_TOOL:
                if (intentSelectsTool(intent, activeSubTool)) {
                    endTool(getActiveSubTool(), true);
                } else if (intentSelectsTool(intent, activeTool)) {
                    // Log.d(TAG,"END TOOL: "+intent.getExtras().getString("tool"));
                    endTool();
                }

                break;
            case INVOKE_METHOD_TOOL:
                if (method != null)
                    invokeToolMethod(intent);
                break;
        }
    }

    private void invokeToolMethod(Intent intent) {
        Tool active = getActiveTool();
        if (active == null)
            return;
        Class<? extends Tool> c = active.getClass();
        try {
            Method m = c.getMethod(intent.getStringExtra("method"),
                    c.getClasses());
            int arg = 1;
            ArrayList<Object> args = new ArrayList<>();

            while (intent.hasExtra(Integer.toString(arg))) {
                Object o = intent.getStringExtra(Integer.toString(arg));
                args.add(o);
                arg++;
            }
            @SuppressWarnings("rawtypes")
            Class[] argTypes = m.getParameterTypes();
            int i = 0;
            if (argTypes.length != args.size()) {
                return;
            }
            for (Object o : args) {
                if (!o.getClass().equals(argTypes[i])) {
                    return;
                }
                i++;
            }
            m.invoke(active, args.toArray());
        } catch (Exception ignored) {
        }
    }

    private void beginTool(final String toolIdentifier, Bundle extras) {
        // Log.d(TAG,"Active tool: "+activeTool+" WaitKeepThePairingLine: "+extras.getBoolean("WaitKeepThePairingLine",
        // true));
        Tool tool = tools.get(toolIdentifier);
        if (activeTool != null && toolIdentifier != null) {
            // TODO OMG UGLY //PairingLineTool.ID
            if (activeTool.equals("PAIRING_LINE_TOOL")
                    && !toolIdentifier.equals(activeTool)
                    && extras.getBoolean("WaitKeepThePairingLine", true)) {
                // do nothing
            } else if (activeTool
                    .equals("com.atakmap.android.toolbars.BloodHoundButtonTool")
                    && toolIdentifier.equals("PAIRING_LINE_TOOL")) {
                _bloodhoundActive = true;
            } else
                endTool();
        }
        beginTool(tool, extras, false);
    }

    private void beginSubTool(String toolIdentifier, Bundle extras) {
        // Sub tools are special; if no tool is running, they work like tools, otherwise they
        // work as a part of the currently running tool, ending when it ends

        Tool subTool = tools.get(toolIdentifier);
        if (activeTool == null || activeTool.equals(toolIdentifier)) {
            beginTool(toolIdentifier, extras);
        } else {
            endTool(getActiveSubTool(), true);
            beginTool(subTool, extras, true);
        }
    }

    public void endCurrentTool() {
        if (activeTool != null && !activeTool.equals("PAIRING_LINE_TOOL"))
            endTool();
    }

    private void endTool() {
        endTool(getActiveSubTool(), true);
        endTool(getActiveTool(), false);
    }

    private void beginTool(Tool tool, Bundle extras, boolean subTool) {
        if (tool == null || !tool.beginTool(extras))
            return;
        if (subTool)
            activeSubTool = tool.getIdentifier();
        else
            activeTool = tool.getIdentifier();

        final Bundle extrasCpy;
        if (extras != null)
            extrasCpy = new Bundle(extras);
        else
            extrasCpy = new Bundle();

        List<ToolListener> listeners;
        synchronized (this) {
            listeners = new ArrayList<>(this.listeners);
        }
        for (ToolListener l : listeners)
            l.onToolBegin(tool, extrasCpy);
    }

    private void endTool(Tool tool, boolean subTool) {
        if (subTool)
            activeSubTool = null;
        else
            activeTool = null;
        if (tool != null) {
            tool.endTool();
            List<ToolListener> listeners;
            synchronized (this) {
                listeners = new ArrayList<>(this.listeners);
            }
            for (ToolListener l : listeners)
                l.onToolEnded(tool);
        }
    }

    public void dispose() {

        // stop any active tools first
        endTool();

        String s = "";
        try {
            for (Tool t : tools.values()) {
                s = t.toString();
                t.dispose();

            }
        } catch (Exception e) {
            Log.d(TAG,
                    "concurrent modification error, discard and continue: potential problem with: "
                            + s);
        }
        tools.clear();
        _instance = null;

    }

    private boolean intentSelectsTool(Intent intent, String tool) {
        boolean retu = false;
        if (intent.getExtras() != null) {
            Object dat = intent.getExtras().get("tool");
            if (dat instanceof String) {
                String string = intent.getExtras().getString("tool");
                if (string != null && string.equals(tool)) {
                    retu = true;
                } else if (tool != null
                        && string != null
                        && string
                                .equals("com.atakmap.android.toolbars.BloodHoundButtonTool")
                        && _bloodhoundActive) {
                    retu = true; // This is a hack job, but it's the best I got with such short
                                 // time. A complete redesign of the bloodhound tool is necessary.
                                 // AS.
                    activeTool = "com.atakmap.android.toolbars.BloodHoundButtonTool";
                    _bloodhoundActive = false;
                }
            } else if (dat instanceof String[]) {
                String[] strArray = intent.getExtras().getStringArray("tool");
                if (strArray != null
                        && Arrays.asList(strArray).contains(tool)) {
                    retu = true;
                }
            }
        }
        return retu;
    }

    public Tool getActiveTool() {
        if (activeTool == null)
            return null;
        else
            return tools.get(activeTool);
    }

    private Tool getActiveSubTool() {
        if (activeSubTool == null)
            return null;
        else
            return tools.get(activeSubTool);
    }

    public synchronized void registerListener(ToolListener l) {
        this.listeners.add(l);
    }

    public synchronized void unregisterListener(ToolListener l) {
        this.listeners.remove(l);
    }

    public static final String BEGIN_TOOL = "com.atakmap.android.maps.toolbar.BEGIN_TOOL";
    public static final String BEGIN_SUB_TOOL = "com.atakmap.android.maps.toolbar.BEGIN_SUB_TOOL";
    public static final String END_TOOL = "com.atakmap.android.maps.toolbar.END_TOOL";
    public static final String INVOKE_METHOD_TOOL = "com.atakmap.android.maps.toolbar.INVOKE_METHOD_TOOL";

    private final HashMap<String, Tool> tools = new HashMap<>();
    private final Set<ToolListener> listeners = new HashSet<>();

    private String activeTool = null;
    private String activeSubTool = null;

    private boolean _bloodhoundActive = false;

    private static ToolManagerBroadcastReceiver _instance = null;

}
