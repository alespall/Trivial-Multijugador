package SERVIDOR;

public class JugadorPartida {
    private final String nombre;
    private int puntuacionTotal;
    private boolean haRespondido;
    private long tiempoRespuesta;
    private String respuestaActual;
    
    public JugadorPartida(String nombre) {
        this.nombre = nombre;
        this.puntuacionTotal = 0;
        this.haRespondido = false;
    }
    public synchronized void responder(long tiempo) {
        if (!haRespondido) {
            this.tiempoRespuesta = tiempo;
            this.haRespondido = true;
        }
    }
    public synchronized void sumarPuntos(int puntos) {
        this.puntuacionTotal += puntos;
    }
    
    public void reiniciarRespuesta() {
        this.haRespondido = false;
        this.tiempoRespuesta = 0;
        this.respuestaActual = null;
    }
    public String getNombre() { return nombre; }
    public int getPuntuacionTotal() { return puntuacionTotal; }
    public boolean haRespondido() { return haRespondido; }
    public long getTiempoRespuesta() { return tiempoRespuesta; }
    public String getRespuestaActual() { return respuestaActual; }
}