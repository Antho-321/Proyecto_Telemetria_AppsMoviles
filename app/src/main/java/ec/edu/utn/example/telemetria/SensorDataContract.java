// --- SensorDataContract.java (NUEVO) ---
package ec.edu.utn.example.telemetria;

import android.provider.BaseColumns;

public final class SensorDataContract {
    private SensorDataContract() {}

    public static class SensorEntry implements BaseColumns {
        public static final String TABLE_NAME = "sensor_data";
        public static final String COLUMN_NAME_DEVICE_ID = "device_id";
        public static final String COLUMN_NAME_LATITUDE = "latitude";
        public static final String COLUMN_NAME_LONGITUDE = "longitude";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    }
}