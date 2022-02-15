package cam.listener;

import config.ConfigManager;
import network.user.UserInfo;
import network.user.register.channel.RegisterClientNettyChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RegisterButtonListener implements ActionListener {

    private static final Logger log = LoggerFactory.getLogger(RegisterButtonListener.class);

    @Override
    public void actionPerformed(ActionEvent e) {
        UserInfo userInfo = ServiceManager.getInstance().getDashManager().getMyUserInfo();

        // Send Register
        RegisterClientNettyChannel registerClientChannel = userInfo.getRegisterClientChannel();
        if (registerClientChannel == null) {
            return;
        }

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        registerClientChannel.sendRegister(
                userInfo.getUserId(),
                configManager.getRegisterTargetIp(),
                configManager.getRegisterTargetPort(),
                null
        );
    }
}
