package state;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;

import audio.Sound;
import entity.Entity;
import game.ScrabbleGame;
import graphics.Framebuffer;
import graphics.Material;
import graphics.Texture;
import graphics.TextureMaterial;
import input.Button;
import input.Input;
import input.MouseInput;
import input.TextField;
import main.Main;
import model.AssetManager;
import model.FilledRectangle;
import model.Model;
import scene.DirLight;
import scene.Light;
import scene.Scene;
import screen.PerspectiveScreen;
import screen.Screen;
import screen.UIScreen;
import server.GameClient;
import server.GameServer;
import ui.Text;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FontUtils;
import util.Mat4;
import util.Pair;
import util.Vec2;
import util.Vec3;
import util.Vec4;

public class ScrabbleState extends State {

	//the server will create an scrabble game based off of the host's specifications, and will communicate
	//all the relevant information to the clients. 

	//when making moves, the clients will send their moves to the server to be validated. If the server validates the move, then
	//it will tell all the clients the move, and who made the move, and then tell who's turn it is next. 

	// -- TODO
	// - recall all tiles button
	// - end game button
	// - better ui animations when someone makes a move

	// -- FINISHED

	private static final int BACKGROUND_SCENE = 0;
	private static final int INPUT_SCENE = 1;
	private static final int BOARD_SCENE = 2;
	private static final int CELL_SCENE = 3;
	private static final int CELL_TEXT_SCENE = 4;
	private static final int TILE_SCENE = 5;
	private static final int TILE_TEXT_SCENE = 6;
	private static final int HELD_TILE_SCENE = 7;
	private static final int HELD_TILE_TEXT_SCENE = 8;

	private static final int HAND_BACKGROUND_SCENE = 9;
	private static final int HAND_CELL_SCENE = 10;
	private static final int HAND_TILE_SCENE = 11;
	private static final int HAND_TILE_TEXT_SCENE = 12;

	private static final int HUD_SCENE = 13;
	private static final int HUD_TEXT_SCENE = 14;

	private Material backgroundColor = new Material(new Vec3(247, 213, 173).mul(1.0f / 255.0f));
	private Material boardBackgroundColor = new Material(new Vec3(255, 247, 227).mul(1.0f / 255.0f));

	private Material normalCellColor = new Material(new Vec3(255, 228, 198).mul(1.0f / 255.0f));

	private Material doubleLetterColor = new Material(new Vec3(122, 168, 128).mul(1.0f / 255.0f));
	private Material tripleLetterColor = new Material(new Vec3(20, 170, 190).mul(1.0f / 255.0f));
	private Material doubleWordColor = new Material(new Vec3(200, 120, 50).mul(1.0f / 255.0f));
	private Material tripleWordColor = new Material(new Vec3(144, 63, 23).mul(1.0f / 255.0f));
	private Material startCellColor = new Material(new Vec3(90, 70, 90).mul(1.0f / 255.0f));

	private Material tileColor = new Material(new Vec3(255, 135, 92).mul(1.0f / 255.0f));
	private Material moveableTileColor = new Material(new Vec3(242, 175, 146).mul(1.0f / 255.0f));

	private Material lightGray = new Material(new Vec3(66, 69, 73).mul(1.0f / 255.0f));
	private Material lightBlue = new Material(new Vec3(114, 137, 218).mul(1.0f / 255.0f));
	private Material gray = new Material(new Vec3(54, 57, 62).mul(1.0f / 255.0f));
	private Material darkGray = new Material(new Vec3(40, 43, 48).mul(1.0f / 255.0f));

	private TextureMaterial tileTexture;

	private GameClient client;
	private State mainLobbyState;

	private UIScreen uiScreen;
	private UIScreen boardScreen;

	private ScrabbleGame game;

	private boolean isInGame = false;

	private UIFilledRectangle scrabbleBoardBackground;

	private int boardInnerMarginPx = 20;
	private int cellGapPx = 2;
	private int cellSizePx = 80;
	private int boardBackgroundSizePx = cellSizePx * ScrabbleGame.boardSize + cellGapPx * (ScrabbleGame.boardSize - 1) + boardInnerMarginPx * 2;

	private int tileHoveredSize = 90;

