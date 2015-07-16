package torrent.download.tracker;

import torrent.download.Torrent;

public class TorrentInfo {
	
	/**
	 * The torrent for which this info is being stored
	 */
	private Torrent torrent;
	/**
	 * The timestamp of the last announce
	 */
	private long lastAnnounceTime;
	/**
	 * The amount of seeders as reported by the tracker
	 */
	private int seeders;
	/**
	 * The amount of leechers as reported by the tracker
	 */
	private int leechers;
	/**
	 * The amount of times this torrent has been download as reported by the tracker
	 */
	private int downloaded;
	/**
	 * The current event
	 */
	private int event;
	
	public TorrentInfo(Torrent torrent) {
		this.torrent = torrent;
		this.event = TrackerConnection.EVENT_STARTED;
		lastAnnounceTime = System.currentTimeMillis() - Tracker.DEFAULT_ANNOUNCE_INTERVAL;
	}
	
	public void updateAnnounceTime() {
		lastAnnounceTime = System.currentTimeMillis();
	}
	
	public void setEvent(int event) {
		this.event = event;
	}
	
	public void setInfo(int seeders, int leechers) {
		this.seeders = seeders;
		this.leechers = leechers;
	}
	
	public void setInfo(int seeders, int leechers, int downloadCount) {
		this.seeders = seeders;
		this.leechers = leechers;
		this.downloaded = downloadCount;
	}
	
	public int getEvent() {
		return event;
	}
	
	/**
	 * The amount of seeders as reported by the tracker
	 * @return the amount of seeders
	 */
	public int getSeeders() {
		return seeders;
	}
	
	/**
	 * The amount of leechers as reported by the tracker
	 * @return the amount of leechers
	 */
	public int getLeechers() {
		return leechers;
	}
	
	/**
	 * The amount of times this torrent has been downloaded<br/>
	 * If the tracker returns 0 it will return N/A as the tracker apparently doesn't support it
	 * @return the count of times downloaded or N/A if not reported
	 */
	public String getDownloadCount() {
		return (downloaded == 0) ? "N/A" : Integer.toString(downloaded);
	}
	
	/**
	 * The time since the last announce
	 * @return milliseconds since last announce
	 */
	public int getTimeSinceLastAnnouce() {
		return (int)(System.currentTimeMillis() - lastAnnounceTime);
	}
	
	/**
	 * Gets the associated torrent
	 * @return The torrent with this info
	 */
	public Torrent getTorrent() {
		return torrent;
	}

}
