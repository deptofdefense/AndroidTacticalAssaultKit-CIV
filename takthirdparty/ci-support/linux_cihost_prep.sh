#!/bin/sh
# Script run by CI on Linux build hosts to install necessary pre-requisites.
# This is an external script (and not part of the CI directly) to allow
# other packages that run their CI using takthirdparty to install deps
# on the linux CI build host as well.
#

if [ $# -gt 0 -a "$1" = "centoscompat" ] ; then

curl -s -o /tmp/epl.rpm https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm || exit 1
rpm -i /tmp/epl.rpm || exit 1

yum -y install bzip2 || exit 1
yum -y install zip || exit 1
yum -y install dos2unix || exit 1
yum -y install autoconf || exit 1
yum -y install automake || exit 1
yum -y install gcc || exit 1
yum -y install gcc-c++ || exit 1
yum -y install libtool || exit 1
yum -y install patch || exit 1
yum -y install make || exit 1
yum -y install swig || exit 1
yum -y install tcl || exit 1
yum -y install patchelf || exit 1
yum -y install jq || exit 1
yum -y install p7zip || exit 1
( cd /bin && ln -s 7za 7z ) || exit 1
yum -y install git || exit 1
curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.rpm.sh | bash
yum -y install git-lfs || exit 1

curl -L -s -o /tmp/cmake.tar.gz https://github.com/Kitware/CMake/releases/download/v3.22.0/cmake-3.22.0-linux-x86_64.tar.gz  || exit 1
( cd /tmp && tar xfz cmake.tar.gz ) || exit 1
( cd /bin && ln -s /tmp/cmake-3.22.0-linux-x86_64/bin/cmake ) || exit 1

curl -L -s -o /tmp/ant.tar.gz https://dlcdn.apache.org/ant/binaries/apache-ant-1.10.12-bin.tar.gz || exit 1
( cd /tmp && tar xfz ant.tar.gz ) || exit 1
( cd /bin && ln -s /tmp/apache-ant-1.10.12/bin/ant ) || exit 1

else

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

fi
