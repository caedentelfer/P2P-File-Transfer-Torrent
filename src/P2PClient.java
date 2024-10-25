import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class P2PClient {
    private String host;
    private int port;
    public static String username;
    private static Socket sendSocket;
    private static ServerSocket receiveSocket;
    private volatile boolean pause = false; // changes are immediately visible in all threads
    public boolean is_ready_to_send = true;
    public boolean exit;
    public String ip;
    public static List<String> filepaths = new ArrayList<>();
    private static PrintWriter out;

    /* GUI stuff */
    private static JProgressBar progressBar;
    private static JTextArea searchResultsTextArea; /* for search results */
    private static JList<String> searchResultsList;

    public P2PClient(String host, int port) {
        this.host = host;
        this.port = port;
        exit = false;
    }

    public void start() throws IOException {
        try (Socket socket = new Socket(host, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)) {

            P2PClient.out = out;

            System.out.println("Connected to server.");
            out.println("Connected:" + username);

            ip = socket.getLocalAddress().getHostAddress();

            // Thread to read messages from the server
            Thread readThread = new Thread(() -> {
                try {
                    String fromServer;
                    while ((fromServer = in.readLine()) != null) {
                        System.out.println("Server: " + fromServer);
                        if (fromServer.startsWith("upload")) {
                            uploadFile();
                        }
                        if (fromServer.contains("requested you to send")) {
                            if (!is_ready_to_send) {
                                System.out.println("Cannot send another file until this one is sent.");
                                continue;
                            }
                            is_ready_to_send = false; // cannot send another file until this one is sent
                            // send file
                            Scanner sc = new Scanner(fromServer).useDelimiter("'");
                            String receiver_ip = sc.next();
                            sc.next(); // skip "requested you to send '"
                            String filePath = sc.next();
                            sc.next(); // skip "' to port '"
                            int port_to = Integer.parseInt(sc.next());

                            System.out.println("Sending file to " + receiver_ip + ":" + port_to);

                            Thread sendThread = new Thread(() -> {
                                try {
                                    sendFile(filePath, receiver_ip, port_to);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });

                            sendThread.start();

                        } else if (fromServer.contains("requested to receive")) {
                            // receive file
                            Scanner sc = new Scanner(fromServer).useDelimiter("'");
                            sc.next(); // skip "'requested to receive file '"
                            String filePath = sc.next();
                            sc.next(); // skip "'from port '"
                            int port_from = Integer.parseInt(sc.next());
                            sc.next(); // skip "'from '"
                            String src_user = sc.next();
                            System.out.println("Receiving file " + filePath + " from " + port_from);

                            Thread receiveThread = new Thread(() -> {
                                try {
                                    receiveFile("rec_", filePath, port_from);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });

                            receiveThread.start();
                            String command = "send " + src_user + " " + filePath + " " + port_from + " " + username;
                            out.println(command);
                        } else if (fromServer.contains("Username already exists")) {
                            System.out.println("Username already exists.");
                            exit = true;
                            close_all();
                            break;
                        } else if (fromServer.equals("pause")) {
                            setPause(true);
                        } else if (fromServer.equals("resume")) {
                            setPause(false);
                        } else if (fromServer.startsWith("File found")) {
                            filepaths.add(fromServer.substring(10, fromServer.indexOf("#in")));
                        } else if (fromServer.startsWith("dwnld connection")) {
                            Scanner sc = new Scanner(fromServer).useDelimiter("#");
                            sc.next(); // skip "dwnld connection found at"
                            String src_user = sc.next();
                            String path = sc.next();
                            out.println("receive " + path + " " + src_user);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Server connection lost.");
                }
            });
            readThread.start();

            // Main loop for user input
            while (!exit) {
                String command = scanner.nextLine();
                out.println(command);

                if (command.equals("pause")) {
                    setPause(true);
                } else if (command.equals("resume")) {
                    setPause(false);
                } else if (command.startsWith("upload")) {
                    JFileChooser fileChooser = new JFileChooser();
                    int result = fileChooser.showOpenDialog(null);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        out.println("upload " + file.getName() + " " + ip);
                        /* logic here */
                    }

                } else if ("exit".equalsIgnoreCase(command)) {
                    break;
                }
            }
        }
    }

    // Method to send a file
    public void sendFile(String filePath, String host, int sendPort) throws IOException {
        sendSocket = new Socket(host, sendPort);
        File file = new File(filePath);
        byte[] bytes = new byte[(int) file.length()];

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        bis.read(bytes, 0, bytes.length);

        OutputStream os = sendSocket.getOutputStream();
        os.write(bytes, 0, bytes.length);
        os.flush();
        os.close();
        bis.close();
        sendSocket.close();

        is_ready_to_send = true;
        System.out.println("File sent successfully to " + host + ":" + port);
    }

    // Method to receive files
    public void receiveFile(String savePath, String filepath, int receivePort) throws IOException {
        receiveSocket = new ServerSocket(receivePort);
        System.out.println("Your receiving socket is listening on port: " + receivePort);

        while (true) {
            Socket clientSocket = receiveSocket.accept();
            System.out.println("Client connected.");

            InputStream in = clientSocket.getInputStream();
            byte[] bytes = new byte[16 * 1024];

            // format file name:
            String fileName = savePath + filepath.substring(filepath.lastIndexOf('/') + 1);

            FileOutputStream fos = new FileOutputStream(fileName);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            int count;
            int totalBytesRead = 0;

            while ((count = in.read(bytes)) > 0) {
                synchronized (this) { // synchronized block to pause/resume download
                    while (pause) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                bos.write(bytes, 0, count);
                totalBytesRead += count;
                System.out.println("Received " + totalBytesRead + " bytes");
            }

            bos.flush();
            bos.close();
            in.close();
            clientSocket.close();

            System.out.println("File received successfully - " + totalBytesRead + "/" + totalBytesRead + " bytes");
            break; // remove this line to keep listening for more files
        }

        receiveSocket.close();
    }

    public synchronized void setPause(boolean pause) {
        if (pause == true) {
            System.out.println("Pausing download ...");
        } else {
            System.out.println("Resuming download ...");
        }
        this.pause = pause;
        if (!pause) {
            notifyAll(); // Notify all threads that might be waiting when resuming
        }
    }

    /**
     * Opens a file chooser dialog for the user to select a file for upload.
     */
    private static void uploadFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            out.println("upload " + file.getPath());
            /* logic here */
        }

    }

    private static void downloadFile() {
        String path = searchResultsList.getSelectedValue();
        System.out.println("Downloading file: " + path);
        out.println("download#" + path + "#" + username);
    }

    /**
     * Prompts the user to enter a keyword to search for.
     * Sends the search request to the server with the specified keyword.
     * Clears the search results list and displays the new search results.
     */
    private static void searchFiles() {
        String keyword = JOptionPane.showInputDialog("Enter keyword to search:");
        out.println("search #" + keyword + "#" + username);
        /* logic for it here */

        DefaultListModel<String> searchResultsModel = (DefaultListModel<String>) searchResultsList.getModel();
        searchResultsModel.clear();

        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (String filepath : filepaths) {
            searchResultsModel.addElement(filepath);
        }
    }

    public static void close_all() {
        try {
            if (sendSocket != null) {
                sendSocket.close();
            }
            if (receiveSocket != null) {
                receiveSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void pause() {
        out.println("pause");
    }

    public static void resume() {
        out.println("resume");
    }

    public static void main(String[] args) {
        String host = "localhost"; // from gui
        int port = 1234; // from gui
        username = args[0]; // from gui

        /* GUI SETUP */
        JFrame frame = new JFrame("P2P File Sharing: " + username);
        frame.setSize(400, 360);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        JButton uploadButton = new JButton("Upload");
        JButton downloadButton = new JButton("Download");
        JButton searchButton = new JButton("Search");

        JButton pauseButton = new JButton("Pause");
        JButton resumeButton = new JButton("Resume");

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        uploadButton.addActionListener(e -> uploadFile());
        downloadButton.addActionListener(e -> downloadFile());
        searchButton.addActionListener(e -> searchFiles());
        pauseButton.addActionListener(e -> pause());
        resumeButton.addActionListener(e -> resume());

        panel.add(uploadButton);
        panel.add(downloadButton);
        panel.add(searchButton);
        panel.add(progressBar);
        panel.add(pauseButton);
        panel.add(resumeButton);

        /* Add some spacing between buttons */
        panel.add(Box.createVerticalStrut(10));

        DefaultListModel<String> searchResultsModel = new DefaultListModel<>();
        searchResultsList = new JList<>(searchResultsModel);
        JScrollPane scrollPane = new JScrollPane(searchResultsList);
        scrollPane.setPreferredSize(new Dimension(300, 250));
        panel.add(scrollPane);

        frame.add(panel);

        /* used for when closing the window, the client safely disconnects */
        frame.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {
            }

            @Override
            public void windowClosing(WindowEvent e) {
                out.println("DISCONNECT");
                close_all();
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }

            @Override
            public void windowIconified(WindowEvent e) {
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
            }

            @Override
            public void windowActivated(WindowEvent e) {
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
            }
        });

        frame.setVisible(true);
        /* UP TO HERE FOR GUI SETUP */

        try {
            P2PClient client = new P2PClient(host, port);
            client.start();
        } catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }
    }

}
