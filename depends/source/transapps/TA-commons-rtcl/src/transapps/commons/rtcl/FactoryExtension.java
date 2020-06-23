package transapps.commons.rtcl;

import plugins.host.context.PluginContext;

/**
 * Created by fhodum on 6/13/14.
 */
public interface FactoryExtension {

    public <T> T factory(Class<T> myThing);

    public PluginContext getContext();

}
