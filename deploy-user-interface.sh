# Script for building the client-side user interface files,
# and deploying them to a local instance of LaBB-CAT,
# which is install here:
LOCAL_LABBCAT=/var/lib/tomcat9/webapps/labbcat

mvn package -pl :nzilbb.labbcat.user-interface
rm -r $LOCAL_LABBCAT/user-interface/*
cp -r user-interface/target/labbcat-view/* $LOCAL_LABBCAT/user-interface/
rm -r $LOCAL_LABBCAT/edit/user-interface/*
cp -r user-interface/target/labbcat-edit/* $LOCAL_LABBCAT/edit/user-interface/
rm -r $LOCAL_LABBCAT/admin/user-interface/*
cp -r user-interface/target/labbcat-admin/* $LOCAL_LABBCAT/admin/user-interface/
