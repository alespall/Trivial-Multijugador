package SERVIDOR;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class ControladorCliente implements Runnable {
    private final Socket clienteSocket;
    private final BufferedReader in;
    private final PrintWriter out;
    private JugadorPartida jugador;
    private Partida partida;
    private boolean esCreador;
    private static boolean mensajeServidorMostrado = false;
    
    public ControladorCliente(Socket socket) throws IOException {
        this.clienteSocket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.esCreador = false;
    }
    
    @Override
    public void run() {
        try {
            synchronized(ControladorCliente.class) {
                if (!mensajeServidorMostrado) {
                    System.out.println("Servidor Trivial iniciado en puerto 5000");
                    mensajeServidorMostrado = true;
                }
            }
            System.out.println("Nuevo cliente conectado desde: " + clienteSocket.getInetAddress());
            
            mostrarMenuPrincipal();
            String opcion;
            while ((opcion = in.readLine()) != null) {
                procesarOpcion(opcion.trim());
            }
        } catch (SocketException se) {
            
            System.out.println("Cliente desconectado normalmente: " + 
                (jugador != null ? jugador.getNombre() : "desconocido"));
            limpiarRecursos();
        } catch (IOException e) {
            System.err.println("Error de E/S con cliente: " + e.getMessage());
            limpiarRecursos();
        } catch (InterruptedException e) {
            System.err.println("Operación interrumpida: " + e.getMessage());
            Thread.currentThread().interrupt();
            limpiarRecursos();
        }
    }
    
    private void mostrarMenuPrincipal() {
        out.println("=== TRIVIAL MULTIJUGADOR ===");
        out.println("1. Crear partida");
        out.println("2. Unirse a partida");
        out.println("3. Salir");
        out.println("Seleccione una opción:");
        out.flush(); // asegurar que enviamos todo
    }
    
    private void procesarOpcion(String opcion) throws IOException, InterruptedException {
        switch (opcion) {
            case "1":
                crearPartida();
                break;
            case "2":
                unirseAPartida();
                break;
            case "3":
                realizarDesconexion();
                return; 
            default:
                out.println("Opción no válida");
                mostrarMenuPrincipal();
        }
    }
    
    private void crearPartida() throws IOException, InterruptedException {
        out.println("Introduce tu nombre:");
        String nombre = in.readLine();
        jugador = new JugadorPartida(nombre);
        esCreador = true;
        
        String codigo = GestorPartidas.getInstance().crearPartida();
        partida = GestorPartidas.getInstance().obtenerPartida(codigo);
        partida.agregarJugador(jugador, true);
        
        out.println("Partida creada con código: " + codigo);
        esperarJugadores();
    }
    
    private void unirseAPartida() throws IOException, InterruptedException {
        out.println("Introduce tu nombre:");
        String nombre = in.readLine();
        out.println("Introduce el código de la partida:");
        String codigo = in.readLine();
        
        partida = GestorPartidas.getInstance().obtenerPartida(codigo);
        if (partida == null) {
            out.println("Partida no encontrada");
            mostrarMenuPrincipal();
            return;
        }
        
        jugador = new JugadorPartida(nombre);
        if (!partida.agregarJugador(jugador, false)) {
            out.println("No se puede unir a la partida");
            mostrarMenuPrincipal();
            return;
        }
        
        esperarJugadores();
    }
    
    private void esperarJugadores() throws IOException, InterruptedException {
        if (esCreador) {
            boolean esperandoJugadores = true;
            int jugadoresAnteriores = 0;
            
            out.println("\nPartida creada. Esperando que se unan jugadores...");
            
            while (esperandoJugadores) {
                int jugadoresActuales = partida.getJugadores().size();
                
                // Si se ha unido un nuevo jugador y hay al menos 2 jugadores
                if (jugadoresActuales > jugadoresAnteriores && jugadoresActuales >= 2) {
                    mostrarMenuComenzar();
                    String opcion = in.readLine();
                    
                    if (opcion.equals("1")) {
                        if (partida.puedeComenzar()) {
                            esperandoJugadores = false;
                            partida.comenzarPartida();
                        } else {
                            out.println("\nNo hay suficientes jugadores para comenzar (mínimo 2).");
                            out.println("\nEsperando que se unan más jugadores...");
                        }
                    } else if (opcion.equals("2")) {
                        out.println("\nEsperando que se unan más jugadores...");
                    }
                    
                    jugadoresAnteriores = jugadoresActuales;
                }
                
                Thread.sleep(1000);
            }
        } else {
            out.println("\nTe has unido a la partida. Esperando a que el creador inicie la partida...");
            while (partida.getEstado() == EstadoPartida.ESPERANDO) {
                Thread.sleep(1000);
            }
        }
        
        if (partida.getEstado() == EstadoPartida.EN_CURSO) {
            iniciarPartida();
        }
    }
    
    private void mostrarMenuComenzar() {
        out.println("\n=== MENÚ DE INICIO ===");
        out.println("Jugadores conectados: " + partida.getJugadores().size());
        out.println("1. Comenzar partida");
        out.println("2. Seguir esperando");
        out.println("Seleccione una opción:");
        out.flush();
    }
    
    private void iniciarPartida() throws IOException {
        try {
            out.println("\n¡La partida comienza!");
            
            for (int ronda = 1; ronda <= partida.getTOTAL_RONDAS(); ronda++) {
                try {
                    out.println("\n=== INICIANDO RONDA " + ronda + " ===");
                    out.flush();
                    jugarRonda();
                    // pausa entre rondas
                    if (ronda < partida.getTOTAL_RONDAS()) {
                        Thread.sleep(1000);
                    }
                } catch (IOException e) {
                    System.err.println("Error en ronda " + ronda + ": " + e.getMessage());
                    if (e.getMessage().contains("Error de sincronización")) {
                        continue; 
                    }
                    throw e;
                }
            }
            mostrarResultadosFinales();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operación interrumpida", e);
        } finally {
            limpiarEstado();
        }
    }
    private void jugarRonda() throws IOException {
        try {
            if (partida == null) {
                throw new IOException("La partida no está inicializada");
            }

            int rondaActual = partida.getRondaActual();
            Pregunta pregunta = partida.getPreguntaActual();
            if (pregunta == null) {
                throw new IOException("No hay pregunta disponible para la ronda " + rondaActual);
            }
            // Calcular tiempo 
            int tiempoRonda = 100 - ((rondaActual - 1) * 20);
            
            System.out.println("DEBUG - Hilo " + Thread.currentThread().getId() + " iniciando ronda " + rondaActual);
            
            try {
                System.out.println("DEBUG - Hilo " + Thread.currentThread().getId() + " esperando en barreraPregunta para ronda " + rondaActual);
                partida.getBarreraPregunta().await(5, TimeUnit.SECONDS);
                
                mostrarInformacionRonda(rondaActual, pregunta);
                out.println("\nTienes " + tiempoRonda + " segundos para responder.");
                out.println("Escribe tu respuesta (A, B, C o D): ");
                out.flush();
                
                boolean respondido = procesarRespuestaConTiempo(tiempoRonda);
                if (!respondido) {
                    out.println("\n¡Tiempo agotado!");
                } else {
                    out.println("\nRespuesta registrada. Esperando a que respondan los demás jugadores...");
                    out.flush();
                }
                System.out.println("DEBUG - Hilo " + Thread.currentThread().getId() + " esperando en barreraRonda para ronda " + rondaActual);
                
                // Mostrar jugadores que faltan por responder
                while (!partida.hanRespondidoTodos()) {
                    int jugadoresRestantes = partida.getJugadoresSinResponder();
                    out.println("Faltan " + jugadoresRestantes + " jugadores por responder...");
                    out.flush();
                    Thread.sleep(2000); // Actualizar cada 2 segundos
                }
                
                partida.getBarreraRonda().await(tiempoRonda + 5, TimeUnit.SECONDS);
                
                mostrarResultadosRonda();
                
                if (rondaActual < partida.getTOTAL_RONDAS()) {
                    if (esCreador) {
                        out.println("\nPreparando siguiente ronda...");
                        Thread.sleep(3000);
                        System.out.println("DEBUG - Preparando siguiente ronda");
                        partida.prepararSiguienteRonda();
                    }
                    System.out.println("DEBUG - Hilo " + Thread.currentThread().getId() + " esperando barreraSiguienteRonda para ronda " + rondaActual);
                    partida.getBarreraSiguienteRonda().await(5, TimeUnit.SECONDS);
                }
            } catch (BrokenBarrierException | TimeoutException e) {
                System.err.println("DEBUG - Error en barrera: " + e.getMessage() + " en ronda " + rondaActual);
                if (rondaActual < partida.getTOTAL_RONDAS()) {
                    throw new IOException("Error de sincronización en ronda " + rondaActual);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operación interrumpida", e);
        }
    }
    
    private void mostrarInformacionRonda(int rondaActual, Pregunta pregunta) {
        if (pregunta == null) {
            out.println("Error: No hay pregunta disponible");
            return;
        }

        out.println("\n=== RONDA " + rondaActual + " DE " + partida.getTOTAL_RONDAS() + " ===");
        
        out.println("\nPregunta: " + pregunta.getEnunciado());
        
        if (pregunta.getCodigo() != null && !pregunta.getCodigo().isEmpty()) {
            out.println("\nCódigo:\n" + pregunta.getCodigo());
        }
        
        mostrarOpciones(pregunta);
        out.flush();
    }
    
    private void mostrarOpciones(Pregunta pregunta) {
        List<String> opciones = pregunta.getOpciones();
        for (int i = 0; i < opciones.size(); i++) {
            out.println((char)('A' + i) + ") " + opciones.get(i));
        }
    }
    
    private boolean procesarRespuestaConTiempo(int tiempoRonda) {
        try {
            CompletableFuture<String> futureRespuesta = new CompletableFuture<>();
            
            Thread hiloRespuesta = new Thread(() -> {
                try {
                    String respuesta = in.readLine();
                    if (!futureRespuesta.isCancelled()) {
                        futureRespuesta.complete(respuesta);
                    }
                } catch (IOException e) {
                    futureRespuesta.completeExceptionally(e);
                }
            });
            
            hiloRespuesta.setDaemon(true);
            hiloRespuesta.start();
            
            try {
                String respuesta = futureRespuesta.get(tiempoRonda, TimeUnit.SECONDS);
                partida.registrarRespuesta(jugador, respuesta);
                return true;
            } catch (TimeoutException e) {
                futureRespuesta.cancel(true);
                partida.registrarRespuesta(jugador, null);
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Respuesta interrumpida: " + e.getMessage());
                return false;
            } catch (ExecutionException e) {
                System.err.println("Error al procesar respuesta: " + e.getCause().getMessage());
                return false;
            }
            
        } catch (IllegalStateException e) {
            System.err.println("Error de estado al procesar respuesta: " + e.getMessage());
            return false;
        }
    }
    
    private void mostrarResultadosRonda() {
        out.println("\n=== RESULTADOS DE LA RONDA " + partida.getRondaActual() + " ===");
        List<JugadorPartida> jugadoresOrdenados = partida.obtenerRanking();
        for (JugadorPartida j : jugadoresOrdenados) {
            out.println(j.getNombre() + ": " + j.getPuntuacionTotal() + " puntos");
        }
        out.flush();
    }
    
    private void mostrarResultadosFinales() {
        out.println("\n=== RESULTADOS FINALES ===");
        
        if (!partida.hayGanador()) {
            out.println("\n¡No hay ganador! Ningún jugador consiguió puntos.");
        } else if (partida.hayEmpate()) {
            List<JugadorPartida> ganadores = partida.obtenerGanadores();
            out.println("\n¡EMPATE entre los siguientes jugadores!");
            for (JugadorPartida ganador : ganadores) {
                out.println("- " + ganador.getNombre() + ": " + ganador.getPuntuacionTotal() + " puntos");
            }
        } else {
            JugadorPartida ganador = partida.obtenerGanadores().get(0);
            out.println("\n ¡GANADOR: " + ganador.getNombre() + "!");
            out.println("Puntuación: " + ganador.getPuntuacionTotal() + " puntos");
        }

        out.println("\nRanking final:");
        List<JugadorPartida> ranking = partida.obtenerRanking();
        for (int i = 0; i < ranking.size(); i++) {
            JugadorPartida j = ranking.get(i);
            out.println((i + 1) + ". " + j.getNombre() + ": " + j.getPuntuacionTotal() + " puntos");
        }
        out.println("\n¡Gracias por jugar!");
        out.flush();
    }
    
    private void realizarDesconexion() {
        try {
            if (jugador != null) {
                System.out.println("Cliente desconectado: " + jugador.getNombre());
            } else {
                System.out.println("Cliente desconectado");
            }
            
            if (partida != null) {
                partida.eliminarJugador(jugador);
                partida = null;
            }
            
            out.println("Conexión cerrada. ¡Hasta pronto!");
            out.flush();
            
            // Limpiamos el estado
            jugador = null;
            esCreador = false;
            
            // pausa para asegurar el envio
            Thread.sleep(100);
            
            // Cerramos la conexión
            if (!clienteSocket.isClosed()) {
                clienteSocket.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void limpiarEstado() {
        esCreador = false;
        partida = null;
        jugador = null;
        mostrarMenuPrincipal();
    }
    
    private void limpiarRecursos() {
        try {
            if (partida != null && jugador != null) {
                partida.eliminarJugador(jugador);
                System.out.println("Jugador " + jugador.getNombre() + " eliminado de la partida " + 
                                 partida.getCodigo());
            }
            
            if (in != null) in.close();
            if (out != null) out.close();
            if (clienteSocket != null && !clienteSocket.isClosed()) {
                clienteSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar recursos: " + e.getMessage());
        } finally {
            jugador = null;
            partida = null;
            esCreador = false;
        }
    }
}
