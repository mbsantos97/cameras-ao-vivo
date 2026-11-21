package io.github.mbsantos97.camerasaovivo.onvif.parser

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Nó XML simplificado. Nomes de elementos e atributos são guardados sem prefixo de namespace
 * (casamento pelo local name), o que tolera respostas SOAP fora do padrão de firmware barato.
 */
class XmlNode(
    val name: String,
    val attrs: Map<String, String>,
    val children: List<XmlNode>,
    val text: String,
) {
    fun child(local: String): XmlNode? = children.firstOrNull { it.name.equals(local, ignoreCase = true) }

    fun childrenNamed(local: String): List<XmlNode> = children.filter { it.name.equals(local, ignoreCase = true) }

    /** Primeiro descendente (busca em profundidade, inclui o próprio nó). */
    fun find(local: String): XmlNode? {
        if (name.equals(local, ignoreCase = true)) return this
        for (c in children) c.find(local)?.let { return it }
        return null
    }

    fun findAll(local: String): List<XmlNode> {
        val out = mutableListOf<XmlNode>()

        fun walk(n: XmlNode) {
            if (n.name.equals(local, ignoreCase = true)) out += n
            n.children.forEach(::walk)
        }
        walk(this)
        return out
    }

    /** Caminho de filhos diretos: path("Resolution", "Width"). */
    fun path(vararg names: String): XmlNode? {
        var n: XmlNode? = this
        for (p in names) n = n?.child(p) ?: return null
        return n
    }

    fun textOf(local: String): String? = find(local)?.text?.trim()?.ifEmpty { null }

    fun attr(local: String): String? = attrs.entries.firstOrNull { it.key.equals(local, ignoreCase = true) }?.value
}

object Xml {
    /** Fábrica do parser (nos testes JVM é trocada por kXML2). */
    @Volatile
    var parserFactory: () -> XmlPullParser = { XmlPullParserFactory.newInstance().newPullParser() }

    private fun local(name: String) = name.substringAfter(':')

    private val BOM = Char(0xFEFF)

    fun parse(xml: String): XmlNode {
        val p = parserFactory()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml.trimStart { it == BOM || it.isWhitespace() }))

        data class Building(
            val name: String,
            val attrs: Map<String, String>,
            val children: MutableList<XmlNode> = mutableListOf(),
            val text: StringBuilder = StringBuilder(),
        )
        val stack = ArrayDeque<Building>()
        var root: XmlNode? = null
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val attrs = (0 until p.attributeCount).associate { local(p.getAttributeName(it)) to p.getAttributeValue(it) }
                    stack.addLast(Building(local(p.name), attrs))
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> stack.lastOrNull()?.text?.append(p.text)
                XmlPullParser.END_TAG -> {
                    val b = stack.removeLast()
                    val node = XmlNode(b.name, b.attrs, b.children, b.text.toString())
                    if (stack.isEmpty()) root = node else stack.last().children += node
                }
            }
            event = p.next()
        }
        return root ?: throw IllegalArgumentException("XML vazio")
    }
}
