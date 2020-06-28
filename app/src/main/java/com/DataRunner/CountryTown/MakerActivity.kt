package com.DataRunner.CountryTown

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.maker_layout.*
import org.json.JSONArray


class MakerActivity : AppCompatActivity() {

    private var makerList = arrayListOf<MakerData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.maker_layout)

        parsing()
    }

    private fun parsing() {
        val madeByAdapter =  MakerAdapter(this, makerList) { maker ->
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // only email apps should handle this
                putExtra(Intent.EXTRA_EMAIL, maker.makerEmail)
                putExtra(Intent.EXTRA_SUBJECT, "문의드립니다.")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }
        madeBy.adapter = madeByAdapter

        // LayoutManager 설정. RecyclerView 에서는 필수
        val lm = LinearLayoutManager(this)
        madeBy.layoutManager = lm
        madeBy.setHasFixedSize(true)

        //start
        StrictMode.enableDefaults()
        try {
            val assetManager = resources.assets
            val inputStream = assetManager.open("made.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jArray = JSONArray(jsonString)

            // 모든 공지 noticeList 에 저장
            for (i in 0 until jArray.length()) {

                val obj = jArray.getJSONObject(i)

                val listLine = MakerData(
                    obj.getString("이름"),
                    obj.getString("이메일"),
                    obj.getString("깃주소"),
                    obj.getString("소속"),
                    obj.getString("사진")
                )
                makerList.add(listLine)
            }
        } catch (e: Exception) {
            val listLine = MakerData(e.toString(), "오류", "오류", "오류", "오류")
            makerList.add(listLine)
        }
    }
}
