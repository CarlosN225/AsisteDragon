package com.utfv.asistedragon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver
 *
 * Las alarmas de AlarmManager se pierden cuando el dispositivo se reinicia.
 * Este receiver escucha BOOT_COMPLETED y reprograma todas las notificaciones
 * automáticamente, siempre que el usuario tenga un horario confirmado.
 *
 * Requiere en AndroidManifest.xml:
 *   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 *
 *   <receiver android:name=".BootReceiver"
 *             android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.intent.action.BOOT_COMPLETED"/>
 *           <action android:name="android.intent.action.QUICKBOOT_POWERON"/>
 *       </intent-filter>
 *   </receiver>
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            boolean materiasConfirmadas = context
                    .getSharedPreferences("AsisteDragonPrefs", Context.MODE_PRIVATE)
                    .getBoolean("materias_confirmadas", false);

            if (materiasConfirmadas) {
                Log.i(TAG, "Boot detectado — reprogramando notificaciones");
                NotificacionesManager.programarTodas(context);
            } else {
                Log.i(TAG, "Boot detectado — sin horario confirmado, sin notificaciones");
            }
        }
    }
}