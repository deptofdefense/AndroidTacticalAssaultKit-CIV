ATAK makes use of many thirdparty libraries.  Where possible, source for those thirdparty libraries is included in this repository. Please review the LICENSE.md file as it relates to the use and distribution of thirdparty libraries with ATAK.

## Thirdparty Source Locations

* Source files integrated into the core ATAK-CIV projects. These files will retain original attribution and license from their upstream origin within the source files.
* Projects integrated into the TAK project structure (e.g. `libstackunwind-ndk`)
* `depends/source`. This directory contains sources for thirdparty Java libraries. Unmodified sources may be included as archives; modified sources will be unarchived. These distributions will contain any license and copyrights as applicable from the upstream origin.
* `takthirdpary/distfiles`. This directory contains the sources for thirdparty binary libraries. The archives represent unmodified snapshots; modifications are applied via patch files at build time.  These distributions will contain any license and copyrights as applicable from the upstream origin.

## Thirdparty Licenses

Relevant thirdparty licenses per the distribution of the ATAK-CIV runtime application may be found under `atak/ATAK/app/src/main/assets/support`.  

## List of Thirdparty Dependencies

The table below presents all thirdparty dependencies that are included with this distribution of ATAK-CIV. Information about the copyright holder and license found in this table is INFORMATIVE. Please refer to the actual source locations and/or copyright and license texts that can be found in the project alongside the source/binaries.  


| Name | Copyright | License | Version | Additional Notes |
| ----- | ----- | ----- | ----- | ----- |
| achartengine | 4ViewSoft SRL (2009-2013) | Apache License v2.0 | 1.2.0 |  |
| ACRA |  | Apache License v2.0 | 4.6.1 |  |  |
| USB Serial for Android | Google (2011-2013)  Mike Wakerly (2013) | LGPL |  |  |
| datadroid | FoxyKeep | Beerware | 2.1.2 |  |
| jackcess | James Ahlborn (2013) | LGPL | 1.2.14.3 | The version of the source we are building from is licensed with LGPL; the current upstream has been updated to the Apache License v2.0 |	
| libunwindstack NDK | The Android Open Source Project (2016) | Apache License v2.0 |  |  |
| Gv2F | PAR Government Systems Corporation | PAR Gv2F License |  | Proprietary library. |
| PGSC Mobile Video | PAR Government Systems Corporation | PAR Gv2F License |  |  | Proprietary library. |
| opencsv |  | Apache License v2.0 |  | Included as inline source |
| OpenNMEA | Joey Gannon | LGPL | 0.1 |  |
| Sanselan |  | Apache License v2.0  |  | The version of the source we are building from is licensed with Apache License v2.0; the upstream archived version (https://code.google.com/archive/p/sanselanandroid/) is now licensed under the MIT license. |
| SimpleKML | Ekito (2012) | Apache License v2.0 |  |  |
| SimpleXML |  | Apache License v2.0 | 2.7.1 |  |
| FFMPEG |  | LGPL |  |  |  |  |  |  | A modified version of FFMPEG is included as part of the Gv2F distribution. Sources are not included, but may be requested per the PAR Gv2F License under the terms of the LGPL. |
| Apache Commons Lang |  | Apache License v2.0 |  | Included as inline source |
| GLU |  | SGI Free Software License B v2.0 | 1.2.1 | Included as inline source |
| Jama |  | Public Domain |  | Included as inline source |
| ASSIMP | assimp team | Open Asset Import Library License | 4.0.1 |  |
| GDAL | Frank Warmerdam (1998, 2002); Evan Rouault (2007-2014) | GDAL/OGR License | 2.2.3 | Modified version, see `depends/gdal-2.2.3-mod.tar.gz` |
| GEOS |  | LGPL v2.1 | 3.4.3 | takthirdparty/distfiles/geos.tar.bz2 |
| LIBICONV |  | LGPL | 1.15 |  |
| GNU libstdc++ |  | GPLv3 |  |  |
| LIBKML | Google (2008) | LIBKML license | 1.3 |  |
| uriparser | Weijia Song (2007) Sebastian Pipping (2007) | uriparser License | 0.7.5 |  |
| Boost |  | Boost Software License v1.0 | 1.34.1 |  |
| minizip | Gilles Vollant (1998-2005) | minizip license | 1.01e |  |
| OGDI |  | OGDI License | 3.2.0 |  |
| Proj.4 | Frank Warmerdam | Proj.4 License | 4.9.1 |  |
| SQLite | Disclaimed | SQLite License | 3.20.1 | License is a blessing. |
| SQL Cipher | ZETETIC LLC (2008) | SQL Cipher License | 3.4.2 |  |
| SpatiaLite | Alessandro Furieri | Mozilla Public License 1.1.1 | 4.3.0a | May be alternatively distributed under GPLv2 or later, LGPLv2.1 or later. |
| LIBEXPAT | Thai Open Source Software Center Ltd and Clark Cooper (1998-2000); Expat maintainers. (2001-2006) | LIBEXPAT License | 2.1.0 |  |
| zlib | Jean-loup Gailly and Mark Adler (1995-2017) | zlib License | 1.2.11 |  |
| GLUES | Silicon Graphics Inc (1991-200) | SGI Free Software License B v2.0 | 1.4 |  |
| STB DXT | Sean Barrett (2017) | Public Domain | 1.08b | Included as inline source. This code may optionally be licensed under the MIT license. |
| WMM |  | Public Domain |  | Included as inline source |
| protobuf | Google (2008) | Protocol Buffers License | 3.5.1 |  |
| LIBMICROHTTPD |  | LGPL | 0.9.46 |  |
| LIBCURL | Daniel Stenberg (1996-2018) | LIBCURL license | 7.61.1 |  |
| LIBXML2 | Daniel Veillard (1998-2012) | MIT License | 2.9.10 |  |
| OpenSSL | The OpenSSL Project (1998-2018) | OpenSSL License and SSLeay License | 1.1.0i | Double licensed; terms for both must be met |