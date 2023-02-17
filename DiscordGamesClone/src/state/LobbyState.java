package state;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;

import audio.Sound;
import client.GameClient;
import entity.Entity;
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
import player.Camera;
import scene.DirLight;
import scene.Light;
import scene.Scene;
import screen.PerspectiveScreen;
import screen.Screen;
import screen.UIScreen;
import server.GameServer;
import ui.Text;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FontUtils;
import util.Mat4;
import util.MathUtils;
import util.NetworkingUtils;
import util.Vec3;

public class LobbyState extends State {

	private static final int PERSPECTIVE_BACKGROUND_SCENE = 0; // background, duh

	//this is dumb, i shouldn't have to use another render pass to do this.
	//try fill color using opengl?
	private static final int BACKGROUND_UI_SCENE = 1; //the blue discord background. 

	private static final int STATIC_UI_SCENE = 2; // for unchanging parts of the ui like the logo
	private static final int DYNAMIC_UI_SCENE = 3; // inputs and stuff

	private static final int LOBBY_MAIN_BACKGROUND = 4;
	private static final int LOBBY_MAIN_DYNAMIC = 5;

	private PerspectiveScreen perspectiveScreen; // 3D background
	private UIScreen uiScreen; // Menu UI

	private GameServer server;
	private GameClient client;

	private boolean initiateNetworking;

	private String ip;
	private int port;
	private boolean hosting;

	private HashMap<Integer, String> players;
	private int hostID;

	private static String[] defaultAdjectives = { "bryan", "autistic", "goofyass", "bitchass", "funny", "laughable", "puny", "disgusting", "enlightened" };
	private static String[] defaultNouns = { "penis", "ballsack", "chair", "idiot", "gronk", "submarine", "cucumber", "urethra", "bryan" };
	private String nickname;

	private Sound menuMusic;

	private long startTime;

	public static Material lightGray = new Material(new Vec3(66, 69, 73).mul(1.0f / 255.0f));
	public static Material lightBlue = new Material(new Vec3(114, 137, 218).mul(1.0f / 255.0f));
	public static Material gray = new Material(new Vec3(54, 57, 62).mul(1.0f / 255.0f));
	public static Material darkGray = new Material(new Vec3(40, 43, 48).mul(1.0f / 255.0f));

	private float sideButtonBaseOffset = 30;
	private float sideButtonMaxOffset = 150;
	private int sideButtonWidth = (int) sideButtonMaxOffset;

	private float startButtonHeight = 80;
	private float startButtonBaseOffset = -startButtonHeight - 10;
	private float startButtonMaxOffset = 10;
	private float startButtonOffset = startButtonBaseOffset;

	private float nickButtonHeight = 30;
	private float nickButtonBaseOffset = -nickButtonHeight - 10;
	private float nickButtonMaxOffset = 10;
	private float nickButtonOffset = nickButtonMaxOffset;

	private float disconnectButtonHeight = 30;
	private float disconnectButtonBaseOffset = -disconnectButtonHeight - 10;
	private float disconnectButtonMaxOffset = nickButtonMaxOffset + nickButtonHeight + 10;

	private ArrayList<UIFilledRectangle> transitionRects;
	private ArrayList<Float> transitionVels;
	private float transitionAccel = -1f;
	private float transitionBounceCoefficient = 0.2f; //1 for full bounce, 0 for no bounce. 

	private int transitionZ = 0;

	private int curBackgroundIndex = 0;

	private UIFilledRectangle backgroundRect;

	private Framebuffer lobbyMainFramebuffer;
	private Texture lobbyMainColorMap;
	private Texture lobbyMainColorIDMap;

	private ArrayList<TextureMaterial> backgroundTextures;
	private ArrayList<TextureMaterial> backgroundColorIDTextures;

	private boolean failedToConnect = false;

	public LobbyState(StateManager sm, String ip, int port, boolean hosting) {
		super(sm);

		this.ip = ip;
		this.port = port;
		this.hosting = hosting;

		if (this.hosting) {
			this.ip = NetworkingUtils.getLocalIP();
		}

		String adjective = defaultAdjectives[(int) (Math.random() * defaultAdjectives.length)];
		String noun = defaultNouns[(int) (Math.random() * defaultNouns.length)];

		this.nickname = adjective + " " + noun;

		this.startTime = System.currentTimeMillis();

		this.initiateNetworking = true;
	}

