package edu.mdc.csclub.ibeaconsurveying;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.os.Handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    // Stops scanning after 10 * 1000 milliseconds.
    private static final long SCAN_PERIOD = 1000;
    private static final int NUM_LOGS = 10;

    //app state
    private boolean mScanning;
    private Handler mHandler;
    private String X;
    private String Y;
    private int beacon1RSSI;
    private int beacon2RSSI;
    private int beacon3RSSI;
    private int beacon4RSSI;
    private int beacon5RSSI;
    private int beacon6RSSI;
    private int numLogs;

    //UI components
    private Button logButton;
    private Button sendLogButton;
    private Spinner XSpinner;
    private Spinner YSpinner;
    private ProgressBar progressBar;

    //Bluetooth objects:
    //For all APIs (<21 and >=21, >=23)
    private BluetoothAdapter mBluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;
    //For API >=23, dynamic permissions are needed
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    //For API >=21
    private BluetoothLeScanner mLEScanner;
    private ScanSettings scanSettings;
    private List<ScanFilter> filters;
    private ScanCallback newerVersionScanCallback;
    //For API < 21
    private BluetoothAdapter.LeScanCallback olderVersionScanCallback;

    // file
    private String filename = "measurements.csv";
    private String dirName = "measurements";
    private File file;
    private FileOutputStream outputStream;


    ///////////////////////////////////////////////////////////////  App Lifecycle Methods ///////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Set up UI components
        setContentView(R.layout.activity_main);
        logButton = (Button) findViewById(R.id.logButton);
        sendLogButton = (Button) findViewById(R.id.sendLogButton);
        XSpinner = (Spinner) findViewById(R.id.XSpinner);
        XSpinner.setOnItemSelectedListener(this);
        YSpinner = (Spinner) findViewById(R.id.YSpinner);
        YSpinner.setOnItemSelectedListener(this);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);


        //init Bluetooth objects and permissions
        initBLESetup();

        mHandler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        progressBar.setVisibility(View.INVISIBLE);

        logButton.setText(R.string.start_log_message);

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT); // See onActivityResult callback method for negative behavior
        } else {
            setupBLEScan();
        }


        try {
            File rootDir = new File(getFilesDir(), dirName);
            rootDir.mkdirs();
            file = new File(rootDir, filename);
            outputStream = new FileOutputStream(file);

            //outputStream = openFileOutput(filename, MODE_APPEND);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        beacon1RSSI = 0;
        beacon2RSSI = 0;
        beacon3RSSI = 0;
        beacon4RSSI = 0;
        beacon5RSSI = 0;
        beacon6RSSI = 0;

    }

    @Override
    protected void onPause(){

        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.onPause();

    }

    @Override
    protected void onDestroy() {
        File file = new File(getFilesDir(), filename);
        file.delete();
        super.onDestroy();
    }

    ///////////////////////////////////////////////////////////////  UI  methods ///////////////////////////////////////////////////////////////

    /**
     * Called when the user taps the Log button
     */
    public void log(View view) {
        Log.i(TAG, "LOGGING FOR " + X + ", " + Y);
        logButton.setText(R.string.logging_message);
        logButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        numLogs = 0;
        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                numLogs++;
                if (numLogs < NUM_LOGS){
                    log();
                    mHandler.postDelayed(this, SCAN_PERIOD);
                } else {
                    mScanning = false;
                    scanBLEDevice(false);
                    logButton.setText(R.string.start_log_message);
                    logButton.setEnabled(true);
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
        }, SCAN_PERIOD);

        mScanning = true;
        scanBLEDevice(true);

    }

    /**
     * Called when the user taps the Scan button
     */
    public void sendLog(View view) {


        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);

        Uri fileUri = null;

        try {
            fileUri = FileProvider.getUriForFile( this,
                    "edu.mdc.csclub.ibeaconsurveying.fileprovider",
                    file);
        } catch (IllegalArgumentException e) {
            Log.e("File Selector",
                    e.toString());

        }

        shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setType("text/csv");
        startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.send_to)));

    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        Spinner spinner = (Spinner) parent;
        if(spinner.getId() == R.id.XSpinner) {
            X = parent.getItemAtPosition(pos).toString();
        }
        else if(spinner.getId() == R.id.YSpinner) {
            Y = parent.getItemAtPosition(pos).toString();
        }

    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    /////////////////////////////////////////////////////////////// Bluetooth methods ///////////////////////////////////////////////////////////////
    private void initBLESetup() {
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        //Beginning in Android 6.0 (API level 23), users grant permissions to apps while the app is running, not when they install the app.
        //Obtaining dynamic permissions from the user
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //23
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access.");
                builder.setMessage("Please grant location access to this app.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }

                });// See onRequestPermissionsResult callback method for negative behavior
                builder.show();
            }
        }
        // Create callback methods
        if (Build.VERSION.SDK_INT >= 21) //LOLLIPOP
            newerVersionScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        BluetoothDevice btDevice = result.getDevice();
                        ScanRecord mScanRecord = result.getScanRecord();
                        int rssi = result.getRssi();
