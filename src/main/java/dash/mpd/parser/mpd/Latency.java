package dash.mpd.parser.mpd;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.Objects;

/**
 *	<ServiceDescription id="0">
 * 		<Latency target="3000" referenceId="1"/>
 * 	</ServiceDescription>
 */
public class Latency {

    @JacksonXmlProperty(isAttribute = true)
    private final long target;

    @JacksonXmlProperty(isAttribute = true)
    private final int referenceId;

    @SuppressWarnings("unused")
    public Latency() {
        target = 0;
        referenceId = 0;
    }

    public Latency(long target, int referenceId) {
        this.target = target;
        this.referenceId = referenceId;
    }

    public long getTarget() {
        return target;
    }

    public int getReferenceId() {
        return referenceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Latency latency = (Latency) o;
        return Objects.equals(target, latency.target)
                && Objects.equals(referenceId, latency.referenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, referenceId);
    }

    @Override
    public String toString() {
        return "Latency{" +
                "target=" + target +
                ", referenceId=" + referenceId +
                '}';
    }

    public Builder buildUpon() {
        return new Builder()
                .withTarget(target)
                .withReferenceId(referenceId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long target;
        private int referenceId;

        public Builder withTarget(long target) {
            this.target = target;
            return this;
        }

        public Builder withReferenceId(int referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Latency build() {
            return new Latency(target, referenceId);
        }
    }

}
