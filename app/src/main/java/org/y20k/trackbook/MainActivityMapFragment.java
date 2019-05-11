/**
 * MainActivityMapFragment.java
 * Implements the map fragment used in the map tab of the main activity
 * This fragment displays a map using osmdroid
 *
 * This file is part of
 * TRACKBOOK - Movement Recorder for Android
 *
 * Copyright (c) 2016-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 *
 * Trackbook uses osmdroid - OpenStreetMap-Tools for Android
 * https://github.com/osmdroid/osmdroid
 */

package org.y20k.trackbook;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.y20k.trackbook.core.Track;
import org.y20k.trackbook.helpers.DialogHelper;
import org.y20k.trackbook.helpers.LocationHelper;
import org.y20k.trackbook.helpers.LogHelper;
import org.y20k.trackbook.helpers.MapHelper;
import org.y20k.trackbook.helpers.NightModeHelper;
import org.y20k.trackbook.helpers.StorageHelper;
import org.y20k.trackbook.helpers.TrackbookKeys;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * MainActivityMapFragment class
 */
public class MainActivityMapFragment extends Fragment implements TrackbookKeys {

    /* Define log tag */
    private static final String LOG_TAG = MainActivityMapFragment.class.getSimpleName();


    /* Main class variables */
    private Activity mActivity;
    private Track mTrack;
    private boolean mFirstStart;
    private Snackbar mLocationOffBar;
    private BroadcastReceiver mTrackUpdatedReceiver;
    private SettingsContentObserver mSettingsContentObserver;
    private MapView mMapView;
    private IMapController mController;
    private StorageHelper mStorageHelper;
    private LocationManager mLocationManager;
    private LocationListener mGPSListener;
    private LocationListener mNetworkListener;
    private ItemizedIconOverlay mMyLocationOverlay;
    private ItemizedIconOverlay mTrackOverlay;
    private Location mCurrentBestLocation;
    private boolean mTrackerServiceRunning;
    private boolean mLocalTrackerRunning;
    private boolean mLocationSystemSetting;
    private boolean mFragmentVisible;

    private boolean tracksOverlayVisible;
    private List<Track> tracksCache;


    /* Constructor (default) */
    public MainActivityMapFragment() {
    }


    /* Return a new Instance of MainActivityMapFragment */
    public static MainActivityMapFragment newInstance() {
        return new MainActivityMapFragment();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get activity
        mActivity = getActivity();

        // restore first start state and tracking state
        mFirstStart = true;
        mTrackerServiceRunning = false;
        loadTrackerServiceState(mActivity);
        if (savedInstanceState != null) {
            mFirstStart = savedInstanceState.getBoolean(INSTANCE_FIRST_START, true);
        }

        // create storage helper
        mStorageHelper = new StorageHelper(mActivity);

        // acquire reference to Location Manager
        mLocationManager = (LocationManager) mActivity.getSystemService(Context.LOCATION_SERVICE);

        // CASE 1: get saved location if possible
        if (savedInstanceState != null) {
            Location savedLocation = savedInstanceState.getParcelable(INSTANCE_CURRENT_LOCATION);
            // check if saved location is still current
            if (LocationHelper.isCurrent(savedLocation)) {
                mCurrentBestLocation = savedLocation;
            } else {
                mCurrentBestLocation = null;
            }
        }

        // CASE 2: get last known location if no saved location or saved location is too old
        if (mCurrentBestLocation == null && mLocationManager.getProviders(true).size() > 0) {
            mCurrentBestLocation = LocationHelper.determineLastKnownLocation(mLocationManager);
        }

        // CASE 3: location services are available but unable to get location - this should not happen
        if (mCurrentBestLocation == null) {
            mCurrentBestLocation = new Location(LocationManager.NETWORK_PROVIDER);
            mCurrentBestLocation.setLatitude(DEFAULT_LATITUDE);
            mCurrentBestLocation.setLongitude(DEFAULT_LONGITUDE);
        }

        // get state of location system setting
        mLocationSystemSetting = LocationHelper.checkLocationSystemSetting(mActivity);

        // create content observer for changes in System Settings
        mSettingsContentObserver = new SettingsContentObserver( new Handler());

        // register broadcast receiver for new WayPoints
        mTrackUpdatedReceiver = createTrackUpdatedReceiver();
        IntentFilter trackUpdatedIntentFilter = new IntentFilter(ACTION_TRACK_UPDATED);
        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mTrackUpdatedReceiver, trackUpdatedIntentFilter);
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // create basic map
        mMapView = new MapView(inflater.getContext());

