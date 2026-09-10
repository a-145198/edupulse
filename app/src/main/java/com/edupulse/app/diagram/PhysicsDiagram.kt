package com.edupulse.app.diagram

sealed class PhysicsDiagram {

    data class Kinematics(
        val mass: String? = null,            // e.g. "5 kg"
        val initialVelocity: String? = null, // e.g. "10 m/s"
        val finalVelocity: String? = null,   // e.g. "0 m/s"
        val acceleration: String? = null,    // e.g. "-2.5 m/s²"
        val force: String? = null,           // e.g. "12.5 N"
        val distance: String? = null,        // e.g. "20 m"
        val time: String? = null,            // e.g. "4 s"
        val isDecelerating: Boolean = true
    ) : PhysicsDiagram() {
        val hasData: Boolean
            get() = initialVelocity != null || finalVelocity != null || distance != null || acceleration != null
    }

    data class FreeBody(
        val mass: String? = null,
        val normalForce: String? = null,
        val gravityForce: String? = null,
        val appliedForce: String? = null,
        val frictionForce: String? = null,
        val netForce: String? = null
    ) : PhysicsDiagram()

    data class Projectile(
        val angle: String? = null,
        val velocity: String? = null,
        val range: String? = null,
        val maxHeight: String? = null,
        val timeOfFlight: String? = null
    ) : PhysicsDiagram()
}
