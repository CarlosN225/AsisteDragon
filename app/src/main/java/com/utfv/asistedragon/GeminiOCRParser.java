package com.utfv.asistedragon;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GeminiOCRParser {
    private static final String TAG = "GeminiOCRParser";

    // ⚠️ CAMBIAR POR TU API KEY
    private static final String API_KEY = "AIzaSyACYhjE9iWaKOeCkgwqISJs_69sQVPH1r0";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;

    public interface GeminiCallback {
        void onSuccess(List<Clase> clases);
        void onError(String error);
    }
// En GeminiOCRParser.java, agrega esta constante al inicio de la clase:

    private static final String[] MATERIAS_UTFV = {
            // 1er Cuatrimestre
            "Álgebra Lineal",
            "Desarrollo de habilidades del Pensamiento Lógico",
            "Fundamentos de TI",
            "Fundamentos de Redes",
            "Metodología de la Programación",
            "Expresión Oral y Escrita I",
            "Formación Sociocultural I",

            // 2º Cuatrimestre
            "Funciones Matemáticas",
            "Metodologías y Modelado de Desarrollo de Software",
            "Interconexión de redes",
            "Programación Orientada a Objetos",
            "Introducción al Diseño Digital",
            "Base de Datos",
            "Formación Sociocultural II",

            // 3er Cuatrimestre
            "Cálculo Diferencial",
            "Probabilidad y Estadística",
            "Sistemas Operativos",
            "Integradora I",
            "Aplicaciones Web",
            "Bases de Datos para Aplicaciones",
            "Formación Sociocultural III",

            // 4º Cuatrimestre
            "Estándares y métricas para el Desarrollo de Software",
            "Principios para IoT",
            "Diseño de Apps",
            "Estructura de Datos Aplicadas",
            "Aplicaciones Web Orientada a Servicios",
            "Evaluación y mejora para el Desarrollo de Software",
            "Formación Sociocultural IV",

            // 5º Cuatrimestre
            "Aplicaciones de IoT",
            "Desarrollo Móvil Multiplataforma",
            "Integradora II",
            "Aplicaciones Web para I4.0",
            "Bases de Datos para cómputo en la nube",
            "Expresión oral y escrita II",

            // 7º Cuatrimestre
            "Matemáticas para Ingeniería I",
            "Metodologías para el desarrollo de proyectos",
            "Arquitecturas de software",
            "Experiencia de Usuario",
            "Seguridad Informática",
            "Administración del tiempo",

            // 8º Cuatrimestre
            "Matemáticas para Ingeniería II",
            "Administración de bases de datos",
            "Desarrollo web profesional",
            "Seguridad en el desarrollo de aplicaciones",
            "Planeación y organización del trabajo",

            // 9º Cuatrimestre
            "Administración de proyectos de TI",
            "Extracción de conocimiento en bases de datos",
            "Desarrollo web integral",
            "Desarrollo para dispositivos inteligentes",
            "Dirección de equipos de alto rendimiento",

            // 10º Cuatrimestre
            "Gestión del proceso de desarrollo de software",
            "Aplicaciones web progresivas",
            "Integradora",
            "Desarrollo móvil integral",
            "Creación de videojuegos",
            "Negociación empresarial",

            // Nombres alternativos/variantes del OCR
            "Programación Estructurada",
            "Fundamentos de Programación",
            "Proyecto Integrador I",
            "Proyecto Integrador II",
            "Conmutación y Enrutamiento de Redes",
            "Habilidades Socioemocionales y Manejo de Conflictos",
            "Liderazgo de Equipo de Alto Desempeño"
    };
    /**
     * Parsea el texto OCR usando Gemini AI
     */
    public static void parsearHorario(String textoOCR, GeminiCallback callback) {
        new Thread(() -> {
            try {
                Log.i(TAG, "📤 Enviando texto a Gemini...");

                String prompt = crearPrompt(textoOCR);
                String respuesta = llamarGeminiAPI(prompt);
                List<Clase> clases = extraerClases(respuesta);

                Log.i(TAG, "✅ Gemini procesó " + clases.size() + " clases");
                callback.onSuccess(clases);

            } catch (Exception e) {
                Log.e(TAG, "❌ Error Gemini: " + e.getMessage(), e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    /**
     * Crea el prompt optimizado para Gemini
     */
    private static String crearPrompt(String textoOCR) {
        return "Eres un parser OCR especializado en horarios UTFV.\n\n" +
                "REGLAS CRÍTICAS (NO NEGOCIABLES):\n" +
                "1. NUNCA incluyas CLAVES (24DSM034, 20IDGS009, etc.) en el campo 'materia'\n" +
                "   ❌ INCORRECTO: \"24DSM034 Estándares y Métricas\"\n" +
                "   ✅ CORRECTO: \"Estándares y Métricas para el Desarrollo de Software\"\n\n" +
                "2. USA NOMBRES COMPLETOS (no abrevies):\n" +
                "   ❌ \"Base de Datos\" → ✅ \"Administración de bases de datos\"\n" +
                "   ❌ \"Metodologías\" → ✅ \"Metodologías para el desarrollo de proyectos\"\n" +
                "   ❌ \"Proyecto Integrador\" → ✅ \"Proyecto Integrador II\" (con número romano)\n\n" +
                "3. CORRIGE ERRORES DEL OCR:\n" +
                "   - \"Conmutacion y Ennutamientos\" → \"Conmutación y Enrutamiento de Redes\"\n" +
                "   - \"Base de Datos DGs\" → \"Administración de bases de datos\"\n" +
                "   - \"Sistenas\" → \"Sistemas\"\n" +
                "   - \"2O2\" → \"202\" (letra O por cero)\n" +
                "   - \"201DGS016\" → \"20IDGS016\" (1 por I)\n\n" +
                "4. EXTRAE TODAS LAS MATERIAS de la tabla superior (DISTRIBUCIÓN DE HORAS):\n" +
                "   Esta tabla lista TODAS las materias con sus claves y grupos.\n" +
                "   NO omitas ninguna materia de esta tabla.\n\n" +
                "5. IGNORA completamente:\n" +
                "   - Inglés I, II, III, IV, V, VI, VII, VIII, IX\n" +
                "   - Desarrollo Fortalecimiento Académico\n" +
                "   - Formación Sociocultural I, II, III, IV\n" +
                "   - Firmas, cabeceras, metadatos\n\n" +
                "6. NORMALIZA GRUPOS:\n" +
                "   - \"DSM 204\" → \"DSM-204\"\n" +
                "   - \"DGS802\" → \"DGS-802\"\n" +
                "   - \"DGSE 1001\" → \"DGSE-1001\"\n\n" +
                "7. Para cada clase detectada en el HORARIO SEMANAL, extrae:\n" +
                "   - materia: nombre completo SIN clave, SIN typos\n" +
                "   - grupo: DSM-XXX, DGS-XXX o DGSE-XXXX\n" +
                "   - dia: Lunes, Martes, Miércoles, Jueves, Viernes, Sábado, Domingo\n" +
                "   - horaInicio: HH:MM\n" +
                "   - horaFin: HH:MM\n" +
                "   - salon: código del aula o \"\"\n\n" +
                "CATÁLOGO DE NOMBRES VÁLIDOS:\n" +
                "Solo usa EXACTAMENTE estos nombres (copia tal cual):\n" +
                "- Sistemas Operativos\n" +
                "- Proyecto Integrador I\n" +
                "- Proyecto Integrador II\n" +
                "- Metodologías para el desarrollo de proyectos\n" +
                "- Administración de bases de datos\n" +
                "- Integradora\n" +
                "- Integradora I\n" +
                "- Integradora II\n" +
                "- Conmutación y Enrutamiento de Redes\n" +
                "- Desarrollo Web Profesional\n" +
                "- Desarrollo Web Integral\n" +
                "- Programación Estructurada\n" +
                "- Estándares y Métricas para el Desarrollo de Software\n" +
                "- Seguridad en el Desarrollo de Aplicaciones\n" +
                "- Creación de Videojuegos\n" +
                "- Análisis y Diseño de Software\n" +
                "- Fundamentos de Programación\n" +
                "- Probabilidad y Estadística\n" +
                "- Base de Datos\n" +
                "- Bases de Datos para Aplicaciones\n" +
                "\n" +
                "Si detectas una materia que NO está en el catálogo, usa el nombre MÁS CERCANO.\n\n" +
                "FORMATO DE SALIDA:\n" +
                "Array JSON puro. Sin markdown, sin explicaciones, sin comentarios.\n" +
                "EJEMPLO CORRECTO:\n" +
                "[{\"materia\":\"Administración de bases de datos\",\"grupo\":\"DGS-802\",\"dia\":\"Martes\",\"horaInicio\":\"16:00\",\"horaFin\":\"17:00\",\"salon\":\"D104\"}]\n\n" +
                "EJEMPLO INCORRECTO (NO HAGAS ESTO):\n" +
                "[{\"materia\":\"24DSM014 Sistemas Operativos\",\"grupo\":\"DSM 204\",\"dia\":\"miercoles\",\"horaInicio\":\"4:00 PM\",\"horaFin\":\"5:00 PM\",\"salon\":\"\"}]\n\n" +
                "TEXTO OCR:\n" + textoOCR;
    }
     /**
     * Llama a la API de Gemini
     */
    private static String llamarGeminiAPI(String prompt) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        JSONObject requestBody = new JSONObject();
        try {
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();

            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

        } catch (Exception e) {
            throw new IOException("Error creando request: " + e.getMessage());
        }

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        Log.d(TAG, "📡 Llamando a Gemini API...");

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            String error = response.body() != null ? response.body().string() : "Sin detalles";
            throw new IOException("Error API " + response.code() + ": " + error);
        }

        String responseBody = response.body().string();
        Log.d(TAG, "📥 Respuesta recibida: " + responseBody.substring(0, Math.min(200, responseBody.length())));

        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray candidates = json.getJSONArray("candidates");
            JSONObject firstCandidate = candidates.getJSONObject(0);
            JSONObject contentObj = firstCandidate.getJSONObject("content");
            JSONArray partsArray = contentObj.getJSONArray("parts");
            String text = partsArray.getJSONObject(0).getString("text");

            // Limpiar markdown si Gemini lo incluye
            text = text.replace("```json", "")
                    .replace("```", "")
                    .trim();

            Log.d(TAG, "📄 JSON extraído: " + text.substring(0, Math.min(300, text.length())));
            return text;

        } catch (Exception e) {
            throw new IOException("Error parseando respuesta de Gemini: " + e.getMessage());
        }
    }

    /**
     * Extrae las clases del JSON devuelto por Gemini
     */
    private static List<Clase> extraerClases(String jsonResponse) {
        try {
            Gson gson = new Gson();
            List<Clase> clases = gson.fromJson(
                    jsonResponse,
                    new TypeToken<List<Clase>>(){}.getType()
            );

            if (clases == null) {
                Log.w(TAG, "⚠️ Gemini devolvió null");
                return new ArrayList<>();
            }

            // Validar y limpiar cada clase
            List<Clase> clasesValidas = new ArrayList<>();
            for (Clase clase : clases) {
                if (esClaseValida(clase)) {
                    // Normalizar datos
                    clase.setMateria(clase.getMateria().trim());
                    clase.setGrupo(normalizarGrupo(clase.getGrupo()));
                    clase.setDia(normalizarDia(clase.getDia()));

                    clasesValidas.add(clase);
                    Log.d(TAG, "✅ " + clase.getMateria() + " | " + clase.getGrupo() +
                            " | " + clase.getDia() + " | " + clase.getHoraInicio() + "-" + clase.getHoraFin());
                } else {
                    Log.w(TAG, "⚠️ Clase inválida descartada: " + clase.getMateria());
                }
            }

            return clasesValidas;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error parseando JSON de Gemini", e);
            Log.e(TAG, "JSON problemático: " + jsonResponse);
            return new ArrayList<>();
        }
    }

    /**
     * Valida que una clase tenga los datos mínimos necesarios
     */
    private static boolean esClaseValida(Clase clase) {
        if (clase == null) return false;

        // Validar materia
        if (clase.getMateria() == null || clase.getMateria().trim().length() < 3) return false;

        // Validar grupo (debe ser DSM-XXX o DGS-XXX)
        if (clase.getGrupo() == null || !clase.getGrupo().matches("(DSM|DGS|DGSE)-\\d{3,4}")) return false;

        // Validar día
        if (clase.getDia() == null || clase.getDia().isEmpty()) return false;

        // Validar horas
        if (clase.getHoraInicio() == null || clase.getHoraFin() == null) return false;
        if (!clase.getHoraInicio().matches("\\d{2}:\\d{2}")) return false;
        if (!clase.getHoraFin().matches("\\d{2}:\\d{2}")) return false;

        return true;
    }

    /**
     * Normaliza el formato del grupo
     */
    private static String normalizarGrupo(String grupo) {
        if (grupo == null) return "";

        // Remover espacios: "DSM 204" → "DSM-204"
        grupo = grupo.replaceAll("\\s+", "-");

        // Asegurar un solo guion: "DSM--204" → "DSM-204"
        grupo = grupo.replaceAll("-+", "-");

        // Uppercase
        grupo = grupo.toUpperCase();

        return grupo;
    }

    /**
     * Normaliza el nombre del día
     */
    private static String normalizarDia(String dia) {
        if (dia == null) return "";

        dia = dia.trim();

        // Capitalizar primera letra
        if (dia.length() > 0) {
            dia = dia.substring(0, 1).toUpperCase() + dia.substring(1).toLowerCase();
        }

        // Normalizar acentos
        switch (dia.toLowerCase()) {
            case "miercoles": return "Miércoles";
            case "sabado": return "Sábado";
            default: return dia;
        }
    }
}