package com.samfreeze.app.util

object PackageUtils {

    // Standard Android package name shape: at least two dot-separated
    // segments, each starting with a letter/underscore, alnum/underscore after.
    private val PACKAGE_NAME_REGEX =
        Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /**
     * Validates a package name strictly. This is the single gate that
     * decides whether a string is ever allowed to reach a root command.
     */
    fun isValidPackageName(name: String): Boolean {
        if (name.isBlank() || name.length > 255) return false
        return PACKAGE_NAME_REGEX.matches(name)
    }
}
