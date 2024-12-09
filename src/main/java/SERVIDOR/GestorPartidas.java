
package SERVIDOR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 * @author aespa
 */
public class GestorPartidas {
    
    private static final GestorPartidas instancia = new GestorPartidas();
    private final Map<String, Partida> partidas;
    private final Map<String, List<String>> codigosPorJugador;
 
    private GestorPartidas() {
        this.partidas = new ConcurrentHashMap<>();
        this.codigosPorJugador = new ConcurrentHashMap<>();
    }
    public static GestorPartidas getInstance() {
        return instancia;
    }
    public synchronized String crearPartida() {
        String codigo;
        do {
            codigo = generarCodigo();
        } while (partidas.containsKey(codigo));
        
        partidas.put(codigo, new Partida(codigo));
        return codigo;
    }
    private String generarCodigo() {
        //código aleatorio para la partida
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int index = (int)(Math.random() * caracteres.length());
            codigo.append(caracteres.charAt(index));
        }
        return codigo.toString();
    }
    
    public Partida obtenerPartida(String codigo) {
        return partidas.get(codigo);
    }
    
    public synchronized void registrarJugadorEnPartida(String nombreJugador, String codigoPartida) {
        codigosPorJugador.computeIfAbsent(nombreJugador, k -> new ArrayList<>()).add(codigoPartida);
    }

    public synchronized void eliminarPartida(String codigo) {
        Partida partida = partidas.remove(codigo);
        if (partida != null) {
            partida.getJugadores().forEach(jugador -> 
                codigosPorJugador.getOrDefault(jugador.getNombre(), new ArrayList<>())
                    .remove(codigo)
            );
        }
    }
    public List<Partida> obtenerPartidasActivas() {
        return new ArrayList<>(partidas.values());
    }
    public boolean existePartida(String codigo) {
        return partidas.containsKey(codigo);
    }
    public synchronized void limpiarPartidasFinalizadas() {
        partidas.values().removeIf(partida -> partida.getEstado() == EstadoPartida.FINALIZADA);
    }
    public List<String> obtenerPartidasDeJugador(String nombreJugador) {
        return codigosPorJugador.getOrDefault(nombreJugador, new ArrayList<>());
    }
    public int getNumeroPartidasActivas() {
        return partidas.size();
    }
    public void finalizarTodasLasPartidas() {
        partidas.clear();
        codigosPorJugador.clear();
    }
    public Collection<Partida> getPartidasActivas() {
        return partidas.values().stream()
            .filter(partida -> partida.getEstado() != EstadoPartida.FINALIZADA)
            .collect(Collectors.toList());
    }
}
