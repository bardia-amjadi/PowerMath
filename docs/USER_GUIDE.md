# Power Math User Guide

## Result modes

- **Exact / Fraction**: `1/3 + 1/6` gives `1/2`; `sqrt(8)` gives `2√2`; `sin(60)` in DEG gives `√3/2`.
- **Decimal**: the same value as a decimal, with ≈ when rounded.
- **Scientific**: for example `1.23 × 10^6`.

The **DEG / RAD** chip switches the angle mode. It also applies to the graph.

## What you can type

Operators: `+ − × ÷ * / ^ ( ) ! %`, decimals, `1.5E3`, `π` or `pi`, `e`, `phi`, `tau`, `√x`, `sqrt(x)`, `root(n, x)`.

Implicit multiplication works: `2x`, `3(x+1)`, `2pi`. It has the same priority as `*`, so `1/2x` means `(1/2)·x`. `-2^2` is `-4`.

Functions: sqrt, root, abs, sin, cos, tan, asin, acos, atan, sinh, cosh, tanh, ln, log(x), log(b, x), exp, mean, min, max, floor, ceil, sgn, fact, gcd, lcm, mod, ncr, npr, delta(a, b, c).

Percent: `x%` is x/100. `200 + 10%` is 220. `200 − 10%` is 180.

Missing closing brackets at the end are added automatically. An extra `)` is an error.

## Variables and functions

| You type | Result |
|---|---|
| `a := 5` | Saves variable `a` (its value at that moment) |
| `f(x) = x^2 + 1` | Defines a new function `f` |
| `f(x) := x^3` | Replaces an existing function |
| `f(2)` | Calls it |
| `ans` | The previous result |

Names use letters and `_` only, so `a1` means `a·1`. Names are case-sensitive and built-in names are reserved. Deleting or renaming warns if something else depends on it. The **Workspace** screen lists everything saved.

## Solving

| Type | Example |
|---|---|
| Equation | `x^2 - 5x + 6 = 0` or `solve(x^2-5x+6=0)` |
| Inequality | `x^2 - 4 < 0` (also `>`, `<=`, `>=`, `!=`) |
| Quadratic tool | `quad(1, -5, 6)` shows Δ, roots, vertex, axis |
| System | `x + y = 3; x - y = 1` |
| Simplify / expand / factor | `simplify(...)`, `expand((x+1)^2)`, `factor(x^2-1)` |
| Evaluate with values | `eval(x^2 + y; x = 2, y = 3)` |

Tap **Steps** under a result to see the derivation. Unsupported input shows a clear message instead of a guess.

## Graphing

Tap **Graph** on the calculator, or **Graph** next to a result. Enter `f(x)`, `y = ...` or just an expression.

- Drag to pan, pinch to zoom.
- Several functions can be shown at once.
- Roots, vertex or extrema, and the y-intercept are marked for polynomials.
- Breaks are drawn at discontinuities such as `1/x`.

## Keypad layers

Shift, Alt and Ctrl are toggles. They stay on until you tap them again, and combining them never clears your expression.

| Layer | Contains |
|---|---|
| Standard | Digits, operators, brackets |
| Alt | x y z a–g, relation symbols, `:=` |
| Shift | √, ⁿ√, x², xʸ, trig and inverse trig, ln, log, n!, \|x\|, mean, Δ, x₁,₂ |
| Ctrl | Define, edit and delete variables and functions; insert saved items; open workspace, history, graph |
| Shift + Alt | Templates such as x², sin x, f(x) |
| Ctrl + Alt | Solve, Inequality, Quad, System, Eval, Simplify, Expand, Factor, Graph |
| Shift + Ctrl | Save result as variable, save expression as function, Ans, Copy, Paste, clear variables, functions or history |
| Ctrl + Shift + Alt | φ, τ, gcd, lcm, mod, nCr, nPr, sinh, cosh, tanh, log_b |

A hardware keyboard works too: digits and operators type directly, Enter evaluates, Backspace deletes.

## Errors

Division by zero, domain errors, syntax errors, undefined names, overflow and timeouts each show a specific message. The expression stays in place so you can correct it.
