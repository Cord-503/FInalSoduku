package com.example.finalsoduku

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Difficulty enum class
enum class Difficulty(val visibleCells: Int) {
    EASY(50),
    MEDIUM(40),
    HARD(30)
}

class MainActivity : AppCompatActivity() {

    private lateinit var sudokuGrid: GridLayout
    private lateinit var generator: SudokuGenerator
    private var selectedNumber = 0 // 0 means no number is selected
    private lateinit var numberButtons: List<Button>
    private lateinit var eraseToggleButton: ToggleButton
    private lateinit var notesToggleButton: ToggleButton

    // Board and solution variables
    private lateinit var originalPuzzle: Array<IntArray>
    private lateinit var solution: Array<IntArray>
    private lateinit var currentBoard: Array<IntArray>
    private lateinit var notesData: Array<Array<MutableSet<Int>>>

    // State control variables
    private var isGameActive = false
    private var isNotesMode = false
    private var isEraseMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sudokuGrid = findViewById(R.id.sudoku_grid)
        generator = SudokuGenerator()

        setupControlButtons()
        setupNewGameButton()
        startNewGame()
        setupDifficultySpinner()

    }

    private fun setupDifficultySpinner() {
        val difficultySpinner: Spinner = findViewById(R.id.difficulty_spinner)

        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.difficulty_levels,
            R.layout.spinner_item_black_text // 这是选中项的布局 (20sp)
        )

        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)

        difficultySpinner.adapter = adapter
    }

    private fun setupControlButtons() {
        notesToggleButton = findViewById(R.id.btn_notes_toggle)
        eraseToggleButton = findViewById(R.id.btn_erase_toggle)

        // Notes Button Listener
        notesToggleButton.setOnCheckedChangeListener { _, isChecked ->
            isNotesMode = isChecked
            if (isChecked) {
                // If notes mode is turned on, turn off erase mode
                isEraseMode = false
                eraseToggleButton.isChecked = false
            }
        }

        // Erase Button Listener
        eraseToggleButton.setOnCheckedChangeListener { _, isChecked ->
            isEraseMode = isChecked
            if (isChecked) {
                // If erase mode is turned on, turn off notes mode
                isNotesMode = false
                notesToggleButton.isChecked = false
                // Also deselect any number, as erase doesn't use one
                selectedNumber = 0
                updateNumberButtonSelection(null)
            }
        }

        // Number Buttons Listeners
        val numButtonIds = listOf(
            R.id.btn_num_1, R.id.btn_num_2, R.id.btn_num_3, R.id.btn_num_4,
            R.id.btn_num_5, R.id.btn_num_6, R.id.btn_num_7, R.id.btn_num_8, R.id.btn_num_9
        )
        numberButtons = numButtonIds.map { findViewById(it) }
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val currentNumber = index + 1
                selectedNumber = if (selectedNumber == currentNumber) 0 else currentNumber
                updateNumberButtonSelection(if (selectedNumber == 0) null else button)

                // **LOGIC FIX:** Only turn off modes if a number is selected AND we are not already in a mode.
                // If we are in notes mode, selecting a number should NOT turn it off.
                if (selectedNumber != 0) {
                    if (isEraseMode) {
                        isEraseMode = false
                        eraseToggleButton.isChecked = false
                    }
                }
            }
        }
    }

    // This function now ONLY handles the visual state of the number buttons
    private fun updateNumberButtonSelection(selectedBtn: Button?) {
        // Reset all number buttons to gray
        numberButtons.forEach {
            it.background = ContextCompat.getDrawable(this, R.drawable.button_background_gray)
        }

        // If a number button is selected (not null), set its background to orange
        selectedBtn?.background = ContextCompat.getDrawable(this, R.drawable.button_background_orange)
    }

    // Updated handleCellClick to include the new erase mode logic
    @SuppressLint("SetTextI18n")
    private fun handleCellClick(cellView: View, row: Int, col: Int) {
        if (!isGameActive || originalPuzzle[row][col] != 0) return

        val frameLayout = cellView as FrameLayout
        val mainTextView = frameLayout.getChildAt(0) as TextView
        val notesGrid = frameLayout.getChildAt(1) as GridLayout

        if (isEraseMode) {
            // --- ERASE MODE ---
            mainTextView.text = ""
            currentBoard[row][col] = 0
            notesData[row][col].clear() // Also clear any notes in the cell
            (0 until notesGrid.childCount).forEach { (notesGrid.getChildAt(it) as TextView).visibility = View.INVISIBLE }
            notesGrid.visibility = View.GONE

        } else if (isNotesMode) {
            // --- NOTES MODE ---
            if (selectedNumber in 1..9) {
                mainTextView.text = "" // Ensure main number is cleared
                currentBoard[row][col] = 0
                notesGrid.visibility = View.VISIBLE
                val noteTextView = notesGrid.getChildAt(selectedNumber - 1) as TextView
                if (notesData[row][col].contains(selectedNumber)) {
                    notesData[row][col].remove(selectedNumber)
                    noteTextView.visibility = View.INVISIBLE
                } else {
                    notesData[row][col].add(selectedNumber)
                    noteTextView.visibility = View.VISIBLE
                }
            }
        } else {
            // --- NORMAL INPUT MODE ---
            if (selectedNumber in 1..9) {
                // Clear notes and hide the notes grid when inputting a final number
                notesData[row][col].clear()
                (0 until notesGrid.childCount).forEach { (notesGrid.getChildAt(it) as TextView).visibility = View.INVISIBLE }
                notesGrid.visibility = View.GONE

                mainTextView.text = selectedNumber.toString()
                currentBoard[row][col] = selectedNumber

                // Validate the number placement and set color accordingly
                if (!isValidPlacement(currentBoard, row, col, selectedNumber)) {
                    mainTextView.setTextColor(Color.RED)
                } else {
                    mainTextView.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
                }

                if (isBoardFilled(currentBoard)) {
                    checkSolution()
                }
            }
        }
    }

    // --- The rest of the functions remain the same ---

    private fun setupEmptyBoard() {
        val emptyBoard = Array(9) { IntArray(9) }
        notesData = Array(9) { Array(9) { mutableSetOf() } }
        setupSudokuBoard(emptyBoard)
    }

    private fun startNewGame() {
        val difficulty = when (findViewById<Spinner>(R.id.difficulty_spinner).selectedItemPosition) {
            0 -> Difficulty.EASY
            1 -> Difficulty.MEDIUM
            2 -> Difficulty.HARD
            else -> Difficulty.EASY
        }

        solution = generator.generateFullSudoku()
        originalPuzzle = generator.createPuzzle(solution, difficulty)
        currentBoard = originalPuzzle.map { it.clone() }.toTypedArray()
        notesData = Array(9) { Array(9) { mutableSetOf() } } // Reset notes
        setupSudokuBoard(currentBoard)
        isGameActive = true
    }

    private fun setupNewGameButton() {
        findViewById<Button>(R.id.btn_new_game).setOnClickListener {
            startNewGame()
        }
    }

    private fun setupSudokuBoard(puzzle: Array<IntArray>) {
        sudokuGrid.removeAllViews()
        sudokuGrid.post {
            val cellSize = sudokuGrid.width / 9

            for (row in 0..8) {
                for (col in 0..8) {
                    val cellView = createSudokuCellView(puzzle[row][col], row, col)
                    val params = GridLayout.LayoutParams().apply {
                        width = cellSize
                        height = cellSize
                        rowSpec = GridLayout.spec(row)
                        columnSpec = GridLayout.spec(col)
                    }
                    sudokuGrid.addView(cellView, params)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createSudokuCellView(value: Int, row: Int, col: Int): View {
        val frameLayout = FrameLayout(this)
        val blockIndex = (row / 3) * 3 + (col / 3)
        frameLayout.background = when (blockIndex % 2) {
            0 -> ContextCompat.getDrawable(this, R.drawable.cell_border)
            else -> ContextCompat.getDrawable(this, R.drawable.cell_border_alt)
        }
        val mainTextView = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        }
        val notesGrid = GridLayout(this).apply {
            rowCount = 3
            columnCount = 3
            visibility = View.GONE
            for (i in 1..9) {
                addView(TextView(context).apply {
                    text = i.toString()
                    setTextColor(ContextCompat.getColor(context, R.color.noteColor))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    gravity = Gravity.CENTER
                    visibility = View.INVISIBLE
                    layoutParams = GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f),
                        GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    ).apply {
                        width = 0
                        height = 0
                    }
                })
            }
        }
        frameLayout.addView(mainTextView)
        frameLayout.addView(notesGrid)
        if (value != 0) {
            mainTextView.text = value.toString()
            mainTextView.setTextColor(Color.BLACK)
        } else {
            frameLayout.setOnClickListener {
                if (!isGameActive) return@setOnClickListener
                handleCellClick(it, row, col)
            }
        }
        return frameLayout
    }

    private fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        for (c in 0..8) {
            if (c != col && board[row][c] == num) return false
        }
        for (r in 0..8) {
            if (r != row && board[r][col] == num) return false
        }
        val boxRowStart = row - row % 3
        val boxColStart = col - col % 3
        for (r in boxRowStart until boxRowStart + 3) {
            for (c in boxColStart until boxColStart + 3) {
                if ((r != row || c != col) && board[r][c] == num) return false
            }
        }
        return true
    }

    private fun isBoardFilled(board: Array<IntArray>): Boolean {
        return board.all { row -> row.all { it != 0 } }
    }

    private fun isBoardValid(board: Array<IntArray>): Boolean {
        // Check rows for validity
        for (r in 0..8) {
            val seen = mutableSetOf<Int>()
            for (c in 0..8) {
                val num = board[r][c]
                // If a cell is empty (0) or a number is repeated in the row, it's invalid.
                if (num == 0 || !seen.add(num)) {
                    return false
                }
            }
        }

        // Check columns for validity
        for (c in 0..8) {
            val seen = mutableSetOf<Int>()
            for (r in 0..8) {
                val num = board[r][c]
                // If a number is repeated in the column, it's invalid.
                if (!seen.add(num)) {
                    return false
                }
            }
        }

        // Check 3x3 boxes for validity
        for (boxRow in 0..2) {
            for (boxCol in 0..2) {
                val seen = mutableSetOf<Int>()
                for (r in (boxRow * 3) until (boxRow * 3 + 3)) {
                    for (c in (boxCol * 3) until (boxCol * 3 + 3)) {
                        val num = board[r][c]
                        // If a number is repeated in the 3x3 box, it's invalid.
                        if (!seen.add(num)) {
                            return false
                        }
                    }
                }
            }
        }

        // If all checks passed, the board is a valid solution.
        return true
    }

    private fun checkSolution() {
        val isCorrect = isBoardValid(currentBoard)

        val title = if (isCorrect) "Congratulations!" else "Not Quite"
        val message = if (isCorrect) "You solved the puzzle successfully!" else "There are some mistakes in your solution."
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("New Game") { _, _ -> startNewGame() }
            .setNegativeButton("Continue") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }
}

