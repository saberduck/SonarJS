#!/bin/bash

mvn package -DskipTests

GOOGLE_APPLICATION_CREDENTIALS=/c/projects/shipit/serviceaccount.json

java -cp shipit/target/shipit-bundled-4.1-SNAPSHOT.jar org.sonar.MainDataflow \
  --project=project-test-199515 \
  --stagingLocation=gs://project-test-199515/staging/ \
  --runner=DataflowRunner \
  --jobName=dataflow-intro14 \
  --inputTableSpec=project-test-199515:github_us.js_files_and_contents \
  --outputTableSpec=project-test-199515:github_us.issues_final
