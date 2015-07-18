# Using the Eclipse project #

The source code of _ChBoSync_ is provided as Eclipse project in the SVN repository of this project at _code.google.com_.

For the following description it is assumed that you have an installation of Eclipse with the _"Android Developer Tools (ADT)"_, which can be obtained at the following URL: http://developer.android.com/sdk/index.html


  * Check out the folder _"trunk/ChBoSync\_EclipseProject"_ from the SVN repository of this project (see tab _"[Source](https://code.google.com/p/chbosync4android/source/checkout)"_ of this GoogleCode project).

  * In Eclipse/ADT choose _"Import ..."_ in menu _"File"_.

  * Selection of import source: _"Existing Android Code Into Workspace"_ under node _"Android"_.

  * Folder to select as _"Root Directory"_: _"ChBoSync\_EclipseProject"_

  * One project should appear in the table unter the _"Root Directory"_; ensure that in column _"Project to Import"_ this project is selected and click on button _"Finish"_.

  * The project contains all libraries needed for compiling in the folder _"external-libs"_ (several Funambol libraries and _"[Joda Time](joda_time_library.md)"_).

  * Start the app in the emulator or a device connected via the _"Android Debug Bridge (ADB)"_ to your PC by opening the context menu (right button of mouse) of the node labled _"ChBoSync"_ in the _"Package Explorer"_ and choose _"Run As | Android Application"_.
<br><br></li></ul>


# Get along in the source code #

A good starting point for exploring the source code are the screen classes in package _de.chbosync.android.syncmlclient.activities_, e.g. _AndroidHomeScreen_ or _AndroidLoginScreen_. These classes are subclasses of class _android.app.Activity_.

The different types of data that can be synced (e.g., contacts, calendar or notes) are called _"Sync Sources"_. For the setup of each of these _Sync Sources_ there is a method in class _AndroidAppSyncSourceManager_, e.g. method _setupContactsSource()_. Each _Sync Source_ is an instance of class _AndroidAppSyncSource_, whereas this class inherits from class _AppSyncSource_. For some data types there are special sub classes of _AndroidAppSyncSource_, e.g. _CalendarAppSyncSource_ and _ContactAppSyncSource_.

If for the activation of a particular _Sync Source_ it has to be checked if a particular 3rd party app is installed on the Android device, then the presence of this app is also checked in the _setupXXXSource()_ method. For example, in method _setupNotesSource()_ it is checked if _["OI Notepad"](oi_notepad.md)_ is installed.

The big buttons on the app's main screen used for triggering the syncing of a particular syncing action (e.g. syncing of contacts or notes) are instances of class _AndroidButtonUISyncSource_.

Class _AndroidHomeScreen_ represents the main screen of the app, i.e. the screen with the big buttons for the individual _Sync Sources_. The creation of these button is done in class _UpdateAvailableSourcesUIThread_, which is an inner class of _AndroidHomeScreen_ .
<br><br>


<h2>Program flow when a sync button is pressed</h2>

When the button for a particular Sync Source is pressed (e.g., for syncing notes), then the following happens:<br>
<br>
<ul><li>For each Sync Source's button an instance of class <i>ButtonListener</i> (inner class of class <i>AndroidHomeScreen</i>) is created.</li></ul>

<ul><li>The corresponding <i>ButtonListener's</i> method <i>onClick()</i> calls the method <i>buttonPressed()</i> of the <i>HomeScreenController</i> object referenced by the <i>AndroidHomeScreen</i> object (actually an object of class <i>AndroidHomeScreenController</i>, which is a sub class of <i>HomeScreenController</i>).</li></ul>

<ul><li>The method <i>buttonPressed()</i> of class <i>HomeScreenController</i> receives the index of the Sync Source as parameter. It fetches the corresponding object of class <i>AppSyncSource</i> and calls method <i>HomeScreenController::syncSource()</i>.</li></ul>

<ul><li>Method <i>syncSource()</i> calls method <i>synchronize()</i> (still in class <i>HomeScreenController</i>). After several overloaded variants of this method have passed method <i>SynchronizationController::forceSynchronization()</i> is invoked.</li></ul>

<ul><li>After some checks by <i>forceSynchronization()</i> method <i>continueSynchronizationAfterBandwithSaverDialog()</i> is invoked, which again invokes another method named <i>continueSynchronizationAfterFirstSyncDialog()</i>.</li></ul>

<ul><li>In this method an instance of class <i>AppSyncRequest</i> is created. An object of class <i>AppSyncSource</i> is added to this <i>AppSyncRequest</i>. The <i>AppSyncRequest</i> again is added to an object of class <i>SyncScheduler</i>.</li></ul>

