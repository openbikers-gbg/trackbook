package org.y20k.trackbook

import androidx.fragment.app.Fragment
import org.y20k.trackbook.helpers.TrackbookKeys
import android.os.Bundle
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import org.y20k.trackbook.core.Track

import org.y20k.trackbook.helpers.StorageHelper
import org.y20k.trackbook.helpers.LogHelper
import java.io.File
import android.R
import kotlinx.android.synthetic.main.list_item.*
import android.graphics.Movie





class MainActivityTrackFragment : Fragment(), TrackbookKeys {
    var tracks = mutableListOf<Track>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rv = RecyclerView(context!!)
        rv.layoutManager = LinearLayoutManager(context)
        val storageHelper = StorageHelper(context)
        var files = storageHelper.listOfTrackbookFiles
        for (file in files) {
            val track = storageHelper.loadTrack(file)
            tracks.add(track)
        }
        rv.adapter = RVAdapter(tracks)
        return rv
    }

    /**
     * Adapter for the RecyclerView
     */
    inner class RVAdapter(private val trackList: List<Track>) : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LinearLayout(parent.context)
            LogHelper.v("viewis:", view.toString())
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = trackList.get(position)
            holder.routeName?.setText("Track")
            holder.date?.setText(track.recordingStart.toString())
            holder.distance?.setText(track.trackDistance.toString())
        }

        override fun getItemCount(): Int {
            return trackList.size
        }
    }

    /**
     * ViewHolder for the RecyclerView
     */
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var routeName = RouteName
        var date = Date
        var distance = Distance

        /*init {
            var routeName = RouteName as TextView
            var date = Date as TextView
            var distance = Distance as TextView
        }*/
    }
}