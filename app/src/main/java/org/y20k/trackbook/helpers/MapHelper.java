/**
 * MapHelper.java
 * Implements the MapHelper class
 * A MapHelper offers helper methods for dealing with Trackbook's map
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

package org.y20k.trackbook.helpers;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.widget.Toast;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.drawing.OsmPath;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.Polyline;
import org.y20k.trackbook.R;
import org.y20k.trackbook.core.Track;
import org.y20k.trackbook.core.WayPoint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

/**
 * MapHelper class
 */
public final class MapHelper {

    /* Define log tag */
    private static final String LOG_TAG = MapHelper.class.getSimpleName();


    /* Creates icon overlay for current position (used in MainActivity Fragment) */
    public static ItemizedIconOverlay createMyLocationOverlay(final Context context, Location currentBestLocation, boolean locationIsNew, boolean trackingActive) {

        final ArrayList<OverlayItem> overlayItems = new ArrayList<>();

        // create marker
        Drawable newMarker;
        if (locationIsNew && !trackingActive) {
            newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_dot_blue_24dp);
        } else if (!locationIsNew && trackingActive) {
            newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_dot_red_grey_24dp);
        } else {
                newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_dot_blue_grey_24dp);
        }

        OverlayItem overlayItem = createOverlayItem(context, currentBestLocation);
        overlayItem.setMarker(newMarker);

        // add marker to list of overlay items
        overlayItems.add(overlayItem);

        // create and return overlay for current position
        return new ItemizedIconOverlay<>(overlayItems,
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    @Override
                    public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                        // tap on My Location dot icon
                        Toast.makeText(context, item.getTitle() + " | " + item.getSnippet(), Toast.LENGTH_LONG).show();
                        return true;
                    }

                    @Override
                    public boolean onItemLongPress(final int index, final OverlayItem item) {
                        // long press on My Location dot icon
                        return true;
                    }
                }, context);
    }


    /* Creates icon overlay for track */
    public static ItemizedIconOverlay createTrackOverlay(final Context context, Track track, boolean trackingActive){

        final ArrayList<OverlayItem> overlayItems = new ArrayList<>();
        boolean currentPosition;
        final int trackSize = track.getSize();
        final List<WayPoint> wayPoints = track.getWayPoints();
        WayPoint wayPoint;

        for (int i = 0; i < track.getSize(); i++) {

            // get WayPoint and check if it is current position
            wayPoint = wayPoints.get(i);
            currentPosition = i == trackSize - 1;

            // create marker
            Drawable newMarker;

            // CASE 1: Tracking active and WayPoint is not current position
            if (trackingActive && !currentPosition) {
                if (wayPoint.getIsStopOver()) {
                    // stop over marker
                    newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_crumb_grey_24dp);
                } else {
                    // default marker for this case
                    newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_crumb_red_24dp);
                }
            }

            // CASE 2: Tracking active and WayPoint is current position
            else if (trackingActive && currentPosition) {
                if (wayPoint.getIsStopOver()) {
                    // stop over marker
                    newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_dot_blue_grey_24dp);
                } else {
                    // default marker for this case
                    newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_dot_red_24dp);
                }
            }

            // CASE 3: Tracking not active and WayPoint is not current position
            else if (!trackingActive && !currentPosition) {
                if (wayPoint.getIsStopOver()) {
                    // stop over marker
                    newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_crumb_grey_24dp);
                } else {
                    // default marker for this case
                    newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_crumb_blue_24dp);
                }
            }

            // CASE 4: Tracking not active and WayPoint is current position
            else {
                // default marker
                newMarker = ContextCompat.getDrawable(context, R.drawable.ic_my_location_crumb_blue_24dp);
            }

            // create overlay item
            OverlayItem overlayItem = createOverlayItem(context, wayPoint.getLocation());
            overlayItem.setMarker(newMarker);

            // add marker to list of overlay items
            overlayItems.add(overlayItem);
        }

        // return overlay for current position
        return new ItemizedIconOverlay<>(overlayItems,
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    @Override
                    public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                        // tap on waypoint
                        Toast.makeText(context, item.getTitle(), Toast.LENGTH_LONG).show();
                        return true;
                    }

                    @Override
                    public boolean onItemLongPress(final int index, final OverlayItem item) {
                        // long press on waypoint
                        Toast.makeText(context, item.getSnippet(), Toast.LENGTH_LONG).show();
                        return true;
                    }

                }, context);
    }


    /* Creates a marker overlay item */
    private static OverlayItem createOverlayItem(Context context, Location location) {
        // create content of overlay item
        String time = SimpleDateFormat.getTimeInstance(SimpleDateFormat.MEDIUM, Locale.getDefault()).format(location.getTime());
        final String title = context.getString(R.string.marker_description_source) + ": " + location.getProvider() + " | " + context.getString(R.string.marker_description_time) + ": " + time;
        final String description = context.getString(R.string.marker_description_accuracy) + ": " + location.getAccuracy();
        final GeoPoint position = new GeoPoint(location.getLatitude(),location.getLongitude());

        return new OverlayItem(title, description, position);
    }

    /* Connect all the waypoints of a Track object with lines */

    @RequiresApi(api = Build.VERSION_CODES.O) //Color.valueOf() requires API-level >= 26
    public static Polyline createOverlayPath(Track track) {

        Polyline path = new Polyline();

        // Create a Geopoint for every wayPoint
        for(WayPoint wayPoint : track.getWayPoints()) {
            // Get location from waypoint
            Location location = wayPoint.getLocation();
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            GeoPoint geoPoint = new GeoPoint(latitude, longitude);

            // Add it to the polyline
            path.addPoint(geoPoint);
        }

        // Assign a random color to a path
        Math.random();
        float r = (float) (Math.random()) * 255 + 1;
        float g = (float) (Math.random()) * 255 + 1;
        float b = (float) (Math.random()) * 255 + 1;

        Color pathColor = Color.valueOf(r, g, b);
        path.setColor(pathColor.toArgb());

        return path;
    }

}