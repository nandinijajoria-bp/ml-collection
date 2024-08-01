#!/bin/bash -x
sleep 5
source ~/.bashrc
echo $JAVA_TOOL_OPTIONS
java $JAVA_TOOL_OPTIONS -Duser.timezone=IST -Dspring.profiles.active=${PROFILE} -jar /app/app.jar