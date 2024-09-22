package example;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Servidor {

    private static final int PUERTO = 6000;
    private static Map<String, DataOutputStream> usuariosConectados = new HashMap<>(); // Guardar usuarios y sus flujos de salida
    private static Map<Socket, String> socketsUsuarios = new HashMap<>(); // Relacionar socket con nombre de usuario

    public static void main(String[] args) {

        ServerSocket servidor = null;

        try {
            // Crear el socket del servidor
            servidor = new ServerSocket(PUERTO);
            System.out.println("Servidor iniciado");

            // Iniciar un hilo para permitir al administrador enviar comandos
            new Thread(new AdminHandler()).start();

            // Mantener el servidor en ejecución constante
            while (true) {
                // Esperar a que un cliente se conecte
                Socket sc = servidor.accept();

                System.out.println("Cliente conectado");

                // Crear un nuevo hilo para manejar la conexión del cliente
                ClienteHandler cliente = new ClienteHandler(sc);
                cliente.start();
            }

        } catch (IOException ex) {
            Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static class ClienteHandler extends Thread {

        private Socket socket;
        private DataInputStream inMsg;
        private DataOutputStream outMsg;
        private String nombreUsuario;

        public ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
        	inMsg = new DataInputStream(socket.getInputStream());
        	outMsg = new DataOutputStream(socket.getOutputStream());

                // Solicitar nombre de usuario al cliente
        	outMsg.writeUTF("Ingrese su nombre de usuario:");
                nombreUsuario = inMsg.readUTF();

                // Registrar el usuario conectado
                usuariosConectados.put(nombreUsuario, outMsg);
                socketsUsuarios.put(socket, nombreUsuario);

                System.out.println(nombreUsuario + " se ha conectado");

                // Informar a todos los usuarios que un nuevo usuario se conectó
                enviarMensajeATodos(nombreUsuario + " se ha conectado");

                // Ciclo para recibir mensajes del cliente
                while (true) {
                    String mensaje = inMsg.readUTF();

                    // Procesar los comandos especiales
                    if (mensaje.equals("/list")) {
                        listarUsuarios(outMsg);
                    } else if (mensaje.startsWith("@")) {
                        // Es un mensaje privado
                        enviarMensajePrivado(mensaje);
                    } else if (mensaje.equals("/exit")) {
                        // Comando para salir del chat
                        salirDelChat();
                        break; // Salir del bucle para desconectar al usuario sin excepciones
                    } else if (mensaje.equals("/help")) {
                        // Mostrar los comandos disponibles
                        mostrarComandos(outMsg);
                    } else {
                        // Enviar el mensaje a todos los usuarios conectados
                        enviarMensajeATodos(nombreUsuario + ": " + mensaje);

                        // Loguear el mensaje en la consola del servidor
                        loguearMensaje(nombreUsuario + ": " + mensaje);
                    }
                }

            } catch (IOException e) {
                // No loguear la excepción si es por desconexión del cliente
                if (!socket.isClosed()) {
                    Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, e);
                }
            } finally {
                try {
                    // Cuando el cliente se desconecta
                    if (nombreUsuario != null) {
                        usuariosConectados.remove(nombreUsuario);
                        socketsUsuarios.remove(socket);
                        socket.close();
                        System.out.println(nombreUsuario + " se ha desconectado");
                        enviarMensajeATodos(nombreUsuario + " se ha desconectado");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void listarUsuarios(DataOutputStream out) throws IOException {
            out.writeUTF("Usuarios conectados: " + usuariosConectados.keySet());
        }

        private void enviarMensajePrivado(String mensaje) throws IOException {
            String[] partes = mensaje.split(" ", 2);
            String nombreDestino = partes[0].substring(1); // Eliminar el '@' del nombre
            String mensajePrivado = partes[1];

            DataOutputStream outDestino = usuariosConectados.get(nombreDestino);
            if (outDestino != null) {
                outDestino.writeUTF("(Privado de " + nombreUsuario + "): " + mensajePrivado);
                outMsg.writeUTF("(Privado a " + nombreDestino + "): " + mensajePrivado);
            } else {
        	outMsg.writeUTF("Usuario " + nombreDestino + " no encontrado.");
            }
        }

        private void enviarMensajeATodos(String mensaje) throws IOException {
            for (DataOutputStream out : usuariosConectados.values()) {
                out.writeUTF(mensaje);
            }
        }

        // Método para loguear los mensajes públicos en la consola
        private void loguearMensaje(String mensaje) {
            System.out.println("Mensaje principal ->: " + mensaje);
        }

        // Método para mostrar los comandos disponibles al usuario
        private void mostrarComandos(DataOutputStream out) throws IOException {
            String comandos = """
                Comandos disponibles:
                /list - Listar todos los usuarios conectados
                /exit - Salir del chat
                /help - Mostrar los comandos disponibles
                @<usuario> <mensaje> - Enviar un mensaje privado a un usuario
                """;
            out.writeUTF(comandos);
        }

        // Método para que el usuario salga del chat de forma limpia
        private void salirDelChat() throws IOException {
            outMsg.writeUTF("Has salido del chat.");
            usuariosConectados.remove(nombreUsuario);
            socketsUsuarios.remove(socket);
            socket.close();
            System.out.println(nombreUsuario + " ha salido del chat.");
            enviarMensajeATodos(nombreUsuario + " ha salido del chat.");
        }
    }

    // Hilo para permitir que el servidor (administrador) envíe comandos y mensajes
    static class AdminHandler implements Runnable {

        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String comando = scanner.nextLine();

                try {
                    if (comando.startsWith("/list")) {
                        // Comando para listar los usuarios conectados
                        System.out.println("Usuarios conectados: " + usuariosConectados.keySet());
                    } else if (comando.startsWith("/ban")) {
                        // Comando para banear a un usuario
                        String nombreUsuario = comando.split(" ")[1];
                        banearUsuario(nombreUsuario);
                    } else {
                        // Enviar mensaje a todos los usuarios
                        enviarMensajeATodos("Servidor: " + comando);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void banearUsuario(String nombreUsuario) throws IOException {
            DataOutputStream out = usuariosConectados.get(nombreUsuario);
            if (out != null) {
                // Informar al usuario que ha sido baneado
                out.writeUTF("Has sido baneado del servidor.");
                // Buscar el socket del usuario y desconectarlo
                Socket socket = null;
                for (Map.Entry<Socket, String> entry : socketsUsuarios.entrySet()) {
                    if (entry.getValue().equals(nombreUsuario)) {
                        socket = entry.getKey();
                        break;
                    }
                }
                if (socket != null) {
                    socket.close(); // Cerrar la conexión con el usuario
                    usuariosConectados.remove(nombreUsuario); // Eliminar de la lista de conectados
                    socketsUsuarios.remove(socket); // Eliminar del mapa de sockets
                    enviarMensajeATodos("Servidor: " + nombreUsuario + " ha sido baneado.");
                    System.out.println(nombreUsuario + " ha sido baneado.");
                }
            } else {
                System.out.println("Usuario " + nombreUsuario + " no encontrado.");
            }
        }

        private void enviarMensajeATodos(String mensaje) throws IOException {
            for (DataOutputStream out : usuariosConectados.values()) {
                out.writeUTF(mensaje);
            }
        }
    }
}
