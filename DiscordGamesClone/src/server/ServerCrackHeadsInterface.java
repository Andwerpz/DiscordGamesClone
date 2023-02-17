package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import state.CrackHeadsState;
import util.Pair;
import util.Quad;
import util.Vec2;

public class ServerCrackHeadsInterface extends ServerGameInterface {

	//want to be able to send line drawing updates in the form Vec2 a, Vec2 b, float size
	private ArrayList<Quad<Vec2, Vec2, Float, Integer>> crackHeadsDrawnLines;

	private boolean crackHeadsStartingGame = false;
	private boolean crackHeadsEndingGame = false;

	private HashMap<Integer, Integer> crackHeadsPoints;
	private boolean crackHeadsUpdatePoints = false; //if true, will send point information next update

	//will wait for everyone to pick
	private boolean crackHeadsStartingPickPhase = false; //pick the word to draw / crack level
	private HashSet<Integer> crackHeadsHasNotPicked;

	//will go for 60 seconds
	private boolean crackHeadsStartingDrawPhase = false; //drawing / guessing
	private String crackHeadsDrawPhaseWord = null;
	private HashSet<Integer> crackHeadsHasNotGuessed; //has not guessed correctly
	private int crackHeadsDrawPhaseTotalPoints = 0;

	private boolean crackHeadsIsInDrawPhase = false;
	private long crackHeadsDrawPhaseDurationMillis = 120 * 1000;
	private long crackHeadsDrawPhaseEndMillis = 0;
	private long crackHeadsLastHintMillis = 0;

	private boolean crackHeadsGiveHint = false;
	private int crackHeadsHintIndex = 0;
	private int crackHeadsNumHints = 0;

	private ArrayList<Pair<Integer, String>> crackHeadsGuesses; //clientID, word guess

	private ArrayList<Integer> crackHeadsMoveOrder;
	private int crackHeadsMoveIndex = 0;
	private int crackHeadsMovesLeft = 0;

	private boolean crackHeadsClearScreen = false;

	private HashMap<Integer, Integer> crackHeadsCrackLevel;

	public ServerCrackHeadsInterface(GameServer server) {
		super(server);

		this.crackHeadsDrawnLines = new ArrayList<>();
		this.crackHeadsGuesses = new ArrayList<>();
	}

	@Override
	public void update() {
		if (this.crackHeadsIsInDrawPhase) {
			int maxHints = this.crackHeadsDrawPhaseWord.length() / 2;
			long hintWaitDuration = 10 * 1000;
			if (System.currentTimeMillis() - crackHeadsLastHintMillis > hintWaitDuration && this.crackHeadsNumHints < maxHints) {
				this.crackHeadsGiveHint = true;
				this.crackHeadsHintIndex = (int) (Math.random() * this.crackHeadsDrawPhaseWord.length());
				this.crackHeadsLastHintMillis = System.currentTimeMillis();
				this.crackHeadsNumHints++;
			}
			if (System.currentTimeMillis() > this.crackHeadsDrawPhaseEndMillis) {
				this.crackHeadsEndMove();
			}
		}
	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		if (this.crackHeadsDrawnLines.size() != 0) {
			packetSender.startSection("crack_heads_draw_line");
			packetSender.write(this.crackHeadsDrawnLines.size());
			for (Quad<Vec2, Vec2, Float, Integer> i : this.crackHeadsDrawnLines) {
				packetSender.write(i.first);
				packetSender.write(i.second);
				packetSender.write(i.third);
				packetSender.write(i.fourth);
			}
		}

		if (this.crackHeadsStartingGame) {
			packetSender.startSection("crack_heads_start_game");
		}

		if (this.crackHeadsEndingGame) {
			packetSender.startSection("crack_heads_end_game");
		}

		if (this.crackHeadsGuesses.size() != 0) {
			packetSender.startSection("crack_heads_guess");
			packetSender.write(this.crackHeadsGuesses.size());
			for (Pair<Integer, String> i : this.crackHeadsGuesses) {
				packetSender.write(i.first);
				packetSender.write(i.second);
			}
		}

		if (this.crackHeadsUpdatePoints) {
			packetSender.startSection("crack_heads_points");
			packetSender.write(this.crackHeadsPoints.size());
			for (int id : this.crackHeadsPoints.keySet()) {
				packetSender.write(id);
				packetSender.write(this.crackHeadsPoints.get(id));
			}
		}

		//sends id of client that is picking the word, and the three words they need to pick from
		if (this.crackHeadsStartingPickPhase) {
			packetSender.startSection("crack_heads_pick_phase");

			//send which client
			packetSender.write(this.crackHeadsMoveOrder.get(this.crackHeadsMoveIndex));

			//send words to pick from
			packetSender.write(3);
			packetSender.write(CrackHeadsState.getRandomWord());
			packetSender.write(CrackHeadsState.getRandomWord());
			packetSender.write(CrackHeadsState.getRandomWord());
		}

		//sends the id of the client that is drawing, and the word that they are drawing. 
		//also, send everyone's crack level. 
		if (this.crackHeadsStartingDrawPhase) {
			packetSender.startSection("crack_heads_draw_phase");
			packetSender.write(this.crackHeadsMoveOrder.get(this.crackHeadsMoveIndex));
			packetSender.write(this.crackHeadsDrawPhaseWord);

			packetSender.write(this.crackHeadsCrackLevel.size());
			for (int id : this.crackHeadsCrackLevel.keySet()) {
				packetSender.write(id);
				packetSender.write(this.crackHeadsCrackLevel.get(id));
			}
		}

		if (this.crackHeadsGiveHint) {
			packetSender.startSection("crack_heads_hint");
			packetSender.write(this.crackHeadsHintIndex);
		}

		if (this.crackHeadsClearScreen) {
			packetSender.startSection("crack_heads_clear_screen");
		}
	}

