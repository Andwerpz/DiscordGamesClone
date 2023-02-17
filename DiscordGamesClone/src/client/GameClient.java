package client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import game.ChessGame;
import game.ScrabbleGame;
import server.GameServer;
import server.PacketListener;
import server.PacketSender;
import state.BlazingEightsState;
import util.Pair;
import util.Quad;
import util.Triple;
import util.Vec2;
import util.Vec3;

public class GameClient extends Client {

	private HashSet<Integer> players;
	private HashMap<Integer, String> playerNicknames;
	private boolean playerInfoChanged = false;

	private ArrayList<String> serverMessages;

	private boolean writeNickname = false;
	private String nickname;

	private int hostID;
	private boolean isHost = false;

	private boolean startingGame = false;
	private int nextGame = -1;

	private boolean returnToMainLobby = false;
	private int curGame = GameServer.LOBBY;

	private ClientGameInterface gameInterface = null;

	public GameClient() {
		super();

		this.players = new HashSet<>();
		this.playerNicknames = new HashMap<>();

		this.serverMessages = new ArrayList<>();
	}

	@Override
	public void _update() {

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		// -- NORMAL CLIENT STUFF --
		if (this.writeNickname) {
			packetSender.startSection("set_nickname");
			packetSender.write(this.nickname);
			this.writeNickname = false;
		}

		if (this.startingGame) {
			packetSender.startSection("start_game");
			packetSender.write(this.nextGame);
			this.startingGame = false;
			this.nextGame = -1;
		}

		if (this.returnToMainLobby) {
			packetSender.startSection("return_to_main_lobby");
			this.returnToMainLobby = false;
		}

		if (this.curGame != GameServer.LOBBY) {
			this.gameInterface.writePacket(packetSender);
		}
	}

	@Override
	public void readSection(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "disconnect": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int playerID = packetListener.readInt();
				this.removePlayer(playerID);
			}
			break;
		}

		case "connect": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int playerID = packetListener.readInt();
				String nickname = packetListener.readString();
				this.addPlayer(playerID, nickname);
			}
			break;
		}

		case "server_messages": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				String s = packetListener.readString();
				this.serverMessages.add(s);
			}
			break;
		}

		case "player_info": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int playerID = packetListener.readInt();
				String nickname = packetListener.readString();
				this.addPlayer(playerID, nickname);
			}
			break;
		}

		case "host_id": {
			this.hostID = packetListener.readInt();
			this.isHost = this.hostID == this.ID;
			break;
		}

		case "start_game": {
			int whichGame = packetListener.readInt();
			this.curGame = whichGame;
			switch (this.curGame) {
			case GameServer.CHESS:
				this.gameInterface = new ClientChessInterface(this);
				break;

			case GameServer.SCRABBLE:
				this.gameInterface = new ClientScrabbleInterface(this);
				break;

			case GameServer.BLAZING_EIGHTS:
				this.gameInterface = new ClientBlazingEightsInterface(this);
				break;

			case GameServer.CRACK_HEADS:
				this.gameInterface = new ClientCrackHeadsInterface(this);
				break;
			}
			break;
		}

		case "return_to_main_lobby": {
			this.curGame = GameServer.LOBBY;
			this.gameInterface = null;
			break;
		}
		}

		if (this.curGame != GameServer.LOBBY) {
			this.gameInterface.readSection(packetListener);
		}
	}

	public ClientGameInterface getGameInterface() {
		return this.gameInterface;
	}

	public int getCurGame() {
		return this.curGame;
	}

	public void returnToMainLobby() {
		if (!this.isHost()) {
			return;
		}
		this.returnToMainLobby = true;
		this.curGame = GameServer.LOBBY;
	}

	public void startGame(int whichGame) {
		if (this.hostID != this.ID) {
			return;
		}
		this.startingGame = true;
		this.nextGame = whichGame;
	}

	public int getHostID() {
		return this.hostID;
	}

	public boolean isHost() {
		return this.isHost;
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
