package game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public class ChessGame {

	private static HashMap<Integer, ChessGame> chessGames = new HashMap<>();

	private int gameID;
	private int whiteID, blackID;

	private HashSet<Integer> spectators;

	private Stack<ChessPosition> positions;
	private Stack<int[][]> moves;

	public ChessGame() {
		this.positions = new Stack<>();
		this.positions.add(new ChessPosition());

		this.moves = new Stack<>();

		this.spectators = new HashSet<>();

		this.gameID = generateNewID();
		chessGames.put(this.gameID, this);

		this.whiteID = -1;
		this.blackID = -1;
	}

	public void setID(int id) {
		this.gameID = id;
	}

	public int getID() {
		return this.gameID;
	}

	public void setWhiteID(int id) {
		this.whiteID = id;
	}

	public void setBlackID(int id) {
		this.blackID = id;
	}

	public int getWhiteID() {
		return this.whiteID;
	}

	public int getBlackID() {
		return this.blackID;
	}

	private static int generateNewID() {
		int newID = -1;
		while (newID == -1 || chessGames.containsKey(newID)) {
			newID = (int) (Math.random() * 1000000.0);
		}
		return newID;
	}

	public void kill() {
		chessGames.remove(this.gameID);
	}

	public ChessPosition getCurPosition() {
		return this.positions.peek();
	}

	public int[][] getPrevMove() {
		if (this.moves.size() == 0) {
			return null;
		}
		return this.moves.peek();
	}

	public boolean performMove(int[] from, int[] to) {
		ChessPosition nextPosition = new ChessPosition(this.getCurPosition());
		if (!nextPosition.move(from, to)) {
			System.out.println("INVALID MOVE " + from[0] + " " + from[1] + " " + to[0] + " " + to[1]);
			return false;
		}
		this.positions.push(nextPosition);
		this.moves.push(new int[][] { from, to });
		return true;
	}

	public boolean isLegalMove(int[] from, int[] to) {
		return this.getCurPosition().isLegalMove(from, to);
	}

	public void addSpectator(int id) {
		this.spectators.add(id);
	}

	public void removeSpectator(int id) {
		this.spectators.remove(id);
	}

	public HashSet<Integer> getSpectators() {
		return this.spectators;
	}

}
