
package Cliente;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Cliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 5000;
    private final Scanner scanner;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean conectado;

    public Cliente() {
        this.scanner = new Scanner(System.in);
        this.conectado = false;
    }

    public void iniciar() {
        try {
            socket = new Socket(HOST, PUERTO);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            conectado = true;

            // Hilo para recibir mensajes del servidor
            Thread receptorMensajes = new Thread(this::recibirMensajes);
            receptorMensajes.setDaemon(true);
            receptorMensajes.start();

            // Hilo para enviar mensajes al servidor
            Thread enviadorMensajes = new Thread(this::enviarMensajes);
            enviadorMensajes.start();

        } catch (IOException e) {
            System.err.println("Error al conectar con el servidor: " + e.getMessage());
        }
    }

    private void recibirMensajes() {
        try {
            String mensajeServidor;
            while (conectado && (mensajeServidor = in.readLine()) != null) {
                System.out.println(mensajeServidor);
                if (mensajeServidor.equals("Conexión cerrada. ¡Hasta pronto!")) {
                    conectado = false;
                    break;
                }
            }
        } catch (IOException e) {
            if (conectado) {
                System.err.println("Error al recibir mensajes: " + e.getMessage());
            }
        }
    }

    private void enviarMensajes() {
        try {
            while (conectado) {
                String mensaje = scanner.nextLine();
                out.println(mensaje);
                if (mensaje.equals("3")) {
                    conectado = false;
                    break;
                }
            }
        } catch (IllegalStateException e) {
            if (conectado) {
                System.err.println("Error al enviar mensajes: " + e.getMessage());
            }
        } finally {
            cerrarConexion();
        }
    }

    private void cerrarConexion() {
        try {
            conectado = false;
            if (scanner != null) scanner.close();
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Cliente().iniciar();
    }
}