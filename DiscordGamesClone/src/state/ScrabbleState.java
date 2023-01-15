package state;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;

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
import util.Vec2;
import util.Vec3;

public class ScrabbleState extends State {

	//the server will create an scrabble game based off of the host's specifications, and will communicate
	//all the relevant information to the clients. 

	//when making moves, the clients will send their moves to the server to be validated. If the server validates the move, then
	//it will tell all the clients the move, and who made the move, and then tell who's turn it is next. 
	
	//one problem: 
	//re-aligning all of the elements is bad, since it's really slow. 
	//we should instead adjust the camera offset, but how??

	private static final int BACKGROUND_SCENE = 0;
	private static final int INPUT_SCENE = 1;
	private static final int BOARD_SCENE = 2;
	private static final int BOARD_TEXT_SCENE = 3;
	private static final int TILE_SCENE = 4;
	private static final int TILE_TEXT_SCENE = 5;
	private static final int HELD_TILE_SCENE = 6;
	private static final int HELD_TILE_TEXT_SCENE = 7;

	private Material backgroundColor = new Material(new Vec3(247, 213, 173).mul(1.0f / 255.0f));
	private Material boardBackgroundColor = new Material(new Vec3(255, 247, 227).mul(1.0f / 255.0f));
	
	private Material normalCellColor = new Material(new Vec3(255, 228, 198).mul(1.0f / 255.0f));
	
	private Material doubleLetterColor = new Material(new Vec3(122, 168, 128).mul(1.0f / 255.0f));
	private Material tripleLetterColor = new Material(new Vec3(20, 170, 190).mul(1.0f / 255.0f));
	private Material doubleWordColor = new Material(new Vec3(200, 120, 50).mul(1.0f / 255.0f));
	private Material tripleWordColor = new Material(new Vec3(144, 63, 23).mul(1.0f / 255.0f));
	private Material startCellColor = new Material(new Vec3(90, 70, 90).mul(1.0f / 255.0f));
	
	private Material tileColor = new Material(new Vec3(255, 135, 92).mul(1.0f / 255.0f));
	
	private TextureMaterial tileTexture;
	
	private GameClient client;
	private State mainLobbyState;

	private UIScreen uiScreen;
	private UIScreen boardScreen;
	
	private ScrabbleGame menuGame;
	private ScrabbleGame scrabbleGame;
	
	private ScrabbleGame activeGame;
	
	private UIFilledRectangle scrabbleBoardBackground;
	
	private int boardInnerMarginPx = 20;
	private int cellGapPx = 2;
	private int cellSizePx = 70;
	private int boardBackgroundSizePx = cellSizePx * ScrabbleGame.boardSize + cellGapPx * (ScrabbleGame.boardSize - 1) + boardInnerMarginPx * 2;
	
	private int tileHoveredSize = 80;
	
	private UIFilledRectangle[][] cellRects;
	private UIFilledRectangle[][] tileRects;
	
	//these ones can be moved by the player
	private UIFilledRectangle[][] placedTileRects;	
	private char[][] placedLetters;
	
	private boolean draggingBoard = false;
	private int camXOffset, camYOffset;
	private Vec2 mousePos;
	
	private long hoveredTileID, hoveredCellID;
	
	private boolean tileHeld = false;
	private UIFilledRectangle tileHeldRect;
	private int tileHeldRow, tileHeldCol;

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
		
		this.menuGame = new ScrabbleGame();
		
		this.activeGame = menuGame;
		
		this.camXOffset += (Main.windowWidth - boardBackgroundSizePx) / 2;
		this.camYOffset += (Main.windowHeight - boardBackgroundSizePx) / 2;
		
		this.mousePos = MouseInput.getMousePos();
		
		this.tileTexture = new TextureMaterial(new Texture("/scrabble/scrabble_tile.png", Texture.VERTICAL_FLIP_BIT));
		
		this.placedTileRects = new UIFilledRectangle[ScrabbleGame.boardSize][ScrabbleGame.boardSize];
		this.placedLetters = new char[ScrabbleGame.boardSize][ScrabbleGame.boardSize];
		
		for(int i = 0; i < ScrabbleGame.boardSize; i++) {
			for(int j = 0; j < ScrabbleGame.boardSize; j++) {
				this.placedLetters[i][j] = ScrabbleGame.LETTER_EMPTY;
			}
		}
		
		this.placedLetters[0][0] = 'L';
		this.placedLetters[0][1] = 'E';
		this.placedLetters[0][2] = 'T';
		this.placedLetters[0][3] = 'T';
		this.placedLetters[0][4] = 'E';
		this.placedLetters[0][5] = 'R';

