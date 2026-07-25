package com.example.tugasakhirfinal.ForDriver.Welcome

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.databinding.ActivityDriverQualificationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

// Diisi driver setelah akun dibuat (SignUpActivityDriver), sebelum verifikasi email.
// Data ini (No. SIM, No. Plat Ambulans, foto dokumen) dipakai Admin untuk menilai kelayakan
// driver sebelum menyetujui (isApproved=true) lewat ApproveDriverActivity.
class DriverQualificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverQualificationBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://tugasakhirfinal-9e73d.firebasestorage.app")
    private lateinit var progressDialog: ProgressDialog

    private var licensePhotoUri: Uri? = null
    private var ambulancePhotoUri: Uri? = null

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
        binding.btnPickAmbulancePhoto.setOnClickListener { pickAmbulanceLauncher.launch("image/*") }
        binding.btnSubmit.setOnClickListener { submitQualification() }
    }

    private val pickLicenseLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            licensePhotoUri = it
            binding.imgLicensePreview.setImageURI(it)
        }
    }

    private val pickAmbulanceLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            ambulancePhotoUri = it
            binding.imgAmbulancePreview.setImageURI(it)
        }
    }

    private fun submitQualification() {
        val licenseNumber = binding.etLicenseNumber.text.toString().trim()
        val plateNumber = binding.etAmbulancePlate.text.toString().trim()

        if (licenseNumber.isEmpty() || plateNumber.isEmpty()) {
            Toast.makeText(this, "Nomor SIM dan Plat Ambulans wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        val licenseUri = licensePhotoUri
        val ambulanceUri = ambulancePhotoUri
        if (licenseUri == null || ambulanceUri == null) {
            Toast.makeText(this, "Foto SIM dan foto Ambulans wajib diunggah", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sesi berakhir, silakan daftar ulang", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.show()
        uploadPhoto(uid, "license", licenseUri) { licenseUrl ->
            uploadPhoto(uid, "ambulance", ambulanceUri) { ambulanceUrl ->
                saveQualificationData(uid, licenseNumber, plateNumber, licenseUrl, ambulanceUrl)
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
        ambulanceUrl: String
    ) {
        val data = hashMapOf<String, Any>(
            "licenseNumber" to licenseNumber,
            "ambulancePlateNumber" to plateNumber,
            "licensePhotoUrl" to licenseUrl,
            "ambulancePhotoUrl" to ambulanceUrl
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
