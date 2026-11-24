# AsisteDragon

Aplicación Android para la gestión de asistencia y control de visitas del profesorado de la Universidad Tecnológica Fidel Velázquez (UTFV), conectada a microservicios contenerizados con Docker y automatizada mediante GitHub Actions y Jenkins.

## Descripción

Asiste Dragon nace como proyecto de la asignatura de Seguridad en las Aplicaciones Móviles, de la carrera de Ingeniería en Desarrollo y Gestión de Software, con el objetivo de simplificar el registro y control de asistencia docente y visitas institucionales. La app permite escanear horarios en PDF, gestionar usuarios, registrar asistencias y sincronizar eventos en tiempo real mediante microservicios contenerizados con almacenamiento en Redis.

## Características principales

- Control y registro de visitas: contador transaccional en tiempo real almacenado en Redis
- Parsing automático de horarios: extrae información de horarios directamente desde archivos PDF usando OCR
- Registro de asistencia por QR: escaneo de códigos QR para marcar asistencia y almacenar registros en Redis
- Verificación por PIN: capa adicional de seguridad para confirmar identidad mediante hash SHA-256
- Notificaciones automáticas: recordatorios de clases próximas y avisos de jornada
- Reportes de asistencia: visualización del historial y estadísticas registradas
- Perfiles de usuario: gestión de cuentas, roles y perfiles mediante correo institucional (@utfv.edu.mx)
- Visor de horarios integrado: consulta el PDF original desde la propia app

## Tecnologías utilizadas

- Lenguaje móvil: Java (Android SDK)
- Backend / Microservicios: Node.js, Express
- Base de datos / Almacenamiento: Redis (Docker)
- Contenedores: Docker Engine, Docker Compose, Docker Hub
- Integración Continua (CI/CD): Jenkins (`Jenkinsfile`) y GitHub Actions
- Autenticación y datos auxiliares: Firebase (Authentication, Firestore, Storage)
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
  - Microservicio de Asistencias/Visitas (Emulador Android): `http://10.0.2.2:3000` (Host: `http://localhost:3000`)
  - Microservicio de Usuarios (Emulador Android): `http://10.0.2.2:3001` (Host: `http://localhost:3001`)
  - Jenkins (Pipeline DevOps): `http://localhost:8080`

## Configuración del proyecto

Este repositorio no incluye credenciales ni archivos de configuración sensibles.

## Requisitos

- Android 7.0 (API 24) o superior
- Android Studio (versión reciente)
- Docker Engine y Docker Compose
- Node.js v20+ y npm (para pruebas locales)
- Jenkins (configurado con Git y Docker)
- Cuenta de Docker Hub

## Estado del proyecto

Microservicios de Usuarios (puerto 3001) y Asistencias/Visitas (puerto 3000) integrados con Redis y Docker. Pipelines de integración continua (CI) configurados y en funcionamiento mediante Jenkins (`Jenkinsfile`) y GitHub Actions (ejecutan pruebas del backend, compilan imágenes Docker y validan el proyecto). Módulos de OCR, horarios, escaneo QR y notificaciones funcionando correctamente.
