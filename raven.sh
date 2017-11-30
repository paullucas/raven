#!/bin/sh

set -e

[ -n "$1" ] && PORT=$1 || PORT=5044

lumo --auto-cache \
     --classpath src \
     --socket-repl $PORT \
     --eval "$(printf '%s\n' "(require 'raven.core)" "(ns raven.core)" "(init)")" \
     --quiet \
     --repl
