
@REM Using adb (Android Debug Bridge) to fetch log messages 
@REM from a connected device (emulator instance or real
@REM device via USB cable or WLAN) with tag "ChBoSync".
@REM This command fetches all log messages written by
@REM method writeLogMessage() in class com.funambol.util.Log

adb logcat ChBoSync:V *:S 
@REM ChBoSync:V : Get all messages with tag "ChBoSync" and a log level of VERBOSE or higher
@REM              (i.e. all log levels, since VERBOSE is the lowest log level)
@REM 
@REM *:S : Suppress all other log messages

@REM More info on adb's "logcat" command:
@REM https://developer.android.com/studio/command-line/logcat.html
