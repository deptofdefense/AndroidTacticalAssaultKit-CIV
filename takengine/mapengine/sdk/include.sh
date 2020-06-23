#!/bin/sh
TE_SRC_DIR=`pwd`/src
TE_BUILD_INCLUDES_DIR=`pwd`/build/include
PGSCUTILS_SRC_DIR=`pwd`/../../../pgsc-utils/src

mkdir -p $TE_BUILD_INCLUDES_DIR || exit 1
(cd $TE_SRC_DIR && find . -name '*.h' | cpio -pdm $TE_BUILD_INCLUDES_DIR/takengine) || exit 1
(cd $PGSCUTILS_SRC_DIR && find . -name '*.hh' | cpio -pdm $TE_BUILD_INCLUDES_DIR/pgscutils) || exit 1
