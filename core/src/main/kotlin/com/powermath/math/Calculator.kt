package com.powermath.math

data class CalcSettings(
    val angle: AngleMode = AngleMode.DEG,
    val format: FormatSettings = FormatSettings(),
    val timeoutMs: Long = 5000
)

/** Single entry point used by the UI: parse -> resolve -> evaluate/solve -> structured Outcome. */
object Calculator {

    fun process(input: String, ws: Workspace, settings: CalcSettings): Outcome {
        val text = ExprUtil.balanceParens(input.trim())
        if (text.isEmpty()) throw MathError(ErrorKind.SYNTAX, "Empty expression")
        val stmts = Parser(ws).parseAll(text)
        for (st in stmts) rejectNestedCommands(st)
        val limits = EvalLimits.withTimeoutMs(settings.timeoutMs)
        val solver = Solver(ws, settings.angle)
        if (stmts.size > 1) {
            if (stmts.all { it is Expr.Rel }) return solver.solveSystem(stmts.map { it as Expr.Rel })
            throw MathError(ErrorKind.SYNTAX, "Several statements are only allowed as a system of equations separated by ';'")
        }
        return when (val st = stmts[0]) {
            is Expr.FuncDefStmt -> {
                val ch = ws.defineFunction(st.name, st.params, st.bodySource, st.overwrite)
                Outcome(OutcomeKind.DEFINED, listOf(Line.Text("${st.name}(${st.params.joinToString(", ")}) = ${st.bodySource}  (saved)")),
                    graphExpr = null, changes = listOf(ch))
            }
            is Expr.Assign -> {
                val ch = ws.defineVariable(st.name, st.source, overwrite = true, angle = settings.angle)
                Outcome(OutcomeKind.DEFINED, listOf(Line.Eq(st.name, ch.def.value)), value = ch.def.value, changes = listOf(ch))
            }
            is Expr.Call -> if (st.name in Names.commands) command(st, text, ws, settings, solver, limits) else plain(st, ws, settings, solver, limits)
            is Expr.Rel -> relation(st, ws, settings, solver, limits)
            else -> plain(st, ws, settings, solver, limits)
        }
    }

    private fun rejectNestedCommands(st: Expr) {
        val inner: List<Expr> = if (st is Expr.Call && st.name in Names.commands) st.args else listOf(st)
        for (e in inner) {
            val bad = ExprUtil.calledFunctions(e).firstOrNull { it in Names.commands }
            if (bad != null) throw MathError(ErrorKind.UNSUPPORTED, "'$bad' must be used as a whole command, not inside an expression")
        }
    }

    private fun freeUnbound(e: Expr, ws: Workspace) = ExprUtil.freeVars(e).filter { ws.variable(it) == null }

    private fun plain(e: Expr, ws: Workspace, s: CalcSettings, solver: Solver, limits: EvalLimits): Outcome {
        val free = freeUnbound(e, ws)
        if (free.isEmpty()) {
            val v = Evaluator(ws, s.angle, limits).eval(e)
            ws.ans = v
            return Outcome(OutcomeKind.VALUE, listOf(Line.Val(v)), value = v)
        }
        try {
            return solver.expand(e)
        } catch (ex: MathError) {
            if (ex.kind == ErrorKind.UNSUPPORTED)
                throw MathError(ErrorKind.UNDEFINED, "Undefined variable '${free.first()}'. Assign it with ${free.first()} := value, or use the Graph tool.")
            throw ex
        }
    }

    private fun relation(rel: Expr.Rel, ws: Workspace, s: CalcSettings, solver: Solver, limits: EvalLimits): Outcome {
        if (freeUnbound(rel, ws).isEmpty()) {
            val ev = Evaluator(ws, s.angle, limits)
            val a = ev.eval(rel.l)
            val b = ev.eval(rel.r)
            val c = if (a is Value.Q && b is Value.Q) a.r.compareTo(b.r) else {
                val x = Ops.toDouble(a); val y = Ops.toDouble(b)
                if (Math.abs(x - y) <= 1e-12 * maxOf(1.0, Math.abs(x), Math.abs(y))) 0 else x.compareTo(y)
            }
            val truth = when (rel.op) {
                RelOp.EQ -> c == 0; RelOp.NE -> c != 0; RelOp.LT -> c < 0; RelOp.GT -> c > 0; RelOp.LE -> c <= 0; RelOp.GE -> c >= 0
            }
            return Outcome(OutcomeKind.BOOLEAN, listOf(Line.Text(if (truth) "True" else "False")))
        }
        return if (rel.op == RelOp.EQ) solver.solveEquation(rel.l, rel.r) else solver.solveInequality(rel.op, rel.l, rel.r)
    }

