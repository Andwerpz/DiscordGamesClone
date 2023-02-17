package client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import server.PacketListener;
import server.PacketSender;
import util.Pair;
import util.Quad;
import util.Vec2;

public class ClientCrackHeadsInterface extends ClientGameInterface {

	private ArrayList<Quad<Vec2, Vec2, Float, Integer>> crackHeadsOutgoingLines; //a, b, size, color index
	private ArrayList<Quad<Vec2, Vec2, Float, Integer>> crackHeadsIncomingLines;

	private ArrayList<Pair<Integer, String>> crackHeadsIncomingGuesses;

	private HashMap<Integer, Integer> crackHeadsPoints;

	private boolean crackHeadsStartGame = false;
	private boolean crackHeadsGameStarting = false;
	private boolean crackHeadsGameEnding = false;

	private boolean crackHeadsPickPhaseStarting = false;
	private boolean crackHeadsIsPickingWord = false;
	private ArrayList<String> crackHeadsWordOptions;
	private boolean crackHeadsPicking = false;
	private String crackHeadsPickedWord = null;
	private int crackHeadsPickedCrackLevel = -1;

	private boolean crackHeadsDrawPhaseStarting = false;
	private boolean crackHeadsIsDrawing = false;
	private String crackHeadsDrawPhaseWord;
	private HashMap<Integer, Integer> crackHeadsCrackLevels;

	private String crackHeadsGuess = null;

	private HashSet<Integer> crackHeadsHints;
	private boolean crackHeadsNewHint = false;

	private boolean crackHeadsClearScreen = false;
	private boolean crackHeadsShouldClearScreen = false;

	public ClientCrackHeadsInterface(GameClient client) {
		super(client);

		this.crackHeadsOutgoingLines = new ArrayList<>();
		this.crackHeadsIncomingLines = new ArrayList<>();
		this.crackHeadsIncomingGuesses = new ArrayList<>();
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		if (this.crackHeadsOutgoingLines.size() != 0) {
			packetSender.startSection("crack_heads_draw_line");
			packetSender.write(this.crackHeadsOutgoingLines.size());
			for (int ind = 0; ind < this.crackHeadsOutgoingLines.size(); ind++) {
				Quad<Vec2, Vec2, Float, Integer> i = this.crackHeadsOutgoingLines.get(ind);
				packetSender.write(i.first);
				packetSender.write(i.second);
				packetSender.write(i.third);
				packetSender.write(i.fourth);
			}
			this.crackHeadsOutgoingLines.clear();
		}

		if (this.crackHeadsGuess != null) {
			packetSender.startSection("crack_heads_guess");
			packetSender.write(this.crackHeadsGuess);
			this.crackHeadsGuess = null;
		}

		if (this.crackHeadsStartGame) {
			packetSender.startSection("crack_heads_start_game");
			this.crackHeadsStartGame = false;
		}

		if (this.crackHeadsPicking) {
			packetSender.startSection("crack_heads_pick");
			if (this.crackHeadsIsPickingWord) {
				packetSender.write(this.crackHeadsPickedWord);
			}
			else {
				packetSender.write(this.crackHeadsPickedCrackLevel);
			}
			this.crackHeadsPicking = false;
		}

		if (this.crackHeadsClearScreen) {
			packetSender.startSection("crack_heads_clear_screen");
			this.crackHeadsClearScreen = false;
		}
	}

