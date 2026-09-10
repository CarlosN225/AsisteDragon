package com.utfv.asistedragon;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR especializado para horarios UTFV — formato tabla de carga horaria.
 *
 * Estrategia de parser:
 *   1. Detectar cabeceras de día (Lunes…Sábado) y calcular el RANGO X de cada columna.
 *   2. Detectar filas de hora ("07:00 08:00", etc.) y calcular el RANGO Y de cada fila.
 *   3. Para cada bloque de texto que parezca una celda de clase, intersectar su bounding box
 *      con los rangos de columna/fila para asignar día y hora de forma precisa.
 *   4. Parsear dentro de la celda: clave asignatura, nombre materia, grupo (DSM/DGS-NNN),
 *      aula (cualquier alfanumérico al final).
 *   5. Agrupar horas contiguas de la misma materia+grupo+día y eliminar duplicados.
 */
public class OCRUtils {

    private static final String TAG = "OCRUtils";

    // ─────────────────────────────────────────────────────────────────────────
    // Nombres canónicos de los días reconocidos (incluyendo variantes sin acento)
    // ─────────────────────────────────────────────────────────────────────────
    private static final String[] DIAS_CANONICOS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };
    private static final String[] DIAS_VARIANTES = {
            "lunes", "martes", "miércoles", "miercoles", "jueves", "viernes",
            "sábado", "sabado", "domingo"
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Interfaces públicas
    // ─────────────────────────────────────────────────────────────────────────
    public interface OCRCallback {
        void onSuccess(List<Clase> clases, String textoCompleto);
        void onError(String error);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Puntos de entrada públicos
    // ─────────────────────────────────────────────────────────────────────────
    public static void procesarImagen(android.content.Context context, Uri imageUri,
                                      OCRCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            realizarOCR(image, callback);
        } catch (IOException e) {
            Log.e(TAG, "Error al cargar imagen", e);
            callback.onError("Error al cargar la imagen: " + e.getMessage());
        }
    }

    public static void procesarPDF(android.content.Context context, Uri pdfUri,
                                   OCRCallback callback) {
        try {
            ParcelFileDescriptor fd = context.getContentResolver()
                    .openFileDescriptor(pdfUri, "r");
            if (fd == null) { callback.onError("No se pudo abrir el PDF"); return; }

            PdfRenderer renderer = new PdfRenderer(fd);
            if (renderer.getPageCount() == 0) {
                callback.onError("El PDF está vacío");
                renderer.close(); fd.close(); return;
            }

            PdfRenderer.Page page = renderer.openPage(0);

            // ── ESTRATEGIA DE RENDERIZADO EN DOS PASADAS ──────────────────────
            // El PDF de UTFV tiene dos secciones: tabla de resumen (arriba) y
            // tabla de horario semanal (abajo). Renderizar a 4× escala con un
            // bitmap de página completa produce imágenes de ~2448×3168 px que
            // el OCR procesa bien, PERO PdfRenderer solo renderiza hasta el
            // límite del bitmap destino — si la escala es muy grande y el
            // contenido es largo, la mitad inferior se recorta.
            //
            // Solución: renderizar la página completa a 2× (suficiente para OCR
            // de texto de tabla) y además renderizar solo la mitad inferior a
            // 3× para asegurar que las celdas del horario se capturen con buena
            // resolución. Los resultados de ambas pasadas se combinan.

            int pdfW = page.getWidth();
            int pdfH = page.getHeight();

            // Pasada 1 — página completa a 2× (captura estructura general)
            int w1 = pdfW * 2;
            int h1 = pdfH * 2;
            Bitmap bmpCompleta = Bitmap.createBitmap(w1, h1, Bitmap.Config.ARGB_8888);
            // Fondo blanco para evitar transparencias que confunden el OCR
            bmpCompleta.eraseColor(android.graphics.Color.WHITE);
            page.render(bmpCompleta, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Log.d(TAG, "Pasada 1 completa: " + w1 + "x" + h1);

            // Pasada 2 — mitad inferior a 3× (donde está la tabla del horario)
            // PdfRenderer.Page.render() acepta Matrix (no Rect) como transformación.
            // Para "ver" solo la mitad inferior del PDF ampliada a 3×:
            //   - scale(3, 3)           → ampliar el PDF 3×
            //   - translate(0, -pdfH/2) → desplazar hacia arriba para que la mitad
            //                             inferior quede centrada en el bitmap
            // El bitmap de destino tiene el tamaño de esa mitad a 3×.
            int w2 = pdfW * 3;
            int h2 = (pdfH / 2) * 3;
            Bitmap bmpMitadInferior = Bitmap.createBitmap(w2, h2, Bitmap.Config.ARGB_8888);
            bmpMitadInferior.eraseColor(android.graphics.Color.WHITE);
            // destRect cubre todo el bitmap de salida
            android.graphics.Rect destRect = new android.graphics.Rect(0, 0, w2, h2);
            // Matrix: escalar 3× y trasladar Y para mostrar la mitad inferior
            android.graphics.Matrix matrix2 = new android.graphics.Matrix();
            matrix2.setScale(3f, 3f);
            matrix2.postTranslate(0f, -(pdfH / 2f) * 3f);
            page.render(bmpMitadInferior, destRect, matrix2,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Log.d(TAG, "Pasada 2 mitad inferior: " + w2 + "x" + h2);

            page.close();
            renderer.close();
            fd.close();

            // Procesar ambas imágenes y combinar los resultados
            realizarOCRDosPasadas(bmpCompleta, bmpMitadInferior, callback);

        } catch (IOException e) {
            Log.e(TAG, "Error al procesar PDF", e);
            callback.onError("Error al procesar el PDF: " + e.getMessage());
        }
    }

    /**
     * Procesa dos bitmaps del mismo PDF y combina sus resultados.
     */
    private static void realizarOCRDosPasadas(Bitmap bmpCompleta, Bitmap bmpMitadInferior,
                                              OCRCallback callback) {
        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Pasada 1
        recognizer.process(InputImage.fromBitmap(bmpCompleta, 0))
                .addOnSuccessListener(texto1 -> {
                    String textoCompleto = texto1.getText();
                    Log.d(TAG, "=== TEXTO RAW (pasada completa) ===\n" + textoCompleto);

                    // ✅ USAR GEMINI EN LUGAR DEL PARSER MANUAL
                    GeminiOCRParser.parsearHorario(textoCompleto, new GeminiOCRParser.GeminiCallback() {
                        @Override
                        public void onSuccess(List<Clase> clases) {
                            Log.i(TAG, "🎉 Gemini procesó exitosamente: " + clases.size() + " clases");
                            callback.onSuccess(clases, textoCompleto);
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "❌ Error de Gemini, usando fallback: " + error);
                            // Si Gemini falla, usar el parser antiguo
                            List<Clase> clasesFallback = parsearHorarioUTFV(texto1);
                            callback.onSuccess(clasesFallback, textoCompleto);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, " Error OCR pasada 1", e);
                    callback.onError("Error: " + e.getMessage());
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Núcleo OCR → parser
    // ─────────────────────────────────────────────────────────────────────────
    private static void realizarOCR(InputImage image, OCRCallback callback) {
        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String textoCompleto = visionText.getText();
                    Log.d(TAG, "=== TEXTO RAW ===\n" + textoCompleto);

                    // ✅ USAR GEMINI
                    GeminiOCRParser.parsearHorario(textoCompleto, new GeminiOCRParser.GeminiCallback() {
                        @Override
                        public void onSuccess(List<Clase> clases) {
                            Log.i(TAG, "🎉 Gemini: " + clases.size() + " clases");
                            callback.onSuccess(clases, textoCompleto);
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "❌ Error Gemini, fallback: " + error);
                            List<Clase> clasesFallback = parsearHorarioUTFV(visionText);
                            callback.onSuccess(clasesFallback, textoCompleto);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error OCR", e);
                    callback.onError("Error: " + e.getMessage());
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parser principal
    // ─────────────────────────────────────────────────────────────────────────
    private static List<Clase> parsearHorarioUTFV(Text visionText) {
        List<Text.TextBlock> bloques = visionText.getTextBlocks();

        // Paso 1: detectar estructura de la tabla
        List<ColumnaHorario> columnas = detectarColumnasConRango(bloques);
        List<FilaHorario>    filas    = detectarFilasConRango(bloques);

        Log.d(TAG, "Columnas detectadas: " + columnas.size());
        for (ColumnaHorario c : columnas) {
            Log.d(TAG, "  → " + c.dia + " [x:" + c.xMin + "–" + c.xMax + "]");
        }
        Log.d(TAG, "Filas de hora detectadas: " + filas.size());
        for (FilaHorario f : filas) {
            Log.d(TAG, "  → " + f.horaInicio + "-" + f.horaFin
                    + " [y:" + f.yMin + "–" + f.yMax + "]");
        }

        if (columnas.isEmpty() || filas.isEmpty()) {
            Log.w(TAG, "No se detectaron columnas o filas. Intentando fallback posicional.");
            return parsearFallbackMasCercano(bloques);
        }

        // Paso 2: extraer clases
        List<Clase> clases = new ArrayList<>();

        for (Text.TextBlock bloque : bloques) {
            Rect bounds = bloque.getBoundingBox();
            if (bounds == null) continue;

            String texto = bloque.getText().trim();
            if (texto.isEmpty()) continue;

            // Filtrar bloques que son claramente cabeceras o metadatos
            if (esCabecera(texto) || esMetadato(texto)) continue;

            // Verificar si el bloque contiene una clase válida
            if (!contieneCelda(texto)) continue;

            // Asignar día por rango de columna
            String dia = asignarDiaPorRango(bounds, columnas);
            if (dia == null) {
                Log.d(TAG, "Sin columna para: " + texto.substring(0, Math.min(40, texto.length())));
                continue;
            }

            // Asignar hora por rango de fila
            FilaHorario fila = asignarFilaPorRango(bounds, filas);
            if (fila == null) {
                Log.d(TAG, "Sin fila para: " + texto.substring(0, Math.min(40, texto.length())));
                continue;
            }

            // Extraer datos de la celda
            CeldaInfo info = extraerCelda(texto);

            // GATE DE ADMISIÓN: solo pasan celdas con materia limpia + grupo DSM/DGS válido.
            // Si falta cualquiera de los dos → basura del OCR, se descarta.
            boolean tieneMateria = info != null && info.materia != null && info.materia.length() >= 4;
            boolean tieneGrupo   = info != null && info.grupo   != null && info.grupo.matches("(DSM|DGS|DGSE)-[0-9]{3,4}");
            if (!tieneMateria || !tieneGrupo) {
                Log.d(TAG, "❌ DESCARTADA (sin materia+grupo válido): "
                        + texto.substring(0, Math.min(50, texto.length())));
                continue;
            }

            Clase clase = new Clase(info.materia, dia, fila.horaInicio, fila.horaFin);
            clase.setGrupo(info.grupo);
            clase.setSalon(info.aula != null ? info.aula : "");

            clases.add(clase);
            Log.i(TAG, String.format("✅ %s | %s | %s | %s-%s | aula: %s",
                    info.materia, info.grupo, dia,
                    fila.horaInicio, fila.horaFin, info.aula));
        }

        // Paso 3: agrupar horas contiguas y deduplicar
        List<Clase> agrupadas    = agruparClasesContinuas(clases);
        List<Clase> deduplicadas = eliminarDuplicados(agrupadas);

        Log.i(TAG, "Clases brutas: " + clases.size()
                + " → agrupadas: " + agrupadas.size()
                + " → finales: " + deduplicadas.size());

        return deduplicadas;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASO 1a — Detectar columnas de día con su rango X
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Busca los bloques cuyo texto sea un nombre de día y calcula el rango X de la columna
     * como el espacio entre ese día y el siguiente (o el borde de la imagen).
     */
    private static List<ColumnaHorario> detectarColumnasConRango(List<Text.TextBlock> bloques) {
        List<ColumnaHorario> cols = new ArrayList<>();

        for (Text.TextBlock bloque : bloques) {
            String texto = bloque.getText().trim();
            String diaCanon = normalizarDia(texto);
            if (diaCanon == null) continue;

            Rect b = bloque.getBoundingBox();
            if (b == null) continue;

            ColumnaHorario col = new ColumnaHorario();
            col.dia    = diaCanon;
            col.xCabec = b.centerX();
            // xMin y xMax se calculan después de ordenar
            col.xMin = b.left;
            col.xMax = b.right;
            cols.add(col);
        }

        if (cols.isEmpty()) return cols;

        // Ordenar por posición X
        Collections.sort(cols, (a, c) -> Integer.compare(a.xCabec, c.xCabec));

        // Expandir rangos: cada columna va desde el punto medio con la anterior
        // hasta el punto medio con la siguiente
        for (int i = 0; i < cols.size(); i++) {
            ColumnaHorario actual = cols.get(i);
            int xIzq = (i == 0)
                    ? 0
                    : (cols.get(i-1).xCabec + actual.xCabec) / 2;
            int xDer = (i == cols.size()-1)
                    ? Integer.MAX_VALUE
                    : (actual.xCabec + cols.get(i+1).xCabec) / 2;
            actual.xMin = xIzq;
            actual.xMax = xDer;
        }

        return cols;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASO 1b — Detectar filas de hora con su rango Y
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Busca bloques que contengan un rango de horas tipo "07:00 08:00" o "07:00-08:00"
     * y calcula el rango Y de la fila.
     */
    private static List<FilaHorario> detectarFilasConRango(List<Text.TextBlock> bloques) {
        List<FilaHorario> filas = new ArrayList<>();
        // Patrón flexible: acepta "07:00 08:00", "07:00-08:00", "7:00 8:00", etc.
        Pattern p = Pattern.compile(
                "(\\d{1,2})[:.]?(\\d{2})\\s*[-–]?\\s*(\\d{1,2})[:.]?(\\d{2})");

        for (Text.TextBlock bloque : bloques) {
            // Quitar espacios para que "07 :00" se unifique
            String texto = bloque.getText().replaceAll("\\s+", " ").trim();
            Matcher m = p.matcher(texto);

            // Puede haber varios rangos si el OCR junta dos celdas; queremos solo 1
            List<String[]> candidatos = new ArrayList<>();
            while (m.find()) {
                String hi = pad(m.group(1)) + ":" + m.group(2);
                String hf = pad(m.group(3)) + ":" + m.group(4);
                // Filtrar horas razonables para un horario escolar (6:00–22:00)
                int hIni = Integer.parseInt(m.group(1));
                int hFin = Integer.parseInt(m.group(3));
                if (hIni >= 6 && hFin <= 22 && hFin > hIni) {
                    candidatos.add(new String[]{hi, hf});
                }
            }

            if (candidatos.isEmpty()) continue;

            Rect b = bloque.getBoundingBox();
            if (b == null) continue;

            // Evitar duplicar la misma hora
            String[] best = candidatos.get(0);
            boolean yaExiste = false;
            for (FilaHorario f : filas) {
                if (f.horaInicio.equals(best[0]) && f.horaFin.equals(best[1])) {
                    yaExiste = true;
                    break;
                }
            }
            if (yaExiste) continue;

            FilaHorario fila = new FilaHorario();
            fila.horaInicio = best[0];
            fila.horaFin    = best[1];
            fila.yCabec     = b.centerY();
            fila.yMin       = b.top;
            fila.yMax       = b.bottom;
            filas.add(fila);
        }

        // Ordenar por posición Y (de arriba hacia abajo)
        Collections.sort(filas, (a, c) -> Integer.compare(a.yCabec, c.yCabec));

        // Expandir rangos Y (igual que las columnas X)
        for (int i = 0; i < filas.size(); i++) {
            FilaHorario actual = filas.get(i);
            int yArr = (i == 0)
                    ? 0
                    : (filas.get(i-1).yCabec + actual.yCabec) / 2;
            int yAba = (i == filas.size()-1)
                    ? Integer.MAX_VALUE
                    : (actual.yCabec + filas.get(i+1).yCabec) / 2;
            actual.yMin = yArr;
            actual.yMax = yAba;
        }

        return filas;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASO 2 — Asignación por intersección de rango
    // ─────────────────────────────────────────────────────────────────────────
    private static String asignarDiaPorRango(Rect bounds, List<ColumnaHorario> columnas) {
        int cx = bounds.centerX();
        for (ColumnaHorario c : columnas) {
            if (cx >= c.xMin && cx <= c.xMax) return c.dia;
        }
        // Fallback: columna más cercana si ninguna intersecta exactamente
        ColumnaHorario best = null;
        int minDist = Integer.MAX_VALUE;
        for (ColumnaHorario c : columnas) {
            int dist = Math.abs(cx - c.xCabec);
            if (dist < minDist) { minDist = dist; best = c; }
        }
        return best != null ? best.dia : null;
    }

    private static FilaHorario asignarFilaPorRango(Rect bounds, List<FilaHorario> filas) {
        int cy = bounds.centerY();
        for (FilaHorario f : filas) {
            if (cy >= f.yMin && cy <= f.yMax) return f;
        }
        FilaHorario best = null;
        int minDist = Integer.MAX_VALUE;
        for (FilaHorario f : filas) {
            int dist = Math.abs(cy - f.yCabec);
            if (dist < minDist) { minDist = dist; best = f; }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASO 3 — Parsear contenido de una celda
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Formato esperado de una celda UTFV:
     *   [CLAVE]  ← código como 24DSM034, 20IDGS010 (opcional, se descarta)
     *   [Nombre de la asignatura]
     *   [GRUPO]  ← DSM 501, DGS 802, DSM 2O2 (OCR confunde O/0)
     *   [AULA]   ← D204, IID104, D100, D105… (opcional)
     */
    private static CeldaInfo extraerCelda(String texto) {
        CeldaInfo info = new CeldaInfo();

        // Normalizar: quitar saltos de línea extra
        String t = texto.replaceAll("\\r", "").trim();

        // ── 1. Extraer GRUPO ──────────────────────────────────────────────────
        // Acepta: DSM 501, DGS802, DSM-401, DSM 2O2 (O→0 corregido por OCR)
        // También acepta grupos sin prefijo solo si son NN-NNN (raro, pero seguro)
        Pattern pGrupo = Pattern.compile(
                "\\b(DSM|DGS|DGSE|IDGS|IDSM)\\s*[-]?\\s*([0-9O]{3,4})\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher mGrupo = pGrupo.matcher(t);
        if (mGrupo.find()) {
            String prefijo = mGrupo.group(1).toUpperCase();
            // Normalizar todos los prefijos a DSM o DGS
            if (prefijo.equals("IDGS") || prefijo.equals("DGSE")) prefijo = "DGS";
            if (prefijo.equals("IDSM")) prefijo = "DSM";
            String numero = mGrupo.group(2).replace('O', '0'); // corregir O por 0
            info.grupo = prefijo + "-" + numero;
        } else {
            Log.d(TAG, "Sin grupo en: " + t.substring(0, Math.min(50, t.length())));
            // No descartamos todavía; puede ser que el nombre de la asignatura
            // sea suficiente sin grupo (ej. tutorías).
        }

        // ── 2. Extraer AULA ───────────────────────────────────────────────────
        // Formatos observados en los PDFs: D204, D103, IID104, D100, D210, D105
        // Patrón general: 1-3 letras seguidas de 3-4 dígitos al final del texto
        Pattern pAula = Pattern.compile(
                "\\b([A-Z]{1,3})-?(\\d{3,4})\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher mAula = pAula.matcher(t);
        String ultimaAula = null;
        while (mAula.find()) {
            String letra = mAula.group(1).toUpperCase();
            String num   = mAula.group(2);
            // Descartar coincidencias que son realmente el grupo (DSM/DGS + número)
            if (letra.equals("DSM") || letra.equals("DGS")) continue;
            // Descartar la clave de asignatura (ej. "24DSM028" ya fue removida antes)
            ultimaAula = letra + "-" + num;
        }
        info.aula = ultimaAula;

        // ── 3. Extraer MATERIA ────────────────────────────────────────────────
        String limpia = t
                // Clave asignatura con prefijo numerico: "8 20IDGS009", "24DSM034"
                .replaceAll("\\b\\d{1,2}\\s*\\d{2}(DSM|DGS|IDGS|IDSM|DGSE)\\d{3,5}\\b", " ")
                .replaceAll("\\b\\d{2}(DSM|DGS|IDGS|IDSM|DGSE)\\d{3,5}\\b", " ")
                // Grupo: DSM 501, DGS 802, DGSE 1001, DSM-505 (incluye grupos de 4 digitos)
                .replaceAll("\\b(DSM|DGS|DGSE|IDGS|IDSM)\\s*[-]?\\s*[0-9O]{3,4}\\b", " ")
                // Aula: D104, D105, IID104, D210 etc.
                .replaceAll("\\b[A-Z]{1,3}-?\\d{3,4}\\b", " ")
                // Pipes y basura tipica del OCR
                .replaceAll("[|!,;]", " ")
                // Digito suelto al inicio de linea (el "8" de "8 20IDGS009")
                .replaceAll("(?m)^\\s*\\d\\s+", " ")
                // Numeros sueltos de 3+ digitos que sobran
                .replaceAll("\\b\\d{3,5}\\b", " ")
                // Saltos de linea y espacios multiples
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        // Capitalizar si vino todo en mayusculas
        if (limpia.length() > 2 && limpia.equals(limpia.toUpperCase())) {
            limpia = capitalizarPalabras(limpia);
        }

        limpia = limpia.trim();

        if (limpia.length() >= 3) {
            info.materia = limpia;
        }

        Log.d(TAG, "  -> Materia: '" + info.materia + "' | Grupo: '" + info.grupo + "' | Aula: '" + info.aula + "'");

        return info;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filtros: ¿es cabecera? ¿es metadato? ¿tiene contenido de celda?
    // ─────────────────────────────────────────────────────────────────────────
    private static boolean esCabecera(String texto) {
        String t = texto.trim().toLowerCase();
        // Nombres de días exactos
        for (String v : DIAS_VARIANTES) { if (t.equals(v)) return true; }
        // Textos de cabecera comunes en el PDF
        return t.contains("distribución") || t.contains("hora/semana")
                || t.contains("horario semanal") || t.contains("horas totales")
                || t.equals("sábado") || t.equals("sabado");
    }

    private static boolean esMetadato(String texto) {
        String t = texto.trim().toLowerCase();
        return t.contains("universidad") || t.contains("secretaría")
                || t.contains("docencia") || t.contains("carga horaria")
                || t.contains("periodo") || t.contains("vigencia")
                || t.contains("firma") || t.contains("autoriza")
                || t.contains("director") || t.contains("frente a grupo")
                || t.contains("asignatura") || t.contains("sumatoria")
                || t.contains("actividades académicas")
                || t.contains("nombre de la división")
                || t.startsWith("ing.") || t.startsWith("lic.")
                || t.startsWith("m. en") || t.startsWith("8 ")
                || texto.matches("\\d{2}:\\d{2}\\s+\\d{2}:\\d{2}");
    }

    /**
     * Determina si el bloque de texto podría ser una celda de clase.
     * Condición mínima: contiene un código de grupo (DSM/DGS) o un nombre de asignatura conocido.
     */
    private static boolean contieneCelda(String texto) {
        // Tiene código de grupo
        if (texto.matches("(?i).*\\b(DSM|DGS|DGSE|IDGS|IDSM)\\s*[-]?\\s*[0-9O]{3,4}\\b.*"))
            return true;

        // Tiene clave de asignatura (ej. 24DSM034)
        if (texto.matches("(?i).*\\b\\d{2}(DSM|DGS|IDGS|IDSM)\\d{3,5}\\b.*"))
            return true;

        // Tiene palabras clave de materias conocidas
        String tl = texto.toLowerCase();
        return tl.contains("sistemas operativos")
                || tl.contains("fundamentos de programación")
                || tl.contains("fundamentos de programacion")
                || tl.contains("desarrollo web")
                || tl.contains("proyecto integrador")
                || tl.contains("análisis") || tl.contains("analisis")
                || tl.contains("cálculo") || tl.contains("calculo")
                || tl.contains("ecuaciones diferenciales")
                || tl.contains("estandares") || tl.contains("estándares")
                || tl.contains("conmutacion") || tl.contains("conmutación")
                || tl.contains("matemáticas") || tl.contains("matematicas")
                || tl.contains("redes")
                || tl.contains("tutoría") || tl.contains("tutoria")
                || tl.contains("fortalecimiento académico")
                || tl.contains("base de datos")
                || tl.contains("móviles") || tl.contains("moviles")
                || tl.contains("servicios");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Agrupación de horas contiguas
    // ─────────────────────────────────────────────────────────────────────────
    private static List<Clase> agruparClasesContinuas(List<Clase> clases) {
        if (clases.isEmpty()) return clases;

        Collections.sort(clases, (a, b) -> {
            int d = ordenDia(a.getDia()) - ordenDia(b.getDia());
            if (d != 0) return d;
            int g = a.getGrupo().compareTo(b.getGrupo());
            if (g != 0) return g;
            int m = a.getMateria().compareToIgnoreCase(b.getMateria());
            if (m != 0) return m;
            return a.getHoraInicio().compareTo(b.getHoraInicio());
        });

        List<Clase> agrupadas = new ArrayList<>();
        Clase actual = null;

        for (Clase c : clases) {
            if (actual == null) {
                actual = c; continue;
            }
            boolean mismoDia = actual.getDia().equalsIgnoreCase(c.getDia());
            boolean mismaM   = actual.getMateria().trim().equalsIgnoreCase(c.getMateria().trim());
            boolean mismoG   = actual.getGrupo().equals(c.getGrupo());
            boolean continua = actual.getHoraFin().equals(c.getHoraInicio());

            if (mismoDia && mismaM && mismoG && continua) {
                actual.setHoraFin(c.getHoraFin());
                Log.d(TAG, "Agrupando " + actual.getMateria() + " → "
                        + actual.getHoraInicio() + "-" + actual.getHoraFin());
            } else {
                agrupadas.add(actual);
                actual = c;
            }
        }
        if (actual != null) agrupadas.add(actual);
        return agrupadas;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eliminación de duplicados
    // ─────────────────────────────────────────────────────────────────────────
    private static List<Clase> eliminarDuplicados(List<Clase> clases) {
        List<Clase> unicas = new ArrayList<>();
        for (Clase c : clases) {
            boolean dup = false;
            for (Clase u : unicas) {
                // Duplicado = misma materia (normalizada) + mismo grupo + mismo día + misma hora
                // El grupo DSM/DGS diferente = materia diferente, NO es duplicado
                if (normalizarMateria(c.getMateria()).equalsIgnoreCase(normalizarMateria(u.getMateria()))
                        && c.getGrupo().equalsIgnoreCase(u.getGrupo())
                        && c.getDia().equalsIgnoreCase(u.getDia())
                        && c.getHoraInicio().equals(u.getHoraInicio())
                        && c.getHoraFin().equals(u.getHoraFin())) {
                    dup = true; break;
                }
            }
            if (!dup) unicas.add(c);
        }
        return unicas;
    }

    /**
     * Normaliza nombre de materia para comparación de duplicados.
     * Quita espacios extra y caracteres basura que el OCR puede variar entre pasadas.
     */
    private static String normalizarMateria(String m) {
        if (m == null) return "";
        return m.trim()
                .toLowerCase()
                .replaceAll("[|!,;]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback: parser posicional por "más cercano" (solo si no hay cabeceras)
    // ─────────────────────────────────────────────────────────────────────────
    private static List<Clase> parsearFallbackMasCercano(List<Text.TextBlock> bloques) {
        Log.w(TAG, "Usando fallback posicional");
        List<Clase> clases = new ArrayList<>();

        // Reusar la lógica antigua simplificada
        List<CabeceraDia>  dias  = detectarDiasSimple(bloques);
        List<CabeceraHora> horas = detectarHorasSimple(bloques);

        for (Text.TextBlock bloque : bloques) {
            Rect b = bloque.getBoundingBox();
            if (b == null) continue;
            String texto = bloque.getText().trim();
            if (esCabecera(texto) || esMetadato(texto)) continue;
            if (!contieneCelda(texto)) continue;

            String dia = diaFallback(b, dias);
            String[] rh = horaFallback(b, horas);
            if (dia == null || rh == null) continue;

            CeldaInfo info = extraerCelda(texto);
            // Mismo gate: materia limpia + grupo DSM/DGS válido
            boolean tieneMateria = info != null && info.materia != null && info.materia.length() >= 4;
            boolean tieneGrupo   = info != null && info.grupo   != null && info.grupo.matches("(DSM|DGS|DGSE)-[0-9]{3,4}");
            if (!tieneMateria || !tieneGrupo) continue;

            Clase clase = new Clase(info.materia, dia, rh[0], rh[1]);
            clase.setGrupo(info.grupo);
            clase.setSalon(info.aula != null ? info.aula : "");
            clases.add(clase);
        }

        return eliminarDuplicados(agruparClasesContinuas(clases));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────────────────────────────────

    /** Normaliza un texto a un nombre canónico de día, o null si no es un día. */
    private static String normalizarDia(String texto) {
        // Normalizacion agresiva: quitar acentos agudos Y graves (SABADO con A grave = SABADO)
        // El OCR del PDF de UTFV genera "SABADO" con acento grave (a) en lugar de agudo (a)
        String t = texto.trim().toLowerCase()
                .replace("\u00e1","a").replace("\u00e0","a")  // a aguda y a grave
                .replace("\u00e9","e").replace("\u00e8","e")  // e aguda y e grave
                .replace("\u00ed","i").replace("\u00ec","i")  // i aguda y i grave
                .replace("\u00f3","o").replace("\u00f2","o")  // o aguda y o grave
                .replace("\u00fa","u").replace("\u00f9","u")  // u aguda y u grave
                .replaceAll("[|!]", "").trim();               // basura OCR
        switch (t) {
            case "lunes":       return "Lunes";
            case "martes":      return "Martes";
            case "miercoles":
            case "mi\u00e9rcoles": return "Mi\u00e9rcoles";
            case "jueves":      return "Jueves";
            case "viernes":     return "Viernes";
            case "sabado":
            case "s\u00e1bado":
            case "s\u00e0bado": return "S\u00e1bado";
            case "domingo":     return "Domingo";
            default:            return null;
        }
    }

    private static int ordenDia(String dia) {
        if (dia == null) return 99;
        switch (dia.toLowerCase()) {
            case "lunes":      return 1;
            case "martes":     return 2;
            case "miércoles":
            case "miercoles":  return 3;
            case "jueves":     return 4;
            case "viernes":    return 5;
            case "sábado":
            case "sabado":     return 6;
            case "domingo":    return 7;
            default:           return 99;
        }
    }

    private static String pad(String h) {
        return h.length() == 1 ? "0" + h : h;
    }

    private static String capitalizarPalabras(String texto) {
        StringBuilder sb = new StringBuilder();
        for (String palabra : texto.split("\\s+")) {
            if (palabra.isEmpty()) continue;
            sb.append(Character.toUpperCase(palabra.charAt(0)));
            sb.append(palabra.substring(1).toLowerCase());
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback helpers (posicional simple, heredado)
    // ─────────────────────────────────────────────────────────────────────────
    private static List<CabeceraDia> detectarDiasSimple(List<Text.TextBlock> bloques) {
        List<CabeceraDia> dias = new ArrayList<>();
        for (Text.TextBlock bloque : bloques) {
            String txt = bloque.getText().trim();
            String canon = normalizarDia(txt);
            if (canon == null) continue;
            Rect b = bloque.getBoundingBox();
            if (b == null) continue;
            CabeceraDia d = new CabeceraDia();
            d.nombre    = canon;
            d.posicionX = b.centerX();
            dias.add(d);
        }
        Collections.sort(dias, (a, c) -> Integer.compare(a.posicionX, c.posicionX));
        return dias;
    }

    private static List<CabeceraHora> detectarHorasSimple(List<Text.TextBlock> bloques) {
        List<CabeceraHora> horas = new ArrayList<>();
        Pattern p = Pattern.compile(
                "(\\d{1,2})[:.]?(\\d{2})\\s*[-–]?\\s*(\\d{1,2})[:.]?(\\d{2})");
        for (Text.TextBlock bloque : bloques) {
            String txt = bloque.getText().replaceAll("\\s+", "");
            Matcher m = p.matcher(txt);
            if (!m.find()) continue;
            Rect b = bloque.getBoundingBox();
            if (b == null) continue;
            int hi = Integer.parseInt(m.group(1)), hf = Integer.parseInt(m.group(3));
            if (hi < 6 || hf > 22 || hf <= hi) continue;
            boolean dup = false;
            String ini = pad(m.group(1)) + ":" + m.group(2);
            String fin = pad(m.group(3)) + ":" + m.group(4);
            for (CabeceraHora h : horas) { if (h.inicio.equals(ini)) { dup=true; break; } }
            if (dup) continue;
            CabeceraHora hora = new CabeceraHora();
            hora.inicio    = ini;
            hora.fin       = fin;
            hora.posicionY = b.centerY();
            horas.add(hora);
        }
        Collections.sort(horas, (a, c) -> Integer.compare(a.posicionY, c.posicionY));
        return horas;
    }

    private static String diaFallback(Rect b, List<CabeceraDia> dias) {
        if (dias.isEmpty()) return null;
        int cx = b.centerX(); CabeceraDia best = null; int md = Integer.MAX_VALUE;
        for (CabeceraDia d : dias) { int dist = Math.abs(cx-d.posicionX); if (dist<md){md=dist;best=d;} }
        return best != null ? best.nombre : null;
    }

    private static String[] horaFallback(Rect b, List<CabeceraHora> horas) {
        if (horas.isEmpty()) return null;
        int cy = b.centerY(); CabeceraHora best = null; int md = Integer.MAX_VALUE;
        for (CabeceraHora h : horas) { int dist = Math.abs(cy-h.posicionY); if (dist<md){md=dist;best=h;} }
        return best != null ? new String[]{best.inicio, best.fin} : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clases internas de soporte
    // ─────────────────────────────────────────────────────────────────────────

    /** Columna de día con su rango X calculado. */
    private static class ColumnaHorario {
        String dia;
        int xCabec;   // X central de la cabecera
        int xMin;     // X izquierdo del rango de la columna
        int xMax;     // X derecho del rango de la columna
    }

    /** Fila de hora con su rango Y calculado. */
    private static class FilaHorario {
        String horaInicio;
        String horaFin;
        int yCabec;
        int yMin;
        int yMax;
    }

    /** Datos extraídos de una celda de clase. */
    private static class CeldaInfo {
        String materia;
        String grupo;
        String aula;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clases legacy para el fallback posicional
    // ─────────────────────────────────────────────────────────────────────────
    private static class CabeceraHora {
        String inicio, fin;
        int posicionY;
    }

    private static class CabeceraDia {
        String nombre;
        int posicionX;
    }
}