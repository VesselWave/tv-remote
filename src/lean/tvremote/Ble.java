package lean.tvremote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;

/**
 * Bluetooth LE wake-up, as LG ThinQ 5.2.12500 sends it (ConnectSdkHandler.powerOnBLEWakeup →
 * BleAdvertiser.start). A TV in standby listens for an LG advertisement that names the TV's
 * Bluetooth MAC and an address registered earlier via ssap://com.webos.service.asc/registerDevice.
 */
final class Ble {
    /** LG Electronics' Bluetooth SIG company identifier. */
    private static final int LG_MANUFACTURER_ID = 0x00C4;
    /** LECCP control service (BleModel.BleService.UUID_LECCP_CONTROL). */
    private static final ParcelUuid LECCP_CONTROL = ParcelUuid.fromString("0000feba-0000-1000-8000-00805f9b34fb");
    /** LeccpControlAdvertisingInfo.CONTROL_WAKEUP. */
    private static final byte CONTROL_WAKEUP = 0;

    private final Context ctx;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback callback;

    Ble(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Manufacturer data: ownAddress[6] ‖ length ‖ CONTROL_WAKEUP ‖ tvBtMac[6] (LeccpControlAdvertisingInfo.getBytes). */
    static byte[] wakePayload(String ownAddress, String tvBtMac) {
        byte[] own = mac(ownAddress), tv = mac(tvBtMac);
        byte[] p = new byte[6 + 2 + 6];
        System.arraycopy(own, 0, p, 0, 6);
        p[6] = (byte) (1 + tv.length); // ControlData length: id + data
        p[7] = CONTROL_WAKEUP;
        System.arraycopy(tv, 0, p, 8, 6);
        return p;
    }

    static boolean bluetoothOn(Context ctx) {
        BluetoothAdapter a = adapter(ctx);
        return a != null && a.isEnabled();
    }

    /** Starts advertising; returns an error to show, or null. Needs BLUETOOTH_ADVERTISE on Android 12+. */
    String start(String ownAddress, String tvBtMac) {
        stop();
        BluetoothAdapter a = adapter(ctx);
        if (a == null) return "This phone has no Bluetooth";
        if (!a.isEnabled()) return "Bluetooth is off";
        BluetoothLeAdvertiser adv = a.getBluetoothLeAdvertiser();
        if (adv == null) return "This phone can't send Bluetooth wake-up";
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .addManufacturerData(LG_MANUFACTURER_ID, wakePayload(ownAddress, tvBtMac))
                .addServiceUuid(LECCP_CONTROL)
                .build();
        AdvertiseCallback cb = new AdvertiseCallback() {
            @Override public void onStartFailure(int errorCode) {
                if (callback == this) callback = null;
            }
        };
        try {
            adv.startAdvertising(settings, data, cb);
        } catch (SecurityException e) {
            return "Allow Nearby devices to wake the TV over Bluetooth";
        }
        advertiser = adv;
        callback = cb;
        return null;
    }

    void stop() {
        if (advertiser != null && callback != null) {
            try {
                advertiser.stopAdvertising(callback);
            } catch (RuntimeException ignored) {
                // Bluetooth turned off or permission revoked meanwhile
            }
        }
        advertiser = null;
        callback = null;
    }

    private static BluetoothAdapter adapter(Context ctx) {
        BluetoothManager m = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        return m != null ? m.getAdapter() : null;
    }

    private static byte[] mac(String s) {
        String[] parts = s.split("[:-]");
        byte[] m = new byte[6];
        for (int i = 0; i < 6; i++) m[i] = (byte) Integer.parseInt(parts[i], 16);
        return m;
    }
}
