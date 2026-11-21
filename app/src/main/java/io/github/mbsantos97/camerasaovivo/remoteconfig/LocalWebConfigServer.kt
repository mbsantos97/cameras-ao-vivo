package io.github.mbsantos97.camerasaovivo.remoteconfig

import fi.iki.elonen.NanoHTTPD
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class CameraForm(
    val id: String? = null,
    val name: String = "",
    val host: String = "",
    val onvifPort: Int = 5000,
    val rtspPort: Int = 554,
    val username: String = "admin",
    /** Vazio = manter a senha atual. */
    val password: String = "",
)

@Serializable
data class CameraSummary(
    val id: String,
    val name: String,
    val host: String,
    val onvifPort: Int,
    val rtspPort: Int,
    val username: String,
    val hasPassword: Boolean,
)

/** O que o servidor precisa do app (injetado para testar sem Android). */
interface RemoteConfigBackend {
    fun list(): List<CameraSummary>

    suspend fun save(form: CameraForm): String

    suspend fun delete(id: String)
}

/**
 * Servidor HTTP mínimo da tela "Configurar pelo celular" (porta 8088).
 * Só responde a IPs privados, exige o PIN de 6 dígitos mostrado na TV e bloqueia após 5 erros.
 */
class LocalWebConfigServer(
    private val backend: RemoteConfigBackend,
    val pin: String = newPin(),
    port: Int = PORT,
    private val now: () -> Long = System::currentTimeMillis,
) : NanoHTTPD(port) {
    private val json = Json { ignoreUnknownKeys = true }
    private val failures = ConcurrentHashMap<String, Pair<Int, Long>>()

    override fun serve(session: IHTTPSession): Response {
        val ip = session.remoteIpAddress.orEmpty()
        if (IpUtils.isPrivateLiteral(ip) != true) {
            AppLogger.w(TAG, "Acesso recusado de fora da rede local")
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Somente na rede local")
        }
        return try {
            route(session, ip)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Erro na configuração pelo celular", e)
            text(Response.Status.INTERNAL_ERROR, "Erro: ${e.message}")
        }
    }

    private fun route(
        session: IHTTPSession,
        ip: String,
    ): Response {
        val path = session.uri
        if (session.method == Method.GET && (path == "/" || path == "/index.html")) {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ConfigPage.HTML)
        }
        if (!path.startsWith("/api/")) return text(Response.Status.NOT_FOUND, "Não encontrado")
        val locked = failures[ip]?.let { (n, until) -> n >= MAX_FAILURES && now() < until } == true
        if (locked) return text(Response.Status.lookup(429) ?: Response.Status.FORBIDDEN, "Muitas tentativas. Aguarde 1 minuto.")
        if (session.headers["x-pin"] != pin) {
            val (n, _) = failures[ip] ?: (0 to 0L)
            failures[ip] = (n + 1) to (now() + LOCK_MS)
            return text(Response.Status.UNAUTHORIZED, "PIN incorreto")
        }
        failures.remove(ip)
        return when {
            path == "/api/cameras" && session.method == Method.GET ->
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json; charset=utf-8",
                    json.encodeToString(ListSerializer(CameraSummary.serializer()), backend.list()),
                )
            path == "/api/cameras" && session.method == Method.POST -> {
                val form = json.decodeFromString(CameraForm.serializer(), body(session))
                val err = validate(form)
                if (err != null) return text(Response.Status.BAD_REQUEST, err)
                val id = runBlocking { backend.save(form) }
                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", """{"id":"$id"}""")
            }
            path == "/api/cameras/delete" && session.method == Method.POST -> {
                val id = json.decodeFromString(CameraForm.serializer(), body(session)).id ?: return text(Response.Status.BAD_REQUEST, "Falta o id")
                runBlocking { backend.delete(id) }
                text(Response.Status.OK, "ok")
            }
            else -> text(Response.Status.NOT_FOUND, "Não encontrado")
        }
    }

    private fun body(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        session.parseBody(map)
        return map["postData"].orEmpty()
    }

    private fun text(
        status: Response.IStatus,
        msg: String,
    ) = newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    companion object {
        private const val TAG = "PhoneConfig"
        const val PORT = 8088
        private const val MAX_FAILURES = 5
        private const val LOCK_MS = 60_000L

        fun newPin(): String = (SecureRandom().nextInt(900_000) + 100_000).toString()

        fun validate(f: CameraForm): String? =
            when {
                f.name.isBlank() -> "Informe o nome da câmera"
                f.host.isBlank() -> "Informe o IP da câmera"
                f.host.all { it.isDigit() } -> "Isso parece o ID do app Yoosee. Use o IP da câmera na rede (ex.: 192.168.1.50)"
                !IpUtils.isIpLiteral(f.host) && !f.host.contains('.') -> "IP inválido (ex.: 192.168.1.50)"
                IpUtils.isIpLiteral(f.host) && IpUtils.isPrivateLiteral(f.host) != true -> "O IP precisa ser da rede local"
                f.onvifPort !in 1..65535 || f.rtspPort !in 1..65535 -> "Porta inválida"
                else -> null
            }
    }
}

/** Página embutida (sem CDN), responsiva, em pt-BR. */
object ConfigPage {
    val HTML =
        """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Câmeras ao Vivo</title>
        <style>
        :root{--bg:#000;--s:#121417;--r:#1B1E23;--h:#252931;--o:#2C313A;--t:#F5F7FA;--t2:#A9B1BE;--a:#38BDF8;--oa:#00131D;--d:#EF4444}
        *{box-sizing:border-box}body{margin:0;background:var(--s);color:var(--t);font:16px system-ui,Roboto,sans-serif}
        main{max-width:560px;margin:0 auto;padding:16px}h1{font-size:24px;margin:8px 0 16px}h2{font-size:18px;color:var(--t2);margin:24px 0 8px}
        .card{background:var(--r);border:1px solid var(--o);border-radius:12px;padding:16px;margin:8px 0}
        label{display:block;color:var(--t2);font-size:14px;margin:10px 0 4px}input{width:100%;padding:12px;border-radius:8px;border:1px solid var(--o);background:var(--h);color:var(--t);font-size:16px}
        button{padding:12px 16px;border-radius:8px;border:0;font-size:16px;font-weight:600;margin-top:12px;cursor:pointer}
        .p{background:var(--a);color:var(--oa)}.s{background:var(--h);color:var(--t)}.d{background:var(--d);color:#000}
        .row{display:flex;gap:8px}.row>*{flex:1}.msg{color:var(--t2);min-height:20px;margin-top:8px}.cam{display:flex;justify-content:space-between;align-items:center;gap:8px}
        small{color:var(--t2)}
        </style></head><body><main>
        <h1>Câmeras ao Vivo</h1>
        <div id="login" class="card"><label for="pin">PIN mostrado na TV</label><input id="pin" inputmode="numeric" maxlength="6" autocomplete="off">
        <button class="p" onclick="entrar()">Entrar</button><div id="lmsg" class="msg"></div></div>
        <div id="app" hidden>
        <h2>Câmeras</h2><div id="list"></div>
        <h2 id="ftitle">Adicionar câmera</h2>
        <div class="card"><input type="hidden" id="id">
        <label for="name">Nome</label><input id="name" placeholder="Câmera Externa">
        <label for="host">IP da câmera na rede (não é o ID do Yoosee)</label><input id="host" placeholder="192.168.1.50" inputmode="decimal">
        <small>Veja o IP no app Yoosee (informações da câmera) ou na lista de aparelhos do roteador.</small>
        <div class="row"><div><label for="onvif">Porta ONVIF</label><input id="onvif" value="5000" inputmode="numeric"></div>
        <div><label for="rtsp">Porta RTSP</label><input id="rtsp" value="554" inputmode="numeric"></div></div>
        <label for="user">Usuário</label><input id="user" value="admin">
        <label for="pass">Senha da "Conexão NVR" (app Yoosee)</label><input id="pass" type="password" placeholder="deixe vazio para manter">
        <div class="row"><button class="p" onclick="salvar()">Salvar</button><button class="s" onclick="limpar()">Nova</button></div>
        <div id="msg" class="msg"></div></div>
        <small>Os dados ficam só na TV Box. Esta página funciona apenas na rede de casa e desliga sozinha em 10 minutos.</small>
        </div></main>
        <script>
        let PIN="";const ${'$'}=id=>document.getElementById(id);
        async function api(p,o={}){o.headers=Object.assign({"X-Pin":PIN,"Content-Type":"application/json"},o.headers||{});const r=await fetch(p,o);const t=await r.text();if(!r.ok)throw new Error(t);return t}
        async function entrar(){PIN=${'$'}("pin").value.trim();try{await carregar();${'$'}("login").hidden=true;${'$'}("app").hidden=false}catch(e){${'$'}("lmsg").textContent=e.message}}
        async function carregar(){const l=JSON.parse(await api("/api/cameras"));${'$'}("list").innerHTML=l.length?"":"<div class='card'><small>Nenhuma câmera ainda.</small></div>";
        l.forEach(c=>{const d=document.createElement("div");d.className="card cam";d.innerHTML="<div><b></b><br><small></small></div><div class='row'><button class='s'>Editar</button><button class='d'>Remover</button></div>";
        d.querySelector("b").textContent=c.name;d.querySelector("small").textContent=c.host+(c.hasPassword?" · senha salva":" · sem senha");
        const [e,r]=d.querySelectorAll("button");e.onclick=()=>editar(c);r.onclick=()=>remover(c);${'$'}("list").appendChild(d)})}
        function editar(c){${'$'}("ftitle").textContent="Editar "+c.name;${'$'}("id").value=c.id;${'$'}("name").value=c.name;${'$'}("host").value=c.host;${'$'}("onvif").value=c.onvifPort;${'$'}("rtsp").value=c.rtspPort;${'$'}("user").value=c.username;${'$'}("pass").value="";window.scrollTo(0,document.body.scrollHeight)}
        function limpar(){${'$'}("ftitle").textContent="Adicionar câmera";["id","name","host","pass"].forEach(k=>${'$'}(k).value="");${'$'}("onvif").value=5000;${'$'}("rtsp").value=554;${'$'}("user").value="admin"}
        async function salvar(){const b={id:${'$'}("id").value||null,name:${'$'}("name").value.trim(),host:${'$'}("host").value.trim(),onvifPort:+${'$'}("onvif").value,rtspPort:+${'$'}("rtsp").value,username:${'$'}("user").value.trim(),password:${'$'}("pass").value};
        try{await api("/api/cameras",{method:"POST",body:JSON.stringify(b)});${'$'}("msg").textContent="Salvo! A TV vai testar a conexão.";limpar();carregar()}catch(e){${'$'}("msg").textContent=e.message}}
        async function remover(c){if(!confirm("Remover "+c.name+"?"))return;await api("/api/cameras/delete",{method:"POST",body:JSON.stringify({id:c.id})});carregar()}
        </script></body></html>
        """.trimIndent()
}
