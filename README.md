# AsisteDragon

Aplicación Android para la gestión de asistencia y control de visitas del profesorado de la Universidad Tecnológica Fidel Velázquez (UTFV), conectada a servicios contenerizados con Docker y automatizada mediante GitHub Actions.

## Descripción

Asiste Dragon nace como proyecto de la asignatura de Seguridad en las Aplicaciones Móviles, de la carrera de Ingeniería en Desarrollo y Gestión de Software, con el objetivo de simplificar el registro y control de asistencia docente y visitas institucionales. La app permite escanear horarios en PDF, registrar asistencias y sincronizar eventos en tiempo real mediante un backend contenerizado con almacenamiento en Redis.

## Características principales

- Control y registro de visitas: contador transaccional en tiempo real almacenado en Redis
- Parsing automático de horarios: extrae información de horarios directamente desde archivos PDF usando OCR
- Registro de asistencia por QR: escaneo de códigos QR para marcar asistencia
- Verificación por PIN: capa adicional de seguridad para confirmar identidad mediante hash SHA-256
- Notificaciones automáticas: recordatorios de clases próximas y avisos de jornada
- Reportes de asistencia: visualización del historial registrado
- Perfiles de usuario: gestión de cuentas mediante correo institucional (@utfv.edu.mx)
- Visor de horarios integrado: consulta el PDF original desde la propia app

## Tecnologías utilizadas

- Lenguaje móvil: Java (Android SDK)
- Backend: Node.js, Express
- Base de datos / Almacenamiento: Redis (Docker)
- Contenedores: Docker Engine, Docker Compose, Docker Hub
- Autenticación y datos: Firebase (Authentication, Firestore, Storage)
- OCR: Mistral AI (modelo pixtral-12b)
- Escaneo QR/código de barras: ML Kit
- Cámara: CameraX
- Red: OkHttp
- Carga de imágenes: Glide
- Pruebas automatizadas: Jest, Supertest

## Comandos de ejecución

* Iniciar servicios del backend y Redis con Docker:
  `docker compose up --build -d`

* Ejecutar pruebas del backend:
  `cd backend && npm test`

* Compilar la aplicación Android:
  `cd android-app && ./gradlew assembleDebug`

* Direcciones de conexión:
  - Emulador Android: `http://10.0.2.2:3000`
  - Máquina local (host): `http://localhost:3000`

## Configuración del proyecto

Este repositorio no incluye credenciales ni archivos de configuración sensibles.

## Requisitos

- Android 7.0 (API 24) o superior
- Android Studio (versión reciente)
- Docker Engine y Docker Compose
- Node.js v20+ y npm (para pruebas locales)
- Cuenta de Docker Hub

## Estado del proyecto

Módulo de control de visitas integrado con Redis y Docker. Pipeline de integración continua (CI) configurado y en funcionamiento mediante GitHub Actions (ejecuta pruebas del backend, compila el APK y publica la imagen en Docker Hub). Módulos de OCR, horarios, escaneo QR y notificaciones funcionando correctamente.
