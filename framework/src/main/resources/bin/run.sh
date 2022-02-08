#!/bin/sh

SERVICE_HOME=/home/jamesj/jdash
SERVICE_NAME=jdash
PATH_TO_JAR=$SERVICE_HOME/lib/jdash_server.jar
JAVA_CONF=$SERVICE_HOME/config/user_conf.ini
JAVA_OPT="-Dlogback.configurationFile=$SERVICE_HOME/config/logback.xml"
JAVA_OPT="$JAVA_OPT -XX:+UseG1GC -XX:G1RSetUpdatingPauseTimePercent=5 -XX:MaxGCPauseMillis=500 -Xlog:gc* -verbosegc"

function exec_start() {
	ulimit -n 65535
	ulimit -s 65535
	ulimit -u 10240
	ulimit -Hn 65535
	ulimit -Hs 65535
	ulimit -Hu 10240

	java -jar $JAVA_OPT $PATH_TO_JAR DashServerMain $JAVA_CONF > /dev/null 2>&1 &
	echo "$SERVICE_NAME started ..."
}

function exec_stop() {
	PID=`ps -ef | grep java | grep DashServerMain | awk '{print $2}'`
	if [ -z $PID ]
	then
		echo "DASH is not running"
	else
		echo "stopping DASH"
		kill $PID
		sleep 1
		PID=`ps -ef | grep java | grep DashServerMain | awk '{print $2}'`
		if [ ! -z $PID ]
		then
			echo "kill -9"
			kill -9 $PID
		fi
		echo "DASH stopped"
	fi
}

case $1 in
    restart)
		exec_stop
		exec_start
		;;
    start)
		exec_start
    ;;
    stop)
		exec_stop
    ;;
esac
