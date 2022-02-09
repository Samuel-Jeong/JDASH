package tool.parser.mpd;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.Objects;

@JsonPropertyOrder({
        "id"
})
public class ServiceDescription {
    @JacksonXmlProperty(isAttribute = true)
    private final int id;

    public ServiceDescription(int id) {
        this.id = id;
    }

    public ServiceDescription() {
        this.id = 0;
    }

    public int getId() {
        return id;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceDescription that = (ServiceDescription) o;
        return Objects.equals(id, that.id);
    }

    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ServiceDescription{" +
                "id=" + id +
                '}';
    }

    public Builder buildUpon() {
        return new ServiceDescription.Builder()
                .withId(id);
    }

    public static Builder builder() {
        return new ServiceDescription.Builder();
    }

    public static class Builder {
        private int id;

        public Builder withId(int id) {
            this.id = id;
            return this;
        }

        public ServiceDescription build() {
            return new ServiceDescription(this.id);
        }
    }

}
