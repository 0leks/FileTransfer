import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import javax.swing.*;
import javax.swing.Timer;

public class Server {
  
  public static final int portNumber = 33456;
  
  //public static final int CHUNK_SIZE = 1073741824/2; // 0.5GB
  public static final int CHUNK_SIZE = 134217728; // 100MB
  public static final boolean SEND_CHUNKS = true;
  
  private static final int BUFFER_SIZE = 1024;

  private int numUnknown = 0;
  
  private File selected;
  private List<File> files;
  
  private JFrame frame;
  private JPanel buttonPanel;
  private StatusPanel infoPanel;
  private ClientPanel clientPanel;
  private Timer timer;

  private Thread receiveThread;
  
  private Thread serverThread;
  private Thread connectionHandler;
  private LinkedBlockingQueue<Socket> connections = new LinkedBlockingQueue<Socket>();

  private LinkedBlockingQueue<String> requestedFiles = new LinkedBlockingQueue<String>();
  private Set<String> completedFiles = new HashSet<String>();
  
  public Server() {
	  
	  Gui gui = new Gui();
	  
    
    frame = new JFrame("File Transfer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(500, 500);
    frame.setLocationRelativeTo(null);
    
    buttonPanel = new JPanel();
    
    JButton receiveButton = new JButton("Receive Files");
    JButton chooseFiles = new JButton("Send Files");
    
    receiveButton.addActionListener((e) -> {
      receiveFiles();
    });
    buttonPanel.add(receiveButton);

    chooseFiles.addActionListener((e) -> {
      boolean success = setupSendFiles();
      if( success ) {
        buttonPanel.remove(receiveButton);
        serverThread.start();
        connectionHandler.start();
      }
    });
    buttonPanel.add(chooseFiles);
    
    frame.add(buttonPanel, BorderLayout.NORTH);
    infoPanel = new StatusPanel();
    infoPanel.setBackground(Color.LIGHT_GRAY);
    frame.add(infoPanel, BorderLayout.CENTER);
    frame.validate();
    frame.repaint();
    
    timer = new Timer(300, (e) -> {
      frame.repaint();
    });
    timer.start();

    frame.setVisible(true);
    
    
    serverThread = new Thread( () -> {
      ServerSocket serverSocket;
      try {
        serverSocket = new ServerSocket(portNumber);
        Socket clientSocket;
        while(true) {
          System.err.println("Waiting for connection");
          clientSocket = serverSocket.accept();
          System.err.println("accepted connection");
          connections.add(clientSocket);
        }
      } catch (IOException e1) {
        infoPanel.connection = "Server thread error";
        e1.printStackTrace();
      }
    }, "Server Thread");
    connectionHandler = new Thread(() -> {
      while(true) {
        try {
          if( connections.isEmpty() ) {
            chooseFiles.setEnabled(true);
          }
          Socket socket = connections.take();
          chooseFiles.setEnabled(false);
          infoPanel.connection = socket.getInetAddress().getHostAddress();
          OutputStream out = socket.getOutputStream();
          BufferedReader in = new BufferedReader( new InputStreamReader(socket.getInputStream()));
          infoPanel.addFile("established connection with " + socket.getInetAddress().getHostAddress());
          
          // First send name of root folder
          sendLine(selected.getAbsolutePath(), out);
          // Next send number of and list of available files
          sendLine((files.size()) + "", out);
          for( File f : files ) {
            sendLine(f.getAbsolutePath(), out);
          }
          
          while( true ) {
            String requestedFileName = in.readLine();
            infoPanel.addFile("request: " + requestedFileName);
            File requestedFile = null;
            for( File f : files ) {
              if( f.getAbsolutePath().equals(requestedFileName) ) {
                requestedFile = f;
              }
            }
            if( requestedFile == null ) {
              break;
            }
            else {
              if( SEND_CHUNKS ) {
                sendFileChunks(requestedFile, out);
              }
              else {
                sendFile(requestedFile, out);
              }
            }
          }
          in.close();
          out.close();
          socket.close();
        } catch (InterruptedException e1) {
          e1.printStackTrace();
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }
    }, "Connection Handler Thread");
  }
  
  private void sendFileChunks(File file, OutputStream out) throws IOException {
    FileInputStream fileStream = null;
    try {
      long fileLength = file.length();
      byte[] totalLength = ByteBuffer.allocate(8).putLong(fileLength).array();
      out.write(totalLength);
      fileStream = new FileInputStream(file);
      while( true ) {
        byte[] buffer = new byte[CHUNK_SIZE];
        int numRead = fileStream.read(buffer);
        System.err.println("Read " + numRead + " bytes from file " + file.getAbsolutePath());
        if( numRead == 0 || numRead == -1 ) {
          break;
        }
        else {
          System.err.println("Sending chunk of size " + numRead);
          byte[] len = ByteBuffer.allocate(4).putInt(numRead).array();
          out.write(len);
          out.write(buffer, 0, numRead);
        }
      }
      System.err.println("Finished writing " + file.getAbsolutePath());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
      try {
        fileStream.close();
      } catch (IOException e1) {
        e1.printStackTrace();
      }
      throw e;
    }
    
  }
  private void sendFile(File file, OutputStream out) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
    int length = bytes.length;
    System.err.println("length is " + length);
    byte[] len = ByteBuffer.allocate(4).putInt(length).array();
    out.write(len);
    out.write(bytes);
    String s = "Wrote " + file.getAbsolutePath();
    System.err.println(s);
  }
  
  private int readBytes(byte[] bytes, InputStream in, int numToRead) throws IOException {
    int marker = 0;
    int numRead = 0;
    int readSize = BUFFER_SIZE;
    while(numRead < numToRead - readSize ) {
      numRead += in.read(bytes, marker, readSize);
      marker = numRead;
//      clientPanel.setProgress(shortFile, 1.0f*numRead / length);
    }
    numRead += in.read(bytes, marker, numToRead - numRead);
    return numRead;
  }
  private boolean receiveFileChunks(String shortFile, String newFileName, File selectedFile, InputStream in) throws IOException {
    File targetFile = null;
    FileOutputStream fos = null;
    try {
      targetFile = new File(newFileName);
      try {
        targetFile.getParentFile().mkdirs();
        fos = new FileOutputStream(targetFile);
      } catch(Exception e) {
        newFileName = selectedFile.getAbsolutePath() + "unknown" + numUnknown++;
        targetFile = new File(newFileName);
        targetFile.getParentFile().mkdirs();
        fos = new FileOutputStream(targetFile);
      }
      long totalNumRead = 0;
      // get 4 byte length of the file
      byte[] totalSize = new byte[8];
      int totalNum = in.read(totalSize, 0, 8);
      if( totalNum == -1 ) {
        fos.close();
        return false;
      }
      long totalLength = ByteBuffer.wrap(totalSize).getLong();
      System.err.println("Read " + totalNum + " totalSize bytes total length of file:" + totalLength);
      
      while(true) {
        // get 4 byte length of the file
        byte[] size = new byte[4];
        int num = in.read(size, 0, 4);
        System.err.println("Read " + num + " chunkSize bytes");
        if( num == -1 ) {
          fos.close();
          return false;
        }
        int length = ByteBuffer.wrap(size).getInt();
        System.err.println("Expecting size " + length + " chunk ");
        
        if( length == 0 || length == -1 ) {
          break;
        }
        
        byte[] buffer = new byte[CHUNK_SIZE];
        
        int numRead = readBytes(buffer, in, length);
        totalNumRead += numRead;
        clientPanel.setProgress(shortFile, 1.0f*totalNumRead / totalLength);
        System.err.println("Read size " + numRead + " chunk");
        fos.write(buffer, 0, numRead);
        if( length < CHUNK_SIZE ) {
          break;
        }
      }
      

      fos.close();
      return true;
      
    }
    catch(Exception e) {
      if( fos != null ) {
        fos.close();
      }
      // if error while writing file, delete it since it will be corrupted
      if( targetFile != null ) {
        if( targetFile.exists() ) {
          System.err.println("Deleting corrupted file: " + targetFile.getAbsolutePath());
          targetFile.delete();
        }
      }
      throw e;
    }
  }
  private boolean receiveFile(String shortFile, String newFileName, File selectedFile, InputStream in) throws IOException {
    File targetFile = null;
    try {
   // get 4 byte length of the file
      byte[] size = new byte[4];
      int num = in.read(size);
      if( num == -1 ) {
        return false;
      }
      int length = ByteBuffer.wrap(size).getInt();
      clientPanel.setProgress(shortFile, 0.03f);
      
      byte[] bytes = new byte[length];
      int marker = 0;
      int numRead = 0;
      int readSize = CHUNK_SIZE;
      while(numRead < length - readSize ) {
        numRead += in.read(bytes, marker, readSize);
        marker = numRead;
        clientPanel.setProgress(shortFile, 1.0f*numRead / length);
      }
      numRead += in.read(bytes, marker, length - numRead);
      clientPanel.setProgress(shortFile, 1.0f);
      
      targetFile = new File(newFileName);
      FileOutputStream fos;
      try {
        targetFile.getParentFile().mkdirs();
        fos = new FileOutputStream(targetFile);
      } catch(Exception e) {
        newFileName = selectedFile.getAbsolutePath() + "unknown" + numUnknown++;
        targetFile = new File(newFileName);
        targetFile.getParentFile().mkdirs();
        fos = new FileOutputStream(targetFile);
      }
      fos.write(bytes);
      fos.close();
      return true;
    }
    catch(IOException e) {
      // if error while writing file, delete it since it will be corrupted
      if( targetFile != null ) {
        if( targetFile.exists() ) {
          System.err.println("Deleting corrupted file: " + targetFile.getAbsolutePath());
          targetFile.delete();
        }
      }
      throw e;
    }
  }
  
  private void switchToReceivingView() {
    frame.remove(buttonPanel);
    frame.remove(infoPanel);
    clientPanel = new ClientPanel((list) -> {
      for( String s : list ) {
        try {
          requestedFiles.put(s);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });
    frame.add(clientPanel, BorderLayout.CENTER);
    frame.validate();
  }
  
  private String[] getListOfAvailableFiles(int numFiles, InputStream in, String rootFolder, File localDirectory) throws IOException {
    String[] fileNames = new String[numFiles];
    for( int i = 0; i < numFiles; i++ ) {
      String fileName = readLine(in);
      fileNames[i] = fileName.substring(rootFolder.length() + 1);
      String newFileName = localDirectory.getAbsolutePath() + fileName.substring(rootFolder.length());
      System.err.println("File " + i + ":\"" + fileName + "\" -> \"" + newFileName + "\"");
    }
    return fileNames;
  }
  
  private void receiveFiles() {
    receiveThread = new Thread(() -> {
      infoPanel.receiveThreadRunning = true;
      File selectedFile = Server.chooseDirectory(frame);
      if( selectedFile == null ) {
        JOptionPane.showMessageDialog(frame, "No Selection ");
        return;
      }
      JOptionPane.showMessageDialog(frame, "Selected directory " + selectedFile.getAbsolutePath());
      
      switchToReceivingView();
      
      String ip = JOptionPane.showInputDialog(frame, "input ip address");
      if (ip == null) {
        System.err.println("null input");
        ip = "localhost";
      }
      System.err.println(ip);
      String hostName = ip;
  
      try ( Socket kkSocket = new Socket(hostName, Server.portNumber);
            PrintWriter out = new PrintWriter(kkSocket.getOutputStream(), true);
            InputStream in = kkSocket.getInputStream()
          ) {
        infoPanel.connection = kkSocket.getInetAddress().getHostAddress();
        
        String rootFolder = readLine(in);
        System.err.println("Root folder name:\"" + rootFolder + "\"");
        
        int numFiles = Integer.parseInt(readLine(in));
        System.err.println(numFiles + " available files");
        String[] fileNames = getListOfAvailableFiles(numFiles, in, rootFolder, selectedFile);
        clientPanel.populateList(fileNames);
        
        while(true) {
          String shortFile = requestedFiles.take();
          // if file was successfully downloaded before, skip it.
          if( completedFiles.contains(shortFile) ) {
            System.err.println("Skipping file '" + shortFile + "' due to previous successful download");
            continue;
          }
          String fileToRequest = rootFolder + "\\" + shortFile;
          clientPanel.setProgress(shortFile, 0.01f);
          out.println(fileToRequest);
          clientPanel.setProgress(shortFile, 0.02f);
          String newFileName = selectedFile.getAbsolutePath() + "\\" + shortFile;
          System.err.println("Requested file " + fileToRequest + " -> " + newFileName);
          
          boolean success;
          if( SEND_CHUNKS ) {
            success = receiveFileChunks(shortFile, newFileName, selectedFile, in);
          }
          else {
            success = receiveFile(shortFile, newFileName, selectedFile, in);
          }
          if( !success ) {
            break;
          }
          clientPanel.setProgress(shortFile, 0.0f);
          clientPanel.finished(shortFile);
          completedFiles.add(shortFile);
        }
      } catch (IOException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch(NumberFormatException e) {
        e.printStackTrace();
        System.err.println("Failed to parse number of available files");
      }
      clientPanel.disconnected();
      infoPanel.connection = "";
      infoPanel.receiveThreadRunning = false;
    }, "Receive Thread");
    receiveThread.start();
  }
  
  /**
   * Converts the line to bytes and sends to the output stream.
   */
  private void sendLine(String line, OutputStream out) throws IOException {
    char[] chars = line.toCharArray();
    byte[] bytes = new byte[chars.length + 1];
    for( int j = 0; j < chars.length; j++ ) {
      bytes[j] = (byte) chars[j];
    }
    bytes[bytes.length-1] = '\n';
    out.write(bytes);
  }
  
  /**
   * Reads up to 2048 characters or until a newline '\n' and returns it as a String
   */
  private String readLine(InputStream in) throws IOException {
    byte[] buffer = new byte[2048];
    int read = 0;
    while( read < buffer.length ) {
      read += in.read(buffer, read, 1);
      if( buffer[read-1] == '\n' ) {
        read--;
        break;
      }
    }
    char[] chars = new char[read];
    for( int i = 0; i < chars.length; i++ ) {
      chars[i] = (char) buffer[i];
    }
    return new String(chars);
  }
  
  private boolean setupSendFiles() {
    selected = chooseDirectory(frame);
    if( selected == null ) {
      return false;
    }
    files = getAllAvailableFilesInDirectory(selected);
    infoPanel.totalFiles = files.size();
    for( File f : files ) {
      infoPanel.addFile(f.getAbsolutePath());
    }
    return true;
  }
  
  /**
   * Prompts the user to choose a directory on the file system
   */
  public static File chooseDirectory(Component parent) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setCurrentDirectory(new java.io.File("."));
    chooser.setDialogTitle("Choose directory");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      return chooser.getSelectedFile();
    } else {
      return null;
    }
  }
  
  /**
   * Returns list of all files within specified directory.
   */
  private List<File> getAllAvailableFilesInDirectory(File directory) {
    List<File> result = new LinkedList<File>();
    List<File> check = new LinkedList<File>();
    check.add(directory);
    while( !check.isEmpty() ) {
      File c = check.remove(check.size() - 1);
      if( c.isDirectory() ) {
        File[] files = c.listFiles();
        for(File f : files) {
          check.add(f);
        }
      }
      else {
        result.add(c);
      }
    }
    return result;
  }

  public static void main(String[] args) {
    new Server();
  }

}
