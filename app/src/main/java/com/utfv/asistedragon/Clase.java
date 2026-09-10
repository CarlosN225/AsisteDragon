package com.utfv.asistedragon;

public class Clase {
    private String materia;
    private String dia;
    private String horaInicio;
    private String horaFin;
    private String salon;
    private String grupo;

    // ✅ Campo para bloqueo visual individual por materia
    public Boolean isConfirmada = false;

    public Clase() {
    }

    public Clase(String materia, String dia, String horaInicio, String horaFin) {
        this.materia = materia;
        this.dia = dia;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.salon = "";
        this.grupo = "";
        this.isConfirmada = false;
    }

    // Getters y Setters
    public String getMateria() { return materia; }
    public void setMateria(String materia) { this.materia = materia; }

    public String getDia() { return dia; }
    public void setDia(String dia) { this.dia = dia; }

    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }

    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = horaFin; }

    public String getSalon() { return salon; }
    public void setSalon(String salon) { this.salon = salon; }

    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }

    // Getter/Setter de confirmada
    public boolean isConfirmada() { return isConfirmada != null && isConfirmada; }
    public void setConfirmada(boolean confirmada) { this.isConfirmada = confirmada; }

    @Override
    public String toString() {
        return dia + " " + horaInicio + "-" + horaFin + ": " + materia;
    }
}