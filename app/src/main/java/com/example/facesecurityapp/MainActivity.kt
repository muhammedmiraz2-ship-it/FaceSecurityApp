package com.example.facesecurityapp

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.facesecurityapp.ui.theme.FaceSecurityAppTheme

class MainActivity : ComponentActivity() {

    pr
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FaceSecurityAppTheme {
                FaceSecurityScreen()
            }
        }
    }
}

@Composable
fun FaceSecurityScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Face Security App",
            fontSize = 28.sp,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Face recognition security system",
            fontSize = 16.sp
        )

        Button(
            onClick = {
                println("Start Face Detection clicked")
            }
        ) {
            Text("Start Face Detection")
        }
    }
}