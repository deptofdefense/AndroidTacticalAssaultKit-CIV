#ifndef TAK_ENGINE_FORMATS_CONS_H_INCLUDED
#define TAK_ENGINE_FORMATS_CONS_H_INCLUDED
/*
   cons.h: Header file for cons.c.
   SLAC Version 1.2

   02-96: JAB
   10-99: JAB  Correction _CONSTS_ to _CONS_
*/

/*
   Define constants
*/

/*
   Define angular conversion constants.
*/

   extern const double TWOPI;
   extern const double DEGRAD;
   extern const double RADDEG;

/*
   The following astronomical constants are referenced from
   the US Naval Observatory annual publication, "The
   Astronomical Almanac," pp. K6-7, except where noted other-
   wise.
*/

/*
   Radius of the Sun in meters.
*/

   extern const double SUNR;

/*
   Radius of the Earth in meters.
*/

   extern const double EARTHR;

/*
   Radius of the Moon in meters.
*/

   extern const double MOONR;

/*
   Length of the Astronomical Unit in meters.
*/

   extern const double AU;

/*
   Julian date of the standard epoch of J2000.0.
*/

   extern const double T0;

#endif
