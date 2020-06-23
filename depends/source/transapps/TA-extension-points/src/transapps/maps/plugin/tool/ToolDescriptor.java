package transapps.maps.plugin.tool;

import transapps.maps.plugin.Describable;


/**
 * Represents a tool item within a list of tools.  This
 * is just enough info to display the tool option in a
 * list
 * 
 * @author mriley
 */
public interface ToolDescriptor extends Describable {
    
    /**
     * @return the short name for this tool.
     * return getDescription() if unsure
     */
    String getShortDescription();
    
    /**
     * @return The groups that this tool should be associated
     * with so I know where to put it and what to pass it
     */
    Group[] getGroups();

    /**
     * @return The tool
     */
    Tool getTool();
}
