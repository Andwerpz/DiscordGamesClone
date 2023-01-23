package state;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL46.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

import entity.Entity;
import graphics.Framebuffer;
import graphics.Material;
import graphics.Texture;
import graphics.TextureMaterial;
import input.Input;
import main.Main;
import model.FilledRectangle;
import model.Model;
import screen.UIScreen;
import server.GameClient;
import server.GameServer;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FileUtils;
import util.GraphicsTools;
import util.Vec2;
import util.Vec3;

public class BlazingEightsState extends State {

	private static final int BACKGROUND_SCENE = 0;
	private static final int INPUT_SCENE = 1;

	private static final int HAND_SCENE = 2;

	private UIScreen uiScreen;

	private GameClient client;
	private State mainLobbyState;

	private static final int SUIT_DIAMOND = 0;
	private static final int SUIT_CLUB = 1;
	private static final int SUIT_HEART = 2;
	private static final int SUIT_SPADE = 3;

	private static final int VALUE_ACE = 0;
	private static final int VALUE_TWO = 1;
	private static final int VALUE_THREE = 2;
	private static final int VALUE_FOUR = 3;
	private static final int VALUE_FIVE = 4;
	private static final int VALUE_SIX = 5;
	private static final int VALUE_SEVEN = 6;
	private static final int VALUE_EIGHT = 7;
	private static final int VALUE_NINE = 8;
	private static final int VALUE_TEN = 9;
	private static final int VALUE_SKIP = 10;
	private static final int VALUE_REVERSE = 11;
	private static final int VALUE_ADDTWO = 12;

	private static final int NR_SUITS = 4;
	private static final int NR_VALUES = 13;
	private static final int NR_CARDS = NR_SUITS * NR_VALUES;

	//we'll use these rects to generate the uifilled rectangles
	//using only one rect for a given card lets us do instanced rendering 
	private HashMap<Integer, FilledRectangle> cardFrontRects;
	private ArrayList<FilledRectangle> cardBackRects;

	private int cardWidthPx = 66 * 3;
	private int cardHeightPx = 90 * 3;

	//one (small) problem is card layering. 
	//should be doable :shrug:
	//it helps that the cards aren't translucent

	private int cardCnt = 0;
	private long prevTime;

	private float cardFrictionMagnitude = 1f;
	private float cardAngularFrictionMagnitude = (float) Math.toRadians(0.2);

	private HashMap<Long, UIFilledRectangle> cardRects;

	//we'll eliminate these when the magnitude drops below a certain point
	private HashMap<Long, Vec2> cardVels;
	private HashMap<Long, Float> cardAngularVels;

