package client;

import java.io.IOException;

import server.PacketListener;
import server.PacketSender;

public abstract class ClientGameInterface {

	protected GameClient client;

	public ClientGameInterface(GameClient client) {
		this.client = client;
	}

	public abstract void update();

	public abstract void writePacket(PacketSender packetSender);

	public abstract void readSection(PacketListener packetListener) throws IOException;
}
