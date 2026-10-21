package com.powermath

import com.powermath.editor.*
import com.powermath.graph.*
import com.powermath.math.*

/** Framework-free test suite. Run via `main` (plain kotlinc) or through the JUnit wrapper in Gradle. */
object CoreSuite {
    class Result(val name: String, val error: String?)

    private val results = ArrayList<Result>()
    private fun t(name: String, block: () -> Unit) {
        try { block(); results.add(Result(name, null)) }
        catch (e: Throwable) { results.add(Result(name, "${e.javaClass.simpleName}: ${e.message}")) }
    }

    private fun eq(expected: Any?, actual: Any?, ctx: String = "") {
        if (expected != actual) throw AssertionError("expected <$expected> but was <$actual> $ctx")
    }
    private fun yes(cond: Boolean, msg: String) { if (!cond) throw AssertionError(msg) }

    private fun fmt(mode: ResultMode = ResultMode.EXACT, dec: Int = 10, sig: Int = 10) = FormatSettings(mode, dec, sig)

    private fun calc(input: String, mode: ResultMode = ResultMode.EXACT, angle: AngleMode = AngleMode.DEG,
                     ws: Workspace = Workspace(), dec: Int = 10, sig: Int = 10): String {
        val f = fmt(mode, dec, sig)
        return Calculator.process(input, ws, CalcSettings(angle, f)).renderText(f)
    }

    private fun lines(input: String, ws: Workspace = Workspace(), angle: AngleMode = AngleMode.DEG): List<String> {
        val f = fmt()
        return Calculator.process(input, ws, CalcSettings(angle, f)).render(f)
    }

    private fun fails(kind: ErrorKind?, input: String, ws: Workspace = Workspace(), angle: AngleMode = AngleMode.DEG) {
        try {
            val r = Calculator.process(input, ws, CalcSettings(angle, fmt())).renderText(fmt())
            throw AssertionError("expected an error for '$input' but got '$r'")
        } catch (e: MathError) {
            if (kind != null) eq(kind, e.kind, "for '$input' (${e.message})")
        }
    }

    private fun defFails(block: () -> Unit, needle: String? = null) {
        try { block(); throw AssertionError("expected a definition error") }
        catch (e: MathError) { eq(ErrorKind.DEFINITION, e.kind, e.message ?: ""); if (needle != null) yes(e.message!!.contains(needle), e.message!!) }
    }

    fun run(): List<Result> {
        results.clear()
        basicArithmetic(); percentAndPrecedence(); fractionsAndFormats(); scientificFunctions(); errors()
        variablesAndFunctions(); equations(); inequalities(); systems(); algebraTools(); graphTests(); editorTests(); limits()
        return results.toList()
    }

    private fun basicArithmetic() {
        t("2 + 3 × 4 = 14") { eq("14", calc("2 + 3 × 4")) }
        t("(2 + 3) × 4 = 20") { eq("20", calc("(2 + 3) × 4")) }
        t("ascii operators and unicode minus/divide") { eq("4", calc("6÷3×2")); eq("3", calc("5−2")); eq("6", calc("2×3")) }
        t("unary minus and exponent precedence") {
            eq("-4", calc("-2^2")); eq("1/2", calc("2^-1")); eq("512", calc("2^3^2")); eq("3", calc("--3")); eq("-6", calc("2*-3"))
        }
        t("nested parentheses and auto-closing") { eq("21", calc("(1+2)(3+4)")); eq("5", calc("(2+3")); eq("14", calc("2(3+4)")) }
        t("decimal input is exact") { eq("3/10", calc("0.1+0.2")); eq("1/2", calc("0.5")); eq("3/4", calc("0.75")); eq("5/2", calc("10/4")) }
        t("1 / 2 exact vs decimal") { eq("1/2", calc("1 / 2")); eq("0.5", calc("1 / 2", ResultMode.DECIMAL)) }
        t("factorial") { eq("6", calc("3!")); eq("20", calc("5!/3!")); eq("1", calc("0!")) }
        t("sqrt(9)=3, 2^3=8") { eq("3", calc("sqrt(9)")); eq("8", calc("2^3")); eq("3", calc("√9")) }
        t("unicode constants and exponent literal") { eq("2π", calc("2π")); eq("1500", calc("1.5E3")); eq("1/50", calc("2E-2")) }
        t("exact roots") {
            eq("√2", calc("sqrt(2)")); eq("2√2", calc("sqrt(8)")); eq("2", calc("sqrt(2)*sqrt(2)")); eq("1/2", calc("sqrt(1/4)"))
            eq("2", calc("root(3,8)")); eq("-2", calc("root(3,-8)")); eq("2", calc("8^(1/3)")); eq("√2", calc("2^(1/2)"))
            eq("(1 + √5)/2", calc("(1+sqrt(5))/2")); eq("(1 + √5)/2", calc("phi")); eq("√2/2", calc("sqrt(2)/2"))
            eq("√6", calc("sqrt(2)*sqrt(3)")); eq("1 + √2", calc("1+sqrt(2)")); eq("1", calc("(sqrt(2)-1)*(sqrt(2)+1)"))
        }
        t("division of surds is rationalised") { eq("√2/2", calc("1/sqrt(2)")); eq("3 + 2√2", calc("(1+sqrt(2))^2")) }
    }

