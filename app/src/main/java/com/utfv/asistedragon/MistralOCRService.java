package com.utfv.asistedragon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
public class MistralOCRService {

    private static final String TAG = "MistralOCR";
    private static final String API_KEY = BuildConfig.MISTRAL_API_KEY;
    //private static final String API_KEY = "";
    private static final String VISION_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final String VISION_MODEL = "pixtral-12b-2409";
    private static final int MAX_REINTENTOS = 4;
    private static final long[] DELAYS_REINTENTO = {0L, 8000L, 20000L, 40000L};

    private final OkHttpClient client;
    private final Context context;

    public MistralOCRService(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    // ─────────────────────────────────────────────
    // RESULTADO DEL OCR
    // ─────────────────────────────────────────────

    public static class ResultadoOCR {
        public List<Clase> clases;
        public String vigencia = "";   // "1 DE ENERO AL 30 ABRIL 2026"
        public String periodo  = "";   // "2026-1"
        public ResultadoOCR(List<Clase> clases, String vigencia, String periodo) {
            this.clases   = clases;
            this.vigencia = vigencia != null ? vigencia : "";
            this.periodo  = periodo  != null ? periodo  : "";
        }
    }

    // ─────────────────────────────────────────────
    // PUNTO DE ENTRADA
    // ─────────────────────────────────────────────

    public ResultadoOCR procesarHorario(byte[] pdfBytes) throws Exception {
        Log.i(TAG, "Convirtiendo PDF a imagenes...");
        List<String> paginas = convertirPdfAImagenes(pdfBytes);
        Log.i(TAG, "Paginas: " + paginas.size());
        return procesarConVision(paginas);
    }

    // ─────────────────────────────────────────────
    // PDF → IMÁGENES BASE64
    // ─────────────────────────────────────────────

    private List<String> convertirPdfAImagenes(byte[] pdfBytes) throws Exception {
        List<String> imagenes = new ArrayList<>();

        File tempFile = File.createTempFile("horario_utfv", ".pdf", context.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(pdfBytes);
        }

        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = new PdfRenderer(pfd);

        for (int i = 0; i < renderer.getPageCount(); i++) {
            PdfRenderer.Page page = renderer.openPage(i);
            int w = (int)(page.getWidth() * 1.5f);
            int h = (int)(page.getHeight() * 1.5f);

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            page.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos);
            imagenes.add(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP));
            bitmap.recycle();

            Log.d(TAG, "📷 Página " + (i + 1) + " → " + baos.size() / 1024 + " KB");
        }

