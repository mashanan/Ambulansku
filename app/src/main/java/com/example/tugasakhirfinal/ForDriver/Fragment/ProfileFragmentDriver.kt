package com.example.tugasakhirfinal.ForDriver.Fragment

import android.Manifest
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.RoleSelectionActivity
import com.example.tugasakhirfinal.databinding.FragmentProfileDriverBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val FILE_PROVIDER_AUTHORITY = "com.example.tugasakhirfinal.fileprovider"

class ProfileFragmentDriver : Fragment() {

    private var _binding: FragmentProfileDriverBinding? = null
    private val binding get() = _binding!!

    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var latestCapturedUri: Uri

    private val sharedPreferences: SharedPreferences by lazy {
        requireActivity().getSharedPreferences("driver_preferences", Context.MODE_PRIVATE)
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

        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && isAdded) {
                    binding.textNama.text = doc.getString("nama") ?: "Driver Baru"
                    binding.noTlp.text = doc.getString("phoneNumber") ?: "Atur Nomor"
                    binding.alamat.text = doc.getString("alamat") ?: "Atur Lokasi"

                    val path = doc.getString("profileImageUrl")
                    loadProfileImage(path)
                }
                _binding?.progressBar?.visibility = View.GONE
            }
            .addOnFailureListener { _binding?.progressBar?.visibility = View.GONE }
    }

    private fun loadProfileImage(path: String?) {
        val userId = firebaseAuth.currentUser?.uid
        val finalPath = path ?: sharedPreferences.getString("local_driver_path_$userId", null)

        Glide.with(this)
            .load(if (finalPath != null) File(finalPath) else R.drawable.foto_profil)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .placeholder(R.drawable.foto_profil)
            .circleCrop()
            .into(binding.profileImage)
    }

    private fun setupListeners() {
        binding.profileImage.setOnClickListener { checkPermissions() }
        binding.logoutButton.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(requireContext(), RoleSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
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
        dialog.setContentView(view)
        dialog.show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { saveImageToInternalStorage(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) saveImageToInternalStorage(latestCapturedUri)
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

    private fun saveImageToInternalStorage(uri: Uri) {
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

            val file = File(requireContext().filesDir, "driver_profile_$userId.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.close()

            sharedPreferences.edit().putString("local_driver_path_$userId", file.absolutePath).apply()
            firestore.collection("users").document(userId).update("profileImageUrl", file.absolutePath)
                .addOnSuccessListener { loadProfileImage(file.absolutePath) }
        } catch (e: Exception) { Log.e("DriverProfile", e.message.toString()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}