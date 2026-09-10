package com.utfv.asistedragon;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * NotificacionesManager
 *
 * Programa alarmas basándose en el horario guardado en SharedPreferences.
 *
 * Notificaciones por día:
 *   1. Inicio de jornada  → 15 min antes de la primera clase
 *   2. Inicio de clase    → 10 min antes de cada clase
 *   3. Fin de clase       → exactamente a la hora de fin (clases intermedias)
 *   4. Fin de jornada     → exactamente a la hora de fin de la última clase
 *
 * IMPORTANTE — Android 12+ (SDK 31+):
 *   SCHEDULE_EXACT_ALARM requiere aprobación manual del usuario.
 *   Si no está disponible, usa setWindow() como alarma aproximada (±5 min).
 *   Nunca hace crash por SecurityException.
 */
public class NotificacionesManager {

    private static final String TAG     = "NotifManager";
    private static final String PREFS   = "AsisteDragonPrefs";
    private static final String KEY_IDS = "notif_ids_programadas";

    // ══ API PÚBLICA ═══════════════════════════════════════════════════════

    public static void programarTodas(Context context) {
        cancelarTodas(context);

        List<Clase> clases = cargarClases(context);
        if (clases == null || clases.isEmpty()) {
            Log.i(TAG, "Sin clases — no se programan notificaciones");
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        boolean puedeExacta = puedeUsarAlarmasExactas(am);
        Log.i(TAG, "Alarmas exactas disponibles: " + puedeExacta);

        List<Integer> idsUsados = new ArrayList<>();
        String[] DIAS_SEMANA = {
                "Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"
        };

        for (String dia : DIAS_SEMANA) {
            List<Clase> clasesDia = new ArrayList<>();
            for (Clase c : clases) {
                if (c.getDia() != null && c.getDia().equalsIgnoreCase(dia))
                    clasesDia.add(c);
            }
            if (clasesDia.isEmpty()) continue;

            Collections.sort(clasesDia, (a, b) -> {
                String ha = a.getHoraInicio() != null ? a.getHoraInicio() : "";
                String hb = b.getHoraInicio() != null ? b.getHoraInicio() : "";
                return ha.compareTo(hb);
            });

            int diaSemana = diaACalendar(dia);
            if (diaSemana < 0) continue;

            Clase primera = clasesDia.get(0);
            Clase ultima  = clasesDia.get(clasesDia.size() - 1);

            // 1. Inicio de jornada (15 min antes de la primera clase)
            int idInicioDia = generarId(dia, "inicio_dia", 0);
            long tsInicioDia = calcularTimestamp(diaSemana, primera.getHoraInicio(), -15);
            if (tsInicioDia > 0) {
                programarAlarma(am, context, puedeExacta, idInicioDia, tsInicioDia,
                        crearIntent(context, "inicio_dia",
                                primera.getMateria(), primera.getGrupo(),
                                primera.getHoraInicio(), primera.getHoraFin(),
                                primera.getSalon(), idInicioDia));
                idsUsados.add(idInicioDia);
            }

            // 2. Inicio y fin de cada clase
            for (int i = 0; i < clasesDia.size(); i++) {
                Clase clase = clasesDia.get(i);

                // 2a. Aviso 10 min antes
                int idInicioClase = generarId(dia, "inicio_clase", i);
                long tsInicioClase = calcularTimestamp(diaSemana, clase.getHoraInicio(), -10);
                if (tsInicioClase > 0) {
                    programarAlarma(am, context, puedeExacta, idInicioClase, tsInicioClase,
                            crearIntent(context, "inicio_clase",
                                    clase.getMateria(), clase.getGrupo(),
                                    clase.getHoraInicio(), clase.getHoraFin(),
                                    clase.getSalon(), idInicioClase));
                    idsUsados.add(idInicioClase);
                }

                // 2b. Fin de clase (solo clases intermedias, no la última)
                if (i < clasesDia.size() - 1) {
                    int idFinClase = generarId(dia, "fin_clase", i);
                    long tsFinClase = calcularTimestamp(diaSemana, clase.getHoraFin(), 0);
                    if (tsFinClase > 0) {
                        programarAlarma(am, context, puedeExacta, idFinClase, tsFinClase,
                                crearIntent(context, "fin_clase",
                                        clase.getMateria(), clase.getGrupo(),
                                        clase.getHoraInicio(), clase.getHoraFin(),
                                        clase.getSalon(), idFinClase));
                        idsUsados.add(idFinClase);
                    }
                }
            }

            // 3. Fin de jornada (última clase)
            int idFinDia = generarId(dia, "fin_dia", 0);
            long tsFinDia = calcularTimestamp(diaSemana, ultima.getHoraFin(), 0);
            if (tsFinDia > 0) {
                programarAlarma(am, context, puedeExacta, idFinDia, tsFinDia,
                        crearIntent(context, "fin_dia",
                                ultima.getMateria(), ultima.getGrupo(),
                                ultima.getHoraInicio(), ultima.getHoraFin(),
                                ultima.getSalon(), idFinDia));
                idsUsados.add(idFinDia);
            }
        }

        guardarIds(context, idsUsados);
        Log.i(TAG, "Total alarmas programadas: " + idsUsados.size()
                + (puedeExacta ? " (exactas)" : " (aproximadas)"));
    }

    public static void cancelarTodas(Context context) {
        List<Integer> ids = cargarIds(context);
        if (ids.isEmpty()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        for (int id : ids) {
            try {
                Intent intent = new Intent(context, ClaseNotificationReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(context, id, intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) { am.cancel(pi); pi.cancel(); }
            } catch (Exception e) {
                Log.w(TAG, "Error cancelando alarma " + id + ": " + e.getMessage());
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_IDS).apply();
        Log.i(TAG, "Canceladas " + ids.size() + " alarmas");
    }

    // ══ HELPERS PRIVADOS ══════════════════════════════════════════════════

    /**
     * Verifica si la app puede usar alarmas exactas.
     * En Android 12+ requiere permiso aprobado manualmente por el usuario.
     * En versiones anteriores siempre es true.
     */
    private static boolean puedeUsarAlarmasExactas(AlarmManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return am.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * Programa la alarma usando el método más apropiado disponible.
     * - Si tiene permiso exacto: setExactAndAllowWhileIdle (preciso, despierta en doze)
     * - Si no tiene permiso exacto: setWindow con ventana de 5 min (no crashea)
     */
    private static void programarAlarma(AlarmManager am, Context context,
                                        boolean exacta, int id,
                                        long triggerMs, Intent intent) {
        try {
            PendingIntent pi = PendingIntent.getBroadcast(context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (exacta && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else if (exacta) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                // Fallback: alarma con ventana de 5 minutos — no requiere permiso especial
                am.setWindow(AlarmManager.RTC_WAKEUP,
                        triggerMs,
                        5 * 60 * 1000L, // ventana de 5 min
                        pi);
            }
        } catch (SecurityException e) {
            // Captura por si el permiso fue revocado entre la verificación y el uso
            Log.w(TAG, "SecurityException al programar alarma " + id
                    + " — intentando con setWindow: " + e.getMessage());
            try {
                PendingIntent pi = PendingIntent.getBroadcast(context, id, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, 5 * 60 * 1000L, pi);
            } catch (Exception e2) {
                Log.e(TAG, "No se pudo programar alarma " + id + ": " + e2.getMessage());
            }
        }
    }

    private static long calcularTimestamp(int diaSemana, String hora, int offsetMinutos) {
        if (hora == null || !hora.contains(":")) return -1;
        try {
            String[] partes = hora.split(":");
            int h = Integer.parseInt(partes[0]);
            int m = Integer.parseInt(partes[1]);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_WEEK, diaSemana);
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, m + offsetMinutos);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if (cal.getTimeInMillis() <= System.currentTimeMillis())
                cal.add(Calendar.DAY_OF_YEAR, 7);

            return cal.getTimeInMillis();
        } catch (Exception e) {
            Log.e(TAG, "Error calculando timestamp: " + e.getMessage());
            return -1;
        }
    }

    private static int diaACalendar(String dia) {
        if (dia == null) return -1;
        switch (dia.toLowerCase()
                .replace("é","e").replace("á","a")
                .replace("ó","o").replace("ú","u").replace("í","i")) {
            case "lunes":     return Calendar.MONDAY;
            case "martes":    return Calendar.TUESDAY;
            case "miercoles": return Calendar.WEDNESDAY;
            case "jueves":    return Calendar.THURSDAY;
            case "viernes":   return Calendar.FRIDAY;
            case "sabado":    return Calendar.SATURDAY;
            case "domingo":   return Calendar.SUNDAY;
            default: return -1;
        }
    }

    private static int generarId(String dia, String tipo, int idx) {
        return Math.abs((dia + tipo + idx).hashCode()) % 100000 + idx;
    }

    private static Intent crearIntent(Context context, String tipo,
                                      String materia, String grupo,
                                      String horaIni, String horaFin,
                                      String salon, int notifId) {
        Intent intent = new Intent(context, ClaseNotificationReceiver.class);
        intent.putExtra(ClaseNotificationReceiver.EXTRA_TIPO,        tipo);
        intent.putExtra(ClaseNotificationReceiver.EXTRA_MATERIA,     materia);
        intent.putExtra(ClaseNotificationReceiver.EXTRA_GRUPO,       grupo);
        intent.putExtra(ClaseNotificationReceiver.EXTRA_HORA_INICIO, horaIni);
        intent.putExtra(ClaseNotificationReceiver.EXTRA_HORA_FIN,    horaFin);
        intent.putExtra(ClaseNotificationReceiver.EXTRA_SALON,       salon);
        intent.putExtra(ClaseNotificationReceiver.EXTRA_NOTIF_ID,    notifId);
        return intent;
    }

    // ══ PERSISTENCIA DE IDs ═══════════════════════════════════════════════

    private static void guardarIds(Context context, List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_IDS, sb.toString()).apply();
    }

    private static List<Integer> cargarIds(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_IDS, "");
        List<Integer> ids = new ArrayList<>();
        if (raw.isEmpty()) return ids;
        for (String s : raw.split(",")) {
            try { ids.add(Integer.parseInt(s.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    private static List<Clase> cargarClases(Context context) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("horario_clases_completas", null);
        if (json == null) return null;
        try {
            Type type = new TypeToken<List<Clase>>(){}.getType();
            return new Gson().fromJson(json, type);
        } catch (Exception e) {
            Log.e(TAG, "Error cargando clases: " + e.getMessage());
            return null;
        }
    }
}