<ul><li>For adding the <i>AppSyncRequest</i> the <i>SyncScheduler's</i> method <i>addRequest()</i> is called, which again calls method <i>doSync()</i>.</li></ul>

<ul><li>Add the end of method <i>SyncSchedule::doSync()</i> method <i>callListener()</i> is invoked. At the end of this method the sync request's content is passed as argument to method <i>sync()</i> of an <i>SyncSchedulerListener</i> object.</li></ul>

<ul><li>Since <i>SyncSchedulerListener</i> is just an interface an object of class <i>SyncEngine</i> is used. Method <i>sync()</i> of <i>SyncEngine</i> is called, which calls method <i>synchronize()</i>.</li></ul>

<ul><li>In method <i>SyncEngine::synchronize()</i> some checks are performed (e.g., if the device has connectivity). If all these checks are passed, then an object of class <i>SyncThread</i> is created and started. <i>SyncThread</i> is an inner class of class <i>SyncEngine</i>.</li></ul>

<ul><li>The work that is done in a background thread (rather than blocking the UI/main thread) is done in method <i>SyncEngine::run()</i>. This method just calls method <i>sync()</i> in the same class.</li></ul>

<ul><li>Method <i>sync()</i> calls method <i>synchronize()</i> (still in class <i>SyncEngine</i>). Within this method another method of <i>SyncEngine</i> is called, namely <i>fireSync()</i>. However, before this is done an instance of class <i>SyncManager</i> is returned, which is also one of <i>fireSync()'s</i> parameters. (There are two implementations of interface <i>SyncManagerI</i>, namely the already mentioned class <i>SyncManager</i> and class <i>SapiSyncManager</i>; however, for the Sync Sources supported by <i>ChBoSync</i> so far always class <i>SyncManager</i> is chosen. <i>SapiSyncManager</i> seems to be for media content like images.)</li></ul>

<ul><li>In method <i>SyncEngine::fireSync()</i> just method <i>sync()</i> of the <i>SyncManager</i> object is invoked.</li></ul>

<ul><li>Method <i>SyncManager:sync()</i> is rather long. Somewhere in this method the method <i>postRequest()</i> of the same class in invoked.<br>
<br></li></ul>

Hint: To navigate to the source code of any of these classes mentioned without knowing the package you can press <i>STRG+SHIFT+T</i> for dialog <i>"Open Type"</i> in Eclipse. In this dialog you can search for classes just by their name.<br>
<br><br>

<h1>Logging</h1>

The app has its own logger class, namely <i>com.funambol.util.Log</i>, which has only static methods. This logger is initialized by the code in method <i>initLog()</i> of class <i>AppInitializer</i>. Here an appender object is created that is passed to method <i>Log.initLog()</i>. Currently only an instance of class <i>AndroidLogAppender</i> will be used as appender, because method <i>AppInitializer::isFileTracingAllowed()</i> returns a hard-coded <i>false</i>.<br>
<br>
The <i>AndroidLogAppender</i> writes the log messages to the corresponding methods of Android's standard logger (class <i>android.util.Log</i>). The tag for all these log messages is <i>"ChBoSync"</i>.<br>
<br>
There are other classes implementing the interface <i>Appender</i>, namely <i>FileAppender</i> or <i>ConsoleAppender</i>. There is also class <i>MultipleAppender</i> for using more than just one appender at the same time.<br>
<br>
All the classes for logging mentioned so far can be found in package <i>com.funambol.util</i>.<br>
<br><br>


<h1>Settings</h1>

Management of app's setting/preferences (e.g. show button to install <i><a href='oi_notepad.md'>OI Notepad</a></i> on main screen instead of sync button when this app is not found on the current device) is handled by class <i>AndroidConfiguration</i> in package <i>de.chbosync.android.syncmlclient</i>. This class implements the singleton pattern and stored the settings as shared preferences file named <i>"fnblPref.xml"</i> (full path on emulator's filesystem: <i>/data/data/de.chbosync.android.syncmlclient/shared_prefs/fnblPref.xml</i>) . The saving and loading of the settings used by the original Funambol client are handled in a superclass of this class, namely class <i>Configuration</i> in package <i>com.funambol.client.configuration</i>.<br>
<br>
Some important methods in class <i>AndroidAdvancedSettingsTab</i> (on which the settings introduced by <i>ChBoSync</i> were added):<br>
<ul><li><i>initScreenElements()</i>: Setup of UI element, e.g. instances of class <i>TwoLinesCheckBox</i> for settings concerning <i><a href='oi_notepad.md'>"OI Notepad"</a></i>.<br>
</li><li><i>hasChanges()</i>: Determines if at least one setting was changed, so that saving is necessary.<br>
<br></li></ul>

The default values for settings are defined in method <i>load()</i> either in class <i>Configuration</i> or in the subclass <i>AndroidConfiguration</i>.