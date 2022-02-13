package gov.tak.platform.marshal;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import gov.tak.api.marshal.IMarshal;

public final class CachingMarshal<InType, OutType> implements IMarshal {

    private final Map<InType, WeakReference<OutType>> _cache;
    private final IMarshal _impl;

    public CachingMarshal(IMarshal impl) {
        this(impl, new WeakHashMap<>());
    }

    public CachingMarshal(IMarshal impl, Map<InType, WeakReference<OutType>> cache) {
        _impl = impl;
        _cache = cache;
    }

    @Override
    public <T, V> T marshal(V in) {
        if(in == null)  return null;
        synchronized(_cache) {
            do {
                final WeakReference ref = _cache.get(in);
                if (ref == null)    break;
                final T value = (T)ref.get();
                if(value == null)   break;

                return value;
            } while (false);
            final T marshaled = _impl.marshal(in);
            _cache.put((InType)in, new WeakReference<>((OutType)marshaled));
            return marshaled;
        }
    }
}
