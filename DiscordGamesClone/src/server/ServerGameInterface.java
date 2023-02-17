package server;

import java.io.IOException;

public abstract class ServerGameInterface {

	protected GameServer server;

	public ServerGameInterface(GameServer server) {
		this.server = server;
	}

	public abstract void update();

	public abstract void writePacket(PacketSender packetSender, int clientID);

	public abstract void writePacketEND();

	public abstract void readSection(PacketListener packetListener, int clientID) throws IOException;

}
