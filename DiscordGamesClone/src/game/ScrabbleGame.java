package game;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import util.FileUtils;
import util.Pair;

public class ScrabbleGame {

	public static final char LETTER_EMPTY = 0;

	public static final int BONUS_EMPTY = 0;
	public static final int BONUS_LETTER_DOUBLE = 1;
	public static final int BONUS_LETTER_TRIPLE = 2;
	public static final int BONUS_WORD_DOUBLE = 3;
	public static final int BONUS_WORD_TRIPLE = 4;

	//kinda weird to make a bonus cell into the start cell
	//maybe call them cell attributes? it doesn't really matter tho
	public static final int BONUS_START = 5;

	private static int boardSize = 15;

	public static HashSet<String> wordList;
	public static HashMap<Character, Integer> letterScore = new HashMap<Character, Integer>() {
		{
			put('A', 1);
			put('E', 1);
			put('I', 1);
			put('O', 1);
			put('U', 1);
			put('L', 1);
			put('N', 1);
			put('S', 1);
			put('T', 1);
			put('R', 1);
			put('D', 2);
			put('G', 2);
			put('B', 3);
			put('C', 3);
			put('M', 3);
			put('P', 3);
			put('F', 4);
			put('H', 4);
			put('V', 4);
			put('W', 4);
			put('Y', 4);
			put('K', 5);
			put('J', 8);
			put('X', 8);
			put('Q', 10);
			put('Z', 10);
		}
	};

	//(1 point) - A, E, I, O, U, L, N, S, T, R
	//(2 points) - D, G
	//(3 points) - B, C, M, P
	//(4 points) - F, H, V, W, Y
	//(5 points) - K
	//(8 points) - J, X
	//(10 points) - Q, Z

	private char[][] letterBoard;
	private int[][] bonusBoard;

	public ScrabbleGame() {
		this.letterBoard = new char[boardSize][boardSize];
		this.bonusBoard = new int[boardSize][boardSize];

		if (ScrabbleGame.wordList == null) {
			ScrabbleGame.loadWordList();
		}

		//TODO generate bonus board

	}

	public ScrabbleGame(ScrabbleGame g) {
		this.letterBoard = new char[boardSize][boardSize];
		this.bonusBoard = new int[boardSize][boardSize];

		if (ScrabbleGame.wordList == null) {
			ScrabbleGame.loadWordList();
		}

		for (int i = 0; i < boardSize; i++) {
			for (int j = 0; j < boardSize; j++) {
				this.letterBoard[i][j] = g.getLetterBoard()[i][j];
				this.bonusBoard[i][j] = g.getBonusBoard()[i][j];
			}
		}
	}

	public static void loadWordList() {
		ScrabbleGame.wordList = new HashSet<String>();

		File f = FileUtils.loadFile("/scrabble/scrabble_dict.txt");
		BufferedReader fin;

		try {
			fin = new BufferedReader(new FileReader(f));

			String next = fin.readLine();
			while (next != null) {
				ScrabbleGame.wordList.add(next.toUpperCase());
				next = fin.readLine();
			}

			fin.close();
		}
		catch (FileNotFoundException e) {
			System.err.println("Unable to load scrabble dictionary");
			e.printStackTrace();
		}
		catch (IOException e) {
			System.err.println("A problem occurred when reading scrabble dictionary");
			e.printStackTrace();
		}
	}

	public char[][] getLetterBoard() {
		return this.letterBoard;
	}

	public int[][] getBonusBoard() {
		return this.bonusBoard;
	}

