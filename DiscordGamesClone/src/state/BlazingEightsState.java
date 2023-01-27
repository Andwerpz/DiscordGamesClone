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
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import entity.Entity;
import graphics.Framebuffer;
import graphics.Material;
import graphics.Texture;
import graphics.TextureMaterial;
import input.Button;
import input.Input;
import main.Main;
import model.FilledRectangle;
import model.Model;
import screen.UIScreen;
import server.GameClient;
import server.GameServer;
import ui.Text;
import ui.UIElement;
import ui.UIFilledRectangle;
import util.FileUtils;
import util.FontUtils;
import util.GraphicsTools;
import util.Vec2;
import util.Vec3;

public class BlazingEightsState extends State {

	private static final int BACKGROUND_SCENE = 0;
	private static final int INPUT_SCENE = 1;

	private static final int CARD_SCENE = 2;

	private static final int HUD_SCENE = 3;
	private static final int HUD_TEXT_SCENE = 4;

	private static final int MOVE_INDICATOR_SCENE = 5;

	private static final int LOGO_SCENE = 6;

	private UIScreen uiScreen;

	private GameClient client;
	private State mainLobbyState;

	private static final int SUIT_DIAMOND = 0;
	private static final int SUIT_CLUB = 1;
	private static final int SUIT_HEART = 2;
	private static final int SUIT_SPADE = 3;

	public static final int VALUE_ACE = 0;
	public static final int VALUE_TWO = 1;
	public static final int VALUE_THREE = 2;
	public static final int VALUE_FOUR = 3;
	public static final int VALUE_FIVE = 4;
	public static final int VALUE_SIX = 5;
	public static final int VALUE_SEVEN = 6;
	public static final int VALUE_EIGHT = 7;
	public static final int VALUE_NINE = 8;
	public static final int VALUE_TEN = 9;
	public static final int VALUE_SKIP = 10;
	public static final int VALUE_REVERSE = 11;
	public static final int VALUE_ADDTWO = 12;

	private static final int NR_SUITS = 4;
	private static final int NR_VALUES = 13;
	private static final int NR_CARDS = NR_SUITS * NR_VALUES;

	private static final float ZOFFSET_TABLE = -100;
	private static final float ZOFFSET_DECK = -200;
	private static final float ZOFFSET_HAND = 100;

	private static final float ZOFFSET_OPPONENT_HAND = 0;

	public static final int MOVE_PLAY = 0;
	public static final int MOVE_DRAW = 1;

	//we'll use these rects to generate the uifilled rectangles
	//using only one rect for a given card lets us do instanced rendering 
	private HashMap<Integer, FilledRectangle> cardFrontRects;
	private ArrayList<FilledRectangle> cardBackRects;

	private int cardWidthPx = 66 * 2;
	private int cardHeightPx = 90 * 2;

	//one (small) problem is card layering. 
	//should be doable :shrug:
	//it helps that the cards aren't translucent

	private float cardFrictionMagnitude = 1f;
	private float cardAngularFrictionMagnitude = (float) Math.toRadians(0.2);

	private HashMap<Long, UIFilledRectangle> cardRects;
	private HashMap<Long, Integer> cardTypes;

	//we'll eliminate these when the magnitude drops below a certain point
	private HashMap<Long, Vec2> cardVels;
	private HashMap<Long, Float> cardAngularVels;

	private HashSet<Long> tableCards;
	private TreeSet<Long> handCards;
	private HashSet<Long> opponentDrawnCards;

	private UIFilledRectangle deckCover;

	private long hoveredCardID;

	private boolean isInGame = false;

	private TextureMaterial cardIconTexture;
	private FilledRectangle cardIconRect;

	private int opponentHUDYInterval;

	private int cardsToPlayerRemaining, cardsToOpponentRemaining;
	private int topCardType = -1;

	private boolean prevMoveWasDraw = false;

	private HashMap<Integer, UIFilledRectangle> playerNameRects;
	private Text playerMoveIndicator;

