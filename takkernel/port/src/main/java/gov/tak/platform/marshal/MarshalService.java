package gov.tak.platform.marshal;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import gov.tak.api.marshal.IMarshal;
import gov.tak.api.marshal.IMarshalService;

public class MarshalService implements IMarshalService {
    final Map<Class<?>, Map<Class<?>, Set<IMarshal>>> registry = new HashMap<>();

    @Override
    public synchronized void registerMarshal(IMarshal marshal, Class<?> inType, Class<?> outType) {
        Map<Class<?>, Set<IMarshal>> registered = registry.get(inType);
        if(registered == null) {
            registered = new HashMap<>();
            registry.put(inType, registered);
        }
        Set<IMarshal> marshals = registered.get(outType);
        if(marshals == null) {
            marshals = new LinkedHashSet<>();
            registered.put(outType, marshals);
        }
        marshals.add(marshal);
    }

    @Override
    public synchronized void unregisterMarshal(IMarshal marshal) {
        for(Map<Class<?>, Set<IMarshal>> registered : registry.values()) {
            for(Set<IMarshal> marshals : registered.values()) {
                marshals.remove(marshal);
            }
        }
    }

    @Override
    public synchronized <T, V> T marshal(V in, Class<V> inType, Class<T> outType) {
        final Map<Class<?>, Set<IMarshal>> registered = registry.get(inType);
        if(registered == null) return null;
        final Set<IMarshal> marshals = registered.get(outType);
        if(marshals == null) return null;
        for(IMarshal marshal : marshals) {
            T obj = marshal.marshal(in);
            if(obj != null)
                return obj;
        }
        return null;
    }
}
