# AsisteDragon

Aplicación Android para la gestión de asistencia del profesorado de la Universidad Tecnológica Fidel Velázquez (UTFV), con reconocimiento automático de horarios mediante inteligencia artificial.

## Descripción

Asiste Dragon nace como proyecto de la asignatura de Seguridad en las Aplicaciones Móviles, de la carrera de Ingeniería en Desarrollo y Gestión de Software, con el objetivo de simplificar el registro y control de asistencia docente. 
La app permite escanear el horario en PDF de un profesor y extraer automáticamente sus clases mediante OCR, eliminando la captura manual de datos.

## Características principales

- Parsing automático de horarios: extrae información de horarios directamente desde archivos PDF usando OCR
- Registro de asistencia por QR: escaneo de códigos QR para marcar asistencia
- Verificación por PIN: capa adicional de seguridad para confirmar identidad
- Notificaciones automáticas: recordatorios de clases próximas
- Reportes de asistencia: visualización del historial registrado
- Perfiles de usuario: gestión de cuentas mediante Firebase Authentication
- Visor de horarios integrado: consulta el PDF original desde la propia app

## Tecnologías utilizadas

- Lenguaje: Java
- Backend / Datos: Firebase (Authentication, Firestore, Storage)
- OCR: Mistral AI (modelo pixtral-12b)
- Escaneo QR/código de barras: ML Kit
- Cámara: CameraX
- Red: OkHttp
- Carga de imágenes: Glide

## Arquitectura del OCR

El parsing de horarios usa una arquitectura de dos pasos: primero se procesa el PDF para extraer las imágenes de cada página, y después cada imagen se envía al modelo de visión de Mistral AI para interpretar el contenido y estructurarlo como datos de horario utilizables por la app.

## Configuración del proyecto

Este repositorio no incluye credenciales ni archivos de configuración sensibles. 

## Requisitos

- Android 7.0 (API 24) o superior
- Android Studio (versión reciente)
- Cuenta de Firebase propia
- API key de Mistral AI

## Estado del proyecto

Módulo de gestión de horarios (Horario.java) documentado. Integración con Firestore y notificaciones automáticas funcionando correctamente.