	//if the move is invalid, then returns -1
	//else, returns the score of the move, and applies the move to the board
	public int makeMove(ArrayList<Pair<int[], Character>> tiles) {
		// - you must place down at least 2 tiles
		if (tiles.size() <= 1) {
			return -1;
		}

		// - all tiles have to be in the same row or column
		int[] tl = tiles.get(0).first;
		int[] br = tiles.get(0).first;
		for (Pair<int[], Character> i : tiles) {
			int[] t = i.first;
			tl[0] = Math.min(tl[0], t[0]);
			tl[1] = Math.min(tl[1], t[1]);
			br[0] = Math.max(br[0], t[0]);
			br[1] = Math.max(br[1], t[1]);
		}
		if (br[0] - tl[0] != 0 && br[1] - tl[1] != 0) {
			return -1;
		}

		// - all tiles must be on the board
		if (tl[0] < 0 || tl[1] < 0 || br[0] >= boardSize || br[1] >= boardSize) {
			return -1;
		}

		// - all of the new tiles can't replace old ones
		char[][] nextBoard = new char[boardSize][boardSize];
		boolean[][] isNewTile = new boolean[boardSize][boardSize];
		for (int i = 0; i < boardSize; i++) {
			for (int j = 0; j < boardSize; j++) {
				nextBoard[i][j] = this.letterBoard[i][j];
			}
		}
		for (Pair<int[], Character> i : tiles) {
			int r = i.first[0];
			int c = i.first[1];
			if (nextBoard[r][c] != LETTER_EMPTY) {
				return -1;
			}
			nextBoard[r][c] = i.second;
			isNewTile[r][c] = true;
		}

		// - there must be no gaps between the first tile and last tile
		for (int i = tl[0]; i <= br[0]; i++) {
			for (int j = tl[1]; j <= br[1]; j++) {
				if (nextBoard[i][j] == LETTER_EMPTY) {
					return -1;
				}
			}
		}

		// - all of the new words have to be valid
		if (!isLetterBoardValid(nextBoard)) {
			return -1;
		}

		// - calculate score and return
		this.letterBoard = nextBoard;
		int score = 0;
		for (int i = 0; i < boardSize; i++) {
			int rowWordScore = 0;
			int rowWordMultiplier = 1;
			boolean rowWordNew = false;

			int colWordScore = 0;
			int colWordMultiplier = 1;
			boolean colWordNew = false;
			for (int j = 0; j < boardSize; j++) {
				if (this.letterBoard[i][j] != LETTER_EMPTY) {
					int lScore = ScrabbleGame.letterScore.get(this.letterBoard[i][j]);
					if (this.bonusBoard[i][j] == BONUS_LETTER_DOUBLE) {
						lScore *= 2;
					}
					else if (this.bonusBoard[i][j] == BONUS_LETTER_TRIPLE) {
						lScore *= 3;
					}
					rowWordScore += lScore;

					if (this.bonusBoard[i][j] == BONUS_WORD_DOUBLE) {
						rowWordMultiplier *= 2;
					}
					else if (this.bonusBoard[i][j] == BONUS_WORD_TRIPLE) {
						rowWordMultiplier *= 3;
					}

					if (isNewTile[i][j]) {
						rowWordNew = true;
					}
				}
				if (this.letterBoard[j][i] != LETTER_EMPTY) {
					int lScore = ScrabbleGame.letterScore.get(this.letterBoard[j][i]);
					if (this.bonusBoard[j][i] == BONUS_LETTER_DOUBLE) {
						lScore *= 2;
					}
					else if (this.bonusBoard[j][i] == BONUS_LETTER_TRIPLE) {
						lScore *= 3;
					}
					colWordScore += lScore;

					if (this.bonusBoard[j][i] == BONUS_WORD_DOUBLE) {
						colWordMultiplier *= 2;
					}
					else if (this.bonusBoard[j][i] == BONUS_WORD_TRIPLE) {
						colWordMultiplier *= 3;
					}

					if (isNewTile[j][i]) {
						colWordNew = true;
					}
				}

				// - add score to counter
				if (this.letterBoard[i][j] == LETTER_EMPTY || j == boardSize - 1) {
					if (rowWordNew) {
						score += rowWordScore * rowWordMultiplier;
					}
					rowWordScore = 0;
					rowWordMultiplier = 1;
					rowWordNew = false;
				}
				if (this.letterBoard[j][i] == LETTER_EMPTY || j == boardSize - 1) {
					if (colWordNew) {
						score += colWordScore * colWordMultiplier;
					}
					colWordScore = 0;
					colWordMultiplier = 1;
					colWordNew = false;
				}
			}
		}

		return score;
	}

	public boolean isMoveValid(ArrayList<Pair<int[], Character>> tiles) {
		ScrabbleGame nextPos = new ScrabbleGame(this);
		return nextPos.makeMove(tiles) != -1;
	}

	private boolean isLetterBoardValid(char[][] board) {
		for (int i = 0; i < boardSize; i++) {
			String rowWord = "";
			String colWord = "";
			for (int j = 0; j < boardSize; j++) {
				if (this.letterBoard[i][j] != LETTER_EMPTY) {
					rowWord += this.letterBoard[i][j];
				}
				if (this.letterBoard[j][i] != LETTER_EMPTY) {
					colWord += this.letterBoard[i][j];
				}

				// - check against dictionary
				if (this.letterBoard[i][j] == LETTER_EMPTY || j == boardSize - 1) {
					if (!ScrabbleGame.wordList.contains(rowWord)) {
						return false;
					}
				}
				if (this.letterBoard[j][i] == LETTER_EMPTY || j == boardSize - 1) {
					if (!ScrabbleGame.wordList.contains(colWord)) {
						return false;
					}
				}
			}
		}
		return true;
	}

}
