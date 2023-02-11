package state;

import java.awt.Color;
import java.awt.Font;

import audio.Sound;
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
import ui.Text;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FontUtils;
import util.Mat4;
import util.MathUtils;
import util.Vec3;

public class MainMenuState extends State {

	private static final int BACKGROUND_SCENE = 0; // background, duh

	//this is dumb, i shouldn't have to use another render pass to do this.
	//try fill color using opengl?
	private static final int BACKGROUND_UI_SCENE = 1; //the blue discord background. 

	private static final int STATIC_UI_SCENE = 2; // for unchanging parts of the ui like the logo
	private static final int DYNAMIC_UI_SCENE = 3; // inputs and stuff

	private PerspectiveScreen perspectiveScreen; // 3D background
	private UIScreen uiScreen; // Menu UI

	private Sound menuMusic;

	private long startTime;

	private Vec3 lightGray = new Vec3(66, 69, 73).mul(1.0f / 255.0f);
	private Vec3 lightBlue = new Vec3(114, 137, 218).mul(1.0f / 255.0f);

	private float sideButtonBaseOffset = 30;
	private float sideButtonMaxOffset = 150;
	private int sideButtonWidth = (int) sideButtonMaxOffset;

	private UIFilledRectangle logoRect;

	public static String versionNumber = "v0.3.0";

	public MainMenuState(StateManager sm) {
		super(sm);

		this.startTime = System.currentTimeMillis();
	}

	@Override
	public void load() {
		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.getCamera().setVerticalFOV((float) Math.toRadians(70f));
		this.perspectiveScreen.getCamera().setPos(new Vec3(18.417412f, 1.7f, -29.812654f));

		this.uiScreen = new UIScreen();

		Main.unlockCursor();

		Entity.killAll();

		// -- BACKGROUND --
		this.clearScene(BACKGROUND_SCENE);
		Model.addInstance(AssetManager.getModel("dust2"), Mat4.rotateX((float) Math.toRadians(90)).mul(Mat4.scale((float) 0.05)), BACKGROUND_SCENE);
		Light.addLight(BACKGROUND_SCENE, new DirLight(new Vec3(0.3f, -1f, -0.5f), new Vec3(0.5f), 0.3f));
		Scene.skyboxes.put(BACKGROUND_SCENE, AssetManager.getSkybox("stars_skybox"));

		this.drawMainMenu();

		menuMusic = new Sound("main_menu_music.ogg", true);
		int menuMusicID = menuMusic.addSource();
		Sound.setRelativePosition(menuMusicID, new Vec3(0));
		Sound.setGain(menuMusicID, 0.3f);
	}

	@Override
	public void kill() {
		this.perspectiveScreen.kill();
		this.uiScreen.kill();

		this.menuMusic.kill();
	}

	private void drawMainMenu() {
		// -- UI BACKGROUND --
		this.clearScene(BACKGROUND_UI_SCENE);
		UIFilledRectangle background = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, BACKGROUND_UI_SCENE);
		background.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		background.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		background.setMaterial(new Material(this.lightGray));

		// -- STATIC UI --
		this.clearScene(STATIC_UI_SCENE);
		this.logoRect = new UIFilledRectangle(120, 50, 0, 560, 80, new FilledRectangle(), STATIC_UI_SCENE);
		this.logoRect.setTextureMaterial(new TextureMaterial(new Texture("/discord_logo.png", Texture.VERTICAL_FLIP_BIT)));
		this.logoRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
		this.logoRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		// -- DYNAMIC UI --
		this.clearScene(DYNAMIC_UI_SCENE);
		Button hostGame = new Button(150, 0, 200, 30, "btn_host_game", "Host Game", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		hostGame.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
		hostGame.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		Button joinGame = new Button(150, 40, 200, 30, "btn_join_game", "Join Game", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		joinGame.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
		joinGame.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		//TODO fix this menu
		//		Button settings = new Button(150, 80, 200, 30, "btn_settings", "Settings", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		//		settings.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
		//		settings.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		Button quitGame = new Button(150, 80, 200, 30, "btn_quit_game", "Quit Game", FontUtils.ggsans.deriveFont(Font.BOLD), 24, DYNAMIC_UI_SCENE);
		quitGame.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
		quitGame.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		TextField tfHostPort = new TextField(360, 0, 180, 30, "tf_host_port", "Port", FontUtils.ggsans, 16, DYNAMIC_UI_SCENE);
		tfHostPort.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
		tfHostPort.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		TextField tfJoinIP = new TextField(360, 40, 180, 30, "tf_join_ip", "IP", FontUtils.ggsans, 16, DYNAMIC_UI_SCENE);
		tfJoinIP.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
		tfJoinIP.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		TextField tfJoinPort = new TextField(550, 40, 180, 30, "tf_join_port", "Port", FontUtils.ggsans, 16, DYNAMIC_UI_SCENE);
		tfJoinPort.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_BOTTOM);
		tfJoinPort.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		Button rightSideButton = new Button((int) this.sideButtonBaseOffset, 0, this.sideButtonWidth, Main.windowHeight, "btn_side", ">", FontUtils.ggsans.deriveFont(Font.BOLD), 48, DYNAMIC_UI_SCENE);
		rightSideButton.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
		rightSideButton.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		Text versionText = new Text(10, 10, MainMenuState.versionNumber, FontUtils.ggsans, 16, Color.WHITE, DYNAMIC_UI_SCENE);
		versionText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		versionText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
	}

	private void drawSettingsMenu() {
		// -- DYNAMIC UI --
		this.clearScene(DYNAMIC_UI_SCENE);
		Button settingsExit = new Button(100, 50, 200, 30, "btn_settings_exit", "Back", FontUtils.ggsans, 32, DYNAMIC_UI_SCENE);
		settingsExit.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		settingsExit.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);

		Button settingsToggleFullscreen = new Button(100, 100, 400, 30, "btn_settings_toggle_fullscreen", "Toggle Fullscreen", FontUtils.ggsans, 32, DYNAMIC_UI_SCENE);
		settingsToggleFullscreen.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		settingsToggleFullscreen.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);

		// -- STATIC UI --
		this.clearScene(STATIC_UI_SCENE);
	}

	@Override
	public void update() {
		Input.inputsHovered(uiScreen.getEntityIDAtMouse(), DYNAMIC_UI_SCENE);

		this.logoRect.setRotationRads(this.logoRect.getRotationRads() + (float) Math.toRadians(1));

		if (Input.getInput("btn_side") != null) {
			Button sideButton = (Button) Input.getInput("btn_side");
			if (sideButton.hasMouseEntered()) {
				sideButton.easeXOffset(this.sideButtonMaxOffset);
			}
			if (sideButton.hasMouseExited()) {
				sideButton.easeXOffset(this.sideButtonBaseOffset);
			}
		}
	}

	@Override
	public void render(Framebuffer outputBuffer) {
		perspectiveScreen.renderSkybox(true);
		perspectiveScreen.renderDecals(false);
		perspectiveScreen.renderPlayermodel(false);
		perspectiveScreen.setWorldScene(BACKGROUND_SCENE);
		perspectiveScreen.render(outputBuffer);

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
		case "btn_host_game":
			try {
				int port = Integer.parseInt(Input.getText("tf_host_port"));
				this.sm.switchState(new LobbyState(this.sm, null, port, true));
			}
			catch (NumberFormatException e) {
				System.err.println("BAD PORT");
			}
			break;

		case "btn_join_game":
			try {
				int port = Integer.parseInt(Input.getText("tf_join_port"));
				String ip = Input.getText("tf_join_ip");
				this.sm.switchState(new LobbyState(this.sm, ip, port, false));
			}
			catch (NumberFormatException e) {
				System.err.println("BAD PORT");
			}
			break;

		case "btn_quit_game":
			Main.main.exit();
			break;

		case "btn_settings":
			this.drawSettingsMenu();
			break;

		case "btn_settings_exit":
			this.drawMainMenu();
			break;

		case "btn_settings_toggle_fullscreen":
			Main.main.toggleFullscreen();
			break;

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
