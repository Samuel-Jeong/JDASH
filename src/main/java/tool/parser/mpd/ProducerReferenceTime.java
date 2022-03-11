package tool.parser.mpd;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.Objects;

public class ProducerReferenceTime {

    @JacksonXmlProperty(localName = "UTCTiming", namespace = MPD.NAMESPACE)
    private final UTCTiming utcTiming;

    @JacksonXmlProperty(isAttribute = true)
    private final int id;

    @JacksonXmlProperty(isAttribute = true)
    private final boolean inband;

    @JacksonXmlProperty(isAttribute = true)
    private final String type;

    @JacksonXmlProperty(isAttribute = true)
    private final String wallClockTime;

    @JacksonXmlProperty(isAttribute = true)
    private final long presentationTime;

    @SuppressWarnings("unused")
    public ProducerReferenceTime() {
        this.utcTiming = null;
        this.id = 0;
        this.inband = false;
        this.type = null;
        this.wallClockTime = null;
        this.presentationTime = 0;
    }

    public ProducerReferenceTime(UTCTiming utcTiming, int id, boolean inband, String type, String wallClockTime, long presentationTime) {
        this.utcTiming = utcTiming;
        this.id = id;
        this.inband = inband;
        this.type = type;
        this.wallClockTime = wallClockTime;
        this.presentationTime = presentationTime;
    }

    public UTCTiming getUtcTiming() {
        return utcTiming;
    }

    public long getId() {
        return id;
    }

    public boolean isInband() {
        return inband;
    }

    public String getType() {
        return type;
    }

    public String getWallClockTime() {
        return wallClockTime;
    }

    public long getPresentationTime() {
        return presentationTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProducerReferenceTime producerReferenceTime = (ProducerReferenceTime) o;
        return Objects.equals(utcTiming, producerReferenceTime.utcTiming) &&
                Objects.equals(id, producerReferenceTime.id) &&
                Objects.equals(inband, producerReferenceTime.inband) &&
                Objects.equals(type, producerReferenceTime.type) &&
                Objects.equals(wallClockTime, producerReferenceTime.wallClockTime) &&
                Objects.equals(presentationTime, producerReferenceTime.presentationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(utcTiming, id, inband, type, wallClockTime, presentationTime);
    }

    @Override
    public String toString() {
        return "ProducerReferenceTime{" +
                "utcTiming=" + utcTiming +
                ", id=" + id +
                ", inband=" + inband +
                ", type='" + type + '\'' +
                ", wallClockTime='" + wallClockTime + '\'' +
                ", presentationTime=" + presentationTime +
                '}';
    }

    public Builder buildUpon() {
        return new Builder()
                .withUTCTiming(utcTiming)
                .withTarget(id)
                .withInband(inband)
                .withType(type)
                .withWallClockTime(wallClockTime)
                .withPresentationTime(presentationTime);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UTCTiming utcTiming;
        private int id;
        private boolean inband;
        private String type;
        private String wallClockTime;
        private long presentationTime;

        public Builder withUTCTiming(UTCTiming utcTiming) {
            this.utcTiming = utcTiming;
            return this;
        }

        public Builder withTarget(int id) {
            this.id = id;
            return this;
        }

        public Builder withInband(boolean inband) {
            this.inband = inband;
            return this;
        }
        public Builder withType(String type) {
            this.type = type;
            return this;
        }
        public Builder withWallClockTime(String wallClockTime) {
            this.wallClockTime = wallClockTime;
            return this;
        }
        public Builder withPresentationTime(long presentationTime) {
            this.presentationTime = presentationTime;
            return this;
        }

        public ProducerReferenceTime build() {
            return new ProducerReferenceTime(utcTiming, id, inband, type, wallClockTime, presentationTime);
        }
    }

}