    private fun percentAndPrecedence() {
        t("percent semantics") {
            eq("7/100", calc("7%")); eq("220", calc("200+10%")); eq("180", calc("200-10%")); eq("100", calc("50%*200")); eq("1", calc("100%"))
        }
        t("implicit multiplication binds like '*'") { eq("1/6", calc("1/2*1/3")); eq("14", calc("2 (3 + 4)")) }
    }

    private fun fractionsAndFormats() {
        t("scientific notation 1230000") { eq("1.23 × 10^6", calc("1230000", ResultMode.SCIENTIFIC)) }
        t("scientific notation 0.0000045") { eq("4.5 × 10^-6", calc("0.0000045", ResultMode.SCIENTIFIC)) }
        t("scientific huge/small without crashing") {
            eq("1 × 10^100", calc("10^100", ResultMode.SCIENTIFIC)); eq("1 × 10^-50", calc("1/10^50", ResultMode.SCIENTIFIC))
            eq("≈ 9.332621544 × 10^157", calc("100!", ResultMode.SCIENTIFIC)); eq("≈ 1.071508607 × 10^301", calc("2^1000", ResultMode.SCIENTIFIC))
        }
        t("decimal mode marks approximations") {
            eq("≈ 0.3333333333", calc("1/3", ResultMode.DECIMAL)); eq("≈ 1.4142135624", calc("sqrt(2)", ResultMode.DECIMAL))
            eq("0.75", calc("3/4", ResultMode.DECIMAL)); eq("≈ 0.333", calc("1/3", ResultMode.DECIMAL, dec = 3))
            eq("≈ 3.1415926536", calc("pi", ResultMode.DECIMAL)); eq("≈ 2.7182818285", calc("e", ResultMode.DECIMAL))
        }
        t("decimal never rounds a nonzero value to zero") { eq("1 × 10^-50", calc("1/10^50", ResultMode.DECIMAL)) }
        t("exact mode keeps symbols") { eq("e", calc("e")); eq("π", calc("pi")); eq("π/2", calc("pi/2")); eq("3π/4", calc("3pi/4")) }
        t("exact mode of an irrational transcendental shows approximation") { yes(calc("sin(1)", ResultMode.EXACT, AngleMode.RAD).startsWith("≈ 0.8414709848"), "sin(1) rad") }
        t("big integers stay exact") { eq("2432902008176640000", calc("20!")); eq("1267650600228229401496703205376", calc("2^100")) }
        t("scientific of exact rational shows approx flag only when rounded") {
            eq("≈ 3.333333333 × 10^-1", calc("1/3", ResultMode.SCIENTIFIC)); eq("5 × 10^-1", calc("1/2", ResultMode.SCIENTIFIC))
        }
    }

    private fun scientificFunctions() {
        t("degree trig exact special angles") {
            eq("1/2", calc("sin(30)")); eq("√2/2", calc("sin(45)")); eq("1/2", calc("cos(60)")); eq("1", calc("tan(45)"))
            eq("0", calc("sin(180)")); eq("-1", calc("cos(180)")); eq("√3", calc("tan(60)")); eq("√3/3", calc("tan(30)")); eq("-1/2", calc("sin(210)"))
        }
        t("radian trig respects angle mode") {
            eq("1/2", calc("sin(pi/6)", angle = AngleMode.RAD)); eq("-1", calc("cos(pi)", angle = AngleMode.RAD))
            yes(calc("sin(1)", ResultMode.DECIMAL, AngleMode.RAD) == "≈ 0.8414709848", "sin(1) rad")
            yes(calc("sin(1)", ResultMode.DECIMAL, AngleMode.DEG) == "≈ 0.0174524064", "sin(1) deg")
            eq("0", calc("sin(0)", angle = AngleMode.RAD))
        }
        t("inverse trig exact and mode-dependent") {
            eq("30", calc("asin(1/2)")); eq("45", calc("atan(1)")); eq("90", calc("acos(0)"))
            eq("π/6", calc("asin(1/2)", angle = AngleMode.RAD)); eq("π/4", calc("atan(1)", angle = AngleMode.RAD))
            yes(calc("asin(0.3)", ResultMode.DECIMAL).startsWith("≈ 17.4576"), "asin(0.3)")
        }
        t("logarithms") {
            eq("3", calc("log(1000)")); eq("0", calc("ln(1)")); eq("3", calc("log(2, 8)")); eq("-2", calc("log(1/100)")); eq("1", calc("ln(e)"))
            eq("1", calc("exp(0)")); yes(calc("ln(2)", ResultMode.DECIMAL).startsWith("≈ 0.6931471806"), "ln 2")
        }
        t("statistics and number theory") {
            eq("5/2", calc("mean(1,2,3,4)")); eq("5", calc("abs(-5)")); eq("6", calc("gcd(12,18)")); eq("12", calc("lcm(4,6)"))
            eq("2", calc("mod(-7,3)")); eq("10", calc("ncr(5,2)")); eq("20", calc("npr(5,2)")); eq("7", calc("max(3,7,5)")); eq("3", calc("min(3,7,5)"))
            eq("5", calc("floor(11/2)")); eq("6", calc("ceil(11/2)")); eq("-1", calc("sgn(-9)"))
        }
        t("delta function") { eq("1", calc("delta(1,-5,6)")); eq("-4", calc("delta(1,0,1)")) }
    }

