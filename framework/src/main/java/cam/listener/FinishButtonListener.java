package cam.listener;

import service.ServiceManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FinishButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        ServiceManager.getInstance().stop();
        System.exit(1);
    }
}
