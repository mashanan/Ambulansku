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
    private lateinit var latestCapturedUri: Uri

    // Listener untuk pembaruan alamat utama secara real-time
    private var primaryAddressListener: ListenerRegistration? = null

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

    private fun loadProfileFromFirestore() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_binding == null) return

        // 1. Load dari Cache (Instan)
        binding.textNama.text = sharedPrefs.getString("name_$uid", "Klik untuk atur nama")
        binding.noTlp.text = sharedPrefs.getString("phone_$uid", "Klik untuk atur telepon")
        binding.email.text = sharedPrefs.getString("email_$uid", "-")
        binding.alamat.text = sharedPrefs.getString("primary_address_label_$uid", "Pilih Alamat Utama")
        loadProfileImage(sharedPrefs.getString("img_$uid", null), sharedPrefs.getLong("imgVersion_$uid", 0L))

        // 2. Load Profil dasar (Nama, No Tlp, Email, Foto) dari Firestore
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && isAdded) {
                    val name = doc.getString("nama") ?: "Klik untuk atur nama"
                    val phone = doc.getString("phoneNumber") ?: "Klik untuk atur telepon"
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
                    binding.alamat.text = "Atur Alamat Utama"
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    // Hanya mengambil label alamat saja (tanpa fullAddress)
                    val label = doc.getString("label") ?: "Alamat Utama"

                    // Tampilkan hanya nama labelnya agar tampilan Profile ringkas
                    binding.alamat.text = label

                    // Simpan Nama Label Utama ke Cache
                    sharedPrefs.edit().putString("primary_address_label_$uid", label).apply()
                } else {
                    // Jika belum ada alamat yang di-set sebagai Utama
                    binding.alamat.text = "Klik untuk memilih Alamat Utama"
                    sharedPrefs.edit().putString("primary_address_label_$uid", "Klik untuk memilih Alamat Utama").apply()
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
        if (success) uploadProfileImage(latestCapturedUri)
    }

    private fun openCamera() {
        try {
            latestCapturedUri = getNewImageUri()
            cameraLauncher.launch(latestCapturedUri)
        } catch (e: IOException) {
            Log.e("PassengerProfile", e.message.toString())
        }
    }

    private fun getNewImageUri(): Uri {
        val photoFile = File.createTempFile("IMG_PSG_", ".jpg", requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return FileProvider.getUriForFile(requireContext(), FILE_PROVIDER_AUTHORITY, photoFile)
    }

    private fun uploadProfileImage(uri: Uri) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            var bitmap = BitmapFactory.decodeStream(inputStream)

            // Rotasi otomatis berdasarkan metadata EXIF
            val ei = ExifInterface(requireContext().contentResolver.openInputStream(uri)!!)
            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            progressDialog.setMessage("Mengunggah foto...")
            progressDialog.show()

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)

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
                                progressDialog.dismiss()
                                loadProfileImage(downloadUri.toString(), newVersion)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Gagal unggah foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("PassengerProfile", e.message.toString())
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
        // Listener untuk Membuka Daftar Alamat
        binding.frameAlamat.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditAlamat::class.java)
            startActivity(intent)
        }
    }

    private fun setupLogoutButton() {
        binding.logoutButton.setOnClickListener {
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
            .setTitle("Hapus Akun?")
            .setMessage("Apakah Anda yakin ingin menghapus akun ini? Tindakan ini tidak dapat dibatalkan.")
            .setPositiveButton("Lanjutkan") { dialog, _ ->
                dialog.dismiss()
                showDeleteConfirmStep2()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Verifikasi 2: konfirmasi akhir dengan memasukkan password (sekaligus re-autentikasi)
    private fun showDeleteConfirmStep2() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_account, null)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etDeletePassword)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Hapus Permanen") { _, _ ->
                val password = etPassword.text.toString().trim()
                if (password.isEmpty()) {
                    Toast.makeText(requireContext(), "Password wajib diisi", Toast.LENGTH_SHORT).show()
                } else {
                    deleteAccountFlow(password)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteAccountFlow(password: String) {
        val user = firebaseAuth.currentUser ?: return
        val email = user.email
        if (email.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Tidak dapat memverifikasi akun ini.", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.setMessage("Memverifikasi password...")
        progressDialog.show()

        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
                deleteUserDataThenAccount(user)
            } else {
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Password salah. Akun tidak dihapus.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteUserDataThenAccount(user: FirebaseUser) {
        val uid = user.uid
        progressDialog.setMessage("Menghapus data akun...")

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
                                Toast.makeText(requireContext(), "Gagal menghapus data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Gagal menghapus data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun finalizeAccountDeletion(user: FirebaseUser) {
        progressDialog.setMessage("Menghapus akun...")
        user.delete().addOnCompleteListener { deleteTask ->
            progressDialog.dismiss()
            if (deleteTask.isSuccessful) {
                sharedPrefs.edit().clear().apply()
                Toast.makeText(requireContext(), "Akun berhasil dihapus.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), RoleSelectionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            } else {
                Toast.makeText(requireContext(), "Gagal menghapus akun: ${deleteTask.exception?.message}", Toast.LENGTH_LONG).show()
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