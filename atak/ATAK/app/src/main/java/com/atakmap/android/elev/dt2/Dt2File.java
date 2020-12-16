
package com.atakmap.android.elev.dt2;

import com.atakmap.android.math.MathUtils;

import java.io.File;
import java.util.Objects;

public class Dt2File {

    public final File file;
    public final int latitude, longitude, level;

    public Dt2File(File file) {
        this.file = file;

        String name = file.getName();
        char ns = Character.toUpperCase(name.charAt(0));
        int lastIdx = name.indexOf('.');
        this.latitude = MathUtils.parseInt(name.substring(1, lastIdx), 0)
                * (ns == 'S' ? -1 : 1);

        this.level = MathUtils.parseInt(name.substring(lastIdx + 3), 0);

        File parent = file.getParentFile();
        if (parent != null) {
            name = parent.getName();
            char ew = Character.toUpperCase(name.charAt(0));
            this.longitude = MathUtils.parseInt(name.substring(1), 0)
                    * (ew == 'W' ? -1 : 1);
        } else
            this.longitude = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Dt2File dt2File = (Dt2File) o;
        return latitude == dt2File.latitude &&
                longitude == dt2File.longitude &&
                level == dt2File.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, level);
    }
}
