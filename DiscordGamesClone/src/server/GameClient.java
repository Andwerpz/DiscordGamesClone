package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import game.ChessGame;
import game.ScrabbleGame;
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
	private boolean returnToMainLobby = false;
	private int curGame = GameServer.LOBBY;

	// -- CHESS --
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

	// -- SCRABBLE --
	private ScrabbleGame scrabbleGame;

	private boolean scrabbleStartGame = false;
	private int scrabbleStartGameRoundAmt = 0;
	private boolean scrabbleIsGameStarting = false;
	private boolean scrabbleIsGameEnding = false;

	private ArrayList<Pair<int[], Character>> scrabbleIncomingMove;
	private ArrayList<Pair<int[], Character>> scrabbleOutgoingMove;
	private boolean scrabbleIsIncomingMoveValid = false;
	private boolean scrabbleIsMoveIncoming = false;

	private HashMap<Integer, Integer> scrabblePlayerScores;

	private int scrabbleNextPlayer;
	private int scrabbleRoundsLeft;

	private boolean scrabbleSkipMove = false;

	private HashMap<Integer, ArrayList<Character>> scrabblePlayerHands;

	// -- BLAZING EIGHTS --
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

	// -- CRACK HEADS --
	private ArrayList<Quad<Vec2, Vec2, Float, Integer>> crackHeadsOutgoingLines; //a, b, size, color index
	private ArrayList<Quad<Vec2, Vec2, Float, Integer>> crackHeadsIncomingLines;

	private ArrayList<Pair<Integer, String>> crackHeadsIncomingGuesses;

	private HashMap<Integer, Integer> crackHeadsPoints;

	private boolean crackHeadsStartGame = false;
	private boolean crackHeadsGameStarting = false;
	private boolean crackHeadsGameEnding = false;

	private boolean crackHeadsPickPhaseStarting = false;
	private boolean crackHeadsIsPickingWord = false;
	private ArrayList<String> crackHeadsWordOptions;
	private boolean crackHeadsPicking = false;
	private String crackHeadsPickedWord = null;
	private int crackHeadsPickedCrackLevel = -1;

	private boolean crackHeadsDrawPhaseStarting = false;
	private boolean crackHeadsIsDrawing = false;
	private String crackHeadsDrawPhaseWord;
	private HashMap<Integer, Integer> crackHeadsCrackLevels;

	private String crackHeadsGuess = null;

	private HashSet<Integer> crackHeadsHints;
	private boolean crackHeadsNewHint = false;

	private boolean crackHeadsClearScreen = false;
	private boolean crackHeadsShouldClearScreen = false;

	public GameClient() {
		super();

		this.players = new HashSet<>();
		this.playerNicknames = new HashMap<>();

		this.serverMessages = new ArrayList<>();

		this.chessGames = new HashMap<>();

		this.scrabbleIncomingMove = new ArrayList<>();
		this.scrabbleOutgoingMove = new ArrayList<>();
		this.scrabblePlayerScores = new HashMap<>();
		this.scrabblePlayerHands = new HashMap<>();

		this.blazingEightsMoveOrder = new ArrayList<>();
		this.blazingEightsCardAmt = new HashMap<>();

		this.crackHeadsOutgoingLines = new ArrayList<>();
		this.crackHeadsIncomingLines = new ArrayList<>();
		this.crackHeadsIncomingGuesses = new ArrayList<>();
	}

	//i don't like this, this is dumb
	public void resetAllGameInfo() {
		this.resetChessGameInfo();
		this.resetScrabbleGameInfo();
		this.resetBlazingEightsGameInfo();
		this.resetCrackHeadsGameInfo();
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
			packetSender.write(this.curGame);
			this.startingGame = false;
		}

		if (this.returnToMainLobby) {
			packetSender.startSection("return_to_main_lobby");
			this.returnToMainLobby = false;
		}

		// -- GAME SPECIFIC --
		switch (this.curGame) {
		case GameServer.CHESS:
			this.writePacketChess(packetSender);
			break;

		case GameServer.SCRABBLE:
			this.writePacketScrabble(packetSender);
			break;

		case GameServer.BLAZING_EIGHTS:
			this.writePacketBlazingEights(packetSender);
			break;

		case GameServer.CRACK_HEADS:
			this.writePacketCrackHeads(packetSender);
			break;
		}
	}

	private void writePacketCrackHeads(PacketSender packetSender) {
		if (this.crackHeadsOutgoingLines.size() != 0) {
			packetSender.startSection("crack_heads_draw_line");
			packetSender.write(this.crackHeadsOutgoingLines.size());
			for (int ind = 0; ind < this.crackHeadsOutgoingLines.size(); ind++) {
				Quad<Vec2, Vec2, Float, Integer> i = this.crackHeadsOutgoingLines.get(ind);
				packetSender.write(i.first);
				packetSender.write(i.second);
				packetSender.write(i.third);
				packetSender.write(i.fourth);
			}
			this.crackHeadsOutgoingLines.clear();
		}

		if (this.crackHeadsGuess != null) {
			packetSender.startSection("crack_heads_guess");
			packetSender.write(this.crackHeadsGuess);
			this.crackHeadsGuess = null;
		}

		if (this.crackHeadsStartGame) {
			packetSender.startSection("crack_heads_start_game");
			this.crackHeadsStartGame = false;
		}

		if (this.crackHeadsPicking) {
			packetSender.startSection("crack_heads_pick");
			if (this.crackHeadsIsPickingWord) {
				packetSender.write(this.crackHeadsPickedWord);
			}
			else {
				packetSender.write(this.crackHeadsPickedCrackLevel);
			}
			this.crackHeadsPicking = false;
		}

		if (this.crackHeadsClearScreen) {
			packetSender.startSection("crack_heads_clear_screen");
			this.crackHeadsClearScreen = false;
		}
	}

	private void writePacketBlazingEights(PacketSender packetSender) {
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

	private void writePacketScrabble(PacketSender packetSender) {
		if (this.scrabbleOutgoingMove.size() != 0) {
			packetSender.startSection("scrabble_make_move");
			packetSender.write(this.scrabbleOutgoingMove.size());
			for (Pair<int[], Character> i : this.scrabbleOutgoingMove) {
				packetSender.write(i.first);
				packetSender.write(i.second);
			}
			this.scrabbleOutgoingMove.clear();
		}

		if (this.scrabbleStartGame) {
			packetSender.startSection("scrabble_start_game");
			packetSender.write(this.scrabbleStartGameRoundAmt);
			this.scrabbleStartGameRoundAmt = 0;
			this.scrabbleStartGame = false;
		}

		if (this.scrabbleSkipMove) {
			packetSender.startSection("scrabble_skip_move");
			this.scrabbleSkipMove = false;
		}
	}

	private void writePacketChess(PacketSender packetSender) {
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
			packetSender.write(this.ID);
			packetSender.write(this.chessMoveFrom);
			packetSender.write(this.chessMoveTo);
			this.chessMakeMove = false;
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
			break;
		}

		case "return_to_main_lobby": {
			this.curGame = GameServer.LOBBY;
			break;
		}
		}

		switch (this.curGame) {
		case GameServer.CHESS:
			this.readPacketChess(packetListener);
			break;

		case GameServer.SCRABBLE:
			this.readPacketScrabble(packetListener);
			break;

		case GameServer.BLAZING_EIGHTS:
			this.readPacketBlazingEights(packetListener);
			break;

		case GameServer.CRACK_HEADS:
			this.readPacketCrackHeads(packetListener);
			break;
		}
	}

	private void readPacketCrackHeads(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "crack_heads_draw_line": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				Vec2 a = packetListener.readVec2();
				Vec2 b = packetListener.readVec2();
				float size = packetListener.readFloat();
				int colorIndex = packetListener.readInt();
				this.crackHeadsIncomingLines.add(new Quad<Vec2, Vec2, Float, Integer>(a, b, size, colorIndex));
			}
			break;
		}

		case "crack_heads_clear_screen": {
			this.crackHeadsShouldClearScreen = true;
			break;
		}

		case "crack_heads_start_game": {
			this.crackHeadsGameStarting = true;
			break;
		}

		case "crack_heads_end_game": {
			this.crackHeadsGameEnding = true;
			break;
		}

		case "crack_heads_guess": {
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int clientID = packetListener.readInt();
				String guess = packetListener.readString();
				this.crackHeadsIncomingGuesses.add(new Pair<Integer, String>(clientID, guess));
			}
			break;
		}

		case "crack_heads_pick_phase": {
			this.crackHeadsPickPhaseStarting = true;
			int wordPickerID = packetListener.readInt();
			int amt = packetListener.readInt();
			this.crackHeadsWordOptions = new ArrayList<String>();
			for (int i = 0; i < amt; i++) {
				String str = packetListener.readString();
				this.crackHeadsWordOptions.add(str);
			}
			this.crackHeadsIsPickingWord = wordPickerID == this.getID();
			break;
		}

		case "crack_heads_draw_phase": {
			this.crackHeadsDrawPhaseStarting = true;
			int drawerID = packetListener.readInt();
			String word = packetListener.readString();
			int amt = packetListener.readInt();
			this.crackHeadsCrackLevels = new HashMap<Integer, Integer>();
			for (int i = 0; i < amt; i++) {
				int id = packetListener.readInt();
				int level = packetListener.readInt();
				this.crackHeadsCrackLevels.put(id, level);
			}
			this.crackHeadsIsDrawing = drawerID == this.getID();
			this.crackHeadsDrawPhaseWord = word;
			this.crackHeadsHints = new HashSet<Integer>();
			break;
		}

		case "crack_heads_points": {
			this.crackHeadsPoints = new HashMap<>();
			int amt = packetListener.readInt();
			for (int i = 0; i < amt; i++) {
				int id = packetListener.readInt();
				int points = packetListener.readInt();
				this.crackHeadsPoints.put(id, points);
			}
			break;
		}

		case "crack_heads_hint": {
			int index = packetListener.readInt();
			this.crackHeadsHints.add(index);
			this.crackHeadsNewHint = true;
			break;
		}
		}
	}

	private void readPacketBlazingEights(PacketListener packetListener) throws IOException {
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

	private void readPacketScrabble(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "scrabble_start_game": {
			this.scrabbleGame = new ScrabbleGame();
			this.scrabblePlayerScores.clear();
			this.scrabblePlayerHands.clear();
			this.scrabbleIsGameStarting = true;

			this.scrabbleIncomingMove.clear();
			this.scrabbleOutgoingMove.clear();
			break;
		}

		case "scrabble_end_game": {
			this.scrabbleIsGameEnding = true;
			break;
		}

		case "scrabble_next_player": {
			this.scrabbleRoundsLeft = packetListener.readInt();
			this.scrabbleNextPlayer = packetListener.readInt();
			break;
		}

		case "scrabble_make_move": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int[] coords = packetListener.readNInts(2);
				char letter = (char) packetListener.readInt();
				this.scrabbleIncomingMove.add(new Pair<int[], Character>(coords, letter));
			}
			this.scrabbleIsMoveIncoming = true;
			if (this.scrabbleGame.makeMove(this.scrabbleIncomingMove) != -1) {
				this.scrabbleIsIncomingMoveValid = true;
			}
			break;
		}

		case "scrabble_player_scores": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int playerID = packetListener.readInt();
				int score = packetListener.readInt();
				this.scrabblePlayerScores.put(playerID, score);
			}
			break;
		}

		case "scrabble_player_hand": {
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int playerID = packetListener.readInt();
				ArrayList<Character> hand = new ArrayList<>();
				for (int j = 0; j < ScrabbleGame.handSize; j++) {
					hand.add((char) packetListener.readInt());
				}
				this.scrabblePlayerHands.put(playerID, hand);
			}
			break;
		}
		}
	}

	private void readPacketChess(PacketListener packetListener) throws IOException {
		switch (packetListener.getSectionName()) {
		case "chess_lobby_updates": {
			this.chessHasLobbyUpdates = true;
			int elementAmt = packetListener.readInt();
			for (int i = 0; i < elementAmt; i++) {
				int updateType = packetListener.readInt();
				int whichGame = packetListener.readInt();
				switch (updateType) {
				case GameServer.CREATE: {
					ChessGame game = new ChessGame();
					game.setID(whichGame);
					System.out.println("ADD CHESS GAME : " + whichGame);
					this.chessGames.put(whichGame, game);

					//lack of break is intentional
				}

				case GameServer.UPDATE: {
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
					if (whiteID == this.ID || blackID == this.ID || game.getSpectators().contains(this.ID)) {
						this.chessCurGameID = whichGame;
						if (game.getSpectators().contains(this.ID)) {
							this.chessIsSpectating = true;
						}
					}
					break;
				}

				case GameServer.DELETE: {
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

	public void resetCrackHeadsGameInfo() {
		this.crackHeadsIncomingLines.clear();
		this.crackHeadsOutgoingLines.clear();
	}

	public HashMap<Integer, Integer> crackHeadsGetCrackLevels() {
		return this.crackHeadsCrackLevels;
	}

	public void crackHeadsClearScreen() {
		this.crackHeadsClearScreen = true;
	}

	public boolean crackHeadsShouldClearScreen() {
		if (!this.crackHeadsShouldClearScreen) {
			return false;
		}
		this.crackHeadsShouldClearScreen = false;
		return true;
	}

	public boolean crackHeadsHasNewHint() {
		if (!this.crackHeadsNewHint) {
			return false;
		}
		this.crackHeadsNewHint = false;
		return true;
	}

	public HashSet<Integer> crackHeadsGetHints() {
		return this.crackHeadsHints;
	}

	public ArrayList<Pair<Integer, String>> crackHeadsGetIncomingGuesses() {
		ArrayList<Pair<Integer, String>> ret = new ArrayList<>();
		ret.addAll(this.crackHeadsIncomingGuesses);
		this.crackHeadsIncomingGuesses.clear();
		return ret;
	}

	public ArrayList<String> crackHeadsGetWordOptions() {
		return this.crackHeadsWordOptions;
	}

	public String crackHeadsGetDrawPhaseWord() {
		return this.crackHeadsDrawPhaseWord;
	}

	public void crackHeadsMakeGuess(String guess) {
		this.crackHeadsGuess = guess;
	}

	public ArrayList<Quad<Vec2, Vec2, Float, Integer>> crackHeadsGetIncomingLines() {
		ArrayList<Quad<Vec2, Vec2, Float, Integer>> ret = new ArrayList<>();
		ret.addAll(this.crackHeadsIncomingLines);
		this.crackHeadsIncomingLines.clear();
		return ret;
	}

	public HashMap<Integer, Integer> crackHeadsGetPoints() {
		return this.crackHeadsPoints;
	}

	public boolean crackHeadsIsDrawing() {
		return this.crackHeadsIsDrawing;
	}

	public boolean crackHeadsIsPickingWord() {
		return this.crackHeadsIsPickingWord;
	}

	public void crackHeadsPickWord(String word) {
		this.crackHeadsPicking = true;
		this.crackHeadsPickedWord = word;
	}

	public void crackHeadsPickCrackLevel(int level) {
		this.crackHeadsPicking = true;
		this.crackHeadsPickedCrackLevel = level;
	}

	public boolean crackHeadsPickPhaseStarting() {
		if (!this.crackHeadsPickPhaseStarting) {
			return false;
		}
		this.crackHeadsPickPhaseStarting = false;
		return true;
	}

	public boolean crackHeadsDrawPhaseStarting() {
		if (!this.crackHeadsDrawPhaseStarting) {
			return false;
		}
		this.crackHeadsDrawPhaseStarting = false;
		return true;
	}

	public boolean crackHeadsGameStarting() {
		if (!this.crackHeadsGameStarting) {
			return false;
		}
		this.crackHeadsGameStarting = false;
		return true;
	}

	public boolean crackHeadsGameEnding() {
		if (!this.crackHeadsGameEnding) {
			return false;
		}
		this.crackHeadsGameEnding = false;
		return true;
	}

	public void crackHeadsStartGame() {
		if (this.isHost()) {
			this.crackHeadsStartGame = true;
		}
	}

	public void crackHeadsDrawLine(Vec2 a, Vec2 b, float size, int colorIndex) {
		this.crackHeadsOutgoingLines.add(new Quad<Vec2, Vec2, Float, Integer>(a, b, size, colorIndex));
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
		return this.blazingEightsMoveOrder.get(this.blazingEightsMoveIndex) == this.getID();
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

	public void resetScrabbleGameInfo() {
		this.scrabbleIncomingMove.clear();
		this.scrabbleOutgoingMove.clear();
		this.scrabbleIsGameStarting = false;
		this.scrabblePlayerHands.clear();
		this.scrabblePlayerScores.clear();
	}

	public void scrabbleSkipMove() {
		this.scrabbleSkipMove = true;
	}

	public int scrabbleGetRoundsLeft() {
		return this.scrabbleRoundsLeft;
	}

	public boolean scrabbleIsMoveIncoming() {
		if (!this.scrabbleIsMoveIncoming) {
			return false;
		}
		this.scrabbleIsMoveIncoming = false;
		return true;
	}

	public boolean scrabbleIsIncomingMoveValid() {
		if (!this.scrabbleIsIncomingMoveValid) {
			return false;
		}
		this.scrabbleIsIncomingMoveValid = false;
		return true;
	}

	public ArrayList<Character> scrabbleGetPlayerHand(int id) {
		return this.scrabblePlayerHands.get(id);
	}

	public ScrabbleGame scrabbleGetGame() {
		return this.scrabbleGame;
	}

	public int scrabbleGetNextPlayer() {
		return this.scrabbleNextPlayer;
	}

	public HashMap<Integer, Integer> scrabbleGetPlayerScores() {
		return this.scrabblePlayerScores;
	}

	public boolean scrabbleIsGameStarting() {
		if (!this.scrabbleIsGameStarting) {
			return false;
		}
		this.scrabbleIsGameStarting = false;
		return true;
	}

	public boolean scrabbleIsGameEnding() {
		if (!this.scrabbleIsGameEnding) {
			return false;
		}
		this.scrabbleIsGameEnding = false;
		return true;
	}

	public void scrabbleStartGame(int roundAmt) {
		if (!this.isHost) {
			return;
		}
		this.scrabbleStartGame = true;
		this.scrabbleStartGameRoundAmt = roundAmt;
	}

	public boolean scrabbleMakeMove(ArrayList<Pair<int[], Character>> move) {
		if (this.getID() != this.scrabbleNextPlayer) {
			return false;
		}
		this.scrabbleOutgoingMove.addAll(move);
		return true;
	}

	public ArrayList<Pair<int[], Character>> scrabbleGetIncomingMove() {
		ArrayList<Pair<int[], Character>> ret = new ArrayList<>();
		ret.addAll(this.scrabbleIncomingMove);
		this.scrabbleIncomingMove.clear();
		return ret;
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
		this.curGame = whichGame;
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
