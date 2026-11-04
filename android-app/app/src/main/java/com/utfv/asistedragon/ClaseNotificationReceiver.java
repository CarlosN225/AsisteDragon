package com.utfv.asistedragon;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

/**
 * BroadcastReceiver que recibe las alarmas programadas por NotificacionesManager
 * y dispara la notificación correspondiente al usuario.
 *
 * Tipos de notificación manejados:
 *   - "inicio_clase"   → Aviso 10 min antes de que empiece una clase
 *   - "fin_clase"      → Aviso cuando termina una clase
 *   - "inicio_dia"     → Aviso 15 min antes de la primera clase del día
 *   - "fin_dia"        → Aviso cuando termina la última clase del día
 */
public class ClaseNotificationReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID        = "asistedragon_clases";
    public static final String CHANNEL_ID_DIA    = "asistedragon_dia";

    public static final String EXTRA_TIPO        = "tipo";
    public static final String EXTRA_MATERIA     = "materia";
    public static final String EXTRA_GRUPO       = "grupo";
    public static final String EXTRA_HORA_INICIO = "hora_inicio";
    public static final String EXTRA_HORA_FIN    = "hora_fin";
    public static final String EXTRA_SALON       = "salon";
    public static final String EXTRA_NOTIF_ID    = "notif_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String tipo      = intent.getStringExtra(EXTRA_TIPO);
        String materia   = intent.getStringExtra(EXTRA_MATERIA);
        String grupo     = intent.getStringExtra(EXTRA_GRUPO);
        String horaIni   = intent.getStringExtra(EXTRA_HORA_INICIO);
        String horaFin   = intent.getStringExtra(EXTRA_HORA_FIN);
        String salon     = intent.getStringExtra(EXTRA_SALON);
        int    notifId   = intent.getIntExtra(EXTRA_NOTIF_ID, (int)(System.currentTimeMillis() % 10000));

        if (tipo == null) return;

        crearCanales(context);

        switch (tipo) {
            case "inicio_clase":
                mostrarNotificacion(context, notifId, CHANNEL_ID,
                        "Clase en 10 minutos ⏰",
                        materia + (grupo != null && !grupo.isEmpty() ? "  •  " + grupo : "")
                                + "\n" + horaIni + " – " + horaFin
                                + (salon != null && !salon.isEmpty() ? "  •  " + salon : ""),
                        android.R.drawable.ic_menu_today);
                break;

            case "fin_clase":
                mostrarNotificacion(context, notifId, CHANNEL_ID,
                        "Clase terminada ✓",
                        materia + " ha concluido."
                                + (grupo != null && !grupo.isEmpty() ? " (" + grupo + ")" : ""),
                        android.R.drawable.ic_menu_today);
                break;

            case "inicio_dia":
                mostrarNotificacion(context, notifId, CHANNEL_ID_DIA,
                        "¡Buen día! Tu jornada comienza pronto 🌅",
                        "Primera clase a las " + horaIni
                                + (salon != null && !salon.isEmpty() ? "  •  " + salon : "")
                                + "\nNo olvides registrar tu asistencia.",
                        android.R.drawable.ic_menu_today);
                break;

            case "fin_dia":
                mostrarNotificacion(context, notifId, CHANNEL_ID_DIA,
                        "Jornada completada 🎉",
                        "Tu última clase (" + materia + ") ha terminado."
                                + "\n¡Buen trabajo hoy!",
                        android.R.drawable.ic_menu_today);
                break;
        }
    }

    // ══ Helpers ══════════════════════════════════════════════════════════

    private void mostrarNotificacion(Context context, int id, String channelId,
                                     String titulo, String contenido, int icono) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // ── Abrir SplashActivity en lugar de PanelPrincipalActivity ──────
        // SplashActivity verifica sesión, PIN y redirige correctamente
        Intent abrirApp = new Intent(context, SplashActivity.class);
        abrirApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, id, abrirApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icono)
                .setContentTitle(titulo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contenido))
                .setContentText(contenido)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        nm.notify(id, builder.build());
    }

    private void crearCanales(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Canal para clases individuales
        NotificationChannel chClases = new NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de clase",
                NotificationManager.IMPORTANCE_HIGH);
        chClases.setDescription("Avisos de inicio y fin de cada clase");
        nm.createNotificationChannel(chClases);

        // Canal para inicio/fin de jornada
        NotificationChannel chDia = new NotificationChannel(
                CHANNEL_ID_DIA,
                "Jornada diaria",
                NotificationManager.IMPORTANCE_HIGH);
        chDia.setDescription("Aviso de inicio y fin de tu jornada");
        nm.createNotificationChannel(chDia);
    }
}