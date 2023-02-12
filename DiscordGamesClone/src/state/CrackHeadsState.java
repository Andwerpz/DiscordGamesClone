package state;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL30.*;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;

import java.awt.Color;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import graphics.Framebuffer;
import graphics.Material;
import graphics.Texture;
import graphics.TextureMaterial;
import input.Button;
import input.Input;
import input.MouseInput;
import input.TextField;
import main.Main;
import model.FilledRectangle;
import model.Model;
import screen.UIScreen;
import server.GameClient;
import ui.Text;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FileUtils;
import util.FontUtils;
import util.NoiseGenerator;
import util.Pair;
import util.Quad;
import util.Vec2;
import util.Vec3;
import util.Vec4;

public class CrackHeadsState extends GameState {
	//we need a framebuffer to store the screen that the player is drawing on. 

	//maybe we'll always have it displayed in the background, it's just hud elements that do transitions. 

	//and clearing the canvas could look pretty cool if we do a wipe effect. 

	//points will be processed on server side, and updates will be sent every time someone's point values are updated. 
	//all clients will receive all other client's guesses. 

	private static final int CANVAS_SCENE = 0; //just a rectangle holding the canvas texture
	private static final int CANVAS_DRAW_SCENE = 1; //the scene where we render to the canvas. 

	private static final int HUD_SCENE = 2;
	private static final int HUD_BACKGROUND_SCENE = 3;
	private static final int HUD_TEXT_SCENE = 4;

	private static final int INPUT_SCENE = 5;

	private UIScreen uiScreen;

	private Framebuffer canvasBuffer;
	private Texture canvasTexture;
	private UIFilledRectangle canvasRect;

	private ArrayList<UIFilledRectangle> canvasDrawnRects;

	private boolean mousePressed = false;
	private Vec2 mousePos;

	private FilledRectangle lineEndcap;

	private UIFilledRectangle colorSelectorFrame;
	private ArrayList<UIFilledRectangle> colorSelectorCircles;
	private ArrayList<Button> colorSelectorButtons;

	private static Color[] colors = { Color.BLACK, Color.GRAY, Color.WHITE, Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.PINK, new Color(112.0f / 255.0f, 87.0f / 255.0f, 64.0f / 255.0f) };
	private static float[] brushSizes = { 20, 10, 5 };

	private int selectedColor = 0;

	private FilledRectangle smoothCircle;

	private long selectedInputID;

	private UIFilledRectangle scoreboardFrame;
	private HashMap<Integer, Text> scoreboardPointTexts;

	private long startTimeMillis;

	private boolean isInGame = false;
	private boolean isInPickPhase = false;
	private boolean isInDrawPhase = false;

	private UIFilledRectangle crackLevelSelectFrame;
	private Button crackLevelSelectBtn;
	private Button crackLevelConfirmBtn;
	private Text crackLevelDescriptionText;
	private Text crackMultiplierDescriptionText;
	private int selectedCrackLevel = 0;
	private String[] crackLevelDescriptionStrings;
	private String[] crackMultiplierStrings;

	private UIFilledRectangle wordSelectFrame;

	private UIFilledRectangle wordHintFrame;
	private Text wordHintText;

	private float guessFrameWidth = 300;
	private UIFilledRectangle guessFrame;
	private ArrayList<UIElement> guessChatElements;

	private String drawPhaseWord;

	private UIFilledRectangle startGameFrame;

	private UIFilledRectangle logo;

	private static ArrayList<String> wordList;

	private boolean doCanvasHueShifting = false;
	private boolean doColorSwapping = false;
	private boolean doLineShifting = false;
	private boolean doGuessScrambling = false;
	private boolean doHintScrambling = false;

	private int crackLvl3Cnt = 0;

	private float brushSize = 10f;

	private FilledRectangle trashCanIcon;

	private boolean guessedDrawPhaseWord = false;

	private boolean displayDrawPhaseWord = false;