	private UIFilledRectangle logoRect;

	private long startTime;

	public BlazingEightsState(StateManager sm, GameClient client, State mainLobbyState) {
		super(sm);

		this.client = client;
		this.mainLobbyState = mainLobbyState;

		this.startTime = System.currentTimeMillis();
	}

	@Override
	public void load() {
		Entity.killAll();
		Main.unlockCursor();

		this.uiScreen = new UIScreen();
		this.uiScreen.setClearColorIDBufferOnRender(true);

		this.cardRects = new HashMap<>();
		this.cardTypes = new HashMap<>();

		this.cardVels = new HashMap<>();
		this.cardAngularVels = new HashMap<>();

		this.tableCards = new HashSet<Long>();
		this.handCards = new TreeSet<Long>((a, b) -> {
			if (!this.cardTypes.containsKey(a) || !this.cardTypes.containsKey(b)) {
				return -1;
			}
			int aType = this.cardTypes.get(a);
			int bType = this.cardTypes.get(b);
			if (aType == bType) {
				return Long.compare(a, b);
			}
			int[] asv = getCardSuitAndValue(aType);
			int[] bsv = getCardSuitAndValue(bType);
			if (asv[0] == bsv[0]) {
				return Integer.compare(asv[1], bsv[1]);
			}
			return Integer.compare(asv[0], bsv[0]);
		});
		this.opponentDrawnCards = new HashSet<Long>();

		this.cardFrontRects = new HashMap<>();
		this.cardBackRects = new ArrayList<>();

		int cardTextureWidth = 66;
		int cardTextureHeight = 90;

		ArrayList<BufferedImage> cardFronts = GraphicsTools.loadAnimation("/blazing_eights/blazing_eights_cards_front_fixed5.png", cardTextureWidth, cardTextureHeight);
		ArrayList<BufferedImage> cardBacks = GraphicsTools.loadAnimation("/blazing_eights/blazing_eights_cards_back_fixed.png", cardTextureWidth, cardTextureHeight);

		for (int i = 0; i < NR_CARDS; i++) {
			FilledRectangle cardFrontRect = new FilledRectangle();
			Texture texture = new Texture(cardFronts.get(i), Texture.VERTICAL_FLIP_BIT, GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST);
			cardFrontRect.setTextureMaterial(new TextureMaterial(texture));
			this.cardFrontRects.put(i / NR_SUITS + (i % NR_SUITS) * NR_VALUES, cardFrontRect);
		}

		for (int i = 0; i < cardBacks.size(); i++) {
			FilledRectangle cardBackRect = new FilledRectangle();
			Texture texture = new Texture(cardBacks.get(i), Texture.VERTICAL_FLIP_BIT, GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST);
			cardBackRect.setTextureMaterial(new TextureMaterial(texture));
			this.cardBackRects.add(cardBackRect);
		}

		this.cardIconTexture = new TextureMaterial(new Texture("/blazing_eights/card_small_icon_fixed.png", 0, GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST));
		this.cardIconRect = new FilledRectangle();
		this.cardIconRect.setTextureMaterial(this.cardIconTexture);

		this.clearScene(BACKGROUND_SCENE);
		this.clearScene(CARD_SCENE);
		this.clearScene(MOVE_INDICATOR_SCENE);
		this.clearScene(LOGO_SCENE);

		UIFilledRectangle backgroundRect = new UIFilledRectangle(0, 0, 0, Main.windowWidth, Main.windowHeight, BACKGROUND_SCENE);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);
		backgroundRect.setMaterial(LobbyState.lightGray);

		this.logoRect = new UIFilledRectangle(100, 0, 0, 400, 250, new FilledRectangle(), LOGO_SCENE);
		this.logoRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.logoRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.logoRect.setClampAlignedCoordinatesToInt(false);
		this.logoRect.setTextureMaterial(new TextureMaterial(new Texture("/blazing_eights/blazing_eights_logo.png", Texture.VERTICAL_FLIP_BIT)));

