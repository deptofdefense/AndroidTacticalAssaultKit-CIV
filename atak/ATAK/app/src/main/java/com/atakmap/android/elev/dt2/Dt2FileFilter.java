
package com.atakmap.android.elev.dt2;

import java.io.File;
import java.io.FileFilter;

public class Dt2FileFilter implements FileFilter {

    private final int _level;

    public Dt2FileFilter(int level) {
        _level = level;
    }

    @Override
    public boolean accept(File f) {
        if (f.isDirectory())
            return true;
        String name = f.getName();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx == -1 || dotIdx != name.length() - 4)
            return false;
        char c1 = name.charAt(dotIdx + 1);
        char c2 = name.charAt(dotIdx + 2);
        char c3 = name.charAt(dotIdx + 3);
        return (_level == -1 && c3 >= '0' && c3 <= '3' || c3 == _level + '0')
                && (c1 == 'd' && c2 == 't' || c1 == 'D' && c2 == 'T');
    }
}
