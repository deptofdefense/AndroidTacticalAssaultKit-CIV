#!/bin/sh
# Script run by CI on Linux build hosts to install necessary pre-requisites.
# This is an external script (and not part of the CI directly) to allow
# other packages that run their CI using takthirdparty to install deps
# on the linux CI build host as well.
#

rm -rf /var/lib/apt/lists/* || exit 1
apt-get update || exit 1
apt-cache gencaches || exit 1
apt-get install -y zip || exit 1
apt-get install -y apg || exit 1
apt-get install -y dos2unix || exit 1
apt-get install -y autoconf || exit 1
apt-get install -y automake || exit 1
apt-get install -y g++ || exit 1
apt-get install -y libtool || exit 1
apt-get install -y patch || exit 1
apt-get install -y make || exit 1
apt-get install -y cmake || exit 1
apt-get install -y swig || exit 1
apt-get install -y tclsh || exit 1
apt-get install -y ant || exit 1
apt-get install -y patchelf || exit 1
apt-get install -y jq || exit 1
apt-get install -y p7zip-full || exit 1
curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | bash
apt-get install git-lfs || exit 1
