package dev.blamera.chatgpt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantPromptTest {
    @Test fun anAvailableHypothesisIsQuotedAsUncertainExternalDataAfterTheOriginalInstruction() {
        val instruction = plantInstruction(FRAME_CHATGPT_INSTRUCTION, "Rosa canina")
        assertTrue(instruction.startsWith(FRAME_CHATGPT_INSTRUCTION))
        assertTrue(instruction.contains("« Rosa canina »"))
        assertTrue(instruction.contains("Pl@ntNet"))
        assertTrue(instruction.contains("Cette hypothèse peut être erronée"))
    }

    @Test fun noRelevantHypothesisPreservesTheOriginalObjectInstructionExactly() {
        assertEquals(OBJECT_CHATGPT_INSTRUCTION, plantInstruction(OBJECT_CHATGPT_INSTRUCTION, null))
    }
}
