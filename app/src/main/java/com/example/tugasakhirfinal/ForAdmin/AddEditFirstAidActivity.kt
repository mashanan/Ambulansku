package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.tugasakhirfinal.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// Admin menambah/mengedit isi list Pertolongan Pertama (dipakai Passenger). Sama seperti
// AddEditHospitalActivity: mode Tambah kalau EXTRA_ID tidak dikirim, mode Edit kalau dikirim.
class AddEditFirstAidActivity : AppCompatActivity() {

    private var editingId: String? = null
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        const val EXTRA_ID = "FIRST_AID_ID"
        const val EXTRA_TITLE = "FIRST_AID_TITLE"
        const val EXTRA_SYMPTOMS = "FIRST_AID_SYMPTOMS"
        const val EXTRA_STEPS = "FIRST_AID_STEPS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_add_edit_first_aid)

        editingId = intent.getStringExtra(EXTRA_ID)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val saveButton = findViewById<AppCompatButton>(R.id.btnSaveFirstAid)

        if (editingId != null) {
            findViewById<TextView>(R.id.tvFormTitle).text = "Edit First Aid"
            saveButton.text = "Save Changes"

            findViewById<TextInputEditText>(R.id.etConditionTitle).setText(intent.getStringExtra(EXTRA_TITLE))
            findViewById<TextInputEditText>(R.id.etConditionSymptoms).setText(intent.getStringExtra(EXTRA_SYMPTOMS))
            findViewById<TextInputEditText>(R.id.etConditionSteps).setText(intent.getStringExtra(EXTRA_STEPS))
        }

        saveButton.setOnClickListener { saveCondition() }
    }

    private fun saveCondition() {
        val title = findViewById<TextInputEditText>(R.id.etConditionTitle).text.toString().trim()
        val symptoms = findViewById<TextInputEditText>(R.id.etConditionSymptoms).text.toString().trim()
        val stepsRaw = findViewById<TextInputEditText>(R.id.etConditionSteps).text.toString()
        val steps = stepsRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        if (title.isEmpty() || symptoms.isEmpty()) {
            Toast.makeText(this, "Condition name and symptoms are required", Toast.LENGTH_SHORT).show()
            return
        }
        if (steps.isEmpty()) {
            Toast.makeText(this, "At least 1 first aid step is required", Toast.LENGTH_SHORT).show()
            return
        }

        val id = editingId
        val task = if (id != null) {
            // "customized": tandai entri yang pernah diedit Admin, supaya tombol "perbarui
            // panduan bawaan" di ManageFirstAidActivity tidak menimpa hasil edit ini.
            val data = mapOf<String, Any>("title" to title, "symptoms" to symptoms, "steps" to steps, "customized" to true)
            firestore.collection("firstAidConditions").document(id).update(data)
        } else {
            val data = mapOf(
                "title" to title,
                "symptoms" to symptoms,
                "steps" to steps,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("firstAidConditions").add(data)
        }

        task.addOnSuccessListener {
            val message = if (id != null) "Updated successfully" else "Added successfully"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
