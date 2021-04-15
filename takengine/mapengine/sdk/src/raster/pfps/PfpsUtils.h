#ifndef ATAKMAP_RASTER_PFPSUTILS_H_INCLUDED
#define ATAKMAP_RASTER_PFPSUTILS_H_INCLUDED

#include "PfpsMapType.h"
#include "raster/mosaic/ATAKMosaicDatabase.h"

namespace atakmap {
    namespace raster {
        namespace pfps {

            class PfpsUtils {

            public:
                static bool isPfpsDataDir(const char *f, int limit = INT_MAX);
                static void createRpfDataDatabase(mosaic::ATAKMosaicDatabase *database, const char *d);


                static const PfpsMapType *getMapType(const char *frame);

                static double cadrgScaleToCibResolution(double scale);

                static int getRpfZone(const char *frameFileName);


                /**
                * Returns the successive version number for the frame file. See MIL-C-89038 section 30.6 /
                * MIL-C-89041 section A.3.6.
                *
                * @param type
                * @param frameFileName
                * @return
                */
                static int getRpfFrameVersion(const PfpsMapType *type, const char *frameFileName);

                /**
                * Returns the unique cumulative frame number with the frame's zone. See MIL-C-89038 section
                * 30.6 / MIL-C-89041 section A.3.6.
                *
                * @param type
                * @param frameFileName
                * @return
                */
                static int getRpfFrameNumber(const PfpsMapType *type, const char *frameFileName);

                /**
                * Returns the base-34 value corresponding to the specified character.
                *
                * @param c A character
                *
                * @return  The base-34 value. A value less than zero is returned if the
                *          character is part of the base-34 character set.
                */
                static int base34Decode(const char c);

                /**
                * Returns the base-34 value corresponding to the specified string.
                *
                * @param s A string
                *
                * @return  The base-34 value. A value less than zero is returned if any of
                *          the characters are part of the base-34 character set.
                */
                static int base34Decode(const char *s);

                /**
                * Returns the base-34 value corresponding to the specified string.
                *
                * @param s     A character array
                * @param off   The array offset
                * @param len   The number of characters in the string
                *
                * @return  The base-34 value. A value less than zero is returned if any of
                *          the characters are part of the base-34 character set.
                */
                static int base34Decode(const char *s, int len);


            };
        }
    }
}

#endif
