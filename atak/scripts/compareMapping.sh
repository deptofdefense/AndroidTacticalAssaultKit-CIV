#!/bin/bash

####################################################################################
# Used to compare a mapping between two versions to see if there is a difference.
# 8/20/2018 - Original Version
# 7/17/2020 - New version with refined matching for    
#             number:number: then important stuff instead of .*:
####################################################################################

if [ ! -f "$1" ] || [ ! -f "$2" ];
then
    echo "./compareMapping [firstMappingFile] [secondMappingFile]"
    exit 1
fi
TMPA=`mktemp`
TMPB=`mktemp`

sed "s/.*:[0-9]*://" $1 > $TMPA
sed "s/.*:[0-9]*://" $2 > $TMPB

diff $TMPA $TMPB
rm $TMPA $TMPB

