package dash.component.segment.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Representation 안에서 하나의 미디어 스트림에 적용
 *
 * 만약, Representation 이 비디오와 오디오 모두를 포함한다면,
 *      Representation 은 오디오에 적용되는 추가적인 정보를 제공하기 위해 하나의 SubRepresentation 을 가진다.
 *      (비디오도 동일)
 *      > 추가적인 정보 : 코덱, sampling-rate, subtitles
 *
 * Multiplexed container 로부터 하나의 스트림을 분리하기 위한 또는
 *      스트림의 낮은 품질 버전(Fast-Forword Mode 에서 유용한 Only I-Frames 와 같은)을 분리하기 위한 필수적인 정보 제공
 *
 */

public class SubRepresentation {

    ////////////////////////////////////////////////////////////
    private final int contentComponent;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SubRepresentation(int contentComponent) {
        this.contentComponent = contentComponent;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public int getContentComponent() {
        return contentComponent;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