    private fun command(c: Expr.Call, text: String, ws: Workspace, s: CalcSettings, solver: Solver, limits: EvalLimits): Outcome {
        fun need(n: Int) { if (c.args.size != n) throw MathError(ErrorKind.SYNTAX, "${c.name}() expects $n argument(s)") }
        return when (c.name) {
            "solve" -> {
                if (c.args.isEmpty()) throw MathError(ErrorKind.SYNTAX, "solve() needs an equation")
                var args = c.args
                var prefer: String? = null
                if (args.size >= 2 && args.last() is Expr.Var && args.dropLast(1).all { it is Expr.Rel }) {
                    prefer = (args.last() as Expr.Var).name
                    args = args.dropLast(1)
                }
                if (args.size >= 2) {
                    if (args.any { it !is Expr.Rel }) throw MathError(ErrorKind.SYNTAX, "A system needs equations such as x + y = 3")
                    solver.solveSystem(args.map { it as Expr.Rel })
                } else when (val a = args[0]) {
                    is Expr.Rel -> if (a.op == RelOp.EQ) solver.solveEquation(a.l, a.r, prefer) else solver.solveInequality(a.op, a.l, a.r, prefer)
                    else -> solver.solveEquation(a, Expr.Num(Rat.ZERO), prefer)
                }
            }
            "simplify" -> { need(1); solver.simplify(c.args[0]) }
            "expand" -> { need(1); solver.expand(c.args[0]) }
            "factor" -> { need(1); solver.factor(c.args[0]) }
            "quad" -> {
                need(3)
                val ev = Evaluator(ws, s.angle, limits)
                val v = c.args.map { ev.eval(it) }
                if (v.any { it !is Value.Q }) throw MathError(ErrorKind.UNSUPPORTED, "quad(a, b, c) needs rational coefficients")
                solver.quadraticTool((v[0] as Value.Q).r, (v[1] as Value.Q).r, (v[2] as Value.Q).r)
            }
            "eval" -> {
                if (c.args.isEmpty()) throw MathError(ErrorKind.SYNTAX, "eval(expression; x = value, ...)")
                val ev = Evaluator(ws, s.angle, limits)
                val binds = LinkedHashMap<String, Value>()
                for (a in c.args.drop(1)) {
                    if (a !is Expr.Rel || a.op != RelOp.EQ || a.l !is Expr.Var) throw MathError(ErrorKind.SYNTAX, "Conditions must look like x = 2")
                    binds[a.l.name] = ev.eval(a.r)
                }
                val e = c.args[0]
                val remaining = ExprUtil.freeVars(e).filter { it !in binds && ws.variable(it) == null }
                if (remaining.isEmpty()) {
                    val v = Evaluator(ws, s.angle, limits, binds).eval(e)
                    ws.ans = v
                    Outcome(OutcomeKind.VALUE, listOf(Line.Val(v)), value = v)
                } else {
                    val layered = LayeredScope(ws, binds)
                    Solver(layered, s.angle).expand(e)
                }
            }
            "plot" -> {
                val inner = text.trim().removePrefix("plot(").removeSuffix(")")
                Outcome(OutcomeKind.ALGEBRA, listOf(Line.Text("Plotting: $inner")), graphExpr = inner)
            }
            else -> throw MathError(ErrorKind.UNDEFINED, "Unknown command '${c.name}'")
        }
    }

    /** Lightweight live preview: only side-effect-free numeric expressions. */
    fun preview(input: String, ws: Workspace, settings: CalcSettings): String? {
        return try {
            val text = ExprUtil.balanceParens(input.trim())
            if (text.isEmpty()) return null
            val stmts = Parser(ws).parseAll(text)
            if (stmts.size != 1) return null
            val st = stmts[0]
            if (st is Expr.Rel || st is Expr.Assign || st is Expr.FuncDefStmt) return null
            if (st is Expr.Call && st.name in Names.commands) return null
            if (freeUnbound(st, ws).isNotEmpty()) return null
            val v = Evaluator(ws, settings.angle, EvalLimits.withTimeoutMs(500)).eval(st)
            Formatter.format(v, settings.format)
        } catch (e: MathError) { null }
    }
}