        // get map controller
        mController = mMapView.getController();

        // basic map setup
        mMapView.setTileSource(TileSourceFactory.HIKEBIKEMAP);
        mMapView.setTilesScaledToDpi(true);

        // set dark map tiles, if necessary
        if (NightModeHelper.getNightMode(mActivity)) {
            mMapView.getOverlayManager().getTilesOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        }

        // add multi-touch capability
        mMapView.setMultiTouchControls(true);

        // disable default zoom controls
        mMapView.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);

        // add compass to map
        CompassOverlay compassOverlay = new CompassOverlay(mActivity, new InternalCompassOrientationProvider(mActivity), mMapView);
        compassOverlay.enableCompass();
        mMapView.getOverlays().add(compassOverlay);

        // initiate map state
        if (savedInstanceState != null) {
            // restore saved instance of map
            GeoPoint position = new GeoPoint(savedInstanceState.getDouble(INSTANCE_LATITUDE_MAIN_MAP, DEFAULT_LATITUDE), savedInstanceState.getDouble(INSTANCE_LONGITUDE_MAIN_MAP, DEFAULT_LONGITUDE));
            mController.setCenter(position);
            mController.setZoom(savedInstanceState.getDouble(INSTANCE_ZOOM_LEVEL_MAIN_MAP, 16f));
            // restore current location
            mCurrentBestLocation = savedInstanceState.getParcelable(INSTANCE_CURRENT_LOCATION);
        } else if (mCurrentBestLocation != null) {
            // fallback or first run: set map to current position
            GeoPoint position = convertToGeoPoint(mCurrentBestLocation);
            mController.setCenter(position);
            mController.setZoom(16f);
        }

        // inform user that new/better location is on its way
        if (mFirstStart && !mTrackerServiceRunning) {
            Toast.makeText(mActivity, mActivity.getString(R.string.toast_message_acquiring_location), Toast.LENGTH_LONG).show();
            mFirstStart = false;
        }

