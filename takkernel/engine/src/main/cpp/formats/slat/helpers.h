#ifndef TAK_ENGINE_FORMATS_HELPERS_H_INCLUDED
#define TAK_ENGINE_FORMATS_HELPERS_H_INCLUDED

#include <math.h>

   long int juldat (short int year, short int month, short int day);

   void jdcd (double jd, short int *year, short int *month, short int *day, short int *hour, short int *minute);

   void caldat (long int jd, short int *year, short int *month, short int *day);

   short int jd2fyr (double jd, double *fyear);

   double cd2jd (short int year, short int month, short int day, short int hour, short int minute, double secnds);

   short int is_leap_year (short int year);
   
   double eval_poly (double a[], short int degree, double x);

   double gmst(double jd);

   void sunpos(double jd, int apparent, double *ra, double *dec, double *rv, double *slong);

   bool TmFromOleDate(double dtSrc, struct tm& tmDest);

   double create_DATE(unsigned short wYear, unsigned short wMonth, unsigned short wDay, unsigned short wHour, unsigned short wMinute, unsigned short wSecond);


#endif
