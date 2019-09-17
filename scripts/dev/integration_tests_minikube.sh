#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

./scripts/dev/start_minikube.sh

echo "** Preparation: Build All **"
./scripts/dev/build_all.sh

echo "** Stage 0: Docker Images **"
./scripts/dev/create_docker_images_all_minikube.sh

echo "** Stage 1 Executor Integration Tests **"

# Unset SKUBER_URL (won't harm if not set)
# The kubernetes client should now find Minkube via ~/.kube/config
unset SKUBER_URL

set +e

sbt executorKubernetes/it:test
result1=$?

sbt executorDocker/it:test
result2=$?

echo "** Stage 2 Planner Integration Tests **"
sbt planner/it:test
result3=$?

echo "** Stage 3 Engine Integration Tests **"
sbt engine/it:test
result4=$?

./scripts/dev/run_python_integration_test.sh
result5=$?

! (( $result1 || $result2 || $result3 || $result4 || $result5 ))
