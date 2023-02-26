package client;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import impulse2d.Body;
import impulse2d.Circle;
import server.PacketListener;
import server.PacketSender;
import util.Vec2;
import util.Vec3;

public class ClientRocketLeagueInterface extends ClientGameInterface {

	//each peg will belong to one player, and have a type. The type of the peg determines its mass. 
	private HashMap<Integer, Body> pegs;

	private HashMap<Integer, Integer> pegToPlayer;
	private HashMap<Integer, Integer> pegTypes;

	private HashSet<Integer> addedPegs;

	private boolean launchPeg = false;
	private int launchPegID = -1;
	private Vec2 launchPegDir;

	public ClientRocketLeagueInterface(GameClient client) {
		super(client);

		this.pegs = new HashMap<>();

		this.pegToPlayer = new HashMap<>();
		this.pegTypes = new HashMap<>();

		this.addedPegs = new HashSet<>();
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		if (this.launchPeg) {
			packetSender.startSection("rocket_league_launch_peg");
			packetSender.write(this.launchPegID);
			packetSender.write(this.launchPegDir);
			this.launchPeg = false;
		}
	}

	@Override
	public void readSection(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "rocket_league_add_pegs": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int pegID = packetListener.readInt();
				int pegType = packetListener.readInt();
				int playerID = packetListener.readInt();

				Body b = new Body(new Circle(1f), 0, 0);
				this.pegs.put(pegID, b);
				this.pegToPlayer.put(pegID, playerID);
				this.pegTypes.put(pegID, pegType);
				this.addedPegs.add(pegID);
			}
			break;
		}

		case "rocket_league_set_peg_type": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int pegID = packetListener.readInt();
				int pegType = packetListener.readInt();

				this.pegTypes.put(pegID, pegType);
			}
			break;
		}

		case "rocket_league_peg_info": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int pegID = packetListener.readInt();
				Vec2 position = packetListener.readVec2();
				Vec2 velocity = packetListener.readVec2();
				float orient = packetListener.readFloat();

				Body b = this.pegs.get(pegID);
				b.setOrient(orient);
				b.setPosition(position);
				b.setVelocity(velocity);
			}
			break;
		}
		}
	}

	public void launchPeg(int pegID, Vec2 dir) {
		this.launchPeg = true;
		this.launchPegID = pegID;
		this.launchPegDir = dir;
	}

	public HashMap<Integer, Body> getPegs() {
		return this.pegs;
	}

	public HashMap<Integer, Integer> getPegToPlayer() {
		return this.pegToPlayer;
	}

	public HashMap<Integer, Integer> getPegTypes() {
		return this.pegTypes;
	}

	public HashSet<Integer> getAddedPegs() {
		HashSet<Integer> ret = new HashSet<>();
		ret.addAll(this.addedPegs);
		this.addedPegs.clear();
		return ret;
	}

}
