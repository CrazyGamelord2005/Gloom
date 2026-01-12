package com.desolate.gloom

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.desolate.gloom.databinding.ActivitySignupBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.Toast
import com.desolate.gloom.model.UserModel
import com.desolate.gloom.util.UiUtil
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

class SignupActivity : AppCompatActivity() {

    lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.submitBtn.setOnClickListener {
            signup()
        }

        binding.goToLoginBtn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    fun setInProgress(inProgress : Boolean) {
        if (inProgress) {
            binding.progressBar.visibility = View.VISIBLE
            binding.submitBtn.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.submitBtn.visibility = View.VISIBLE
        }
    }

    fun signup() {
        val email = binding.emailInput.text.toString()
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInput.setError("Email not valid")
            return
        }
        if (password.length < 6) {
            binding.passwordInput.setError("Minimum 6 character")
            return
        }
        if (password != confirmPassword) {
            binding.confirmPasswordInput.setError("Password not matched")
            return
        }
        signupWithSupabase(email, password)
    }

    fun signupWithSupabase(email : String, password: String) {
        setInProgress(true)

        lifecycleScope.launch {
            try {
                val result = SupabaseManager.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                val userId = result?.id ?: SupabaseManager.client.auth.currentUserOrNull()?.id ?: throw Exception("User ID not available")

                val userModel = UserModel(id = userId, email = email, username = email.substringBefore("@"))

                SupabaseManager.client.postgrest["users"].insert(userModel)

                UiUtil.showToast(applicationContext, "Account created successfully")
                setInProgress(false)
                startActivity(Intent(this@SignupActivity, MainActivity::class.java))
                finish()

            } catch (e : Exception) {
                UiUtil.showToast(applicationContext, e.localizedMessage ?: "Something went wrong")
                setInProgress(false)
            }
        }

    }
}