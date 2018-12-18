#!/bin/bash

##
## The Daily Runner
##
## Prepares and then executes proxy classification
##
## Author: Micah Sherr <msherr@cs.georgetown.edu>
##


## Configuration variables
CODEDIR=code/
LOGDIR=logs/
JSONDIR=jsons/
DOWNLOADDIR=files/


check_dirs () {
    if [ \! -d $CODEDIR ]; then
	echo "Cannot find code directory: $CODEDIR"
	exit 1
    fi
    if [ \! -d $LOGDIR ]; then
	echo "Cannot find log directory: $LOGDIR"
	exit 1
    fi
    if [ \! -d $JSONDIR ]; then
	echo "Cannot find json directory: $JSONDIR"
	exit 1
    fi
    if [ \! -d $DOWNLOADDIR ]; then
	echo "Cannot find json directory: $DOWNLOADDIR"
	exit 1
    fi
}



update_and_compile () {
    echo "Updating and compiling"
    git pull
    OLDDIR=`pwd`
    cd $CODEDIR/src
    make
    cd $OLDDIR
}    



reboot_if_on_aws () {
    curl -s http://instance-data.ec2.internal > /dev/null && ec2=1 || ec2=0
    if [ $ec2 -eq 0 ]; then
	echo "AWS EC2 not detected."
    else
	echo "AWS EC2 detected.  Shutting down this machine in 10 seconds.  Really."
	sleep 10
	sudo shutdown -h now
    fi
}


## Main execution begins here
set -e                          # fail on ANY error

check_dirs
#update_and_compile


DATESTAMP=`date -u +%Y%m%d`	# in UTC
echo "date stamp is $DATESTAMP (UTC)"

echo "Fetching proxies"
UNCLASSIFIEDFILE=$JSONDIR"proxies-unclassified-"$DATESTAMP"-"$HOSTNAME".json.gz"
CLASSIFIEDFILE=$JSONDIR"proxies-classified-"$DATESTAMP"-"$HOSTNAME".json.gz"
LOGFILE=$LOGDIR"log-"$DATESTAMP"-"$HOSTNAME".log"
python $CODEDIR/fetcher.py --output $UNCLASSIFIEDFILE

echo "Classifying proxies"
./run-java-classify.sh -i $UNCLASSIFIEDFILE -o $CLASSIFIEDFILE -l $LOGFILE --debug

echo "Compressing classify log file"
xz $LOGFILE


echo "Fetching files through functioning proxies"
LOGFILE=$LOGDIR"fetchlog-"$DATESTAMP"-"$HOSTNAME".log"
FETCHFILE=$JSONDIR"fetched-"$DATESTAMP"-"$HOSTNAME".json.gz"

./run-java-proxyfetch.sh  -l $LOGFILE -i $CLASSIFIEDFILE -o $FETCHFILE -d $DOWNLOADDIR -H $HOSTNAME -f $CODEDIR/files_to_fetch.json --debug

echo "Compressing fetch log file"
xz $LOGFILE

# if we're on AWS, then reboot
reboot_if_on_aws

exit 0



