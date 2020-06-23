package transapps.mapi;

/**
 * Simple disposable marker
 * 
 * @author mriley
 */
public interface Disposable {

    /**
     * Perform cleanup when no longer needed
     */
    void dispose();
}
