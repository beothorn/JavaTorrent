package torrent.download.tracker;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.johnnei.utils.config.Config;

import torrent.download.Torrent;
import torrent.download.peer.PeerConnectInfo;
import torrent.network.InStream;
import torrent.network.OutStream;
import torrent.network.UdpUtils;

public class TrackerConnection {

	public static final int ACTION_CONNECT = 0;
	public static final int ACTION_ANNOUNCE = 1;
	public static final int ACTION_SCRAPE = 2;
	public static final int ACTION_ERROR = 3;
	public static final int ACTION_TRANSACTION_ID_ERROR = 256;
	
	public static final int EVENT_NONE = 0; 
	public static final int EVENT_COMPLETED = 1;
	public static final int EVENT_STARTED = 2;
	public static final int EVENT_STOPPED = 3;
	
	public static final long NO_CONNECTION_ID = 0x41727101980L;

	public static final String ERROR_CONNECTION_ID = "Connection ID missmatch.";
	
	private Logger log;

	private InetAddress address;
	private String name;
	private int port;
	private DatagramSocket socket;

	private long connectionId;
	private int action;

	private String status;
	
	/**
	 * The pool of {@link PeerConnector} which will connect peers for us
	 */
	private PeerConnectorPool connectorPool;
	
	private TrackerManager manager;

	public TrackerConnection(Logger log, String url, PeerConnectorPool connectorPool, TrackerManager manager) {
		this.connectorPool = connectorPool;
		this.manager = manager;
		this.log = log;
		connectionId = NO_CONNECTION_ID;
		String[] urlData = url.split(":");
		if (!urlData[0].equals("udp")) {
			System.err.println("Only UDP trackers are supported: " + url);
		} else {
			try {
				name = urlData[1] + ":" + urlData[2];
				address = InetAddress.getByName(urlData[1].substring(2));
				port = Integer.parseInt(urlData[2].split("/")[0]);
				status = "Waiting";
			} catch (Exception e) {
				address = null;
				status = "Failed to parse address";
				log.warning(String.format("Failed to resolve tracker: %s", e.getMessage()));
			}
		}
	}

	public boolean isConnected() {
		return connectionId != NO_CONNECTION_ID;
	}

	public void connect() throws TrackerException {
		setStatus("Connecting");
		int transactionId = manager.getTransactionId();
		OutStream outStream = new OutStream();
		try {
			outStream.writeLong(connectionId);
			outStream.writeInt(ACTION_CONNECT);
			outStream.writeInt(transactionId);
			if (socket == null) {
				socket = new DatagramSocket();
				socket.setSoTimeout(15000);
			}
			UdpUtils.write(socket, address, port, outStream);
			InStream inStream = UdpUtils.read(socket);
			action = inStream.readInt();
			if (inStream.readInt() != transactionId) {
				action = ACTION_TRANSACTION_ID_ERROR;
			}
			if (action != ACTION_CONNECT) {
				String error = inStream.readString(inStream.available());
				setStatus("Connection failed");
				throw new TrackerException("Tracker responded with an error: " + error);
			} else {
				connectionId = inStream.readLong();
				setStatus("Connected");
			}
		} catch (IOException e) {
			socket = null;
			setStatus("Connection failed");
			throw new TrackerException(String.format("Tracker Packet got lost: %s", e.getMessage()));
		}
	}