	@Override
	public void load() {
		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.getCamera().setVerticalFOV((float) Math.toRadians(70f));
		this.perspectiveScreen.getCamera().setPos(new Vec3(18.417412f, 1.7f, -29.812654f));

		this.uiScreen = new UIScreen();
		this.uiScreen.setClearColorIDBufferOnRender(false);

		Main.unlockCursor();

		Entity.killAll();

		// -- NETWORKING --
		if (this.initiateNetworking) {
			this.client = new GameClient();
			this.players = this.client.getPlayers();

			if (this.hosting) {
				this.startHosting();
			}

			if (!this.connect()) {
				this.failedToConnect = true;
			}

			this.client.setNickname(this.nickname);
			this.initiateNetworking = false;
		}

		// -- BACKGROUND --
		this.clearScene(PERSPECTIVE_BACKGROUND_SCENE);
		Model.addInstance(AssetManager.getModel("dust2"), Mat4.rotateX((float) Math.toRadians(90)).mul(Mat4.scale((float) 0.05)), PERSPECTIVE_BACKGROUND_SCENE);
		Light.addLight(PERSPECTIVE_BACKGROUND_SCENE, new DirLight(new Vec3(0.3f, -1f, -0.5f), new Vec3(0.5f), 0.3f));
		Scene.skyboxes.put(PERSPECTIVE_BACKGROUND_SCENE, AssetManager.getSkybox("stars_skybox"));

		this.drawMainMenu();

		this.menuMusic = new Sound("main_menu_music.ogg", true);
		int menuMusicID = this.menuMusic.addSource();
		Sound.setRelativePosition(menuMusicID, new Vec3(0));
		Sound.setGain(menuMusicID, 0.05f);

		this.transitionRects = new ArrayList<>();
		this.transitionVels = new ArrayList<>();
	}

	private void startHosting() {
		this.server = new GameServer(this.ip, this.port);
	}

	private void stopHosting() {
		if (this.server == null) {
			return;
		}
		this.server.exit();
	}

	private boolean connect() {
		return this.client.connect(this.ip, this.port);
	}

	private void disconnect() {
		this.client.disconnect();
		this.client.exit();
	}

	private void killNetworking() {
		this.disconnect();
		this.stopHosting();
	}

	private void returnToMainMenu() {
		this.killNetworking();
		this.sm.switchState(new MainMenuState(this.sm));
	}

	@Override
	public void kill() {
		this.perspectiveScreen.kill();
		this.uiScreen.kill();

		this.menuMusic.kill();
	}