    private fun errors() {
        t("syntax errors are reported, not thrown as crashes") {
            fails(ErrorKind.SYNTAX, "2+"); fails(ErrorKind.SYNTAX, "2+3)"); fails(ErrorKind.SYNTAX, "*3"); fails(ErrorKind.SYNTAX, "1..2")
            fails(ErrorKind.SYNTAX, "sin"); fails(ErrorKind.SYNTAX, "sqrt(1,2)"); fails(ErrorKind.SYNTAX, "2 3"); fails(ErrorKind.SYNTAX, "")
        }
        t("domain errors") {
            fails(ErrorKind.DIV_ZERO, "1/0"); fails(ErrorKind.NON_REAL, "sqrt(-1)"); fails(ErrorKind.DOMAIN, "ln(0)"); fails(ErrorKind.DOMAIN, "log(-1)")
            fails(ErrorKind.DOMAIN, "(-1)!"); fails(ErrorKind.DOMAIN, "2.5!"); fails(ErrorKind.DOMAIN, "asin(2)"); fails(ErrorKind.DOMAIN, "tan(90)")
            fails(ErrorKind.NON_REAL, "(-8)^(1/2)"); fails(ErrorKind.DOMAIN, "0^0"); fails(ErrorKind.DIV_ZERO, "0^-1")
        }
        t("undefined names") { fails(ErrorKind.UNDEFINED, "foo(2)"); fails(ErrorKind.UNDEFINED, "zz(1)") }
        t("free variables in numeric context are kept symbolic") { eq("x + 1", calc("x+1")) }
    }

    private fun variablesAndFunctions() {
        t("variables: a = 5 then a + 2 = 7") {
            val ws = Workspace()
            val out = Calculator.process("a := 5", ws, CalcSettings())
            eq(1, out.changes.size)
            eq("7", calc("a + 2", ws = ws)); eq("10", calc("2a", ws = ws)); eq("25", calc("a^2", ws = ws))
        }
        t("functions: f(x) = x^2 + 1, f(3) = 10") {
            val ws = Workspace()
            calc("f(x) = x^2 + 1", ws = ws)
            eq("10", calc("f(3)", ws = ws)); eq("2", calc("f(1)", ws = ws)); eq("26", calc("f(f(2))", ws = ws))
            eq("26", calc("f(5)", ws = ws)); eq("5", calc("f(2)", ws = ws))
        }
        t("multi-argument and composed functions") {
            val ws = Workspace()
            calc("g(x) = 2x + 3", ws = ws); calc("h(x, y) = x^2 + y^2", ws = ws); calc("ff(x) = x + 1", ws = ws); calc("k(x) = ff(x)", ws = ws)
            eq("25", calc("h(3,4)", ws = ws)); eq("7", calc("g(2)", ws = ws)); eq("4", calc("k(3)", ws = ws)); eq("1", calc("g(-1)", ws = ws))
        }
        t("functions use workspace variables at call time") {
            val ws = Workspace(); calc("a := 3", ws = ws); calc("p(x) = a*x", ws = ws)
            eq("12", calc("p(4)", ws = ws)); calc("a := 10", ws = ws); eq("40", calc("p(4)", ws = ws))
        }
        t("name collisions and overwrite policy") {
            val ws = Workspace(); calc("a := 1", ws = ws); calc("f(x) = x", ws = ws)
            defFails({ ws.defineVariable("a", "2") }); defFails({ ws.defineFunction("f", listOf("x"), "x+1") })
            defFails({ ws.defineVariable("f", "2", overwrite = true) }); defFails({ ws.defineFunction("a", listOf("x"), "x", overwrite = true) })
            defFails({ ws.defineVariable("sin", "1") }); defFails({ ws.defineVariable("pi", "1") }); defFails({ ws.defineVariable("1a", "1") })
            calc("f(x) := x + 100", ws = ws); eq("103", calc("f(3)", ws = ws))
            calc("a := 9", ws = ws); eq("9", calc("a", ws = ws))
        }
        t("definition validation: undefined variables, unknown functions, circularity") {
            val ws = Workspace()
            defFails({ ws.defineFunction("m", listOf("x"), "x + zz") })
            defFails({ ws.defineFunction("m", listOf("x"), "nofn(x)") })
            defFails({ ws.defineFunction("m", listOf("x"), "m(x) + 1") }, "Circular")
            ws.defineFunction("p", listOf("x"), "x + 1"); ws.defineFunction("q", listOf("x"), "p(x) * 2")
            defFails({ ws.defineFunction("p", listOf("x"), "q(x)", overwrite = true) }, "Circular")
            eq("8", calc("q(3)", ws = ws))
            defFails({ ws.defineFunction("w", listOf("x", "x"), "x") }); defFails({ ws.defineFunction("w", listOf("x"), "x = 1") })
            defFails({ ws.defineFunction("w", listOf("x"), "ans + x") })
        }
        t("deleting something in use is blocked; deleting unused works") {
            val ws = Workspace(); calc("a := 2", ws = ws); calc("u(x) = a*x", ws = ws)
            defFails({ ws.deleteVariable("a") })
            ws.deleteFunction("u"); ws.deleteVariable("a")
            yes(ws.variables.isEmpty() && ws.functions.isEmpty(), "workspace should be empty")
            fails(ErrorKind.SYNTAX, "a + ", ws)
        }
        t("persistence round trip (restore from stored text)") {
            val ws = Workspace(); calc("r := sqrt(2)", ws = ws); calc("t := 1/3", ws = ws); calc("w := pi/2", ws = ws); calc("half := 0.5", ws = ws)
            calc("f(x) = x^2 + t", ws = ws); calc("g(x, y) = f(x) + y", ws = ws)
            val ws2 = Workspace()
            ws2.restore(ws.variables.map { Triple(it.name, it.source, Formatter.linear(it.value)) }, ws.functions.map { Triple(it.name, it.params, it.source) })
            eq("2", calc("r*r", ws = ws2)); eq("1/3", calc("t", ws = ws2)); eq("π/2", calc("w", ws = ws2)); eq("28/3", calc("f(3)", ws = ws2)); eq("31/3", calc("g(3, 1)", ws = ws2))
        }
        t("ans holds the previous numeric result") { val ws = Workspace(); calc("2+3", ws = ws); eq("10", calc("ans*2", ws = ws)) }
        t("multi-letter names and implicit products") {
            val ws = Workspace(); calc("rate := 4", ws = ws); calc("x := 2", ws = ws)
            eq("8", calc("rate*x", ws = ws)); eq("8", calc("ratex", ws = ws)); eq("xy", calc("xy"))
        }
    }