        renderer.close();
        pfd.close();
        tempFile.delete();
        return imagenes;
    }

    // ─────────────────────────────────────────────
    // LLAMADA A MISTRAL CON BACKOFF
    // ─────────────────────────────────────────────

    private ResultadoOCR procesarConVision(List<String> paginas) throws Exception {
        Exception ultimoError = null;

        for (int intento = 1; intento <= MAX_REINTENTOS; intento++) {
            try {
                if (intento > 1) {
                    long delay = DELAYS_REINTENTO[Math.min(intento - 1, DELAYS_REINTENTO.length - 1)];
                    Log.i(TAG, "Esperando " + delay / 1000 + "s...");
                    Thread.sleep(delay);
                }

                Log.i(TAG, "Intento " + intento + "/" + MAX_REINTENTOS);
                String respuesta = llamarVisionAPI(paginas);

                // Extraer vigencia antes del JSON
                String vigencia = extraerVigencia(respuesta);
                String periodo  = inferirPeriodo(vigencia);

                List<Clase> clases = parsearClases(extraerJSON(respuesta));

                if (!clases.isEmpty()) {
                    Log.i(TAG, clases.size() + " clases extraidas | vigencia: " + vigencia);
                    return new ResultadoOCR(clases, vigencia, periodo);
                }

                ultimoError = new Exception("Respuesta vacía");

            } catch (Exception e) {
                ultimoError = e;
                Log.e(TAG, "Intento " + intento + ": " + e.getMessage());
                String msg = e.getMessage();
                if (msg != null && !msg.contains("429") && !msg.contains("503") && !msg.contains("timed out")) {
                    break;
                }
            }
        }

        throw new Exception("Fallo tras " + MAX_REINTENTOS + " intentos: " +
                (ultimoError != null ? ultimoError.getMessage() : "desconocido"));
    }

    // ── Extrae vigencia del texto OCR ─────────────────────────────────────

    private String extraerVigencia(String raw) {
        if (raw == null) return "";
        int idx = raw.indexOf("###VIGENCIA###");
        if (idx < 0) return "";
        String after = raw.substring(idx + "###VIGENCIA###".length()).trim();
        // Tomar solo la primera línea
        int nl = after.indexOf("\n");
        String linea = (nl > 0 ? after.substring(0, nl) : after).trim();
        if (linea.equalsIgnoreCase("SIN VIGENCIA") || linea.isEmpty()) return "";
        return linea;
    }

    // ── Infiere el periodo cuatrimestral de la vigencia ───────────────────
    // Ej: "1 DE ENERO AL 30 ABRIL 2026" → "2026-1"
    //     "1 DE MAYO AL 31 AGOSTO 2026" → "2026-2"
    //     "1 DE SEPT AL 31 DIC 2026"    → "2026-3"

    private String inferirPeriodo(String vigencia) {
        if (vigencia == null || vigencia.isEmpty()) return "";
        String v = vigencia.toUpperCase();
        // Extraer año con regex simple
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})").matcher(v);
        String anio = m.find() ? m.group(1) : "";
        if (anio.isEmpty()) return "";
        if (v.contains("ENERO") || v.contains("FEBRERO") || v.contains("MARZO") || v.contains("ABRIL")) {
            return anio + "-1";
        } else if (v.contains("MAYO") || v.contains("JUNIO") || v.contains("JULIO") || v.contains("AGOSTO")) {
            return anio + "-2";
        } else if (v.contains("SEPTIEMBRE") || v.contains("OCTUBRE") || v.contains("NOVIEMBRE") || v.contains("DICIEMBRE")
                || v.contains("SEPT") || v.contains("OCT") || v.contains("NOV") || v.contains("DIC")) {
            return anio + "-3";
        }
        return anio + "-1";
    }

    // ─────────────────────────────────────────────
    // API CALL
    // ─────────────────────────────────────────────

    private String llamarVisionAPI(List<String> paginas) throws Exception {
        JSONArray content = new JSONArray();

        for (String base64 : paginas) {
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:image/jpeg;base64," + base64);
            JSONObject img = new JSONObject();
            img.put("type", "image_url");
            img.put("image_url", imageUrl);
            content.put(img);
        }

        JSONObject texto = new JSONObject();
        texto.put("type", "text");
        texto.put("text", buildPrompt());
        content.put(texto);

        JSONObject mensaje = new JSONObject();
        mensaje.put("role", "user");
        mensaje.put("content", content);

        JSONArray messages = new JSONArray();
        messages.put(mensaje);

        JSONObject body = new JSONObject();
        body.put("model", VISION_MODEL);
        body.put("max_tokens", 4096);
        body.put("temperature", 0.1);
        body.put("messages", messages);

        Request request = new Request.Builder()
                .url(VISION_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code() + ": " + responseBody);
            }
            return new JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        }
    }

    // ─────────────────────────────────────────────
    // PROMPT DEFINITIVO
    // ─────────────────────────────────────────────

    private String buildPrompt() {
        return "Eres un extractor preciso de horarios académicos universitarios.\n"
                + "Analiza ÚNICAMENTE la tabla visual de \"HORARIOS SEMANAL / DISTRIBUCIÓN DE HORA/SEMANA/MES\""
                + " del documento PDF. IGNORA completamente la tabla de resumen de asignaturas de la parte superior.\n\n"
                + "PASO 0 — LEE LA TABLA DE ASIGNATURAS SUPERIOR SOLO PARA SABER CUÁNTAS MATERIAS HAY:\n"
                + "Cuenta cuántas filas distintas de asignatura+grupo hay en la tabla superior.\n"
                + "IMPORTANTE: la misma asignatura puede tener MÚLTIPLES grupos (DGS 801, DGS 802, DGS 901).\n"
                + "Cada combinación ASIGNATURA+GRUPO es una materia DIFERENTE. Debes encontrar TODAS en la tabla.\n\n"
                + "PASO 1 — IDENTIFICA ENCABEZADOS DE COLUMNAS:\n"
                + "Localiza la fila: DISTRIBUCIÓN DE HORA/SEMANA/MES | LUNES | MARTES | MIÉRCOLES | JUEVES | VIERNES | SÁBADO\n"
                + "NO asumas el día — léelo del encabezado real.\n\n"
                + "REGLAS ESTRICTAS:\n"
                + "1. Lee columna por columna, de arriba hacia abajo.\n"
                + "2. Una materia SOLO está en un día si su texto aparece VISUALMENTE en esa columna.\n"
                + "3. La misma materia en dos columnas = dos registros independientes (días distintos).\n"
                + "4. Celda vacía = sin clase. NO asumas continuidad.\n"
                + "5. Consolida filas CONSECUTIVAS de la misma materia+grupo en el MISMO día en un solo bloque.\n"
                + "6. Si la misma materia aparece con DIFERENTE GRUPO en la misma celda, son entradas SEPARADAS.\n"
                + "7. NUNCA fusiones grupos distintos de la misma asignatura.\n"
                + "8. Prioriza la posición horizontal para determinar a qué día pertenece cada bloque.\n\n"
                + "GRUPOS: siempre con espacio: DSM 204, DGS 801, DGS 802, DGS 901 (nunca guión).\n"
                + "Si el grupo tiene número seguido sin espacio (DGS901), agrégalo como DGS 901.\n\n"
                + "FORMATO DE SALIDA:\n"
                + "Día [Nombre del día]\n"
                + "HH:00 – HH:00 → [Materia completa] ([Grupo] [Aula])\n"
                + "Si un día no tiene clases: Sin clases registradas.\n\n"
                + "Antes del JSON escribe exactamente: ###VIGENCIA###\n"
                + "seguido de la vigencia encontrada en el documento, ej: 1 DE ENERO AL 30 ABRIL 2026\n"
                + "Si no encuentras vigencia escribe: ###VIGENCIA###\nSIN VIGENCIA\n\n"
                + "Luego escribe exactamente: ###JSON###\n"
                + "Y el array JSON:\n"
                + "[{\"materia\":\"...\",\"grupo\":\"...\",\"dia\":\"...\",\"horaInicio\":\"HH:00\",\"horaFin\":\"HH:00\",\"salon\":\"...\"}]\n"
                + "Si no hay aula usa salon:\"\".";
    }

    // ─────────────────────────────────────────────
    // EXTRAER JSON DE LA RESPUESTA
    // ─────────────────────────────────────────────

    private String extraerJSON(String raw) {
        if (raw == null) return "[]";
        String s = raw.trim();

        // Extraer lo que viene después de ###JSON###
        int marcador = s.indexOf("###JSON###");
        if (marcador >= 0) s = s.substring(marcador + "###JSON###".length()).trim();

        // Limpiar markdown
        s = s.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        // Extraer array [ ... ]
        int ini = s.indexOf('[');
        int fin = s.lastIndexOf(']');
        if (ini >= 0 && fin > ini) s = s.substring(ini, fin + 1);

        // Normalizar guión → espacio en grupos
        s = s.replaceAll("\"(DSM|DGS|DGSE)-(\\d+)\"", "\"$1 $2\"");

        Log.d(TAG, "🧹 JSON: " + s.substring(0, Math.min(200, s.length())));
        return s;
    }

    // ─────────────────────────────────────────────
    // PARSEO
    // ─────────────────────────────────────────────

    private List<Clase> parsearClases(String json) throws Exception {
        List<Clase> clases = new ArrayList<>();
        JSONArray arr;
        try {
            arr = new JSONArray(json);
        } catch (Exception e) {
            throw new Exception("JSON malformado: " + e.getMessage());
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String materia = obj.optString("materia", "").trim();
            String grupo   = obj.optString("grupo",   "").trim();
            if (materia.isEmpty()) continue; // grupo puede ser vacío

            Clase c = new Clase();
            c.setMateria(materia);
            c.setGrupo(grupo);
            c.setDia(normalizarDia(obj.optString("dia", "").trim()));
            c.setHoraInicio(obj.optString("horaInicio", "").trim());
            c.setHoraFin(obj.optString("horaFin", "").trim());
            c.setSalon(obj.optString("salon", "").trim());
            clases.add(c);

            Log.i(TAG, "✅ " + c.getDia() + " " + c.getHoraInicio() + "-" + c.getHoraFin()
                    + " | " + materia + " | " + grupo);
        }

        Log.i(TAG, "🎯 Total: " + clases.size());
        return clases;
    }

    // ─────────────────────────────────────────────
    // NORMALIZACIÓN DE DÍAS
    // ─────────────────────────────────────────────

    private String normalizarDia(String dia) {
        if (dia == null || dia.isEmpty()) return dia;
        switch (dia.toLowerCase().replace("é","e").replace("á","a").replace("ó","o").trim()) {
            case "lunes":                       return "Lunes";
            case "martes":                      return "Martes";
            case "miercoles": case "miércoles": return "Miércoles";
            case "jueves":                      return "Jueves";
            case "viernes":                     return "Viernes";
            case "sabado": case "sábado":       return "Sábado";
            case "domingo":                     return "Domingo";
            default: return dia.substring(0,1).toUpperCase() + dia.substring(1).toLowerCase();
        }
    }
}