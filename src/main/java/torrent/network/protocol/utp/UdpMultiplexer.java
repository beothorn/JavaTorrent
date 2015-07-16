package torrent.network.protocol.utp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.johnnei.utils.ConsoleLogger;
import org.johnnei.utils.config.Config;

import torrent.network.Stream;
import torrent.network.protocol.UtpSocket;
import torrent.network.protocol.utp.packet.Packet;
import torrent.util.tree.BinarySearchTree;

public class UdpMultiplexer extends Thread {

	private static UdpMultiplexer instance = new UdpMultiplexer();
	
	public static UdpMultiplexer getInstance() {
		return instance;
	}
	
	private final Object BST_LOCK = new Object();
	/**
	 * The Factory to create the packet instances<br/>
	 * If JavaTorrent will need to update the protocol then we can use multiple factory's to create the correct version of the packet
	 */
	private UtpPacketFactory packetFactory;
	/**
	 * The socket on which the udp packet will be received and send
	 */
	private DatagramSocket multiplexerSocket;
	/**
	 * All {@link UtpSocket}s which have registered to listen for packets
	 */
	private BinarySearchTree<UtpSocket> utpSockets;
	
	private UdpMultiplexer() {
		super("UdpMultiplexer");
		utpSockets = new BinarySearchTree<>();
		packetFactory = new UtpPacketFactory();
		try {
			multiplexerSocket = new DatagramSocket(Config.getConfig().getInt("download-port"));
			new UtpSocketTimeout().start();
			start();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	/**
	 * Registers a socket to the UdpMultiplexer<br/>
	 * Packet received will only be directed to registered sockets
	 * @param socket The socket to register
	 */
	public void register(UtpSocket socket) {
		synchronized (BST_LOCK) {
			utpSockets.add(socket);
		}
	}
	
	/**
	 * Removes a socket from the UdpMultiplexer
	 * @param socket The socket to remove
	 */
	public void unregister(UtpSocket socket) {
		synchronized (BST_LOCK) {
			utpSockets.remove(socket);
		}
	}
	
	public void updateTimeout() {
		Iterator<UtpSocket> i;
		synchronized (BST_LOCK) {
			 i = utpSockets.iterator();
		}
		while(i.hasNext()) {
			UtpSocket socket = i.next();
			socket.checkTimeouts();
			socket.checkDisconnect();
		}
	}
	
	/**
	 * Tries to send the UdpPacket
	 * @param packet The packet to send
	 * @return If the send call did not throw an exception
	 */
	public synchronized boolean send(DatagramPacket packet) {
		try {
			multiplexerSocket.send(packet);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public void run() {
		Logger log = ConsoleLogger.createLogger("UDP Multiplexer", Level.INFO);
		
		while(true) {
			try {
				byte[] dataBuffer = new byte[25600]; //25kB buffer
				DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length);
				multiplexerSocket.receive(packet);
				//Check if packet is valid
				int type = dataBuffer[0] >>> 4;
				int version = dataBuffer[0] & 0x7;
				if(version == Packet.VERSION) {
					try {
						Packet utpPacket = packetFactory.getFromId(type);
						Stream inStream = new Stream(packet.getData(), packet.getOffset(), packet.getLength());
						utpPacket.read(inStream);
						UtpSocket socket = new UtpSocket(utpPacket.getConnectionId());
						socket = utpSockets.find(socket);
						if(socket != null) {
							socket.updateLastInteraction();
							utpPacket.process(socket);
						}
					} catch (IllegalArgumentException e) {
						log.fine(String.format("Invalid Packet of %d bytes with type %d (%s:%d)",
							packet.getLength(), type, packet.getAddress(), packet.getPort())
						);
					}
				} else {
					log.fine(String.format("Invalid Packet of %d bytes with version %d (%s:%d)",
						packet.getLength(), version, packet.getAddress(), packet.getPort())
					);
				}
			} catch (IOException e) {
				
			}
		}
	}

}