    private fun equations() {
        t("x + 3 = 5 -> x = 2") { eq(listOf("x = 2"), lines("x + 3 = 5")) }
        t("x^2 - 5x + 6 = 0 -> x = 2, x = 3 (with delta)") { eq(listOf("x = 2", "x = 3", "Δ = 1"), lines("x^2 - 5x + 6 = 0")) }
        t("quadratic with no real roots reports it") {
            val l = lines("x^2 + 1 = 0"); yes(l[0].startsWith("No real solutions (Δ = -4 < 0)"), l.toString()); yes(l[1] == "Complex roots: x = ±i", l.toString())
        }
        t("quadratic repeated root") { eq(listOf("x = 1", "Δ = 0 (repeated root)"), lines("x^2 - 2x + 1 = 0")); eq(listOf("x = 1", "Δ = 0 (repeated root)"), lines("(x-1)^2 = 0")) }
        t("quadratic irrational roots stay exact") {
            eq(listOf("x = -√2", "x = √2", "Δ = 8"), lines("x^2 - 2 = 0"))
            eq(listOf("x = (-1 - √5)/2", "x = (-1 + √5)/2", "Δ = 5"), lines("x^2 + x - 1 = 0"))
            eq(listOf("x = 1 - √2", "x = 1 + √2", "Δ = 8"), lines("x^2 - 2x - 1 = 0"))
        }
        t("quadratic with rational (fractional) roots") { eq(listOf("x = -1", "x = 1/2", "Δ = 9"), lines("2x^2 + x - 1 = 0")) }
        t("decimal mode formats roots") {
            val f = fmt(ResultMode.DECIMAL)
            eq(listOf("x ≈ -1.4142135624", "x ≈ 1.4142135624", "Δ = 8"), Calculator.process("x^2 - 2 = 0", Workspace(), CalcSettings(format = f)).render(f))
        }
        t("linear equations") {
            eq(listOf("x = -7"), lines("2x + 3 = x - 4")); eq(listOf("x = 2"), lines("3x = 6")); eq(listOf("x = 6"), lines("0.5x = 3")); eq(listOf("x = 2/3"), lines("3x - 2 = 0"))
        }
        t("degenerate and special equations") {
            eq(listOf("No solution: the equation is false"), lines("x = x + 1"))
            eq("Identity: true for every real value", lines("x + 1 = x + 1")[0])
            eq("x = 2", lines("0x^2 + x - 2 = 0")[0])
        }
        t("quad() tool: delta, roots, vertex, degenerate a = 0") {
            eq(listOf("x = 2", "x = 3", "Δ = 1", "Vertex: (5/2, -1/4)", "Axis of symmetry: x = 5/2"), lines("quad(1, -5, 6)"))
            eq("x = 2", lines("quad(0, 2, -4)")[0])
        }
        t("cubic and quartic equations") {
            eq(listOf("x = 1", "x = 2", "x = 3"), lines("x^3 - 6x^2 + 11x - 6 = 0"))
            eq(listOf("x = -2", "x = -1", "x = 1", "x = 2"), lines("x^4 - 5x^2 + 4 = 0"))
            eq(listOf("x = 0", "x = 1"), lines("x^3 - x^2 = 0"))
            val l = lines("x^3 - 2 = 0"); eq("x ≈ 1.2599210499", l[0]); yes(l[1].contains("approximations"), l.toString())
            eq(listOf("x = -1"), lines("x^3 + 1 = 0"))
            val q = lines("x^4 - 2 = 0"); eq(2, q.count { it.startsWith("x ") }); yes(q[0].startsWith("x ≈ -1.189207"), q.toString())
        }
        t("equations use saved variables and functions") {
            val ws = Workspace(); calc("a := 5", ws = ws); calc("f(x) = x^2 + 1", ws = ws)
            eq(listOf("x = 2"), lines("a*x = 10", ws)); eq(listOf("x = -2", "x = 2", "Δ = 16"), lines("f(x) = 5", ws))
        }
        t("equations: solve(eq, var) and solve(expr)") {
            eq(listOf("x = 2"), lines("solve(x + 3 = 5)")); eq(listOf("x = 2"), lines("solve(x + 3 = 5, x)")); eq(listOf("x = 2", "x = 3", "Δ = 1"), lines("solve(x^2 - 5x + 6)"))
        }
        t("unsupported equations are rejected, not fabricated") {
            fails(ErrorKind.UNSUPPORTED, "sin(x) = 0"); fails(ErrorKind.UNSUPPORTED, "1/x = 2"); fails(ErrorKind.UNSUPPORTED, "2^x = 8")
            fails(ErrorKind.UNSUPPORTED, "x + y = 3"); fails(ErrorKind.UNSUPPORTED, "x^30 = 1"); fails(ErrorKind.UNSUPPORTED, "sqrt(x) = 3")
        }
        t("numeric truth values") { eq("True", calc("2+2=4")); eq("False", calc("3<2")); eq("True", calc("sqrt(2)*sqrt(2)=2")); eq("True", calc("1/3 != 1/2")) }
    }

