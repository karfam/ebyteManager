package gr.enorasys.loramanager;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RegisterProtocolTest {
    @Test
    public void readFrameAccumulatesFragmentedResponse() throws Exception {
        byte[][] chunks = {
                {(byte) 0xC1},
                {0x00, 0x03},
                {0x11, 0x22, 0x33}
        };
        int[] nextChunk = {0};

        byte[] frame = RegisterProtocol.readFrame((buffer, timeout) -> {
            byte[] chunk = chunks[nextChunk[0]++];
            System.arraycopy(chunk, 0, buffer, 0, chunk.length);
            return chunk.length;
        }, new byte[]{(byte) 0xC1, 0x00, 0x03}, 3, 1000);

        assertArrayEquals(new byte[]{(byte) 0xC1, 0x00, 0x03, 0x11, 0x22, 0x33}, frame);
    }

    @Test
    public void readFrameRejectsWrongHeaderAndShortResponse() {
        assertThrows(IOException.class, () -> RegisterProtocol.readFrame(
                (buffer, timeout) -> {
                    byte[] wrong = {(byte) 0xC1, 0x01, 0x03};
                    System.arraycopy(wrong, 0, buffer, 0, wrong.length);
                    return wrong.length;
                }, new byte[]{(byte) 0xC1, 0x00, 0x03}, 1, 1000));

        int[] reads = {0};
        assertThrows(IOException.class, () -> RegisterProtocol.readFrame(
                (buffer, timeout) -> {
                    if (reads[0]++ > 0) return 0;
                    buffer[0] = (byte) 0xC1;
                    buffer[1] = 0x00;
                    return 2;
                }, new byte[]{(byte) 0xC1, 0x00, 0x03}, 1, 1000));
    }

    @Test
    public void drainPendingDiscardsStaleInput() throws Exception {
        int[] reads = {0};
        int drained = RegisterProtocol.drainPending((buffer, timeout) -> {
            if (reads[0]++ > 0) return 0;
            buffer[0] = (byte) 0xC2;
            buffer[1] = 0x00;
            return 2;
        }, 64, 1000);
        assertEquals(2, drained);
    }

    @Test
    public void updateReg1PreservesUnmodifiedBits() {
        assertEquals(0x3A, RegisterProtocol.updateReg1(0x3A, 0, 2));
        assertEquals(0xFA, RegisterProtocol.updateReg1(0x3A, 3, 2));
    }

    @Test
    public void updateReg3PreservesLowReservedBitsAndDecodesMode() {
        assertEquals(0x0B, RegisterProtocol.updateReg3(0xAB, false, false, false, false));
        assertEquals(0xEB, RegisterProtocol.updateReg3(0x0B, true, true, true, false));
        assertEquals("Fixed-point", RegisterProtocol.transmissionMode(0x40));
        assertEquals("Transparent", RegisterProtocol.transmissionMode(0x00));
    }

    @Test
    public void validatesHexFieldsAndChannelBounds() {
        assertEquals(255, RegisterProtocol.parseHexField("FF", 2));
        assertEquals(0, RegisterProtocol.parseChannel("0"));
        assertEquals(63, RegisterProtocol.parseChannel("63"));
        assertThrows(IllegalArgumentException.class, () -> RegisterProtocol.parseHexField("G0", 2));
        assertThrows(IllegalArgumentException.class, () -> RegisterProtocol.parseHexField("F", 2));
        assertThrows(IllegalArgumentException.class, () -> RegisterProtocol.parseChannel("-1"));
        assertThrows(IllegalArgumentException.class, () -> RegisterProtocol.parseChannel("64"));
    }
}
