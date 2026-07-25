package com.example.tugasakhirfinal.ForPassenger.EditProfileFragment

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tugasakhirfinal.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class ActivityEditFotoProfile : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_edit_foto_profile)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance("gs://tugasakhirfinal-9e73d.firebasestorage.app")

        progressDialog = ProgressDialog(this).apply {
            setTitle("Upload Foto")
            setMessage("Sedang memproses...")
            setCancelable(false)
        }

        findViewById<Button>(R.id.selectImageButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        findViewById<Button>(R.id.captureImageButton).setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Minta izin kamera secara runtime
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            // Izin sudah ada, buka kamera
            openCamera()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Izin kamera ditolak. Tidak bisa mengambil foto.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            captureImageLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val bitmap = uriToBitmap(uri)
                bitmap?.let { uploadToFirebase(it) }
            }
        }
    }

    private val captureImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                uploadToFirebase(bitmap)
            }
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

    private fun uploadToFirebase(bitmap: Bitmap) {
        val user = firebaseAuth.currentUser
        if (user == null) {
            Toast.makeText(this, "Sesi berakhir, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.show()
        val uid = user.uid
        // Nama file menggunakan UID agar di-overwrite jika upload ulang
        val storageRef = storage.reference.child("profile_images/$uid.jpg")

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos)
        val data = baos.toByteArray()

        Log.d("DEBUG_FIREBASE", "Mencoba upload ke: ${storageRef.path}")

        storageRef.putBytes(data)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    updateFirestoreUrl(uri.toString())
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e("DEBUG_FIREBASE", "Gagal Upload: ${e.message}")
                Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateFirestoreUrl(url: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update("profileImageUrl", url)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e("DEBUG_FIREBASE", "Firestore Gagal: ${e.message}")
            }
    }
}