
package com.atakmap.android.icons;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Icon2525cTypeResolverTest {

    @Test
    public void simpleTest() {

        // see https://spatialillusions.com for the explanation of SIDC
        // also please see tak-assets/mil-std-2525c
        // and tak-assets/tools/assetbuilder/2525/
        // along with the human name tree in the ATAK tree
        // ATAK/app/src/main/assets/symbols.dat

        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-f"),
                "sf-------------");
        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-u"),
                "su-------------");
        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-n"),
                "sn-------------");
        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-h"),
                "sh-------------");

        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-f-A"),
                "sfap-----------");
        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-u-A"),
                "suap-----------");
        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-n-A"),
                "snap-----------");
        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-h-A"),
                "shap-----------");

        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a-f-G-i"),
                "sfgpi-----h----");

        // oddball not standard but will work
        assertEquals(Icon2525cTypeResolver.mil2525cFromCotType("a,h"),
                "sh-------------");

    }

}
