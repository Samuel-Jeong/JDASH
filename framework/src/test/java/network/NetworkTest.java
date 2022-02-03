package network;

import instance.BaseEnvironment;
import instance.DebugLevel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import network.handler.ClientHandler;
import network.handler.ServerHandler;
import network.socket.GroupSocket;
import network.definition.NetAddress;
import network.socket.SocketManager;
import network.socket.SocketProtocol;
import org.junit.Assert;
import org.junit.Test;
import service.ResourceManager;
import service.scheduler.schedule.ScheduleManager;

public class NetworkTest {

    @Test
    public void test() {
        //socketNormalTest();
        tcpSocketTest();
    }

    public void socketNormalTest() {
        // 인스턴스 생성
        BaseEnvironment baseEnvironment = new BaseEnvironment(
                new ScheduleManager(),
                new ResourceManager(5000, 7000),
                DebugLevel.DEBUG
        );

        // SocketManager 생성
        SocketManager socketManager = new SocketManager(
                baseEnvironment,
                false,
                false,
                10,
                500000,
                500000
        );

        // NetAddress 생성
        NetAddress netAddress1 = new NetAddress("127.0.0.1", 5000,true, SocketProtocol.UDP);
        NetAddress netAddress2 = new NetAddress("127.0.0.1", 6000,true, SocketProtocol.UDP);
        //

        // Netty Channel Initializer 생성
        ChannelInitializer<NioDatagramChannel> clientChannelInitializer = new ChannelInitializer<NioDatagramChannel>() {
            @Override
            protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                final ChannelPipeline channelPipeline = nioDatagramChannel.pipeline();
                channelPipeline.addLast(new ClientHandler());
            }
        };

        ChannelInitializer<NioDatagramChannel> serverChannelInitializer = new ChannelInitializer<NioDatagramChannel>() {
            @Override
            protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                final ChannelPipeline channelPipeline = nioDatagramChannel.pipeline();
                channelPipeline.addLast(new ServerHandler());
            }
        };
        //

        // 소켓 생성 (GroupSocket)
        Assert.assertTrue(socketManager.addSocket(netAddress1, serverChannelInitializer));
        GroupSocket groupSocket1 = socketManager.getSocket(netAddress1);
        Assert.assertNotNull(groupSocket1);
        // Listen channel open
        Assert.assertTrue(groupSocket1.getListenSocket().openListenChannel());
        //

        // 소켓 생성 (GroupSocket)
        Assert.assertTrue(socketManager.addSocket(netAddress2, serverChannelInitializer));
        GroupSocket groupSocket2 = socketManager.getSocket(netAddress2);
        Assert.assertNotNull(groupSocket2);
        Assert.assertTrue(groupSocket2.getListenSocket().openListenChannel());
        //

        // Destination 추가
        Assert.assertTrue(groupSocket1.addDestination(netAddress2, null, 1234, clientChannelInitializer));
        baseEnvironment.printMsg("GROUP-SOCKET1: {%s}", groupSocket1.toString());
        baseEnvironment.printMsg("GROUP-SOCKET2: {%s}", groupSocket2.toString());
        //

        // 소켓 삭제
        Assert.assertTrue(socketManager.removeSocket(netAddress1));
        Assert.assertTrue(socketManager.removeSocket(netAddress2));
        //

        // 인스턴스 삭제
        baseEnvironment.stop();
    }

    public void tcpSocketTest() {
        // 인스턴스 생성
        BaseEnvironment baseEnvironment = new BaseEnvironment(
                new ScheduleManager(),
                new ResourceManager(5000, 7000),
                DebugLevel.DEBUG
        );

        // SocketManager 생성
        SocketManager socketManager = new SocketManager(
                baseEnvironment,
                false,
                false,
                10,
                0,
                500000
        );

        // NetAddress 생성
        NetAddress netAddress1 = new NetAddress("127.0.0.1", 5000,true, SocketProtocol.TCP);

        // Netty Channel Initializer 생성
        ChannelInitializer<NioSocketChannel> serverChannelInitializer = new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel nioDatagramChannel) {
                final ChannelPipeline channelPipeline = nioDatagramChannel.pipeline();
                channelPipeline.addLast(new ServerHandler());
            }
        };
        //

        // 소켓 생성 (GroupSocket)
        Assert.assertTrue(socketManager.addSocket(netAddress1, serverChannelInitializer));
        GroupSocket groupSocket1 = socketManager.getSocket(netAddress1);
        Assert.assertNotNull(groupSocket1);
        // Listen channel open
        Assert.assertTrue(groupSocket1.getListenSocket().openListenChannel());
        baseEnvironment.printMsg("GROUP-SOCKET1: {%s}", groupSocket1.toString());
        //
    }

}
