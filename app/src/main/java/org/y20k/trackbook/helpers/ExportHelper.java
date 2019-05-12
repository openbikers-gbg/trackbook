/**
 * ExportHelper.java
 * Implements the ExportHelper class
 * A ExportHelper can convert Track object into a GPX string
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
import android.content.Intent;
import android.location.Location;
import android.os.Environment;
import android.widget.Toast;

import org.y20k.trackbook.R;
import org.y20k.trackbook.core.Track;
import org.y20k.trackbook.core.WayPoint;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * ExportHelper class
 */
public final class ExportHelper extends FileProvider implements TrackbookKeys {

    /* Define log tag */
    private static final String LOG_TAG = ExportHelper.class.getSimpleName();


    /* Checks if a GPX file for given track is already present */
    public static boolean gpxFileExists(Track track) {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return createFile(track, folder).exists();
    }


    /* Exports given track to GPX */
    public static boolean exportToGpx(Context context, Track track) {
        // get file for given track
        File gpxFile = createFile(track, getDownloadFolder());

        // get GPX string representation for given track
        String gpxString = createGpxString(track);

        // write GPX file
        if (writeGpxToFile(gpxString, gpxFile)) {
            String toastMessage = context.getResources().getString(R.string.toast_message_export_success) + " " + gpxFile.toString();
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
            return true;
        } else {
            String toastMessage = context.getResources().getString(R.string.toast_message_export_fail) + " " + gpxFile.toString();
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /* Exports given track to JSON */
    public static boolean exportToJSON(Context context, Track track) {
        // create file in Cache directory for given track
        File jsonFile = new File(context.getCacheDir(), track.getmTrackName() + ".json");

        // get GPX string representation for given track
        String jsonString = createJsonString(track);

        LogHelper.v(LOG_TAG, "Saving track to JSON: " + jsonString);

        // write JSON file
        if(writeJSONToFile(jsonString, jsonFile)) {
            String toastMessage = context.getResources().getString(R.string.toast_message_export_json_success) + " " + jsonFile.toString();
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
            return true;
        } else {
            String toastMessage = context.getResources().getString(R.string.toast_message_export_fail) + " " + jsonFile.toString();
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static String createJsonString(Track track) {
        // convert track to JSON
        Gson gson = getCustomGson();
        String json = gson.toJson(track);
        return json;
    }

    /*  Creates a Gson object */
    private static Gson getCustomGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setDateFormat("M/d/yy hh:mm a");
        return gsonBuilder.create();
    }

    /* Creates Intent used to bring up an Android share sheet */
    public static Intent getJSONFileIntent(Context context, Track track) {

        // create file in Cache directory for given track
        File jsonFile = new File(context.getCacheDir(), "openbikers" + ".json");


        // get Json string representation for given track
        String jsonString = createJsonString(track);

        // write GPX file
        if (writeJSONToFile(jsonString, jsonFile)) {
            String toastMessage = context.getResources().getString(R.string.toast_message_export_json_success) + " " + jsonFile.toString();
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
        } else {
            String toastMessage = context.getResources().getString(R.string.toast_message_export_fail) + " " + jsonFile.toString();
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
        }

        // create intent
        String authority = "org.y20k.trackbook.exporthelper.provider";
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setDataAndType(FileProvider.getUriForFile(context, authority, jsonFile), "application/gpx+xml");
        intent.setType("application/gpx+xml");
        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, authority, jsonFile));

        return intent;
    }


    /* Empties the internal chache directory */
    public static void emptyCacheDirectory(Context context) {
        // todo implement a date check - delete only stuff that is a week old
        File[] cacheFiles = context.getCacheDir().listFiles();
        for (File file: cacheFiles) {
            file.delete();
        }
    }


    /* Get "Download" folder */
    private static File getDownloadFolder() {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (folder != null && !folder.exists()) {
            LogHelper.v(LOG_TAG, "Creating new folder: " + folder.toString());
            folder.mkdirs();
        }
        return folder;
    }


    /* Return a GPX filepath for a given track */
    private static File createFile(Track track, File folder) {
        Date recordingStart = track.getRecordingStart();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);

        return new File(folder, dateFormat.format(recordingStart) + FILE_TYPE_GPX_EXTENSION);
    }


    /* Writes given GPX string to given file */
    private static boolean writeGpxToFile (String gpxString, File gpxFile) {
        // write track
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(gpxFile))) {
            LogHelper.v(LOG_TAG, "Saving track to external storage: " + gpxFile.toString());
            bw.write(gpxString);
            return true;
        } catch (IOException e) {
            LogHelper.e(LOG_TAG, "Unable to saving track to external storage (IOException): " + gpxFile.toString());
            return false;
        }
    }

    /* Writes given GPX string to given file */
    private static boolean writeJSONToFile (String jsonString, File jsonFile) {
        // write track
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(jsonFile))) {
            LogHelper.v(LOG_TAG, "Saving track to external storage: " + jsonFile);
            LogHelper.v(LOG_TAG, "data: " + jsonString);
            bw.write(jsonString);
            return true;
        } catch (IOException e) {
            LogHelper.e(LOG_TAG, "Unable to saving track to external storage (IOException): " + jsonFile.toString());
            return false;
        }
    }


    /* Creates GPX formatted string */
    private static String createGpxString(Track track) {
        String gpxString;

        // add header
        gpxString = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n" +
                    "<gpx version=\"1.1\" creator=\"Transistor App (Android)\"\n" +
                    "     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n";

        // add track
        gpxString = gpxString + addTrack(track);

        // add closing tag
        gpxString = gpxString + "</gpx>\n";

        return gpxString;
    }


    /* Creates Track */
    private static String addTrack(Track track) {
        StringBuilder gpxTrack = new StringBuilder("");
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // add opening track tag
        gpxTrack.append("\t<trk>\n");

        // add name to track
        gpxTrack.append("\t\t<name>");
        gpxTrack.append("Trackbook Recording");
        gpxTrack.append("</name>\n");

        // add opening track segment tag
        gpxTrack.append("\t\t<trkseg>\n");

        // add route point
        for (WayPoint wayPoint:track.getWayPoints()) {
            // get location from waypoint
            Location location = wayPoint.getLocation();

            // add longitude and latitude
            gpxTrack.append("\t\t\t<trkpt lat=\"");
            gpxTrack.append(location.getLatitude());
            gpxTrack.append("\" lon=\"");
            gpxTrack.append(location.getLongitude());
            gpxTrack.append("\">\n");

            // add time
            gpxTrack.append("\t\t\t\t<time>");
            gpxTrack.append(dateFormat.format(new Date(location.getTime())));
            gpxTrack.append("</time>\n");

            // add altitude
            gpxTrack.append("\t\t\t\t<ele>");
            gpxTrack.append(location.getAltitude());
            gpxTrack.append("</ele>\n");

            // add closing tag
            gpxTrack.append("\t\t\t</trkpt>\n");
        }

        // add closing track segment tag
        gpxTrack.append("\t\t</trkseg>\n");

        // add closing track tag
        gpxTrack.append("\t</trk>\n");

        return gpxTrack.toString();
    }

}
