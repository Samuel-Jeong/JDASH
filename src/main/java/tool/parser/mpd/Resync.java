package tool.parser.mpd;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.Objects;

public class Resync {

    @JacksonXmlProperty(isAttribute = true)
    private final String dT;

    @JacksonXmlProperty(isAttribute = true)
    private final String type;

    @SuppressWarnings("unused")
    public Resync() {
        dT = null;
        type = null;
    }

    public Resync(String dT, String type) {
        this.dT = dT;
        this.type = type;
    }

    public String getdT() {
        return dT;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resync resync = (Resync) o;
        return Objects.equals(dT, resync.dT) &&
                Objects.equals(type, resync.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dT, type);
    }

    @Override
    public String toString() {
        return "Resync{" +
                "dT='" + dT + '\'' +
                ", type='" + type + '\'' +
                '}';
    }

    public Builder buildUpon() {
        return new Builder()
                .withDT(dT)
                .withType(type);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dT;
        private String type;

        public Builder withDT(String dT) {
            this.dT = dT;
            return this;
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Resync build() {
            return new Resync(dT, type);
        }
    }

}
