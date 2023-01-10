package game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ChessPosition {

	//by valid move, i mean that the piece can move there according to it's own moveset. We disregard checks to the king, 
	//and we can even take the other team's king. 

	public static final int BOARD_SIZE = 8;

	public static final byte PAWN = 1;
	public static final byte KNIGHT = 2;
	public static final byte BISHOP = 3;
	public static final byte ROOK = 4;
	public static final byte QUEEN = 5;
	public static final byte KING = 6;

	public static HashMap<Byte, Double> pieceValues = new HashMap<Byte, Double>() {
		{
			put(PAWN, 1d);
			put(KNIGHT, 3d);
			put(BISHOP, 3d);
			put(ROOK, 5d);
			put(QUEEN, 9d);
			put(KING, 12d); //matters for checks
		}
	};

	public byte[][] board;
	public boolean[][] moved; //records whether the piece has been moved yet

	public boolean whiteMove = true;

	public boolean whiteWin = false;
	public boolean blackWin = false;

	public boolean stalemate = false;

	/*
	 * CASTLING: DONE
	 * 1. Neither the king or rook in question has moved previously
	 * 2. There are no pieces between the rook and king
	 * 3. The king is not currently in check
	 * 4. The king does not pass through a square attacked by an opposing piece
	 * 5. The king does not end up in check
	 */

	//EN PASSANT

	//PAWN PROMOTION
	//for now just going to automatically promote to queen

	//make a starting board
	public ChessPosition() {
		//negative values represent black pieces. 
		this.board = new byte[][] { { -ROOK, -KNIGHT, -BISHOP, -QUEEN, -KING, -BISHOP, -KNIGHT, -ROOK }, { -PAWN, -PAWN, -PAWN, -PAWN, -PAWN, -PAWN, -PAWN, -PAWN }, { 0, 0, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 0, 0 },
				{ PAWN, PAWN, PAWN, PAWN, PAWN, PAWN, PAWN, PAWN }, { ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK } };

		this.moved = new boolean[BOARD_SIZE][BOARD_SIZE];

		//so if a piece moves into an empty square, they won't be counted as not moved
		for (int i = 0; i < BOARD_SIZE; i++) {
			for (int j = 0; j < BOARD_SIZE; j++) {
				if (this.board[i][j] == 0) {
					this.moved[i][j] = true;
				}
			}
		}

		this.whiteMove = true;
	}

	//makes copy of chess position
	public ChessPosition(ChessPosition c) {
		this.board = new byte[BOARD_SIZE][BOARD_SIZE];
		this.moved = new boolean[BOARD_SIZE][BOARD_SIZE];
		for (int i = 0; i < BOARD_SIZE; i++) {
			for (int j = 0; j < BOARD_SIZE; j++) {
				this.board[i][j] = c.board[i][j];
				this.moved[i][j] = c.moved[i][j];
			}
		}

		this.whiteMove = c.whiteMove;
	}

	//returns the fitness of the current position
	//positive for white and negative for black
	public double calculateFitness() {
		double ans = 0;

		//calc material
		ans += this.calculateMaterialFitness();

		//MIDGAME

		//calc control advantage:
		//give 0.1x val of enemy piece if attacked
		//give extra points for control of the center
		//divide attack points by value of piece to prevent sacking a queen for a minor piece or checking the enemy king
		ArrayList<int[]> whiteMoves = this.generateAllLegalMoves(true);
		ArrayList<int[]> blackMoves = this.generateAllLegalMoves(false);

		double attackWeight = 0.1;
		double controlWeight = 0.05;
		double centerWeight = 0.15;

		int r, c;
		double pieceVal, atkPiece;

		for (int[] i : whiteMoves) {
			r = i[2];
			c = i[3];
			atkPiece = this.board[i[2]][i[3]];
			pieceVal = pieceValues.get((byte) Math.abs(this.board[i[0]][i[1]]));
			if (atkPiece < 0) { //attacking a black piece
				ans += pieceValues.get((byte) Math.abs(atkPiece)) * attackWeight / pieceVal;
			}
			if ((r == 3 || r == 4) && (c == 3 || c == 4)) {
				ans += centerWeight / pieceVal;
			}
			else {
				ans += controlWeight / pieceVal;
			}
		}

		for (int[] i : blackMoves) {
			r = i[2];
			c = i[3];
			atkPiece = this.board[i[2]][i[3]];
			pieceVal = pieceValues.get((byte) Math.abs(this.board[i[0]][i[1]]));
			if (atkPiece > 0) { //attacking a white piece
				ans -= pieceValues.get((byte) Math.abs(atkPiece)) * attackWeight / pieceVal;
			}
			if ((r == 3 || r == 4) && (c == 3 || c == 4)) {
				ans -= centerWeight / pieceVal;
			}
			else {
				ans -= controlWeight / pieceVal;
			}
		}

		//checkmate
		if (whiteWin) { //white mated black
			ans += 1e7;
		}
		if (blackWin) { //black mated white
			ans -= 1e7;
		}
		//TODO: this actually doesn't check for stalemates, they are viewed the same, as in a stalemate, one side doesn't have any moves.
		//to fix, just make sure that if white mates black, that white is actually attacking the black king. 

		return ans;
	}

	//returns the material fitness of the board
	public double calculateMaterialFitness() {
		double ans = 0;
		for (int i = 0; i < BOARD_SIZE; i++) {
			for (int j = 0; j < BOARD_SIZE; j++) {
				int val = this.board[i][j];
				if (val == 0) {
					continue;
				}
				ans += pieceValues.get((byte) Math.abs(val)) * (val < 0 ? -1 : 1);
			}
		}
		return ans;
	}

	//coordinates are in the form r, c
	//checks whether move is valid. If move is valid, then it performs the move, and returns true, 
	//otherwise it returns false and does nothing.

	//it accounts for discovered checks. 
	public boolean move(int[] from, int[] to) {

		//from cell is out of bounds
		if (from[0] < 0 || from[1] < 0 || from[0] >= BOARD_SIZE || from[1] >= BOARD_SIZE) {
			return false;
		}

		//to cell is out of bounds
		if (to[0] < 0 || to[1] < 0 || to[0] >= BOARD_SIZE || to[1] >= BOARD_SIZE) {
			return false;
		}

		//from and to cell are the same
		if (from[0] == to[0] && from[1] == to[1]) {
			return false;
		}

		//from cell is empty
		if (board[from[0]][from[1]] == 0) {
			return false;
		}

		//check if move tries to take the king
		//obviously this shouldn't be possible if this method actually works, but it doesn't hurt to put in an extra check
		if (Math.abs(board[to[0]][to[1]]) == KING) {
			return false;
		}

		//check if move can be performed by piece
		int piece = board[from[0]][from[1]];
		boolean white = piece > 0;

		//wrong player is moving right now
		if ((white && !whiteMove) || (!white && whiteMove)) {
			return false;
		}

		//check for legality of move
		if (!this.isLegalMove(from, to)) {
			return false;
		}

		//perform move
		board[to[0]][to[1]] = board[from[0]][from[1]];
		board[from[0]][from[1]] = 0;

		//move is locked in now
		//mark pos as moved
		this.moved[from[0]][from[1]] = true;
		this.moved[to[0]][to[1]] = true;

		//handle special moves
		if (piece == PAWN && to[0] == 0) { //white pawn promote
			board[to[0]][to[1]] = QUEEN;
		}
		if (piece == -PAWN && to[0] == BOARD_SIZE - 1) { //black pawn promote
			board[to[0]][to[1]] = -QUEEN;
		}

		//castling
		if (Math.abs(piece) == KING && Math.abs(to[1] - from[1]) == 2) {
			//we have to move the rook
			if (to[1] - from[1] == 2) { //queen side
				board[from[0]][5] = board[from[0]][7];
				board[from[0]][7] = 0;
				this.moved[from[0]][7] = true;
				this.moved[from[0]][5] = true;
			}
			else if (to[1] - from[1] == -2) { //king side
				board[from[0]][3] = board[from[0]][0];
				board[from[0]][0] = 0;
				this.moved[from[0]][0] = true;
				this.moved[from[0]][3] = true;
			}
		}

		//check for win
		ArrayList<int[]> opponentMoves = this.generateAllLegalMoves(!this.whiteMove);
		if (opponentMoves.size() == 0) {
			if (this.whiteMove) {
				this.whiteWin = true;
			}
			else if (!this.whiteMove) {
				this.blackWin = true;
			}
		}

		this.whiteMove = !whiteMove; //next person to move

		return true; //valid move
	}

	//if from and to form a diagonal path, then it'll check along that path
	//if they form a straight path, then it'll check along that path
	//else, it won't check, and return false;

	//it checks all tiles excluding the first and last tile in the path
	public boolean isPathEmpty(int[] from, int[] to) {
		int rDiff = to[0] - from[0];
		int cDiff = to[1] - from[1];

		//from and to are same
		if (rDiff == 0 && cDiff == 0) {
			return false;
		}

		//it is not diagonal or straight line
		if (!(Math.abs(rDiff) - Math.abs(cDiff) == 0 || Math.min(Math.abs(rDiff), Math.abs(cDiff)) == 0)) {
			return false;
		}

		int rUnitDiff = rDiff == 0 ? 0 : (rDiff < 0 ? -1 : 1);
		int cUnitDiff = cDiff == 0 ? 0 : (cDiff < 0 ? -1 : 1);

		int curR = from[0] + rUnitDiff;
		int curC = from[1] + cUnitDiff;

		while (curR != to[0] || curC != to[1]) {
			if (board[curR][curC] != 0) {
				return false;
			}
			curR += rUnitDiff;
			curC += cUnitDiff;
		}

		return true;
	}

	//checks if pawn can move to space; not to be confused with attacking
	public boolean isValidPawnMove(int[] from, int[] to) {
		int piece = board[from[0]][from[1]];
		boolean validMove = false;

		int rDiff = to[0] - from[0];
		int cDiff = to[1] - from[1];

		if (piece == PAWN) { //white
			if (rDiff == -2 && cDiff == 0 && !this.moved[from[0]][from[1]] && board[to[0]][to[1]] == 0 && board[to[0] + 1][to[1]] == 0) { //first move, 2 moves forward
				validMove = true;
			}
			else if (rDiff == -1 && cDiff == 0 && board[to[0]][to[1]] == 0) { //normal move
				validMove = true;
			}
		}
		else if (piece == -PAWN) { //black
			if (rDiff == 2 && cDiff == 0 && !this.moved[from[0]][from[1]] && board[to[0]][to[1]] == 0 && board[to[0] - 1][to[1]] == 0) { //first move, 2 moves forward
				validMove = true;
			}
			else if (rDiff == 1 && cDiff == 0 && board[to[0]][to[1]] == 0) { //normal move
				validMove = true;
			}
		}

		return validMove;
	}

	//checks if pawn can attack into a space; not to be confused with moving. 
	public boolean isValidPawnAttack(int[] from, int[] to) {
		int piece = board[from[0]][from[1]];
		boolean validMove = false;

		int rDiff = to[0] - from[0];
		int cDiff = to[1] - from[1];

		if (piece == PAWN) { //white
			if (rDiff == -1 && Math.abs(cDiff) == 1 && board[to[0]][to[1]] < 0) { //taking
				validMove = true;
			}
		}
		else if (piece == -PAWN) { //black
			if (rDiff == 1 && Math.abs(cDiff) == 1 && board[to[0]][to[1]] > 0) { //taking
				validMove = true;
			}
		}

		return validMove;
	}

	//checks if the move is allowed in a normal game. 
	//accounts for checks
	public boolean isLegalMove(int[] from, int[] to) {

		//from cell is out of bounds
		if (from[0] < 0 || from[1] < 0 || from[0] >= BOARD_SIZE || from[1] >= BOARD_SIZE) {
			return false;
		}

		//to cell is out of bounds
		if (to[0] < 0 || to[1] < 0 || to[0] >= BOARD_SIZE || to[1] >= BOARD_SIZE) {
			return false;
		}

		int piece = board[from[0]][from[1]];
		boolean white = piece > 0;
		piece = Math.abs(piece);

		//trying to take piece of same color, or trying to take king
		int toVal = board[to[0]][to[1]];
		if (Math.abs(toVal) == KING) {
			return false;
		}

		if (piece == PAWN && !(this.isValidPawnAttack(from, to) || this.isValidPawnMove(from, to))) {
			return false;
		}
		else if (piece != PAWN && !this.isValidPieceMove(from, to)) {
			return false;
		}

		ChessPosition nextPos = new ChessPosition(this);

		//castling
		if (piece == KING) {
			int rDiff = to[0] - from[0];
			int cDiff = to[1] - from[1];
			if (cDiff == 2 && rDiff == 0) {
				//move da rook
				nextPos.board[from[0]][5] = nextPos.board[from[0]][7];
				nextPos.board[from[0]][7] = 0;
			}
			else if (cDiff == -2 && rDiff == 0) {
				//move rook
				nextPos.board[from[0]][3] = nextPos.board[from[0]][0];
				nextPos.board[from[0]][0] = 0;
			}
		}

		boolean legalMove = false;

		//check if king is in check
		//perform move on temp board
		nextPos.board[to[0]][to[1]] = nextPos.board[from[0]][from[1]];
		nextPos.board[from[0]][from[1]] = 0;

		if (white) {
			legalMove = !nextPos.isWhiteInCheck();
		}
		else if (!white) {
			legalMove = !nextPos.isBlackInCheck();
		}
		//System.out.println(legalMove? "NOT IN CHECK" : "IN CHECK");

		return legalMove;
	}

	//checks if the move is valid according to the rules of the piece.
	//doesn't account for illegal moves due to discovered checks
	//also, taking the other teams king is a valid move. This is to make check detection easier
	public boolean isValidPieceMove(int[] from, int[] to) {
		int piece = board[from[0]][from[1]];
		boolean white = piece > 0;
		piece = Math.abs(piece);

		//check if trying to take piece of same color, or trying to take king
		int toVal = board[to[0]][to[1]];
		if (toVal != 0 && ((toVal > 0 && white) || (toVal < 0 && !white))) {
			return false;
		}

		boolean validMove = false;

		if (piece == KNIGHT) {
			int rDiff = Math.abs(from[0] - to[0]);
			int cDiff = Math.abs(from[1] - to[1]);
			if (Math.min(rDiff, cDiff) == 1 && Math.max(rDiff, cDiff) == 2) { //making sure it's an L shape
				validMove = true;
			}
		}
		else if (piece == BISHOP) {
			int rDiff = Math.abs(from[0] - to[0]);
			int cDiff = Math.abs(from[1] - to[1]);
			if (rDiff == cDiff && this.isPathEmpty(from, to)) { //diagonal move
				validMove = true;
			}
		}
		else if (piece == ROOK) {
			int rDiff = Math.abs(from[0] - to[0]);
			int cDiff = Math.abs(from[1] - to[1]);
			if (Math.min(rDiff, cDiff) == 0 && this.isPathEmpty(from, to)) { //moving in straight lines
				validMove = true;
			}
		}
		else if (piece == QUEEN) {
			int rDiff = Math.abs(from[0] - to[0]);
			int cDiff = Math.abs(from[1] - to[1]);
			if ((rDiff == cDiff || Math.min(rDiff, cDiff) == 0) && this.isPathEmpty(from, to)) { //bishop or rook behavior
				validMove = true;
			}
		}
		else if (piece == KING) {
			int rDiff = to[0] - from[0];
			int cDiff = to[1] - from[1];
			if (Math.max(Math.abs(rDiff), Math.abs(cDiff)) == 1) { //maximum 1 cell diff for normal moves
				validMove = true;
			}
			//castling
			if (!this.isPathEmpty(from, to)) {
				validMove = false;
			}
			else if (cDiff == 2 && rDiff == 0 && !this.moved[from[0]][4] && !this.moved[from[0]][7] && !(white ? this.isWhiteInCheck() : this.isBlackInCheck()) && //can't castle in check
					!this.isCellAttacked(new int[] { from[0], from[1] + 1 }, !white)) { //castling queen side
				validMove = true;
			}
			else if (cDiff == -2 && rDiff == 0 && !this.moved[from[0]][4] && !this.moved[from[0]][0] && !(white ? this.isWhiteInCheck() : this.isBlackInCheck()) && !this.isCellAttacked(new int[] { from[0], from[1] - 1 }, !white)) { //castling king side
				validMove = true;
			}
		}
		return validMove;
	}

	//returns true if a piece from the white team (if byWhite) or black team (if !byWhite) is attacking
	//the specified cell. Returns false otherwise
	public boolean isCellAttacked(int[] cell, boolean byWhite) {
		for (int i = 0; i < BOARD_SIZE; i++) {
			for (int j = 0; j < BOARD_SIZE; j++) {
				int val = board[i][j];
				if ((val < 0 && byWhite) || (val > 0 && !byWhite)) { //checking the wrong team
					continue;
				}
				if (Math.abs(val) == PAWN) {
					if (this.isValidPawnAttack(new int[] { i, j }, cell)) {
						return true;
					}
				}
				else if (val != 0) {
					if (this.isValidPieceMove(new int[] { i, j }, cell)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean isWhiteInCheck() {
		int[] kingPos = new int[2];

		//find king
		for (int i = 0; i < BOARD_SIZE; i++) {
			for (int j = 0; j < BOARD_SIZE; j++) {
				if (board[i][j] == KING) {
					kingPos[0] = i;
					kingPos[1] = j;
				}
			}
		}

		//for each piece on the other team, check if it is a valid move from that piece location to the king location
		boolean inCheck = isCellAttacked(kingPos, false);

		return inCheck;
	}

	public boolean isBlackInCheck() {
		int[] kingPos = new int[2];

		//find king
		for (int i = 0; i < BOARD_SIZE; i++) {
			for (int j = 0; j < BOARD_SIZE; j++) {
				if (board[i][j] == -KING) {
					kingPos[0] = i;
					kingPos[1] = j;
				}
			}
		}

		//for each piece on the other team, check if it is a valid move from that piece location to the king location
		boolean inCheck = isCellAttacked(kingPos, true);

		return inCheck;
	}

	int[] bishopDR = new int[] { 1, 1, -1, -1 };
	int[] bishopDC = new int[] { -1, 1, -1, 1 };

	int[] rookDR = new int[] { -1, 1, 0, 0 };
	int[] rookDC = new int[] { 0, 0, -1, 1 };

	int[] knightDR = new int[] { -1, -1, 1, 1, -2, -2, 2, 2 };
	int[] knightDC = new int[] { -2, 2, -2, 2, -1, 1, -1, 1 };

	//generates the list of all possible moves in the form: fromRow, fromColumn, toRow, toColumn
	//(if white), then generate all white moves, else, generate black moves
	public ArrayList<int[]> generateAllLegalMoves(boolean white) {
		ArrayList<int[]> moves = new ArrayList<>();
		for (int i = 0; i < BOARD_SIZE; i++) {
			for (int j = 0; j < BOARD_SIZE; j++) {
				int val = this.board[i][j];
				if (val == 0 || (white && val < 0) || (!white && val > 0)) { //not a piece, or piece on the wrong side
					continue;
				}

				int[] from = new int[] { i, j };

				val = Math.abs(val);

				if (val == PAWN) {
					//moves
					if (this.isLegalMove(from, new int[] { i + 2, j })) {
						moves.add(new int[] { i, j, i + 2, j });
					}
					if (this.isLegalMove(from, new int[] { i + 1, j })) {
						moves.add(new int[] { i, j, i + 1, j });
					}
					if (this.isLegalMove(from, new int[] { i - 2, j })) {
						moves.add(new int[] { i, j, i - 2, j });
					}
					if (this.isLegalMove(from, new int[] { i - 1, j })) {
						moves.add(new int[] { i, j, i - 1, j });
					}

					//attacks
					if (this.isLegalMove(from, new int[] { i + 1, j + 1 })) {
						moves.add(new int[] { i, j, i + 1, j + 1 });
					}
					if (this.isLegalMove(from, new int[] { i - 1, j + 1 })) {
						moves.add(new int[] { i, j, i - 1, j + 1 });
					}
					if (this.isLegalMove(from, new int[] { i - 1, j - 1 })) {
						moves.add(new int[] { i, j, i - 1, j - 1 });
					}
					if (this.isLegalMove(from, new int[] { i + 1, j - 1 })) {
						moves.add(new int[] { i, j, i + 1, j - 1 });
					}
				}
				else if (val == KNIGHT) {
					for (int k = 0; k < knightDR.length; k++) {
						int nextR = i + knightDR[k];
						int nextC = j + knightDC[k];
						if (this.isLegalMove(from, new int[] { nextR, nextC })) {
							moves.add(new int[] { i, j, nextR, nextC });
						}
					}
				}
				else if (val == BISHOP) {
					for (int k = 0; k < bishopDR.length; k++) {
						int dr = bishopDR[k];
						int dc = bishopDC[k];
						int curR = i + dr;
						int curC = j + dc;
						while (this.isLegalMove(from, new int[] { curR, curC })) {
							moves.add(new int[] { i, j, curR, curC });
							curR += dr;
							curC += dc;
						}
					}
				}
				else if (val == ROOK) {
					for (int k = 0; k < rookDR.length; k++) {
						int dr = rookDR[k];
						int dc = rookDC[k];
						int curR = i + dr;
						int curC = j + dc;
						while (this.isLegalMove(from, new int[] { curR, curC })) {
							moves.add(new int[] { i, j, curR, curC });
							curR += dr;
							curC += dc;
						}
					}
				}
				else if (val == QUEEN) {
					//combination of rook and bishop
					for (int k = 0; k < bishopDR.length; k++) {
						int dr = bishopDR[k];
						int dc = bishopDC[k];
						int curR = i + dr;
						int curC = j + dc;
						while (this.isLegalMove(from, new int[] { curR, curC })) {
							moves.add(new int[] { i, j, curR, curC });
							curR += dr;
							curC += dc;
						}
					}
					for (int k = 0; k < rookDR.length; k++) {
						int dr = rookDR[k];
						int dc = rookDC[k];
						int curR = i + dr;
						int curC = j + dc;
						while (this.isLegalMove(from, new int[] { curR, curC })) {
							moves.add(new int[] { i, j, curR, curC });
							curR += dr;
							curC += dc;
						}
					}
				}
				else if (val == KING) {
					//combination of rook and bishop, but only 1 move in each direction
					for (int k = 0; k < bishopDR.length; k++) {
						int dr = bishopDR[k];
						int dc = bishopDC[k];
						int curR = i + dr;
						int curC = j + dc;
						if (this.isLegalMove(from, new int[] { curR, curC })) {
							moves.add(new int[] { i, j, curR, curC });
						}
					}
					for (int k = 0; k < rookDR.length; k++) {
						int dr = rookDR[k];
						int dc = rookDC[k];
						int curR = i + dr;
						int curC = j + dc;
						if (this.isLegalMove(from, new int[] { curR, curC })) {
							moves.add(new int[] { i, j, curR, curC });
						}
					}

					//castling
					if (this.isLegalMove(from, new int[] { i, j + 2 })) {
						moves.add(new int[] { i, j, i, j + 2 });
					}
					if (this.isLegalMove(from, new int[] { i, j - 2 })) {
						moves.add(new int[] { i, j, i, j - 2 });
					}
				}
			}
		}
		return moves;
	}

}
