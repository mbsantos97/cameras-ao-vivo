package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.onvif.parser.Xml
import io.github.mbsantos97.camerasaovivo.onvif.parser.XmlNode
import org.kxml2.io.KXmlParser

/** Na JVM o XmlPullParser vem do kXML2 (no Android, do sistema). */
object TestXml {
    init {
        Xml.parserFactory = { KXmlParser() }
    }

    fun install() = Unit
}

fun fixture(name: String): String {
    TestXml.install()
    return requireNotNull(TestXml::class.java.classLoader!!.getResource("onvif/$name.xml")) { "Fixture $name não encontrada" }.readText()
}

fun fixtureXml(name: String): XmlNode = Xml.parse(fixture(name))
