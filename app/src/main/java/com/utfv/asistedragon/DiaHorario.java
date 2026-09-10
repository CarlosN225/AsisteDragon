package com.utfv.asistedragon;

import java.util.ArrayList;
import java.util.List;

public class DiaHorario {
    private String dia;
    private List<Clase> clases;

    public DiaHorario(String dia) {
        this.dia = dia;
        this.clases = new ArrayList<>();
    }

    public String getDia() {
        return dia;
    }

    public List<Clase> getClases() {
        return clases;
    }

    public void agregarClase(Clase clase) {
        clases.add(clase);
    }

    public boolean tieneClases() {
        return !clases.isEmpty();
    }
}