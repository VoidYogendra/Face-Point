package com.avoid.facepoint.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.avoid.facepoint.R
import com.avoid.facepoint.model.FilterItem
import com.google.android.material.imageview.ShapeableImageView

class ButtonAdapter(val dataSet: Array<FilterItem>) : RecyclerView.Adapter<ButtonAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ShapeableImageView = view.findViewById(R.id.IvBtn_Filter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.filter_adapter, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.image.setImageResource(dataSet[position].drawable)
        Log.e("onBindViewHolder", "getItemViewType: $position", )
    }

}