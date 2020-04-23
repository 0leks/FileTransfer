import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

public class ClientPanel extends JPanel {
  
  private JButton download;
  private JList<String> fileList;
  private ClientInterface clientInterface;
  private boolean[] finished;
  private float[] progress;
  private String[] fileNames;
  
  public ClientPanel(ClientInterface clientInterface) {
    this.clientInterface = clientInterface;
    fileList = new JList<String>();
    fileList.setLayoutOrientation(JList.VERTICAL);
    JScrollPane listScroller = new JScrollPane(fileList);
    listScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    JPanel buttonPanel = new JPanel();
    download = new JButton("Download Selected");
    download.addActionListener((e) -> {
      clientInterface.requestFiles(fileList.getSelectedValuesList());
    });
    buttonPanel.add(download);
    setLayout(new BorderLayout() );
    add(listScroller, BorderLayout.CENTER);
    add(buttonPanel, BorderLayout.SOUTH);
  }
  
  public void populateList(String[] files) {
    SwingUtilities.invokeLater(() -> {
      fileNames = files;
      DefaultListModel<String> model = new DefaultListModel<String>();
      for( int i = 0; i < files.length; i++ ) {
        model.addElement(files[i]);
      }
      fileList.setModel(model);
      DownloadCellRenderer renderer = new DownloadCellRenderer(files.length);
      fileList.setCellRenderer(renderer);
      finished = renderer.finished;
      progress = renderer.progress;
      validate();
    });
  }
  
  public void disconnected() {
    download.setText("Disconnected");
    download.setEnabled(false);
  }
  
  public void setProgress(String fileName, float p) {
    for( int i = 0; i < fileNames.length; i++ ) {
      if( fileNames[i].equals(fileName) ) {
        progress[i] = p;
      }
    }
  }
  public void finished(String fileName) {
    for( int i = 0; i < fileNames.length; i++ ) {
      if( fileNames[i].equals(fileName) ) {
        finished[i] = true;
      }
    }
  }
}
