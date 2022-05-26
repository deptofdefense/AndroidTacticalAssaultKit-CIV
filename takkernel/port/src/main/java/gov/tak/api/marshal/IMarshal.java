package gov.tak.api.marshal;

public interface IMarshal {
    <T, V> T marshal(V in);
}
