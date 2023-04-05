package gov.takx.api.persist;

/**
 * Implemented by entity classes to support persistence.
 *
 * @since 0.55.0
 */
public interface IPersistable {
    /**
     * @return The unique ID of the IPersistable instance
     */
    Integer getId();
}
