package cam.listener;

import config.ConfigManager;
import network.user.UserInfo;
import network.user.register.channel.RegisterClientNettyChannel;
import service.AppInstance;
import service.ServiceManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UnregisterButtonListener implements ActionListener {

    private int number = 1;

    @Override
    public void actionPerformed(ActionEvent e) {
        UserInfo userInfo = ServiceManager.getInstance().getDashManager().getMyUserInfo();

        // Send UnRegister
        RegisterClientNettyChannel rtspRegisterNettyChannel = userInfo.getRegisterClientChannel();
        if (rtspRegisterNettyChannel == null) {
            return;
        }

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        rtspRegisterNettyChannel.sendUnRegister(
                userInfo.getUserId(),
                configManager.getRegisterTargetIp(),
                configManager.getRegisterTargetPort()
        );
    }
}
