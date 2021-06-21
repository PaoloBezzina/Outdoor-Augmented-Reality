package com.example.geofencing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.ar_activity.*
import kotlinx.android.synthetic.main.layout_bg.view.*
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException


class ArActivity : AppCompatActivity() {

    private val TAG = ArActivity::class.java.simpleName
    private var installRequested: Boolean = false
    private var session: Session? = null
    private var shouldConfigureSession = false
    private val messageSnackbarHelper = SnackbarHelper()
    internal lateinit var dataView: CompletableFuture<ViewRenderable>
    var sensorsMap = HashMap<String, ViewRenderable>()
    val degree: Char = '\u00B0'

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.ar_activity)

        initializeSceneView()
        initializeQRScanner()

        val goToARbutton: ImageButton = findViewById(R.id.back_to_map_button)
        goToARbutton.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG
            )
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

    private fun initializeSceneView() {

        makeInfoView()
        arSceneView.scene.setOnUpdateListener(this::onUpdateFrame)

    }


    private fun initializeQRScanner() {
        val options = FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(
                FirebaseVisionBarcode.FORMAT_QR_CODE,
                FirebaseVisionBarcode.FORMAT_AZTEC
            )
            .build()
    }

    private fun onUpdateFrame(frameTime: FrameTime) {
        frameTime.toString()
        val frame = arSceneView.arFrame
        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        for (augmentedImage in updatedAugmentedImages) {
            if (augmentedImage.trackingState == TrackingState.TRACKING) {
                // Check camera image matches our reference image
                if (augmentedImage.name == "info_tag") {
                    takePhoto(augmentedImage)
                }
            }
        }
    }

    fun takeScreenshot(bitmap: Bitmap, augmentedImage: AugmentedImage) {

        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance()
            .visionBarcodeDetector

        val result = detector.detectInImage(image)
            .addOnSuccessListener { barcodes ->
                val names = sensorsMap.keys

                if (barcodes.size > 0) {
                    val sensorId = barcodes[0].displayValue!!
                    if (!names.contains(sensorId)) {
                        createRenderable(augmentedImage)
                    }
                }
            }
            .addOnFailureListener {
                print(it.toString())
            }
    }

    private fun takePhoto(augmentedImage: AugmentedImage) {

        // Create a bitmap the size of the scene view.
        val bitmap = Bitmap.createBitmap(
            arSceneView.width, arSceneView.height,
            Bitmap.Config.ARGB_8888
        )

        // Create a handler thread to offload the processing of the image.
        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()

        try {
            PixelCopy.request(arSceneView, bitmap, { copyResult ->
                if (copyResult === PixelCopy.SUCCESS) {
                    takeScreenshot(bitmap, augmentedImage)

                } else {
                    Log.d("DrawAR", "Failed to copyPixels: $copyResult")
                    val toast = Toast.makeText(
                        this@ArActivity,
                        "Failed to copyPixels: $copyResult", Toast.LENGTH_LONG
                    )
                    toast.show()
                }
                handlerThread.quitSafely()
            }, Handler(handlerThread.looper))
        } catch (e: java.lang.Exception) {

        }
    }

    private fun setupAugmentedImageDb(config: Config): Boolean {

        val augmentedImageBitmap = loadAugmentedImage() ?: return false

        val augmentedImageDatabase = AugmentedImageDatabase(session)
        augmentedImageDatabase.addImage("info_tag", augmentedImageBitmap)

        config.augmentedImageDatabase = augmentedImageDatabase
        return true
    }

    private fun loadAugmentedImage(): Bitmap? {
        try {
            assets.open("info_tag.jpg").use { `is` -> return BitmapFactory.decodeStream(`is`) }
        } catch (e: IOException) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e)
        }

        return null
    }

    private fun configureSession() {
        val config = Config(session)
        config.focusMode = Config.FocusMode.AUTO
        if (!setupAugmentedImageDb(config)) {
            messageSnackbarHelper.showError(this, "Could not setup augmented image database")
        }
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        session!!.configure(config)
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                    }
                }

                // ARCore requires camera permissions to operate. If we did not yet obtained, request them.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this)
                    return
                }

                session = Session(/* context = */this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: Exception) {
                message = "This device does not support AR"
                exception = e
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message)
                Log.e(TAG, "Exception creating session", exception)
                return
            }

            shouldConfigureSession = true
        }

        if (shouldConfigureSession) {
            configureSession()
            shouldConfigureSession = false
            arSceneView.setupSession(session)
        }

        try {
            session!!.resume()
            arSceneView.resume()
        } catch (e: CameraNotAvailableException) {
            /* In some cases (such as another camera app launching) the camera may be given to
            a different app instead. Handle this properly by showing a message and recreate the
            session at the next iteration.*/
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.")
            session = null
            return
        }

    }

    private fun createRenderable(augmentedImage: AugmentedImage) {
        var renderable: ViewRenderable? = null
        try {
            renderable = dataView.get()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }
        val node = Node()
        try {

            val anchorNode = AnchorNode(arSceneView.session.createAnchor(augmentedImage.centerPose))
            arSceneView.scene.removeChild(anchorNode)
            val pose = Pose.makeTranslation(0.0f, 0.0f, 0.12f)
            node.localPosition = Vector3(pose.tx(), pose.ty(), pose.tz())

            node.renderable = renderable
            node.setParent(anchorNode)
            node.localRotation = Quaternion(pose.qx(), 90f, -90f, pose.qw())

            arSceneView.scene.addChild(anchorNode)

            val name = GeofenceBroadcastReceiver.currentGeofenceId

            setNodeData(renderable!!, name)
            sensorsMap[name] = renderable
            makeInfoView()

        } catch (e: Exception) {
            e.toString()
        }
    }

    fun setNodeData(viewRenderable: ViewRenderable, name: String) {

        val index = MapsActivity.getLandmark(name)

        val view = viewRenderable.view
        view.landmarkName.text = MapsActivity.landmarks[index].name
        view.landmarkDate.text = MapsActivity.landmarks[index].date
        view.landmarkDesc.text = MapsActivity.landmarks[index].desc

    }

    fun makeInfoView() {
        dataView = ViewRenderable.builder().setView(this, R.layout.layout_bg).build()
    }
}
