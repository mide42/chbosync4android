On this page known limitations/bugs of _ChBoSync_ are described. For each entry the affected version and possible workarounds or solution approaches are to be mentioned.
<br><br>


<h1>Self-Signed Certificates from Server are not accepted</h1>

<b>Versions: 1.0</b>

Self-signed certificates (for SSL) from the server are no longer supported (but were supported by the <i><a href='PTBV_SyncML_Client.md'>PTBV SyncML Client</a></i>, i.e. this is a regression bug). This was fixed as of version 1.1 of ChBoSync (released in April 2015).<br>
<br><br>


<h1>App icon might be blurry</h1>

<b>Versions: 1.0 & 1.1</b>

The app's icon (e.g. used on the homescreen or the app depot) might be blurry depending on the resolution of the display. The reason for this is that the icon is only available with a size of 47x47 pixels, which is appropriate for displays with density class <i>"MDPI"</i> (about 160 DPI). However, for displays with higher density classes like <i>"HDPI"</i> (about 240 DPI), <i>"XHDPI"</i> (about 320 DPI) or <i>"XXHDPI"</i> (about 480 DPI) this icon has to be scaled up by the Android system at runtime which means that it becomes blurry.<br>
<br><br>
Idea for solution: Generate icons for bigger density classes (HDPI, XHDPI, XXHDPI) by centering the original icon over a transparent rectangle of the corresponding target size; see Bash script <i>BashScripts/CreateBiggerIconFiles.sh</i> in the source code repository (script uses ImageMagick).<br>
<br><br>


<h1>Calendar entries with URLs with special characters</h1>

<b>Versions: 1.0 & 1.1</b>

Synchronization of calendar entries with URLs that contain special characters like ?, =, # might lead to an abort of the calendar syncing.<br>
<br>
<b>Workaround:</b> Use URL shortender like <i><a href='http://tinyurl.com'>tinyurl.com</a></i> for such URLs (for example <a href='http://tinyurl.com/k4cx3hm'>http://tinyurl.com/k4cx3hm</a> ).<br>
<br><br>

<h1>Encrypted notes are not supported</h1>

<b>Versions: 1.0 & 1.1</b>

Individual notes in <i><a href='oinotepad.md'>OI Notepad</a></i> can be encrypted if the app <i>"OI Safe"</i> is also installed on the devices. If such an encrypted note is synced to another device, then it is not recognized as encrypted note by <i>OI Notepad</i>.