
package gr.enorasys.loramanager;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "loramanager";
    private static final String ACTION_USB_PERMISSION = "gr.enorasys.loramanager.USB_PERMISSION";
    private Spinner ebyteDeviceSpinner, worRoleSpinner, worCycleSpinner, relaySpinner;
    private Button connectButton, readRegisterButton,writeRegisterButton;
    private TextView connectionStatusTextView, infoTextView,frequencyTextView,netIDTextView,keyTextView;
    private UsbSerialPort serialPort;
    private UsbManager usbManager;
    private String enableRssi, transmissionMethod, relayFunction, lbtEnable;
    private final ExecutorService serialExecutor = Executors.newSingleThreadExecutor();

    private static class Reg3Flags {
        private final String enableRssi;
        private final String transmissionMethod;
        private final String relayFunction;
        private final String lbtEnable;

        private Reg3Flags(String enableRssi, String transmissionMethod, String relayFunction, String lbtEnable) {
            this.enableRssi = enableRssi;
            this.transmissionMethod = transmissionMethod;
            this.relayFunction = relayFunction;
            this.lbtEnable = lbtEnable;
        }
    }

    private static class RegisterData {
        private final String addh;
        private final String addl;
        private final String netId;
        private final String baudRate;
        private final String parity;
        private final String airSpeed;
        private final String packetSize;
        private final String transmitPower;
        private final String channel;
        private final int channelValue;
        private final double frequency;

        private RegisterData(
                String addh,
                String addl,
                String netId,
                String baudRate,
                String parity,
                String airSpeed,
                String packetSize,
                String transmitPower,
                String channel,
                int channelValue,
                double frequency
        ) {
            this.addh = addh;
            this.addl = addl;
            this.netId = netId;
            this.baudRate = baudRate;
            this.parity = parity;
            this.airSpeed = airSpeed;
            this.packetSize = packetSize;
            this.transmitPower = transmitPower;
            this.channel = channel;
            this.channelValue = channelValue;
            this.frequency = frequency;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        ebyteDeviceSpinner = findViewById(R.id.deviceSpinner);
        connectButton = findViewById(R.id.connectButton);
        readRegisterButton = findViewById(R.id.readRegisterButton);
        writeRegisterButton = findViewById(R.id.writeRegisterButton);
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);
        infoTextView = findViewById(R.id.infoTextView);
        frequencyTextView = findViewById(R.id.frequencyTextView);
        frequencyTextView.setText("-");
        netIDTextView = findViewById(R.id.netIdEditText);
        netIDTextView.setText("-");
        keyTextView = findViewById(R.id.keyEditText);
        keyTextView.setText("-");
        worRoleSpinner = findViewById(R.id.worRoleSpinner);
        worCycleSpinner = findViewById(R.id.worCycleSpinner);
        relaySpinner = findViewById(R.id.relaySpinner);


        initializeSpinners();

        // USB manager
        usbManager = (UsbManager) getSystemService(USB_SERVICE);

        // Register USB permission broadcast receiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        // Set up connect button
        connectButton.setOnClickListener(view -> connectToSerialPort());

        // Set up read register button
        readRegisterButton.setOnClickListener(view -> readMultipleRegisters());
        writeRegisterButton.setOnClickListener(view -> writeRegister());
    }

    private void writeRegister() {
        if (serialPort == null || !serialPort.isOpen()) {
            updateStatus("Serial port not open. Connect first.");
            return;
        }

        Spinner baudRateSpinner = findViewById(R.id.baudRateSpinner);
        Spinner airRateSpinner = findViewById(R.id.airRateSpinner);
        Spinner paritySpinner = findViewById(R.id.paritySpinner);
        Spinner packetSizeSpinner = findViewById(R.id.packetSizeSpinner);
        Spinner powerSpinner = findViewById(R.id.powerSpinner);
        Spinner channelSpinner = findViewById(R.id.channelSpinner);
        Spinner txModeSpinner = findViewById(R.id.txModeSpinner);
        Spinner relaySpinner = findViewById(R.id.relaySpinner);
        Spinner lbtSpinner = findViewById(R.id.lbtSpinner);
        Spinner packetRssiSpinner = findViewById(R.id.packetRssiSpinner);

        String baudRateSelection = baudRateSpinner.getSelectedItem().toString();
        String airRateSelection = airRateSpinner.getSelectedItem().toString();
        String paritySelection = paritySpinner.getSelectedItem().toString();
        String packetSizeSelection = packetSizeSpinner.getSelectedItem().toString();
        String powerSelection = powerSpinner.getSelectedItem().toString();
        String channelSelection = channelSpinner.getSelectedItem().toString();
        String txModeSelection = txModeSpinner.getSelectedItem().toString();
        String relaySelection = relaySpinner.getSelectedItem().toString();
        String lbtSelection = lbtSpinner.getSelectedItem().toString();
        String packetRssiSelection = packetRssiSpinner.getSelectedItem().toString();

        if ("-".equals(baudRateSelection)
                || "-".equals(airRateSelection)
                || "-".equals(paritySelection)
                || "-".equals(packetSizeSelection)
                || "-".equals(powerSelection)
                || "-".equals(channelSelection)
                || "-".equals(txModeSelection)
                || "-".equals(relaySelection)
                || "-".equals(lbtSelection)
                || "-".equals(packetRssiSelection)) {
            updateStatus("Select all configuration fields before writing.");
            return;
        }

        String netIdValue = netIDTextView.getText().toString().trim();
        String keyValue = keyTextView.getText().toString().trim();
        if (netIdValue.isEmpty() || "-".equals(netIdValue) || keyValue.isEmpty() || "-".equals(keyValue)) {
            updateStatus("Address and NetID must be set before writing.");
            return;
        }

        netIdValue = netIdValue.replaceAll("\\s+", "");
        keyValue = keyValue.replaceAll("\\s+", "");
        if (netIdValue.length() != 2 || keyValue.length() != 4) {
            updateStatus("Invalid address or NetID format.");
            return;
        }

        serialExecutor.execute(() -> {
            try {
                int addh = Integer.parseInt(keyValue.substring(0, 2), 16);
                int addl = Integer.parseInt(keyValue.substring(2, 4), 16);
                int netId = Integer.parseInt(netIdValue, 16);

                int baudRateBits = encodeBaudRate(baudRateSelection);
                int parityBits = encodeParity(paritySelection);
                int airRateBits = encodeAirRate(airRateSelection);
                int packetSizeBits = encodePacketSize(packetSizeSelection);
                int powerBits = encodeTransmitPower(powerSelection);
                int channelValue = Integer.parseInt(channelSelection);

                int reg0 = (baudRateBits << 5) | (parityBits << 3) | airRateBits;
                int reg1 = (packetSizeBits << 6) | powerBits;

                int reg3 = 0;
                if ("Enabled".equalsIgnoreCase(packetRssiSelection)) {
                    reg3 |= 0b1000_0000;
                }
                if ("Fixed-point".equalsIgnoreCase(txModeSelection) || "Fixed-point".equalsIgnoreCase(txModeSelection.trim())) {
                    reg3 |= 0b0100_0000;
                }
                if ("Enabled".equalsIgnoreCase(relaySelection)) {
                    reg3 |= 0b0010_0000;
                }
                if ("Enabled".equalsIgnoreCase(lbtSelection)) {
                    reg3 |= 0b0001_0000;
                }

                byte[] writeCommand = new byte[]{
                        (byte) 0xC2,
                        (byte) 0x00,
                        (byte) 0x07,
                        (byte) addh,
                        (byte) addl,
                        (byte) netId,
                        (byte) reg0,
                        (byte) reg1,
                        (byte) channelValue,
                        (byte) reg3
                };

                serialPort.write(writeCommand, 1000);
                updateStatus("Writing registers...");

                byte[] response = new byte[64];
                int numBytesRead = serialPort.read(response, 1000);
                if (numBytesRead > 0) {
                    String hexResponse = bytesToHex(response, numBytesRead);
                    Log.d(TAG, "Write response (hex): " + hexResponse);
                }

                readMultipleRegisters();
            } catch (NumberFormatException e) {
                updateStatus("Invalid numeric selection.");
                Log.e(TAG, "Invalid numeric selection", e);
            } catch (Exception e) {
                updateStatus("Write failed: " + e.getMessage());
                Log.e(TAG, "Error writing register", e);
            }
        });
    }

    private int encodeBaudRate(String baudRate) {
        switch (baudRate) {
            case "1200 bps":
                return 0;
            case "2400 bps":
                return 1;
            case "4800 bps":
                return 2;
            case "9600 bps":
                return 3;
            case "19200 bps":
                return 4;
            case "38400 bps":
                return 5;
            case "57600 bps":
                return 6;
            case "115200 bps":
                return 7;
            default:
                throw new NumberFormatException("Unsupported baud rate");
        }
    }

    private int encodeParity(String parity) {
        switch (parity) {
            case "8N1":
                return 0;
            case "8O1":
                return 1;
            case "8E1":
                return 2;
            default:
                throw new NumberFormatException("Unsupported parity");
        }
    }

    private int encodeAirRate(String airRate) {
        switch (airRate) {
            case "0.8 kbps":
                return 0;
            case "1.2 kbps":
                return 1;
            case "2.4 kbps":
                return 2;
            case "4.8 kbps":
                return 3;
            case "9.6 kbps":
                return 4;
            case "19.2 kbps":
                return 5;
            case "38.4 kbps":
                return 6;
            case "62.5 kbps":
                return 7;
            default:
                throw new NumberFormatException("Unsupported air rate");
        }
    }

    private int encodePacketSize(String packetSize) {
        switch (packetSize) {
            case "240 Bytes":
                return 0;
            case "128 Bytes":
                return 1;
            case "64 Bytes":
                return 2;
            case "32 Bytes":
                return 3;
            default:
                throw new NumberFormatException("Unsupported packet size");
        }
    }

    private int encodeTransmitPower(String transmitPower) {
        switch (transmitPower) {
            case "22 dBm":
                return 0;
            case "17 dBm":
                return 1;
            case "13 dBm":
                return 2;
            case "10 dBm":
                return 3;
            default:
                throw new NumberFormatException("Unsupported power");
        }
    }

    private void connectToSerialPort() {
        if (serialPort != null && serialPort.isOpen()) {
            updateStatus("Already connected.");
            return;
        }

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (availableDrivers.isEmpty()) {
            updateStatus("No USB device found.");
            Log.e(TAG, "No USB devices found.");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        if (!usbManager.hasPermission(device)) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
            );
            usbManager.requestPermission(device, permissionIntent);
            updateStatus("Requesting USB permission...");
            return;
        }

        Spinner baudRateSpinner = findViewById(R.id.baudRateSpinner);
        String baudRateSelection = baudRateSpinner.getSelectedItem() != null
                ? baudRateSpinner.getSelectedItem().toString()
                : "-";
        if ("-".equals(baudRateSelection.trim())) {
            updateStatus("Select a baud rate before connecting.");
            return;
        }

        String baudRateDigits = baudRateSelection.replaceAll("[^\\d]", "");
        if (baudRateDigits.isEmpty()) {
            updateStatus("Invalid baud rate selection.");
            return;
        }

        int baudRate;
        try {
            baudRate = Integer.parseInt(baudRateDigits);
        } catch (NumberFormatException e) {
            updateStatus("Invalid baud rate selection.");
            Log.e(TAG, "Invalid baud rate selection: " + baudRateSelection, e);
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            updateStatus("Failed to open USB connection.");
            Log.e(TAG, "Failed to open USB connection for device: " + device.getDeviceName());
            return;
        }

        try {
            serialPort = driver.getPorts().get(0); // Get the first port
            serialPort.open(connection);

            serialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            updateStatus("Connected at " + baudRate + " baud.");
            Log.d(TAG, "Serial port opened successfully at " + baudRate + " baud.");
            //readRegister();
        } catch (Exception e) {
            updateStatus("Connection failed: " + e.getMessage());
            Log.e(TAG, "Serial port connection failed.", e);
            closeSerialPort();
        }
    }



    private void readReg3(Consumer<Reg3Flags> onSuccess) {
        readRegisters(0x06, 0x01, "C106", hexResponse -> {
            String reg3Hex = hexResponse.substring(6, 8); // Extract REG3 (1 byte)
            int reg3Value = Integer.parseInt(reg3Hex, 16);
            onSuccess.accept(decodeReg3(reg3Value));
        });
    }


    private void readRegisters(int startAddress, int length, String expectedHeader, Consumer<String> onSuccess) {
        serialExecutor.execute(() -> {
            if (serialPort == null || !serialPort.isOpen()) {
                updateStatus("Serial port not open. Connect first.");
                return;
            }
            try {
                // Prepare and send the read command
                byte[] readCommand = new byte[]{(byte) 0xC1, (byte) startAddress, (byte) length};
                serialPort.write(readCommand, 1000);
                updateStatus("Reading registers...");

                // Read the response
                byte[] response = new byte[64];
                int numBytesRead = serialPort.read(response, 1000);

                if (numBytesRead > 0) {
                    String hexResponse = bytesToHex(response, numBytesRead);
                    Log.d(TAG, "Raw response (hex): " + hexResponse);

                    // Validate the response header
                    if (hexResponse.startsWith(expectedHeader)) {
                        onSuccess.accept(hexResponse);
                        updateStatus("Register data updated.");
                    } else {
                        updateStatus("Unexpected response: " + hexResponse);
                    }
                } else {
                    updateStatus("No response: CHECK MODULE STATUS");
                }
            } catch (Exception e) {
                updateStatus("Read failed: " + e.getMessage());
                Log.e(TAG, "Error reading register", e);
            }
        });
    }


    private RegisterData parseRegisterData(String hexResponse) {
        try {
            // Decode fields
            String addh = hexResponse.substring(6, 8); // ADDH
            String addl = hexResponse.substring(8, 10); // ADDL
            String netId = hexResponse.substring(10, 12); // NETID
            String reg0 = hexResponse.substring(12, 14); // REG0
            String reg1 = hexResponse.substring(14, 16); // REG1
            String channelHex = hexResponse.substring(16, 18); // Channel
            // Decode REG0
            int reg0Value = Integer.parseInt(reg0, 16);
            String baudRate = decodeBaudRate((reg0Value >> 5) & 0b111);
            String parity = decodeParity((reg0Value >> 3) & 0b11);
            String airSpeed = decodeAirSpeed(reg0Value & 0b111);


            // Decode REG1
            int reg1Value = Integer.parseInt(reg1, 16);
            String packetSize = decodePacketSize((reg1Value >> 6) & 0b11);
            String transmitPower = decodeTransmitPower(reg1Value & 0b11);

            // Decode channel
            int channelValue = Integer.parseInt(channelHex, 16);
            double frequency = 410.125 + channelValue; // Calculate actual frequency (MHz)
            String channel = String.valueOf(channelValue);
            return new RegisterData(
                    addh,
                    addl,
                    netId,
                    baudRate,
                    parity,
                    airSpeed,
                    packetSize,
                    transmitPower,
                    channel,
                    channelValue,
                    frequency
            );
        } catch (Exception e) {
            Log.e(TAG, "Error parsing register response", e);
            updateStatus("Error parsing response: " + e.getMessage());
        }
        return null;
    }

    private Reg3Flags decodeReg3(int reg3Value) {
        // Decode individual fields
        enableRssi = ((reg3Value >> 7) & 0b1) == 1 ? "Enabled" : "Disabled";
        transmissionMethod = ((reg3Value >> 6) & 0b1) == 1 ? "Fixed-point" : "Transparent";
        relayFunction = ((reg3Value >> 5) & 0b1) == 1 ? "Enabled" : "Disabled";
        lbtEnable = ((reg3Value >> 4) & 0b1) == 1 ? "Enabled" : "Disabled";
        return new Reg3Flags(enableRssi, transmissionMethod, relayFunction, lbtEnable);
    }

    private void readMultipleRegisters() {
        readRegisters(0x00, 0x06, "C10006", hexResponse -> {
            RegisterData data = parseRegisterData(hexResponse);
            if (data == null) {
                return;
            }
            readReg3(flags -> readProductInformation(info -> updateUiWithRegisterData(data, flags, info)));
        });
    }

    private void updateUiWithRegisterData(RegisterData data, Reg3Flags flags, String productInfo) {
        runOnUiThread(() -> {
            String safeProductInfo = productInfo == null ? "Model: Unknown\nVersion: Unknown" : productInfo;
            String info = safeProductInfo + "\n" +
                    "Frequency: " + data.frequency + "\n" +
                    "Address: 0x" + data.addh + data.addl + "\n" +
                    "Network ID: " + data.netId + "\n" +
                    "Packet Size: " + data.packetSize + "\n" +
                    "Baud Rate: " + data.baudRate + "\n" +
                    "Parity: " + data.parity + "\n" +
                    "Air Speed: " + data.airSpeed + "\n" +
                    "Transmit Power: " + data.transmitPower + "\n" +
                    "Channel: " + data.channelValue + "\n" +
                    "RSSI: " + flags.enableRssi + "\n" +
                    "Transmission Method: " + flags.transmissionMethod + "\n" +
                    "Relay Function: " + flags.relayFunction + "\n" +
                    "LBT Enable: " + flags.lbtEnable;

            infoTextView.setText(info);

            updateSpinnerValue(R.id.baudRateSpinner, data.baudRate);
            updateSpinnerValue(R.id.airRateSpinner, data.airSpeed);
            updateSpinnerValue(R.id.powerSpinner, data.transmitPower);
            updateSpinnerValue(R.id.channelSpinner, data.channel);
            updateSpinnerValue(R.id.paritySpinner, data.parity);
            updateSpinnerValue(R.id.packetSizeSpinner, data.packetSize);
            updateSpinnerValue(R.id.txModeSpinner, "Fixed-point");
            netIDTextView.setText(" " + data.netId);
            keyTextView.setText(" " + data.addh + data.addl);
            updateSpinnerValue(R.id.relaySpinner, flags.relayFunction);
            updateSpinnerValue(R.id.lbtSpinner, flags.lbtEnable);
            updateSpinnerValue(R.id.packetRssiSpinner, flags.enableRssi);
            updateSpinnerValue(R.id.channelRssiSpinner, flags.enableRssi);
            frequencyTextView.setText(" " + data.frequency + " MHz");
        });
    }

    private void readProductInformation(Consumer<String> onSuccess) {
        readRegisters(0x80, 0x07, "C18007", hexResponse -> {
            String info = parseAndDisplayProductInformation(hexResponse);
            onSuccess.accept(info);
        });
    }



    private String parseAndDisplayProductInformation(String hexResponse) {
        try {
            // Validate response header
            if (!hexResponse.startsWith("C18007")) {
                updateStatus("Unexpected product information response: " + hexResponse);
                return hexResponse;
            }

            // Extract 7 bytes of product information
            String pidHex = hexResponse.substring(6, 20); // Extract 7 bytes (14 hex characters)

            // Decode Model
            String model = decodeModel(pidHex.substring(0, 6));

            // Decode Version
            String version = decodeVersion(pidHex.substring(6, 14));

            // Display the product information
            return "Model: " + model + "\n"+ "Version: " + version;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing product information", e);
            updateStatus("Error parsing product information: " + e.getMessage());
        }
        return "Model: Unknown\nVersion: Unknown";
    }

    private String decodeModel(String modelHex) {
        // Decode Model from hex (e.g., "00 22 20")
        if (modelHex.equals("002220")) {
            return "E22-400T22U";
        }
        return "Unknown Model";
    }

    private String decodeVersion(String versionHex) {
        // Decode Version from hex (e.g., "16 0A 00 00")
        if (versionHex.equals("160A0000")) {
            return "7434-1-10";
        }
        return "Unknown Version";
    }





    private void updateSpinnerValue(int spinnerId, String value) {
        Spinner spinner = findViewById(spinnerId);
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();

        // Normalize value for comparison
        String normalizedValue = value.trim().toLowerCase();

        int position = -1;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).trim().toLowerCase().equals(normalizedValue)) {
                position = i;
                break;
            }
        }

        if (position >= 0) {
            spinner.setSelection(position);
        } else {
            Log.e("SpinnerUpdate", "Value \"" + value + "\" not found in Spinner with ID " + spinnerId);
        }
    }



    private String decodeTransmitPower(int transmitPowerBits) {
        switch (transmitPowerBits) {
            case 0:
                return "22 dBm";
            case 1:
                return "17 dBm";
            case 2:
                return "13 dBm";
            case 3:
                return "10 dBm";
            default:
                return "Unknown";
        }
    }

    private String decodeChannel(int channelBits) {
        return String.valueOf(channelBits); // Directly return channel number
    }


    private String decodeBaudRate(int baudRateBits) {
        switch (baudRateBits) {
            case 0: return "1200 bps";
            case 1: return "2400 bps";
            case 2: return "4800 bps";
            case 3: return "9600 bps";
            case 4: return "19200 bps";
            case 5: return "38400 bps";
            case 6: return "57600 bps";
            case 7: return "115200 bps";
            default: return "Unknown";
        }
    }


    private String decodeAirSpeed(int airSpeedBits) {
        switch (airSpeedBits) {
            case 0:
                return "0.8 kbps";
            case 1:
                return "1.2 kbps";
            case 2:
                return "2.4 kbps";
            case 3:
                return "4.8 kbps";
            case 4:
                return "9.6 kbps";
            case 5:
                return "19.2 kbps";
            case 6:
                return "38.4 kbps";
            case 7:
                return "62.5 kbps";
            default:
                return "Unknown";
        }
    }


    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }






    private void closeSerialPort() {
        if (serialPort != null) {
            try {
                serialPort.close();
                serialPort = null;
                updateStatus("Serial port closed.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to close serial port.", e);
            }
        }
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> connectionStatusTextView.setText("Status: " + message));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeSerialPort();
        serialExecutor.shutdownNow();
        unregisterReceiver(usbReceiver);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            updateStatus("Permission granted for device: " + device.getDeviceName());
                            connectToSerialPort(); // Trigger connection only if permission granted
                        }
                    } else {
                        updateStatus("Permission denied for device: " + (device != null ? device.getDeviceName() : "unknown"));
                    }
                }
            }
        }
    };


    private void initializeSpinners() {
        // Ebyte Device Spinner
        ArrayAdapter<String> ebyteDeviceSpinner = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"E-22-400T22 USB", "DTU E-90"});
        ebyteDeviceSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.deviceSpinner)).setAdapter(ebyteDeviceSpinner);

        // Air Rate Spinner
        ArrayAdapter<String> airRateAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","2.4 kbps", "4.8 kbps", "9.6 kbps", "19.2 kbps", "38.4 kbps", "62.5 kbps"});
        airRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.airRateSpinner)).setAdapter(airRateAdapter);

        //Baud Rate Spinner
        ArrayAdapter<String> baudRateAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","1200 bps", "2400 bps", "4800 bps", "9600 bps", "19200 bps", "38400 bps", "57600 bps", "115200 bps"});
        baudRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.baudRateSpinner)).setAdapter(baudRateAdapter);

        //Parity Spinner
        ArrayAdapter<String> parityAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","8N1", "8O1", "8E1", "8N1"});
        parityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.paritySpinner)).setAdapter(parityAdapter);

        // Packet Size Spinner
        ArrayAdapter<String> packetSizeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","240 Bytes", "128 Bytes", "64 Bytes", "32 Bytes"});
        packetSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.packetSizeSpinner)).setAdapter(packetSizeAdapter);

        // Transmit Power Spinner
        ArrayAdapter<String> powerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","22 dBm", "17 dBm", "13 dBm", "10 dBm"});
        powerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.powerSpinner)).setAdapter(powerAdapter);

        // Channel Spinner
        ArrayAdapter<String> channelAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
                        "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37",
                        "38", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55",
                        "56", "57", "58", "59", "60", "61", "62", "63"});
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.channelSpinner)).setAdapter(channelAdapter);

        //TX Mode
        ArrayAdapter<String> txModeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","Fixed-point", "Transparent"});
        txModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.txModeSpinner)).setAdapter(txModeAdapter);

        //WOR Role
        ArrayAdapter<String> worRoleAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","Sleep", "WOR"});
        worRoleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.worRoleSpinner)).setAdapter(worRoleAdapter);

        //WOR Cycle
        ArrayAdapter<String> worCycleAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","250 ms", "500 ms", "1 s", "2 s", "4 s", "8 s", "16 s", "32 s"});
        worCycleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.worCycleSpinner)).setAdapter(worCycleAdapter);

        //Relay
        ArrayAdapter<String> relayAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","Enabled","Disabled"});
        relayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.relaySpinner)).setAdapter(relayAdapter);

        //LBT
        ArrayAdapter<String> lbtAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","Enabled","Disabled"});
        lbtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.lbtSpinner)).setAdapter(lbtAdapter);

        //Packet RSSI
        ArrayAdapter<String> packetRssiAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","Enabled","Disabled"});
        packetRssiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.packetRssiSpinner)).setAdapter(packetRssiAdapter);

        //Channel RSSI
        ArrayAdapter<String> channelRssiAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-","Enabled","Disabled"});
        channelRssiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.channelRssiSpinner)).setAdapter(channelRssiAdapter);
    }


    private String decodeParity(int bits) {
        switch (bits) {
            case 0: return "8N1";
            case 1: return "8O1";
            case 2: return "8E1";
            case 3: return "8N1";
            default: return "Unknown";
        }
    }

    private String decodePacketSize(int packetSizeBits) {
        switch (packetSizeBits) {
            case 0b00: return "240 Bytes";
            case 0b01: return "128 Bytes";
            case 0b10: return "64 Bytes";
            case 0b11: return "32 Bytes";
            default: return "Unknown Packet Size";
        }
    }

}
