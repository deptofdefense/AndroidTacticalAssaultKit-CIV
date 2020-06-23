package transapps.maps.plugin;

/**
 * This object provides information about the underlining application suite/product that the plugin
 * is running inside. This information can be used to adjust what code is run to avoid using code
 * which is not available for a specific application suite.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public final class SDK
{
    /**
     * This specifies the product/application suite that the plugin is being used inside. Possible
     * values include, but are not limited to "NettWarrior" and "ATAK".
     */
    public static final String PRODUCT = "NettWarrior";

    /**
     * This is the unique product identifier for Nett Warrior
     */
    public static final long NETT_WARRIOR_PRODUCT_ID = 1;

    /**
     * This is the unique product identifier for ATAK
     */
    public static final long ATAK_PRODUCT_ID = 2;

    /**
     * This is a specifies the product/application suite unique identifier.<br/>
     * Know values include:<br/>
     * 0 = Undefined<br/>
     * 1 = Nett Warrior<br/>
     * 2 = ATAK<br/>
     */
    public static final long PRODUCT_ID = NETT_WARRIOR_PRODUCT_ID;

    /**
     * This provides the major version number of the SDK. If the version string is "1.2.3" then 1
     * would be the major version number
     */
    public static final int VERSION_MAJOR_NUMBER = 1;

    /**
     * This provides the minor version number of the SDK. If the version string is "1.2.3" then 2
     * would be the minor version number
     */
    public static final int VERSION_MINOR_NUMBER = 1;

    /**
     * This provides the release version number of the SDK. If the version string is "1.2.3" then 3
     * would be the release version number
     */
    public static final int VERSION_RELEASE_NUMBER = 14;

    /**
     * This provides the internal development version number of the SDK. If the version string is "1.2.3.4" then 4
     * would be the internal development version number
     */
    public static final int VERSION_DEVELOPMENT_NUMBER = 57;


    /**
     * This provides a string representation of the version of the SDK. This value should not be
     * parsed to find the version number since it is not guaranteed to be in a specific format
     * between products. Instead the {@link SDK#VERSION_MAJOR_NUMBER},
     * {@link SDK#VERSION_MINOR_NUMBER} and {@link SDK#VERSION_RELEASE_NUMBER} values should be used
     */
    public static final String VERSION_STRING = VERSION_MAJOR_NUMBER + "." + VERSION_MINOR_NUMBER
        + "." + VERSION_RELEASE_NUMBER + "." + VERSION_DEVELOPMENT_NUMBER;

    private SDK( )
    {
    }
}
