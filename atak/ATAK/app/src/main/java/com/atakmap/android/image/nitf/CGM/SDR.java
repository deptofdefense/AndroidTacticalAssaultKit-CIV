
package com.atakmap.android.image.nitf.CGM;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class SDR {
    enum DataType {
        SDR(1),
        CI(2),
        CD(3),
        N(4),
        E(5),
        I(6),
        RESERVED(7),
        IF8(8),
        IF16(9),
        IF32(10),
        IX(11),
        R(12),
        S(13),
        SF(14),
        VC(15),
        VDC(16),
        CCO(17),
        UI8(18),
        UI32(19),
        BS(20),
        CL(21),
        UI16(22);

        private final int index;

        DataType(int index) {
            this.index = index;
        }

        static DataType get(int index) {
            for (DataType type : values()) {
                if (type.index == index)
                    return type;
            }
            throw new IllegalArgumentException("unknown index " + index);
        }
    }

    static class Entry {
        private final DataType type;
        private final int count;
        private final List<Object> data;

        Entry(DataType type, int count, List<Object> data) {
            this.type = type;
            this.count = count;
            this.data = data;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[type=");
            builder.append(this.type);
            builder.append(", count=");
            builder.append(this.count);
            builder.append(", data=");
            builder.append(this.data);
            builder.append("]");
            return builder.toString();
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    void add(DataType type, int count, List<Object> data) {
        this.entries.add(new Entry(type, count, data));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("StructuredDataRecord [members=");
        builder.append(this.entries);
        builder.append("]");
        return builder.toString();
    }

}
