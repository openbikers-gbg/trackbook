package org.y20k.trackbook

import androidx.fragment.app.Fragment
import org.y20k.trackbook.helpers.TrackbookKeys
import android.os.Bundle
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import org.y20k.trackbook.core.Track

import org.y20k.trackbook.helpers.StorageHelper
import kotlinx.android.synthetic.main.list_item.*


class MainActivityTrackFragment : Fragment(), TrackbookKeys {
    var tracks = mutableListOf<Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // get activity
        val mActivity = activity
    }

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
            val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item, parent, false)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = trackList.get(position)
            holder.routeName?.text = "Track"
            holder.date?.text = track.recordingStart.toString()
            holder.distance?.text = track.trackDistance.toString()
        }

        override fun getItemCount(): Int {
            return trackList.size
        }
    }

    /**
     * ViewHolder for the RecyclerView
     */
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var routeName : TextView?
        var date : TextView?
        var distance : TextView?

        init {
            routeName = RouteName
            date = Date
            distance = Distance
        }
    }
}