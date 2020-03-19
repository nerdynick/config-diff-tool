#!/bin/sh
set -o nounset \
    -o errexit \
    +o xtrace

#Load Common Functions    
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION="0.0.1"
JAR="config-diff-tool-$VERSION.jar"
POSSIBLE_PATHS=("$DIR/$JAR" "$DIR/../../../target/$JAR" "/usr/share/java/$JAR")
PATH=""

#Install Required Connectors
for i in "${POSSIBLE_PATHS[@]}"
do
    if [ -f "$i" ]; then
        PATH=$i
    fi
done

if [ -z "$PATH" ]; then 
    echo "Failed to find Jar"; 
    exit 1
fi

# Which java to use
if [ -z "$JAVA_HOME" ]; then
  JAVA="java"
else
  JAVA="$JAVA_HOME/bin/java"
fi

exec $JAVA -jar $PATH "$@"