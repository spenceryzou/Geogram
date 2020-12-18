package edu.ucsb.cs.cs184.spencerzou.szougeopics


import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.location.LocationManager
import android.media.ExifInterface
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import edu.ucsb.cs.cs184.spencerzou.szougeopics.util.lerp
import edu.ucsb.cs.cs184.spencerzou.szougeopics.util.lerpArgb
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.android.synthetic.main.fragment_lessons_sheet.*
import kotlinx.android.synthetic.main.fragment_marker_sheet.*
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

data class Post(
    var name: String? = "",
    var caption: String? = "",
    var latitude: Double? = null,
    var longitude: Double? = null,
    var imagePath: String? = "",
    var image: Long? = null,
    var likesLength: Int = 0
)
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var marker: Marker
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    var defaultImagesIndex = 0
    var defaultImages = arrayOf(1573537538000, 1573537538001, 1573537538002, 1573537538003, 1573537538004, 1573537538005, 1573537538006, 1573537538007, 1573537538008, 1573537538009)
    var markerExists = 0
    var fromCameraFab = false
    var cameraOnFlag = false
    var onFlag = 0
    private lateinit var takenPhotoUri : Uri
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var markerBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private var mAuth: FirebaseAuth? = null
    private val CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE: Int = 1034
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: Int = 1
    private val RC_SIGN_IN: Int = 1
    private val APP_TAG = "szouGeoPics"
    var PERMISSION_ALL = 1
    var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )
    fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // set up PERSISTENT BOTTOM SHEET for marker on click
        markerBottomSheetBehavior = BottomSheetBehavior.from(marker_sheet)
        marker_sheet.setBackgroundColor(Color.WHITE)
        markerBottomSheetBehavior.isHideable = true
        markerBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        markerBottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    marker_sheet_expand.alpha = lerp(1f, 0f, 0f, 0.15f, slideOffset)
                    marker_sheet_expand.visibility = if (slideOffset < 0.5) View.VISIBLE else View.GONE
                }
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            Toast.makeText(
                                applicationContext,
                                "STATE_COLLAPSED",
                                Toast.LENGTH_SHORT
                            ).show()
                                if(onFlag == 1) {
                                    markerFab.show()
                                } else {
                                    cameraFab.show()
                                    markerFab.show()
                                }
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            Toast.makeText(
                                applicationContext,
                                "STATE_EXPANDED",
                                Toast.LENGTH_SHORT
                            ).show()
                            markerFab.hide()
                            cameraFab.hide()
                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {
                            Toast.makeText(
                                applicationContext,
                                "STATE_DRAGGING",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        BottomSheetBehavior.STATE_SETTLING -> Toast.makeText(
                            applicationContext,
                            "STATE_SETTLING",
                            Toast.LENGTH_SHORT
                        ).show()
                        BottomSheetBehavior.STATE_HIDDEN -> Toast.makeText(
                            applicationContext,
                            "STATE_HIDDEN",
                            Toast.LENGTH_SHORT
                        ).show()
                        else -> Toast.makeText(
                            applicationContext,
                            "OTHER_STATE",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        marker_collapse_playlist.setOnClickListener{
            markerBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        marker_sheet_expand.setOnClickListener{
            markerBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            markerFab.hide()
            cameraFab.hide()
        }
        // set up PERSISTENT BOTTOM SHEET for posting
        // https://blog.mindorks.com/android-bottomsheet-in-kotlin
        // https://www.androidhive.info/2017/12/android-working-with-bottom-sheet/
        // https://github.com/material-components/material-components-android-examples/tree/develop/Owl
        bottomSheetBehavior = BottomSheetBehavior.from(lessons_sheet)
        val fab: FloatingActionButton = findViewById(R.id.markerFab)
        val sheetStartColor = lessons_sheet.context.getColor(R.color.owl_pink_500)
        val sheetBackground = MaterialShapeDrawable(
            ShapeAppearanceModel.builder(
                lessons_sheet.context,
                R.style.roundCorner,
                0
            ).build()
        ).apply {
                fillColor = ColorStateList.valueOf(sheetStartColor)
            }
        val sheetEndColor = lessons_sheet.context.getColor(R.color.colorSecondary)
        lessons_sheet.background = sheetBackground
        lessons_sheet.doOnLayout {
            val peek = bottomSheetBehavior.peekHeight + post_title.width
            val maxTranslationX = (it.width - peek).toFloat()
            Log.d("view width", it.width.toString())
            lessons_sheet.translationX = (it.width).toFloat()
            bottomSheetBehavior.addBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    lessons_sheet.translationX =
                        lerp(maxTranslationX, 0f, 0f, 0.15f, slideOffset)
                    sheetBackground.interpolation = lerp(1f, 0f, 0f, 0.15f, slideOffset)
                    sheetBackground.fillColor = ColorStateList.valueOf(
                        lerpArgb(
                            sheetStartColor,
                            sheetEndColor,
                            0f,
                            0.3f,
                            slideOffset
                        )
                    )
                    post_title.alpha = lerp(1f, 0f, 0f, 0.15f, slideOffset)
                    playlist_icon.alpha = lerp(1f, 0f, 0f, 0.15f, slideOffset)
                    sheet_expand.alpha = lerp(1f, 0f, 0f, 0.15f, slideOffset)
                    sheet_expand.visibility = if (slideOffset < 0.5) View.VISIBLE else View.GONE
                    playlist_title.alpha = lerp(0f, 1f, 0.2f, 0.8f, slideOffset)
                    collapse_playlist.alpha = lerp(0f, 1f, 0.2f, 0.8f, slideOffset)
                    playlist_title_divider.alpha = lerp(0f, 1f, 0.2f, 0.8f, slideOffset)
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            Toast.makeText(
                                applicationContext,
                                "STATE_COLLAPSED",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (cameraOnFlag)
                                cameraFab.show()
                            else
                                fab.show()
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> Toast.makeText(
                            applicationContext,
                            "STATE_EXPANDED",
                            Toast.LENGTH_SHORT
                        ).show()
                        BottomSheetBehavior.STATE_DRAGGING -> {
                            Toast.makeText(
                                applicationContext,
                                "STATE_DRAGGING",
                                Toast.LENGTH_SHORT
                            ).show()
                            fab.hide()
                            cameraFab.hide()
                        }
                        BottomSheetBehavior.STATE_SETTLING -> Toast.makeText(
                            applicationContext,
                            "STATE_SETTLING",
                            Toast.LENGTH_SHORT
                        ).show()
                        BottomSheetBehavior.STATE_HIDDEN -> Toast.makeText(
                            applicationContext,
                            "STATE_HIDDEN",
                            Toast.LENGTH_SHORT
                        ).show()
                        else -> Toast.makeText(
                            applicationContext,
                            "OTHER_STATE",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            })
            collapse_playlist.setOnClickListener{
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            sheet_expand.setOnClickListener{
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                fab.hide()
                cameraFab.hide()
            }
        }

        // setup GOOGLE MAPS
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // setup GOOGLE SIGN IN and FIREBASE AUTH
        mAuth = FirebaseAuth.getInstance();
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        auth = Firebase.auth
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)

        // setup FIREBASE DATABASE

        //ask for permissions
        if (!hasPermissions(this, *PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
    }

    fun getPhotoFileUri(): Uri? {
        if (isExternalStorageAvailable()) {
            Log.d(APP_TAG, "external storage available")
            val mediaStorageDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), APP_TAG)
            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
                Log.d(APP_TAG, "failed to create directory")
            }
            // Return the file target for the photo based on filename
            val formatter = SimpleDateFormat("yyyyMMddHHmm", Locale.US)
            val now = Date()
            val file = File(mediaStorageDir.getPath() + File.separator.toString() + formatter.format(now) + ".jpg")
            Log.d(
                APP_TAG, FileProvider.getUriForFile(
                    applicationContext,
                    "edu.ucsb.cs.cs184.spencerzou.szougeopics.provider",
                    file
                ).toString()
            )
            // wrap File object into a content provider, required for API >= 24
            return FileProvider.getUriForFile(
                applicationContext,
                "edu.ucsb.cs.cs184.spencerzou.szougeopics.provider",
                file
            )
        } else {
            Log.d(APP_TAG, "no external storage")
        }
        return null
    }
    private fun isExternalStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        return state == Environment.MEDIA_MOUNTED
    }
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val database = Firebase.database
        val myRef = database.getReference().child("posts")
        myRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                Log.d("firebase contents", dataSnapshot.toString())
                for(i in dataSnapshot.children){
                    Log.d("child contents", i.toString())
                    var post: Post? = i.getValue(Post::class.java)
                    var latLng = LatLng(post?.latitude!!,post?.longitude!!)
                    var m: Marker = mMap.addMarker(MarkerOptions().position(latLng).title(post.name))
                    m.tag = i.key
                }
            }
            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w(ContentValues.TAG, "Failed to read value.", error.toException())
            }
        })
        mMap.setOnMarkerClickListener(object : GoogleMap.OnMarkerClickListener {
            override fun onMarkerClick(m: Marker?): Boolean {
                // get info from database based on tag key
                if(markerExists == 0) {
                    val myRef = database.getReference().child("posts").child(m?.tag.toString())
                    val likesRef = myRef.child("likes").child(auth.currentUser?.displayName!!)
                    likesRef.addListenerForSingleValueEvent(object :
                        ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                like_button.setImageResource(R.drawable.ic_favorite_24px)
                                like_button.imageTintList =
                                    ColorStateList.valueOf(resources.getColor(R.color.owl_pink_500))
                            } else {
                                like_button.setImageResource(R.drawable.ic_favorite_border_24px)
                                like_button.imageTintList =
                                    ColorStateList.valueOf(Color.GRAY)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Failed to read value
                            Log.w(
                                ContentValues.TAG,
                                "Failed to read value.",
                                error.toException()
                            )
                        }
                    })
                    var post: Post
                    myRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            // update marker bottom sheet with database values.
                            post = dataSnapshot.getValue(Post::class.java)!!
                            marker_title.text = post.name
                            marker_caption.text = post.caption
                            var likesLength = post.likesLength
                            like_count.text = likesLength.toString()
                            // handle like button
                            like_button.setOnClickListener {
                                likesRef.addListenerForSingleValueEvent(object :
                                    ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        if (snapshot.exists()) {
                                            Log.d("snapshot", snapshot.toString())
                                            likesRef.removeValue()
                                            myRef.child("likesLength").setValue(--likesLength)
                                            like_count.text = likesLength.toString()
                                            like_button.setImageResource(R.drawable.ic_favorite_border_24px)
                                            like_button.imageTintList =
                                                ColorStateList.valueOf(Color.GRAY)
                                        } else {
                                            likesRef.setValue(true)
                                            myRef.child("likesLength").setValue(++likesLength)
                                            like_count.text = likesLength.toString()
                                            like_button.setImageResource(R.drawable.ic_favorite_24px)
                                            like_button.imageTintList =
                                                ColorStateList.valueOf(resources.getColor(R.color.owl_pink_500))
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        // Failed to read value
                                        Log.w(
                                            ContentValues.TAG,
                                            "Failed to read value.",
                                            error.toException()
                                        )
                                    }
                                })
                            }
                            if (post.name == auth.currentUser?.displayName) {
                                var post_uri: Uri = Uri.parse(post.imagePath)
                                val parcelFileDescriptor: ParcelFileDescriptor? =
                                    contentResolver.openFileDescriptor(
                                        post_uri,
                                        "r"
                                    )
                                val fileDescriptor: FileDescriptor? =
                                    parcelFileDescriptor?.fileDescriptor
                                val exif = ExifInterface(fileDescriptor!!)
                                val orientation: Int = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL
                                )
                                var angle = 0
                                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                                    angle = 90
                                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                                    angle = 180
                                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                                    angle = 270
                                }
                                val mat = Matrix()
                                mat.postRotate(angle.toFloat())
                                var takenImage: Bitmap = getBitmapFromUri(post_uri);
