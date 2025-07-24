# Sistema de Monitoreo Remoto de Dispositivos

Este proyecto es una aplicación de Android diseñada para recolectar datos de sensores GPS y de estado del dispositivo, almacenarlos localmente y exponerlos a través de una API REST segura e integrada. La aplicación está pensada para escenarios donde se necesita monitorear una flota de dispositivos móviles, como en vehículos.

## Características Principales

* **Recolección de Datos GPS**: La aplicación recolecta latitud, longitud y marca de tiempo del sensor GPS cada 30 segundos.
* **Almacenamiento Local**: Todos los datos recolectados se guardan de forma persistente en una base de datos local SQLite.
* **Recolección Configurable**: La interfaz de usuario permite programar la recolección de datos para días y horas específicos.
* **Servidor API Integrado**: La aplicación ejecuta un servidor HTTP interno que expone endpoints para la consulta remota.
* **Autenticación Segura**: Todas las peticiones a la API requieren un token de autenticación (JWT Bearer Token) que se obtiene a través de un endpoint de login.
* **Endpoints de Datos**:
    * `/api/sensor_data`: Devuelve los datos del GPS en un rango de tiempo especificado.
    * `/api/device_status`: Proporciona información actual del dispositivo como nivel de batería, conectividad, almacenamiento y versión del SO.
* **Interfaz de Usuario Sencilla**: La UI permite iniciar/detener la recolección, muestra el estado actual, la IP local para pruebas y los datos almacenados.

***

## Requisitos Previos

* Android Studio (versión recomendada: Iguana o superior).
* Un dispositivo físico Android o un emulador con los **Servicios de Google Play** (necesario para el `FusedLocationProviderClient`).
* Conexión a una red WiFi para que el dispositivo y el cliente de prueba (ej. tu computadora) estén en la misma red.

***

## Configuración y Ejecución

1.  **Clonar el Repositorio**
    ```bash
    git clone <URL_DEL_REPOSITORIO>
    ```

2.  **Abrir en Android Studio**
    * Inicia Android Studio.
    * Selecciona "Open" y navega hasta la carpeta del proyecto clonado.
    * Espera a que Gradle sincronice y construya el proyecto. Las dependencias necesarias (como NanoHTTPD y JWT) se descargarán automáticamente.

3.  **Ejecutar la Aplicación**
    * Conecta tu dispositivo Android a tu computadora (con la depuración USB habilitada) o inicia un emulador de Android.
    * Presiona el botón "Run 'app'" (icono de play verde) en Android Studio.
    * La aplicación se instalará y se iniciará en tu dispositivo/emulador.

4.  **Conceder Permisos**
    * Al iniciarse, la aplicación te pedirá permiso para acceder a la ubicación del dispositivo. **Debes concederlo** para que la recolección de GPS funcione.

5.  **¡Listo para Usar!**
    * La pantalla principal mostrará el **ID del Dispositivo** y la **Dirección IP local** seguida del puerto `:8080`. Anota esta dirección IP, la necesitarás para probar la API.
    * Puedes usar los botones "Recolectar Ahora" o programar un horario para empezar a generar datos.

***

## Uso de la API

Para interactuar con la API, puedes usar una herramienta como `cURL` o Postman desde una computadora conectada a la misma red WiFi que el dispositivo Android. Reemplaza `<IP_DEL_DISPOSITIVO>` con la dirección que aparece en la aplicación.

### 1. Obtener un Token de Autenticación

Primero, necesitas obtener un token enviando tu nombre de usuario al endpoint `/login`.

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser"}' \
  http://<IP_DEL_DISPOSITIVO>:8080/login
```
La respuesta será un JSON con tu token. Cópialo para los siguientes pasos.

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 2\. Consultar el Estado del Dispositivo

Usa el token obtenido como un "Bearer Token" en la cabecera `Authorization` para acceder a los endpoints protegidos.

```bash
curl -X GET \
  -H "Authorization: Bearer <TOKEN_OBTENIDO>" \
  http://<IP_DEL_DISPOSITIVO>:8080/api/device_status
```

### 3\. Consultar los Datos del Sensor

Para obtener los datos del GPS, especifica un rango de tiempo con los parámetros `start_time` y `end_time` (en milisegundos desde la época).

```bash
# Ejemplo para obtener los datos de la última hora
END_TIME=$(date +%s000)
START_TIME=$((END_TIME - 3600000))

curl -X GET \
  -H "Authorization: Bearer <TOKEN_OBTENIDO>" \
  "http://<IP_DEL_DISPOSITIVO>:8080/api/sensor_data?start_time=$START_TIME&end_time=$END_TIME"
```
