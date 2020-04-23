import java.awt.*;

import javax.swing.*;

public class DownloadCellRenderer implements ListCellRenderer<Object> {
  
  private static final Stroke mainStroke = new BasicStroke(3);
  public boolean[] finished;
  public float[] progress;
  public DownloadCellRenderer(int numElements) {
    finished = new boolean[numElements];
    progress = new float[numElements];
  }

  public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
      boolean cellHasFocus) {
    
    JLabel r = new JLabel() {
      @Override
      public void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.green);
        g.fillRect(0, 0, (int) (getWidth()*progress[index]), getHeight());
        if( finished[index] ) {
          g.setColor(Color.red);
          ((Graphics2D)g).setStroke(mainStroke);
          int x1 = (int)(getWidth()*0.01);
          int x2 = (int)(getWidth()*0.02);
          int x3 = (int)(getWidth()*0.04);
//          int x1 = (int)(getWidth()*0.96);
//          int x2 = (int)(getWidth()*0.97);
//          int x3 = (int)(getWidth()*0.99);
          int y1 = (int)(getHeight()*0.03);
          int y2 = (int)(getHeight()*0.5);
          int y3 = (int)(getHeight()*0.97);
          g.drawLine(x1, y2, x2, y3);
          g.drawLine(x2, y3, x3, y1);
        }
        super.paintComponent(g);
      }
    };
    r.setOpaque(false);
    r.setText(value.toString());

    Color background;
    Color foreground;

    // check if this cell represents the current DnD drop location
    JList.DropLocation dropLocation = list.getDropLocation();
    if (dropLocation != null && !dropLocation.isInsert() && dropLocation.getIndex() == index) {

      background = Color.BLUE;
      foreground = Color.WHITE;

      // check if this cell is selected
    } else if (isSelected) {
      background = Color.YELLOW;
      foreground = Color.BLACK;

      // unselected, and not the DnD drop location
    } else {
      background = Color.WHITE;
      foreground = Color.BLACK;
    }

    r.setBackground(background);
    r.setForeground(foreground);
    return r;
  }
}