package cam.panel;

import cam.base.ButtonType;
import cam.listener.PlaylistAddButtonListener;
import cam.util.TextFieldLimit;
import service.AppInstance;

import javax.swing.*;
import java.awt.*;

public class UriPanel extends JPanel {

    private static final JLabel URI_LABEL = new JLabel("  URI : ");

    private final JTextField uriTextField = new JTextField(1);
    private final JButton addPlaylistButton = new JButton(ButtonType.ADD);

    public UriPanel() {
        this.setLayout(new BorderLayout());

        initTextField();
        initButton();

        this.add(URI_LABEL, BorderLayout.WEST);
        this.add(uriTextField, BorderLayout.CENTER);
        this.add(addPlaylistButton, BorderLayout.EAST);
    }

    private void initTextField() {
        uriTextField.setDocument(new TextFieldLimit(AppInstance.getInstance().getConfigManager().getUriLimit()));
    }

    private void initButton() {
        addPlaylistButton.addActionListener(new PlaylistAddButtonListener());
    }

    public JTextField getUriTextField() {
        return uriTextField;
    }
}
