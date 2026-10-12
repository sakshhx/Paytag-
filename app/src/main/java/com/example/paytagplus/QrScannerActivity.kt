package com.example.paytagplus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.paytagplus.utils.BitmapHolder
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class QrScannerActivity : BaseActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var galleryBtn: AppCompatButton
    private lateinit var btnFlash: ImageView

    private var isScanned = false
    private var isFlashOn = false
    private var camera: Camera? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processGalleryImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        previewView = findViewById(R.id.previewView)
        galleryBtn  = findViewById(R.id.btnGallery)
        btnFlash    = findViewById(R.id.btnTorch)

        // Force icon white on blue background
        galleryBtn.compoundDrawableTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        galleryBtn.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        btnFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            camera?.cameraControl?.enableTorch(isFlashOn)
        }

        findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            finish()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val barcodeScanner = BarcodeScanning.getClient()

            val imageAnalysis = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val bitmap = imageProxy.toBitmap()
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    if (isScanned) break
                                    barcode.rawValue?.let { value ->
                                        isScanned = true
                                        BitmapHolder.qrBitmap = bitmap
                                        val upiId     = extractUpiId(value)
                                        val payeeName = extractPayeeName(value)
                                        startActivity(
                                            Intent(this, ScannedUpiActivity::class.java)
                                                .putExtra("upi_id", upiId ?: "Invalid UPI")
                                                .putExtra("payee_name", payeeName ?: "Unknown")
                                        )
                                        finish()
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
            }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processGalleryImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            BitmapHolder.qrBitmap = bitmap

            val image   = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isEmpty()) {
                        Toast.makeText(this, "No QR found in image", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { value ->
                            val upiId     = extractUpiId(value)
                            val payeeName = extractPayeeName(value)
                            startActivity(
                                Intent(this, ScannedUpiActivity::class.java)
                                    .putExtra("upi_id", upiId ?: "Invalid UPI")
                                    .putExtra("payee_name", payeeName ?: "Unknown")
                            )
                            finish()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "No QR found", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractUpiId(rawValue: String): String? =
        try { Uri.parse(rawValue).getQueryParameter("pa") }
        catch (e: Exception) { null }

    private fun extractPayeeName(rawValue: String): String? =
        try { Uri.parse(rawValue).getQueryParameter("pn")?.replace("%20", " ") }
        catch (e: Exception) { null }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}