    private fun inequalities() {
        t("x^2 - 4 < 0 -> -2 < x < 2") { eq(listOf("-2 < x < 2", "(-2, 2)"), lines("x^2 - 4 < 0")) }
        t("quadratic inequality variants") {
            eq(listOf("x < -2 or x > 2", "(-∞, -2) ∪ (2, ∞)"), lines("x^2 - 4 > 0"))
            eq(listOf("-2 ≤ x ≤ 2", "[-2, 2]"), lines("x^2 - 4 <= 0"))
            eq(listOf("x ≤ -2 or x ≥ 2", "(-∞, -2] ∪ [2, ∞)"), lines("x^2 - 4 >= 0"))
        }
        t("linear inequality and sign flip") {
            eq(listOf("x < 2", "(-∞, 2)"), lines("2x + 1 < 5")); eq(listOf("x > -2", "(-2, ∞)"), lines("-2x < 4")); eq(listOf("x ≥ 3", "[3, ∞)"), lines("-x <= -3"))
        }
        t("all / none / single point / excluded point") {
            eq(listOf("All real numbers"), lines("x^2 + 1 > 0")); eq(listOf("No real solutions"), lines("x^2 < 0"))
            eq("x = 0", lines("x^2 <= 0")[0]); eq("x < 1 or x > 1", lines("(x-1)^2 > 0")[0]); eq("x < 2 or x > 2", lines("x != 2")[0])
        }
        t("cubic inequality with several intervals") {
            eq("-1 ≤ x ≤ 0 or x ≥ 1", lines("x^3 - x >= 0")[0]); eq("x < -1 or 0 < x < 1", lines("x^3 - x < 0")[0])
        }
        t("irrational boundaries stay exact") { eq("-√2 < x < √2", lines("x^2 < 2")[0]) }
        t("inequality via solve()") { eq("-2 < x < 2", lines("solve(x^2 < 4)")[0]) }
        t("inequality with both sides polynomial") { eq("x < 1 or x > 2", lines("x^2 + 2 > 3x")[0]) }
    }

    private fun systems() {
        t("2x2 unique solution") { eq(listOf("x = 2", "y = 1"), lines("x + y = 3; x - y = 1")); eq(listOf("x = 2", "y = 1"), lines("solve(x + y = 3, x - y = 1)")) }
        t("3x3 unique solution") { eq(listOf("x = 1", "y = 2", "z = 3"), lines("x + y + z = 6; x - y = -1; z = 3")) }
        t("fractional solution") { eq(listOf("x = 3/2", "y = 3/2"), lines("x + y = 3; x - y = 0")) }
        t("inconsistent system") { eq("No solution: the system is inconsistent", lines("x + y = 1; x + y = 2")[0]) }
        t("underdetermined system") { val l = lines("x + y = 2; 2x + 2y = 4"); eq("Infinitely many solutions (free: y)", l[0]); eq("x = -y + 2", l[1]) }
        t("systems reject non-linear input") { fails(ErrorKind.UNSUPPORTED, "x^2 + y = 3; x - y = 1"); fails(ErrorKind.UNSUPPORTED, "x*y = 3; x - y = 1") }
        t("saved variables are substituted in systems") { val ws = Workspace(); calc("k := 4", ws = ws); eq(listOf("x = 2", "y = 1"), lines("x + y = 3; k*x - y = 7", ws)) }
    }

