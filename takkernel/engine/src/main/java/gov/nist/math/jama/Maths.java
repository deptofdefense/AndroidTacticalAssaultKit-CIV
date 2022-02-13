package gov.nist.math.jama;

/**
 * As Jama is in the public domain other developers are free to adopt and 
 * adapt this code to other styles of programming or to extend or modernize 
 * the API. You might find one of these libraries to be more suitable to your 
 * purposes.
 **/



public class Maths {

   /** sqrt(a^2 + b^2) without under/overflow. **/

   public static double hypot(double a, double b) {
      double r;
      if (Math.abs(a) > Math.abs(b)) {
         r = b/a;
         r = Math.abs(a)*Math.sqrt(1+r*r);
      } else if (b != 0) {
         r = a/b;
         r = Math.abs(b)*Math.sqrt(1+r*r);
      } else {
         r = 0.0;
      }
      return r;
   }
}
