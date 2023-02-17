package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import game.ScrabbleGame;
import util.Pair;

public class ServerScrabbleInterface extends ServerGameInterface {

	//players will communicate with the server what moves they want to perform, and the server will calculate the score
	//server communicates all players scores after every move

	private ScrabbleGame scrabbleGame;

	private HashMap<Integer, Integer> scrabblePlayerScores;

	private ArrayList<Pair<int[], Character>> scrabbleNextMove;
	private ArrayList<Integer> scrabblePlayerMoveOrder;
	private int scrabbleMoveIndex;

	private boolean scrabbleMovePerformed = false;
	private boolean scrabbleStartingGame = false;
	private boolean scrabbleEndingGame = false;

	private HashMap<Integer, ArrayList<Character>> scrabblePlayerHands;

	private int scrabbleRoundsLeft = 0;

	public ServerScrabbleInterface(GameServer server) {
		super(server);

		this.scrabbleNextMove = new ArrayList<>();
		this.scrabblePlayerMoveOrder = new ArrayList<>();
		this.scrabblePlayerScores = new HashMap<>();
		this.scrabblePlayerHands = new HashMap<>();
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		if (this.scrabbleStartingGame) {
			packetSender.startSection("scrabble_start_game");
			this.scrabbleGame = new ScrabbleGame();

			this.scrabblePlayerMoveOrder = new ArrayList<>();
			this.scrabblePlayerScores.clear();
			this.scrabblePlayerHands.clear();
			for (int i : this.server.getPlayersInGame()) {
				this.scrabblePlayerMoveOrder.add(i);
				this.scrabblePlayerScores.put(i, 0);

				ArrayList<Character> hand = new ArrayList<Character>();
				for (int j = 0; j < ScrabbleGame.handSize; j++) {
					hand.add(ScrabbleGame.getRandomLetter());
				}
				this.scrabblePlayerHands.put(i, hand);
			}
			this.scrabbleMoveIndex = 0;
		}

		if (this.scrabbleMovePerformed || this.scrabbleStartingGame) {
			packetSender.startSection("scrabble_next_player");
			packetSender.write(this.scrabbleRoundsLeft);
			packetSender.write(this.scrabblePlayerMoveOrder.get(this.scrabbleMoveIndex));
		}

		if (this.scrabbleMovePerformed || this.scrabbleStartingGame) {
			packetSender.startSection("scrabble_player_hand");
			packetSender.write(this.scrabblePlayerHands.size());
			for (int i : this.scrabblePlayerHands.keySet()) {
				packetSender.write(i);
				for (char j : this.scrabblePlayerHands.get(i)) {
					packetSender.write(j);
				}
			}
		}

		if (this.scrabbleMovePerformed) {
			packetSender.startSection("scrabble_make_move");
			packetSender.write(this.scrabbleNextMove.size());
			for (Pair<int[], Character> i : this.scrabbleNextMove) {
				packetSender.write(i.first);
				packetSender.write(i.second);
			}
		}

		if (this.scrabbleMovePerformed || this.scrabbleStartingGame) {
			packetSender.startSection("scrabble_player_scores");
			packetSender.write(this.scrabblePlayerScores.size());
			for (int id : this.scrabblePlayerScores.keySet()) {
				packetSender.write(id);
				packetSender.write(this.scrabblePlayerScores.get(id));
			}
		}

		if (this.scrabbleEndingGame) {
			packetSender.startSection("scrabble_end_game");
		}
	}

	@Override
	public void writePacketEND() {
		this.scrabbleStartingGame = false;
		this.scrabbleEndingGame = false;
		this.scrabbleNextMove.clear();
		this.scrabbleMovePerformed = false;
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "scrabble_start_game": {
			this.scrabbleRoundsLeft = packetListener.readInt();
			this.scrabbleStartingGame = true;
			break;
		}

		case "scrabble_end_game": {
			this.scrabbleEndingGame = true;
			break;
		}

		case "scrabble_make_move": {
			this.scrabbleNextMove.clear();

			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int[] coords = packetListener.readNInts(2);
				char letter = (char) packetListener.readInt();
				this.scrabbleNextMove.add(new Pair<int[], Character>(coords, letter));
			}

			if (clientID != this.scrabblePlayerMoveOrder.get(this.scrabbleMoveIndex)) {
				//this player shouldn't be moving right now
				this.scrabbleNextMove.clear();
				break;
			}

			//play the move and figure out the score
			int score = this.scrabbleGame.makeMove(this.scrabbleNextMove);
			if (score == -1) {
				//the move is invalid, or the player was just trying to swap out their tiles
				score = 0;
			}

			//update, and move on to the next player
			ArrayList<Character> hand = this.scrabblePlayerHands.get(clientID);
			for (Pair<int[], Character> i : this.scrabbleNextMove) {
				char c = i.second;
				hand.remove((Character) c);
			}

			while (hand.size() != ScrabbleGame.handSize) {
				hand.add(ScrabbleGame.getRandomLetter());
			}

			this.scrabblePlayerScores.put(clientID, this.scrabblePlayerScores.get(clientID) + score);
			this.scrabbleMoveIndex = (this.scrabbleMoveIndex + 1) % this.scrabblePlayerMoveOrder.size();
			this.scrabbleMovePerformed = true;

			if (this.scrabbleMoveIndex == 0) {
				this.scrabbleRoundsLeft--;
			}
			if (this.scrabbleRoundsLeft == 0) {
				this.scrabbleEndingGame = true;
			}
			break;
		}

		case "scrabble_skip_move": {
			//just move onto the next player
			this.scrabbleNextMove.clear();
			this.scrabbleMoveIndex = (this.scrabbleMoveIndex + 1) % this.scrabblePlayerMoveOrder.size();
			this.scrabbleMovePerformed = true;

			if (this.scrabbleMoveIndex == 0) {
				this.scrabbleRoundsLeft--;
			}
			if (this.scrabbleRoundsLeft == 0) {
				this.scrabbleEndingGame = true;
			}
			break;
		}
		}
	}

	private void resetScrabbleGameInfo() {
		this.scrabblePlayerScores.clear();
		this.scrabbleMovePerformed = false;
		this.scrabbleStartingGame = false;
		this.scrabblePlayerHands.clear();
		this.scrabbleRoundsLeft = 0;
	}
}
