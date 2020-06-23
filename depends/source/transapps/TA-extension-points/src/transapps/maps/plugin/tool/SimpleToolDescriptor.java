package transapps.maps.plugin.tool;

import android.graphics.drawable.Drawable;

/**
 * Utility implementation of the ToolDescriptor interface.
 *
 * It provides implementations of all the methods
 * except the getTool which must be implemented by the
 * Tool developer to "factory" or create the Tool.
 *
 */
public abstract class SimpleToolDescriptor implements ToolDescriptor {
    @Override
    public String getDescription() {return null;}
    
    @Override
    public String getShortDescription() {return getDescription();}
    
    @Override
    public Drawable getIcon() {return null;}
    
    @Override
    public Group[] getGroups() {return new Group[] {};}
}
