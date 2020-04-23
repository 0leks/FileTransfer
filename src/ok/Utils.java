package ok;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import javax.swing.*;

public class Utils {

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
	 * Prompts the user to choose a file on the file system
	 */
	public static File chooseFile(Component parent) {
		final JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(new java.io.File("."));
		chooser.setDialogTitle("Choose file or directory");
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		//chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		} else {
			return null;
		}
	}

	/**
	 * Returns list of all files within specified directory.
	 */
	public static List<File> getAllAvailableFilesInDirectory(File directory) {
		List<File> result = new LinkedList<File>();
		List<File> check = new LinkedList<File>();
		check.add(directory);
		while (!check.isEmpty()) {
			File c = check.remove(check.size() - 1);
			if (c.isDirectory()) {
				File[] files = c.listFiles();
				for (File f : files) {
					check.add(f);
				}
			} else {
				result.add(c);
			}
		}
		return result;
	}
	
	public static String getMyAddress() {
		InetAddress local;
		try {
			local = InetAddress.getLocalHost();
			return local.getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return "ERROR";
	}

	public static String[] getLocalAddresses() {
		String address = getMyAddress();
		String[] splits = address.split("\\.");
		String base = "";
		for(int i = 0; i < splits.length-1; i++) {
			base += splits[i] + ".";
		}
		System.out.println("Searching on subnet: " + base);
		ConcurrentLinkedQueue<InetAddress> addresses = new ConcurrentLinkedQueue<>();
		Thread[] threads = new Thread[256];
		for(int i = 0; i < 256; i++) {
			String query = base + i;
			threads[i] = new Thread(() -> {
				try {
					InetAddress in = InetAddress.getByName(query);
					if(in.isReachable(100)) {
						addresses.add(in);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			threads[i].start();
		}
		for(int i = 0; i < 256; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		String[] result = new String[addresses.size()];
		int index = 0;
		for(InetAddress in : addresses) {
			System.out.println("Found host at: " + in.getHostAddress());
			//if(!in.getHostAddress().equals(address)) {
				result[index++] = in.getHostAddress();
			//}
		}
		return result;
	}
}
