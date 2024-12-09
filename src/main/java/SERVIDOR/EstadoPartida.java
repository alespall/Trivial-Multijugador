
package SERVIDOR;

/**
 *
 * @author aespa
 */
public enum EstadoPartida {
    ESPERANDO("Esperando jugadores"),
    INICIANDO("Iniciando partida"),
    EN_CURSO("Partida en curso"),
    ENTRE_RONDAS("Entre rondas"),
    FINALIZADA("Partida finalizada"),
    CANCELADA("Partida cancelada");
    
    private final String descripcion;
    
    EstadoPartida(String descripcion) {
        this.descripcion = descripcion;
    }
    public String getDescripcion() {
        return descripcion;
    }

    public boolean puedeUnirseJugador() {
        return this == ESPERANDO;
    }

    public boolean estaActiva() {
        return this == EN_CURSO || this == ENTRE_RONDAS;
    }

    public boolean haFinalizado() {
        return this == FINALIZADA || this == CANCELADA;
    }
    @Override
    public String toString() {
        return descripcion;
    }
}
