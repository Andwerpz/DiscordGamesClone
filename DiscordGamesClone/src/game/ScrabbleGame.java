package game;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

	public static int boardSize = 15;
	public static int handSize = 7;

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

	public static HashMap<Character, Integer> letterDistribution = new HashMap<Character, Integer>() {
		{
			put('A', 9);
			put('B', 2);
			put('C', 2);
			put('D', 4);
			put('E', 12);
			put('F', 2);
			put('G', 3);
			put('H', 2);
			put('I', 9);
			put('J', 1);
			put('K', 1);
			put('L', 4);
			put('M', 2);
			put('N', 6);
			put('O', 8);
			put('P', 2);
			put('Q', 1);
			put('R', 6);
			put('S', 4);
			put('T', 6);
			put('U', 4);
			put('V', 2);
			put('W', 2);
			put('X', 1);
			put('Y', 2);
			put('Z', 1);
		}
	};

	public static char getRandomLetter() {
		int charTotal = 0;
		for (char c : ScrabbleGame.letterDistribution.keySet()) {
			charTotal += ScrabbleGame.letterDistribution.get(c);
		}
		int rand = (int) (Math.random() * charTotal);
		for (char c : ScrabbleGame.letterDistribution.keySet()) {
			int val = ScrabbleGame.letterDistribution.get(c);
			if (rand < val) {
				return c;
			}
			rand -= val;
		}
		System.err.println("SOMETHING WENT WRONG SCRABBLE RANDOM LETTER GENERATION");
		return '*';
	}

	private char[][] letterBoard;
	private int[][] bonusBoard;

	public ScrabbleGame() {
		this.letterBoard = new char[boardSize][boardSize];
		this.bonusBoard = new int[boardSize][boardSize];

		if (ScrabbleGame.wordList == null) {
			ScrabbleGame.loadWordList();
		}

		for (char[] i : this.letterBoard) {
			Arrays.fill(i, LETTER_EMPTY);
		}

		//generate bonus board
		this.bonusBoard = new int[][] { { 4, 0, 0, 1, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 4 }, { 0, 3, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 3, 0 }, { 0, 0, 3, 0, 0, 0, 1, 0, 1, 0, 0, 0, 3, 0, 0 }, { 1, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0, 3, 0, 0, 1 }, { 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0 },
				{ 0, 2, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 2, 0 }, { 0, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0 }, { 4, 0, 0, 1, 0, 0, 0, 5, 0, 0, 0, 1, 0, 0, 4 }, { 0, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0 }, { 0, 2, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 2, 0 },
				{ 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0 }, { 1, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0, 3, 0, 0, 1 }, { 0, 0, 3, 0, 0, 0, 1, 0, 1, 0, 0, 0, 3, 0, 0 }, { 0, 3, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 3, 0 }, { 4, 0, 0, 1, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 4 }, };
	}

	public ScrabbleGame(ScrabbleGame g) {
		this.letterBoard = new char[boardSize][boardSize];
		this.bonusBoard = new int[boardSize][boardSize];

		if (ScrabbleGame.wordList == null) {
			ScrabbleGame.loadWordList();
		}

		for (char[] i : this.letterBoard) {
			Arrays.fill(i, LETTER_EMPTY);
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

	private boolean isFirstMove() {
		for (char[] i : this.letterBoard) {
			for (char j : i) {
				if (j != LETTER_EMPTY) {
					return false;
				}
			}
		}
		return true;
	}

	private int[] dr = new int[] { -1, 1, 0, 0 };
	private int[] dc = new int[] { 0, 0, -1, 1 };

	//if the move is invalid, then returns -1
	//else, returns the score of the move, and applies the move to the board
	public int makeMove(ArrayList<Pair<int[], Character>> tiles) {
		// - you must place down at least 2 tiles
		if (tiles.size() <= 1) {
			//System.err.println("MUST PLACE AT LEAST 2 TILES");
			return -1;
		}

		// - all tiles have to be in the same row or column
		int[] tl = new int[] { tiles.get(0).first[0], tiles.get(0).first[1] };
		int[] br = new int[] { tiles.get(0).first[0], tiles.get(0).first[1] };

		boolean overStartTile = false;
		boolean adjToOldTile = false;
		for (Pair<int[], Character> i : tiles) {
			int r = i.first[0];
			int c = i.first[1];
			tl[0] = Math.min(tl[0], r);
			tl[1] = Math.min(tl[1], c);
			br[0] = Math.max(br[0], r);
			br[1] = Math.max(br[1], c);

			if (this.bonusBoard[r][c] == BONUS_START) {
				overStartTile = true;
			}

			if (!adjToOldTile) {
				for (int j = 0; j < 4; j++) {
					int nr = r + dr[j];
					int nc = c + dc[j];
					if (nr < 0 || nc < 0 || nr >= boardSize || nc >= boardSize) {
						continue;
					}
					if (this.letterBoard[nr][nc] != LETTER_EMPTY) {
						adjToOldTile = true;
					}
				}
			}
		}
		if (br[0] - tl[0] != 0 && br[1] - tl[1] != 0) {
			//System.err.println("NOT IN SAME ROW OR COL");
			return -1;
		}

		// - if it's the first move, at least one of the tiles should be on the start tile
		if (this.isFirstMove() && !overStartTile) {
			return -1;
		}

		// - if it's not the first move, then at least one tile must be adjacent to a previously placed tile
		if (!this.isFirstMove() && !adjToOldTile) {
			return -1;
		}

		// - all tiles must be on the board
		if (tl[0] < 0 || tl[1] < 0 || br[0] >= boardSize || br[1] >= boardSize) {
			//System.err.println("ALL TILES MUST BE ON BOARD");
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
				//System.err.println("NEW TILES MUST NOT REPLACE OLD TILES");
				return -1;
			}
			nextBoard[r][c] = i.second;
			isNewTile[r][c] = true;
		}

		// - there must be no gaps between the first tile and last tile
		for (int i = tl[0]; i <= br[0]; i++) {
			for (int j = tl[1]; j <= br[1]; j++) {
				if (nextBoard[i][j] == LETTER_EMPTY) {
					//System.err.println("THERE MUST BE NO GAPS BETWEEN FIRST AND LAST TILE");
					return -1;
				}
			}
		}

		// - all of the new words have to be valid
		if (!isLetterBoardValid(nextBoard)) {
			//System.err.println("ALL WORDS MUST BE VALID");
			return -1;
		}

		// - calculate score and return
		this.letterBoard = nextBoard;
		int score = 0;
		for (int i = 0; i < boardSize; i++) {
			int rowWordScore = 0;
			int rowWordMultiplier = 1;
			int rowWordLength = 0;
			boolean rowWordNew = false;

			int colWordScore = 0;
			int colWordMultiplier = 1;
			int colWordLength = 0;
			boolean colWordNew = false;
			for (int j = 0; j < boardSize; j++) {
				if (this.letterBoard[i][j] != LETTER_EMPTY) {
					int lScore = ScrabbleGame.letterScore.get(this.letterBoard[i][j]);
					rowWordLength++;
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
					colWordLength++;
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
					if (rowWordNew && rowWordLength >= 2) {
						score += rowWordScore * rowWordMultiplier;
					}
					rowWordScore = 0;
					rowWordMultiplier = 1;
					rowWordNew = false;
				}
				if (this.letterBoard[j][i] == LETTER_EMPTY || j == boardSize - 1) {
					if (colWordNew && colWordLength >= 2) {
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

	public int getMoveScore(ArrayList<Pair<int[], Character>> tiles) {
		ScrabbleGame nextPos = new ScrabbleGame(this);
		return nextPos.makeMove(tiles);
	}

	private boolean isLetterBoardValid(char[][] board) {
		for (int i = 0; i < boardSize; i++) {
			String rowWord = "";
			String colWord = "";
			for (int j = 0; j < boardSize; j++) {
				if (board[i][j] != LETTER_EMPTY) {
					rowWord += board[i][j];
				}
				if (board[j][i] != LETTER_EMPTY) {
					colWord += board[j][i];
				}

				// - check against dictionary
				if (rowWord.length() != 0 && (board[i][j] == LETTER_EMPTY || j == boardSize - 1)) {
					if (rowWord.length() >= 2 && !ScrabbleGame.wordList.contains(rowWord)) {
						return false;
					}
					rowWord = "";
				}
				if (colWord.length() != 0 && (board[j][i] == LETTER_EMPTY || j == boardSize - 1)) {
					if (colWord.length() >= 2 && !ScrabbleGame.wordList.contains(colWord)) {
						return false;
					}
					colWord = "";
				}
			}
		}
		return true;
	}

	private <T> ArrayList<ArrayList<T>> generatePermutations(ArrayList<T> a) {
		ArrayList<ArrayList<T>> ans = new ArrayList<>();
		this.generatePermutations(a, a.size(), a.size(), ans);
		return ans;
	}

	private <T> void generatePermutations(ArrayList<T> a, int s, int n, ArrayList<ArrayList<T>> ans) {
		if (s == 1) {
			ArrayList<T> next = new ArrayList<>();
			next.addAll(a);
			ans.add(next);
		}

		for (int i = 0; i < s; i++) {
			this.generatePermutations(a, s - 1, n, ans);
			if (s % 2 == 1) {
				T temp = a.get(0);
				a.set(0, a.get(a.size() - 1));
				a.set(a.size() - 1, temp);
			}
			else {
				T temp = a.get(i);
				a.set(i, a.get(a.size() - 1));
				a.set(a.size() - 1, temp);
			}
		}
	}

	//returns the maximum scoring move, and returns null if no move exists. 
	public ArrayList<Pair<int[], Character>> generateBestMove(ArrayList<Character> hand) {
		int maxScore = 0;
		ArrayList<Pair<int[], Character>> ans = null;

		ArrayList<ArrayList<Character>> permutations = this.generatePermutations(hand);

		//for each cell, try to make a word to the right
		for (int i = 0; i < boardSize; i++) {
			//find the cells that you can place stuff on
			ArrayList<int[]> openCells = new ArrayList<>();
			for (int j = 0; j < boardSize; j++) {
				if (this.letterBoard[i][j] == LETTER_EMPTY) {
					openCells.add(new int[] { i, j });
				}
			}

			//generate the moves, and test them
			for (int j = 0; j < openCells.size(); j++) {
				//go through all move sizes
				for (int k = 2; k <= hand.size() && j + k < openCells.size(); k++) {
					int amtOfMoves = 1;
					for (int l = hand.size(); l > hand.size() - k; l--) {
						amtOfMoves *= l;
					}

					//go through each permutation
					for (int l = 0; l < amtOfMoves; l++) {
						ArrayList<Pair<int[], Character>> move = new ArrayList<>();
						for (int r = 0; r < k; r++) {
							int[] coord = openCells.get(r + j);
							char c = permutations.get(l).get(r);
							Pair<int[], Character> next = new Pair<>(coord, c);
							move.add(next);
						}

						//test the move
						int score = this.getMoveScore(move);
						if (score > maxScore) {
							ans = move;
							maxScore = score;
						}
					}
				}
			}
		}

		//now do the same, but up to down
		for (int i = 0; i < boardSize; i++) {
			//find the cells that you can place stuff on
			ArrayList<int[]> openCells = new ArrayList<>();
			for (int j = 0; j < boardSize; j++) {
				if (this.letterBoard[j][i] == LETTER_EMPTY) {
					openCells.add(new int[] { j, i });
				}
			}

			//generate the moves, and test them
			for (int j = 0; j < openCells.size(); j++) {
				//go through all move sizes
				for (int k = 2; k <= hand.size() && j + k < openCells.size(); k++) {
					int amtOfMoves = 1;
					for (int l = hand.size(); l > hand.size() - k; l--) {
						amtOfMoves *= l;
					}

					//go through each permutation
					for (int l = 0; l < amtOfMoves; l++) {
						ArrayList<Pair<int[], Character>> move = new ArrayList<>();
						for (int r = 0; r < k; r++) {
							int[] coord = openCells.get(r + j);
							char c = permutations.get(l).get(r);
							Pair<int[], Character> next = new Pair<>(coord, c);
							move.add(next);
						}

						//test the move
						int score = this.getMoveScore(move);
						if (score > maxScore) {
							ans = move;
							maxScore = score;
						}
					}
				}
			}
		}

		return ans;
	}

}
