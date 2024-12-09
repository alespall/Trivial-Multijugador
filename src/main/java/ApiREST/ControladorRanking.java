
package ApiREST;


import SERVIDOR.GestorPartidas;
import SERVIDOR.JugadorPartida;
import SERVIDOR.Partida;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ranking")
@CrossOrigin(origins = "*")
public class ControladorRanking {

    @GetMapping("/global")
    public ResponseEntity<List<Map<String, Object>>> getRankingGlobal() {
        List<Map<String, Object>> rankingGlobal = new ArrayList<>();
        
        for (Partida partida : GestorPartidas.getInstance().getPartidasActivas()) {
            List<JugadorPartida> jugadores = partida.obtenerRanking();
            for (JugadorPartida jugador : jugadores) {
                Map<String, Object> jugadorInfo = new HashMap<>();
                jugadorInfo.put("nombre", jugador.getNombre());
                jugadorInfo.put("puntuacion", jugador.getPuntuacionTotal());
                jugadorInfo.put("partidaId", partida.getCodigo());
                rankingGlobal.add(jugadorInfo);
            }
        }        
        return ResponseEntity.ok(rankingGlobal);
    }

    @GetMapping("/partida/{codigo}")
    public ResponseEntity<List<Map<String, Object>>> getRankingPartida(@PathVariable String codigo) {
        Partida partida = GestorPartidas.getInstance().obtenerPartida(codigo);
        
        if (partida == null) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> rankingPartida = new ArrayList<>();
        List<JugadorPartida> jugadores = partida.obtenerRanking();
        
        for (JugadorPartida jugador : jugadores) {
            Map<String, Object> jugadorInfo = new HashMap<>();
            jugadorInfo.put("nombre", jugador.getNombre());
            jugadorInfo.put("puntuacion", jugador.getPuntuacionTotal());
            jugadorInfo.put("posicion", rankingPartida.size() + 1);
            rankingPartida.add(jugadorInfo);
        }
        
        return ResponseEntity.ok(rankingPartida);
    }

    @GetMapping("/jugador/{nombre}")
    public ResponseEntity<Map<String, Object>> getEstadisticasJugador(@PathVariable String nombre) {
        Map<String, Object> estadisticas = new HashMap<>();
        int partidasJugadas = 0;
        int puntuacionTotal = 0;
        int mejorPuntuacion = 0;
        
        for (Partida partida : GestorPartidas.getInstance().getPartidasActivas()) {
            for (JugadorPartida jugador : partida.getJugadores()) {
                if (jugador.getNombre().equalsIgnoreCase(nombre)) {
                    partidasJugadas++;
                    puntuacionTotal += jugador.getPuntuacionTotal();
                    mejorPuntuacion = Math.max(mejorPuntuacion, jugador.getPuntuacionTotal());
                }
            }
        }
        
        if (partidasJugadas == 0) {
            return ResponseEntity.notFound().build();
        }

        estadisticas.put("nombre", nombre);
        estadisticas.put("partidasJugadas", partidasJugadas);
        estadisticas.put("puntuacionTotal", puntuacionTotal);
        estadisticas.put("puntuacionMedia", puntuacionTotal / (double) partidasJugadas);
        estadisticas.put("mejorPuntuacion", mejorPuntuacion);
        
        return ResponseEntity.ok(estadisticas);
    }

    @GetMapping("/top/{n}")
    public ResponseEntity<List<Map<String, Object>>> getTopJugadores(@PathVariable int n) {
        Map<String, Integer> mejoresPuntuaciones = new HashMap<>();
        
        for (Partida partida : GestorPartidas.getInstance().getPartidasActivas()) {
            for (JugadorPartida jugador : partida.getJugadores()) {
                mejoresPuntuaciones.merge(jugador.getNombre(), 
                                        jugador.getPuntuacionTotal(), 
                                        Integer::max);
            }
        }

        List<Map<String, Object>> topJugadores = new ArrayList<>();
        mejoresPuntuaciones.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(n)
            .forEach(entry -> {
                Map<String, Object> jugadorInfo = new HashMap<>();
                jugadorInfo.put("nombre", entry.getKey());
                jugadorInfo.put("mejorPuntuacion", entry.getValue());
                topJugadores.add(jugadorInfo);
            });
        
        return ResponseEntity.ok(topJugadores);
    }
}

