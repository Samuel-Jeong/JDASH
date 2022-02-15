package network.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class UserManager {

    private static final Logger logger = LoggerFactory.getLogger(UserManager.class);

    private static final String USER_REGISTER_PRIVATE_KEY = "dash1234";

    ////////////////////////////////////////////////////////////////////////////////
    private final HashMap<String, UserInfo> userInfoMap = new HashMap<>();
    private final ReentrantLock userInfoMapLock = new ReentrantLock();
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public UserManager() {}
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public int getCallMapSize() {
        try {
            userInfoMapLock.lock();

            return userInfoMap.size();
        } catch (Exception e) {
            logger.warn("[CallManager] Fail to get the user map size.", e);
            return 0;
        } finally {
            userInfoMapLock.unlock();
        }
    }

    public UserInfo addUserInfo(String userId) {
        if (userId == null) {
            logger.warn("[CallManager] Fail to add the user info. User id is null.");
            return null;
        }

        try {
            userInfoMapLock.lock();

            userInfoMap.putIfAbsent(userId, new UserInfo(userId));
            return userInfoMap.get(userId);
        } catch (Exception e) {
            logger.warn("[CallManager] Fail to add the user info[userId={}].", userId, e);
            return null;
        } finally {
            userInfoMapLock.unlock();
        }
    }

    public UserInfo deleteUserInfo(String userId) {
        if (userId == null) { return null; }

        try {
            userInfoMapLock.lock();

            return userInfoMap.remove(userId);
        } catch (Exception e) {
            logger.warn("[CallManager] Fail to delete the user info[userId={}].", userId, e);
            return null;
        } finally {
            userInfoMapLock.unlock();
        }
    }

    public Map<String, UserInfo> getCloneCallMap( ) {
        HashMap<String, UserInfo> cloneMap;

        try {
            userInfoMapLock.lock();

            cloneMap = (HashMap<String, UserInfo>) userInfoMap.clone();
        } catch (Exception e) {
            logger.warn("[CallManager] Fail to clone the user map.", e);
            cloneMap = userInfoMap;
        } finally {
            userInfoMapLock.unlock();
        }

        return cloneMap;
    }

    public UserInfo getUserInfo(String userId) {
        if (userId == null) { return null; }

        try {
            userInfoMapLock.lock();

            return userInfoMap.get(userId);
        } catch (Exception e) {
            logger.warn("[CallManager] Fail to get the user info[userId={}].", userId, e);
            return null;
        } finally {
            userInfoMapLock.unlock();
        }
    }

    public static String getHashKey() {
        return USER_REGISTER_PRIVATE_KEY;
    }

    public boolean validateRegistration() {
        return false;
    }

    public void clearUserInfoMap() {
        try {
            userInfoMapLock.lock();

            userInfoMap.clear();
            logger.debug("Success to clear the user map.");
        } catch (Exception e) {
            logger.warn("[CallManager] Fail to clear the user map.", e);
        } finally {
            userInfoMapLock.unlock();
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

}
