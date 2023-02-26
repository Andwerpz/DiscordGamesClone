package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

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

	private int ballID = -1; //peg id of the ball

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
				Body pegBody = new Body(new Circle(1f), (Math.random() - 0.5f) * 20, (Math.random() - 0.5f) * 20);
				pegBody.setMass(1);
				pegBody.setRestitution(0.7f);
				int pegType = RocketLeagueState.PEG_TYPE_OCTANE;

				this.pegs.put(pegID, pegBody);
				this.pegToPlayer.put(pegID, playerID);
				this.pegTypes.put(pegID, pegType);

				this.impulseScene.addBody(pegBody);
			}
		}

		{
			this.ballID = this.generatePegID();
			Body pegBody = new Body(new Circle(1f), (Math.random() - 0.5f) * 20, (Math.random() - 0.5f) * 20);
			pegBody.setMass(1);
			pegBody.setRestitution(1f);
			int pegType = RocketLeagueState.PEG_TYPE_BALL;

			this.pegs.put(this.ballID, pegBody);
			this.pegToPlayer.put(this.ballID, -1);
			this.pegTypes.put(this.ballID, pegType);

			this.impulseScene.addBody(pegBody);
		}

		this.addPlayerPegs = true;
	}

	@Override
	public void update() {
		this.impulseScene.tick();

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

	}

	@Override
	public void writePacketEND() {
		this.addPlayerPegs = false;
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "rocket_league_launch_peg": {
			int pegID = packetListener.readInt();
			Vec2 dir = packetListener.readVec2();

			Body b = this.pegs.get(pegID);
			b.applyImpulse(dir, new Vec2(0));
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
