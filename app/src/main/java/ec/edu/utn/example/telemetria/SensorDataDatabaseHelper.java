// Asegúrate que el paquete coincida con tu proyecto
package ec.edu.utn.example.telemetria;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

public class SensorDataDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "telemetry.db";
    private static final int DATABASE_VERSION = 1;

    // Definición de la tabla y columnas
    private static final String TABLE_SENSOR_DATA = "sensor_data";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_DEVICE_ID = "device_id";
    private static final String COLUMN_LATITUDE = "latitud";
    private static final String COLUMN_LONGITUDE = "longitud";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    // Sentencia SQL para crear la tabla
    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_SENSOR_DATA + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_DEVICE_ID + " TEXT, " +
                    COLUMN_LATITUDE + " REAL, " +
                    COLUMN_LONGITUDE + " REAL, " +
                    COLUMN_TIMESTAMP + " INTEGER" +
                    ");";

    public SensorDataDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Se ejecuta la primera vez que la base de datos es creada
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Para una actualización, simplemente borramos la tabla y la volvemos a crear
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SENSOR_DATA);
        onCreate(db);
    }

    /**
     * Inserta un nuevo registro de datos del sensor en la base de datos.
     * Este es el método que faltaba.
     */
    public void insertSensorData(String deviceId, double latitude, double longitude, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_DEVICE_ID, deviceId);
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);
        values.put(COLUMN_TIMESTAMP, timestamp);

        // Insertar la nueva fila
        long newRowId = db.insert(TABLE_SENSOR_DATA, null, values);
        if (newRowId == -1) {
            Log.e("DatabaseHelper", "Error al insertar datos");
        } else {
            Log.d("DatabaseHelper", "Datos insertados con ID: " + newRowId);
        }
        db.close(); // Cerrar la conexión
    }

    /**
     * Obtiene todos los registros de la base de datos y los devuelve como un JSONArray.
     * Este método es usado por el botón "Mostrar Datos".
     */
    public JSONArray getAllSensorData() {
        JSONArray jsonArray = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SENSOR_DATA, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");

        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("id", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                    jsonObject.put("device_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_ID)));
                    jsonObject.put("latitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)));
                    jsonObject.put("longitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE)));
                    jsonObject.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                    jsonArray.put(jsonObject);
                } catch (Exception e) {
                    Log.e("DatabaseHelper", "Error al convertir cursor a JSON", e);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return jsonArray;
    }
    /**
     * Obtiene los registros de la base de datos dentro de un rango de tiempo específico.
     * @param startTime El timestamp de inicio del período (en milisegundos).
     * @param endTime El timestamp de fin del período (en milisegundos).
     * @return Un JSONArray con los datos del sensor que coinciden con el rango de tiempo.
     */
    public JSONArray getSensorDataByTimeRange(long startTime, long endTime) {
        JSONArray jsonArray = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();

        // 1. Definir la cláusula WHERE para filtrar por timestamp
        String selection = COLUMN_TIMESTAMP + " >= ? AND " + COLUMN_TIMESTAMP + " <= ?";

        // 2. Definir los valores para los placeholders (?) en la cláusula WHERE
        String[] selectionArgs = { String.valueOf(startTime), String.valueOf(endTime) };

        // 3. Ejecutar la consulta con los filtros de selección
        Cursor cursor = db.query(
                TABLE_SENSOR_DATA,   // La tabla a consultar
                null,                // Columnas a devolver (null para todas)
                selection,           // La cláusula WHERE
                selectionArgs,       // Los valores para la cláusula WHERE
                null,                // No agrupar las filas
                null,                // No filtrar por grupos de filas
                COLUMN_TIMESTAMP + " DESC" // Ordenar por
        );

        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject jsonObject = new JSONObject();
                    // El resto del código para poblar el JSONObject es idéntico al método original
                    jsonObject.put("id", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                    jsonObject.put("device_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_ID)));
                    jsonObject.put("latitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)));
                    jsonObject.put("longitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE)));
                    jsonObject.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                    jsonArray.put(jsonObject);
                } catch (Exception e) {
                    Log.e("DatabaseHelper", "Error al convertir cursor a JSON", e);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return jsonArray;
    }
}