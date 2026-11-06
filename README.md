# NetScope

**Laboratorio de red local para Android: descubrimiento, identificación y auditoría no destructiva.**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Java-11-007396?logo=openjdk&logoColor=white)](https://openjdk.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-69%20passing-brightgreen.svg)](test.sh)

NetScope convierte un teléfono Android en una estación de reconocimiento de red: descubre equipos, identifica servicios y puertos, mide proximidad y ejecuta un shell ADB interactivo contra tus propios dispositivos. Todo el análisis ocurre en el teléfono, sin telemetría y sin root.

> Úsalo únicamente en redes de tu propiedad o en las que tengas autorización expresa.

## Características

### Descubrimiento de dispositivos
- Barrido TCP del segmento local con concurrencia configurable (64 por defecto, 512 hosts máximo).
- Fuentes combinadas: mDNS/Bonjour, SSDP/UPnP, NetBIOS y DNS inverso.
- Hostname, fabricante inferido, tipo de equipo, sistema operativo y servicios detectados.
- Nombres personalizados por IP y marca de confianza con alerta de equipos nuevos.
- Historial de escaneos y comparación con el anterior (nuevos, desaparecidos, puertos cambiados).

### Escaneo de puertos
- Presets rápidos (20), comunes (100), web, personalizado y completo (1–65535).
- Captura de banners, catálogo de servicios y clasificación de riesgo.
- Resultados con puertos abiertos visibles en la lista, el resumen y el detalle.

### Wi-Fi
- SSID, BSSID, banda, canal, seguridad y potencia de señal de la red conectada y de las cercanas.
- Estimación de distancia al punto de acceso por RSSI.
- Enumeración de las IP de la red conectada.

### Laboratorio
- Objetivo por IP **o dominio**; el puerto se descubre automáticamente al escanear.
- Huella HTTP: cabeceras, título, servidor, TLS y avisos de seguridad.
- Wordlist de 114 rutas habituales con código de respuesta, tamaño y redirecciones.
- Proximidad del objetivo por RTT (conexión TCP repetida).
- Inventario Wi-Fi con distancia estimada.
- **Consola ADB sobre TCP** con comando único y **shell interactivo**: sesión persistente, entrada de órdenes y salida en vivo.
  - Autenticación RSA con clave guardada en el dispositivo; el diálogo «Recordar este equipo» solo aparece la primera vez.
  - Fingerprint SHA-256 visible para verificar la clave.
  - Compatible con `127.0.0.1:5555` (depuración inalámbrica de tu propio teléfono) y con otros equipos con adbd expuesto y autorizado.

### Exportación y segundo plano
- Exportación completa a JSON y CSV en `Download/NetScope`.
- Escaneo en servicio en primer plano con progreso y cancelación.

## Requisitos

- Android 8.0 (API 26) o superior.
- Wi-Fi o Ethernet en la misma red que los equipos a analizar.
- Para ADB: depuración activada y adbd escuchando en TCP (`adb tcpip 5555` o depuración inalámbrica).

Permisos declarados: Internet, estado de red, estado y multicast Wi-Fi, ubicación precisa (lectura del SSID), dispositivos Wi-Fi cercanos, notificaciones, servicio en primer plano y wake lock. NetScope no solicita root.

## Instalación

Descarga o compila el APK y instálalo:

```
adb install -r NetScope-v1.0.apk
```

En el proyecto, `build.sh` deja el APK firmado en `dist/` y una copia en `Download/NetScope-v1.0.apk`.

## Compilación

El proyecto no usa Gradle: se compila con las herramientas del SDK de Android y JDK 11+.

```bash
pkg install aapt2 apksigner d8 zipalign openjdk-17
./build.sh
```

`build.sh` compila recursos con `aapt2`, código con `javac --release 11`, genera `classes.dex` con `d8`, alinea con `zipalign` y firma con `apksigner` (v2/v3). Si no existe `keystore/netscope.jks`, se genera automáticamente; para distribuir la app debes usar tu propia clave y contraseña.

## Pruebas

```bash
./test.sh
```

Ejecuta 69 pruebas JUnit sobre la lógica pura (modelos, escáneres, exportadores, utilidades) sin necesidad de emulador.

## Estructura

```
java/com/netscope/lan/
├── core/
│   ├── db/          Historial con SQLite
│   ├── diff/        Comparación entre escaneos
│   ├── export/      Exportadores JSON/CSV y escritura en Download
│   ├── model/       Device, PortResult, ScanReport, ScanConfig…
│   └── net/         Escáneres, descubrimiento, HTTP/TLS, ADB, utilidades de red
├── service/         Escaneo en primer plano
├── ui/              Actividades, adaptadores y vistas
└── util/            Preferencias, formatos y permisos
res/                 Layouts, drawables, temas y cadenas
tests/               Pruebas JUnit
tools/               android.jar para compilar
build.sh             Compilación y firma del APK
test.sh              Compilación y ejecución de pruebas
```

## Uso responsable

NetScope es una herramienta de **reconocimiento y auditoría no destructiva**. No implementa desautenticación Wi-Fi, ARP spoofing ni denegación de servicio, y no instala binarios en los equipos analizados.

- Analiza solo redes y dispositivos sobre los que tengas autorización.
- La consola ADB requiere que el dispositivo destino acepte la clave RSA de NetScope.
- Si detectas un intruso en tu red, NetScope lo identifica y alerta; el bloqueo real debe aplicarse desde tu router.
- La app no envía datos a Internet ni incluye telemetría.

## Limitaciones

- Android 10+ oculta la tabla ARP y la MAC real sin root; NetScope muestra nombre, fabricante y tipo inferidos, no inventa direcciones MAC.
- El escaneo Wi-Fi depende de los permisos de ubicación y de los límites de frecuencia de Android.
- La distancia por RSSI y la proximidad por RTT son estimaciones orientativas.
- ADB requiere depuración activa y que adbd escuche en TCP; el acceso depende del usuario shell del dispositivo.

## Hoja de ruta

- Firma de APK con clave de distribución propia.
- Más fuentes de identificación (SNMP, UPnP description).
- Historial comparativo con exportación de diferencias.

## Licencia

Distribuido bajo la licencia [MIT](LICENSE).

## Autor

**FerchoSD** · [github.com/FerchoSD](https://github.com/FerchoSD)