		this.cardsToPlayerRemaining = 7;

		this.drawHUD();
		this.drawInputs();
		this.replaceDeckCover();

		UIElement.alignAllUIElements();
	}

	private void drawInputs() {
		this.clearScene(INPUT_SCENE);

		if (!this.isInGame) {
			if (this.client.isHost()) {
				Button startGameBtn = new Button(20, 20, 200, 80, "btn_start_game", "Start Game", FontUtils.ggsans.deriveFont(Font.BOLD), 36, INPUT_SCENE);
				startGameBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
				startGameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);

				Button leaveGameBtn = new Button(20, 20 + 80 + 20, 200, 80, "btn_leave_game", "Leave Game", FontUtils.ggsans.deriveFont(Font.BOLD), 36, INPUT_SCENE);
				leaveGameBtn.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
				leaveGameBtn.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
			}
		}
	}

	private void drawHUD() {
		this.clearScene(HUD_SCENE);
		this.clearScene(HUD_TEXT_SCENE);

		if (this.isInGame) {
			this.playerNameRects = new HashMap<>();

			// -- OPPONENT HAND VIEWER --
			UIFilledRectangle opponentHandBackground = new UIFilledRectangle(0, 10, 0, 250, 0, HUD_SCENE);
			opponentHandBackground.setFillHeight(true);
			opponentHandBackground.setFillHeightMargin(10);
			opponentHandBackground.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_BOTTOM);
			opponentHandBackground.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_BOTTOM);
			opponentHandBackground.setMaterial(LobbyState.lightGray);

			this.opponentHUDYInterval = 72;

			ArrayList<Integer> playerMoveOrder = this.client.blazingEightsGetMoveOrder();
			HashMap<Integer, Integer> playerNumCards = this.client.blazingEightsGetCardAmt();

			for (int i = 0; i < playerMoveOrder.size(); i++) {
				int id = playerMoveOrder.get(i);
				String nick = this.client.getPlayers().get(id);
				int numCards = playerNumCards.get(id);

				UIFilledRectangle nickBackground = new UIFilledRectangle(0, this.opponentHUDYInterval * i, 0, 0, 32, HUD_SCENE);
				nickBackground.setFillWidth(true);
				nickBackground.setFillWidthMargin(0);
				nickBackground.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
				nickBackground.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
				nickBackground.setMaterial(LobbyState.gray);
				nickBackground.bind(opponentHandBackground);

				this.playerNameRects.put(id, nickBackground);

				Text nickText = new Text(5, 0, nick, FontUtils.ggsans.deriveFont(Font.BOLD), 24, Color.WHITE, HUD_TEXT_SCENE);
				nickText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
				nickText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
				nickText.bind(nickBackground);

				int iconXInterval = 10;
				int iconWidth = 11 * 2;
				int iconHeight = 15 * 2;

				for (int j = 0; j < numCards; j++) {
					UIFilledRectangle iconRect = new UIFilledRectangle(5 + iconXInterval * j, this.opponentHUDYInterval * i + nickBackground.getHeight() + 5, 0, iconWidth, iconHeight, this.cardIconRect, HUD_SCENE);
					iconRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
					iconRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
					iconRect.setMaterial(new Material(Color.WHITE));
					iconRect.bind(opponentHandBackground);
					iconRect.setZ(iconRect.getZ() + 0.01f * j);
				}
			}
		}

		UIElement.alignAllUIElements();
	}

	//suit major card sorting. 
	public static int getCardID(int suit, int value) {
		return suit * NR_VALUES + value;
	}

	public static int[] getCardSuitAndValue(int type) {
		return new int[] { type / NR_VALUES, type % NR_VALUES };
	}

	public static int generateRandomCardID() {
		return (int) (Math.random() * NR_CARDS);
	}

	private UIFilledRectangle generateCardFront(int type) {
		FilledRectangle frect = this.cardFrontRects.get(type);
		UIFilledRectangle rect = new UIFilledRectangle(0, 0, 1, this.cardWidthPx, this.cardHeightPx, frect, CARD_SCENE);
		rect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		rect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		rect.setMaterial(new Material(Color.WHITE));
		rect.setClampAlignedCoordinatesToInt(false);

		this.cardRects.put(rect.getID(), rect);
		this.cardTypes.put(rect.getID(), type);

		return rect;
	}

	private UIFilledRectangle generateCardBack() {
		FilledRectangle frect = this.cardBackRects.get((int) (Math.random() * this.cardBackRects.size()));
		UIFilledRectangle rect = new UIFilledRectangle(0, 0, 1, this.cardWidthPx, this.cardHeightPx, frect, CARD_SCENE);
		rect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
		rect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		rect.setMaterial(new Material(Color.WHITE));
		rect.setClampAlignedCoordinatesToInt(false);

		this.cardRects.put(rect.getID(), rect);
		this.cardTypes.put(rect.getID(), -1);

		return rect;
	}

	private void removeCard(long cardID) {
		UIFilledRectangle cardRect = this.cardRects.get(cardID);

		if (this.tableCards.contains(cardID)) {
			this.tableCards.remove(cardID);
		}

		if (this.handCards.contains(cardID)) {
			this.handCards.remove(cardID);
		}

		if (this.opponentDrawnCards.contains(cardID)) {
			this.opponentDrawnCards.remove(cardID);
		}

		this.cardRects.remove(cardID);
		this.cardTypes.remove(cardID);

		this.cardVels.remove(cardID);
		this.cardAngularVels.remove(cardID);

		cardRect.kill();
	}

	//this is really dumb
	private void removeAllCardsFromSet(HashSet<Long> s) {
		ArrayList<Long> temp = new ArrayList<>();
		for (long id : s) {
			temp.add(id);
		}
		for (long id : temp) {
			this.removeCard(id);
		}
	}

	//this is really dumb x2
	private void removeAllCardsFromSet(TreeSet<Long> s) {
		ArrayList<Long> temp = new ArrayList<>();
		for (long id : s) {
			temp.add(id);
		}
		for (long id : temp) {
			this.removeCard(id);
		}
	}

	private void replaceDeckCover() {
		if (this.deckCover != null) {
			this.removeCard(deckCover.getID());
		}

		this.deckCover = this.generateCardBack();
		this.deckCover.setFrameAlignmentOffset(this.cardWidthPx / 2, this.cardHeightPx / 2);

		this.deckCover.setZ(ZOFFSET_DECK);
	}

	private UIFilledRectangle drawCardFromDeck(boolean showFront) {
		this.replaceDeckCover();

		UIFilledRectangle cardRect = null;
		if (showFront) {
			cardRect = this.generateCardFront((int) (Math.random() * NR_CARDS));
		}
		else {
			cardRect = this.generateCardBack();
		}

		cardRect.setZ(ZOFFSET_DECK + 0.001f);
		cardRect.setFrameAlignmentOffset(this.cardWidthPx / 2, this.cardHeightPx / 2);
		cardRect.align();

		return cardRect;
	}

	//draws the card faceup
	private void drawCardToHand() {
		UIFilledRectangle cardRect = this.drawCardFromDeck(true);

		cardRect.setZ(ZOFFSET_HAND);

		this.handCards.add(cardRect.getID());
	}

	//draws the card face down
	//points the card towards the right sidebar at opponent no. ind
	private void drawCardToOpponent() {
		UIFilledRectangle cardRect = this.drawCardFromDeck(false);
		cardRect.setZ(ZOFFSET_OPPONENT_HAND);
		this.applyImpulseToCard(new Vec2(Main.windowWidth + 100, Main.windowHeight / 2), 0, 0, cardRect.getID());
		this.opponentDrawnCards.add(cardRect.getID());
	}

	private void playCardToTable(long cardID) {
		this.addCardToTable(cardID);
	}

	private void addCardToTable(long cardID) {
		if (this.handCards.contains(cardID)) {
			this.handCards.remove(cardID);
		}

		this.tableCards.add(cardID);
		float newZ = this.tableCards.size() * 0.001f + ZOFFSET_TABLE;

		Vec2 target = new Vec2(Main.windowWidth / 2, Main.windowHeight / 2);

		Vec2 offset = new Vec2(0, Math.random() * 100);
		offset.rotate((float) Math.toRadians(Math.random() * 360));
		target.addi(offset);

		this.applyImpulseToCard(target, 8, 12, cardID);

		this.cardRects.get(cardID).setZ(newZ);
		this.cardRects.get(cardID).align();
	}

	private Vec2 calculateInitialVel(Vec2 start, Vec2 end, float frictionMagnitude) {
		float mag = (float) (Math.sqrt(end.sub(start).length() * frictionMagnitude * 2) - (frictionMagnitude / 2f));
		Vec2 ans = new Vec2(start, end).setLength(mag);
		return ans;
	}

	private void applyImpulseToCard(Vec2 target, float minAngularVelDeg, float maxAngularVelDeg, long cardID) {
		Vec2 cur = this.cardRects.get(cardID).getCenter();
		Vec2 initialVel = this.calculateInitialVel(cur, target, this.cardFrictionMagnitude);

		float angularVel = (float) Math.toRadians(Math.random() * (maxAngularVelDeg - minAngularVelDeg) + minAngularVelDeg);
		if (Math.random() > 0.5) {
			angularVel *= -1;
		}

		this.cardVels.put(cardID, initialVel);
		this.cardAngularVels.put(cardID, angularVel);
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

		if (this.client.blazingEightsIsGameStarting()) {
			this.isInGame = true;
			this.removeAllCardsFromSet(this.handCards);
			this.removeAllCardsFromSet(this.tableCards);
			this.topCardType = -1;
			this.cardsToOpponentRemaining = 0;
			this.cardsToPlayerRemaining = this.client.blazingEightsGetCardAmt().get(this.client.getID());
			this.prevMoveWasDraw = false;

			this.drawHUD();
			this.drawInputs();

			if (this.playerMoveIndicator != null) {
				this.playerMoveIndicator.kill();
			}

			this.playerMoveIndicator = new Text(0, 0, ">", FontUtils.ggsans.deriveFont(Font.BOLD), 36, Color.WHITE, MOVE_INDICATOR_SCENE);
			this.playerMoveIndicator.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_BOTTOM);
			this.playerMoveIndicator.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_CENTER);

			int nextPlayer = this.client.blazingEightsGetNextPlayer();
			UIFilledRectangle nextPlayerRect = this.playerNameRects.get(nextPlayer);
			float tx = nextPlayerRect.getLeftBorder() - 10;
			float ty = nextPlayerRect.getCenter().y;
			this.playerMoveIndicator.setFrameAlignmentOffset(tx, ty);
		}

		if (this.isInGame) {
			int[] nextMove = this.client.blazingEightsGetPerformedMove();
			if (nextMove != null) {
				int movePlayer = nextMove[0];
				int moveType = nextMove[1];
				int moveValue = nextMove[2];
				switch (moveType) {
				case MOVE_PLAY: {
					this.prevMoveWasDraw = false;
					this.topCardType = moveValue;
					if (movePlayer != this.client.getID()) {
						UIFilledRectangle card = this.generateCardFront(moveValue);
						card.setFrameAlignmentOffset(Main.windowWidth + 100, Main.windowHeight / 2);
						card.align();
						this.playCardToTable(card.getID());
					}
					break;
				}

				case MOVE_DRAW: {
					this.prevMoveWasDraw = true;
					if (movePlayer == this.client.getID()) {
						this.cardsToPlayerRemaining += moveValue;
					}
					else {
						this.cardsToOpponentRemaining += moveValue;
					}
					break;
				}
				}
				this.drawHUD();
			}

		}

		if (this.client.blazingEightsIsGameEnding()) {
			this.isInGame = false;
			this.drawInputs();
		}

		if (this.playerMoveIndicator != null) {
			int nextPlayer = this.client.blazingEightsGetNextPlayer();
			UIFilledRectangle nextPlayerRect = this.playerNameRects.get(nextPlayer);
			float tx = nextPlayerRect.getLeftBorder() - 10;
			float ty = nextPlayerRect.getCenter().y;
			this.playerMoveIndicator.setFrameAlignmentOffset(this.playerMoveIndicator.getXOffset() + (tx - this.playerMoveIndicator.getXOffset()) * 0.1f, this.playerMoveIndicator.getYOffset() + (ty - this.playerMoveIndicator.getYOffset()) * 0.1f);
			this.playerMoveIndicator.align();
		}

		//recalc where hand cards are supposed to be. 
		float handXInterval = 34;
		float handYInterval = 60;
		int cnt = 0;

		int cardsPerRow = 20;

		for (long cardID : this.handCards) {
			int numCardsInRow = Math.min(this.handCards.size() - (cnt / cardsPerRow) * cardsPerRow, cardsPerRow);
			float rowWidth = handXInterval * numCardsInRow;

			float x = (Main.windowWidth + rowWidth) / 2 - (cnt % cardsPerRow) * handXInterval;
			float y = -20 + (cnt / cardsPerRow) * handYInterval;

			if (this.hoveredCardID == cardID && (this.isValidMove(this.cardTypes.get(cardID)) || !this.isInGame)) {
				y += 40;
			}

			UIFilledRectangle cardRect = this.cardRects.get(cardID);

			Vec2 cur = cardRect.getCenter();
			Vec2 target = new Vec2(x, y);

			if (cur.sub(target).lengthSq() > 0.001f && !this.cardVels.containsKey(cardID)) {
				this.applyImpulseToCard(target, 0, 0, cardID);
			}

			if (cardRect.getZ() != ZOFFSET_HAND - cnt * 0.01f) {
				cardRect.setZ(ZOFFSET_HAND - cnt * 0.01f);
				cardRect.align();
			}

			cnt++;
		}

		//dealing cards to player and opponent
		if (this.cardsToPlayerRemaining != 0) {
			this.cardsToPlayerRemaining--;
			this.drawCardToHand();
		}

		if (this.cardsToOpponentRemaining != 0) {
			this.cardsToOpponentRemaining--;
			this.drawCardToOpponent();
		}

		//deck cover card position
		if (this.hoveredCardID == this.deckCover.getID()) {
			this.applyImpulseToCard(new Vec2(0, 20).add(new Vec2(this.cardWidthPx / 2, this.cardHeightPx / 2)), 0, 0, this.deckCover.getID());
		}
		else {
			this.applyImpulseToCard(new Vec2(0, 0).add(new Vec2(this.cardWidthPx / 2, this.cardHeightPx / 2)), 0, 0, this.deckCover.getID());
		}

		//logo position
		float ty = 100;
		float cy = this.logoRect.getYOffset();
		if (this.isInGame) {
			ty = -this.logoRect.getHeight() - 100;
		}
		else {
			float curMs = System.currentTimeMillis() - this.startTime;
			curMs /= 1000f;
			ty += (float) (Math.sin(curMs) * 10f);
		}
		float diff = ty - cy;
		this.logoRect.setFrameAlignmentOffset(100, cy + diff * 0.1f);
		this.logoRect.align();

		// -- ANIMATIONS --
		HashSet<Long> toRemove = new HashSet<>();
		for (long id : this.cardVels.keySet()) {
			UIFilledRectangle cardRect = this.cardRects.get(id);
			Vec2 vel = this.cardVels.get(id);

			cardRect.setFrameAlignmentOffset(cardRect.getXOffset() + vel.x, cardRect.getYOffset() + vel.y);
			cardRect.align();

			Vec2 friction = new Vec2(vel).setLength(-this.cardFrictionMagnitude);
			vel.addi(friction);

			if (vel.dot(friction) > 0 || vel.length() < this.cardFrictionMagnitude) {
				toRemove.add(id);
			}
		}
		for (long id : toRemove) {
			this.cardVels.remove(id);
		}

		toRemove.clear();
		for (long id : this.cardAngularVels.keySet()) {
			UIFilledRectangle cardRect = this.cardRects.get(id);
			float vel = this.cardAngularVels.get(id);
			float friction = this.cardAngularFrictionMagnitude * (vel < 0 ? 1 : -1);

			cardRect.setRotationRads(cardRect.getRotationRads() + vel);
			cardRect.align();

			vel += friction;
			this.cardAngularVels.put(id, vel);

			if (vel * friction > 0) {
				toRemove.add(id);
			}
		}
		for (long id : toRemove) {
			this.cardAngularVels.remove(id);
		}

		toRemove.clear();
		for (long id : this.opponentDrawnCards) {
			if (!this.cardVels.containsKey(id)) {
				toRemove.add(id);
			}
		}
		for (long id : toRemove) {
			this.removeCard(id);
		}
	}

	@Override
	public void render(Framebuffer outputBuffer) {
		this.uiScreen.setUIScene(BACKGROUND_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(HUD_SCENE);
		this.uiScreen.render(outputBuffer);
		this.uiScreen.setUIScene(HUD_TEXT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(MOVE_INDICATOR_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(CARD_SCENE);
		this.uiScreen.setReverseDepthColorID(false);
		this.uiScreen.render(outputBuffer);
		this.hoveredCardID = uiScreen.getEntityIDAtMouse();

		this.uiScreen.setUIScene(LOGO_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(INPUT_SCENE);
		this.uiScreen.setReverseDepthColorID(true);
		this.uiScreen.render(outputBuffer);

	}

	public boolean isValidMove(int cardType) {
		if (this.topCardType == -1) {
			return true;
		}
		int topSuit = getCardSuitAndValue(this.topCardType)[0];
		int topValue = getCardSuitAndValue(this.topCardType)[1];
		int nextSuit = getCardSuitAndValue(cardType)[0];
		int nextValue = getCardSuitAndValue(cardType)[1];
		if (topValue == VALUE_ADDTWO && nextValue != VALUE_ADDTWO && !this.prevMoveWasDraw) {
			return false;
		}
		if (topSuit == nextSuit || topValue == nextValue) {
			//general case
			return true;
		}
		return false;
	}

	@Override
	public void mousePressed(int button) {
		Input.inputsPressed(this.uiScreen.getEntityIDAtMouse());
		if (!this.isInGame) {
			if (this.hoveredCardID == this.deckCover.getID()) {
				this.drawCardToHand();
			}

			else if (this.handCards.contains(this.hoveredCardID)) {
				this.playCardToTable(this.hoveredCardID);
			}
		}
		else {
			if (this.client.blazingEightsIsMyTurn()) {
				if (this.hoveredCardID == this.deckCover.getID()) {
					this.client.blazingEightsPerformMove(MOVE_DRAW, 1);
				}
				else if (this.handCards.contains(this.hoveredCardID)) {
					int cardType = this.cardTypes.get(this.hoveredCardID);
					if (this.isValidMove(cardType)) {
						this.client.blazingEightsPerformMove(MOVE_PLAY, cardType);
						this.playCardToTable(this.hoveredCardID);
					}
				}
			}
		}
	}

	@Override
	public void mouseReleased(int button) {
		Input.inputsReleased(this.uiScreen.getEntityIDAtMouse());
		switch (Input.getClicked()) {
		case "btn_start_game": {
			this.client.blazingEightsStartGame();
			break;
		}

		case "btn_leave_game": {
			this.client.returnToMainLobby();
			break;
		}
		}
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
