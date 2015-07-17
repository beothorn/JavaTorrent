package torrent;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.johnnei.utils.ConsoleLogger;
import org.johnnei.utils.config.Config;

import torrent.download.MagnetLink;
import torrent.download.Torrent;
import torrent.download.tracker.TrackerManager;

public class Downloader {
	
	public static final byte[] RESERVED_EXTENTION_BYTES = new byte[8];
	
	private static Logger log;
	
	private static boolean alreadyStarted = false;
	private static TorrentManager torrentManagerLazy;
	private static TrackerManager trackerManagerLazy;
	
	public static void downloadMagnet(String magnetUri, String outputFolder, Consumer<String> onSuccessCallback) {
		if(!alreadyStarted){			
			log = ConsoleLogger.createLogger("JavaTorrent", Level.INFO);
			Config.getConfig().load();
			Config.getConfig().setDefault("peer-max", 500);
			Config.getConfig().setDefault("peer-max_burst_ratio", 1.5F);
			Config.getConfig().setDefault("peer-max_concurrent_connecting", 2);
			Config.getConfig().setDefault("peer-max_connecting", 50);
			Config.getConfig().set("download-output_folder", outputFolder);
			Config.getConfig().setDefault("download-port", 6881);
			Config.getConfig().setDefault("general-show_all_peers", false);
			
			// Initialise reserved bytes field
			RESERVED_EXTENTION_BYTES[5] |= 0x10; // Extended Messages
			
			// Initialise managers
			torrentManagerLazy = new TorrentManager();
			trackerManagerLazy = new TrackerManager();		
			
			Thread trackerManagerThread = new Thread(trackerManagerLazy, "Tracker manager");
			trackerManagerThread.setDaemon(true);
			trackerManagerThread.start();
			
			torrentManagerLazy.startListener(trackerManagerLazy);
			alreadyStarted = true;
		}
		
		MagnetLink magnet = new MagnetLink(magnetUri, torrentManagerLazy, trackerManagerLazy, onSuccessCallback);
		if (!magnet.isDownloadable()) {
			log.severe("Magnet link error occured");
			return;
		}
		Torrent torrent = magnet.getTorrent();
		torrent.start();
	}

}
