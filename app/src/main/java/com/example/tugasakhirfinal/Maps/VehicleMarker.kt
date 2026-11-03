package com.example.tugasakhirfinal.Maps

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import kotlin.math.roundToInt

fun LatLng.toRoutePoint() = RoutePoint(latitude, longitude)
fun RoutePoint.toLatLng() = LatLng(lat, lng)
fun List<LatLng>.toRoutePoints() = map { it.toRoutePoint() }
fun List<RoutePoint>.toLatLngs() = map { it.toLatLng() }

/**
 * Ikon kendaraan (ambulans tampak atas) untuk marker peta - dipakai bersama oleh sisi driver
 * dan passenger supaya tampilannya identik.
 */
object VehicleMarkerIcon {

    /**
     * Ukuran dalam dp, BUKAN pixel. Versi lama memakai 90px mentah: di layar sangat rapat
     * (Note 20 Ultra) ikonnya jadi sangat kecil, di layar biasa (A02) jadi besar. Dengan dp,
     * ukuran fisiknya sama di semua HP.
     */
    private const val DEFAULT_HEIGHT_DP = 44f
    private const val SHADOW_RADIUS_DP = 3f
    private const val SHADOW_OFFSET_Y_DP = 1.5f

    /**
     * Membuat ikon dengan rasio gambar asli + bayangan tipis. Bayangan dibutuhkan karena badan
     * ambulans putih dan mudah "tenggelam" di peta yang terang.
     */
    fun create(context: Context, @DrawableRes drawableId: Int, heightDp: Float = DEFAULT_HEIGHT_DP): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val density = context.resources.displayMetrics.density

        val heightPx = (heightDp * density).roundToInt().coerceAtLeast(1)
        val intrinsicW = drawable.intrinsicWidth.takeIf { it > 0 } ?: heightPx
        val intrinsicH = drawable.intrinsicHeight.takeIf { it > 0 } ?: heightPx
        val widthPx = (heightPx * intrinsicW / intrinsicH.toFloat()).roundToInt().coerceAtLeast(1)

        val icon = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(Canvas(icon))

        // Padding sama di keempat sisi supaya titik tengah gambar tetap di tengah bitmap -
        // penting karena marker memakai anchor(0.5, 0.5) dan berputar pada titik itu.
        val shadowRadiusPx = SHADOW_RADIUS_DP * density
        val shadowOffsetYPx = SHADOW_OFFSET_Y_DP * density
        val padding = (shadowRadiusPx * 2 + shadowOffsetYPx).roundToInt()

        val output = Bitmap.createBitmap(widthPx + padding * 2, heightPx + padding * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val blurPaint = Paint().apply { maskFilter = BlurMaskFilter(shadowRadiusPx, BlurMaskFilter.Blur.NORMAL) }
        val shadowOffset = IntArray(2)
        val shadowAlpha = icon.extractAlpha(blurPaint, shadowOffset)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
        canvas.drawBitmap(
            shadowAlpha,
            padding + shadowOffset[0].toFloat(),
            padding + shadowOffset[1].toFloat() + shadowOffsetYPx,
            shadowPaint
        )
        canvas.drawBitmap(icon, padding.toFloat(), padding.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        shadowAlpha.recycle()
        icon.recycle()
        return BitmapDescriptorFactory.fromBitmap(output)
    }
}

/**
 * Menggerakkan marker secara halus ke posisi baru, seperti ikon mobil di Gojek/Grab.
 *
 * Lokasi driver hanya masuk tiap ~3 detik. Tanpa animasi, ikon "loncat" tiap update dan
 * rotasinya berputar mendadak. Di sini posisi & rotasi diinterpolasi selama [DURATION_MS] -
 * sedikit lebih pendek dari interval update, jadi ikon terlihat terus bergerak (tidak jalan-
 * berhenti-jalan) tapi tetap selesai sebelum update berikutnya biasanya datang.
 *
 * Satu instance per marker. Panggil [cancel] saat marker dihapus atau layar ditutup.
 */
class SmoothMarkerAnimator {

    private var animator: ValueAnimator? = null

    fun animateTo(marker: Marker, target: LatLng, targetRotation: Float?) {
        animator?.cancel()

        // Mulai dari posisi marker SAAT INI (bisa di tengah animasi sebelumnya), bukan posisi
        // update terakhir - jadi kalau update datang lebih cepat, gerakannya tetap mulus.
        val start = marker.position
        val startRotation = marker.rotation
        val rotationDelta = targetRotation?.let { RouteGeometry.shortestRotationDelta(startRotation, it) } ?: 0f

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                // runCatching: kalau marker sempat dihapus (mis. trip selesai) di tengah animasi,
                // jangan sampai aplikasi crash hanya karena satu frame animasi terakhir.
                runCatching {
                    marker.position = LatLng(
                        start.latitude + (target.latitude - start.latitude) * f,
                        start.longitude + (target.longitude - start.longitude) * f
                    )
                    if (targetRotation != null) {
                        marker.rotation = (startRotation + rotationDelta * f + 360f) % 360f
                    }
                }.onFailure { anim.cancel() }
            }
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    private companion object {
        const val DURATION_MS = 2_500L
    }
}
