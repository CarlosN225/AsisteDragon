package com.utfv.asistedragon;

import android.app.Activity;
import android.view.View;

/**
 * AnimUtils — Animaciones reutilizables para toda la app.
 *
 * USO:
 *   // Fade in al entrar a una pantalla (en onCreate, después de setContentView):
 *   AnimUtils.fadeIn(this);
 *
 *   // Fade out antes de salir (antes de finish() o startActivity()):
 *   AnimUtils.fadeOut(this, () -> finish());
 *
 *   // Transición entre activities (reemplaza startActivity + animación):
 *   AnimUtils.irA(this, NuevaActivity.class);
 *
 *   // Transición y cerrar la actual:
 *   AnimUtils.irACerrando(this, NuevaActivity.class);
 */
public class AnimUtils {

    // ── Duración estándar ─────────────────────────────────────────────
    private static final int DURACION_ENTRADA = 280;
    private static final int DURACION_SALIDA  = 180;
    private static final int DELAY_ENTRADA    = 60; // ms antes de arrancar el fade

    // ══ FADE IN ═══════════════════════════════════════════════════════
    /**
     * Fade in suave al entrar a la pantalla.
     * Llámalo en onCreate() después de setContentView() e inicializar vistas.
     */
    public static void fadeIn(Activity activity) {
        View root = activity.getWindow().getDecorView().getRootView();
        if (root == null) return;
        root.setAlpha(0f);
        root.postDelayed(() ->
                        root.animate()
                                .alpha(1f)
                                .setDuration(DURACION_ENTRADA)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start()
                , DELAY_ENTRADA);
    }

    // ══ FADE OUT ══════════════════════════════════════════════════════
    /**
     * Fade out suave antes de salir. Ejecuta el callback al terminar.
     * Úsalo cuando necesitas hacer algo después del fade (ej: finish()).
     */
    public static void fadeOut(Activity activity, Runnable onComplete) {
        View root = activity.getWindow().getDecorView().getRootView();
        if (root == null) { if (onComplete != null) onComplete.run(); return; }
        root.animate()
                .alpha(0f)
                .setDuration(DURACION_SALIDA)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(onComplete != null ? onComplete : () -> {})
                .start();
    }

    // ══ NAVEGACIÓN CON FADE ═══════════════════════════════════════════
    /**
     * Va a una Activity con fade usando los archivos anim/fade_in y fade_out.
     * La Activity destino debe llamar fadeIn() en su onCreate().
     */
    public static void irA(Activity desde, Class<?> destino) {
        desde.startActivity(new android.content.Intent(desde, destino));
        desde.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    /**
     * Va a una Activity y cierra la actual, con fade.
     */
    public static void irACerrando(Activity desde, Class<?> destino) {
        desde.startActivity(new android.content.Intent(desde, destino));
        desde.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        desde.finish();
    }

    /**
     * Va a una Activity con Intent personalizado (para pasar extras).
     */
    public static void irAConIntent(Activity desde, android.content.Intent intent) {
        desde.startActivity(intent);
        desde.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    /**
     * Va a una Activity con Intent y cierra la actual.
     */
    public static void irAConIntentCerrando(Activity desde, android.content.Intent intent) {
        desde.startActivity(intent);
        desde.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        desde.finish();
    }

    public static void backConFade(Activity activity) {
        activity.finish();
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}