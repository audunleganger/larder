package com.caloriecompanion.server

import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.NutrientOrderInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.UnitService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Units, nutrients and food stuffs (F-*, U-*, N-*). */
fun Route.catalogRoutes(database: Database, auth: Auth) {
    route("/units") {
        get {
            val all = call.flag("includeArchived")
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).list(all) })
        }
        post {
            val input = call.receive<UnitInput>()
            call.respond(HttpStatusCode.Created, call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).create(input) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<UnitInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).update(id, input) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/nutrients") {
        get {
            val all = call.flag("includeArchived")
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).list(all) })
        }
        post {
            val input = call.receive<NutrientInput>()
            call.respond(HttpStatusCode.Created, call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).create(input) })
        }
        put("/order") {
            val input = call.receive<NutrientOrderInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).reorder(input.ids) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<NutrientInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).update(id, input) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/foods") {
        get {
            val query = call.request.queryParameters["q"]
            val all = call.flag("includeArchived")
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).list(query, all) })
        }
        post {
            val input = call.receive<FoodInput>()
            call.respond(HttpStatusCode.Created, call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).create(input) })
        }
        get("/ref-default") {
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).refDefault() })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<FoodInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).update(id, input) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
