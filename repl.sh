#!/bin/sh

set -e

[ -n "$1" ] && PORT=$1 || PORT=5044

lumo -K -c src -n $PORT
