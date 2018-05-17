#!/bin/sh
if [ "$#" -ne 1 ]; then
  echo "Expected exactly one argument: the total number of workers"
  exit 1
fi

WORKER_TOTAL=$1
JOBS=jobs

rm -rf $JOBS
mkdir $JOBS
for ((i=0;i<WORKER_TOTAL;i++)); do
  sed "s/%WORKER_ID%/$i/g; s/%WORKER_TOTAL%/$WORKER_TOTAL/g;" worker.yaml > $JOBS/worker-$i.yaml
done
