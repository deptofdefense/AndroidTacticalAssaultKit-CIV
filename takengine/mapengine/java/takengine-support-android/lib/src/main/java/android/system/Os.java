package android.system;

import java.io.File;
import java.sql.Struct;

public class Os {
    public static StructStat stat(String absolutePath) throws ErrnoException {
        final File f = new File(absolutePath);
        if(!f.exists())
            return null;
        return new StructStat();
    }
}
