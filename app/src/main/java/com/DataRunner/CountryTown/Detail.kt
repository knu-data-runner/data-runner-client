package com.DataRunner.CountryTown

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import com.DataRunner.CountryTown.WeatherRecieveData.Item
import com.DataRunner.CountryTown.WeatherRecieveData.Result
import com.DataRunner.CountryTown.WeatherRecieveData.WeatherAPI
import com.bumptech.glide.Glide
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.gson.GsonBuilder
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import kotlinx.android.synthetic.main.detail_layout.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Detail : AppCompatActivity(), OnMapReadyCallback {
    private var destinationTitle = ""
    private val utils = Utils()
    private val gpsUtils = GpsUtils()
    private var latlan: LatLng = LatLng(0.0, 0.0)
    var TO_GRID = 0
    var TO_GPS = 1

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.detail_layout)

        // Get variables
        val bundleData = intent.getBundleExtra("bundleData")
        val parceledData = bundleData.getParcelable<TownData>("parceledData")
        val sigungu = parceledData?.sigungu
        val town = parceledData?.title
        val type = parceledData?.programType
        val content = parceledData?.programContent
        val lat = parceledData!!.lat
        val lon = parceledData!!.lon
        val address = parceledData?.addr
        val number = parceledData?.number
        val link = parceledData?.link
        val townId = parceledData?.townId
        var detailContent = parceledData.detailContent

        // Set variables and processing
        title_sigungu.text = sigungu
        title_town.text = town
        if (detailContent.isNotEmpty()) {
            program_type.text = ""
            program_content.text = detailContent
        }
        else {
            program_type.text = type
            program_content.text = content
        }
        addr.text = address
        latlan = LatLng(lat, lon)
        setClickListener(number, link)
        loadImage(townId)
        destinationTitle = sigungu.toString() + " " + town.toString()

        // Map
        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map, it).commit()
            }
        mapFragment.getMapAsync(this)

        // Weather
        val grid = gpsUtils.convertGpsToGrid(TO_GRID, lat, lon)
        val gridX = grid.x.toInt()
        val gridY = grid.y.toInt()
        weather(gridX, gridY)

        // Path finding
        if (!gpsUtils.checkLocationServicesStatus(this)) {
            gpsUtils.showDialogForLocationServiceSetting(this)
        } else {
            gpsUtils.checkRunTimePermission(this)
        }
        trip_guide_button.setOnClickListener { findPath() }
    }

    private fun findPath() {
        val gpsTracker = GpsTracker(this)
        val startAddress = gpsUtils
            .getCurrentAddress(this, gpsTracker.getLat(), gpsTracker.getLon())
            ?.substring(9)
        val url =
            "nmap://route/car?slat=" + gpsTracker.getLat() +
                    "&slng=" + gpsTracker.getLon() +
                    "&sname=" + startAddress +
                    "&dlat=" + latlan.latitude +
                    "&dlng=" + latlan.longitude +
                    "&dname=" + destinationTitle +
                    "&appname=" + BuildConfig.APPLICATION_ID
        openUrl(url)

    }

    private fun openUrl(urlString: String) {

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
        intent.addCategory(Intent.CATEGORY_BROWSABLE)

        val resolveInfoList: List<ResolveInfo>? =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfoList == null || resolveInfoList.isEmpty()) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.nhn.android.nmap")
                )
            )
        } else {
            startActivity(intent)
        }
    }

    /*
     * ActivityCompat.requestPermissions를 사용한 퍼미션 요청의 결과를 리턴받는 메소드입니다.
     */
    override fun onRequestPermissionsResult(
        permsRequestCode: Int,
        permissions: Array<String?>,
        grandResults: IntArray
    ) {
        gpsUtils.onRequestPermissionsResult(
            this,
            permsRequestCode,
            permissions,
            grandResults
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        gpsUtils.onActivityResult(
            this,
            requestCode,
            resultCode,
            data
        )
    }

    @UiThread
    override fun onMapReady(naverMap: NaverMap) {
        naverMap.cameraPosition = CameraPosition(latlan, 8.0)

        val marker = Marker()
        marker.position = latlan
        marker.map = naverMap
    }

    private fun loadImage(townId: String) {
        // Set loading image
        Glide.with(this)
            .load(R.drawable.loading_spinningwheel)
            .into(detail_img)

        // Set image
        val storage = Firebase.storage
        val storageRef = storage.reference
        storageRef.child("img/town/" + townId + "_2.png").downloadUrl.addOnSuccessListener {
            // Got the download URL for 'users/me/profile.png'
            Glide.with(this)
                .load(it)
                .into(detail_img)
        }.addOnFailureListener {
            // Handle any errors
            storageRef.child("img/town/" + townId + "_2.PNG").downloadUrl.addOnSuccessListener {
                Glide.with(this)
                    .load(it)
                    .into(detail_img)
            }
        }
    }

    private fun setClickListener(number: String, link: String) {
        val call = fun() {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:" + number)
            startActivity(intent)
        }
        val share = fun() {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, "이 체험 마을 어때요?");
            intent.putExtra(Intent.EXTRA_TEXT, link);
            startActivity(Intent.createChooser(intent, "이 체험 마을 어때요?"))
        }
        val web = fun() {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(link)
            startActivity(intent)
        }
        call_layout.setOnClickListener { call() }
        share_layout.setOnClickListener { share() }
        web_layout.setOnClickListener { web() }
    }

    private fun setWeather(wt: WeathersTemperatures) {
        val weathers = wt.weathers // 없음(0), 비(1), 비/눈(2), 눈(3), 소나기(4)
        val temperatures = wt.temperatures
        var weatherDescription = "이 마을의 현재 날씨는\n"

        if (weathers == "0") {
            weatherDescription += "맑음, " + temperatures + "℃ 입니다."
            weather_img.setImageResource(R.drawable.ic_weather_sun)
        } else if (weathers == "1") {
            weatherDescription += "비, " + temperatures + "℃ 입니다."
            weather_img.setImageResource(R.drawable.ic_weather_rain)
        } else if (weathers == "2") {
            weatherDescription += "비와 눈, " + temperatures + "℃ 입니다."
            weather_img.setImageResource(R.drawable.ic_weather_snow_rain)
        } else if (weathers == "3") {
            weatherDescription += "눈, " + temperatures + "℃ 입니다."
            weather_img.setImageResource(R.drawable.ic_weather_snow)
        } else if (weathers == "4") {
            weatherDescription += "소나기, " + temperatures + "℃ 입니다."
            weather_img.setImageResource(R.drawable.ic_weather_sonagi)
        }
        weather_description.text = weatherDescription
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun weather(gridX: Int, gridY: Int) {
        fun createOkHttpClient(): OkHttpClient? {
            val builder = OkHttpClient.Builder()
            val interceptor = HttpLoggingInterceptor()
            interceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(interceptor)
            return builder.build()
        }

        // Set loading image
        Glide.with(this)
            .load(R.drawable.loading_spinningwheel)
            .into(weather_img)

        val current = LocalDateTime.now().minus(Duration.ofHours(1))
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val timeFormatter = DateTimeFormatter.ofPattern("HHMM")
        val dateFormatted = current.format(dateFormatter)
        val timeFormatted = current.format(timeFormatter)
        val gson = GsonBuilder()
            .setLenient()
            .create()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://apis.data.go.kr/")
            .client(createOkHttpClient()) // debug
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val weatherApi = retrofit.create(WeatherAPI::class.java)
        val callApi = weatherApi.getResponse(
            getSecret("weather", "KEY"),
            dateFormatted,
            timeFormatted,
            gridX.toString(),
            gridY.toString()
        )

        val wt = WeathersTemperatures()
        callApi.enqueue(object : Callback<Result> {
            override fun onResponse(call: Call<Result>, response: Response<Result>) {
                val itemList: List<Item> = response.body()!!.response!!.body!!.items!!.item!!
                for (item in itemList) {
                    val categorys = item.category
                    if (categorys.equals("PTY"))
                        wt.weathers = item.obsrValue.toString()
                    if (categorys.equals("T1H"))
                        wt.temperatures = item.obsrValue.toString()
                }
                setWeather(wt)
            }

            override fun onFailure(call: Call<Result>, t: Throwable) {
                Log.d("Call Failed", t.toString())
                setWeather(wt)
            }
        })
    }

    internal class WeathersTemperatures {
        var weathers = "0"
        var temperatures = "29"
    }

    private fun getSecret(provider: String, keyArg: String): String {
        val assetManager = resources.assets
        val inputStream = assetManager.open("secret.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(jsonString)
        val secret = obj.getJSONObject(provider)
        return secret.getString(keyArg)
    }
}