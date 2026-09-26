package com.edupulse.app.diagram

import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.*

/**
 * Extracts a [PhysicsDiagram] from raw LLM response text + the user's original query.
 *
 * Strategy (in priority order):
 *  1. Explicit `[DIAGRAM:TYPE ...]` structured tag emitted by Gemma.
 *  2. Projectile heuristic (keyword detection + SI solver).
 *  3. Free-body heuristic (keyword detection + SI solver).
 *  4. Kinematics heuristic (any 2+ physics quantities found → full solver).
 *
 * All numerical quantities are converted to SI before solving so that the renderer
 * always receives consistent, correctly-derived values regardless of the units used
 * in the question (km/h, g, kN, min, cm, etc.).
 */
object DiagramExtractor {

    private val EXPLICIT_TAG_PATTERN: Pattern = Pattern.compile(
        "\\[DIAGRAM:(KINEMATICS|FREE_BODY|PROJECTILE)(.*?)(?:\\]|\\[/DIAGRAM\\])",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    fun stripDiagramTags(text: String): String =
        text.replace(EXPLICIT_TAG_PATTERN.toRegex(), "").trim()

    fun extract(text: String, query: String = ""): PhysicsDiagram? {
        extractFromTag(text)?.let { return it }
        extractProjectileHeuristic(text, query)?.let { return it }
        extractFreeBodyHeuristic(text, query)?.let { return it }
        return extractKinematicsHeuristic(text, query)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SI unit converter
    // Returns the value in the canonical SI unit for that quantity, or null.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given a matched capture group string such as "-4.17 m/s²" or "25 km/h",
     * returns the SI-converted float value.
     */
    private fun siValue(raw: String): Float? {
        val trimmed = raw.trim()
        // Extract numeric prefix (possibly negative / decimal)
        val numMatch = Regex("""^(-?[0-9]+(?:\.[0-9]*)?)""").find(trimmed) ?: return null
        val num = numMatch.value.toFloatOrNull() ?: return null
        val unit = trimmed.substring(numMatch.value.length).trim().lowercase()
        return when {
            unit.startsWith("km/h") || unit.startsWith("kmph") || unit.startsWith("kph") -> num / 3.6f
            unit.startsWith("mph")  -> num * 0.44704f
            unit.startsWith("m/s")  -> num               // m/s or m/s² — num is already SI
            unit.startsWith("km/s") -> num * 1000f
            unit.startsWith("cm/s") -> num / 100f
            unit == "km" || unit.startsWith("kilomet") -> num * 1000f
            unit.startsWith("cm")   -> num / 100f
            unit.startsWith("mm")   -> num / 1000f
            unit == "m" || unit.startsWith("meter") || unit.startsWith("metre") -> num
            unit.startsWith("kn")   -> num * 1000f       // kN → N
            unit == "n" || unit.startsWith("newton") -> num
            // mass: default "g" alone is grams, "kg" is kilograms
            unit.startsWith("kg")   -> num
            unit == "g" || unit.startsWith("gram") -> num / 1000f
            unit.startsWith("min")  -> num * 60f          // minutes → seconds
            unit == "h" || unit == "hr" || unit.startsWith("hour") -> num * 3600f
            unit == "s" || unit.startsWith("sec") -> num
            // degree / angle — return raw numeric value; callers interpret as degrees
            unit == "°" || unit.startsWith("deg") -> num
            else -> num                                    // assume already SI
        }
    }

    /** Try each regex in order; return SI float for first match, else null. */
    private fun firstSI(text: String, patterns: List<Regex>): Float? {
        for (re in patterns) {
            val g = re.find(text)?.groupValues?.getOrNull(1) ?: continue
            return siValue(g) ?: continue
        }
        return null
    }

    /** Non-SI string match (returns raw capture group string). */
    private fun firstRaw(text: String, patterns: List<Regex>): String? {
        for (re in patterns) {
            val g = re.find(text)?.groupValues?.getOrNull(1) ?: continue
            if (g.isNotBlank()) return g.trim()
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regex pattern libraries
    // ─────────────────────────────────────────────────────────────────────────

    private val MASS_PAT = listOf(
        Regex("""(?:mass|m)\s*(?:of\s+(?:the\s+)?(?:body|object|car|block|ball|particle|truck|train|ship|man|person|stone|brick))?\s*[=:]\s*(-?[0-9.]+\s*(?:kg|g\b))""", RegexOption.IGNORE_CASE),
        Regex("""(-?[0-9.]+\s*kg)\s*(?:mass|body|object|car|block|ball|truck|train|stone)""", RegexOption.IGNORE_CASE),
        Regex("""(-?[0-9.]+\s*g)\s*(?:mass|body|object|ball|stone)""", RegexOption.IGNORE_CASE),
        Regex("""mass\s+(?:of\s+)?(?:\w+\s+)?(?:is\s+)?(-?[0-9.]+\s*k?g)""", RegexOption.IGNORE_CASE)
    )

    private val INIT_VEL_PAT = listOf(
        Regex("""(?:initial\s+velocity|initial\s+speed|u|v_?0)\s*[=:]\s*(-?[0-9.]+\s*(?:m/s|km/h|kmph|kph|mph))""", RegexOption.IGNORE_CASE),
        Regex("""(?:moving|travels?|running|driving)\s+(?:at|with)?\s*(?:a\s+speed\s+of\s+)?(-?[0-9.]+\s*(?:m/s|km/h|kmph|kph|mph))""", RegexOption.IGNORE_CASE),
        Regex("""(?:with|at)\s+(?:a\s+)?(?:uniform\s+)?(?:speed|velocity)\s+(?:of\s+)?(-?[0-9.]+\s*(?:m/s|km/h|kmph|kph|mph))""", RegexOption.IGNORE_CASE),
        Regex("""velocity\s+(?:of\s+)?(-?[0-9.]+\s*(?:m/s|km/h|kmph))""", RegexOption.IGNORE_CASE),
        Regex("""speed\s+(?:of\s+)?(-?[0-9.]+\s*(?:m/s|km/h|kmph))""", RegexOption.IGNORE_CASE)
    )

    private val FINAL_VEL_PAT = listOf(
        Regex("""(?:final\s+velocity|final\s+speed|v_?f?)\s*[=:]\s*(-?[0-9.]+\s*(?:m/s|km/h|kmph))""", RegexOption.IGNORE_CASE),
        Regex("""velocity\s+(?:becomes|reaches|increases?\s+to|decreases?\s+to)\s*(-?[0-9.]+\s*(?:m/s|km/h|kmph))""", RegexOption.IGNORE_CASE)
    )

    private val ACCEL_PAT = listOf(
        Regex("""(?:acceleration|retardation|deceleration)\s*[=:]\s*(-?[0-9.]+\s*m/s(?:\^2|²|2)?)""", RegexOption.IGNORE_CASE),
        Regex("""a\s*[=:]\s*(-?[0-9.]+\s*m/s(?:\^2|²|2)?)""", RegexOption.IGNORE_CASE),
        Regex("""accelerates?\s+(?:uniformly\s+)?(?:at\s+)?(-?[0-9.]+\s*m/s(?:\^2|²|2)?)""", RegexOption.IGNORE_CASE),
        Regex("""with\s+(?:a\s+)?(?:uniform\s+)?(?:acceleration|retardation)\s+(?:of\s+)?(-?[0-9.]+\s*m/s(?:\^2|²|2)?)""", RegexOption.IGNORE_CASE)
    )

    private val FORCE_PAT = listOf(
        Regex("""(?:force|F)\s*(?:applied|retarding|braking|net|of\s+friction)?\s*[=:]\s*(-?[0-9.]+\s*(?:kN|N))""", RegexOption.IGNORE_CASE),
        Regex("""magnitude\s+of\s+(?:the\s+)?(?:net\s+)?force\s*(?:is\s+)?(-?[0-9.]+\s*(?:kN|N))""", RegexOption.IGNORE_CASE),
        Regex("""(?:net\s+)?force\s+(?:of\s+|is\s+)?(-?[0-9.]+\s*(?:kN|N))""", RegexOption.IGNORE_CASE),
        Regex("""(-?[0-9.]+\s*(?:kN|N))\s+(?:force|thrust|push|pull)""", RegexOption.IGNORE_CASE)
    )

    private val DISTANCE_PAT = listOf(
        Regex("""(?:distance|displacement|s|d)\s*(?:travelled|covered|before\s+stopping|of\s+stopping)?\s*[=:]\s*(-?[0-9.]+\s*(?:km|cm|mm|m\b))(?!\s*/|\s*s)""", RegexOption.IGNORE_CASE),
        Regex("""(?:travels?|covers?|stops?\s+after|comes\s+to\s+rest\s+after)\s+(-?[0-9.]+\s*(?:km|cm|m\b))(?!\s*/|\s*s)""", RegexOption.IGNORE_CASE),
        Regex("""(?:range|horizontal\s+distance)\s*[=:]\s*(-?[0-9.]+\s*(?:km|m\b))(?!\s*/|\s*s)""", RegexOption.IGNORE_CASE)
    )

    private val TIME_PAT = listOf(
        Regex("""(?:time|t)\s*(?:taken|of\s+flight)?\s*[=:]\s*(-?[0-9.]+\s*(?:min(?:utes?)?|h(?:ours?|r)?|s(?:ec(?:onds?)?)?\b))""", RegexOption.IGNORE_CASE),
        Regex("""(?:in|for|after|within)\s+(-?[0-9.]+\s*(?:s(?:ec(?:onds?)?)?\b|min(?:utes?)?|h(?:ours?|r)?))""", RegexOption.IGNORE_CASE)
    )

    private val ANGLE_PAT = listOf(
        Regex("""(?:angle|theta|θ)\s*[=:]\s*(-?[0-9.]+\s*(?:°|deg(?:rees?)?))""", RegexOption.IGNORE_CASE),
        Regex("""at\s+(?:an\s+)?angle\s+of\s+(-?[0-9.]+\s*(?:°|deg(?:rees?)?))""", RegexOption.IGNORE_CASE),
        Regex("""(-?[0-9.]+\s*(?:°|deg(?:rees?)?))\s*(?:to\s+the\s+horizontal|above\s+(?:the\s+)?horizontal)""", RegexOption.IGNORE_CASE)
    )

    private val NORMAL_FORCE_PAT = listOf(
        Regex("""(?:normal\s+(?:force|reaction)|N|Fn)\s*[=:]\s*(-?[0-9.]+\s*(?:kN|N\b))""", RegexOption.IGNORE_CASE)
    )
    private val FRICTION_PAT = listOf(
        Regex("""(?:friction\s+force|force\s+of\s+friction|kinetic\s+friction|f_?k|f_?r)\s*[=:]\s*(-?[0-9.]+\s*(?:kN|N\b))""", RegexOption.IGNORE_CASE),
        Regex("""(?:coefficient\s+of\s+(?:kinetic\s+)?friction|μ_?k?|mu)\s*[=:]\s*(-?[0-9.]+)""", RegexOption.IGNORE_CASE)
    )
    private val APPLIED_FORCE_PAT = listOf(
        Regex("""(?:applied|pushing|pulling|external)\s+force\s*[=:]\s*(-?[0-9.]+\s*(?:kN|N\b))""", RegexOption.IGNORE_CASE)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Analytical Kinematics Solver
    // Iteratively applies: v=u+at, s=ut+½at², v²=u²+2as, F=ma
    // ─────────────────────────────────────────────────────────────────────────

    private data class KVars(
        var u: Float? = null,
        var v: Float? = null,
        var a: Float? = null,
        var t: Float? = null,
        var s: Float? = null,
        var m: Float? = null,
        var f: Float? = null
    )

    private fun solve(k: KVars): KVars {
        var changed = true
        while (changed) {
            changed = false
            fun set(cond: Boolean, block: () -> Unit) { if (cond) { block(); changed = true } }

            // F = ma
            set(k.f == null && k.m != null && k.a != null) { k.f = k.m!! * abs(k.a!!) }
            set(k.a == null && k.m != null && k.f != null && k.m!! != 0f) { k.a = k.f!! / k.m!! }
            set(k.m == null && k.a != null && k.f != null && k.a!! != 0f) { k.m = k.f!! / abs(k.a!!) }

            // v = u + at
            set(k.v == null && k.u != null && k.a != null && k.t != null) { k.v = k.u!! + k.a!! * k.t!! }
            set(k.u == null && k.v != null && k.a != null && k.t != null) { k.u = k.v!! - k.a!! * k.t!! }
            set(k.a == null && k.v != null && k.u != null && k.t != null && k.t!! != 0f) { k.a = (k.v!! - k.u!!) / k.t!! }
            set(k.t == null && k.a != null && k.a!! != 0f && k.v != null && k.u != null) { k.t = (k.v!! - k.u!!) / k.a!! }

            // s = ut + ½at²
            set(k.s == null && k.u != null && k.t != null && k.a != null) {
                k.s = k.u!! * k.t!! + 0.5f * k.a!! * k.t!! * k.t!!
            }
            set(k.u == null && k.s != null && k.t != null && k.a != null && k.t!! != 0f) {
                k.u = (k.s!! - 0.5f * k.a!! * k.t!! * k.t!!) / k.t!!
            }

            // v² = u² + 2as
            if (k.v == null && k.u != null && k.a != null && k.s != null) {
                val v2 = k.u!! * k.u!! + 2f * k.a!! * k.s!!
                if (v2 >= 0f) { k.v = sqrt(v2); changed = true }
            }
            if (k.u == null && k.v != null && k.a != null && k.s != null) {
                val u2 = k.v!! * k.v!! - 2f * k.a!! * k.s!!
                if (u2 >= 0f) { k.u = sqrt(u2); changed = true }
            }
            set(k.s == null && k.u != null && k.v != null && k.a != null && k.a!! != 0f) {
                k.s = (k.v!! * k.v!! - k.u!! * k.u!!) / (2f * k.a!!)
            }
            set(k.a == null && k.u != null && k.v != null && k.s != null && k.s!! != 0f) {
                k.a = (k.v!! * k.v!! - k.u!! * k.u!!) / (2f * k.s!!)
            }

            // t = 2s / (u + v)  [average velocity]
            set(k.t == null && k.s != null && k.u != null && k.v != null) {
                val avg = k.u!! + k.v!!
                if (abs(avg) > 1e-6f) k.t = 2f * k.s!! / avg
            }
        }
        return k
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build diagram from solved variables
    // ─────────────────────────────────────────────────────────────────────────

    private fun kinematicsFromSolved(k: KVars, isDecel: Boolean): PhysicsDiagram.Kinematics {
        val u    = k.u ?: 10f
        val v    = k.v ?: if (isDecel) 0f else u + (k.a ?: 2.5f) * (k.t ?: 4f)
        val aAbs = if (k.a != null) abs(k.a!!) else {
            if (k.t != null && k.t!! > 0f && abs(v - u) > 0f) abs(v - u) / k.t!! else 2.5f
        }
        val a    = if (isDecel) -aAbs else aAbs
        val t    = k.t ?: (if (aAbs > 0f) abs(v - u) / aAbs else 4f)
        val dist = k.s?.let { abs(it) } ?: abs(u * t + 0.5f * a * t * t)
        val m    = k.m ?: 5f
        val fMag = k.f?.let { abs(it) } ?: (m * aAbs)

        return PhysicsDiagram.Kinematics(
            mass            = "%.1f kg".format(m),
            initialVelocity = "%.2f m/s".format(u),
            finalVelocity   = "%.2f m/s".format(v),
            acceleration    = "%.3f m/s²".format(a),
            force           = "%.1f N".format(if (isDecel) -fMag else fMag),
            distance        = "%.2f m".format(dist),
            time            = "%.2f s".format(t),
            isDecelerating  = isDecel
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Structured [DIAGRAM:...] tag extractor
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractFromTag(text: String): PhysicsDiagram? {
        val matcher = EXPLICIT_TAG_PATTERN.matcher(text)
        if (!matcher.find()) return null

        val type = matcher.group(1)?.uppercase() ?: return null
        val body = matcher.group(2) ?: ""

        val p = mutableMapOf<String, String>()
        body.split(Regex("[|,\n]")).forEach { pair ->
            val parts = pair.split(Regex("[:=]"), 2)
            if (parts.size == 2) p[parts[0].trim().lowercase()] = parts[1].trim()
        }

        fun pf(vararg keys: String): Float? =
            keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }

        return when (type) {
            "KINEMATICS" -> {
                val k = KVars(
                    u = pf("u", "initial_velocity", "v0"),
                    v = pf("v", "final_velocity"),
                    a = pf("a", "acceleration"),
                    t = pf("t", "time"),
                    s = pf("s", "d", "distance"),
                    m = pf("mass", "m"),
                    f = pf("f", "force")
                )
                val isDecel = (k.v ?: 0f) < (k.u ?: 0f) ||
                    p["a"]?.startsWith("-") == true ||
                    p["f"]?.startsWith("-") == true
                if (k.a != null && isDecel) k.a = -abs(k.a!!)
                kinematicsFromSolved(solve(k), isDecel)
            }
            "FREE_BODY"  -> buildFreeBody(p)
            "PROJECTILE" -> buildProjectile(p)
            else         -> null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kinematics heuristic
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractKinematicsHeuristic(text: String, query: String): PhysicsDiagram? {
        val combined = "$query\n$text"

        val mSI  = firstSI(combined, MASS_PAT)

        var uSI  = firstSI(combined, INIT_VEL_PAT)
        if (uSI == null && combined.contains(
                Regex("""(from\s+rest|starts?\s+from\s+rest|initially\s+at\s+rest|stationary\b|at\s+rest\b)""",
                    RegexOption.IGNORE_CASE))) {
            uSI = 0f
        }

        var vSI  = firstSI(combined, FINAL_VEL_PAT)
        val isBroughtToRest = combined.contains(
            Regex("""(brought\s+to\s+rest|comes\s+to\s+rest|before\s+stopping|(?<![a-z])stops?\b|v\s*=\s*0)""",
                RegexOption.IGNORE_CASE))
        if (vSI == null && isBroughtToRest) vSI = 0f

        val aSI  = firstSI(combined, ACCEL_PAT)
        val fSI  = firstSI(combined, FORCE_PAT)
        val sSI  = firstSI(combined, DISTANCE_PAT)
        val tSI  = firstSI(combined, TIME_PAT)

        val count = listOfNotNull(mSI, uSI, vSI, aSI, fSI, sSI, tSI).size
        if (count < 2) return null
        if (uSI == null && fSI == null && sSI == null && aSI == null) return null

        val isDecel = isBroughtToRest ||
            (vSI != null && uSI != null && vSI < uSI) ||
            combined.contains(Regex("""(decelerat|retard|brak|slow(?:ing)?\s+down)""", RegexOption.IGNORE_CASE))

        // Determine signed acceleration
        val aSigned: Float? = when {
            aSI != null && isDecel -> -abs(aSI)
            aSI != null            -> aSI
            fSI != null && mSI != null && mSI > 0f -> (if (isDecel) -fSI else fSI) / mSI
            else                   -> null
        }

        val k = KVars(u = uSI, v = vSI, a = aSigned, t = tSI, s = sSI, m = mSI, f = fSI)
        val solved = solve(k)

        if (solved.u == null && solved.v == null) return null

        return kinematicsFromSolved(solved, isDecel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Projectile heuristic
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractProjectileHeuristic(text: String, query: String): PhysicsDiagram? {
        val combined = "$query\n$text"
        val isProjectile = combined.contains(
            Regex("""(projectile|launched|kicked|thrown|fired|angle\s+of|trajectory)""", RegexOption.IGNORE_CASE))
        if (!isProjectile) return null

        val angleDeg = firstSI(combined, ANGLE_PAT) ?: 45f
        val v0       = firstSI(combined, INIT_VEL_PAT) ?: return null   // must have v0
        val rangeSI  = firstSI(combined, listOf(
            Regex("""(?:range|horizontal\s+range)\s*[=:]\s*(-?[0-9.]+\s*(?:km|m\b))""", RegexOption.IGNORE_CASE)))
        val heightSI = firstSI(combined, listOf(
            Regex("""(?:max(?:imum)?\s+height|height)\s*[=:]\s*(-?[0-9.]+\s*(?:km|m\b))""", RegexOption.IGNORE_CASE)))
        val tFlightSI = firstSI(combined, listOf(
            Regex("""time\s+of\s+flight\s*[=:]\s*(-?[0-9.]+\s*s)""", RegexOption.IGNORE_CASE)))

        return buildProjectileFromPhysics(angleDeg, v0, rangeSI, heightSI, tFlightSI)
    }

    private fun buildProjectileFromPhysics(
        angleDeg: Float, v0: Float,
        rangeM: Float? = null, heightM: Float? = null, tFlightS: Float? = null
    ): PhysicsDiagram.Projectile {
        val g = 9.8f
        val rad = angleDeg * PI.toFloat() / 180f
        val sinA = sin(rad)
        val cosA = cos(rad)

        val tFlight = tFlightS ?: (2f * v0 * sinA / g)
        val hMax    = heightM   ?: (v0 * v0 * sinA * sinA / (2f * g))
        val range   = rangeM    ?: (v0 * v0 * sin(2f * rad) / g)

        return PhysicsDiagram.Projectile(
            angle        = "%.1f°".format(angleDeg),
            velocity     = "%.1f m/s".format(v0),
            range        = "%.2f m".format(range),
            maxHeight    = "%.2f m".format(hMax),
            timeOfFlight = "%.2f s".format(tFlight)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Free-body heuristic
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractFreeBodyHeuristic(text: String, query: String): PhysicsDiagram? {
        val combined = "$query\n$text"
        if (!combined.contains(
                Regex("""(free[-\s]body|normal\s+force|friction\s+force|coefficient\s+of\s+friction|rough\s+surface)""",
                    RegexOption.IGNORE_CASE))) return null

        val mSI = firstSI(combined, MASS_PAT) ?: return null
        return buildFreeBodyFromMass(combined, mSI)
    }

    private fun buildFreeBodyFromMass(combined: String, mSI: Float): PhysicsDiagram.FreeBody {
        val g  = 9.8f
        val w  = mSI * g

        val normal = firstSI(combined, NORMAL_FORCE_PAT) ?: w

        // Friction: either given directly or via coefficient
        val frictionDirect = firstSI(combined, listOf(
            Regex("""(?:friction\s+force|kinetic\s+friction|f_?k)\s*[=:]\s*(-?[0-9.]+\s*(?:kN|N\b))""", RegexOption.IGNORE_CASE)
        ))
        val mu = firstSI(combined, listOf(
            Regex("""(?:coefficient\s+of\s+(?:kinetic\s+)?friction|μ_?k?|mu_?k?)\s*[=:]\s*(-?[0-9.]+)""", RegexOption.IGNORE_CASE)
        ))
        val friction = frictionDirect ?: (if (mu != null) mu * normal else 0.3f * normal)

        val applied = firstSI(combined, APPLIED_FORCE_PAT) ?: (friction + mSI * 2f)
        val net     = applied - friction

        return buildFreeBody(
            mapOf(
                "mass"     to "%.1f".format(mSI),
                "normal"   to "%.1f".format(normal),
                "gravity"  to "%.1f".format(w),
                "applied"  to "%.1f".format(applied),
                "friction" to "%.1f".format(friction),
                "net"      to "%.1f".format(net)
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FreeBody & Projectile builders from param maps (used by tag extractor)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildFreeBody(p: Map<String, String>): PhysicsDiagram.FreeBody {
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val m       = pf("mass", "m") ?: 10f
        val g       = 9.8f
        val w       = pf("gravity", "w", "mg") ?: (m * g)
        val normal  = pf("normal", "fn", "n") ?: w
        val applied = pf("applied", "fa") ?: (m * 2f + (pf("friction", "f_friction", "fk") ?: 0.3f * normal))
        val friction= pf("friction", "f_friction", "fk") ?: 0.3f * normal
        val net     = pf("net", "fnet") ?: (applied - friction)
        return PhysicsDiagram.FreeBody(
            mass          = "%.1f kg".format(m),
            normalForce   = "%.1f N".format(normal),
            gravityForce  = "%.1f N".format(w),
            appliedForce  = "%.1f N".format(applied),
            frictionForce = "%.1f N".format(friction),
            netForce      = "%.1f N".format(net)
        )
    }

    private fun buildProjectile(p: Map<String, String>): PhysicsDiagram.Projectile {
        fun pf(vararg keys: String) = keys.firstNotNullOfOrNull { p[it]?.toFloatOrNull() }
        val angleDeg = pf("angle", "theta") ?: 45f
        val v0       = pf("u", "v", "velocity") ?: 20f
        val g        = 9.8f
        val rad      = angleDeg * PI.toFloat() / 180f
        val tFlight  = pf("time", "t") ?: (2f * v0 * sin(rad) / g)
        val hMax     = pf("height", "h") ?: (v0 * v0 * sin(rad) * sin(rad) / (2f * g))
        val range    = pf("range", "r") ?: (v0 * v0 * sin(2f * rad) / g)
        return PhysicsDiagram.Projectile(
            angle        = "%.1f°".format(angleDeg),
            velocity     = "%.1f m/s".format(v0),
            range        = "%.2f m".format(range),
            maxHeight    = "%.2f m".format(hMax),
            timeOfFlight = "%.2f s".format(tFlight)
        )
    }
}