	@Override
	public void writePacketEND() {
		this.crackHeadsDrawnLines.clear();
		this.crackHeadsGuesses.clear();
		this.crackHeadsStartingGame = false;
		this.crackHeadsEndingGame = false;
		this.crackHeadsStartingPickPhase = false;
		this.crackHeadsStartingDrawPhase = false;
		this.crackHeadsUpdatePoints = false;
		this.crackHeadsGiveHint = false;
		this.crackHeadsClearScreen = false;
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "crack_heads_draw_line": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				Vec2 a = packetListener.readVec2();
				Vec2 b = packetListener.readVec2();
				float size = packetListener.readFloat();
				int colorIndex = packetListener.readInt();
				this.crackHeadsDrawnLines.add(new Quad<Vec2, Vec2, Float, Integer>(a, b, size, colorIndex));
			}
			break;
		}

		case "crack_heads_clear_screen": {
			this.crackHeadsClearScreen = true;
			break;
		}

		case "crack_heads_start_game": {
			this.crackHeadsStartingGame = true;
			this.crackHeadsUpdatePoints = true;
			this.crackHeadsMoveOrder = new ArrayList<Integer>();
			this.crackHeadsPoints = new HashMap<Integer, Integer>();
			this.crackHeadsMoveIndex = 0;
			for (int id : this.server.getPlayersInGame()) {
				this.crackHeadsMoveOrder.add(id);
				this.crackHeadsPoints.put(id, 0);
			}

			this.crackHeadsMovesLeft = this.server.getPlayersInGame().size() * 2;
			this.crackHeadsStartPickPhase();
			break;
		}

		case "crack_heads_pick": {
			if (clientID == this.crackHeadsMoveOrder.get(this.crackHeadsMoveIndex)) {
				//read in picked word
				this.crackHeadsDrawPhaseWord = packetListener.readString();
			}
			else {
				int level = packetListener.readInt();
				this.crackHeadsCrackLevel.put(clientID, level);
			}
			this.crackHeadsHasNotPicked.remove(clientID);
			if (this.crackHeadsHasNotPicked.size() == 0) {
				this.crackHeadsStartDrawPhase();
			}
			break;
		}

		case "crack_heads_guess": {
			String guess = packetListener.readString();
			if (guess.equalsIgnoreCase(this.crackHeadsDrawPhaseWord)) {
				int points = 100;
				int numGuessed = this.crackHeadsMoveOrder.size() - this.crackHeadsHasNotGuessed.size();
				points = Math.max(20, points - numGuessed * 20);

				//apply crack multiplier
				switch (this.crackHeadsCrackLevel.get(clientID)) {
				case 1:
					points *= 1.25;
					break;

				case 2:
					points *= 1.5;
					break;

				case 3:
					points *= 2;
					break;
				}

				this.crackHeadsDrawPhaseTotalPoints += points;

				this.crackHeadsPoints.put(clientID, this.crackHeadsPoints.get(clientID) + points);
				this.crackHeadsUpdatePoints = true;
				this.crackHeadsHasNotGuessed.remove(clientID);
			}
			if (this.crackHeadsHasNotGuessed.size() == 1) { //since the drawer cannot guess
				this.crackHeadsEndMove();
			}
			this.crackHeadsGuesses.add(new Pair<Integer, String>(clientID, guess));
			break;
		}
		}
	}

	private void resetCrackHeadsGameInfo() {
		this.crackHeadsCrackLevel = null;
		this.crackHeadsDrawnLines.clear();
		this.crackHeadsGuesses.clear();
		this.crackHeadsMoveOrder = null;
	}

	private void crackHeadsEndMove() {
		int prevDrawer = this.crackHeadsMoveOrder.get(this.crackHeadsMoveIndex);
		this.crackHeadsPoints.put(prevDrawer, this.crackHeadsPoints.get(prevDrawer) + this.crackHeadsDrawPhaseTotalPoints / 3);
		this.crackHeadsUpdatePoints = true;

		this.crackHeadsMoveIndex = (this.crackHeadsMoveIndex + 1) % this.crackHeadsMoveOrder.size();
		this.crackHeadsMovesLeft--;
		if (this.crackHeadsMovesLeft == 0) {
			this.crackHeadsEndingGame = true;
		}
		else {
			this.crackHeadsStartPickPhase();
		}
		this.crackHeadsIsInDrawPhase = false;
		this.crackHeadsDrawPhaseEndMillis = 0;
	}

	private void crackHeadsStartPickPhase() {
		this.crackHeadsStartingPickPhase = true;
		this.crackHeadsHasNotPicked = new HashSet<Integer>();
		this.crackHeadsCrackLevel = new HashMap<Integer, Integer>();
		for (int id : this.crackHeadsMoveOrder) {
			this.crackHeadsHasNotPicked.add(id);
			this.crackHeadsCrackLevel.put(id, 0);
		}
	}

	private void crackHeadsStartDrawPhase() {
		this.crackHeadsNumHints = 0;
		this.crackHeadsDrawPhaseTotalPoints = 0;
		this.crackHeadsStartingDrawPhase = true;
		this.crackHeadsHasNotGuessed = new HashSet<Integer>();
		for (int id : this.crackHeadsMoveOrder) {
			this.crackHeadsHasNotGuessed.add(id);
		}
		this.crackHeadsDrawPhaseEndMillis = System.currentTimeMillis() + this.crackHeadsDrawPhaseDurationMillis;
		this.crackHeadsIsInDrawPhase = true;
	}
}
