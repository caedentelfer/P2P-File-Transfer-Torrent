import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class P2PClientHandler implements Runnable {
    public Socket clientSocket;
    private BufferedReader in;
    public PrintWriter out;
    private P2PServer server;
    public String username;
    public List<String> filepaths = new ArrayList<>();

    public P2PClientHandler(Socket socket, P2PServer server) {
        this.clientSocket = socket;
        this.server = server;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Read client messages
            String line;
            while ((line = in.readLine()) != "exit" && line != null) {
                if (line.startsWith("Connected:")) {
                    username = line.substring(10);
                    out.println("Welcome to the server! - " + username);
                } else if ("list".equalsIgnoreCase(line)) {
                    // Send a list of all connected clients
                    server.broadcastClients(line);
                } else if (line.startsWith("send")) {
                    line += " " + username;
                    server.broadcastClients(line);

                } else if (line.startsWith("receive")) {
                    line += " " + username;
                    server.broadcastClients(line);
                } else if (line.startsWith("pause") || line.startsWith("resume")) {
                    sendMessage(line);
                } else if (line.startsWith("upload")) {
                    filepaths.add(line.substring(7));
                    sendMessage(line);
                } else if (line.startsWith("search")) {
                    server.broadcastClients(line);
                } else if (line.startsWith("download")) {
                    server.broadcastClients(line);
                } else {
                    out.println(
                            "Unkown command entered. Usage:\n\n<send>  'src user'  '/path/to/file'  'port-to-send through'"
                                    + " \n<receive>  'src user'  '/path/to/file'\n(while downloading), "
                                    + "type 'pause' or 'resume'\n<search> 'filename'\n<upload> 'filepath'");
                }
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        } finally {
            server.removeClient(this);
            try {
                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}
