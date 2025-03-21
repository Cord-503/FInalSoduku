package com.example.finalsoduku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var sudokuGrid: GridLayout
    private lateinit var generator: SudokuGenerator
    private var selectedNumber = 1
    private lateinit var selectedNumberButtons: List<Button>

    // To track the original puzzle and solution
    private lateinit var originalPuzzle: Array<IntArray>
    private lateinit var solution: Array<IntArray>
    private lateinit var currentBoard: Array<IntArray>
    private var isGameActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sudokuGrid = findViewById(R.id.sudoku_grid)
        generator = SudokuGenerator()

        setupNumberSelector()
        setupNewGameButton()
        setupEmptyBoard()
    }

    private fun setupEmptyBoard() {
        // Initialize with an empty board until New Game is pressed
        val emptyBoard = Array(9) { IntArray(9) }
        setupSudokuBoard(emptyBoard)
    }

    private fun setupNewGameButton() {
        findViewById<Button>(R.id.btn_new_game).setOnClickListener {
            val difficulty = when (findViewById<Spinner>(R.id.difficulty_spinner).selectedItemPosition) {
                0 -> Difficulty.EASY
                1 -> Difficulty.MEDIUM
                else -> Difficulty.HARD
            }

            solution = generator.generateFullSudoku()
            originalPuzzle = generator.createPuzzle(solution, difficulty)
            currentBoard = originalPuzzle.map { it.clone() }.toTypedArray()
            setupSudokuBoard(currentBoard)
            isGameActive = true
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupNumberSelector() {
        val numberLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16.dpToPx()
                bottomMargin = 16.dpToPx()
            }
        }

        val buttons = mutableListOf<Button>()
        for (i in 1..9) {
            val btn = Button(this).apply {
                text = "$i"
                layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply {
                    marginEnd = 4.dpToPx()
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    selectedNumber = i
                    // Highlight selected button
                    updateNumberButtonSelection(this)
                }
            }
            buttons.add(btn)
            numberLayout.addView(btn)
        }
        selectedNumberButtons = buttons

        // Add an "Erase" button
        val eraseBtn = Button(this).apply {
            text = "X"
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener {
                selectedNumber = 0
                updateNumberButtonSelection(this)
            }
        }
        buttons.add(eraseBtn)
        numberLayout.addView(eraseBtn)

        // Add the number buttons below the spinner
        val rootLayout = findViewById<View>(R.id.difficulty_spinner).parent as ViewGroup
        rootLayout.addView(numberLayout, rootLayout.indexOfChild(findViewById(R.id.difficulty_spinner)) + 1)
    }

    private fun updateNumberButtonSelection(selected: Button) {
        // Reset all buttons
        for (button in selectedNumberButtons) {
            if (button == selected) {
                button.alpha = 1.0f
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            } else {
                button.alpha = 0.8f
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
            }
        }
    }

    private fun setupSudokuBoard(puzzle: Array<IntArray>) {
        sudokuGrid.removeAllViews()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels - 32.dpToPx() // 16dp padding on each side
        val cellSize = screenWidth / 9

        for (row in 0..8) {
            for (col in 0..8) {
                val value = puzzle[row][col]
                val cell = createSudokuCell(value, cellSize, row, col)

                // Save the row and col positions with the cell
                cell.tag = "$row,$col"

                setupCellAppearance(cell, row, col)

                // Set the cell's position in the grid
                val params = GridLayout.LayoutParams()
                params.width = cellSize
                params.height = cellSize
                params.rowSpec = GridLayout.spec(row)
                params.columnSpec = GridLayout.spec(col)

                // Add margins for block separation
                val blockMargin = 2
                val cellMargin = 1
                params.setMargins(
                    if (col % 3 == 0) blockMargin else cellMargin,
                    if (row % 3 == 0) blockMargin else cellMargin,
                    if (col % 3 == 2) blockMargin else cellMargin,
                    if (row % 3 == 2) blockMargin else cellMargin
                )

                cell.layoutParams = params
                sudokuGrid.addView(cell)
            }
        }
    }

    // DP to pixel conversion utility
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun createSudokuCell(value: Int, size: Int, row: Int, col: Int): TextView {
        return TextView(this).apply {
            if (value != 0) {
                text = value.toString()
                setTextColor(Color.BLACK) // Fixed numbers are black
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                // Fixed cells from the original puzzle are not clickable
                isClickable = false
            } else {
                // Empty cells
                text = ""
                setTextColor(ContextCompat.getColor(context, R.color.colorAccent)) // User input color
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                // Make empty cells clickable
                setOnClickListener { cellView ->
                    if (!isGameActive) return@setOnClickListener

                    val position = cellView.tag.toString().split(",")
                    val r = position[0].toInt()
                    val c = position[1].toInt()

                    // Only allow changes to empty cells in the original puzzle
                    if (originalPuzzle[r][c] == 0) {
                        // Update the cell text based on selected number
                        if (selectedNumber == 0) {
                            (cellView as TextView).text = ""
                            currentBoard[r][c] = 0
                        } else {
                            (cellView as TextView).text = selectedNumber.toString()
                            currentBoard[r][c] = selectedNumber

                            // Highlight invalid placements
                            if (!isValidPlacement(currentBoard, r, c, selectedNumber)) {
                                cellView.setTextColor(Color.RED)
                            } else {
                                cellView.setTextColor(ContextCompat.getColor(context, R.color.colorAccent))
                            }
                        }

                        // Check if puzzle is complete
                        if (isBoardFilled(currentBoard)) {
                            checkSolution()
                        }
                    }
                }
            }

            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.cell_border)
        }
    }

    private fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row
        for (c in 0..8) {
            if (c != col && board[row][c] == num) return false
        }

        // Check column
        for (r in 0..8) {
            if (r != row && board[r][col] == num) return false
        }

        // Check 3x3 box
        val boxRow = row - row % 3
        val boxCol = col - col % 3
        for (r in 0..2) {
            for (c in 0..2) {
                if ((boxRow + r != row || boxCol + c != col) &&
                    board[boxRow + r][boxCol + c] == num) return false
            }
        }

        return true
    }

    private fun isBoardFilled(board: Array<IntArray>): Boolean {
        for (row in 0..8) {
            for (col in 0..8) {
                if (board[row][col] == 0) return false
            }
        }
        return true
    }

    private fun checkSolution() {
        var isCorrect = true

        // Check each cell against the solution
        for (row in 0..8) {
            for (col in 0..8) {
                if (currentBoard[row][col] != solution[row][col]) {
                    isCorrect = false
                    break
                }
            }
            if (!isCorrect) break
        }

        // Show completion dialog
        val title = if (isCorrect) "Congratulations!" else "Not Quite Right"
        val message = if (isCorrect) "You've solved the puzzle correctly!"
        else "There are some mistakes in your solution."

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("New Game") { _, _ ->
                val difficulty = when (findViewById<Spinner>(R.id.difficulty_spinner).selectedItemPosition) {
                    0 -> Difficulty.EASY
                    1 -> Difficulty.MEDIUM
                    else -> Difficulty.HARD
                }

                solution = generator.generateFullSudoku()
                originalPuzzle = generator.createPuzzle(solution, difficulty)
                currentBoard = originalPuzzle.map { it.clone() }.toTypedArray()
                setupSudokuBoard(currentBoard)
            }
            .setNegativeButton("Continue") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupCellAppearance(cell: TextView, row: Int, col: Int) {
        // Alternate block backgrounds
        val blockIndex = (row / 3) * 3 + (col / 3)
        cell.background = when (blockIndex % 2) {
            0 -> ContextCompat.getDrawable(this, R.drawable.cell_border)
            else -> ContextCompat.getDrawable(this, R.drawable.cell_border_alt)
        }
    }
}

