#!/bin/bash

##
## The Daily Runner
##
## Prepares and then fetches files from exit
##


## Configuration variables
LOGDIR=logs/
TEMPDIR=temp/

check_dirs () {
    if [ \! -d $LOGDIR ]; then
	echo "Cannot find log directory: $LOGDIR"
	exit 1
    fi
    if [ \! -d $TEMPDIR ]; then
        echo "Cannot find temp directory: $TEMPDIR"
        exit 1
    fi
}


## Main execution begins here
set -e                          # fail on ANY error

check_dirs

DATESTAMP=`date -u +%Y%m%d`	# in UTC
echo "date stamp is $DATESTAMP (UTC)"

echo "Fetching files through exits"
LOGFILE=$LOGDIR"fetchlog-"$DATESTAMP"-"$HOSTNAME".log"

while true
do
  ./bin/exitmap -d 5 patchingCheck -o $LOGFILE &

  sleep 3m

  if cat $LOGFILE | grep "PID"; then
    break
  fi

  echo "Killing Tor client"
  kill $(ps aux | grep 'exitmap' | awk '{print $2}')

  rm -rf $LOGFILE $TEMPDIR*
done

echo "Tor running successfully"

wait

echo "Compressing fetch log file"
xz $LOGFILE

exit 0