//        // load track from saved instance
//        if (savedInstanceState != null) {
//            mTrack = savedInstanceState.getParcelable(INSTANCE_TRACK_MAIN_MAP);
//        }

        // mark user's location on map
        if (mCurrentBestLocation != null && !mTrackerServiceRunning) {
            mMyLocationOverlay = MapHelper.createMyLocationOverlay(mActivity, mCurrentBestLocation, LocationHelper.isCurrent(mCurrentBestLocation), false);
            mMapView.getOverlays().add(mMyLocationOverlay);
        }

        return mMapView;
    }


    @Override
    public void onResume() {
        super.onResume();

        // set visibility
        mFragmentVisible = true;

        // Do not show all tracks as overlay to begin with
        tracksOverlayVisible = false;

        // load state of tracker service - see if anything changed
        loadTrackerServiceState(mActivity);

        // load track from temp file if it exists
        if (mStorageHelper.tempFileExists()) {
            LoadTempTrackAsyncHelper loadTempTrackAsyncHelper = new LoadTempTrackAsyncHelper();
            loadTempTrackAsyncHelper.execute();
        }

//        // CASE 1: recording active
//        if (mTrackerServiceRunning) {
//            // request an updated track recording from service
//            ((MainActivity)mActivity).requestTrack();
//        }
//
//        // CASE 2: recording stopped - temp file exists
//        else if (mStorageHelper.tempFileExists()) {
//            // load track from temp file if it exists
//            LoadTempTrackAsyncHelper loadTempTrackAsyncHelper = new LoadTempTrackAsyncHelper();
//            loadTempTrackAsyncHelper.execute();
//        }

//        // CASE 3: not recording and no temp file
//        else if (mTrack != null) {
//            // just draw existing track data (from saved instance)
//            drawTrackOverlay(mTrack);
//        }

        // show/hide the location off notification bar
        toggleLocationOffBar();

        // start preliminary tracking - if no TrackerService is running
        if (!mTrackerServiceRunning && mFragmentVisible) {
            startPreliminaryTracking();
        }

        // register content observer for changes in System Settings
        mActivity.getContentResolver().registerContentObserver(android.provider.Settings.Secure.CONTENT_URI, true, mSettingsContentObserver );
    }


    @Override
    public void onPause() {
        super.onPause();

        // set visibility
        mFragmentVisible = false;

        // disable preliminary location listeners
        stopPreliminaryTracking();

        // disable content observer for changes in System Settings
        mActivity.getContentResolver().unregisterContentObserver(mSettingsContentObserver);
    }


    @Override
    public void onDestroyView(){
        super.onDestroyView();

        // deactivate map
        mMapView.onDetach();
    }


    @Override
    public void onDestroy() {
        LogHelper.v(LOG_TAG, "onDestroy called.");

        // reset first start state
        mFirstStart = true;

        // disable  broadcast receivers
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mTrackUpdatedReceiver);

        super.onDestroy();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case RESULT_SAVE_DIALOG:
                if (resultCode == Activity.RESULT_OK) {
                    // user chose SAVE
                    if (mTrack.getSize() > 0) {
                        // Track is not empty - clear map AND save track
                        clearSingleTrack(true);
                        // FloatingActionButton state is already being handled in MainActivity
                        ((MainActivity)mActivity).onFloatingActionButtonResult(requestCode, resultCode);
                        LogHelper.v(LOG_TAG, "Save dialog result: SAVE");
                    } else {
                        // track is empty
                        handleEmptyRecordingSaveRequest();
                    }
                } else if (resultCode == Activity.RESULT_CANCELED){
                    LogHelper.v(LOG_TAG, "Save dialog result: CANCEL");
                }
                break;
            case RESULT_CLEAR_DIALOG:
                if (resultCode == Activity.RESULT_OK) {
                    // User chose CLEAR
                    if (mTrack.getSize() > 0) {
                        // Track is not empty - notify user
                        Toast.makeText(mActivity, getString(R.string.toast_message_track_clear), Toast.LENGTH_LONG).show();
                    }
                    // clear map, DO NOT save track
                    clearSingleTrack(false);
                    // handle FloatingActionButton state in MainActivity
                    ((MainActivity)mActivity).onFloatingActionButtonResult(requestCode, resultCode);
                } else if (resultCode == Activity.RESULT_CANCELED){
                    LogHelper.v(LOG_TAG, "Clear dialog result: CANCEL");
                }
                break;
            case RESULT_EMPTY_RECORDING_DIALOG:
                // handle FloatingActionButton state and possible Resume-Action in MainActivity
                ((MainActivity)mActivity).onFloatingActionButtonResult(requestCode, resultCode);
                break;
        }
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(INSTANCE_FIRST_START, mFirstStart);
        outState.putBoolean(INSTANCE_TRACKING_STATE, mTrackerServiceRunning);
        outState.putParcelable(INSTANCE_CURRENT_LOCATION, mCurrentBestLocation);
        outState.putDouble(INSTANCE_LATITUDE_MAIN_MAP, mMapView.getMapCenter().getLatitude());
        outState.putDouble(INSTANCE_LONGITUDE_MAIN_MAP, mMapView.getMapCenter().getLongitude());
        outState.putDouble(INSTANCE_ZOOM_LEVEL_MAIN_MAP, mMapView.getZoomLevelDouble());