enum class Difficulty(val visibleCells: Int) {
    EASY(80), MEDIUM(30), HARD(25)
}

class SudokuGenerator {

    fun generateFullSudoku(): Array<IntArray> {
        val board = Array(9) { IntArray(9) }
        fillDiagonalBlocks(board)
        solveSudoku(board)
        return board
    }

    private fun fillDiagonalBlocks(board: Array<IntArray>) {
        for (i in 0..2) {
            val numbers = (1..9).shuffled().toMutableList()
            for (r in 0..2) {
                for (c in 0..2) {
                    board[i*3 + r][i*3 + c] = numbers[r*3 + c]
                }
            }
        }
    }

    private fun solveSudoku(board: Array<IntArray>): Boolean {
        for (row in 0..8) {
            for (col in 0..8) {
                if (board[row][col] == 0) {
                    for (num in (1..9).shuffled()) {
                        if (isValidPlacement(board, row, col, num)) {
                            board[row][col] = num
                            if (solveSudoku(board)) return true
                            board[row][col] = 0
                        }
                    }
                    return false
                }
            }
        }
        return true
    }

    fun createPuzzle(baseBoard: Array<IntArray>, difficulty: Difficulty): Array<IntArray> {
        val puzzle = baseBoard.map { it.clone() }.toTypedArray()
        val cellsToRemove = 81 - difficulty.visibleCells

        val cellsToTry = mutableListOf<Pair<Int, Int>>()
        for (r in 0..8) {
            for (c in 0..8) {
                cellsToTry.add(Pair(r, c))
            }
        }

        cellsToTry.shuffle()
        var removed = 0

        for (cell in cellsToTry) {
            if (removed >= cellsToRemove) break

            val (row, col) = cell
            val temp = puzzle[row][col]
            puzzle[row][col] = 0

            // In a production app, we would verify unique solution here
            // For simplicity, we're skipping that check in this example

            removed++
        }

        return puzzle
    }

    private fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row and column
        for (i in 0..8) {
            if (board[row][i] == num || board[i][col] == num) return false
        }

        // Check 3x3 box
        val startRow = row - row % 3
        val startCol = col - col % 3
        for (r in 0..2) {
            for (c in 0..2) {
                if (board[startRow + r][startCol + c] == num) return false
            }
        }
        return true
    }

    private fun IntRange.shuffled() = this.toList().shuffled()
}