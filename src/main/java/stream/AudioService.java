package stream;

import config.ConfigManager;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.TimeUnit;

public class AudioService {

    private static final Logger logger = LoggerFactory.getLogger(AudioService.class);

    ////////////////////////////////////////////////////////////////////////////////
    private static final String AUDIO_SERVICE_SCHEDULE_KEY = "AUDIO_SERVICE_SCHEDULE_KEY";

    public final static int DEFAULT_SAMPLE_RATE = 44100;
    public final static int CHANNEL_NUM = 1;

    private int sampleRate = 0;
    private TargetDataLine line;
    private byte[] audioBytes;

    private ScheduleManager scheduleManager = null;
    private AudioSampler audioSampler = null;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void initSampleService(ScheduleManager scheduleManager) throws Exception {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        this.scheduleManager = scheduleManager;

        sampleRate = configManager.getLocalAudioSampleRate();
        if (configManager.getLocalAudioSampleRate() <= 0) {
            sampleRate = DEFAULT_SAMPLE_RATE;
        }

        AudioFormat audioFormat = new AudioFormat(
                sampleRate,
                16,
                CHANNEL_NUM,
                true,
                false
        );

        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
        line = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
        line.open(audioFormat);
        line.start();

        final int audioBufferSize = sampleRate * CHANNEL_NUM;
        audioBytes = new byte[audioBufferSize];

        if (scheduleManager != null) {
            if (scheduleManager.initJob(AUDIO_SERVICE_SCHEDULE_KEY, 1, 1)) {
                logger.debug("[AudioService] Success to init [{}]", AUDIO_SERVICE_SCHEDULE_KEY);
            }
        }

        logger.debug("[AudioService] START");
    }

    public void releaseOutputResource() {
        if (audioSampler != null) {
            if (scheduleManager != null) {
                scheduleManager.stopJob(AUDIO_SERVICE_SCHEDULE_KEY, audioSampler);
            }
            audioSampler = null;
        }

        line.stop();
        line.close();

        logger.debug("[AudioService] STOP");
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public void startSampling(FFmpegFrameRecorder fFmpegFrameRecorder) {
        if (fFmpegFrameRecorder == null) {
            logger.warn("[AudioService] Recorder is not defined. Fail to start sampling");
            return;
        }

        if (scheduleManager != null) {
            audioSampler = new AudioSampler(
                    scheduleManager,
                    AudioSampler.class.getSimpleName(),
                    0, 1, TimeUnit.MILLISECONDS,
                    1, 1, true,
                    fFmpegFrameRecorder
            );
            scheduleManager.startJob(AUDIO_SERVICE_SCHEDULE_KEY, audioSampler);
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    private class AudioSampler extends Job {

        /////////////////////////////////
        private final FFmpegFrameRecorder fFmpegFrameRecorder;
        /////////////////////////////////

        /////////////////////////////////
        public AudioSampler(ScheduleManager scheduleManager,
                            String name,
                            int initialDelay, int interval, TimeUnit timeUnit,
                            int priority, int totalRunCount, boolean isLasted,
                            FFmpegFrameRecorder fFmpegFrameRecorder) {
            super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

            this.fFmpegFrameRecorder = fFmpegFrameRecorder;
        }
        /////////////////////////////////

        /////////////////////////////////
        @Override
        public void run() {
            try {
                record(fFmpegFrameRecorder);
            } catch (Exception e) {
                logger.warn("[AudioService] [AudioSampler] run.Exception", e);
            }
        }
        /////////////////////////////////

    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public boolean record(FFmpegFrameRecorder fFmpegFrameRecorder) throws Exception {
        int nBytesRead = 0;
        while (nBytesRead == 0) {
            nBytesRead = line.read(audioBytes, 0, line.available());
        }
        if (nBytesRead < 1) { return false; }

        int nSamplesRead = nBytesRead / 2;
        short[] samples = new short[nSamplesRead];

        ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        ShortBuffer sBuff = ShortBuffer.wrap(samples, 0, nSamplesRead);
        fFmpegFrameRecorder.recordSamples(sampleRate, CHANNEL_NUM, sBuff);
        return true;
    }
    ////////////////////////////////////////////////////////////////////////////////

}