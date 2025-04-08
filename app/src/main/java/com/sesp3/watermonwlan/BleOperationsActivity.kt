package com.sesp3.watermonwlan

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.sesp3.watermonwlan.ble.ConnectionEventListener
import com.sesp3.watermonwlan.ble.ConnectionManager
import com.sesp3.watermonwlan.ble.ConnectionManager.parcelableExtraCompat
import com.sesp3.watermonwlan.ble.isIndicatable
import com.sesp3.watermonwlan.ble.isNotifiable
import com.sesp3.watermonwlan.ble.isReadable
import com.sesp3.watermonwlan.ble.isWritable
import com.sesp3.watermonwlan.ble.isWritableWithoutResponse
import com.sesp3.watermonwlan.ble.toHexString
import com.sesp3.watermonwlan.databinding.ActivityBleOperationsBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@SuppressLint("MissingPermission")
class BleOperationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBleOperationsBinding

    private val device: BluetoothDevice by lazy {
        intent.parcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")
    }

    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    private val characteristicProperties by lazy {
        characteristics.associateWith { characteristic ->
            mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }
    }

    private val characteristicAdapter: CharacteristicAdapter by lazy {
        CharacteristicAdapter(characteristics)
    }

    private val notifyingCharacteristics = mutableListOf<UUID>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionManager.registerListener(connectionEventListener)

        binding = ActivityBleOperationsBinding.inflate(layoutInflater)

        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.ble_playground)
        }

        setupRecyclerView()
        setupEventListener()
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        binding.characteristicsRecyclerView.apply {
            adapter = characteristicAdapter
            layoutManager = LinearLayoutManager(
                this@BleOperationsActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false

            itemAnimator.let {
                if (it is SimpleItemAnimator) {
                    it.supportsChangeAnimations = false
                }
            }
        }
    }

    private fun setupEventListener() {
        binding.sendCommand.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select an action to perform")
                .setItems(
                    arrayOf(
                        "Connect to WiFi",
                        "Forgot WiFi",
                        "Set Server",
                    )
                ) { _, i ->
                    when (i) {
                        0 -> {
                            showCredentialPayloadDialog()
                        }

                        1 -> {
                            this.writeCharacteristic(JSONObject().apply {
                                put("description", "forgot")
                            }.toString().replace("\"", "\\\""))
                        }

                        2 -> {
                            showEndpointDialog()
                        }
                    }
                }
                .show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        val formattedMessage = "${dateFormatter.format(Date())}: $message"
        runOnUiThread {
            val uiText = binding.logTextView.text
            val currentLogText = uiText.ifEmpty { "Beginning of log." }
            binding.logTextView.text = "$currentLogText\n$formattedMessage"
            binding.logScrollView.post { binding.logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun showCredentialPayloadDialog() {
        val wlanCredential = layoutInflater.inflate(R.layout.wlan_credential_payload, null)

        val wlanSsid = wlanCredential.findViewById<EditText>(R.id.text_ssid)
        val wlanPassword = wlanCredential.findViewById<EditText>(R.id.text_password)

        AlertDialog.Builder(this)
            .setView(wlanCredential)
            .setPositiveButton("Write") { _, _ ->
                this.writeCharacteristic(JSONObject().apply {
                    put("ssid", wlanSsid.text.toString())
                    put("passwd", wlanPassword.text.toString())
                }.toString().replace("\"", "\\\""))
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
                wlanSsid.showKeyboard()
                show()
            }
    }

    private fun showEndpointDialog() {
        val wlanUriLayout = layoutInflater.inflate(R.layout.wlan_base_url_payload, null)

        val wlanBaseUrl = wlanUriLayout.findViewById<EditText>(R.id.text_base_uri)
        val wlanBaseUrlMeta = wlanUriLayout.findViewById<EditText>(R.id.text_base_uri_meta)

        AlertDialog.Builder(this)
            .setView(wlanUriLayout)
            .setPositiveButton("Write") { _, _ ->
                this.writeCharacteristic(JSONObject().apply {
                    put("serverUrl", wlanBaseUrl.text.toString())
                    put("serverUrlMetadata", wlanBaseUrlMeta.text.toString())
                }.toString().replace("\"", "\\\""))
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
                wlanBaseUrl.showKeyboard()
                show()
            }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    AlertDialog.Builder(this@BleOperationsActivity)
                        .setTitle("Disconnected")
                        .setMessage("Disconnected from device.")
                        .setPositiveButton("OK") { _, _ -> onBackPressedDispatcher.onBackPressed() }
                        .show()
                }
            }

            onCharacteristicRead = { _, characteristic, value ->
                log("Read from ${characteristic.uuid}: ${value.toHexString()}")
            }

            onCharacteristicWrite = { _, characteristic ->
                log("Wrote to ${characteristic.uuid}")
            }

            onMtuChanged = { _, mtu ->
                log("MTU updated to $mtu")
            }

            onCharacteristicChanged = { _, characteristic, value ->
                log("Value changed on ${characteristic.uuid}: ${value.toHexString()}")
            }

            onNotificationsEnabled = { _, characteristic ->
                log("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                log("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun EditText.showKeyboard() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun writeCharacteristic(gson: String){
        val characteristic = characteristics.first { characteristic -> characteristic.isWritable() }
        val gsonHex = gson.toByteArray().joinToString("") { "%02x".format(it) }

        log("Writing to ${characteristic.uuid}: ${gsonHex.hexToBytes()}")
        ConnectionManager.writeCharacteristic(device, characteristic, gsonHex.hexToBytes())
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()
}
