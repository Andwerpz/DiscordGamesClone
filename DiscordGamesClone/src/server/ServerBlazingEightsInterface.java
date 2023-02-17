package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import state.BlazingEightsState;

public class ServerBlazingEightsInterface extends ServerGameInterface {

	private int blazingEightsMoveIndex;
	private int blazingEightsMoveIndexIncrement;
	private ArrayList<Integer> blazingEightsMoveOrder;

	private int blazingEightsDrawPenalty;

	private HashMap<Integer, Integer> blazingEightsCardAmt;

	private boolean blazingEightsStartingGame = false;
	private boolean blazingEightsEndingGame = false;

	private boolean blazingEightsMovePerformed = false;
	private int blazingEightsMovePlayer, blazingEightsMoveValue, blazingEightsMoveType;

	public ServerBlazingEightsInterface(GameServer server) {
		super(server);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void writePacket(PacketSender packetSender, int clientID) {
		if (this.blazingEightsStartingGame) {
			packetSender.startSection("blazing_eights_start_game");
			packetSender.write(this.blazingEightsMoveOrder.size());
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

	@Override
	public void writePacketEND() {
		this.blazingEightsStartingGame = false;
		this.blazingEightsEndingGame = false;
		this.blazingEightsMovePerformed = false;
	}

	@Override
	public void readSection(PacketListener packetListener, int clientID) throws IOException {
		switch (packetListener.getSectionName()) {
		case "blazing_eights_start_game": {
			this.blazingEightsStartingGame = true;

			this.blazingEightsMoveIndex = 0;
			this.blazingEightsMoveIndexIncrement = 1;
			this.blazingEightsDrawPenalty = 0;

			this.blazingEightsCardAmt = new HashMap<>();
			this.blazingEightsMoveOrder = new ArrayList<>();
			for (int id : this.server.getPlayersInGame()) {
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

	private void resetBlazingEightsGameInfo() {
		this.blazingEightsCardAmt = null;
		this.blazingEightsMoveOrder = null;
		this.blazingEightsStartingGame = false;
		this.blazingEightsEndingGame = false;
		this.blazingEightsMovePerformed = false;
	}
}
