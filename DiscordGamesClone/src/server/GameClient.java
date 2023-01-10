package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import game.ChessGame;
import util.Pair;
import util.Vec3;

public class GameClient extends Client {

	private HashSet<Integer> players;
	private HashMap<Integer, String> playerNicknames;
	private boolean playerInfoChanged = false;

	private ArrayList<String> serverMessages;

	private boolean writeNickname = false;
	private String nickname;

	private int hostID;

	private boolean startingGame = false;
	private int curGame = GameServer.LOBBY;

	// -- CHESS --
	private HashMap<Integer, ChessGame> chessGames;
	private int chessCurGameID = -1;
	private boolean chessCreateGame = false;

	private boolean chessJoinGame = false;
	private int chessJoinWhichGame = -1;

	private boolean chessLeaveGame = false;

	private boolean chessMakeMove = false;
	private int[] chessMoveFrom, chessMoveTo;

	private boolean chessHasLobbyUpdates = false;
	private boolean chessCurGameHasMoveUpdate = false;

	public GameClient() {
		super();

		this.players = new HashSet<>();
		this.playerNicknames = new HashMap<>();

		this.serverMessages = new ArrayList<>();

		this.chessGames = new HashMap<>();
	}

	@Override
	public void _update() {

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		// -- NORMAL CLIENT STUFF --
		if (this.writeNickname) {
			packetSender.writeSectionHeader("set_nickname", 1);
			packetSender.write(this.nickname.length());
			packetSender.write(this.nickname);
			this.writeNickname = false;
		}

		if (this.startingGame) {
			packetSender.writeSectionHeader("start_game", 1);
			packetSender.write(this.curGame);
			this.startingGame = false;
		}

		// -- GAME SPECIFIC --
		switch (this.curGame) {
		case GameServer.CHESS:
			this.writePacketChess(packetSender);
			break;
		}
	}

	private void writePacketChess(PacketSender packetSender) {
		if (this.chessCreateGame) {
			packetSender.writeSectionHeader("chess_create_game", 1);
			this.chessCreateGame = false;
		}

		if (this.chessJoinGame) {
			packetSender.writeSectionHeader("chess_join_game", 1);
			packetSender.write(this.chessJoinWhichGame);
			this.chessJoinGame = false;
		}

		if (this.chessLeaveGame) {
			packetSender.writeSectionHeader("chess_leave_game", 1);
			this.chessLeaveGame = false;
		}

		if (this.chessMakeMove) {
			packetSender.writeSectionHeader("chess_make_move", 1);
			packetSender.write(this.chessCurGameID);
			packetSender.write(this.ID);
			packetSender.write(this.chessMoveFrom);
			packetSender.write(this.chessMoveTo);
			this.chessMakeMove = false;
		}
	}

	@Override
	public void readPacket(PacketListener packetListener) {
		while (packetListener.hasMoreBytes()) {
			String sectionName = packetListener.readSectionHeader();
			int elementAmt = packetListener.getSectionElementAmt();

			switch (sectionName) {
			case "disconnect": {
				for (int i = 0; i < elementAmt; i++) {
					int playerID = packetListener.readInt();
					this.removePlayer(playerID);
				}
				break;
			}

			case "connect": {
				for (int i = 0; i < elementAmt; i++) {
					int playerID = packetListener.readInt();
					int nickLen = packetListener.readInt();
					String nickname = packetListener.readString(nickLen);
					this.addPlayer(playerID, nickname);
				}
				break;
			}

			case "server_messages": {
				for (int i = 0; i < elementAmt; i++) {
					int sLength = packetListener.readInt();
					String s = packetListener.readString(sLength);
					this.serverMessages.add(s);
				}
				break;
			}

			case "player_info": {
				for (int i = 0; i < elementAmt; i++) {
					int playerID = packetListener.readInt();
					int nickLen = packetListener.readInt();
					String nickname = packetListener.readString(nickLen);
					this.addPlayer(playerID, nickname);
				}
				break;
			}

			case "host_id": {
				this.hostID = packetListener.readInt();
				break;
			}

			case "start_game": {
				int whichGame = packetListener.readInt();
				this.curGame = whichGame;
				break;
			}
			}

			switch (this.curGame) {
			case GameServer.CHESS:
				this.readPacketChess(packetListener, sectionName, elementAmt);
				break;
			}
		}
	}