	public CrackHeadsState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm, client, mainLobbyState);

		this.startTimeMillis = System.currentTimeMillis();
	}

	@Override
	public void _load() {
		if (wordList == null) {
			wordList = new ArrayList<String>();
			try {
				BufferedReader fin = new BufferedReader(new FileReader(FileUtils.loadFile("/crack_heads/crack_heads_words.txt")));
				String next = fin.readLine();
				while (next != null) {
					next = next.toUpperCase();
					next = next.trim();
					wordList.add(next);
					next = fin.readLine();
				}
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		this.crackLevelDescriptionStrings = new String[] { "You should take some crack, no balls", "A little bit of crack, cmon man, you can take more than that", "Zooted", "Uh Oh", "Overdose", };
		this.crackMultiplierStrings = new String[] { "1.0x", "1.25x", "1.5x", "2.0x" };

		this.uiScreen = new UIScreen();
		this.uiScreen.setClearColorIDBufferOnRender(false);
		this.uiScreen.setReverseDepthColorID(true);

		TextureMaterial endcapTexture = new TextureMaterial(new Texture("/crack_heads/circle-128.png", 0, GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST, 1));
		this.lineEndcap = new FilledRectangle();
		this.lineEndcap.setTextureMaterial(endcapTexture);

		TextureMaterial smoothCircleTexture = new TextureMaterial(new Texture("/crack_heads/circle-128-smooth-shadow.png", Texture.VERTICAL_FLIP_BIT));
		this.smoothCircle = new FilledRectangle();
		this.smoothCircle.setTextureMaterial(smoothCircleTexture);

		TextureMaterial trashCanTexture = new TextureMaterial(new Texture("/crack_heads/trash_can.png", Texture.VERTICAL_FLIP_BIT));
		this.trashCanIcon = new FilledRectangle();
		this.trashCanIcon.setTextureMaterial(trashCanTexture);

		this.canvasBuffer = new Framebuffer(Main.windowWidth, Main.windowHeight);
		this.canvasTexture = new Texture(GL_RGBA, Main.windowWidth, Main.windowHeight, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.canvasBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.canvasTexture.getID());
		this.canvasBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.canvasBuffer.isComplete();

		this.canvasBuffer.bind();

		this.clearScene(CANVAS_SCENE);
		this.clearScene(CANVAS_DRAW_SCENE);
		this.canvasRect = new UIFilledRectangle(0, 0, 0, Main.windowWidth, Main.windowHeight, new FilledRectangle(), CANVAS_SCENE);
		this.canvasRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		this.canvasRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		this.canvasRect.setTextureMaterial(new TextureMaterial(this.canvasTexture));
		this.canvasRect.setMaterial(new Material(new Vec3(1, 1, 1)));

		UIFilledRectangle canvasBackground = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, CANVAS_SCENE);
		canvasBackground.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
		canvasBackground.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		canvasBackground.setMaterial(new Material(new Vec3(1, 1, 1)));
		canvasBackground.align();

		//setting background of canvas to white
		this.clearCanvas();

		this.mousePos = MouseInput.getMousePos();

		this.canvasDrawnRects = new ArrayList<>();
		this.scoreboardPointTexts = new HashMap<>();

		this.clearScene(INPUT_SCENE);

		this.drawHud();

		if (this.client.isHost()) {
			this.startGameFrame.easeXOffset(10);
		}

		UIElement.alignAllUIElements();
	}

	public static String getRandomWord() {
		return wordList.get((int) (Math.random() * wordList.size()));
	}

	@Override
	public void _kill() {
		this.uiScreen.kill();
	}

	private void drawHud() {
		this.clearScene(HUD_SCENE);
		this.clearScene(HUD_BACKGROUND_SCENE);

		//color selector
		float colorSelectorButtonWidth = 25;
		float colorSelectorMargin = 10;

		float colorSelectorFrameWidth = colors.length * (colorSelectorButtonWidth + colorSelectorMargin) - colorSelectorMargin;
		this.colorSelectorFrame = new UIFilledRectangle(0, colorSelectorMargin, 0, colorSelectorFrameWidth, colorSelectorButtonWidth, HUD_BACKGROUND_SCENE);
		this.colorSelectorFrame.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_BOTTOM);
		this.colorSelectorFrame.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_BOTTOM);
		this.colorSelectorFrame.setMaterial(new Material(new Vec4(0, 0, 0, 0f)));

		this.colorSelectorButtons = new ArrayList<>();
		this.colorSelectorCircles = new ArrayList<>();
		for (int i = 0; i < colors.length; i++) {
			float leftMargin = (colorSelectorButtonWidth + colorSelectorMargin) * i;

			Button colorButton = new Button(leftMargin, 0, colorSelectorButtonWidth, colorSelectorButtonWidth, "color_select " + i, " ", FontUtils.ggsans, 16, INPUT_SCENE);
			colorButton.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
			colorButton.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
			colorButton.setPressedMaterial(Material.transparent);
			colorButton.setHoveredMaterial(Material.transparent);
			colorButton.setReleasedMaterial(Material.transparent);
			colorButton.bind(this.colorSelectorFrame);

			UIFilledRectangle colorButtonCircle = new UIFilledRectangle(leftMargin, 0, 0, colorSelectorButtonWidth, colorSelectorButtonWidth, this.smoothCircle, HUD_SCENE);
			colorButtonCircle.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
			colorButtonCircle.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
			colorButtonCircle.setMaterial(new Material(colors[i]));
			colorButtonCircle.setEasingDurationMillis(100);
			colorButtonCircle.setEasingStyle(UIElement.EASE_OUT_QUAD);
			colorButtonCircle.bind(this.colorSelectorFrame);

			this.colorSelectorButtons.add(colorButton);
			this.colorSelectorCircles.add(colorButtonCircle);
		}

		for (int i = 0; i < brushSizes.length; i++) {
			float rightMargin = (colorSelectorButtonWidth + colorSelectorMargin) * i + colorSelectorMargin * 2;
			if (i == 2) {
				rightMargin -= 10;
			}

			Button colorButton = new Button(-rightMargin, 0, colorSelectorButtonWidth, colorSelectorButtonWidth, "size_select " + i, " ", FontUtils.ggsans, 16, INPUT_SCENE);
			colorButton.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
			colorButton.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_CENTER);
			colorButton.setPressedMaterial(Material.transparent);
			colorButton.setHoveredMaterial(Material.transparent);
			colorButton.setReleasedMaterial(Material.transparent);
			colorButton.bind(this.colorSelectorFrame);

			UIFilledRectangle colorButtonCircle = new UIFilledRectangle(-rightMargin, 0, 0, colorSelectorButtonWidth * (brushSizes[i] / 20f), colorSelectorButtonWidth * (brushSizes[i] / 20f), this.smoothCircle, HUD_SCENE);
			colorButtonCircle.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
			colorButtonCircle.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_CENTER);
			colorButtonCircle.setMaterial(new Material(colors[2]));
			colorButtonCircle.setEasingDurationMillis(100);
			colorButtonCircle.setEasingStyle(UIElement.EASE_OUT_QUAD);
			colorButtonCircle.bind(this.colorSelectorFrame);

			this.colorSelectorButtons.add(colorButton);
			this.colorSelectorCircles.add(colorButtonCircle);
		}

		Button trashButton = new Button(-colorSelectorMargin * 2, 0, colorSelectorButtonWidth, colorSelectorButtonWidth, "trash_select", " ", FontUtils.ggsans, 16, INPUT_SCENE);
		trashButton.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_CENTER_TOP);
		trashButton.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
		trashButton.setPressedMaterial(Material.transparent);
		trashButton.setHoveredMaterial(Material.transparent);
		trashButton.setReleasedMaterial(Material.transparent);
		trashButton.bind(this.colorSelectorFrame);

		UIFilledRectangle trashCan = new UIFilledRectangle(-colorSelectorMargin * 2, 0, 0, colorSelectorButtonWidth, colorSelectorButtonWidth, this.trashCanIcon, HUD_SCENE);
		trashCan.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_CENTER_TOP);
		trashCan.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
		trashCan.setMaterial(new Material(new Vec3(1)));
		trashCan.setEasingDurationMillis(100);
		trashCan.setEasingStyle(UIElement.EASE_OUT_QUAD);
		trashCan.bind(this.colorSelectorFrame);

		this.colorSelectorButtons.add(trashButton);
		this.colorSelectorCircles.add(trashCan);

		//logo
		TextureMaterial logoTexture = new TextureMaterial(new Texture("/crack_heads/crack_heads_logo.png", Texture.VERTICAL_FLIP_BIT));

		this.logo = new UIFilledRectangle(0, 10, 0, 600, 150, new FilledRectangle(), HUD_SCENE);
		this.logo.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.logo.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		this.logo.setTextureMaterial(logoTexture);

		this.drawScoreboard();
		this.drawCrackLevelSelect();
		this.drawGuessFrame();
		this.drawStartGameFrame();
	}

	private void drawScoreboard() {
		//scoreboard
		float scoreboardWidth = 250;
		float scoreboardCellHeight = 50;

		if (this.scoreboardFrame == null) {
			//initialize everything. 
			this.scoreboardFrame = new UIFilledRectangle(-scoreboardWidth - 10, 10, 0, scoreboardWidth, scoreboardCellHeight, HUD_SCENE);
			this.scoreboardFrame.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			this.scoreboardFrame.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
			this.scoreboardFrame.setMaterial(Material.transparent);

			this.scoreboardPointTexts = new HashMap<>();
			for (int id : this.client.getPlayers().keySet()) {
				float yOffset = this.scoreboardPointTexts.size() * (scoreboardCellHeight + 10);
				UIFilledRectangle pointDisplayCell = new UIFilledRectangle(0, yOffset, 0, scoreboardWidth, scoreboardCellHeight, HUD_SCENE);
				pointDisplayCell.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
				pointDisplayCell.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
				pointDisplayCell.setMaterial(LobbyState.lightGray);
				pointDisplayCell.bind(this.scoreboardFrame);

				Text nickDisplayText = new Text(10, 0, 0, scoreboardWidth - 70, this.client.getPlayers().get(id), FontUtils.ggsans, 16, Color.WHITE, HUD_TEXT_SCENE);
				nickDisplayText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
				nickDisplayText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
				nickDisplayText.bind(pointDisplayCell);

				Text pointDisplayText = new Text(10, 0, 0, 50, "0", FontUtils.ggsans.deriveFont(Font.BOLD), 24, Color.WHITE, HUD_TEXT_SCENE);
				pointDisplayText.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_CENTER_TOP);
				pointDisplayText.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_CENTER);
				pointDisplayText.bind(pointDisplayCell);

				this.scoreboardPointTexts.put(id, pointDisplayText);
			}

			this.scoreboardFrame.setEasingStyle(UIElement.EASE_OUT_QUAD);
		}
	}

	private void updatePoints() {
		//update points
		HashMap<Integer, Integer> playerPoints = this.client.crackHeadsGetPoints();
		for (int id : playerPoints.keySet()) {
			Text pointText = this.scoreboardPointTexts.get(id);
			pointText.setText(playerPoints.get(id) + "");
		}
	}

	private void drawCrackLevelSelect() {
		this.crackLevelSelectFrame = new UIFilledRectangle(0, Main.windowHeight, 0, 300, 300, HUD_BACKGROUND_SCENE);
		this.crackLevelSelectFrame.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		this.crackLevelSelectFrame.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		this.crackLevelSelectFrame.setMaterial(LobbyState.lightGray);

		this.crackLevelSelectBtn = new Button(0, 10, 130, 130, "crack_level_btn", "0", FontUtils.ggsans.deriveFont(Font.BOLD), 48, INPUT_SCENE);
		this.crackLevelSelectBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.crackLevelSelectBtn.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		this.crackLevelSelectBtn.bind(this.crackLevelSelectFrame);

		this.crackLevelConfirmBtn = new Button(0, 10, 130, 40, "crack_level_confirm_btn", "Confirm", FontUtils.ggsans.deriveFont(Font.BOLD), 24, INPUT_SCENE);
		this.crackLevelConfirmBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_BOTTOM);
		this.crackLevelConfirmBtn.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_BOTTOM);
		this.crackLevelConfirmBtn.bind(this.crackLevelSelectFrame);

		this.crackLevelDescriptionText = new Text(0, this.crackLevelSelectBtn.getHeight() + 20, 0, this.crackLevelSelectFrame.getWidth() - 20, this.crackLevelDescriptionStrings[0], FontUtils.ggsans, 16, Color.WHITE, HUD_TEXT_SCENE);
		this.crackLevelDescriptionText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.crackLevelDescriptionText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		this.crackLevelDescriptionText.setTextWrapping(true);
		this.crackLevelDescriptionText.bind(this.crackLevelSelectFrame);

		this.crackMultiplierDescriptionText = new Text(0, this.crackLevelConfirmBtn.getHeight() + 20, this.crackMultiplierStrings[0], FontUtils.ggsans.deriveFont(Font.BOLD), 24, Color.WHITE, HUD_TEXT_SCENE);
		this.crackMultiplierDescriptionText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_BOTTOM);
		this.crackMultiplierDescriptionText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_BOTTOM);
		this.crackMultiplierDescriptionText.bind(this.crackLevelSelectFrame);

		this.setSelectedCrackLevel(0);
	}

	private void incrementSelectedCrackLevel() {
		this.setSelectedCrackLevel(this.selectedCrackLevel + 1);
	}

	private void setSelectedCrackLevel(int level) {
		this.selectedCrackLevel = level;
		this.selectedCrackLevel %= 4;
		this.crackLevelSelectBtn.setText(this.selectedCrackLevel + "");
		this.crackMultiplierDescriptionText.setText(this.crackMultiplierStrings[this.selectedCrackLevel]);
		this.crackMultiplierDescriptionText.setWidth(this.crackMultiplierDescriptionText.getTextWidth());
		this.crackLevelDescriptionText.setText(this.crackLevelDescriptionStrings[this.selectedCrackLevel]);
	}

	private void drawWordSelectFrame() {
		if (this.wordSelectFrame != null) {
			this.wordSelectFrame.kill();
		}

		this.wordSelectFrame = new UIFilledRectangle(0, Main.windowHeight, 0, 500, 160, HUD_BACKGROUND_SCENE);
		this.wordSelectFrame.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		this.wordSelectFrame.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		this.wordSelectFrame.setMaterial(LobbyState.lightGray);

		ArrayList<String> words = this.client.crackHeadsGetWordOptions();
		Button word1 = new Button(0, 10, 480, 40, "btn_word_select 0", words.get(0), FontUtils.ggsans.deriveFont(Font.BOLD), 24, INPUT_SCENE);
		word1.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		word1.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		word1.bind(this.wordSelectFrame);

		Button word2 = new Button(0, 60, 480, 40, "btn_word_select 1", words.get(1), FontUtils.ggsans.deriveFont(Font.BOLD), 24, INPUT_SCENE);
		word2.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		word2.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		word2.bind(this.wordSelectFrame);

		Button word3 = new Button(0, 110, 480, 40, "btn_word_select 2", words.get(2), FontUtils.ggsans.deriveFont(Font.BOLD), 24, INPUT_SCENE);
		word3.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		word3.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		word3.bind(this.wordSelectFrame);
	}

	private void drawGuessFrame() {
		float inputHeight = 30;
		this.guessFrame = new UIFilledRectangle(-this.guessFrameWidth - 10, 10, 0, this.guessFrameWidth, inputHeight + 20, HUD_BACKGROUND_SCENE);
		this.guessFrame.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		this.guessFrame.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
		this.guessFrame.setMaterial(LobbyState.lightGray);

		TextField guessTf = new TextField(10, 10, 240, 30, "tf_guess", "Guess the word", FontUtils.ggsans, 16, INPUT_SCENE);
		guessTf.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		guessTf.setContentAlignmentStyle(UIElement.FROM_LEFT, UIElement.ALIGN_BOTTOM);
		guessTf.bind(this.guessFrame);

		Button guessBtn = new Button(10, 10, 30, 30, "btn_guess", "<", FontUtils.ggsans.deriveFont(Font.BOLD), 16, INPUT_SCENE);
		guessBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		guessBtn.setContentAlignmentStyle(UIElement.FROM_RIGHT, UIElement.ALIGN_BOTTOM);
		guessBtn.bind(this.guessFrame);

		this.guessChatElements = new ArrayList<>();
	}

	private void drawWordHintFrame() {
		if (this.wordHintFrame != null) {
			this.wordHintFrame.kill();
		}
		HashSet<Integer> hints = this.client.crackHeadsGetHints();
		char[] carr = this.drawPhaseWord.toCharArray();
		for (int i = 0; i < carr.length; i++) {
			if (Character.isLetterOrDigit(carr[i]) && !this.client.crackHeadsIsDrawing() && !hints.contains(i)) {
				carr[i] = '_';
			}
		}

		String displayWord = "";
		for (int i = 0; i < carr.length; i++) {
			displayWord += carr[i];
			if (i != carr.length) {
				displayWord += " ";
			}
		}

		this.wordHintText = new Text(0, 0, displayWord, FontUtils.ggsans.deriveFont(Font.BOLD), 36, Color.WHITE, HUD_TEXT_SCENE);
		this.wordHintText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_BOTTOM);
		this.wordHintText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);

		this.wordHintFrame = new UIFilledRectangle(0, -Main.windowHeight, 0, this.wordHintText.getWidth() + 40, 60, HUD_BACKGROUND_SCENE);
		this.wordHintFrame.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.wordHintFrame.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		this.wordHintFrame.setMaterial(LobbyState.lightGray);

		this.wordHintText.bind(this.wordHintFrame);
	}

	//when new letters get revealed, show more chars. 
	private void updateWordHint() {
		HashSet<Integer> hints = this.client.crackHeadsGetHints();
		char[] carr = this.drawPhaseWord.toCharArray();
		for (int i = 0; i < carr.length; i++) {
			if (Character.isLetterOrDigit(carr[i]) && !this.client.crackHeadsIsDrawing() && !hints.contains(i) && !this.displayDrawPhaseWord) {
				carr[i] = '_';
			}
		}

		String displayWord = "";
		for (int i = 0; i < carr.length; i++) {
			displayWord += carr[i];
			if (i != carr.length) {
				displayWord += " ";
			}
		}

		this.wordHintText.setText(displayWord);
		this.wordHintText.setWidth(this.wordHintText.getTextWidth());
		this.wordHintText.align();
		this.wordHintFrame.setWidth(this.wordHintText.getWidth() + 40);
		this.wordHintFrame.align();
	}

	private void drawStartGameFrame() {
		this.startGameFrame = new UIFilledRectangle(-Main.windowWidth, 10, 0, 220, 100, HUD_BACKGROUND_SCENE);
		this.startGameFrame.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		this.startGameFrame.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
		this.startGameFrame.setMaterial(LobbyState.lightGray);

		Button startGameBtn = new Button(10, 10, 200, 80, "btn_start_game", "Start Game", FontUtils.ggsans.deriveFont(Font.BOLD), 36, INPUT_SCENE);
		startGameBtn.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		startGameBtn.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		startGameBtn.bind(this.startGameFrame);
	}

	private void alignGuesses() {
		int yOffset = 50 + 10;

		while (this.guessChatElements.size() > 30) {
			UIElement e = this.guessChatElements.remove(this.guessChatElements.size() - 1);
			e.kill();
		}

		for (int i = 0; i < this.guessChatElements.size(); i++) {
			UIElement e = this.guessChatElements.get(i);
			e.easeYOffset(yOffset);
			yOffset += e.getHeight() + 10;
		}
	}

	private void clearGuesses() {
		//remove all chat elements. 
		for (UIElement e : this.guessChatElements) {
			e.kill();
		}
		this.guessChatElements.clear();
	}

	private void clearCanvas() {
		this.canvasBuffer.bind();
		glClear(GL_COLOR_BUFFER_BIT);
	}

	private void setCanvasHue(Material m) {
		this.canvasRect.setMaterial(m);
	}

	//puts rects into the canvas draw scene for rendering. 
	private void drawLine(Vec2 a, Vec2 b, float lineWidth, int colorIndex) {
		Material lineColor = new Material(colors[colorIndex]);

		Vec2 dir = new Vec2(a, b);
		float lineLength = dir.length();

		Vec2 center = a.add(dir.mul(0.5f));

		float lineRotationRads = (float) (-Math.atan2(dir.x, dir.y) + Math.PI / 2);

		//line body
		UIFilledRectangle lineBody = new UIFilledRectangle(0, 0, 0, lineLength, lineWidth, CANVAS_DRAW_SCENE);
		lineBody.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		lineBody.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		lineBody.setMaterial(lineColor);
		lineBody.setRotationRads(lineRotationRads);
		lineBody.setFrameAlignmentOffset(center.x, center.y);
		lineBody.align();

		this.canvasDrawnRects.add(lineBody);

		//endcaps
		float endcapWidth = lineWidth - 1f;
		UIFilledRectangle endcapA = new UIFilledRectangle(a.x, a.y, 0, endcapWidth, endcapWidth, this.lineEndcap, CANVAS_DRAW_SCENE);
		endcapA.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		endcapA.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		endcapA.setMaterial(lineColor);
		endcapA.align();

		UIFilledRectangle endcapB = new UIFilledRectangle(b.x, b.y, 0, endcapWidth, endcapWidth, this.lineEndcap, CANVAS_DRAW_SCENE);
		endcapB.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		endcapB.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		endcapB.setMaterial(lineColor);
		endcapB.align();

		this.canvasDrawnRects.add(endcapA);
		this.canvasDrawnRects.add(endcapB);
	}

	private void resetHUDOffsets() {
		if (this.scoreboardFrame != null) {
			this.scoreboardFrame.easeXOffset(-Main.windowWidth / 2);
		}
		if (this.wordSelectFrame != null) {
			this.wordSelectFrame.easeYOffset(Main.windowHeight);
		}
		this.colorSelectorFrame.easeYOffset(-Main.windowHeight);
		this.crackLevelSelectFrame.easeYOffset(Main.windowHeight);
		this.guessFrame.easeXOffset(-Main.windowWidth / 2);
		if (this.wordHintFrame != null) {
			this.wordHintFrame.easeYOffset(-Main.windowHeight / 2);
		}
		this.startGameFrame.easeXOffset(-Main.windowWidth);
	}

	public void startGame() {
		this.resetHUDOffsets();
		this.drawScoreboard();
		this.scoreboardFrame.easeXOffset(10);
		this.colorSelectorFrame.easeYOffset(10);
		this.isInGame = true;
		this.logo.easeYOffset(-Main.windowWidth / 2);
		this.updatePoints();
	}

	public void endGame() {
		this.resetHUDOffsets();
		this.isInGame = false;
		if (this.client.isHost()) {
			this.startGameFrame.easeXOffset(10);
		}
		this.scoreboardFrame.easeXOffset(10);
		this.colorSelectorFrame.easeYOffset(10);
		this.logo.easeYOffset(10);
		this.isInPickPhase = false;
		this.isInDrawPhase = false;
		this.doCanvasHueShifting = false;
		this.doColorSwapping = false;
		this.doLineShifting = false;
		this.doGuessScrambling = false;
		this.doHintScrambling = false;
		this.setCanvasHue(new Material(new Vec3(1)));
	}

	public void startDrawPhase() {
		this.resetHUDOffsets();
		this.clearGuesses();
		this.displayDrawPhaseWord = false;
		this.drawPhaseWord = this.client.crackHeadsGetDrawPhaseWord();
		this.drawWordHintFrame();
		this.wordHintFrame.easeYOffset(10);
		this.guessFrame.easeXOffset(10);
		this.scoreboardFrame.easeXOffset(10);
		if (this.client.crackHeadsIsDrawing()) {
			this.colorSelectorFrame.easeYOffset(10);
		}
		this.isInDrawPhase = true;
		this.isInPickPhase = false;
		this.guessedDrawPhaseWord = false;
		this.clearCanvas();

		//apply crack effects
		switch (this.selectedCrackLevel) {
		case 3:
			this.doGuessScrambling = true;
			this.doHintScrambling = true;

		case 2:
			this.doColorSwapping = true;
			this.doLineShifting = true;

		case 1:
			this.doCanvasHueShifting = true;
		}

		if (this.doHintScrambling) {
			char[] carr = this.drawPhaseWord.toCharArray();
			int guarantee = carr.length / 4 + (carr.length % 4 == 0 ? 0 : 1);
			for (int j = 0; j < guarantee; j++) {
				int ind = (int) (Math.random() * carr.length);
				carr[ind] = (char) ('A' + (int) (Math.random() * 26));
			}
			for (int j = 0; j < carr.length; j++) {
				if (Math.random() < 0.3) {
					carr[j] = (char) ('A' + (int) (Math.random() * 26));
				}
			}
			this.drawPhaseWord = new String(carr);
		}

		NoiseGenerator.randomizeNoise();
	}

	public void startPickPhase() {
		this.resetHUDOffsets();
		if (this.isInDrawPhase) {
			this.displayDrawPhaseWord = true;
			this.updateWordHint();
			this.wordHintFrame.easeYOffset(10);
		}

		this.isInPickPhase = true;
		this.isInDrawPhase = false;
		this.scoreboardFrame.easeXOffset(10);
		if (this.client.crackHeadsIsPickingWord()) {
			this.drawWordSelectFrame();
			this.wordSelectFrame.easeYOffset(0);
		}
		else {
			this.crackLevelSelectFrame.easeYOffset(0);
		}
		this.colorSelectorFrame.easeYOffset(10);
		this.setSelectedCrackLevel(0);
		this.doCanvasHueShifting = false;
		this.doColorSwapping = false;
		this.doLineShifting = false;
		this.doGuessScrambling = false;
		this.doHintScrambling = false;
		this.setCanvasHue(new Material(new Vec3(1)));
	}

	@Override
	public void _update() {
		Input.inputsHovered(this.selectedInputID, INPUT_SCENE);

		// -- NETWORKING --
		if (this.client.crackHeadsGameStarting()) {
			this.startGame();
		}
		if (this.client.crackHeadsGameEnding()) {
			this.endGame();
		}
		if (this.client.crackHeadsPickPhaseStarting()) {
			this.startPickPhase();
		}
		if (this.client.crackHeadsDrawPhaseStarting()) {
			this.startDrawPhase();
		}

		//manage guesses
		ArrayList<Pair<Integer, String>> guesses = this.client.crackHeadsGetIncomingGuesses();
		for (Pair<Integer, String> i : guesses) {
			int id = i.first;
			String guess = i.second;
			String nick = this.client.getPlayers().get(id);
			UIElement nextElement = null;

			boolean guessedTheWord = guess.equalsIgnoreCase(this.client.crackHeadsGetDrawPhaseWord());

			if (this.doGuessScrambling) {
				char[] carr = guess.toCharArray();
				int guarantee = carr.length / 4 + (carr.length % 4 == 0 ? 0 : 1);
				for (int j = 0; j < guarantee; j++) {
					int ind = (int) (Math.random() * carr.length);
					carr[ind] = (char) ('a' + (int) (Math.random() * 26));
				}
				for (int j = 0; j < carr.length; j++) {
					if (Math.random() < 0.3) {
						carr[j] = (char) ('a' + (int) (Math.random() * 26));
					}
				}
				guess = new String(carr);
			}

			if (guessedTheWord) {
				UIFilledRectangle greenRect = new UIFilledRectangle(10, 0, 0, 0, 30, HUD_BACKGROUND_SCENE);
				greenRect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
				greenRect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
				greenRect.setFillWidth(true);
				greenRect.setFillWidthMargin(10);
				greenRect.setMaterial(new Material(new Vec3(17, 130, 59).mul(1.0f / 255.0f)));
				greenRect.bind(this.guessFrame);

				//different colors for different levels of crack
				switch (this.client.crackHeadsGetCrackLevels().get(id)) {
				case 1: {
					greenRect.setMaterial(new Material(new Vec3(255, 193, 0).mul(1.0f / 255.0f)));
					break;
				}

				case 2: {
					greenRect.setMaterial(new Material(new Vec3(255, 116, 0).mul(1.0f / 255.0f)));
					break;
				}

				case 3: {
					greenRect.setMaterial(new Material(new Vec3(255, 0, 0).mul(1.0f / 255.0f)));
					break;
				}
				}

				Text msg = new Text(10, 0, 0, this.guessFrameWidth - 40, nick + " guessed the word", FontUtils.ggsans.deriveFont(Font.BOLD), 16, Color.WHITE, HUD_TEXT_SCENE);
				msg.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
				msg.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
				msg.bind(greenRect);

				greenRect.setEasingDurationMillis(100);
				greenRect.setEasingStyle(UIElement.EASE_OUT_QUAD);

				nextElement = greenRect;
			}
			else {
				Text nickText = new Text(10, 0, 0, 70, nick, FontUtils.ggsans, 16, Color.BLACK, HUD_TEXT_SCENE);
				nickText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
				nickText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

				Text msg = new Text(85, 0, 0, this.guessFrameWidth - 25 - 70, ": " + guess, FontUtils.ggsans, 16, Color.BLACK, HUD_TEXT_SCENE);
				msg.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
				msg.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

				UIFilledRectangle rect = new UIFilledRectangle(10, 0, 0, 0, nickText.getHeight(), HUD_BACKGROUND_SCENE);
				rect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
				rect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
				rect.setFillWidth(true);
				rect.setFillWidthMargin(10);
				rect.setMaterial(Material.transparent);
				rect.bind(this.guessFrame);

				nickText.bind(rect);
				msg.bind(rect);

				rect.setEasingDurationMillis(100);
				rect.setEasingStyle(UIElement.EASE_OUT_QUAD);

				nextElement = rect;
			}
			this.guessChatElements.add(0, nextElement);
			nextElement.setYOffset(60);
		}

		if (guesses.size() != 0) {
			this.alignGuesses();
			this.updatePoints();
		}

		if (this.isInDrawPhase) {
			if (this.client.crackHeadsHasNewHint()) {
				this.updateWordHint();
			}
		}

		if (this.client.crackHeadsShouldClearScreen()) {
			this.clearCanvas();
		}

		//drawing lines onto canvas. 
		//do this first, since model updates are the last to happen
		if (this.canvasDrawnRects.size() != 0) {
			this.uiScreen.setUIScene(CANVAS_DRAW_SCENE);
			this.uiScreen.render(this.canvasBuffer);

			for (UIFilledRectangle rect : this.canvasDrawnRects) {
				rect.kill();
			}
			this.canvasDrawnRects.clear();
		}

		//color selector animations
		for (int i = 0; i < this.colorSelectorButtons.size(); i++) {
			if (this.colorSelectorButtons.get(i).hasMouseEntered() && i != this.selectedColor) {
				this.colorSelectorCircles.get(i).easeYOffset(5);
			}
			if (this.colorSelectorButtons.get(i).hasMouseExited()) {
				this.colorSelectorCircles.get(i).easeYOffset(0);
			}
		}

		Vec2 nextMousePos = MouseInput.getMousePos();
		if (this.mousePressed) {
			if (!this.isInDrawPhase || (this.isInDrawPhase && this.client.crackHeadsIsDrawing() && this.isInGame)) {
				this.drawLine(this.mousePos, nextMousePos, this.brushSize, this.selectedColor);
				this.client.crackHeadsDrawLine(this.mousePos, nextMousePos, this.brushSize, this.selectedColor);
			}
		}
		this.mousePos = nextMousePos;

		//canvas hue shifting
		if (this.doCanvasHueShifting) {
			float phase = (System.currentTimeMillis() - this.startTimeMillis) / 200.0f;
			Vec3 v = new Vec3((float) Math.sqrt(3) / 2, (float) Math.sqrt(3) / 2, 0);
			v.rotateX(phase);
			v.rotateZ((float) -Math.toRadians(45));
			v.rotateY((float) Math.toRadians(45));
			this.setCanvasHue(new Material(v));
		}

		//get lines from other players
		ArrayList<Quad<Vec2, Vec2, Float, Integer>> incomingLines = this.client.crackHeadsGetIncomingLines();
		for (Quad<Vec2, Vec2, Float, Integer> i : incomingLines) {
			Vec2 a = i.first;
			Vec2 b = i.second;
			float size = i.third;
			int colorIndex = i.fourth;

			if (this.doColorSwapping) {
				colorIndex = (int) (Math.random() * colors.length);
			}

			if (this.doLineShifting) {
				a = shiftVec(a);
				b = shiftVec(b);
			}

			this.drawLine(a, b, size, colorIndex);
		}
	}

	private Vec2 shiftVec(Vec2 a) {
		float xNoise = (float) NoiseGenerator.noise(a.x, 1.0 / 50.0, 80, 0.5, 2, 2);
		float yNoise = (float) NoiseGenerator.noise(a.y, 1.0 / 50.0, 80, 0.5, 2, 2);
		return new Vec2(a.x + xNoise, a.y + yNoise);
	}

	private void makeGuess() {
		if (this.client.crackHeadsIsDrawing()) {
			return;
		}
		if (this.guessedDrawPhaseWord) {
			return;
		}
		String guess = Input.getText("tf_guess");
		if (guess.length() == 0) {
			return;
		}
		if (guess.equalsIgnoreCase(this.client.crackHeadsGetDrawPhaseWord())) {
			this.guessedDrawPhaseWord = true;
		}
		this.client.crackHeadsMakeGuess(guess);
		TextField tf = (TextField) Input.getInput("tf_guess");
		tf.setText("");
	}

	@Override
	public void _render(Framebuffer outputBuffer) {
		this.uiScreen.setUIScene(CANVAS_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(HUD_BACKGROUND_SCENE);
		this.uiScreen.render(outputBuffer);
		this.uiScreen.setUIScene(HUD_SCENE);
		this.uiScreen.render(outputBuffer);
		this.uiScreen.setUIScene(HUD_TEXT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(INPUT_SCENE);
		this.uiScreen.render(outputBuffer);
		this.selectedInputID = this.uiScreen.getEntityIDAtMouse();
	}

	@Override
	public void _mousePressed(int button) {
		Input.inputsPressed(this.selectedInputID);

		boolean inputPressed = false;
		for (Input i : Input.getInputs().values()) {
			if (i.isPressed()) {
				inputPressed = true;
			}
		}
		if (!inputPressed) {
			this.mousePressed = true;
		}
	}

	@Override
	public void _mouseReleased(int button) {
		Input.inputsReleased(this.selectedInputID, INPUT_SCENE);
		this.mousePressed = false;

		String[] clicked = Input.getClicked().split(" ");
		switch (clicked[0]) {
		case "color_select": {
			int which = Integer.parseInt(clicked[1]);
			if (which != this.selectedColor) {
				this.selectedColor = which;
				this.colorSelectorCircles.get(which).easeYOffset(0);
			}
			break;
		}

		case "size_select": {
			int which = Integer.parseInt(clicked[1]);
			if (brushSizes[which] != this.brushSize) {
				this.brushSize = brushSizes[which];
				this.colorSelectorCircles.get(which + colors.length).easeYOffset(0);
			}
			break;
		}

		case "trash_select": {
			this.client.crackHeadsClearScreen();
			break;
		}

		case "crack_level_btn": {
			this.incrementSelectedCrackLevel();
			break;
		}

		case "crack_level_confirm_btn": {
			this.client.crackHeadsPickCrackLevel(this.selectedCrackLevel);
			this.crackLevelSelectFrame.easeYOffset(Main.windowHeight);
			break;
		}

		case "btn_word_select": {
			String word = this.client.crackHeadsGetWordOptions().get(Integer.parseInt(clicked[1]));
			this.client.crackHeadsPickWord(word);
			this.wordSelectFrame.easeYOffset(Main.windowHeight);
			break;
		}

		case "btn_guess": {
			this.makeGuess();
			break;
		}

		case "btn_start_game": {
			if (this.client.isHost()) {
				this.client.crackHeadsStartGame();
			}
			break;
		}
		}
	}

	@Override
	public void _mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

	}

	@Override
	public void _keyPressed(int key) {
		if (key == GLFW_KEY_ENTER) {
			TextField tf = (TextField) Input.getInput("tf_guess");
			if (tf.isClicked()) {
				this.makeGuess();
			}
		}
	}

	@Override
	public void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
