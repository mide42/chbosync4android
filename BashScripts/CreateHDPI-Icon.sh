#!/bin/bash

# Bash script using tools from ImageMagick to produce app icon of size 72x72 (HDPI)
# by centering the MDPI icon over a white background. The reason for this is
# that the source code of the Funambol app comes only with a app of size
# 47x47 (MDPI icon should have size of 48x48).
# The "factor" between MDPI and HDPI resolution is 1.5, see also figure 4 on
# http://developer.android.com/guide/practices/screens_support.html#DesigningResources
#
# Icon file "ic_launcher.png" created by ADT wizard in folder "drawable_hdpi"
# has resolution 72x72 pixels.
#
# This script is distributed WITHOUT ANY WARRANTY!
#
# May 2015


export PATH=$PATH:/usr/bin


# Define variables with the names of the relevant files
MDPI_LOGO_FILE=1_logo_47x47.png

BACKGROUND_BITMAP=2_EmptyWhite_72x72.png

TARGET_FILE=3_logo_72x72.png


# Create white bitmap of target size as "background"
/usr/bin/convert -size 72x72 xc:white $BACKGROUND_BITMAP


# Step 2: Insert small logo on backgroud bitmap
composite -gravity center $MDPI_LOGO_FILE $BACKGROUND_BITMAP  $TARGET_FILE

echo -e "\nFile was created: "$(du -sh $TARGET_FILE)


echo -e "\n\nPress on ENTER key ...\n"
read DUMMY_VAR