	private void readPacketChess(PacketListener packetListener, String sectionName, int elementAmt) {

		switch (sectionName) {
		case "chess_lobby_updates": {
			System.out.println(sectionName);
			this.chessHasLobbyUpdates = true;
			for (int i = 0; i < elementAmt; i++) {
				int updateType = packetListener.readInt();
				int whichGame = packetListener.readInt();
				switch (updateType) {
				case GameServer.CREATE: {
					ChessGame game = new ChessGame();
					game.setID(whichGame);
					System.out.println("ADD GAME : " + whichGame);
					this.chessGames.put(whichGame, game);

					//lack of break is intentional
				}

				case GameServer.UPDATE: {
					ChessGame game = this.chessGames.get(whichGame);
					int whiteID = packetListener.readInt();
					int blackID = packetListener.readInt();
					game.setWhiteID(whiteID);
					game.setBlackID(blackID);

					//check if server has put the client into a game
					if (whiteID == this.ID || blackID == this.ID) {
						this.chessCurGameID = whichGame;
					}
					break;
				}

				case GameServer.DELETE: {
					System.out.println("REMOVE GAME : " + whichGame);
					this.chessGames.remove(whichGame);
					break;
				}
				}
			}
			break;
		}

		case "chess_move_updates": {
			for (int i = 0; i < elementAmt; i++) {
				int whichGame = packetListener.readInt();
				int playerID = packetListener.readInt();
				int[] from = packetListener.readNInts(2);
				int[] to = packetListener.readNInts(2);

				if (playerID != this.ID) {
					ChessGame game = this.chessGames.get(whichGame);
					//this should always work, client and server desync if this didn't work
					game.performMove(from, to);

					if (whichGame == this.chessCurGameID) {
						this.chessCurGameHasMoveUpdate = true;
					}
				}

			}
			break;
		}
		}
	}

	public boolean chessCurGameHasMoveUpdate() {
		if (!this.chessCurGameHasMoveUpdate) {
			return false;
		}
		this.chessCurGameHasMoveUpdate = false;
		return true;
	}

	public boolean chessHasLobbyUpdates() {
		if (!this.chessHasLobbyUpdates) {
			return false;
		}
		this.chessHasLobbyUpdates = false;
		return true;
	}

	public HashMap<Integer, ChessGame> getChessGames() {
		return this.chessGames;
	}

	public ChessGame chessGetCurGame() {
		if (this.chessCurGameID == -1) {
			return null;
		}
		return this.chessGames.get(this.chessCurGameID);
	}

	public int chessGetCurGameID() {
		return this.chessCurGameID;
	}

	public void chessMakeMove(int[] from, int[] to) {
		this.chessMakeMove = true;
		this.chessMoveFrom = from;
		this.chessMoveTo = to;

		//go ahead and make the move on the client
		this.chessGetCurGame().performMove(from, to);
	}

	public void chessJoinGame(int whichGame) {
		this.chessJoinGame = true;
		this.chessJoinWhichGame = whichGame;
	}

	public void chessLeaveGame() {
		this.chessLeaveGame = true;
		this.chessCurGameID = -1;
	}

	//tells server to create the chess game. Server will automatically put the client into the game
	public void chessCreateGame() {
		this.chessCreateGame = true;
	}

	public int getCurGame() {
		return this.curGame;
	}

	public void startGame(int whichGame) {
		if (this.hostID != this.ID) {
			return;
		}
		this.startingGame = true;
		this.curGame = whichGame;
	}

	public int getHostID() {
		return this.hostID;
	}

	public boolean hasPlayerInfoChanged() {
		if (!this.playerInfoChanged) {
			return false;
		}
		this.playerInfoChanged = false;
		return true;
	}

	private void removePlayer(int playerID) {
		this.players.remove(playerID);
		this.playerNicknames.remove(playerID);
		this.playerInfoChanged = true;
	}

	private void addPlayer(int playerID, String nickname) {
		if (this.players.contains(playerID)) {
			this.updatePlayerNickname(playerID, nickname);
			return;
		}

		this.players.add(playerID);
		this.playerNicknames.put(playerID, nickname);
		this.playerInfoChanged = true;
	}

	private void updatePlayerNickname(int playerID, String nickname) {
		if (this.playerNicknames.get(playerID).equals(nickname)) {
			return;
		}

		this.playerNicknames.put(playerID, nickname);
		this.playerInfoChanged = true;
	}

	public ArrayList<String> getServerMessages() {
		ArrayList<String> ans = new ArrayList<>();
		ans.addAll(this.serverMessages);
		this.serverMessages.clear();
		return ans;
	}

	public HashMap<Integer, String> getPlayers() {
		return this.playerNicknames;
	}

	public int getID() {
		return this.ID;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
		this.writeNickname = true;
	}

}
