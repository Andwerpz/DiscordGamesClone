package state;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

import audio.Sound;
import entity.Entity;
import game.ChessGame;
import game.ChessPosition;
import graphics.Framebuffer;
import graphics.Material;
import graphics.Texture;
import graphics.TextureMaterial;
import input.Button;
import input.Input;
import input.TextField;
import main.Main;
import model.AssetManager;
import model.FilledRectangle;
import model.Model;
import scene.DirLight;
import scene.Light;
import scene.Scene;
import screen.PerspectiveScreen;
import screen.UIScreen;
import server.GameClient;
import ui.Text;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FileUtils;
import util.FontUtils;
import util.GraphicsTools;
import util.Mat4;
import util.Pair;
import util.Triple;
import util.Vec2;
import util.Vec3;

public class ChessState extends State {

	private static final int BACKGROUND_UI_SCENE = 0;
	private static final int STATIC_UI_SCENE = 1; // for unchanging parts of the ui like the logo
	private static final int DYNAMIC_UI_SCENE = 2; // inputs and stuff

	private UIScreen uiScreen; // Menu UI

	private GameClient client;

	private Vec3 lightGray = new Vec3(66, 69, 73).mul(1.0f / 255.0f);
	private Vec3 lightBlue = new Vec3(114, 137, 218).mul(1.0f / 255.0f);
	private Vec3 gray = new Vec3(54, 57, 62).mul(1.0f / 255.0f);
	private Vec3 darkGray = new Vec3(40, 43, 48).mul(1.0f / 255.0f);

	private Vec3 darkGreen = new Vec3(118, 150, 86).mul(1.0f / 255.0f);
	private Vec3 lightGreen = new Vec3(238, 238, 210).mul(1.0f / 255.0f);

	private TextureMaterial backgroundTexture;

	private long entityAtMouse;

	// -- LOBBY --
	private boolean inLobby = true;

	private int lobbyBackgroundMargin = 50;
	private int lobbyBackgroundWidth = Main.windowWidth - lobbyBackgroundMargin * 2;
	private int lobbyBackgroundHeight = Main.windowHeight - lobbyBackgroundMargin * 2;

	// -- CHESS GAME --
	private int boardCellSize = 80;
	private int boardBackgroundMarginSize = 20;
	private int boardSize = boardCellSize * 8;
	private int boardBackgroundSize = boardSize + boardBackgroundMarginSize * 2;

	private ChessGame curChessGame;
	private int curChessGameID = -1;

	private UIFilledRectangle[][] cellRects = new UIFilledRectangle[8][8];

	private Vec2[][] cellBaseOffsets = new Vec2[8][8];
	private Vec2[][] cellOffsets = new Vec2[8][8];
	private Vec2[][] cellVels = new Vec2[8][8];

	//theres a weird issue with longs always being inequal
	private TreeSet<Triple<Long, int[], Vec2>> cellAccels = new TreeSet<>((a, b) -> {
		int retVal = 0;
		if (Long.compare(a.first, b.first) != 0) {
			retVal = Long.compare(a.first.longValue(), b.first.longValue());
		}
		if (retVal == 0 && a.second[0] != b.second[0]) {
			retVal = Integer.compare(a.second[0], b.second[0]);
		}
		if (retVal == 0 && a.second[1] != b.second[1]) {
			retVal = Integer.compare(a.second[1], b.second[1]);
		}
		return retVal;
	});

	private float cellFriction = 0.8f;

	private static HashMap<Byte, TextureMaterial> chessPieceTextures;
	private UIFilledRectangle[][] pieceRects = new UIFilledRectangle[8][8];

	private boolean isWhite = false;

	private boolean chessPieceHeld = false;
	private int chessPieceHeldRow = 0;
	private int chessPieceHeldCol = 0;

	public ChessState(StateManager sm, GameClient client) {
		super(sm);

		this.client = client;
	}

