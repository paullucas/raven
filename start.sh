#!/usr/bin/env bash
if [ ! -f "./synthDefs.scd" ]; then
    touch synthDefs.scd
fi
if [ ! -d "./synths" ]; then
    mkdir synths
fi
lumo=$(which lumo)
$lumo --init rvn.cljs --quiet --repl 
