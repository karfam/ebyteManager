package gr.enorasys.loramanager;

final class ConfigurationInput {
    private ConfigurationInput() {
    }

    static boolean isValidAddress(String value) {
        return value != null && value.matches("[0-9A-Fa-f]{4}");
    }

    static boolean isValidNetId(String value) {
        return value != null && value.matches("[0-9A-Fa-f]{2}");
    }

    static String decodeTransmissionMethod(int reg3Value) {
        return ((reg3Value >> 6) & 0b1) == 1 ? "Fixed-point" : "Transparent";
    }
}
