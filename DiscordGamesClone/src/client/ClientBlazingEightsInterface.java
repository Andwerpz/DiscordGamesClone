package client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import server.PacketListener;
import server.PacketSender;
import state.BlazingEightsState;

public class ClientBlazingEightsInterface extends ClientGameInterface {

	private int blazingEightsMoveIndex;
	private ArrayList<Integer> blazingEightsMoveOrder;

	private HashMap<Integer, Integer> blazingEightsCardAmt;

	private boolean blazingEightsStartGame = false; //flag to command server to start game
	private boolean blazingEightsPerformMove = false;
	private int blazingEightsPerformMoveType, blazingEightsPerformMoveValue;

	private boolean blazingEightsStartingGame = false;
	private boolean blazingEightsEndingGame = false;

	private boolean blazingEightsMovePerformed = false;
	private int blazingEightsMovePlayer, blazingEightsMoveValue, blazingEightsMoveType;

	public ClientBlazingEightsInterface(GameClient client) {
		super(client);

		this.blazingEightsMoveOrder = new ArrayList<>();
		this.blazingEightsCardAmt = new HashMap<>();
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacket(PacketSender packetSender) {
		if (this.blazingEightsStartGame) {
			packetSender.startSection("blazing_eights_start_game");
			this.blazingEightsStartGame = false;
		}

		if (this.blazingEightsPerformMove) {
			packetSender.startSection("blazing_eights_perform_move");
			packetSender.write(this.blazingEightsPerformMoveType);
			packetSender.write(this.blazingEightsPerformMoveValue);
			this.blazingEightsPerformMove = false;
		}
	}

	@Override
	public void readSection(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "blazing_eights_start_game": {
			this.blazingEightsStartingGame = true;
			this.blazingEightsMoveOrder = new ArrayList<>();
			this.blazingEightsCardAmt = new HashMap<>();
			this.blazingEightsMoveIndex = 0;

			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int id = packetListener.readInt();
				int cardAmt = packetListener.readInt();
				this.blazingEightsMoveOrder.add(id);
				this.blazingEightsCardAmt.put(id, cardAmt);
			}
			break;
		}

		case "blazing_eights_end_game": {
			this.blazingEightsEndingGame = true;
			break;
		}

		case "blazing_eights_move_performed": {
			this.blazingEightsMovePerformed = true;
			this.blazingEightsMovePlayer = packetListener.readInt();
			this.blazingEightsMoveType = packetListener.readInt();
			this.blazingEightsMoveValue = packetListener.readInt();
			this.blazingEightsMoveIndex = packetListener.readInt();

			switch (this.blazingEightsMoveType) {
			case BlazingEightsState.MOVE_PLAY: {
				this.blazingEightsCardAmt.put(this.blazingEightsMovePlayer, this.blazingEightsCardAmt.get(this.blazingEightsMovePlayer) - 1);
				break;
			}

			case BlazingEightsState.MOVE_DRAW: {
				this.blazingEightsCardAmt.put(this.blazingEightsMovePlayer, this.blazingEightsCardAmt.get(this.blazingEightsMovePlayer) + this.blazingEightsMoveValue);
				break;
			}
			}
			break;
		}
		}
	}

	public void resetBlazingEightsGameInfo() {
		this.blazingEightsCardAmt.clear();
		this.blazingEightsMoveOrder.clear();
		this.blazingEightsStartingGame = false;
		this.blazingEightsStartGame = false;
		this.blazingEightsEndingGame = false;
		this.blazingEightsMoveIndex = 0;
	}

	public int blazingEightsGetNextPlayer() {
		return this.blazingEightsMoveOrder.get(this.blazingEightsMoveIndex);
	}

	public boolean blazingEightsIsMyTurn() {
		return this.blazingEightsMoveOrder.get(this.blazingEightsMoveIndex) == this.client.getID();
	}

	public int[] blazingEightsGetPerformedMove() {
		if (!this.blazingEightsMovePerformed) {
			return null;
		}
		this.blazingEightsMovePerformed = false;
		return new int[] { this.blazingEightsMovePlayer, this.blazingEightsMoveType, this.blazingEightsMoveValue };
	}

	public void blazingEightsPerformMove(int type, int value) {
		this.blazingEightsPerformMove = true;
		this.blazingEightsPerformMoveType = type;
		this.blazingEightsPerformMoveValue = value;
	}

	public ArrayList<Integer> blazingEightsGetMoveOrder() {
		return this.blazingEightsMoveOrder;
	}

	public HashMap<Integer, Integer> blazingEightsGetCardAmt() {
		return this.blazingEightsCardAmt;
	}

	public void blazingEightsStartGame() {
		this.blazingEightsStartGame = true;
	}

	public boolean blazingEightsIsGameStarting() {
		if (!this.blazingEightsStartingGame) {
			return false;
		}
		this.blazingEightsStartingGame = false;
		return true;
	}

	public boolean blazingEightsIsGameEnding() {
		if (!this.blazingEightsEndingGame) {
			return false;
		}
		this.blazingEightsEndingGame = false;
		return true;
	}
}