	public BlazingEightsState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm);

		this.client = client;
		this.mainLobbyState = mainLobbyState;
	}

	@Override
	public void load() {
		Entity.killAll();
		Main.unlockCursor();

		this.uiScreen = new UIScreen();
		this.uiScreen.setClearColorIDBufferOnRender(false);

		this.cardRects = new HashMap<>();
		this.cardVels = new HashMap<>();
		this.cardAngularVels = new HashMap<>();

		this.cardFrontRects = new HashMap<>();
		this.cardBackRects = new ArrayList<>();

		int cardTextureWidth = 66;
		int cardTextureHeight = 90;

		ArrayList<BufferedImage> cardFronts = GraphicsTools.loadAnimation("/blazing_eights/blazing_eights_cards_front_fixed2.png", cardTextureWidth, cardTextureHeight);
		ArrayList<BufferedImage> cardBacks = GraphicsTools.loadAnimation("/blazing_eights/blazing_eights_cards_back.png", cardTextureWidth, cardTextureHeight);

		for (int i = 0; i < NR_CARDS; i++) {
			FilledRectangle cardFrontRect = new FilledRectangle();
			Texture texture = new Texture(cardFronts.get(i), Texture.VERTICAL_FLIP_BIT, GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST);
			cardFrontRect.setTextureMaterial(new TextureMaterial(texture));
			this.cardFrontRects.put(i, cardFrontRect);
		}

		for (int i = 0; i < cardBacks.size(); i++) {
			FilledRectangle cardBackRect = new FilledRectangle();
			Texture texture = new Texture(cardBacks.get(i), Texture.VERTICAL_FLIP_BIT, GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST);
			cardBackRect.setTextureMaterial(new TextureMaterial(texture));
			this.cardBackRects.add(cardBackRect);
		}

		this.clearScene(BACKGROUND_SCENE);
		this.clearScene(HAND_SCENE);
		this.clearScene(INPUT_SCENE);

		UIFilledRectangle testRect = new UIFilledRectangle(0, 0, 0, Main.windowWidth, Main.windowHeight, BACKGROUND_SCENE);
		testRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		testRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		testRect.setMaterial(LobbyState.lightGray);

		UIElement.alignAllUIElements();
	}

	//suit major card sorting. 
	public static int getCardID(int suit, int value) {
		return suit * NR_VALUES + value;
	}

	public static int[] getCardSuitAndValue(int id) {
		return new int[] { id / NR_VALUES, id % NR_VALUES };
	}

	public static int generateRandomCardID() {
		return (int) (Math.random() * NR_CARDS);
	}

	private UIFilledRectangle generateCardFront(int id, int scene) {
		FilledRectangle frect = this.cardFrontRects.get(id);
		UIFilledRectangle rect = new UIFilledRectangle(0, 0, 1, this.cardWidthPx, this.cardHeightPx, frect, scene);
		rect.setMaterial(new Material(Color.WHITE));
		rect.setClampAlignedCoordinatesToInt(false);

		this.cardRects.put(rect.getID(), rect);

		return rect;
	}

	private UIFilledRectangle generateCardBack(int scene) {
		FilledRectangle frect = this.cardBackRects.get((int) (Math.random() * this.cardBackRects.size()));
		UIFilledRectangle rect = new UIFilledRectangle(0, 0, 1, this.cardWidthPx, this.cardHeightPx, frect, scene);
		rect.setMaterial(new Material(Color.WHITE));
		rect.setClampAlignedCoordinatesToInt(false);

		this.cardRects.put(rect.getID(), rect);

		return rect;
	}

	private void removeCard(long cardID) {
		UIFilledRectangle cardRect = this.cardRects.get(cardID);

		this.cardRects.remove(cardID);
		this.cardVels.remove(cardID);

		cardRect.kill();
	}

	private Vec2 calculateInitialVel(Vec2 start, Vec2 end, float frictionMagnitude) {
		float mag = (float) (Math.sqrt(end.sub(start).length() * frictionMagnitude * 2) - (frictionMagnitude / 2f));
		Vec2 ans = new Vec2(start, end).setLength(mag);
		return ans;
	}

	@Override
	public void kill() {
		this.uiScreen.kill();
	}

	@Override
	public void update() {
		Input.inputsHovered(uiScreen.getEntityIDAtMouse());

		// -- NETWORKING --
		if (this.client.getCurGame() == GameServer.LOBBY) {
			this.sm.switchState(this.mainLobbyState);
		}

		if (System.currentTimeMillis() - prevTime > 1000 && cardCnt < 300) {
			UIFilledRectangle rect = this.generateCardFront(cardCnt % NR_CARDS, HAND_SCENE);
			rect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			rect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
			rect.setFrameAlignmentOffset(0, 0);
			rect.align();

			Vec2 cur = new Vec2(0, 0);
			Vec2 target = new Vec2(Main.windowWidth / 2, Main.windowHeight / 2);

			Vec2 offset = new Vec2(0, Math.random() * 300);
			offset.rotate((float) Math.toRadians(Math.random() * 360));
			target.addi(offset);

			Vec2 initialVel = this.calculateInitialVel(cur, target, this.cardFrictionMagnitude);

			float angularVel = (float) Math.toRadians(Math.random() * 4 + 8);
			if (Math.random() > 0.5) {
				angularVel *= -1;
			}

			this.cardVels.put(rect.getID(), initialVel);
			this.cardAngularVels.put(rect.getID(), angularVel);

			cardCnt++;
			prevTime = System.currentTimeMillis();
		}

		// -- ANIMATIONS --
		ArrayList<Long> doneMoving = new ArrayList<>();
		for (long id : this.cardVels.keySet()) {
			UIFilledRectangle cardRect = this.cardRects.get(id);
			Vec2 vel = this.cardVels.get(id);

			cardRect.setFrameAlignmentOffset(cardRect.getXOffset() + vel.x, cardRect.getYOffset() + vel.y);
			cardRect.align();

			Vec2 friction = new Vec2(vel).setLength(-this.cardFrictionMagnitude);
			vel.addi(friction);

			if (vel.dot(friction) > 0) {
				doneMoving.add(id);
			}
		}
		for (long id : doneMoving) {
			this.cardVels.remove(id);
		}

		doneMoving.clear();
		for (long id : this.cardAngularVels.keySet()) {
			UIFilledRectangle cardRect = this.cardRects.get(id);
			float vel = this.cardAngularVels.get(id);
			float friction = this.cardAngularFrictionMagnitude * (vel < 0 ? 1 : -1);

			cardRect.setRotationRads(cardRect.getRotationRads() + vel);
			cardRect.align();

			vel += friction;
			this.cardAngularVels.put(id, vel);

			if (vel * friction > 0) {
				doneMoving.add(id);
			}
		}
		for (long id : doneMoving) {
			this.cardAngularVels.remove(id);
		}
	}

	@Override
	public void render(Framebuffer outputBuffer) {
		this.uiScreen.setUIScene(BACKGROUND_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(HAND_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(INPUT_SCENE);
		this.uiScreen.render(outputBuffer);
	}

	@Override
	public void mousePressed(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseReleased(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyPressed(int key) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