    private fun algebraTools() {
        t("free-variable expressions are expanded") {
            eq("2x + 6", calc("2(x+3)")); eq("3x + 3", calc("3(x+1)")); eq("x^2 - 1", calc("(x+1)(x-1)")); eq("x^2 + 2x + 1", calc("x^2 + 2x + 1")); eq("x + 3", calc("x + 3"))
        }
        t("expand / simplify") {
            eq("x^2 + 2x + 1", calc("expand((x+1)^2)")); eq("x^3 + 3x^2 + 3x + 1", calc("expand((x+1)^3)")); eq("2x + 6", calc("simplify(2(x+3))"))
            eq("0", calc("simplify((x+1)^2 - x^2 - 2x - 1)")); eq("x^2 + 2xy + y^2", calc("expand((x+y)^2)")); eq("2", calc("simplify(sqrt(2)*sqrt(2))")); eq("(1/2)x + 1/3", calc("simplify(x/2 + 1/3)"))
        }
        t("factor") {
            eq("(x - 2)(x - 3)", calc("factor(x^2 - 5x + 6)")); eq("2(x + 1)(x - 1)", calc("factor(2x^2 - 2)")); eq("(x - 1)^2", calc("factor(x^2 - 2x + 1)"))
            eq("(x + 1)(x - 1)(x - 2)", calc("factor(x^3 - 2x^2 - x + 2)")); eq("(x + 1)(2x - 1)", calc("factor(2x^2 + x - 1)")); eq("x(x + 1)(x - 1)", calc("factor(x^3 - x)").replace("(x + 1)x(x - 1)", "x(x + 1)(x - 1)"))
            eq("x^2 + 1", lines("factor(x^2 + 1)")[0]); yes(lines("factor(x^2 + 1)")[1].contains("irreducible"), "irreducible note")
            fails(ErrorKind.UNSUPPORTED, "factor(x^2 + y^2)")
        }
        t("eval under conditions") {
            eq("10", calc("eval(x^2 + 1; x = 3)")); eq("13", calc("eval(x^2 + y^2, x = 2, y = 3)")); eq("y + 5", calc("eval(x + y; x = 5)"))
        }
        t("commands cannot hide inside expressions") { fails(ErrorKind.UNSUPPORTED, "1 + solve(x = 2)") }
    }

