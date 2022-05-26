package gov.tak.api.util;

public interface Visitor<T> {
    void visit(T object);
}
