
package com.atakmap.android.importexport;

/**
 * Map items, groups, overlays that can be exported implement this interface.
 */
public interface Exportable {

    /**
     * Check if this instance supports the specified target class type
     * 
     * @param target the target class
     * @return true if this instance supports the target.
     */
    boolean isSupported(Class<?> target);

    /**
     * Export to the specified target class type
     * 
     * @param target the target class
     * @param filters    Allows <code>ExportMarshal</code> instances to filter e.g. based
     *     on geographic region or other criteria
     * @return the object that represents the instance based on the target and filter
     * @throws FormatNotSupportedException if there is an issue creating the object.
     */
    Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException;
}
