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
	private HashSet<Integer> removedPegs;

	private HashMap<Integer, Vec2> incomingBatchedLaunches;

	private HashMap<Integer, Integer> playerTeams;
	private boolean newTeamAssignment = false;

	//write a launch to the server
	private boolean writeLaunch = false;
	private int launchPegID = -1;
	private Vec2 launchVec = new Vec2(0);
	private boolean launchStillDragging = false;

	private boolean startGame = false;

	private int teamRedScore = 0;
	private int teamBlueScore = 0;

	private boolean scored = false;
	private boolean redScored = false;

	private long inputPhaseEndMillis;

	private boolean isInGame = false;
	private boolean gameStarted = false;

	public ClientRocketLeagueInterface(GameClient client) {
		super(client);

		this.pegs = new HashMap<>();

		this.pegToPlayer = new HashMap<>();
		this.pegTypes = new HashMap<>();

		this.incomingBatchedLaunches = new HashMap<>();

		this.playerTeams = new HashMap<>();

		this.addedPegs = new HashSet<>();
		this.removedPegs = new HashSet<>();
	}

	@Override
	public void update() {

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		if (this.writeLaunch) {
			packetSender.startSection("rocket_league_write_launch");
			packetSender.write(this.launchPegID);
			packetSender.write(this.launchVec);
			packetSender.write(this.launchStillDragging ? 1 : 0);
			this.writeLaunch = false;
		}

		if (this.startGame) {
			packetSender.startSection("rocket_league_start_game");
			this.startGame = false;
		}
	}

	@Override
	public void readSection(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "rocket_league_start_game": {
			this.teamRedScore = 0;
			this.teamBlueScore = 0;
			this.isInGame = true;
			this.gameStarted = true;
			break;
		}

		case "rocket_league_end_game": {
			this.isInGame = false;
			break;
		}

		case "rocket_league_start_input_phase": {
			this.inputPhaseEndMillis = packetListener.readLong();
			break;
		}

		case "rocket_league_score": {
			this.scored = true;
			this.redScored = packetListener.readInt() == 1;
			if (this.redScored) {
				this.teamRedScore++;
			}
			else {
				this.teamBlueScore++;
			}
			break;
		}

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

		case "rocket_league_remove_pegs": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int pegID = packetListener.readInt();

				this.pegs.remove(pegID);
				this.pegToPlayer.remove(pegID);
				this.pegTypes.remove(pegID);
				this.removedPegs.add(pegID);
			}
			break;
		}

		case "rocket_league_assign_teams": {
			int amt = packetListener.readInt();
			this.playerTeams.clear();
			for (int i = 0; i < amt; i++) {
				int playerID = packetListener.readInt();
				int team = packetListener.readInt();
				this.playerTeams.put(playerID, team);
			}
			this.newTeamAssignment = true;
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

		case "rocket_league_batched_launches": {
			this.incomingBatchedLaunches.clear();
			int amt = packetListener.readInt();
			HashMap<Integer, Vec2> batchedLaunches = new HashMap<>();
			for (int i = 0; i < amt; i++) {
				int pegID = packetListener.readInt();
				Vec2 launchVec = packetListener.readVec2();
				batchedLaunches.put(pegID, launchVec);
			}
			this.incomingBatchedLaunches = batchedLaunches;
			break;
		}
		}
	}

	public int getRedScore() {
		return this.teamRedScore;
	}

	public int getBlueScore() {
		return this.teamBlueScore;
	}

	public boolean gameStarted() {
		if (!this.gameStarted) {
			return false;
		}
		this.gameStarted = false;
		return true;
	}

	public boolean isInGame() {
		return this.isInGame;
	}

	public boolean inInputPhase() {
		return System.currentTimeMillis() < this.inputPhaseEndMillis;
	}

	public long getInputPhaseEndMillis() {
		return this.inputPhaseEndMillis;
	}

	public void startGame() {
		if (this.client.isHost()) {
			this.startGame = true;
		}
	}

	public boolean hasNewTeamAssignment() {
		if (!this.newTeamAssignment) {
			return false;
		}
		this.newTeamAssignment = false;
		return true;
	}

	public HashMap<Integer, Integer> getPlayerTeams() {
		return this.playerTeams;
	}

	public HashMap<Integer, Vec2> getBatchedLaunches() {
		HashMap<Integer, Vec2> ret = new HashMap<>();
		ret.putAll(this.incomingBatchedLaunches);
		return ret;
	}

	public void writeLaunch(int pegID, Vec2 launchVec, boolean stillDragging) {
		this.writeLaunch = true;
		this.launchPegID = pegID;
		this.launchVec.set(launchVec);
		this.launchStillDragging = stillDragging;
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

	public HashSet<Integer> getRemovedPegs() {
		HashSet<Integer> ret = new HashSet<>();
		ret.addAll(this.removedPegs);
		this.removedPegs.clear();
		return ret;
	}

}
