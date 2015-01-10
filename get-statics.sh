#!/bin/bash

# create folders
cd resources/public
mkdir static
cd static
mkdir bootstrap-3.2.0
mkdir bootstrap-material

# bootstrap
wget https://github.com/twbs/bootstrap/releases/download/v3.2.0/bootstrap-3.2.0-dist.zip -O bootstrap.zip
unzip bootstrap.zip -d bootstrap-3.2.0
mv bootstrap-3.2.0/dist/* bootstrap-3.2.0
rm -r bootstrap-3.2.0/dist
rm bootstrap.zip


# bootstrap material
git clone https://github.com/FezVrasta/bootstrap-material-design.git
mv bootstrap-material-design/dist/* bootstrap-material
rm -r bootstrap-material-design

# react
wget https://fb.me/react-0.12.2.js

# jquery
wget https://code.jquery.com/jquery-2.1.3.min.js
