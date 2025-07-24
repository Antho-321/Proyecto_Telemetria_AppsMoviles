package ec.edu.utn.example.telemetria;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class GPSLoggerService extends Service {

    // Suponiendo que tienes una clase que maneja la lógica de GPS
    private GPSLocationManager gpsLocationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // Inicializa tu manejador de GPS aquí
        gpsLocationManager = new GPSLocationManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String deviceId = intent.getStringExtra("DEVICE_ID");

            switch (intent.getAction()) {
                case MainActivity.ACTION_START_LOGGING:
                    startLogging(deviceId);
                    break;
                case MainActivity.ACTION_STOP_LOGGING:
                    stopLogging();
                    break;
            }
        }
        return START_STICKY;
    }

    private void startLogging(String deviceId) {
        Notification notification = new NotificationCompat.Builder(this, MainActivity.NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Telemetría Activa")
                .setContentText("Recolectando datos de ubicación...")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener este ícono
                .build();

        startForeground(1, notification);
        logToActivity("Servicio de recolección INICIADO.");

        // Inicia la lógica de captura de GPS
        if (gpsLocationManager != null) {
            gpsLocationManager.startLocationUpdates(deviceId);
        }
    }

    private void stopLogging() {
        logToActivity("Servicio de recolección DETENIDO.");

        // Detiene la lógica de captura de GPS
        if (gpsLocationManager != null) {
            gpsLocationManager.stopLocationUpdates();
        }

        stopForeground(true);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void logToActivity(String message) {
        Intent intent = new Intent(MainActivity.LOG_UPDATE_ACTION);
        intent.putExtra("log_message", "Servicio: " + message);
        sendBroadcast(intent);
    }
}