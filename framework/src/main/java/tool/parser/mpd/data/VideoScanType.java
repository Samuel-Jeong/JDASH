package tool.parser.mpd.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum VideoScanType {
    @JsonProperty("progressive") PROGRESSIVE,
    @JsonProperty("interlaced") INTERLACED,
    @JsonProperty("unknown") UNKNOWN;
}
