package dash.component.segment.definition;

import dash.component.segment.SegmentFactory;

/**
 * Initialization Segments contain initialization information
 * for accessing the Representation and
 * it does not contain any media data with an assigned presentation time.
 *
 * Conceptually, the Initialization Segment is processed by
 * the client to initialize the media engines
 * for enabling play-out of Media Segments of the containing Representation.
 */
public class InitializationSegment extends SegmentFactory {

    ////////////////////////////////////////////////////////////
    public InitializationSegment(String index, String url) {
        super(index, url);
    }
    ////////////////////////////////////////////////////////////

}
