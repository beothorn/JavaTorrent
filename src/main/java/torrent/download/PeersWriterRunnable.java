package torrent.download;

import java.io.IOException;

import org.johnnei.utils.ThreadUtils;

import torrent.TorrentManager;
import torrent.download.peer.Peer;
import torrent.download.peer.PeerDirection;
import torrent.network.BitTorrentSocket;

public class PeersWriterRunnable implements Runnable {
	
	private TorrentManager manager;

	public PeersWriterRunnable(TorrentManager manager) {
		this.manager = manager;
	}

	@Override
	public void run() {
		while (true) {
			for (Torrent torrent : manager.getTorrents()) {
				processTorrent(torrent);
			}
			ThreadUtils.sleep(1);
		}
	}
	
	private void processTorrent(Torrent torrent) {
		synchronized (torrent) {
			for (Peer peer : torrent.getPeers()) {
				processPeer(peer);
			}
		}
	}
	
	private void processPeer(Peer peer) {
		if (peer.getBitTorrentSocket().closed()) {
			return;
		}
		
		try {
			BitTorrentSocket socket = peer.getBitTorrentSocket();
			
			if (socket.canWriteMessage()) {
				peer.getBitTorrentSocket().sendMessage();
			} else if (peer.getWorkQueueSize(PeerDirection.Upload) > 0) {
				peer.queueNextPieceForSending();
			}
		} catch (IOException e) {
			peer.getLogger().severe(e.getMessage());
			peer.getBitTorrentSocket().close();
		}
	}
}
