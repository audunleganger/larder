package com.larder.server

import com.larder.shared.api.FoodImageData
import com.larder.shared.api.FoodInput
import com.larder.shared.api.FoodMetadataInput
import com.larder.shared.api.MetadataInput
import com.larder.shared.api.NutrientInput
import com.larder.shared.api.NutrientOrderInput
import com.larder.shared.api.PhotoLinkInput
import com.larder.shared.api.FoodTagsInput
import com.larder.shared.api.TagColorInput
import com.larder.shared.api.TagInput
import com.larder.shared.api.TagMetadataInput
import com.larder.shared.api.UnitOrderInput
import com.larder.shared.api.UnitInput
import com.larder.shared.domain.notFound
import com.larder.shared.service.FoodService
import com.larder.shared.service.NutrientService
import com.larder.shared.service.TagService
import com.larder.shared.service.UnitService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
    suspend fun <T> ApplicationCall.units(block: UnitService.() -> T) = withService(database, auth, ::UnitService, block)
    suspend fun <T> ApplicationCall.nutrients(block: NutrientService.() -> T) = withService(database, auth, ::NutrientService, block)
    suspend fun <T> ApplicationCall.foods(block: FoodService.() -> T) = withService(database, auth, ::FoodService, block)
    suspend fun <T> ApplicationCall.tags(block: TagService.() -> T) = withService(database, auth, ::TagService, block)

    route("/units") {
        get {
            val all = call.flag("includeHidden")
            call.respond(call.units { list(all) })
        }
        post {
            val input = call.receive<UnitInput>()
            call.respond(HttpStatusCode.Created, call.units { create(input) })
        }
        put("/order") {
            val input = call.receive<UnitOrderInput>()
            call.respond(call.units { reorder(input.ids) })
        }
        // Back to alphabetical order.
        delete("/order") {
            call.respond(call.units { resetOrder() })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.units { detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<UnitInput>()
            call.respond(call.units { update(id, input) })
        }
        // Admins only.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<MetadataInput>()
            call.respond(call.units { setMetadata(id, input) })
        }
        post("/{id}/hide") {
            val id = call.idParam()
            call.respond(call.units { setHidden(id, true) })
        }
        post("/{id}/show") {
            val id = call.idParam()
            call.respond(call.units { setHidden(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.units { delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/nutrients") {
        get {
            val all = call.flag("includeHidden")
            call.respond(call.nutrients { list(all) })
        }
        post {
            val input = call.receive<NutrientInput>()
            call.respond(HttpStatusCode.Created, call.nutrients { create(input) })
        }
        put("/order") {
            val input = call.receive<NutrientOrderInput>()
            call.respond(call.nutrients { reorder(input.ids) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.nutrients { detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<NutrientInput>()
            call.respond(call.nutrients { update(id, input) })
        }
        // Admins only.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<MetadataInput>()
            call.respond(call.nutrients { setMetadata(id, input) })
        }
        post("/{id}/hide") {
            val id = call.idParam()
            call.respond(call.nutrients { setHidden(id, true) })
        }
        post("/{id}/show") {
            val id = call.idParam()
            call.respond(call.nutrients { setHidden(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.nutrients { delete(id) }
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
            call.respond(call.foods { list(query, all) })
        }
        post {
            val input = call.receive<FoodInput>()
            call.respond(HttpStatusCode.Created, call.foods { create(input) })
        }
        get("/ref-default") {
            call.respond(call.foods { refDefault() })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.foods { detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<FoodInput>()
            call.respond(call.foods { update(id, input) })
        }
        // Tags (F-16), saved at once: set them, or add one by name, made if new.
        put("/{id}/tags") {
            val id = call.idParam()
            val input = call.receive<FoodTagsInput>()
            call.respond(call.foods { setTags(id, input.tagIds) })
        }
        post("/{id}/tags") {
            val id = call.idParam()
            val input = call.receive<TagInput>()
            call.respond(call.foods { addTag(id, input) })
        }
        // Photos (F-13). With ?v=<imageVersion> the response never changes, so browsers may cache it for good.
        get("/{id}/image") {
            val id = call.idParam()
            val thumbnail = call.request.queryParameters["size"] == "thumbnail"
            val image = call.foods { image(id, thumbnail) } ?: notFound("Photo")
            val versioned = call.request.queryParameters["v"] == image.version.toString()
            call.response.header(HttpHeaders.CacheControl, if (versioned) "private, max-age=31536000, immutable" else "private, no-cache")
            call.respondBytes(image.bytes, ContentType.parse(image.contentType))
        }
        put("/{id}/image") {
            val id = call.idParam()
            val input = call.receive<FoodImageData>()
            call.respond(call.foods { setImage(id, input) })
        }
        delete("/{id}/image") {
            val id = call.idParam()
            call.respond(call.foods { deleteImage(id) })
        }
        // Admins only, for their own foods.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<FoodMetadataInput>()
            call.respond(call.foods { setMetadata(id, input) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.foods { setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.foods { setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.foods { delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // Tags (F-16): per user, like foods. They're made from a food (POST /foods/{id}/tags).
    route("/tags") {
        get {
            val all = call.flag("includeArchived")
            call.respond(call.tags { list(all) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.tags { detail(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<TagInput>()
            call.respond(call.tags { update(id, input) })
        }
        // Admins only, for their own tags.
        put("/{id}/metadata") {
            val id = call.idParam()
            val input = call.receive<TagMetadataInput>()
            call.respond(call.tags { setMetadata(id, input) })
        }
        put("/{id}/color") {
            val id = call.idParam()
            val input = call.receive<TagColorInput>()
            call.respond(call.tags { setColor(id, input.color) })
        }
        post("/{id}/archive") {
            val id = call.idParam()
            call.respond(call.tags { setArchived(id, true) })
        }
        post("/{id}/unarchive") {
            val id = call.idParam()
            call.respond(call.tags { setArchived(id, false) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.tags { delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
