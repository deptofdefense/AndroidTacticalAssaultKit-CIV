package transapps.maps.plugin;


/**
 * A few constants and utils for the genaral intent stuff used by the extensions.
 * 
 * @author mriley
 */
public final class Intents {

    /**
     * Action used by external systems to start a tool within maps.  Maps will then find the
     * first suitable tool for the intent using isToolFor(intent)
     */
    public static final String ACTION_SHOW_TOOL = "transapps.maps.plugin.ACTION_SHOW_TOOL";

    /**
     * Extra key used to identify the group of tools that should be tested.  The value
     * should be the index of one of the enum values in Group
     */
    public static final String EXTRA_TOOL_GROUP = "transapps.maps.plugin.EXTRA_TOOL_GROUP";


    private Intents() {}
}
