package cam.module;

import config.ConfigManager;
import service.AppInstance;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;
import javax.swing.text.TextAction;
import javax.swing.undo.UndoManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.*;

public class TextEditor extends JFrame implements ActionListener {

    // Text component
    private final JTextArea editArea;

    // Frame
    private final JFrame frame;

    // Constructor
    public TextEditor() {
        // Create a frame
        frame = new JFrame("editor");

        try {
            // Set metal look and feel
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");

            // Set theme to ocean
            MetalLookAndFeel.setCurrentTheme(new OceanTheme());
        } catch (Exception e) {
            // ignore
        }

        // Text component
        editArea = new JTextArea();
        editArea.setEditable(true);

        // Create a menubar
        JMenuBar mb = new JMenuBar();

        // Create amenu for menu
        JMenu m1 = new JMenu("File");

        // Create menu items
        //JMenuItem mi1 = new JMenuItem("New");
        JMenuItem mi2 = new JMenuItem("Open");
        JMenuItem mi3 = new JMenuItem("Save");
        JMenuItem mi9 = new JMenuItem("Print");

        // Add action listener
        //mi1.addActionListener(this);
        mi2.addActionListener(this);
        mi3.addActionListener(this);
        mi9.addActionListener(this);

        //m1.add(mi1);
        m1.add(mi2);
        m1.add(mi3);
        m1.add(mi9);

        // Create amenu for menu
        //JMenu m2 = new JMenu("Edit");

        // Create menu items
        /*JMenuItem mi4 = new JMenuItem("cut");
        JMenuItem mi5 = new JMenuItem("copy");
        JMenuItem mi6 = new JMenuItem("paste");*/

        // Add action listener
        //mi4.addActionListener(this);
        //mi5.addActionListener(this);
        //mi6.addActionListener(this);

        //m2.add(mi4);
        //m2.add(mi5);
        //m2.add(mi6);

        JMenuItem mc = new JMenuItem("Close");
        mc.addActionListener(this);

        mb.add(m1);
        //mb.add(m2);
        mb.add(mc);

        //
        JPopupMenu popup = new JPopupMenu();
        UndoManager undoManager = new UndoManager();

        Action copyAction = new AbstractAction("Copy") { @Override public void actionPerformed(ActionEvent ae) { editArea.copy(); } };
        Action cutAction = new AbstractAction("Cut") { @Override public void actionPerformed(ActionEvent ae) { editArea.cut(); } };
        Action pasteAction = new AbstractAction("Paste") { @Override public void actionPerformed(ActionEvent ae) { editArea.paste(); } };
        Action selectAllAction = new AbstractAction("Select All") { @Override public void actionPerformed(ActionEvent ae) { editArea.selectAll(); } };

        editArea.getDocument().addUndoableEditListener(undoManager);
        editArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_MASK), "undo");
        editArea.getActionMap().put("undo", new TextAction("undo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            }
        });

        cutAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_MASK));
        copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_MASK));
        pasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_MASK));
        selectAllAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_MASK));

        popup.addSeparator();
        popup.add(cutAction);
        popup.add(copyAction);
        popup.add(pasteAction);
        popup.addSeparator();
        popup.add(selectAllAction);
        editArea.setComponentPopupMenu(popup);
        //

        frame.setJMenuBar(mb);

        JScrollPane scrollPane = new JScrollPane(editArea);
        frame.add(scrollPane);

        frame.setSize(500, 500);
    }

    public boolean isVisible() {
        return frame.isVisible();
    }

    public void start() {
        frame.setVisible(true);
    }

    public void stop() {
        frame.setVisible(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String s = e.getActionCommand();

        switch (s) {
            /*case "cut":
                editArea.cut();
                break;
            case "copy":
                editArea.copy();
                break;
            case "paste":
                editArea.paste();
                break;*/
            case "Save": {
                String configFilePath = AppInstance.getInstance().getConfigPath();
                if (configFilePath == null) { return; }

                File configFile = new File(configFilePath);
                if (!configFile.exists() || !configFile.isFile()) {
                    return;
                }

                // Create an object of JFileChooser class
                //JFileChooser j = new JFileChooser("f:");

                // Invoke the showsSaveDialog function to show the save dialog
                //int r = j.showSaveDialog(null);

                //if (r == JFileChooser.APPROVE_OPTION) {

                    // Set the label to the path of the selected directory
                    //File fi = new File(j.getSelectedFile().getAbsolutePath());

                    try {
                        // Write
                        String text = editArea.getText();
                        if (text.length() > 0) {
                            int result = JOptionPane.showConfirmDialog(null, "정말 수정하시겠습니까?", "Save", JOptionPane.YES_NO_OPTION);
                            //if (result == JOptionPane.CLOSED_OPTION || result == JOptionPane.NO_OPTION) {
                                //JOptionPane.showMessageDialog(frame, "수정 취소");
                            //} else if (result == JOptionPane.YES_OPTION) {
                            if (result == JOptionPane.YES_OPTION) {
                                // Create a file writer
                                //FileWriter wr = new FileWriter(fi, false);
                                FileWriter wr = new FileWriter(configFile, false);

                                // Create buffered writer to write
                                BufferedWriter w = new BufferedWriter(wr);
                                w.write(text);
                                w.flush();
                                w.close();

                                ConfigManager configManager = new ConfigManager(configFilePath);
                                AppInstance.getInstance().setConfigManager(configManager);
                                JOptionPane.showMessageDialog(frame, "수정 완료");
                            }
                        }
                    } catch (Exception evt) {
                        JOptionPane.showMessageDialog(frame, evt.getMessage());
                    }
                //}
                // If the user cancelled the operation
                //else
                    //JOptionPane.showMessageDialog(f, "the user cancelled the operation");
                break;
            }
            case "Print":
                try {
                    // print the file
                    editArea.print();
                } catch (Exception evt) {
                    JOptionPane.showMessageDialog(frame, evt.getMessage());
                }
                break;
            case "Open": {
                String configFilePath = AppInstance.getInstance().getConfigPath();
                if (configFilePath == null) { return; }

                File configFile = new File(configFilePath);
                if (!configFile.exists() || !configFile.isFile()) {
                    return;
                }

                // Create an object of JFileChooser class
                //JFileChooser j = new JFileChooser("f:");

                // Invoke the showsOpenDialog function to show the save dialog
                //int r = j.showOpenDialog(null);

                // If the user selects a file
                //if (r == JFileChooser.APPROVE_OPTION) {
                    // Set the label to the path of the selected directory
                    //File fi = new File(j.getSelectedFile().getAbsolutePath());

                String text = editArea.getText();
                if (text.length() > 0) {
                    int result = JOptionPane.showConfirmDialog(null, "정말 다시 로드하시겠습니까?", "Open", JOptionPane.YES_NO_OPTION);
                    if (result != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                try {
                    FileReader fr = new FileReader(configFile);
                    BufferedReader br = new BufferedReader(fr);
                    StringBuilder sl = new StringBuilder(br.readLine());

                    // Take the input from the file
                    String s1;
                    while ((s1 = br.readLine()) != null) {
                        sl.append("\n").append(s1);
                    }

                    editArea.setText(sl.toString());
                } catch (Exception evt) {
                    JOptionPane.showMessageDialog(frame, evt.getMessage());
                }
                //}
                // If the user cancelled the operation
                //else
                    //JOptionPane.showMessageDialog(f, "the user cancelled the operation");
                break;
            }
            /*case "New":
                t.setText("");
                break;*/
            case "Close":
                stop();
                break;
        }
    }
}