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
import screen.UIScreen;
import server.GameClient;
import server.GameServer;
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
import util.Vec4;

public class ChessState extends State {
	//ok, we have a few things left to do:
	// -- IMPORTANT --
	// - sound effects

	// -- OTHER --
	// - Pawn Underpromotion - eh, who needs to underpromote anyways :shrug:
	// - Move History Board
	// - nice animations?
	// - make lobby nicer?
	// - right click to draw arrows

	// -- DONE --
	// - move preview dots
	// - previous move on board
	// - on check make king square red
	// - Win Checking, more like telling the players when someone has won

	private static final int BACKGROUND_UI_SCENE = 0;
	private static final int STATIC_UI_SCENE = 1; // for unchanging parts of the ui like the logo
	private static final int DYNAMIC_UI_SCENE = 2; // inputs and stuff
	private static final int HELD_PIECE_SCENE = 3; //just for the held piece D:
	private static final int BOARD_INFO_SCENE = 4; //move preview dots
	private static final int WIN_INFO_SCENE = 5;
	private static final int WIN_INFO_TEXT_SCENE = 6;

	private State mainLobbyState;

	private UIScreen uiScreen; // Menu UI

	private GameClient client;

	private Vec3 lightGray = new Vec3(66, 69, 73).mul(1.0f / 255.0f);
	private Vec3 lightBlue = new Vec3(114, 137, 218).mul(1.0f / 255.0f);
	private Vec3 gray = new Vec3(54, 57, 62).mul(1.0f / 255.0f);
	private Vec3 darkGray = new Vec3(40, 43, 48).mul(1.0f / 255.0f);

	private Vec3 darkGreen = new Vec3(118, 150, 86).mul(1.0f / 255.0f);
	private Vec3 lightGreen = new Vec3(238, 238, 210).mul(1.0f / 255.0f);

	private Material yellowHighlight = new Material(new Vec4(186, 202, 68, 170).mul(1.0f / 255.0f));
	private Material grayHighlight = new Material(new Vec4(66, 69, 73, 120).mul(1.0f / 255.0f));
	private Material redHighlight = new Material(new Vec4(255, 0, 0, 170).mul(1.0f / 255.0f));

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

	private int toEdgeMargin = (Main.windowHeight - boardBackgroundSize) / 2;

	private UIFilledRectangle chessBoardBackground;

	private ChessGame curChessGame;
	private int curChessGameID = -1;
	private boolean chessIsSpectating = false;

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

	private UIFilledRectangle heldPieceRect;

	private Input leaveGameBtn;

	private UIFilledRectangle spectatorBoard;

	private UIFilledRectangle playerLabel, opponentLabel;

	private UIFilledRectangle moveHistoryBoard;

	private static TextureMaterial circleTexture;

