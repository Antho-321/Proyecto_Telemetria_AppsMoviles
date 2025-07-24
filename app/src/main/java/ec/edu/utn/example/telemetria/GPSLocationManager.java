// Asegúrate que el paquete coincida con tu proyecto
package ec.edu.utn.example.telemetria;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class GPSLocationManager {

    private static final String TAG = "GPSLocationManager";
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationCallback locationCallback;
    private final SensorDataDatabaseHelper dbHelper;
    private String currentDeviceId;

    public GPSLocationManager(Context context) {
        this.context = context;
        this.dbHelper = new SensorDataDatabaseHelper(context);
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // Este es el callback que se ejecuta cada vez que se recibe una nueva ubicación
        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(TAG, "Nueva ubicación recibida: " + location.getLatitude() + ", " + location.getLongitude());
                        // Guardar los datos en la base de datos local
                        dbHelper.insertSensorData(
                                currentDeviceId, // El ID del dispositivo actual
                                location.getLatitude(),
                                location.getLongitude(),
                                System.currentTimeMillis()
                        );
                    }
                }
            }
        };
    }

    public void startLocationUpdates(String deviceId) {
        this.currentDeviceId = deviceId;
        Log.d(TAG, "Iniciando actualizaciones de ubicación...");

        // Comprobar permisos antes de empezar
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permisos de ubicación no concedidos. No se pueden iniciar las actualizaciones.");
            return;
        }

        // Configurar la solicitud de ubicación
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000) // 30000 ms = 30 segundos
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000) // 5 segundos
                .build();

        // Solicitar actualizaciones de ubicación
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public void stopLocationUpdates() {
        Log.d(TAG, "Deteniendo actualizaciones de ubicación.");
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }
}