package com.savvasg.offlinenotes

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureTest {

    @Test
    fun `domain depends on no framework`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.packagee?.name?.startsWith("com.savvasg.offlinenotes.domain") == true }
            .assertFalse { file ->
                file.imports.any { import ->
                    FRAMEWORK_PREFIXES.any { import.name.startsWith(it) }
                }
            }
    }

    @Test
    fun `view models depend on use cases, never on repositories`() {
        Konsist.scopeFromProject()
            .classes()
            .withNameEndingWith("ViewModel")
            .assertFalse { viewModel ->
                viewModel.constructors.any { constructor ->
                    constructor.parameters.any { it.type.name.endsWith("Repository") }
                }
            }
    }

    @Test
    fun `use cases live in the domain use case package and are named for it`() {
        Konsist.scopeFromProject()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("com.savvasg.offlinenotes.domain.usecase") }
    }

    @Test
    fun `room entities stay inside the database module`() {
        Konsist.scopeFromProject()
            .classes()
            .withNameEndingWith("Entity")
            .assertTrue { it.resideInPackage("com.savvasg.offlinenotes.data.database.entity") }
    }

    @Test
    fun `the design system knows nothing about the domain`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.packagee?.name?.startsWith("com.savvasg.offlinenotes.core.designsystem") == true }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("com.savvasg.offlinenotes.domain") }
            }
    }

    @Test
    fun `no println in production code`() {
        Konsist.scopeFromProduction()
            .functions()
            .assertFalse { it.text.contains("println(") }
    }

    private companion object {
        val FRAMEWORK_PREFIXES = listOf(
            "android.",
            "androidx.",
            "retrofit2.",
            "okhttp3.",
            "kotlinx.serialization.",
            "dagger.",
        )
    }
}
