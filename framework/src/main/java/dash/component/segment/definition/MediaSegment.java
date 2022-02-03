package dash.component.segment.definition;

import dash.component.segment.SegmentFactory;

/**
 * A Media Segment contains and encapsulates media streams
 * that are either described within this Media Segment or
 * described by the Initialization Segment of this Representation or both.
 *
 * Media Segments must contain a whole number of complete Access Units and
 * should contain at least one Stream Access Point (SAP) for each contained media stream.
 *
 * Other requirements applicable to Media Segments are described in ISO/IEC 23009-1, clause 6.2.3.
 */
public class MediaSegment extends SegmentFactory {

    ////////////////////////////////////////////////////////////
    public MediaSegment(String index, String url) {
        super(index, url);
    }
    ////////////////////////////////////////////////////////////

}
