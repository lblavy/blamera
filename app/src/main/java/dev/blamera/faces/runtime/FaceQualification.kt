package dev.blamera.faces.runtime

internal class FaceQualification {
    private var incompatible = false

    fun <T> verify(check: () -> T): T {
        if (incompatible) throw FaceEngineException()
        try {
            return check()
        } catch (failed: FaceParityFailure) {
            incompatible = true
            throw failed
        }
    }

    fun openKernel(open: () -> FaceKernel, check: (FaceKernel) -> Unit): FaceKernel = verify {
        val kernel = open()
        var accepted = false
        try {
            check(kernel)
            accepted = true
            kernel
        } finally {
            if (!accepted) kernel.close()
        }
    }
}
