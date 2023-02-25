package state;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;

import client.ClientRocketLeagueInterface;
import client.GameClient;
import graphics.Framebuffer;
import graphics.Material;
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

	private PerspectiveScreen perspectiveScreen;
	private UIScreen uiScreen;

	private ClientRocketLeagueInterface gameInterface;

	private Model soccerFieldModel;

	private boolean freeCamera = false;
	private Vec2 mousePos = new Vec2();

	private PlayerInputController playerController;

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
		Scene.skyboxes.put(WORLD_SCENE, AssetManager.getSkybox("lake_skybox"));

		this.soccerFieldModel = new Model("/rocket_league/soccer_field/", "Soccer Field.obj");
		long soccerFieldID = Model.addInstance(this.soccerFieldModel, Mat4.rotateY((float) Math.toRadians(90)), WORLD_SCENE);
		//Material soccerFieldMaterial = new Material(new Vec4(1), new Vec4(0), 0f);
		//Model.updateInstance(soccerFieldID, soccerFieldMaterial);

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

		if (this.freeCamera) {
			this.playerController.update();
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

		this.uiScreen.setUIScene(UI_BACKGROUND_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(INPUT_SCENE);
		this.uiScreen.render(outputBuffer);
	}

	@Override
	public void _mousePressed(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void _mouseReleased(int button) {
		// TODO Auto-generated method stub

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
