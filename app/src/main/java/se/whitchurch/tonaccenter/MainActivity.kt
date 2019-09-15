package se.whitchurch.tonaccenter

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.material.chip.Chip
import java.io.File
import java.lang.Float.max
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    lateinit var textQuestion: TextView
    lateinit var textTime: TextView
    lateinit var buttonOne: Button
    lateinit var buttonTwo: Button
    lateinit var correctButton: Button
    lateinit var wrongButton: Button
    lateinit var buttonNext: Button
    lateinit var buttonReplay: Button
    lateinit var sentence: Sentence
    lateinit var player: ExoPlayer
    lateinit var textTitle: TextView
    lateinit var chartCorrect: LineChart
    lateinit var dataSourceFactory: DefaultDataSourceFactory
    lateinit var db: SQLiteDatabase
    lateinit var prefs: SharedPreferences
    lateinit var editSearch: EditText
    val handler = Handler()
    var runnable: Runnable? = null
    val sentenceFilters: Map<Int, String> = mapOf(
        R.id.chipAccent1Focus to "accent1Focus",
        R.id.chipAccent1NonFocussed to "accent1NonFocussed",
        R.id.chipAccent2Focus to "accent2Focus",
        R.id.chipAccent2NonFocussed to "accent2NonFocussed",
        R.id.chipBerries to "berries",
        R.id.chipVerbs to "verbs",
        R.id.chipNouns to "nouns",
        R.id.chipCough to "cough",
        R.id.chipDays to "days"
    )
    var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        prefs = getPreferences(Context.MODE_PRIVATE)

        textQuestion = findViewById(R.id.textQuestion)
        textTime = findViewById(R.id.textTime)
        textTitle = findViewById(R.id.textTitle)
        editSearch = findViewById<EditText>(R.id.editSearch).apply {
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    delayedHandleNext()
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

            })
        }

        buttonOne = findViewById<Button>(R.id.buttonOne).apply {
            setOnClickListener { handleAnswer(it as Button) }
        }

        buttonTwo = findViewById<Button>(R.id.buttonTwo).apply {
            setOnClickListener { handleAnswer(it as Button) }
        }

        buttonNext = findViewById<Button>(R.id.buttonNext).apply {
            setOnClickListener { handleNext() }
        }

        buttonReplay = findViewById<Button>(R.id.buttonReplay).apply {
            setOnClickListener { handleReplay() }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    1
                )

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }

        for (id in sentenceFilters.keys) {
            findViewById<Chip>(id).apply {
                isChecked = prefs.getBoolean(sentenceFilters[id], true)

                setOnClickListener {
                    handleChipClick(it as Chip, id)
                }
            }
        }

        db = SQLiteDatabase.openDatabase(
            "/sdcard/Android/obb/se.whitchurch.tonaccenter/sentences.db",
            null, SQLiteDatabase.OPEN_READONLY
        )

        player = ExoPlayerFactory.newSimpleInstance(this)
        dataSourceFactory = DefaultDataSourceFactory(this, "hej")

        player.playWhenReady = true
        player.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !buttonNext.isEnabled) {
                    startTime = System.currentTimeMillis()
                    buttonOne.isEnabled = true
                    buttonTwo.isEnabled = true
                }
            }
        })

        chartCorrect = findViewById<LineChart>(R.id.chartCorrect)
        chartCorrect.visibility = View.GONE

