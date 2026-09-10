package com.edupulse.app.diagram

import java.util.regex.Pattern

object DiagramExtractor {

    private val EXPLICIT_TAG_PATTERN = Pattern.compile(
        "\\[DIAGRAM:(KINEMATICS|FREE_BODY|PROJECTILE)(.*?)(?:\\]|\\[/DIAGRAM\\])",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    fun stripDiagramTags(text: String): String {
        return text.replace(EXPLICIT_TAG_PATTERN.toRegex(), "").trim()
    }

    fun extract(text: String, query: String = ""): PhysicsDiagram? {
        // 1. Try explicit structured tag from Gemma
        val tagDiagram = extractFromTag(text)
        if (tagDiagram != null) return tagDiagram

        // 2. Intelligent physics heuristic extraction from text + query
        return extractKinematicsHeuristic(text, query)
    }

    private fun extractFromTag(text: String): PhysicsDiagram? {
        val matcher = EXPLICIT_TAG_PATTERN.matcher(text)
        if (!matcher.find()) return null

        val type = matcher.group(1)?.uppercase() ?: return null
        val body = matcher.group(2) ?: ""

        val params = mutableMapOf<String, String>()
        val pairs = body.split(Regex("[|,\n]"))
        for (pair in pairs) {
            val parts = pair.split(Regex("[:=]"), 2)
            if (parts.size == 2) {
                params[parts[0].trim().lowercase()] = parts[1].trim()
            }
        }

        return when (type) {
            "KINEMATICS" -> {
                PhysicsDiagram.Kinematics(
                    mass = params["mass"] ?: params["m"],
                    initialVelocity = params["u"] ?: params["initial_velocity"] ?: params["v0"],
                    finalVelocity = params["v"] ?: params["final_velocity"],
                    acceleration = params["a"] ?: params["acceleration"],
                    force = params["f"] ?: params["force"],
                    distance = params["s"] ?: params["d"] ?: params["distance"],
                    time = params["t"] ?: params["time"]
                )
            }
            "FREE_BODY" -> {
                PhysicsDiagram.FreeBody(
                    mass = params["mass"] ?: params["m"],
                    normalForce = params["normal"] ?: params["fn"] ?: params["n"],
                    gravityForce = params["gravity"] ?: params["w"] ?: params["mg"],
                    appliedForce = params["applied"] ?: params["fa"],
                    frictionForce = params["friction"] ?: params["f_friction"] ?: params["fk"],
                    netForce = params["net"] ?: params["fnet"]
                )
            }
            "PROJECTILE" -> {
                PhysicsDiagram.Projectile(
                    angle = params["angle"] ?: params["theta"],
                    velocity = params["u"] ?: params["v"] ?: params["velocity"],
                    range = params["range"] ?: params["r"],
                    maxHeight = params["height"] ?: params["h"],
                    timeOfFlight = params["time"] ?: params["t"]
                )
            }
            else -> null
        }
    }

    private fun extractKinematicsHeuristic(text: String, query: String): PhysicsDiagram? {
        val combined = "$query\n$text"

        // Mass (e.g. 5 kg, 500 g)
        val mass = extractFirst(combined, listOf(
            Regex("""(?:mass|m)\s*(?:of\s+(?:the\s+)?body)?\s*[=:]\s*([0-9.]+\s*k?g)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9.]+\s*k?g)\s*(?:mass|body|object|car|ball)""", RegexOption.IGNORE_CASE),
            Regex("""mass\s+([0-9.]+\s*k?g)""", RegexOption.IGNORE_CASE)
        ))

        // Initial velocity (e.g. 10 m/s, 20 km/h)
        val initialVelocity = extractFirst(combined, listOf(
            Regex("""(?:initial\s+velocity|u|v0)\s*[=:]\s*([0-9.]+\s*m/s)""", RegexOption.IGNORE_CASE),
            Regex("""moving\s+at\s+([0-9.]+\s*m/s)""", RegexOption.IGNORE_CASE),
            Regex("""speed\s+of\s+([0-9.]+\s*m/s)""", RegexOption.IGNORE_CASE),
            Regex("""velocity\s*(?:of)?\s*([0-9.]+\s*m/s)""", RegexOption.IGNORE_CASE)
        ))

        // Final velocity
        var finalVelocity = extractFirst(combined, listOf(
            Regex("""(?:final\s+velocity|v)\s*[=:]\s*([0-9.]+\s*m/s)""", RegexOption.IGNORE_CASE),
            Regex("""velocity\s+(?:becomes|reaches)\s+([0-9.]+\s*m/s)""", RegexOption.IGNORE_CASE)
        ))
        val isBroughtToRest = combined.contains("brought to rest", ignoreCase = true) ||
            combined.contains("comes to rest", ignoreCase = true) ||
            combined.contains("before stopping", ignoreCase = true) ||
            combined.contains("stops", ignoreCase = true) ||
            combined.contains("v = 0", ignoreCase = true)
        if (finalVelocity == null && isBroughtToRest) {
            finalVelocity = "0 m/s"
        }

        // Time (e.g. 4 s, 4 seconds)
        val time = extractFirst(combined, listOf(
            Regex("""(?:time|t)\s*(?:taken)?\s*[=:]\s*([0-9.]+\s*s(?:ec(?:onds?)?)?)""", RegexOption.IGNORE_CASE),
            Regex("""in\s+([0-9.]+\s*s(?:ec(?:onds?)?)?)""", RegexOption.IGNORE_CASE)
        ))

        // Force (e.g. 12.5 N, -12.5 N)
        val force = extractFirst(combined, listOf(
            Regex("""(?:force|F)\s*(?:applied|retarding|braking)?\s*[=:]\s*(-?[0-9.]+\s*N)""", RegexOption.IGNORE_CASE),
            Regex("""magnitude\s+of\s+(?:the\s+)?force\s*(?:applied)?\s*is\s*([0-9.]+\s*N)""", RegexOption.IGNORE_CASE),
            Regex("""force\s+of\s+([0-9.]+\s*N)""", RegexOption.IGNORE_CASE)
        ))

        // Acceleration (e.g. -2.5 m/s^2, 2.5 m/s²)
        val acceleration = extractFirst(combined, listOf(
            Regex("""(?:acceleration|retardation|deceleration|a)\s*[=:]\s*(-?[0-9.]+\s*m/s(?:\^2|²|2)?)""", RegexOption.IGNORE_CASE),
            Regex("""a\s*=\s*(-?[0-9.]+\s*m/s(?:\^2|²|2)?)""", RegexOption.IGNORE_CASE)
        ))

        // Distance (e.g. 20 m)
        val distance = extractFirst(combined, listOf(
            Regex("""(?:distance|displacement|s|d)\s*(?:travelled|covered|before\s+stopping)?\s*[=:]\s*([0-9.]+\s*m(?:eters?)?)(?!\s*/|\s*s)""", RegexOption.IGNORE_CASE),
            Regex("""(?:distance|s)\s*=\s*([0-9.]+\s*m)(?!\s*/|\s*s)""", RegexOption.IGNORE_CASE),
            Regex("""travels?\s+([0-9.]+\s*m)(?!\s*/|\s*s)""", RegexOption.IGNORE_CASE)
        ))

        // We require at least initial velocity or mass, plus at least one other parameter
        val count = listOfNotNull(mass, initialVelocity, finalVelocity, time, force, acceleration, distance).size
        if (count >= 2 && (initialVelocity != null || force != null || distance != null)) {
            val isDecel = isBroughtToRest || (acceleration?.startsWith("-") == true) || (force?.startsWith("-") == true)
            return PhysicsDiagram.Kinematics(
                mass = mass,
                initialVelocity = initialVelocity,
                finalVelocity = finalVelocity,
                acceleration = acceleration,
                force = force,
                distance = distance,
                time = time,
                isDecelerating = isDecel
            )
        }

        return null
    }

    private fun extractFirst(text: String, regexes: List<Regex>): String? {
        for (regex in regexes) {
            val match = regex.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }
}
