package torrent.protocol.messages.extension;

import java.util.HashMap;

import torrent.JavaTorrent;
import torrent.download.peer.Peer;
import torrent.encoding.Bencode;
import torrent.encoding.Bencoder;
import torrent.network.InStream;
import torrent.network.OutStream;
import torrent.protocol.BitTorrent;
import torrent.protocol.IMessage;
import torrent.protocol.UTMetadata;

public class MessageHandshake implements IMessage {

	private String bencodedHandshake;

	public MessageHandshake(long metadataSize) {
		Bencoder encoder = new Bencoder();
		encoder.dictionaryStart();
		encoder.string("m");
		encoder.dictionaryStart();
		encoder.string("ut_metadata");
		encoder.integer(UTMetadata.ID);
		encoder.dictionaryEnd();
		encoder.string("v");
		encoder.string(JavaTorrent.BUILD);
		if (metadataSize > 0) {
			encoder.string("metadata_size");
			encoder.integer(metadataSize);
		}
		encoder.dictionaryEnd();
		bencodedHandshake = encoder.getBencodedData();
	}

	public MessageHandshake() {
		this(0);
	}

	@Override
	public void write(OutStream outStream) {
		outStream.writeString(bencodedHandshake);
	}

	@Override
	public void read(InStream inStream) {
		bencodedHandshake = inStream.readString(inStream.available());
	}

	@Override
	public void process(Peer peer) {
		Bencode decoder = new Bencode(bencodedHandshake);
		try {
			HashMap<String, Object> dictionary = (HashMap<String, Object>) decoder.decodeDictionary();
			Object m = dictionary.get("m");
			if (m != null) {
				if (m instanceof HashMap<?, ?>) {
					HashMap<?, ?> extensionData = (HashMap<?, ?>) m;
					if (extensionData.containsKey(UTMetadata.NAME)) {
						peer.getExtensions().register(UTMetadata.NAME, (Integer) extensionData.get(UTMetadata.NAME));
						if (dictionary.containsKey("metadata_size")) {
							peer.getTorrent().setMetadataSize((int) dictionary.get("metadata_size"));
						}
					}
				}
			}
			Object reqq = dictionary.get("reqq");
			if (reqq != null) {
				peer.setAbsoluteRequestLimit((int) reqq);
			}
			Object v = dictionary.get("v");
			if (v != null) {
				peer.setClientName((String) v);
			}
		} catch (Exception e) {
			e.printStackTrace();
			peer.getLogger().severe("Extension handshake error: " + e.getMessage());
			peer.getBitTorrentSocket().close();
		}
	}

	@Override
	public int getLength() {
		return bencodedHandshake.length();
	}

	@Override
	public int getId() {
		return BitTorrent.EXTENDED_MESSAGE_HANDSHAKE;
	}

	@Override
	public void setReadDuration(int duration) {
	}

	@Override
	public String toString() {
		return "Handshake";
	}

}
