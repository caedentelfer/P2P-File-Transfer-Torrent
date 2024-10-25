import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class P2PServer {

    private ServerSocket serverSocket;
    private List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    public static final int PORT = 1234;
    public int curr_port = 1111;
    public Set<String> usernameList = Collections.synchronizedSet(new HashSet<>());

    public P2PServer() throws IOException {
        serverSocket = new ServerSocket(PORT);
    }

    public void start() throws IOException {
        System.out
                .println("Peer to peer Server started on port " + PORT + "\nlistening for incoming connections ...\n");
        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(clientSocket, this);
            clientHandlers.add(clientHandler);
            new Thread(clientHandler).start();

        }
    }

    public void broadcastClients(String command) {

        if (command != null && command.startsWith("send")) { // handle send requests
            // send file
            handleSendRequest(command);

        } else if (command != null && command.startsWith("receive")) { // handle receive requests
            handleReceiveRequest(command);
        } else if (command != null && command.startsWith("search")) {
            // search for file
            handleSearchRequest(command);
        } else if (command != null && command.startsWith("download")) {
            // download file
            handleDownloadRequest(command);
        }

    }

    public void handleDownloadRequest(String command) {
        Scanner sc = new Scanner(command).useDelimiter("#");
        sc.next(); // skip "download"
        String filepath = sc.next();
        String user = sc.next();
        ClientHandler src_user = null;
        for (ClientHandler client : clientHandlers) {
            for (String file : client.filepaths) {
                if (file.contains(filepath)) {
                    src_user = client;
                    break;
                }
            }
        }
        System.out.println(filepath + " WITH USER " + user);
        for (ClientHandler client : clientHandlers) {
            if (client.username.contains(user)) {
                client.sendMessage("dwnld connection found at#" + src_user + "#" + filepath);
                break;
            }
        }
    }

    public void handleSearchRequest(String command) {
        Scanner sc = new Scanner(command).useDelimiter("#");
        sc.next();
        String keyword = sc.next();
        String username = sc.next();
        ClientHandler sender = null;
        for (ClientHandler client : clientHandlers) {
            if (client.username.equals(username)) {
                sender = client;
                break;
            }
        }

        for (ClientHandler client : clientHandlers) {
            for (String filepath : client.filepaths) {
                if (filepath.contains(keyword)) {
                    sender.sendMessage("File found " + filepath + "#in " + client.username);
                }
            }
        }

    }

    public void handleSendRequest(String command) {
        Scanner sc = new Scanner(command).useDelimiter(" ");
        sc.next(); // skip "send"
        String username_to = sc.next();
        String filePath = sc.next();
        int new_port = Integer.parseInt(sc.next());
        String username_from = sc.next();

        for (ClientHandler client : clientHandlers) {
            if (client.username.equals(username_to)) {
                String receiver_ip = client.clientSocket.getInetAddress().getHostAddress(); // Can be localhost
                client.sendMessage(
                        "'" + receiver_ip + "'requested you to send '" + filePath + "' to port '" + new_port + "'");
                break;
            }
        }
    }

    public void handleReceiveRequest(String command) {
        Scanner sc = new Scanner(command).useDelimiter(" ");
        sc.next(); // skip "receive"
        String src_user = sc.next();
        String filepath = sc.next();
        int new_port = curr_port; // new port is generated for every send/receive
        curr_port++;
        String username = sc.next();
        for (ClientHandler client : clientHandlers) {
            if (client.username.equals(username)) {
                client.sendMessage(
                        "requested to receive file '" + filepath + "'from on port '" + new_port + "'from '"
                                + src_user + "'");
                break;
            }
        }
    }

    public boolean addUsername(String username) {
        return usernameList.add(username); // Returns false if the username already exists
    }

    public void removeClient(ClientHandler clientHandler) {
        usernameList.remove(clientHandler.username);
        clientHandlers.remove(clientHandler);
    }

    public static void main(String[] args) throws IOException {
        P2PServer server = new P2PServer();
        server.start();
    }
}
