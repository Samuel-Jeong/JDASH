package stream;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AudioService {

    private static final Logger logger = LoggerFactory.getLogger(AudioService.class);

    ////////////////////////////////////////////////////////////////////////////////
    public final static int SAMPLE_RATE = 44100;
    public final static int CHANNEL_NUM = 1;
    private ScheduledThreadPoolExecutor sampleTask = null;
    private TargetDataLine line;
    private byte[] audioBytes;
    private volatile boolean isFinish = false;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void initSampleService() throws Exception {
        AudioFormat audioFormat = new AudioFormat(
                SAMPLE_RATE,
                16,
                CHANNEL_NUM,
                true,
                false
        );

        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
        line = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
        line.open(audioFormat);
        line.start();

        final int audioBufferSize = SAMPLE_RATE * CHANNEL_NUM;
        audioBytes = new byte[audioBufferSize];

        logger.debug("[AudioService] START");
    }

    public void releaseOutputResource() {
        isFinish = true;
        if (sampleTask != null) {
            sampleTask.shutdown();
        }
        line.stop();
        line.close();

        logger.debug("[AudioService] STOP");
    }

    public void startSampling(FFmpegFrameRecorder fFmpegFrameRecorder, double frameRate) {
        if (fFmpegFrameRecorder == null) {
            logger.warn("[AudioService] Recorder is not defined. Fail to start sampling");
            return;
        }

        sampleTask = new ScheduledThreadPoolExecutor(1);
        sampleTask.scheduleAtFixedRate(() -> {
                    try {
                        if (isFinish) { return; }
                        record(fFmpegFrameRecorder);
                    } catch (Exception e) {
                        logger.warn("AudioService.startSampling.Exception", e);
                    }
                },
                0,
                1000 / (long) frameRate,
                TimeUnit.MILLISECONDS
        );
    }

    private void record(FFmpegFrameRecorder fFmpegFrameRecorder) throws Exception {
        int nBytesRead = 0;
        while (nBytesRead == 0) {
            nBytesRead = line.read(audioBytes, 0, line.available());
        }
        if (nBytesRead < 1) { return; }

        int nSamplesRead = nBytesRead / 2;
        short[] samples = new short[nSamplesRead];

        ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        ShortBuffer sBuff = ShortBuffer.wrap(samples, 0, nSamplesRead);
        fFmpegFrameRecorder.recordSamples(SAMPLE_RATE, CHANNEL_NUM, sBuff);
    }
    ////////////////////////////////////////////////////////////////////////////////

}