//                        Log.i(TAG, "Address: "+ btDevice.getAddress());
//                        Log.i(TAG, "TX Power Level: " + result.getScanRecord().getTxPowerLevel());
//                        Log.i(TAG, "RSSI in DBm: " + rssi);
//                        Log.i(TAG, "Manufacturer data: "+ mScanRecord.getManufacturerSpecificData());
//                        Log.i(TAG, "device name: "+ mScanRecord.getDeviceName());
//                        Log.i(TAG, "Advertise flag: "+ mScanRecord.getAdvertiseFlags());
//                        Log.i(TAG, "service uuids: "+ mScanRecord.getServiceUuids());
//                        Log.i(TAG, "Service data: "+ mScanRecord.getServiceData());
                        byte[] recordBytes = mScanRecord.getBytes();

                        iBeacon ib = parseBLERecord(recordBytes);

                        if (ib != null) {
                            setLog(ib.getUuid(), ib.getMajor(), ib.getMinor(), rssi);

                        }
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e("Scan Failed", "Error Code: " + errorCode);
                }
            };
        else
            olderVersionScanCallback =
                    new BluetoothAdapter.LeScanCallback() {
                        @Override
                        public void onLeScan(final BluetoothDevice device, final int rssi,
                                             final byte[] scanRecord) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.i("onLeScan", device.toString());

                                    iBeacon ib = parseBLERecord(scanRecord);

                                    if (ib != null) {
                                       setLog(ib.getUuid(), ib.getMajor(), ib.getMinor(), rssi);

                                    }
                                }
                            });
                        }
                    };
    }

    private void setupBLEScan() {
        //Android 4.3 (JELLY_BEAN_MR2) introduced platform support for Bluetooth Low Energy (Bluetooth LE) in the central role.
        // In Android 5.0 (LOLLIPOP, 21), an Android device can now act as a Bluetooth LE peripheral device. Apps can use this capability to make their presence known to nearby devices.
        // There was a new android.bluetooth.le API!!!
        if (Build.VERSION.SDK_INT >= 21) {//LOLLIPOP
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<ScanFilter>();
            ScanFilter.Builder mBuilder = new ScanFilter.Builder();
            ByteBuffer mManufacturerData = ByteBuffer.allocate(23);
            ByteBuffer mManufacturerDataMask = ByteBuffer.allocate(24);
            byte[] uuid = getIdAsByte(UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B575555"));
            mManufacturerData.put(0, (byte) 0xBE);
            mManufacturerData.put(1, (byte) 0xAC);
            for (int i = 2; i <= 17; i++) {
                mManufacturerData.put(i, uuid[i - 2]);
            }
            for (int i = 0; i <= 17; i++) {
                mManufacturerDataMask.put((byte) 0x01);
            }
            mBuilder.setManufacturerData(76, mManufacturerData.array(), mManufacturerDataMask.array());
            ScanFilter mScanFilter = mBuilder.build();
            //TODO
            //filters.add(mScanFilter);

        }
    }

    private void scanBLEDevice(final boolean enable) {
        if (enable) {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(olderVersionScanCallback);
            } else {
                mLEScanner.startScan(filters, scanSettings, newerVersionScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(olderVersionScanCallback);
            } else {
                mLEScanner.stopScan(newerVersionScanCallback);
            }
        }
    }

    /////////////////////////////////////////////////////////////// Utility methods   ///////////////////////////////////////////////////////////////

    private void setLog(String uuid, int major, int minor, int rssi) {
        if("B9407F30-F5F8-466E-AFF9-25556B575555".equalsIgnoreCase(uuid)){
            if (major == 1){
                if (minor == 1){ //beacon1
                    beacon1RSSI = rssi;
                } else if (minor == 2){ //beacon2
                    beacon2RSSI = rssi;
                }
            } else if (major == 2){
                if (minor == 1){ //beacon3
                    beacon3RSSI = rssi;
                } else if (minor == 2){ //beacon4
                    beacon4RSSI = rssi;
                }
            } else if (major == 3){
                if (minor == 1){ //beacon5
                    beacon5RSSI = rssi;
                } else if (minor == 2){ //beacon6
                    beacon6RSSI = rssi;
                }
            }
        }
    }

    private void log() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(X).append(",").append(Y).append(",")
                    .append(beacon1RSSI).append(",").append(beacon2RSSI).append(",")
                    .append(beacon3RSSI).append(",").append(beacon4RSSI).append(",")
                    .append(beacon5RSSI).append(",").append(beacon6RSSI).append('\n');

            outputStream.write(sb.toString().getBytes());
            Log.i(TAG, "LOGGING " + sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] getIdAsByte(java.util.UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private iBeacon parseBLERecord(byte[] scanRecord) {
        iBeacon ib = null;
        String record = scanRecord.toString();
        Log.i(TAG, "record: " + record);
        int startByte = 2;
        boolean patternFound = false;
        while (startByte <= 5) {
            if (((int) scanRecord[startByte + 2] & 0xff) == 0x02 && //Identifies an iBeacon
                    ((int) scanRecord[startByte + 3] & 0xff) == 0x15) { //Identifies correct data length
                patternFound = true;
                break;
            }
            startByte++;
        }
        if (patternFound) {
            //Convert to hex String
            byte[] uuidBytes = new byte[16];
            System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16);
            String hexString = bytesToHex(uuidBytes);

            //Here is your UUID
            String uuid = hexString.substring(0, 8) + "-" +
                    hexString.substring(8, 12) + "-" +
                    hexString.substring(12, 16) + "-" +
                    hexString.substring(16, 20) + "-" +
                    hexString.substring(20, 32);

            //Here is your Major value
            int major = (scanRecord[startByte + 20] & 0xff) * 0x100 + (scanRecord[startByte + 21] & 0xff);

            //Here is your Minor value
            int minor = (scanRecord[startByte + 22] & 0xff) * 0x100 + (scanRecord[startByte + 23] & 0xff);
            Log.i(TAG, "uuid: " + hexString + ", major: " + major + ", minor: " + minor);

            ib = new iBeacon(uuid, major, minor);

        }
        return ib;

    }

    /**
     * bytesToHex method
     * http://stackoverflow.com/a/9855338
     */
    static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


}
