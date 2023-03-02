package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import impulse2d.Body;
import impulse2d.Circle;
import impulse2d.ImpulseMath;
import impulse2d.ImpulseScene;
import impulse2d.Polygon;
import impulse2d.Shape;
import state.RocketLeagueState;
import util.MathUtils;
import util.Pair;
import util.Vec2;

public class ServerRocketLeagueInterface extends ServerGameInterface {

	//we're calling the little cylinders 'pegs'. 

	//can one player only get one peg?
	//letting the user know when they are hovering over one of their pegs is... hmm

	private ImpulseScene impulseScene;

	private HashMap<Integer, Body> pegs;

	private ArrayList<Integer> addPegList;
	private ArrayList<Integer> removePegList;

	private HashMap<Integer, Integer> pegToPlayer;
	private HashMap<Integer, Integer> pegTypes;

	private boolean batchLaunches = false;
	private HashMap<Integer, Vec2> batchedLaunches; //pegID, forceVec
	private HashMap<Integer, Boolean> launchStillDragging; //if it's still being dragged, don't apply the change. 

	private HashMap<Integer, Integer> playerTeams; //playerID, team
	private boolean assignTeams = false; //if true, will tell everyone once what team they are on

	private boolean startGame = false;
	private boolean startInputPhase = false;

	private boolean endGame = false;

	private boolean isInGame = false;
	private boolean isInputPhase = false; //where everyone batches their inputs
	private long inputPhaseEndMillis;
	private long inputPhaseDurationMillis = 10 * 1000;

	private static final int WINNING_SCORE = 5;

	private int teamRedScore = 0;
	private int teamBlueScore = 0;
	private boolean scored = false;
	private boolean redScored = false;
	private boolean repositionPegsOnInputPhase = false;
	private boolean hasScoredThisRound = false;

	private int ballID = -1;

	private static float fieldLeftBound = -20f;
	private static float fieldRightBound = 20f;

	private HashMap<Integer, Pair<Vec2, Integer>> powerups;
	private ArrayList<Integer> addPowerupList;
	private ArrayList<Integer> removePowerupList;

	private ArrayList<Pair<Integer, Integer>> assignPegTypeList; //pegID, type

	private int roundCnt = 0;

	public ServerRocketLeagueInterface(GameServer server) {
		super(server);

		this.impulseScene = new ImpulseScene();
		this.impulseScene.setDoGravity(false);
		this.impulseScene.setDoCollision(true);
		this.impulseScene.setSimulateOnSurface(true);
		this.impulseScene.setSurfaceFrictionCoefficient(4f);

		//create the walls for the soccer field
		float x1 = fieldLeftBound;
		float x2 = fieldRightBound;
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

		topWall.staticFriction = 0.2f;
		topWall.dynamicFriction = 0.2f;
		bottomWall.staticFriction = 0.2f;
		bottomWall.dynamicFriction = 0.2f;
		leftTopWall.staticFriction = 0.2f;
		leftTopWall.dynamicFriction = 0.2f;
		leftBottomWall.staticFriction = 0.2f;
		leftBottomWall.dynamicFriction = 0.2f;
		rightTopWall.staticFriction = 0.2f;
		rightTopWall.dynamicFriction = 0.2f;
		rightBottomWall.staticFriction = 0.2f;
		rightBottomWall.dynamicFriction = 0.2f;
		leftGoalWall.staticFriction = 0.2f;
		leftGoalWall.dynamicFriction = 0.2f;
		rightGoalWall.staticFriction = 0.2f;
		rightGoalWall.dynamicFriction = 0.2f;

		this.impulseScene.addBody(topWall);
		this.impulseScene.addBody(bottomWall);
		this.impulseScene.addBody(leftTopWall);
		this.impulseScene.addBody(leftBottomWall);
		this.impulseScene.addBody(rightTopWall);
		this.impulseScene.addBody(rightBottomWall);
		this.impulseScene.addBody(leftGoalWall);
		this.impulseScene.addBody(rightGoalWall);

		this.addPegList = new ArrayList<>();
		this.removePegList = new ArrayList<>();

		this.batchedLaunches = new HashMap<>();
		this.launchStillDragging = new HashMap<>();

		this.powerups = new HashMap<>();
		this.addPowerupList = new ArrayList<>();
		this.removePowerupList = new ArrayList<>();

		this.assignPegTypeList = new ArrayList<>();

		this.playerTeams = new HashMap<>();
		this.assignTeams = true;
		for (int playerID : this.server.getPlayersInGame()) {
			this.playerTeams.put(playerID, RocketLeagueState.TEAM_WHITE);
		}

		this.pegs = new HashMap<>();
		this.pegToPlayer = new HashMap<>();
		this.pegTypes = new HashMap<>();
		for (int playerID : this.server.getPlayersInGame()) {
			//give each player 1 peg to control
			for (int i = 0; i < 1; i++) {
				int pegID = this.addPeg(playerID, RocketLeagueState.getRandomBasicPegType());
			}
		}

		{
			this.ballID = this.addPeg(-1, RocketLeagueState.PEG_TYPE_BALL);
			Body pegBody = this.pegs.get(this.ballID);
			pegBody.setMass(1);
			pegBody.setRestitution(1f);
		}

		this.repositionPegs();
	}

