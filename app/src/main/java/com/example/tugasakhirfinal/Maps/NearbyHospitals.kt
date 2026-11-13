package com.example.tugasakhirfinal.Maps

import com.example.tugasakhirfinal.ForPassenger.DriveFragment.Hospital
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

/**
 * Loads only the hospitals near the user instead of the whole "hospitals" collection, so the
 * number of Firestore reads stays small no matter how many hospitals the database holds.
 *
 * Every hospital document stores a `geohash` (see [geohashFor]); a radius query becomes a handful
 * of geohash range queries ([GeoFireUtils.getGeoHashQueryBounds]). The radius starts small and
 * grows ([RADII_METERS]) until at least one hospital is found. If none is found even at the
 * largest radius (e.g. documents without a geohash yet), it falls back to reading everything.
 */
object NearbyHospitals {

    /** Search radii tried in order, in meters. */
    val RADII_METERS = doubleArrayOf(30_000.0, 75_000.0, 150_000.0, 400_000.0)

    const val FIELD_GEOHASH = "geohash"

    fun geohashFor(latitude: Double, longitude: Double): String =
        GeoFireUtils.getGeoHashForLocation(GeoLocation(latitude, longitude))

    /** Parses a hospital document (supports both the `lokasi` GeoPoint and legacy lat/lng fields). */
    fun fromDocument(doc: DocumentSnapshot): Hospital {
        val geoPoint = doc.getGeoPoint("lokasi")
        return Hospital(
            id = doc.id,
            nama = doc.getString("nama") ?: "No Name",
            alamat = doc.getString("alamat") ?: "-",
            telepon = doc.getString("telepon") ?: "-",
            latitude = geoPoint?.latitude ?: doc.getDouble("latitude") ?: 0.0,
            longitude = geoPoint?.longitude ?: doc.getDouble("longitude") ?: 0.0,
            lokasi = geoPoint
        )
    }

    /**
     * @param onResult hospitals sorted by straight-line distance (nearest first), with
     *   [Hospital.distanceMeters] filled in, plus the radius that produced them (null when the
     *   full-collection fallback was used).
     */
    fun load(
        firestore: FirebaseFirestore,
        center: LatLng,
        onResult: (hospitals: List<Hospital>, radiusMeters: Double?) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        searchRadius(firestore, center, 0, onResult, onError)
    }

    private fun searchRadius(
        firestore: FirebaseFirestore,
        center: LatLng,
        radiusIndex: Int,
        onResult: (List<Hospital>, Double?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (radiusIndex >= RADII_METERS.size) {
            loadAll(firestore, center, onResult, onError)
            return
        }
        val radius = RADII_METERS[radiusIndex]
        val origin = GeoLocation(center.latitude, center.longitude)
        val tasks = GeoFireUtils.getGeoHashQueryBounds(origin, radius).map { b ->
            firestore.collection("hospitals")
                .orderBy(FIELD_GEOHASH)
                .startAt(b.startHash)
                .endAt(b.endHash)
                .get()
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            val failure = tasks.firstOrNull { !it.isSuccessful }?.exception
            if (failure != null) {
                onError(failure)
                return@addOnCompleteListener
            }
            val docs = tasks.flatMap { (it.result as QuerySnapshot).documents }.distinctBy { it.id }
            val nearby = withDistances(docs.map(::fromDocument), center).filter { it.distanceMeters <= radius }
            if (nearby.isNotEmpty()) onResult(nearby, radius)
            else searchRadius(firestore, center, radiusIndex + 1, onResult, onError)
        }
    }

    private fun loadAll(
        firestore: FirebaseFirestore,
        center: LatLng,
        onResult: (List<Hospital>, Double?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("hospitals").get()
            .addOnSuccessListener { snap -> onResult(withDistances(snap.documents.map(::fromDocument), center), null) }
            .addOnFailureListener(onError)
    }

    private fun withDistances(hospitals: List<Hospital>, center: LatLng): List<Hospital> {
        val origin = GeoLocation(center.latitude, center.longitude)
        for (h in hospitals) {
            val loc = h.getLatLng() ?: continue
            h.distanceMeters = GeoFireUtils.getDistanceBetween(origin, GeoLocation(loc.latitude, loc.longitude)).toFloat()
        }
        return hospitals.filter { it.distanceMeters >= 0 }.sortedBy { it.distanceMeters }
    }
}
