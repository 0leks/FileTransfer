package ok;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.concurrent.*;

import javax.swing.*;

import ok.Sender.*;

public class Receiver {

	private LinkedBlockingQueue<Socket> connections = new LinkedBlockingQueue<Socket>();
	private ConcurrentLinkedQueue<String> receivedFiles = new ConcurrentLinkedQueue<>();
	
	private JPanel panel;
	private JLabel message;
	private JTextArea textArea;
	
	private int numUnknown = 0;
	
	public Receiver(JPanel panel) {
		this.panel = panel;
		panel.setBackground(Color.white);
		message = new JLabel(); 
		textArea = new JTextArea ();
		JScrollPane scroll = new JScrollPane (textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		panel.setLayout(new BorderLayout());
		panel.add(message, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		panel.revalidate();
	}

	public void setupReceiving() {
		Thread serverThread = new Thread(() -> {
			ServerSocket serverSocket;
			try {
				serverSocket = new ServerSocket(Driver.PORT_NUMBER);
				Socket clientSocket;
				while (true) {
					System.err.println("Waiting for connection");
					message.setText("Waiting for connection");
					clientSocket = serverSocket.accept();
					System.err.println("accepted connection from " + clientSocket.getInetAddress());
					message.setText("Connected to sender at " + clientSocket.getInetAddress().getHostAddress());
					message.repaint();
					panel.repaint();
					panel.revalidate();
					System.err.println("accepted connection from " + clientSocket.getInetAddress());
					connections.add(clientSocket);
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}, "Server Thread");
		Thread connectionHandler = new Thread(() -> {
			while (true) {
				try (Socket socket = connections.take();){
					message.setText("Connected to sender " + socket.getInetAddress().getHostAddress());
					message.repaint();
					panel.repaint();
					panel.revalidate();
					
					OutputStream out = socket.getOutputStream();
					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					
					while(true) {
						receiveFileChunks(socket.getInputStream());
					}
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}, "Connection Handler Thread");
		serverThread.start();
		connectionHandler.start();
	}
	
	public void refreshFileList() {
		String fileList = "";
		for(String f : receivedFiles) {
			fileList = fileList + f + "\n";
		}
		textArea.setText(fileList);
		textArea.repaint();
	}
	
	private boolean receiveFileChunks(InputStream in) throws IOException {
		String filename = readLine(in);
		String newFileName = System.getProperty("user.dir") + "/RECEIVE/" + filename;
		System.err.println(newFileName);
		File targetFile = null;
		FileOutputStream fos = null;
		try {
			targetFile = new File(newFileName);
			try {
				targetFile.getParentFile().mkdirs();
				fos = new FileOutputStream(targetFile);
			} catch (Exception e) {
				newFileName = "unknown" + numUnknown++;
				targetFile = new File(newFileName);
				targetFile.getParentFile().mkdirs();
				fos = new FileOutputStream(targetFile);
			}
			long totalNumRead = 0;
			// get 4 byte length of the file
			byte[] totalSize = new byte[8];
			int totalNum = in.read(totalSize, 0, 8);
			if (totalNum == -1) {
				fos.close();
				return false;
			}
			long totalLength = ByteBuffer.wrap(totalSize).getLong();
			System.err.println("Read " + totalNum + " totalSize bytes total length of file:" + totalLength);

			while (true) {
				// get 4 byte length of the file
				byte[] size = new byte[4];
				int num = in.read(size, 0, 4);
				System.err.println("Read " + num + " chunkSize bytes");
				if (num == -1) {
					fos.close();
					return false;
				}
				int length = ByteBuffer.wrap(size).getInt();
				System.err.println("Expecting size " + length + " chunk ");

				if (length == 0 || length == -1) {
					break;
				}

				byte[] buffer = new byte[Driver.CHUNK_SIZE];

				int numRead = readBytes(buffer, in, length);
				totalNumRead += numRead;
				//clientPanel.setProgress(shortFile, 1.0f * totalNumRead / totalLength);
				System.err.println("Read size " + numRead + " chunk");
				fos.write(buffer, 0, numRead);
				if (length < Driver.CHUNK_SIZE) {
					break;
				}
			}

			fos.close();
			receivedFiles.add(filename);
			refreshFileList();
			return true;

		} catch (Exception e) {
			if (fos != null) {
				fos.close();
			}
			// if error while writing file, delete it since it will be corrupted
			if (targetFile != null) {
				if (targetFile.exists()) {
					System.err.println("Deleting corrupted file: " + targetFile.getAbsolutePath());
					targetFile.delete();
				}
			}
			throw e;
		}
	}

	private int readBytes(byte[] bytes, InputStream in, int numToRead) throws IOException {
		int marker = 0;
		int numRead = 0;
		int readSize = 1024;
		while (numRead < numToRead - readSize) {
			numRead += in.read(bytes, marker, readSize);
			marker = numRead;
			// clientPanel.setProgress(shortFile, 1.0f*numRead / length);
		}
		numRead += in.read(bytes, marker, numToRead - numRead);
		return numRead;
	}

	/**
	 * Reads up to 2048 characters or until a newline '\n' and returns it as a
	 * String
	 */
	private String readLine(InputStream in) throws IOException {
		byte[] buffer = new byte[2048];
		int read = 0;
		while (read < buffer.length) {
			read += in.read(buffer, read, 1);
			if (buffer[read - 1] == '\n') {
				read--;
				break;
			}
		}
		char[] chars = new char[read];
		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) buffer[i];
		}
		return new String(chars);
	}
}
