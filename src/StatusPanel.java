
import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

public class StatusPanel extends JPanel {
  
  private static final Font mainFont = new Font("Times", Font.PLAIN, 12);
  
  public int totalFiles = 0;
  public int currentFiles = 0;
  public String connection = "";
  
  public volatile boolean sendThreadRunning;
  public volatile boolean receiveThreadRunning;
  
  private JTextArea fileNames;
  
  private List<String> files = new LinkedList<String>();
  private String fileText;
  
  private JPanel drawPanel;
  
  public StatusPanel() {
    
    this.setLayout(new BorderLayout());
    
    drawPanel = new JPanel() {
      @Override
      public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setFont(mainFont);
        g.setColor(Color.BLACK);
        int y = 0;
        g.drawString("Total files: " + totalFiles, 1, y+=mainFont.getSize());
        g.drawString("Current files: " + currentFiles, 1, y+=mainFont.getSize());
        g.drawString("Send Thread Running: " + sendThreadRunning, 1, y+=mainFont.getSize());
        g.drawString("Receive Thread Running: " + receiveThreadRunning, 1, y+=mainFont.getSize());
        g.drawString("Connected to: " + connection, 1, y+=mainFont.getSize());
      }
    };
    add(drawPanel, BorderLayout.CENTER);
    fileNames = new JTextArea(16, 5);
    fileNames.setEditable(false);
    JScrollPane scrollable = new JScrollPane(fileNames);
    add(scrollable, BorderLayout.SOUTH);
    scrollable.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
  }
  
  public void addFile(String text) {
    files.add(text);
    
    if( fileText != null ) {
      fileText += "\n" + text;
    }
    else {
      fileText = text;
    }
    fileNames.setText(fileText);
  }
  public void clearFiles() {
    files.clear();
    fileText = null;
    fileNames.setText("");
  }
  
}
