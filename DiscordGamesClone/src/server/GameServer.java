package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import entity.Capsule;
import graphics.Material;
import model.Model;
import state.GameState;
import util.Mat4;
import util.MathUtils;
import util.Pair;
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

	public GameServer(String ip, int port) {
		super(ip, port);

		this.players = new HashSet<>();
		this.playerNicknames = new HashMap<>();

		this.disconnect = new ArrayList<>();
		this.connect = new ArrayList<>();

		this.serverMessages = new ArrayList<>();
	}

	@Override
	public void _update() {

	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
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

		packetSender.writeSectionHeader("host_id", 1);
		packetSender.write(this.hostID);

	}

	@Override
	public void writePacketEND() {
		disconnect.clear();
		connect.clear();
		serverMessages.clear();
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
