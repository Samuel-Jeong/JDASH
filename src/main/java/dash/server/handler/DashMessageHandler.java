package dash.server.handler;

import config.ConfigManager;
import dash.server.DashServer;
import dash.server.handler.definition.HttpMessageHandler;
import dash.server.handler.definition.HttpRequest;
import dash.server.handler.definition.HttpResponse;
import dash.unit.DashUnit;
import io.netty.channel.ChannelHandlerContext;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import stream.AudioService;
import stream.RemoteStreamService;
import util.module.FileManager;

import java.io.File;

public class DashMessageHandler implements HttpMessageHandler {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashMessageHandler.class);

    private final String uri;
    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public DashMessageHandler(String uri) {
        this.uri = uri;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public Object handle(HttpRequest request, HttpResponse response, String originUri, String uriFileName, ChannelHandlerContext ctx, DashUnit dashUnit) {
        DashServer dashServer = ServiceManager.getInstance().getDashServer();
        if (request == null || uriFileName == null || ctx == null) {
            return null;
        }

        ///////////////////////////
        // CHECK URI
        String result = null;
        String uri = request.getRequest().uri();
        if (!this.uri.equals(uri)) {
            logger.warn("[DashMessageHandler(uri={})] URI is not equal with handler's uri. (uri={})", this.uri, uri);
            return null;
        }

        String uriFileNameWithExtension = FileManager.getFileNameWithExtensionFromUri(uri);
        if (uriFileNameWithExtension.contains(".") && uriFileNameWithExtension.endsWith(".mp4")) {
            File uriFile = new File(uri);
            if (!uriFile.exists() || uriFile.isDirectory()) {
                logger.warn("[DashMessageHandler(uri={})] Fail to find the mp4 file. (uri={})", this.uri, uri);
                return null;
            }
        }
        ///////////////////////////

        ///////////////////////////
        // GENERATE MPD FROM MP4 BY GPAC
        String mp4Path; // Absolute path
        String mpdPath = null; // Absolute path
        try {
            ///////////////////////////
            // GET COMMAND & RUN SCRIPT
            if (uriFileNameWithExtension.contains(".")) {
                if (uri.endsWith(".mp4")) {
                    mp4Path = uri;
                    mpdPath = uri.replace(".mp4", ".mpd");
                } else if (uri.endsWith(".mpd")) {
                    mp4Path = uri.replace(".mpd", ".mp4");
                    mpdPath = uri;
                } else {
                    logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Wrong file extension. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                    return null;
                }

                File mpdFile = new File(mpdPath);
                if (!mpdFile.exists()) {
                    ///////////////////////////
                    FFmpegFrameRecorder audioFrameRecorder = null;
                    FFmpegFrameRecorder videoFrameRecorder = null;
                    try {
                        ///////////////////////////
                        FFmpegFrameGrabber fFmpegFrameGrabber = FFmpegFrameGrabber.createDefault(mp4Path);
                        if (!configManager.isAudioOnly()) {
                            fFmpegFrameGrabber.setImageWidth(RemoteStreamService.CAPTURE_WIDTH);
                            fFmpegFrameGrabber.setImageHeight(RemoteStreamService.CAPTURE_HEIGHT);
                        }
                        fFmpegFrameGrabber.start();
                        ///////////////////////////

                        /////////////////////////////////
                        // [OUTPUT] FFmpegFrameRecorder
                        if (configManager.isAudioOnly()) {
                            audioFrameRecorder = new FFmpegFrameRecorder(
                                    mpdPath,
                                    AudioService.CHANNEL_NUM
                            );
                            RemoteStreamService.setAudioOptions(audioFrameRecorder);
                            RemoteStreamService.setDashOptions(audioFrameRecorder,
                                    uriFileName,
                                    configManager.isAudioOnly(),
                                    configManager.getSegmentDuration(), 0
                            );
                            audioFrameRecorder.start();
                        } else {
                            videoFrameRecorder = new FFmpegFrameRecorder(
                                    mpdPath,
                                    RemoteStreamService.CAPTURE_WIDTH, RemoteStreamService.CAPTURE_HEIGHT,
                                    AudioService.CHANNEL_NUM
                            );
                            RemoteStreamService.setVideoOptions(videoFrameRecorder);
                            RemoteStreamService.setAudioOptions(videoFrameRecorder);
                            RemoteStreamService.setDashOptions(videoFrameRecorder,
                                    uriFileName,
                                    configManager.isAudioOnly(),
                                    configManager.getSegmentDuration(), 0
                            );
                            videoFrameRecorder.start();
                        }
                        /////////////////////////////////

                        /////////////////////////////////
                        long startTime = 0;
                        Frame capturedFrame;
                        while (true) {
                            //////////////////////////////////////
                            if (configManager.isAudioOnly()) {
                                capturedFrame = fFmpegFrameGrabber.grabSamples();
                            } else {
                                capturedFrame = fFmpegFrameGrabber.grab();
                            }
                            if(capturedFrame == null){ break; }
                            //////////////////////////////////////

                            //////////////////////////////////////
                            if (configManager.isAudioOnly() && audioFrameRecorder != null) {
                                // AUDIO DATA ONLY
                                if (capturedFrame.samples != null && capturedFrame.samples.length > 0) {
                                    audioFrameRecorder.record(capturedFrame);
                                }
                            } else if (videoFrameRecorder != null) {
                                //////////////////////////////////////
                                // Check for AV drift
                                if (startTime == 0) { startTime = System.currentTimeMillis(); }
                                long curTimeStamp = 1000 * (System.currentTimeMillis() - startTime);
                                if (curTimeStamp > videoFrameRecorder.getTimestamp()) { // Lip-flap correction
                                    videoFrameRecorder.setTimestamp(curTimeStamp);
                                }

                                videoFrameRecorder.record(capturedFrame);
                                //////////////////////////////////////
                            }
                            /////////////////////////////////////
                        }
                        /////////////////////////////////
                    } catch (Exception e) {
                        // ignore
                        //logger.warn("RemoteStreamService.run.Exception", e);
                    } finally {
                        try {
                            if (videoFrameRecorder != null) {
                                videoFrameRecorder.stop();
                                videoFrameRecorder.release();
                            }

                            if (audioFrameRecorder != null) {
                                audioFrameRecorder.stop();
                                audioFrameRecorder.release();
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    mpdFile = new File(mpdPath);
                    if (!mpdFile.exists()) {
                        logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MPD file is not exists. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                        return null;
                    }
                } /*else {
                    logger.debug("[DashMessageHandler(uri={})] The mpd file is already exist. (mpdPath={})", this.uri, mpdPath);
                }*/
            } else {
                mpdPath = FileManager.concatFilePath(uri, uriFileName + ".mpd");
            }

            ///////////////////////////
            // GET MPD
            if (!dashServer.getMpdManager().parseMpd(mpdPath)) {
                logger.warn("[DashMessageHandler(uri={})] Fail to parse the mpd. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                return null;
            }

            // VALIDATE MPD
            if (configManager.isEnableValidation() && dashServer.getMpdManager().validate()) {
                logger.debug("[DashMessageHandler(uri={})] Success to validate the mpd.", this.uri);
            } else {
                logger.warn("[DashMessageHandler(uri={})] Fail to validate the mpd.", this.uri);
                return null;
            }

            result = dashServer.getMpdManager().writeAsString();
            ///////////////////////////
        } catch (Exception e) {
            logger.warn("DashMessageHandler(uri={}).handle.Exception (uri={}, mpdPath={})\n", this.uri, uri, mpdPath, e);
        }
        ///////////////////////////

        return result;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public String getUri() {
        return uri;
    }
    ////////////////////////////////////////////////////////////////////////////////

}
