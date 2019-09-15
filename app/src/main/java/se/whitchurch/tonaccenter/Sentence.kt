package se.whitchurch.tonaccenter

import android.database.Cursor
import com.github.mikephil.charting.data.Entry

class Sentence(
    val questionFileName: String,
    val fullFileName: String,
    val correct: String,
    val wrong: String,
    val clipPosition: Long,
    val question: String,
    val full: String,
    val level: Int
) {
    var pitch = ArrayList<ArrayList<Entry>>()
    var intensity = ArrayList<Entry>()

    constructor() : this("Error", "Error", "Error", "Error", 0, "Error", "Error", 0)

    constructor(c: Cursor) : this(
        c.getString(1),
        c.getString(2),
        c.getString(3),
        c.getString(4),
        c.getLong(5),
        c.getString(6),
        c.getString(7),
        c.getInt(8)
    )
}
