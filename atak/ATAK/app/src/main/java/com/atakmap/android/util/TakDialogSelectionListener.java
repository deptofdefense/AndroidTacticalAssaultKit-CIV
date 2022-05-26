
package com.atakmap.android.util;

import android.app.Dialog;

import java.util.List;

public interface TakDialogSelectionListener {
    void dialogCompletedWithResult(Dialog dialog, List<Integer> results);
}
