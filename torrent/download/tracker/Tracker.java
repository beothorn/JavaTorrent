package torrent.download.tracker;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

import torrent.Logable;
import torrent.Manager;
import torrent.download.Torrent;
import torrent.download.peer.Peer;
import torrent.network.Stream;

public class Tracker extends Thread implements Logable {

	public static final int ACTION_CONNECT = 0;
	public static final int ACTION_ANNOUNCE = 1;
	public static final int ACTION_SCRAPE = 2;
	public static final int ACTION_ERROR = 3;
	public static final int ACTION_TRANSACTION_ID_ERROR = 256;

	public static final String ERROR_CONNECTION_ID = "Connection ID missmatch.";

	/**
	 * The Torrent attached to this tracker
	 */
	private Torrent torrent;
	private InetAddress address;
	private String name;
	private int port;
	private DatagramSocket socket;
	private Stream stream;

	private long connectionId;
	private int transactionId;
	private int action;

	private int seeders;
	private int leechers;

	// Announce Data
	private long lastAnnounce;
	private int announceInterval;

	private String status;

	public Tracker(Torrent torrent, String url) {
		super("Tracker " + url);
		this.torrent = torrent;
		stream = new Stream();
		connectionId = 0x41727101980L;
		transactionId = Manager.getTransactionId();
		String[] urlData = url.split(":");
		if (!urlData[0].equals("udp")) {
			System.err.println("Only UDP trackers are supported: " + url);
		} else {
			try {
				name = urlData[1] + ":" + urlData[2];
				address = InetAddress.getByName(urlData[1].substring(2));
				port = Integer.parseInt(urlData[2]);
				status = "";
			} catch (Exception e) {
				address = null;
				status = "Failed to parse address";
			}
		}
	}

	private boolean attemptConnect() {
		for (int i = 0; i < 3; i++) {
			connect();
			if (socket == null) {
				setStatus("Connecting (Attempt: " + (2 + i) + ")");
			} else
				return true;
		}
		return false;
	}

	@Override
	public void run() {
		lastAnnounce = System.currentTimeMillis();

		while (torrent.keepDownloading()) {
			if (connectionId == 0x41727101980L) {
				setStatus("Connecting");
				if (!attemptConnect())
					break;
			} else {
				if (lastAnnounce + announceInterval < System.currentTimeMillis() && torrent.needAnnounce()) {
					announce();
				}
				// scrape
			}
			try {
				Thread.sleep(50);
			} catch (Exception e) {
			}
		}
		setStatus("Unable to connect");
	}

	public void connect() {
		stream.reset(1000);
		stream.writeLong(connectionId);
		stream.writeInt(0x0);
		stream.writeInt(transactionId);
		try {
			if (socket == null) {
				socket = new DatagramSocket();
				socket.setSoTimeout(5000);
			}
			log("Connecting to tracker");
			socket.send(stream.write(address, port));
			stream.read(socket);
			action = stream.readInt();
			if (stream.readInt() != transactionId)
				action = ACTION_TRANSACTION_ID_ERROR;
			else
				log("Transaction ID Matched with " + transactionId);
			if (action != ACTION_CONNECT) {
				String error = stream.readString(stream.available());
				log("Tracker Error: " + action + ", Message: " + error, true);
			} else {
				connectionId = stream.readLong();
				log("Recieved Connection ID: " + connectionId);
			}
		} catch (IOException e) {
			log(e.getMessage(), true);
			socket = null;
		}
	}

	public void announce() {
		log("Announcing (Want: " + torrent.peersWanted() + ")");
		setStatus("Announcing");
		stream.reset(100);
		stream.writeLong(connectionId);
		stream.writeInt(ACTION_ANNOUNCE);
		stream.writeInt(transactionId);
		stream.writeByte(torrent.getHashArray());
		stream.writeByte(Manager.getPeerId());
		stream.writeLong(0); // Downloaded Bytes
		stream.writeLong(0); // Bytes left
		stream.writeLong(0); // Uploaded bytes
		stream.writeInt(2); // EVENT: None = 0, Completed = 1, Started = 2, Stopped = 3
		stream.writeInt(0); // Use sender ip
		stream.writeInt(new Random().nextInt());
		stream.writeInt(torrent.peersWanted()); // Use defaults num_want (-1) Use the max our buffer can hold
		stream.writeInt(socket.getLocalPort());
		try {
			socket.send(stream.write(address, port));
			stream.read(socket);
			action = stream.readInt();
			if (transactionId != stream.readInt())
				action = ACTION_TRANSACTION_ID_ERROR;
			if (action != ACTION_ANNOUNCE) {
				String error = stream.readString(stream.available());
				log("Announce failed with error: " + action + ", Message: " + error, true);
				setStatus("Announce failed");
				announceInterval = 30000;
				lastAnnounce = System.currentTimeMillis();
				handleError(error);
				return;
			}
			lastAnnounce = System.currentTimeMillis();
			announceInterval = stream.readInt();
			leechers = stream.readInt();
			seeders = stream.readInt();
			while (stream.available() >= 6) {
				byte[] address = stream.readIP();
				int port = stream.readShort();
				if (isEmptyIP(address))
					continue;
				Peer p = new Peer(torrent);
				p.setSocket(InetAddress.getByAddress(address), port);
				torrent.addPeer(p);
			}
			setStatus("Announced");
		} catch (IOException e) {

		}
	}

	private void handleError(String error) {
		if (error.startsWith(ERROR_CONNECTION_ID)) {
			connectionId = 0x41727101980L;
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
	public void log(String s, boolean error) {
		s = "[" + toString() + "] " + s;
		if (error)
			System.err.println(s);
		else
			System.out.println(s);
	}

	@Override
	public void log(String s) {
		log(s, false);
	}

	@Override
	public String toString() {
		return address.getHostAddress();
	}

	private void setStatus(String s) {
		status = s;
		setName(name + " " + status);
	}

	public int getLeechers() {
		return leechers;
	}

	public int getSeeders() {
		return seeders;
	}

	@Override
	public String getStatus() {
		return status;
	}

	public String getTrackerName() {
		return name;
	}

}
