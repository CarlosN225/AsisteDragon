package com.utfv.asistedragon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HorarioPagerAdapter extends RecyclerView.Adapter<HorarioPagerAdapter.DiaViewHolder> {

    private List<DiaHorario> diasHorario;

    public HorarioPagerAdapter(List<DiaHorario> diasHorario) {
        this.diasHorario = diasHorario;
    }

    @NonNull
    @Override
    public DiaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.page_dia_horario, parent, false);
        return new DiaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DiaViewHolder holder, int position) {
        DiaHorario diaHorario = diasHorario.get(position);
        holder.bind(diaHorario);
    }

    @Override
    public int getItemCount() {
        return diasHorario.size();
    }

    static class DiaViewHolder extends RecyclerView.ViewHolder {

        TextView txtDiaNombre;
        LinearLayout containerClasesDia;
        LinearLayout estadoVacio;

        public DiaViewHolder(@NonNull View itemView) {
            super(itemView);
            txtDiaNombre = itemView.findViewById(R.id.txt_dia_nombre);
            containerClasesDia = itemView.findViewById(R.id.container_clases_dia);
            estadoVacio = itemView.findViewById(R.id.estado_vacio);
        }

        public void bind(DiaHorario diaHorario) {
            txtDiaNombre.setText(diaHorario.getDia().toUpperCase());

            // Limpiar container
            containerClasesDia.removeAllViews();

            if (!diaHorario.tieneClases()) {
                // Mostrar estado vacío
                containerClasesDia.setVisibility(View.GONE);
                estadoVacio.setVisibility(View.VISIBLE);
            } else {
                containerClasesDia.setVisibility(View.VISIBLE);
                estadoVacio.setVisibility(View.GONE);

                // Ordenar clases por hora
                List<Clase> clasesOrdenadas = new ArrayList<>(diaHorario.getClases());
                Collections.sort(clasesOrdenadas, (c1, c2) ->
                        c1.getHoraInicio().compareTo(c2.getHoraInicio()));

                // Agregar cada clase
                for (Clase clase : clasesOrdenadas) {
                    View claseView = crearVistaClase(clase);
                    containerClasesDia.addView(claseView);
                }
            }
        }

        private View crearVistaClase(Clase clase) {
            LinearLayout layout = new LinearLayout(itemView.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setBackground(ContextCompat.getDrawable(itemView.getContext(),
                    R.drawable.item_materia_background));
            layout.setPadding(16, 14, 16, 14);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 12);
            layout.setLayoutParams(params);

            // Hora
            TextView txtHora = new TextView(itemView.getContext());
            txtHora.setText(clase.getHoraInicio() + " - " + clase.getHoraFin());
            txtHora.setTextSize(13);
            txtHora.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_primary));
            txtHora.setTypeface(null, android.graphics.Typeface.BOLD);
            layout.addView(txtHora);

            // Materia
            TextView txtMateria = new TextView(itemView.getContext());
            txtMateria.setText(clase.getMateria());
            txtMateria.setTextSize(16);
            txtMateria.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
            txtMateria.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams materiaParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            materiaParams.setMargins(0, 6, 0, 6);
            txtMateria.setLayoutParams(materiaParams);
            layout.addView(txtMateria);

            // Grupo y Aula
            TextView txtDetalles = new TextView(itemView.getContext());
            String detalles = "";
            if (clase.getGrupo() != null) {
                detalles += clase.getGrupo();
            }
            if (clase.getSalon() != null) {
                if (!detalles.isEmpty()) detalles += " • ";
                detalles += clase.getSalon();
            }
            txtDetalles.setText(detalles);
            txtDetalles.setTextSize(13);
            txtDetalles.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.gray_medium));
            layout.addView(txtDetalles);

            return layout;
        }
    }
}