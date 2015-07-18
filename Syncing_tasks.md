**!!! ChBoSync version 1.0 does _NOT_ support the syncing of tasks so far. This wiki page is about a possible feature that might be added in the future. !!!**

# Introduction: From Astrid to Tasquid #

The original _[Funambol](Funambol.md) SyncML client_ for Android supported the syncing of tasks (ToDo lists). For this is was necessary that the Android app _"Astrid"_ by Toodoroo Inc was installed on the Android device. The syncing of tasks was disabled by the developer of the _[PTBV SyncML client](PTBV_SyncML_Client.md)_ (like syncing of notes was also disabled). The communication between the _[Funambol](Funambol.md) SyncML client_ and _"Astrid"_ was based on a _[Content Provider](http://developer.android.com/guide/topics/providers/content-providers.html)_ offered by the Astrid app. There is also a _[Youtube video](https://www.youtube.com/watch?v=h3gf0wYEUlY)_ showing _Astrid_ in action.

However, _"Astrid"_ is no longer available as app in Google's app store, because _Astrid/Toodoroo_ was purchased by Yahoo around May 2013. The source code of the Astrid app was released under an open source license: https://github.com/todoroo/astrid

Based on Astrid's code the app _Tasquid_ was developed, which is also available in Google's app store:
  * http://www.tasquid.com
  * https://play.google.com/store/apps/details?id=com.eztransition.tasquid
  * https://www.facebook.com/tasquid
  * https://github.com/shaded2/Tasquid

The developers of _ChBoSync_ are neither related to the developers of _"Astrid"_ nor _"Tasquid"_.

# Changes needed in the source code of _ChBoSync_ #

The following is a list of changes necessary to re-enable the syncing of tasks for _ChBoSync_ (with _Tasquid_ instead of _Astrid_):

  * Add the following two permissions to the manifest file: _"com.todoroo.astrid.READ"_ and _"com.todoroo.astrid.WRITE"_. These permissions are needed to access the content provider offered by _Tasquid_.

  * Class _AndroidAppSyncSourceManager_ in package _de.chbosync.android.syncmlclient_, method _setupTasksSource()_: When invoking method _resolveContentProvider()_ the first argument has to be _"com.eztransition.tasquid"_ instead of _"com.todoroo.astrid"_. The purpose of this change is that _ChBoSync_ can detect that _Tasquid_ is installed on the same device (when no task app is found then syncing of tasks is disabled by _ChBoSync_).

  * Class _AndroidCustomization_ in package _de.chbosync.android.syncmlclient_: The values for the two constants _"TASKS\_AVAILABLE"_ and _"TASKS\_ENABLED"_ have to be set to _"true"_. Further, the value _"AndroidAppSyncSourceManager.TASKS\_ID"_ for the array _"SOURCES\_ORDER"_ has to be re-enabled.

  * Class _AstridTaskManager_ in package _de.chbosync.android.syncmlclient.source.pim.task_: The value of constant _"AUTHORITY"_ has to be changed to _"com.eztransition.tasquid"_. Further, the URI stored in constant _"CONTENT\_URI"_ (in inner class _Task_) has to be changed to _"content://com.eztransition.tasquid/tasks"_. The reason for these changes is that the authority/URI for the content provider offered by _Tasquid_ were changed by the developers of _Tasquid_.


See also the _[changelist for r94](https://code.google.com/p/chbosync4android/source/detail?r=94)_ and the _[changelist for r101](https://code.google.com/p/chbosync4android/source/detail?spec=svn101&r=101)_ in this project's Subversion repository. See also SVN tag _["svn/tags/2015-01-16\_TasquidIntegration"](https://chbosync4android.googlecode.com/svn/tags/2015-01-16_TasquidIntegration)_.


![https://chbosync4android.googlecode.com/svn/wiki/images/MainScreenWithTaskSyncButton.png](https://chbosync4android.googlecode.com/svn/wiki/images/MainScreenWithTaskSyncButton.png)


# Usage of _Tasquid_ #

When _Tasquid_ is started for the first time, then you will be asked to register for an online account. However, when using _Tasquid_ together with _ChBoSync_ you can skip this, because for syncing with _ChBoSync_ the account on your SyncML enabled groupware server is used.


# Changes needed in the source code of _Tasquid_ #

To use _Tasquid_ together with _ChBoSync_ it is also necessary to make a little change in the source code of _Tasquid_ (in the library project _"astridApi"_ to be more precise). **This means that the current version 4.6.8 of _Tasquid_, which is available in Google's app store, _CANNOT_ be used with _ChBoSync_.**

The following change has to be made in class _AstridApiConstants_ (package _com.todoroo.astrid.api_): The constant _"API\_PACKAGE"_ has to have the value _"com.eztransition.tasquid"_ rather than _"com.todoroo.astrid"_. This has to be changed, because in _Tasquid's_ manifest file the attribute _"android:authorities"_ for the content provider _"com.todoroo.astrid.provider.Astrid3ContentProvider"_ was changed to _"com.eztransition.tasquid"_.

Without this change the URIs added to the _UriMatcher_ object created in the static initializer block of class _Astrid3ContentProvider_ have a authority part at the beginning which does not match the authority definied in the manifest file. This prevents any app (not just _ChBoSync_) from actually accessing this content provider.

This was submitted as issue no 37 in Tasquid's GitHub project: https://github.com/shaded2/Tasquid/issues/37

When compiling _Tasquid_ with Android 4.3 (API-Level 18, _"Jelly Bean MR2"_) or later as _"targetSDK"_, then in the manifest file the following attribut has to be added to the provider tag: _android:exported="true"_. The reason for this is that as of Android 4.3 this attribute is set to _"false"_ by default, see also _[this answer on stackoverflow.com](http://stackoverflow.com/a/18853675)_.


# Further limitation of _Tasquid_ #

**_Tasquid_ runs only on Android devices with an ARM CPU**, but not on devices with an x86 or MIPS CPU/ABI. Since most Android smartphones & tablets have an ARM CPU this is only a minor limitation. The installation of _Tasquid_ on devices with a non-ARM CPU will fail. However, this also means that when working with the Emulator no x86 image can be used. Emulator instances based on an x86 image (such as used by the _[Genymotion Emulator](http://www.genymotion.com)_) are usually much faster than emulators with ARM images, because the virtualization support of the desktop's computer x86 CPU can be utilized.

The reason for this limitation is that the library (sub-)project _"android-aac-enc"_ depends on a native library named _"libaac-encoder"_, but this library is only provided for ARM (see _[this folder](https://github.com/shaded2/Tasquid/tree/master/android-aac-enc/libs)_ in _Tasquid's_ github repository).

Created entry in _Tasquid's_ issue tracker: https://github.com/shaded2/Tasquid/issues/36