	@Override
	public void readSection(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "crack_heads_draw_line": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				Vec2 a = packetListener.readVec2();
				Vec2 b = packetListener.readVec2();
				float size = packetListener.readFloat();
				int colorIndex = packetListener.readInt();
				this.crackHeadsIncomingLines.add(new Quad<Vec2, Vec2, Float, Integer>(a, b, size, colorIndex));
			}
			break;
		}

		case "crack_heads_clear_screen": {
			this.crackHeadsShouldClearScreen = true;
			break;
		}

		case "crack_heads_start_game": {
			this.crackHeadsGameStarting = true;
			break;
		}

		case "crack_heads_end_game": {
			this.crackHeadsGameEnding = true;
			break;
		}

		case "crack_heads_guess": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int clientID = packetListener.readInt();
				String guess = packetListener.readString();
				this.crackHeadsIncomingGuesses.add(new Pair<Integer, String>(clientID, guess));
			}
			break;
		}

		case "crack_heads_pick_phase": {
			this.crackHeadsPickPhaseStarting = true;
			int wordPickerID = packetListener.readInt();
			int amt = packetListener.readInt();
			this.crackHeadsWordOptions = new ArrayList<String>();
			for (int i = 0; i < amt; i++) {
				String str = packetListener.readString();
				this.crackHeadsWordOptions.add(str);
			}
			this.crackHeadsIsPickingWord = wordPickerID == this.client.getID();
			break;
		}

		case "crack_heads_draw_phase": {
			this.crackHeadsDrawPhaseStarting = true;
			int drawerID = packetListener.readInt();
			String word = packetListener.readString();
			int amt = packetListener.readInt();
			this.crackHeadsCrackLevels = new HashMap<Integer, Integer>();
			for (int i = 0; i < amt; i++) {
				int id = packetListener.readInt();
				int level = packetListener.readInt();
				this.crackHeadsCrackLevels.put(id, level);
			}
			this.crackHeadsIsDrawing = drawerID == this.client.getID();
			this.crackHeadsDrawPhaseWord = word;
			this.crackHeadsHints = new HashSet<Integer>();
			break;
		}

		case "crack_heads_points": {
			this.crackHeadsPoints = new HashMap<>();
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int id = packetListener.readInt();
				int points = packetListener.readInt();
				this.crackHeadsPoints.put(id, points);
			}
			break;
		}

		case "crack_heads_hint": {
			int index = packetListener.readInt();
			this.crackHeadsHints.add(index);
			this.crackHeadsNewHint = true;
			break;
		}
		}
	}

	public void resetCrackHeadsGameInfo() {
		this.crackHeadsIncomingLines.clear();
		this.crackHeadsOutgoingLines.clear();
	}

	public HashMap<Integer, Integer> crackHeadsGetCrackLevels() {
		return this.crackHeadsCrackLevels;
	}

	public void crackHeadsClearScreen() {
		this.crackHeadsClearScreen = true;
	}

	public boolean crackHeadsShouldClearScreen() {
		if (!this.crackHeadsShouldClearScreen) {
			return false;
		}
		this.crackHeadsShouldClearScreen = false;
		return true;
	}

	public boolean crackHeadsHasNewHint() {
		if (!this.crackHeadsNewHint) {
			return false;
		}
		this.crackHeadsNewHint = false;
		return true;
	}

	public HashSet<Integer> crackHeadsGetHints() {
		return this.crackHeadsHints;
	}

	public ArrayList<Pair<Integer, String>> crackHeadsGetIncomingGuesses() {
		ArrayList<Pair<Integer, String>> ret = new ArrayList<>();
		ret.addAll(this.crackHeadsIncomingGuesses);
		this.crackHeadsIncomingGuesses.clear();
		return ret;
	}

	public ArrayList<String> crackHeadsGetWordOptions() {
		return this.crackHeadsWordOptions;
	}

	public String crackHeadsGetDrawPhaseWord() {
		return this.crackHeadsDrawPhaseWord;
	}

	public void crackHeadsMakeGuess(String guess) {
		this.crackHeadsGuess = guess;
	}

	public ArrayList<Quad<Vec2, Vec2, Float, Integer>> crackHeadsGetIncomingLines() {
		ArrayList<Quad<Vec2, Vec2, Float, Integer>> ret = new ArrayList<>();
		ret.addAll(this.crackHeadsIncomingLines);
		this.crackHeadsIncomingLines.clear();
		return ret;
	}

	public HashMap<Integer, Integer> crackHeadsGetPoints() {
		return this.crackHeadsPoints;
	}

	public boolean crackHeadsIsDrawing() {
		return this.crackHeadsIsDrawing;
	}

	public boolean crackHeadsIsPickingWord() {
		return this.crackHeadsIsPickingWord;
	}

	public void crackHeadsPickWord(String word) {
		this.crackHeadsPicking = true;
		this.crackHeadsPickedWord = word;
	}

	public void crackHeadsPickCrackLevel(int level) {
		this.crackHeadsPicking = true;
		this.crackHeadsPickedCrackLevel = level;
	}

	public boolean crackHeadsPickPhaseStarting() {
		if (!this.crackHeadsPickPhaseStarting) {
			return false;
		}
		this.crackHeadsPickPhaseStarting = false;
		return true;
	}

	public boolean crackHeadsDrawPhaseStarting() {
		if (!this.crackHeadsDrawPhaseStarting) {
			return false;
		}
		this.crackHeadsDrawPhaseStarting = false;
		return true;
	}

	public boolean crackHeadsGameStarting() {
		if (!this.crackHeadsGameStarting) {
			return false;
		}
		this.crackHeadsGameStarting = false;
		return true;
	}

	public boolean crackHeadsGameEnding() {
		if (!this.crackHeadsGameEnding) {
			return false;
		}
		this.crackHeadsGameEnding = false;
		return true;
	}

	public void crackHeadsStartGame() {
		if (this.client.isHost()) {
			this.crackHeadsStartGame = true;
		}
	}

	public void crackHeadsDrawLine(Vec2 a, Vec2 b, float size, int colorIndex) {
		this.crackHeadsOutgoingLines.add(new Quad<Vec2, Vec2, Float, Integer>(a, b, size, colorIndex));
	}
}
