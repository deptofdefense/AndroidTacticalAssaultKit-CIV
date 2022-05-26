
package gov.tak.platform.graphics;

// not sure if we want to leverage android.graphics.Insets which require API 29
public class Insets {
   public int top;
   public int left;
   public int bottom;
   public int right;

   public Insets (int top, int left, int bottom, int right) {
      this.top = top;
      this.left = left;
      this.bottom = bottom;
      this.right = right;
   }
}
