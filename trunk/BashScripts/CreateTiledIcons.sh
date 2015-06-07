#!/bin/bash

# Create logo for ChBoSync by repeating Funambol's original logo (tiles).
#
# Docu on ImageMagick's tool "montage": http://www.imagemagick.org/Usage/montage/
#
#
# The following tools from ImageMagick are used: convert, composite, montage, identify.
#
# This script was developed for the Android app "ChBoSync" (SyncML client).
#
# !!! This script is distributed WITHOUT ANY WARRANTY !!!
#
# June 2015

export PATH=$PATH:/usr/bin


INPUT_LOGO_FILE=input_logo_47x47.png

LOGO_CORRECTED=logo_48x48.png

BACKGROUND_BITMAP_MDPI=EmptyBackground_MDPI_48x48.png     # 48x48 (  baseline)

TARGET_FILE_XHDPI=montage_XHDPI_96x96.png                 # 96x96    (baseline*2)

TARGET_FILE_XXHDPI=montage_XXHDPI_144x144.png           # 144x144  (baseline*3)

TARGET_FILE_XXXHDPI=montage_XXXHDPI_192_192.png           # 192x192  (baseline*4)




# Step 1: Correct original logo of size 47x47 to 48x48 by adding transparent pixels.
# (using -geometry 48x48+1+1 would not work, because this would add 1 pixel to each
#  side of the logo BEFORE tiling, so the resulting image would have a size of
#  98x98 instead of 96x96).

/usr/bin/convert -size 48x48   xc:"rgba(0,0,0,0)"  $BACKGROUND_BITMAP_MDPI

/usr/bin/composite -gravity center $INPUT_LOGO_FILE  $BACKGROUND_BITMAP_MDPI  $LOGO_CORRECTED



# Step 2: Actual creation of tiled images

# Four tiles for XHDPI (96x96)
/usr/bin/montage $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI \
                 $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED \
                 -tile 2x2 \
                 -geometry 48x48+0+0 \
                 -background "rgba(0,0,0,0)" \
                 $TARGET_FILE_XHDPI
                 
                                  
# Nine tiles for XXHDPI (144x144): five times the logo in form of an "x"
/usr/bin/montage $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED \
                 $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI \
                 $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED \
                 -tile 3x3 \
                 -geometry 48x48+0+0 \
                 -background "rgba(0,0,0,0)" \
                 $TARGET_FILE_XXHDPI
                 
                 
# Sixteen tiles for XXXHDPI (192x192), "checkerboard pattern"
/usr/bin/montage $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED $BACKGROUND_BITMAP_MDPI \
                 $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED \
                 $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED $BACKGROUND_BITMAP_MDPI \
                 $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED         $BACKGROUND_BITMAP_MDPI $LOGO_CORRECTED \
                 -tile 4x4 \
                 -geometry 48x48+0+0 \
                 -background "rgba(0,0,0,0)" \
                 $TARGET_FILE_XXXHDPI
                 
                 
                 
# Step 3: Write some details of each created target file to console (e.g. to check that file is not empty)
echo -e "\n"
for TARGET_FILE in montage_*.png
do
  echo -e "\nFile was created: "$(identify $TARGET_FILE | cut -d " " -f 1,3,7)
done


# Step 4: Wait for user interaction to close the window
echo -e "\n\n\nPress on ENTER key to close this program ...\n"
read DUMMY_VAR

