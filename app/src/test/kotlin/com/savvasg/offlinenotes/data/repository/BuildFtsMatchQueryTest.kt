package com.savvasg.offlinenotes.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildFtsMatchQueryTest {

    @Test
    fun `blank input returns null - meaning no filter`() {
        assertThat(buildFtsMatchQuery("")).isNull()
        assertThat(buildFtsMatchQuery("   ")).isNull()
    }

    @Test
    fun `single word becomes a quoted prefix match`() {
        assertThat(buildFtsMatchQuery("shop")).isEqualTo("\"shop\"*")
    }

    @Test
    fun `multiple words become multiple quoted prefix terms`() {
        assertThat(buildFtsMatchQuery("grocery list")).isEqualTo("\"grocery\"* \"list\"*")
    }

    @Test
    fun `FTS5 syntax characters in user input are neutralized by quoting`() {
        assertThat(buildFtsMatchQuery("milk OR eggs")).isEqualTo("\"milk\"* \"OR\"* \"eggs\"*")
        assertThat(buildFtsMatchQuery("col:value")).isEqualTo("\"col:value\"*")
    }

    @Test
    fun `embedded double quotes are escaped rather than breaking out of the quoted term`() {
        assertThat(buildFtsMatchQuery("say \"hi\"")).isEqualTo("\"say\"* \"\"\"hi\"\"\"*")
    }

    @Test
    fun `extra whitespace between words collapses`() {
        assertThat(buildFtsMatchQuery("  a    b  ")).isEqualTo("\"a\"* \"b\"*")
    }
}
