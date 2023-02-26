package state;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import client.ClientRocketLeagueInterface;
import client.GameClient;
import graphics.Framebuffer;
import graphics.Material;
import impulse2d.Body;
import impulse2d.Circle;
import input.MouseInput;
import main.Main;
import model.AssetManager;
import model.Model;
import player.PlayerInputController;
import scene.DirLight;
import scene.Light;
import scene.Scene;
import screen.PerspectiveScreen;
import screen.UIScreen;
import server.PacketListener;
import util.Mat4;
import util.Vec2;
import util.Vec3;
import util.Vec4;

public class RocketLeagueState extends GameState {

	private static final int WORLD_SCENE = 0;
	private static final int PARTICLE_SCENE = 1;
	private static final int DECAL_SCENE = 2;

	private static final int UI_BACKGROUND_SCENE = 3;
	private static final int INPUT_SCENE = 4;

	//hardcoded from the peg model height. use so we can properly align the models that go ontop of the pegs
	private static float pegHeight = 0.15f;

	public static final int PEG_TYPE_BALL = -1;
	public static final int PEG_TYPE_OCTANE = 0;
	public static final int PEG_TYPE_DOMINUS = 1;
	public static final int PEG_TYPE_FENNEC = 2;
	public static final int PEG_TYPE_DINGUS = 3;

	//TODO put in all the densities for each peg type
	public static HashMap<Integer, Model> pegTopperModels;

	private static float maxLaunchStrength = 20;
	private static float maxLaunchPx = 300; //distance in screen pixels to achieve maximum force
	private static float maxLaunchArrowLen = 2; //maximum visual size of the 3d arrow indicator

	private PerspectiveScreen perspectiveScreen;
	private UIScreen uiScreen;

	private ClientRocketLeagueInterface gameInterface;

	private Model soccerFieldModel;
	private Model pegModel;

	private Model arrowBodyModel;
	private Model arrowHeadModel;

	private Model flatRingModel; //drawn around any peg that is owned by the client
	private HashMap<Integer, Long> pegRings;

	private boolean freeCamera = false;
	private Vec2 mousePos = new Vec2();
	private boolean mousePressed = false;
	private Vec2 mousePressedPos = new Vec2();

	private boolean isDraggingPeg = false;
	private int draggedPegID = -1;
	private Vec2 draggingForceVec = new Vec2();

	private long arrowBodyInstance;
	private long arrowHeadInstance;

	private PlayerInputController playerController;

	private HashMap<Integer, Long> pegModelInstances;
	private HashMap<Integer, Long> pegTopperModelInstances;
	private HashMap<Long, Long> topperToPeg;

	private long hoveredPeg;

