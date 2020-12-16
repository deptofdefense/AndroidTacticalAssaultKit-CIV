
package transapps.maps.plugin.tool;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ViewGroup;

import transapps.mapi.MapView;

public abstract class Tool implements ToolDescriptor {

    public abstract Drawable getIcon();

    @Override
    public Group[] getGroups() {
        return new Group[] {
                Group.GENERAL
        };
    }

    @Override
    public Tool getTool() {
        return this;
    };

    public abstract void onActivate(Activity arg0, MapView arg1, ViewGroup arg2,
            Bundle arg3,
            Tool.ToolCallback arg4);

    public interface ToolCallback {
        void onInvalidate(Tool tool);

        void onToolDeactivated(Tool tool);

    }

    public abstract void onDeactivate(ToolCallback arg0);

}
