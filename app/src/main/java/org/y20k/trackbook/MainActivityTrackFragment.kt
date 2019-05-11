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


class MainActivityTrackFragment : Fragment(), TrackbookKeys {
    // dumb data to populate the recyclerView
    var strings = arrayOf("route1", "route2", "route3", "route4", "route5", "route6", "route7")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rv = RecyclerView(context!!)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = RVAdapter(strings)
        return rv
    }

    /**
     * Adapter for the RecyclerView
     */
    inner class RVAdapter(private val dataSource: Array<String>) : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = TextView(parent.context)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = dataSource[position]
        }

        override fun getItemCount(): Int {
            return dataSource.size
        }
    }

    /**
     * ViewHolder for the RecyclerView
     */
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView

        init {
            textView = itemView as TextView
        }
    }
}