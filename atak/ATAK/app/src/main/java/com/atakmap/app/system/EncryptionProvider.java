
package com.atakmap.app.system;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.atakmap.annotations.DeprecatedApi;

public interface EncryptionProvider {

    @Deprecated
    @DeprecatedApi(since = "4.3.1", forRemoval = true, removeAt = "4.5.0")
    interface Callback {
        int SETUP_FAILED = 1;
        int SETUP_SUCCESSFUL = 0;

        /**
         * Called when setup is complete.
         * @param condition SETUP_SUCCESSFUL if setup of the encryption plugin was successful.
         * @param title the title of the status message in the case of a non SETUP_SUCCESSFUL call.
         * @param icon the icon for the status message in the case of a non SETUP_SUCCESSFUL call.
         * @param msg the message presented to the user in the case of a non SETUP_SUCCESSFUL call.
         */
        void complete(int condition, @NonNull String title,
                @NonNull Drawable icon, @NonNull String msg);
    }

    /**
     * Encapsulates the mechanism to setup the loaded encryption component.
     *
     * Returns true if setup was completed without the need for the callback
     * @param callback the callback to use by the system if setup has returned false.
     * @return true if the setup is completed without needing to make use of the callback or
     * false if the provider promises in all cases to call the callback.   If true is returned the
     * callback will be invalidated and any calls to it processed.   If the callback is called once,
     * it will be invalidated and any calls to it will not be processed.
     * @deprecated
     * @see AbstractSystemComponent#load(AbstractSystemComponent.Callback)
     */
    @Deprecated
    @DeprecatedApi(since = "4.3.1", forRemoval = true, removeAt = "4.5.0")
    boolean setup(Callback callback);

}
