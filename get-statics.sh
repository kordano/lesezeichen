#!/bin/bash

# create folders
cd resources/public
mkdir static
cd static
mkdir bootstrap-3.3.1

# bootstrap
wget https://github.com/twbs/bootstrap/releases/download/v3.3.1/bootstrap-3.3.1-dist.zip -O bootstrap.zip
unzip bootstrap.zip -d bootstrap-3.3.1
mv bootstrap-3.3.1/dist/* bootstrap-3.3.1
rm -r bootstrap-3.3.1/dist
rm bootstrap.zip

# react
wget https://fb.me/react-0.12.2.js

# jquery
wget https://code.jquery.com/jquery-2.1.3.min.js
