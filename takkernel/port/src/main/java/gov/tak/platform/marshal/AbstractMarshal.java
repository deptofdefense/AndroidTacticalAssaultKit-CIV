package gov.tak.platform.marshal;

import gov.tak.api.marshal.IMarshal;

public abstract class AbstractMarshal implements IMarshal {
    protected final Class<?> _inType;
    protected final Class<?> _outType;

    public AbstractMarshal(Class<?> inType, Class<?> outType) {
        _inType = inType;
        _outType = outType;
    }

    @Override
    public final <T, V> T marshal(V in) {
        if(in == null)
            return null;
        else if(_outType.isAssignableFrom(in.getClass()))
            return (T)in;
        else
            return (T)marshalImpl(in);
    }

    protected abstract <T, V> T marshalImpl(V in);
}