		this.drawMainMenu();
	}

	@Override
	public void kill() {
		this.uiScreen.kill();
		this.boardScreen.kill();
	}

	private void drawMainMenu() {
		// -- BACKGROUND --
		this.clearScene(BACKGROUND_SCENE);
		UIFilledRectangle backgroundRect = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, BACKGROUND_SCENE);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		backgroundRect.setMaterial(this.backgroundColor);
		
		this.drawScrabbleGame();
	}
	
	private void drawScrabbleGame() {
		this.clearScene(BOARD_SCENE);
		this.clearScene(BOARD_TEXT_SCENE);
		this.clearScene(TILE_SCENE);
		this.clearScene(TILE_TEXT_SCENE);
		
		this.scrabbleBoardBackground = new UIFilledRectangle(this.camXOffset, this.camYOffset, -1, this.boardBackgroundSizePx, this.boardBackgroundSizePx, BACKGROUND_SCENE);
		this.scrabbleBoardBackground.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.scrabbleBoardBackground.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.scrabbleBoardBackground.setMaterial(this.boardBackgroundColor);
		
		this.cellRects = new UIFilledRectangle[ScrabbleGame.boardSize][ScrabbleGame.boardSize];
		this.tileRects = new UIFilledRectangle[ScrabbleGame.boardSize][ScrabbleGame.boardSize];
		for(int i = 0; i < ScrabbleGame.boardSize; i++) {
			for(int j = 0; j < ScrabbleGame.boardSize; j++) {
				int xOffset = (this.cellSizePx + this.cellGapPx) * j + this.boardInnerMarginPx;
				int yOffset = (this.cellSizePx + this.cellGapPx) * i + this.boardInnerMarginPx;
				
				int cellType = this.activeGame.getBonusBoard()[i][j];
				
				UIFilledRectangle cellRect = new UIFilledRectangle(xOffset, yOffset, 0, this.cellSizePx, this.cellSizePx, BOARD_SCENE);
				cellRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
				cellRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
				cellRect.bind(this.scrabbleBoardBackground);
				
				String cellString = "";
				
				switch(cellType) {
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
					cellString = "WD";
					break;
				}

				case ScrabbleGame.BONUS_WORD_TRIPLE: {
					cellRect.setMaterial(this.tripleWordColor);
					cellString = "WT";
					break;
				}

				case ScrabbleGame.BONUS_START: {
					cellRect.setMaterial(this.startCellColor);
					cellString = "S";
					break;
				}
				}
				
				if(cellString.length() != 0) {
					Text cellText = new Text(0, 0, cellString, FontUtils.ggsans.deriveFont(Font.BOLD), 36, Color.WHITE, BOARD_TEXT_SCENE);
					cellText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
					cellText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
					cellText.bind(cellRect);
				}
				
				this.cellRects[i][j] = cellRect;
				
				char tileChar = this.activeGame.getLetterBoard()[i][j];
				if(tileChar != ScrabbleGame.LETTER_EMPTY) {
					UIFilledRectangle tileRect = this.generateTile(tileChar, TILE_SCENE, TILE_TEXT_SCENE);
					tileRect.bind(cellRect);
					
					this.tileRects[i][j] = tileRect;
				}
				
				if(this.placedLetters[i][j] != ScrabbleGame.LETTER_EMPTY) {
					UIFilledRectangle tileRect = this.generateTile(this.placedLetters[i][j], TILE_SCENE, TILE_TEXT_SCENE);
					tileRect.bind(cellRect);
					
					this.placedTileRects[i][j] = tileRect;
				}
				
			}
		}
	}
	
	private UIFilledRectangle generateTile(char tileChar, int tileScene, int textScene) {
		if(tileChar >= 'a' && tileChar <= 'z') {
			tileChar = (char) ((tileChar - 'a') + 'A');
		}
		if(tileChar < 'A' || tileChar > 'Z') {
			System.err.println("Can't generate tile with character: " + tileChar);
			return null;
		}
		
		UIFilledRectangle tileRect = new UIFilledRectangle(0, 0, 0, this.cellSizePx, this.cellSizePx, new FilledRectangle(), tileScene);
		tileRect.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		tileRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		tileRect.setMaterial(this.tileColor);
		tileRect.setTextureMaterial(this.tileTexture);
		
		int tilePoints = ScrabbleGame.letterScore.get(tileChar);
		
		Text tileText = new Text(0, 0, tileChar + "", FontUtils.ggsans.deriveFont(Font.BOLD), 32, Color.WHITE, textScene);
		tileText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		tileText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		tileText.bind(tileRect);
		
		Text tilePointText = new Text(18, 18, tilePoints + "", FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, textScene);
		tilePointText.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		tilePointText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		tilePointText.bind(tileRect);
		
		return tileRect;
	}

	@Override
	public void update() {
		Input.inputsHovered(uiScreen.getEntityIDAtMouse());
		
		// -- NETWORKING --
		if (this.client.getCurGame() == GameServer.LOBBY) {
			this.sm.switchState(this.mainLobbyState);
		}
			
		// -- OTHER STUFF --
		Vec2 curMousePos = MouseInput.getMousePos();
		if(this.draggingBoard) {
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
		
		if(this.tileHeld) {
			this.tileHeldRect.setFrameAlignmentOffset(this.mousePos.x, this.mousePos.y);
			this.tileHeldRect.align();
		}
		
		// -- Tile Animations --
		for(int i = 0; i < ScrabbleGame.boardSize; i++) {
			for(int j = 0; j < ScrabbleGame.boardSize; j++) {
				UIFilledRectangle t = this.placedTileRects[i][j];
				
				if(t == null) {
					continue;
				}
				
				float targetWidth = this.cellSizePx;
				if(t.getID() == this.hoveredTileID) {
					targetWidth = this.tileHoveredSize;
				}
				
				float curWidth = t.getWidth();
				float diff = targetWidth - curWidth;
				
				diff *= 0.7f;
				
				t.setWidth(curWidth + diff);
				t.setHeight(curWidth + diff);
				
				if(Math.abs(diff) > 0.001f) {
					t.align();
				}
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
		
		this.hoveredCellID = boardScreen.getEntityIDAtMouse();
		
		boardScreen.setUIScene(BOARD_TEXT_SCENE);
		boardScreen.render(outputBuffer);
		boardScreen.setUIScene(TILE_SCENE);
		boardScreen.render(outputBuffer);
		
		this.hoveredTileID = boardScreen.getEntityIDAtMouse();
		
		boardScreen.setUIScene(TILE_TEXT_SCENE);
		boardScreen.render(outputBuffer);
		
		uiScreen.setUIScene(HELD_TILE_SCENE);
		uiScreen.render(outputBuffer);
		uiScreen.setUIScene(HELD_TILE_TEXT_SCENE);
		uiScreen.render(outputBuffer);
	}

	@Override
	public void mousePressed(int button) {
		Input.inputsPressed(uiScreen.getEntityIDAtMouse());
		
		if(!this.tileHeld) {
			outer:
			for(int i = 0; i < ScrabbleGame.boardSize; i++) {
				for(int j = 0; j < ScrabbleGame.boardSize; j++) {
					UIFilledRectangle t = this.placedTileRects[i][j];
					
					if(t == null) {
						continue;
					}
					
					if(t.getID() == this.hoveredTileID) {
						char tileChar = this.placedLetters[i][j];
						
						//offset tile at cell so that it's not visible
						this.placedTileRects[i][j].setFrameAlignmentOffset(10000, 10000);
						this.placedTileRects[i][j].align();
						
						this.tileHeld = true;
						this.tileHeldRect = this.generateTile(tileChar, HELD_TILE_SCENE, HELD_TILE_TEXT_SCENE);
						
						this.tileHeldRow = i;
						this.tileHeldCol = j;
						
						this.tileHeldRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
						this.tileHeldRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
						this.tileHeldRect.setFrameAlignmentOffset(this.mousePos.x, this.mousePos.y);
						this.tileHeldRect.align();
						
						break outer;
					}
				}
			}
		}
		
		if(!this.tileHeld) {
			this.draggingBoard = true;
		}
	}

	@Override
	public void mouseReleased(int button) {
		Input.inputsReleased(uiScreen.getEntityIDAtMouse());
		String clickedButton = Input.getClicked();
		switch (clickedButton) {

		}
		
		if(this.tileHeld) {
			this.tileHeld = false;
			this.tileHeldRect.kill();
			this.tileHeldRect = null;
			
			this.placedTileRects[this.tileHeldRow][this.tileHeldCol].setFrameAlignmentOffset(0, 0);
			this.placedTileRects[this.tileHeldRow][this.tileHeldCol].align();
			
			outer:
			for(int i = 0; i < ScrabbleGame.boardSize; i++) {
				for(int j = 0; j < ScrabbleGame.boardSize; j++) {
					if(this.cellRects[i][j].getID() != this.hoveredCellID) {
						continue;
					}
					
					if(this.tileRects[i][j] == null && this.placedTileRects[i][j] == null) {
						//place the held tile down 
						this.placedTileRects[i][j] = this.placedTileRects[this.tileHeldRow][this.tileHeldCol];
						this.placedTileRects[this.tileHeldRow][this.tileHeldCol] = null;
						
						this.placedTileRects[i][j].bind(this.cellRects[i][j]);
						this.placedTileRects[i][j].align();
						
						this.placedLetters[i][j] = this.placedLetters[this.tileHeldRow][this.tileHeldCol];
						this.placedLetters[this.tileHeldRow][this.tileHeldCol] = ScrabbleGame.LETTER_EMPTY;
					}
					
					break outer;
				}
			}
		}
		
		this.draggingBoard = false;
	}

	@Override
	public void keyPressed(int key) {

	}

	@Override
	public void keyReleased(int key) {

	}

}