	public ChessState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm);

		this.mainLobbyState = mainLobbyState;
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

		if (ChessState.circleTexture == null) {
			ChessState.circleTexture = new TextureMaterial(new Texture("/chess/dot.png"));
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
		Button createGameBtn = new Button(10, 10, 200, 30, "btn_create_game", "Create Game", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		createGameBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		createGameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
		createGameBtn.bind(lobbyBackground);

		if (this.client.isHost()) {
			Button returnToMainLobbyBtn = new Button(10 + createGameBtn.getWidth() + 10, 10, 200, 30, "btn_return_to_main_lobby", "Return To Lobby", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
			returnToMainLobbyBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
			returnToMainLobbyBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
			returnToMainLobbyBtn.bind(lobbyBackground);
		}

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

		this.chessBoardBackground = new UIFilledRectangle(0, 0, 0, this.boardBackgroundSize, this.boardBackgroundSize, BACKGROUND_UI_SCENE);
		this.chessBoardBackground.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		this.chessBoardBackground.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		this.chessBoardBackground.setMaterial(new Material(this.gray));

		// -- STATIC UI --
		this.clearScene(STATIC_UI_SCENE);

		// -- DYNAMIC UI --
		this.clearScene(DYNAMIC_UI_SCENE);
		this.leaveGameBtn = new Button(this.toEdgeMargin, this.toEdgeMargin, Main.windowWidth - this.chessBoardBackground.getRightBorder() - toEdgeMargin * 2, 30, "btn_leave_game", "Leave Game", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		this.leaveGameBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		this.leaveGameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);

		// -- CHESS PIECES AND BOARD --
		for (int i = 0; i < 8; i++) { //numbers and letters
			int offset = i * this.boardCellSize + this.boardCellSize / 2 + this.boardBackgroundMarginSize;

			Text letterText = new Text(offset, this.boardBackgroundMarginSize / 2, (char) ('a' + i) + "", FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, DYNAMIC_UI_SCENE);
			letterText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			letterText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			letterText.bind(chessBoardBackground);

			Text numberText = new Text(this.boardBackgroundMarginSize / 2, offset, (i + 1) + "", FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, DYNAMIC_UI_SCENE);
			numberText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			numberText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			numberText.bind(chessBoardBackground);
		}

		byte[][] board = this.curChessGame.getCurPosition().board;

		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				int xOffset = this.boardBackgroundMarginSize + j * this.boardCellSize;
				int yOffset = this.boardBackgroundMarginSize + i * this.boardCellSize;
				Material color = new Material((i + j) % 2 == 1 ? this.darkGreen : this.lightGreen);

				UIFilledRectangle boardCell = new UIFilledRectangle(xOffset, yOffset, 0, this.boardCellSize, this.boardCellSize, STATIC_UI_SCENE);
				boardCell.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
				boardCell.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
				boardCell.setMaterial(color);

				if (!this.isWhite) {
					boardCell.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
					boardCell.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
				}

				boardCell.bind(chessBoardBackground);

				this.cellRects[i][j] = boardCell;
				this.cellBaseOffsets[i][j] = new Vec2(xOffset, yOffset);
				this.cellOffsets[i][j] = new Vec2(0, 0);
				this.cellVels[i][j] = new Vec2(0, 0);

				if (board[i][j] != 0) {
					TextureMaterial pieceTexture = ChessState.chessPieceTextures.get(board[i][j]);

					UIFilledRectangle pieceRect = new UIFilledRectangle(0, 0, 0, this.boardCellSize, this.boardCellSize, new FilledRectangle(), DYNAMIC_UI_SCENE);
					pieceRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
					pieceRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
					pieceRect.setTextureMaterial(pieceTexture);
					pieceRect.bind(boardCell);

					this.pieceRects[i][j] = pieceRect;
				}
			}
		}

		// -- SPECTATOR BOARD --
		this.drawSpectatorBoard();

		// -- PLAYER LABELS --
		this.drawPlayerLabels();

		// -- MOVE HISTORY BOARD --
		this.drawMoveHistoryBoard();

		// -- BOARD INFO --
		this.drawBoardInfo();

		// -- WIN INFO --
		this.drawWinInfo();
	}

	private void drawSpectatorBoard() {
		if (this.spectatorBoard != null) {
			this.spectatorBoard.kill();
		}

		int spectatorBoardWidth = Main.windowWidth - this.chessBoardBackground.getRightBorder() - this.toEdgeMargin * 2;
		int spectatorBoardHeight = leaveGameBtn.getBottomBorder() - this.toEdgeMargin * 2;
		this.spectatorBoard = new UIFilledRectangle(this.toEdgeMargin, this.toEdgeMargin, 0, spectatorBoardWidth, spectatorBoardHeight, BACKGROUND_UI_SCENE);
		this.spectatorBoard.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		this.spectatorBoard.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
		this.spectatorBoard.setMaterial(new Material(this.lightGray));

		Text spectatorBoardTitle = new Text(10, 10, "Spectators:", FontUtils.ggsans.deriveFont(Font.BOLD), 24, Color.WHITE, STATIC_UI_SCENE);
		spectatorBoardTitle.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		spectatorBoardTitle.setContentAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		spectatorBoardTitle.bind(this.spectatorBoard);

		Text prevText = spectatorBoardTitle;
		int spectatorTextLineSpacing = 10;

		for (int id : this.client.chessGetCurGame().getSpectators()) {
			String spectatorNick = this.client.getPlayers().get(id);

			Text spectatorText = new Text(10, prevText.getYOffset() + prevText.getHeight() + spectatorTextLineSpacing, spectatorNick, FontUtils.ggsans, 16, Color.WHITE, STATIC_UI_SCENE);
			spectatorText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			spectatorText.setContentAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			spectatorText.bind(this.spectatorBoard);

			prevText = spectatorText;
		}
	}

	private void drawPlayerLabels() {
		if (this.playerLabel != null) {
			this.playerLabel.kill();
		}
		if (this.opponentLabel != null) {
			this.opponentLabel.kill();
		}

		int playerID = this.isWhite ? this.curChessGame.getWhiteID() : this.curChessGame.getBlackID();
		int opponentID = this.isWhite ? this.curChessGame.getBlackID() : this.curChessGame.getWhiteID();

		String playerStr = playerID == -1 ? "Empty Slot" : this.client.getPlayers().get(playerID);
		String opponentStr = opponentID == -1 ? "Empty Slot" : this.client.getPlayers().get(opponentID);

		int labelWidth = this.chessBoardBackground.getLeftBorder() - this.toEdgeMargin * 2;

		this.playerLabel = new UIFilledRectangle(this.toEdgeMargin, this.toEdgeMargin, 0, labelWidth, 30, BACKGROUND_UI_SCENE);
		this.playerLabel.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		this.playerLabel.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		this.playerLabel.setMaterial(new Material(this.lightGray));

		Text playerText = new Text(0, 0, playerStr, FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, STATIC_UI_SCENE);
		playerText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		playerText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		playerText.bind(this.playerLabel);

		this.opponentLabel = new UIFilledRectangle(this.toEdgeMargin, this.toEdgeMargin, 0, labelWidth, 30, BACKGROUND_UI_SCENE);
		this.opponentLabel.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.opponentLabel.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.FROM_TOP);
		this.opponentLabel.setMaterial(new Material(this.lightGray));

		Text opponentText = new Text(0, 0, opponentStr, FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, STATIC_UI_SCENE);
		opponentText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		opponentText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		opponentText.bind(this.opponentLabel);
	}

	private void drawMoveHistoryBoard() {
		//TODO
	}

	private void drawBoardInfo() {
		this.clearScene(BOARD_INFO_SCENE);

		// -- PREV MOVE HIGHLIGHTING --
		if (this.curChessGame.getPrevMove() != null) {
			int[][] prev = this.curChessGame.getPrevMove();
			int[] from = prev[0];
			int[] to = prev[1];

			UIFilledRectangle fromCell = this.cellRects[from[0]][from[1]];
			UIFilledRectangle toCell = this.cellRects[to[0]][to[1]];

			UIFilledRectangle fromHighlight = new UIFilledRectangle(0, 0, 0, this.boardCellSize, this.boardCellSize, BOARD_INFO_SCENE);
			fromHighlight.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			fromHighlight.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
			fromHighlight.setMaterial(this.yellowHighlight);
			fromHighlight.bind(fromCell);

			UIFilledRectangle toHighlight = new UIFilledRectangle(0, 0, 0, this.boardCellSize, this.boardCellSize, BOARD_INFO_SCENE);
			toHighlight.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			toHighlight.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
			toHighlight.setMaterial(this.yellowHighlight);
			toHighlight.bind(toCell);
		}

		// -- KING IN CHECK HIGHLIGHTING --
		int[] wk = new int[2];
		int[] bk = new int[2];
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				if (this.curChessGame.getCurPosition().board[i][j] == ChessPosition.KING) {
					wk[0] = i;
					wk[1] = j;
				}
				if (this.curChessGame.getCurPosition().board[i][j] == -ChessPosition.KING) {
					bk[0] = i;
					bk[1] = j;
				}
			}
		}
		if (this.curChessGame.getCurPosition().isWhiteInCheck()) {
			UIFilledRectangle checkHighlight = new UIFilledRectangle(0, 0, 0, this.boardCellSize, this.boardCellSize, BOARD_INFO_SCENE);
			checkHighlight.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			checkHighlight.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
			checkHighlight.setMaterial(this.redHighlight);
			checkHighlight.bind(this.cellRects[wk[0]][wk[1]]);
		}
		if (this.curChessGame.getCurPosition().isBlackInCheck()) {
			UIFilledRectangle checkHighlight = new UIFilledRectangle(0, 0, 0, this.boardCellSize, this.boardCellSize, BOARD_INFO_SCENE);
			checkHighlight.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			checkHighlight.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
			checkHighlight.setMaterial(this.redHighlight);
			checkHighlight.bind(this.cellRects[bk[0]][bk[1]]);
		}

		// -- AVAILABLE MOVE HIGHLIGHTING --
		if (!this.inLobby && !this.chessIsSpectating && this.chessPieceHeld) {
			for (int i = 0; i < 8; i++) {
				for (int j = 0; j < 8; j++) {
					if (!this.curChessGame.getCurPosition().isLegalMove(new int[] { this.chessPieceHeldRow, this.chessPieceHeldCol }, new int[] { i, j })) {
						continue;
					}

					UIFilledRectangle movePreviewDot = new UIFilledRectangle(0, 0, 0, this.boardCellSize / 2, this.boardCellSize / 2, new FilledRectangle(), BOARD_INFO_SCENE);
					movePreviewDot.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
					movePreviewDot.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
					movePreviewDot.setTextureMaterial(ChessState.circleTexture);
					movePreviewDot.setMaterial(this.grayHighlight);
					movePreviewDot.bind(this.cellRects[i][j]);
				}
			}
		}
	}

	private void drawWinInfo() {
		this.clearScene(WIN_INFO_SCENE);

		ChessPosition curPosition = this.curChessGame.getCurPosition();

		String winString = "";
		if (curPosition.whiteWin) {
			winString = "White Wins";
		}
		else if (curPosition.blackWin) {
			winString = "Black Wins";
		}
		else if (curPosition.stalemate) {
			winString = "Stalemate";
		}

		if (winString.length() == 0) {
			return;
		}

		UIFilledRectangle darkeningRect = new UIFilledRectangle(0, 0, 0, this.boardSize, this.boardSize, WIN_INFO_SCENE);
		darkeningRect.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		darkeningRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		darkeningRect.setMaterial(new Material(new Vec4(0, 0, 0, 0.3f)));
		darkeningRect.bind(this.chessBoardBackground);

		Text winText = new Text(0, 0, winString, FontUtils.ggsans.deriveFont(Font.BOLD), 48, Color.WHITE, WIN_INFO_TEXT_SCENE);
		winText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		winText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		winText.bind(this.chessBoardBackground);

		UIFilledRectangle textBackground = new UIFilledRectangle(0, 0, 0, winText.getWidth() + 100, winText.getHeight() + 100, WIN_INFO_SCENE);
		textBackground.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		textBackground.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		textBackground.setMaterial(new Material(this.lightGray));
		textBackground.bind(darkeningRect);

		textBackground.setZ(10);
	}

	@Override
	public void update() {
		Input.inputsHovered(this.uiScreen.getEntityIDAtMouse());

		// -- NETWORKING --
		if (this.inLobby) {
			if (this.client.chessGetCurGameID() != this.curChessGameID) { //server says that you're in a game
				this.inLobby = false;
				this.curChessGame = this.client.chessGetCurGame();
				this.curChessGameID = this.client.chessGetCurGameID();

				if (this.client.chessIsSpectating()) {
					this.chessIsSpectating = true;
					this.isWhite = true;
				}
				else {
					this.chessIsSpectating = false;
					this.isWhite = this.client.chessGetCurGame().getWhiteID() == this.client.getID();
				}

				this.drawChessBoard();
			}
		}
		else {
			if (this.client.chessGetCurGameID() == -1) { //server says that you're in the lobby
				this.inLobby = true;
				this.curChessGame = null;
				this.curChessGameID = -1;

				this.chessIsSpectating = false;
				this.isWhite = false;

				this.drawLobby();
			}
			if (this.client.chessCurGameHasMoveUpdate()) {
				this.drawChessBoard();
				this.drawBoardInfo();
			}
		}

		if (this.client.getCurGame() == GameServer.LOBBY) {
			this.sm.switchState(this.mainLobbyState);
		}

		if (this.client.chessHasLobbyUpdates()) {
			if (this.inLobby) {
				this.drawLobby();
			}
			else {
				this.drawSpectatorBoard();
				this.drawPlayerLabels();
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

			if (this.chessPieceHeld) {
				Vec2 mousePos = MouseInput.getMousePos();
				this.heldPieceRect.setFrameAlignmentOffset((int) mousePos.x, (int) mousePos.y);
				this.heldPieceRect.align();
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

		this.entityAtMouse = uiScreen.getEntityIDAtMouse();

		uiScreen.setUIScene(BOARD_INFO_SCENE);
		uiScreen.render(outputBuffer);
		uiScreen.setUIScene(DYNAMIC_UI_SCENE);
		uiScreen.render(outputBuffer);

		if (this.chessPieceHeld) {
			uiScreen.setUIScene(HELD_PIECE_SCENE);
			uiScreen.render(outputBuffer);
		}

		uiScreen.setUIScene(WIN_INFO_SCENE);
		uiScreen.render(outputBuffer);
		uiScreen.setUIScene(WIN_INFO_TEXT_SCENE);
		uiScreen.render(outputBuffer);
	}

	@Override
	public void mousePressed(int button) {
		Input.inputsPressed(uiScreen.getEntityIDAtMouse());

		//picking up a chess piece
		if (!this.inLobby) {

			if (!this.chessPieceHeld && !this.chessIsSpectating) {
				for (int i = 0; i < 8; i++) {
					for (int j = 0; j < 8; j++) {
						byte pieceID = this.curChessGame.getCurPosition().board[i][j];
						if (pieceID == 0 || (pieceID < 0 && this.isWhite) || (pieceID > 0 && !this.isWhite)) {
							continue;
						}
						if (this.cellRects[i][j].getID() == this.entityAtMouse) {
							this.chessPieceHeld = true;
							this.chessPieceHeldRow = i;
							this.chessPieceHeldCol = j;

							UIFilledRectangle pieceRect = this.pieceRects[this.chessPieceHeldRow][this.chessPieceHeldCol];
							pieceRect.setFrameAlignmentOffset(Main.windowWidth * 10, Main.windowHeight * 10); //moving it offscreen
							pieceRect.align();

							TextureMaterial pieceTexture = ChessState.chessPieceTextures.get(pieceID);
							this.heldPieceRect = new UIFilledRectangle(0, 0, 0, this.boardCellSize, this.boardCellSize, new FilledRectangle(), HELD_PIECE_SCENE);
							this.heldPieceRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
							this.heldPieceRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
							this.heldPieceRect.setTextureMaterial(pieceTexture);

							this.drawBoardInfo();
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

		case "btn_return_to_main_lobby": {
			this.client.returnToMainLobby();
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
						if (this.cellRects[i][j].getID() == this.entityAtMouse) {
							to = new int[] { i, j };
						}
					}
				}
				if (to != null && this.curChessGame.getCurPosition().isLegalMove(from, to)) {
					this.client.chessMakeMove(from, to);
					this.drawChessBoard();
				}
				else {
					//return the original piece back to its cell
					UIFilledRectangle pieceRect = this.pieceRects[from[0]][from[1]];
					pieceRect.setFrameAlignmentOffset(0, 0);
					pieceRect.align();
				}

				this.heldPieceRect.kill();
				this.heldPieceRect = null;

				this.chessPieceHeld = false;

				this.drawBoardInfo();
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
