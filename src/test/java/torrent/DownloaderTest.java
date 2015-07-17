package torrent;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.junit.Ignore;
import org.junit.Test;

public class DownloaderTest {

	@Ignore
	@Test
	public void downloadTwoSmallFilesFromMagnetLinks() throws IOException, InterruptedException{
		File temp = File.createTempFile("FOO", "BAR");
		temp.delete();
		temp.mkdir();
		String magnetUri1 = "magnet:?xt=urn:btih:fc8a15a2faf2734dbb1dc5f7afdc5c9beaeb1f59&dn=Ubuntu+15.04+Desktop+Amd64%2C+%5BIso+-+MultiLang%5D+%5BTNTVillage%5D&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Fopen.demonii.com%3A1337&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969&tr=udp%3A%2F%2Fexodus.desync.com%3A6969";
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		Downloader.downloadMagnet(magnetUri1, temp.getAbsolutePath(), (finishedFile) -> countDownLatch.countDown() );
		countDownLatch.await();
		assertEquals(1, temp.listFiles().length);
	}
	
	
}
