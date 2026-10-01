package gr.enorasys.loramanager;

import android.app.PendingIntent;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.widget.Toolbar;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "loramanager";
    private static final String ACTION_USB_PERMISSION = "gr.enorasys.loramanager.USB_PERMISSION";
    private static final boolean DEVICE_WRITE_PROTOCOL_VERIFIED = false;
    private AutoCompleteTextView ebyteDeviceSpinner;
    private Spinner worRoleSpinner, worCycleSpinner, relaySpinner;
    private Button connectButton, readRegisterButton,writeRegisterButton;
    private TextView connectionStatusTextView, infoTextView,frequencyTextView,netIDTextView,keyTextView;
    private View statusIndicator;
    private volatile UsbSerialPort serialPort;
    private UsbManager usbManager;
    private UsbSerialDriver pendingDriver;
    private int pendingPortIndex;
    private UsbDevice connectedDevice;
    private final ExecutorService serialExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean operationBusy = new AtomicBoolean();
    private volatile boolean registerSnapshotAvailable;
    private volatile boolean supportedModelDetected;
    private volatile int lastReg1Value;
    private volatile int lastReg3Value;
    private volatile int lastReg0Value;
    private volatile byte[] expectedReadback;
    private volatile boolean writeVerificationPending;
    private volatile boolean destroyed;

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
        private final int reg0Value;
        private final int reg1Value;

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
                double frequency,
                int reg0Value,
                int reg1Value
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
            this.reg0Value = reg0Value;
            this.reg1Value = reg1Value;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.rootLayout);
        int rootLeft = root.getPaddingLeft();
        int rootTop = root.getPaddingTop();
        int rootRight = root.getPaddingRight();
        int rootBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            view.setPadding(rootLeft + bars.left, rootTop + bars.top,
                    rootRight + bars.right, rootBottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize views
        ebyteDeviceSpinner = findViewById(R.id.deviceSpinner);
        connectButton = findViewById(R.id.connectButton);
        readRegisterButton = findViewById(R.id.readRegisterButton);
        writeRegisterButton = findViewById(R.id.writeRegisterButton);
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);
        statusIndicator = findViewById(R.id.statusIndicator);
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
        worRoleSpinner.setEnabled(false);
        worCycleSpinner.setEnabled(false);
        findViewById(R.id.channelRssiSpinner).setEnabled(false);
        findViewById(R.id.airRateSpinner).setEnabled(false);


        initializeSpinners();

        // USB manager
        usbManager = (UsbManager) getSystemService(USB_SERVICE);

        // Register USB permission broadcast receiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        // Set up connect button
        connectButton.setOnClickListener(view -> connectToSerialPort());

        // Set up read register button
        readRegisterButton.setOnClickListener(view -> readMultipleRegisters());
        writeRegisterButton.setOnClickListener(view -> writeRegister());
    }

    private void writeRegister() {
        if (!DEVICE_WRITE_PROTOCOL_VERIFIED || !registerSnapshotAvailable || !supportedModelDetected) {
            updateStatus("Configuration writes are disabled until the device protocol is verified.");
            return;
        }
        if (serialPort == null || !serialPort.isOpen()) {
            updateStatus("Serial port not open. Connect first.");
            return;
        }
        if (!registerSnapshotAvailable || !supportedModelDetected) {
            updateStatus("Read a recognized E-22-400T22U first; write state is unavailable.");
            return;
        }

        Spinner baudRateSpinner = findViewById(R.id.baudRateSpinner);
        Spinner paritySpinner = findViewById(R.id.paritySpinner);
        Spinner packetSizeSpinner = findViewById(R.id.packetSizeSpinner);
        Spinner powerSpinner = findViewById(R.id.powerSpinner);
        Spinner channelSpinner = findViewById(R.id.channelSpinner);
        Spinner txModeSpinner = findViewById(R.id.txModeSpinner);
        Spinner relaySpinner = findViewById(R.id.relaySpinner);
        Spinner lbtSpinner = findViewById(R.id.lbtSpinner);
        Spinner packetRssiSpinner = findViewById(R.id.packetRssiSpinner);

        String baudRateSelection = baudRateSpinner.getSelectedItem().toString();
        String paritySelection = paritySpinner.getSelectedItem().toString();
        String packetSizeSelection = packetSizeSpinner.getSelectedItem().toString();
        String powerSelection = powerSpinner.getSelectedItem().toString();
        String channelSelection = channelSpinner.getSelectedItem().toString();
        String txModeSelection = txModeSpinner.getSelectedItem().toString();
        String relaySelection = relaySpinner.getSelectedItem().toString();
        String lbtSelection = lbtSpinner.getSelectedItem().toString();
        String packetRssiSelection = packetRssiSpinner.getSelectedItem().toString();

        if ("-".equals(baudRateSelection)
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

        final String finalNetIdValue = netIdValue.replaceAll("\\s+", "");
        final String finalKeyValue = keyValue.replaceAll("\\s+", "");
        if (!ConfigurationInput.isValidNetId(finalNetIdValue)
                || !ConfigurationInput.isValidAddress(finalKeyValue)) {
            updateStatus("Address must be two hex digits and NetID must be four hex digits.");
            return;
        }
        final int channelValue;
        try {
            channelValue = RegisterProtocol.parseChannel(channelSelection);
        } catch (IllegalArgumentException e) {
            updateStatus("Channel must be between 0 and 63.");
            return;
        }

        String preview = "Address: " + finalKeyValue + "\nNetID: " + finalNetIdValue
                + "\nBaud: " + baudRateSelection + "\nAir-rate register bits (unchanged): "
                + (lastReg0Value & 0x07)
                + "\nParity: " + paritySelection + "\nPacket size: " + packetSizeSelection
                + "\nPower: " + powerSelection + "\nChannel: " + channelSelection
                + "\nTX mode: " + txModeSelection
                + "\nRelay: " + relaySelection + "\nLBT: " + lbtSelection
                + "\nPacket RSSI: " + packetRssiSelection
                + "\n\nRegister mappings, acknowledgement, and temporary/permanent write behavior"
                + " have not been verified against a manual or hardware. Continue?";
        new AlertDialog.Builder(this)
                .setTitle("Review configuration write")
                .setMessage(preview)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Write", (dialog, which) -> {
                    if (!operationBusy.compareAndSet(false, true)) {
                        updateStatus("Another USB operation is in progress.");
                        return;
                    }
                    setOperationBusy(true);
                    serialExecutor.execute(() -> {
                        boolean[] readbackStarted = {false};
                        try {
                            int addh = RegisterProtocol.parseHexField(finalKeyValue.substring(0, 2), 2);
                            int addl = RegisterProtocol.parseHexField(finalKeyValue.substring(2, 4), 2);
                            int netId = RegisterProtocol.parseHexField(finalNetIdValue, 2);

                            int baudRateBits = encodeBaudRate(baudRateSelection);
                            int parityBits = encodeParity(paritySelection);
                            int packetSizeBits = encodePacketSize(packetSizeSelection);
                            int powerBits = encodeTransmitPower(powerSelection);

                            int reg0 = (baudRateBits << 5) | (parityBits << 3) | (lastReg0Value & 0x07);
                            int reg1 = RegisterProtocol.updateReg1(lastReg1Value, packetSizeBits, powerBits);
                            int reg3 = RegisterProtocol.updateReg3(
                                    lastReg3Value,
                                    "Enabled".equalsIgnoreCase(packetRssiSelection),
                                    "Fixed-point".equalsIgnoreCase(txModeSelection.trim()),
                                    "Enabled".equalsIgnoreCase(relaySelection),
                                    "Enabled".equalsIgnoreCase(lbtSelection));

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

                            expectedReadback = new byte[]{
                                    (byte) addh, (byte) addl, (byte) netId, (byte) reg0,
                                    (byte) reg1, (byte) channelValue, (byte) reg3
                            };
                            writeVerificationPending = true;
                            serialPort.write(writeCommand, 1000);
                            updateStatus("Write sent; checking register readback.");
                            operationBusy.set(false);
                            readbackStarted[0] = true;
                            readMultipleRegisters();
                        } catch (NumberFormatException e) {
                            updateStatus("Invalid numeric selection.");
                            Log.e(TAG, "Invalid numeric selection", e);
                            writeVerificationPending = false;
                            expectedReadback = null;
                        } catch (Exception e) {
                            updateStatus("Write failed: " + e.getMessage());
                            Log.e(TAG, "Error writing register", e);
                            writeVerificationPending = false;
                            expectedReadback = null;
                        } finally {
                            if (!readbackStarted[0]) {
                                operationBusy.set(false);
                                setOperationBusy(false);
                            }
                        }
                    });
                })
                .show();
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
            if (!operationBusy.compareAndSet(false, true)) {
                updateStatus("Another USB operation is in progress.");
                return;
            }
            setOperationBusy(true);
            serialExecutor.execute(() -> {
                closeSerialPort();
                operationBusy.set(false);
                setOperationBusy(false);
            });
            return;
        }

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (availableDrivers.isEmpty()) {
            updateStatus("No USB device found.");
            Log.e(TAG, "No USB devices found.");
            return;
        }

        if (availableDrivers.size() > 1) {
            String[] choices = new String[availableDrivers.size()];
            for (int i = 0; i < choices.length; i++) {
                UsbDevice device = availableDrivers.get(i).getDevice();
                choices[i] = device.getDeviceName() + " (VID "
                        + String.format("%04X", device.getVendorId()) + ", PID "
                        + String.format("%04X", device.getProductId()) + ")";
            }
            new AlertDialog.Builder(this)
                    .setTitle("Select USB serial device")
                    .setItems(choices, (dialog, which) -> requestPermissionOrOpen(availableDrivers.get(which)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        requestPermissionOrOpen(availableDrivers.get(0));
    }

    private void requestPermissionOrOpen(UsbSerialDriver driver) {
        List<UsbSerialPort> ports = driver.getPorts();
        if (ports.isEmpty()) {
            updateStatus("Selected USB device has no serial ports.");
            return;
        }
        if (ports.size() > 1) {
            String[] portChoices = new String[ports.size()];
            for (int i = 0; i < portChoices.length; i++) {
                portChoices[i] = "Port " + (i + 1);
            }
            new AlertDialog.Builder(this)
                    .setTitle("Select serial port")
                    .setItems(portChoices, (dialog, which) -> requestPermissionOrOpen(driver, which))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        requestPermissionOrOpen(driver, 0);
    }

    private void requestPermissionOrOpen(UsbSerialDriver driver, int portIndex) {
        pendingDriver = driver;
        pendingPortIndex = portIndex;
        UsbDevice device = driver.getDevice();

        if (!usbManager.hasPermission(device)) {
            // Create an explicit Intent (required for Android 14+)
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            intent.setPackage(getPackageName());

            // Use FLAG_IMMUTABLE for Android 12+ (required for Android 14+)
            int permissionFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this,
                    device.getDeviceId(),
                    intent,
                    permissionFlags
            );
            usbManager.requestPermission(device, permissionIntent);
            updateStatus("Requesting USB permission...");
            return;
        }

        openSelectedDriver(driver, device, portIndex);
    }

    private void openSelectedDriver(UsbSerialDriver driver, UsbDevice device, int portIndex) {
        Spinner baudRateSpinner = findViewById(R.id.baudRateSpinner);
        String baudRateSelection = baudRateSpinner.getSelectedItem() != null
                ? baudRateSpinner.getSelectedItem().toString()
                : "-";
        if ("-".equals(baudRateSelection.trim())) {
            updateSpinnerValue(R.id.baudRateSpinner, "9600 bps");
            baudRateSelection = "9600 bps";
            updateStatus("Defaulting baud rate to 9600 bps.");
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

        if (!operationBusy.compareAndSet(false, true)) {
            updateStatus("Another USB operation is in progress.");
            return;
        }
        setOperationBusy(true);
        serialExecutor.execute(() -> openSelectedDriverOnExecutor(driver, device, portIndex, baudRate));
    }

    private void openSelectedDriverOnExecutor(
            UsbSerialDriver driver, UsbDevice device, int portIndex, int baudRate) {
        UsbDeviceConnection connection = null;
        UsbSerialPort selectedPort = null;
        try {
            connection = usbManager.openDevice(device);
            if (connection == null) {
                updateStatus("Failed to open USB connection.");
                return;
            }
            selectedPort = driver.getPorts().get(portIndex);
            selectedPort.open(connection);
            selectedPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            if (destroyed) {
                selectedPort.close();
                connection.close();
                return;
            }
            serialPort = selectedPort;
            connectedDevice = device;
            pendingDriver = null;
            if (destroyed) {
                closeSerialPort();
                return;
            }
            runOnUiThread(() -> connectButton.setText(R.string.disconnect));
            updateStatus("Connected at " + baudRate + " baud.");
        } catch (Exception e) {
            updateStatus("Connection failed: " + e.getMessage());
            Log.e(TAG, "Serial port connection failed.", e);
            if (selectedPort != null) {
                try {
                    selectedPort.close();
                } catch (Exception closeException) {
                    Log.e(TAG, "Failed to close serial port after connection failure.", closeException);
                }
            }
            if (connection != null) connection.close();
            serialPort = null;
            pendingDriver = null;
        } finally {
            operationBusy.set(false);
            setOperationBusy(false);
        }
    }



    private void readReg3(Consumer<Reg3Flags> onSuccess) {
        readRegisters(0x06, 0x01, hexResponse -> {
            if (hexResponse == null) {
                onSuccess.accept(null);
                return;
            }
            String reg3Hex = hexResponse.substring(6, 8); // Extract REG3 (1 byte)
            int reg3Value = Integer.parseInt(reg3Hex, 16);
            lastReg3Value = reg3Value;
            registerSnapshotAvailable = true;
            onSuccess.accept(decodeReg3(reg3Value));
        });
    }


    private void readRegisters(int startAddress, int length, Consumer<String> onSuccess) {
        serialExecutor.execute(() -> {
            if (serialPort == null || !serialPort.isOpen()) {
                updateStatus("Serial port not open. Connect first.");
                onSuccess.accept(null);
                return;
            }
            String hexResponse;
            try {
                RegisterProtocol.drainPending(
                        (buffer, timeout) -> serialPort.read(buffer, timeout), 64, 50);
                byte[] header = new byte[]{(byte) 0xC1, (byte) startAddress, (byte) length};
                byte[] readCommand = new byte[]{(byte) 0xC1, (byte) startAddress, (byte) length};
                serialPort.write(readCommand, 1000);
                Log.d(TAG, "Sent command: " + bytesToHex(readCommand, readCommand.length));
                updateStatus("Reading registers...");

                byte[] response = RegisterProtocol.readFrame(
                        (buffer, timeout) -> serialPort.read(buffer, timeout), header, length, 2000);
                hexResponse = bytesToHex(response, response.length);
            } catch (Exception e) {
                updateStatus("Read failed: " + e.getMessage());
                Log.e(TAG, "Error reading register", e);
                onSuccess.accept(null);
                return;
            }
            updateStatus("Register data updated.");
            onSuccess.accept(hexResponse);
        });
    }


    private RegisterData parseRegisterData(String hexResponse) {
        try {
            // Decode fields
            if (hexResponse == null || hexResponse.length() != 18
                    || !hexResponse.startsWith("C10006")
                    || !hexResponse.matches("[0-9A-Fa-f]+")) {
                updateStatus("Invalid register response.");
                return null;
            }
            String addh = hexResponse.substring(6, 8); // ADDH
            String addl = hexResponse.substring(8, 10); // ADDL
            String netId = hexResponse.substring(10, 12); // NETID
            String reg0 = hexResponse.substring(12, 14); // REG0
            String reg1 = hexResponse.substring(14, 16); // REG1
            String channelHex = hexResponse.substring(16, 18); // Channel
            // Decode REG0
            int reg0Value = Integer.parseInt(reg0, 16);
            lastReg0Value = reg0Value;
            String baudRate = decodeBaudRate((reg0Value >> 5) & 0b111);
            String parity = decodeParity((reg0Value >> 3) & 0b11);
            String airSpeed = decodeAirSpeed(reg0Value & 0b111);


            // Decode REG1
            int reg1Value = Integer.parseInt(reg1, 16);
            lastReg1Value = reg1Value;
            String packetSize = decodePacketSize((reg1Value >> 6) & 0b11);
            String transmitPower = decodeTransmitPower(reg1Value & 0b11);

            // Decode channel
            int channelValue = Integer.parseInt(channelHex, 16);
            if (channelValue > 63) {
                updateStatus("Invalid channel value in register response.");
                return null;
            }
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
                    frequency,
                    reg0Value,
                    reg1Value
            );
        } catch (Exception e) {
            Log.e(TAG, "Error parsing register response", e);
            updateStatus("Error parsing response: " + e.getMessage());
        }
        return null;
    }

    private Reg3Flags decodeReg3(int reg3Value) {
        String enableRssi = ((reg3Value >> 7) & 0b1) == 1 ? "Enabled" : "Disabled";
        String transmissionMethod = ConfigurationInput.decodeTransmissionMethod(reg3Value);
        String relayFunction = ((reg3Value >> 5) & 0b1) == 1 ? "Enabled" : "Disabled";
        String lbtEnable = ((reg3Value >> 4) & 0b1) == 1 ? "Enabled" : "Disabled";
        return new Reg3Flags(enableRssi, transmissionMethod, relayFunction, lbtEnable);
    }

    private void readMultipleRegisters() {
        if (!operationBusy.compareAndSet(false, true)) {
            updateStatus("Another USB operation is in progress.");
            return;
        }
        setOperationBusy(true);
        registerSnapshotAvailable = false;
        supportedModelDetected = false;
        readRegisters(0x00, 0x06, hexResponse -> {
            RegisterData data = parseRegisterData(hexResponse);
            if (data == null) {
                writeVerificationPending = false;
                expectedReadback = null;
                operationBusy.set(false);
                setOperationBusy(false);
                return;
            }
            readReg3(flags -> {
                Reg3Flags safeFlags = flags == null
                        ? new Reg3Flags("Unknown", "Unknown", "Unknown", "Unknown") : flags;
                readProductInformation(info -> {
                    updateUiWithRegisterData(data, safeFlags, info);
                    if (writeVerificationPending) {
                        byte[] expected = expectedReadback;
                        boolean matches = expected != null && registerSnapshotAvailable
                                && supportedModelDetected
                                && expected[0] == (byte) Integer.parseInt(data.addh, 16)
                                && expected[1] == (byte) Integer.parseInt(data.addl, 16)
                                && expected[2] == (byte) Integer.parseInt(data.netId, 16)
                                && expected[3] == (byte) data.reg0Value
                                && expected[4] == (byte) data.reg1Value
                                && expected[5] == (byte) data.channelValue
                                && expected[6] == (byte) lastReg3Value;
                        updateStatus(matches
                                ? "Register readback matches. Acknowledgement and power-cycle persistence are unverified."
                                : "Write could not be verified by register readback.");
                        expectedReadback = null;
                        writeVerificationPending = false;
                    }
                    operationBusy.set(false);
                    setOperationBusy(false);
                });
            });
        });
    }

    private void updateUiWithRegisterData(RegisterData data, Reg3Flags flags, String productInfo) {
        if (destroyed) return;
        runOnUiThread(() -> {
            if (destroyed) return;
            String safeProductInfo = productInfo == null ? "Model: Unknown\nVersion: Unknown" : productInfo;
            String info = safeProductInfo + "\n" +
                    "Frequency estimate (unverified): " + data.frequency + " MHz\n" +
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
            infoTextView.setVisibility(android.view.View.VISIBLE);

            // Update status to show successful read
            updateStatus("Device data loaded successfully.");

            updateSpinnerValue(R.id.baudRateSpinner, data.baudRate);
            updateSpinnerValue(R.id.powerSpinner, data.transmitPower);
            updateSpinnerValue(R.id.channelSpinner, data.channel);
            updateSpinnerValue(R.id.paritySpinner, data.parity);
            updateSpinnerValue(R.id.packetSizeSpinner, data.packetSize);
            if (!"Unknown".equals(flags.transmissionMethod)) {
                updateSpinnerValue(R.id.txModeSpinner, flags.transmissionMethod);
            }
            netIDTextView.setText(" " + data.netId);
            keyTextView.setText(" " + data.addh + data.addl);
            if (!"Unknown".equals(flags.relayFunction)) {
                updateSpinnerValue(R.id.relaySpinner, flags.relayFunction);
            }
            if (!"Unknown".equals(flags.lbtEnable)) {
                updateSpinnerValue(R.id.lbtSpinner, flags.lbtEnable);
            }
            if (!"Unknown".equals(flags.enableRssi)) {
                updateSpinnerValue(R.id.packetRssiSpinner, flags.enableRssi);
            }
            frequencyTextView.setText(" " + data.frequency + " MHz");
        });
    }

    private void readProductInformation(Consumer<String> onSuccess) {
        readRegisters(0x80, 0x07, hexResponse -> {
            String info = hexResponse == null ? null : parseAndDisplayProductInformation(hexResponse);
            onSuccess.accept(info);
        });
    }



    private String parseAndDisplayProductInformation(String hexResponse) {
        try {
            // Validate response header
            if (hexResponse == null || hexResponse.length() != 20
                    || !hexResponse.startsWith("C18007")
                    || !hexResponse.matches("[0-9A-Fa-f]+")) {
                updateStatus("Unexpected product information response: " + hexResponse);
                return null;
            }

            // Extract 7 bytes of product information
            String pidHex = hexResponse.substring(6, 20); // Extract 7 bytes (14 hex characters)

            // Decode Model
            String model = decodeModel(pidHex.substring(0, 6));
            supportedModelDetected = "E22-400T22U".equals(model);

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
            spinner.setSelection(0);
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
        return "Register code " + airSpeedBits + " (unverified)";
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
                connectedDevice = null;
                updateStatus("Serial port closed.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to close serial port.", e);
            } finally {
                serialPort = null;
            }
            connectedDevice = null;
            registerSnapshotAvailable = false;
            supportedModelDetected = false;
            runOnUiThread(() -> {
                if (!destroyed) connectButton.setText(R.string.connect);
            });
        }
    }

    private void setOperationBusy(boolean busy) {
        runOnUiThread(() -> {
            if (destroyed) return;
            readRegisterButton.setEnabled(!busy);
            writeRegisterButton.setEnabled(!busy);
            connectButton.setEnabled(!busy);
        });
    }

    private void updateStatus(String message) {
        if (destroyed) return;
        runOnUiThread(() -> {
            if (destroyed) return;
            connectionStatusTextView.setText("Status: " + message);
            boolean connected = serialPort != null && serialPort.isOpen();
            statusIndicator.setBackgroundResource(connected
                    ? R.drawable.status_indicator_connected : R.drawable.status_indicator_disconnected);
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
        serialExecutor.shutdownNow();
        closeSerialPort();
        unregisterReceiver(usbReceiver);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device == null || pendingDriver == null || !device.equals(pendingDriver.getDevice())) {
                    return;
                }
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    openSelectedDriver(pendingDriver, device, pendingPortIndex);
                } else {
                    pendingDriver = null;
                    updateStatus("USB permission denied.");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && pendingDriver != null && device.equals(pendingDriver.getDevice())) {
                    pendingDriver = null;
                }
                if (device != null && device.equals(connectedDevice)) {
                    try {
                        serialExecutor.execute(() -> closeSerialPort());
                    } catch (RuntimeException ignored) {
                        closeSerialPort();
                    }
                }
            }
        }
    };


    private void initializeSpinners() {
        // Ebyte Device Spinner (AutoCompleteTextView)
        String[] devices = new String[]{"E-22-400T22U (protocol unverified)"};
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, devices);
        ebyteDeviceSpinner.setAdapter(deviceAdapter);
        ebyteDeviceSpinner.setText(devices[0], false); // Set default value

        // Air Rate Spinner
        ArrayAdapter<String> airRateAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"-", "Not verified"});
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
                new String[]{"-","8N1", "8O1", "8E1"});
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
            case 3: return "Unknown";
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
