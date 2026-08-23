package org.aprsdroid.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView

class PrefSymbolAct : Activity(), View.OnClickListener, TextWatcher {

    companion object {
        const val OVERLAYABLE = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    var chosen_sym: String = "/$"

    val symbolview: SymbolView by lazy { findViewById(R.id.symbol) }
    val overlayedit: EditText by lazy { findViewById(R.id.overlay) }
    val okbutton: Button by lazy { findViewById(R.id.ok) }

    fun overlayAllowed(symbol: String): Boolean {
        return symbol.length > 1 && symbol[0] != '/' && OVERLAYABLE.contains(symbol[1])
    }

    fun setSymbol(symbol: String) {
        val ov_en = overlayAllowed(symbol)
        overlayedit.isEnabled = ov_en

        val ov = overlayedit.text.toString()
        chosen_sym = if (ov_en && ov.length == 1) {
            "" + ov[0] + symbol[1]
        } else {
            symbol
        }
        if (chosen_sym.length == 2) {
            symbolview.setSymbol(chosen_sym)
        } else {
            symbolview.setSymbol("/$")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.prefsymbol)
        val gv = findViewById<GridView>(R.id.gridview)
        gv.adapter = SymbolAdapter(this)
        gv.onItemClickListener = AdapterView.OnItemClickListener { _, v, _, _ ->
            val sym = (v as? SymbolView)?.symbol ?: "/$"
            Log.d("PrefSymbolAct", "tapped " + sym)
            setSymbol(sym)
        }
        okbutton.setOnClickListener(this)
        chosen_sym = prefs.getString("symbol", "/$")
        if (chosen_sym.length != 2) chosen_sym = "/$"
        val ov = chosen_sym[0]
        if (ov != '/' && ov != '\\') {
            overlayedit.setText(ov.toString())
        }
        overlayedit.addTextChangedListener(this)
        setSymbol(chosen_sym)
    }

    override fun onClick(view: View) {
        prefs.prefs.edit().putString("symbol", chosen_sym).apply()
        finish()
    }

    override fun afterTextChanged(s: Editable?) {
        if (chosen_sym.length > 1) {
            setSymbol("\\" + chosen_sym[1])
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    inner class SymbolAdapter(val context: Context) : BaseAdapter() {
        override fun getCount(): Int = 16 * 12 - 2

        override fun getItem(position: Int): Any {
            val primary = position / 95
            val secondary = position % 95
            val prefix = if (primary == 0) "/" else "\\"
            return prefix + ('!' + secondary).toChar()
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v: SymbolView = if (convertView == null) {
                val vt = SymbolView(context, null)
                val px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 48f,
                    resources.displayMetrics
                ).toInt()
                vt.layoutParams = AbsListView.LayoutParams(px, px)
                vt.scaleType = ImageView.ScaleType.CENTER_INSIDE
                vt
            } else {
                convertView as SymbolView
            }
            v.setSymbol(getItem(position) as String)
            return v
        }
    }
}
