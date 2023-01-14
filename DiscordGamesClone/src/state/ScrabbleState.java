package state;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;

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
import util.Vec3;

public class ScrabbleState extends State {

	//the server will create an scrabble game based off of the host's specifications, and will communicate
	//all the relevant information to the clients. 

	//when making moves, the clients will send their moves to the server to be validated. If the server validates the move, then
	//it will tell all the clients the move, and who made the move, and then tell who's turn it is next. 

	private static final int BACKGROUND_SCENE = 0;
	private static final int INPUT_SCENE = 1;
	private static final int BOARD_SCENE = 2;
	private static final int TILE_SCENE = 3;

	private Material backgroundColor = new Material(new Vec3(216, 213, 194).mul(1.0f / 255.0f));

	private GameClient client;
	private State mainLobbyState;

	private UIScreen uiScreen;

	public ScrabbleState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm);

		this.client = client;
		this.mainLobbyState = mainLobbyState;
	}

	@Override
	public void load() {
		this.uiScreen = new UIScreen();
		this.uiScreen.setClearColorIDBufferOnRender(false);

		Main.unlockCursor();

		Entity.killAll();

		this.drawMainMenu();
	}

	@Override
	public void kill() {
		this.uiScreen.kill();
	}

	private void drawMainMenu() {
		// -- BACKGROUND --
		this.clearScene(BACKGROUND_SCENE);
		UIFilledRectangle backgroundRect = new UIFilledRectangle(0, 0, -1, Main.windowWidth, Main.windowHeight, BACKGROUND_SCENE);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		backgroundRect.setMaterial(this.backgroundColor);

	}

	@Override
	public void update() {
		Input.inputsHovered(uiScreen.getEntityIDAtMouse());

		if (this.client.getCurGame() == GameServer.LOBBY) {
			this.sm.switchState(this.mainLobbyState);
		}

		Entity.updateEntities();
		Model.updateModels();
	}

	@Override
	public void render(Framebuffer outputBuffer) {
		uiScreen.setUIScene(BACKGROUND_SCENE);
		uiScreen.render(outputBuffer);
	}

	@Override
	public void mousePressed(int button) {
		Input.inputsPressed(uiScreen.getEntityIDAtMouse());
	}

	@Override
	public void mouseReleased(int button) {
		Input.inputsReleased(uiScreen.getEntityIDAtMouse());
		String clickedButton = Input.getClicked();
		switch (clickedButton) {

		}

	}

	@Override
	public void keyPressed(int key) {

	}

	@Override
	public void keyReleased(int key) {

	}

}
