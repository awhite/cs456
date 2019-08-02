import java.io.File;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver {
	// Log streams
	private PrintStream arrivalStream, dataStream;

	private int expectedSeqNum;
	private int lastAckSeqNum;
	private DatagramSocket socket;

	private InetAddress netEmuAddr;
	private int netEmuPort;
	private int receiverPort;

	public Receiver(String[] args) throws Exception {
		this.expectedSeqNum = 0;
		this.lastAckSeqNum = -1;
		this.netEmuPort = Integer.parseInt(args[1]);
		this.receiverPort = Integer.parseInt(args[2]);

		arrivalStream = new PrintStream(new File("arrival.log"));
		dataStream = new PrintStream(new File(args[3]));

		this.socket = new DatagramSocket(receiverPort);
		this.netEmuAddr = InetAddress.getByName(args[0]);
	}

	public void listen() throws Exception {
		byte[] bytes = new byte[512];
		DatagramPacket datagram = new DatagramPacket(bytes, bytes.length);
		while (true) {
			socket.receive(datagram);
			Packet packet = Packet.parseUDPdata(bytes);
			if (packet.getType() == Packet.DATA) {
				onDataPacketReceived(packet);
			} else if (packet.getType() == Packet.EOT) {
				onEotReceived(packet.getSeqNum());
				arrivalStream.close();
				dataStream.close();
				System.exit(0);
			}
		}
	}

	private void onDataPacketReceived(Packet dataPacket) throws Exception {
		int seqNum = dataPacket.getSeqNum();
		byte[] bytes = dataPacket.getData();
		arrivalStream.println(seqNum);
		int ackSeqNum = expectedSeqNum;
		if (seqNum == expectedSeqNum) {
			dataStream.print(new String(bytes));
			expectedSeqNum++;
			expectedSeqNum %= Packet.SeqNumModulo;
		} else {
			ackSeqNum = lastAckSeqNum;
		}
		sendAck(ackSeqNum);
	}

	private void onEotReceived(int seqNum) throws Exception {
		sendEot(seqNum);
	}

	private void sendAck(int seqNum) throws Exception {
		Packet ack = Packet.createACK(seqNum);
		lastAckSeqNum = seqNum;
		send(ack);
	}

	private void sendEot(int seqNum) throws Exception {
		Packet eot = Packet.createEOT(seqNum);
		send(eot);
	}

	private void send(Packet packet) throws Exception {
		byte[] bytes = packet.getUDPdata();
		DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, netEmuAddr, netEmuPort);
		socket.send(datagram);
	}

	public static void main(String[] args) throws Exception {
		Receiver receiver = new Receiver(args);
		receiver.listen();
	}
}
