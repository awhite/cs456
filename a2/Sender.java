import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

public class Sender {
	// Keep track of whether we sent the last packet to decide whether to send EOT
	private boolean lastPacketSent = false;

	// Log streams
	private PrintStream seqNumStream, ackStream;

	private DatagramSocket socket;
	private final int maxDataLength = 500;
	private final int windowSize = 10;

	private Thread receiver;

	private InetAddress netEmuAddr;
	private int netEmuPort;
	private int senderPort;
	private String filename;

	private int nextSeqNum;
	private int base;
	// Index into the strings array
	private int packetNum;

	private String[] strings;

	private Timer timer;

	public Sender(String[] args) throws Exception {
		this.netEmuPort = Integer.parseInt(args[1]);
		this.senderPort = Integer.parseInt(args[2]);
		seqNumStream = new PrintStream(new File("seqnum.log"));
		ackStream = new PrintStream(new File("ack.log"));

		this.socket = new DatagramSocket(senderPort);
		this.netEmuAddr = InetAddress.getByName(args[0]);
		this.filename = args[3];

		this.receiver = new Thread() {
			@Override
			public void run() {
				byte[] bytes = new byte[512];
				DatagramPacket ack = new DatagramPacket(bytes, bytes.length);
				while (true) {
					try {
						socket.receive(ack);
						Packet ackPacket = Packet.parseUDPdata(bytes);
						if (ackPacket.getType() == Packet.ACK) {
							onAckReceived(ackPacket.getSeqNum());
						} else if (ackPacket.getType() == Packet.EOT) {
							seqNumStream.close();
							ackStream.close();
							System.exit(0);
						}
					} catch (EotException e) {

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};

		this.base = 0;
		this.nextSeqNum = 0;
		this.packetNum = 0;
	}

	public static void main(String[] args) throws Exception {
		Sender sender = new Sender(args);

		sender.readData();
		sender.startReceiver();
		sender.sendNPackets();
	}

	public void readData() throws IOException {
		// Load data from file into memory
		FileInputStream fis = new FileInputStream(filename);
		int i = 0;
		double num = (double) fis.available() / (double) maxDataLength;
		int numberOfPackets = (int) Math.ceil(num);
		this.strings = new String[numberOfPackets];

		while (fis.available() > 0) {
			byte[] bytes = new byte[Math.min(maxDataLength, fis.available())];
			fis.read(bytes);
			String data = new String(bytes);
			this.strings[i++] = data;
		}

		fis.close();
	}

	public void startReceiver() {
		this.receiver.start();
	}

	public void sendNPackets() throws Exception {
		startTimer();
		// Send the remaining packets if it is less than the window size N, otherwise
		// send N packets
		int numPackets = Math.min(windowSize, strings.length - packetNum);
		for (int i = 0; i < numPackets; i++) {
			try {
				sendNextPacket();
			} catch (EotException e) {
				break;
			}
		}
	}

	private void sendNextPacket() throws Exception {
		Packet packet;
		try {
			packet = Packet.createPacket(nextSeqNum, strings[packetNum]);
		} catch (ArrayIndexOutOfBoundsException e) {
			return;
		}
		send(packet);
		seqNumStream.println(packet.getSeqNum());
		nextSeqNum++;
		nextSeqNum %= Packet.SeqNumModulo;
		packetNum++;
		if (packetNum == strings.length) {
			lastPacketSent = true;
		}
	}

	private void send(Packet packet) throws IOException {
		byte[] bytes = packet.getUDPdata();
		DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, netEmuAddr, netEmuPort);
		socket.send(datagram);
	}

	private void sendEot() throws Exception {
		Packet eot = Packet.createEOT(nextSeqNum);
		send(eot);
	}

	private void onAckReceived(int seqNum) throws Exception {
		ackStream.println(seqNum);
		if (seqNum >= base || seqNum - base + Packet.SeqNumModulo < windowSize) { // handle wraparound
			base = (seqNum + 1) % Packet.SeqNumModulo;
			stopTimer();
			if (lastPacketSent && seqNum == (strings.length - 1) % Packet.SeqNumModulo) {
				// Received ack for last packet, send EOT
				stopTimer();
				sendEot();
			} else {
				startTimer();
				sendNextPacket();
			}
		}
	}

	private void startTimer() {
		this.timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				nextSeqNum = base;
				packetNum -= (packetNum - base) % Packet.SeqNumModulo;
				lastPacketSent = packetNum == strings.length;
				if (!lastPacketSent) {
					try {
						sendNPackets();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}, 100);
	}

	private void stopTimer() {
		timer.cancel();
	}
}
