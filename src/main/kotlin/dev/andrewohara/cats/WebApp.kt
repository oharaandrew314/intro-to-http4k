package dev.andrewohara.cats

import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.body.form
import org.http4k.routing.bind
import org.http4k.routing.routes

fun webApp(clientId: String, redirectUri: Uri) = routes(
    "" bind Method.GET to {
        Response(Status.OK).body(
            """<html>
  <body>
    <script src="https://accounts.google.com/gsi/client" async></script>
    <div id="g_id_onload"
        data-client_id="$clientId"
        data-login_uri="$redirectUri"
        data-auto_prompt="false">
    </div>
    <div class="g_id_signin"
        data-type="standard"
        data-size="large"
        data-theme="outline"
        data-text="sign_in_with"
        data-shape="rectangular"
        data-logo_alignment="left">
    </div>
  <body>
</html>"""
        )
    },
    "redirect" bind Method.POST to { request ->
        val token = request.form("credential") ?: "ERROR!"
        Response(Status.OK).body(token)
    }
)