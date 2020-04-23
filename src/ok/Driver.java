package ok;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

import javax.swing.*;

public class Driver {
	public static final int PORT_NUMBER = 33456;
	public static final int CHUNK_SIZE = 1024*1024*64; // 64MB
	
	JFrame frame;
	JPanel contentPanel = new JPanel();
	
	Sender sender;
	Receiver receiver;
	
	public Driver() {
			
		frame = new JFrame("File Transfer at " + Utils.getMyAddress());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(500, 500);
		frame.setLocationRelativeTo(null);
		URL a = Utils.class.getClassLoader().getResource("ok/icon.png");
		if (a != null) {
			ImageIcon icon =  new ImageIcon(a);
			frame.setIconImage(icon.getImage());
		}

		JPanel buttonPanel = new JPanel();
		JButton debugButton = new JButton("Debug");
		debugButton.addActionListener(e -> {
			try {
				PrintStream fileErr = new PrintStream("./cerr.txt");
				System.setErr(fileErr);
				PrintStream fileOut = new PrintStream("./cout.txt");
				System.setOut(fileOut);
				buttonPanel.remove(debugButton);
				frame.revalidate();
				frame.repaint();
			} catch (FileNotFoundException ee) {
				ee.printStackTrace();
			}
		});
		
		JButton sendButton = new JButton("Send");
		sendButton.addActionListener(e -> {
			frame.remove(contentPanel);
			frame.remove(buttonPanel);
			contentPanel = new JPanel();
			contentPanel.setBackground(Color.black);
			frame.add(contentPanel, BorderLayout.CENTER);
			frame.revalidate();
			sender = new Sender(contentPanel);
			sender.setupSending();
			//frame.validate();
			frame.repaint();
		});
		JButton receiveButton = new JButton("Receive");
		receiveButton.addActionListener(e -> {
			frame.remove(contentPanel);
			frame.remove(buttonPanel);
			contentPanel = new JPanel();
			contentPanel.setBackground(Color.black);
			frame.add(contentPanel, BorderLayout.CENTER);
			frame.revalidate();
			receiver = new Receiver(contentPanel);
			receiver.setupReceiving();
		});
		

		buttonPanel.add(sendButton);
		buttonPanel.add(receiveButton);
		buttonPanel.add(debugButton);
		
		frame.add(buttonPanel, BorderLayout.NORTH);
		
		
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		new Driver();
	}

}
