package input;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import entity.Entity;
import graphics.Material;
import graphics.Texture;
import graphics.TextureMaterial;
import model.FilledRectangle;
import ui.Text;
import ui.UIElement;
import util.FontUtils;
import util.GraphicsTools;
import util.Mat4;
import util.Vec3;
import util.Vec4;

public class TextField extends Input {

	private static HashMap<Character, Character> shiftMap = new HashMap<Character, Character>() {
		{
			put('`', '~');
			put('1', '!');
			put('2', '@');
			put('3', '#');
			put('4', '$');
			put('5', '%');
			put('6', '^');
			put('7', '&');
			put('8', '*');
			put('9', '(');
			put('0', ')');
			put('-', '_');
			put('=', '+');
			put('q', 'Q');
			put('w', 'W');
			put('e', 'E');
			put('r', 'R');
			put('t', 'T');
			put('y', 'Y');
			put('u', 'U');
			put('i', 'I');
			put('o', 'O');
			put('p', 'P');
			put('[', '{');
			put(']', '}');
			put('\\', '|');
			put('a', 'A');
			put('s', 'S');
			put('d', 'D');
			put('f', 'F');
			put('g', 'G');
			put('h', 'H');
			put('j', 'J');
			put('k', 'K');
			put('l', 'L');
			put(';', ':');
			put('\'', '"');
			put('z', 'Z');
			put('x', 'X');
			put('c', 'C');
			put('v', 'V');
			put('b', 'B');
			put('n', 'N');
			put('m', 'M');
			put(',', '<');
			put('.', '>');
			put('/', '?');
		}
	};

	//private long fieldInnerID;
	private Text fieldText;

	private String text, hintText;
	private int textLeftMargin = 5;
	private int textRightMargin = 5;
	private Font font;

	private HashSet<Integer> pressedKeys; // stores key codes, not chars

	private Material textMaterial, hintTextMaterial;
	private Material releasedMaterial, pressedMaterial, hoveredMaterial, selectedMaterial;
	private Material currentMaterial;

	private boolean textWrapping = false;

	public TextField(float x, float y, float width, float height, String sID, String hintText, Font font, int fontSize, int scene) {
		super(x, y, 0, width, height, sID, scene);
		this.init(hintText, FontUtils.deriveSize(fontSize, font));
	}

	private void init(String hintText, Font font) {
		this.font = font;

		this.setFrameAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_BOTTOM);

		this.horizontalAlignContent = UIElement.ALIGN_LEFT;
		this.verticalAlignContent = UIElement.ALIGN_BOTTOM;

		this.text = "";
		this.hintText = hintText;
		this.pressedKeys = new HashSet<>();

		this.textMaterial = new Material(Color.WHITE);
		this.hintTextMaterial = new Material(new Vec4(1, 1, 1, 0.3f));

		float textWidth = this.width - (textLeftMargin + textRightMargin);
		textWidth = Math.max(textWidth, 1);

		this.fieldText = new Text(textLeftMargin, 0, z + depthSpacing, textWidth, hintText, font, this.hintTextMaterial, this.scene);
		this.fieldText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
		this.fieldText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
		this.fieldText.bind(this);

		this.pressedMaterial = new Material(new Vec4(0, 0, 0, 0.6f));
		this.hoveredMaterial = new Material(new Vec4(0, 0, 0, 0.3f));
		this.selectedMaterial = new Material(new Vec4(0, 0, 0, 0.4f));
		this.releasedMaterial = new Material(new Vec4(0, 0, 0, 0.15f));

		this.setMaterial(this.releasedMaterial);
	}

	@Override
	protected void __update() {
		// -- FIELD INNER --
		Material nextMaterial = null;
		if (this.clicked) { // check for clicks happens when mouse is released.
			nextMaterial = this.selectedMaterial;
		}
		else if (this.pressed) {
			nextMaterial = this.pressedMaterial;
		}
		else if (this.hovered) {
			nextMaterial = this.hoveredMaterial;
		}
		else {
			nextMaterial = this.releasedMaterial;
		}
		if (this.currentMaterial != nextMaterial) {
			this.currentMaterial = nextMaterial;
			this.setMaterial(this.currentMaterial);
			//this.updateModelInstance(this.fieldInnerID, this.currentMaterial);
		}

		// -- TEXT --
		if (this.text.length() == 0) {
			this.fieldText.setMaterial(this.hintTextMaterial);
			this.fieldText.setText(this.hintText);
		}
		else {
			this.fieldText.setMaterial(this.textMaterial);
			this.fieldText.setText(this.text);
		}
	}

	@Override
	protected void ___kill() {
		this.fieldText.kill();
	}

	@Override
	protected void _alignContents() {
		float textWidth = this.width - (textLeftMargin + textRightMargin);
		textWidth = Math.max(textWidth, 1);

		this.fieldText.setWidth(textWidth);
	}

	public void setTextWrapping(boolean b) {
		this.textWrapping = b;
		if (this.textWrapping) {
			this.fieldText.setTextWrapping(true);
			this.fieldText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
			this.fieldText.setFrameAlignmentOffset(this.textLeftMargin, this.textLeftMargin);
			this.fieldText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		}
		else {
			this.fieldText.setTextWrapping(false);
			this.fieldText.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
			this.fieldText.setFrameAlignmentOffset(this.textLeftMargin, 0);
			this.fieldText.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
		}

	}

	public String getText() {
		return this.text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public void keyPressed(int key) {
		if (this.clicked) {
			pressedKeys.add(key);

			// looking for ctrl + v
			if ((pressedKeys.contains(GLFW_KEY_LEFT_CONTROL) || pressedKeys.contains(GLFW_KEY_RIGHT_CONTROL)) && pressedKeys.contains(GLFW_KEY_V)) {
				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				String result = "";
				try {
					result = (String) clipboard.getData(DataFlavor.stringFlavor);
				}
				catch (UnsupportedFlavorException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				this.text += result;
				return;
			}
			else if (key == GLFW_KEY_BACKSPACE) {
				if (this.text.length() != 0) {
					this.text = this.text.substring(0, this.text.length() - 1);
				}
			}
			else if (key == GLFW_KEY_SPACE) {
				this.text += " ";
			}
			else {
				String keyName = glfwGetKeyName(key, 0);
				if (keyName == null) {
					return;
				}
				char k = keyName.charAt(0);
				if (pressedKeys.contains(GLFW_KEY_LEFT_SHIFT) || pressedKeys.contains(GLFW_KEY_RIGHT_SHIFT)) {
					k = shiftMap.get(k);
				}
				this.text += k;
			}
		}
	}

	public void keyReleased(int key) {
		if (this.clicked) {
			if (pressedKeys.contains(key)) {
				pressedKeys.remove(key);
			}
		}
	}

}
