package com.example.tugasakhirfinal.ForPassenger.Fragment

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.tugasakhirfinal.ForPassenger.EditProfileFragment.ActivityEditEmail
import com.example.tugasakhirfinal.ForPassenger.EditProfileFragment.ActivityEditNama
import com.example.tugasakhirfinal.ForPassenger.EditProfileFragment.ActivityEditNoTlp
import com.example.tugasakhirfinal.ForPassenger.EditProfileFragment.ActivityEditAlamat
import com.example.tugasakhirfinal.RoleSelectionActivity
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.FragmentProfileBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val FILE_PROVIDER_AUTHORITY = "com.example.tugasakhirfinal.fileprovider"

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://tugasakhirfinal-9e73d.firebasestorage.app")
    // Nullable (bukan lateinit) + dipulihkan dari savedInstanceState: kalau OS sempat mematikan
    // proses app selagi aplikasi kamera terbuka di depan, Fragment ini dibuat ulang dari nol dan
    // lateinit var akan crash (UninitializedPropertyAccessException) saat hasil kamera kembali.
    private var latestCapturedUri: Uri? = null

    // Listener untuk pembaruan alamat utama secara real-time
    private var primaryAddressListener: ListenerRegistration? = null

    // Upload foto profil ke Storage/Firestore itu proses async yang butuh waktu - kalau user
    // sempat logout SEBELUM proses ini selesai, sesi login sudah tercabut duluan dan write
    // Firestore-nya bakal ditolak security rules (muncul sebagai "Gagal unggah/simpan foto").
    // Flag ini dipakai buat memblokir tombol logout selama upload masih berjalan.
    private var isUploadingPhoto = false

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
    }

    private val progressDialog by lazy {
        ProgressDialog(requireContext()).apply {
            setCancelable(false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
        latestCapturedUri = savedInstanceState?.getParcelable("latestCapturedUri")

        setupListeners()
        setupLogoutButton()
        setupDeleteAccountButton()

        binding.profileImage.setOnClickListener { checkCameraPermissionThenShowOptions() }
    }

    override fun onResume() {
        super.onResume()
        loadProfileFromFirestore()
        loadPrimaryAddressFromFirestore()
    }

    // Supaya latestCapturedUri selamat kalau OS mematikan proses app selagi aplikasi kamera
    // terbuka di depan (lihat penjelasan di deklarasi field-nya).
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        latestCapturedUri?.let { outState.putParcelable("latestCapturedUri", it) }
    }

    private fun loadProfileFromFirestore() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_binding == null) return

        // 1. Load dari Cache (Instan)
        binding.textNama.text = sharedPrefs.getString("name_$uid", "Tap to set name")
        binding.noTlp.text = sharedPrefs.getString("phone_$uid", "Tap to set phone number")
        binding.email.text = sharedPrefs.getString("email_$uid", "-")
        binding.alamat.text = sharedPrefs.getString("primary_address_label_$uid", "Select Primary Address")
        loadProfileImage(sharedPrefs.getString("img_$uid", null), sharedPrefs.getLong("imgVersion_$uid", 0L))

        // 2. Load Profil dasar (Nama, No Tlp, Email, Foto) dari Firestore
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && isAdded) {
                    val name = doc.getString("nama") ?: "Tap to set name"
                    val phone = doc.getString("phoneNumber") ?: "Tap to set phone number"
                    val email = doc.getString("email") ?: "-"
                    val imageUrl = doc.getString("profileImageUrl")
                    val imageVersion = doc.getLong("profileImageUpdatedAt") ?: 0L

                    // Update UI Profil
                    binding.textNama.text = name
                    binding.noTlp.text = phone
                    binding.email.text = email
                    loadProfileImage(imageUrl, imageVersion)

                    // Simpan ke Cache
                    sharedPrefs.edit().apply {
                        putString("name_$uid", name)
                        putString("phone_$uid", phone)
                        putString("email_$uid", email)
                        putString("img_$uid", imageUrl)
                        putLong("imgVersion_$uid", imageVersion)
                        apply()
                    }
                }
            }
    }

    /**
     * Membaca Alamat Utama (isPrimary = true) dari sub-koleksi addresses secara Real-Time
     */
    private fun loadPrimaryAddressFromFirestore() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_binding == null) return

        // Hentikan listener lama jika ada sebelum membuat baru
        primaryAddressListener?.remove()

        primaryAddressListener = firestore.collection("users").document(uid)
            .collection("addresses")
            .whereEqualTo("isPrimary", true)
            .limit(1) // Mengambil 1 alamat utama saja
            .addSnapshotListener { snapshot, error ->
                if (_binding == null || !isAdded) return@addSnapshotListener

                if (error != null) {
                    binding.alamat.text = "Set Primary Address"
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    // Hanya mengambil label alamat saja (tanpa fullAddress)
                    val label = doc.getString("label") ?: "Primary Address"

                    // Tampilkan hanya nama labelnya agar tampilan Profile ringkas
                    binding.alamat.text = label

                    // Simpan Nama Label Utama ke Cache
                    sharedPrefs.edit().putString("primary_address_label_$uid", label).apply()
                } else {
                    // Jika belum ada alamat yang di-set sebagai Utama
                    binding.alamat.text = "Tap to select Primary Address"
                    sharedPrefs.edit().putString("primary_address_label_$uid", "Tap to select Primary Address").apply()
                }
            }
    }

    // Upload foto selalu overwrite path Storage yang sama (profile_images/{uid}.jpg), jadi
    // URL-nya sering identik dengan sebelumnya. Supaya cache Glide tetap kepakai (cepat) TAPI
    // otomatis invalid begitu foto benar-benar diganti, cache key-nya pakai "version" terpisah
    // (profileImageUpdatedAt, di-set ulang tiap kali upload) lewat signature(), bukan matikan
    // cache total (yang bikin lambat & rawan nyangkut ke foto default kalau network sempat gagal).
    private fun loadProfileImage(url: String?, version: Long = 0L) {
        if (_binding == null) return
        Glide.with(this)
            .load(url?.ifEmpty { null } ?: R.drawable.foto_profil)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(com.bumptech.glide.signature.ObjectKey(version))
            .placeholder(R.drawable.foto_profil)
            .circleCrop()
            .into(binding.profileImage)
    }

    // --- LOGIKA FOTO (KAMERA & GALERI) --- meniru pola ProfileFragmentDriver supaya bentuk
    // popup ganti foto sama persis di kedua role (bottom sheet, bukan dialog activity terpisah).
    private fun checkCameraPermissionThenShowOptions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            showPhotoOptionsDialog()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) showPhotoOptionsDialog()
    }

    private fun showPhotoOptionsDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.activity_edit_foto_profile, null)

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
        uri?.let { uploadProfileImage(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = latestCapturedUri
        if (success && uri != null) {
            uploadProfileImage(uri)
        } else if (success) {
            Toast.makeText(requireContext(), "Photo capture session expired, please try again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            val uri = getNewImageUri()
            latestCapturedUri = uri
            cameraLauncher.launch(uri)
        } catch (e: IOException) {
            Log.e("PassengerProfile", e.message.toString())
        }
    }

    private fun getNewImageUri(): Uri {
        val photoFile = File.createTempFile("IMG_PSG_", ".jpg", requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return FileProvider.getUriForFile(requireContext(), FILE_PROVIDER_AUTHORITY, photoFile)
    }

    // Foto kamera/galeri modern bisa 4000x3000px+ (bahkan puluhan MP di HP flagship). Decode
    // penuh baru di-rotate EXIF baru di-resize (urutan lama) berarti sempat ada bitmap raksasa
    // di memory & CPU sibuk rotate gambar segede itu - inilah yang bikin "stuck di mengunggah
    // foto". Solusinya: decode-nya sendiri sudah diperkecil dari awal via BitmapFactory
    // inSampleSize (baca dimensi dulu tanpa load pixel, baru decode ulang dengan sample size
    // yang pas), jadi rotate/resize sesudahnya sudah bekerja di bitmap kecil, bukan raksasa.
    private fun decodeSampledBitmapFromUri(uri: Uri, reqSize: Int): Bitmap? {
        val resolver = requireContext().contentResolver

        // inJustDecodeBounds=true SELALU membuat decodeStream() balikin null (memang tidak
        // decode pixel apapun, cuma isi outWidth/outHeight) - jadi hasilnya tidak boleh dipakai
        // sebagai penanda gagal/berhasil, cuma stream-nya yang perlu dipastikan berhasil dibuka.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        var inSampleSize = 1
        var halfWidth = boundsOptions.outWidth / 2
        var halfHeight = boundsOptions.outHeight / 2
        while (halfWidth / inSampleSize >= reqSize && halfHeight / inSampleSize >= reqSize) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val decodeStream = resolver.openInputStream(uri) ?: return null
        return decodeStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    // inSampleSize cuma kelipatan 2, jadi hasilnya belum tentu pas <= maxDimension - dirapikan
    // sekali lagi di sini (bitmap-nya sudah kecil di titik ini, jadi operasi ini murah/cepat).
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun uploadProfileImage(uri: Uri) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        // Context aman dipakai di callback async - requireContext() bisa melempar exception
        // kalau fragment ini sudah di-detach saat callback-nya baru jalan belakangan.
        val appContext = requireContext().applicationContext
        try {
            var bitmap = decodeSampledBitmapFromUri(uri, 800)
            if (bitmap == null) {
                Toast.makeText(appContext, "Failed to process photo", Toast.LENGTH_SHORT).show()
                return
            }

            // Rotasi otomatis berdasarkan metadata EXIF (bitmap sudah kecil, jadi cepat)
            val ei = ExifInterface(requireContext().contentResolver.openInputStream(uri)!!)
            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap = resizeBitmap(bitmap, 800)

            isUploadingPhoto = true
            progressDialog.setMessage("Uploading photo...")
            progressDialog.show()

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)

            val storageRef = storage.reference.child("profile_images/$userId.jpg")
            storageRef.putBytes(baos.toByteArray())
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val newVersion = System.currentTimeMillis()
                        val data = mapOf(
                            "profileImageUrl" to downloadUri.toString(),
                            "profileImageUpdatedAt" to newVersion
                        )
                        firestore.collection("users").document(userId)
                            .update(data)
                            .addOnSuccessListener {
                                isUploadingPhoto = false
                                progressDialog.dismiss()
                                if (_binding != null) loadProfileImage(downloadUri.toString(), newVersion)
                            }
                            .addOnFailureListener { e ->
                                // Kalau Firestore menolak write (mis. karena aturan security rules,
                                // atau sesi login sudah tercabut duluan), dialog "Mengunggah foto..."
                                // harus tetap tertutup + ada pesan error, bukan nyangkut diam-diam.
                                isUploadingPhoto = false
                                progressDialog.dismiss()
                                Log.e("PassengerProfile", "Firestore update failed: ${e.message}")
                                Toast.makeText(appContext, "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    isUploadingPhoto = false
                    progressDialog.dismiss()
                    Toast.makeText(appContext, "Failed to upload photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            // Selalu tutup dialog & kasih tahu user kalau ada apa-apa - sebelumnya exception di
            // sini cuma di-log, dialog "Mengunggah foto..." nyangkut selamanya tanpa penjelasan.
            isUploadingPhoto = false
            progressDialog.dismiss()
            Log.e("PassengerProfile", e.message.toString())
            Toast.makeText(appContext, "Failed to process photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        binding.frameNama.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditNama::class.java)
            intent.putExtra("current_name", binding.textNama.text.toString())
            startActivity(intent)
        }
        binding.frameEmail.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditEmail::class.java)
            intent.putExtra("current_email", binding.email.text.toString())
            startActivity(intent)
        }
        binding.frameNoTlp.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditNoTlp::class.java)
            intent.putExtra("current_no_tlp", binding.noTlp.text.toString())
            startActivity(intent)
        }
        // Listener untuk Membuka Daftar Alamat - pastikan izin lokasi sudah diberikan dulu
        // (dibutuhkan saat nanti user menambah/edit alamat lewat peta di AddAddressActivity),
        // baru izin diberikan langsung arahkan ke daftar alamat yang ada.
        binding.frameAlamat.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                openAddressList()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Baik diizinkan maupun ditolak, tetap lanjut ke daftar alamat - AddAddressActivity sendiri
        // sudah menangani permintaan izin lokasi lagi kalau user memilih tambah/edit alamat lewat
        // peta nanti tanpa izin.
        openAddressList()
    }

    private fun openAddressList() {
        startActivity(Intent(requireContext(), ActivityEditAlamat::class.java))
    }

    private fun setupLogoutButton() {
        binding.logoutButton.setOnClickListener {
            if (isUploadingPhoto) {
                Toast.makeText(requireContext(), "Please wait for the profile photo upload to finish first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firebaseAuth.signOut()
            sharedPrefs.edit().clear().apply()
            startActivity(Intent(requireContext(), RoleSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    private fun setupDeleteAccountButton() {
        binding.deleteAccountButton.setOnClickListener {
            showDeleteConfirmStep1()
        }
    }

    // Verifikasi 1: konfirmasi niat menghapus akun
    private fun showDeleteConfirmStep1() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account?")
            .setMessage("Are you sure you want to delete this account? This action cannot be undone.")
            .setPositiveButton("Continue") { dialog, _ ->
                dialog.dismiss()
                showDeleteConfirmStep2()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Verifikasi 2: konfirmasi akhir dengan memasukkan password (sekaligus re-autentikasi)
    private fun showDeleteConfirmStep2() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_account, null)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etDeletePassword)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Delete Permanently") { _, _ ->
                val password = etPassword.text.toString().trim()
                if (password.isEmpty()) {
                    Toast.makeText(requireContext(), "Password is required", Toast.LENGTH_SHORT).show()
                } else {
                    deleteAccountFlow(password)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccountFlow(password: String) {
        val user = firebaseAuth.currentUser ?: return
        val email = user.email
        if (email.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Unable to verify this account.", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.setMessage("Verifying password...")
        progressDialog.show()

        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
                deleteUserDataThenAccount(user)
            } else {
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Incorrect password. Account was not deleted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteUserDataThenAccount(user: FirebaseUser) {
        val uid = user.uid
        progressDialog.setMessage("Deleting account data...")

        firestore.collection("users").document(uid).collection("addresses").get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                for (addressDoc in snapshot.documents) batch.delete(addressDoc.reference)

                batch.commit()
                    .addOnCompleteListener {
                        firestore.collection("users").document(uid).delete()
                            .addOnSuccessListener { finalizeAccountDeletion(user) }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()
                                Toast.makeText(requireContext(), "Failed to delete data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Failed to delete data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun finalizeAccountDeletion(user: FirebaseUser) {
        progressDialog.setMessage("Deleting account...")
        user.delete().addOnCompleteListener { deleteTask ->
            progressDialog.dismiss()
            if (deleteTask.isSuccessful) {
                sharedPrefs.edit().clear().apply()
                Toast.makeText(requireContext(), "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), RoleSelectionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            } else {
                Toast.makeText(requireContext(), "Failed to delete account: ${deleteTask.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Hentikan listener Firestore saat fragment tidak aktif untuk menghemat memory
        primaryAddressListener?.remove()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}