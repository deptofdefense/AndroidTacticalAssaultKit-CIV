
package gov.tak.platform.graphics;

public class PointF {
   public float  x;
   public float  y;

   public PointF() {}

   public PointF(float  x, float  y) {
      this.x = x;
      this.y = y;
   }

   public PointF(Point p) {
      this.x = p.x;
      this.y = p.y;
   }
}
