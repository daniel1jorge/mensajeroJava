package example;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Cliente {

    public static void main(String[] args) {

        DataInputStream msgin;
        DataOutputStream msgout;
        Scanner scanner = new Scanner(System.in);

        try {
            // Solicitar host y puerto del servidor
            System.out.print("Ingresa el host (ejemplo: localhost): ");
            String host = scanner.nextLine();

            System.out.print("Ingresa el puerto (ejemplo: 6000): ");
            int puerto = Integer.parseInt(scanner.nextLine());

            // Crear el socket para conectarse con el servidor
            Socket socket = new Socket(host, puerto);

            msgin = new DataInputStream(socket.getInputStream());
            msgout = new DataOutputStream(socket.getOutputStream());

            // Iniciar un hilo para escuchar los mensajes del servidor
            Thread escucharServidor = new Thread(() -> {
                try {
                    while (true) {
                        String mensaje = msgin.readUTF();
                        System.out.println(mensaje);

                        // Detectar si el servidor indica que el cliente fue baneado o salió del chat
                        if (mensaje.contains("Has sido baneado del servidor.") || mensaje.contains("Has salido del chat.")) {
                            System.out.println("Cerrando cliente...");
                            socket.close();
                            System.exit(0); // Cerrar la ejecución del cliente
                        }
                    }
                } catch (IOException ex) {
                    // No mostrar error si el cliente cierra el socket
                    if (!socket.isClosed()) {
                        Logger.getLogger(Cliente.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });

            escucharServidor.start(); // Iniciar el hilo que escucha los mensajes del servidor

            // Solicitar nombre de usuario
            System.out.println("Ingresa tu nombre de usuario: ");
            String nombreUsuario = scanner.nextLine();
            msgout.writeUTF(nombreUsuario); // Enviar el nombre de usuario al servidor

            // Ciclo para enviar mensajes al servidor
            while (true) {
                String mensaje = scanner.nextLine();
                msgout.writeUTF(mensaje);

                // Si el cliente escribe "/exit", también cerrar el socket y salir
                if (mensaje.equals("/exit")) {
                    System.out.println("Cerrando cliente...");
                    socket.close();
                    System.exit(0); // Cerrar la ejecución del cliente
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(Cliente.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}