	@Override
	public void update() {
		this.impulseScene.tick();

		//apply powerups
		HashSet<Integer> usedPowerups = new HashSet<>();
		for (int powerupID : this.powerups.keySet()) {
			Vec2 powerupPos = this.powerups.get(powerupID).first;
			int powerupType = this.powerups.get(powerupID).second;
			for (int pegID : this.pegs.keySet()) {
				if (pegID == this.ballID) {
					continue;
				}

				Vec2 pegPos = this.pegs.get(pegID).position;
				float dist = powerupPos.distance(pegPos);
				if (dist < 1.2f) {
					//give powerup to player
					this.assignPegType(pegID, powerupType);
					usedPowerups.add(powerupID);
					break;
				}
			}
		}
		for (int powerupID : usedPowerups) {
			this.removePowerup(powerupID);
		}

		boolean allStopped = true;
		for (int pegID : this.pegs.keySet()) {
			Body b = this.pegs.get(pegID);

			if (b.velocity.length() > 0.01f) {
				allStopped = false;
			}

			switch (this.pegTypes.get(pegID)) {
			case RocketLeagueState.PEG_TYPE_DINGUS:
				b.angularVelocity = (float) Math.PI / 2;
				break;
			}
		}

		if (this.isInGame) {
			if (this.isInputPhase) {
				//TODO see if everyone has batched an input

				if (this.inputPhaseEndMillis < System.currentTimeMillis()) {
					this.endInputPhase();
				}
			}
			else {
				if (allStopped) {
					this.startInputPhase();
				}

				//check to see if someone scored
				if (!this.hasScoredThisRound) {
					Body ball = this.pegs.get(this.ballID);
					if (ball.position.x < fieldLeftBound) {
						this.teamRedScore++;
						this.scored = true;
						this.redScored = true;
						this.repositionPegsOnInputPhase = true;
						this.hasScoredThisRound = true;
					}
					if (ball.position.x > fieldRightBound) {
						this.teamBlueScore++;
						this.scored = true;
						this.redScored = false;
						this.repositionPegsOnInputPhase = true;
						this.hasScoredThisRound = true;
					}
				}

			}
		}

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
	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		if (this.startGame) {
			packetSender.startSection("rocket_league_start_game");
		}

		if (this.endGame) {
			packetSender.startSection("rocket_league_end_game");
		}

		if (this.startInputPhase) {
			packetSender.startSection("rocket_league_start_input_phase");
			packetSender.write(this.inputPhaseEndMillis);
		}

		if (this.scored) {
			packetSender.startSection("rocket_league_score");
			packetSender.write(this.redScored ? 1 : 0);
		}

		if (this.assignTeams) {
			packetSender.startSection("rocket_league_assign_teams");
			packetSender.write(this.playerTeams.size());
			for (int playerID : this.playerTeams.keySet()) {
				packetSender.write(playerID);
				packetSender.write(this.playerTeams.get(playerID));
			}
		}

		//run once at the start of the game. 
		if (this.addPegList.size() != 0) {
			packetSender.startSection("rocket_league_add_pegs");
			packetSender.write(this.addPegList.size());
			for (int pegID : this.addPegList) {
				packetSender.write(pegID);
				packetSender.write(this.pegTypes.get(pegID));
				packetSender.write(this.pegToPlayer.get(pegID));
			}
		}

		if (this.removePegList.size() != 0) {
			packetSender.startSection("rocket_league_remove_pegs");
			packetSender.write(this.removePegList.size());
			for (int pegID : this.removePegList) {
				packetSender.write(pegID);
			}
		}

		if (this.assignPegTypeList.size() != 0) {
			packetSender.startSection("rocket_league_assign_peg_type");
			packetSender.write(this.assignPegTypeList.size());
			for (Pair<Integer, Integer> p : this.assignPegTypeList) {
				packetSender.write(p.first);
				packetSender.write(p.second);
			}
		}

		if (this.addPowerupList.size() != 0) {
			packetSender.startSection("rocket_league_add_powerup");
			packetSender.write(this.addPowerupList.size());
			for (int powerupID : this.addPowerupList) {
				packetSender.write(powerupID);
				packetSender.write(this.powerups.get(powerupID).first);
				packetSender.write(this.powerups.get(powerupID).second);
			}
		}

		if (this.removePowerupList.size() != 0) {
			packetSender.startSection("rocket_league_remove_powerup");
			packetSender.write(this.removePowerupList.size());
			for (int powerupID : this.removePowerupList) {
				packetSender.write(powerupID);
			}
		}

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
		this.addPegList.clear();
		this.removePegList.clear();

		this.assignTeams = false;
		this.scored = false;
		this.startGame = false;
		this.endGame = false;

		this.addPowerupList.clear();
		this.removePowerupList.clear();

		this.assignPegTypeList.clear();
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

		case "rocket_league_start_game": {
			if (!this.isInGame) {
				this.initializeGame();
			}
			break;
		}
		}
	}

	private void startInputPhase() {
		if (this.teamRedScore >= WINNING_SCORE || this.teamBlueScore >= WINNING_SCORE) {
			this.endGame();
			return;
		}

		this.isInputPhase = true;
		this.batchLaunches = true;
		this.inputPhaseEndMillis = System.currentTimeMillis() + this.inputPhaseDurationMillis;
		this.startInputPhase = true;

		if (this.roundCnt % 3 == 0) {
			this.addPowerup();
		}

		if (this.repositionPegsOnInputPhase) {
			this.repositionPegs();
			this.repositionPegsOnInputPhase = false;
		}
	}

	private void endInputPhase() {
		this.isInputPhase = false;
		this.batchLaunches = false;
		this.hasScoredThisRound = false;

		this.roundCnt++;
	}

	private void initializeGame() {
		this.startGame = true;

		this.teamRedScore = 0;
		this.teamBlueScore = 0;

		this.roundCnt = 0;

		//assign teams
		this.playerTeams.clear();
		this.assignTeams = true;
		for (int pegID : this.pegs.keySet()) {
			int pegType = this.pegTypes.get(pegID);
			if (pegType == RocketLeagueState.PEG_TYPE_BALL) {
				continue;
			}

			int playerID = this.pegToPlayer.get(pegID);
			Body b = this.pegs.get(pegID);
			if (b.position.x < 0) {
				this.playerTeams.put(playerID, RocketLeagueState.TEAM_BLUE);
			}
			else {
				this.playerTeams.put(playerID, RocketLeagueState.TEAM_RED);
			}
		}

		//give everyone an extra peg
		for (int playerID : this.server.getPlayersInGame()) {
			this.addPeg(playerID, RocketLeagueState.getRandomBasicPegType());
		}

		System.out.println(this.pegToPlayer);
		System.out.println(this.pegTypes);

		this.repositionPegs();

		this.isInGame = true;
		this.startInputPhase();
	}

	private void endGame() {
		//removes a peg from everyone, and sets everyone to white team

		HashSet<Integer> removedPeg = new HashSet<Integer>();
		HashSet<Integer> remove = new HashSet<>();
		for (int pegID : this.pegs.keySet()) {
			int pegType = this.pegTypes.get(pegID);
			if (pegType == RocketLeagueState.PEG_TYPE_BALL) {
				continue;
			}

			int playerID = this.pegToPlayer.get(pegID);
			if (removedPeg.contains(playerID)) {
				continue;
			}

			remove.add(pegID);
			removedPeg.add(playerID);
		}
		for (int pegID : remove) {
			this.removePeg(pegID);
		}

		for (int playerID : this.server.getPlayersInGame()) {
			this.playerTeams.put(playerID, RocketLeagueState.TEAM_WHITE);
		}
		this.assignTeams = true;

		this.isInGame = false;
		this.endGame = true;
	}

	private void repositionPegs() {
		//move pegs based off of what team each player is on
		//also reset velocity of each peg. 
		//puts ball peg to center

		for (int pegID : this.pegs.keySet()) {
			Body b = this.pegs.get(pegID);
			Vec2 rVec = new Vec2((Math.random() - 0.5f) * 10, (Math.random() - 0.5f) * 10);
			b.position.set(rVec);
			b.velocity.set(new Vec2(0));

			int pegType = this.pegTypes.get(pegID);
			if (pegType == RocketLeagueState.PEG_TYPE_BALL) {
				b.position.set(new Vec2(0, 0));
				continue;
			}

			int pegTeam = this.playerTeams.get(this.pegToPlayer.get(pegID));
			switch (pegTeam) {
			case RocketLeagueState.TEAM_BLUE:
				b.position.addi(new Vec2(-10, 0));
				break;

			case RocketLeagueState.TEAM_RED:
				b.position.addi(new Vec2(10, 0));
				break;
			}
		}
	}

	private void assignPegType(int pegID, int pegType) {
		float mass = RocketLeagueState.pegTypeMasses.get(pegType);
		this.pegs.get(pegID).setMass(mass);

		this.pegTypes.put(pegID, pegType);
		this.assignPegTypeList.add(new Pair<Integer, Integer>(pegID, pegType));
	}

	private int generatePowerupID() {
		int id = 0;
		while (id == 0 || this.powerups.containsKey(id)) {
			id = (int) (Math.random() * 1000000);
		}
		return id;
	}

	private int addPowerup() {
		//find location for powerup that doesn't collide with any pegs
		Vec2 pos = new Vec2(0, 0);
		float width = 40f;
		float height = 20f;
		int attCnt = 0;
		while (attCnt < 100000) {
			attCnt++;
			pos = new Vec2((Math.random() * width) - width / 2, (Math.random() * height) - height / 2);
			boolean isValid = true;
			for (int pegID : this.pegs.keySet()) {
				Vec2 pegPos = this.pegs.get(pegID).position;
				float dist = pos.distance(pegPos);
				if (dist < 2) {
					isValid = false;
					break;
				}
			}
			if (isValid) {
				break;
			}
			attCnt++;
		}
		int id = this.generatePowerupID();
		this.powerups.put(id, new Pair<Vec2, Integer>(pos, RocketLeagueState.getRandomAdvancedPegType()));
		this.addPowerupList.add(id);
		return id;
	}

	private void removePowerup(int id) {
		this.powerups.remove(id);
		this.removePowerupList.add(id);
	}

	private int generatePegID() {
		int id = 0;
		while (id == 0 || this.pegs.containsKey(id)) {
			id = (int) (Math.random() * 1000000);
		}
		return id;
	}

	private int addPeg(int playerID, int pegType) {
		int pegID = this.generatePegID();
		Body pegBody = new Body(new Circle(1f), 0, 0);
		pegBody.setMass(RocketLeagueState.pegTypeMasses.get(pegType));
		pegBody.setRestitution(0.7f);
		pegBody.dynamicFriction = 0.1f;
		pegBody.staticFriction = 0.1f;

		this.pegs.put(pegID, pegBody);
		this.pegToPlayer.put(pegID, playerID);
		this.pegTypes.put(pegID, pegType);

		this.impulseScene.addBody(pegBody);

		this.addPegList.add(pegID);

		return pegID;
	}

	private void removePeg(int pegID) {
		Body b = this.pegs.get(pegID);
		this.impulseScene.removeBody(b);

		this.pegs.remove(pegID);
		this.pegToPlayer.remove(pegID);
		this.pegTypes.remove(pegID);

		this.removePegList.add(pegID);
	}

}
