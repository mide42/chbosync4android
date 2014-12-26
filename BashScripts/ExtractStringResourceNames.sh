#!/bin/bash

# Bash script to extract the identifiers of string resources from Android resource files (XML files).
# By comparing the list of identifiers (which is sorted) it is easy to find out if there 
# are missing translations. For this comparison a diff tool (e.g., WinMerge on Windows 7)
# can be used.
#
# Usage: Copy this script into a folder, where the XML file with the string resources can be found
#        under names matching the following pattern: strings_*.xml.
#
# Examples for input files:
# * strings_en.xml (for English strings), copied from "res\values\strings.xml".
# * strings_de.xml (for German strings),  copied from "res\values-de\strings.xml".
#
# Examples for output files for the input files mentioned above:
#   strings_en.txt, strings_de.txt
#
# This script is distributed WITHOUT ANY WARRANTY!
#
# December 2014.


NUMBER_OF_FILES=0

for STRING_RESOURCE_FILE in strings_*.xml
do

  echo -e "\n\nProcessing file: "$STRING_RESOURCE_FILE

  FILE_NAME_WITHOUT_SUFFIX=$(basename $STRING_RESOURCE_FILE .xml)
  TARGET_FILE_NAME=${FILE_NAME_WITHOUT_SUFFIX}.txt
  
  grep --only-matching --extended-regexp "<string name=\".*\">" $STRING_RESOURCE_FILE | \
    cut -d "\"" -f2 | sort > $TARGET_FILE_NAME
	
  NUMBER_OF_LINES=$(wc -l $TARGET_FILE_NAME | cut -d " " -f1)
	
  echo -e "\nCreated file: "${TARGET_FILE_NAME}" ("$NUMBER_OF_LINES")"
	
  let NUMBER_OF_FILES+=1
done


if [ $NUMBER_OF_FILES -gt 0 ]
then
  echo -e "\n\nNumber of files created: "${NUMBER_OF_FILES}"."
  echo -e "Use diff or WinMerge to compare these files.\n"
else
  echo -e "\nNo string resource files for processing found!\n"
fi
