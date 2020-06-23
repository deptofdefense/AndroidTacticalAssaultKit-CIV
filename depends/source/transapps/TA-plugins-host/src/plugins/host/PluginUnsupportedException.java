package plugins.host;

/**
 * This is a special exception that should be thrown from the constructor of a plugin if it is
 * detected that the plugin is currently not supported. For example this exception can be used
 * if a plugin request a specific messaging version like 6017A or CoT and the device is currently
 * not setup for that mode.<br/>
 * <br/>
 * Please note that the throwing this exception will result in the plugin's extension point not
 * being loaded/available at runtime until the extension points are loaded again which might not
 * happen until a reboot. Therefore, throwing this exception is not advised if a normal procedure
 * for making plugin/extension point supported does not require rebooting the device or at the
 * very least force closing the pluggable application.
 *
 * @author CSRA
 * @since NW SDK 1.1.6.3
 */
public class PluginUnsupportedException extends Exception
{
    /**
     * This is the base constructor for the PluginUnsupportedException
     *
     * @param message
     *             The message describing why the plugin is not supported
     */
    public PluginUnsupportedException( String message )
    {
        super( message );
    }

    /**
     * This is the main constructor for the PluginUnsupportedException
     *
     * @param message
     *             The message describing why the plugin is not supported
     * @param cause
     *             The exception that is the root cause for the plugin not being supported
     */
    public PluginUnsupportedException( String message, Throwable cause )
    {
        super( message, cause );
    }
}
