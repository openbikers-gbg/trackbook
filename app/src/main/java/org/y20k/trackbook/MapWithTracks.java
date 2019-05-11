package org.y20k.trackbook;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.y20k.trackbook.core.Track;
import org.y20k.trackbook.helpers.DropdownAdapter;
import org.y20k.trackbook.helpers.ExportHelper;
import org.y20k.trackbook.helpers.LengthUnitHelper;
import org.y20k.trackbook.helpers.LocationHelper;
import org.y20k.trackbook.helpers.LogHelper;
import org.y20k.trackbook.helpers.MapHelper;
import org.y20k.trackbook.helpers.StorageHelper;

import java.io.File;
import java.text.DateFormat;
import java.util.Locale;

import static org.y20k.trackbook.helpers.TrackbookKeys.DEFAULT_LATITUDE;
import static org.y20k.trackbook.helpers.TrackbookKeys.DEFAULT_LONGITUDE;
import static org.y20k.trackbook.helpers.TrackbookKeys.FILE_MOST_CURRENT_TRACK;
import static org.y20k.trackbook.helpers.TrackbookKeys.INSTANCE_CURRENT_TRACK;
import static org.y20k.trackbook.helpers.TrackbookKeys.INSTANCE_TRACK_TRACK_MAP;


public class MapWithTracks extends AppCompatActivity {

    /* Define log tag */
    private static final String LOG_TAG = MapWithTracks.class.getSimpleName();

    // class to show selected saved routes one by one

    private int mCurrentTrack;
    private MapView mMapView;
    private Track mTrack;
    private DropdownAdapter mDropdownAdapter;
    private ItemizedIconOverlay mTrackOverlay;
    private IMapController mController;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get reference to basic map
        mMapView = (MapView) findViewById(R.id.track_map);

        // create drop-down adapter
        mDropdownAdapter = new DropdownAdapter(this);

        // get map controller
        mController = mMapView.getController();

        // empty cache
        ExportHelper.emptyCacheDirectory(this);

        // ToDo: check permissions

        // set user agent to prevent getting banned from the osm servers
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        // set the path for osmdroid's files (e.g. tile cache)
        Configuration.getInstance().setOsmdroidBasePath(this.getExternalFilesDir(null));

        // set up main layout
        // point to the track map layout
        setContentView(R.layout.fragment_main_track);

        // get current track
        if (savedInstanceState != null) {
            mCurrentTrack = savedInstanceState.getInt(INSTANCE_CURRENT_TRACK, 0);
        } else {
            mCurrentTrack = 0;
        }

        // display map and statistics
        if (savedInstanceState != null) {
            // get track from saved instance and display map and statistics
            mTrack = savedInstanceState.getParcelable(INSTANCE_TRACK_TRACK_MAP);
            displayTrack();
        } else if (mTrack == null) {
            // load track and display map and statistics
            LoadTrackAsyncHelper loadTrackAsyncHelper = new LoadTrackAsyncHelper();
            loadTrackAsyncHelper.execute();
        } else {
            // just display map and statistics
            displayTrack();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

    }


    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /* Displays map and statistics for track */
    private void displayTrack() {
        GeoPoint position;

        if (mTrack != null && mTrack.getSize() > 0) {
            // set end of track as position
            Location lastLocation = mTrack.getWayPointLocation(mTrack.getSize() -1);
            position = new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude());

            // draw track on map
            drawTrackOverlay(mTrack);

        } else {
            position = new GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
        }

        // center map over position
        mController.setCenter(position);

    }

    /* Draws track onto overlay */
    private void drawTrackOverlay(Track track) {
        mMapView.getOverlays().remove(mTrackOverlay);
        mTrackOverlay = MapHelper.createTrackOverlay(this, track, false);
        mMapView.getOverlays().add(mTrackOverlay);
    }


    /**
     * Inner class: Loads track from external storage using AsyncTask
     */
    private class LoadTrackAsyncHelper extends AsyncTask<Integer, Void, Void> {

        @Override
        protected Void doInBackground(Integer... ints) {
            LogHelper.v(LOG_TAG, "Loading track object in background.");

            StorageHelper storageHelper = new StorageHelper(MapWithTracks.this);
            if (ints.length > 0) {
                // get track file from dropdown adapter
                int item = ints[0];
                File trackFile = mDropdownAdapter.getItem(item).getTrackFile();
                LogHelper.v(LOG_TAG, "Loading track number " + item);
                mTrack = storageHelper.loadTrack(trackFile);
            } else {
                // load track object from most current file
                LogHelper.v(LOG_TAG, "No specific track specified. Loading most current one.");
                mTrack = storageHelper.loadTrack(FILE_MOST_CURRENT_TRACK);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            // display track on map
            displayTrack();
        }
    }


}
