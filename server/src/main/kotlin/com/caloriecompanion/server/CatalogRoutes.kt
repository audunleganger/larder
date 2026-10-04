package com.caloriecompanion.server

import com.caloriecompanion.shared.api.FoodImageData
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodMetadataInput
import com.caloriecompanion.shared.api.MetadataInput
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.NutrientOrderInput
import com.caloriecompanion.shared.api.PhotoLinkInput
import com.caloriecompanion.shared.api.FoodTagsInput
import com.caloriecompanion.shared.api.TagColorInput
import com.caloriecompanion.shared.api.TagInput
import com.caloriecompanion.shared.api.TagMetadataInput
import com.caloriecompanion.shared.api.UnitOrderInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.TagService
import com.caloriecompanion.shared.service.UnitService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Units and nutrients (shared by all users, each hidden or shown per user) and food stuffs (F-*, U-*, N-*). */
fun Route.catalogRoutes(database: Database, auth: Auth, photoFetcher: PhotoFetcher) {
    route("/units") {
        get {
            val all = call.flag("includeHidden")
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).list(all) })
        }
        post {
            val input = call.receive<UnitInput>()
            call.respond(HttpStatusCode.Created, call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).create(input) })
        }
        put("/order") {
            val input = call.receive<UnitOrderInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).reorder(input.ids) })
        }
        // Back to alphabetical order.
        delete("/order") {
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).resetOrder() })
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
        // Admins only.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<MetadataInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).setMetadata(id, input) })
        }
        post("/{id}/hide") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).setHidden(id, true) })
        }
        post("/{id}/show") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).setHidden(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUserLanguage(database, auth) { db, user, language -> UnitService(db, user, language).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/nutrients") {
        get {
            val all = call.flag("includeHidden")
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
        // Admins only.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<MetadataInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).setMetadata(id, input) })
        }
        post("/{id}/hide") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).setHidden(id, true) })
        }
        post("/{id}/show") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).setHidden(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUserLanguage(database, auth) { db, user, language -> NutrientService(db, user, language).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // A photo from a link (F-13): downloaded here, then shrunk and uploaded by the client like a file.
    post("/photo-links/fetch") {
        call.user(database, auth)
        val input = call.receive<PhotoLinkInput>()
        val image = photoFetcher.fetch(input.url)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondBytes(image.bytes, ContentType.parse(image.contentType))
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
        // Tags (F-16), saved at once: set them, or add one by name, made if new.
        put("/{id}/tags") {
            val id = call.idParam()
            val input = call.receive<FoodTagsInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).setTags(id, input.tagIds) })
        }
        post("/{id}/tags") {
            val id = call.idParam()
            val input = call.receive<TagInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).addTag(id, input) })
        }
        // Photos (F-13). With ?v=<imageVersion> the response never changes, so browsers may cache it for good.
        get("/{id}/image") {
            val id = call.idParam()
            val thumbnail = call.request.queryParameters["size"] == "thumbnail"
            val image = call.withUser(database, auth) { db, user -> FoodService(db, user).image(id, thumbnail) } ?: notFound("Photo")
            val versioned = call.request.queryParameters["v"] == image.version.toString()
            call.response.header(HttpHeaders.CacheControl, if (versioned) "private, max-age=31536000, immutable" else "private, no-cache")
            call.respondBytes(image.bytes, ContentType.parse(image.contentType))
        }
        put("/{id}/image") {
            val id = call.idParam()
            val input = call.receive<FoodImageData>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).setImage(id, input) })
        }
        delete("/{id}/image") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).deleteImage(id) })
        }
        // Admins only, for their own foods.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<FoodMetadataInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> FoodService(db, user, language).setMetadata(id, input) })
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

    // Tags (F-16): per user, like foods. They're made from a food (POST /foods/{id}/tags).
    route("/tags") {
        get {
            val all = call.flag("includeArchived")
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).list(all) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<TagInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).update(id, input) })
        }
        // Admins only, for their own tags.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<TagMetadataInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).setMetadata(id, input) })
        }
        put("/{id}/color") {
            val id = call.idParam()
            val input = call.receive<TagColorInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).setColor(id, input.color) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUserLanguage(database, auth) { db, user, language -> TagService(db, user, language).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
