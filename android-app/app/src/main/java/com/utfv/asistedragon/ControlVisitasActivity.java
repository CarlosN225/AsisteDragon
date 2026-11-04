package com.utfv.asistedragon;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ControlVisitasActivity extends AppCompatActivity {

    private TextView txtVisitasResultado;
    private Button btnRegistrarVisita, btnConsultarVisitas;
    private OkHttpClient httpClient;

    // 10.0.2.2 apunta al localhost de tu máquina Linux desde el emulador oficial de Android
    private static final String API_URL = "http://10.0.2.2:3000/api/visitas";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_visitas);

        txtVisitasResultado = findViewById(R.id.txt_visitas_resultado);
        btnRegistrarVisita = findViewById(R.id.btn_registrar_visita);
        btnConsultarVisitas = findViewById(R.id.btn_consultar_visitas);

        httpClient = new OkHttpClient();

        // Ambos botones consumen el endpoint
        btnRegistrarVisita.setOnClickListener(v -> realizarPeticionVisitas("Registrando visita..."));
        btnConsultarVisitas.setOnClickListener(v -> realizarPeticionVisitas("Consultando visitas..."));
    }

    private void realizarPeticionVisitas(String mensajeEspera) {
        Toast.makeText(this, mensajeEspera, Toast.LENGTH_SHORT).show();

        Request request = new Request.Builder()
                .url(API_URL)
                .get()
                .build();

        // Solicitud asíncrona fuera del hilo principal
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(ControlVisitasActivity.this,
                            "Error de conexión con el servidor: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    txtVisitasResultado.setText("Error");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String respuestaJson = response.body().string();
                    try {
                        JSONObject json = new JSONObject(respuestaJson);
                        int visitas = json.getInt("visitas");

                        // Actualizar UI en el hilo principal
                        runOnUiThread(() -> txtVisitasResultado.setText(String.valueOf(visitas)));
                    } catch (Exception ex) {
                        runOnUiThread(() ->
                                Toast.makeText(ControlVisitasActivity.this, "Error al procesar JSON", Toast.LENGTH_SHORT).show()
                        );
                    }
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(ControlVisitasActivity.this, "Error del servidor: " + response.code(), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }
}