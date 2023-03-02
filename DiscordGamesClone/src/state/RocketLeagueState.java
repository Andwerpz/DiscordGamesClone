package state;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_B;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import client.ClientRocketLeagueInterface;
import client.GameClient;
import graphics.Framebuffer;
import graphics.Material;
import impulse2d.Body;
import impulse2d.Circle;
import input.Button;
import input.Input;
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
import ui.Text;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FontUtils;
import util.Mat4;
import util.Vec2;
import util.Vec3;
import util.Vec4;

public class RocketLeagueState extends GameState {

	// -- IMPORTANT --
	//peg topper assignment
	//boost orbs

	// -- not so important --
	//some sort of effect when a goal is scored?

	private static final int WORLD_SCENE = 0;
	private static final int PARTICLE_SCENE = 1;
	private static final int DECAL_SCENE = 2;

	private static final int UI_BACKGROUND_SCENE = 3;
	private static final int UI_TEXT_SCENE = 4;
	private static final int INPUT_SCENE = 5;

	//hardcoded from the peg model height. use so we can properly align the models that go ontop of the pegs
	private static float pegHeight = 0.3f;

	public static final int PEG_TYPE_BALL = -1;
	public static final int PEG_TYPE_OCTANE = 0;
	public static final int PEG_TYPE_DOMINUS = 1;
	public static final int PEG_TYPE_FENNEC = 2;
	public static final int PEG_TYPE_DINGUS = 3;
	public static final int PEG_TYPE_TANK = 4;
	public static final int PEG_TYPE_SMART_CAR = 5;

	public static final int TEAM_WHITE = 0;
	public static final int TEAM_RED = 1;
	public static final int TEAM_BLUE = 2;

	public static HashMap<Integer, Model> pegTopperModels;

	public static HashMap<Integer, Float> pegTypeMasses = new HashMap<Integer, Float>() {
		{
			put(PEG_TYPE_BALL, 1f);
			put(PEG_TYPE_OCTANE, 1f);
			put(PEG_TYPE_DOMINUS, 1f);
			put(PEG_TYPE_FENNEC, 1f);
			put(PEG_TYPE_DINGUS, 4f);
			put(PEG_TYPE_TANK, 2f);
			put(PEG_TYPE_SMART_CAR, 0.5f);
		}
	};

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

	private PlayerInputController playerController;

	private HashMap<Integer, Long> pegModelInstances;
	private HashMap<Integer, Long> pegTopperModelInstances;
	private HashMap<Long, Long> topperToPeg;

	//these are just for visuals. mostly. 
	private HashMap<Integer, Vec2> batchedLaunches; //pegID, launch force vec
	private HashMap<Integer, Long> batchedLaunchArrowBody;
	private HashMap<Integer, Long> batchedLaunchArrowHead;

	private long hoveredPeg;

	private boolean isInGame = false;

	private UIElement scoreboard;
	private Text redScoreText, blueScoreText, timerText;

	private UIElement startGameRect;

	private long hoveredInput;

