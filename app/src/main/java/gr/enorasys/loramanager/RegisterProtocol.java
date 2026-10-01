package gr.enorasys.loramanager;

import java.io.IOException;

final class RegisterProtocol {
    interface ByteReader {
        int read(byte[] buffer, int timeoutMillis) throws Exception;
    }

    private RegisterProtocol() {
    }

    static byte[] readFrame(ByteReader reader, byte[] expectedHeader, int payloadLength, int timeoutMillis)
            throws Exception {
        if (expectedHeader == null || expectedHeader.length != 3
                || payloadLength <= 0 || payloadLength > 255 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("Invalid frame parameters");
        }

        byte[] frame = new byte[expectedHeader.length + payloadLength];
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        int received = 0;
        while (received < frame.length) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new IOException("Timed out waiting for register response");
            }

            int remainingMillis = (int) Math.max(1, (remainingNanos + 999_999L) / 1_000_000L);
            byte[] next = new byte[frame.length - received];
            int count = reader.read(next, remainingMillis);
            if (count <= 0) {
                throw new IOException("Timed out waiting for register response");
            }
            if (count > next.length) {
                throw new IOException("Invalid register response length");
            }
            System.arraycopy(next, 0, frame, received, count);
            received += count;

            int headerBytesReceived = Math.min(received, expectedHeader.length);
            for (int i = 0; i < headerBytesReceived; i++) {
                if (frame[i] != expectedHeader[i]) {
                    throw new IOException("Unexpected register response header");
                }
            }
        }
        return frame;
    }

    static int drainPending(ByteReader reader, int maxBytes, int timeoutMillis) throws Exception {
        if (maxBytes <= 0 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("Invalid drain limits");
        }
        byte[] buffer = new byte[Math.min(64, maxBytes)];
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        int drained = 0;
        while (drained < maxBytes) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) break;
            int remainingMillis = (int) Math.max(1, (remainingNanos + 999_999L) / 1_000_000L);
            int count = reader.read(buffer, remainingMillis);
            if (count <= 0) break;
            if (count > buffer.length || count > maxBytes - drained) {
                throw new IOException("Invalid pending-data length");
            }
            drained += count;
        }
        return drained;
    }

    static String transmissionMode(int reg3) {
        return (reg3 & 0x40) != 0 ? "Fixed-point" : "Transparent";
    }

    static int parseHexField(String value, int digits) {
        if (value == null || digits <= 0 || value.length() != digits
                || !value.matches("[0-9A-Fa-f]+")) {
            throw new IllegalArgumentException("Expected " + digits + " hexadecimal digits");
        }
        return Integer.parseInt(value, 16);
    }

    static int parseChannel(String value) {
        if (value == null || !value.matches("[0-9]+")) {
            throw new IllegalArgumentException("Channel must be a decimal number");
        }
        int channel = Integer.parseInt(value);
        if (channel > 63) {
            throw new IllegalArgumentException("Channel must be between 0 and 63");
        }
        return channel;
    }

    static int updateReg1(int current, int packetSizeBits, int transmitPowerBits) {
        requireTwoBitValue(packetSizeBits);
        requireTwoBitValue(transmitPowerBits);
        return (current & ~0xC3) | (packetSizeBits << 6) | transmitPowerBits;
    }

    static int updateReg3(int current, boolean rssi, boolean fixedPoint, boolean relay, boolean lbt) {
        int updated = current & 0x0F;
        if (rssi) updated |= 0x80;
        if (fixedPoint) updated |= 0x40;
        if (relay) updated |= 0x20;
        if (lbt) updated |= 0x10;
        return updated;
    }

    private static void requireTwoBitValue(int value) {
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException("Register field must fit in two bits");
        }
    }
}
