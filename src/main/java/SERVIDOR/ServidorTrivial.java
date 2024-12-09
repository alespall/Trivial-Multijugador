
package SERVIDOR;


import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ServidorTrivial {
    private static final int PUERTO = 5000;
    private static final int MAX_CLIENTES = 100;
    private final ExecutorService poolHilos;
    private final GestorPartidas gestorPartidas;
    
    public ServidorTrivial() {
        this.poolHilos = Executors.newFixedThreadPool(MAX_CLIENTES);
        this.gestorPartidas = GestorPartidas.getInstance();
    }
    
    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("Servidor Trivial iniciado en puerto " + PUERTO);
            
            while (true) {
                Socket clienteSocket = serverSocket.accept();
                System.out.println("Nuevo cliente conectado desde: " + clienteSocket.getInetAddress());
                ControladorCliente controlador = new ControladorCliente(clienteSocket);
                poolHilos.execute(controlador);
            }
        } catch (Exception e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        } finally {
            poolHilos.shutdown();
        }
    }
    
    public static void main(String[] args) {
        new ServidorTrivial().iniciar();
    }
}
