
package plugins.core.model;

final public class PluginDescriptor {

    private final String pkgName;
    private final String pluginVersion;

    public PluginDescriptor(String pkgName, String pluginVersion) {
        this.pkgName = pkgName;
        this.pluginVersion = pluginVersion;
    }

    public String getPackageName() {
        return pkgName;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

}
