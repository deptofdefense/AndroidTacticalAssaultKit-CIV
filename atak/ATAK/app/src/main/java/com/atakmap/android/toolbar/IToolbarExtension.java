
package com.atakmap.android.toolbar;

import com.atakmap.android.tools.ActionBarView;
import com.atakmap.annotations.DeprecatedApi;

import java.util.List;

public interface IToolbarExtension {

    /***
     * Implement this method to return a set of tools to be managed by ToolbarLibrary.
     * 
     * @return A list of tools
     * @deprecated Use getToolbarView() instead.
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    List<Tool> getTools();

    /***
     * Implement this method to return the view that should be placed in the toolbar drawer when
     * your component has control of it.
     * 
     * @return
     */
    ActionBarView getToolbarView();

    /***
     * Implement this method to tell ToolbarLibrary whether your component will sometimes take
     * control of the toolbar's contents or whether it only implements tools without implementing a
     * toolbar.
     * 
     * @return
     */
    boolean hasToolbar();

    void onToolbarVisible(final boolean vis);
}
