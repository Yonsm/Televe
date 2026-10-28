package io.library.client;

/**
 * This class wraps a RTP packet providing method to convert from a byte array
 *
 * A RTP packet is composed of an header and the subsequent payload. It has the following format:
 *
 *        0                   1                   2                   3
 *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                           timestamp                           |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |           synchronization source (SSRC) identifier            |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        |            contributing source (CSRC) identifiers             |
 *        |                             ....                              |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        | Profile-specific extension ID |   Extension header length     |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                       Extension header                        |
 *        |                             ....                              |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The first twelve octets are present in every RTP packet, while the list of
 * CSRC identifiers is present only when inserted by a mixer.
 *
 */
public final class RtpPacket {
    private static final int RTP_VERSION = 0x02;

    protected static final int RTP_MIN_SIZE = 4;
    protected static final int RTP_HDR_SIZE = 12; /* RFC 3550 */
    protected static final int RTP_MAX_HDR_SIZE = 76;

    private static final int RTP_XTHDR_SIZE = 4;

    private static final int CSRC_SIZE = 4;

    /* MPEG payload-type constants */
    private static final int RTP_MPA_TYPE = 0x0E;     // MPEG-1 and MPEG-2 audio
    private static final int RTP_MPV_TYPE = 0x20;     // MPEG-1 and MPEG-2 video

    private static final int RTP_PAYLOAD_MAX = 127;

    // RTP Payload Format for H.261 Video Streams (RFC 2032)
    public static final int RTCP_PAYLOAD_MIN = 192;
    public static final int RTCP_PAYLOAD_MAX = 210;

    public static int parse(byte[] packet, int length) {
        if((length < RTP_MIN_SIZE) || (length < RTP_HDR_SIZE))
            return -1;

        // Read the packet header
        int version = (packet[0] & 0xC0) >> 6;
        if (RTP_VERSION != version)
            return -1;

        boolean padding = ((packet[0] & 0x20) >> 5) == 1;
        boolean extension = ((packet[0] & 0x10) >> 4) == 1;
        int csrcCount = packet[0] & 0x0F;

        int csrcLen = CSRC_SIZE * csrcCount;
        int headLen = RTP_HDR_SIZE + csrcLen;

        // Read and check the payload type as describe on RFC 5761 - Multiplexing RTP Data and
        // Control Packets on a Single Port according to Section 4
        int payloadType = packet[1] & 0x7F;
        if ((payloadType >= RTCP_PAYLOAD_MIN &&
                payloadType <= RTCP_PAYLOAD_MAX) ||
                (payloadType+RTP_PAYLOAD_MAX >= RTCP_PAYLOAD_MIN &&
                        payloadType+RTP_PAYLOAD_MAX <= RTCP_PAYLOAD_MAX)) {
            return -1;
        }

        /* profile-based skip: adopted from vlc 0.8.6 code */
        if ((RTP_MPA_TYPE == payloadType) || (RTP_MPV_TYPE == payloadType)) {
            headLen += 4;
        }

        int frontSkip = headLen;
        int backSkip = 0;

        if (padding) {
            int padLen = packet[length - 1];
            backSkip += padLen;
        }

        if (length < (frontSkip + backSkip))
            return -1;

        if (extension) {
            if (length < RTP_HDR_SIZE + RTP_XTHDR_SIZE + csrcLen)
                return -1;

            int extLen = RTP_XTHDR_SIZE +
                    4 * (((packet[RTP_HDR_SIZE + csrcLen + 2] & 0xFF) << 8) |
                            (packet[RTP_HDR_SIZE + csrcLen + 3] & 0xFF));

            frontSkip += extLen;
        }
        return frontSkip;
/*
        boolean marker = ((packet[1] & 0x80) >> 7) == 1;

        int sequenceNumber = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);

        long timestamp = ((((long)packet[4]) & 0xFF) << 24) | ((((long)packet[5]) & 0xFF) << 16) |
                ((((long)packet[6]) & 0xFF) << 8) | (((long)packet[7]) & 0xFF);

        long ssrc = (((((long)packet[8]) & 0xFF) << 24) | ((((long)packet[9]) & 0xFF) << 16) |
                ((((long)packet[10]) & 0xFF) << 8) | (((long)packet[11]) & 0xFF));

        long[] csrc;

        // CSRC list
        if (csrcCount > 0) {
            csrc = new long[csrcCount];

            for (int ndx=0, offset=RTP_HDR_SIZE; ndx < csrcCount; ndx++, offset+=4) {
                csrc[ndx] = ((packet[offset] & 0xFF) << 24) | ((packet[offset + 1] & 0xFF) << 16) |
                        ((packet[offset + 2] & 0xFF) << 8) | (packet[offset + 3] & 0xFF);
            }
        } else {
            csrc = new long[0];
        }

        byte[] hdrExtension;

        // Read the extension header if present
        if (extension) {
            hdrExtension = Arrays.copyOfRange(packet, headLen, headLen + extLen);
        } else {
            hdrExtension = new byte[0];
        }

        // Read the payload
        byte[] payload = Arrays.copyOfRange(packet, frontSkip, length);

        return new RtpPacket(version, padLen, hdrExtension, marker, payloadType,
                sequenceNumber, timestamp, ssrc, csrc, payload);*/
    }
}