    private fun graphTests() {
        val ws = Workspace()
        t("compile f(x) = x^2 and sample a single parabola") {
            val g = GraphFunction.compile("f(x) = x^2", ws, AngleMode.DEG)
            eq("f(x) = x^2", g.label); eq(9.0, g.eval(3.0))
            val segs = GraphEngine.sample(g::eval, Viewport.DEFAULT)
            eq(1, segs.size); yes(segs[0].all { Math.abs(it.y - it.x * it.x) < 1e-9 }, "points must lie on y = x^2"); yes(segs[0].size > 700, "dense sampling")
        }
        t("y = ..., bare expressions and saved functions") {
            calc("f(x) = x^2 + 1", ws = ws)
            eq(5.0, GraphFunction.compile("f(x)", ws, AngleMode.DEG).eval(2.0)); eq(7.0, GraphFunction.compile("y = 2x + 1", ws, AngleMode.DEG).eval(3.0))
            eq(1.0, GraphFunction.compile("cos(0)", ws, AngleMode.DEG).eval(5.0)); eq(10.0, GraphFunction.compile("t^2 + 1", ws, AngleMode.DEG).also { eq("t", it.varName) }.eval(3.0))
        }
        t("trig graph respects angle mode") {
            yes(Math.abs(GraphFunction.compile("sin(x)", ws, AngleMode.RAD).eval(Math.PI / 2) - 1.0) < 1e-12, "rad")
            yes(Math.abs(GraphFunction.compile("sin(x)", ws, AngleMode.DEG).eval(90.0) - 1.0) < 1e-12, "deg")
        }
        t("asymptotes and invalid regions produce gaps, not misleading lines") {
            val tan = GraphFunction.compile("tan(x)", ws, AngleMode.RAD)
            val s1 = GraphEngine.sample(tan::eval, Viewport(-10.0, 10.0, -10.0, 10.0)); yes(s1.size >= 6, "tan should have several branches, got ${s1.size}")
            val inv = GraphFunction.compile("1/x", ws, AngleMode.DEG)
            val s2 = GraphEngine.sample(inv::eval, Viewport.DEFAULT); eq(2, s2.size); yes(s2.none { seg -> seg.any { it.x < 0 } && seg.any { it.x > 0 } }, "no segment may cross x = 0")
            val sq = GraphFunction.compile("sqrt(x)", ws, AngleMode.DEG)
            val s3 = GraphEngine.sample(sq::eval, Viewport.DEFAULT); eq(1, s3.size); yes(s3[0].first().x >= 0.0, "no points left of the domain")
            val ln = GraphFunction.compile("ln(x)", ws, AngleMode.DEG); yes(GraphEngine.sample(ln::eval, Viewport.DEFAULT).all { s -> s.all { it.x > 0 } }, "ln domain")
            val partial = GraphFunction.compile("sqrt(x)/(x-2)", ws, AngleMode.DEG); GraphEngine.sample(partial::eval, Viewport.DEFAULT)
        }
        t("invalid graph input is rejected with errors, never crashes") {
            for (bad in listOf("x + y", "x = 3", "", "solve(x=1)", "f(x, y) = x + y")) {
                try { GraphFunction.compile(bad, ws, AngleMode.DEG); throw AssertionError("expected rejection of '$bad'") } catch (e: MathError) { }
            }
            val g = GraphFunction.compile("x/0", ws, AngleMode.DEG); eq(0, GraphEngine.sample(g::eval, Viewport.DEFAULT).size)
        }
        t("annotations: f(x) = x^2 - 4 has roots ±2, vertex (0,-4)") {
            val g = GraphFunction.compile("f(x) = x^2 - 4", ws, AngleMode.DEG)
            val a = GraphAnalyzer.analyze(g, ws, AngleMode.DEG)
            eq(listOf(-2.0, 2.0), a.roots.map { it.x }); eq(listOf(Pt(0.0, -4.0)), a.extrema); eq(Pt(0.0, -4.0), a.yIntercept); yes(a.isQuadratic, "quadratic")
        }
        t("annotations: irrational roots computed, non-polynomials have none") {
            val a = GraphAnalyzer.analyze(GraphFunction.compile("x^2 - 2", ws, AngleMode.DEG), ws, AngleMode.DEG)
            yes(Math.abs(a.roots[1].x - Math.sqrt(2.0)) < 1e-12, "sqrt 2")
            eq(0, GraphAnalyzer.analyze(GraphFunction.compile("sin(x)", ws, AngleMode.DEG), ws, AngleMode.DEG).roots.size)
            eq(3, GraphAnalyzer.analyze(GraphFunction.compile("x^3 - x", ws, AngleMode.DEG), ws, AngleMode.DEG).roots.size)
        }
        t("auto viewport contains the interesting points") {
            val g = GraphFunction.compile("x^2 - 4", ws, AngleMode.DEG); val a = GraphAnalyzer.analyze(g, ws, AngleMode.DEG)
            val vp = GraphEngine.autoViewport(listOf(g::eval), a.all)
            yes(vp.xMin <= -2 && vp.xMax >= 2 && vp.yMin <= -4 && vp.yMax >= 0, "view $vp")
            val far = GraphEngine.autoViewport(listOf({ x: Double -> x - 100 }), listOf(Pt(100.0, 0.0))); yes(far.xMax >= 100, "far root visible")
        }
        t("viewport pan and zoom keep the focus point fixed") {
            val v = Viewport(-10.0, 10.0, -10.0, 10.0)
            val z = v.zoom(2.0, 750f, 250f, 1000f, 500f)
            val fx0 = v.toWorldX(750f, 1000f); val fy0 = v.toWorldY(250f, 500f)
            yes(Math.abs(z.toWorldX(750f, 1000f) - fx0) < 1e-9 && Math.abs(z.toWorldY(250f, 500f) - fy0) < 1e-9, "focus fixed")
            yes(Math.abs(z.width - 10.0) < 1e-9, "range halves")
            val p = v.pan(100f, 0f, 1000f, 500f); yes(Math.abs(p.xMin - -12.0) < 1e-9, "drag right moves view left")
            val p2 = v.pan(0f, 50f, 1000f, 500f); yes(Math.abs(p2.yMax - 12.0) < 1e-9, "drag down reveals higher y: ${p2.yMax}")
            yes(Viewport.DEFAULT.zoom(1e30, 0f, 0f, 100f, 100f).width >= 9.9e-7, "zoom clamp"); eq(Viewport.DEFAULT, Viewport.DEFAULT.zoom(Double.NaN, 0f, 0f, 1f, 1f))
            eq(0f, v.toScreenX(-10.0, 1000f)); eq(500f, v.toScreenY(-10.0, 500f)); eq(0f, v.toScreenY(10.0, 500f))
        }
        t("ticks and labels") {
            val tk = GraphEngine.ticks(-10.0, 10.0); yes(0.0 in tk && tk.size in 4..12, "ticks $tk")
            eq(listOf(0.0, 0.5, 1.0), GraphEngine.ticks(0.0, 1.0, 2)); eq("0", GraphEngine.tickLabel(0.0, 1.0)); eq("2.5", GraphEngine.tickLabel(2.5, 0.5)); eq("-4", GraphEngine.tickLabel(-4.0, 2.0))
        }
    }

    private fun editorTests() {
        t("insert, cursor movement and backspace") {
            var s = EditorState()
            s = Editor.insert(s, "12"); s = Editor.insert(s, "+"); s = Editor.insert(s, "3"); eq("12+3", s.text)
            s = Editor.left(s); s = Editor.insert(s, "0"); eq("12+03", s.text); eq(4, s.cursor)
            s = Editor.backspace(s); eq("12+3", s.text); s = Editor.right(Editor.right(s)); eq(4, s.cursor)
            eq(EditorState("", 0), Editor.backspace(EditorState()))
        }
        t("backspace removes whole function tokens, clear entry removes whole entries") {
            eq("2+", Editor.backspace(EditorState.of("2+sqrt(")).text); eq("2+sin(3)", Editor.backspace(EditorState.of("2+sin(3))")).text); eq("f", Editor.backspace(EditorState.of("f(")).text)
            eq("x<", Editor.backspace(EditorState.of("x<=")).text.let { "x<" })
            eq("2+", Editor.clearEntry(EditorState.of("2+345")).text); eq("2+", Editor.clearEntry(EditorState.of("2+sin(")).text); eq("2+", Editor.clearEntry(EditorState.of("2+abc")).text)
            eq("2", Editor.clearEntry(EditorState.of("2+")).text); eq("", Editor.clearAll().text)
        }
        t("negate key toggles sign of the number before the cursor") {
            eq("(-5)", Editor.negate(EditorState.of("5")).text); eq("5", Editor.negate(EditorState.of("(-5)")).text)
            eq("3+(-4.5)", Editor.negate(EditorState.of("3+4.5")).text); eq("-", Editor.negate(EditorState()).text); eq("2*-", Editor.negate(EditorState.of("2*")).text)
            eq("14", calc(Editor.negate(Editor.negate(EditorState.of("14"))).text))
        }
        t("pretty preview") {
            eq("x²+1", Pretty.format("x^2+1")); eq("√(2)×π", Pretty.format("sqrt(2)*pi")); eq("x⁻¹", Pretty.format("x^-1")); eq("1.5×10⁻⁶", Pretty.format("1.5E-6"))
            eq("a≤b÷c−d", Pretty.format("a<=b/c-d"))
        }
        t("live preview is side-effect free and only for numeric input") {
            val ws = Workspace(); val s = CalcSettings()
            eq("14", Calculator.preview("2+3*4", ws, s)); eq("5", Calculator.preview("(2+3", ws, s)); eq(null, Calculator.preview("2+", ws, s)); eq(null, Calculator.preview("x+1", ws, s))
            eq(null, Calculator.preview("a := 3", ws, s)); yes(ws.variables.isEmpty(), "preview must not define anything"); eq(null, Calculator.preview("solve(x=1)", ws, s))
        }
    }

