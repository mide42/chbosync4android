In the Subversion repository of this project some auxiliary
_[Bash](http://en.wikipedia.org/wiki/Bash_(Unix_shell))_ scripts (scripts for the Bash shell) can be found under _[trunk/BashScripts](https://code.google.com/p/chbosync4android/source/browse/#svn%2Ftrunk%2FBashScripts)_. These scripts can be executed on a Linux system (they were tested with _OpenSuse 13.1_) or with _[Cygwin32](https://www.cygwin.com)_ on Windows.

List of scripts:

  * _**CutScreenshotFromNexus4.sh**_: Crop screenshots taken on Nexus 4 with Emulator. These screenshots can be used in the app's listing in the app store.

  * _**BuildFeatureGraphic.sh**_: Takes the app's icon (512x512 pixel) as input and creates a _"Feature Graphic"_ to be used in the app's listing in the app store. The icon's size is reduced to 480x480 and centered over a black background. The feature graphic created as the size of 1024x500. Feature Graphics are used at the top of the app's listing in the app store. Click _[here](https://chbosync4android.googlecode.com/svn/wiki/images/AppStoreListing_FeatureGraphic_1024x500.png)_ to see an example _Feature Graphic_ which was generated with this script.

  * _**ExtractStringResourceNames.sh**_: Script for extracting the identifiers of translatable strings in XML resource files. The list of identifiers of each XML file is written to a text file in sorted order. These text files can be compared to find missing translations.

The script files that do image processing need _[ImageMagick](http://www.imagemagick.org)_ to be installed (is also available as package for Cygwin).

Please also read the comments at top of each of these script files.

These script were written especially for _ChoBoSync_, i.e. they were not taken from the _[Funambol](Funambol.md) open source project_ or the _[PTBV SyncML client](PTBV_SyncML_Client.md)_.

The scripts are distributed _WITHOUT ANY WARRANTY_!