R"(
const float radiusEquator = 6378137.0;
const float radiusPolar = 6356752.3142;
vec3 lla2ecef(in vec3 llh) {
    float flattening = (radiusEquator - radiusPolar)/radiusEquator;
    float eccentricitySquared = 2.0 * flattening - flattening * flattening;
    float sin_latitude = sin(radians(llh.y));
    float cos_latitude = cos(radians(llh.y));
    float sin_longitude = sin(radians(llh.x));
    float cos_longitude = cos(radians(llh.x));
    float N = radiusEquator / sqrt(1.0 - eccentricitySquared * sin_latitude * sin_latitude);
    float x = (N + llh.z) * cos_latitude * cos_longitude;
    float y = (N + llh.z) * cos_latitude * sin_longitude; 
    float z = (N * (1.0 - eccentricitySquared) + llh.z) * sin_latitude;
    return vec3(x, y, z);
}
)"