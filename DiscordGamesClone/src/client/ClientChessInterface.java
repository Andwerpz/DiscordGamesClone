package client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import game.ChessGame;
import server.GameServer;
import server.PacketListener;
import server.PacketSender;
import server.ServerChessInterface;

public class ClientChessInterface extends ClientGameInterface {

	private HashMap<Integer, ChessGame> chessGames;
	private int chessCurGameID = -1;
	private boolean chessIsSpectating = false;
	private boolean chessCreateGame = false;

	private boolean chessJoinGame = false;
	private int chessJoinWhichGame = -1;

	private boolean chessLeaveGame = false;

	private boolean chessMakeMove = false;
	private int[] chessMoveFrom, chessMoveTo;

	private boolean chessHasLobbyUpdates = false;
	private boolean chessCurGameHasMoveUpdate = false;

	public ClientChessInterface(GameClient client) {
		super(client);

		this.chessGames = new HashMap<>();
	}

	@Override
	public void update() {

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		if (this.chessCreateGame) {
			packetSender.startSection("chess_create_game");
			this.chessCreateGame = false;
		}

		if (this.chessJoinGame) {
			packetSender.startSection("chess_join_game");
			packetSender.write(this.chessJoinWhichGame);
			this.chessJoinGame = false;
		}

		if (this.chessLeaveGame) {
			packetSender.startSection("chess_leave_game");
			this.chessLeaveGame = false;
		}

		if (this.chessMakeMove) {
			packetSender.startSection("chess_make_move");
			packetSender.write(this.chessCurGameID);
			packetSender.write(this.client.getID());
			packetSender.write(this.chessMoveFrom);
			packetSender.write(this.chessMoveTo);
			this.chessMakeMove = false;
		}
	}

	@Override
	public void readSection(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "chess_lobby_updates": {
			this.chessHasLobbyUpdates = true;
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int updateType = packetListener.readInt();
				int whichGame = packetListener.readInt();
				switch (updateType) {
				case ServerChessInterface.CREATE: {
					ChessGame game = new ChessGame();
					game.setID(whichGame);
					System.out.println("ADD CHESS GAME : " + whichGame);
					this.chessGames.put(whichGame, game);

					//lack of break is intentional
				}

				case ServerChessInterface.UPDATE: {
					ChessGame game = this.chessGames.get(whichGame);
					int whiteID = packetListener.readInt();
					int blackID = packetListener.readInt();
					int spectatorAmt = packetListener.readInt();
					game.setWhiteID(whiteID);
					game.setBlackID(blackID);

					game.getSpectators().clear();
					for (int j = 0; j < spectatorAmt; j++) {
						int nextID = packetListener.readInt();
						game.addSpectator(nextID);
					}

					//check if server has put the client into a game
					if (whiteID == this.client.getID() || blackID == this.client.getID() || game.getSpectators().contains(this.client.getID())) {
						this.chessCurGameID = whichGame;
						if (game.getSpectators().contains(this.client.getID())) {
							this.chessIsSpectating = true;
						}
					}
					break;
				}

				case ServerChessInterface.DELETE: {
					System.out.println("REMOVE CHESS GAME : " + whichGame);
					this.chessGames.remove(whichGame);

					if (this.chessCurGameID == whichGame) {
						//leave the game, it's been deleted
						this.chessLeaveGame();
					}
					break;
				}
				}
			}
			break;
		}

		case "chess_move_updates": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int whichGame = packetListener.readInt();
				int playerID = packetListener.readInt();
				int[] from = packetListener.readNInts(2);
				int[] to = packetListener.readNInts(2);

				if (playerID != this.client.getID()) {
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

	public void resetChessGameInfo() {
		this.chessGames = new HashMap<>();
		chessCurGameID = -1;
		chessIsSpectating = false;
		chessCreateGame = false;

		chessJoinGame = false;
		chessJoinWhichGame = -1;

		chessLeaveGame = false;

		chessMakeMove = false;
		chessMoveFrom = null;
		chessMoveTo = null;

		chessHasLobbyUpdates = false;
		chessCurGameHasMoveUpdate = false;
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

	public boolean chessIsSpectating() {
		return this.chessIsSpectating;
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
		this.chessIsSpectating = false;
		this.chessCurGameID = -1;
	}

	//tells server to create the chess game. Server will automatically put the client into the game
	public void chessCreateGame() {
		this.chessCreateGame = true;
	}

}
