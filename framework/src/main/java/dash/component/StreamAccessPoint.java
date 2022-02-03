package dash.component;

/**
 * A Stream Access Point (SAP) is a position
 *      in a Representation enabling playback
 *      of a media stream to be started using
 *      only the information contained in Representation data
 *      starting from that position onwards
 *      (preceded by initializing data in the Initialization Segment, if any).
 *
 * Stream access points are usually defined for each Media Segment,
 *      and used to support variety of streaming client operations, including:
 *
 *      1) stream switching, e.g. for adaptation to changes in network bandwidth or other events,
 *      2) random access (seek and rewind operations),
 *      3) trick modes.
 *
 * ISO/IEC 14496-12, Annex I defines six possible types of SAPs.
 *
 * The mappings between SAP types and commonly used video prediction structures,
 * such as Open GOP and Closed GOP structures are explained in Clause Error! Reference source not found. of this document.
 */
public class StreamAccessPoint {
}
