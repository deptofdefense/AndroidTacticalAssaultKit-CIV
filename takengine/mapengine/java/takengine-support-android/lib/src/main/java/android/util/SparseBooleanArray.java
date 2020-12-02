package android.util;

import java.util.Map;
import java.util.LinkedHashMap;

public class SparseBooleanArray {
    final Map<Integer, Boolean> impl = new LinkedHashMap<>();

    public void append(int key, boolean value) {
        impl.put(key, value);
    }

    public int size() {
        return impl.size();
    }

    public int keyAt(int idx) {
        if(idx < 0 || idx >= impl.size())
            throw new ArrayIndexOutOfBoundsException();
        for(Integer v : impl.keySet()) {
            if(idx == 0)
                return v.intValue();
            idx--;
        }
        throw new IllegalStateException();
    }
}
