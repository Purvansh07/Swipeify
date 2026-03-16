package com.swipeify

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeify.ui.theme.SwipeifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("swipeify_prefs", MODE_PRIVATE)
        if (prefs.getString("auth_token", null) != null) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContent {
            SwipeifyTheme {
                LoginWithSignUpSheet(
                    onAuthSuccess = {
                        startActivity(Intent(this, OnboardingActivity::class.java))
                        finish()
                    },
                    onSaveToken = { token, user ->
                        getSharedPreferences("swipeify_prefs", MODE_PRIVATE)
                            .edit()
                            .putString("auth_token", token)
                            .putString("username", user.username)
                            .putString("full_name", user.full_name)
                            .commit()
                    },
                    onGoogleSignIn = {
                        Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
                    },
                    onAppleSignIn = {
                        Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWithSignUpSheet(
    onAuthSuccess: () -> Unit,
    onSaveToken: (String, UserData) -> Unit,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSignUpSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D0D), Color(0xFF1A1A2E), Color(0xFF0D0D0D))
                )
            )
    ) {
        // Login screen content
        LoginScreenContent(
            onLoginSuccess = onAuthSuccess,
            onSignUpClick = { showSignUpSheet = true },
            onGoogleSignIn = onGoogleSignIn,
            onAppleSignIn = onAppleSignIn,
            onSaveToken = onSaveToken
        )

        // Sign Up bottom sheet
        if (showSignUpSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSignUpSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF121212),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color(0xFF444444), RoundedCornerShape(2.dp))
                    )
                }
            ) {
                SignUpSheetContent(
                    onSignUpSuccess = {
                        showSignUpSheet = false
                        onAuthSuccess()
                    },
                    onSaveToken = onSaveToken
                )
            }
        }
    }
}

// ─── Login Screen Content ─────────────────────────────────────────────────────

@Composable
fun LoginScreenContent(
    onLoginSuccess: () -> Unit,
    onSignUpClick: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onSaveToken: (String, UserData) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Text("♪", fontSize = 56.sp, color = Color(0xFF1DB954))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Swipeify", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Discover music you'll love.", fontSize = 14.sp, color = Color(0xFFAAAAAA))

        Spacer(modifier = Modifier.height(48.dp))

        // Email field
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email or Username",
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password field
        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible }
        )

        // Don't have an account
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text("Don't have an account? ", color = Color(0xFF888888), fontSize = 12.sp)
            Text(
                "Sign Up",
                color = Color(0xFF1DB954),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onSignUpClick() }
            )
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = Color(0xFFFF4444), fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Login button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    if (isLoading) Color(0xFF155E32) else Color(0xFF1DB954),
                    RoundedCornerShape(28.dp)
                )
                .clickable(enabled = !isLoading) {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all fields"
                        return@clickable
                    }
                    isLoading = true
                    errorMessage = ""
                    MainScope().launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.service.login(LoginRequest(email, password))
                            }
                            onSaveToken(response.token, response.user)
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = "Invalid email or password"
                            isLoading = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Divider
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
            Text("  or  ", color = Color(0xFF666666), fontSize = 13.sp)
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Google button
        SocialAuthButton(
            label = "Continue with Google",
            icon = "G",
            backgroundColor = Color.White,
            textColor = Color(0xFF1A1A1A),
            iconColor = Color(0xFF4285F4),
            onClick = onGoogleSignIn
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Apple button
        SocialAuthButton(
            label = "Continue with Apple",
            icon = "",
            backgroundColor = Color.Black,
            textColor = Color.White,
            iconColor = Color.White,
            borderColor = Color(0xFF444444),
            onClick = onAppleSignIn
        )
    }
}

// ─── Sign Up Bottom Sheet Content ─────────────────────────────────────────────

@Composable
fun SignUpSheetContent(
    onSignUpSuccess: () -> Unit,
    onSaveToken: (String, UserData) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create Account", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Join Swipeify today", fontSize = 14.sp, color = Color(0xFFAAAAAA))

        Spacer(modifier = Modifier.height(28.dp))

        AuthTextField(value = fullName, onValueChange = { fullName = it }, label = "Full Name")
        Spacer(modifier = Modifier.height(12.dp))
        AuthTextField(value = email, onValueChange = { email = it }, label = "Email", keyboardType = KeyboardType.Email)
        Spacer(modifier = Modifier.height(12.dp))
        AuthTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number (optional)", keyboardType = KeyboardType.Phone)
        Spacer(modifier = Modifier.height(12.dp))
        AuthTextField(value = username, onValueChange = { username = it }, label = "Username")
        Spacer(modifier = Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible }
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = Color(0xFFFF4444), fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    if (isLoading) Color(0xFF155E32) else Color(0xFF1DB954),
                    RoundedCornerShape(28.dp)
                )
                .clickable(enabled = !isLoading) {
                    if (fullName.isBlank() || email.isBlank() || username.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all required fields"
                        return@clickable
                    }
                    isLoading = true
                    errorMessage = ""
                    MainScope().launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.service.register(
                                    RegisterRequest(
                                        full_name = fullName,
                                        username = username,
                                        email = email,
                                        password = password
                                    )
                                )
                            }
                            onSaveToken(response.token, response.user)
                            onSignUpSuccess()
                        } catch (e: retrofit2.HttpException) {
                            val errorBody = e.response()?.errorBody()?.string()
                            errorMessage = when {
                                errorBody?.contains("Email already registered") == true -> "Email already registered"
                                errorBody?.contains("Username already taken") == true -> "Username already taken"
                                else -> "Sign up failed. Please try again."
                            }
                            isLoading = false
                        } catch (e: Exception) {
                            errorMessage = "Connection error. Is the server running?"
                            isLoading = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// ─── Shared Components ────────────────────────────────────────────────────────

@Composable
fun SocialAuthButton(
    label: String,
    icon: String,
    backgroundColor: Color,
    textColor: Color,
    iconColor: Color,
    borderColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(backgroundColor, RoundedCornerShape(28.dp))
            .border(1.dp, borderColor, RoundedCornerShape(28.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 18.sp, color = iconColor, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFF888888), fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                Text(
                    if (passwordVisible) "Hide" else "Show",
                    color = Color(0xFF1DB954),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onTogglePassword?.invoke() }
                        .padding(end = 12.dp)
                )
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF1DB954),
            unfocusedBorderColor = Color(0xFF333333),
            cursorColor = Color(0xFF1DB954),
            focusedContainerColor = Color(0xFF1A1A1A),
            unfocusedContainerColor = Color(0xFF1A1A1A)
        )
    )
}