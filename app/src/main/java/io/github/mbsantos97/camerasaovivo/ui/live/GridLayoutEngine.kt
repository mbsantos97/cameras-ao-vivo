package io.github.mbsantos97.camerasaovivo.ui.live

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class GridLayoutMode { AUTO, BIG_PLUS_2, BIG_PLUS_3 }

/** Item da grade. [tall] = lente dupla empilhada em "Coluna dupla" (ocupa 2 quadrantes na vertical). */
@Immutable
data class GridItem(
    val id: String,
    val tall: Boolean = false,
)

sealed interface CellContent {
    data class Source(
        val id: String,
        /** false quando um item "alto" foi colocado num quadrante simples (vira Compacto). */
        val tallPlacement: Boolean = false,
    ) : CellContent

    data object Panel : CellContent

    data object Empty : CellContent
}

@Immutable
data class GridCell(
    val content: CellContent,
    val col: Int,
    val row: Int,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
)

@Immutable
data class GridSpec(
    val columns: Int,
    val rows: Int,
    val cells: List<GridCell>,
    val page: Int = 0,
    val pageCount: Int = 1,
) {
    fun cellOf(id: String): GridCell? = cells.firstOrNull { (it.content as? CellContent.Source)?.id == id }

    companion object {
        val EMPTY = GridSpec(1, 1, listOf(GridCell(CellContent.Panel, 0, 0)))
    }
}

/**
 * Calcula a grade. Automático: 1 fonte → tela cheia; 2 → lado a lado; 3–4 → 2x2; 5–6 → 3x2; 7–9 → 3x3;
 * acima de 9 quadrantes, páginas de 3x3. O quadrante que sobra vira o Painel.
 */
object GridLayoutEngine {
    private val autoShapes = listOf(1 to 1, 2 to 1, 2 to 2, 3 to 2, 3 to 3)
    private const val MAX_COLS = 3
    private const val MAX_ROWS = 3

    fun compute(
        items: List<GridItem>,
        mode: GridLayoutMode = GridLayoutMode.AUTO,
        page: Int = 0,
        showPanel: Boolean = true,
    ): GridSpec {
        if (items.isEmpty()) return GridSpec.EMPTY
        return when (mode) {
            GridLayoutMode.AUTO -> auto(items, page, showPanel)
            GridLayoutMode.BIG_PLUS_2 -> big(items, page, showPanel, smallCount = 2)
            GridLayoutMode.BIG_PLUS_3 -> big(items, page, showPanel, smallCount = 3)
        }
    }

    private fun units(item: GridItem) = if (item.tall) 2 else 1

    /** Divide em páginas que caibam numa grade 3x3. */
    internal fun paginate(items: List<GridItem>): List<List<GridItem>> {
        val pages = mutableListOf<MutableList<GridItem>>()
        var current = mutableListOf<GridItem>()
        for (item in items) {
            val trial = current + item
            if (current.isNotEmpty() && place(trial, MAX_COLS, MAX_ROWS) == null) {
                pages += current
                current = mutableListOf()
            }
            current += item
        }
        if (current.isNotEmpty()) pages += current
        return pages
    }

    private fun auto(
        all: List<GridItem>,
        page: Int,
        showPanel: Boolean,
    ): GridSpec {
        val pages = paginate(all)
        val p = page.coerceIn(0, pages.size - 1)
        val items = pages[p]
        val total = items.sumOf(::units)
        val anyTall = items.any { it.tall }
        for ((cols, rows) in autoShapes) {
            if (cols * rows < total) continue
            if (anyTall && rows < 2) continue
            val placed = place(items, cols, rows) ?: continue
            return GridSpec(cols, rows, fillFree(placed, cols, rows, showPanel), p, pages.size)
        }
        // Não deveria acontecer (paginate garante 3x3); por segurança, sem spans.
        val flat = items.map { it.copy(tall = false) }
        val placed = place(flat, MAX_COLS, MAX_ROWS)!!
        return GridSpec(MAX_COLS, MAX_ROWS, fillFree(placed, MAX_COLS, MAX_ROWS, showPanel), p, pages.size)
    }

    /** Coloca primeiro os itens altos (da esquerda para a direita), depois os simples em ordem. */
    private fun place(
        items: List<GridItem>,
        cols: Int,
        rows: Int,
    ): List<GridCell>? {
        val used = Array(rows) { BooleanArray(cols) }
        val cells = mutableListOf<GridCell>()
        for (item in items.filter { it.tall }) {
            var done = false
            loop@ for (c in 0 until cols) {
                for (r in 0 until rows - 1) {
                    if (!used[r][c] && !used[r + 1][c]) {
                        used[r][c] = true
                        used[r + 1][c] = true
                        cells += GridCell(CellContent.Source(item.id, tallPlacement = true), c, r, 1, 2)
                        done = true
                        break@loop
                    }
                }
            }
            if (!done) return null
        }
        for (item in items.filter { !it.tall }) {
            var done = false
            loop@ for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (!used[r][c]) {
                        used[r][c] = true
                        cells += GridCell(CellContent.Source(item.id), c, r)
                        done = true
                        break@loop
                    }
                }
            }
            if (!done) return null
        }
        return cells
    }

    /** Primeiro quadrante livre vira o Painel (estendido para baixo se couber); o resto fica vazio. */
    private fun fillFree(
        placed: List<GridCell>,
        cols: Int,
        rows: Int,
        showPanel: Boolean,
    ): List<GridCell> {
        val used = Array(rows) { BooleanArray(cols) }
        for (cell in placed) {
            for (r in cell.row until cell.row + cell.rowSpan) {
                for (c in cell.col until cell.col + cell.colSpan) used[r][c] = true
            }
        }
        val out = placed.toMutableList()
        var panelPlaced = !showPanel
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (used[r][c]) continue
                if (!panelPlaced) {
                    var span = 1
                    while (r + span < rows && !used[r + span][c]) span++
                    for (k in 0 until span) used[r + k][c] = true
                    out += GridCell(CellContent.Panel, c, r, 1, span)
                    panelPlaced = true
                } else {
                    used[r][c] = true
                    out += GridCell(CellContent.Empty, c, r)
                }
            }
        }
        return out
    }

    private fun big(
        all: List<GridItem>,
        page: Int,
        showPanel: Boolean,
        smallCount: Int,
    ): GridSpec {
        val perPage = 1 + smallCount
        val pages = all.chunked(perPage)
        val p = page.coerceIn(0, pages.size - 1)
        val items = pages[p]
        val cols = 3
        val rows = if (smallCount == 2) 2 else 3
        val cells = mutableListOf<GridCell>()
        cells += GridCell(CellContent.Source(items[0].id), 0, 0, 2, 2)
        for (i in 0 until smallCount) {
            val item = items.getOrNull(i + 1)
            cells += GridCell(if (item != null) CellContent.Source(item.id) else CellContent.Empty, 2, i)
        }
        if (smallCount == 3) {
            cells += GridCell(if (showPanel) CellContent.Panel else CellContent.Empty, 0, 2, 2, 1)
        }
        // Se sobrou quadrante pequeno vazio e ainda não há Painel, ele vira o Painel.
        if (showPanel && cells.none { it.content == CellContent.Panel }) {
            val idx = cells.indexOfFirst { it.content == CellContent.Empty }
            if (idx >= 0) cells[idx] = cells[idx].copy(content = CellContent.Panel)
        }
        return GridSpec(cols, rows, cells, p, pages.size)
    }
}
