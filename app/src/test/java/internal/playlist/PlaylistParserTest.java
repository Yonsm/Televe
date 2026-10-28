package internal.playlist;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class PlaylistParserTest {
    private List<Channel> parse(String text, boolean providers) throws Exception {
        return new PlaylistParser(providers).onParse("text/plain", "UTF-8",
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), false);
    }

    @Test
    public void standardM3uPreservesNamesLogosAndUrls() throws Exception {
        Channel channel = parse("#EXTM3U\n#EXTINF:-1 tvg-name=\"News HD\" tvg-logo=\"https://example.com/logo?a=b\",News, World\nhttps://example.com/live.m3u8\n", false).get(0);
        assertEquals("News HD", channel.id);
        assertEquals("News, World", channel.title);
        assertEquals("https://example.com/logo?a=b", channel.thumbnail);
        assertEquals("https://example.com/live.m3u8", channel.url);
        assertEquals(Channel.NO_ORDER, channel.order);
        assertFalse(channel.playlist);
    }

    @Test
    public void legacyProviderListsKeepServerIdentityAndOrder() throws Exception {
        Channel channel = parse("#EXTM3U\n#EXTINF:2,server-id=\"primary\",Main provider\nhttps://example.com/channels.m3u\n", true).get(0);
        assertEquals("primary", channel.id);
        assertEquals("Main provider", channel.title);
        assertEquals(2, channel.order);
        assertTrue(channel.playlist);
    }

    @Test
    public void textListsKeepStreamQueriesAndSkipCategoryHeaders() throws Exception {
        List<Channel> channels = parse("News,#genre#\n2-News HD,http://example.com/live?token=a,b\ninvalid\nEmpty,\n", false);
        assertEquals(1, channels.size());
        assertEquals("News HD", channels.get(0).title);
        assertEquals(2, channels.get(0).order);
        assertEquals("http://example.com/live?token=a,b", channels.get(0).url);
    }

    @Test
    public void emptyAndBomPrefixedPlaylistsAreAccepted() throws Exception {
        assertTrue(parse("\n \n", false).isEmpty());
        assertEquals(1, parse("\uFEFF#EXTM3U\n#EXTINF:1,News\nhttps://example.com/live\n", false).size());
    }

    @Test
    public void metadataDoesNotLeakBetweenChannels() throws Exception {
        List<Channel> channels = parse("#EXTM3U\n#EXTINF:1 tvg-name=\"one\",First\nhttp://example.com/1\n#EXTINF:2,Second\nhttp://example.com/2\n", false);
        assertEquals("one", channels.get(0).id);
        assertEquals("Second", channels.get(1).id);
    }

    @Test
    public void channelCacheRoundTripPreservesPlaylistMetadata() throws Exception {
        Channel channel = parse("#EXTM3U\n#EXTINF:3 tvg-name=\"源\",Provider\nhttp://example.com/list\n", true).get(0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        channel.writeTo(new DataOutputStream(bytes));
        Channel restored = new Channel(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertTrue(channel.equalsContent(restored));
        assertTrue(restored.playlist);
    }

    @Test
    public void channelsWithSameNameAndDifferentStreamsRemainDistinct() {
        Channel first = Channel.fromUrl("http://example.com/1");
        Channel second = Channel.fromUrl("http://example.com/2");
        first.id = second.id = "News";
        first.title = second.title = "News";
        assertNotEquals(first, second);
        assertNotEquals(0, new ChannelComparator(false).compare(first, second));
    }

    @Test
    public void explicitChannelOrderPrecedesNaturalNameOrder() {
        Channel first = Channel.fromUrl("http://example.com/1");
        Channel second = Channel.fromUrl("http://example.com/2");
        first.title = "News10";
        second.title = "News2";
        ChannelComparator comparator = new ChannelComparator(false);
        assertTrue(comparator.compare(first, second) > 0);
        first.order = 1;
        assertTrue(comparator.compare(first, second) < 0);
    }
}