	@Override
	public void load() {
		this.uiScreen = new UIScreen();

		this.backgroundTexture = new TextureMaterial(new Texture("/chess/chess_background.png", Texture.VERTICAL_FLIP_BIT));

		if (ChessState.chessPieceTextures == null) {
			ChessState.chessPieceTextures = new HashMap<>();

			ArrayList<BufferedImage> pieceImages = GraphicsTools.loadAnimation(GraphicsTools.verticalFlip(FileUtils.loadImage("/chess/chess_pieces.png")), 215, 215);

			ChessState.chessPieceTextures.put(ChessPosition.KING, new TextureMaterial(pieceImages.get(6)));
			ChessState.chessPieceTextures.put(ChessPosition.QUEEN, new TextureMaterial(pieceImages.get(7)));
			ChessState.chessPieceTextures.put(ChessPosition.BISHOP, new TextureMaterial(pieceImages.get(8)));
			ChessState.chessPieceTextures.put(ChessPosition.KNIGHT, new TextureMaterial(pieceImages.get(9)));
			ChessState.chessPieceTextures.put(ChessPosition.ROOK, new TextureMaterial(pieceImages.get(10)));
			ChessState.chessPieceTextures.put(ChessPosition.PAWN, new TextureMaterial(pieceImages.get(11)));
			ChessState.chessPieceTextures.put((byte) -ChessPosition.KING, new TextureMaterial(pieceImages.get(0)));
			ChessState.chessPieceTextures.put((byte) -ChessPosition.QUEEN, new TextureMaterial(pieceImages.get(1)));
			ChessState.chessPieceTextures.put((byte) -ChessPosition.BISHOP, new TextureMaterial(pieceImages.get(2)));
			ChessState.chessPieceTextures.put((byte) -ChessPosition.KNIGHT, new TextureMaterial(pieceImages.get(3)));
			ChessState.chessPieceTextures.put((byte) -ChessPosition.ROOK, new TextureMaterial(pieceImages.get(4)));
			ChessState.chessPieceTextures.put((byte) -ChessPosition.PAWN, new TextureMaterial(pieceImages.get(5)));
		}

		Main.unlockCursor();
		Entity.killAll();

		this.drawLobby();
	}

	@Override
	public void kill() {
		this.uiScreen.kill();
	}

	private void drawLobby() {
		// -- UI BACKGROUND --
		this.clearScene(BACKGROUND_UI_SCENE);
		UIFilledRectangle background = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, new FilledRectangle(), BACKGROUND_UI_SCENE);
		background.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		background.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		//background.setMaterial(new Material(this.lightGray));
		background.setTextureMaterial(this.backgroundTexture);

		// -- STATIC UI --
		this.clearScene(STATIC_UI_SCENE);
		UIFilledRectangle lobbyBackground = new UIFilledRectangle(0, 0, -1, this.lobbyBackgroundWidth, this.lobbyBackgroundHeight, STATIC_UI_SCENE);
		lobbyBackground.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		lobbyBackground.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		lobbyBackground.setMaterial(new Material(this.gray));

		// -- DYNAMIC UI --
		this.clearScene(DYNAMIC_UI_SCENE);
		Button createGameBtn = new Button(10 + lobbyBackgroundMargin, 10 + lobbyBackgroundMargin, 200, 30, "btn_create_game", "Create Game", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		createGameBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		createGameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);

		// -- Game Selector Buttons --
		int xOffset = this.lobbyBackgroundMargin + 10;
		int yOffset = this.lobbyBackgroundMargin + 10;

		int xMargin = 10;
		int yMargin = 10;

		int gameButtonWidth = 300;
		int gameButtonHeight = 150;

		for (int i : this.client.getChessGames().keySet()) {
			ChessGame game = this.client.getChessGames().get(i);
			String whiteNickname = game.getWhiteID() == -1 ? "Empty Slot" : this.client.getPlayers().get(game.getWhiteID());
			String blackNickname = game.getBlackID() == -1 ? "Empty Slot" : this.client.getPlayers().get(game.getBlackID());

			Text whitePlayerText = new Text(xOffset + 10, yOffset + 10, whiteNickname, FontUtils.ggsans, 24, Color.WHITE, STATIC_UI_SCENE);
			whitePlayerText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			whitePlayerText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);