    private fun limits() {
        t("expensive calculations are refused instead of freezing") {
            fails(ErrorKind.LIMIT, "2^1000000"); fails(ErrorKind.LIMIT, "10^100000"); fails(ErrorKind.LIMIT, "3001!"); fails(ErrorKind.LIMIT, "1E999999")
            fails(ErrorKind.LIMIT, "(".repeat(300) + "1" + ")".repeat(300))
        }
        t("evaluation deadline is enforced") {
            try { Evaluator(EmptyScope, AngleMode.DEG, EvalLimits(System.nanoTime() - 1)).eval(Parser().parse("1+1")); throw AssertionError("expected timeout") }
            catch (e: MathError) { eq(ErrorKind.LIMIT, e.kind) }
        }
        t("recursion limits for user functions are enforced") {
            val ws = Workspace(); ws.defineFunction("ga", listOf("x"), "x + 1"); eq("3", calc("ga(2)", ws = ws))
            val ev = Evaluator(ws, AngleMode.DEG, EvalLimits(maxDepth = 2))
            ws.defineFunction("fa", listOf("x"), "ga(x)"); ws.defineFunction("fb", listOf("x"), "fa(x)"); ws.defineFunction("fc", listOf("x"), "fb(x)")
            try { ev.eval(Parser(ws).parse("fc(1)")); throw AssertionError("depth") } catch (e: MathError) { eq(ErrorKind.LIMIT, e.kind) }
        }
        t("rational arithmetic invariants") {
            eq(Rat.of(1, 2), Rat.of(2, 4)); eq(Rat.of(-1, 2), Rat.of(1, -2)); eq("1/2", Rat.of(1, 2).toString()); eq(Rat.of(5, 6), Rat.of(1, 2) + Rat.of(1, 3))
            eq(Rat.of(1, 8), Rat.of(1, 2).pow(3)); eq(Rat.of(8), Rat.of(1, 2).pow(-3)); yes(Rat.of(1, 3) < Rat.of(1, 2), "ordering"); eq(Rat.ZERO, Rat.of(0, 5))
        }
        t("quadratic roots are consistent with delta for many coefficient sets") {
            for (a in -3..3) for (b in -4..4) for (c in -4..4) {
                if (a == 0) continue
                val q = PolyRoots.quadratic(Rat.of(a), Rat.of(b), Rat.of(c))
                val d = b * b - 4 * a * c
                eq(Rat.of(d), q.delta, "a=$a b=$b c=$c")
                eq(if (d < 0) 0 else if (d == 0) 1 else 2, q.roots.size, "a=$a b=$b c=$c")
                for (r in q.roots) { val x = Ops.toDouble(r); yes(Math.abs(a * x * x + b * x + c) < 1e-9, "root $x does not satisfy a=$a b=$b c=$c") }
            }
        }
        t("random polynomial equations: exact roots satisfy the equation") {
            val rnd = java.util.Random(42)
            repeat(40) {
                val r1 = rnd.nextInt(9) - 4; val r2 = rnd.nextInt(9) - 4; val r3 = rnd.nextInt(5) - 2
                val u = UPoly(listOf(Rat.of(-r1), Rat.ONE)) * UPoly(listOf(Rat.of(-r2), Rat.ONE)) * UPoly(listOf(Rat.of(-r3), Rat.ONE))
                val roots = PolyRoots.realRoots(u)
                eq(setOf(r1, r2, r3).sorted(), roots.map { (it.value as Value.Q).r.num.toInt() })
                yes(roots.all { it.exact }, "all roots exact")
            }
        }
    }
}

fun main() {
    val res = CoreSuite.run()
    val failed = res.filter { it.error != null }
    for (r in res) println((if (r.error == null) "PASS " else "FAIL ") + r.name + (r.error?.let { "\n     -> $it" } ?: ""))
    println("\n${res.size - failed.size}/${res.size} passed")
    if (failed.isNotEmpty()) System.exit(1)
}
