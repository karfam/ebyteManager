package gr.enorasys.loramanager;

import java.util.Arrays;

final class RegisterResponse {
    private static final int HEADER_LENGTH = 3;
    private static final int MAX_PAYLOAD_LENGTH = 61;

    private RegisterResponse() {
    }

    static final class Accumulator {
        private final byte[] response;
        private int responseLength;
        private boolean invalid;

        Accumulator(int payloadLength) {
            if (payloadLength < 1 || payloadLength > MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException("Invalid register payload length.");
            }
            response = new byte[HEADER_LENGTH + payloadLength];
        }

        int remaining() {
            return response.length - responseLength;
        }

        void append(byte[] fragment, int fragmentLength) {
            if (invalid || fragment == null || fragmentLength < 0 || fragmentLength > fragment.length
                    || fragmentLength > remaining()) {
                invalid = true;
                return;
            }
            System.arraycopy(fragment, 0, response, responseLength, fragmentLength);
            responseLength += fragmentLength;
        }

        boolean isComplete(int startAddress, int payloadLength) {
            return !invalid
                    && payloadLength == response.length - HEADER_LENGTH
                    && responseLength == response.length
                    && (response[0] & 0xFF) == 0xC1
                    && (response[1] & 0xFF) == startAddress
                    && (response[2] & 0xFF) == payloadLength;
        }

        byte[] getResponse() {
            return Arrays.copyOf(response, responseLength);
        }
    }
}