			Text blackPlayerText = new Text(xOffset + 10, yOffset + 40, blackNickname, FontUtils.ggsans, 24, Color.WHITE, STATIC_UI_SCENE);
			blackPlayerText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			blackPlayerText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);

			Button gameSelectBtn = new Button(xOffset, yOffset, gameButtonWidth, gameButtonHeight, "btn_join_game " + game.getID(), " ", FontUtils.ggsans, 24, DYNAMIC_UI_SCENE);
			gameSelectBtn.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			gameSelectBtn.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);

			xOffset += gameButtonWidth + xMargin;
			if (xOffset + gameButtonWidth + xMargin > lobbyBackgroundWidth) {
				xOffset = this.lobbyBackgroundMargin + 10;
				yOffset += gameButtonHeight + yMargin;
			}
		}
	}

	private void drawChessBoard() {
		// -- UI BACKGROUND --
		this.clearScene(BACKGROUND_UI_SCENE);
		UIFilledRectangle background = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, new FilledRectangle(), BACKGROUND_UI_SCENE);
		background.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		background.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		background.setTextureMaterial(this.backgroundTexture);

		UIFilledRectangle chessBoardBackground = new UIFilledRectangle(0, 0, 0, this.boardBackgroundSize, this.boardBackgroundSize, BACKGROUND_UI_SCENE);
		chessBoardBackground.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		chessBoardBackground.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		chessBoardBackground.setMaterial(new Material(this.gray));

		// -- STATIC UI --
		this.clearScene(STATIC_UI_SCENE);

		// -- DYNAMIC UI --
		this.clearScene(DYNAMIC_UI_SCENE);
		Button leaveGameBtn = new Button(10, 10, 200, 30, "btn_leave_game", "Leave Game", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		leaveGameBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		leaveGameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);

		// -- CHESS PIECES AND BOARD --
		for (int i = 0; i < 8; i++) { //numbers and letters
			int offset = i * this.boardCellSize + this.boardCellSize / 2 + this.boardBackgroundMarginSize;

			Text letterText = new Text(offset, this.boardBackgroundMarginSize / 2, (char) ('a' + i) + "", FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, DYNAMIC_UI_SCENE);
			letterText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			letterText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			chessBoardBackground.bindElement(letterText);

			Text numberText = new Text(this.boardBackgroundMarginSize / 2, offset, (i + 1) + "", FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, DYNAMIC_UI_SCENE);
			numberText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			numberText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			chessBoardBackground.bindElement(numberText);
		}

		long curMs = System.currentTimeMillis();
		byte[][] board = this.curChessGame.getCurPosition().board;

		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				int xOffset = this.boardBackgroundMarginSize + j * this.boardCellSize;
				int yOffset = this.boardBackgroundMarginSize + i * this.boardCellSize;
				Material color = new Material((i + j) % 2 == 0 ? this.darkGreen : this.lightGreen);

				UIFilledRectangle boardCell = new UIFilledRectangle(xOffset, yOffset, 0, this.boardCellSize, this.boardCellSize, STATIC_UI_SCENE);
				boardCell.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
				boardCell.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
				boardCell.setMaterial(color);

				chessBoardBackground.bindElement(boardCell);

				this.cellRects[i][j] = boardCell;
				this.cellBaseOffsets[i][j] = new Vec2(xOffset, yOffset);
				this.cellOffsets[i][j] = new Vec2(0, 0);
				this.cellVels[i][j] = new Vec2(0, 0);

				this.cellAccels.add(new Triple<Long, int[], Vec2>(curMs + (i + j) * 50, new int[] { i, j }, new Vec2(-3, 10).setLength(10)));

				if (board[i][j] != 0) {
					TextureMaterial pieceTexture = ChessState.chessPieceTextures.get(board[i][j]);

					UIFilledRectangle pieceRect = new UIFilledRectangle(0, 0, 0, this.boardCellSize, this.boardCellSize, new FilledRectangle(), DYNAMIC_UI_SCENE);
					pieceRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
					pieceRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
					pieceRect.setTextureMaterial(pieceTexture);

					boardCell.bindElement(pieceRect);

					this.pieceRects[i][j] = pieceRect;
				}
			}
		}
	}

	@Override
	public void update() {
		this.entityAtMouse = uiScreen.getEntityIDAtMouse();
		Input.inputsHovered(this.entityAtMouse);

		// -- NETWORKING --
		if (this.inLobby) {
			if (this.client.chessHasLobbyUpdates()) {
				this.drawLobby();
			}

			if (this.client.chessGetCurGameID() != this.curChessGameID) { //server says that you're in a game
				this.inLobby = false;

				this.curChessGame = this.client.chessGetCurGame();
				this.curChessGameID = this.client.chessGetCurGameID();
				this.isWhite = this.client.chessGetCurGame().getWhiteID() == this.client.getID();
				this.drawChessBoard();
			}
		}
		else {
			if (this.client.chessCurGameHasMoveUpdate()) {
				this.drawChessBoard();
			}
		}

		// -- ANIMATIONS --
		if (!this.inLobby) {
			//do cool stuff idk
			// -- CELL ANIMATIONS --
			for (int i = 0; i < 8; i++) {
				for (int j = 0; j < 8; j++) {
					this.cellOffsets[i][j].addi(this.cellVels[i][j]);
					this.cellOffsets[i][j].muli(this.cellFriction);
					this.cellVels[i][j].muli(this.cellFriction);

					this.cellRects[i][j].setFrameAlignmentOffset((int) (this.cellOffsets[i][j].x + this.cellBaseOffsets[i][j].x), (int) (this.cellOffsets[i][j].y + this.cellBaseOffsets[i][j].y));
					this.cellRects[i][j].align();
				}
			}

			while (this.cellAccels.size() != 0 && this.cellAccels.first().first <= System.currentTimeMillis()) {
				Triple<Long, int[], Vec2> next = this.cellAccels.pollFirst();
				int r = next.second[0];
				int c = next.second[1];
				Vec2 accel = next.third;
				this.cellVels[r][c].addi(accel);
			}

		}

		Entity.updateEntities();
		Model.updateModels();
	}

	@Override
	public void render(Framebuffer outputBuffer) {
		uiScreen.setUIScene(BACKGROUND_UI_SCENE);
		uiScreen.render(outputBuffer);
		uiScreen.setUIScene(STATIC_UI_SCENE);
		uiScreen.render(outputBuffer);
		uiScreen.setUIScene(DYNAMIC_UI_SCENE);
		uiScreen.render(outputBuffer);
	}

	@Override
	public void mousePressed(int button) {
		Input.inputsPressed(uiScreen.getEntityIDAtMouse());

		//picking up a chess piece
		if (!this.inLobby) {

			if (!this.chessPieceHeld) {
				for (int i = 0; i < 8; i++) {
					for (int j = 0; j < 8; j++) {
						byte pieceID = this.curChessGame.getCurPosition().board[i][j];
						if (pieceID == 0 || (pieceID < 0 && this.isWhite) || (pieceID > 0 && !this.isWhite)) {
							continue;
						}
						if (this.cellRects[i][j].getID() == this.entityAtMouse || this.pieceRects[i][j].getID() == this.entityAtMouse) {
							this.chessPieceHeld = true;
							this.chessPieceHeldRow = i;
							this.chessPieceHeldCol = j;
						}
					}
				}
			}

		}

	}

	@Override
	public void mouseReleased(int button) {
		Input.inputsReleased(uiScreen.getEntityIDAtMouse());
		String clickedButton = Input.getClicked();
		switch (clickedButton.split(" ")[0]) {
		case "btn_create_game": {
			this.client.chessCreateGame();
			break;
		}

		case "btn_leave_game": {
			this.client.chessLeaveGame();
			this.curChessGameID = -1;
			this.curChessGame = null;
			this.inLobby = true;
			this.drawLobby();
			break;
		}

		case "btn_join_game": {
			int gameID = Integer.parseInt(clickedButton.split(" ")[1]);
			this.client.chessJoinGame(gameID);
			break;
		}
		}

		if (!this.inLobby) {
			//letting go of a chess piece
			if (this.chessPieceHeld) {
				int[] from = new int[] { this.chessPieceHeldRow, this.chessPieceHeldCol };
				int[] to = null;
				//find 'to' cell
				for (int i = 0; i < 8; i++) {
					for (int j = 0; j < 8; j++) {
						if (this.cellRects[i][j].getID() == this.entityAtMouse || (this.pieceRects[i][j] != null && this.pieceRects[i][j].getID() == this.entityAtMouse)) {
							to = new int[] { i, j };
						}
					}
				}
				if (to != null && this.curChessGame.getCurPosition().isLegalMove(from, to)) {
					this.client.chessMakeMove(from, to);
					this.drawChessBoard();
				}
				else {
					//TODO reset held chess piece offset
				}
				this.chessPieceHeld = false;
			}
		}

	}

	@Override
	public void keyPressed(int key) {

	}

	@Override
	public void keyReleased(int key) {

	}

}