	//maybe have some auxillary arrays describing where each cell rect is?
	//the problem with doing away with the 2d arrays is that in order to process moves, we need to know where each letter is placed. 
	//maybe have a mapping between each cell, and it's coordinates, whether it is on the board, or in the hand.
	//also, maybe have a mapping between each cell and which scene the tile should be drawn in if it is bound to it. 

	//to find out the coordinates of a given tile, you can just look at the coordinates of it's parent cell. 

	//cellID, cellRect
	private HashMap<Long, UIFilledRectangle> cellRects;

	//cellID, cellCoords : -1 in first coordinate if in hand
	//if cellCoords[0] == -1:
	// - this cell belongs to the hand, cellCoords[1] denotes which slot
	//else:
	// - provides coords
	private HashMap<Long, int[]> cellCoords;

	//cellID, {tileScene, tileTextScene}
	//is this one required? we already have info on whether or not a cell is in the hand in the cellCoords map
	//provides which scene a tile should be drawn given a cell
	private HashMap<Long, int[]> cellScenes;

	//tileID, tileRect
	private HashMap<Long, UIFilledRectangle> tileRects;

	//cellID, tileID
	//cellTiles.get(cellID) = tileID means that tileID is bound to cellID
	private HashMap<Long, Long> cellTiles;

	//tileID, char
	private HashMap<Long, Character> tileLetters;

	//tileID, isMoveable
	//determines whether or not you can move a given tile.
	private HashMap<Long, Boolean> tileIsMoveable;

	private boolean draggingBoard = false;
	private int camXOffset, camYOffset;
	private Vec2 mousePos;

	private long hoveredTileID, hoveredCellID;

	private boolean tileHeld = false;
	private long tileHeldRectID;
	private long tileHeldOriginalCellID;

	private int uiElementGap = 20;

	private int numRounds = 5;

	private HashMap<Integer, Integer> playerScores;

