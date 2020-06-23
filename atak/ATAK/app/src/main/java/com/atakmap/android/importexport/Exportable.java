
package com.atakmap.android.importexport;

/**
 * Map items, groups, overlays that can be exported implement this interface.
 */
public interface Exportable {

    /**
     * Check if this instance supports the specified target class type
     * 
     * @param target
     * @return
     */
    boolean isSupported(Class target);

    /**
     * Export to the specified target class type
     * 
     * @param target
     * @param filters    Allows <code>ExportMarshal</code> instances to filter e.g. based
     *     on geographic region or other criteria
     * @return    
     * @throws FormatNotSupportedException
     */
    Object toObjectOf(Class target, ExportFilters filters)
            throws FormatNotSupportedException;
}
