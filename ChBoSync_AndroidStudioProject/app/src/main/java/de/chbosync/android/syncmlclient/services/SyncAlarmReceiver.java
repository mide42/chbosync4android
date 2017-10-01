package de.chbosync.android.syncmlclient.services;

import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import de.chbosync.android.syncmlclient.App;

public class SyncAlarmReceiver extends BroadcastReceiver {

	static boolean doUIResponse = false;
	
	@Override
	public void onReceive(Context arg0, Intent arg1) {
		String message = "Sync-Alarm: " + new Date().toString();
		if (doUIResponse) Toast.makeText(App.i().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
		Log.d("SyncAlarmReceiver", message);
	}

}
