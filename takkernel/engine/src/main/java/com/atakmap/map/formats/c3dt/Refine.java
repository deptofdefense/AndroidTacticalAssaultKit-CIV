package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.locale.LocaleUtil;

enum Refine {
    Add,
    Replace;

    public static Refine parse(String value) {
        if(value == null)
            return null;
        value = value.toUpperCase(LocaleUtil.getCurrent());
        if(value.equals("ADD"))
            return Add;
        else if(value.equals("REPLACE"))
            return Replace;
        else
            throw new IllegalArgumentException();
    }

}
