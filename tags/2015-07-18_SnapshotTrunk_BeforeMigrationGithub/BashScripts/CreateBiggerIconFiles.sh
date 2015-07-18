#!/bin/bash

# Bash script using tools from ImageMagick to produce app icons of bigger sizes
# by centering the original icon file over a empty background. 
# The reason for this is that the source code of the Funambol app comes only
# with an icon of size 47x47 (MDPI icon should have size of 48x48).
#
# Icon sizes for Android:
# * MDPI    for displays with about 160 DPI:  48x48  (baseline)
# * HDPI    for displays with about 240 DPI:  72x72  (baseline * 1.5)
# * XHDPI   for displays with about 320 DPI:  96x96  (baseline * 2.0)
# * XXHDPI  for displays with about 480 DPI: 144x144 (baseline * 3.0)
# * XXXHDPI for displays with about 640 DPI: 192x192 (baseline * 4.0)
#
# See also the following page in the official Android documentation: 
# http://developer.android.com/guide/practices/screens_support.html
#
#
# The following tools from ImageMagick are used: convert, composite, identify.
#
# This script was developed for the Android app "ChBoSync" (SyncML client).
#
# !!! This script is distributed WITHOUT ANY WARRANTY !!!
#
# May 2015


export PATH=$PATH:/usr/bin


# ### Define variables with the names of the relevant files ###

INPUT_LOGO_FILE=input_logo_47x47.png

# Intermediate files
BACKGROUND_BITMAP_HDPI=EmptyBackground_HDPI.png       # 72x72

BACKGROUND_BITMAP_XHDPI=EmptyBackground_XHDPI.png     # 96x96

BACKGROUND_BITMAP_XXHDPI=EmptyBackground_XXHDPI.png   # 144x144

BACKGROUND_BITMAP_XXXHDPI=EmptyBackground_XXXHDPI.png # 192x192


# Result files
TARGET_FILE_HDPI=TargetFile_HDPI.png         # 72x72,   copy to res\drawable-hdpi\logo.png

TARGET_FILE_XHDPI=TargetFile_XHDPI.png       # 96x96,   copy to res\drawable-xhdpi\logo.png

TARGET_FILE_XXHDPI=TargetFile_XXHDPI.png     # 144x144, copy to res\drawable-xxhdpi\logo.png

TARGET_FILE_XXXHDPI=TargetFile_XXXHDPI.png   # 192x192, copy to res\drawable-xxxhdpi\logo.png



# Step 1: Create transparent bitmaps for the different target sizes as background 
# (use xc:white for non-transparent background)

BACKGROUND_COLOR_OPTION=xc:"rgba(0,0,0,0)"

/usr/bin/convert -size 72x72   $BACKGROUND_COLOR_OPTION  $BACKGROUND_BITMAP_HDPI

/usr/bin/convert -size 96x96   $BACKGROUND_COLOR_OPTION  $BACKGROUND_BITMAP_XHDPI

/usr/bin/convert -size 144x144 $BACKGROUND_COLOR_OPTION  $BACKGROUND_BITMAP_XXHDPI

/usr/bin/convert -size 192x192 $BACKGROUND_COLOR_OPTION  $BACKGROUND_BITMAP_XXXHDPI



# Step 2: Insert small logo on backgroud bitmaps
composite -gravity center $INPUT_LOGO_FILE  $BACKGROUND_BITMAP_HDPI     $TARGET_FILE_HDPI     # 72x72

composite -gravity center $INPUT_LOGO_FILE  $BACKGROUND_BITMAP_XHDPI    $TARGET_FILE_XHDPI    # 96x96

composite -gravity center $INPUT_LOGO_FILE  $BACKGROUND_BITMAP_XXHDPI   $TARGET_FILE_XXHDPI   # 144x144

composite -gravity center $INPUT_LOGO_FILE  $BACKGROUND_BITMAP_XXXHDPI  $TARGET_FILE_XXXHDPI  # 192x192


# Step 3: Write some details of each created target file to console (e.g. to check that file is not empty)
echo -e "\n"
for TARGET_FILE in TargetFile_*DPI.png
do
  echo -e "\nFile was created: "$(identify $TARGET_FILE | cut -d " " -f 1,3,7)
done



# Step 4: Wait for user interaction to close the window
echo -e "\n\n\nPress on ENTER key to close this program ...\n"
read DUMMY_VAR