// Sudoku Generator Class
class SudokuGenerator {
    fun generateFullSudoku(): Array<IntArray> {
        val board = Array(9) { IntArray(9) }
        fillDiagonalBlocks(board)
        solveSudoku(board)
        return board
    }

    private fun fillDiagonalBlocks(board: Array<IntArray>) {
        for (i in 0..2) {
            fillBox(board, i * 3, i * 3)
        }
    }

    private fun fillBox(board: Array<IntArray>, row: Int, col: Int) {
        val numbers = (1..9).shuffled()
        var k = 0
        for (i in 0..2) {
            for (j in 0..2) {
                board[row + i][col + j] = numbers[k]
                k++
            }
        }
    }

    private fun solveSudoku(board: Array<IntArray>): Boolean {
        for (row in 0..8) {
            for (col in 0..8) {
                if (board[row][col] == 0) {
                    for (num in (1..9).shuffled()) {
                        if (isValidToPlace(board, row, col, num)) {
                            board[row][col] = num
                            if (solveSudoku(board)) {
                                return true
                            }
                            board[row][col] = 0 // Backtrack
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
        var removed = 0

        val cellCoords = (0..80).toMutableList()
        cellCoords.shuffle()

        while (removed < cellsToRemove && cellCoords.isNotEmpty()) {
            val cellIndex = cellCoords.removeAt(0)
            val row = cellIndex / 9
            val col = cellIndex % 9

            if (puzzle[row][col] != 0) {
                puzzle[row][col] = 0
                removed++
            }
        }
        return puzzle
    }

    private fun isValidToPlace(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row and column
        for (i in 0..8) {
            if (board[row][i] == num || board[i][col] == num) {
                return false
            }
        }
        // Check 3x3 box
        val startRow = row - row % 3
        val startCol = col - col % 3
        for (i in 0..2) {
            for (j in 0..2) {
                if (board[startRow + i][startCol + j] == num) {
                    return false
                }
            }
        }
        return true
    }
}