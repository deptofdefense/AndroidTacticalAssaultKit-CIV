package gov.tak.platform.marshal;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import gov.tak.api.marshal.IMarshal;
import gov.tak.api.marshal.IMarshalService;

public final class MarshalManager {
    final static IMarshalService impl = new MarshalService();
    static {
        PlatformMarshals.registerAll(impl);
    }

    public static void registerMarshal(IMarshal marshal, Class<?> inType, Class<?> outType) {
        impl.registerMarshal(marshal, inType, outType);
    }

    public static void unregisterMarshal(IMarshal marshal) {
        impl.unregisterMarshal(marshal);
    }

    public static <T, V> T marshal(V in, Class<V> inType, Class<T> outType) {
        return impl.marshal(in, inType, outType);
    }

    public static IMarshalService service() {
        return impl;
    }
}