//        outState.putParcelable(INSTANCE_TRACK_MAIN_MAP, mTrack);
        super.onSaveInstanceState(outState);
    }


    /* Setter for tracking state */
    public void setTrackingState(boolean trackingState) {
        mTrackerServiceRunning = trackingState;

        // turn on/off tracking for MainActivity Fragment - prevent double tracking
        if (mTrackerServiceRunning) {
            stopPreliminaryTracking();
        } else if (!mLocalTrackerRunning && mFragmentVisible) {
            startPreliminaryTracking();
        }

        if (mTrack != null) {
            drawTrackOverlay(mTrack); // TODO check if redundant
        }

        // update marker
        updateMyLocationMarker();
        LogHelper.v(LOG_TAG, "TrackingState: " + trackingState);
    }


    /* Getter for current best location */
    public Location getCurrentBestLocation() {
        if (mLocationSystemSetting) {
            return mCurrentBestLocation;
        } else {
            return null;
        }
    }


    /* Handles tap on the my location button */
    public boolean handleShowMyLocation() {

        // do nothing if location setting is off
        if (toggleLocationOffBar()) {
            stopPreliminaryTracking();
            return false;
        }

        GeoPoint position;

        // get current position
        if (mTrackerServiceRunning && mTrack != null && mTrack.getSize() > 0) {
            // get current Location from tracker service
            mCurrentBestLocation = mTrack.getWayPointLocation(mTrack.getSize() - 1);
        } else if (mCurrentBestLocation == null) {
            // app does not have any location fix
            mCurrentBestLocation = LocationHelper.determineLastKnownLocation(mLocationManager);
        }

        // check if really got a position
        if (mCurrentBestLocation != null) {
            position = convertToGeoPoint(mCurrentBestLocation);

            // center map on current position
            mController.setCenter(position);

            // mark user's new location on map and remove last marker
            updateMyLocationMarker();

            // inform user about location quality
            String locationInfo;
            long locationAge =  (SystemClock.elapsedRealtimeNanos() - mCurrentBestLocation.getElapsedRealtimeNanos()) / 1000000;
            String locationAgeString = LocationHelper.convertToReadableTime(locationAge, false);
            if (locationAgeString == null) {
                locationAgeString = mActivity.getString(R.string.toast_message_last_location_age_one_hour);
            }
            locationInfo = " " + locationAgeString + " | " + mCurrentBestLocation.getProvider();
            Toast.makeText(mActivity, mActivity.getString(R.string.toast_message_last_location) + locationInfo, Toast.LENGTH_LONG).show();
            return true;
        } else {
            Toast.makeText(mActivity, mActivity.getString(R.string.toast_message_location_services_not_ready), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public boolean handleToggleTracks() {
        // Toggle current visibility state of all tracks overlay
        tracksOverlayVisible = !tracksOverlayVisible;

        if(tracksOverlayVisible) {
            // Show all tracks as overlay
            displayAllTracks();
        } else {
            // Clear map of tracks overlay (But do not save them to filesystem (false as parameter))
            clearAllTracks();
        }
        LogHelper.v(LOG_TAG, "Toggled overlay of all tracks, showing overlay: "
                + tracksOverlayVisible);
        return true;
    }


    /* Removes track crumbs from map */
    private void clearSingleTrack(boolean saveTrack) {

        // clear map
        if (mTrackOverlay != null) {
            mMapView.getOverlays().remove(mTrackOverlay);
            mTrackOverlay = null;
        }

        if (saveTrack) {
            // save track object if requested
            SaveTrackAsyncHelper saveTrackAsyncHelper = new SaveTrackAsyncHelper();
            saveTrackAsyncHelper.execute();
            Toast.makeText(mActivity, mActivity.getString(R.string.toast_message_save_track), Toast.LENGTH_LONG).show();
        } else {
            // clear track object and delete temp file
            mTrack = null;
            mStorageHelper.deleteTempFile();
        }

    }

    private void clearAllTracks() {
        // We do never want to save all tracks again
        // Clear all track overlays
        if(mMapView != null) {
            mMapView.getOverlays().clear();
            // Clear current track overlay
            mTrackOverlay = null;
        }

        // Redraw the user instantly - Do not wait for next draw cycle
        mTrackOverlay = MapHelper.createMyLocationOverlay(mActivity, mCurrentBestLocation, false, mTrackerServiceRunning);
        mMapView.getOverlays().add(mTrackOverlay);

    }


    /* Handles case when user chose to save recording with zero waypoints */ // todo implement
    private void handleEmptyRecordingSaveRequest() {
        // prepare empty recording dialog ("Unable to save")
        int dialogTitle = R.string.dialog_error_empty_recording_title;
        String dialogMessage = getString(R.string.dialog_error_empty_recording_content);
        int dialogPositiveButton = R.string.dialog_error_empty_recording_action_resume;
        int dialogNegativeButton = R.string.dialog_default_action_cancel;
        // show  empty recording dialog
        DialogFragment dialogFragment = DialogHelper.newInstance(dialogTitle, dialogMessage, dialogPositiveButton, dialogNegativeButton);
        dialogFragment.setTargetFragment(this, RESULT_EMPTY_RECORDING_DIALOG);
        dialogFragment.show(((AppCompatActivity)mActivity).getSupportFragmentManager(), "EmptyRecordingDialog");
        // results of dialog are handled by onActivityResult
    }


    /* Start preliminary tracking for map */
    private void startPreliminaryTracking() {
        if (mLocationSystemSetting && !mLocalTrackerRunning) {
            // create location listeners
            List locationProviders = mLocationManager.getAllProviders();
            if (locationProviders.contains(LocationManager.GPS_PROVIDER)) {
                mGPSListener = createLocationListener();
                mLocalTrackerRunning = true;
            }
            if (locationProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                mNetworkListener = createLocationListener();
                mLocalTrackerRunning = true;
            }
            // register listeners
            LocationHelper.registerLocationListeners(mLocationManager, mGPSListener, mNetworkListener);
            LogHelper.v(LOG_TAG, "Starting preliminary tracking.");
        }
    }


    /* Removes gps and network location listeners */
    private void stopPreliminaryTracking() {
        if (mLocalTrackerRunning) {
            mLocalTrackerRunning = false;
            // remove listeners
            LocationHelper.removeLocationListeners(mLocationManager, mGPSListener, mNetworkListener);
            LogHelper.v(LOG_TAG, "Stopping preliminary tracking.");
        }
    }


    /* Creates listener for changes in location status */
    private LocationListener createLocationListener() {
        return new LocationListener() {
            public void onLocationChanged(Location location) {
                // check if the new location is better
                if (mCurrentBestLocation == null || LocationHelper.isBetterLocation(location, mCurrentBestLocation)) {
                    // save location
                    mCurrentBestLocation = location;
                    // mark user's new location on map and remove last marker
                    updateMyLocationMarker();
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                LogHelper.v(LOG_TAG, "Location provider status change: " +  provider + " | " + status);
            }

            public void onProviderEnabled(String provider) {
                LogHelper.v(LOG_TAG, "Location provider enabled: " +  provider);
            }

            public void onProviderDisabled(String provider) {
                LogHelper.v(LOG_TAG, "Location provider disabled: " +  provider);
            }
        };
    }


    /* Updates marker for current user location  */
    private void updateMyLocationMarker() {
        mMapView.getOverlays().remove(mMyLocationOverlay);
        // only update while not tracking
        if (!mTrackerServiceRunning) {
            mMyLocationOverlay = MapHelper.createMyLocationOverlay(mActivity, mCurrentBestLocation, LocationHelper.isCurrent(mCurrentBestLocation), false);
            mMapView.getOverlays().add(mMyLocationOverlay);
        }
    }


    /* Draws track onto overlay */
    private void drawTrackOverlay(Track track) {
        //mMapView.getOverlays().remove(mTrackOverlay);
        mTrackOverlay = null;
        if (track == null || track.getSize() == 0) {
            LogHelper.i(LOG_TAG, "Waiting for a track. Showing preliminary location.");
            mTrackOverlay = MapHelper.createMyLocationOverlay(mActivity, mCurrentBestLocation, false, mTrackerServiceRunning);
            Toast.makeText(mActivity, mActivity.getString(R.string.toast_message_acquiring_location), Toast.LENGTH_LONG).show();
        } else {
            LogHelper.v(LOG_TAG, "Drawing track overlay.");
            mTrackOverlay = MapHelper.createTrackOverlay(mActivity, track, mTrackerServiceRunning);
        }

        mMapView.getOverlays().add(mTrackOverlay);

    }

    private void displayAllTracks() {
        // Function for drawing all tracks as an overlay to the map fragment

        if(tracksCache == null || tracksCache.isEmpty()) {
            // If cache is not loaded, load all tracks from filesystem
            LogHelper.v(LOG_TAG, "Initializing all tracks cache");
            tracksCache = loadAllTracks();
        }
        LogHelper.v(LOG_TAG, "Trying to draw all overlays onto map");
        // Draw all tracks as an overlay
        for(Track track : tracksCache) {
            drawTrackOverlay(track);
        }
    }

    private List<Track> loadAllTracks() {
        // Create a storage helper for loading track files
        StorageHelper storageHelper = new StorageHelper(mActivity);

        File[] listOfTrackbookFiles = storageHelper.getListOfTrackbookFiles();
        List<Track> loadedTracks = new ArrayList<>();

        LogHelper.v(LOG_TAG, "Trying to all tracks from file system");
        // Try and read all track files into memory
        for(File trackbookFile : listOfTrackbookFiles) {
            loadedTracks.add(storageHelper.loadTrack(trackbookFile));
        }

        return loadedTracks;
    }


    /* Toggles snackbar indicating that location setting is off */
    private boolean toggleLocationOffBar() {
        // create snackbar indicator for location setting off
        if (mLocationOffBar == null) {
            mLocationOffBar = Snackbar.make(mMapView, R.string.snackbar_message_location_offline, Snackbar.LENGTH_INDEFINITE).setAction("Action", null);
        }

        // get state of location system setting
        mLocationSystemSetting = LocationHelper.checkLocationSystemSetting(mActivity);

        // show snackbar if necessary
        if (!mLocationSystemSetting)  {
            // show snackbar
            mLocationOffBar.show();
            return true;

        } else {
            // hide snackbar
            mLocationOffBar.dismiss();
            return false;
        }

    }


    /* Creates receiver for new WayPoints */
    private BroadcastReceiver createTrackUpdatedReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(EXTRA_TRACK) && intent.hasExtra(EXTRA_LAST_LOCATION)) {
                    // draw track on map
                    mTrack = intent.getParcelableExtra(EXTRA_TRACK);
                    drawTrackOverlay(mTrack);
                    // center map over last location
                    mCurrentBestLocation = intent.getParcelableExtra(EXTRA_LAST_LOCATION);
                    mController.setCenter(convertToGeoPoint(mCurrentBestLocation));
                    // clear intent
                    intent.setAction(ACTION_DEFAULT);
                }
            }
        };
    }


    /* Converts Location to GeoPoint */
    private GeoPoint convertToGeoPoint (Location location) {
        if (location != null) {
            return new GeoPoint(location.getLatitude(), location.getLongitude());
        } else {
            return new GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
        }
    }


    /* Loads state tracker service from preferences */
    private void loadTrackerServiceState(Context context) {
        // TODO: get state directly from service, create a ServiceConnection.
        // see: https://github.com/ena1106/FragmentBoundServiceExample/blob/master/app/src/main/java/it/ena1106/fragmentboundservice/BoundFragment.java
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        mTrackerServiceRunning = settings.getBoolean(PREFS_TRACKER_SERVICE_RUNNING, false);
    }


    /**
     * Inner class: SettingsContentObserver is a custom ContentObserver for changes in Android Settings
     */
    private class SettingsContentObserver extends ContentObserver {

        SettingsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            LogHelper.v(LOG_TAG, "System Setting change detected.");

            // check if location setting was changed
            boolean previousLocationSystemSetting = mLocationSystemSetting;
            mLocationSystemSetting = LocationHelper.checkLocationSystemSetting(mActivity);
            if (previousLocationSystemSetting != mLocationSystemSetting) {
                LogHelper.v(LOG_TAG, "Location Setting change detected.");
                toggleLocationOffBar();
            }

            // start / stop preliminary tracking
            if (!mLocationSystemSetting) {
                stopPreliminaryTracking();
            } else if (!mTrackerServiceRunning && mFragmentVisible) {
                startPreliminaryTracking();
            }
        }

    }


    /**
     * Inner class: Saves track to external storage using AsyncTask
     */
    private class SaveTrackAsyncHelper extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            LogHelper.v(LOG_TAG, "Saving track object in background.");
            // save track object
            mStorageHelper.saveTrack(mTrack, FILE_MOST_CURRENT_TRACK);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // clear track object
            LogHelper.v(LOG_TAG, "Saving finished.");
            mTrack = null;

            // notify track fragment that save is finished
            Intent i = new Intent();
            i.setAction(ACTION_TRACK_SAVE);
            i.putExtra(EXTRA_SAVE_FINISHED, true);
            LocalBroadcastManager.getInstance(mActivity).sendBroadcast(i);
        }
    }
    /**
     * End of inner class
     */


    /**
     * Inner class: Loads track from external storage using AsyncTask
     */
    private class LoadTempTrackAsyncHelper extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            LogHelper.v(LOG_TAG, "Loading temporary track object in background.");
            // load track object
            mTrack = mStorageHelper.loadTrack(FILE_TEMP_TRACK);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            LogHelper.v(LOG_TAG, "Loading finished.");

            // draw track on map
            if (mTrack != null) {
                drawTrackOverlay(mTrack);
            }

            // delete temp file
//            mStorageHelper.deleteTempFile(); // todo check if necessary
        }
    }
    /**
     * End of inner class
     */

}