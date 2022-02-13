package gov.tak.api.marshal;

public interface IMarshalService {
    void registerMarshal(IMarshal marshal, Class<?> inType, Class<?> outType);
    void unregisterMarshal(IMarshal marshal);
    <T, V> T marshal(V in, Class<V> inType, Class<T> outType);
}
