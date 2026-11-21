package io.github.mbsantos97.camerasaovivo.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLayoutEngineTest {
    private fun items(n: Int) = (1..n).map { GridItem("c$it") }

    private fun shape(spec: GridSpec) = spec.columns to spec.rows

    @Test
    fun `automatico escolhe a grade pelo numero de fontes`() {
        val expected = listOf(1 to 1, 2 to 1, 2 to 2, 2 to 2, 3 to 2, 3 to 2, 3 to 3, 3 to 3, 3 to 3)
        expected.forEachIndexed { i, s -> assertEquals("n=${i + 1}", s, shape(GridLayoutEngine.compute(items(i + 1)))) }
    }

    @Test
    fun `uma fonte ocupa a tela cheia sem painel`() {
        val spec = GridLayoutEngine.compute(items(1))
        assertEquals(1, spec.cells.size)
        assertEquals(CellContent.Source("c1"), spec.cells[0].content)
    }

    @Test
    fun `quadrante que sobra vira o painel`() {
        val panel = GridLayoutEngine.compute(items(3)).cells.single { it.content == CellContent.Panel }
        assertEquals(1 to 1, panel.col to panel.row)
    }

    @Test
    fun `cenario atual - lente dupla e lampada em 2x2 com painel embaixo a direita`() {
        val spec = GridLayoutEngine.compute(listOf(GridItem("externa", tall = true), GridItem("lampada")))
        assertEquals(2 to 2, shape(spec))
        val ext = spec.cellOf("externa")!!
        assertEquals(listOf(0, 0, 1, 2), listOf(ext.col, ext.row, ext.colSpan, ext.rowSpan))
        assertTrue((ext.content as CellContent.Source).tallPlacement)
        assertEquals(1 to 0, spec.cellOf("lampada")!!.let { it.col to it.row })
        val panel = spec.cells.single { it.content == CellContent.Panel }
        assertEquals(1 to 1, panel.col to panel.row)
    }

    @Test
    fun `lente dupla sozinha usa coluna e painel estendido`() {
        val spec = GridLayoutEngine.compute(listOf(GridItem("ext", tall = true)))
        assertEquals(2 to 2, shape(spec))
        assertEquals(2, spec.cells.single { it.content == CellContent.Panel }.rowSpan)
    }

    @Test
    fun `nenhuma celula se sobrepoe e a grade fica toda coberta`() {
        for (n in 1..9) {
            for (talls in 0..2) {
                val list = items(n).mapIndexed { i, it -> if (i < talls) it.copy(tall = true) else it }
                val spec = GridLayoutEngine.compute(list)
                val used = Array(spec.rows) { IntArray(spec.columns) }
                spec.cells.forEach { c ->
                    for (r in c.row until c.row + c.rowSpan) for (k in c.col until c.col + c.colSpan) used[r][k]++
                }
                assertTrue("n=$n talls=$talls", used.all { row -> row.all { it == 1 } })
            }
        }
    }

    @Test
    fun `mais de 9 fontes gera paginas`() {
        val spec0 = GridLayoutEngine.compute(items(11), page = 0)
        assertEquals(2, spec0.pageCount)
        assertEquals(9, spec0.cells.count { it.content is CellContent.Source })
        val spec1 = GridLayoutEngine.compute(items(11), page = 1)
        assertEquals(2, spec1.cells.count { it.content is CellContent.Source })
        assertEquals(2 to 1, shape(spec1))
    }

    @Test
    fun `um grande mais dois`() {
        val spec = GridLayoutEngine.compute(items(3), GridLayoutMode.BIG_PLUS_2)
        assertEquals(3 to 2, shape(spec))
        assertEquals(2 to 2, spec.cellOf("c1")!!.let { it.colSpan to it.rowSpan })
        assertEquals(2 to 0, spec.cellOf("c2")!!.let { it.col to it.row })
        assertEquals(2 to 1, spec.cellOf("c3")!!.let { it.col to it.row })
    }

    @Test
    fun `um grande mais tres tem painel na faixa de baixo`() {
        val spec = GridLayoutEngine.compute(items(4), GridLayoutMode.BIG_PLUS_3)
        assertEquals(3 to 3, shape(spec))
        val panel = spec.cells.single { it.content == CellContent.Panel }
        assertEquals(listOf(0, 2, 2), listOf(panel.col, panel.row, panel.colSpan))
    }

    @Test
    fun `um grande mais dois com uma fonte so preenche o vazio com painel`() {
        assertEquals(1, GridLayoutEngine.compute(items(1), GridLayoutMode.BIG_PLUS_2).cells.count { it.content == CellContent.Panel })
    }

    @Test
    fun `lista vazia devolve so o painel`() {
        assertEquals(GridSpec.EMPTY, GridLayoutEngine.compute(emptyList()))
    }
}