//        chartWrong = findViewById<LineChart>(R.id.chartWrong)
//        chartWrong.visibility = View.INVISIBLE

        handleNext()
    }

    private fun delayedHandleNext() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = Runnable {
            handleNext()
        }
        handler.postDelayed(runnable, 1000)
    }

    private fun handleChipClick(chip: Chip, id: Int) {
        with(prefs.edit()) {
            putBoolean(sentenceFilters[id], chip.isChecked)
            commit()
        }

        delayedHandleNext()
    }

    private fun getMediaSource(fileName: String): MediaSource {
        val uri =
            Uri.fromFile(File("/sdcard/Android/obb/se.whitchurch.tonaccenter/${fileName}"))
        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
    }

    private fun getSentence(cursor: Cursor): Sentence {
        val rowid = cursor.getInt(0)
        val sent = Sentence(cursor)

        var cursor2 = db.rawQuery(
            "SELECT timestamp, frequency FROM pitch WHERE sentence = ? ORDER BY timestamp",
            arrayOf("$rowid")
        )

        var lines = arrayListOf<ArrayList<Entry>>()
        var points = arrayListOf<Entry>()

        while (cursor2.moveToNext()) {
            val frequency = cursor2.getFloat(1)

            if (frequency == 0f) {
                if (points.isNotEmpty()) {
                    lines.add(points)
                    points = arrayListOf()
                }

                continue
            }

            points.add(Entry(cursor2.getFloat(0), cursor2.getFloat(1)))
        }

        if (points.isNotEmpty()) lines.add(points)

        sent.pitch = lines

        cursor2 = db.rawQuery(
            "SELECT timestamp, amplitude FROM intensity WHERE sentence = ? ORDER BY timestamp",
            arrayOf("$rowid")
        )
        val entries = arrayListOf<Entry>()

        while (cursor2.moveToNext()) {
            var amplitude = cursor2.getFloat(1)

            if (amplitude < 50f) {
                amplitude = 50f
            }

            entries.add(Entry(cursor2.getFloat(0), amplitude))
        }

        sent.intensity = entries

        return sent
    }

    private fun getWrongSentence(sentence: Sentence): Sentence? {
        val correctName = sentence.questionFileName

        val wrongName = if (correctName.contains("_2_f.ogg")) {
            correctName.replace("_2_f.ogg", "_1_f.ogg")
        } else if (correctName.contains("_1_f.ogg")) {
            correctName.replace("_1_f.ogg", "_2_f.ogg")
        } else if (correctName.contains("_2_o.ogg")) {
            correctName.replace("_2_o.ogg", "_1_o.ogg")
        } else if (correctName.contains("_1_o.ogg")) {
            correctName.replace("_1_o.ogg", "_2_o.ogg")
        } else {
            return null
        }

        var cursor = db.rawQuery(
            "SELECT rowid, * from sentences WHERE questionFileName = ? LIMIT 1",
            arrayOf(wrongName)
        )
        if (!cursor.moveToNext()) {
            return null
        }

        return getSentence(cursor)
    }

    private fun getRandomSentence(): Sentence {
        val builder = StringBuilder("SELECT rowid, * from sentences WHERE 1=1")

        for (option in sentenceFilters.values) {
            val enabled = prefs.getBoolean(option, true)
            if (enabled) continue;

            when (option) {
                "accent1Focus" -> {
                    builder.append(" AND questionFileName NOT LIKE \"%_1_f%\"")
                    builder.append(" AND questionFileName NOT LIKE \"%_bs%\"")
                    builder.append(" AND questionFileName NOT LIKE \"%_ps%\"")
                }
                "accent1NonFocussed" -> builder.append(" AND questionFileName NOT LIKE \"%_1_o%\"")
                "accent2Focus" -> {
                    builder.append(" AND questionFileName NOT LIKE \"%_2_f%\"")
                    builder.append(" AND questionFileName NOT LIKE \"%_op%\"")
                    builder.append(" AND questionFileName NOT LIKE \"%_pt%\"")
                }
                "accent2NonFocussed" -> builder.append(" AND questionFileName NOT LIKE \"%_2_o%\"")
                "berries" -> builder.append(" AND questionFileName NOT LIKE \"b_%\"")
                "cough" -> builder.append(" AND questionFileName NOT LIKE \"%cough%\"")
                "days" -> builder.append(" AND questionFileName NOT LIKE \"d_%\"")
                "nouns" -> {
                    builder.append(" AND questionFileName NOT LIKE \"n_%\"")
                    builder.append(" AND questionFileName NOT LIKE \"sn_%\"")
                    builder.append(" AND questionFileName NOT LIKE \"a_s_%\"")
                    builder.append(" AND questionFileName NOT LIKE \"sms_%\"")
                }
                "verbs" -> {
                    builder.append(" AND questionFileName NOT LIKE \"v_%\"")
                    builder.append(" AND questionFileName NOT LIKE \"sv_%\"")
                    builder.append(" AND questionFileName NOT LIKE \"a_v_%\"")
                }

                else ->
                    Log.e("foof", "unhandled filter " + option)
            }

        }

        var args: Array<String>? = null
        val search = editSearch.text.toString()
        if (search.isNotEmpty()) {
            builder.append(" AND full LIKE ?")
            args = arrayOf("%$search%")
        }

        builder.append(" ORDER BY RANDOM() LIMIT 1")

        Log.i("foof", builder.toString())

        var cursor = db.rawQuery(builder.toString(), args)

        if (!cursor.moveToNext()) {
            return Sentence()
        }

        return getSentence(cursor)
    }

    private fun updateChart(chart: LineChart, sentence: Sentence) {
        val dataSets = arrayListOf<ILineDataSet>()

        dataSets.addAll(sentence.pitch.map {
            LineDataSet(it, "pitch").apply {
                color = Color.RED
                setDrawCircles(false)
                lineWidth = 2F
            }
        })

        dataSets.add(LineDataSet(sentence.intensity, "intensity").apply {
            color = Color.BLUE
            setDrawCircles(false)
            axisDependency = YAxis.AxisDependency.RIGHT
            lineWidth = 2F
        })

        chart.description.text = sentence.fullFileName

        chart.axisRight.axisMinimum = 50f
        chart.axisRight.axisMaximum = 100f

        chart.axisLeft.axisMinimum = 75.0f
        chart.axisLeft.axisMaximum = 500f

        val clip = sentence.clipPosition / 1000F

        val pre = if (sentence.questionFileName.contains("cough")) {
            1.0f
        } else {
            0.75f
        }

        val post = if (pre == 1.0f) {
            0.5f
        } else {
            0.75f
        }

        chart.xAxis.axisMinimum = max(clip - pre, 0f)
        chart.xAxis.axisMaximum = clip + post

        chart.data = LineData(dataSets)

        val limitLine = LimitLine(sentence.clipPosition / 1000F, "Clip")
        limitLine.enableDashedLine(10F, 5F, 0F)
        limitLine.lineWidth = 1F
        limitLine.lineColor = Color.BLACK
        chart.xAxis.removeAllLimitLines()
        chart.xAxis.addLimitLine(limitLine)

        chart.invalidate()
    }

    private fun handleNext() {
        textTime.text = ""
        buttonNext.isEnabled = false
        buttonReplay.isEnabled = false
        chartCorrect.visibility = View.GONE
        textTitle.visibility = View.GONE

        sentence = getRandomSentence()
        Log.i("foof", sentence.questionFileName)
        updateChart(chartCorrect, sentence)

//        getWrongSentence(sentence)?.let {
//            updateChart(chartWrong, it)
//        }

        if (Random.nextBoolean()) {
            correctButton = buttonOne
            wrongButton = buttonTwo
        } else {
            correctButton = buttonTwo
            wrongButton = buttonOne
        }

        buttonOne.isEnabled = false
        buttonTwo.isEnabled = false

        correctButton.background.clearColorFilter()
        wrongButton.background.clearColorFilter()

        textQuestion.text = "${sentence.question}..."
        correctButton.text = sentence.correct
        wrongButton.text = sentence.wrong

        val clippingSource = ClippingMediaSource(
            getMediaSource(sentence.questionFileName),
            0, sentence.clipPosition * 1000
        )
        player.prepare(clippingSource)
    }

    private fun handleReplay() {
        player.prepare(
            getMediaSource(
                if (sentence.fullFileName.isEmpty())
                    sentence.questionFileName else (sentence.fullFileName)
            )
        )
    }

    private fun handleAnswer(button: Button) {
        val stopTime = System.currentTimeMillis()
        val correct = button == correctButton

        buttonOne.isEnabled = false
        buttonTwo.isEnabled = false

        button.background.setColorFilter(
            if (correct) Color.GREEN else Color.RED,
            PorterDuff.Mode.MULTIPLY
        )

        val clippingSource = ClippingMediaSource(
            getMediaSource(sentence.questionFileName),
            sentence.clipPosition * 1000, C.TIME_END_OF_SOURCE
        )
        player.prepare(
            if (sentence.fullFileName.isEmpty()) clippingSource else getMediaSource(
                sentence.fullFileName
            )
        )

        if (correct) {
            textTime.text = (stopTime - startTime).toString() + " ms"
        }

        val fileName = if (sentence.fullFileName.isEmpty()) {
            sentence.questionFileName
        } else {
            sentence.fullFileName
        }
        textTitle.text = if (fileName.contains("_2_f.ogg") ||
            fileName.contains("_op.ogg") ||
            fileName.contains("_pt.ogg")
        ) {
            "Accent 2, focused ($fileName)"
        } else if (fileName.contains("_1_f.ogg") ||
            fileName.contains("_bs.ogg") ||
            fileName.contains("_ps.ogg")
        ) {
            "Accent 1, focused ($fileName)"
        } else if (fileName.contains("_2_o.ogg")) {
            "Accent 2, unfocused ($fileName)"
        } else if (fileName.contains("_1_o.ogg")) {
            "Accent 1, unfocused ($fileName)"
        } else {
            fileName
        }

        textQuestion.text = "${sentence.full}."
        buttonNext.isEnabled = true
        buttonReplay.isEnabled = true
        textTitle.visibility = View.VISIBLE
        chartCorrect.visibility = View.VISIBLE
    }
}