	public RocketLeagueState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm, client, mainLobbyState);

		this.gameInterface = (ClientRocketLeagueInterface) this.client.getGameInterface();
	}

	@Override
	public void _load() {
		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.renderSkybox(true);
		this.perspectiveScreen.renderParticles(true);
		this.perspectiveScreen.renderPlayermodel(false);

		this.perspectiveScreen.getCamera().setFacing(new Vec3(0f, 0f, -1f).rotateX(1.1082847f));
		this.perspectiveScreen.getCamera().setPos(new Vec3(0, 15.43986f, 10.38977f));
		this.perspectiveScreen.setWorldCameraFOV(75f);

		this.uiScreen = new UIScreen();
		this.uiScreen.setClearColorIDBufferOnRender(false);
		this.uiScreen.setReverseDepthColorID(true);

		this.playerController = new PlayerInputController(new Vec3(0, 10, 10));

		this.clearScene(WORLD_SCENE);
		this.clearScene(PARTICLE_SCENE);
		this.clearScene(DECAL_SCENE);
		Light.addLight(WORLD_SCENE, new DirLight(new Vec3(0.3f, -1f, -0.2f), new Vec3(1f), 0.3f));
		Scene.skyboxes.put(WORLD_SCENE, AssetManager.getSkybox("green_skybox"));

		this.soccerFieldModel = new Model("/rocket_league/soccer_field/", "soccer_field.obj");
		Model.addInstance(this.soccerFieldModel, Mat4.rotateY((float) Math.toRadians(90)), WORLD_SCENE);

		if (pegTopperModels == null) {
			pegTopperModels = new HashMap<>();
			pegTopperModels.put(PEG_TYPE_BALL, new Model("/rocket_league/ball/", "ball.obj"));
			pegTopperModels.put(PEG_TYPE_OCTANE, new Model("/rocket_league/octane/", "octane.obj"));
			pegTopperModels.put(PEG_TYPE_DOMINUS, new Model("/rocket_league/ball/", "ball.obj"));
			pegTopperModels.put(PEG_TYPE_FENNEC, new Model("/rocket_league/octane/", "octane.obj"));
			pegTopperModels.put(PEG_TYPE_DINGUS, new Model("/rocket_league/dingus/", "dingus.obj"));
		}

		this.pegModel = new Model("/rocket_league/peg/", "peg.obj");

		this.pegModelInstances = new HashMap<>();
		this.pegTopperModelInstances = new HashMap<>();
		this.topperToPeg = new HashMap<>();
		this.pegRings = new HashMap<>();

		this.arrowHeadModel = new Model("/rocket_league/arrow_head/", "arrow_head.obj");
		this.arrowBodyModel = new Model("/rocket_league/arrow_body/", "arrow_body.obj");
		this.flatRingModel = new Model("/rocket_league/flat_ring/", "flat_ring.obj");
	}

	@Override
	public void _kill() {
		this.perspectiveScreen.kill();
		this.uiScreen.kill();
	}

	@Override
	public void _update() {
		Vec2 nextMouse = MouseInput.getMousePos();
		float dx = nextMouse.x - this.mousePos.x;
		float dy = nextMouse.y - this.mousePos.y;
		this.mousePos.set(nextMouse);

		//if dragging, draw indicator arrow 
		if (this.isDraggingPeg) {
			Vec2 forceVec = new Vec2(mousePressedPos, MouseInput.getMousePos());
			forceVec.muli(maxLaunchArrowLen / maxLaunchPx);

			float rads = (float) Math.atan2(forceVec.y, forceVec.x);
			float length = Math.min(forceVec.length(), maxLaunchArrowLen);
			Vec2 pegPos = this.gameInterface.getPegs().get(this.draggedPegID).position;

			Mat4 bodyMat4 = Mat4.scale(length, 1, 0.2f);
			bodyMat4.muli(Mat4.translate(1.1f, 0, 0));
			bodyMat4.muli(Mat4.rotateY(rads));
			bodyMat4.muli(Mat4.translate(pegPos.x, pegHeight / 2, pegPos.y));
			Model.updateInstance(this.arrowBodyInstance, bodyMat4);

			Mat4 headMat4 = Mat4.scale(0.4f, 1, 0.4f);
			headMat4.muli(Mat4.translate(1.1f + length * 2, 0, 0));
			headMat4.muli(Mat4.rotateY(rads));
			headMat4.muli(Mat4.translate(pegPos.x, pegHeight / 2, pegPos.y));
			Model.updateInstance(this.arrowHeadInstance, headMat4);
		}

		if (this.freeCamera) {
			this.playerController.update();
		}

		// -- NETWORKING --
		HashSet<Integer> addedPegs = this.gameInterface.getAddedPegs();
		for (int pegID : addedPegs) {
			long instanceID = Model.addInstance(this.pegModel, Mat4.identity(), WORLD_SCENE);
			this.pegModelInstances.put(pegID, instanceID);

			//peg topper
			int pegType = this.gameInterface.getPegTypes().get(pegID);
			long topperInstanceID = Model.addInstance(pegTopperModels.get(pegType), Mat4.identity(), WORLD_SCENE);
			this.pegTopperModelInstances.put(pegID, topperInstanceID);
			this.topperToPeg.put(topperInstanceID, instanceID);

			if (this.gameInterface.getPegToPlayer().get(pegID) == this.client.getID()) {
				//indicator ring
				long ringInstanceID = Model.addInstance(this.flatRingModel, Mat4.identity(), WORLD_SCENE);
				this.pegRings.put(pegID, ringInstanceID);
				Model.updateInstance(ringInstanceID, new Material(Color.YELLOW));
			}
		}

		//update peg positions
		for (int pegID : this.gameInterface.getPegs().keySet()) {
			Body b = this.gameInterface.getPegs().get(pegID);
			long instanceID = this.pegModelInstances.get(pegID);

			float pegZ = 0;

			//peg selection feedback
			if ((this.hoveredPeg == instanceID && this.gameInterface.getPegToPlayer().get(pegID) == this.client.getID()) || (this.isDraggingPeg && this.draggedPegID == pegID)) {
				pegZ = 0.1f;
			}

			Mat4 pegMat4 = Mat4.translate(b.position.x, pegZ, b.position.y);
			Model.updateInstance(instanceID, pegMat4);

			Mat4 topperMat4 = Mat4.rotateY(b.orient);
			topperMat4 = topperMat4.mul(Mat4.translate(b.position.x, pegZ + pegHeight, b.position.y));
			Model.updateInstance(this.pegTopperModelInstances.get(pegID), topperMat4);

			if (this.gameInterface.getPegToPlayer().get(pegID) == this.client.getID()) {
				//indicator ring
				long ringInstanceID = this.pegRings.get(pegID);
				Mat4 ringMat4 = Mat4.translate(b.position.x, pegHeight / 2, b.position.y);
				Model.updateInstance(ringInstanceID, ringMat4);
			}
		}
	}

	@Override
	public void _render(Framebuffer outputBuffer) {
		//update camera pos
		if (this.freeCamera) {
			float cameraXRot = this.playerController.getCamXRot();
			float cameraYRot = this.playerController.getCamYRot();
			this.perspectiveScreen.getCamera().setFacing(new Vec3(0, 0, -1).rotateX(cameraXRot).rotateY(cameraYRot));
			this.perspectiveScreen.getCamera().setPos(this.playerController.getPos());
		}

		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.setParticleScene(PARTICLE_SCENE);
		this.perspectiveScreen.setDecalScene(DECAL_SCENE);
		this.perspectiveScreen.render(outputBuffer);
		this.hoveredPeg = this.perspectiveScreen.getModelIDAtMouse();
		if (this.topperToPeg.containsKey(this.hoveredPeg)) {
			this.hoveredPeg = this.topperToPeg.get(this.hoveredPeg);
		}

		this.uiScreen.setUIScene(UI_BACKGROUND_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(INPUT_SCENE);
		this.uiScreen.render(outputBuffer);
	}

	private void launchPegs() {
		Vec2 forceVec = new Vec2(mousePressedPos, MouseInput.getMousePos());
		forceVec.muli(maxLaunchStrength / maxLaunchPx);

		if (forceVec.lengthSq() > maxLaunchStrength * maxLaunchStrength) {
			forceVec.setLength(maxLaunchStrength);
		}

		this.gameInterface.launchPeg(this.draggedPegID, forceVec);

		//remove arrow indicators
		Model.removeInstance(this.arrowBodyInstance);
		Model.removeInstance(this.arrowHeadInstance);
	}

	@Override
	public void _mousePressed(int button) {
		this.mousePressed = true;
		this.mousePressedPos = MouseInput.getMousePos();

		for (int pegID : this.gameInterface.getPegs().keySet()) {
			long instanceID = this.pegModelInstances.get(pegID);
			if (this.hoveredPeg == instanceID && this.gameInterface.getPegToPlayer().get(pegID) == this.client.getID()) {
				this.isDraggingPeg = true;
				this.draggedPegID = pegID;
				this.draggingForceVec = new Vec2();

				this.arrowBodyInstance = Model.addInstance(this.arrowBodyModel, Mat4.identity(), WORLD_SCENE);
				this.arrowHeadInstance = Model.addInstance(this.arrowHeadModel, Mat4.identity(), WORLD_SCENE);
				Model.updateInstance(this.arrowBodyInstance, new Material(Color.YELLOW));
				Model.updateInstance(this.arrowHeadInstance, new Material(Color.YELLOW));
				break;
			}
		}

	}

	@Override
	public void _mouseReleased(int button) {
		this.mousePressed = false;

		//launch player owned peg in direction. 
		if (this.isDraggingPeg) {
			this.launchPegs();
		}
		this.isDraggingPeg = false;
	}

	@Override
	public void _mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

	}

	@Override
	public void _keyPressed(int key) {
		if (key == GLFW_KEY_C) {
			this.freeCamera = !this.freeCamera;
			if (this.freeCamera) {
				Main.lockCursor();
			}
			else {
				Main.unlockCursor();
			}
		}
		if (key == GLFW_KEY_P) {
			//print out current camera information
			System.out.println(this.playerController.getPos());
			System.out.println(this.playerController.getCamXRot());
			System.out.println(this.playerController.getCamYRot());
		}
	}

	@Override
	public void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