	private HashMap<Integer, Long> powerupAddMillis;
	private HashMap<Integer, Long> powerupModelInstances;

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
			pegTopperModels.put(PEG_TYPE_DOMINUS, new Model("/rocket_league/dominus/", "dominus.obj"));
			pegTopperModels.put(PEG_TYPE_FENNEC, new Model("/rocket_league/fennec/", "fennec.obj"));
			pegTopperModels.put(PEG_TYPE_DINGUS, new Model("/rocket_league/dingus/", "dingus.obj"));
			pegTopperModels.put(PEG_TYPE_TANK, new Model("/rocket_league/tank/", "m1_abrams.obj"));
			pegTopperModels.put(PEG_TYPE_SMART_CAR, new Model("/rocket_league/smart_car/", "smart_car.obj"));
		}

		this.pegModel = new Model("/rocket_league/peg/", "peg.obj");

		this.pegModelInstances = new HashMap<>();
		this.pegTopperModelInstances = new HashMap<>();
		this.topperToPeg = new HashMap<>();
		this.pegRings = new HashMap<>();

		this.arrowHeadModel = new Model("/rocket_league/arrow_head/", "arrow_head.obj");
		this.arrowBodyModel = new Model("/rocket_league/arrow_body/", "arrow_body.obj");
		this.flatRingModel = new Model("/rocket_league/flat_ring/", "flat_ring.obj");

		this.batchedLaunches = new HashMap<>();
		this.batchedLaunchArrowBody = new HashMap<>();
		this.batchedLaunchArrowHead = new HashMap<>();

		this.powerupAddMillis = new HashMap<>();
		this.powerupModelInstances = new HashMap<>();

		this.drawUI();
	}

	public void drawUI() {
		this.clearScene(UI_BACKGROUND_SCENE);
		this.clearScene(UI_TEXT_SCENE);
		this.clearScene(INPUT_SCENE);

		//scoreboard
		int scoreboardWidth = 300;
		int scoreboardHeight = 80;

		this.scoreboard = new UIFilledRectangle(0, -scoreboardHeight, 0, scoreboardWidth, scoreboardHeight, UI_BACKGROUND_SCENE);
		this.scoreboard.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.scoreboard.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		this.scoreboard.setMaterial(LobbyState.lightGray);

		UIFilledRectangle blueBackgroundRect = new UIFilledRectangle(0, 0, 0, scoreboardHeight, scoreboardHeight, UI_BACKGROUND_SCENE);
		blueBackgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		blueBackgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		blueBackgroundRect.setMaterial(new Material(Color.BLUE));
		blueBackgroundRect.bind(this.scoreboard);

		UIFilledRectangle redBackgroundRect = new UIFilledRectangle(0, 0, 0, scoreboardHeight, scoreboardHeight, UI_BACKGROUND_SCENE);
		redBackgroundRect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		redBackgroundRect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);
		redBackgroundRect.setMaterial(new Material(Color.RED));
		redBackgroundRect.bind(this.scoreboard);

		this.blueScoreText = new Text(0, 0, "0", FontUtils.ggsans.deriveFont(Font.BOLD), 48, Color.WHITE, UI_TEXT_SCENE);
		this.blueScoreText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
		this.blueScoreText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		this.blueScoreText.bind(blueBackgroundRect);

		this.redScoreText = new Text(0, 0, "0", FontUtils.ggsans.deriveFont(Font.BOLD), 48, Color.WHITE, UI_TEXT_SCENE);
		this.redScoreText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
		this.redScoreText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		this.redScoreText.bind(redBackgroundRect);

		this.timerText = new Text(0, 0, "0.00", FontUtils.ggsans, 32, Color.WHITE, UI_TEXT_SCENE);
		this.timerText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
		this.timerText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		this.timerText.bind(this.scoreboard);

		//start game rect
		this.startGameRect = new UIFilledRectangle(10, 10, 0, 200, 80, UI_BACKGROUND_SCENE);
		this.startGameRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		this.startGameRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		this.startGameRect.setMaterial(LobbyState.gray);

		Button startGameBtn = new Button(0, 0, 190, 70, "btn_start_game", "Start Game", FontUtils.ggsans.deriveFont(Font.BOLD), 32, INPUT_SCENE);
		startGameBtn.setFrameAlignmentStyle(UIElement.FROM_CENTER_RIGHT, UIElement.FROM_CENTER_TOP);
		startGameBtn.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		startGameBtn.bind(this.startGameRect);
	}

	private void updateScoreboard() {
		this.blueScoreText.setText(this.gameInterface.getBlueScore() + "");
		this.redScoreText.setText(this.gameInterface.getRedScore() + "");

		long timeLeftMillis = this.gameInterface.getInputPhaseEndMillis() - System.currentTimeMillis();
		timeLeftMillis = Math.max(timeLeftMillis, 0);
		DecimalFormat df = new DecimalFormat("#.##");
		df.setMinimumFractionDigits(2);
		this.timerText.setText(df.format(timeLeftMillis / 1000.0f));

		this.blueScoreText.setWidth(this.blueScoreText.getTextWidth());
		this.redScoreText.setWidth(this.redScoreText.getTextWidth());
		this.timerText.setWidth(this.timerText.getTextWidth());
	}

	public static int getRandomBasicPegType() {
		int[] basicPegs = new int[] { PEG_TYPE_OCTANE, PEG_TYPE_DOMINUS, PEG_TYPE_FENNEC };
		return basicPegs[(int) (Math.random() * basicPegs.length)];
	}

	public static int getRandomAdvancedPegType() {
		int[] advPegs = new int[] { PEG_TYPE_DINGUS, PEG_TYPE_TANK, PEG_TYPE_SMART_CAR };
		return advPegs[(int) (Math.random() * advPegs.length)];
	}

	@Override
	public void _kill() {
		this.perspectiveScreen.kill();
		this.uiScreen.kill();
	}

	@Override
	public void _update() {
		Input.inputsHovered(this.hoveredInput, INPUT_SCENE);

		Vec2 nextMouse = MouseInput.getMousePos();
		float dx = nextMouse.x - this.mousePos.x;
		float dy = nextMouse.y - this.mousePos.y;
		this.mousePos.set(nextMouse);

		if (this.freeCamera) {
			this.playerController.update();
		}

		// -- NETWORKING --
		if (this.gameInterface.gameStarted()) {
			this.startGameRect.easeYOffset(-100);
			this.scoreboard.easeYOffset(0);
		}
		if (this.gameInterface.gameEnded()) {
			this.startGameRect.easeYOffset(10);
		}
		this.updateScoreboard();

		HashSet<Integer> removedPegs = this.gameInterface.getRemovedPegs();
		for (int pegID : removedPegs) {
			this.removePeg(pegID);
		}

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

		//recolor pegs
		if (this.gameInterface.hasNewTeamAssignment() || addedPegs.size() != 0) {
			this.recolorPegs();
		}

		//update peg type
		ArrayList<Integer> updPegType = this.gameInterface.getUpdPegTypeList();
		for (int pegID : updPegType) {
			//get rid of old topper
			long oldTopperID = this.pegTopperModelInstances.get(pegID);
			Model.removeInstance(oldTopperID);
			this.topperToPeg.remove(oldTopperID);

			//add new topper
			int pegType = this.gameInterface.getPegTypes().get(pegID);
			long topperInstanceID = Model.addInstance(pegTopperModels.get(pegType), Mat4.identity(), WORLD_SCENE);
			this.pegTopperModelInstances.put(pegID, topperInstanceID);
			this.topperToPeg.put(topperInstanceID, this.pegModelInstances.get(pegID));
		}

		//process added powerups
		ArrayList<Integer> addedPowerups = this.gameInterface.getAddedPowerups();
		ArrayList<Integer> removedPowerups = this.gameInterface.getRemovedPowerups();
		for (int powerupID : addedPowerups) {
			this.powerupAddMillis.put(powerupID, System.currentTimeMillis());

			int type = this.gameInterface.getPowerups().get(powerupID).second;

			long modelInstanceID = Model.addInstance(RocketLeagueState.pegTopperModels.get(type), Mat4.identity(), WORLD_SCENE);
			this.powerupModelInstances.put(powerupID, modelInstanceID);
		}
		for (int powerupID : removedPowerups) {
			this.powerupAddMillis.remove(powerupID);
			Model.removeInstance(this.powerupModelInstances.get(powerupID));
			this.powerupModelInstances.remove(powerupID);
		}

		//upd powerup model instances
		for (int powerupID : this.powerupModelInstances.keySet()) {
			float rot = (float) (Math.toRadians(System.currentTimeMillis() - this.powerupAddMillis.get(powerupID))) * 0.07f;
			float y = (float) (Math.cos(rot * 0.723) * 0.2) + 0.4f;
			Vec2 pos = this.gameInterface.getPowerups().get(powerupID).first;

			Mat4 mat4 = Mat4.scale(0.75f);
			mat4.muli(Mat4.rotateY(rot));
			mat4.muli(Mat4.translate(pos.x, y, pos.y));

			Model.updateInstance(this.powerupModelInstances.get(powerupID), mat4);
		}

		if (!this.canDragPegs()) {
			if (this.isDraggingPeg) {
				this.releaseDraggedPeg();
			}
		}

		//get batched launches from server
		HashMap<Integer, Vec2> incomingBatchedLaunches = this.gameInterface.getBatchedLaunches();
		for (int pegID : incomingBatchedLaunches.keySet()) {
			int playerID = this.gameInterface.getPegToPlayer().get(pegID);
			int playerTeam = this.gameInterface.getPlayerTeams().get(playerID);
			if (playerTeam != this.gameInterface.getPlayerTeams().get(this.client.getID())) {
				//can't see intentions of other team
				continue;
			}
			if (!this.batchedLaunches.containsKey(pegID)) {
				this.addBatchedLaunch(pegID);
			}
			this.batchedLaunches.put(pegID, incomingBatchedLaunches.get(pegID));
		}
		HashSet<Integer> removeLaunch = new HashSet<>();
		for (int pegID : this.batchedLaunches.keySet()) {
			if (pegID != this.draggedPegID && !incomingBatchedLaunches.containsKey(pegID)) {
				removeLaunch.add(pegID);
			}
		}
		for (int pegID : removeLaunch) {
			this.removeBatchedLaunch(pegID);
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

		//if dragging, update force vec
		if (this.isDraggingPeg) {
			Vec2 forceVec = this.getLaunchForceVec();
			forceVec.muli(maxLaunchStrength / maxLaunchPx);
			forceVec.setLength(Math.min(maxLaunchStrength, forceVec.length()));
			this.batchedLaunches.put(this.draggedPegID, forceVec);

			this.gameInterface.writeLaunch(this.draggedPegID, forceVec, true);
		}

		for (int pegID : this.batchedLaunchArrowBody.keySet()) {
			Vec2 forceVec = this.batchedLaunches.get(pegID).mul(maxLaunchArrowLen / maxLaunchStrength);

			float rads = (float) Math.atan2(forceVec.y, forceVec.x);
			float length = Math.min(forceVec.length(), maxLaunchArrowLen);
			Vec2 pegPos = this.gameInterface.getPegs().get(pegID).position;

			Mat4 bodyMat4 = Mat4.scale(length, 1, 0.2f);
			bodyMat4.muli(Mat4.translate(1.1f, 0, 0));
			bodyMat4.muli(Mat4.rotateY(rads));
			bodyMat4.muli(Mat4.translate(pegPos.x, pegHeight / 2, pegPos.y));
			Model.updateInstance(this.batchedLaunchArrowBody.get(pegID), bodyMat4);

			Mat4 headMat4 = Mat4.scale(0.4f, 1, 0.4f);
			headMat4.muli(Mat4.translate(1.1f + length * 2, 0, 0));
			headMat4.muli(Mat4.rotateY(rads));
			headMat4.muli(Mat4.translate(pegPos.x, pegHeight / 2, pegPos.y));
			Model.updateInstance(this.batchedLaunchArrowHead.get(pegID), headMat4);
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

		this.uiScreen.setUIScene(UI_TEXT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(INPUT_SCENE);
		this.uiScreen.render(outputBuffer);
		this.hoveredInput = this.uiScreen.getEntityIDAtMouse();
	}

	private void recolorPegs() {
		for (int pegID : this.pegModelInstances.keySet()) {
			if (this.gameInterface.getPegTypes().get(pegID) == PEG_TYPE_BALL) {
				continue;
			}

			int playerID = this.gameInterface.getPegToPlayer().get(pegID);
			int playerTeam = this.gameInterface.getPlayerTeams().get(playerID);
			Material teamColor = null;

			switch (playerTeam) {
			case TEAM_WHITE:
				teamColor = new Material(Color.WHITE);
				break;

			case TEAM_RED:
				teamColor = new Material(Color.RED);
				break;

			case TEAM_BLUE:
				teamColor = new Material(Color.BLUE);
				break;
			}

			Model.updateInstance(this.pegModelInstances.get(pegID), teamColor);
		}
	}

	private boolean canDragPegs() {
		return !this.gameInterface.isInGame() || this.gameInterface.inInputPhase();
	}

	private Vec2 getLaunchForceVec() {
		if (!this.isDraggingPeg) {
			return null;
		}
		Vec2 forceVec = new Vec2(mousePressedPos, MouseInput.getMousePos());
		forceVec.muli(-1f);
		return forceVec;
	}

	private void removePeg(int pegID) {
		long pegInstance = this.pegModelInstances.get(pegID);
		long topperInstance = this.pegTopperModelInstances.get(pegID);

		Model.removeInstance(pegInstance);
		Model.removeInstance(topperInstance);

		this.pegModelInstances.remove(pegID);
		this.pegTopperModelInstances.remove(pegID);

		if (this.pegRings.containsKey(pegID)) {
			Model.removeInstance(this.pegRings.get(pegID));
			this.pegRings.remove(pegID);
		}

		if (this.batchedLaunches.containsKey(pegID)) {
			this.removeBatchedLaunch(pegID);
		}

	}

	private void releaseDraggedPeg() {
		if (this.isDraggingPeg) {
			//notify server that you stopped dragging
			this.gameInterface.writeLaunch(this.draggedPegID, this.batchedLaunches.get(this.draggedPegID), false);
		}
		this.isDraggingPeg = false;
		this.draggedPegID = -1;
	}

	private void addBatchedLaunch(int pegID) {
		if (!this.batchedLaunches.containsKey(pegID)) {
			this.batchedLaunches.put(pegID, new Vec2(0));
		}

		//set up arrow models
		if (!this.batchedLaunchArrowBody.containsKey(pegID)) {
			long bodyID = Model.addInstance(this.arrowBodyModel, Mat4.identity(), WORLD_SCENE);
			Model.updateInstance(bodyID, new Material(Color.YELLOW));
			this.batchedLaunchArrowBody.put(pegID, bodyID);
		}
		if (!this.batchedLaunchArrowHead.containsKey(pegID)) {
			long headID = Model.addInstance(this.arrowHeadModel, Mat4.identity(), WORLD_SCENE);
			Model.updateInstance(headID, new Material(Color.YELLOW));
			this.batchedLaunchArrowHead.put(pegID, headID);
		}
	}

	private void removeBatchedLaunch(int pegID) {
		if (!this.batchedLaunches.containsKey(pegID)) {
			System.err.println("Tried to remove launch that didn't exist");
			return;
		}

		Model.removeInstance(this.batchedLaunchArrowBody.get(pegID));
		Model.removeInstance(this.batchedLaunchArrowHead.get(pegID));

		this.batchedLaunchArrowBody.remove(pegID);
		this.batchedLaunchArrowHead.remove(pegID);
		this.batchedLaunches.remove(pegID);
	}

	@Override
	public void _mousePressed(int button) {
		Input.inputsPressed(this.hoveredInput);

		this.mousePressed = true;
		this.mousePressedPos = MouseInput.getMousePos();

		if (this.canDragPegs()) {
			for (int pegID : this.gameInterface.getPegs().keySet()) {
				long instanceID = this.pegModelInstances.get(pegID);
				if (this.hoveredPeg == instanceID && this.gameInterface.getPegToPlayer().get(pegID) == this.client.getID()) {
					this.isDraggingPeg = true;
					this.draggedPegID = pegID;

					this.addBatchedLaunch(pegID);
					break;
				}
			}
		}

	}

	@Override
	public void _mouseReleased(int button) {
		Input.inputsReleased(this.hoveredInput, INPUT_SCENE);
		switch (Input.getClicked()) {
		case "btn_start_game": {
			this.gameInterface.startGame();
			break;
		}
		}

		this.mousePressed = false;

		this.releaseDraggedPeg();
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
				this.perspectiveScreen.getCamera().setFacing(new Vec3(0f, 0f, -1f).rotateX(1.1082847f));
				this.perspectiveScreen.getCamera().setPos(new Vec3(0, 15.43986f, 10.38977f));
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
