package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import entity.Capsule;
import game.ChessGame;
import game.ScrabbleGame;
import graphics.Material;
import model.Model;
import state.BlazingEightsState;
import util.Mat4;
import util.MathUtils;
import util.Pair;
import util.Vec3;
import util.Vec4;

public class GameServer extends Server {

	public static final int CREATE = 0;
	public static final int UPDATE = 1;
	public static final int DELETE = 2;

	private HashSet<Integer> players;
	private HashMap<Integer, String> playerNicknames;

	//for notifying clients
	private ArrayList<Integer> disconnect;
	private ArrayList<Integer> connect;

	private ArrayList<String> serverMessages;

	private int hostID = -1;

	public static final int LOBBY = 0;
	public static final int CHESS = 1;
	public static final int SCRABBLE = 2;
	public static final int BLAZING_EIGHTS = 3;

	private int curGame = LOBBY;

	private boolean startingGame = false;
	private boolean returnToMainLobby = false;

	// -- CHESS -- 
	//first two players to join a chess lobby will be the players
	//any other players can spectate by querying the chess game associated with the lobby. 
	private HashMap<Integer, Integer> playerToChessGames; //map player id to their chess games
	private HashMap<Integer, ChessGame> chessGames;
	private HashMap<Integer, Integer> chessLobbyUpdates; //lobby updates D:
	private HashMap<Integer, Pair<Integer, int[][]>> chessMoveUpdates; //it just tells moves. pretty simple

	// -- SCRABBLE --
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

	// -- BLAZING EIGHTS --
	private int blazingEightsMoveIndex;
	private int blazingEightsMoveIndexIncrement;
	private ArrayList<Integer> blazingEightsMoveOrder;

	private int blazingEightsDrawPenalty;

	private HashMap<Integer, Integer> blazingEightsCardAmt;

	private boolean blazingEightsStartingGame = false;
	private boolean blazingEightsEndingGame = false;

	private boolean blazingEightsMovePerformed = false;
	private int blazingEightsMovePlayer, blazingEightsMoveValue, blazingEightsMoveType;

	public GameServer(String ip, int port) {
		super(ip, port);

		this.players = new HashSet<>();
		this.playerNicknames = new HashMap<>();

		this.disconnect = new ArrayList<>();
		this.connect = new ArrayList<>();

		this.serverMessages = new ArrayList<>();

		this.playerToChessGames = new HashMap<>();
		this.chessGames = new HashMap<>();
		this.chessLobbyUpdates = new HashMap<>();
		this.chessMoveUpdates = new HashMap<>();

		this.scrabbleNextMove = new ArrayList<>();
		this.scrabblePlayerMoveOrder = new ArrayList<>();
		this.scrabblePlayerScores = new HashMap<>();
		this.scrabblePlayerHands = new HashMap<>();
	}

	@Override
	public void _update() {

	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		// -- NORMAL SERVER STUFF --
		if (this.disconnect.size() != 0) {
			packetSender.startSection("disconnect");
			packetSender.write(this.disconnect.size());
			for (int i : this.disconnect) {
				packetSender.write(i);
			}
		}

		if (this.connect.size() != 0) {
			packetSender.startSection("connect");
			packetSender.write(this.connect.size());
			for (int i : this.connect) {
				packetSender.write(i);
				packetSender.write(this.playerNicknames.get(i).length());
				packetSender.write(this.playerNicknames.get(i));
			}
		}

		if (this.serverMessages.size() != 0) {
			packetSender.startSection("server_messages");
			packetSender.write(this.serverMessages.size());
			for (String s : this.serverMessages) {
				packetSender.write(s.length());
				packetSender.write(s);
			}
		}

		packetSender.startSection("player_info");
		packetSender.write(this.players.size());
		for (int i : this.players) {
			packetSender.write(i);
			packetSender.write(this.playerNicknames.get(i).length());
			packetSender.write(this.playerNicknames.get(i));
		}

		if (this.startingGame) {
			packetSender.startSection("start_game");
			packetSender.write(this.curGame);
		}

		if (this.returnToMainLobby) {
			packetSender.startSection("return_to_main_lobby");
		}

		packetSender.startSection("host_id");
		packetSender.write(this.hostID);

		// -- GAME SPECIFIC --
		switch (this.curGame) {
		case CHESS:
			this.writePacketChess(packetSender, clientID);
			break;

		case SCRABBLE:
			this.writePacketScrabble(packetSender, clientID);
			break;

		case BLAZING_EIGHTS:
			this.writePacketBlazingEights(packetSender, clientID);
			break;
		}

	}

