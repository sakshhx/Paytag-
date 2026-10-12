package com.example.paytagplus

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class RegisterActivity : BaseActivity () {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_GOOGLE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        // Views
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnGoogle = findViewById<LinearLayout>(R.id.btnGoogle)
        val eyePassword = findViewById<ImageView>(R.id.eye)
        val eyeConfirm = findViewById<ImageView>(R.id.eyeConfirm)

        // Go to Login
        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // 👁 Password toggle
        var passwordVisible = false
        eyePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            etPassword.inputType =
                if (passwordVisible)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            eyePassword.setImageResource(
                if (passwordVisible) R.drawable.ic_eye else R.drawable.ic_eye_closed
            )
            etPassword.setSelection(etPassword.text.length)
        }

        // 👁 Confirm password toggle
        var confirmVisible = false
        eyeConfirm.setOnClickListener {
            confirmVisible = !confirmVisible
            etConfirmPassword.inputType =
                if (confirmVisible)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            eyeConfirm.setImageResource(
                if (confirmVisible) R.drawable.ic_eye else R.drawable.ic_eye_closed
            )
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        // Email signup
        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val confirmPass = etConfirmPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                toast("Fill all fields")
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                toast("Passwords do not match")
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        toast("Signup Success")
                        goToHome()
                    } else {
                        toast(it.exception?.message ?: "Signup failed")
                    }
                }
        }

        // Google signup
        setupGoogleLogin()
        btnGoogle.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE)
        }
    }

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
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.result
                val credential =
                    GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            toast("Google Signup Success")
                            goToHome()
                        } else {
                            toast("Google Signup Failed")
                        }
                    }
            } catch (e: Exception) {
                toast("Google error: ${e.message}")
            }
        }
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
