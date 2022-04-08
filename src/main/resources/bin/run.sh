#!/bin/sh

SERVICE_HOME=/home/uangel/jdash
SERVICE_NAME=jdash
PATH_TO_JAR=$SERVICE_HOME/lib/JDASH.jar
JAVA_CONF=$SERVICE_HOME/config/user_conf.ini
JAVA_OPT="-Dlogback.configurationFile=$SERVICE_HOME/config/logback.xml"
JAVA_OPT="$JAVA_OPT -XX:+UseG1GC -XX:G1RSetUpdatingPauseTimePercent=5 -XX:MaxGCPauseMillis=500 -XX:+UseLargePages -verbosegc -Xms4G -Xmx4G -verbose:gc -Xms4G -Xmx4G -Xlog:gc=debug:file=$SERVICE_HOME/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=100m"

function exec_start() {
        PID=`ps -ef | grep java | grep DashServerMain | awk '{print $2}'`
        if ! [ -z "$PID" ]
        then
                echo "[$SERVICE_NAME] is already running"
        else
                #ulimit -n 65535
                #ulimit -s 65535
                #ulimit -u 10240
                #ulimit -Hn 65535
                #ulimit -Hs 65535
                #ulimit -Hu 10240

                /usr/lib/jvm/java-11/bin/java -jar $JAVA_OPT $PATH_TO_JAR DashServerMain $JAVA_CONF > /dev/null 2>&1 &
                echo "[$SERVICE_NAME] started ..."
        fi
}

function exec_stop() {
	PID=`ps -ef | grep java | grep DashServerMain | awk '{print $2}'`
	if [ -z "$PID" ]
	then
		echo "[$SERVICE_NAME] is not running"
	else
		echo "stopping [$SERVICE_NAME]"
		kill "$PID"
		sleep 1
		PID=`ps -ef | grep java | grep DashServerMain | awk '{print $2}'`
		if [ ! -z "$PID" ]
		then
			echo "kill -9 ${PID}"
			kill -9 "$PID"
		fi
		echo "[$SERVICE_NAME] stopped"
	fi
}

function exec_status() {
  PID=`ps -ef | grep java | grep DashServerMain | awk '{print $2}'`
	if [ -z "$PID" ]
	then
		echo "[$SERVICE_NAME] is not running"
	else
	  echo "[$SERVICE_NAME] is running"
	  ps -aux | grep DashServerMain | grep "$PID"
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
    status)
    exec_status
    ;;
esac
