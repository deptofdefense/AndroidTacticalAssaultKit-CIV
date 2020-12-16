
package plugins.core.model;

final public class Plugin {

    private String name;
    private PluginDescriptor descriptor;

    public void setName(String name) {
        this.name = name;
    }

    public void setDescriptor(PluginDescriptor descriptor) {
        this.descriptor = descriptor;
    }

}
