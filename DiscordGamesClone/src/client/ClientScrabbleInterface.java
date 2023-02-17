package client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import game.ScrabbleGame;
import server.PacketListener;
import server.PacketSender;
import util.Pair;

public class ClientScrabbleInterface extends ClientGameInterface {

	private ScrabbleGame scrabbleGame;

	private boolean scrabbleStartGame = false;
	private int scrabbleStartGameRoundAmt = 0;
	private boolean scrabbleIsGameStarting = false;
	private boolean scrabbleIsGameEnding = false;

	private ArrayList<Pair<int[], Character>> scrabbleIncomingMove;
	private ArrayList<Pair<int[], Character>> scrabbleOutgoingMove;
	private boolean scrabbleIsIncomingMoveValid = false;
	private boolean scrabbleIsMoveIncoming = false;

	private HashMap<Integer, Integer> scrabblePlayerScores;

	private int scrabbleNextPlayer;
	private int scrabbleRoundsLeft;

	private boolean scrabbleSkipMove = false;

	private HashMap<Integer, ArrayList<Character>> scrabblePlayerHands;

	public ClientScrabbleInterface(GameClient client) {
		super(client);

		this.scrabbleIncomingMove = new ArrayList<>();
		this.scrabbleOutgoingMove = new ArrayList<>();
		this.scrabblePlayerScores = new HashMap<>();
		this.scrabblePlayerHands = new HashMap<>();
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		if (this.scrabbleOutgoingMove.size() != 0) {
			packetSender.startSection("scrabble_make_move");
			packetSender.write(this.scrabbleOutgoingMove.size());
			for (Pair<int[], Character> i : this.scrabbleOutgoingMove) {
				packetSender.write(i.first);
				packetSender.write(i.second);
			}
			this.scrabbleOutgoingMove.clear();
		}

		if (this.scrabbleStartGame) {
			packetSender.startSection("scrabble_start_game");
			packetSender.write(this.scrabbleStartGameRoundAmt);
			this.scrabbleStartGameRoundAmt = 0;
			this.scrabbleStartGame = false;
		}

		if (this.scrabbleSkipMove) {
			packetSender.startSection("scrabble_skip_move");
			this.scrabbleSkipMove = false;
		}
	}

	@Override
	public void readSection(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "scrabble_start_game": {
			this.scrabbleGame = new ScrabbleGame();
			this.scrabblePlayerScores.clear();
			this.scrabblePlayerHands.clear();
			this.scrabbleIsGameStarting = true;

			this.scrabbleIncomingMove.clear();
			this.scrabbleOutgoingMove.clear();
			break;
		}

		case "scrabble_end_game": {
			this.scrabbleIsGameEnding = true;
			break;
		}

		case "scrabble_next_player": {
			this.scrabbleRoundsLeft = packetListener.readInt();
			this.scrabbleNextPlayer = packetListener.readInt();
			break;
		}

		case "scrabble_make_move": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int[] coords = packetListener.readNInts(2);
				char letter = (char) packetListener.readInt();
				this.scrabbleIncomingMove.add(new Pair<int[], Character>(coords, letter));
			}
			this.scrabbleIsMoveIncoming = true;
			if (this.scrabbleGame.makeMove(this.scrabbleIncomingMove) != -1) {
				this.scrabbleIsIncomingMoveValid = true;
			}
			break;
		}

		case "scrabble_player_scores": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int playerID = packetListener.readInt();
				int score = packetListener.readInt();
				this.scrabblePlayerScores.put(playerID, score);
			}
			break;
		}

		case "scrabble_player_hand": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int playerID = packetListener.readInt();
				ArrayList<Character> hand = new ArrayList<>();
				for (int j = 0; j < ScrabbleGame.handSize; j++) {
					hand.add((char) packetListener.readInt());
				}
				this.scrabblePlayerHands.put(playerID, hand);
			}
			break;
		}
		}
	}

	public void resetScrabbleGameInfo() {
		this.scrabbleIncomingMove.clear();
		this.scrabbleOutgoingMove.clear();
		this.scrabbleIsGameStarting = false;
		this.scrabblePlayerHands.clear();
		this.scrabblePlayerScores.clear();
	}

	public void scrabbleSkipMove() {
		this.scrabbleSkipMove = true;
	}

	public int scrabbleGetRoundsLeft() {
		return this.scrabbleRoundsLeft;
	}

	public boolean scrabbleIsMoveIncoming() {
		if (!this.scrabbleIsMoveIncoming) {
			return false;
		}
		this.scrabbleIsMoveIncoming = false;
		return true;
	}

	public boolean scrabbleIsIncomingMoveValid() {
		if (!this.scrabbleIsIncomingMoveValid) {
			return false;
		}
		this.scrabbleIsIncomingMoveValid = false;
		return true;
	}

	public ArrayList<Character> scrabbleGetPlayerHand(int id) {
		return this.scrabblePlayerHands.get(id);
	}

	public ScrabbleGame scrabbleGetGame() {
		return this.scrabbleGame;
	}

	public int scrabbleGetNextPlayer() {
		return this.scrabbleNextPlayer;
	}

	public HashMap<Integer, Integer> scrabbleGetPlayerScores() {
		return this.scrabblePlayerScores;
	}

	public boolean scrabbleIsGameStarting() {
		if (!this.scrabbleIsGameStarting) {
			return false;
		}
		this.scrabbleIsGameStarting = false;
		return true;
	}

	public boolean scrabbleIsGameEnding() {
		if (!this.scrabbleIsGameEnding) {
			return false;
		}
		this.scrabbleIsGameEnding = false;
		return true;
	}

	public void scrabbleStartGame(int roundAmt) {
		if (!this.client.isHost()) {
			return;
		}
		this.scrabbleStartGame = true;
		this.scrabbleStartGameRoundAmt = roundAmt;
	}

	public boolean scrabbleMakeMove(ArrayList<Pair<int[], Character>> move) {
		if (this.client.getID() != this.scrabbleNextPlayer) {
			return false;
		}
		this.scrabbleOutgoingMove.addAll(move);
		return true;
	}

	public ArrayList<Pair<int[], Character>> scrabbleGetIncomingMove() {
		ArrayList<Pair<int[], Character>> ret = new ArrayList<>();
		ret.addAll(this.scrabbleIncomingMove);
		this.scrabbleIncomingMove.clear();
		return ret;
	}

}
