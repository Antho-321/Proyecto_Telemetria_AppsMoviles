// Asegúrate que el paquete coincida con tu proyecto
package ec.edu.utn.example.telemetria;

// Importaciones de ambas funcionalidades
import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // --- Miembros de la funcionalidad de Disponibilidad ---
    private final Map<String, String> mapDay = new LinkedHashMap<>();
    private final Map<String, View> dayBlocks = new HashMap<>();
    private final Map<String, TextView> dayButtons = new HashMap<>();
    private LinearLayout dayPickerContainer, scheduleContainer;
    private View activeDayBlock = null;

    // --- Miembros de la funcionalidad de Telemetría ---
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    public static final String LOG_UPDATE_ACTION = "ec.edu.utn.example.telemetria.LOG_UPDATE";
    public static final String NOTIFICATION_CHANNEL_ID = "GPSLoggerChannel";
    public static final String ACTION_START_LOGGING = "ec.edu.utn.START_LOGGING";
    public static final String ACTION_STOP_LOGGING = "ec.edu.utn.STOP_LOGGING";

    // --- NUEVOS Y MODIFICADOS MIEMBROS DE UI ---
    private Button btnSchedule, btnShowData, btnCollectNow; // Botón nuevo agregado
    private TextView lblStatus, txtApiLogs;
    private String deviceId;
    private BroadcastReceiver logReceiver;
    private SensorDataDatabaseHelper dbHelper;
    private AlarmManager alarmManager;
    private final ArrayList<PendingIntent> activeAlarms = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Inicialización de UI de Disponibilidad ---
        dayPickerContainer = findViewById(R.id.dayPickerContainer);
        scheduleContainer = findViewById(R.id.scheduleContainer);
        mapDay.put("L", "Lunes");
        mapDay.put("M", "Martes");
        mapDay.put("X", "Miércoles");
        mapDay.put("J", "Jueves");
        mapDay.put("V", "Viernes");
        mapDay.put("S", "Sábado");
        mapDay.put("D", "Domingo");
        createDayButtons();

        // --- Inicialización de UI y Lógica de Telemetría ---
        dbHelper = new SensorDataDatabaseHelper(this);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // --- BOTONES ---
        btnSchedule = findViewById(R.id.btnScheduleCollection);
        btnCollectNow = findViewById(R.id.btnCollectNow); // Inicializar nuevo botón
        btnShowData = findViewById(R.id.btnShowData);
        lblStatus = findViewById(R.id.lblStatus);
        txtApiLogs = findViewById(R.id.txtApiLogs);
        txtApiLogs.setMovementMethod(new ScrollingMovementMethod());

        logToUi("App inicializada. Listo para programar recolección.");
        createNotificationChannel();
        deviceId = loadOrGenerateDeviceId();

        // --- EVENTOS DE BOTONES ---
        btnSchedule.setOnClickListener(v -> onScheduleButtonClick());
        btnCollectNow.setOnClickListener(v -> onCollectNowClick()); // Asignar evento al nuevo botón
        btnShowData.setOnClickListener(v -> showAllSensorData());

        String ip = getLocalIp();
        lblStatus.setText("ID Dispositivo: " + deviceId + "\nIP local: " + ip + ":8080");

        setupLogReceiver();
        startHttpService();
    }

    // ===================================================================
    // MÉTODO NUEVO PARA EL BOTÓN "RECOLECTAR AHORA"
    // ===================================================================

    /**
     * Inicia la recolección de datos inmediatamente.
     */
    private void onCollectNowClick() {
        // Verificar permisos de ubicación antes de iniciar
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            Toast.makeText(this, "Se necesita permiso de ubicación para iniciar la recolección.", Toast.LENGTH_LONG).show();
            return;
        }

        logToUi("Iniciando recolección inmediata por solicitud del usuario...");
        Toast.makeText(this, "Iniciando recolección ahora...", Toast.LENGTH_SHORT).show();

        // Crear el intent para iniciar el servicio de logging
        Intent intent = new Intent(this, GPSLoggerService.class);
        intent.setAction(ACTION_START_LOGGING);
        intent.putExtra("DEVICE_ID", deviceId);
        startService(intent);
    }


    // ===================================================================
    // MÉTODOS DE LA FUNCIONALIDAD DE DISPONIBILIDAD (Sin cambios)
    // ===================================================================

    private void createDayButtons() {
        for (Map.Entry<String, String> entry : mapDay.entrySet()) {
            TextView btn = new TextView(this);
            btn.setText(entry.getKey());
            btn.setTag(entry.getValue());
            btn.setTextColor(ContextCompat.getColor(this, R.color.white));
            btn.setTextSize(16);
            btn.setGravity(Gravity.CENTER);
            btn.setBackgroundResource(R.drawable.day_button_selector);

            int size = (int) (40 * getResources().getDisplayMetrics().density);
            int margin = (int) (4 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> onDayButtonClick((TextView) v));
            dayPickerContainer.addView(btn);
            dayButtons.put(entry.getValue(), btn);
        }
    }

    private void onDayButtonClick(TextView clickedButton) {
        String dayName = (String) clickedButton.getTag();
        View block = dayBlocks.get(dayName);

        if (block != null && block == activeDayBlock) {
            scheduleContainer.removeView(block);
            dayBlocks.remove(dayName);
            clickedButton.setSelected(false);
            activeDayBlock = null;
            return;
        }

        if (activeDayBlock != null) {
            activeDayBlock.setVisibility(View.GONE);
        }

        if (block == null) {
            block = createDayBlock(dayName);
            dayBlocks.put(dayName, block);
            scheduleContainer.addView(block);
        }

        block.setVisibility(View.VISIBLE);
        activeDayBlock = block;
        clickedButton.setSelected(true);

        dayButtons.forEach((name, button) -> {
            if (!name.equals(dayName)) {
                View otherBlock = dayBlocks.get(name);
                LinearLayout slots = otherBlock != null ? otherBlock.findViewById(R.id.slotsContainer) : null;
                if (slots == null || slots.getChildCount() == 0) {
                    button.setSelected(false);
                }
            }
        });
    }

    private View createDayBlock(String dayName) {
        View blockView = LayoutInflater.from(this).inflate(R.layout.layout_day_block, scheduleContainer, false);
        TextView dayNameTextView = blockView.findViewById(R.id.dayNameTextView);
        LinearLayout slotsContainer = blockView.findViewById(R.id.slotsContainer);
        Button addSlotButton = blockView.findViewById(R.id.addSlotButton);
        TextView statusTextView = blockView.findViewById(R.id.statusTextView);

        dayNameTextView.setText(dayName);
        addSlotButton.setOnClickListener(v -> {
            addSlotView(slotsContainer, "09:00", "17:00");
            updateStatus(slotsContainer, statusTextView);
        });

        updateStatus(slotsContainer, statusTextView);
        return blockView;
    }

    private void addSlotView(LinearLayout slotsContainer, String startTime, String endTime) {
        View slotView = LayoutInflater.from(this).inflate(R.layout.layout_time_slot, slotsContainer, false);
        TextView startTimeText = slotView.findViewById(R.id.startTimeText);
        TextView endTimeText = slotView.findViewById(R.id.endTimeText);
        ImageButton removeButton = slotView.findViewById(R.id.removeSlotButton);

        startTimeText.setText(startTime);
        endTimeText.setText(endTime);

        startTimeText.setOnClickListener(v -> showTimePicker(startTimeText));
        endTimeText.setOnClickListener(v -> showTimePicker(endTimeText));

        removeButton.setOnClickListener(v -> {
            ((ViewGroup) slotView.getParent()).removeView(slotView);
            View blockView = (View) slotsContainer.getParent();
            updateStatus(slotsContainer, blockView.findViewById(R.id.statusTextView));
        });

        slotsContainer.addView(slotView);
    }

    private void updateStatus(LinearLayout slotsContainer, TextView statusTextView) {
        statusTextView.setVisibility(slotsContainer.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        if (slotsContainer.getChildCount() == 0) {
            statusTextView.setText("Sin intervalos");
        }
    }

    private void showTimePicker(TextView timeTextView) {
        Calendar c = Calendar.getInstance();
        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            timeTextView.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour));
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
        timePickerDialog.show();
    }


    // ===================================================================
    // MÉTODOS DE LA FUNCIONALIDAD DE TELEMETRÍA (MODIFICADOS)
    // ===================================================================

    private void onScheduleButtonClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            Toast.makeText(this, "Se necesita permiso de ubicación para programar.", Toast.LENGTH_LONG).show();
            return;
        }

        cancelAllAlarms(); // Limpiar alarmas anteriores
        int alarmsSet = 0;

        for (Map.Entry<String, View> entry : dayBlocks.entrySet()) {
            String dayName = entry.getKey();
            View dayBlock = entry.getValue();
            LinearLayout slotsContainer = dayBlock.findViewById(R.id.slotsContainer);

            for (int i = 0; i < slotsContainer.getChildCount(); i++) {
                View slotView = slotsContainer.getChildAt(i);
                TextView startTimeText = slotView.findViewById(R.id.startTimeText);
                TextView endTimeText = slotView.findViewById(R.id.endTimeText);

                String startTime = startTimeText.getText().toString();
                String endTime = endTimeText.getText().toString();

                scheduleAlarm(dayName, startTime, true, alarmsSet * 2); // Alarma de inicio
                scheduleAlarm(dayName, endTime, false, alarmsSet * 2 + 1); // Alarma de fin
                alarmsSet++;
            }
        }

        if (alarmsSet > 0) {
            checkAndStartActiveIntervals();
            String message = "Se programaron " + alarmsSet + " intervalos de recolección.";
            logToUi(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else {
            logToUi("No hay intervalos para programar. Todas las alarmas han sido canceladas.");
            Toast.makeText(this, "No se programó ninguna recolección.", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAndStartActiveIntervals() {
        Calendar now = Calendar.getInstance();
        int currentDay = now.get(Calendar.DAY_OF_WEEK);
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);
        int currentTimeInMinutes = currentHour * 60 + currentMinute;

        for (Map.Entry<String, View> entry : dayBlocks.entrySet()) {
            String dayName = entry.getKey();
            int dayOfWeek = getCalendarDayOfWeek(dayName);

            if (dayOfWeek == currentDay) {
                View dayBlock = entry.getValue();
                LinearLayout slotsContainer = dayBlock.findViewById(R.id.slotsContainer);

                for (int i = 0; i < slotsContainer.getChildCount(); i++) {
                    View slotView = slotsContainer.getChildAt(i);
                    TextView startTimeText = slotView.findViewById(R.id.startTimeText);
                    TextView endTimeText = slotView.findViewById(R.id.endTimeText);

                    String[] startParts = startTimeText.getText().toString().split(":");
                    String[] endParts = endTimeText.getText().toString().split(":");

                    int startMinutes = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
                    int endMinutes = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);

                    if (currentTimeInMinutes >= startMinutes && currentTimeInMinutes < endMinutes) {
                        Intent intent = new Intent(this, GPSLoggerService.class);
                        intent.setAction(ACTION_START_LOGGING);
                        intent.putExtra("DEVICE_ID", deviceId);
                        startService(intent);
                        logToUi("Iniciando recolección inmediata - intervalo activo detectado");
                        break;
                    }
                }
            }
        }
    }

    private void scheduleAlarm(String dayName, String time, boolean isStart, int requestCode) {
        int dayOfWeek = getCalendarDayOfWeek(dayName);
        if (dayOfWeek == -1) return;

        String[] timeParts = time.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 7);
        }

        Intent intent = new Intent(this, GPSLoggerService.class);
        intent.setAction(isStart ? ACTION_START_LOGGING : ACTION_STOP_LOGGING);
        intent.putExtra("DEVICE_ID", deviceId);

        PendingIntent pendingIntent = PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY * 7, pendingIntent);
        activeAlarms.add(pendingIntent);

        String action = isStart ? "INICIO" : "FIN";
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault());
        logToUi(String.format("Alarma de %s programada para %s a las %s. Próxima: %s", action, dayName, time, sdf.format(calendar.getTime())));
    }

    private void cancelAllAlarms() {
        logToUi("Cancelando todas las alarmas programadas...");
        if (alarmManager != null) {
            for (int i = 0; i < 50; i++) {
                Intent intent = new Intent(this, GPSLoggerService.class);
                intent.setAction(ACTION_START_LOGGING);
                PendingIntent startPi = PendingIntent.getService(this, i, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (startPi != null) {
                    alarmManager.cancel(startPi);
                    startPi.cancel();
                }
                intent.setAction(ACTION_STOP_LOGGING);
                PendingIntent stopPi = PendingIntent.getService(this, i, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (stopPi != null) {
                    alarmManager.cancel(stopPi);
                    stopPi.cancel();
                }
            }
        }
        activeAlarms.clear();
        logToUi("Alarmas canceladas.");
    }

    private int getCalendarDayOfWeek(String dayName) {
        switch (dayName) {
            case "Domingo": return Calendar.SUNDAY;
            case "Lunes": return Calendar.MONDAY;
            case "Martes": return Calendar.TUESDAY;
            case "Miércoles": return Calendar.WEDNESDAY;
            case "Jueves": return Calendar.THURSDAY;
            case "Viernes": return Calendar.FRIDAY;
            case "Sábado": return Calendar.SATURDAY;
            default: return -1;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                logToUi("Permiso de ubicación concedido. Ahora puede programar o iniciar la recolección.");
                Toast.makeText(this, "Permiso concedido. Intente la acción de nuevo.", Toast.LENGTH_LONG).show();
            } else {
                logToUi("Permiso de ubicación denegado.");
                Toast.makeText(this, "El permiso de ubicación es necesario para la telemetría.", Toast.LENGTH_LONG).show();
            }
        }
    }


    // --- MÉTODOS AUXILIARES Y DE LOG (Sin cambios importantes) ---

    private void showAllSensorData() {
        logToUi("Consultando todos los datos de la base de datos...");
        JSONArray data = dbHelper.getAllSensorData();
        txtApiLogs.setText("--- Datos del Sensor Almacenados ---\n");
        if (data.length() == 0) {
            txtApiLogs.append("No hay datos almacenados en la base de datos.\n");
            return;
        }
        try {
            for (int i = 0; i < data.length(); i++) {
                JSONObject record = data.getJSONObject(i);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String formattedDate = sdf.format(new Date(record.getLong("timestamp")));
                String entry = String.format(Locale.getDefault(),
                        "[%d] Lat: %.4f, Lon: %.4f, Time: %s\n",
                        i + 1, record.getDouble("latitud"), record.getDouble("longitud"), formattedDate);
                txtApiLogs.append(entry);
            }
        } catch (Exception e) {
            logToUi("Error al formatear los datos: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "GPS Logger Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void setupLogReceiver() {
        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && LOG_UPDATE_ACTION.equals(intent.getAction())) {
                    String logMessage = intent.getStringExtra("log_message");
                    if (logMessage != null) logToUi(logMessage, false);
                }
            }
        };
        ContextCompat.registerReceiver(this, logReceiver, new IntentFilter(LOG_UPDATE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
        logToUi("BroadcastReceiver registrado.");
    }

    private void logToUi(String message) {
        logToUi(message, true);
    }

    private void logToUi(String message, boolean addTimestamp) {
        Log.d(TAG, message);
        String finalMessage = message;
        if (addTimestamp) {
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            finalMessage = "[" + timestamp + "] " + message;
        }
        txtApiLogs.append(finalMessage + "\n");
    }

    private String loadOrGenerateDeviceId() {
        File file = new File(getFilesDir(), "device_id.txt");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                return new String(data);
            } catch (IOException e) {
                logToUi("ERROR al leer el ID: " + e.getMessage());
            }
        }
        String newId = UUID.randomUUID().toString();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(newId.getBytes());
            logToUi("Nuevo ID de dispositivo generado.");
        } catch (IOException e) {
            logToUi("ERROR al guardar el ID: " + e.getMessage());
        }
        return newId;
    }

    private String getLocalIp() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
        } catch (Exception e) {
            return "N/A";
        }
    }

    private void startHttpService() {
        Intent httpServiceIntent = new Intent(this, HttpService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(httpServiceIntent);
        } else {
            startService(httpServiceIntent);
        }
        logToUi("Iniciando servicio HTTP en puerto 8080...");
    }

    private void stopHttpService() {
        Intent httpServiceIntent = new Intent(this, HttpService.class);
        stopService(httpServiceIntent);
        logToUi("Deteniendo servicio HTTP...");
    }

    private boolean isHttpServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (HttpService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logReceiver != null) unregisterReceiver(logReceiver);
        if (dbHelper != null) dbHelper.close();
    }
}