
package transapps.mapi;

import android.view.View;

import com.atakmap.annotations.DeprecatedApi;

import transapps.geom.Projection;

public interface MapView {

    View getView();

    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    Projection getProjection();

}
