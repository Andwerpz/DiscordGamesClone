package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import impulse2d.Body;
import impulse2d.Circle;
import impulse2d.ImpulseScene;
import impulse2d.Polygon;
import impulse2d.Shape;
import state.RocketLeagueState;
import util.Vec2;

public class ServerRocketLeagueInterface extends ServerGameInterface {

	//we're calling the little cylinders 'pegs'. 

	//can one player only get one peg?
	//letting the user know when they are hovering over one of their pegs is... hmm

	private ImpulseScene impulseScene;

	private HashMap<Integer, Body> pegs;
	private boolean addPlayerPegs = false; //this is true only once, when the game is first started

	private HashMap<Integer, Integer> pegToPlayer;
	private HashMap<Integer, Integer> pegTypes;

	private boolean batchLaunches = false;
	private HashMap<Integer, Vec2> batchedLaunches; //pegID, forceVec
	private HashMap<Integer, Boolean> launchStillDragging; //if it's still being dragged, don't apply the change. 

	private HashMap<Integer, Integer> playerTeams; //playerID, team
	private boolean assignTeams = false; //if true, will tell everyone once what team they are on

	public ServerRocketLeagueInterface(GameServer server) {
		super(server);

		this.impulseScene = new ImpulseScene();
		this.impulseScene.setDoGravity(false);
		this.impulseScene.setDoCollision(true);
		this.impulseScene.setSimulateOnSurface(true);
		this.impulseScene.setSurfaceFrictionCoefficient(4f);

		//create the walls for the soccer field
		float x1 = -20;
		float x2 = 20;
		float z1 = -10;
		float z2 = 10;

		float fieldWidth = 40;
		float fieldHeight = 20;

		float goalWidth = 1.5f * 2;
		float goalHeight = 4.31f * 2;

		Body topWall = new Body(new Polygon(fieldWidth, 2f), 0, z1 - 1f);
		Body bottomWall = new Body(new Polygon(fieldWidth, 2f), 0, z2 + 1f);

		Body leftTopWall = new Body(new Polygon(goalWidth, (fieldHeight - goalHeight) / 2), x1 - goalWidth / 2, z1 + (fieldHeight - goalHeight) / 4);
		Body leftBottomWall = new Body(new Polygon(goalWidth, (fieldHeight - goalHeight) / 2), x1 - goalWidth / 2, z2 - (fieldHeight - goalHeight) / 4);

		Body rightTopWall = new Body(new Polygon(goalWidth, (fieldHeight - goalHeight) / 2), x2 + goalWidth / 2, z1 + (fieldHeight - goalHeight) / 4);
		Body rightBottomWall = new Body(new Polygon(goalWidth, (fieldHeight - goalHeight) / 2), x2 + goalWidth / 2, z2 - (fieldHeight - goalHeight) / 4);

		Body leftGoalWall = new Body(new Polygon(2f, goalHeight), x1 - goalWidth - 1f, 0);
		Body rightGoalWall = new Body(new Polygon(2f, goalHeight), x2 + goalWidth + 1f, 0);

		topWall.setStatic();
		topWall.setOrient(0);
		bottomWall.setStatic();
		bottomWall.setOrient(0);
		leftTopWall.setStatic();
		leftTopWall.setOrient(0);
		leftBottomWall.setStatic();
		leftBottomWall.setOrient(0);
		rightTopWall.setStatic();
		rightTopWall.setOrient(0);
		rightBottomWall.setStatic();
		rightBottomWall.setOrient(0);
		leftGoalWall.setStatic();
		leftGoalWall.setOrient(0);
		rightGoalWall.setStatic();
		rightGoalWall.setOrient(0);

		topWall.setRestitution(1f);
		bottomWall.setRestitution(1f);
		leftTopWall.setRestitution(1f);
		leftBottomWall.setRestitution(1f);
		rightTopWall.setRestitution(1f);
		rightBottomWall.setRestitution(1f);
		leftGoalWall.setRestitution(1f);
		rightGoalWall.setRestitution(1f);

		this.impulseScene.addBody(topWall);
		this.impulseScene.addBody(bottomWall);
		this.impulseScene.addBody(leftTopWall);
		this.impulseScene.addBody(leftBottomWall);
		this.impulseScene.addBody(rightTopWall);
		this.impulseScene.addBody(rightBottomWall);
		this.impulseScene.addBody(leftGoalWall);
		this.impulseScene.addBody(rightGoalWall);

		this.pegs = new HashMap<>();
		this.pegToPlayer = new HashMap<>();
		this.pegTypes = new HashMap<>();
		for (int playerID : this.server.getPlayersInGame()) {
			//give each player 1 peg to control
			for (int i = 0; i < 10; i++) {
				int pegID = this.generatePegID();
				int pegType = RocketLeagueState.PEG_TYPE_OCTANE;
				Body pegBody = new Body(new Circle(1f), (Math.random() - 0.5f) * 20, (Math.random() - 0.5f) * 20);
				pegBody.setMass(RocketLeagueState.pegTypeMasses.get(pegType));
				pegBody.setRestitution(0.7f);

				this.pegs.put(pegID, pegBody);
				this.pegToPlayer.put(pegID, playerID);
				this.pegTypes.put(pegID, pegType);

				this.impulseScene.addBody(pegBody);
			}
		}

		{
			Body pegBody = new Body(new Circle(1f), (Math.random() - 0.5f) * 20, (Math.random() - 0.5f) * 20);
			pegBody.setMass(1);
			pegBody.setRestitution(1f);
			int pegType = RocketLeagueState.PEG_TYPE_BALL;

			this.pegs.put(RocketLeagueState.BALL_ID, pegBody);
			this.pegToPlayer.put(RocketLeagueState.BALL_ID, -1);
			this.pegTypes.put(RocketLeagueState.BALL_ID, pegType);

			this.impulseScene.addBody(pegBody);
		}

		this.addPlayerPegs = true;

		this.batchedLaunches = new HashMap<>();
		this.launchStillDragging = new HashMap<>();

		this.playerTeams = new HashMap<>();
		this.assignTeams = true;
		for (int playerID : this.server.getPlayersInGame()) {
			this.playerTeams.put(playerID, RocketLeagueState.TEAM_WHITE);
		}
	}

