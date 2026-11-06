# Changelog

Todas las versiones relevantes de NetScope. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el versionado [SemVer](https://semver.org/lang/es/).

## [1.0.0] · 2026-11-06

### Añadido
- Descubrimiento de dispositivos por TCP, mDNS/Bonjour, SSDP/UPnP, NetBIOS y DNS inverso.
- Escaneo de puertos con presets rápidos, comunes, web, personalizado y completo (1–65535), banners y clasificación de riesgo.
- Identificación de nombre, fabricante, tipo, sistema operativo y servicios; nombres personalizados y dispositivos de confianza con alerta de nuevos.
- Escaneo Wi-Fi con SSID, BSSID, banda, canal, seguridad y señal; distancia estimada al punto de acceso.
- Laboratorio de auditoría: objetivo por IP o dominio con descubrimiento automático de puerto, huella HTTP/TLS, wordlist de 114 rutas, proximidad por RTT e inventario Wi-Fi.
- Consola ADB sobre TCP con comando único y shell interactivo persistente, autenticación RSA y fingerprint de la clave.
- Historial de escaneos con diferencias entre ejecuciones.
- Exportación a JSON y CSV en `Download/NetScope`.
- Escaneo en servicio en primer plano con progreso y cancelación.
- Icono adaptativo, modo desarrollador y temas claros sobre fondo oscuro.

### Corregido
- La lista de dispositivos, el resumen y el laboratorio muestran los puertos abiertos concretos, no solo el conteo.
- La clave RSA de ADB se guarda de forma persistente y la autorización se solicita una sola vez por equipo.
- El flujo AUTH de ADB firma cada token y solo envía la clave pública cuando el dispositivo la pide.
- El objetivo ADB se conserva entre sesiones.

[1.0.0]: https://github.com/FerchoSD/NetScope-/releases/tag/v1.0.0
