package com.sesac.lbsmaphw

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.sesac.lbsmaphw.common.PermissionCheck
import com.sesac.lbsmaphw.common.YoonPreferenceManager
import com.sesac.lbsmaphw.databinding.ActivityMainBinding

const val LBS_CHECK_TAG = "LBS_CHECK_TAG"
const val LBS_CHECK_CODE = 100

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionCheck: PermissionCheck
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mCurrentLocation: Location
    private val itemList = ArrayList<String>()
    private val boardAdapter = CustomAdapter(itemList)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }
        if (!YoonPreferenceManager.getInstance().isPermission) {
            permissionCheck()
        }
        if (isNetworkAvailable()) {
            checkLocationCurrentDevice()
        } else {
            Toast.makeText(
                applicationContext,
                "네트웍이 연결되지 않아 종료합니다", Toast.LENGTH_SHORT
            ).show()
            finish()
        }
        val rvboard = findViewById<RecyclerView>(R.id.recycler_view)
        rvboard.adapter = boardAdapter
        rvboard.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val networkCapabilities = cm.getNetworkCapabilities(nw) ?: return false
            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            return cm.activeNetworkInfo?.isConnected ?: false
        }
    }

    private fun checkLocationCurrentDevice() {
        val locationIntervalTime = 5000L
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, locationIntervalTime)
                /**
                 * 정확한 위치를 기다림: true 일시 지하, 이동 중일 경우 늦어질 수 있음
                 */
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(locationIntervalTime)
                .setMaxUpdateDelayMillis(locationIntervalTime).build()


        val lbsSettingsRequest: LocationSettingsRequest =
            LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
        val settingClient: SettingsClient = LocationServices.getSettingsClient(this)
        val taskLBSSettingResponse: Task<LocationSettingsResponse> =
            settingClient.checkLocationSettings(lbsSettingsRequest)
        /**
         * 위치 정보설정이 On 일 경우
         */
        taskLBSSettingResponse.addOnSuccessListener {
            Toast.makeText(
                applicationContext,
                "위치정보 설정은  On 상태",
                Toast.LENGTH_SHORT
            ).show()
            /**
             *  addOnSuccessListener가 비동기식 처리라 처음 permissionRequest를 하고 resume보다 나중에 되면 값을 띄워주지 않음
             */
            if (PermissionCheck(applicationContext, this).currentAppCheckPermission()) getGPS()
        }
        /**
         * 위치 정보설정이 OFF 일 경우
         */
        taskLBSSettingResponse.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    Toast.makeText(
                        applicationContext,
                        "위치정보 설정은 반드시 On 상태여야 해요!",
                        Toast.LENGTH_SHORT
                    ).show()
                    /**
                     * 위치 설정이 되어있지 않을 시 대응방안을 정의
                     * 여기선 onActivityResult 를 이용해 대응한다
                     */
                    exception.startResolutionForResult(
                        this,
                        LBS_CHECK_CODE
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(LBS_CHECK_TAG, sendEx.message.toString())
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == LBS_CHECK_CODE) {
            checkLocationCurrentDevice()
        }
    }

    private fun permissionCheck() {
        permissionCheck = PermissionCheck(applicationContext, this)
        if (!permissionCheck.currentAppCheckPermission()) {
            permissionCheck.currentAppRequestPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!permissionCheck.currentAppPermissionResult(requestCode, grantResults)) {
            permissionCheck.currentAppRequestPermissions()
        } else {
            YoonPreferenceManager.getInstance().isPermission = true
        }
    }


    /**
     *  RecyclerView에 GPS값을 반영하는 LocationCallback
     */
    private val mLocationCallback: LocationCallback = object : LocationCallback() {
        @SuppressLint("NotifyDataSetChanged")
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            mCurrentLocation = locationResult.locations[0]
            itemList.add("${mCurrentLocation.latitude} ${mCurrentLocation.longitude}")
            boardAdapter.notifyDataSetChanged()
        }
    }


    /**
     * Get GPS
     *
     *  5초마다 gps를 얻는 함수
     */
    @SuppressLint("MissingPermission")
    private fun getGPS() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000L) //위치 획득 후 update 되는 최소 주기
            .setMaxUpdateDelayMillis(5000L).build()
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            locationRequest,
            mLocationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onResume() {
        super.onResume()
        if (PermissionCheck(applicationContext, this).currentAppCheckPermission()) getGPS()
    }
}