// RESIZE BITMAP (if desired)
                                takenImage = Bitmap.createBitmap(
                                    takenImage, 0, 0, takenImage.getWidth(), takenImage.getHeight(),
                                    mat, true
                                );
// Load the taken image into a preview
                                markerImageView.setImageBitmap(takenImage)
                                markerBottomSheetBehavior.state =
                                    BottomSheetBehavior.STATE_COLLAPSED
                            } else {
                                Glide.with(applicationContext)
                                    .load("https://sites.cs.ucsb.edu/~holl/CS184/assignments/" + post.image.toString() + ".jpg")
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .into(markerImageView)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Failed to read value
                            Log.w(ContentValues.TAG, "Failed to read value.", error.toException())
                        }
                    })
                }
                return true
            }
        })
        // Add a marker in Sydney and move the camera
        val ucsb = LatLng(34.412936, -119.847863)
        mMap.addMarker(MarkerOptions().position(ucsb).title("Marker in UCSB"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(ucsb))
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15.0f))
        val fab: FloatingActionButton = findViewById(R.id.markerFab)
        fab.setOnClickListener { view ->
            if(onFlag == 0) {
                onFlag = 1
                fab.setImageResource(R.drawable.ic_close_24px)
                fab.setBackgroundTintList(ColorStateList.valueOf(resources.getColor(R.color.owl_pink_500)))
                cameraFab.hide()
                Snackbar.make(
                    view,
                    "Tap anywhere to add a marker, then press Post.",
                    Snackbar.LENGTH_LONG
                )
                    .setAction("Action", null)
                    .show()
                mMap.setOnMapClickListener(object : GoogleMap.OnMapClickListener {
                    override fun onMapClick(point: LatLng?) {
                        val viewWidth =
                            mMap.projection.toScreenLocation(mMap.projection.visibleRegion.farRight).x.toFloat()
                        val peek = bottomSheetBehavior.peekHeight + post_title.width
                        val animation: ObjectAnimator = ObjectAnimator.ofFloat(
                            lessons_sheet,
                            "translationX",
                            viewWidth,
                            viewWidth - peek
                        )
                        animation.duration = 200
                        if (point != null) {
                            if (markerExists == 1) {
                                marker.remove()
                            }
                            marker = mMap.addMarker(
                                MarkerOptions().position(point).title("New Marker")
                            )
                            markerExists = 1
                            markerBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                            animation.start()
                            Log.d(
                                "display width",
                                (mMap.projection.toScreenLocation(mMap.projection.visibleRegion.farRight)).toString()
                            )
                        }
                    }
                })
            }else{
                onFlag = 0
                if(markerExists == 1) {
                    marker.remove()
                    val viewWidth =
                        mMap.projection.toScreenLocation(mMap.projection.visibleRegion.farRight).x.toFloat()
                    val peek = bottomSheetBehavior.peekHeight + post_title.width
                    val animation: ObjectAnimator = ObjectAnimator.ofFloat(
                        lessons_sheet,
                        "translationX",
                        viewWidth - peek,
                        viewWidth
                    )
                    animation.duration = 200
                    animation.start()
                }
                markerExists = 0
                fab.setImageResource(R.drawable.ic_add_location_alt_24px)
                fab.setBackgroundTintList(ColorStateList.valueOf(resources.getColor(R.color.colorAccent)))
                cameraFab.show()
                mMap.setOnMapClickListener(null);
            }
        }
        // camera fab, take photo, place marker at current location, expand bottom sheet
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takenPhotoUri = getPhotoFileUri()!!
        intent.putExtra(MediaStore.EXTRA_OUTPUT, takenPhotoUri)
        cameraFab.setOnClickListener {
            if(!cameraOnFlag) {
                if (ContextCompat.checkSelfPermission(
                        this.applicationContext,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    fromCameraFab = true
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                    }
                } else {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                    )
                }
            } else if(cameraOnFlag){
                if(markerExists == 1) {
                    marker.remove()
                    val viewWidth =
                        mMap.projection.toScreenLocation(mMap.projection.visibleRegion.farRight).x.toFloat()
                    val peek = bottomSheetBehavior.peekHeight + post_title.width
                    val animation: ObjectAnimator = ObjectAnimator.ofFloat(
                        lessons_sheet,
                        "translationX",
                        viewWidth - peek,
                        viewWidth
                    )
                    animation.duration = 200
                    animation.start()
                }
                markerExists = 0
                cameraFab.setImageResource(R.drawable.ic_photo_camera_24px)
                cameraFab.setBackgroundTintList(ColorStateList.valueOf(resources.getColor(R.color.colorAccent)))
                markerFab.show()
                cameraOnFlag = false
            }
        }
        // setup post button onclick
        post_button.setOnClickListener {
            val database = Firebase.database
            val myRef = database.getReference().child("posts").push()
            if(markerExists == 1){
                val post = Post(auth.currentUser?.displayName, caption.text.toString(), marker.position.latitude, marker.position.longitude, takenPhotoUri.toString(), defaultImages[defaultImagesIndex])
                defaultImagesIndex++
                if(defaultImagesIndex == 10)
                    defaultImagesIndex = 0
                val defaultImagesIndexRef = database.getReference().child("defaultImagesIndex")
                myRef.setValue(post)
                defaultImagesIndexRef.setValue(defaultImagesIndex)
                marker.remove()
                markerExists = 0
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            handleSignInResult(result!!)
        }
//        if (requestCode == REQ_CODE_TAKE_PICTURE
//                && resultCode == RESULT_OK) {
//            var bmp: Bitmap = data?.extras?.get("data") as Bitmap
//            var img: ImageView = findViewById(R.id.imageScalingView);
//            img.setImageBitmap(bmp);
//        }
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
// by this point we have the camera photo on disk
                val parcelFileDescriptor: ParcelFileDescriptor? = contentResolver.openFileDescriptor(
                    takenPhotoUri!!,
                    "r"
                )
                val fileDescriptor: FileDescriptor? = parcelFileDescriptor?.fileDescriptor
                val exif = ExifInterface(fileDescriptor!!)
                val orientation: Int = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                var angle = 0
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    angle = 90
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    angle = 180
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    angle = 270
                }
                val mat = Matrix()
                mat.postRotate(angle.toFloat())
                var takenImage : Bitmap = getBitmapFromUri(takenPhotoUri);
