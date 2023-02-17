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
import state.CrackHeadsState;
import util.Mat4;
import util.MathUtils;
import util.Pair;
import util.Quad;
import util.Triple;
import util.Vec2;
import util.Vec3;
import util.Vec4;

public class GameServer extends Server {

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
	public static final int CRACK_HEADS = 4;

	private int curGame = LOBBY;

	private boolean startingGame = false;
	private boolean returnToMainLobby = false;

	//when you start a game, this will just keep track of everyone that is actually inside the game. 
	//this is so that the game doesn't break when someone new joins the lobby during a game. 
	private HashSet<Integer> playersInGame;

	private ServerGameInterface gameInterface = null;

	public GameServer(String ip, int port) {
		super(ip, port);

		this.players = new HashSet<>();
		this.playerNicknames = new HashMap<>();

		this.disconnect = new ArrayList<>();
		this.connect = new ArrayList<>();

		this.serverMessages = new ArrayList<>();

		this.playersInGame = new HashSet<>();
	}

	@Override
	public void _update() {
		if (this.curGame != LOBBY) {
			this.gameInterface.update();
		}
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
				packetSender.write(this.playerNicknames.get(i));
			}
		}

		if (this.serverMessages.size() != 0) {
			packetSender.startSection("server_messages");
			packetSender.write(this.serverMessages.size());
			for (String s : this.serverMessages) {
				packetSender.write(s);
			}
		}

		packetSender.startSection("player_info");
		packetSender.write(this.players.size());
		for (int i : this.players) {
			packetSender.write(i);
			packetSender.write(this.playerNicknames.get(i));
		}

		if (this.startingGame) {
			packetSender.startSection("start_game");
			packetSender.write(this.curGame);

			this.playersInGame.add(clientID);
		}

		if (this.returnToMainLobby) {
			packetSender.startSection("return_to_main_lobby");
		}

		packetSender.startSection("host_id");
		packetSender.write(this.hostID);

		if (this.curGame != LOBBY) {
			this.gameInterface.writePacket(packetSender, clientID);
		}
	}

	@Override
	public void writePacketEND() {
		this.disconnect.clear();
		this.connect.clear();
		this.serverMessages.clear();

		this.startingGame = false;
		this.returnToMainLobby = false;

		if (this.curGame != LOBBY) {
			this.gameInterface.writePacketEND();
		}
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "set_nickname": {
			String nickname = packetListener.readString();
			if (nickname.length() <= 100) {
				this.serverMessages.add(this.playerNicknames.get(clientID) + " changed their name to " + nickname);
				this.playerNicknames.put(clientID, nickname);
			}
			break;
		}

		case "start_game": {
			int whichGame = packetListener.readInt();
			this.curGame = whichGame;
			this.startingGame = true;
			this.playersInGame = new HashSet<>();
			switch (this.curGame) {
			case CHESS:
				this.gameInterface = new ServerChessInterface(this);
				break;

			case SCRABBLE:
				this.gameInterface = new ServerScrabbleInterface(this);
				break;

			case BLAZING_EIGHTS:
				this.gameInterface = new ServerBlazingEightsInterface(this);
				break;

			case CRACK_HEADS:
				this.gameInterface = new ServerCrackHeadsInterface(this);
				break;
			}
			break;
		}

		case "return_to_main_lobby": {
			this.returnToMainLobby = true;
			this.curGame = LOBBY;
			this.gameInterface = null;
			break;
		}
		}

		if (this.curGame != LOBBY) {
			this.gameInterface.readSection(packetListener, clientID);
		}
	}

	public HashSet<Integer> getPlayersInGame() {
		return this.playersInGame;
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