	private void writePacketBlazingEights(PacketSender packetSender, int clientID) {
		if (this.blazingEightsStartingGame) {
			packetSender.startSection("blazing_eights_start_game");
			packetSender.write(this.players.size());
			for (int id : this.blazingEightsMoveOrder) {
				packetSender.write(id);
				packetSender.write(this.blazingEightsCardAmt.get(id));
			}
		}

		if (this.blazingEightsMovePerformed) {
			packetSender.startSection("blazing_eights_move_performed");
			packetSender.write(this.blazingEightsMovePlayer);
			packetSender.write(this.blazingEightsMoveType);
			packetSender.write(this.blazingEightsMoveValue);
			packetSender.write(this.blazingEightsMoveIndex);
		}

		if (this.blazingEightsEndingGame) {
			packetSender.startSection("blazing_eights_end_game");
		}
	}

	private void writePacketScrabble(PacketSender packetSender, int clientID) {
		if (this.scrabbleStartingGame) {
			packetSender.startSection("scrabble_start_game");
			this.scrabbleGame = new ScrabbleGame();

			this.scrabblePlayerMoveOrder = new ArrayList<>();
			this.scrabblePlayerScores.clear();
			this.scrabblePlayerHands.clear();
			for (int i : this.players) {
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

	private void writePacketChess(PacketSender packetSender, int clientID) {
		//send list of chess games currently being played
		// - ID of chess game
		// - ID of players, -1 if no player
		// - send who is spectating the game
		// - TODO send current position so we can have a render on the select button
		if (this.chessLobbyUpdates.size() != 0) {
			packetSender.startSection("chess_lobby_updates");
			packetSender.write(this.chessLobbyUpdates.size());
			for (int i : this.chessLobbyUpdates.keySet()) {
				int updateType = this.chessLobbyUpdates.get(i);
				int chessGameID = i;
				packetSender.write(updateType);
				packetSender.write(chessGameID);
				switch (updateType) {
				case CREATE:
				case UPDATE:
					ChessGame game = this.chessGames.get(chessGameID);
					packetSender.write(game.getWhiteID());
					packetSender.write(game.getBlackID());
					packetSender.write(game.getSpectators().size());
					for (int j : game.getSpectators()) {
						packetSender.write(j);
					}
					break;

				case DELETE:
					this.chessGames.remove(chessGameID);
					break;
				}
			}
		}

		//first which game, then the from and to. Clients will simulate the moves on their side. 
		//this should only send legal moves. 
		// - ID of chess game
		// - int[] from coordinate
		// - int[] to coordinate
		if (this.chessMoveUpdates.size() != 0) {
			packetSender.startSection("chess_move_updates");
			packetSender.write(this.chessMoveUpdates.size());
			for (int i : this.chessMoveUpdates.keySet()) {
				int whichGame = i;
				Pair<Integer, int[][]> next = this.chessMoveUpdates.get(i);
				int playerID = next.first;
				int[] from = next.second[0];
				int[] to = next.second[1];
				packetSender.write(whichGame);
				packetSender.write(playerID);
				packetSender.write(from);
				packetSender.write(to);
			}
		}
	}

	@Override
	public void writePacketEND() {
		disconnect.clear();
		connect.clear();
		serverMessages.clear();

		this.startingGame = false;
		this.returnToMainLobby = false;

		this.chessLobbyUpdates.clear();
		this.chessMoveUpdates.clear();

		this.scrabbleStartingGame = false;
		this.scrabbleEndingGame = false;
		this.scrabbleNextMove.clear();
		this.scrabbleMovePerformed = false;

		this.blazingEightsStartingGame = false;
		this.blazingEightsEndingGame = false;
		this.blazingEightsMovePerformed = false;
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "set_nickname": {
			int nickLength = packetListener.readInt();
			String nickname = packetListener.readString(nickLength);
			if (nickLength <= 100) {
				this.serverMessages.add(this.playerNicknames.get(clientID) + " changed their name to " + nickname);
				this.playerNicknames.put(clientID, nickname);
			}
			break;
		}

		case "start_game": {
			int whichGame = packetListener.readInt();
			this.curGame = whichGame;
			this.startingGame = true;
			break;
		}

		case "return_to_main_lobby": {
			this.returnToMainLobby = true;
			this.resetGameInfo();
			break;
		}
		}

		// -- GAME SPECIFIC --
		switch (this.curGame) {
		case CHESS:
			this.readPacketChess(packetListener, clientID);
			break;

		case SCRABBLE:
			this.readPacketScrabble(packetListener, clientID);
			break;

		case BLAZING_EIGHTS:
			this.readPacketBlazingEights(packetListener, clientID);
			break;
		}
	}

	public void readPacketBlazingEights(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "blazing_eights_start_game": {
			this.blazingEightsStartingGame = true;

			this.blazingEightsMoveIndex = 0;
			this.blazingEightsMoveIndexIncrement = 1;
			this.blazingEightsDrawPenalty = 0;

			this.blazingEightsCardAmt = new HashMap<>();
			this.blazingEightsMoveOrder = new ArrayList<>();
			for (int id : this.players) {
				this.blazingEightsMoveOrder.add(id);
				this.blazingEightsCardAmt.put(id, 7);
			}
			Collections.shuffle(this.blazingEightsMoveOrder);
			break;
		}

		case "blazing_eights_perform_move": {
			this.blazingEightsMovePlayer = clientID;
			this.blazingEightsMoveType = packetListener.readInt();
			this.blazingEightsMoveValue = packetListener.readInt();

			if (clientID != this.blazingEightsMoveOrder.get(this.blazingEightsMoveIndex)) {
				break;
			}

			this.blazingEightsMovePerformed = true;

			switch (this.blazingEightsMoveType) {
			case BlazingEightsState.MOVE_PLAY: {
				this.blazingEightsCardAmt.put(clientID, this.blazingEightsCardAmt.get(clientID) - 1);
				switch (BlazingEightsState.getCardSuitAndValue(this.blazingEightsMoveValue)[1]) {
				case BlazingEightsState.VALUE_SKIP:
					this.blazingEightsMoveIndex += this.blazingEightsMoveIndexIncrement;
					break;

				case BlazingEightsState.VALUE_REVERSE:
					this.blazingEightsMoveIndexIncrement *= -1;
					break;

				case BlazingEightsState.VALUE_ADDTWO:
					this.blazingEightsDrawPenalty += 2;
					break;

				case BlazingEightsState.VALUE_ADDTWOHUNDRED:
					this.blazingEightsDrawPenalty += 200;
					break;

				case BlazingEightsState.VALUE_WILDCARDADDFOUR:
					this.blazingEightsDrawPenalty += 4;
					break;
				}
				break;
			}

			case BlazingEightsState.MOVE_DRAW: {
				if (this.blazingEightsDrawPenalty != 0) {
					this.blazingEightsMoveValue = this.blazingEightsDrawPenalty;
					this.blazingEightsDrawPenalty = 0;
				}
				int curCardAmt = this.blazingEightsCardAmt.get(clientID);
				this.blazingEightsMoveValue = Math.min(BlazingEightsState.CARD_AMT_LIMIT - curCardAmt, this.blazingEightsMoveValue);
				curCardAmt += this.blazingEightsMoveValue;
				this.blazingEightsCardAmt.put(clientID, curCardAmt);
				break;
			}
			}

			//someone has gotten rid of all of their cards
			if (this.blazingEightsCardAmt.get(clientID) <= 0) {
				this.blazingEightsEndingGame = true;
			}
			else {
				this.blazingEightsMoveIndex += this.blazingEightsMoveIndexIncrement;
				this.blazingEightsMoveIndex = (this.blazingEightsMoveIndex % this.blazingEightsMoveOrder.size() + this.blazingEightsMoveOrder.size()) % this.blazingEightsMoveOrder.size();
			}

			break;
		}
		}
	}

	public void readPacketScrabble(PacketListener packetListener, int clientID) throws IOException {
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

	public void readPacketChess(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "chess_create_game": {
			ChessGame newGame = new ChessGame();
			newGame.setWhiteID(clientID);
			this.chessGames.put(newGame.getID(), newGame);
			this.chessLobbyUpdates.put(newGame.getID(), CREATE);
			this.playerToChessGames.put(clientID, newGame.getID());
			break;
		}

		case "chess_join_game": {
			int whichGame = packetListener.readInt();
			ChessGame game = this.chessGames.get(whichGame);
			if (game.getWhiteID() == -1) {
				game.setWhiteID(clientID);
			}
			else if (game.getBlackID() == -1) {
				game.setBlackID(clientID);
			}
			else {
				game.addSpectator(clientID);
			}
			this.playerToChessGames.put(clientID, whichGame);
			this.chessLobbyUpdates.put(whichGame, UPDATE);
			break;
		}

		case "chess_leave_game": {
			ChessGame game = this.chessGames.get(this.playerToChessGames.get(clientID));
			if (game == null) {
				break;
			}
			if (game.getWhiteID() == clientID) {
				game.setWhiteID(-1);
			}
			else if (game.getBlackID() == clientID) {
				game.setBlackID(-1);
			}
			else {
				game.removeSpectator(clientID);
			}
			this.playerToChessGames.remove(clientID);
			if (game.getWhiteID() == -1 && game.getBlackID() == -1) {
				this.chessLobbyUpdates.put(game.getID(), DELETE);
			}
			else {
				this.chessLobbyUpdates.put(game.getID(), UPDATE);
			}
			break;
		}

		case "chess_make_move": {
			int whichGame = packetListener.readInt();
			int playerID = packetListener.readInt();
			int[] from = packetListener.readNInts(2);
			int[] to = packetListener.readNInts(2);
			ChessGame game = this.chessGames.get(whichGame);
			if (game.performMove(from, to)) {
				this.chessMoveUpdates.put(whichGame, new Pair<Integer, int[][]>(playerID, new int[][] { from, to }));
			}
			break;
		}
		}
	}

	private void resetGameInfo() {
		this.resetChessGameInfo();
		this.resetScrabbleGameInfo();
		this.resetBlazingEightsGameInfo();
	}

	public void resetBlazingEightsGameInfo() {
		this.blazingEightsCardAmt = null;
		this.blazingEightsMoveOrder = null;
		this.blazingEightsStartingGame = false;
		this.blazingEightsEndingGame = false;
		this.blazingEightsMovePerformed = false;
	}

	private void resetScrabbleGameInfo() {
		this.scrabblePlayerScores.clear();
		this.scrabbleMovePerformed = false;
		this.scrabbleStartingGame = false;
		this.scrabblePlayerHands.clear();
		this.scrabbleRoundsLeft = 0;
	}

	private void resetChessGameInfo() {
		this.chessGames.clear();
		this.chessLobbyUpdates.clear();
		this.chessMoveUpdates.clear();
		this.playerToChessGames.clear();
	}

	@Override
	public void _clientConnect(int clientID) {
		this.playerNicknames.put(clientID, "" + clientID);
		this.players.add(clientID);
		this.serverMessages.add(this.playerNicknames.get(clientID) + " connected");

		this.connect.add(clientID);

		if (this.players.size() == 1) {
			this.hostID = clientID;
		}
	}

	@Override
	public void _clientDisconnect(int clientID) {
		this.serverMessages.add(this.playerNicknames.get(clientID) + " disconnected");
		this.playerNicknames.remove(clientID);
		this.players.remove(clientID);

		this.disconnect.add(clientID);
	}

	@Override
	public void _exit() {

	}

}
