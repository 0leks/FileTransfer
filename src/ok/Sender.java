package ok;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.sql.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.List;

import javax.swing.*;



public class Sender {
	
	private class ToSend implements Comparable {
		String originalFolder;
		String filepath;
		File file;
		long filesize;
		public ToSend(String originalFolder, String filepath, File file) {
			this.originalFolder = originalFolder;
			this.filepath = filepath;
			this.file = file;
			filesize = file.length();
		}
		@Override
		public String toString() {
			return originalFolder + "/" + filepath;
		}
		@Override
		public int compareTo(Object o) {
			if(o instanceof ToSend) {
				if(filesize > ((ToSend)o).filesize) {
					return -1;
				}
				else if(filesize < ((ToSend)o).filesize) {
					return 1;
				}
				else {
					return 0;
				}
			}
			return 0;
		}
	}
	class ToSendComparator implements Comparator<ToSend> { 
	    public int compare(ToSend one, ToSend two) 
	    { 
	        return two.compareTo(one); 
	    } 
	}
	
	private PriorityBlockingQueue<ToSend> filesToSend = new PriorityBlockingQueue<ToSend>(1, new ToSendComparator());
	
	private JPanel panel;
	private JLabel message;
	private JTextArea textArea;
	public Sender(JPanel panel) {
		this.panel = panel;
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BorderLayout());
		panel.setBackground(Color.white);
		message = new JLabel(); 
		topPanel.add(message, BorderLayout.NORTH);

		JButton sendButton = new JButton("Choose Files");
		textArea = new JTextArea();
		
		sendButton.addActionListener(e -> {
			File chosen = Utils.chooseFile(panel);
			if(chosen.isDirectory()) {
				String originalPath = chosen.getAbsolutePath();
				List<File> files = Utils.getAllAvailableFilesInDirectory(chosen);
				for(File f : files) {
					filesToSend.put(new ToSend(originalPath, f.getAbsolutePath().substring(originalPath.length()), f));
				}
			}
			else {
				filesToSend.put(new ToSend(chosen.getParent(), chosen.getName(), chosen));
			}
			refreshFileList();
		});
		JScrollPane scroll = new JScrollPane (textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		panel.setLayout(new BorderLayout());
		topPanel.add(sendButton, BorderLayout.CENTER);
		panel.add(topPanel, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		panel.revalidate();
		
	}
	
	public Socket findReceiver() throws UnknownHostException, IOException {
		while(true) {
			String[] potential = Utils.getLocalAddresses();
			for(String address : potential) {
				message.setText("Trying " + address);
				System.out.println("Trying " + address);
				Socket socket = null;
				try {
					socket = new Socket();
					socket.setSoTimeout(200);
					socket.connect(new InetSocketAddress(address, Driver.PORT_NUMBER), 1000);
					return socket;
				}
				catch(SocketTimeoutException e) {
					System.out.println("Rejected by " + address);
					if(socket != null) {
						try {
							socket.close();
						} catch (IOException ee) {
							ee.printStackTrace();
						}
					}
				}
			}
		}
	}
	
	public void refreshFileList() {
		String fileList = "";
		for(ToSend f : filesToSend) {
			fileList = fileList + f + "\n";
		}
		textArea.setText(fileList);
		textArea.repaint();
	}

	public void setupSending() {
		Thread thread = new Thread(() -> {
			message.setText("Searching for receiver."); 
			try (Socket socket = findReceiver();){
				if(socket == null) {
					message.setText("Could not connect to receiver.");
					return;
				}
				message.setText("Connected to receiver at " + socket.getInetAddress().getHostAddress());
				try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
						InputStream in = socket.getInputStream();) {
					
					while(true) {
						ToSend tosend = filesToSend.take();
						File tosendFile = new File(tosend.toString());
						sendFileChunks(tosend.filepath, tosendFile, socket.getOutputStream());
						refreshFileList();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		});
		thread.start();
	}

	private void sendFileChunks(String filename, File file, OutputStream out) throws IOException {
		sendLine(filename, out);
		FileInputStream fileStream = null;
		try {
			long fileLength = file.length();
			byte[] totalLength = ByteBuffer.allocate(8).putLong(fileLength).array();
			out.write(totalLength);
			fileStream = new FileInputStream(file);
			while (true) {
				byte[] buffer = new byte[Driver.CHUNK_SIZE];
				int numRead = fileStream.read(buffer);
				System.err.println("Read " + numRead + " bytes from file " + file.getAbsolutePath());
				if (numRead == 0 || numRead == -1) {
					break;
				} else {
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

	/**
	 * Converts the line to bytes and sends to the output stream.
	 */
	private void sendLine(String line, OutputStream out) throws IOException {
		char[] chars = line.toCharArray();
		byte[] bytes = new byte[chars.length + 1];
		for (int j = 0; j < chars.length; j++) {
			bytes[j] = (byte) chars[j];
		}
		bytes[bytes.length - 1] = '\n';
		out.write(bytes);
	}
}
