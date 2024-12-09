package SERVIDOR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Partida {
    private final String codigo;
    private final List<JugadorPartida> jugadores;
    private EstadoPartida estado;
    private CyclicBarrier barreraRonda;
    private CyclicBarrier barreraPregunta;
    private CyclicBarrier barreraSiguienteRonda;
    private final AtomicInteger rondaActual;
    private int tiempoRonda;
    private final AtomicInteger jugadoresRespondidos;
    private List<Pregunta> preguntasPartida;
    private Pregunta preguntaActual;
    private static final int MAX_JUGADORES = 4;
    private static final int MIN_JUGADORES = 2;
    private static final int TOTAL_RONDAS = 5;
    private static final int TIEMPO_INICIAL = 100;
    private static final int REDUCCION_TIEMPO = 20;
    private JugadorPartida creador;
    private boolean partidaIniciada;
    private final Object lockRonda = new Object();
    private volatile boolean rondaEnProceso = false;
    private final Object lockRespuestas = new Object();
    private int jugadoresAcertantes = 0;
    
    public Partida(String codigo) {
        this.codigo = codigo;
        this.jugadores = Collections.synchronizedList(new ArrayList<>());
        this.estado = EstadoPartida.ESPERANDO;
        this.rondaActual = new AtomicInteger(0);
        this.jugadoresRespondidos = new AtomicInteger(0);
        this.preguntasPartida = new ArrayList<>();
    }
    
    public synchronized boolean agregarJugador(JugadorPartida jugador, boolean esCreador) {
        if (jugadores.size() >= MAX_JUGADORES || estado != EstadoPartida.ESPERANDO) {
            return false;
        }
        jugadores.add(jugador);
        
        if (esCreador) {
            this.creador = jugador;
        }
        
        if (jugadores.size() == MAX_JUGADORES) {
            iniciarPartida();
        }
        return true;
    }
    
    public synchronized boolean puedeComenzar() {
        return jugadores.size() >= MIN_JUGADORES && !partidaIniciada && estado == EstadoPartida.ESPERANDO;
    }
    
    public synchronized void comenzarPartida() {
        synchronized(lockRonda) {
            System.out.println("DEBUG - Comenzando partida");
            seleccionarPreguntas(GestorPreguntas.getInstance().obtenerPreguntas());
            rondaActual.set(1);
            estado = EstadoPartida.EN_CURSO;
            
            int numJugadores = jugadores.size();
            System.out.println("DEBUG - Inicializando barreras para " + numJugadores + " jugadores");
            barreraPregunta = new CyclicBarrier(numJugadores);
            barreraRonda = new CyclicBarrier(numJugadores);
            barreraSiguienteRonda = new CyclicBarrier(numJugadores);
        }
    }
    
    void reiniciarBarreras() {
        System.out.println("DEBUG - Reiniciando barreras para " + jugadores.size() + " jugadores");
        int numJugadores = jugadores.size();
        barreraPregunta = new CyclicBarrier(numJugadores);
        barreraRonda = new CyclicBarrier(numJugadores);
        barreraSiguienteRonda = new CyclicBarrier(numJugadores);
    }
    
    public boolean esCreador(JugadorPartida jugador) {
        return creador != null && creador.equals(jugador);
    }
    
    public void iniciarPartida() {
        if (!partidaIniciada && jugadores.size() >= MIN_JUGADORES) {
            partidaIniciada = true;
            rondaActual.set(0);
            seleccionarPreguntas(GestorPreguntas.getInstance().obtenerPreguntas());
            this.estado = EstadoPartida.EN_CURSO;
            iniciarNuevaRonda();
        }
    }
    
    private void seleccionarPreguntas(List<Pregunta> todasLasPreguntas) {
        if (todasLasPreguntas == null || todasLasPreguntas.isEmpty()) {
            throw new IllegalStateException("No hay preguntas disponibles");
        }
        Random random = new Random();
        int totalPreguntas = todasLasPreguntas.size();
        int maxIndiceInicial = totalPreguntas - TOTAL_RONDAS;
        if (maxIndiceInicial < 0) {
            throw new IllegalStateException("No hay suficientes preguntas para una partida");
        }
        
        int indiceInicial = random.nextInt(maxIndiceInicial + 1);
        preguntasPartida = new ArrayList<>();
        System.out.println("DEBUG - Seleccionando " + TOTAL_RONDAS + " preguntas consecutivas desde el índice " + indiceInicial);
        
        for (int i = 0; i < TOTAL_RONDAS; i++) {
            Pregunta pregunta = todasLasPreguntas.get(indiceInicial + i);
            preguntasPartida.add(pregunta);
            System.out.println("Ronda " + (i + 1) + " de " + TOTAL_RONDAS + ": " + pregunta.getEnunciado());
        }
    }
    
    private synchronized void iniciarNuevaRonda() {
        synchronized (lockRonda) {
            int nuevaRonda = rondaActual.incrementAndGet();
            System.out.println("DEBUG - Iniciando ronda " + nuevaRonda + " con pregunta: " + preguntaActual.getEnunciado());
            tiempoRonda = TIEMPO_INICIAL - ((nuevaRonda - 1) * REDUCCION_TIEMPO);
            preguntaActual = preguntasPartida.get(nuevaRonda - 1);
            jugadoresRespondidos.set(0);
            for (JugadorPartida jugador : jugadores) {
                jugador.reiniciarRespuesta();
            }
        }
    }
    
    public synchronized void registrarRespuesta(JugadorPartida jugador, String respuesta) {
        synchronized(lockRespuestas) {
            if (jugador == null || jugador.haRespondido()) {
                return;
            }

            System.out.println("DEBUG - Registrando respuesta '" + respuesta + "' del jugador " + jugador.getNombre() + " en ronda " + rondaActual.get());
            jugador.responder(System.currentTimeMillis());
         
            Pregunta preguntaActual = getPreguntaActual();
            if (preguntaActual != null && respuesta != null && 
                preguntaActual.esRespuestaCorrecta(respuesta.toUpperCase())) {
                
                int puntos = calcularPuntos(++jugadoresAcertantes);
                jugador.sumarPuntos(puntos);
                
                System.out.println("DEBUG - Jugador " + jugador.getNombre() + " acertó y recibe " + puntos + " puntos");
            }
        }
    }
    
    public void marcarRespuestasPendientesPorTimeout() {
        jugadores.stream()
                .filter(j -> !j.haRespondido())
                .forEach(j -> registrarRespuesta(j, null));
    }
    
    public boolean debeAvanzarRonda() {
        return todosHanRespondido() || tiempoAgotado();
    }
    
    private boolean tiempoAgotado() {
        return System.currentTimeMillis() >= tiempoInicioRonda + (tiempoRonda * 1000);
    }
    
    public void esperarSiguienteRonda() throws InterruptedException, BrokenBarrierException {
        try {
            if (barreraSiguienteRonda != null && !partidaFinalizada()) {
                System.out.println("DEBUG - Jugadores esperando en barrera: " + barreraSiguienteRonda.getNumberWaiting() + " de " + barreraSiguienteRonda.getParties());
                barreraSiguienteRonda.await();
            }
        } catch (BrokenBarrierException | InterruptedException e) {
            System.err.println("DEBUG - Error en barrera: " + e.getMessage());
            if (!partidaFinalizada()) {
                throw e;
            }
        }
    }
    
    public synchronized void prepararSiguienteRonda() {
        synchronized (lockRonda) {
            if (rondaEnProceso) {
                System.out.println("DEBUG - Ronda ya en proceso, esperando...");
                return;
            }
            
            rondaEnProceso = true;
            int rondaAnterior = rondaActual.get();
            System.out.println("DEBUG - Preparando ronda. Actual: " + rondaAnterior + " -> Nueva: " + (rondaAnterior + 1));

            if (rondaAnterior < TOTAL_RONDAS) {
                int nuevaRonda = rondaActual.incrementAndGet();
                preguntaActual = preguntasPartida.get(nuevaRonda - 1);
                System.out.println("DEBUG - Iniciando ronda " + nuevaRonda + " con pregunta: " + preguntaActual.getEnunciado());

                // Reiniciamos contadores
                jugadoresAcertantes = 0;
                for (JugadorPartida jugador : jugadores) {
                    jugador.reiniciarRespuesta();
                }
                if (barreraPregunta == null || barreraPregunta.isBroken()) {
                    reiniciarBarreras();
                }
            } else {
                finalizarPartida();
            }
            rondaEnProceso = false;
        }
    }
    
    private int calcularPuntos(int posicionRespuesta) {
        switch (posicionRespuesta) {
            case 1:
                return 40; // Primer acierto
            case 2:
                return 25; // Segundo acierto
            case 3:
                return 5;  // Tercer acierto
            default:
                return 0;  // Cuarto acierto
        }
    }
    
    public boolean todosHanRespondido() {
        return jugadoresRespondidos.get() >= jugadores.size();
    }
    public List<JugadorPartida> obtenerRanking() {
        return jugadores.stream()
                .sorted((j1, j2) -> Integer.compare(j2.getPuntuacionTotal(), j1.getPuntuacionTotal()))
                .collect(Collectors.toList());
    }
    public String getCodigo() { return codigo; }
    public EstadoPartida getEstado() { return estado; }
    public int getRondaActual() {
        return rondaActual.get();
    }
    
    public int getTiempoRonda() { return tiempoRonda; }
    
    public Pregunta getPreguntaActual() {
        synchronized(lockRonda) {
            int indice = rondaActual.get() - 1;
            if (indice >= 0 && indice < preguntasPartida.size()) {
                preguntaActual = preguntasPartida.get(indice);
                return preguntaActual;
            }
            return null;
        }
    }
    public List<JugadorPartida> getJugadores() { return new ArrayList<>(jugadores); }
    
    public synchronized boolean partidaFinalizada() {
        synchronized(lockRonda) {
            return rondaActual.get() > TOTAL_RONDAS || estado == EstadoPartida.FINALIZADA;
        }
    }

    public CyclicBarrier getBarreraRonda() {
        return barreraRonda;
    }

    public CyclicBarrier getBarreraPregunta() {
        return barreraPregunta;
    }

    public synchronized void finalizarPartida() {
        synchronized(lockRonda) {
            if (estado != EstadoPartida.FINALIZADA) {
                System.out.println("DEBUG - Finalizando partida en ronda " + rondaActual.get() + " de " + TOTAL_RONDAS);
                this.estado = EstadoPartida.FINALIZADA;
                if (barreraRonda != null) barreraRonda.reset();
                if (barreraPregunta != null) barreraPregunta.reset();
                if (barreraSiguienteRonda != null) barreraSiguienteRonda.reset();
            }
        }
    }

    public int getTOTAL_RONDAS() {
        return TOTAL_RONDAS;
    }

    private long tiempoInicioRonda;

    public void iniciarTiempoRonda() {
        this.tiempoInicioRonda = System.currentTimeMillis();
    }

    public long getTiempoRestante() {
        long tiempoTranscurrido = System.currentTimeMillis() - tiempoInicioRonda;
        return Math.max(0, tiempoRonda * 1000 - tiempoTranscurrido);
    }

    public boolean hayGanador() {
        return jugadores.stream().anyMatch(j -> j.getPuntuacionTotal() > 0);
    }

    public boolean hayEmpate() {
        List<JugadorPartida> ranking = obtenerRanking();
        if (ranking.size() >= 2) {
            return ranking.get(0).getPuntuacionTotal() == ranking.get(1).getPuntuacionTotal() 
                   && ranking.get(0).getPuntuacionTotal() > 0;
        }
        return false;
    }

    public List<JugadorPartida> obtenerGanadores() {
        int maxPuntuacion = jugadores.stream()
                .mapToInt(JugadorPartida::getPuntuacionTotal)
                .max()
                .orElse(0);
        
        return jugadores.stream()
                .filter(j -> j.getPuntuacionTotal() == maxPuntuacion && maxPuntuacion > 0)
                .collect(Collectors.toList());
    }

    public CyclicBarrier getBarreraSiguienteRonda() {
        return barreraSiguienteRonda;
    }

    public synchronized void eliminarJugador(JugadorPartida jugador) {
        if (jugador != null && jugadores.contains(jugador)) {
            jugadores.remove(jugador);
            System.out.println("Jugador " + jugador.getNombre() + " eliminado de la partida " + codigo);
            
            if (jugadores.isEmpty()) {
                System.out.println("Partida " + codigo + " finalizada por falta de jugadores");
                finalizarPartida();
            }
        }
    }

    public synchronized boolean hanRespondidoTodos() {
        for (JugadorPartida jugador : jugadores) {
            if (!jugador.haRespondido()) {
                return false;
            }
        }
        return true;
    }

    public synchronized int getJugadoresSinResponder() {
        int sinResponder = 0;
        for (JugadorPartida jugador : jugadores) {
            if (!jugador.haRespondido()) {
                sinResponder++;
            }
        }
        return sinResponder;
    }
}
