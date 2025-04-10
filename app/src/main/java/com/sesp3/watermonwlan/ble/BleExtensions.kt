package com.sesp3.watermonwlan.ble

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import timber.log.Timber
import java.util.Locale
import java.util.UUID

const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Timber.i("No service and characteristic available, call discoverServices() first?")
        return
    }
    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--",
            prefix = "|--"
        ) { char ->
            var description = "${char.uuid}: ${char.printProperties()}"
            if (char.descriptors.isNotEmpty()) {
                description += "\n" + char.descriptors.joinToString(
                    separator = "\n|------",
                    prefix = "|------"
                ) { descriptor ->
                    "${descriptor.uuid}: ${descriptor.printProperties()}"
                }
            }
            description
        }
        Timber.i("Service ${service.uuid}\nCharacteristics:\n$characteristicsTable")
    }
}

fun BluetoothGatt.findCharacteristic(
    characteristicUuid: UUID,
    serviceUuid: UUID? = null
): BluetoothGattCharacteristic? {
    return if (serviceUuid != null) {
        services
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics?.firstOrNull { it.uuid == characteristicUuid }
    } else {
        services?.forEach { service ->
            service.characteristics?.firstOrNull { characteristic ->
                characteristic.uuid == characteristicUuid
            }?.let { matchingCharacteristic ->
                return matchingCharacteristic
            }
        }
        return null
    }
}

fun BluetoothGatt.findDescriptor(
    descriptorUuid: UUID,
    characteristicUuid: UUID? = null,
    serviceUuid: UUID? = null
): BluetoothGattDescriptor? {
    return if (characteristicUuid != null && serviceUuid != null) {
        services
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics?.firstOrNull { it.uuid == characteristicUuid }
            ?.descriptors?.firstOrNull { it.uuid == descriptorUuid }
    } else {
        services?.forEach { service ->
            service.characteristics.forEach { characteristic ->
                characteristic.descriptors?.firstOrNull { descriptor ->
                    descriptor.uuid == descriptorUuid
                }?.let { matchingDescriptor ->
                    return matchingDescriptor
                }
            }
        }
        return null
    }
}

fun BluetoothGattCharacteristic.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add("READABLE")
    if (isWritable()) add("WRITABLE")
    if (isWritableWithoutResponse()) add("WRITABLE WITHOUT RESPONSE")
    if (isIndicatable()) add("INDICATABLE")
    if (isNotifiable()) add("NOTIFIABLE")
    if (isEmpty()) add("EMPTY")
}.joinToString()

fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
    properties and property != 0

@SuppressLint("MissingPermission")
fun BluetoothGattCharacteristic.executeWrite(
    gatt: BluetoothGatt,
    payload: ByteArray,
    writeType: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(this, payload, writeType)
    } else {
        legacyCharacteristicWrite(gatt, payload, writeType)
    }
}

@TargetApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattCharacteristic.legacyCharacteristicWrite(
    gatt: BluetoothGatt,
    payload: ByteArray,
    writeType: Int
) {
    this.writeType = writeType
    value = payload
    gatt.writeCharacteristic(this)
}

fun BluetoothGattDescriptor.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add("READABLE")
    if (isWritable()) add("WRITABLE")
    if (isEmpty()) add("EMPTY")
}.joinToString()

fun BluetoothGattDescriptor.isReadable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_READ)

fun BluetoothGattDescriptor.isWritable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE)

fun BluetoothGattDescriptor.containsPermission(permission: Int): Boolean =
    permissions and permission != 0

@SuppressLint("MissingPermission")
fun BluetoothGattDescriptor.executeWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(this, payload)
    } else {
        legacyDescriptorWrite(gatt, payload)
    }
}

@TargetApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGattDescriptor.legacyDescriptorWrite(
    gatt: BluetoothGatt,
    payload: ByteArray
) {
    value = payload
    gatt.writeDescriptor(this)
}

fun BluetoothGattDescriptor.isCccd() =
    uuid.toString().uppercase(Locale.US) == CCC_DESCRIPTOR_UUID.uppercase(Locale.US)

fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }
