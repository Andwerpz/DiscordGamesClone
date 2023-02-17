package state;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import java.awt.Font;

import client.ClientGameInterface;
import client.GameClient;
import entity.Entity;
import graphics.Framebuffer;
import graphics.Material;
import input.Button;
import input.Input;
import main.Main;
import screen.UIScreen;
import server.GameServer;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FontUtils;
import util.Vec4;

public abstract class GameState extends State {

	//this class is the base class for all of the games. 
	//so you can press esc and access some sort of pause menu to leave the current lobby. 

	private static final int PAUSE_BACKGROUND_SCENE = -100;
	private static final int PAUSE_SCENE = -101;

	protected GameClient client;
	private State mainLobbyState;

	private UIScreen pauseMenuScreen;

	private UIFilledRectangle pauseRect;

	protected boolean pauseMenuActive = false;

	public GameState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm);

		this.client = client;
		this.mainLobbyState = mainLobbyState;
	}

	@Override
	public void load() {
		Main.unlockCursor();
		Entity.killAll();

		this.pauseMenuScreen = new UIScreen();
		this.pauseMenuScreen.setClearColorIDBufferOnRender(false);
		this.pauseMenuScreen.setReverseDepthColorID(true);

		this.drawPauseMenu();

		this._load();
	}

	public abstract void _load();

	private void drawPauseMenu() {
		this.clearScene(PAUSE_BACKGROUND_SCENE);
		this.clearScene(PAUSE_SCENE);

		this.pauseRect = new UIFilledRectangle(0, -Main.windowHeight / 2, 0, 500, 300, PAUSE_BACKGROUND_SCENE);
		this.pauseRect.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.pauseRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		this.pauseRect.setMaterial(new Material(new Vec4(0f, 0f, 0f, 0.3f)));
		this.pauseRect.setEasingStyle(UIElement.EASE_OUT_QUAD);
		this.pauseRect.setEasingDurationMillis(300);

		if (!this.client.isHost()) {
			Button mainMenuBtn = new Button(0, 0, 300, 50, "btn_return_to_main_menu", "Return to Main Menu", FontUtils.ggsans.deriveFont(Font.BOLD), 24, PAUSE_SCENE);
			mainMenuBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
			mainMenuBtn.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			mainMenuBtn.bind(this.pauseRect);
		}

		if (this.client.isHost()) {
			Button lobbyBtn = new Button(0, 0, 300, 50, "btn_return_to_lobby", "Return Party to Lobby", FontUtils.ggsans.deriveFont(Font.BOLD), 24, PAUSE_SCENE);
			lobbyBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
			lobbyBtn.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			lobbyBtn.bind(this.pauseRect);
		}
	}

	@Override
	public void kill() {
		this.pauseMenuScreen.kill();
		this._kill();
	}

	public abstract void _kill();

	@Override
	public void update() {
		Input.inputsHovered(this.pauseMenuScreen.getEntityIDAtMouse(), PAUSE_SCENE);
		if (this.client.getCurGame() == GameServer.LOBBY) {
			this.sm.switchState(this.mainLobbyState);
		}

		this._update();
	}

	public abstract void _update();

	@Override
	public void render(Framebuffer outputBuffer) {
		this._render(outputBuffer);

		this.pauseMenuScreen.setUIScene(PAUSE_BACKGROUND_SCENE);
		this.pauseMenuScreen.render(outputBuffer);
		this.pauseMenuScreen.setUIScene(PAUSE_SCENE);
		this.pauseMenuScreen.render(outputBuffer);
	}

	public abstract void _render(Framebuffer outputBuffer);

	private void returnToLobby() {
		this.client.returnToMainLobby();
	}

	private void returnToMainMenu() {
		this.mainLobbyState.kill();
		this.client.disconnect();
		this.client.exit();

		this.sm.switchState(new MainMenuState(this.sm));
	}

	@Override
	public void mousePressed(int button) {
		Input.inputsPressed(this.pauseMenuScreen.getEntityIDAtMouse());

		if (!this.pauseMenuActive) {
			this._mousePressed(button);
		}

	}

	@Override
	public void mouseReleased(int button) {
		Input.inputsReleased(this.pauseMenuScreen.getEntityIDAtMouse(), PAUSE_SCENE);
		switch (Input.getClicked()) {
		case "btn_return_to_main_menu": {
			this.returnToMainMenu();
			break;
		}

		case "btn_return_to_lobby": {
			this.returnToLobby();
			break;
		}
		}

		if (!this.pauseMenuActive) {
			this._mouseReleased(button);
		}

	}

	@Override
	public void mouseScrolled(float wheelOffset, float smoothOffset) {
		if (!this.pauseMenuActive) {
			this._mouseScrolled(wheelOffset, smoothOffset);
		}

	}

	@Override
	public void keyPressed(int key) {
		switch (key) {
		case GLFW_KEY_ESCAPE: {
			if (!this.pauseMenuActive) {
				this.pauseRect.easeYOffset(Main.windowHeight / 2f);
			}
			else {
				this.pauseRect.easeYOffset(-Main.windowHeight / 2f);
			}
			this.pauseMenuActive = !this.pauseMenuActive;
			break;
		}
		}

		if (!this.pauseMenuActive) {
			this._keyPressed(key);
		}
	}

	@Override
	public void keyReleased(int key) {
		if (!this.pauseMenuActive) {
			this._keyReleased(key);
		}
	}

	public abstract void _mousePressed(int button);

	public abstract void _mouseReleased(int button);

	public abstract void _mouseScrolled(float wheelOffset, float smoothOffset);

	public abstract void _keyPressed(int key);

	public abstract void _keyReleased(int key);

}
