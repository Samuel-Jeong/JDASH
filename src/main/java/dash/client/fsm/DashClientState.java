package dash.client.fsm;

/**
 * @class public class DashClientState
 * @brief DashClientState class
 */
public class DashClientState {

    public static final String NAME = "DashClientState";

    public static final String IDLE = "IDLE";
    public static final String MPD_DONE = "MPD_DONE";
    public static final String AUDIO_INIT_SEG_DONE = "AUDIO_INIT_SEG_DONE";
    public static final String VIDEO_INIT_SEG_DONE = "VIDEO_INIT_SEG_DONE";
    public static final String AUDIO_MEDIA_SEG_DONE = "AUDIO_MEDIA_SEG_DONE";
    public static final String VIDEO_MEDIA_SEG_DONE = "VIDEO_MEDIA_SEG_DONE";

}
