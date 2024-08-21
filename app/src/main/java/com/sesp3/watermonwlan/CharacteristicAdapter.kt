package com.sesp3.watermonwlan

import android.bluetooth.BluetoothGattCharacteristic
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sesp3.watermonwlan.ble.printProperties

class CharacteristicAdapter(
    private val items: List<BluetoothGattCharacteristic>
) : RecyclerView.Adapter<CharacteristicAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.row_characteristic,
            parent,
            false
        )
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View
    ) : RecyclerView.ViewHolder(view) {

        fun bind(characteristic: BluetoothGattCharacteristic) {
            view.findViewById<TextView>(R.id.characteristic_uuid).text =
                characteristic.uuid.toString()
            view.findViewById<TextView>(R.id.characteristic_properties).text =
                characteristic.printProperties()
        }
    }
}