	/**
	 * Announces the torrent to the tracker and returns the announceInterval
	 * @param torrentInfo the torrent to announce
	 * @return The interval report by tracker or {@link Tracker#DEFAULT_ANNOUNCE_INTERVAL} on error
	 */
	public int announce(TorrentInfo torrentInfo) throws TrackerException {
		int connectorCapacity = connectorPool.getFreeCapacity();
		if (connectorCapacity == 0) {
			log.info("Ignored announce, connector is full.");
			return (int) TimeUnit.SECONDS.toMillis(30);
		}
		
		Torrent torrent = torrentInfo.getTorrent();
		setStatus("Announcing");
		int transactionId = manager.getTransactionId();
		OutStream outStream = new OutStream();
		outStream.writeLong(connectionId);
		outStream.writeInt(ACTION_ANNOUNCE);
		outStream.writeInt(transactionId);
		outStream.writeByte(torrent.getHashArray());
		outStream.writeByte(manager.getPeerId());
		outStream.writeLong(torrent.getDownloadedBytes()); // Downloaded Bytes
		if (torrent.getFiles() != null) {
			outStream.writeLong(torrent.getFiles().countRemainingBytes()); // Bytes left
		} else {
			outStream.writeLong(0); // Bytes left
		}
		outStream.writeLong(torrent.getUploadedBytes()); // Uploaded bytes
		int event = torrentInfo.getEvent();
		outStream.writeInt(event);
		if(event != EVENT_NONE) {
			torrentInfo.setEvent(EVENT_NONE);
		}
		outStream.writeInt(0); // Use sender ip
		outStream.writeInt(new Random().nextInt());
		outStream.writeInt(Math.min(connectorCapacity, torrent.peersWanted())); // Use defaults num_want (-1) Use the max our buffer can hold
		outStream.writeShort(Config.getConfig().getInt("download-port"));
		outStream.writeShort(0); // No extensions
		try {
			UdpUtils.write(socket, address, port, outStream);
			InStream inStream = UdpUtils.read(socket);
			action = inStream.readInt();
			if (transactionId != inStream.readInt())
				action = ACTION_TRANSACTION_ID_ERROR;
			if (action != ACTION_ANNOUNCE) {
				String error = inStream.readString(inStream.available());
				log.warning(String.format("Announce failed with error: %d, Message: %s", action, error));
				setStatus("Announce failed");
				handleError(error);
				throw new TrackerException("Tracker responded with an error: " + error);
			}
			int announceInterval = inStream.readInt();
			int leechers = inStream.readInt();
			int seeders = inStream.readInt();
			torrentInfo.setInfo(seeders, leechers);
			while (inStream.available() >= 6) {
				byte[] address = new byte[4];
				inStream.readFully(address);
				int port = inStream.readUnsignedShort();
				if (isEmptyIP(address))
					continue;
				
				InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByAddress(address), port);
				PeerConnectInfo peerInfo = new PeerConnectInfo(torrent, socketAddress);
				
				connectorPool.addPeer(peerInfo);
			}
			setStatus("Announced");
			return announceInterval;
		} catch (IOException e) {
			setStatus("Announce failed");
			throw new TrackerException("Tracker Packet got lost");
		}
	}

	public void scrape(TorrentInfo torrentInfo) throws TrackerException {
		Torrent torrent = torrentInfo.getTorrent();
		setStatus("Scraping");
		int transactionId = manager.getTransactionId();
		OutStream outStream = new OutStream();
		outStream.writeLong(connectionId);
		outStream.writeInt(ACTION_SCRAPE);
		outStream.writeInt(transactionId);
		outStream.writeByte(torrent.getHashArray());
		try {
			UdpUtils.write(socket, address, port, outStream);
			InStream inStream = UdpUtils.read(socket);
			action = inStream.readInt();
			if (inStream.readInt() != transactionId) {
				action = ACTION_TRANSACTION_ID_ERROR;
			} if (action == ACTION_SCRAPE) {
				int seeders = inStream.readInt();
				int downloaded = inStream.readInt();
				int leechers = inStream.readInt();
				torrentInfo.setInfo(seeders, leechers, downloaded);
				setStatus("Scraped");
			} else {
				String error = inStream.readString(inStream.available());
				setStatus("Scrape failed");
				handleError(error);
				throw new TrackerException("Tracker responded with an error: " + error);
			}
		} catch (IOException e) {
			throw new TrackerException("Tracker Packet got lost");
		}
	}

	private void handleError(String error) {
		if (error.startsWith(ERROR_CONNECTION_ID)) {
			connectionId = NO_CONNECTION_ID;
		}
	}

	private boolean isEmptyIP(byte[] address) {
		for (int i = 0; i < address.length; i++) {
			if (address[i] != 0)
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return address.getHostAddress();
	}

	private void setStatus(String s) {
		status = s;
	}

	public String getStatus() {
		return status;
	}
	
	public InetAddress getAddress() {
		return address;
	}

	public String getTrackerName() {
		return name;
	}

}
