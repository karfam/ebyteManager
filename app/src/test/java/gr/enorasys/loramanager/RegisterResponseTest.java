package gr.enorasys.loramanager;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegisterResponseTest {
    @Test
    public void acceptsResponseReceivedInFragments() {
        RegisterResponse.Accumulator response = new RegisterResponse.Accumulator(2);
        response.append(new byte[]{(byte) 0xC1, 0x06}, 2);

        assertFalse(response.isComplete(0x06, 2));

        response.append(new byte[]{0x02, 0x10, 0x20}, 3);

        assertTrue(response.isComplete(0x06, 2));
    }

    @Test
    public void rejectsTruncatedResponse() {
        RegisterResponse.Accumulator response = new RegisterResponse.Accumulator(2);
        response.append(new byte[]{(byte) 0xC1, 0x06, 0x02, 0x10}, 4);

        assertFalse(response.isComplete(0x06, 2));
    }

    @Test
    public void rejectsMismatchedHeader() {
        RegisterResponse.Accumulator response = new RegisterResponse.Accumulator(1);
        response.append(new byte[]{(byte) 0xC1, 0x05, 0x01, 0x10}, 4);

        assertFalse(response.isComplete(0x06, 1));
    }

    @Test
    public void rejectsExcessDataAndUnsupportedLengths() {
        RegisterResponse.Accumulator response = new RegisterResponse.Accumulator(1);
        response.append(new byte[]{(byte) 0xC1, 0x06, 0x01, 0x10, 0x20}, 5);

        assertFalse(response.isComplete(0x06, 1));
        assertIllegalPayloadLength(0);
        assertIllegalPayloadLength(62);
    }

    private void assertIllegalPayloadLength(int length) {
        try {
            new RegisterResponse.Accumulator(length);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected invalid payload length to be rejected.");
    }
}
