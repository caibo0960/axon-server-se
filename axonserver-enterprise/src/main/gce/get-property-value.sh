#!/bin/bash

SHOW_USAGE=n

PROPERTIES=axonserver.properties
while [[ "${SHOW_USAGE}" == "n" && $# -gt 0 && $(expr "x$1" : x-) = 2 ]] ; do

    if [[ "$1" == "--properties" ]] ; then
        if [[ $# -gt 1 ]] ; then
            PROPERTIES=$2
            shift 2
        else
            echo "Expected name of properties file after \"--properties\"."
            SHOW_USAGE=y
        fi
    else
        echo "Unknown option \"$1\"."
        SHOW_USAGE=y
    fi

done

PROP=
if [[ $# == 1 ]] ; then
    PROP=$1
else
    echo "Expected property name."
    SHOW_USAGE=y
fi

if [[ ${SHOW_USAGE} == y ]] ; then
    echo "Usage: $0 [--properties <filename>] <property-name>"
    exit 1
fi

grep -e '^[ ]*'${PROP}'[ ]*=.*$' ${PROPERTIES} | sed -e 's/^[^=]*=[ ]*//'