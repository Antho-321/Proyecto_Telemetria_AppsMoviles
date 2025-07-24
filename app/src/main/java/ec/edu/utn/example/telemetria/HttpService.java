package ec.edu.utn.example.telemetria;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo; // ✅ ADDED
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

public class HttpService extends Service {

    private static final String TAG = "HttpService";
    private static final int PORT = 8080;
    public static final String LOG_UPDATE_ACTION = "ec.edu.utn.example.telemetria.LOG_UPDATE";

    private WebServer server;
    private TokenDatabaseHelper tokenDbHelper;
    private SensorDataDatabaseHelper sensorDbHelper;

    public static final String JWT_SECRET = "mi_secreto_muy_largo_y_dificil_de_adivinar_12345";

    private void startForegroundService() {
        String channelId = "http_service_channel";
        String channelName = "Http Service Channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(chan);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Servidor HTTP activo")
                .setContentText("El servidor está corriendo en el puerto " + PORT)
                .setSmallIcon(R.mipmap.ic_launcher) // It's good practice to set an icon
                .setOngoing(true);

        Notification notification = notificationBuilder.build();

        // ✅ CORRECTED FOREGROUND CALL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tokenDbHelper = new TokenDatabaseHelper(this);
        sensorDbHelper = new SensorDataDatabaseHelper(this);
        Log.d(TAG, "Servicio HttpService creado y BDs inicializadas.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();
        if (server != null && server.isAlive()) {
            sendLogToActivity("ℹ️ El servidor ya se está ejecutando en el puerto " + PORT);
            return START_STICKY;
        }
        server = new WebServer();
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            sendLogToActivity("✅ Servidor iniciado correctamente en el puerto " + PORT);
        } catch (IOException e) {
            sendLogToActivity("❌ Fallo al iniciar el servidor: " + e.getMessage());
            server = null;
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            server = null;
            sendLogToActivity("🛑 Servidor detenido.");
        }
        if (tokenDbHelper != null) {
            tokenDbHelper.close();
        }
        if (sensorDbHelper != null) {
            sensorDbHelper.close();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendLogToActivity(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logWithTimestamp = "[" + timestamp + "] " + message;
        Intent intent = new Intent(LOG_UPDATE_ACTION);
        intent.putExtra("log_message", logWithTimestamp);
        sendBroadcast(intent);
    }

    private JSONObject getDeviceStatus() {
        JSONObject status = new JSONObject();
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, ifilter);
            int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
            float batteryPct = level * 100 / (float)scale;
            status.put("nivel_bateria", batteryPct);

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            status.put("conectividad_red", isConnected ? "Conectado" : "Desconectado");
            if(isConnected) {
                status.put("tipo_red", activeNetwork.getTypeName());
            }

            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long bytesAvailable = stat.getAvailableBytes();
            status.put("almacenamiento_disponible_mb", bytesAvailable / (1024 * 1024));
            status.put("version_so", Build.VERSION.RELEASE);
            status.put("modelo_dispositivo", Build.MANUFACTURER + " " + Build.MODEL);

        } catch (JSONException e) {
            Log.e(TAG, "Error creando JSON de estado del dispositivo", e);
        }
        return status;
    }

    private class WebServer extends NanoHTTPD {
        public WebServer() {
            super(PORT);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            Method method = session.getMethod();
            sendLogToActivity("Petición: " + method + " " + uri);

            if (method.equals(Method.POST) && uri.equalsIgnoreCase("/login")) {
                return handleLogin(session);
            }

            String token = extractToken(session);
            if (token == null || !isTokenValid(token)) {
                sendLogToActivity("❌ Acceso denegado a " + uri + ". Token inválido o ausente.");
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\":\"Acceso no autorizado\"}");
            }

            if (method.equals(Method.GET) && uri.equalsIgnoreCase("/api/device_status")) {
                return handleDeviceStatus();
            }

            if (method.equals(Method.GET) && uri.equalsIgnoreCase("/api/sensor_data")) {
                return handleSensorData(session);
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Ruta no encontrada");
        }

        private Response handleLogin(IHTTPSession session) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");
                if (body == null || body.isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Cuerpo de la petición vacío\"}");
                }
                JSONObject json = new JSONObject(body);
                String username = json.optString("username", "");
                if (username.isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"El campo 'username' es requerido\"}");
                }
                String token = generateAndStoreToken(username);
                JSONObject responseJson = new JSONObject();
                responseJson.put("token", token);
                sendLogToActivity("✅ Autenticación exitosa para '" + username + "'.");
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson.toString());
            } catch (Exception e) {
                sendLogToActivity("❌ Error en /login: " + e.getMessage());
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"Error interno del servidor\"}");
            }
        }

        private Response handleDeviceStatus() {
            JSONObject status = getDeviceStatus();
            sendLogToActivity("✅ Acceso concedido a /api/device_status.");
            return newFixedLengthResponse(Response.Status.OK, "application/json", status.toString());
        }

        private Response handleSensorData(IHTTPSession session) {
            Map<String, String> params = session.getParms();
            String startTimeStr = params.get("start_time");
            String endTimeStr = params.get("end_time");

            if (startTimeStr == null || endTimeStr == null) {
                sendLogToActivity("⚠️ Petición a /api/sensor_data sin parámetros de tiempo.");
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Los parámetros 'start_time' y 'end_time' son requeridos (en ms).\"}");
            }

            try {
                // Convertir los parámetros String a long
                long startTime = Long.parseLong(startTimeStr);
                long endTime = Long.parseLong(endTimeStr);

                // ¡Llamar al nuevo método que filtra por tiempo!
                JSONArray sensorData = sensorDbHelper.getSensorDataByTimeRange(startTime, endTime);

                sendLogToActivity("✅ Acceso concedido a /api/sensor_data. Enviando " + sensorData.length() + " registros filtrados.");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sensorData.toString());

            } catch (NumberFormatException e) {
                // Manejar el caso en que los parámetros no sean números válidos
                sendLogToActivity("❌ Error en /api/sensor_data: Parámetros de tiempo no son números válidos.");
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Los parámetros 'start_time' y 'end_time' deben ser números válidos (timestamps en ms).\"}");
            }
        }

        private String extractToken(IHTTPSession session) {
            String authorizationHeader = session.getHeaders().get("authorization");
            if (authorizationHeader != null && authorizationHeader.toLowerCase().startsWith("bearer ")) {
                return authorizationHeader.substring(7);
            }
            return null;
        }

        private boolean isTokenValid(String token) {
            try {
                Algorithm algorithm = Algorithm.HMAC256(JWT_SECRET);
                JWTVerifier verifier = JWT.require(algorithm).withIssuer("HttpServiceApp").build();
                verifier.verify(token);
                return tokenDbHelper.isTokenInDb(token);
            } catch (JWTVerificationException exception) {
                return false;
            }
        }

        private String generateAndStoreToken(String username) {
            try {
                Algorithm algorithm = Algorithm.HMAC256(JWT_SECRET);
                String token = JWT.create()
                        .withIssuer("HttpServiceApp")
                        .withSubject(username)
                        .withIssuedAt(new Date())
                        .withExpiresAt(new Date(System.currentTimeMillis() + 3600_000)) // 1 hora
                        .withJWTId(UUID.randomUUID().toString())
                        .sign(algorithm);
                tokenDbHelper.addToken(token, username);
                return token;
            } catch (Exception e) {
                Log.e(TAG, "Error al generar y guardar el token", e);
                return null;
            }
        }
    }
}