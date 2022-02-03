package dash.component.segment.definition;

import dash.component.segment.SegmentFactory;

/**
 * Index Segments contain information that is related to Media Segments,
 * including timing and access information for Media Segments or Subsegments.
 *
 * An Index Segment may provide information for one or more Media Segments.
 * The Index Segment may be media format specific and more details are defined for each media format that supports Index Segments.
 */
public class IndexSegment extends SegmentFactory {

    ////////////////////////////////////////////////////////////
    public IndexSegment(String index, String url) {
        super(index, url);
    }
    ////////////////////////////////////////////////////////////

}
