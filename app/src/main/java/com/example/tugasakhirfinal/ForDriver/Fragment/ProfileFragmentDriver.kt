package com.example.tugasakhirfinal.ForDriver.Fragment

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import com.example.tugasakhirfinal.ForDriver.DriverOnlineService
import com.example.tugasakhirfinal.ForPassenger.EditProfileFragment.ActivityEditNama
import com.example.tugasakhirfinal.ForPassenger.EditProfileFragment.ActivityEditNoTlp
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.RoleSelectionActivity
import com.example.tugasakhirfinal.databinding.FragmentProfileDriverBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val FILE_PROVIDER_AUTHORITY = "com.example.tugasakhirfinal.fileprovider"

class ProfileFragmentDriver : Fragment() {

    private var _binding: FragmentProfileDriverBinding? = null
    private val binding get() = _binding!!

    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://tugasakhirfinal-9e73d.firebasestorage.app")
    private lateinit var latestCapturedUri: Uri

    private val sharedPreferences: SharedPreferences by lazy {
        requireActivity().getSharedPreferences("driver_preferences", Context.MODE_PRIVATE)
    }

    private val progressDialog by lazy {
        ProgressDialog(requireContext()).apply {
            setCancelable(false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileDriverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()

        setupListeners()
        loadProfileFromFirestore()
    }

    override fun onResume() {
        super.onResume()
        loadProfileFromFirestore()
    }

    private fun loadProfileFromFirestore() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        if (_binding == null) return

        // 1. Tampilkan cache dulu (instan) supaya pindah dari Home ke Profile tidak terasa
        // lama menunggu request Firestore, sama seperti pola di ProfileFragment (Passenger).
        binding.textNama.text = sharedPreferences.getString("name_$userId", "Driver Baru")
        binding.noTlp.text = sharedPreferences.getString("phone_$userId", "Atur Nomor")
        binding.alamat.text = sharedPreferences.getString("alamat_$userId", "Atur Lokasi")
        loadProfileImage(sharedPreferences.getString("img_$userId", null), sharedPreferences.getLong("imgVersion_$userId", 0L))

        // 2. Ambil data terbaru dari Firestore di belakang layar, lalu update UI + cache
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && isAdded) {
                    val name = doc.getString("nama") ?: "Driver Baru"
                    val phone = doc.getString("phoneNumber") ?: "Atur Nomor"
                    val alamat = doc.getString("alamat") ?: "Atur Lokasi"
                    val imageUrl = doc.getString("profileImageUrl")
                    val imageVersion = doc.getLong("profileImageUpdatedAt") ?: 0L

                    binding.textNama.text = name
                    binding.noTlp.text = phone
                    binding.alamat.text = alamat
                    loadProfileImage(imageUrl, imageVersion)

                    sharedPreferences.edit().apply {
                        putString("name_$userId", name)
                        putString("phone_$userId", phone)
                        putString("alamat_$userId", alamat)
                        putString("img_$userId", imageUrl)
                        putLong("imgVersion_$userId", imageVersion)
                        apply()
                    }
                }
            }
    }

    // Cache key Glide pakai "version" (profileImageUpdatedAt) supaya cache normal tetap kepakai
    // (cepat, tidak perlu network tiap buka Profile) TAPI otomatis invalid begitu foto diganti -
    // lihat penjelasan sama di ProfileFragment (Passenger).
    private fun loadProfileImage(url: String?, version: Long = 0L) {
        Glide.with(this)
            .load(url?.ifEmpty { null } ?: R.drawable.foto_profil)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(com.bumptech.glide.signature.ObjectKey(version))
            .placeholder(R.drawable.foto_profil)
            .circleCrop()
            .into(binding.profileImage)
    }

    private fun setupListeners() {
        binding.profileImage.setOnClickListener { checkPermissions() }
        binding.frameNama.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditNama::class.java)
            intent.putExtra("current_name", binding.textNama.text.toString())
            startActivity(intent)
        }
        binding.frameNoTlp.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditNoTlp::class.java)
            intent.putExtra("current_no_tlp", binding.noTlp.text.toString())
            startActivity(intent)
        }
        binding.logoutButton.setOnClickListener {
            requireContext().stopService(Intent(requireContext(), DriverOnlineService::class.java))
            firebaseAuth.signOut()
            startActivity(Intent(requireContext(), RoleSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
        binding.deleteAccountButton.setOnClickListener {
            showDeleteConfirmStep1()
        }
    }

    // Verifikasi 1: konfirmasi niat menghapus akun
    private fun showDeleteConfirmStep1() {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Akun?")
            .setMessage("Apakah Anda yakin ingin menghapus akun Driver ini? Tindakan ini tidak dapat dibatalkan.")
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
        progressDialog.setMessage("Menghapus data akun...")
        firestore.collection("users").document(user.uid).delete()
            .addOnSuccessListener { finalizeAccountDeletion(user) }
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
                requireContext().stopService(Intent(requireContext(), DriverOnlineService::class.java))
                sharedPreferences.edit().clear().apply()
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

    // --- LOGIKA FOTO (KAMERA & GALERI) ---
    private fun checkPermissions() {
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
        } catch (e: IOException) { Log.e("DriverProfile", e.message.toString()) }
    }

    private fun getNewImageUri(): Uri {
        val photoFile = File.createTempFile("IMG_DRV_", ".jpg", requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return FileProvider.getUriForFile(requireContext(), FILE_PROVIDER_AUTHORITY, photoFile)
    }

    // Upload ke Firebase Storage (BUKAN disimpan lokal di device) supaya foto profil driver
    // benar-benar bisa dilihat dari device lain (mis. passenger saat menunggu jemputan).
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
            Log.e("DriverProfile", e.message.toString())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}