	public ScrabbleState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm);

		this.client = client;
		this.mainLobbyState = mainLobbyState;
	}

	@Override
	public void load() {
		this.uiScreen = new UIScreen();
		this.uiScreen.setClearColorIDBufferOnRender(false);

		this.boardScreen = new UIScreen();
		this.boardScreen.setClearColorIDBufferOnRender(true);

		Main.unlockCursor();
		Entity.killAll();

		this.game = new ScrabbleGame();

		this.camXOffset += (Main.windowWidth - boardBackgroundSizePx) / 2;
		this.camYOffset += (Main.windowHeight - boardBackgroundSizePx) / 2;

		this.boardScreen.setLeft(-camXOffset);
		this.boardScreen.setRight(-camXOffset + Main.windowWidth);
		this.boardScreen.setBottom(camYOffset);
		this.boardScreen.setTop(camYOffset + Main.windowHeight);

		this.mousePos = MouseInput.getMousePos();

		this.tileTexture = new TextureMaterial(new Texture("/scrabble/scrabble_tile.png", Texture.VERTICAL_FLIP_BIT));

		this.drawUI();

		// -- ONLY DO THIS ONCE D: --
		this.drawScrabbleGame();
		this.drawHand();

		this.placeTileOnCell(7, 4, 'L', true);
		this.placeTileOnCell(7, 5, 'E', true);
		this.placeTileOnCell(7, 6, 'T', true);
		this.placeTileOnCell(7, 7, 'T', true);
		this.placeTileOnCell(7, 8, 'E', true);
		this.placeTileOnCell(7, 9, 'R', true);

		this.placeTileOnCell(4, 8, 'L', true);
		this.placeTileOnCell(5, 8, 'E', true);
		this.placeTileOnCell(6, 8, 'G', true);
		this.placeTileOnCell(7, 8, 'E', true);
		this.placeTileOnCell(8, 8, 'N', true);
		this.placeTileOnCell(9, 8, 'D', true);
		this.placeTileOnCell(10, 8, 'S', true);
	}

	@Override
	public void kill() {
		this.uiScreen.kill();
		this.boardScreen.kill();
	}

	private void drawUI() {
		//don't clear the scenes related to board rendering
		//it is problematic
		this.clearScene(BACKGROUND_SCENE);
		this.clearScene(INPUT_SCENE);
		this.clearScene(HUD_SCENE);
		this.clearScene(HUD_TEXT_SCENE);

		// -- BACKGROUND --
		UIFilledRectangle backgroundRect = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, BACKGROUND_SCENE);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		backgroundRect.setMaterial(this.backgroundColor);

		if (this.isInGame) {
			this.drawGameHUD();
		}
		else {
			this.drawPregameMenu();
		}

		this.drawScoreDisplay();

		UIElement.alignAllUIElements();
	}

	private void drawPregameMenu() {
		if (this.client.isHost()) {
			int rectWidth = this.cellSizePx * 3 + this.uiElementGap * 2;
			int rectHeight = this.cellSizePx * 2 + this.uiElementGap * 3;

			UIFilledRectangle backgroundRect = new UIFilledRectangle(this.uiElementGap, this.uiElementGap, 0, rectWidth, rectHeight, HUD_SCENE);
			backgroundRect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
			backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
			backgroundRect.setMaterial(this.lightGray);

			Button startGameBtn = new Button(this.uiElementGap, this.uiElementGap, this.cellSizePx * 3, this.cellSizePx, "btn_start_game", "Start Game", FontUtils.ggsans.deriveFont(Font.BOLD), 36, INPUT_SCENE);
			startGameBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
			startGameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
			startGameBtn.bind(backgroundRect);

			Button incrementRounds = new Button(this.uiElementGap, this.uiElementGap, this.cellSizePx, this.cellSizePx, "btn_increment_rounds", ">", FontUtils.ggsans.deriveFont(Font.BOLD), 36, INPUT_SCENE);
			incrementRounds.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
			incrementRounds.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);
			incrementRounds.bind(backgroundRect);

			Button decrementRounds = new Button(this.uiElementGap, this.uiElementGap, this.cellSizePx, this.cellSizePx, "btn_decrement_rounds", "<", FontUtils.ggsans.deriveFont(Font.BOLD), 36, INPUT_SCENE);
			decrementRounds.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			decrementRounds.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
			decrementRounds.bind(backgroundRect);

			UIFilledRectangle textAlignmentRect = new UIFilledRectangle(0, this.uiElementGap, 0, this.cellSizePx, this.cellSizePx, HUD_SCENE);
			textAlignmentRect.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
			textAlignmentRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
			textAlignmentRect.setMaterial(this.lightGray);
			textAlignmentRect.bind(backgroundRect);

			Text roundCounter = new Text(0, 0, this.numRounds + "", FontUtils.ggsans.deriveFont(Font.BOLD), 36, Color.WHITE, HUD_TEXT_SCENE);
			roundCounter.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
			roundCounter.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			roundCounter.bind(textAlignmentRect);

			UIFilledRectangle returnToLobbyBackgroundRect = new UIFilledRectangle(this.uiElementGap, this.uiElementGap, 0, rectWidth, this.cellSizePx + this.uiElementGap * 2, HUD_SCENE);
			returnToLobbyBackgroundRect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
			returnToLobbyBackgroundRect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);
			returnToLobbyBackgroundRect.setMaterial(this.lightGray);

			Button returnToLobbyBtn = new Button(this.uiElementGap, this.uiElementGap, this.cellSizePx * 3, this.cellSizePx, "btn_return_to_lobby", "Return To Lobby", FontUtils.ggsans.deriveFont(Font.BOLD), 24, INPUT_SCENE);
			returnToLobbyBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
			returnToLobbyBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);
			returnToLobbyBtn.bind(returnToLobbyBackgroundRect);
		}
	}

	private void drawScoreDisplay() {
		// -- SCORE DISPLAY --
		if (this.playerScores == null) {
			return;
		}
		HashMap<Integer, String> players = this.client.getPlayers();

		int playerRectWidth = 250;
		int playerRectHeight = 50;
		int playerRectGap = 5;
		int scoreboardWidth = playerRectWidth + playerRectGap * 2;
		int scoreboardHeight = playerRectHeight * players.size() + playerRectGap * (players.size() + 1);

		UIFilledRectangle scoreboardBackground = new UIFilledRectangle(this.uiElementGap, this.uiElementGap, 0, scoreboardWidth, scoreboardHeight, HUD_SCENE);
		scoreboardBackground.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		scoreboardBackground.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		scoreboardBackground.setMaterial(this.gray);

		int yOffset = playerRectGap;
		int xOffset = playerRectGap;
		for (int id : this.playerScores.keySet()) {
			int score = this.playerScores.get(id);
			String nick = players.get(id);

			UIFilledRectangle playerRect = new UIFilledRectangle(xOffset, yOffset, 0, playerRectWidth, playerRectHeight, HUD_SCENE);
			playerRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			playerRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
			playerRect.setMaterial(this.lightGray);
			playerRect.bind(scoreboardBackground);

			Text nickText = new Text(playerRectGap, playerRectGap, nick, FontUtils.ggsans.deriveFont(Font.BOLD), 24, Color.WHITE, HUD_TEXT_SCENE);
			nickText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			nickText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
			nickText.bind(playerRect);

			Text scoreText = new Text(playerRectGap, playerRectGap, score + "", FontUtils.ggsans, 16, Color.WHITE, HUD_TEXT_SCENE);
			scoreText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			scoreText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
			scoreText.bind(playerRect);

			yOffset += playerRectHeight + playerRectGap;
		}
	}

	private void drawGameHUD() {
		// -- MOVE INDICATOR --
		int rectWidth = 500;
		int rectHeight = 50;

		UIFilledRectangle moveIndicatorRect = new UIFilledRectangle(0, 0, 0, rectWidth, rectHeight, HUD_SCENE);
		moveIndicatorRect.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		moveIndicatorRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		moveIndicatorRect.setMaterial(this.gray);

		int nextPlayer = this.client.scrabbleGetNextPlayer();
		String indicatorString = this.client.getPlayers().get(nextPlayer) + "'s Move";
		if (nextPlayer == this.client.getID()) {
			indicatorString = "Your Move";
		}

		Text moveIndicatorText = new Text(0, 0, indicatorString, FontUtils.ggsans.deriveFont(Font.BOLD), 32, Color.WHITE, HUD_TEXT_SCENE);
		moveIndicatorText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		moveIndicatorText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		moveIndicatorText.bind(moveIndicatorRect);

		UIFilledRectangle makeMoveBackground = new UIFilledRectangle(0, rectHeight, 0, rectWidth, rectHeight, HUD_SCENE);
		makeMoveBackground.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		makeMoveBackground.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);

		int curMoveScore = this.getCurMoveScore();
		int curMoveSize = this.getCurMove().size();
		String makeMoveBtnString = "Play Move";

		if (curMoveScore != -1 && nextPlayer == this.client.getID()) {
			makeMoveBackground.setMaterial(this.tileColor);
			makeMoveBtnString += " (" + curMoveScore + " points)";
		}
		else if (curMoveScore == -1 && curMoveSize != 0 && nextPlayer == this.client.getID()) {
			makeMoveBackground.setMaterial(this.tileColor);
			makeMoveBtnString = "Swap Out Active Tiles";
		}
		else {
			makeMoveBackground.setMaterial(this.darkGray);
		}

		Button makeMoveBtn = new Button(0, 0, rectWidth, rectHeight, "btn_make_move", makeMoveBtnString, FontUtils.ggsans.deriveFont(Font.BOLD), 20, INPUT_SCENE);
		makeMoveBtn.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		makeMoveBtn.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		makeMoveBtn.bind(makeMoveBackground);

		// -- ROUND INDICATOR --
		UIFilledRectangle roundIndicatorRect = new UIFilledRectangle(this.uiElementGap, this.uiElementGap, 0, 200, 60, HUD_SCENE);
		roundIndicatorRect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		roundIndicatorRect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);
		roundIndicatorRect.setMaterial(this.lightGray);

		Text roundIndicatorText = new Text(0, 0, this.client.scrabbleGetRoundsLeft() + " Rounds Left", FontUtils.ggsans.deriveFont(Font.BOLD), 24, Color.WHITE, HUD_TEXT_SCENE);
		roundIndicatorText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		roundIndicatorText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		roundIndicatorText.bind(roundIndicatorRect);
	}

	private void drawScrabbleGame() {
		this.scrabbleBoardBackground = new UIFilledRectangle(0, 0, -1, this.boardBackgroundSizePx, this.boardBackgroundSizePx, BOARD_SCENE);
		this.scrabbleBoardBackground.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.scrabbleBoardBackground.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.scrabbleBoardBackground.setMaterial(this.boardBackgroundColor);

		this.cellRects = new HashMap<>();
		this.cellScenes = new HashMap<>();
		this.cellCoords = new HashMap<>();
		this.cellTiles = new HashMap<>();

		this.tileRects = new HashMap<>();
		this.tileLetters = new HashMap<>();
		this.tileIsMoveable = new HashMap<>();

		for (int i = 0; i < ScrabbleGame.boardSize; i++) {
			for (int j = 0; j < ScrabbleGame.boardSize; j++) {
				int xOffset = (this.cellSizePx + this.cellGapPx) * j + this.boardInnerMarginPx;
				int yOffset = (this.cellSizePx + this.cellGapPx) * i + this.boardInnerMarginPx;

				int cellType = this.game.getBonusBoard()[i][j];

				UIFilledRectangle cellRect = new UIFilledRectangle(xOffset, yOffset, 0, this.cellSizePx, this.cellSizePx, CELL_SCENE);
				cellRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
				cellRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
				cellRect.bind(this.scrabbleBoardBackground);

				String cellString = "";

				switch (cellType) {
				case ScrabbleGame.BONUS_EMPTY: {
					cellRect.setMaterial(this.normalCellColor);
					break;
				}

				case ScrabbleGame.BONUS_LETTER_DOUBLE: {
					cellRect.setMaterial(this.doubleLetterColor);
					cellString = "DL";
					break;
				}

				case ScrabbleGame.BONUS_LETTER_TRIPLE: {
					cellRect.setMaterial(this.tripleLetterColor);
					cellString = "TL";
					break;
				}

				case ScrabbleGame.BONUS_WORD_DOUBLE: {
					cellRect.setMaterial(this.doubleWordColor);
					cellString = "DW";
					break;
				}

				case ScrabbleGame.BONUS_WORD_TRIPLE: {
					cellRect.setMaterial(this.tripleWordColor);
					cellString = "TW";
					break;
				}

				case ScrabbleGame.BONUS_START: {
					cellRect.setMaterial(this.startCellColor);
					cellString = "S";
					break;
				}
				}

				if (cellString.length() != 0) {
					Text cellText = new Text(0, 0, cellString, FontUtils.ggsans.deriveFont(Font.BOLD), 24, Color.WHITE, CELL_TEXT_SCENE);
					cellText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
					cellText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
					cellText.bind(cellRect);
				}

				long cellID = cellRect.getID();

				this.cellRects.put(cellID, cellRect);
				this.cellCoords.put(cellID, new int[] { i, j });
				this.cellScenes.put(cellID, new int[] { TILE_SCENE, TILE_TEXT_SCENE });
			}
		}
	}

	private void drawHand() {
		int handBackgroundWidth = ScrabbleGame.handSize * this.cellSizePx + (ScrabbleGame.handSize + 1) * this.cellGapPx;
		int handBackgroundHeight = this.cellSizePx + this.cellGapPx * 2;

		Material handMaterial = new Material(new Vec4(0, 0, 0, 0.3f));

		UIFilledRectangle backgroundRect = new UIFilledRectangle(0, this.uiElementGap, 1, handBackgroundWidth, handBackgroundHeight, HAND_CELL_SCENE);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_BOTTOM);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_BOTTOM);
		backgroundRect.setMaterial(handMaterial);

		for (int i = 0; i < ScrabbleGame.handSize; i++) {
			int xOffset = this.cellGapPx + i * (this.cellSizePx + this.cellGapPx);
			UIFilledRectangle cellRect = new UIFilledRectangle(xOffset, this.cellGapPx, 0, this.cellSizePx, this.cellSizePx, HAND_CELL_SCENE);
			cellRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			cellRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
			cellRect.bind(backgroundRect);
			cellRect.setMaterial(new Material(new Vec4(0, 0, 0, 0.3f)));

			long cellID = cellRect.getID();

			this.cellRects.put(cellID, cellRect);
			this.cellCoords.put(cellID, new int[] { -1, i });
			this.cellScenes.put(cellID, new int[] { HAND_TILE_SCENE, HAND_TILE_TEXT_SCENE });
		}
	}

	private void removeAllMoveableTiles() {
		this.releaseTile();

		ArrayList<Long> moveableTileIDs = new ArrayList<>();
		for (long tileID : this.tileRects.keySet()) {
			if (this.tileIsMoveable.get(tileID)) {
				moveableTileIDs.add(tileID);
			}
		}

		for (long tileID : moveableTileIDs) {
			this.removeTile(tileID);
		}
	}

	private void makeAllTilesMoveable() {
		for (long tileID : this.tileRects.keySet()) {
			this.tileIsMoveable.put(tileID, true);
		}
	}

	private UIFilledRectangle generateTile(char tileChar, boolean isMoveable, long cellID) {
		int tileScene = this.cellScenes.get(cellID)[0];
		int tileTextScene = this.cellScenes.get(cellID)[1];

		return this.generateTile(tileChar, isMoveable, tileScene, tileTextScene);
	}

	private UIFilledRectangle generateTile(char tileChar, boolean isMoveable, int tileScene, int textScene) {
		if (tileChar >= 'a' && tileChar <= 'z') {
			tileChar = (char) ((tileChar - 'a') + 'A');
		}
		if (tileChar < 'A' || tileChar > 'Z') {
			System.err.println("Can't generate tile with character: " + tileChar);
			return null;
		}

		UIFilledRectangle tileRect = new UIFilledRectangle(0, 0, 0, this.cellSizePx, this.cellSizePx, new FilledRectangle(), tileScene);
		tileRect.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		tileRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		tileRect.setTextureMaterial(this.tileTexture);
		tileRect.setMaterial(this.tileColor);

		tileRect.setClampAlignedCoordinatesToInt(false);

		int tilePoints = ScrabbleGame.letterScore.get(tileChar);

		Text tileText = new Text(0, 0, tileChar + "", FontUtils.ggsans.deriveFont(Font.BOLD), 32, Color.WHITE, textScene);
		tileText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		tileText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		tileText.setClampAlignedCoordinatesToInt(false);
		tileText.bind(tileRect);

		Text tilePointText = new Text(18, 18, tilePoints + "", FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, textScene);
		tilePointText.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		tilePointText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		tilePointText.setClampAlignedCoordinatesToInt(false);
		tilePointText.bind(tileRect);

		this.tileRects.put(tileRect.getID(), tileRect);
		this.tileLetters.put(tileRect.getID(), tileChar);
		this.tileIsMoveable.put(tileRect.getID(), isMoveable);

		return tileRect;
	}

	private void placeTileOnCell(int row, int col, char tileLetter, boolean isMoveable) {
		long cellID = -1;
		for (long id : this.cellRects.keySet()) {
			int[] next = this.cellCoords.get(id);
			if (next[0] == row && next[1] == col) {
				cellID = id;
				break;
			}
		}
		this.placeTileOnCell(cellID, tileLetter, isMoveable);
	}

	private void placeTileOnCell(long cellID, char tileLetter, boolean isMoveable) {
		if (!this.cellRects.containsKey(cellID) || this.cellTiles.containsKey(cellID)) {
			return;
		}

		UIFilledRectangle tileRect = this.generateTile(tileLetter, isMoveable, cellID);
		UIFilledRectangle cellRect = this.cellRects.get(cellID);
		tileRect.bind(cellRect);
		this.cellTiles.put(cellID, tileRect.getID());
	}

	private void removeTile(long tileID) {
		if (!this.tileRects.containsKey(tileID)) {
			return;
		}

		UIFilledRectangle tileRect = this.tileRects.get(tileID);

		if (tileRect.isBound()) {
			long cellID = tileRect.getParent().getID();
			this.cellTiles.remove(cellID);
		}

		this.tileRects.remove(tileID);
		this.tileLetters.remove(tileID);
		this.tileIsMoveable.remove(tileID);

		tileRect.kill();
	}

	private boolean pickUpTile(long tileID) {
		if (this.tileHeld) {
			return false;
		}

		if (!this.tileRects.containsKey(tileID)) {
			return false;
		}

		if (!this.tileIsMoveable.get(tileID)) {
			return false;
		}

		UIFilledRectangle tileRect = this.tileRects.get(tileID);
		long cellID = tileRect.getParent().getID();

		char tileLetter = this.tileLetters.get(tileID);

		UIFilledRectangle tileHeldRect = this.generateTile(tileLetter, true, HELD_TILE_SCENE, HELD_TILE_TEXT_SCENE);
		tileHeldRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);

		this.tileHeldRectID = tileHeldRect.getID();
		this.tileHeldOriginalCellID = cellID;
		this.tileHeld = true;

		this.removeTile(tileID);

		return true;
	}

	private void releaseTile() {
		if (!this.tileHeld) {
			return;
		}

		this.tileHeld = false;

		long cellID = this.tileHeldOriginalCellID;
		if (this.cellRects.containsKey(this.hoveredCellID) && this.cellTiles.get(this.hoveredCellID) == null) {
			cellID = this.hoveredCellID;
		}

		char tileLetter = this.tileLetters.get(this.tileHeldRectID);

		this.placeTileOnCell(cellID, tileLetter, true);

		this.removeTile(this.tileHeldRectID);
	}

	private ArrayList<Pair<int[], Character>> getCurMove() {
		ArrayList<Pair<int[], Character>> ans = new ArrayList<>();
		for (long tileID : this.tileRects.keySet()) {
			long cellID = this.tileRects.get(tileID).getParent().getID();
			int[] coords = this.cellCoords.get(cellID);
			if (coords[0] != -1 && this.tileIsMoveable.get(tileID)) {
				ans.add(new Pair<int[], Character>(new int[] { coords[0], coords[1] }, this.tileLetters.get(tileID)));
			}
		}
		return ans;
	}

	private int getCurMoveScore() {
		ScrabbleGame game = this.client.scrabbleGetGame();
		return game.getMoveScore(this.getCurMove());
	}

	private boolean isCurMoveValid() {
		return this.getCurMoveScore() != -1;
	}

	@Override
	public void update() {
		Input.inputsHovered(uiScreen.getEntityIDAtMouse());

		// -- NETWORKING --
		if (this.client.getCurGame() == GameServer.LOBBY) {
			this.sm.switchState(this.mainLobbyState);
		}

		if (this.client.scrabbleIsGameStarting()) {
			this.removeAllMoveableTiles();
			this.isInGame = true;

			//retrieve player's hand
			ArrayList<Character> hand = this.client.scrabbleGetPlayerHand(this.client.getID());
			for (int i = 0; i < hand.size(); i++) {
				this.placeTileOnCell(-1, i, hand.get(i), true);
			}

			//reset player scores
			this.playerScores = new HashMap<>();
			for (int i : this.client.getPlayers().keySet()) {
				this.playerScores.put(i, 0);
			}

			this.drawUI();
		}

		if (this.client.scrabbleIsGameEnding()) {
			this.makeAllTilesMoveable();
			this.isInGame = false;
			this.drawUI();
		}

		if (this.isInGame) {
			ArrayList<Pair<int[], Character>> nextMove = this.client.scrabbleGetIncomingMove();
			if (nextMove.size() != 0) {
				this.removeAllMoveableTiles();

				//retrieve player's hand
				ArrayList<Character> hand = this.client.scrabbleGetPlayerHand(this.client.getID());
				for (int i = 0; i < hand.size(); i++) {
					this.placeTileOnCell(-1, i, hand.get(i), true);
				}

				if (this.client.scrabbleIsIncomingMoveValid()) {
					//place down move if valid
					for (Pair<int[], Character> i : nextMove) {
						this.placeTileOnCell(i.first[0], i.first[1], i.second, false);
					}
				}

				this.playerScores = this.client.scrabbleGetPlayerScores();

				this.drawUI();
			}
		}

		// -- OTHER STUFF --
		Vec2 curMousePos = MouseInput.getMousePos();
		if (this.draggingBoard) {
			int xDiff = (int) (curMousePos.x - this.mousePos.x);
			int yDiff = (int) (curMousePos.y - this.mousePos.y);

			this.camXOffset += xDiff;
			this.camYOffset += yDiff;

			this.boardScreen.setLeft(-camXOffset);
			this.boardScreen.setRight(-camXOffset + Main.windowWidth);
			this.boardScreen.setBottom(camYOffset);
			this.boardScreen.setTop(camYOffset + Main.windowHeight);
		}
		this.mousePos.set(curMousePos);

		if (this.tileHeld) {
			UIFilledRectangle tileRect = this.tileRects.get(this.tileHeldRectID);
			tileRect.setFrameAlignmentOffset(this.mousePos.x, this.mousePos.y);
			tileRect.align();
		}

		// -- Tile Animations --
		for (long tileID : this.tileRects.keySet()) {
			UIFilledRectangle t = this.tileRects.get(tileID);

			if (!this.tileIsMoveable.get(tileID)) {
				continue;
			}

			float targetWidth = this.cellSizePx;
			if (t.getID() == this.hoveredTileID) {
				targetWidth = this.tileHoveredSize;
			}

			float curWidth = t.getWidth();
			float diff = targetWidth - curWidth;

			diff *= 0.7f;

			t.setWidth(curWidth + diff);
			t.setHeight(curWidth + diff);

			if (Math.abs(diff) > 0.001f) {
				t.align();
			}
		}

		Entity.updateEntities();
		Model.updateModels();
	}

	@Override
	public void render(Framebuffer outputBuffer) {
		uiScreen.setUIScene(BACKGROUND_SCENE);
		uiScreen.render(outputBuffer);

		boardScreen.setUIScene(BOARD_SCENE);
		boardScreen.render(outputBuffer);

		boardScreen.setUIScene(CELL_SCENE);
		boardScreen.render(outputBuffer);
		this.hoveredCellID = boardScreen.getEntityIDAtMouse();
		boardScreen.setUIScene(CELL_TEXT_SCENE);
		boardScreen.render(outputBuffer);

		boardScreen.setUIScene(TILE_SCENE);
		boardScreen.render(outputBuffer);
		this.hoveredTileID = boardScreen.getEntityIDAtMouse();
		boardScreen.setUIScene(TILE_TEXT_SCENE);
		boardScreen.render(outputBuffer);

		uiScreen.setUIScene(HAND_CELL_SCENE);
		uiScreen.render(outputBuffer);
		if (this.cellRects.containsKey(uiScreen.getEntityIDAtMouse())) {
			this.hoveredCellID = uiScreen.getEntityIDAtMouse();
		}

		uiScreen.setUIScene(HAND_TILE_SCENE);
		uiScreen.render(outputBuffer);
		if (this.tileRects.containsKey(uiScreen.getEntityIDAtMouse())) {
			this.hoveredTileID = uiScreen.getEntityIDAtMouse();
		}
		uiScreen.setUIScene(HAND_TILE_TEXT_SCENE);
		uiScreen.render(outputBuffer);

		uiScreen.setUIScene(HELD_TILE_SCENE);
		uiScreen.render(outputBuffer);
		uiScreen.setUIScene(HELD_TILE_TEXT_SCENE);
		uiScreen.render(outputBuffer);

		uiScreen.setUIScene(HUD_SCENE);
		uiScreen.render(outputBuffer);
		uiScreen.setUIScene(HUD_TEXT_SCENE);
		uiScreen.render(outputBuffer);

		uiScreen.setUIScene(INPUT_SCENE);
		uiScreen.render(outputBuffer);
	}

	@Override
	public void mousePressed(int button) {
		Input.inputsPressed(uiScreen.getEntityIDAtMouse());

		if (!this.tileHeld) {
			this.pickUpTile(this.hoveredTileID);
		}

		if (!this.tileHeld) {
			this.draggingBoard = true;
		}
	}

	@Override
	public void mouseReleased(int button) {
		Input.inputsReleased(uiScreen.getEntityIDAtMouse());
		String clickedButton = Input.getClicked();
		switch (clickedButton) {
		case "btn_start_game": {
			if (this.isInGame) {
				break;
			}
			this.client.scrabbleStartGame(this.numRounds); //TODO make this adjustable
			break;
		}

		case "btn_make_move": {
			if (this.getCurMove().size() == 0 || this.client.getID() != this.client.scrabbleGetNextPlayer()) {
				break;
			}
			this.client.scrabbleMakeMove(this.getCurMove());
			break;
		}

		case "btn_increment_rounds": {
			this.numRounds++;
			this.drawUI();
			break;
		}

		case "btn_decrement_rounds": {
			this.numRounds--;
			this.drawUI();
			break;
		}

		case "btn_return_to_lobby": {
			this.client.returnToMainLobby();
			break;
		}
		}

		if (this.tileHeld) {
			this.releaseTile();

			this.drawUI();
		}

		this.draggingBoard = false;
	}

	@Override
	public void keyPressed(int key) {
		if (this.isInGame && key == GLFW_KEY_C && this.client.isHost()) {
			ArrayList<Character> hand = new ArrayList<>();
			for (long id : this.tileRects.keySet()) {
				if (this.tileIsMoveable.get(id)) {
					hand.add(this.tileLetters.get(id));
				}
			}
			ArrayList<Pair<int[], Character>> bestMove = this.client.scrabbleGetGame().generateBestMove(hand);

			if (bestMove == null) {
				System.out.println("THERE IS NO MOVE");
			}
			else {
				this.client.scrabbleMakeMove(bestMove);
			}
		}
	}

	@Override
	public void keyReleased(int key) {

	}

}
