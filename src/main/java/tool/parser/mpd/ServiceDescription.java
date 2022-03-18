package tool.parser.mpd;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.Objects;

@JsonPropertyOrder({
        "id",
        "latency"
})
public class ServiceDescription {
    @JacksonXmlProperty(isAttribute = true)
    private final int id;

    @JacksonXmlProperty(localName = "Latency", namespace = MPD.NAMESPACE)
    private final Latency latency;

    public ServiceDescription(int id, Latency latency) {
        this.id = id;
        this.latency = latency;
    }

    public ServiceDescription() {
        this.id = 0;
        this.latency = null;
    }

    public int getId() {
        return id;
    }

    public Latency getLatency() {
        return latency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceDescription that = (ServiceDescription) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(latency, that.latency);
    }

    public int hashCode() {
        return Objects.hash(id, latency);
    }

    @Override
    public String toString() {
        return "ServiceDescription{" +
                "id=" + id +
                ", latency=" + latency +
                '}';
    }

    public Builder buildUpon() {
        return new Builder()
                .withId(id)
                .withLatency(latency);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private Latency latency;

        public Builder withId(int id) {
            this.id = id;
            return this;
        }

        public Builder withLatency(Latency latency) {
            this.latency = latency;
            return this;
        }

        public ServiceDescription build() {
            return new ServiceDescription(this.id, this.latency);
        }
    }

}
