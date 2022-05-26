
package gov.tak.platform.widget;

import android.content.Context;
import android.util.AttributeSet;

public class LinearLayout extends android.widget.LinearLayout {
   public LinearLayout(Context context) { super(context); }

   public static class LayoutParams extends android.widget.LinearLayout.LayoutParams {
      public LayoutParams(Context context, AttributeSet attrs) { super(context, attrs); }
   }
}
