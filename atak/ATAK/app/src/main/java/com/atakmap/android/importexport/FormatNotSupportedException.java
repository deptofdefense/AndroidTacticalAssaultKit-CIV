
package com.atakmap.android.importexport;

/**
 * Exception class thrown when an import or export type is not supported by a class.
 */
public class FormatNotSupportedException extends Exception {

    private static final long serialVersionUID = -7686245597367564276L;

    /**
     * 
     */
    public FormatNotSupportedException() {
    }

    /**
     * @param detailMessage
     */
    public FormatNotSupportedException(String detailMessage) {
        super(detailMessage);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param throwable
     */
    public FormatNotSupportedException(Throwable throwable) {
        super(throwable);
    }

    /**
     * @param detailMessage
     * @param throwable
     */
    public FormatNotSupportedException(String detailMessage,
            Throwable throwable) {
        super(detailMessage, throwable);
    }

}