	private void drawMainMenu() {
		this.backgroundTextures = new ArrayList<>();

		this.lobbyMainFramebuffer = new Framebuffer(Main.windowWidth, Main.windowHeight);
		this.lobbyMainColorMap = new Texture(GL_RGB, Main.windowWidth, Main.windowHeight, GL_RGB, GL_FLOAT);
		this.lobbyMainColorIDMap = new Texture(GL_RGBA, Main.windowWidth, Main.windowHeight, GL_RGBA, GL_FLOAT);
		this.lobbyMainFramebuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.lobbyMainColorMap.getID());
		this.lobbyMainFramebuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.lobbyMainColorIDMap.getID());
		this.lobbyMainFramebuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });
		this.lobbyMainFramebuffer.isComplete();

		this.backgroundTextures.add(new TextureMaterial(this.lobbyMainColorMap));
		this.backgroundTextures.add(new TextureMaterial(new Texture("/lobby/chess_with_mr_beast.png", Texture.VERTICAL_FLIP_BIT)));
		this.backgroundTextures.add(new TextureMaterial(new Texture("/lobby/letter_league_v1.png", Texture.VERTICAL_FLIP_BIT)));
		this.backgroundTextures.add(new TextureMaterial(new Texture("/lobby/blazing_eights.png", Texture.VERTICAL_FLIP_BIT)));
		this.backgroundTextures.add(new TextureMaterial(new Texture("/lobby/crack_heads_final.png", Texture.VERTICAL_FLIP_BIT)));

		// -- UI BACKGROUND --
		this.clearScene(BACKGROUND_UI_SCENE);
		this.backgroundRect = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, new FilledRectangle(), BACKGROUND_UI_SCENE);
		this.backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		this.backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		this.backgroundRect.setTextureMaterial(this.backgroundTextures.get(this.curBackgroundIndex));

		// -- MAIN BACKGROUND --	
		this.clearScene(LOBBY_MAIN_BACKGROUND);

		UIFilledRectangle mainBackgroundRect = new UIFilledRectangle(0, 0, 0, Main.windowWidth, Main.windowHeight, new FilledRectangle(), LOBBY_MAIN_BACKGROUND);
		mainBackgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		mainBackgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		mainBackgroundRect.setMaterial(lightGray);

		// -- MAIN DYNAMIC --
		this.drawLobbyMain();

		// -- STATIC UI --
		this.clearScene(STATIC_UI_SCENE);

		// -- DYNAMIC UI --
		this.clearScene(DYNAMIC_UI_SCENE);

		if (this.hosting) {
			Button startGameBtn = new Button(0, 50, 300, (int) this.startButtonHeight, "btn_start_game", "Start Game", FontUtils.ggsans.deriveFont(Font.BOLD), 48, DYNAMIC_UI_SCENE);
			startGameBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_BOTTOM);
			startGameBtn.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_BOTTOM);
		}

		TextField nicknameTf = new TextField(5, 10, 200, (int) this.nickButtonHeight, "tf_set_nickname", "New Nickname", FontUtils.ggsans, 16, DYNAMIC_UI_SCENE);
		nicknameTf.setFrameAlignmentStyle(UIElement.FROM_CENTER_RIGHT, UIElement.FROM_BOTTOM);
		nicknameTf.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		Button nicknameBtn = new Button(5, 10, 200, (int) this.nickButtonHeight, "btn_set_nickname", "Set Nickname", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		nicknameBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_BOTTOM);
		nicknameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);

		Button rightSideButton = new Button((int) this.sideButtonBaseOffset, 0, this.sideButtonWidth, Main.windowHeight, "btn_right_side", ">", FontUtils.ggsans.deriveFont(Font.BOLD), 48, DYNAMIC_UI_SCENE);
		rightSideButton.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		rightSideButton.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		Button leftSideButton = new Button((int) this.sideButtonBaseOffset, 0, this.sideButtonWidth, Main.windowHeight, "btn_left_side", "<", FontUtils.ggsans.deriveFont(Font.BOLD), 48, DYNAMIC_UI_SCENE);
		leftSideButton.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		leftSideButton.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);

		Button leaveLobbyBtn = new Button(0, this.disconnectButtonBaseOffset, 200, this.disconnectButtonHeight, "btn_disconnect", "Leave Lobby", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		leaveLobbyBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_RIGHT, UIElement.FROM_BOTTOM);
		leaveLobbyBtn.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_BOTTOM);

		UIElement.alignAllUIElements();
	}

	public void drawLobbyMain() {
		this.clearScene(LOBBY_MAIN_DYNAMIC);
		Text isHostingText = new Text(0, 30, this.hosting ? "You Are Host" : "Joined " + this.players.get(this.hostID) + "'s Game", FontUtils.ggsans.deriveFont(Font.BOLD), 48, Color.WHITE, LOBBY_MAIN_DYNAMIC);
		isHostingText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		isHostingText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);

		Text helloText = new Text(0, 90, "Hello, " + this.nickname + ".", FontUtils.ggsans, 24, Color.WHITE, LOBBY_MAIN_DYNAMIC);
		helloText.setFrameAlignmentStyle(UIElement.FROM_CENTER_RIGHT, UIElement.FROM_TOP);
		helloText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);

		Text versionText = new Text(20, 20, MainMenuState.versionNumber, FontUtils.ggsans, 16, Color.WHITE, LOBBY_MAIN_DYNAMIC);
		versionText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		versionText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		int yOffset = 130;
		int yIncrement = 30;
		for (int i : this.players.keySet()) {
			Text playerNickText = new Text(0, yOffset, this.players.get(i), FontUtils.ggsans, 16, Color.WHITE, LOBBY_MAIN_DYNAMIC);
			playerNickText.setFrameAlignmentStyle(UIElement.FROM_CENTER_RIGHT, UIElement.FROM_TOP);
			playerNickText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);

			yOffset += yIncrement;
		}
	}

	@Override
	public void update() {
		if (this.failedToConnect || !this.client.isConnected()) {
			this.returnToMainMenu();
		}

		Input.inputsHovered(uiScreen.getEntityIDAtMouse(), DYNAMIC_UI_SCENE);

		if (Input.getInput("btn_right_side") != null) {
			Button sideButton = (Button) Input.getInput("btn_right_side");
			if (sideButton.hasMouseEntered()) {
				sideButton.easeXOffset(this.sideButtonMaxOffset);
			}
			if (sideButton.hasMouseExited()) {
				sideButton.easeXOffset(this.sideButtonBaseOffset);
			}
		}

		if (Input.getInput("btn_left_side") != null) {
			Button sideButton = (Button) Input.getInput("btn_left_side");
			if (sideButton.hasMouseEntered()) {
				sideButton.easeXOffset(this.sideButtonMaxOffset);
			}
			if (sideButton.hasMouseExited()) {
				sideButton.easeXOffset(this.sideButtonBaseOffset);
			}
		}

		if (Input.getInput("btn_start_game") != null) {
			Button startButton = (Button) Input.getInput("btn_start_game");
			float diff = 0;
			if (this.curBackgroundIndex != 0) {
				diff = this.startButtonMaxOffset - this.startButtonOffset;
			}
			else {
				diff = this.startButtonBaseOffset - this.startButtonOffset;
			}
			this.startButtonOffset += diff * 0.1f;
			startButton.setFrameAlignmentOffset(0, (int) this.startButtonOffset);
			startButton.align();
		}

		if (Input.getInput("btn_set_nickname") != null) {
			Button setNickButton = (Button) Input.getInput("btn_set_nickname");
			float diff = 0;
			if (this.curBackgroundIndex == 0) {
				diff = this.nickButtonMaxOffset - this.nickButtonOffset;
			}
			else {
				diff = this.nickButtonBaseOffset - this.nickButtonOffset;
			}
			this.nickButtonOffset += diff * 0.1f;
			setNickButton.setFrameAlignmentOffset(5, (int) this.nickButtonOffset);
			setNickButton.align();
		}

		if (Input.getInput("tf_set_nickname") != null) {
			TextField setNickTF = (TextField) Input.getInput("tf_set_nickname");
			setNickTF.setFrameAlignmentOffset(5, (int) this.nickButtonOffset);
			setNickTF.align();
		}

		if (Input.getInput("btn_disconnect") != null) {
			Button setNickButton = (Button) Input.getInput("btn_disconnect");
			float diff = 0;
			float offset = setNickButton.getYOffset();
			if (this.curBackgroundIndex == 0) {
				diff = this.disconnectButtonMaxOffset - offset;
			}
			else {
				diff = this.disconnectButtonBaseOffset - offset;
			}
			offset += diff * 0.1f;
			setNickButton.setFrameAlignmentOffset(0, offset);
			setNickButton.align();
		}

		for (int i = 0; i < this.transitionRects.size(); i++) {
			UIFilledRectangle rect = this.transitionRects.get(i);
			float vel = this.transitionVels.get(i);

			float xOffset = rect.getXOffset() + vel;

			vel += this.transitionAccel;

			if (xOffset < 0) {
				xOffset = -xOffset;
				vel = -vel * this.transitionBounceCoefficient;
			}

			rect.setFrameAlignmentOffset((int) xOffset, 0);
			rect.align();

			this.transitionVels.set(i, vel);

			if (Math.abs(vel) < 4f && xOffset < 1f) {
				rect.setZ(-1);
				rect.setFrameAlignmentOffset(0, 0);

				this.backgroundRect.kill();
				this.backgroundRect = rect;

				this.transitionRects.remove(i);
				this.transitionVels.remove(i);
				i--;
			}
		}

		if (this.transitionRects.size() == 0) {
			this.transitionZ = 0;
		}

		this.hostID = this.client.getHostID();

		if (this.client.hasPlayerInfoChanged()) {
			this.drawLobbyMain();
		}

		if (this.client.getCurGame() != GameServer.LOBBY) {
			int nextGame = this.client.getCurGame();
			switch (nextGame) {
			case GameServer.CHESS:
				this.sm.switchState(new ChessState(this.sm, this.client, this));
				break;

			case GameServer.SCRABBLE:
				this.sm.switchState(new ScrabbleState(this.sm, this.client, this));
				break;

			case GameServer.BLAZING_EIGHTS:
				this.sm.switchState(new BlazingEightsState(this.sm, this.client, this));
				break;

			case GameServer.CRACK_HEADS:
				this.sm.switchState(new CrackHeadsState(this.sm, this.client, this));
				break;
			}
		}
	}

	@Override
	public void render(Framebuffer outputBuffer) {
		this.lobbyMainFramebuffer.bind();
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

		uiScreen.setUIScene(LOBBY_MAIN_BACKGROUND);
		uiScreen.render(this.lobbyMainFramebuffer);
		uiScreen.setUIScene(LOBBY_MAIN_DYNAMIC);
		uiScreen.render(this.lobbyMainFramebuffer);

		uiScreen.clearColorIDBuffer();

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
	}

	@Override
	public void mouseReleased(int button) {
		Input.inputsReleased(uiScreen.getEntityIDAtMouse(), DYNAMIC_UI_SCENE);
		String clickedButton = Input.getClicked();
		switch (clickedButton) {
		case "btn_right_side": {
			this.curBackgroundIndex++;
			this.curBackgroundIndex %= this.backgroundTextures.size();

			UIFilledRectangle rect1 = new UIFilledRectangle(Main.windowWidth, 0, this.transitionZ, Main.windowWidth, Main.windowHeight, new FilledRectangle(), BACKGROUND_UI_SCENE);
			rect1.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			rect1.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
			rect1.setTextureMaterial(this.backgroundTextures.get(this.curBackgroundIndex));

			this.transitionRects.add(rect1);
			this.transitionVels.add(0f);
			this.transitionZ++;
			break;
		}

		case "btn_left_side": {
			this.curBackgroundIndex--;
			this.curBackgroundIndex += this.backgroundTextures.size();
			this.curBackgroundIndex %= this.backgroundTextures.size();

			UIFilledRectangle rect2 = new UIFilledRectangle(Main.windowWidth, 0, this.transitionZ, Main.windowWidth, Main.windowHeight, new FilledRectangle(), BACKGROUND_UI_SCENE);
			rect2.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
			rect2.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
			rect2.setTextureMaterial(this.backgroundTextures.get(this.curBackgroundIndex));

			this.transitionRects.add(rect2);
			this.transitionVels.add(0f);
			this.transitionZ++;
			break;
		}

		case "btn_set_nickname": {
			String newNickname = Input.getText("tf_set_nickname");
			if (newNickname.length() != 0) {
				this.nickname = newNickname;
				((TextField) Input.getInput("tf_set_nickname")).setText("");
			}
			this.client.setNickname(this.nickname);
			this.drawLobbyMain();
			break;
		}

		case "btn_start_game": {
			switch (this.curBackgroundIndex) {
			case 1:
				this.client.startGame(GameServer.CHESS);
				break;

			case 2:
				this.client.startGame(GameServer.SCRABBLE);
				break;

			case 3:
				this.client.startGame(GameServer.BLAZING_EIGHTS);
				break;

			case 4:
				this.client.startGame(GameServer.CRACK_HEADS);
				break;
			}
			break;
		}

		case "btn_back_to_main_menu": {
			this.disconnect();
			if (this.hosting) {
				this.stopHosting();
			}
			break;
		}

		case "btn_disconnect": {
			this.returnToMainMenu();
			break;
		}

		}

	}

	@Override
	public void mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub
	}

	@Override
	public void keyPressed(int key) {

	}

	@Override
	public void keyReleased(int key) {

	}

}
