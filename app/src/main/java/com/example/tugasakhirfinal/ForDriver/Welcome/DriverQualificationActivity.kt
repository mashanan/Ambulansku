package com.example.tugasakhirfinal.ForDriver.Welcome

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.ForDriver.DriverOnlineService
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.ActivityDriverQualificationBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val FILE_PROVIDER_AUTHORITY = "com.example.tugasakhirfinal.fileprovider"

// Diisi driver setelah akun dibuat (SignUpActivityDriver), sebelum verifikasi email - JUGA
// dipakai untuk EDIT data kualifikasi dari ProfileFragmentDriver (lihat EXTRA_EDIT_MODE).
// Mencakup 3 standar kelayakan: (1) legalitas SIM/STNK/foto ambulans + sertifikat AED opsional,
// (2) kompetensi medis dasar (sertifikat BTLS/BHD opsional), (3) checklist SOP etika/operasional.
// Data ini dipakai Admin untuk menilai kelayakan driver sebelum menyetujui (isApproved=true)
// lewat ApproveDriverActivity - baik saat daftar pertama kali MAUPUN setelah edit (edit selalu
// mengunci ulang isApproved=false, submit ulang untuk direview persis seperti driver baru).
class DriverQualificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EDIT_MODE = "EDIT_MODE"
    }

    private lateinit var binding: ActivityDriverQualificationBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://tugasakhirfinal-9e73d.firebasestorage.app")
    private lateinit var progressDialog: ProgressDialog
    private var isEditMode = false

    private var licensePhotoUri: Uri? = null
    private var stnkPhotoUri: Uri? = null
    private var aedCertUri: Uri? = null
    private var medicalCertUri: Uri? = null
    private val ambulancePhotoUris = ArrayList<Uri>()

    // Slot foto yang sedang dipilih ("license"/"stnk"/"ambulance"/"aed"/"medical") - dipakai
    // bersama oleh SATU dialog galeri/kamera generik untuk semua slot foto, supaya tidak perlu
    // duplikasi launcher & dialog 5x. latestCapturedUri nullable + dipulihkan dari
    // savedInstanceState (bukan lateinit) - kalau OS mematikan proses app selagi aplikasi kamera
    // terbuka, Activity ini dibuat ulang dari nol dan lateinit var akan crash saat hasil kamera
    // kembali (pola yang sama dipakai fix crash kamera di Profile/Chat).
    private var activePhotoTarget: String? = null
    private var latestCapturedUri: Uri? = null

    // Mode edit: foto yang SUDAH ADA di Firestore, dipakai lagi apa adanya kalau driver tidak
    // pilih foto baru di slot itu - supaya edit kecil (mis. benerin nomor plat) tidak memaksa
    // upload ulang semua dokumen.
    private var existingLicenseUrl: String? = null
    private var existingStnkUrl: String? = null
    private var existingAedUrl: String? = null
    private var existingMedicalUrl: String? = null
    private var existingAmbulanceUrls: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityDriverQualificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isEditMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
        activePhotoTarget = savedInstanceState?.getString("activePhotoTarget")
        latestCapturedUri = savedInstanceState?.getParcelable("latestCapturedUri")

        progressDialog = ProgressDialog(this).apply {
            setMessage("Mengunggah data...")
            setCancelable(false)
        }

        binding.btnPickLicensePhoto.setOnClickListener { checkCameraPermissionThenShowOptions("license") }
        binding.btnPickStnkPhoto.setOnClickListener { checkCameraPermissionThenShowOptions("stnk") }
        binding.btnAddAmbulancePhoto.setOnClickListener { checkCameraPermissionThenShowOptions("ambulance") }
        binding.btnPickAedCert.setOnClickListener { checkCameraPermissionThenShowOptions("aed") }
        binding.btnPickMedicalCert.setOnClickListener { checkCameraPermissionThenShowOptions("medical") }
        binding.btnSubmit.setOnClickListener { submitQualification() }

        binding.etLicenseNumber.addTextChangedListener { validateForm() }
        binding.etAmbulancePlate.addTextChangedListener { validateForm() }
        for (checkbox in listOf(binding.cbSopSirine, binding.cbSopEquipment, binding.cbSopApd, binding.cbSopHealthCheck)) {
            checkbox.setOnCheckedChangeListener { _, _ -> validateForm() }
        }

        if (isEditMode) {
            binding.tvFormTitle.text = "Edit Data Kualifikasi"
            binding.tvFormSubtitle.text = "Perubahan akan dikirim ulang untuk diverifikasi Admin. Anda tidak bisa menerima pesanan sampai disetujui kembali."
            binding.btnSubmit.text = "Simpan Perubahan"
            loadExistingData()
        }
    }

    // Prefill form dengan data yang sudah ada supaya driver tidak perlu isi/upload ulang semuanya
    // dari nol untuk edit kecil.
    private fun loadExistingData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                binding.etLicenseNumber.setText(doc.getString("licenseNumber") ?: "")
                binding.etAmbulancePlate.setText(doc.getString("ambulancePlateNumber") ?: "")

                binding.cbSopSirine.isChecked = doc.getBoolean("sopSirineChecked") ?: false
                binding.cbSopEquipment.isChecked = doc.getBoolean("sopEquipmentChecked") ?: false
                binding.cbSopApd.isChecked = doc.getBoolean("sopApdChecked") ?: false
                binding.cbSopHealthCheck.isChecked = doc.getBoolean("sopHealthCheckChecked") ?: false

                existingLicenseUrl = doc.getString("licensePhotoUrl")?.ifEmpty { null }
                existingStnkUrl = doc.getString("stnkPhotoUrl")?.ifEmpty { null }
                existingAedUrl = doc.getString("aedCertificateUrl")?.ifEmpty { null }
                existingMedicalUrl = doc.getString("medicalCertificateUrl")?.ifEmpty { null }
                @Suppress("UNCHECKED_CAST")
                existingAmbulanceUrls = doc.get("ambulancePhotoUrls") as? List<String> ?: emptyList()

                existingLicenseUrl?.let { Glide.with(this).load(it).into(binding.imgLicensePreview) }
                existingStnkUrl?.let { Glide.with(this).load(it).into(binding.imgStnkPreview) }
                existingAedUrl?.let {
                    binding.imgAedPreview.visibility = View.VISIBLE
                    Glide.with(this).load(it).into(binding.imgAedPreview)
                }
                existingMedicalUrl?.let {
                    binding.imgMedicalPreview.visibility = View.VISIBLE
                    Glide.with(this).load(it).into(binding.imgMedicalPreview)
                }
                renderAmbulanceThumbnails()
                validateForm()
            }
    }

    // --- PILIH SUMBER FOTO (GALERI / KAMERA) --- satu dialog generik dipakai bersama kelima
    // slot foto, dibedakan lewat activePhotoTarget. Reuse pola yang sama dipakai ganti foto
    // profil & kirim foto chat.
    private fun checkCameraPermissionThenShowOptions(target: String) {
        activePhotoTarget = target
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            showPhotoOptionsDialog()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) showPhotoOptionsDialog()
    }

    private fun showPhotoOptionsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.activity_edit_foto_profile, null)
        view.findViewById<TextView>(R.id.editProfileTitle).text = "Pilih Foto Dokumen"

        view.findViewById<Button>(R.id.selectImageButton).setOnClickListener {
            galleryLauncher.launch("image/*")
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.captureImageButton).setOnClickListener {
            openCamera()
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.cancelButton).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = activePhotoTarget
        if (uri != null && target != null) handlePickedPhoto(target, uri)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = latestCapturedUri
        val target = activePhotoTarget
        if (success && uri != null && target != null) {
            handlePickedPhoto(target, uri)
        } else if (success) {
            Toast.makeText(this, "Sesi pengambilan foto berakhir, coba lagi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            val uri = getNewImageUri()
            latestCapturedUri = uri
            cameraLauncher.launch(uri)
        } catch (e: IOException) {
            Log.e("DriverQualification", e.message.toString())
        }
    }

    private fun getNewImageUri(): Uri {
        val photoFile = File.createTempFile("IMG_QUAL_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, photoFile)
    }

    // Salurkan hasil foto (dari galeri ATAU kamera, keduanya berujung ke sini) ke slot yang tepat.
    private fun handlePickedPhoto(target: String, uri: Uri) {
        when (target) {
            "license" -> {
                licensePhotoUri = uri
                binding.imgLicensePreview.setImageURI(uri)
                validateForm()
            }
            "stnk" -> {
                stnkPhotoUri = uri
                binding.imgStnkPreview.setImageURI(uri)
                validateForm()
            }
            "aed" -> {
                aedCertUri = uri
                binding.imgAedPreview.visibility = View.VISIBLE
                binding.imgAedPreview.setImageURI(uri)
            }
            "medical" -> {
                medicalCertUri = uri
                binding.imgMedicalPreview.visibility = View.VISIBLE
                binding.imgMedicalPreview.setImageURI(uri)
            }
            "ambulance" -> {
                ambulancePhotoUris.add(uri)
                renderAmbulanceThumbnails()
                validateForm()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activePhotoTarget?.let { outState.putString("activePhotoTarget", it) }
        latestCapturedUri?.let { outState.putParcelable("latestCapturedUri", it) }
    }

    // Render thumbnail foto ambulans: foto LAMA (remote, mode edit) ditampilkan lebih dulu
    // sebagai referensi tanpa tombol hapus (batasan sengaja - lihat catatan di plan), baru
    // foto BARU yang baru dipilih (bisa dihapus sebelum submit).
    private fun renderAmbulanceThumbnails() {
        val container = binding.containerAmbulancePhotos
        container.removeAllViews()

        val sizePx = (72 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        val removeSizePx = (22 * resources.displayMetrics.density).toInt()

        for (url in existingAmbulanceUrls) {
            val frame = FrameLayout(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginEnd = marginPx
                }
            }
            val thumb = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFEEEEEE.toInt())
            }
            Glide.with(this).load(url).into(thumb)
            frame.addView(thumb)
            container.addView(frame)
        }

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
    // 1 foto ambulans TERSEDIA (baru dipilih ATAU sudah ada dari sebelumnya), DAN keempat
    // checklist SOP tercentang. Sertifikat opsional (AED, BTLS/BHD) tidak menghalangi submit.
    private fun validateForm() {
        val textsFilled = binding.etLicenseNumber.text.toString().trim().isNotEmpty() &&
            binding.etAmbulancePlate.text.toString().trim().isNotEmpty()
        val photosReady = (licensePhotoUri != null || existingLicenseUrl != null) &&
            (stnkPhotoUri != null || existingStnkUrl != null) &&
            (ambulancePhotoUris.isNotEmpty() || existingAmbulanceUrls.isNotEmpty())
        val sopChecked = binding.cbSopSirine.isChecked && binding.cbSopEquipment.isChecked &&
            binding.cbSopApd.isChecked && binding.cbSopHealthCheck.isChecked

        val isValid = textsFilled && photosReady && sopChecked
        binding.btnSubmit.isEnabled = isValid
        binding.btnSubmit.alpha = if (isValid) 1f else 0.5f
    }

    private fun submitQualification() {
        val licenseNumber = binding.etLicenseNumber.text.toString().trim()
        val plateNumber = binding.etAmbulancePlate.text.toString().trim()

        val hasLicense = licensePhotoUri != null || existingLicenseUrl != null
        val hasStnk = stnkPhotoUri != null || existingStnkUrl != null
        val hasAmbulance = ambulancePhotoUris.isNotEmpty() || existingAmbulanceUrls.isNotEmpty()
        if (licenseNumber.isEmpty() || plateNumber.isEmpty() || !hasLicense || !hasStnk || !hasAmbulance) {
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
        uploadOrKeep(uid, "license", licensePhotoUri, existingLicenseUrl) { licenseUrl ->
            uploadOrKeep(uid, "stnk", stnkPhotoUri, existingStnkUrl) { stnkUrl ->
                uploadMultiple(uid, ambulancePhotoUris, "ambulance") { newAmbulanceUrls ->
                    val ambulanceUrls = existingAmbulanceUrls + newAmbulanceUrls
                    uploadOrKeep(uid, "aed_certificate", aedCertUri, existingAedUrl) { aedUrl ->
                        uploadOrKeep(uid, "medical_certificate", medicalCertUri, existingMedicalUrl) { medicalUrl ->
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

    // Kalau ada foto BARU dipilih, upload itu. Kalau tidak, pakai lagi URL lama apa adanya
    // (tidak upload apapun) - inilah yang bikin edit kecil tidak perlu re-upload semua dokumen.
    private fun uploadOrKeep(uid: String, type: String, newUri: Uri?, existingUrl: String?, onDone: (String) -> Unit) {
        if (newUri != null) {
            uploadPhoto(uid, type, newUri, onDone)
        } else {
            onDone(existingUrl ?: "")
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

    // Upload berantai semua foto kondisi ambulans BARU (ambulance_0.jpg, ambulance_1.jpg, dst),
    // dikumpulkan jadi satu List<String> sebelum lanjut ke tahap berikutnya.
    private fun uploadMultiple(uid: String, uris: List<Uri>, prefix: String, onDone: (List<String>) -> Unit) {
        val results = ArrayList<String>()

        fun uploadAt(index: Int) {
            if (index >= uris.size) {
                onDone(results)
                return
            }
            uploadPhoto(uid, "${prefix}_${System.currentTimeMillis()}_$index", uris[index]) { url ->
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
            "sopHealthCheckChecked" to binding.cbSopHealthCheck.isChecked,
            // Submit ulang (baik daftar baru maupun edit) SELALU mengunci ulang ke belum-approved -
            // driver baru memang sudah false dari sign-up, edit MENURUNKAN dari true->false
            // (diizinkan khusus oleh firestore.rules, tidak boleh menaikkan sendiri).
            "isApproved" to false
        )

        firestore.collection("users").document(uid).update(data)
            .addOnSuccessListener {
                if (isEditMode) {
                    finishEditMode()
                } else {
                    sendVerificationAndFinish()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal simpan data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Beda dari submit awal: TIDAK sign-out, TIDAK kirim email verifikasi - driver tetap login,
    // cuma langsung dihentikan online-nya (kalau kebetulan sedang online) karena isApproved
    // baru saja diturunkan ke false, menunggu verifikasi ulang Admin.
    private fun finishEditMode() {
        progressDialog.dismiss()
        stopService(Intent(this, DriverOnlineService::class.java))
        Toast.makeText(
            this,
            "Perubahan disimpan. Menunggu verifikasi ulang Admin - Anda tidak bisa menerima pesanan sementara.",
            Toast.LENGTH_LONG
        ).show()
        finish()
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
