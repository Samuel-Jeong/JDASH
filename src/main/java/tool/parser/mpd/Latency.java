package tool.parser.mpd;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.Objects;

public class Latency {

    @JacksonXmlProperty(isAttribute = true)
    private final long target;

    @SuppressWarnings("unused")
    public Latency() {
        target = 0;
    }

    public Latency(long target) {
        this.target = target;
    }

    public long getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Latency latency = (Latency) o;
        return Objects.equals(target, latency.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target);
    }

    @Override
    public String toString() {
        return "Latency{" +
                "target=" + target +
                '}';
    }

    public Builder buildUpon() {
        return new Builder()
                .withTarget(target);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long target;

        public Builder withTarget(long target) {
            this.target = target;
            return this;
        }

        public Latency build() {
            return new Latency(target);
        }
    }

}
