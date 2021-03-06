package de.tum.in.tumcampusapp.activities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import de.tum.in.tumcampus.R;
import de.tum.in.tumcampusapp.activities.wizzard.WizNavExtrasActivity;
import de.tum.in.tumcampusapp.activities.wizzard.WizNavStartActivity;
import de.tum.in.tumcampusapp.adapters.StartSectionsPagerAdapter;
import de.tum.in.tumcampusapp.auxiliary.Const;
import de.tum.in.tumcampusapp.auxiliary.PersonalLayoutManager;
import de.tum.in.tumcampusapp.preferences.UserPreferencesActivity;
import de.tum.in.tumcampusapp.services.BackgroundService;
import de.tum.in.tumcampusapp.services.ImportService;
import de.tum.in.tumcampusapp.services.SilenceService;

/**
 * Main activity displaying the categories and menu items to start each activity
 * (feature)
 * 
 * @author Sascha Moecker
 */
public class StartActivity extends FragmentActivity {
	public static final int DEFAULT_SECTION = 1;
	public static final String LAST_CHOOSEN_SECTION = "last_choosen_section";
	public static final int REQ_CODE_COLOR_CHANGE = 0;

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	StartSectionsPagerAdapter mSectionsPagerAdapter;
	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;

	/**
	 * Receiver for Services
	 */
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ImportService.BROADCAST_NAME)) {
				String message = intent.getStringExtra(Const.MESSAGE_EXTRA);
				String action = intent.getStringExtra(Const.ACTION_EXTRA);

				if (action.length() != 0) {
					Log.i(getClass().getSimpleName(), message);
				}
			}
			if (intent.getAction().equals(WizNavExtrasActivity.BROADCAST_NAME)) {
				Log.i(getClass().getSimpleName(), "Color has changed");
				shouldRestartOnResume = true;
			}
		}
	};

	boolean shouldRestartOnResume;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Check if there is a result key in an intent
		if (data != null && data.hasExtra(Const.PREFS_HAVE_CHANGED)
				&& data.getBooleanExtra(Const.PREFS_HAVE_CHANGED, false)) {
			// Restart the Activity if prefs have changed
			Intent intent = getIntent();
			finish();
			startActivity(intent);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start);

		// Create the adapter that will return a fragment for each of the
		// primary sections of the app.
		mSectionsPagerAdapter = new StartSectionsPagerAdapter(this,
				getSupportFragmentManager());
		
		// Workaround for new API version. There was a security update which
		// disallows applications to execute HTTP request in the GUI main
		// thread.
		if (android.os.Build.VERSION.SDK_INT > 8) {
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
					.permitAll().build();
			StrictMode.setThreadPolicy(policy);
		}

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
		mViewPager.setCurrentItem(DEFAULT_SECTION);

		// Registers receiver for download and import
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ImportService.BROADCAST_NAME);
		intentFilter.addAction(WizNavExtrasActivity.BROADCAST_NAME);
		registerReceiver(receiver, intentFilter);

		// Imports default values into database
		Intent service;
		service = new Intent(this, ImportService.class);
		service.putExtra(Const.ACTION_EXTRA, Const.DEFAULTS);
		startService(service);

		// Start silence Service (if already started it will just invoke a check)
		service = new Intent(this, SilenceService.class);
		startService(service);

		// Start daily Service (same here: if already started it will just invoke a check)
		service = new Intent(this, BackgroundService.class);
		startService(service);

		Boolean hideWizzardOnStartup = PreferenceManager
				.getDefaultSharedPreferences(this).getBoolean(
						Const.HIDE_WIZZARD_ON_STARTUP, false);

		// Check for important news 
        try {
			DefaultHttpClient client = new DefaultHttpClient();
	
			HttpGet request = new HttpGet("http://vmbaumgarten1.informatik.tu-muenchen.de/tca/info.txt");
			HttpResponse response = client.execute(request);
	
			StatusLine stat = response.getStatusLine();
			if (stat.getStatusCode() != 200) {
				Log.d("Info", "Cannot fetch important news - wrong status code or server down - no important news?");
			} else {
				HttpEntity responseEntity = response.getEntity();
				InputStream is = responseEntity.getContent();
				ByteArrayOutputStream content = new ByteArrayOutputStream();
	
				int i = 0;
				// We expect a short message only
				byte[] buffer = new byte[128]; 
				while ((i = is.read(buffer)) != -1) {
				   content.write(buffer, 0, i);
				}
				String newsstring = new String(content.toByteArray());
				
				String ns = Context.NOTIFICATION_SERVICE;
				NotificationManager nm = (NotificationManager) getSystemService(ns);
				      
				int icon = android.R.drawable.ic_dialog_alert;
				CharSequence tickerText = newsstring; 
				long when = System.currentTimeMillis(); // show it right now
	
				@SuppressWarnings("deprecation")
				Notification notification = new Notification(icon, tickerText, when);
				
				Intent intent = new Intent(this, StartActivity.class);
				PendingIntent appIntent = PendingIntent.getActivity(this, 0, intent, 0);
	
				notification.setLatestEventInfo(this, "TCA Information", newsstring, appIntent);
				
				nm.notify(1, notification);  
				Log.d("News",newsstring);
			}
        } catch (IOException e) {
        	Log.e("Error", "Cannot fetch important news - unknown exception");
        } 
		
		// Check the flag if user wnats the wizzard to open at startup
		if (!hideWizzardOnStartup) {
			Intent intent = new Intent(this, WizNavStartActivity.class);
			startActivity(intent);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_start_activity, menu);
		return true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// important to unregister the broadcast receiver
		unregisterReceiver(receiver);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			// Opens the preferences screen
			Intent intent = new Intent(this, UserPreferencesActivity.class);
			startActivityForResult(intent, REQ_CODE_COLOR_CHANGE);
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		PersonalLayoutManager.setColorForId(this, R.id.pager_title_strip);
		if (shouldRestartOnResume) {
			// finish and restart myself
			finish();
			Intent intent = new Intent(this, this.getClass());
			startActivity(intent);
		}
	}
}
