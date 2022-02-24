package cam;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
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
    private FFmpegFrameRecorder recorder;
    private ScheduledThreadPoolExecutor sampleTask;
    private TargetDataLine line;
    byte[] audioBytes;
    private volatile boolean isFinish = false;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void setRecorderParams(FrameRecorder recorder) {
        this.recorder = (FFmpegFrameRecorder) recorder;
        recorder.setAudioOption("crf", "0");
        recorder.setAudioQuality(0);
        recorder.setAudioBitrate(192000);
        recorder.setSampleRate(SAMPLE_RATE);
        recorder.setAudioChannels(CHANNEL_NUM);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
    }

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
        sampleTask = new ScheduledThreadPoolExecutor(1);
    }

    public void releaseOutputResource() {
        isFinish = true;
        sampleTask.shutdown();
        line.stop();
        line.close();
    }

    public void startSampling(double frameRate) {
        sampleTask.scheduleAtFixedRate(() -> {
            try {
                if (isFinish) { return; }

                int nBytesRead = 0;
                while (nBytesRead == 0) {
                    nBytesRead = line.read(audioBytes, 0, line.available());
                }
                if (nBytesRead < 1) { return; }

                int nSamplesRead = nBytesRead / 2;
                short[] samples = new short[nSamplesRead];

                ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                ShortBuffer sBuff = ShortBuffer.wrap(samples, 0, nSamplesRead);

                recorder.recordSamples(SAMPLE_RATE, CHANNEL_NUM, sBuff);
            } catch (Exception e) {
                logger.warn("AudioService.startSampling.Exception", e);
            }
        }, 0, 1000 / (long) frameRate, TimeUnit.MILLISECONDS);
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public boolean isFinish() {
        return isFinish;
    }

    public void setFinish(boolean finish) {
        isFinish = finish;
    }
    ////////////////////////////////////////////////////////////////////////////////

}