package dash.client.handler;

import config.ConfigManager;
import dash.client.DashClient;
import dash.server.network.DashLocalAddressManager;
import dash.server.network.DashLocalNetworkInfo;
import io.netty.handler.codec.http.*;
import network.definition.DestinationRecord;
import network.definition.NetAddress;
import network.socket.GroupSocket;
import network.socket.netty.NettyChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;

import java.net.URI;
import java.util.UUID;

public class DashHttpMessageSender {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageSender.class);

    public static final String HTTP_PREFIX = "http";

    private final String dashUnitId;
    private final String socketSessionId;
    private String host = null;

    private int localMpdNetworkInfoIndex = -1;
    private int localAudioNetworkInfoIndex = -1;
    private int localVideoNetworkInfoIndex = -1;

    private final ConfigManager configManager;
    private final DashLocalAddressManager dashLocalAddressManager;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageSender(String dashUnitId) {
        this.dashUnitId = dashUnitId;
        this.socketSessionId = UUID.randomUUID().toString();
        this.configManager = AppInstance.getInstance().getConfigManager();
        this.dashLocalAddressManager = ServiceManager.getInstance().getDashServer().getDashLocalAddressManager();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean start(DashClient dashClient, NetAddress targetAddress) {
        localMpdNetworkInfoIndex = dashLocalAddressManager.addTargetToMpdSocket(dashClient, targetAddress, socketSessionId);
        if (localMpdNetworkInfoIndex < 0) { return false; }

        localAudioNetworkInfoIndex = dashLocalAddressManager.addTargetToAudioSocket(dashClient, targetAddress, socketSessionId);
        if (localAudioNetworkInfoIndex < 0) { return false; }

        if (!configManager.isAudioOnly()) {
            localVideoNetworkInfoIndex = dashLocalAddressManager.addTargetToVideoSocket(dashClient, targetAddress, socketSessionId);
            return localVideoNetworkInfoIndex >= 0;
        }

        return true;
    }

    public void stop() {
        if (!dashLocalAddressManager.deleteTargetFromMpdSocket(localMpdNetworkInfoIndex, socketSessionId)) {
            logger.warn("[DashHttpMessageSender({})] Fail to delete the target from mpd socket.", dashUnitId);
        }

        if (!dashLocalAddressManager.deleteTargetFromAudioSocket(localAudioNetworkInfoIndex, socketSessionId)) {
            logger.warn("[DashHttpMessageSender({})] Fail to delete the target from audio socket.", dashUnitId);
        }

        if (!configManager.isAudioOnly()) {
            if (!dashLocalAddressManager.deleteTargetFromVideoSocket(localVideoNetworkInfoIndex, socketSessionId)) {
                logger.warn("[DashHttpMessageSender({})] Fail to delete the target from video socket.", dashUnitId);
            }
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpRequest makeHttpGetRequestMessage(String path) {
        URI uri = makeUri(path);
        if (uri == null) { return null; }

        HttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                uri.getRawPath()
        );

        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        request.headers().set(HttpHeaderNames.USER_AGENT, configManager.getServiceName());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "0");

        return request;
    }

    public void sendMessageForMpd(HttpRequest httpRequest) {
        if (httpRequest == null) { return; }

        DashLocalNetworkInfo mpdNetworkInfo = dashLocalAddressManager.getMpdNetworkInfo(localMpdNetworkInfoIndex);
        if (mpdNetworkInfo == null) { return; }

        sendMessage(mpdNetworkInfo.getLocalGroupSocket(), httpRequest);
    }

    public void sendMessageForAudio(HttpRequest httpRequest) {
        if (httpRequest == null) { return; }

        DashLocalNetworkInfo audioNetworkInfo = dashLocalAddressManager.getAudioNetworkInfo(localAudioNetworkInfoIndex);
        if (audioNetworkInfo == null) { return; }

        sendMessage(audioNetworkInfo.getLocalGroupSocket(), httpRequest);
    }

    public void sendMessageForVideo(HttpRequest httpRequest) {
        if (httpRequest == null) { return; }

        DashLocalNetworkInfo videoNetworkInfo = dashLocalAddressManager.getVideoNetworkInfo(localVideoNetworkInfoIndex);
        if (videoNetworkInfo == null) { return; }

        sendMessage(videoNetworkInfo.getLocalGroupSocket(), httpRequest);
    }

    public void sendMessage(GroupSocket groupSocket, HttpRequest httpRequest) {
        if (groupSocket == null) { return; }

        DestinationRecord destinationRecord = groupSocket.getDestination(socketSessionId);
        if (destinationRecord == null) { return; }

        NettyChannel nettyChannel = destinationRecord.getNettyChannel();
        if (nettyChannel != null) {
            nettyChannel.sendHttpRequest(httpRequest);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public URI makeUri(String path) {
        URI uri;
        try {
            uri = new URI(path);
            String scheme = uri.getScheme() == null ? HTTP_PREFIX : uri.getScheme();
            host = uri.getHost() == null ? configManager.getHttpListenIp() : uri.getHost();

            // 아직 https 지원하지 않음
            if (!HTTP_PREFIX.equalsIgnoreCase(scheme)) { // && !"https".equalsIgnoreCase(scheme)) {
                logger.warn("[DashHttpMessageSender({})] Only HTTP(S) is supported.", dashUnitId);
                return null;
            }
        } catch (Exception e) {
            logger.warn("[DashHttpMessageSender({})] URI Parsing error", dashUnitId, e);
            return null;
        }

        return uri;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    ////////////////////////////////////////////////////////////

}
