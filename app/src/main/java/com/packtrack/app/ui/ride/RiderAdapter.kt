package com.packtrack.app.ui.ride

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.packtrack.app.databinding.ItemRiderBinding
import com.packtrack.app.model.Rider

class RiderAdapter(private val myRiderId: String) :
    ListAdapter<Rider, RiderAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemRiderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rider: Rider) {
            binding.tvRiderName.text = if (rider.id == myRiderId) "${rider.name} (You)" else rider.name
            binding.tvRiderDist.text = if (rider.distanceToDestinationKm > 0)
                String.format("%.1f km", rider.distanceToDestinationKm) else "—"

            val (color, label) = when (rider.status) {
                Rider.STATUS_HALTED -> Pair(Color.parseColor("#FFB300"), "HALTED")
                Rider.STATUS_EMERGENCY -> Pair(Color.parseColor("#D9383A"), "EMERGENCY")
                Rider.STATUS_REGROUP -> Pair(Color.parseColor("#FF6F00"), "REGROUP")
                else -> Pair(Color.parseColor("#4CAF50"), "MOVING")
            }
            binding.viewStatusDot.setBackgroundColor(color)
            binding.tvRiderStatus.text = label
            binding.tvRiderStatus.setTextColor(color)

            if (rider.nudge.isNotBlank()) {
                val nudgeLabel = when (rider.nudge) {
                    Rider.NUDGE_EMERGENCY -> "SOS"
                    Rider.NUDGE_REGROUP -> "REGROUP"
                    Rider.NUDGE_ACK -> "ACK"
                    else -> ""
                }
                binding.tvNudge.text = nudgeLabel
                binding.tvNudge.visibility = android.view.View.VISIBLE
            } else {
                binding.tvNudge.visibility = android.view.View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemRiderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Rider>() {
            override fun areItemsTheSame(a: Rider, b: Rider) = a.id == b.id
            override fun areContentsTheSame(a: Rider, b: Rider) = a == b
        }
    }
}
