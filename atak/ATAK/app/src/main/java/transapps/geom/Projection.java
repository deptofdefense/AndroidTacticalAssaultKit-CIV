
package transapps.geom;

import android.graphics.Point;

import com.atakmap.annotations.DeprecatedApi;

/**
 * Do not make use of this class.  It only exists for the purposes of getting a few legacy plugins to compile.
 */
@Deprecated
@DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
public interface Projection {

    Point toPixels(Coordinate in, Point out);

}
