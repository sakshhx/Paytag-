package com.example.paytagplus

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var prefs: SharedPreferences

    private val RC_GOOGLE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }


        auth = FirebaseAuth.getInstance()
        prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val cbRemember = findViewById<CheckBox>(R.id.cbRemember)
        val tvForgot = findViewById<TextView>(R.id.tvForgotPassword)
        val eyePassword = findViewById<ImageView>(R.id.eyePassword)
        val btnGoogle = findViewById<LinearLayout>(R.id.btnGoogle)

        // ---------------- REMEMBER PASSWORD LOAD ----------------
        etEmail.setText(prefs.getString("email", ""))
        etPassword.setText(prefs.getString("password", ""))
        cbRemember.isChecked = prefs.getBoolean("remember", false)

        // ---------------- PASSWORD EYE TOGGLE ----------------
        // ---------------- PASSWORD EYE TOGGLE ----------------
        var isVisible = false

        eyePassword.setOnClickListener {
            isVisible = !isVisible

            if (isVisible) {
                // Show Password
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyePassword.setImageResource(R.drawable.ic_eye)
            } else {
                // Hide Password
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyePassword.setImageResource(R.drawable.ic_eye_closed)
            }

            // Move cursor to end
            etPassword.setSelection(etPassword.text.length)
        }

        // ---------------- EMAIL LOGIN ----------------
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener {
                    if (it.isSuccessful) {

                        // SAVE REMEMBER PASSWORD
                        if (cbRemember.isChecked) {
                            prefs.edit().putString("email", email)
                                .putString("password", pass)
                                .putBoolean("remember", true)
                                .apply()
                        } else {
                            prefs.edit().clear().apply()
                        }

                        Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()
                        goToHome()
                    } else {
                        Toast.makeText(this, it.exception?.message, Toast.LENGTH_LONG).show()
                    }
                }
        }

        // ---------------- FORGOT PASSWORD ----------------
        tvForgot.setOnClickListener {
            showForgotDialog()
        }

        // ---------------- GOOGLE LOGIN ----------------
        setupGoogleLogin()
        btnGoogle.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE)
        }
    }

    // ---------------- GOOGLE CONFIG ----------------
    private fun setupGoogleLogin() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.result

                if (account.idToken.isNullOrEmpty()) {
                    Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show()
                    return
                }

                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            Toast.makeText(this, "Google Login Success", Toast.LENGTH_SHORT).show()
                            goToHome()
                        } else {
                            Toast.makeText(
                                this,
                                it.exception?.message ?: "Google Login Failed",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            } catch (e: Exception) {
                Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }



    // ---------------- FORGOT PASSWORD DIALOG ----------------
    private fun showForgotDialog() {
        val builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
        val input = EditText(this)
        input.hint = "Enter your email"

        builder.setTitle("Reset Password")
        builder.setView(input)

        builder.setPositiveButton("Send") { _, _ ->
            val email = input.text.toString()
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Reset link sent!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, it.exception?.message, Toast.LENGTH_LONG).show()
                    }
                }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // ---------------- GO TO HOME ----------------
    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
