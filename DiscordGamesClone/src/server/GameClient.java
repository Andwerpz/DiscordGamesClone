package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

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
		if (this.writeNickname) {
			packetSender.writeSectionHeader("set_nickname", 1);
			packetSender.write(this.nickname.length());
			packetSender.write(this.nickname);
			this.writeNickname = false;
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
			}
		}
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
