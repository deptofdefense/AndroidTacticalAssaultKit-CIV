
package com.atakmap.android.maps;

/**
 * Engine resource reference that points to an entry inside a ZIP archive
 *
 */
public class ArchiveEntryMapDataRef extends MapDataRef {

    /**
     * Construct a ArchiveEntryMapDataRef with an archive file path and an entry path
     * 
     * @param archiveFilePath the path to the archive file
     * @param entryPath the path into the archive
     */
    public ArchiveEntryMapDataRef(String archiveFilePath, String entryPath) {
        _archiveFilePath = archiveFilePath;
        _entryPath = entryPath;
    }

    /**
     * Get the path to the archive file
     * 
     * @return the archive file path as a string.
     */
    public String getArchiveFilePath() {
        return _archiveFilePath;
    }

    /**
     * Get the path to the archive entry
     * 
     * @return the achive entry path as a string.
     */
    public String getEntryPath() {
        return _entryPath;
    }

    /**
     * Get a human readable representation of the ArchiveEntryMapDataRef
     */
    public String toString() {
        return "archive: " + _archiveFilePath + ":" + _entryPath;
    }

    private String _archiveFilePath;
    private String _entryPath;

    @Override
    public String toUri() {
        return "arc:" + _archiveFilePath + "!/" + _entryPath;
    }
}
