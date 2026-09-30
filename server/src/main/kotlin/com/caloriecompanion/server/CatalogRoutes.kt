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
            call.respond(call.withUser(database, auth) { db, user -> UnitService(db, user).list(all) })
        }
        post {
            val input = call.receive<UnitInput>()
            call.respond(HttpStatusCode.Created, call.withUser(database, auth) { db, user -> UnitService(db, user).create(input) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> UnitService(db, user).detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<UnitInput>()
            call.respond(call.withUser(database, auth) { db, user -> UnitService(db, user).update(id, input) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> UnitService(db, user).setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> UnitService(db, user).setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUser(database, auth) { db, user -> UnitService(db, user).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/nutrients") {
        get {
            val all = call.flag("includeArchived")
            call.respond(call.withUser(database, auth) { db, user -> NutrientService(db, user).list(all) })
        }
        post {
            val input = call.receive<NutrientInput>()
            call.respond(HttpStatusCode.Created, call.withUser(database, auth) { db, user -> NutrientService(db, user).create(input) })
        }
        put("/order") {
            val input = call.receive<NutrientOrderInput>()
            call.respond(call.withUser(database, auth) { db, user -> NutrientService(db, user).reorder(input.ids) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> NutrientService(db, user).detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<NutrientInput>()
            call.respond(call.withUser(database, auth) { db, user -> NutrientService(db, user).update(id, input) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> NutrientService(db, user).setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> NutrientService(db, user).setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUser(database, auth) { db, user -> NutrientService(db, user).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/foods") {
        get {
            val query = call.request.queryParameters["q"]
            val all = call.flag("includeArchived")
            call.respond(call.withUser(database, auth) { db, user -> FoodService(db, user).list(query, all) })
        }
        post {
            val input = call.receive<FoodInput>()
            call.respond(HttpStatusCode.Created, call.withUser(database, auth) { db, user -> FoodService(db, user).create(input) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> FoodService(db, user).detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<FoodInput>()
            call.respond(call.withUser(database, auth) { db, user -> FoodService(db, user).update(id, input) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> FoodService(db, user).setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.withUser(database, auth) { db, user -> FoodService(db, user).setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUser(database, auth) { db, user -> FoodService(db, user).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
