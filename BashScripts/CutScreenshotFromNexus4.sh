#!/bin/bash


# Bash script to crop screenshots taken on Nexus 4 with Emulator.
# The screenshots created by this script are to be used for the app store entry.
#
# Warning: Original files are replaced!
#
# Dimensions of input files (taken using screen capture feature of DDMS): 768  x 1280 
#
# Prerequiste: ImageMagick has to be installed.
# Tested with Cygwin32 on Windows 7 (ImageMagick 6.7.6-3) and on OpenSuse 13.1 (ImageMagick 6.8.6-9).
#
# PNG files with screenshots to be processed have to be in the same folder as this script.
# When using Cygwin on Windows this script can be started by double click.
#
# This script is distributed WITHOUT ANY WARRANTY!
#
# August 2014, September 2014, December 2014.

PATH=$PATH:/usr/bin

COUNTER=0


# Check if at least one png file can be found in the current folder
ls *.png > /dev/null 2> /dev/null
if [ $? -ne 0 ]
then

	echo -e "\n\nError: Could not find any png files in the current folder.\n"

else

	for IMAGE_FILE in *.png
	do

	  let COUNTER+=1

	  echo -e "\nProcessing File "${COUNTER}": "${IMAGE_FILE}
	  
	  # Crop the status bar at the top of the image (50 pixels)
	  # and the soft buttons at the bottom (100 pixels).
	  # Also draws a thin black border around the picture.
	  # Mogrify replaces the image file!!!
	  mogrify -crop 768x1130+0+50 -bordercolor black -border 1  $IMAGE_FILE

	  # Creating copy of image which is resized to 50 % (used on homepage of app).
	  TARGET_FILE_NAME=$(basename $IMAGE_FILE .png)"_resized.png"
	  /usr/bin/convert $IMAGE_FILE -resize 50% $TARGET_FILE_NAME
	  # Using fully qualified path to convert to prevent that Window's "convert.exe"
	  # is started (convert.exe is for conversion of FAT volumes to NTFS).
	  
	done

	echo -e "\n\nNumber of files processed: "$COUNTER

fi

echo -e "\n\nPlease press ENTER ..."
read DUMMY_VAR
