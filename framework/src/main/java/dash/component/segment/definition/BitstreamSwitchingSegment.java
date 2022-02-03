package dash.component.segment.definition;

import dash.component.segment.SegmentFactory;

/**
 * A Bitstream Switching Segment contains data enabling switching to the Representation it is assigned to.
 * It is media format specific and more details are defined for each media format that permits Bitstream Switching Segments.
 * At most one bitstream switching segment can be defined for each Representation.
 */
public class BitstreamSwitchingSegment extends SegmentFactory {

    ////////////////////////////////////////////////////////////
    public BitstreamSwitchingSegment(String index, String url) {
        super(index, url);
    }
    ////////////////////////////////////////////////////////////

}
