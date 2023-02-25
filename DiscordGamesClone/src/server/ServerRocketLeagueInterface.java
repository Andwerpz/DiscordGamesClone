package server;

import java.io.IOException;

import impulse2d.ImpulseScene;

public class ServerRocketLeagueInterface extends ServerGameInterface {

	private ImpulseScene impulseScene;

	public ServerRocketLeagueInterface(GameServer server) {
		super(server);

		this.impulseScene = new ImpulseScene();
	}

	@Override
	public void update() {
		this.impulseScene.tick();
	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacketEND() {
		// TODO Auto-generated method stub

	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		// TODO Auto-generated method stub

	}

}