// RESIZE BITMAP (if desired)
                takenImage = Bitmap.createBitmap(
                    takenImage, 0, 0, takenImage.getWidth(), takenImage.getHeight(),
                    mat, true
                );
// Load the taken image into a preview
                val ivPreview : ImageView = findViewById(R.id.imageScalingView);
                ivPreview.setImageBitmap(takenImage);
                if(fromCameraFab == true){
                    val locationManager =
                        this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val locationProvider = LocationManager.NETWORK_PROVIDER
                    @SuppressLint("MissingPermission")
                    val lastKnownLocation = locationManager.getLastKnownLocation(locationProvider)
                    val userLat = lastKnownLocation!!.latitude
                    val userLong = lastKnownLocation!!.longitude
                    val currentLocation = LatLng(userLat, userLong)
                    val viewWidth =
                        mMap.projection.toScreenLocation(mMap.projection.visibleRegion.farRight).x.toFloat()
                    val peek = bottomSheetBehavior.peekHeight + post_title.width
                    val animation: ObjectAnimator = ObjectAnimator.ofFloat(
                        lessons_sheet,
                        "translationX",
                        viewWidth,
                        viewWidth - peek
                    )
                    animation.duration = 200
                    if (currentLocation != null) {
                        if (markerExists == 1) {
                            marker.remove()
                        }
                        marker = mMap.addMarker(
                            MarkerOptions().position(currentLocation).title("current location")
                        )
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation))
                        markerExists = 1
                        markerBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        animation.start()
                        Log.d(
                            "display width",
                            (mMap.projection.toScreenLocation(mMap.projection.visibleRegion.farRight)).toString()
                        )
                    }
                    cameraFab.setImageResource(R.drawable.ic_close_24px)
                    cameraFab.setBackgroundTintList(ColorStateList.valueOf(resources.getColor(R.color.owl_pink_500)))
                    markerFab.hide()
                    cameraOnFlag = true
                }
            } else { // Result was a failure
                Toast.makeText(applicationContext, "Picture wasn't taken!", Toast.LENGTH_SHORT).show();
            }
            fromCameraFab = false
        }
    }
    //https://medium.com/@pednekarshashank33/android-10s-scoped-storage-image-picker-gallery-camera-d3dcca427bbf
    @Throws(IOException::class)
    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val parcelFileDescriptor: ParcelFileDescriptor? = contentResolver.openFileDescriptor(
            uri,
            "r"
        )
        val fileDescriptor: FileDescriptor? = parcelFileDescriptor?.fileDescriptor
        val image: Bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor?.close()
        return image
    }
    private fun handleSignInResult(result: GoogleSignInResult) {
        if (result.isSuccess) {
            Toast.makeText(
                applicationContext,
                "Welcome " + result.signInAccount?.displayName + "!",
                Toast.LENGTH_LONG
            ).show()
            try {
                firebaseAuthWithGoogle(result.signInAccount?.idToken!!)
            } catch (e: ApiException) {
                Log.w("firebase", "Google sign in failed", e)
            }
        } else {
            Toast.makeText(applicationContext, "Sign in cancel", Toast.LENGTH_LONG).show()
        }
    }
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("firebase", "signInWithCredential:success")
                    val user = auth.currentUser
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("firebase", "signInWithCredential:failure", task.exception)
                }
            }
    }
}