	@Override
	public void update() {
		this.impulseScene.tick();

		if (!this.batchLaunches) {
			HashSet<Integer> remove = new HashSet<Integer>();
			for (int pegID : this.batchedLaunches.keySet()) {
				if (this.launchStillDragging.get(pegID)) {
					continue;
				}

				Body b = this.pegs.get(pegID);
				Vec2 forceVec = this.batchedLaunches.get(pegID);
				b.applyImpulse(forceVec, new Vec2(0));
				remove.add(pegID);
			}

			for (int pegID : remove) {
				this.batchedLaunches.remove(pegID);
				this.launchStillDragging.remove(pegID);
			}
		}

		for (int pegID : this.pegs.keySet()) {
			Body b = this.pegs.get(pegID);
			switch (this.pegTypes.get(pegID)) {
			case RocketLeagueState.PEG_TYPE_DINGUS:
				b.angularVelocity = (float) Math.PI / 2;
				break;
			}
		}
	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		if (this.assignTeams) {
			packetSender.startSection("rocket_league_assign_teams");
			packetSender.write(this.playerTeams.size());
			for (int playerID : this.playerTeams.keySet()) {
				packetSender.write(playerID);
				packetSender.write(this.playerTeams.get(playerID));
			}
		}

		//run once at the start of the game. 
		if (this.addPlayerPegs) {
			packetSender.startSection("rocket_league_add_pegs");
			packetSender.write(this.pegs.size());
			for (int pegID : this.pegs.keySet()) {
				packetSender.write(pegID);
				packetSender.write(this.pegTypes.get(pegID));
				packetSender.write(this.pegToPlayer.get(pegID));
			}
		}

		//TODO changing peg types

		//sends the current state of the physics scene
		packetSender.startSection("rocket_league_peg_info");
		packetSender.write(this.pegs.size());
		for (int id : this.pegs.keySet()) {
			packetSender.write(id);

			Body b = this.pegs.get(id);
			packetSender.write(b.position);
			packetSender.write(b.velocity);
			packetSender.write(b.orient);
		}

		packetSender.startSection("rocket_league_batched_launches");
		packetSender.write(this.batchedLaunches.size());
		for (int pegID : this.batchedLaunches.keySet()) {
			packetSender.write(pegID);
			packetSender.write(this.batchedLaunches.get(pegID));
		}

	}

	@Override
	public void writePacketEND() {
		this.addPlayerPegs = false;
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "rocket_league_write_launch": {
			int pegID = packetListener.readInt();
			Vec2 launchVec = packetListener.readVec2();
			boolean stillDragging = packetListener.readInt() == 1;
			this.batchedLaunches.put(pegID, launchVec);
			this.launchStillDragging.put(pegID, stillDragging);
			break;
		}
		}
	}

	private int generatePegID() {
		int id = 0;
		while (id == 0 || this.pegs.containsKey(id)) {
			id = (int) (Math.random() * 1000000);
		}
		return id;
	}

}
