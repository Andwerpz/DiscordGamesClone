package server;

import java.io.IOException;
import java.util.HashMap;

import game.ChessGame;
import util.Pair;

public class ServerChessInterface extends ServerGameInterface {

	public static final int CREATE = 0;
	public static final int UPDATE = 1;
	public static final int DELETE = 2;

	//first two players to join a chess lobby will be the players
	//any other players can spectate by querying the chess game associated with the lobby. 
	private HashMap<Integer, Integer> playerToChessGames; //map player id to their chess games
	private HashMap<Integer, ChessGame> chessGames;
	private HashMap<Integer, Integer> chessLobbyUpdates; //lobby updates D:
	private HashMap<Integer, Pair<Integer, int[][]>> chessMoveUpdates; //it just tells moves. pretty simple

	public ServerChessInterface(GameServer server) {
		super(server);

		this.playerToChessGames = new HashMap<>();
		this.chessGames = new HashMap<>();
		this.chessLobbyUpdates = new HashMap<>();
		this.chessMoveUpdates = new HashMap<>();
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
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
		this.chessLobbyUpdates.clear();
		this.chessMoveUpdates.clear();
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		System.out.println(packetListener.getSectionName());
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

	private void resetChessGameInfo() {
		this.chessGames.clear();
		this.chessLobbyUpdates.clear();
		this.chessMoveUpdates.clear();
		this.playerToChessGames.clear();
	}
}
