package com.atakmap.commoncommo;

/**
 * A bundle of information updating status of an ongoing cloud IO operation.
 * See the CloudIO interface and CloudClient.
 * 
 * The info in the superclass applies here-in as well, of course.
 * Particularly, the notion of progress updates and final status delivery
 * carries through here as well.  See in particular the documentation of super.status!
 * 
 * This extension is to provide the type of transfer, as well as additional
 * information on some cloud operations, namely
 * getEntries() for LIST operations.
 * 
 * Superclass's additionalInfo will be filled with the remote server version string
 * on a TEST_SERVER operation's successful completion (if known).
 * 
 * For get operations that fail, it is possibly that the local file was
 * partially written and the client may wish to clean up the file.
 */
public class CloudIOUpdate extends SimpleFileIOUpdate {
    /**
     * The type of operation that this is updating status on.
     * This is here for convenience; it will always match the 
     * original type of transfer (identified by transferId).
     */
    public final CloudIOOperation operation;
    
    /**
     * Entries; only valid for operation == LIST_COLLECTION
     * on successful completion.
     * null or non-sense otherwise.
     */
    private final CloudCollectionEntry[] entries;
    

    CloudIOUpdate(CloudIOOperation op, 
                  int transferId, SimpleFileIOStatus status,
                  String info,
                  long bytesTransferred,
                  long totalBytes,
                  CloudCollectionEntry[] entries)
    {
        super(transferId, status, info, bytesTransferred, totalBytes);
        this.operation = op;
        this.entries = entries; 
    }
    
    /**
     * Get the entries of a collection listing.  The returned value
     * will only make sense for update events that indicate successful
     * completion status *and* are for a LIST_COLLECTION operation.
     * null or a nonsense value may be returned otherwise.
     * @return entries that are result of the list collection operation
     */
    public CloudCollectionEntry[] getEntries() {
        if (entries == null)
            return null;
        CloudCollectionEntry[] ret = new CloudCollectionEntry[entries.length];
        System.arraycopy(entries, 0, ret, 0, entries.length);
        return ret;
    }
    
}
