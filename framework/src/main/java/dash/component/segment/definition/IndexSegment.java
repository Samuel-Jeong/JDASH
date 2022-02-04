package dash.component.segment.definition;

import dash.component.segment.SegmentFactory;

/**
 * Index Segments contain information that is related to Media Segments,
 * including timing and access information for Media Segments or Subsegments.
 *
 * An Index Segment may provide information for one or more Media Segments.
 * The Index Segment may be media format specific and more details are defined for each media format that supports Index Segments.
 *
 * Index Segments come in two types:
 *          one Representation Index Segment for the entire Representation,
 *                  or a Single Index Segment per Media Segment.
 *
 *          A Representation Index Segment is always a separate file,
 *                  but a Single Index Segment can be a byte range in the same file as the Media Segment.
 *
 * Index Segments contain ISOBMFF 'sidx' boxes,
 *          with information about Media Segment durations (in both bytes and time), stream access point types,
 *          and optionally subsegment information in 'ssix' boxes (the same information, but within segments).
 *
 * In the case of a Representation Index Segment,
 *          the 'sidx' boxes come one after another,
 *          but they are preceded by an 'sidx' for the index segment itself.
 *
 */
public class IndexSegment extends SegmentFactory {

    ////////////////////////////////////////////////////////////
    public IndexSegment(String index, String url) {
        super(index, url);
    }
    ////////////////////////////////////////////////////////////

}
