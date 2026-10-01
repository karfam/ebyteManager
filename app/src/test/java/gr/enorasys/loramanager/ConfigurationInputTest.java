package gr.enorasys.loramanager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationInputTest {
    @Test
    public void validatesFixedWidthHexValues() {
        assertTrue(ConfigurationInput.isValidAddress("aF09"));
        assertTrue(ConfigurationInput.isValidNetId("B2"));
        assertFalse(ConfigurationInput.isValidAddress("AF0"));
        assertFalse(ConfigurationInput.isValidAddress("AF 09"));
        assertFalse(ConfigurationInput.isValidNetId("B2G"));
        assertFalse(ConfigurationInput.isValidNetId(null));
    }

    @Test
    public void decodesBothTransmissionMethods() {
        assertEquals("Transparent", ConfigurationInput.decodeTransmissionMethod(0x00));
        assertEquals("Fixed-point", ConfigurationInput.decodeTransmissionMethod(0x40));
        assertEquals("Transparent", ConfigurationInput.decodeTransmissionMethod(0xBF));
        assertEquals("Fixed-point", ConfigurationInput.decodeTransmissionMethod(0xFF));
    }
}
