package tool.parser.mpd;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tool.parser.mpd.descriptor.Descriptor;
import tool.parser.support.Utils;

import java.util.List;
import java.util.Objects;


@JsonPropertyOrder({
        "id",
        "bandwidth",

        "width",
        "height",
        "codecs",

        "framePackings",
        "audioChannelConfigurations",
        "producerReferenceTime",
        "contentProtections",
        "essentialProperties",
        "supplementalProperties",
        "inbandEventStreams",
        "baseURLs",
        "SubRepresentation",
        "segmentBase",
        "segmentList",
        "segmentTemplate",
        "resync",
        "producerReferenceTime"
})
public class Representation extends RepresentationBase {
    @JacksonXmlProperty(localName = "BaseURL", namespace = MPD.NAMESPACE)
    private final List<BaseURL> baseURLs;

    @JacksonXmlProperty(localName = "SubRepresentation", namespace = MPD.NAMESPACE)
    private final List<SubRepresentation> subRepresentations;

    @JacksonXmlProperty(localName = "Resync", namespace = MPD.NAMESPACE)
    private final Resync resync;

    @JacksonXmlProperty(localName = "SegmentBase", namespace = MPD.NAMESPACE)
    private final SegmentBase segmentBase;

    @JacksonXmlProperty(localName = "SegmentList", namespace = MPD.NAMESPACE)
    private final SegmentList segmentList;

    @JacksonXmlProperty(localName = "SegmentTemplate", namespace = MPD.NAMESPACE)
    private final SegmentTemplate segmentTemplate;

    @JacksonXmlProperty(localName = "ProducerReferenceTime", namespace = MPD.NAMESPACE)
    private final ProducerReferenceTime producerReferenceTime;

    @JacksonXmlProperty(isAttribute = true)
    private final String id;

    @JacksonXmlProperty(isAttribute = true)
    private final long bandwidth;

    @JacksonXmlProperty(isAttribute = true)
    private final Long qualityRanking;

    @JacksonXmlProperty(isAttribute = true)
    private final String dependencyId;

    @JacksonXmlProperty(isAttribute = true)
    private final String mediaStreamStructureId;

    private Representation(List<Descriptor> framePackings, List<Descriptor> audioChannelConfigurations, Resync resync, List<Descriptor> contentProtections, List<Descriptor> essentialProperties, List<Descriptor> supplementalProperties, List<EventStream> inbandEventStreams, String profiles, Long width, Long height, Ratio sar, FrameRate frameRate, String audioSamplingRate, String mimeType, String segmentProfiles, String codecs, Double maximumSAPPeriod, Long startWithSAP, Double maxPlayoutRate, Boolean codingDependency, VideoScanType scanType, List<BaseURL> baseURLs, List<SubRepresentation> subRepresentations, SegmentBase segmentBase, SegmentList segmentList, SegmentTemplate segmentTemplate, String id, long bandwidth, Long qualityRanking, String dependencyId, String mediaStreamStructureId, ProducerReferenceTime producerReferenceTime) {
        super(framePackings, audioChannelConfigurations, contentProtections, essentialProperties, supplementalProperties, inbandEventStreams, profiles, width, height, sar, frameRate, audioSamplingRate, mimeType, segmentProfiles, codecs, maximumSAPPeriod, startWithSAP, maxPlayoutRate, codingDependency, scanType);
        this.baseURLs = baseURLs;
        this.subRepresentations = subRepresentations;
        this.resync = resync;
        this.segmentBase = segmentBase;
        this.segmentList = segmentList;
        this.segmentTemplate = segmentTemplate;
        this.id = id;
        this.bandwidth = bandwidth;
        this.qualityRanking = qualityRanking;
        this.dependencyId = dependencyId;
        this.mediaStreamStructureId = mediaStreamStructureId;
        this.producerReferenceTime = producerReferenceTime;
    }

    @SuppressWarnings("unused")
    private Representation() {
        this.baseURLs = null;
        this.subRepresentations = null;
        this.resync = null;
        this.segmentBase = null;
        this.segmentList = null;
        this.segmentTemplate = null;
        this.id = null;
        this.bandwidth = 0;
        this.qualityRanking = null;
        this.dependencyId = null;
        this.mediaStreamStructureId = null;
        this.producerReferenceTime = null;
    }

    public List<BaseURL> getBaseURLs() {
        return Utils.unmodifiableList(baseURLs);
    }

    public List<SubRepresentation> getSubRepresentations() {
        return Utils.unmodifiableList(subRepresentations);
    }

    public Resync getResync() {
        return resync;
    }

    public SegmentBase getSegmentBase() {
        return segmentBase;
    }

    public SegmentList getSegmentList() {
        return segmentList;
    }

    public SegmentTemplate getSegmentTemplate() {
        return segmentTemplate;
    }

    public String getId() {
        return id;
    }

    public long getBandwidth() {
        return bandwidth;
    }

    public Long getQualityRanking() {
        return qualityRanking;
    }

