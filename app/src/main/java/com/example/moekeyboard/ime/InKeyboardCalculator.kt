package com.example.moekeyboard.ime

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@Composable
fun InKeyboardCalculator(
    isDarkTheme: Boolean,
    height: Dp,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var calcMode by remember { mutableStateOf("calc") } // "calc" or "currency"
    var currencyVal by remember { mutableStateOf("1") }
    var selectedPair by remember { mutableStateOf("USD_BDT") } // USD_BDT, BDT_USD, EUR_BDT, SAR_BDT

    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var lastEvaluatedExpr by remember { mutableStateOf("") }

    val usdRate = 120.5
    val eurRate = 131.0
    val sarRate = 32.1

    fun getConvertedCurrency(): String {
        val amount = currencyVal.toDoubleOrNull() ?: 0.0
        return when (selectedPair) {
            "USD_BDT" -> "${DecimalFormat("#,##0.##").format(amount)} USD = ${DecimalFormat("#,##0.##").format(amount * usdRate)} BDT"
            "BDT_USD" -> "${DecimalFormat("#,##0.##").format(amount)} BDT = ${DecimalFormat("#,##0.##").format(amount / usdRate)} USD"
            "EUR_BDT" -> "${DecimalFormat("#,##0.##").format(amount)} EUR = ${DecimalFormat("#,##0.##").format(amount * eurRate)} BDT"
            "SAR_BDT" -> "${DecimalFormat("#,##0.##").format(amount)} SAR = ${DecimalFormat("#,##0.##").format(amount * sarRate)} BDT"
            else -> ""
        }
    }

    val bgColor = if (isDarkTheme) Color(0xFF0F1115) else Color(0xFFECEFF1)
    val cardBg = if (isDarkTheme) Color(0xFF1B1E24) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFF2F3F5) else Color(0xFF1F2937)
    val neonGreen = Color(0xFF00FF88)
    val operatorColor = if (isDarkTheme) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val specialBg = if (isDarkTheme) Color(0xFF282C34) else Color(0xFFD1D5DB)

    fun evaluateExpression(expr: String): String {
        if (expr.isBlank()) return "0"
        return try {
            val sanitized = expr
                .replace("×", "*")
                .replace("÷", "/")
                .replace("%", "/100")

            val eval = SimpleMathEvaluator.eval(sanitized)
            val formatter = DecimalFormat("#,##0.######")
            formatter.format(eval)
        } catch (_: Exception) {
            "..."
        }
    }

    fun onNumClick(char: String) {
        expression += char
        resultText = evaluateExpression(expression)
    }

    fun onOpClick(op: String) {
        if (expression.isNotEmpty()) {
            val lastChar = expression.last()
            if (lastChar in listOf('+', '-', '×', '÷', '%')) {
                expression = expression.dropLast(1) + op
            } else {
                expression += op
            }
        } else if (op == "-") {
            expression += "-"
        }
    }

    fun onBackspace() {
        if (expression.isNotEmpty()) {
            expression = expression.dropLast(1)
            resultText = evaluateExpression(expression)
        }
    }

    fun onClear() {
        expression = ""
        resultText = "0"
        lastEvaluatedExpr = ""
    }

    fun onEquals() {
        val eval = evaluateExpression(expression)
        if (eval != "...") {
            lastEvaluatedExpr = "$expression = $eval"
            resultText = eval
            expression = eval.replace(",", "")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = neonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "মিনি ক্যালকুলেটর",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = (calcMode == "calc"),
                    onClick = { calcMode = "calc" },
                    label = { Text("হিসাব", fontSize = 10.sp) },
                    modifier = Modifier.height(26.dp)
                )

                FilterChip(
                    selected = (calcMode == "currency"),
                    onClick = { calcMode = "currency" },
                    label = { Text("টাকা/ডলার 💱", fontSize = 10.sp) },
                    modifier = Modifier.height(26.dp)
                )

                // Paste Result
                Button(
                    onClick = {
                        val finalRes = if (calcMode == "calc") {
                            if (resultText != "..." && resultText.isNotBlank()) resultText else "0"
                        } else {
                            getConvertedCurrency()
                        }
                        onInsertText(finalRes)
                        Toast.makeText(context, "চ্যাটে পেস্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("পেস্ট", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (calcMode == "currency") {
            // Currency Converter UI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Pair selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = (selectedPair == "USD_BDT"),
                        onClick = { selectedPair = "USD_BDT" },
                        label = { Text("💵 USD → BDT", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = (selectedPair == "BDT_USD"),
                        onClick = { selectedPair = "BDT_USD" },
                        label = { Text("🇧🇩 BDT → USD", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = (selectedPair == "SAR_BDT"),
                        onClick = { selectedPair = "SAR_BDT" },
                        label = { Text("🇸🇦 SAR → BDT", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, neonGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "পরিমাণ লিখুন:",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        OutlinedTextField(
                            value = currencyVal,
                            onValueChange = { currencyVal = it.filter { c -> c.isDigit() || c == '.' } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💱 " + getConvertedCurrency(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = neonGreen,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Standard Calculator Screen / Display Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .border(1.dp, neonGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = expression.ifBlank { "0" },
                        fontSize = 14.sp,
                        color = textColor.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = resultText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = neonGreen,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Keypad Grid
            val btnHeight = 36.dp
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Row 1: C, (, ), ÷
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalcButton("C", specialBg, Color(0xFFFF5252), btnHeight, Modifier.weight(1f)) { onClear() }
                    CalcButton("(", specialBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("(") }
                    CalcButton(")", specialBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick(")") }
                    CalcButton("÷", operatorColor, Color.White, btnHeight, Modifier.weight(1f)) { onOpClick("÷") }
                }
                // Row 2: 7, 8, 9, ×
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalcButton("7", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("7") }
                    CalcButton("8", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("8") }
                    CalcButton("9", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("9") }
                    CalcButton("×", operatorColor, Color.White, btnHeight, Modifier.weight(1f)) { onOpClick("×") }
                }
                // Row 3: 4, 5, 6, -
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalcButton("4", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("4") }
                    CalcButton("5", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("5") }
                    CalcButton("6", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("6") }
                    CalcButton("-", operatorColor, Color.White, btnHeight, Modifier.weight(1f)) { onOpClick("-") }
                }
                // Row 4: 1, 2, 3, +
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalcButton("1", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("1") }
                    CalcButton("2", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("2") }
                    CalcButton("3", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("3") }
                    CalcButton("+", operatorColor, Color.White, btnHeight, Modifier.weight(1f)) { onOpClick("+") }
                }
                // Row 5: %, 0, ., =, ⌫
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalcButton("%", specialBg, textColor, btnHeight, Modifier.weight(1f)) { onOpClick("%") }
                    CalcButton("0", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick("0") }
                    CalcButton(".", cardBg, textColor, btnHeight, Modifier.weight(1f)) { onNumClick(".") }
                    CalcButton("⌫", specialBg, textColor, btnHeight, Modifier.weight(1f)) { onBackspace() }
                    CalcButton("=", neonGreen, Color.Black, btnHeight, Modifier.weight(1.2f)) { onEquals() }
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    bgColor: Color,
    textColor: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

object SimpleMathEvaluator {
    fun eval(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) x /= parseFactor()
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                return x
            }
        }.parse()
    }
}
