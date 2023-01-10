package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import entity.Capsule;
import game.ChessGame;
import graphics.Material;
import model.Model;
import state.GameState;
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

	private int curGame = LOBBY;

	private boolean startingGame = false;

	// -- CHESS -- 
	//first two players to join a chess lobby will be the players
	//any other players can spectate by querying the chess game associated with the lobby. 
	private HashMap<Integer, Integer> playerToChessGames; //map player id to their chess games
	private HashMap<Integer, ChessGame> chessGames;
	private HashMap<Integer, Integer> chessLobbyUpdates; //lobby updates D:
	private HashMap<Integer, Pair<Integer, int[][]>> chessMoveUpdates; //it just tells moves. pretty simple

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
	}

	@Override
	public void _update() {

	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		// -- NORMAL SERVER STUFF --
		if (this.disconnect.size() != 0) {
			packetSender.writeSectionHeader("disconnect", this.disconnect.size());
			for (int i : this.disconnect) {
				packetSender.write(i);
			}
		}

		if (this.connect.size() != 0) {
			packetSender.writeSectionHeader("connect", this.connect.size());
			for (int i : this.connect) {
				packetSender.write(i);
				packetSender.write(this.playerNicknames.get(i).length());
				packetSender.write(this.playerNicknames.get(i));
			}
		}

		if (this.serverMessages.size() != 0) {
			packetSender.writeSectionHeader("server_messages", this.serverMessages.size());
			for (String s : this.serverMessages) {
				packetSender.write(s.length());
				packetSender.write(s);
			}
		}

		packetSender.writeSectionHeader("player_info", this.players.size());
		for (int i : this.players) {
			packetSender.write(i);
			packetSender.write(this.playerNicknames.get(i).length());
			packetSender.write(this.playerNicknames.get(i));
		}

		if (this.startingGame) {
			packetSender.writeSectionHeader("start_game", 1);
			packetSender.write(this.curGame);
		}

		packetSender.writeSectionHeader("host_id", 1);
		packetSender.write(this.hostID);

		// -- GAME SPECIFIC --
		switch (this.curGame) {
		case CHESS:
			this.writePacketChess(packetSender, clientID);
			break;
		}

	}

	private void writePacketChess(PacketSender packetSender, int clientID) {
		//send list of chess games currently being played
		// - ID of chess game
		// - ID of players, -1 if no player
		// - TODO send who is spectating the game
		// - TODO send current position so we can have a render on the select button
		if (this.chessLobbyUpdates.size() != 0) {
			packetSender.writeSectionHeader("chess_lobby_updates", this.chessLobbyUpdates.size());
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
			packetSender.writeSectionHeader("chess_move_updates", this.chessMoveUpdates.size());
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

		this.chessLobbyUpdates.clear();
		this.chessMoveUpdates.clear();
	}

	@Override
	public void readPacket(PacketListener packetListener, int clientID) {
		while (packetListener.hasMoreBytes()) {
			String sectionName = packetListener.readSectionHeader();
			int elementAmt = packetListener.getSectionElementAmt();

			switch (sectionName) {
			case "set_nickname": {
				int nickLength = packetListener.readInt();
				String nickname = packetListener.readString(nickLength);
				this.serverMessages.add(this.playerNicknames.get(clientID) + " changed their name to " + nickname);
				this.playerNicknames.put(clientID, nickname);
				break;
			}

			case "start_game": {
				int whichGame = packetListener.readInt();
				this.curGame = whichGame;
				this.startingGame = true;
				break;
			}
			}

			// -- GAME SPECIFIC --
			switch (this.curGame) {
			case CHESS:
				this.readPacketChess(packetListener, clientID, sectionName, elementAmt);
				break;
			}
		}
	}

	public void readPacketChess(PacketListener packetListener, int clientID, String sectionName, int elementAmt) {
		switch (sectionName) {
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
				break;
			}
			this.playerToChessGames.put(clientID, whichGame);
			this.chessLobbyUpdates.put(whichGame, UPDATE);
			break;
		}

		case "chess_leave_game": {
			ChessGame game = this.chessGames.get(this.playerToChessGames.get(clientID));
			if (game.getWhiteID() == clientID) {
				game.setWhiteID(-1);
			}
			else if (game.getBlackID() == clientID) {
				game.setBlackID(-1);
			}
			else {
				break;
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