    public String getDependencyId() {
        return dependencyId;
    }

    public String getMediaStreamStructureId() {
        return mediaStreamStructureId;
    }

    public ProducerReferenceTime getProducerReferenceTime() {
        return producerReferenceTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Representation that = (Representation) o;
        return bandwidth == that.bandwidth &&
                Objects.equals(baseURLs, that.baseURLs) &&
                Objects.equals(subRepresentations, that.subRepresentations) &&
                Objects.equals(resync, that.resync) &&
                Objects.equals(segmentBase, that.segmentBase) &&
                Objects.equals(segmentList, that.segmentList) &&
                Objects.equals(segmentTemplate, that.segmentTemplate) &&
                Objects.equals(id, that.id) &&
                Objects.equals(qualityRanking, that.qualityRanking) &&
                Objects.equals(dependencyId, that.dependencyId) &&
                Objects.equals(mediaStreamStructureId, that.mediaStreamStructureId) &&
                Objects.equals(producerReferenceTime, that.producerReferenceTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), baseURLs, subRepresentations, resync, segmentBase, segmentList, segmentTemplate, id, bandwidth, qualityRanking, dependencyId, mediaStreamStructureId, producerReferenceTime);
    }

    @Override
    public String toString() {
        return "Representation{" +
                "baseURLs=" + baseURLs +
                ", subRepresentations=" + subRepresentations +
                ", resync=" + resync +
                ", segmentBase=" + segmentBase +
                ", segmentList=" + segmentList +
                ", segmentTemplate=" + segmentTemplate +
                ", producerReferenceTime=" + producerReferenceTime +
                ", id='" + id + '\'' +
                ", bandwidth=" + bandwidth +
                ", qualityRanking=" + qualityRanking +
                ", dependencyId='" + dependencyId + '\'' +
                ", mediaStreamStructureId='" + mediaStreamStructureId + '\'' +
                '}';
    }

    public Builder buildUpon() {
        return buildUpon(new Builder()
                .withBaseURLs(baseURLs)
                .withSubRepresentations(subRepresentations)
                .withResync(resync)
                .withSegmentBase(segmentBase)
                .withSegmentList(segmentList)
                .withSegmentTemplate(segmentTemplate)
                .withId(id)
                .withBandwidth(bandwidth)
                .withQualityRanking(qualityRanking)
                .withDependencyId(dependencyId)
                .withMediaStreamStructureId(mediaStreamStructureId))
                .withProducerReferenceTime(producerReferenceTime);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Builder> {
        private List<BaseURL> baseURLs;
        private List<SubRepresentation> subRepresentations;
        private Resync resync;
        private SegmentBase segmentBase;
        private SegmentList segmentList;
        private SegmentTemplate segmentTemplate;
        private String id;
        private long bandwidth;
        private Long qualityRanking;
        private String dependencyId;
        private String mediaStreamStructureId;
        private ProducerReferenceTime producerReferenceTime;

        @Override
        Builder getThis() {
            return this;
        }

        public Builder withBaseURLs(List<BaseURL> baseURLs) {
            this.baseURLs = baseURLs;
            return this;
        }

        public Builder withSubRepresentations(List<SubRepresentation> subRepresentations) {
            this.subRepresentations = subRepresentations;
            return this;
        }

        public Builder withResync(Resync resync) {
            this.resync = resync;
            return this;
        }

        public Builder withSegmentBase(SegmentBase segmentBase) {
            this.segmentBase = segmentBase;
            return this;
        }

        public Builder withSegmentList(SegmentList segmentList) {
            this.segmentList = segmentList;
            return this;
        }

        public Builder withSegmentTemplate(SegmentTemplate segmentTemplate) {
            this.segmentTemplate = segmentTemplate;
            return this;
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withBandwidth(long bandwidth) {
            this.bandwidth = bandwidth;
            return this;
        }

        public Builder withQualityRanking(Long qualityRanking) {
            this.qualityRanking = qualityRanking;
            return this;
        }

        public Builder withDependencyId(String dependencyIds) {
            this.dependencyId = dependencyIds;
            return this;
        }

        public Builder withMediaStreamStructureId(String mediaStreamStructureId) {
            this.mediaStreamStructureId = mediaStreamStructureId;
            return this;
        }

        public Builder withProducerReferenceTime(ProducerReferenceTime producerReferenceTime) {
            this.producerReferenceTime = producerReferenceTime;
            return this;
        }

        public Representation build() {
            return new Representation(framePackings, audioChannelConfigurations, resync, contentProtections, essentialProperties, supplementalProperties, inbandEventStreams, profiles, width, height, sar, frameRate, audioSamplingRate, mimeType, segmentProfiles, codecs, maximumSAPPeriod, startWithSAP, maxPlayoutRate, codingDependency, scanType, baseURLs, subRepresentations, segmentBase, segmentList, segmentTemplate, id, bandwidth, qualityRanking, dependencyId, mediaStreamStructureId, producerReferenceTime);
        }
    }
}
