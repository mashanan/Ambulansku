package com.example.tugasakhirfinal.ForDriver.Welcome

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.tugasakhirfinal.databinding.ActivityDriverQualificationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

// Diisi driver setelah akun dibuat (SignUpActivityDriver), sebelum verifikasi email.
// Mencakup 3 standar kelayakan: (1) legalitas SIM/STNK/foto ambulans + sertifikat AED opsional,
// (2) kompetensi medis dasar (sertifikat BTLS/BHD opsional), (3) checklist SOP etika/operasional.
// Data ini dipakai Admin untuk menilai kelayakan driver sebelum menyetujui (isApproved=true)
// lewat ApproveDriverActivity.
class DriverQualificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverQualificationBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://tugasakhirfinal-9e73d.firebasestorage.app")
    private lateinit var progressDialog: ProgressDialog

    private var licensePhotoUri: Uri? = null
    private var stnkPhotoUri: Uri? = null
    private var aedCertUri: Uri? = null
    private var medicalCertUri: Uri? = null
    private val ambulancePhotoUris = ArrayList<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityDriverQualificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressDialog = ProgressDialog(this).apply {
            setMessage("Mengunggah data...")
            setCancelable(false)
        }

        binding.btnPickLicensePhoto.setOnClickListener { pickLicenseLauncher.launch("image/*") }
        binding.btnPickStnkPhoto.setOnClickListener { pickStnkLauncher.launch("image/*") }
        binding.btnAddAmbulancePhoto.setOnClickListener { pickAmbulanceLauncher.launch("image/*") }
        binding.btnPickAedCert.setOnClickListener { pickAedLauncher.launch("image/*") }
        binding.btnPickMedicalCert.setOnClickListener { pickMedicalLauncher.launch("image/*") }
        binding.btnSubmit.setOnClickListener { submitQualification() }

        binding.etLicenseNumber.addTextChangedListener { validateForm() }
        binding.etAmbulancePlate.addTextChangedListener { validateForm() }
        for (checkbox in listOf(binding.cbSopSirine, binding.cbSopEquipment, binding.cbSopApd, binding.cbSopHealthCheck)) {
            checkbox.setOnCheckedChangeListener { _, _ -> validateForm() }
        }
    }

    private val pickLicenseLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            licensePhotoUri = it
            binding.imgLicensePreview.setImageURI(it)
            validateForm()
        }
    }

    private val pickStnkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            stnkPhotoUri = it
            binding.imgStnkPreview.setImageURI(it)
            validateForm()
        }
    }

    private val pickAedLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            aedCertUri = it
            binding.imgAedPreview.visibility = View.VISIBLE
            binding.imgAedPreview.setImageURI(it)
        }
    }

    private val pickMedicalLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            medicalCertUri = it
            binding.imgMedicalPreview.visibility = View.VISIBLE
            binding.imgMedicalPreview.setImageURI(it)
        }
    }

    private val pickAmbulanceLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            ambulancePhotoUris.add(it)
            renderAmbulanceThumbnails()
            validateForm()
        }
    }

    // Render ulang seluruh thumbnail foto ambulans yang sudah dipilih, tiap thumbnail
    // punya tombol hapus kecil supaya driver bisa koreksi sebelum submit.
    private fun renderAmbulanceThumbnails() {
        val container = binding.containerAmbulancePhotos
        container.removeAllViews()

        val sizePx = (72 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        val removeSizePx = (22 * resources.displayMetrics.density).toInt()

        for ((index, uri) in ambulancePhotoUris.withIndex()) {
            val frame = FrameLayout(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginEnd = marginPx
                }
            }

            val thumb = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                setBackgroundColor(0xFFEEEEEE.toInt())
            }
            frame.addView(thumb)

            val removeBtn = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(removeSizePx, removeSizePx, Gravity.TOP or Gravity.END)
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(0x99000000.toInt())
                setOnClickListener {
                    ambulancePhotoUris.removeAt(index)
                    renderAmbulanceThumbnails()
                    validateForm()
                }
            }
            frame.addView(removeBtn)

            container.addView(frame)
        }
    }

    // Tombol submit baru aktif kalau: No. SIM & No. Plat terisi, Foto SIM + Foto STNK + minimal
    // 1 foto ambulans sudah dipilih, DAN keempat checklist SOP tercentang. Sertifikat opsional
    // (AED, BTLS/BHD) tidak menghalangi submit.
    private fun validateForm() {
        val textsFilled = binding.etLicenseNumber.text.toString().trim().isNotEmpty() &&
            binding.etAmbulancePlate.text.toString().trim().isNotEmpty()
        val photosReady = licensePhotoUri != null && stnkPhotoUri != null && ambulancePhotoUris.isNotEmpty()
        val sopChecked = binding.cbSopSirine.isChecked && binding.cbSopEquipment.isChecked &&
            binding.cbSopApd.isChecked && binding.cbSopHealthCheck.isChecked

        val isValid = textsFilled && photosReady && sopChecked
        binding.btnSubmit.isEnabled = isValid
        binding.btnSubmit.alpha = if (isValid) 1f else 0.5f
    }

    private fun submitQualification() {
        val licenseNumber = binding.etLicenseNumber.text.toString().trim()
        val plateNumber = binding.etAmbulancePlate.text.toString().trim()

        val licenseUri = licensePhotoUri
        val stnkUri = stnkPhotoUri
        if (licenseNumber.isEmpty() || plateNumber.isEmpty() || licenseUri == null || stnkUri == null || ambulancePhotoUris.isEmpty()) {
            Toast.makeText(this, "Lengkapi semua data & foto wajib Standar 1", Toast.LENGTH_SHORT).show()
            return
        }
        if (!(binding.cbSopSirine.isChecked && binding.cbSopEquipment.isChecked &&
                binding.cbSopApd.isChecked && binding.cbSopHealthCheck.isChecked)
        ) {
            Toast.makeText(this, "Centang semua pernyataan checklist SOP", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sesi berakhir, silakan daftar ulang", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.show()
        uploadPhoto(uid, "license", licenseUri) { licenseUrl ->
            uploadPhoto(uid, "stnk", stnkUri) { stnkUrl ->
                uploadMultiple(uid, ambulancePhotoUris, "ambulance") { ambulanceUrls ->
                    uploadOptional(uid, "aed_certificate", aedCertUri) { aedUrl ->
                        uploadOptional(uid, "medical_certificate", medicalCertUri) { medicalUrl ->
                            saveQualificationData(
                                uid, licenseNumber, plateNumber,
                                licenseUrl, stnkUrl, ambulanceUrls, aedUrl, medicalUrl
                            )
                        }
                    }
                }
            }
        }
    }

    private fun uploadPhoto(uid: String, type: String, uri: Uri, onUploaded: (String) -> Unit) {
        val bitmap = uriToBitmap(uri)
        if (bitmap == null) {
            progressDialog.dismiss()
            Toast.makeText(this, "Gagal memproses foto $type", Toast.LENGTH_SHORT).show()
            return
        }

        val storageRef = storage.reference.child("driver_documents/$uid/$type.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)

        storageRef.putBytes(baos.toByteArray())
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onUploaded(downloadUri.toString())
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal unggah foto $type: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Sama seperti uploadPhoto, tapi kalau uri null (sertifikat opsional tidak diisi) langsung
    // lanjut dengan URL kosong tanpa menyentuh Storage.
    private fun uploadOptional(uid: String, type: String, uri: Uri?, onUploaded: (String) -> Unit) {
        if (uri == null) {
            onUploaded("")
            return
        }
        uploadPhoto(uid, type, uri, onUploaded)
    }

    // Upload berantai semua foto kondisi ambulans (ambulance_0.jpg, ambulance_1.jpg, dst),
    // dikumpulkan jadi satu List<String> sebelum lanjut ke tahap berikutnya.
    private fun uploadMultiple(uid: String, uris: List<Uri>, prefix: String, onDone: (List<String>) -> Unit) {
        val results = ArrayList<String>()

        fun uploadAt(index: Int) {
            if (index >= uris.size) {
                onDone(results)
                return
            }
            uploadPhoto(uid, "${prefix}_$index", uris[index]) { url ->
                results.add(url)
                uploadAt(index + 1)
            }
        }

        uploadAt(0)
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveQualificationData(
        uid: String,
        licenseNumber: String,
        plateNumber: String,
        licenseUrl: String,
        stnkUrl: String,
        ambulanceUrls: List<String>,
        aedUrl: String,
        medicalUrl: String
    ) {
        val data = hashMapOf<String, Any>(
            "licenseNumber" to licenseNumber,
            "ambulancePlateNumber" to plateNumber,
            "licensePhotoUrl" to licenseUrl,
            "stnkPhotoUrl" to stnkUrl,
            "ambulancePhotoUrls" to ambulanceUrls,
            "aedCertificateUrl" to aedUrl,
            "medicalCertificateUrl" to medicalUrl,
            "sopSirineChecked" to binding.cbSopSirine.isChecked,
            "sopEquipmentChecked" to binding.cbSopEquipment.isChecked,
            "sopApdChecked" to binding.cbSopApd.isChecked,
            "sopHealthCheckChecked" to binding.cbSopHealthCheck.isChecked
        )

        firestore.collection("users").document(uid).update(data)
            .addOnSuccessListener { sendVerificationAndFinish() }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal simpan data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendVerificationAndFinish() {
        val user = auth.currentUser
        user?.sendEmailVerification()?.addOnCompleteListener {
            progressDialog.dismiss()
            Toast.makeText(
                this,
                "Registrasi berhasil! Cek email verifikasi. Akun akan aktif setelah disetujui Admin.",
                Toast.LENGTH_LONG
            ).show()
            auth.signOut()

            val intent = Intent(this, SignInActivityDriver::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
