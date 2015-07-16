package torrent.protocol.messages.ut_metadata;

import java.io.InvalidObjectException;
import java.util.HashMap;

import torrent.encoding.Bencode;
import torrent.encoding.Bencoder;
import torrent.network.InStream;
import torrent.network.OutStream;
import torrent.protocol.IMessage;

public abstract class Message implements IMessage {

	protected HashMap<String, Object> dictionary;
	protected String bencodedData;

	public Message() {
		bencodedData = "";
	}

	public Message(int piece) {
		Bencoder encode = new Bencoder();
		encode.dictionaryStart();
		encode.string("msg_type");
		encode.integer(getId());
		encode.string("piece");
		encode.integer(piece);
		encode.dictionaryEnd();
		bencodedData = encode.getBencodedData();
	}

	@Override
	public void write(OutStream outStream) {
		outStream.writeString(bencodedData);
	}

	@Override
	public void read(InStream inStream) {
		Bencode decoder = new Bencode(inStream.readString(inStream.available()));
		try {
			dictionary = decoder.decodeDictionary();
			inStream.moveBack(decoder.remainingChars());
		} catch (InvalidObjectException e) {
		}
	}

	@Override
	public void setReadDuration(int duration) {
	}

}
